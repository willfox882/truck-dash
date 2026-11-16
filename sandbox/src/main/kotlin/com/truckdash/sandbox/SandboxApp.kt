package com.truckdash.sandbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckdash.ui.Dash
import com.truckdash.ui.LocalBackHook
import com.truckdash.ui.TruckDashRoot
import com.truckdash.ui.TruckDashTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SandboxApp() {
    var session by remember { mutableStateOf(SandboxSession.create()) }
    var backCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(session) {
        withContext(Dispatchers.Default) {
            var last = System.nanoTime()
            while (isActive) {
                delay(33)
                val now = System.nanoTime()
                session.controller.tick((now - last) / 1e9)
                last = now
            }
        }
    }

    TruckDashTheme {
        CompositionLocalProvider(
            LocalBackHook provides { enabled, onBack -> SideEffect { backCallback = if (enabled) onBack else null } },
        ) {
            Row(
                Modifier.fillMaxSize().background(Color(0xFF05070A)).onKeyEvent {
                    val cb = backCallback
                    if (it.key == Key.Escape && it.type == KeyEventType.KeyUp && cb != null) { cb(); true } else false
                },
            ) {
                // ---- the "truck screen" (same aspect as the 14.4" portrait head unit) ----
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(20.dp), contentAlignment = Alignment.Center) {
                    val h = minOf(maxHeight, maxWidth * 16f / 9f)
                    Box(
                        Modifier.width(h * 9f / 16f).height(h)
                            .border(8.dp, Color(0xFF2A323C), RoundedCornerShape(22.dp))
                            .padding(8.dp).clip(RoundedCornerShape(14.dp)),
                    ) {
                        TruckDashRoot(session.repo)
                    }
                }
                // ---- simulator controls ----
                ControlPanel(session, onReset = { session = SandboxSession.create() })
            }
        }
    }
}

private val ColorMuted = Color(0xFF8A9AAD)

@Composable
private fun ControlPanel(session: SandboxSession, onReset: () -> Unit) {
    val c = session.controller
    val status by c.status.collectAsState()
    val live by session.repo.live.collectAsState()
    val settings by session.repo.settings.collectAsState()
    var factor by remember(session) { mutableStateOf(1.0) }
    var paused by remember(session) { mutableStateOf(false) }
    var manual by remember(session) { mutableStateOf(false) }
    var kmh by remember(session) { mutableStateOf(0f) }
    var yaw by remember(session) { mutableStateOf(0f) }
    var grade by remember(session) { mutableStateOf(0f) }

    Column(
        Modifier.width(340.dp).fillMaxHeight().background(Color(0xFF0D1218)).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Truck Dash Sandbox", color = Dash.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Synthetic GPS + IMU feed the real engine and UI. Nothing here touches a truck.",
            color = ColorMuted, fontSize = 12.sp,
        )

        Section("Scenario")
        Scenarios.all.forEach { s ->
            Column {
                Button(
                    onClick = { manual = false; c.manual = false; c.startScenario(s) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(s.name) }
                Text(s.blurb, color = ColorMuted, fontSize = 11.sp)
            }
        }
        Text(
            buildString {
                append(if (manual) "Manual drive" else status.scenario ?: "No scenario (parked)")
                if (!manual && status.segCount > 0) append("  ·  step ${status.segIndex + 1}/${status.segCount}")
                append("  ·  sim ${status.simSeconds / 60}:%02d".format(status.simSeconds % 60))
                if (status.gpsLost) append("  ·  GPS LOST")
            },
            color = Dash.cyan, fontSize = 12.sp,
        )

        Section("Time")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1.0, 5.0, 20.0, 60.0).forEach { f ->
                FilterChip(
                    selected = factor == f,
                    onClick = { factor = f; c.speedFactor = f },
                    label = { Text("${f.roundToInt()}×") },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { paused = !paused; c.paused = paused }) { Text(if (paused) "Resume" else "Pause") }
            OutlinedButton(onClick = { c.fastForward(360.0) }) { Text("Skip 6 min") }
        }
        Text("“Skip 6 min” while parked lets the trip-end timer (5 min) fire so you can see the finished trip.", color = ColorMuted, fontSize = 11.sp)

        Section("Manual drive")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(manual, onCheckedChange = { manual = it; c.manual = it; if (it) c.stopScenario() })
            Text("  Drive with sliders", color = Dash.text, fontSize = 13.sp)
        }
        LabeledSlider("Target speed", "${kmh.roundToInt()} km/h", kmh, 0f..160f) { kmh = it; c.manualKmh = it.toDouble() }
        LabeledSlider("Turn rate", "${yaw.roundToInt()} °/s (+ right)", yaw, -30f..30f) { yaw = it; c.manualYawDegS = it.toDouble() }
        LabeledSlider("Road grade", "%.1f %%".format(grade), grade, -10f..10f) { grade = it; c.manualGradePct = it.toDouble() }

        Section("Events")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { c.impulse(Seg(3.0, 0.0, brake = 8.0)) }) { Text("Hard brake") }
            OutlinedButton(onClick = { c.impulse(Seg(4.0, 130.0, accel = 4.5)) }) { Text("Floor it") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { c.impulse(Seg(3.0, 50.0, yawDegS = 25.0)) }) { Text("Sharp right") }
            OutlinedButton(onClick = { c.impulse(Seg(3.0, 50.0, yawDegS = -25.0)) }) { Text("Sharp left") }
        }
        OutlinedButton(onClick = { c.gpsDropout(10) }) { Text("GPS dropout 10 s") }

        Section("App state")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(settings.passengerMode, onCheckedChange = session.repo::setPassengerMode)
            Text("  Passenger mode (no speed lock)", color = Dash.text, fontSize = 13.sp)
        }
        OutlinedButton(onClick = onReset) { Text("Reset sandbox data") }

        HorizontalDivider(color = Dash.track)
        Text(
            "Live: %.0f km/h · long %+.2f g · lat %+.2f g · REC %s".format(
                live.speedKmh, live.longG, live.latG, if (live.recording) "on" else "off",
            ),
            color = ColorMuted, fontSize = 11.sp,
        )
        Text("Esc = back out of a trip. Window is resizable; the truck screen keeps its 9:16 shape.", color = ColorMuted, fontSize = 11.sp)
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(color = Dash.track)
    Text(title.uppercase(), color = ColorMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun LabeledSlider(label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, color = Dash.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(value, color = Dash.cyan, fontSize = 12.sp)
        }
        Slider(v, onChange, valueRange = range, modifier = Modifier.height(24.dp))
    }
}
