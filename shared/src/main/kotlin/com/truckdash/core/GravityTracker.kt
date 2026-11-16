package com.truckdash.core

import kotlin.math.abs
import kotlin.math.exp

/**
 * Fallback for devices without TYPE_GRAVITY / TYPE_LINEAR_ACCELERATION: estimates the gravity vector
 * from the raw accelerometer with a slow low-pass filter (time-based, so independent of sample rate).
 * Gravity is only updated while |a| is within [gateFraction] of 1 g, i.e. while the vehicle is not
 * braking/accelerating/cornering hard; otherwise a sustained manoeuvre would leak into "gravity"
 * and make the g reading decay to zero (and read backwards afterwards).
 */
class GravityTracker(private val tauMs: Float = 6_000f, private val gateFraction: Float = 0.03f) {
    var gravity: Vec3? = null
        private set
    private var lastMs = -1L

    /** @return linear acceleration (raw minus gravity) in m/s², or null until a steady sample initialises gravity. */
    fun update(tMs: Long, raw: Vec3): Vec3? {
        val steady = abs(raw.norm() / Geo.STD_G.toFloat() - 1f) <= gateFraction
        val g0 = gravity
        if (g0 == null) {
            if (!steady) return null
            gravity = raw
            lastMs = tMs
            return Vec3(0f, 0f, 0f)
        }
        val dt = (tMs - lastMs).coerceIn(1, 1_000)
        lastMs = tMs
        if (steady) {
            val a = 1f - exp(-dt / tauMs)
            gravity = g0 * (1f - a) + raw * a
        }
        return raw - gravity!!
    }

    fun reset() { gravity = null; lastMs = -1 }
}
