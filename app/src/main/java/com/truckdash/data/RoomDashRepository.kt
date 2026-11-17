package com.truckdash.data

import com.truckdash.core.Geo
import com.truckdash.engine.DriveEngine
import com.truckdash.model.FuelLog
import com.truckdash.model.LiveState
import com.truckdash.model.MaintItem
import com.truckdash.model.Settings
import com.truckdash.model.Trip
import com.truckdash.model.TripDetail
import com.truckdash.repo.DashRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomDashRepository(
    private val db: AppDatabase,
    private val store: SettingsStore,
    engine: DriveEngine,
) : DashRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val live: StateFlow<LiveState> = engine.state
    override val settings: StateFlow<Settings> = store.flow

    override val trips: Flow<List<Trip>> = db.tripDao().observeTrips().map { l -> l.map { it.toModel() } }

    /** Odometer = user-entered base + GPS distance driven since it was set (not derived from trip rows). */
    override val odometerKm: Flow<Double> = store.flow
        .map { it.odoBaseKm + (it.distTotalM - it.odoBaseTotalM) * Geo.M_TO_KM }
        .distinctUntilChanged()

    override val maintenance: Flow<List<MaintItem>> = db.maintDao().observeAll().map { l -> l.map { it.toModel() } }
    override val fuelLogs: Flow<List<FuelLog>> = db.fuelDao().observeAll().map { l -> l.map { it.toModel() } }

    override suspend fun tripDetail(id: Long): TripDetail? {
        val dao = db.tripDao()
        val t = dao.trip(id) ?: return null
        return TripDetail(t.toModel(), dao.points(id).map { it.toModel() }, dao.events(id).map { it.toModel() })
    }

    override suspend fun deleteTrip(id: Long) {
        val t = db.tripDao().trip(id) ?: return
        if (t.endMs != null) db.tripDao().deleteTrip(id) // never delete the trip being recorded
    }

    override fun setOdometer(km: Double) {
        val firstTime = store.value.odoBaseSetMs == 0L
        store.update { it.copy(odoBaseKm = km, odoBaseSetMs = System.currentTimeMillis(), odoBaseTotalM = it.distTotalM) }
        if (firstTime) scope.launch { db.maintDao().rebaseUntouched(km) }
    }

    override fun setPassengerMode(on: Boolean) = store.update { it.copy(passengerMode = on) }
    override fun setDrivingLock(on: Boolean) = store.update { it.copy(drivingLock = on) }

    override suspend fun addFuel(log: FuelLog) = db.fuelDao().insert(log.toEntity())
    override suspend fun deleteFuel(log: FuelLog) = db.fuelDao().delete(log.toEntity())
    override suspend fun addMaintenance(item: MaintItem) = db.maintDao().insert(item.toEntity())
    override suspend fun updateMaintenance(item: MaintItem) = db.maintDao().update(item.toEntity())
    override suspend fun deleteMaintenance(item: MaintItem) = db.maintDao().delete(item.toEntity())
}
