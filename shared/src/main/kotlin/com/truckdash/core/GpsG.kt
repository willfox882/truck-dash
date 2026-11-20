package com.truckdash.core

import kotlin.math.abs
import kotlin.math.exp

/**
 * G-force from GPS alone, for head units whose IMU is missing or silent.
 * Longitudinal g = change of GPS (Doppler) speed per second; lateral g = speed x heading change rate.
 * Smoothed with a ~1.2 s time constant because fixes arrive at about 1 Hz. + long = accelerating, + lat = right.
 */
class GpsGEstimator(private val tauMs: Float = 1_200f) {
    private var lastClock = -1L
    private var lastSpeed = 0f
    private var lastBearing: Float? = null
    private var longG = 0f
    private var latG = 0f

    /** Feed every accepted fix. Returns the smoothed estimate (zero until two usable fixes). */
    fun update(clockMs: Long, speedMps: Float, bearingDeg: Float?): VehicleG {
        val dtMs = if (lastClock >= 0) clockMs - lastClock else -1L
        val b = if (speedMps > MIN_TURN_MPS) bearingDeg else null
        if (dtMs in MIN_DT_MS..MAX_DT_MS) {
            val dtS = dtMs / 1000f
            val rawLong = ((speedMps - lastSpeed) / dtS / Geo.STD_G.toFloat()).coerceIn(-MAX_G, MAX_G)
            val prevB = lastBearing
            val rawLat = if (b != null && prevB != null) {
                val dPsiRad = Math.toRadians(wrap180((b - prevB).toDouble())).toFloat()
                ((speedMps * dPsiRad / dtS) / Geo.STD_G.toFloat()).coerceIn(-MAX_G, MAX_G)
            } else 0f
            val a = 1f - exp(-dtMs / tauMs)
            longG += (rawLong - longG) * a
            latG += (rawLat - latG) * a
        } else if (dtMs > MAX_DT_MS || dtMs < 0) {
            longG = 0f; latG = 0f // gap or first fix: do not carry an old value
        }
        if (dtMs !in 0 until MIN_DT_MS) { // keep the previous sample if two fixes arrive almost together
            lastClock = clockMs; lastSpeed = speedMps; lastBearing = b
        }
        if (speedMps < STILL_MPS) { longG = 0f; latG = 0f }
        return VehicleG(longG, latG, 0f)
    }

    fun reset() {
        lastClock = -1; lastSpeed = 0f; lastBearing = null; longG = 0f; latG = 0f
    }

    private fun wrap180(d: Double): Double {
        var x = d % 360.0
        if (x > 180) x -= 360
        if (x < -180) x += 360
        return x
    }

    private companion object {
        const val MIN_DT_MS = 400L
        const val MAX_DT_MS = 2_500L
        const val MIN_TURN_MPS = 3f // GPS bearing is meaningless below about 11 km/h
        const val STILL_MPS = 0.5f
        const val MAX_G = 1.2f
    }
}

/** True when a g sample is exactly or almost exactly zero on both axes, which a real accelerometer never reports. */
fun isDeadSample(g: VehicleG) = abs(g.longG) + abs(g.latG) < 1e-4f
