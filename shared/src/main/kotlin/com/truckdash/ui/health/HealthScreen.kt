package com.truckdash.ui.health

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.truckdash.core.MaintLevel
import com.truckdash.core.MaintenanceCalc
import com.truckdash.model.FuelLog
import com.truckdash.model.MaintItem
import com.truckdash.ui.Dash
import com.truckdash.ui.DashCard
import com.truckdash.ui.StatTile
import com.truckdash.ui.charts.LineChart
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val shortDate = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun HealthScreen(vm: HealthPresenter, modifier: Modifier = Modifier) {
    val odo by vm.odometerKm.collectAsState()
    val maint by vm.maintenance.collectAsState()
    val fuel by vm.fuelLogs.collectAsState()
    val stats by vm.fuelStats.collectAsState()
    val settings by vm.settings.collectAsState()
    var dlg by remember { mutableStateOf<Dialog?>(null) }
    val now = System.currentTimeMillis()

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Truck Health", color = Dash.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }

        item {
            DashCard(Modifier.fillMaxWidth()) {
                Text("ODOMETER (GPS-tracked)", color = Dash.dim, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("%,.0f".format(odo), color = Dash.text, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                    Text("  km", color = Dash.dim, fontSize = 20.sp)
                    Box(Modifier.weight(1f))
                    TextButton(onClick = { dlg = Dialog.Odometer }) { Text("Set", fontSize = 18.sp) }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MAINTENANCE", color = Dash.dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { dlg = Dialog.AddMaint }) { Text("+ Add", fontSize = 16.sp) }
            }
        }
        items(maint, key = { "m${it.id}" }) { item ->
            MaintCard(item, odo, now, onDone = { vm.markDone(item) }, onDelete = { vm.deleteMaintenance(item) })
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FUEL", color = Dash.dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { dlg = Dialog.AddFuel }) { Text("+ Fill-up", fontSize = 16.sp) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("AVG", stats.avgLPer100?.let { "%.1f".format(it) } ?: "--", "L/100 km", Modifier.weight(1f))
                StatTile("LAST", stats.lastLPer100?.let { "%.1f".format(it) } ?: "--", "L/100 km", Modifier.weight(1f))
                StatTile("COST", stats.costPer100Km?.let { "$%.1f".format(it) } ?: "--", "per 100 km", Modifier.weight(1f))
            }
        }
        if (stats.points.size >= 2) {
            item {
                DashCard(Modifier.fillMaxWidth()) {
                    Text("FUEL USE TREND (L/100 km, lower is better)", color = Dash.dim, fontSize = 13.sp)
                    Box(Modifier.fillMaxWidth().height(110.dp).padding(top = 6.dp)) {
                        LineChart(stats.points.map { it.lPer100.toFloat() }, Dash.green)
                    }
                }
            }
        }
        items(fuel.take(30), key = { "f${it.id}" }) { f -> FuelRow(f) { vm.deleteFuel(f) } }

        item {
            DashCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Passenger mode", color = Dash.text, fontSize = 18.sp)
                        Text("Unlock Trips & Health while driving", color = Dash.dim, fontSize = 13.sp)
                    }
                    Switch(settings.passengerMode, onCheckedChange = vm::setPassengerMode)
                }
            }
            Box(Modifier.height(16.dp))
        }
    }

    when (dlg) {
        Dialog.Odometer -> NumberDialog("Set odometer (km)", odo.roundToInt().toString(), onDismiss = { dlg = null }) {
            vm.setOdometer(it); dlg = null
        }
        Dialog.AddMaint -> AddMaintDialog(onDismiss = { dlg = null }) { n, mi, d -> vm.addMaintenance(n, mi, d); dlg = null }
        Dialog.AddFuel -> AddFuelDialog(odo, onDismiss = { dlg = null }) { g, p, o, full -> vm.addFuel(g, p, o, full); dlg = null }
        null -> Unit
    }
}

private enum class Dialog { Odometer, AddMaint, AddFuel }

@Composable
private fun MaintCard(item: MaintItem, odo: Double, now: Long, onDone: () -> Unit, onDelete: () -> Unit) {
    val s = MaintenanceCalc.status(item, odo, now)
    val color = when (s.level) {
        MaintLevel.OK -> Dash.green
        MaintLevel.SOON -> Dash.amber
        MaintLevel.OVERDUE -> Dash.red
    }
    val detail = listOfNotNull(
        s.kmLeft?.let { if (it >= 0) "%,.0f km left".format(it) else "%,.0f km overdue".format(-it) },
        s.daysLeft?.let { if (it >= 0) "$it d left" else "${-it} d overdue" },
    ).joinToString("  •  ")
    DashCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, color = Dash.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = color, fontSize = 14.sp)
            }
            TextButton(onClick = onDone) { Text("Done", fontSize = 16.sp) }
            TextButton(onClick = onDelete) { Text("✕", color = Dash.dim, fontSize = 16.sp) }
        }
        LinearProgressIndicator(
            progress = { s.used.coerceIn(0f, 1f) },
            color = color, trackColor = Dash.track, gapSize = 0.dp, drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun FuelRow(f: FuelLog, onDelete: () -> Unit) {
    DashCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "%.1f L  @ $%.3f/L  =  $%.2f".format(f.litres, f.pricePerLitre, f.litres * f.pricePerLitre),
                    color = Dash.text, fontSize = 17.sp,
                )
                Text(
                    Instant.ofEpochMilli(f.dateMs).atZone(ZoneId.systemDefault()).format(shortDate) +
                        "  •  %,.0f km".format(f.odometerKm) + if (f.fullTank) "" else "  •  partial",
                    color = Dash.dim, fontSize = 13.sp,
                )
            }
            TextButton(onClick = onDelete) { Text("✕", color = Dash.dim, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun NumberDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (Double) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { NumField("Value", v) { v = it } },
        confirmButton = { TextButton(onClick = { v.toDoubleOrNull()?.let(onOk) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AddMaintDialog(onDismiss: () -> Unit, onOk: (String, Int?, Int?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add maintenance item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumField("Every … km (optional)", km) { km = it }
                NumField("Every … days (optional)", days) { days = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = km.toDoubleOrNull()?.roundToInt()
                val d = days.toDoubleOrNull()?.roundToInt()
                if (name.isNotBlank() && (m != null || d != null)) onOk(name.trim(), m, d)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddFuelDialog(odo: Double, onDismiss: () -> Unit, onOk: (Double, Double, Double, Boolean) -> Unit) {
    var litres by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var o by remember { mutableStateOf(odo.roundToInt().toString()) }
    var full by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add fill-up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Litres", litres) { litres = it }
                NumField("Price per litre ($/L)", price) { price = it }
                NumField("Odometer (km)", o) { o = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(full, { full = it })
                    Text("Filled the tank", color = Color.Unspecified)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val g = litres.toDoubleOrNull(); val p = price.toDoubleOrNull(); val od = o.toDoubleOrNull()
                if (g != null && p != null && od != null && g > 0) onOk(g, p, od, full)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
