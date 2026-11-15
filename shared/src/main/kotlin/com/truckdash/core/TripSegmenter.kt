package com.truckdash.core

enum class SegEvent { NONE, START, END }

/**
 * Splits a continuous speed stream into trips.
 * Starts after speed >= startMps held for startHoldMs; ends after speed <= stopMps for endIdleMs.
 * Times are any monotonic millisecond clock.
 */
class TripSegmenter(
    private val startMps: Float = 2.2f, // ~5 mph
    private val startHoldMs: Long = 5_000,
    private val stopMps: Float = 0.45f, // ~1 mph
    private val endIdleMs: Long = 300_000,
) {
    var active = false
        private set

    /** Clock time speed first exceeded the start threshold (valid right after START). */
    var startedAtMs = 0L
        private set

    /** Clock time the vehicle last came to rest (valid right after END). */
    var endedAtMs = 0L
        private set

    private var aboveSince = -1L
    private var belowSince = -1L

    fun update(tMs: Long, speedMps: Float): SegEvent {
        if (!active) {
            if (speedMps >= startMps) {
                if (aboveSince < 0) aboveSince = tMs
                if (tMs - aboveSince >= startHoldMs) {
                    active = true
                    startedAtMs = aboveSince
                    belowSince = -1
                    aboveSince = -1
                    return SegEvent.START
                }
            } else {
                aboveSince = -1
            }
        } else {
            if (speedMps <= stopMps) {
                if (belowSince < 0) belowSince = tMs
                if (tMs - belowSince >= endIdleMs) {
                    active = false
                    endedAtMs = belowSince
                    belowSince = -1
                    return SegEvent.END
                }
            } else {
                belowSince = -1
            }
        }
        return SegEvent.NONE
    }

    /** Force-end (e.g. service stopping). Returns true if a trip was active. */
    fun forceEnd(tMs: Long): Boolean {
        if (!active) return false
        active = false
        endedAtMs = if (belowSince >= 0) belowSince else tMs
        belowSince = -1
        return true
    }
}
