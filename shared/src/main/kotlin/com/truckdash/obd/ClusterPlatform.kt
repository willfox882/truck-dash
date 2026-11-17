package com.truckdash.obd

import kotlinx.coroutines.flow.StateFlow

/**
 * Everything Bluetooth the shared UI needs from the platform. Android: `AndroidClusterPlatform` (RFCOMM).
 * Sandbox: a fake dongle. Nothing here is touched until the Cluster tab is opened, so a hidden tab means no Bluetooth activity.
 */
interface ClusterPlatform {
    /** False while the app is in the background: the dongle is disconnected then. */
    val appActive: StateFlow<Boolean>

    /** Runtime permission (BLUETOOTH_CONNECT on Android 12+). Always true where none is needed. */
    fun hasPermission(): Boolean

    /** Asks the user. [onResult] is called with the answer; only used when [hasPermission] is false. */
    fun requestPermission(onResult: (Boolean) -> Unit)

    /**
     * False when the system will not show the permission dialog any more (denied before, no rationale): the only way
     * left is the app's settings page ([openPermissionSettings]). True where asking can still work.
     */
    fun canRequestPermission(): Boolean

    /** Opens the system settings page of this app so the user can allow the permission by hand. Never throws. */
    fun openPermissionSettings()

    /** A fresh, unopened link. [preferredAddress] is the user's saved dongle (null = pick automatically by name). */
    fun newLink(preferredAddress: String?): ObdLink

    /** Bonded devices for the picker; empty when Bluetooth is off or the permission is missing. Never throws. */
    fun pairedDevices(): List<PairedDevice>
}
