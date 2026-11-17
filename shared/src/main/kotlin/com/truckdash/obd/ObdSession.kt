package com.truckdash.obd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Connect, initialise, poll, reconnect. Run [run] in a coroutine; cancel it to disconnect (the link is closed).
 *
 * Behaviour (ported from the owner's OBD-Gage-Cluster app):
 *  - init ATZ ATE0 ATL0 ATS0 ATSP6, each waiting for the '>' prompt;
 *  - poll 0104, 0111, 010F, then transmission temperature, about every 50 ms between cycles;
 *  - any error: close, wait [reconnectDelayMs] (a Retry press cuts the wait short), try again, forever;
 *  - transmission temperature (GM mode 22 `221940`) is staged: functional (broadcast), then physical (ATSH7E1 before and
 *    ATSH7DF after each request), then permanently coolant temperature (`0105`) relabelled.
 *    Guards: a miss only counts when another PID answered in the same cycle (a flaky link never escalates), a stage that
 *    answered once is never escalated away from, and the stage persists across reconnects (it lives in this object).
 */
class ObdSession(
    private val newLink: () -> ObdLink,
    private val reconnectDelayMs: Long = RECONNECT_DELAY_MS,
    private val pollPauseMs: Long = 50,
    private val initPauseMs: Long = 100,
) {
    companion object {
        const val RECONNECT_DELAY_MS = 5000L
        const val TRANS_FAIL_LIMIT = 8

        /** A gauge whose PID has not answered this many cycles in a row shows "--" instead of a stale number. */
        const val STALE_AFTER_MISSES = 10
    }

    private val _state = MutableStateFlow(ClusterState())
    val state: StateFlow<ClusterState> = _state.asStateFlow()

    private enum class Step { Connect, Init, Poll }
    private var step = Step.Connect

    private val wake = Channel<Unit>(Channel.CONFLATED)

    // trans acquisition state: persists across reconnects
    private var transFails = 0
    private var transConfirmed = false
    private val misses = HashMap<GaugeId, Int>()

    /** Skip the remaining reconnect wait (the Retry button, or permission just granted). */
    fun retryNow() {
        wake.trySend(Unit)
    }

    suspend fun run() {
        try {
            while (true) {
                val link = newLink()
                try {
                    attempt(link)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    fail(classify(t))
                } finally {
                    link.close()
                }
                withTimeoutOrNull(reconnectDelayMs) { wake.receive() }
            }
        } finally {
            _state.update { it.copy(phase = ClusterPhase.Idle, failure = null, retrying = false, device = null, values = emptyMap()) }
        }
    }

    private fun classify(t: Throwable): ObdFailure {
        if (t is ObdLinkException && t.failure !in setOf(ObdFailure.ConnectionFailed, ObdFailure.LinkLost, ObdFailure.InitFailed)) {
            return t.failure
        }
        return when (step) {
            Step.Connect -> ObdFailure.ConnectionFailed
            Step.Init -> ObdFailure.InitFailed
            Step.Poll -> ObdFailure.LinkLost
        }
    }

    private fun fail(f: ObdFailure) {
        _state.update { it.copy(phase = ClusterPhase.Failed, failure = f, retrying = false, device = null, values = emptyMap()) }
    }

    private suspend fun attempt(link: ObdLink) {
        _state.update {
            if (it.failure != null) it.copy(phase = ClusterPhase.Failed, retrying = true)
            else it.copy(phase = ClusterPhase.Connecting, retrying = false, device = null, values = emptyMap())
        }
        step = Step.Connect
        link.open()
        step = Step.Init
        _state.update { it.copy(phase = ClusterPhase.Initializing, failure = null, retrying = false, device = link.deviceName) }
        for (c in ObdProtocol.INIT) {
            link.exchange(c.command, c.timeoutMs)
            delay(initPauseMs)
        }
        step = Step.Poll
        misses.clear()
        _state.update { it.copy(phase = ClusterPhase.Live, values = emptyMap()) }
        while (true) {
            pollCycle(link)
            delay(pollPauseMs)
        }
    }

    private suspend fun pollCycle(link: ObdLink) {
        val got = HashMap<GaugeId, Float>()
        val missed = ArrayList<GaugeId>()
        var othersOk = false
        for (pid in ObdProtocol.STANDARD) {
            val a = ObdProtocol.parseByteA(link.exchange(pid.command, ObdProtocol.COMMAND_TIMEOUT_MS), pid.prefix)
            if (a != null) { got[pid.id] = pid.convert(a); othersOk = true } else missed += pid.id
        }
        var stage = _state.value.transStage
        val staged = pollTrans(link, othersOk, stage)
        val transValue = staged.value
        if (transValue != null) got[GaugeId.Trans] = transValue else missed += GaugeId.Trans
        val stageChanged = staged.stage != stage
        stage = staged.stage

        _state.update { s ->
            val vals = HashMap(s.values)
            if (stageChanged) { vals.remove(GaugeId.Trans); misses.remove(GaugeId.Trans) }
            for ((id, v) in got) { vals[id] = v; misses[id] = 0 }
            for (id in missed) {
                if (stageChanged && id == GaugeId.Trans) continue
                val n = (misses[id] ?: 0) + 1
                misses[id] = n
                if (n >= STALE_AFTER_MISSES) vals.remove(id)
            }
            s.copy(values = vals, transStage = stage)
        }
    }

    private class TransResult(val value: Float?, val stage: TransStage)

    private suspend fun pollTrans(link: ObdLink, othersOk: Boolean, stage: TransStage): TransResult {
        if (stage == TransStage.Fallback) {
            val pid = ObdProtocol.COOLANT
            val a = ObdProtocol.parseByteA(link.exchange(pid.command, ObdProtocol.COMMAND_TIMEOUT_MS), pid.prefix)
            return TransResult(a?.let { pid.convert(it) }, stage)
        }
        val pid = ObdProtocol.TRANS
        val raw = if (stage == TransStage.Physical) {
            link.exchange(ObdProtocol.ADDRESS_TCM, ObdProtocol.COMMAND_TIMEOUT_MS)
            val r = link.exchange(pid.command, ObdProtocol.COMMAND_TIMEOUT_MS)
            link.exchange(ObdProtocol.ADDRESS_BROADCAST, ObdProtocol.COMMAND_TIMEOUT_MS) // restore broadcast for mode 01
            r
        } else {
            link.exchange(pid.command, ObdProtocol.COMMAND_TIMEOUT_MS)
        }
        val a = ObdProtocol.parseByteA(raw, pid.prefix)
        if (a != null) {
            transFails = 0
            transConfirmed = true
            return TransResult(pid.convert(a), stage)
        }
        // whole link struggling, or this stage already proved itself: a transient miss, never escalate
        if (!othersOk || transConfirmed) return TransResult(null, stage)
        transFails++
        if (transFails < TRANS_FAIL_LIMIT) return TransResult(null, stage)
        transFails = 0
        return TransResult(null, if (stage == TransStage.Functional) TransStage.Physical else TransStage.Fallback)
    }
}
