package com.truckdash.ui.health

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.truckdash.core.FuelStats
import com.truckdash.core.Geo
import com.truckdash.core.MpgCalc
import com.truckdash.data.AppGraph
import com.truckdash.data.FuelLogEntity
import com.truckdash.data.MaintenanceItemEntity
import com.truckdash.data.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppGraph.db
    private val store = AppGraph.settings

    val settings: StateFlow<Settings> = store.flow

    /** Odometer = user-entered base + GPS-recorded trip distance since it was set. */
    val odometerMi: StateFlow<Double> = store.flow
        .distinctUntilChangedBy { it.odoBaseSetMs to it.odoBaseMi }
        .flatMapLatest { s ->
            db.tripDao().observeDistanceSince(s.odoBaseSetMs).map { s.odoBaseMi + it * Geo.M_TO_MI }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), store.value.odoBaseMi)

    val maintenance: StateFlow<List<MaintenanceItemEntity>> =
        db.maintDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fuelLogs: StateFlow<List<FuelLogEntity>> =
        db.fuelDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fuelStats: StateFlow<FuelStats> = fuelLogs.map { MpgCalc.compute(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MpgCalc.compute(emptyList()))

    fun setOdometer(mi: Double) =
        store.update { it.copy(odoBaseMi = mi, odoBaseSetMs = System.currentTimeMillis()) }

    fun setPassengerMode(on: Boolean) = store.update { it.copy(passengerMode = on) }

    fun addFuel(gallons: Double, price: Double, odoMi: Double, full: Boolean) {
        viewModelScope.launch {
            db.fuelDao().insert(
                FuelLogEntity(
                    dateMs = System.currentTimeMillis(), odometerMi = odoMi,
                    gallons = gallons, pricePerGal = price, fullTank = full,
                ),
            )
        }
    }

    fun deleteFuel(e: FuelLogEntity) = viewModelScope.launch { db.fuelDao().delete(e) }

    fun addMaintenance(name: String, miles: Int?, days: Int?) {
        viewModelScope.launch {
            db.maintDao().insert(
                MaintenanceItemEntity(
                    name = name, intervalMiles = miles, intervalDays = days,
                    lastOdoMi = odometerMi.value, lastDateMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun markDone(item: MaintenanceItemEntity) {
        viewModelScope.launch {
            db.maintDao().update(item.copy(lastOdoMi = odometerMi.value, lastDateMs = System.currentTimeMillis()))
        }
    }

    fun deleteMaintenance(item: MaintenanceItemEntity) = viewModelScope.launch { db.maintDao().delete(item) }
}
