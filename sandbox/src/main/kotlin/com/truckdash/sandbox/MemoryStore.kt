package com.truckdash.sandbox

import com.truckdash.core.Geo
import com.truckdash.engine.DriveEngine
import com.truckdash.engine.TripSink
import com.truckdash.model.DriveEvent
import com.truckdash.model.FuelLog
import com.truckdash.model.LiveState
import com.truckdash.model.MaintItem
import com.truckdash.model.Settings
import com.truckdash.model.TrackPoint
import com.truckdash.model.Trip
import com.truckdash.model.TripDetail
import com.truckdash.repo.DashRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory stand-in for Room. Thread-safe enough for one sim thread + the UI thread. */
class MemoryStore {
    val tripsFlow = MutableStateFlow<List<Trip>>(emptyList())
    val settingsFlow = MutableStateFlow(Settings(odoBaseKm = 77_600.0, odoBaseSetMs = 0L))
    val maintFlow = MutableStateFlow<List<MaintItem>>(emptyList())
    val fuelFlow = MutableStateFlow<List<FuelLog>>(emptyList())

    private val points = HashMap<Long, MutableList<TrackPoint>>()
    private val events = HashMap<Long, MutableList<DriveEvent>>()
    private var nextId = 1L

    @Synchronized fun nextId(): Long = nextId++

    /** Odometer feed from the engine (see DriveEngine.onDistance). */
    fun addDistance(deltaM: Double) = settingsFlow.update { it.copy(distTotalM = it.distTotalM + deltaM) }

    /** Each engine gets its own sink so per-engine trip keys never collide. */
    fun newSink(): TripSink = object : TripSink {
        private val ids = HashMap<Long, Long>()

        override fun tripStarted(key: Long, startMs: Long) {
            val id = nextId()
            ids[key] = id
            tripsFlow.update { it + Trip(id = id, startMs = startMs) }
        }

        override fun pointsAdded(key: Long, points: List<TrackPoint>) {
            val id = ids[key] ?: return
            synchronized(this@MemoryStore) { this@MemoryStore.points.getOrPut(id) { ArrayList() }.addAll(points) }
        }

        override fun eventAdded(key: Long, event: DriveEvent) {
            val id = ids[key] ?: return
            synchronized(this@MemoryStore) { events.getOrPut(id) { ArrayList() }.add(event) }
        }

        override fun tripUpdated(key: Long, trip: Trip, final: Boolean) {
            val id = ids[key] ?: return
            tripsFlow.update { l -> l.map { if (it.id == id) trip.copy(id = id) else it } }
            if (final) ids.remove(key)
        }
    }

    @Synchronized fun detail(id: Long): TripDetail? {
        val t = tripsFlow.value.firstOrNull { it.id == id } ?: return null
        return TripDetail(t, points[id]?.toList().orEmpty(), events[id]?.toList().orEmpty())
    }

    @Synchronized fun deleteTrip(id: Long) {
        points.remove(id); events.remove(id)
        tripsFlow.update { l -> l.filter { it.id != id } }
    }
}

class SandboxRepo(private val store: MemoryStore, engine: DriveEngine) : DashRepository {
    override val live: StateFlow<LiveState> = engine.state
    override val settings: StateFlow<Settings> = store.settingsFlow.asStateFlow()
    override val trips: Flow<List<Trip>> = store.tripsFlow
    override val maintenance: Flow<List<MaintItem>> = store.maintFlow
    override val fuelLogs: Flow<List<FuelLog>> = store.fuelFlow

    override val odometerKm: Flow<Double> = store.settingsFlow
        .map { it.odoBaseKm + (it.distTotalM - it.odoBaseTotalM) * Geo.M_TO_KM }
        .distinctUntilChanged()

    override suspend fun tripDetail(id: Long) = store.detail(id)
    override suspend fun deleteTrip(id: Long) {
        if (store.tripsFlow.value.firstOrNull { it.id == id }?.endMs != null) store.deleteTrip(id)
    }

    override fun setOdometer(km: Double) {
        val firstTime = store.settingsFlow.value.odoBaseSetMs == 0L
        store.settingsFlow.update { it.copy(odoBaseKm = km, odoBaseSetMs = System.currentTimeMillis(), odoBaseTotalM = it.distTotalM) }
        if (firstTime) store.maintFlow.update { l -> l.map { if (it.lastOdoKm == 0.0) it.copy(lastOdoKm = km) else it } }
    }

    override fun setPassengerMode(on: Boolean) = store.settingsFlow.update { it.copy(passengerMode = on) }
    override fun setDrivingLock(on: Boolean) = store.settingsFlow.update { it.copy(drivingLock = on) }
    override fun setShowCluster(on: Boolean) = store.settingsFlow.update { it.copy(showCluster = on) }
    override fun setObdAddress(address: String?) = store.settingsFlow.update { it.copy(obdAddress = address) }

    override suspend fun addFuel(log: FuelLog) = store.fuelFlow.update { it + log.copy(id = store.nextId()) }
    override suspend fun deleteFuel(log: FuelLog) = store.fuelFlow.update { l -> l.filter { it.id != log.id } }
    override suspend fun addMaintenance(item: MaintItem) = store.maintFlow.update { it + item.copy(id = store.nextId()) }
    override suspend fun updateMaintenance(item: MaintItem) =
        store.maintFlow.update { l -> l.map { if (it.id == item.id) item else it } }

    override suspend fun deleteMaintenance(item: MaintItem) = store.maintFlow.update { l -> l.filter { it.id != item.id } }
}
