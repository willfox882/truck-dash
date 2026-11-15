package com.truckdash.engine

import com.truckdash.model.DriveEvent
import com.truckdash.model.TrackPoint
import com.truckdash.model.Trip

/**
 * Where [TripRecorder] sends finished data. [key] is a local per-trip counter; the sink maps it to
 * its own storage id. Calls arrive from one thread, in order; implementations may persist async.
 */
interface TripSink {
    fun tripStarted(key: Long, startMs: Long)
    fun pointsAdded(key: Long, points: List<TrackPoint>)
    fun eventAdded(key: Long, event: DriveEvent)

    /** Running totals; [final] = trip has ended (Trip.endMs set). Trip.id is ignored. */
    fun tripUpdated(key: Long, trip: Trip, final: Boolean)
}
