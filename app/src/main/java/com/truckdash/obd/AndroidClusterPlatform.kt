package com.truckdash.obd

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android side of the Cluster tab. One instance lives for the whole process (`AppGraph`), together with the
 * `ClusterPresenter`, so it only holds the application context. The activity attaches a [Host] while it exists (it owns the
 * BLUETOOTH_CONNECT activity-result launcher) and reports visibility. Creating it does nothing with Bluetooth.
 */
class AndroidClusterPlatform(ctx: Context) : ClusterPlatform {
    /** What only an activity can do. */
    interface Host {
        fun launchPermissionRequest()
        fun shouldShowPermissionRationale(): Boolean
    }

    private val ctx = ctx.applicationContext
    private val _active = MutableStateFlow(false)
    override val appActive: StateFlow<Boolean> = _active

    @Volatile private var host: Host? = null
    private var pendingResult: ((Boolean) -> Unit)? = null
    private var deniedBefore = false

    fun attach(h: Host) { host = h }
    fun detach(h: Host) { if (host === h) host = null }

    /** Called from onStart / onStop: the dongle stays connected while the app is visible (a popup over it is fine). */
    fun setActive(active: Boolean) { _active.value = active }

    /** Called with the outcome of the permission prompt. */
    fun onPermissionResult(granted: Boolean) {
        if (!granted) deniedBefore = true
        pendingResult?.invoke(granted)
        pendingResult = null
    }

    override fun hasPermission(): Boolean = BluetoothObdLink.hasConnectPermission(ctx)

    /** After a denial Android only shows the dialog again when it says a rationale is due; otherwise the system settings. */
    override fun canRequestPermission(): Boolean {
        val h = host ?: return !deniedBefore
        return !deniedBefore || h.shouldShowPermissionRationale()
    }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        pendingResult = onResult
        try {
            host?.launchPermissionRequest() ?: run { pendingResult = null }
        } catch (_: RuntimeException) {
            pendingResult = null
        }
    }

    override fun openPermissionSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        } catch (_: RuntimeException) {
        }
    }

    override fun newLink(preferredAddress: String?): ObdLink = BluetoothObdLink(ctx, preferredAddress)

    override fun pairedDevices(): List<PairedDevice> = BluetoothObdLink.pairedDevices(ctx)
}
