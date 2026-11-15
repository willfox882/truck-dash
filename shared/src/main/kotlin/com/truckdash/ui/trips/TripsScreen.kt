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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.truckdash.core.Analytics
import com.truckdash.core.Geo
import com.truckdash.core.HarshType
import com.truckdash.model.Trip
import com.truckdash.model.TripDetail
import com.truckdash.ui.LocalBackHook
import com.truckdash.ui.Dash
import com.truckdash.ui.DashCard
import com.truckdash.ui.StatTile
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

private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d  h:mm a")
private val monthFmt = DateTimeFormatter.ofPattern("MMM")

private fun fmtDate(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateFmt)

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
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Trips", color = Dash.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("7 DAYS", "%.0f".format(sum.weekMiles), "miles", Modifier.weight(1f))
                StatTile("THIS MONTH", "%.0f".format(sum.monthMiles), "miles", Modifier.weight(1f))
                StatTile("ALL TIME", "%.0f".format(sum.totalMiles), "miles", Modifier.weight(1f))
            }
        }
        item {
            DashCard(Modifier.fillMaxWidth()) {
                Text("MILES PER MONTH", color = Dash.dim, fontSize = 13.sp)
                Box(Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp)) {
                    BarChart(sum.monthly.map { it.second.toFloat() }, Dash.cyan)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    sum.monthly.forEach { Text(it.first.format(monthFmt), color = Dash.dim, fontSize = 12.sp) }
                }
            }
        }
        if (trips.isEmpty()) {
            item { Text("No trips yet. Drive somewhere — recording starts automatically.", color = Dash.dim, fontSize = 16.sp) }
        }
        items(trips, key = { it.id }) { t -> TripRow(t) { vm.open(t.id) } }
        item { Box(Modifier.height(12.dp)) }
    }
}

@Composable
private fun TripRow(t: Trip, onClick: () -> Unit) {
    DashCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fmtDate(t.startMs), color = Dash.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                val dur = ((t.endMs ?: System.currentTimeMillis()) - t.startMs) / 1000
                Text(
                    "%.1f mi  •  ${formatDurationShort(dur)}  •  max ${(t.maxSpeedMps * Geo.MPS_TO_MPH).roundToInt()} mph"
                        .format(t.distanceM * Geo.M_TO_MI) + if (t.endMs == null) "  • in progress" else "",
                    color = Dash.dim, fontSize = 14.sp,
                )
            }
            val harshTotal = t.harshAccel + t.harshBrake + t.harshCorner
            if (harshTotal > 0) Text("$harshTotal ⚠", color = Dash.amber, fontSize = 18.sp)
        }
    }
}

@Composable
private fun TripDetailScreen(d: TripDetail, onBack: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    var confirm by remember { mutableStateOf(false) }
    val t = d.trip
    val speeds = d.points.map { (it.speedMps * Geo.MPS_TO_MPH).toFloat() }
    val alts = d.points.map { (it.altM * Geo.M_TO_FT).toFloat() }
    val route = d.points.map { GeoPt(it.lat, it.lon) }
    val markers = d.events.map {
        RouteMarker(
            GeoPt(it.lat, it.lon),
            when (it.type) {
                HarshType.BRAKE.name -> Dash.red
                HarshType.ACCEL.name -> Dash.amber
                else -> androidx.compose.ui.graphics.Color(0xFFB388FF)
            },
        )
    }
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", fontSize = 18.sp) }
                Box(Modifier.weight(1f))
                TextButton(onClick = { confirm = true }) { Text("Delete", color = Dash.red, fontSize = 16.sp) }
            }
            Text(fmtDate(t.startMs), color = Dash.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("DISTANCE", "%.1f".format(t.distanceM * Geo.M_TO_MI), "miles", Modifier.weight(1f))
                StatTile("MOVING", formatClock(t.movingMs / 1000).first, formatClock(t.movingMs / 1000).second, Modifier.weight(1f))
                StatTile("STOPPED", formatClock(t.stoppedMs / 1000).first, formatClock(t.stoppedMs / 1000).second, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("AVG", Analytics.avgMovingMph(t)?.roundToInt()?.toString() ?: "--", "mph", Modifier.weight(1f))
                StatTile("MAX", (t.maxSpeedMps * Geo.MPS_TO_MPH).roundToInt().toString(), "mph", Modifier.weight(1f))
                StatTile("CLIMB", (t.elevGainM * Geo.M_TO_FT).roundToInt().toString(), "ft", Modifier.weight(1f))
            }
        }
        item {
            DashCard(Modifier.fillMaxWidth()) {
                Text("ROUTE", color = Dash.dim, fontSize = 13.sp)
                Box(Modifier.fillMaxWidth().height(260.dp)) { RouteMap(route, markers) }
                Text(
                    "green start · red end · red dot brake · amber accel · purple corner",
                    color = Dash.dim, fontSize = 12.sp,
                )
            }
        }
        item {
            DashCard(Modifier.fillMaxWidth()) {
                Text("SPEED (mph)  ·  peak ${speeds.maxOrNull()?.roundToInt() ?: 0}", color = Dash.dim, fontSize = 13.sp)
                Box(Modifier.fillMaxWidth().height(130.dp).padding(top = 6.dp)) { LineChart(speeds, Dash.cyan, minOverride = 0f, minSpan = 20f) }
            }
        }
        item {
            DashCard(Modifier.fillMaxWidth()) {
                Text("ELEVATION (ft)  ·  ${alts.minOrNull()?.roundToInt() ?: 0} to ${alts.maxOrNull()?.roundToInt() ?: 0}", color = Dash.dim, fontSize = 13.sp)
                Box(Modifier.fillMaxWidth().height(130.dp).padding(top = 6.dp)) { LineChart(alts, Dash.green, minSpan = 40f) }
            }
        }
        item {
            DashCard(Modifier.fillMaxWidth()) {
                Text("DRIVING EVENTS", color = Dash.dim, fontSize = 13.sp)
                Text("Hard braking: ${t.harshBrake}", color = Dash.text, fontSize = 18.sp)
                Text("Hard acceleration: ${t.harshAccel}", color = Dash.text, fontSize = 18.sp)
                Text("Hard cornering: ${t.harshCorner}", color = Dash.text, fontSize = 18.sp)
            }
            Box(Modifier.height(16.dp))
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete this trip?") },
            text = { Text("The route and stats will be removed permanently.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Delete", color = Dash.red) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
