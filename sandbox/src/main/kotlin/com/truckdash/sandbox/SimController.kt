package com.truckdash.sandbox

import com.truckdash.core.VehicleG
import com.truckdash.engine.DriveEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class SimStatus(
    val scenario: String? = null,
    val segIndex: Int = 0,
    val segCount: Int = 0,
    val simSeconds: Long = 0,
    val gpsLost: Boolean = false,
)

private data class Impulse(val seg: Seg, val untilClockMs: Long)

/**
 * Drives a [DriveEngine] with synthetic GPS + IMU data, exactly like the head unit's sensors would.
 * [tick] is for the live UI (real time x [speedFactor]); [runFor] is for tests and history seeding.
 * Only one thread should call tick/runFor; the setters below are safe to call from the UI thread.
 */
class SimController(
    private val engine: DriveEngine,
    wallStartMs: Long = System.currentTimeMillis(),
    seed: Long = 1L,
    startLat: Double = 39.7392,
    startLon: Double = -104.9903,
    startHeadingDeg: Double = 90.0,
) {
    private val sim = VehicleSim(startLat, startLon, startHeadingDeg)
    private val rnd = Random(seed)

    private var clockMs = 1_000_000L
    private var wallMs = wallStartMs
    private var stepCount = 0L
    private var budgetS = 0.0

    @Volatile var paused = false
    @Volatile var speedFactor = 1.0
    @Volatile var manual = false
    @Volatile var manualKmh = 0.0
    @Volatile var manualYawDegS = 0.0
    @Volatile var manualGradePct = 0.0
    @Volatile private var gpsLossUntilClock = 0L
    @Volatile private var pendingFastForwardS = 0.0
    @Volatile private var impulse: Impulse? = null
    @Volatile private var scenario: Scenario? = null
    @Volatile private var durationScale = 1.0
    private var segIdx = 0
    private var segT = 0.0

    private val _status = MutableStateFlow(SimStatus())
    val status: StateFlow<SimStatus> = _status.asStateFlow()

    fun startScenario(s: Scenario, durationScale: Double = 1.0) {
        manual = false
        this.durationScale = durationScale
        segIdx = 0; segT = 0.0
        scenario = s
    }

    fun stopScenario() { scenario = null }

    /** Temporary override of the current driving input (hard brake, floor it, sharp turn...). */
    fun impulse(seg: Seg) { impulse = Impulse(seg, clockMs + (seg.durS * 1000).toLong()) }

    fun gpsDropout(seconds: Int) { gpsLossUntilClock = clockMs + seconds * 1000L }

    /** Skip ahead [seconds] of simulated time as fast as possible (e.g. wait out the 5 min trip-end timer). */
    fun fastForward(seconds: Double) { pendingFastForwardS += seconds }

    /** Advance by real elapsed seconds x speed factor. */
    fun tick(realDtS: Double) {
        if (paused) return
        budgetS += realDtS * speedFactor + pendingFastForwardS.also { pendingFastForwardS = 0.0 }
        while (budgetS >= STEP_S) {
            step()
            budgetS -= STEP_S
        }
    }

    /** Run [simSeconds] immediately (ignores pause / speed factor). */
    fun runFor(simSeconds: Double) {
        repeat((simSeconds / STEP_S).toInt()) { step() }
    }

    private fun currentSeg(): Seg {
        impulse?.let { if (clockMs < it.untilClockMs) return it.seg else impulse = null }
        if (manual) return Seg(1.0, manualKmh, manualYawDegS, manualGradePct, accel = 2.5, brake = 4.0)
        val s = scenario
        if (s != null && segIdx < s.segs.size) return s.segs[segIdx]
        return IDLE
    }

    private fun step() {
        val seg = currentSeg()
        sim.advance(STEP_S, seg.kmh / 3.6, seg.yawDegS, seg.gradePct, seg.accel, seg.brake)

        // advance scenario clock
        val s = scenario
        if (!manual && impulse == null && s != null && segIdx < s.segs.size) {
            segT += STEP_S
            if (segT >= s.segs[segIdx].durS * durationScale) { segIdx++; segT = 0.0 }
            if (segIdx >= s.segs.size) scenario = null
        }

        clockMs += STEP_MS
        wallMs += STEP_MS
        stepCount++

        engine.onAcceleration(
            clockMs, wallMs,
            VehicleG(sim.longG() + noise(0.012).toFloat(), sim.latG() + noise(0.012).toFloat(), noise(0.01).toFloat()),
        )
        if (stepCount % FIX_EVERY == 0L) {
            val lost = clockMs < gpsLossUntilClock
            if (!lost) {
                val v = sim.speedMps
                engine.onLocation(
                    clockMs, wallMs, sim.lat, sim.lon, sim.altM + noise(0.4),
                    if (v < 0.05) 0f else (v + noise(0.08)).coerceAtLeast(0.0).toFloat(),
                    sim.headingDeg.toFloat(), 4.5f,
                )
            }
            engine.tick(clockMs)
            _status.value = SimStatus(
                scenario = (scenario?.name).takeIf { !manual },
                segIndex = segIdx, segCount = scenario?.segs?.size ?: 0,
                simSeconds = (clockMs - 1_000_000L) / 1000, gpsLost = lost,
            )
        }
    }

    private fun noise(amp: Double) = (rnd.nextDouble() * 2 - 1) * amp

    private companion object {
        const val STEP_MS = 50L
        const val STEP_S = 0.05
        const val FIX_EVERY = 20L // 1 Hz GPS
        val IDLE = Seg(1.0, 0.0)
    }
}
