package com.truckdash.ui.trips

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.truckdash.core.Analytics
import com.truckdash.data.AppGraph
import com.truckdash.data.EventEntity
import com.truckdash.data.PointEntity
import com.truckdash.data.TripEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

data class TripDetail(val trip: TripEntity, val points: List<PointEntity>, val events: List<EventEntity>)

data class TripsSummary(
    val weekMiles: Double,
    val monthMiles: Double,
    val totalMiles: Double,
    val monthly: List<Pair<YearMonth, Double>>,
)

class TripsViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppGraph.db.tripDao()

    val trips: StateFlow<List<TripEntity>> =
        dao.observeTrips().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsSummary(0.0, 0.0, 0.0, emptyList()))

    val detail = MutableStateFlow<TripDetail?>(null)

    fun open(id: Long) {
        viewModelScope.launch {
            val t = dao.trip(id) ?: return@launch
            detail.value = TripDetail(t, dao.points(id), dao.events(id))
        }
    }

    fun close() {
        detail.value = null
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            dao.deleteTrip(id)
            detail.value = null
        }
    }
}
