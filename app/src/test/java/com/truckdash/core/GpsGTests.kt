package com.truckdash.core

import com.truckdash.engine.DriveEngine
import com.truckdash.engine.TripSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsGTests {
    @Test fun brakingAndCorneringFromGpsOnly() {
        val e = GpsGEstimator()
        var g = VehicleG(0f, 0f, 0f)
        // 20 m/s, steady heading, then 4 m/s2 hard braking for 5 s
        var v = 20f
        for (i in 0..4) { g = e.update(i * 1000L, v, 90f); v -= 4f }
        assertTrue("long ${g.longG}", g.longG < -0.2f)
        // right turn: heading +10 deg per second at 15 m/s = 0.27 g
        val c = GpsGEstimator()
        var h = 0f
        for (i in 0..8) { g = c.update(i * 1000L, 15f, h); h += 10f }
        assertEquals(0.27f, g.latG, 0.05f)
        // left turn is negative
        val d = GpsGEstimator()
        h = 100f
        for (i in 0..8) { g = d.update(i * 1000L, 15f, h); h -= 10f }
        assertTrue(g.latG < -0.2f)
    }

    @Test fun stoppedReadsZero() {
        val e = GpsGEstimator()
        e.update(0, 10f, 0f); e.update(1000, 0f, 0f)
        assertEquals(0f, e.update(2000, 0f, 0f).longG, 0f)
    }

    @Test fun engineUsesGpsWhenImuSilentAndSensorWhenAlive() {
        val sink = object : TripSink {
            override fun tripStarted(key: Long, startMs: Long) = Unit
            override fun tripUpdated(key: Long, trip: com.truckdash.model.Trip, final: Boolean) = Unit
            override fun pointsAdded(key: Long, points: List<com.truckdash.model.TrackPoint>) = Unit
            override fun eventAdded(key: Long, event: com.truckdash.model.DriveEvent) = Unit
        }
        val eng = DriveEngine(sink)
        // IMU reports exact zeros the whole time; GPS shows a right turn
        var h = 0f
        for (i in 0..8) {
            val t = i * 1000L
            eng.onAcceleration(t, t, VehicleG(0f, 0f, 0f))
            eng.onLocation(t, t, 49.0, -123.0, 10.0, 15f, h, 5f)
            h += 10f
        }
        assertTrue("lat ${eng.state.value.latG}", eng.state.value.latG > 0.15f)
        // a live IMU takes over
        eng.onAcceleration(9_000, 9_000, VehicleG(0.3f, 0.0f, 0f))
        eng.onLocation(9_000, 9_000, 49.0, -123.0, 10.0, 15f, h, 5f)
        assertEquals(0.3f, eng.state.value.longG, 0.2f)
    }

    @Test fun gradeNullWhenParked() {
        val sink = object : TripSink {
            override fun tripStarted(key: Long, startMs: Long) = Unit
            override fun tripUpdated(key: Long, trip: com.truckdash.model.Trip, final: Boolean) = Unit
            override fun pointsAdded(key: Long, points: List<com.truckdash.model.TrackPoint>) = Unit
            override fun eventAdded(key: Long, event: com.truckdash.model.DriveEvent) = Unit
        }
        val eng = DriveEngine(sink)
        for (i in 0..60) eng.onLocation(i * 1000L, i * 1000L, 49.0, -123.0, 100.0 - i * 0.3, 15f, 0f, 5f)
        assertTrue(eng.state.value.gradePct != null)
        eng.onLocation(61_000, 61_000, 49.0, -123.0, 82.0, 5f, 0f, 5f)
        eng.onLocation(62_000, 62_000, 49.0, -123.0, 82.0, 0f, 0f, 5f)
        assertNull(eng.state.value.gradePct)
    }
}
