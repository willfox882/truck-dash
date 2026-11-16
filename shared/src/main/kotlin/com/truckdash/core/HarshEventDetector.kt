package com.truckdash.core

import kotlin.math.abs

enum class HarshType { ACCEL, BRAKE, CORNER }

/**
 * A harsh event fires only after the g-force stays over its threshold for [holdMs] (so a pothole or
 * mount vibration doesn't count), then stays disarmed until the value falls below [rearmFraction] of the
 * threshold (so one long ramp or corner is one event, not several).
 */
class HarshEventDetector(
    private val accelG: Float = 0.30f,
    private val brakeG: Float = 0.35f,
    private val cornerG: Float = 0.40f,
    private val minSpeedMps: Float = 3f,
    private val holdMs: Long = 300,
    private val rearmFraction: Float = 0.7f,
) {
    private class State { var since = -1L; var armed = true }

    private val states = HarshType.entries.associateWith { State() }

    /** Returns a type when a new harsh event fires, else null. */
    fun update(tMs: Long, longG: Float, latG: Float, speedMps: Float): HarshType? {
        if (speedMps < minSpeedMps) { reset(); return null }
        var fired: HarshType? = null
        for (type in HarshType.entries) {
            val st = states.getValue(type)
            val (v, threshold) = when (type) {
                HarshType.BRAKE -> -longG to brakeG
                HarshType.ACCEL -> longG to accelG
                HarshType.CORNER -> abs(latG) to cornerG
            }
            if (v >= threshold) {
                if (st.since < 0) st.since = tMs
                if (st.armed && fired == null && tMs - st.since >= holdMs) { st.armed = false; fired = type }
            } else {
                st.since = -1
                if (v < threshold * rearmFraction) st.armed = true
            }
        }
        return fired
    }

    fun reset() = states.values.forEach { it.since = -1; it.armed = true }
}
