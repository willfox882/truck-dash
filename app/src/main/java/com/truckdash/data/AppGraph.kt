package com.truckdash.data

import android.content.Context
import com.truckdash.engine.DriveEngine
import com.truckdash.obd.AndroidClusterPlatform
import com.truckdash.obd.ClusterPresenter
import com.truckdash.repo.DashRepository
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
    lateinit var engine: DriveEngine
        private set
    lateinit var repo: DashRepository
        private set

    /** Cluster tab: process-wide so the trans stage (and the dongle session) outlive the activity. Inert until the tab opens. */
    lateinit var clusterPlatform: AndroidClusterPlatform
        private set
    lateinit var cluster: ClusterPresenter
        private set

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun init(ctx: Context) {
        db = AppDatabase.build(ctx)
        settings = SettingsStore(ctx)
        engine = DriveEngine(RoomTripSink(db), onDistance = settings::addDistance)
        repo = RoomDashRepository(db, settings, engine)
        clusterPlatform = AndroidClusterPlatform(ctx)
        cluster = ClusterPresenter(clusterPlatform, repo.settings, repo::setObdAddress, main)
        initDistanceCounterOnce()
        seedDefaultsOnce()
    }

    /** Installs from before the counter existed: derive it once from the stored trips. */
    private fun initDistanceCounterOnce() {
        if (settings.distInitialised) return
        io.launch {
            val dao = db.tripDao()
            val base = settings.value.odoBaseSetMs
            val total = dao.totalDistance()
            val before = if (base > 0) dao.distanceBefore(base) else 0.0
            settings.update { it.copy(distTotalM = it.distTotalM + total, odoBaseTotalM = it.odoBaseTotalM + before) }
            settings.distInitialised = true
        }
    }

    private fun seedDefaultsOnce() {
        if (settings.seeded) return
        io.launch {
            if (db.maintDao().count() == 0) {
                val now = System.currentTimeMillis()
                listOf(
                    MaintenanceItemEntity(name = "Oil & filter", intervalKm = 8000, intervalDays = 180, lastDateMs = now),
                    MaintenanceItemEntity(name = "Tire rotation", intervalKm = 10000, lastDateMs = now),
                    MaintenanceItemEntity(name = "Brake inspection", intervalDays = 365, lastDateMs = now),
                    MaintenanceItemEntity(name = "Registration", intervalDays = 365, lastDateMs = now),
                    MaintenanceItemEntity(name = "Insurance", intervalDays = 180, lastDateMs = now),
                ).forEach { db.maintDao().insert(it) }
            }
            settings.seeded = true
        }
    }
}
