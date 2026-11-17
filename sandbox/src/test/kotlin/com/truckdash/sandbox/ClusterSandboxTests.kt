package com.truckdash.sandbox

import com.truckdash.obd.ClusterPhase
import com.truckdash.obd.GaugeId
import com.truckdash.obd.ObdFailure
import com.truckdash.obd.ObdSession
import com.truckdash.obd.TransStage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The real [ObdSession] + protocol parser against the simulated ELM327 (all the scenarios of the sandbox control panel). */
@OptIn(ExperimentalCoroutinesApi::class)
class ClusterSandboxTests {
    private fun TestScope.run(d: FakeDongle): ObdSession {
        val s = ObdSession(d::newLink)
        backgroundScope.launch { s.run() }
        return s
    }

    private suspend fun TestScope.seconds(n: Int) { advanceTimeBy(n * 1000L); runCurrent() }

    @Test fun healthyDongleGoesLiveWithPlausibleMetricValues() = runTest {
        val d = FakeDongle(DongleScenario.Healthy)
        val s = run(d)
        seconds(4)
        val v = s.state.value
        assertEquals(ClusterPhase.Live, v.phase)
        assertEquals("V-LINK", v.device)
        for (id in GaugeId.entries) assertNotNull(id.name, v.value(id))
        assertTrue(v.value(GaugeId.Trans)!! in 55f..115f)
        assertTrue(v.value(GaugeId.Load)!! in 0f..100f)
        assertTrue(v.value(GaugeId.Throttle)!! in 0f..100f)
        assertTrue(v.value(GaugeId.Intake)!! in 15f..40f)
    }

    @Test fun lineNoiseInAHealthyRunNeverEscalatesTheTransStage() = runTest {
        val s = run(FakeDongle(DongleScenario.Healthy))
        seconds(90)
        assertEquals(ClusterPhase.Live, s.state.value.phase)
        assertEquals(TransStage.Functional, s.state.value.transStage)
        assertNotNull(s.state.value.value(GaugeId.Trans))
    }

    @Test fun absentDongleFailsWithAReasonAndRetriesEveryFiveSeconds() = runTest {
        val d = FakeDongle(DongleScenario.Absent)
        val s = run(d)
        seconds(1)
        assertEquals(ClusterPhase.Failed, s.state.value.phase)
        assertEquals(ObdFailure.ConnectionFailed, s.state.value.failure)
        assertEquals(1, d.opens)
        seconds(11)
        assertEquals(3, d.opens)          // 0 s, about 5.3 s, about 10.6 s
        assertEquals(ClusterPhase.Failed, s.state.value.phase)
    }

    @Test fun absentThenPluggedInRecovers() = runTest {
        val d = FakeDongle(DongleScenario.Absent)
        val s = run(d)
        seconds(2)
        d.select(DongleScenario.Healthy)
        seconds(8)
        assertEquals(ClusterPhase.Live, s.state.value.phase)
        assertNull(s.state.value.failure)
    }

    @Test fun droppedLinkShowsLostThenReconnectsOnItsOwn() = runTest {
        val d = FakeDongle(DongleScenario.Drops)
        val s = run(d)
        seconds(3)
        assertEquals(ClusterPhase.Failed, s.state.value.phase)
        assertEquals(ObdFailure.LinkLost, s.state.value.failure)
        seconds(8)
        assertEquals(ClusterPhase.Live, s.state.value.phase)
        assertEquals(2, d.opens)
        seconds(20)
        assertEquals(ClusterPhase.Live, s.state.value.phase)      // dropped once, then stays up
        assertEquals(2, d.opens)
    }

    @Test fun silentTransGoesFunctionalPhysicalThenCoolant() = runTest {
        val s = run(FakeDongle(DongleScenario.TransSilent))
        seconds(1)
        assertEquals(TransStage.Functional, s.state.value.transStage)
        var sawPhysical = false
        repeat(30) {
            advanceTimeBy(200); runCurrent()
            if (s.state.value.transStage == TransStage.Physical) sawPhysical = true
        }
        assertTrue("passed through the physical stage", sawPhysical)
        assertEquals(TransStage.Fallback, s.state.value.transStage)
        val coolant = s.state.value.value(GaugeId.Trans)
        assertNotNull(coolant)
        assertTrue("coolant about 88 degC, was $coolant", coolant!! in 80f..96f)
    }

    @Test fun tcmOnlyTransStaysInThePhysicalStageAndReadsTemperature() = runTest {
        val s = run(FakeDongle(DongleScenario.TransTcmOnly))
        seconds(30)
        assertEquals(TransStage.Physical, s.state.value.transStage)
        val t = s.state.value.value(GaugeId.Trans)
        assertNotNull(t)
        assertTrue(t!! in 60f..110f)
    }

    @Test fun clusterTabIsHiddenByDefaultInTheSandboxToo() {
        val s = SandboxSession.create(seedHistory = false, nowMs = 1_700_000_000_000L)
        assertFalse(s.repo.settings.value.showCluster)
        assertNull(s.repo.settings.value.obdAddress)
        assertEquals(0, s.dongle.opens)
        s.repo.setShowCluster(true)
        assertTrue(s.repo.settings.value.showCluster)
        s.repo.setObdAddress("00:1D:A5:68:98:8B")
        assertEquals("00:1D:A5:68:98:8B", s.repo.settings.value.obdAddress)
        s.repo.setObdAddress(null)
        assertNull(s.repo.settings.value.obdAddress)
        assertEquals(0, s.dongle.opens)   // choosing settings never touches Bluetooth
    }
}
