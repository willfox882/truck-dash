package com.truckdash.ui.health

import com.truckdash.core.FuelStats
import com.truckdash.core.MpgCalc
import com.truckdash.model.FuelLog
import com.truckdash.model.MaintItem
import com.truckdash.model.Settings
import com.truckdash.repo.DashRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HealthPresenter(private val repo: DashRepository, private val scope: CoroutineScope) {
    val settings: StateFlow<Settings> = repo.settings

    val odometerMi: StateFlow<Double> =
        repo.odometerMi.stateIn(scope, SharingStarted.Eagerly, repo.settings.value.odoBaseMi)

    val maintenance: StateFlow<List<MaintItem>> =
        repo.maintenance.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val fuelLogs: StateFlow<List<FuelLog>> =
        repo.fuelLogs.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val fuelStats: StateFlow<FuelStats> = fuelLogs.map { MpgCalc.compute(it) }
        .stateIn(scope, SharingStarted.Eagerly, MpgCalc.compute(emptyList()))

    fun setOdometer(mi: Double) = repo.setOdometer(mi)

    fun setPassengerMode(on: Boolean) = repo.setPassengerMode(on)

    fun addFuel(gallons: Double, price: Double, odoMi: Double, full: Boolean) {
        scope.launch {
            repo.addFuel(
                FuelLog(
                    dateMs = System.currentTimeMillis(), odometerMi = odoMi,
                    gallons = gallons, pricePerGal = price, fullTank = full,
                ),
            )
        }
    }

    fun deleteFuel(e: FuelLog) {
        scope.launch { repo.deleteFuel(e) }
    }

    fun addMaintenance(name: String, miles: Int?, days: Int?) {
        scope.launch {
            repo.addMaintenance(
                MaintItem(
                    name = name, intervalMiles = miles, intervalDays = days,
                    lastOdoMi = odometerMi.value, lastDateMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun markDone(item: MaintItem) {
        scope.launch {
            repo.updateMaintenance(item.copy(lastOdoMi = odometerMi.value, lastDateMs = System.currentTimeMillis()))
        }
    }

    fun deleteMaintenance(item: MaintItem) {
        scope.launch { repo.deleteMaintenance(item) }
    }
}
