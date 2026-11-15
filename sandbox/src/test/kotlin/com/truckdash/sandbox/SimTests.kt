package com.truckdash.sandbox

import com.truckdash.core.Geo
import com.truckdash.engine.DriveEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertTrue("distance ${t.distanceM}", t.distanceM * Geo.M_TO_MI in 0.8..3.0)
        assertTrue(t.stoppedMs > 20_000)
        assertFalse(engine.state.value.recording)
        assertTrue(store.detail(t.id)!!.points.size > 100)
    }

    @Test fun highwayClimbsAndCruises() {
        val (store, _) = run(Scenarios.highway)
        val t = store.tripsFlow.value.single()
        assertEquals(72.0, t.maxSpeedMps / 0.44704, 3.0)
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
        assertEquals(0f, engine.state.value.speedMph, 0.01f)
    }

    @Test fun seededSessionHasHistory() {
        val s = SandboxSession.create(seedHistory = true, nowMs = 1_760_000_000_000L)
        assertTrue(s.store.tripsFlow.value.size >= 12)
        assertTrue(s.store.tripsFlow.value.all { it.endMs != null })
        assertEquals(5, s.store.maintFlow.value.size)
        assertTrue(s.store.fuelFlow.value.size >= 4)
    }
}
