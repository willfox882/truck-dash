package com.truckdash.engine

import com.truckdash.core.Geo
import com.truckdash.core.VehicleG
import com.truckdash.model.LiveState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Platform-independent core: feed it GPS fixes and vehicle-frame g samples (from real sensors on the
 * truck, or from the sandbox simulator) and it maintains [state] and records trips via [sink].
 * [onDistance] receives driven metres as trips progress (about every 10 s and at trip end); the odometer
 * is built from it, independent of the trip rows. Call from a single thread.
 */
class DriveEngine(sink: TripSink, onDistance: (Double) -> Unit = {}) {
    private val recorder = TripRecorder(DistanceReportingSink(sink, onDistance))
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private var longEma = 0f
    private var latEma = 0f
    private var lastGClock = -1L
    private var lastPush = -1_000L
    private var lastFixClock = -1L
    private var lastSpeed = 0f
    private var lastSpeedClock = -1L
    private var rejects = 0

    fun setServiceRunning(running: Boolean) = _state.update { it.copy(serviceRunning = running) }

    fun onLocation(
        clockMs: Long, wallMs: Long, lat: Double, lon: Double, altM: Double?,
        speedMps: Float, bearingDeg: Float?, accuracyM: Float?,
    ) {
        // Fix-quality gating: cold-start / multipath fixes would otherwise set bogus max speeds and phantom trips.
        if (accuracyM != null && accuracyM > MAX_ACCURACY_M) return
        val speed = speedMps.coerceAtLeast(0f)
        if (lastSpeedClock >= 0) {
            val dtS = (clockMs - lastSpeedClock) / 1000.0
            if (dtS <= 3.0 && abs(speed - lastSpeed) > MAX_ACCEL_MPS2 * max(dtS, 1.0) && rejects < 2) {
                rejects++ // physically impossible speed jump; two in a row means it's real, so accept
                return
            }
        }
        rejects = 0
        lastSpeed = speed
        lastSpeedClock = clockMs
        lastFixClock = clockMs

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

    /**
     * Call about once a second. Marks the GPS fix lost after 5 s of silence (the last speed is kept, so the
     * "parked only" lock doesn't release in a tunnel) and ends trips whose fixes stopped for good.
     */
    fun tick(clockMs: Long) {
        recorder.onTick(clockMs)
        val noFix = lastFixClock >= 0 && clockMs - lastFixClock > 5_000
        val rec = recorder.active
        val s = _state.value
        if ((noFix && s.hasFix) || s.recording != rec) {
            _state.update { it.copy(hasFix = if (noFix) false else it.hasFix, recording = rec) }
        }
    }

    fun onProviderDisabled() = _state.update { it.copy(hasFix = false) }

    /** Ends any open trip and clears all live data. */
    fun stop(clockMs: Long, wallMs: Long) {
        recorder.stop(clockMs, wallMs)
        recorder.reset()
        longEma = 0f; latEma = 0f; lastGClock = -1
        lastSpeed = 0f; lastSpeedClock = -1; lastFixClock = -1; rejects = 0
        _state.value = LiveState()
    }

    /** Turns the recorder's running trip totals into odometer increments. */
    private class DistanceReportingSink(private val inner: TripSink, private val report: (Double) -> Unit) :
        TripSink by inner {
        private val last = HashMap<Long, Double>()

        override fun tripUpdated(key: Long, trip: com.truckdash.model.Trip, final: Boolean) {
            val delta = trip.distanceM - (last[key] ?: 0.0)
            last[key] = trip.distanceM
            if (delta > 0) report(delta)
            if (final) last.remove(key)
            inner.tripUpdated(key, trip, final)
        }
    }

    private companion object {
        const val TAU_MS = 120f
        const val MAX_ACCURACY_M = 40f
        const val MAX_ACCEL_MPS2 = 12.0 // faster than any truck can change speed
    }
}
