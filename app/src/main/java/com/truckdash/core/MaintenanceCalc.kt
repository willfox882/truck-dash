package com.truckdash.core

import com.truckdash.data.MaintenanceItemEntity
import kotlin.math.max

enum class MaintLevel { OK, SOON, OVERDUE }

data class MaintStatus(
    val milesLeft: Double?,
    val daysLeft: Long?,
    /** 0..1+ share of the interval already used (worst of miles / days). */
    val used: Float,
    val level: MaintLevel,
)

object MaintenanceCalc {
    private const val DAY_MS = 86_400_000L

    fun status(item: MaintenanceItemEntity, odoMi: Double, nowMs: Long): MaintStatus {
        val milesLeft = item.intervalMiles?.let { item.lastOdoMi + it - odoMi }
        val daysLeft = item.intervalDays?.let { item.lastDateMs / DAY_MS + it - nowMs / DAY_MS }
        val usedMiles = item.intervalMiles?.takeIf { it > 0 }?.let { ((odoMi - item.lastOdoMi) / it).toFloat() } ?: 0f
        val usedDays = item.intervalDays?.takeIf { it > 0 }?.let { ((nowMs - item.lastDateMs).toDouble() / DAY_MS / it).toFloat() } ?: 0f
        val used = max(max(usedMiles, usedDays), 0f)
        val level = when {
            used >= 1f -> MaintLevel.OVERDUE
            used >= 0.85f -> MaintLevel.SOON
            else -> MaintLevel.OK
        }
        return MaintStatus(milesLeft, daysLeft, used, level)
    }
}
