package com.truckdash.obd

/** Plain-language strings for the Cluster screen. No em-dashes, metric only. */
object ClusterText {
    const val FAILED_TITLE = "Could not connect to the OBD dongle"
    const val RETRY_HINT = "Trying again every 5 seconds"
    const val RETRYING = "Trying again now"

    /** [permissionViaSettings]: Android will not show the permission dialog again, so Retry opens the app's settings. */
    fun reason(f: ObdFailure, permissionViaSettings: Boolean = false): String = when (f) {
        ObdFailure.BluetoothOff -> "Bluetooth is off. Turn it on in the unit's settings."
        ObdFailure.NoAdapter -> "This unit has no Bluetooth."
        ObdFailure.NoPairedDevice -> "No paired OBD adapter. Pair it in the unit's Bluetooth settings."
        ObdFailure.PermissionNeeded ->
            if (permissionViaSettings) "Bluetooth permission is blocked. Tap Retry to open the app's settings, then allow Nearby devices."
            else "Bluetooth permission is needed. Allow it, then tap Retry."
        ObdFailure.ConnectionFailed -> "Connection failed. Check the adapter is plugged in and the truck is on."
        ObdFailure.InitFailed -> "The adapter did not answer. Check it is plugged in, then try again."
        ObdFailure.LinkLost -> "The connection dropped. Check the adapter is plugged in."
    }

    enum class Tone { Neutral, Ok, Caution }

    class Status(val text: String, val tone: Tone)

    /** The single status line under the title: connection progress, staged transmission messages, or "live". */
    fun status(s: ClusterState): Status = when (s.phase) {
        ClusterPhase.Idle -> Status("Not connected", Tone.Neutral)
        ClusterPhase.Connecting -> Status("Connecting to the OBD dongle", Tone.Neutral)
        ClusterPhase.Initializing -> Status("Starting the adapter" + (s.device?.let { ", $it" } ?: ""), Tone.Neutral)
        ClusterPhase.Failed -> Status(if (s.retrying) RETRYING else RETRY_HINT, Tone.Caution)
        ClusterPhase.Live -> when {
            s.transStage == TransStage.Physical && !s.transConfirmed -> Status("Trans temp: asking the transmission module", Tone.Caution)
            s.transStage == TransStage.Fallback -> Status("Showing coolant temp instead", Tone.Caution)
            s.values.isEmpty() -> Status("Adapter connected, waiting for the truck", Tone.Neutral)
            else -> Status("Live" + (s.device?.let { " · $it" } ?: ""), Tone.Ok)
        }
    }
}
