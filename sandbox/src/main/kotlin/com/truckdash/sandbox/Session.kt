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
) {
    companion object {
        private const val DAY = 86_400_000L

        fun create(seedHistory: Boolean = true, nowMs: Long = System.currentTimeMillis()): SandboxSession {
            val store = MemoryStore()
            if (seedHistory) seed(store, nowMs)
            val engine = DriveEngine(store.newSink())
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
                val engine = DriveEngine(store.newSink())
                val c = SimController(
                    engine, start, seed = 100L + i,
                    startLat = 39.7 + rnd.nextDouble() * 0.2, startLon = -105.1 + rnd.nextDouble() * 0.2,
                    startHeadingDeg = rnd.nextDouble() * 360,
                )
                val scale = 0.7 + rnd.nextDouble() * 0.9
                c.startScenario(scenario, scale)
                c.runFor(scenario.segs.sumOf { it.durS * (if (it.mph == 0.0 && it.durS > 300) 1.0 else scale) } + 20)
                engine.stop(0, start)
            }

            // fuel + maintenance relative to the odometer these trips produce
            val odo = store.settingsFlow.value.odoBaseMi +
                store.tripsFlow.value.sumOf { it.distanceM } * Geo.M_TO_MI
            var fillOdo = odo - 15.0
            repeat(5) { i ->
                val miles = 270.0 + rnd.nextInt(45) // miles driven since the previous fill-up
                store.fuelFlow.value = store.fuelFlow.value + FuelLog(
                    id = store.nextId(), dateMs = nowMs - (2 + i * 16) * DAY, odometerMi = fillOdo,
                    gallons = miles / (16.0 + rnd.nextDouble() * 3), pricePerGal = 3.29 + rnd.nextDouble() * 0.45,
                    fullTank = i != 2,
                )
                fillOdo -= miles
            }
            store.maintFlow.value = listOf(
                MaintItem(store.nextId(), "Oil & filter", 7500, 180, odo - 6500, nowMs - 120 * DAY),
                MaintItem(store.nextId(), "Tire rotation", 7500, null, odo - 3000, nowMs - 90 * DAY),
                MaintItem(store.nextId(), "Brake inspection", null, 365, 0.0, nowMs - 200 * DAY),
                MaintItem(store.nextId(), "Registration", null, 365, 0.0, nowMs - 345 * DAY),
                MaintItem(store.nextId(), "Insurance", null, 180, 0.0, nowMs - 195 * DAY),
            )
        }
    }
}
