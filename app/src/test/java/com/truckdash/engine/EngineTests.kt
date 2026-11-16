package com.truckdash.engine

import com.truckdash.core.VehicleG
import com.truckdash.model.DriveEvent
import com.truckdash.model.TrackPoint
import com.truckdash.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSink : TripSink {
    val started = mutableListOf<Long>()
    val points = mutableListOf<TrackPoint>()
    val events = mutableListOf<DriveEvent>()
    val updates = mutableListOf<Pair<Trip, Boolean>>()
    override fun tripStarted(key: Long, startMs: Long) { started += key }
    override fun pointsAdded(key: Long, points: List<TrackPoint>) { this.points += points }
    override fun eventAdded(key: Long, event: DriveEvent) { events += event }
    override fun tripUpdated(key: Long, trip: Trip, final: Boolean) { updates += trip to final }
}

class DriveEngineTest {
    private val wall0 = 1_700_000_000_000L

    private fun DriveEngine.fix(t: Long, speed: Float, lat: Double = 40.0) =
        onLocation(t, wall0 + t, lat, -105.0, 1600.0, speed, 0f, 5f)

    @Test fun driveThenParkProducesOneFinishedTrip() {
        val sink = RecordingSink()
        val e = DriveEngine(sink)
        var t = 0L
        repeat(60) { e.fix(t, 20f, 40.0 + it * 0.00018); t += 1_000 }
        assertTrue(e.state.value.recording)
        assertEquals(72f, e.state.value.speedKmh, 0.2f)
        repeat(360) { e.fix(t, 0f, 40.011); t += 1_000 }

        assertFalse(e.state.value.recording)
        assertEquals(1, sink.started.size)
        val (trip, final) = sink.updates.last()
        assertTrue(final)
        assertEquals(1_100.0, trip.distanceM, 25.0)
        assertEquals(20f, trip.maxSpeedMps, 0.01f)
        assertTrue(sink.points.isNotEmpty())
        assertTrue(trip.endMs!! > trip.startMs)
        assertEquals("trailing idle must not count as stopped time", 0L, trip.stoppedMs)
        assertTrue("no points after trip end", sink.points.all { it.tMs <= trip.endMs!! })
    }

    @Test fun hardBrakeIsRecordedOncePerCooldown() {
        val sink = RecordingSink()
        val e = DriveEngine(sink)
        var t = 0L
        repeat(10) { e.fix(t, 20f); t += 1_000 }
        assertTrue(e.state.value.recording)
        repeat(20) { e.onAcceleration(t, wall0 + t, VehicleG(-0.7f, 0f, 0f)); t += 50 }
        assertEquals(1, sink.events.count { it.type == "BRAKE" })
        assertTrue(e.state.value.longG < -0.3f)
    }

    @Test fun gpsTimeoutClearsFix() {
        val e = DriveEngine(RecordingSink())
        e.fix(0, 10f)
        assertTrue(e.state.value.hasFix)
        e.tick(3_000)
        assertTrue(e.state.value.hasFix)
        e.tick(6_000)
        assertFalse(e.state.value.hasFix)
    }

    @Test fun stopClosesOpenTrip() {
        val sink = RecordingSink()
        val e = DriveEngine(sink)
        var t = 0L
        repeat(10) { e.fix(t, 15f); t += 1_000 }
        e.stop(t, wall0 + t)
        assertTrue(sink.updates.last().second)
        assertFalse(e.state.value.recording)
    }

    @Test fun midTripStopIsKept() {
        val sink = RecordingSink()
        val e = DriveEngine(sink)
        var t = 0L
        repeat(10) { e.fix(t, 15f, 40.0 + it * 0.0001); t += 1_000 }
        repeat(30) { e.fix(t, 0f, 40.001); t += 1_000 } // red light, well under 5 min
        repeat(10) { e.fix(t, 15f, 40.001 + it * 0.0001); t += 1_000 }
        e.stop(t, wall0 + t)
        val trip = sink.updates.last().first
        assertEquals(30_000.0, trip.stoppedMs.toDouble(), 1_500.0)
        assertTrue(sink.points.count { it.speedMps == 0f } >= 29)
    }
}
