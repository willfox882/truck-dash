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

    /** A fresh, unopened link. [preferredAddress] is the user's saved dongle (null = pick automatically by name). */
    fun newLink(preferredAddress: String?): ObdLink

    /** Bonded devices for the picker; empty when Bluetooth is off or the permission is missing. Never throws. */
    fun pairedDevices(): List<PairedDevice>
}
