package com.truckdash.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Tiny service locator. Initialised once from [com.truckdash.TruckDashApplication]. */
object AppGraph {
    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(ctx: Context) {
        db = AppDatabase.build(ctx)
        settings = SettingsStore(ctx)
        seedDefaultsOnce()
    }

    private fun seedDefaultsOnce() {
        if (settings.seeded) return
        io.launch {
            if (db.maintDao().count() == 0) {
                val now = System.currentTimeMillis()
                listOf(
                    MaintenanceItemEntity(name = "Oil & filter", intervalMiles = 7500, intervalDays = 180, lastDateMs = now),
                    MaintenanceItemEntity(name = "Tire rotation", intervalMiles = 7500, lastDateMs = now),
                    MaintenanceItemEntity(name = "Brake inspection", intervalDays = 365, lastDateMs = now),
                    MaintenanceItemEntity(name = "Registration", intervalDays = 365, lastDateMs = now),
                    MaintenanceItemEntity(name = "Insurance", intervalDays = 180, lastDateMs = now),
                ).forEach { db.maintDao().insert(it) }
            }
            settings.seeded = true
        }
    }
}
