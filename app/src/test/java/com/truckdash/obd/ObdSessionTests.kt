package com.truckdash.obd

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

@OptIn(ExperimentalCoroutinesApi::class)
class ObdSessionTests {
    private class Rig(
        val links: MutableList<ScriptLink> = mutableListOf(),
        val log: MutableList<String> = mutableListOf(),
        /** one handler per connection attempt; the last one repeats */
        val handlers: MutableList<(String) -> String?> = mutableListOf(),
        val openFailures: MutableList<ObdFailure?> = mutableListOf(),
        val openDelayMs: Long = 0,
    ) {
        fun factory(): () -> ObdLink = {
            val i = links.size
            val link = ScriptLink(
                log, openFailures.getOrElse(i) { openFailures.lastOrNull() }, openDelayMs,
                handlers.getOrElse(i) { handlers.last() },
            )
            links += link
            link
        }
    }

    private fun TestScope.start(rig: Rig): Pair<ObdSession, Job> {
        val s = ObdSession(rig.factory())
        return s to backgroundScope.launch { s.run() }
    }

    private val healthy: (String) -> String? = { healthyReply(it) }

    /** time at which the first poll cycle is done: 5 init commands x 100 ms */
    private val INIT_MS = 500L

    @Test fun initSequenceThenPollOrder() = runTest {
        val rig = Rig(handlers = mutableListOf(healthy))
        val (s, _) = start(rig)
        advanceTimeBy(INIT_MS + 10); runCurrent()
        assertEquals(listOf("open", "ATZ", "ATE0", "ATL0", "ATS0", "ATSP6"), rig.log.take(6))
        assertEquals(listOf("0104", "0111", "010F", "221940"), rig.log.drop(6).take(4))
        assertEquals(ClusterPhase.Live, s.state.value.phase)
    }

    @Test fun liveValuesAreConvertedToMetric() = runTest {
        val rig = Rig(handlers = mutableListOf(healthy))
        val (s, _) = start(rig)
        advanceTimeBy(INIT_MS + 60); runCurrent()
        val v = s.state.value
        assertEquals(100f, v.value(GaugeId.Load)!!, 0.01f)
        assertEquals(49.8f, v.value(GaugeId.Throttle)!!, 0.1f)
        assertEquals(30f, v.value(GaugeId.Intake)!!, 0f)
        assertEquals(85f, v.value(GaugeId.Trans)!!, 0f)
        assertEquals(TransStage.Functional, v.transStage)
        assertEquals("TEST-OBD", v.device)
        assertNull(v.failure)
    }

    @Test fun noiseAndGarbageNeverBecomeValues() = runTest {
        val rig = Rig(handlers = mutableListOf({ c ->
            when (c) {
                "0104" -> "SEARCHING...\r41 04 80\r\r"
                "0111" -> "NO DATA\r\r"
                "010F" -> "?\r\r"
                else -> healthyReply(c)
            }
        }))
        val (s, _) = start(rig)
        advanceTimeBy(INIT_MS + 200); runCurrent()
        val v = s.state.value
        assertEquals(50.2f, v.value(GaugeId.Load)!!, 0.1f)
        assertNull(v.value(GaugeId.Throttle))
        assertNull(v.value(GaugeId.Intake))
    }

    @Test fun staleValueIsDroppedAfterTenMisses() = runTest {
        var calls = 0
        val rig = Rig(handlers = mutableListOf({ c ->
            if (c == "0111") { calls++; if (calls <= 1) "41117F\r\r" else "NO DATA\r\r" } else healthyReply(c)
        }))
        val (s, _) = start(rig)
        advanceTimeBy(INIT_MS + 60); runCurrent()
        assertNotNull(s.state.value.value(GaugeId.Throttle))     // answered once, then 1 miss
        advanceTimeBy(400); runCurrent()
        assertNotNull(s.state.value.value(GaugeId.Throttle))     // 9 misses: still shown
        advanceTimeBy(100); runCurrent()
        assertNull(s.state.value.value(GaugeId.Throttle))        // 10+ misses: shows "--"
    }

    // ---- staged transmission temperature --------------------------------------------------------------------------------

    private val transSilent: (String) -> String? = { c -> if (c == "221940") "NO DATA\r\r" else healthyReply(c) }

    @Test fun silentTransGoesFunctionalThenPhysicalThenCoolant() = runTest {
        val rig = Rig(handlers = mutableListOf(transSilent))
        val (s, _) = start(rig)
        // cycles 1..7: still functional (needs 8 misses)
        advanceTimeBy(INIT_MS + 50L * 6); runCurrent()
        assertEquals(TransStage.Functional, s.state.value.transStage)
        advanceTimeBy(60); runCurrent()
        assertEquals(TransStage.Physical, s.state.value.transStage)
        // 8 more misses addressed to the TCM
        advanceTimeBy(50L * 7); runCurrent()
        assertEquals(TransStage.Physical, s.state.value.transStage)
        advanceTimeBy(60); runCurrent()
        assertEquals(TransStage.Fallback, s.state.value.transStage)
        advanceTimeBy(200); runCurrent()
        assertEquals(50f, s.state.value.value(GaugeId.Trans)!!, 0f)   // coolant 0105 now fills the Trans slot
        assertEquals("0105", rig.log.last { it == "0105" || it == "221940" })
    }

    @Test fun physicalStageAddressesTheTcmAndRestoresBroadcastAroundEachRequest() = runTest {
        val rig = Rig(handlers = mutableListOf(transSilent))
        val (s, _) = start(rig)
        advanceTimeBy(INIT_MS + 50L * 8 + 60); runCurrent()
        assertEquals(TransStage.Physical, s.state.value.transStage)
        advanceTimeBy(50L * 3); runCurrent()
        val idx = rig.log.indexOf("ATSH7E1")
        assertTrue(idx > 0)
        assertEquals(listOf("ATSH7E1", "221940", "ATSH7DF"), rig.log.subList(idx, idx + 3))
    }

    @Test fun physicalStageThatAnswersStaysThere() = runTest {
        // only answers when addressed to the TCM
        var header = "7DF"
        val rig = Rig(handlers = mutableListOf({ c ->
            when (c) {
                "ATSH7E1" -> { header = "7E1"; "OK\r\r" }
                "ATSH7DF" -> { header = "7DF"; "OK\r\r" }
                "221940" -> if (header == "7E1") "6219407D\r\r" else "NO DATA\r\r"
                else -> healthyReply(c)
            }
        }))
        val (s, _) = start(rig)
        advanceTimeBy(INIT_MS + 50L * 60); runCurrent()
        assertEquals(TransStage.Physical, s.state.value.transStage)
        assertEquals(85f, s.state.value.value(GaugeId.Trans)!!, 0f)
        assertFalse(rig.log.contains("0105"))
    }

    @Test fun missesOnlyCountWhenTheOtherPidsAnswer() = runTest {
        // the whole link is bad: every PID says NO DATA, so trans misses must not escalate
        val rig = Rig(handlers = mutableListOf({ c -> if (c.startsWith("AT")) healthyReply(c) else "NO DATA\r\r" }))
        val (s, _) = start(rig)
        advanceTimeBy(INIT_MS + 50L * 60); runCurrent()
        assertEquals(TransStage.Functional, s.state.value.transStage)
        assertFalse(rig.log.contains("ATSH7E1"))
    }

    @Test fun aStageThatAnsweredOnceIsNeverEscalatedAwayFrom() = runTest {
        var answered = false
        val rig = Rig(handlers = mutableListOf({ c ->
            if (c == "221940") { if (!answered) { answered = true; "6219407D\r\r" } else "NO DATA\r\r" } else healthyReply(c)
        }))
        val (s, _) = start(rig)
        advanceTimeBy(INIT_MS + 50L * 100); runCurrent()
        assertEquals(TransStage.Functional, s.state.value.transStage)
        assertFalse(rig.log.contains("ATSH7E1"))
        assertFalse(rig.log.contains("0105"))
    }

    @Test fun stageSurvivesReconnects() = runTest {
        var dropNow = false
        val first: (String) -> String? = { c -> if (dropNow) throw ObdLinkException(ObdFailure.LinkLost) else transSilent(c) }
        val rig = Rig(handlers = mutableListOf(first, transSilent))
        val (s, _) = start(rig)
        advanceTimeBy(INIT_MS + 50L * 9); runCurrent()
        assertEquals(TransStage.Physical, s.state.value.transStage)
        dropNow = true
        advanceTimeBy(100); runCurrent()
        assertEquals(ClusterPhase.Failed, s.state.value.phase)
        assertEquals(TransStage.Physical, s.state.value.transStage)
        val before = rig.log.size
        advanceTimeBy(5000 + INIT_MS + 120); runCurrent()
        assertEquals(2, rig.links.size)
        // the new connection goes straight to addressing the TCM, it does not start over at functional
        val after = rig.log.drop(before)
        assertTrue(after.contains("ATSH7E1"))
        assertEquals(TransStage.Physical, s.state.value.transStage)
    }

    @Test fun fallbackIsPermanent() = runTest {
        var drop = false
        val rig = Rig(handlers = mutableListOf({ c -> if (drop) throw ObdLinkException(ObdFailure.LinkLost) else transSilent(c) }, transSilent))
        val (s, _) = start(rig)
        advanceTimeBy(INIT_MS + 50L * 18); runCurrent()
        assertEquals(TransStage.Fallback, s.state.value.transStage)
        drop = true
        advanceTimeBy(100 + 5000 + INIT_MS + 200); runCurrent()
        assertEquals(TransStage.Fallback, s.state.value.transStage)
        assertFalse(rig.log.drop(rig.log.indexOfLast { it == "open" }).contains("221940"))
    }

    @Test fun physicalStageThatAnswersShowsConfirmedAndTheFlagSurvivesReconnects() = runTest {
        var header = "7DF"
        var drop = false
        val h: (String) -> String? = { c ->
            when {
                drop -> throw ObdLinkException(ObdFailure.LinkLost)
                c == "ATSH7E1" -> { header = "7E1"; "OK\r\r" }
                c == "ATSH7DF" -> { header = "7DF"; "OK\r\r" }
                c == "221940" -> if (header == "7E1") "6219407D\r\r" else "NO DATA\r\r"
                else -> healthyReply(c)
            }
        }
        val rig = Rig(handlers = mutableListOf(h))
        val (s, _) = start(rig)
        advanceTimeBy(INIT_MS + 50L * 7 + 10); runCurrent()   // the 8th miss has just moved it to the physical stage
        assertEquals(TransStage.Physical, s.state.value.transStage)
        assertFalse(s.state.value.transConfirmed)             // still asking
        advanceTimeBy(50L * 6); runCurrent()
        assertTrue(s.state.value.transConfirmed)
        assertEquals(ClusterText.Tone.Ok, ClusterText.status(s.state.value).tone)
        drop = true; advanceTimeBy(100); runCurrent()
        assertTrue(s.state.value.transConfirmed)              // kept while reconnecting
    }

    @Test fun anAnsweringFunctionalStageIsConfirmedToo() = runTest {
        val rig = Rig(handlers = mutableListOf(healthy))
        val (s, _) = start(rig)
        advanceTimeBy(INIT_MS + 60); runCurrent()
        assertTrue(s.state.value.transConfirmed)
        assertEquals(TransStage.Functional, s.state.value.transStage)
    }

    @Test fun restoringTheBroadcastAddressWithoutOkReconnects() = runTest {
        var restoreFails = false
        val h: (String) -> String? = { c ->
            when {
                c == "ATSH7DF" -> if (restoreFails) "?\r\r" else "OK\r\r"
                else -> transSilent(c)
            }
        }
        val rig = Rig(handlers = mutableListOf(h, healthy))
        val (s, _) = start(rig)
        advanceTimeBy(INIT_MS + 50L * 9); runCurrent()
        assertEquals(TransStage.Physical, s.state.value.transStage)
        restoreFails = true
        advanceTimeBy(100); runCurrent()
        assertEquals(ClusterPhase.Failed, s.state.value.phase)
        assertEquals(ObdFailure.LinkLost, s.state.value.failure)
        assertTrue(rig.links[0].closed)
        advanceTimeBy(5000 + INIT_MS + 100); runCurrent()
        assertEquals(2, rig.links.size)
        assertEquals("ATZ", rig.log.drop(rig.log.indexOfLast { it == "open" } + 1).first())   // a clean start, ATZ first
    }

    // ---- connection lifecycle ------------------------------------------------------------------------------------------

    @Test fun everyOpenFailureShowsItsReasonAndRetriesEveryFiveSeconds() = runTest {
        for (f in listOf(ObdFailure.BluetoothOff, ObdFailure.NoAdapter, ObdFailure.NoPairedDevice, ObdFailure.PermissionNeeded, ObdFailure.ConnectionFailed)) {
            val rig = Rig(handlers = mutableListOf(healthy), openFailures = mutableListOf(f))
            val (s, job) = start(rig)
            runCurrent()
            assertEquals(f.name, ClusterPhase.Failed, s.state.value.phase)
            assertEquals(f, s.state.value.failure)
            assertEquals(1, rig.links.size)
            advanceTimeBy(4_900); runCurrent()
            assertEquals(1, rig.links.size)
            advanceTimeBy(200); runCurrent()
            assertEquals(2, rig.links.size)
            advanceTimeBy(5_000); runCurrent()
            assertEquals(3, rig.links.size)
            job.cancel()
        }
    }

    @Test fun retryNowSkipsTheWait() = runTest {
        val rig = Rig(handlers = mutableListOf(healthy), openFailures = mutableListOf(ObdFailure.ConnectionFailed))
        val (s, _) = start(rig)
        runCurrent()
        assertEquals(1, rig.links.size)
        s.retryNow(); runCurrent()
        assertEquals(2, rig.links.size)
    }

    @Test fun recoversWhenTheAdapterComesBack() = runTest {
        val rig = Rig(handlers = mutableListOf(healthy), openFailures = mutableListOf(ObdFailure.ConnectionFailed, null))
        val (s, _) = start(rig)
        runCurrent()
        assertEquals(ClusterPhase.Failed, s.state.value.phase)
        advanceTimeBy(5_000 + INIT_MS + 100); runCurrent()
        assertEquals(ClusterPhase.Live, s.state.value.phase)
        assertNull(s.state.value.failure)
        assertEquals(85f, s.state.value.value(GaugeId.Trans)!!, 0f)
    }

    @Test fun failureStaysOnScreenWhileRetrying() = runTest {
        val rig = Rig(handlers = mutableListOf(healthy), openFailures = mutableListOf(ObdFailure.ConnectionFailed), openDelayMs = 3_000)
        val (s, _) = start(rig)
        advanceTimeBy(3_100); runCurrent()
        assertEquals(ClusterPhase.Failed, s.state.value.phase)
        assertFalse(s.state.value.retrying)
        advanceTimeBy(5_000); runCurrent()                 // second attempt is now in its 3 s connect
        assertEquals(ClusterPhase.Failed, s.state.value.phase)
        assertTrue(s.state.value.retrying)
        assertEquals(ObdFailure.ConnectionFailed, s.state.value.failure)
    }

    @Test fun adapterThatNeverAnswersInitIsAnInitFailure() = runTest {
        val rig = Rig(handlers = mutableListOf({ _ -> null }))
        val (s, _) = start(rig)
        runCurrent()
        assertEquals(ClusterPhase.Failed, s.state.value.phase)
        assertEquals(ObdFailure.InitFailed, s.state.value.failure)
        assertTrue(rig.links[0].closed)
    }

    @Test fun initRepliesAreChecked() = runTest {
        // ATZ without the ELM banner, then an adapter that refuses ATE0: both are init failures that reconnect
        for (bad in listOf("ATZ" to "OK\r\r", "ATE0" to "?\r\r", "ATSP6" to "ERROR\r\r")) {
            val rig = Rig(handlers = mutableListOf({ c -> if (c == bad.first) bad.second else healthyReply(c) }, healthy))
            val (s, _) = start(rig)
            advanceTimeBy(INIT_MS + 50); runCurrent()
            assertEquals(bad.first, ClusterPhase.Failed, s.state.value.phase)
            assertEquals(bad.first, ObdFailure.InitFailed, s.state.value.failure)
            assertTrue(rig.links[0].closed)
            assertFalse(bad.first, rig.log.contains("0104"))             // never polled
            advanceTimeBy(5_000 + INIT_MS + 100); runCurrent()
            assertEquals(bad.first, 2, rig.links.size)
            assertEquals(bad.first, ClusterPhase.Live, s.state.value.phase)
        }
    }

    @Test fun aStaleRetryFromBeforeTheAttemptDoesNotCutTheNextWait() = runTest {
        val rig = Rig(handlers = mutableListOf(healthy), openFailures = mutableListOf(ObdFailure.ConnectionFailed))
        val s = ObdSession(rig.factory())
        s.retryNow(); s.retryNow()                                       // pressed while nothing was running
        backgroundScope.launch { s.run() }
        runCurrent()
        assertEquals(1, rig.links.size)
        advanceTimeBy(4_900); runCurrent()
        assertEquals(1, rig.links.size)                                  // still waiting the full 5 s
        advanceTimeBy(200); runCurrent()
        assertEquals(2, rig.links.size)
    }

    @Test fun timeoutOrErrorWhilePollingIsALostLinkAndReconnects() = runTest {
        var cycles = 0
        val rig = Rig(handlers = mutableListOf({ c -> if (c == "0111" && ++cycles == 3) null else healthyReply(c) }, healthy))
        val (s, _) = start(rig)
        advanceTimeBy(INIT_MS + 50L * 3 + 20); runCurrent()
        assertEquals(ClusterPhase.Failed, s.state.value.phase)
        assertEquals(ObdFailure.LinkLost, s.state.value.failure)
        assertTrue(s.state.value.values.isEmpty())
        advanceTimeBy(5_000 + INIT_MS + 100); runCurrent()
        assertEquals(ClusterPhase.Live, s.state.value.phase)
    }

    @Test fun cancellingDisconnectsAndResetsToIdle() = runTest {
        val rig = Rig(handlers = mutableListOf(healthy))
        val (s, job) = start(rig)
        advanceTimeBy(INIT_MS + 100); runCurrent()
        assertEquals(ClusterPhase.Live, s.state.value.phase)
        job.cancel(); runCurrent()
        assertTrue(rig.links[0].closed)
        assertEquals(ClusterPhase.Idle, s.state.value.phase)
        assertTrue(s.state.value.values.isEmpty())
        val n = rig.log.size
        advanceTimeBy(10_000); runCurrent()
        assertEquals(n, rig.log.size)                       // nothing more is sent
    }
}
