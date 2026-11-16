package com.truckdash.core

import com.truckdash.model.FuelLog

data class EconPoint(val dateMs: Long, val lPer100: Double)

data class FuelStats(
    val points: List<EconPoint>,
    /** Average fuel consumption, L/100 km (lower = better). */
    val avgLPer100: Double?,
    val lastLPer100: Double?,
    val costPerKm: Double?,
    val totalSpend: Double,
) {
    val costPer100Km: Double? get() = costPerKm?.times(100)
}

object FuelEconomy {
    /**
     * Fill-to-fill method: consumption is only computed between two "full tank" fill-ups; partial fills
     * in between add their litres to the segment. The first full fill only sets the baseline.
     * Result in L/100 km.
     */
    fun compute(logs: List<FuelLog>): FuelStats {
        val sorted = logs.sortedBy { it.odometerKm }
        val points = mutableListOf<EconPoint>()
        var baseOdo: Double? = null
        var litres = 0.0
        var cost = 0.0
        var totalKm = 0.0
        var totalLitres = 0.0
        var totalCost = 0.0
        for (e in sorted) {
            val base = baseOdo
            if (base == null) {
                if (e.fullTank) {
                    baseOdo = e.odometerKm
                    litres = 0.0
                    cost = 0.0
                }
                continue
            }
            litres += e.litres
            cost += e.litres * e.pricePerLitre
            if (e.fullTank) {
                val km = e.odometerKm - base
                if (km > 0 && litres > 0) {
                    points += EconPoint(e.dateMs, litres / km * 100.0)
                    totalKm += km
                    totalLitres += litres
                    totalCost += cost
                }
                baseOdo = e.odometerKm
                litres = 0.0
                cost = 0.0
            }
        }
        return FuelStats(
            points = points,
            avgLPer100 = if (totalKm > 0) totalLitres / totalKm * 100.0 else null,
            lastLPer100 = points.lastOrNull()?.lPer100,
            costPerKm = if (totalKm > 0) totalCost / totalKm else null,
            totalSpend = logs.sumOf { it.litres * it.pricePerLitre },
        )
    }
}
