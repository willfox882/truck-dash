package com.truckdash.core

import com.truckdash.data.FuelLogEntity
import com.truckdash.data.MaintenanceItemEntity
import com.truckdash.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneOffset

class GForceTest {
    // Phone upright, screen facing driver: up = +Y, forward = -Z, right = +X.
    private val up = Vec3(0f, 9.81f, 0f)

    @Test fun forwardAcceleration() {
        val g = GForce.toVehicle(up, Vec3(0f, 0f, -1.96f))!!
        assertEquals(0.2f, g.longG, 0.01f)
        assertEquals(0f, g.latG, 0.01f)
    }

    @Test fun rightTurnIsPositiveLat() {
        val g = GForce.toVehicle(up, Vec3(3.92f, 0f, 0f))!!
        assertEquals(0.4f, g.latG, 0.01f)
    }

    @Test fun brakingIsNegativeLong() {
        val g = GForce.toVehicle(up, Vec3(0f, 0f, 3.43f))!!
        assertTrue(g.longG < -0.3f)
    }

    @Test fun tiltedBackMountStillWorks() {
        // screen tilted back 20deg: gravity gains a +Z component
        val s = Math.sin(Math.toRadians(20.0)).toFloat()
        val c = Math.cos(Math.toRadians(20.0)).toFloat()
        val grav = Vec3(0f, 9.81f * c, 9.81f * s)
        // forward accel of 2 m/s^2 along the horizontal forward direction
        val fwd = Vec3(0f, s, -c) // horizontal forward in device frame (perpendicular to gravity)
        val g = GForce.toVehicle(grav, fwd * 2f)!!
        assertEquals(2f / 9.80665f, g.longG, 0.01f)
        assertEquals(0f, g.vertG, 0.01f)
    }

    @Test fun flatDeviceHasNoForward() {
        assertNull(GForce.toVehicle(Vec3(0f, 0f, 9.81f), Vec3(1f, 0f, 0f)))
    }
}

class TripSegmenterTest {
    @Test fun startsAfterHoldAndEndsAfterIdle() {
        val s = TripSegmenter(startHoldMs = 3_000, endIdleMs = 10_000)
        var t = 0L
        var started = false
        repeat(5) { if (s.update(t, 10f) == SegEvent.START) started = true; t += 1_000 }
        assertTrue(started)
        assertTrue(s.active)
        // stop at 20s; end at 30s
        var ended = false
        repeat(15) { if (s.update(t, 0f) == SegEvent.END) ended = true; t += 1_000 }
        assertTrue(ended)
        assertEquals(5_000L, s.endedAtMs)
    }

    @Test fun briefBlipDoesNotStart() {
        val s = TripSegmenter(startHoldMs = 5_000)
        assertEquals(SegEvent.NONE, s.update(0, 10f))
        assertEquals(SegEvent.NONE, s.update(1_000, 0f))
        assertEquals(SegEvent.NONE, s.update(2_000, 10f))
        assertEquals(false, s.active)
    }

    @Test fun shortStopDoesNotEndTrip() {
        val s = TripSegmenter(startHoldMs = 0, endIdleMs = 60_000)
        s.update(0, 10f); s.update(1, 10f)
        assertTrue(s.active)
        s.update(10_000, 0f)
        s.update(40_000, 0f)
        s.update(41_000, 10f)
        assertTrue(s.active)
    }
}

class HarshEventDetectorTest {
    @Test fun detectsBrakeAndCooldown() {
        val d = HarshEventDetector(cooldownMs = 3_000)
        assertEquals(HarshType.BRAKE, d.update(0, -0.5f, 0f, 20f))
        assertNull(d.update(1_000, -0.5f, 0f, 20f))
        assertEquals(HarshType.BRAKE, d.update(4_000, -0.5f, 0f, 20f))
    }

    @Test fun ignoresAtLowSpeed() {
        assertNull(HarshEventDetector().update(0, -0.9f, 0f, 1f))
    }

    @Test fun cornerDetected() {
        assertEquals(HarshType.CORNER, HarshEventDetector().update(0, 0f, -0.5f, 15f))
    }
}

class ElevationTest {
    @Test fun jitterIgnoredClimbCounted() {
        val e = ElevationAccumulator(3.0)
        listOf(100.0, 101.0, 99.5, 100.5, 104.0, 108.0, 107.0, 111.0).forEach(e::add)
        assertEquals(11.0, e.gainM, 0.01)
    }

    @Test fun gradeUphill() {
        val g = GradeEstimator(100.0)
        var last: Float? = null
        for (i in 1..40) last = g.add(10.0, 100.0 + i * 0.5) // 5 m rise / 100 m = 5%
        assertNotNull(last)
        assertEquals(5f, last!!, 0.3f)
    }
}

class MpgCalcTest {
    private fun f(odo: Double, gal: Double, full: Boolean = true) =
        FuelLogEntity(dateMs = odo.toLong(), odometerMi = odo, gallons = gal, pricePerGal = 3.0, fullTank = full)

    @Test fun fillToFill() {
        val s = MpgCalc.compute(listOf(f(1000.0, 15.0), f(1300.0, 15.0), f(1600.0, 20.0)))
        assertEquals(2, s.points.size)
        assertEquals(20.0, s.points[0].mpg, 0.01)
        assertEquals(15.0, s.points[1].mpg, 0.01)
        assertEquals(600.0 / 35.0, s.avgMpg!!, 0.01)
        assertEquals(3.0 * 35.0 / 600.0, s.costPerMile!!, 0.001)
    }

    @Test fun partialFillsFoldIntoNextFull() {
        val s = MpgCalc.compute(listOf(f(0.0, 10.0), f(100.0, 5.0, full = false), f(200.0, 5.0)))
        assertEquals(1, s.points.size)
        assertEquals(20.0, s.points[0].mpg, 0.01)
    }

    @Test fun needsTwoFulls() {
        assertNull(MpgCalc.compute(listOf(f(0.0, 10.0))).avgMpg)
    }
}

class MaintenanceCalcTest {
    private val day = 86_400_000L

    @Test fun okSoonOverdue() {
        val item = MaintenanceItemEntity(name = "Oil", intervalMiles = 1000, lastOdoMi = 0.0, lastDateMs = 0)
        assertEquals(MaintLevel.OK, MaintenanceCalc.status(item, 500.0, 0).level)
        assertEquals(MaintLevel.SOON, MaintenanceCalc.status(item, 900.0, 0).level)
        val over = MaintenanceCalc.status(item, 1100.0, 0)
        assertEquals(MaintLevel.OVERDUE, over.level)
        assertEquals(-100.0, over.milesLeft!!, 0.01)
    }

    @Test fun worstOfMilesAndDays() {
        val item = MaintenanceItemEntity(name = "X", intervalMiles = 10_000, intervalDays = 100, lastOdoMi = 0.0, lastDateMs = 0)
        val st = MaintenanceCalc.status(item, 100.0, 120 * day)
        assertEquals(MaintLevel.OVERDUE, st.level)
        assertEquals(-20L, st.daysLeft)
    }
}

class AnalyticsTest {
    @Test fun milesByMonthBuckets() {
        val z = ZoneOffset.UTC
        fun ms(y: Int, m: Int, d: Int) = java.time.LocalDate.of(y, m, d).atStartOfDay(z).toInstant().toEpochMilli()
        val trips = listOf(
            TripEntity(startMs = ms(2026, 11, 2), distanceM = 1609.344 * 10),
            TripEntity(startMs = ms(2026, 11, 9), distanceM = 1609.344 * 5),
            TripEntity(startMs = ms(2026, 9, 20), distanceM = 1609.344 * 7),
        )
        val r = Analytics.milesByMonth(trips, ms(2026, 11, 15), months = 3, zone = z)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), r.map { it.first })
        assertEquals(7.0, r[0].second, 0.01)
        assertEquals(0.0, r[1].second, 0.01)
        assertEquals(15.0, r[2].second, 0.01)
    }
}

class GeoTest {
    @Test fun compass() {
        assertEquals("N", Geo.compassLabel(0f))
        assertEquals("N", Geo.compassLabel(359f))
        assertEquals("E", Geo.compassLabel(90f))
        assertEquals("SW", Geo.compassLabel(225f))
    }

    @Test fun haversineOneDegreeLat() {
        assertEquals(111_195.0, Geo.haversineM(0.0, 0.0, 1.0, 0.0), 100.0)
    }
}
