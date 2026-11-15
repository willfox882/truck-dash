package com.truckdash.core

import com.truckdash.data.TripEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

object Analytics {
    /** Miles driven per calendar month for the last [months] months (oldest first, includes empty months). */
    fun milesByMonth(
        trips: List<TripEntity>,
        nowMs: Long,
        months: Int = 6,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Pair<YearMonth, Double>> {
        val current = YearMonth.from(Instant.ofEpochMilli(nowMs).atZone(zone))
        val buckets = (months - 1 downTo 0).map { current.minusMonths(it.toLong()) }
        val sums = trips.groupBy { YearMonth.from(Instant.ofEpochMilli(it.startMs).atZone(zone)) }
            .mapValues { (_, v) -> v.sumOf { it.distanceM } * Geo.M_TO_MI }
        return buckets.map { it to (sums[it] ?: 0.0) }
    }

    fun milesSince(trips: List<TripEntity>, sinceMs: Long): Double =
        trips.filter { it.startMs >= sinceMs }.sumOf { it.distanceM } * Geo.M_TO_MI

    /** Average moving speed (mph) of a trip, or null if it never moved. */
    fun avgMovingMph(t: TripEntity): Double? =
        if (t.movingMs > 0) (t.distanceM / (t.movingMs / 1000.0)) * Geo.MPS_TO_MPH else null
}
