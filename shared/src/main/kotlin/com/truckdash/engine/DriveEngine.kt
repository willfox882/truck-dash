package com.truckdash.engine

import com.truckdash.core.Geo
import com.truckdash.core.VehicleG
import com.truckdash.model.LiveState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.exp

/**
 * Platform-independent core: feed it GPS fixes and vehicle-frame g samples (from real sensors on the
 * truck, or from the sandbox simulator) and it maintains [state] and records trips via [sink].
 * Call from a single thread.
 */
class DriveEngine(sink: TripSink) {
    private val recorder = TripRecorder(sink)
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private var longEma = 0f
    private var latEma = 0f
    private var lastGClock = -1L
    private var lastPush = -1_000L
    private var lastFixClock = -1L

    fun setServiceRunning(running: Boolean) = _state.update { it.copy(serviceRunning = running) }

    fun onLocation(
        clockMs: Long, wallMs: Long, lat: Double, lon: Double, altM: Double?,
        speedMps: Float, bearingDeg: Float?, accuracyM: Float?,
    ) {
        lastFixClock = clockMs
        val speed = speedMps.coerceAtLeast(0f)
        recorder.onFix(clockMs, wallMs, lat, lon, altM, speed)
        _state.update {
            it.copy(
                hasFix = true,
                speedKmh = (speed * Geo.MPS_TO_KMH).toFloat(),
                bearingDeg = if (bearingDeg != null && speed > 1f) bearingDeg else it.bearingDeg,
                altM = recorder.altM,
                lat = lat, lon = lon, accuracyM = accuracyM,
                gradePct = recorder.gradePct ?: it.gradePct,
                recording = recorder.active,
                tripKm = recorder.distM * Geo.M_TO_KM,
                tripMovingSec = recorder.movingMs / 1000,
                tripMaxKmh = (recorder.maxMps * Geo.MPS_TO_KMH).toFloat(),
            )
        }
    }

    /** Raw vehicle-frame g sample (any rate). Smoothed with a ~120 ms time constant. */
    fun onAcceleration(clockMs: Long, wallMs: Long, g: VehicleG) {
        if (lastGClock < 0) {
            longEma = g.longG; latEma = g.latG
        } else {
            val dt = (clockMs - lastGClock).coerceIn(1, 1_000)
            val a = 1f - exp(-dt / TAU_MS)
            longEma += (g.longG - longEma) * a
            latEma += (g.latG - latEma) * a
        }
        lastGClock = clockMs
        recorder.onAcceleration(clockMs, wallMs, longEma, latEma)
        if (clockMs - lastPush >= 100) {
            lastPush = clockMs
            _state.update { it.copy(longG = longEma, latG = latEma) }
        }
    }

    /** Call about once a second: marks the GPS fix as lost after 5 s of silence. */
    fun tick(clockMs: Long) {
        if (lastFixClock >= 0 && clockMs - lastFixClock > 5_000 && _state.value.hasFix) {
            _state.update { it.copy(hasFix = false, speedKmh = 0f) }
        }
    }

    fun onProviderDisabled() = _state.update { it.copy(hasFix = false, speedKmh = 0f) }

    /** Ends any open trip and clears live data. */
    fun stop(clockMs: Long, wallMs: Long) {
        recorder.stop(clockMs, wallMs)
        _state.value = LiveState()
    }

    private companion object {
        const val TAU_MS = 120f
    }
}
