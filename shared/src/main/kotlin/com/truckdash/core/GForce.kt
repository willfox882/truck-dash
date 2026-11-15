package com.truckdash.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = Vec3(x * k, y * k, z * k)
    operator fun div(k: Float) = Vec3(x / k, y / k, z / k)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun norm() = sqrt(x * x + y * y + z * z)
}

/** latG: + = right. longG: + = accelerating forward. vertG: + = up. */
data class VehicleG(val longG: Float, val latG: Float, val vertG: Float)

object GForce {
    /**
     * Projects device-frame linear acceleration into the vehicle frame.
     * Assumes the screen faces the driver, so "forward" = device -Z projected onto the horizontal plane.
     *
     * @param gravity Android TYPE_GRAVITY vector (points "up" in device frame, ~9.81 m/s²)
     * @param linear  linear acceleration (gravity removed), m/s², device frame
     * @param mountYawDeg extra rotation of forward axis about vertical (+ = toward vehicle left)
     */
    fun toVehicle(gravity: Vec3, linear: Vec3, mountYawDeg: Float = 0f): VehicleG? {
        val gn = gravity.norm()
        if (gn < 1f) return null
        val up = gravity / gn
        val fwdDevice = Vec3(0f, 0f, -1f)
        val proj = fwdDevice - up * (fwdDevice dot up)
        val pn = proj.norm()
        if (pn < 0.2f) return null // device lying flat; forward undefined
        var f = proj / pn
        var left = up cross f
        if (mountYawDeg != 0f) {
            val r = Math.toRadians(mountYawDeg.toDouble())
            val c = cos(r).toFloat()
            val s = sin(r).toFloat()
            val f2 = f * c + left * s
            val l2 = left * c - f * s
            f = f2
            left = l2
        }
        val g = Geo.STD_G.toFloat()
        return VehicleG(
            longG = (linear dot f) / g,
            latG = -(linear dot left) / g,
            vertG = (linear dot up) / g,
        )
    }
}
