package com.truckdash.sandbox

import com.truckdash.core.Geo
import com.truckdash.engine.DriveEngine
import com.truckdash.model.FuelLog
import com.truckdash.model.MaintItem
import kotlin.random.Random

/** One complete sandbox: fake storage + engine + simulator + repository (all real app logic in between). */
class SandboxSession(
    val store: MemoryStore,
    val engine: DriveEngine,
    val controller: SimController,
    val repo: SandboxRepo,
    val dongle: FakeDongle = FakeDongle(),
) {
    /** The Android Bluetooth layer's stand-in; hand it to `TruckDashRoot(cluster = ...)`. */
    val cluster = SandboxClusterPlatform(dongle)


    companion object {
        private const val DAY = 86_400_000L

        fun create(seedHistory: Boolean = true, nowMs: Long = System.currentTimeMillis()): SandboxSession {
            val store = MemoryStore()
            if (seedHistory) seed(store, nowMs)
            val engine = DriveEngine(store.newSink(), store::addDistance)
            return SandboxSession(store, engine, SimController(engine, nowMs, seed = 42), SandboxRepo(store, engine))
        }

        /**
         * Fills the store with a few months of plausible history by running the real engine against the
         * simulator (so the Trips tab has real recorded data), plus fuel logs and maintenance items.
         */
        fun seed(store: MemoryStore, nowMs: Long) {
            val rnd = Random(7)
            val plan = listOf(
                165 to Scenarios.highway, 150 to Scenarios.cityLoop, 128 to Scenarios.cityLoop, 101 to Scenarios.highway,
                84 to Scenarios.cityLoop, 70 to Scenarios.highway, 55 to Scenarios.cityLoop, 41 to Scenarios.hardEvents,
                33 to Scenarios.cityLoop, 26 to Scenarios.highway, 19 to Scenarios.cityLoop, 12 to Scenarios.cityLoop,
                8 to Scenarios.highway, 5 to Scenarios.cityLoop, 3 to Scenarios.cityLoop, 1 to Scenarios.hardEvents,
            )
            for ((i, p) in plan.withIndex()) {
                val (daysAgo, scenario) = p
                val dayStart = java.time.Instant.ofEpochMilli(nowMs - daysAgo * DAY).atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val start = dayStart + (7 + rnd.nextInt(10)) * 3_600_000L + rnd.nextInt(60) * 60_000L
                val engine = DriveEngine(store.newSink(), store::addDistance)
                val c = SimController(
                    engine, start, seed = 100L + i,
                    startLat = 39.7 + rnd.nextDouble() * 0.2, startLon = -105.1 + rnd.nextDouble() * 0.2,
                    startHeadingDeg = rnd.nextDouble() * 360,
                )
                val scale = 0.7 + rnd.nextDouble() * 0.9
                c.startScenario(scenario, scale)
                c.runFor(scenario.segs.sumOf { it.durS * (if (it.kmh == 0.0 && it.durS > 300) 1.0 else scale) } + 20)
                engine.stop(0, start)
            }

            // fuel + maintenance relative to the odometer these trips produce
            val odo = store.settingsFlow.value.odoBaseKm +
                store.tripsFlow.value.sumOf { it.distanceM } * Geo.M_TO_KM
            var fillOdo = odo - 25.0
            repeat(5) { i ->
                val km = 430.0 + rnd.nextInt(70) // km driven since the previous fill-up
                val lPer100 = 11.0 + rnd.nextDouble() * 4.0 // full-size pickup, mixed driving
                store.fuelFlow.value = store.fuelFlow.value + FuelLog(
                    id = store.nextId(), dateMs = nowMs - (2 + i * 16) * DAY, odometerKm = fillOdo,
                    litres = km * lPer100 / 100.0, pricePerLitre = 1.489 + rnd.nextDouble() * 0.16,
                    fullTank = i != 2,
                )
                fillOdo -= km
            }
            store.maintFlow.value = listOf(
                MaintItem(store.nextId(), "Oil & filter", 8000, 180, odo - 6900, nowMs - 120 * DAY),
                MaintItem(store.nextId(), "Tire rotation", 10000, null, odo - 4800, nowMs - 90 * DAY),
                MaintItem(store.nextId(), "Brake inspection", null, 365, 0.0, nowMs - 200 * DAY),
                MaintItem(store.nextId(), "Registration", null, 365, 0.0, nowMs - 345 * DAY),
                MaintItem(store.nextId(), "Insurance", null, 180, 0.0, nowMs - 195 * DAY),
            )
        }
    }
}
