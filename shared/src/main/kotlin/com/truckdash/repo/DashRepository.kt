package com.truckdash.repo

import com.truckdash.model.FuelLog
import com.truckdash.model.LiveState
import com.truckdash.model.MaintItem
import com.truckdash.model.Settings
import com.truckdash.model.Trip
import com.truckdash.model.TripDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Everything the shared UI needs. Android: Room-backed. Sandbox: in-memory. */
interface DashRepository {
    val live: StateFlow<LiveState>
    val settings: StateFlow<Settings>
    val trips: Flow<List<Trip>>
    val odometerKm: Flow<Double>
    val maintenance: Flow<List<MaintItem>>
    val fuelLogs: Flow<List<FuelLog>>

    suspend fun tripDetail(id: Long): TripDetail?
    suspend fun deleteTrip(id: Long)

    fun setOdometer(km: Double)
    fun setPassengerMode(on: Boolean)
    fun setDrivingLock(on: Boolean)
    fun setShowCluster(on: Boolean)
    fun setObdAddress(address: String?)

    suspend fun addFuel(log: FuelLog)
    suspend fun deleteFuel(log: FuelLog)
    suspend fun addMaintenance(item: MaintItem)
    suspend fun updateMaintenance(item: MaintItem)
    suspend fun deleteMaintenance(item: MaintItem)
}
