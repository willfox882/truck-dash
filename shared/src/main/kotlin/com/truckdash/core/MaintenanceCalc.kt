package com.truckdash.core

import com.truckdash.model.MaintItem
import kotlin.math.max

enum class MaintLevel { OK, SOON, OVERDUE }

data class MaintStatus(
    val kmLeft: Double?,
    val daysLeft: Long?,
    /** 0..1+ share of the interval already used (worst of km / days). */
    val used: Float,
    val level: MaintLevel,
)

object MaintenanceCalc {
    private const val DAY_MS = 86_400_000L

    fun status(item: MaintItem, odoKm: Double, nowMs: Long): MaintStatus {
        val kmLeft = item.intervalKm?.let { item.lastOdoKm + it - odoKm }
        val daysLeft = item.intervalDays?.let { item.lastDateMs / DAY_MS + it - nowMs / DAY_MS }
        val usedKm = item.intervalKm?.takeIf { it > 0 }?.let { ((odoKm - item.lastOdoKm) / it).toFloat() } ?: 0f
        val usedDays = item.intervalDays?.takeIf { it > 0 }?.let { ((nowMs - item.lastDateMs).toDouble() / DAY_MS / it).toFloat() } ?: 0f
        val used = max(max(usedKm, usedDays), 0f)
        val level = when {
            used >= 1f -> MaintLevel.OVERDUE
            used >= 0.85f -> MaintLevel.SOON
            else -> MaintLevel.OK
        }
        return MaintStatus(kmLeft, daysLeft, used, level)
    }
}
