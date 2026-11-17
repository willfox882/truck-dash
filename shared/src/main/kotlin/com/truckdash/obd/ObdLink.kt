package com.truckdash.obd

/** Why the dongle is not usable. Each value maps to one plain sentence in [ClusterText.reason]. */
enum class ObdFailure { BluetoothOff, NoAdapter, NoPairedDevice, PermissionNeeded, ConnectionFailed, InitFailed, LinkLost }

/** A link-level error with a known reason (Bluetooth off, nothing paired, ...). */
class ObdLinkException(val failure: ObdFailure, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: failure.name, cause)

/** No prompt ('>') arrived within the allowed time. */
class ObdTimeoutException(message: String = "timeout") : Exception(message)

/** A bonded Bluetooth device as the platform reports it. */
data class PairedDevice(val name: String, val address: String)

/**
 * One connection to an ELM327 adapter, one instance per attempt. Implementations: Bluetooth RFCOMM on Android,
 * `FakeDongle` in the sandbox. All calls suspend and must be cancellable: cancelling the caller must free the
 * underlying socket promptly (the session also calls [close] in a finally block).
 */
interface ObdLink {
    /** Display name of the adapter actually used, known after a successful [open]. */
    val deviceName: String?

    /** Connects. Throws [ObdLinkException] (with the reason) when it cannot. */
    suspend fun open()

    suspend fun write(text: String)

    /** Reads everything up to (not including) the ELM327 '>' prompt. Throws [ObdTimeoutException] or [ObdLinkException]. */
    suspend fun readUntilPrompt(timeoutMs: Long): String

    /** Idempotent, never throws, safe to call from any thread. */
    fun close()
}

/** Sends one command (CR terminated) and returns the reply text. */
suspend fun ObdLink.exchange(command: String, timeoutMs: Long): String {
    write(command + "\r")
    return readUntilPrompt(timeoutMs)
}
