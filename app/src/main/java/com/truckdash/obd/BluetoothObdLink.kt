package com.truckdash.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bluetooth SPP (RFCOMM) link to an ELM327 adapter. Thin on purpose: device choice is [DeviceSelector], everything else is
 * the shared [ObdSession]. Never throws anything but [ObdLinkException] / [ObdTimeoutException] / cancellation.
 *
 *  - connect: standard SPP service record first, then the reflection `createRfcommSocket(1)` fallback many clones need;
 *  - the blocking `connect()` runs on its own thread and cancelling the caller closes the socket, which aborts it at once;
 *  - reads poll `available()` (like the original app) so a cancelled coroutine never sits in a blocking read.
 */
class BluetoothObdLink(private val ctx: Context, private val preferredAddress: String?) : ObdLink {
    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val MAX_REPLY = 4096

        /** One RFCOMM connect attempt gives up after this long, so "could not connect" shows within about 10 s with the truck off. */
        const val CONNECT_TIMEOUT_MS = 8_000L

        fun hasConnectPermission(ctx: Context): Boolean =
            Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

        /** Bonded devices, or empty when there is no adapter, Bluetooth is off or the permission is missing. */
        @SuppressLint("MissingPermission")
        fun bonded(ctx: Context): List<BluetoothDevice> = try {
            val adapter = adapterOf(ctx)
            if (adapter == null || !adapter.isEnabled || !hasConnectPermission(ctx)) emptyList() else adapter.bondedDevices.orEmpty().toList()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }

        @SuppressLint("MissingPermission")
        fun pairedDevices(ctx: Context): List<PairedDevice> = try {
            bonded(ctx).map { PairedDevice(it.name.orEmpty(), it.address) }
        } catch (_: SecurityException) {
            emptyList()
        }

        private fun adapterOf(ctx: Context): BluetoothAdapter? =
            (ctx.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    override var deviceName: String? = null
        private set

    @Volatile private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @SuppressLint("MissingPermission")
    override suspend fun open() {
        val adapter = adapterOf(ctx) ?: throw ObdLinkException(ObdFailure.NoAdapter)
        if (!hasConnectPermission(ctx)) throw ObdLinkException(ObdFailure.PermissionNeeded)
        try {
            if (!adapter.isEnabled) throw ObdLinkException(ObdFailure.BluetoothOff)
            val bondedDevices = adapter.bondedDevices.orEmpty().toList()
            val infos = bondedDevices.map { PairedDevice(it.name.orEmpty(), it.address) }
            val pick = DeviceSelector.pick(infos, preferredAddress) ?: throw ObdLinkException(ObdFailure.NoPairedDevice)
            val device = bondedDevices.first { it.address == pick.address }
            deviceName = pick.name.ifBlank { pick.address }
            // we never discover, but if something else does it slows the connection; may need a permission we do not hold
            try { adapter.cancelDiscovery() } catch (_: SecurityException) { }
            connect(device)
        } catch (e: SecurityException) {
            throw ObdLinkException(ObdFailure.PermissionNeeded, cause = e)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connect(device: BluetoothDevice) {
        var sock: BluetoothSocket? = null
        try {
            sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
            if (!connectWithinTimeout(sock)) {
                // nobody answered (truck off, out of range): no point trying the other route for another 8 s
                closeQuietly(sock)
                throw ObdLinkException(ObdFailure.ConnectionFailed, "connect timed out")
            }
        } catch (e: IOException) {
            // many clone adapters reject the SDP route: raw channel 1 via reflection
            closeQuietly(sock)
            sock = null
            try {
                @Suppress("DiscouragedPrivateApi")
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                sock = m.invoke(device, 1) as BluetoothSocket
                if (!connectWithinTimeout(sock)) {
                    closeQuietly(sock)
                    throw ObdLinkException(ObdFailure.ConnectionFailed, "connect timed out")
                }
            } catch (e2: IOException) {
                closeQuietly(sock)
                throw ObdLinkException(ObdFailure.ConnectionFailed, cause = e2)
            } catch (e2: ReflectiveOperationException) {
                closeQuietly(sock)
                throw ObdLinkException(ObdFailure.ConnectionFailed, cause = e2)
            }
        }
        try {
            input = sock.inputStream
            output = sock.outputStream
        } catch (e: IOException) {
            closeQuietly(sock)
            throw ObdLinkException(ObdFailure.ConnectionFailed, cause = e)
        }
        socket = sock
    }

    /** False when the connect did not finish in [CONNECT_TIMEOUT_MS]; the timeout cancels connectBlocking, which closes the socket. */
    private suspend fun connectWithinTimeout(sock: BluetoothSocket): Boolean =
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connectBlocking(sock); true } ?: false

    /** Blocking connect on a helper thread; cancelling the coroutine closes the socket, which unblocks (and fails) connect(). */
    @SuppressLint("MissingPermission") // permission is checked in open(); a revoked one surfaces as SecurityException and is mapped there
    private suspend fun connectBlocking(sock: BluetoothSocket) = suspendCancellableCoroutine<Unit> { cont ->
        socket = sock
        cont.invokeOnCancellation { closeQuietly(sock) }
        thread(isDaemon = true, name = "obd-connect") {
            try {
                sock.connect()
                cont.resume(Unit)
            } catch (t: Throwable) {
                if (cont.isActive) cont.resumeWithException(if (t is IOException || t is SecurityException) t else IOException(t))
            }
        }
    }

    override suspend fun write(text: String) {
        val out = output ?: throw ObdLinkException(ObdFailure.LinkLost, "not open")
        try {
            out.write(text.toByteArray(Charsets.US_ASCII))
            out.flush()
        } catch (e: IOException) {
            throw ObdLinkException(ObdFailure.LinkLost, cause = e)
        }
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String {
        val inp = input ?: throw ObdLinkException(ObdFailure.LinkLost, "not open")
        val sb = StringBuilder()
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        try {
            while (true) {
                while (inp.available() > 0) {
                    val b = inp.read()
                    if (b < 0) throw ObdLinkException(ObdFailure.LinkLost, "socket closed")
                    val ch = b.toChar()
                    if (ch == '>') return sb.toString()
                    if (sb.length < MAX_REPLY) sb.append(ch)
                }
                if (System.nanoTime() > deadline) throw ObdTimeoutException()
                delay(20) // cancellable: a cancelled session leaves here immediately
            }
        } catch (e: IOException) {
            throw ObdLinkException(ObdFailure.LinkLost, cause = e)
        }
    }

    override fun close() {
        val s = socket
        socket = null
        input = null
        output = null
        closeQuietly(s)
    }

    private fun closeQuietly(s: BluetoothSocket?) {
        try { s?.close() } catch (_: Throwable) { }
    }
}
