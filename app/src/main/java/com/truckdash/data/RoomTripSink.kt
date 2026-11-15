package com.truckdash.data

import com.truckdash.engine.TripSink
import com.truckdash.model.DriveEvent
import com.truckdash.model.TrackPoint
import com.truckdash.model.Trip
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Persists engine output to Room. Callbacks come from the main thread; writes run in order on a
 * single IO thread. Trip row ids are resolved lazily (the insert may not have finished yet).
 */
class RoomTripSink(private val db: AppDatabase) : TripSink {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val dao get() = db.tripDao()
    private val ids = HashMap<Long, CompletableDeferred<Long>>()

    init {
        // A previous run may have died mid-trip: close those trips at their last recorded point.
        scope.launch {
            dao.openTrips().forEach { t ->
                dao.updateTrip(t.copy(endMs = dao.lastPointTime(t.id) ?: t.startMs))
            }
        }
    }

    override fun tripStarted(key: Long, startMs: Long) {
        val d = CompletableDeferred<Long>()
        ids[key] = d
        scope.launch {
            runCatching { dao.insertTrip(TripEntity(startMs = startMs)) }
                .onSuccess { d.complete(it) }
                .onFailure { d.completeExceptionally(it) }
        }
    }

    override fun pointsAdded(key: Long, points: List<TrackPoint>) {
        val d = ids[key] ?: return
        scope.launch { dao.insertPoints(points.map { it.toEntity(d.await()) }) }
    }

    override fun eventAdded(key: Long, event: DriveEvent) {
        val d = ids[key] ?: return
        scope.launch { dao.insertEvent(event.toEntity(d.await())) }
    }

    override fun tripUpdated(key: Long, trip: Trip, final: Boolean) {
        val d = ids[key] ?: return
        scope.launch { dao.updateTrip(trip.toEntity(d.await())) }
        if (final) ids.remove(key)
    }
}
