package com.truckdash.sandbox

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** One scripted driving segment: hold [mph] for [durS] seconds while turning [yawDegS] (+ = right) on [gradePct]. */
data class Seg(
    val durS: Double,
    val kmh: Double,
    val yawDegS: Double = 0.0,
    val gradePct: Double = 0.0,
    val accel: Double = 2.2, // m/s^2 limit when speeding up
    val brake: Double = 2.5, // m/s^2 limit when slowing down (0.25 g: normal braking)
)

class Scenario(val name: String, val blurb: String, val segs: List<Seg>) {
    val totalS: Double get() = segs.sumOf { it.durS }
}

private fun turn(deg: Double, kmh: Double, durS: Double = 6.0) = Seg(durS, kmh, yawDegS = deg / durS)

/** End-of-drive stop long enough (> 5 min) for the recorder to close the trip. */
private val parkFor = Seg(330.0, 0.0)

object Scenarios {
    val cityLoop = Scenario(
        "City loop", "Stop lights, turns, 50 km/h streets, a couple of small hills.",
        listOf(
            Seg(15.0, 40.0), turn(90.0, 25.0), Seg(25.0, 50.0), Seg(10.0, 0.0), Seg(12.0, 0.0),
            Seg(20.0, 50.0), turn(-90.0, 25.0), Seg(30.0, 60.0, gradePct = 2.0), Seg(8.0, 0.0), Seg(10.0, 0.0),
            Seg(25.0, 50.0), turn(90.0, 20.0), Seg(20.0, 50.0), turn(90.0, 20.0), Seg(25.0, 50.0, gradePct = -2.0),
            Seg(10.0, 0.0), parkFor,
        ),
    )

    val highway = Scenario(
        "Highway climb", "On-ramp, 110 km/h cruise, a 5% climb and a 6% descent.",
        listOf(
            Seg(25.0, 70.0, accel = 2.0), Seg(20.0, 100.0), Seg(150.0, 110.0),
            Seg(15.0, 110.0, yawDegS = 1.5), Seg(15.0, 110.0, yawDegS = -1.5),
            Seg(60.0, 105.0, gradePct = 5.0), Seg(40.0, 110.0), Seg(60.0, 110.0, gradePct = -6.0),
            Seg(40.0, 110.0), Seg(20.0, 70.0), Seg(15.0, 40.0, yawDegS = 6.0), Seg(15.0, 0.0), parkFor,
        ),
    )

    val hardEvents = Scenario(
        "Hard-driving demo", "Hard brake, hard launch and sharp corners to light up the G-ball and event markers.",
        listOf(
            Seg(20.0, 65.0), Seg(3.0, 13.0, brake = 7.5), Seg(6.0, 13.0), Seg(5.0, 70.0, accel = 4.5),
            Seg(8.0, 55.0), Seg(3.0, 50.0, yawDegS = 25.0), Seg(5.0, 50.0), Seg(3.0, 50.0, yawDegS = -25.0),
            Seg(10.0, 55.0), Seg(15.0, 0.0), parkFor,
        ),
    )

    val parked = Scenario("Parked", "Engine idling, GPS fix, no movement.", listOf(Seg(3600.0, 0.0)))

    val all = listOf(cityLoop, highway, hardEvents, parked)
}

/** Point-mass vehicle on a flat-earth patch: speed, heading, position, altitude. */
class VehicleSim(var lat: Double = 39.7392, var lon: Double = -104.9903, var headingDeg: Double = 90.0, var altM: Double = 1609.0) {
    var speedMps = 0.0; private set
    var accel = 0.0; private set
    var yawDegS = 0.0; private set

    fun advance(dt: Double, targetMps: Double, yawDegS: Double, gradePct: Double, accelLimit: Double, brakeLimit: Double) {
        val dv = targetMps - speedMps
        accel = if (dv >= 0) min(dv / dt, accelLimit) else max(dv / dt, -brakeLimit)
        speedMps = max(0.0, speedMps + accel * dt)
        this.yawDegS = if (speedMps > 0.5) yawDegS else 0.0
        headingDeg = ((headingDeg + this.yawDegS * dt) % 360.0 + 360.0) % 360.0
        val rad = Math.toRadians(headingDeg)
        lat += speedMps * cos(rad) * dt / 111_320.0
        lon += speedMps * kotlin.math.sin(rad) * dt / (111_320.0 * cos(Math.toRadians(lat)))
        altM += speedMps * dt * gradePct / 100.0
    }

    /** Longitudinal g (+ = accelerating). */
    fun longG() = (accel / 9.80665).toFloat()

    /** Lateral g (+ = right turn). */
    fun latG() = (speedMps * Math.toRadians(yawDegS) / 9.80665).toFloat()
}
