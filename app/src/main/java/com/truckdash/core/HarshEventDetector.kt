package com.truckdash.core

import kotlin.math.abs

enum class HarshType { ACCEL, BRAKE, CORNER }

class HarshEventDetector(
    private val accelG: Float = 0.30f,
    private val brakeG: Float = 0.35f,
    private val cornerG: Float = 0.40f,
    private val minSpeedMps: Float = 3f,
    private val cooldownMs: Long = 3_000,
) {
    private val last = mutableMapOf<HarshType, Long>()

    /** Returns a type when a new harsh event fires (per-type cooldown), else null. */
    fun update(tMs: Long, longG: Float, latG: Float, speedMps: Float): HarshType? {
        if (speedMps < minSpeedMps) return null
        val type = when {
            longG <= -brakeG -> HarshType.BRAKE
            longG >= accelG -> HarshType.ACCEL
            abs(latG) >= cornerG -> HarshType.CORNER
            else -> return null
        }
        val prev = last[type]
        if (prev != null && tMs - prev < cooldownMs) return null
        last[type] = tMs
        return type
    }

    fun reset() = last.clear()
}
