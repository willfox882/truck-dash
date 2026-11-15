package com.truckdash.engine

import com.truckdash.core.ElevationAccumulator
import com.truckdash.core.GradeEstimator
import com.truckdash.core.HarshEventDetector
import com.truckdash.core.HarshType
import com.truckdash.core.SegEvent
import com.truckdash.core.TripSegmenter
import com.truckdash.model.DriveEvent
import com.truckdash.model.TrackPoint
import com.truckdash.model.Trip

/**
 * Pure-Kotlin trip state machine: GPS fixes + g-force samples in, [TripSink] calls out.
 * Not thread-safe; feed it from a single thread. Clock = any monotonic ms clock; wall = epoch ms.
 */
class TripRecorder(private val sink: TripSink) {
    private val segmenter = TripSegmenter()
    private val harsh = HarshEventDetector()
    private var elev = ElevationAccumulator()
    private val grade = GradeEstimator()

    private var key = 0L
    private var startWall = 0L
    private var lastClock = -1L
    private var lastSaveClock = 0L
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastSpeed = 0f
    private var altEma: Double? = null
    private var nAccel = 0
    private var nBrake = 0
    private var nCorner = 0
    private val pointBuf = ArrayList<TrackPoint>()

    /** Stationary fixes held back: committed if the vehicle moves again, dropped if the trip ends (trailing idle). */
    private val idleBuf = ArrayList<TrackPoint>()
    private var idleMs = 0L

    val active: Boolean get() = segmenter.active
    var distM = 0.0; private set
    var movingMs = 0L; private set
    var stoppedMs = 0L; private set
    var maxMps = 0f; private set
    var gradePct: Float? = null; private set

    /** Smoothed GPS altitude in metres (null until first altitude). */
    val altM: Double? get() = altEma

    fun onFix(clockMs: Long, wallMs: Long, lat: Double, lon: Double, altM: Double?, speedMps: Float) {
        val dtMs = if (lastClock >= 0) (clockMs - lastClock).coerceIn(0, 5_000) else 0L
        lastClock = clockMs
        lastLat = lat; lastLon = lon; lastSpeed = speedMps
        if (altM != null) altEma = altEma?.let { it * 0.8 + altM * 0.2 } ?: altM
        val moving = speedMps > 0.5f

        when (segmenter.update(clockMs, speedMps)) {
            SegEvent.START -> startTrip(wallMs, clockMs)
            SegEvent.END -> finishTrip(wallMs - (clockMs - segmenter.endedAtMs))
            SegEvent.NONE -> Unit
        }

        val a = altEma
        if (moving && a != null) grade.add(speedMps * dtMs / 1000.0, a)?.let { gradePct = it }

        if (segmenter.active) {
            if (speedMps > maxMps) maxMps = speedMps
            val p = TrackPoint(wallMs, lat, lon, a ?: 0.0, speedMps)
            if (moving) {
                if (idleBuf.isNotEmpty()) { // a real mid-trip stop (light, drive-through): keep it
                    pointBuf += idleBuf
                    idleBuf.clear()
                    stoppedMs += idleMs
                    idleMs = 0
                }
                distM += speedMps * dtMs / 1000.0
                movingMs += dtMs
                a?.let { elev.add(it) }
                pointBuf += p
            } else {
                idleBuf += p
                idleMs += dtMs
            }
            if (pointBuf.size >= 5) flushPoints()
            if (clockMs - lastSaveClock > 10_000) saveTrip(final = false, endWall = null, clockMs = clockMs)
        }
    }

    /** Smoothed vehicle-frame g (longG + = forward, latG + = right). */
    fun onAcceleration(clockMs: Long, wallMs: Long, longG: Float, latG: Float) {
        if (!segmenter.active) return
        val type = harsh.update(clockMs, longG, latG, lastSpeed) ?: return
        when (type) {
            HarshType.ACCEL -> nAccel++
            HarshType.BRAKE -> nBrake++
            HarshType.CORNER -> nCorner++
        }
        sink.eventAdded(
            key,
            DriveEvent(wallMs, type.name, lastLat, lastLon, if (type == HarshType.CORNER) latG else longG),
        )
    }

    /** Close any open trip (service stopping / sandbox reset). */
    fun stop(clockMs: Long, wallMs: Long) {
        if (segmenter.forceEnd(clockMs)) finishTrip(wallMs)
    }

    private fun startTrip(wallMs: Long, clockMs: Long) {
        key++
        startWall = wallMs
        distM = 0.0; movingMs = 0; stoppedMs = 0; maxMps = 0f
        nAccel = 0; nBrake = 0; nCorner = 0
        elev = ElevationAccumulator()
        harsh.reset()
        pointBuf.clear()
        idleBuf.clear(); idleMs = 0
        lastSaveClock = clockMs
        sink.tripStarted(key, wallMs)
    }

    private fun finishTrip(endWall: Long) {
        idleBuf.clear(); idleMs = 0 // trailing idle is not part of the trip
        flushPoints()
        saveTrip(final = true, endWall = endWall, clockMs = lastClock)
    }

    private fun flushPoints() {
        if (pointBuf.isEmpty()) return
        sink.pointsAdded(key, pointBuf.toList())
        pointBuf.clear()
    }

    private fun saveTrip(final: Boolean, endWall: Long?, clockMs: Long) {
        lastSaveClock = clockMs
        sink.tripUpdated(
            key,
            Trip(
                startMs = startWall, endMs = if (final) endWall else null,
                distanceM = distM, maxSpeedMps = maxMps, movingMs = movingMs, stoppedMs = stoppedMs,
                elevGainM = elev.gainM.toFloat(), harshAccel = nAccel, harshBrake = nBrake, harshCorner = nCorner,
            ),
            final,
        )
    }
}
