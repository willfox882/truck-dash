package com.truckdash.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LiveState(
    val serviceRunning: Boolean = false,
    val hasFix: Boolean = false,
    val speedMph: Float = 0f,
    val bearingDeg: Float? = null,
    val altFt: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    val longG: Float = 0f,
    val latG: Float = 0f,
    val gradePct: Float? = null,
    val recording: Boolean = false,
    val tripMiles: Double = 0.0,
    val tripMovingSec: Long = 0,
    val tripMaxMph: Float = 0f,
) {
    val tripAvgMph: Float
        get() = if (tripMovingSec > 0) (tripMiles / (tripMovingSec / 3600.0)).toFloat() else 0f
}

/** In-process bridge: [DriveService] writes, UI reads. */
object TelemetryHub {
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    fun update(f: (LiveState) -> LiveState) = _state.update(f)
}
