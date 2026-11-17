package com.truckdash.obd

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android side of the Cluster tab. [requestPermissionLauncher] starts the BLUETOOTH_CONNECT prompt (MainActivity owns the
 * activity result contract). Created with the activity but does nothing until the Cluster tab asks.
 */
class AndroidClusterPlatform(
    private val ctx: Context,
    private val requestPermissionLauncher: () -> Unit,
) : ClusterPlatform {
    private val _active = MutableStateFlow(false)
    override val appActive: StateFlow<Boolean> = _active

    private var pendingResult: ((Boolean) -> Unit)? = null

    /** Called from onResume / onPause: the dongle is only connected while the app is in the foreground. */
    fun setActive(active: Boolean) { _active.value = active }

    /** Called with the outcome of the permission prompt. */
    fun onPermissionResult(granted: Boolean) {
        pendingResult?.invoke(granted)
        pendingResult = null
    }

    override fun hasPermission(): Boolean = BluetoothObdLink.hasConnectPermission(ctx)

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        pendingResult = onResult
        try {
            requestPermissionLauncher()
        } catch (_: RuntimeException) {
            pendingResult = null
        }
    }

    override fun newLink(preferredAddress: String?): ObdLink = BluetoothObdLink(ctx, preferredAddress)

    override fun pairedDevices(): List<PairedDevice> = BluetoothObdLink.pairedDevices(ctx)
}
