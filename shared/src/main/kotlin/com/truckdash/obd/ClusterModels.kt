package com.truckdash.obd

enum class ClusterPhase { Idle, Connecting, Initializing, Live, Failed }

/** Staged acquisition of transmission temperature: broadcast request, then addressed to the TCM, then coolant instead. */
enum class TransStage { Functional, Physical, Fallback }

/**
 * Everything the Cluster screen shows. [values] holds degC / percent; a gauge that has no (or stale) data is absent.
 * While [phase] is Failed, [retrying] is true during the background reconnect attempt (the failure stays on screen).
 */
data class ClusterState(
    val phase: ClusterPhase = ClusterPhase.Idle,
    val failure: ObdFailure? = null,
    val retrying: Boolean = false,
    val device: String? = null,
    val values: Map<GaugeId, Float> = emptyMap(),
    val transStage: TransStage = TransStage.Functional,
    /** The current trans stage has answered at least once (it is then never escalated away from). */
    val transConfirmed: Boolean = false,
    /** Permission is missing and Android will not show its dialog again: Retry opens the app's settings instead. */
    val permissionViaSettings: Boolean = false,
) {
    fun value(id: GaugeId): Float? = values[id]
}

/** Label, unit, scale and thresholds of one dial. Amber at/above [warn], red at/above [critical]. */
data class GaugeSpec(
    val id: GaugeId,
    val label: String,
    val short: String,
    val unit: String,
    val min: Float,
    val max: Float,
    val warn: Float? = null,
    val critical: Float? = null,
) {
    fun fraction(v: Float): Float = if (max <= min) 0f else ((v - min) / (max - min)).coerceIn(0f, 1f)

    fun level(v: Float?): Level = when {
        v == null -> Level.None
        critical != null && v >= critical -> Level.Critical
        warn != null && v >= warn -> Level.Warn
        else -> Level.Ok
    }

    enum class Level { None, Ok, Warn, Critical }
}

/** The four dials. All temperatures are degrees C (ranges and warn points converted from the original degF values). */
object ClusterGauges {
    // trans warn is 116 C (the original's 240 F); coolant and intake follow their own original points
    val TRANS = GaugeSpec(GaugeId.Trans, "Transmission", "Trans", "°C", 20f, 150f, warn = 116f, critical = 130f)
    val COOLANT = GaugeSpec(GaugeId.Trans, "Coolant", "Coolant", "°C", 40f, 140f, warn = 118f, critical = 125f)
    val LOAD = GaugeSpec(GaugeId.Load, "Engine load", "Load", "%", 0f, 100f, warn = 90f)
    val THROTTLE = GaugeSpec(GaugeId.Throttle, "Throttle", "Throttle", "%", 0f, 100f)
    val INTAKE = GaugeSpec(GaugeId.Intake, "Intake air", "Intake", "°C", -40f, 80f, warn = 54f) // original 130 F

    /** Dial order: row 1 = Trans/Coolant, Load; row 2 = Throttle, Intake. The first dial relabels itself after the fallback. */
    fun forStage(stage: TransStage): List<GaugeSpec> =
        listOf(if (stage == TransStage.Fallback) COOLANT else TRANS, LOAD, THROTTLE, INTAKE)
}
