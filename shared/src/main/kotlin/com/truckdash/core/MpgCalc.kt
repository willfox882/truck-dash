package com.truckdash.core

import com.truckdash.model.FuelLog

data class MpgPoint(val dateMs: Long, val mpg: Double)

data class FuelStats(
    val points: List<MpgPoint>,
    val avgMpg: Double?,
    val lastMpg: Double?,
    val costPerMile: Double?,
    val totalSpend: Double,
)

object MpgCalc {
    /**
     * Fill-to-fill method: MPG is only computed between two "full tank" fill-ups; partial fills
     * in between add their gallons to the segment. The first full fill only sets the baseline.
     */
    fun compute(logs: List<FuelLog>): FuelStats {
        val sorted = logs.sortedBy { it.odometerMi }
        val points = mutableListOf<MpgPoint>()
        var baseOdo: Double? = null
        var gal = 0.0
        var cost = 0.0
        var totalMiles = 0.0
        var totalGal = 0.0
        var totalCost = 0.0
        for (e in sorted) {
            val base = baseOdo
            if (base == null) {
                if (e.fullTank) {
                    baseOdo = e.odometerMi
                    gal = 0.0
                    cost = 0.0
                }
                continue
            }
            gal += e.gallons
            cost += e.gallons * e.pricePerGal
            if (e.fullTank) {
                val miles = e.odometerMi - base
                if (miles > 0 && gal > 0) {
                    points += MpgPoint(e.dateMs, miles / gal)
                    totalMiles += miles
                    totalGal += gal
                    totalCost += cost
                }
                baseOdo = e.odometerMi
                gal = 0.0
                cost = 0.0
            }
        }
        return FuelStats(
            points = points,
            avgMpg = if (totalGal > 0) totalMiles / totalGal else null,
            lastMpg = points.lastOrNull()?.mpg,
            costPerMile = if (totalMiles > 0) totalCost / totalMiles else null,
            totalSpend = logs.sumOf { it.gallons * it.pricePerGal },
        )
    }
}
