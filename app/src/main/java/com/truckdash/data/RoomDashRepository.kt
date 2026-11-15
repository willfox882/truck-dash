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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class RoomDashRepository(
    private val db: AppDatabase,
    private val store: SettingsStore,
    engine: DriveEngine,
) : DashRepository {
    override val live: StateFlow<LiveState> = engine.state
    override val settings: StateFlow<Settings> = store.flow

    override val trips: Flow<List<Trip>> = db.tripDao().observeTrips().map { l -> l.map { it.toModel() } }

    /** Odometer = user-entered base + GPS-recorded trip distance since it was set. */
    override val odometerMi: Flow<Double> = store.flow
        .distinctUntilChangedBy { it.odoBaseSetMs to it.odoBaseMi }
        .flatMapLatest { s ->
            db.tripDao().observeDistanceSince(s.odoBaseSetMs).map { s.odoBaseMi + it * Geo.M_TO_MI }
        }

    override val maintenance: Flow<List<MaintItem>> = db.maintDao().observeAll().map { l -> l.map { it.toModel() } }
    override val fuelLogs: Flow<List<FuelLog>> = db.fuelDao().observeAll().map { l -> l.map { it.toModel() } }

    override suspend fun tripDetail(id: Long): TripDetail? {
        val dao = db.tripDao()
        val t = dao.trip(id) ?: return null
        return TripDetail(t.toModel(), dao.points(id).map { it.toModel() }, dao.events(id).map { it.toModel() })
    }

    override suspend fun deleteTrip(id: Long) = db.tripDao().deleteTrip(id)

    override fun setOdometer(mi: Double) =
        store.update { it.copy(odoBaseMi = mi, odoBaseSetMs = System.currentTimeMillis()) }

    override fun setPassengerMode(on: Boolean) = store.update { it.copy(passengerMode = on) }

    override suspend fun addFuel(log: FuelLog) = db.fuelDao().insert(log.toEntity())
    override suspend fun deleteFuel(log: FuelLog) = db.fuelDao().delete(log.toEntity())
    override suspend fun addMaintenance(item: MaintItem) = db.maintDao().insert(item.toEntity())
    override suspend fun updateMaintenance(item: MaintItem) = db.maintDao().update(item.toEntity())
    override suspend fun deleteMaintenance(item: MaintItem) = db.maintDao().delete(item.toEntity())
}
