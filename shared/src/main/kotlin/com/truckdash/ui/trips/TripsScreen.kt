package com.truckdash.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckdash.core.Analytics
import com.truckdash.core.Geo
import com.truckdash.core.HarshType
import com.truckdash.model.Trip
import com.truckdash.model.TripDetail
import com.truckdash.ui.Dash
import com.truckdash.ui.DashCard
import com.truckdash.ui.DashDialog
import com.truckdash.ui.Dot
import com.truckdash.ui.Hairline
import com.truckdash.ui.Label
import com.truckdash.ui.LocalBackHook
import com.truckdash.ui.Numeral
import com.truckdash.ui.ScreenTitle
import com.truckdash.ui.SectionHeader
import com.truckdash.ui.StatGrid
import com.truckdash.ui.StatItem
import com.truckdash.ui.TextAction
import com.truckdash.ui.charts.BarChart
import com.truckdash.ui.charts.GeoPt
import com.truckdash.ui.charts.LineChart
import com.truckdash.ui.charts.RouteMap
import com.truckdash.ui.charts.RouteMarker
import com.truckdash.ui.formatClock
import com.truckdash.ui.formatDurationShort
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
private val monthFmt = DateTimeFormatter.ofPattern("MMM")

private fun zoned(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

@Composable
fun TripsScreen(vm: TripsPresenter, modifier: Modifier = Modifier) {
    val detail by vm.detail.collectAsState()
    LocalBackHook.current(detail != null) { vm.close() }
    if (detail != null) {
        TripDetailScreen(detail!!, onBack = vm::close, onDelete = { vm.delete(detail!!.trip.id) }, modifier = modifier)
    } else {
        TripsList(vm, modifier)
    }
}

@Composable
private fun TripsList(vm: TripsPresenter, modifier: Modifier) {
    val trips by vm.trips.collectAsState()
    val sum by vm.summary.collectAsState()
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Trips", Modifier.padding(top = 18.dp)) }
        item {
            StatGrid(
                listOf(
                    StatItem("7 days", "%.0f".format(sum.weekKm), "km"),
                    StatItem("This month", "%.0f".format(sum.monthKm), "km"),
                    StatItem("All time", "%.0f".format(sum.totalKm), "km"),
                ),
            )
        }
        item {
            DashCard(Modifier.fillMaxWidth()) {
                Label("Km per month")
                Box(Modifier.fillMaxWidth().height(130.dp).padding(top = 10.dp)) {
                    BarChart(sum.monthly.map { it.second.toFloat() })
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    sum.monthly.forEach { Text(it.first.format(monthFmt), color = Dash.faint, fontSize = 11.sp) }
                }
            }
        }
        item { SectionHeader("Recent trips") }
        if (trips.isEmpty()) {
            item { Text("No trips yet. Recording starts automatically when you drive.", color = Dash.dim, fontSize = 15.sp) }
        }
        items(trips, key = { it.id }) { t ->
            TripRow(t) { vm.open(t.id) }
            Hairline()
        }
        item { Box(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TripRow(t: Trip, onClick: () -> Unit) {
    val start = zoned(t.startMs)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(start.format(dayFmt), color = Dash.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            val dur = (((t.endMs ?: System.currentTimeMillis()) - t.startMs) / 1000).coerceAtLeast(0)
            val inProgress = if (t.endMs == null) "  ·  in progress" else ""
            Text(
                "${start.format(timeFmt)}  ·  ${formatDurationShort(dur)}  ·  max ${(t.maxSpeedMps * Geo.MPS_TO_KMH).roundToInt()} km/h$inProgress",
                color = Dash.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }
        val harsh = t.harshAccel + t.harshBrake + t.harshCorner
        if (harsh > 0) {
            Dot(Dash.amber, 6)
            Text("$harsh", color = Dash.amber, fontSize = 13.sp, modifier = Modifier.padding(start = 5.dp, end = 14.dp))
        }
        Numeral("%.1f".format(t.distanceM * Geo.M_TO_KM), "km", 22.sp)
    }
}

@Composable
private fun TripDetailScreen(d: TripDetail, onBack: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    var confirm by remember { mutableStateOf(false) }
    val t = d.trip
    val speeds = d.points.map { (it.speedMps * Geo.MPS_TO_KMH).toFloat() }
    val alts = d.points.map { it.altM.toFloat() }
    val route = d.points.map { GeoPt(it.lat, it.lon) }
    val markers = d.events.map {
        RouteMarker(
            GeoPt(it.lat, it.lon),
            when (it.type) {
                HarshType.BRAKE.name -> Dash.red
                HarshType.ACCEL.name -> Dash.amber
                else -> Dash.text
            },
        )
    }
    val moving = formatClock(t.movingMs / 1000)
    val stopped = formatClock(t.stoppedMs / 1000)
    val start = zoned(t.startMs)
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextAction("‹ Trips", onBack, color = Dash.text)
                Box(Modifier.weight(1f))
                if (t.endMs != null) TextAction("Delete", { confirm = true }, color = Dash.red)
            }
            ScreenTitle(start.format(dayFmt))
            Text(start.format(timeFmt), color = Dash.dim, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
        }
        item {
            StatGrid(
                listOf(
                    StatItem("Distance", "%.1f".format(t.distanceM * Geo.M_TO_KM), "km"),
                    StatItem("Moving", moving.first, moving.second),
                    StatItem("Stopped", stopped.first, stopped.second),
                    StatItem("Average", Analytics.avgMovingKmh(t)?.roundToInt()?.toString() ?: "--", "km/h"),
                    StatItem("Max", (t.maxSpeedMps * Geo.MPS_TO_KMH).roundToInt().toString(), "km/h"),
                    StatItem("Climb", t.elevGainM.roundToInt().toString(), "m"),
                ),
            )
        }
        item {
            DashCard(Modifier.fillMaxWidth()) {
                Label("Route")
                Box(Modifier.fillMaxWidth().height(260.dp)) { RouteMap(route, markers) }
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(Dash.red, 6); Text(" Hard brake   ", color = Dash.dim, fontSize = 12.sp)
                    Dot(Dash.amber, 6); Text(" Hard launch   ", color = Dash.dim, fontSize = 12.sp)
                    Dot(Dash.text, 6); Text(" Sharp corner", color = Dash.dim, fontSize = 12.sp)
                }
            }
        }
        item {
            DashCard(Modifier.fillMaxWidth()) {
                Label("Speed  ·  peak ${speeds.maxOrNull()?.roundToInt() ?: 0} km/h")
                Box(Modifier.fillMaxWidth().height(130.dp).padding(top = 8.dp)) {
                    LineChart(speeds, minOverride = 0f, minSpan = 30f)
                }
            }
        }
        item {
            DashCard(Modifier.fillMaxWidth()) {
                Label("Elevation  ·  ${alts.minOrNull()?.roundToInt() ?: 0} to ${alts.maxOrNull()?.roundToInt() ?: 0} m")
                Box(Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp)) {
                    LineChart(alts, color = Dash.dim, minSpan = 12f)
                }
            }
        }
        item {
            DashCard(Modifier.fillMaxWidth()) {
                Label("Driving events")
                EventRow("Hard braking", t.harshBrake, Dash.red)
                Hairline()
                EventRow("Hard acceleration", t.harshAccel, Dash.amber)
                Hairline()
                EventRow("Hard cornering", t.harshCorner, Dash.text)
            }
            Box(Modifier.height(16.dp))
        }
    }
    if (confirm) {
        DashDialog(
            title = "Delete this trip?",
            onDismiss = { confirm = false },
            confirmText = "Delete",
            onConfirm = { confirm = false; onDelete() },
            danger = true,
        ) { Text("The route and stats will be removed.", color = Dash.dim) }
    }
}

@Composable
private fun EventRow(name: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(if (count > 0) color else Dash.line, 7)
        Text(name, color = Dash.text, fontSize = 16.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
        Text("$count", color = if (count > 0) Dash.text else Dash.faint, fontSize = 16.sp)
    }
}
