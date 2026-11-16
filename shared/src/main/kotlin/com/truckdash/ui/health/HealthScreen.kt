package com.truckdash.ui.health

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckdash.core.MaintLevel
import com.truckdash.core.MaintenanceCalc
import com.truckdash.model.FuelLog
import com.truckdash.model.MaintItem
import com.truckdash.ui.Dash
import com.truckdash.ui.DashCard
import com.truckdash.ui.DashDialog
import com.truckdash.ui.Hairline
import com.truckdash.ui.Label
import com.truckdash.ui.Numeral
import com.truckdash.ui.PillButton
import com.truckdash.ui.ScreenTitle
import com.truckdash.ui.SectionHeader
import com.truckdash.ui.StatGrid
import com.truckdash.ui.StatItem
import com.truckdash.ui.TextAction
import com.truckdash.ui.charts.LineChart
import com.truckdash.ui.dashCheckboxColors
import com.truckdash.ui.dashFieldColors
import com.truckdash.ui.dashSwitchColors
import com.truckdash.ui.pane
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val shortDate = DateTimeFormatter.ofPattern("MMM d")

private enum class Dlg { Odometer, AddMaint, AddFuel }

@Composable
fun HealthScreen(vm: HealthPresenter, modifier: Modifier = Modifier) {
    val odo by vm.odometerKm.collectAsState()
    val maint by vm.maintenance.collectAsState()
    val fuel by vm.fuelLogs.collectAsState()
    val stats by vm.fuelStats.collectAsState()
    val settings by vm.settings.collectAsState()
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    val now = System.currentTimeMillis()

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = pane(20.dp, 16.dp)),
        verticalArrangement = Arrangement.spacedBy(pane(12.dp, 8.dp)),
    ) {
        item { ScreenTitle("Health", Modifier.padding(top = pane(18.dp, 10.dp))) }

        item {
            DashCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Label("Odometer")
                        Numeral("%,.0f".format(odo), "km", pane(54.sp, 40.sp), modifier = Modifier.padding(top = 4.dp))
                        Text("Tracked by GPS", color = Dash.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    PillButton("Set", { dlg = Dlg.Odometer }, small = true)
                }
            }
        }

        item {
            SectionHeader("Maintenance") { TextAction("Add", { dlg = Dlg.AddMaint }, color = Dash.text) }
        }
        item {
            DashCard(Modifier.fillMaxWidth(), padding = 0) {
                if (maint.isEmpty()) Text("Nothing scheduled.", color = Dash.dim, modifier = Modifier.padding(18.dp))
                maint.forEachIndexed { i, item ->
                    if (i > 0) Hairline()
                    MaintRow(item, odo, now, onDone = { vm.markDone(item) }, onDelete = { vm.deleteMaintenance(item) })
                }
            }
        }

        item { SectionHeader("Fuel") { TextAction("Add fill-up", { dlg = Dlg.AddFuel }, color = Dash.text) } }
        item {
            StatGrid(
                listOf(
                    StatItem("Average", stats.avgLPer100?.let { "%.1f".format(it) } ?: "--", sub = "L/100 km"),
                    StatItem("Last fill", stats.lastLPer100?.let { "%.1f".format(it) } ?: "--", sub = "L/100 km"),
                    StatItem("Cost", stats.costPer100Km?.let { "$%.0f".format(it) } ?: "--", sub = "per 100 km"),
                ),
            )
        }
        if (stats.points.size >= 2) {
            item {
                DashCard(Modifier.fillMaxWidth()) {
                    Label("Fuel use, L/100 km")
                    Box(Modifier.fillMaxWidth().height(pane(100.dp, 80.dp, 64.dp)).padding(top = 8.dp)) {
                        LineChart(stats.points.map { it.lPer100.toFloat() }, minSpan = 4f)
                    }
                }
            }
        }
        if (fuel.isNotEmpty()) {
            item {
                DashCard(Modifier.fillMaxWidth(), padding = 0) {
                    fuel.take(30).forEachIndexed { i, f ->
                        if (i > 0) Hairline()
                        FuelRow(f) { vm.deleteFuel(f) }
                    }
                }
            }
        }

        item {
            DashCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Passenger mode", color = Dash.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text("Keep Trips and Health open while driving", color = Dash.dim, fontSize = 13.sp)
                    }
                    Switch(settings.passengerMode, onCheckedChange = vm::setPassengerMode, colors = dashSwitchColors())
                }
            }
            Box(Modifier.height(16.dp))
        }
    }

    when (dlg) {
        Dlg.Odometer -> NumberDialog("Set odometer (km)", odo.roundToInt().toString(), onDismiss = { dlg = null }) {
            vm.setOdometer(it); dlg = null
        }
        Dlg.AddMaint -> AddMaintDialog(onDismiss = { dlg = null }) { n, km, d -> vm.addMaintenance(n, km, d); dlg = null }
        Dlg.AddFuel -> AddFuelDialog(odo, onDismiss = { dlg = null }) { l, p, o, full -> vm.addFuel(l, p, o, full); dlg = null }
        null -> Unit
    }
}

@Composable
private fun MaintRow(item: MaintItem, odo: Double, now: Long, onDone: () -> Unit, onDelete: () -> Unit) {
    val s = MaintenanceCalc.status(item, odo, now)
    val color = when (s.level) {
        MaintLevel.OK -> Dash.dim
        MaintLevel.SOON -> Dash.amber
        MaintLevel.OVERDUE -> Dash.red
    }
    val bar = when (s.level) {
        MaintLevel.OK -> Dash.text
        MaintLevel.SOON -> Dash.amber
        MaintLevel.OVERDUE -> Dash.red
    }
    val detail = listOfNotNull(
        s.kmLeft?.let { if (it >= 0) "%,.0f km left".format(it) else "%,.0f km overdue".format(-it) },
        s.daysLeft?.let { if (it >= 0) "$it days left" else "${-it} days overdue" },
    ).joinToString("  ·  ")
    Column(Modifier.fillMaxWidth().padding(horizontal = pane(18.dp, 14.dp), vertical = pane(14.dp, 9.dp))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, color = Dash.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(detail, color = color, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            PillButton("Done", onDone, small = true)
            TextAction("×", onDelete, color = Dash.faint)
        }
        // slim progress: neutral until it needs attention
        Box(Modifier.fillMaxWidth().padding(top = pane(10.dp, 6.dp)).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Dash.line)) {
            Box(Modifier.fillMaxWidth(s.used.coerceIn(0f, 1f)).height(3.dp).background(bar))
        }
    }
}

@Composable
private fun FuelRow(f: FuelLog, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = pane(18.dp, 14.dp), top = pane(12.dp, 8.dp), bottom = pane(12.dp, 8.dp), end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "%.1f L  ·  $%.3f/L".format(f.litres, f.pricePerLitre),
                color = Dash.text, fontSize = 16.sp,
            )
            Text(
                Instant.ofEpochMilli(f.dateMs).atZone(ZoneId.systemDefault()).format(shortDate) +
                    "  ·  %,.0f km".format(f.odometerKm) + if (f.fullTank) "" else "  ·  partial",
                color = Dash.dim, fontSize = 13.sp,
            )
        }
        Text("$%.2f".format(f.litres * f.pricePerLitre), color = Dash.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        TextAction("×", onDelete, color = Dash.faint)
    }
}

@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = dashFieldColors(), shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (Double) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    DashDialog(title, onDismiss, "OK", { v.toDoubleOrNull()?.let(onOk) }) { NumField("Value", v) { v = it } }
}

@Composable
private fun AddMaintDialog(onDismiss: () -> Unit, onOk: (String, Int?, Int?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    DashDialog("Add maintenance item", onDismiss, "Add", {
        val k = km.toDoubleOrNull()?.roundToInt()
        val d = days.toDoubleOrNull()?.roundToInt()
        if (name.isNotBlank() && (k != null || d != null)) onOk(name.trim(), k, d)
    }) {
        OutlinedTextField(
            name, { name = it }, label = { Text("Name") }, singleLine = true,
            colors = dashFieldColors(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
        )
        NumField("Every ... km (optional)", km) { km = it }
        NumField("Every ... days (optional)", days) { days = it }
    }
}

@Composable
private fun AddFuelDialog(odo: Double, onDismiss: () -> Unit, onOk: (Double, Double, Double, Boolean) -> Unit) {
    var litres by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var o by remember { mutableStateOf(odo.roundToInt().toString()) }
    var full by remember { mutableStateOf(true) }
    DashDialog("Add fill-up", onDismiss, "Save", {
        val l = litres.toDoubleOrNull(); val p = price.toDoubleOrNull(); val od = o.toDoubleOrNull()
        if (l != null && p != null && od != null && l > 0) onOk(l, p, od, full)
    }) {
        NumField("Litres", litres) { litres = it }
        NumField("Price per litre ($/L)", price) { price = it }
        NumField("Odometer (km)", o) { o = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(full, { full = it }, colors = dashCheckboxColors())
            Text("Filled the tank", color = Dash.text)
        }
    }
}
