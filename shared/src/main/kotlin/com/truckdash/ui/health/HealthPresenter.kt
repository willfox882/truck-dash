package com.truckdash.ui.health

import com.truckdash.core.FuelStats
import com.truckdash.core.FuelEconomy
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

    val odometerKm: StateFlow<Double> =
        repo.odometerKm.stateIn(scope, SharingStarted.Eagerly, repo.settings.value.odoBaseKm)

    val maintenance: StateFlow<List<MaintItem>> =
        repo.maintenance.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val fuelLogs: StateFlow<List<FuelLog>> =
        repo.fuelLogs.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val fuelStats: StateFlow<FuelStats> = fuelLogs.map { FuelEconomy.compute(it) }
        .stateIn(scope, SharingStarted.Eagerly, FuelEconomy.compute(emptyList()))

    fun setOdometer(km: Double) = repo.setOdometer(km)

    fun setPassengerMode(on: Boolean) = repo.setPassengerMode(on)
    fun setDrivingLock(on: Boolean) = repo.setDrivingLock(on)
    fun setShowCluster(on: Boolean) = repo.setShowCluster(on)

    fun addFuel(litres: Double, pricePerLitre: Double, odoKm: Double, full: Boolean) {
        scope.launch {
            repo.addFuel(
                FuelLog(
                    dateMs = System.currentTimeMillis(), odometerKm = odoKm,
                    litres = litres, pricePerLitre = pricePerLitre, fullTank = full,
                ),
            )
        }
    }

    fun deleteFuel(e: FuelLog) {
        scope.launch { repo.deleteFuel(e) }
    }

    fun addMaintenance(name: String, km: Int?, days: Int?) {
        scope.launch {
            repo.addMaintenance(
                MaintItem(
                    name = name, intervalKm = km, intervalDays = days,
                    lastOdoKm = odometerKm.value, lastDateMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun markDone(item: MaintItem) {
        scope.launch {
            repo.updateMaintenance(item.copy(lastOdoKm = odometerKm.value, lastDateMs = System.currentTimeMillis()))
        }
    }

    fun deleteMaintenance(item: MaintItem) {
        scope.launch { repo.deleteMaintenance(item) }
    }
}
