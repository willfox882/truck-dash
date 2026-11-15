package com.truckdash.ui.trips

import com.truckdash.core.Analytics
import com.truckdash.model.Trip
import com.truckdash.model.TripDetail
import com.truckdash.repo.DashRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class TripsSummary(
    val weekMiles: Double,
    val monthMiles: Double,
    val totalMiles: Double,
    val monthly: List<Pair<YearMonth, Double>>,
)

class TripsPresenter(private val repo: DashRepository, private val scope: CoroutineScope) {
    val trips: StateFlow<List<Trip>> =
        repo.trips.map { l -> l.sortedByDescending { it.startMs } }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val summary: StateFlow<TripsSummary> = trips.map { list ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        TripsSummary(
            weekMiles = Analytics.milesSince(list, weekStart),
            monthMiles = Analytics.milesSince(list, monthStart),
            totalMiles = Analytics.milesSince(list, 0),
            monthly = Analytics.milesByMonth(list, System.currentTimeMillis()),
        )
    }.stateIn(scope, SharingStarted.Eagerly, TripsSummary(0.0, 0.0, 0.0, emptyList()))

    val detail = MutableStateFlow<TripDetail?>(null)

    fun open(id: Long) {
        scope.launch { detail.value = repo.tripDetail(id) }
    }

    fun close() {
        detail.value = null
    }

    fun delete(id: Long) {
        scope.launch {
            repo.deleteTrip(id)
            detail.value = null
        }
    }
}
