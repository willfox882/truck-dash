package com.truckdash.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    const val MPS_TO_MPH = 2.2369362920544
    const val M_TO_MI = 0.000621371192237
    const val M_TO_FT = 3.280839895
    const val STD_G = 9.80665

    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        return 2 * r * asin(sqrt(a))
    }

    private val LABELS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun compassLabel(deg: Float): String {
        val d = ((deg % 360f) + 360f) % 360f
        return LABELS[((d + 22.5f) / 45f).toInt() % 8]
    }
}
