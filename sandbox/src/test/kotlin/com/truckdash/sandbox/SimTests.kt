package com.truckdash.sandbox

import com.truckdash.core.Geo
import com.truckdash.core.SpeedLock
import com.truckdash.engine.DriveEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SimTests {
    private fun run(s: Scenario): Pair<MemoryStore, DriveEngine> {
        val store = MemoryStore()
        val engine = DriveEngine(store.newSink())
        val c = SimController(engine, wallStartMs = 1_700_000_000_000L)
        c.startScenario(s)
        c.runFor(s.totalS + 10)
        return store to engine
    }

    @Test fun cityLoopMakesOneCompletedTrip() {
        val (store, engine) = run(Scenarios.cityLoop)
        val trips = store.tripsFlow.value
        assertEquals(1, trips.size)
        val t = trips[0]
        assertTrue("trip should end", t.endMs != null)
        assertTrue("distance ${t.distanceM}", t.distanceM * Geo.M_TO_KM in 1.5..5.0)
        assertTrue(t.stoppedMs > 20_000)
        assertFalse(engine.state.value.recording)
        assertTrue(store.detail(t.id)!!.points.size > 100)
    }

    @Test fun highwayClimbsAndCruises() {
        val (store, _) = run(Scenarios.highway)
        val t = store.tripsFlow.value.single()
        assertEquals(110.0, t.maxSpeedMps * 3.6, 4.0)
        assertTrue("elev gain ${t.elevGainM}", t.elevGainM > 20f)
    }

    @Test fun hardDemoRecordsBrakeAccelAndCornerEvents() {
        val (store, _) = run(Scenarios.hardEvents)
        val t = store.tripsFlow.value.single()
        assertTrue("brake ${t.harshBrake}", t.harshBrake >= 1)
        assertTrue("accel ${t.harshAccel}", t.harshAccel >= 1)
        assertTrue("corner ${t.harshCorner}", t.harshCorner >= 1)
        assertTrue(store.detail(t.id)!!.events.isNotEmpty())
    }

    @Test fun parkedNeverRecords() {
        val (store, engine) = run(Scenarios.parked.let { Scenario(it.name, it.blurb, listOf(Seg(120.0, 0.0))) })
        assertTrue(store.tripsFlow.value.isEmpty())
        assertTrue(engine.state.value.hasFix)
        assertEquals(0f, engine.state.value.speedKmh, 0.01f)
    }

    @Test fun seededSessionHasHistory() {
        val s = SandboxSession.create(seedHistory = true, nowMs = 1_760_000_000_000L)
        assertTrue(s.store.tripsFlow.value.size >= 12)
        assertTrue(s.store.tripsFlow.value.all { it.endMs != null })
        assertEquals(5, s.store.maintFlow.value.size)
        assertTrue(s.store.fuelFlow.value.size >= 4)
    }

    @Test fun drivingLockIsOffByDefaultAndTogglesThroughTheRepo() {
        val s = SandboxSession.create(seedHistory = false, nowMs = 1_700_000_000_000L)
        s.controller.startScenario(Scenarios.cityLoop)
        s.controller.runFor(100.0)
        val kmh = s.engine.state.value.speedKmh
        assertTrue("should be moving, was $kmh", kmh > 20f)
        val st = s.repo.settings.value
        assertFalse(st.drivingLock)
        assertFalse(SpeedLock.isLocked(kmh, st.drivingLock, st.passengerMode))   // moving, lock off: never locked
        s.repo.setDrivingLock(true)
        val on = s.repo.settings.value
        assertTrue(on.drivingLock)
        assertTrue(SpeedLock.isLocked(kmh, on.drivingLock, on.passengerMode))
        s.repo.setPassengerMode(true)
        assertFalse(SpeedLock.isLocked(kmh, true, s.repo.settings.value.passengerMode))
    }

    @Test fun odometerSurvivesMidTripSetAndTripDeletion() = runBlocking {
        val s = SandboxSession.create(seedHistory = false, nowMs = 1_700_000_000_000L)
        s.controller.startScenario(Scenarios.cityLoop)
        s.controller.runFor(120.0)                                   // mid-trip
        val kmAtSet = s.engine.state.value.tripKm
        assertTrue(s.engine.state.value.recording)
        s.repo.setOdometer(1_000.0)                                  // user enters the odometer at a stop
        s.controller.runFor(Scenarios.cityLoop.totalS)               // finish the trip
        val tripKm = s.store.tripsFlow.value.single().distanceM * Geo.M_TO_KM
        val odo = s.repo.odometerKm.first()
        assertEquals("rest of the in-progress trip must still count", 1_000.0 + (tripKm - kmAtSet), odo, 0.15)
        // deleting the trip must not change the odometer
        s.repo.deleteTrip(s.store.tripsFlow.value.single().id)
        assertEquals(0, s.store.tripsFlow.value.size)
        assertEquals(odo, s.repo.odometerKm.first(), 1e-9)
    }

    @Test fun cannotDeleteTheTripThatIsBeingRecorded() = runBlocking {
        val s = SandboxSession.create(seedHistory = false)
        s.controller.startScenario(Scenarios.cityLoop)
        s.controller.runFor(60.0)
        assertTrue(s.engine.state.value.recording)
        s.repo.deleteTrip(s.store.tripsFlow.value.single().id)
        assertEquals(1, s.store.tripsFlow.value.size)
    }

    @Test fun firstOdometerEntryRebasesUntouchedMaintenance() = runBlocking {
        val s = SandboxSession.create(seedHistory = true, nowMs = 1_760_000_000_000L)
        s.store.maintFlow.value = listOf(
            com.truckdash.model.MaintItem(1, "Oil", 8000, 180, 0.0, 0L),
            com.truckdash.model.MaintItem(2, "Done once", 8000, 180, 5_000.0, 0L),
        )
        s.repo.setOdometer(142_000.0)
        val items = s.store.maintFlow.value
        assertEquals(142_000.0, items[0].lastOdoKm, 0.001)
        assertEquals(5_000.0, items[1].lastOdoKm, 0.001)
    }
}
