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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.truckdash.core.PaneRules
import com.truckdash.ui.Dash
import com.truckdash.ui.PillButton
import com.truckdash.ui.PillKind
import com.truckdash.ui.dashSwitchColors
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
    // pane height in design dp (UI is laid out 440 dp wide): 782 = full 9:16, 391 = top/bottom half, 293 = short strip
    var paneH by remember { mutableStateOf(FULL_PANE_H) }

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
                Modifier.fillMaxSize().background(Color(0xFF000000)).onKeyEvent {
                    val cb = backCallback
                    if (it.key == Key.Escape && it.type == KeyEventType.KeyUp && cb != null) { cb(); true } else false
                },
            ) {
                // ---- the "truck screen" (same aspect as the 14.4" portrait head unit) ----
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(20.dp), contentAlignment = Alignment.Center) {
                    val ratio = paneH / 440f // height / width of the pane
                    val availW = maxWidth - 16.dp
                    val availH = maxHeight - 16.dp
                    val iw = minOf(availW, availH / ratio)
                    Box(
                        Modifier.width(iw + 16.dp).height(iw * ratio + 16.dp)
                            .border(8.dp, Dash.surfaceHigh, RoundedCornerShape(22.dp))
                            .padding(8.dp).clip(RoundedCornerShape(14.dp)),
                    ) {
                        TruckDashRoot(session.repo, cluster = session.cluster)
                    }
                }
                // ---- simulator controls ----
                ControlPanel(session, paneH, { paneH = it }, onReset = { session = SandboxSession.create() })
            }
        }
    }
}

private const val FULL_PANE_H = 440f * 16f / 9f
private const val HALF_PANE_H = 440f * 8f / 9f
private const val STRIP_PANE_H = 440f * 2f / 3f

private val ColorMuted = Dash.dim

@Composable
private fun ControlPanel(session: SandboxSession, paneH: Float, onPaneH: (Float) -> Unit, onReset: () -> Unit) {
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
        Modifier.width(340.dp).fillMaxHeight().background(Dash.surface).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Truck Dash Sandbox", color = Dash.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Synthetic GPS + IMU feed the real engine and UI. Nothing here touches a truck.",
            color = ColorMuted, fontSize = 12.sp,
        )

        Section("Pane shape (split-screen)")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Full 9:16" to FULL_PANE_H, "Half 9:8" to HALF_PANE_H, "Strip 3:2" to STRIP_PANE_H).forEach { (name, h) ->
                PillButton(
                    name, { onPaneH(h) }, small = true,
                    kind = if (kotlin.math.abs(paneH - h) < 1f) PillKind.Primary else PillKind.Secondary,
                )
            }
        }
        val spec = PaneRules.classify(paneH)
        LabeledSlider(
            "Pane height", "${paneH.roundToInt()} dp · " + if (spec.tiny) "tiny" else if (spec.compact) "compact" else "full",
            paneH, 200f..800f, onPaneH,
        )

        Section("Scenario")
        Scenarios.all.forEach { s ->
            Column {
                PillButton(
                    s.name, { manual = false; c.manual = false; c.startScenario(s) },
                    Modifier.fillMaxWidth(), kind = PillKind.Primary,
                )
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
            color = Dash.text, fontSize = 12.sp,
        )

        Section("Time")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1.0, 5.0, 20.0, 60.0).forEach { f ->
                PillButton(
                    "${f.roundToInt()}x", { factor = f; c.speedFactor = f }, small = true,
                    kind = if (factor == f) PillKind.Primary else PillKind.Secondary,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(if (paused) "Resume" else "Pause", { paused = !paused; c.paused = paused }, small = true)
            PillButton("Skip 6 min", { c.fastForward(360.0) }, small = true)
        }
        Text("“Skip 6 min” while parked lets the trip-end timer (5 min) fire so you can see the finished trip.", color = ColorMuted, fontSize = 11.sp)

        Section("Manual drive")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(manual, onCheckedChange = { manual = it; c.manual = it; if (it) c.stopScenario() }, colors = dashSwitchColors())
            Text("  Drive with sliders", color = Dash.text, fontSize = 13.sp)
        }
        LabeledSlider("Target speed", "${kmh.roundToInt()} km/h", kmh, 0f..160f) { kmh = it; c.manualKmh = it.toDouble() }
        LabeledSlider("Turn rate", "${yaw.roundToInt()} °/s (+ right)", yaw, -30f..30f) { yaw = it; c.manualYawDegS = it.toDouble() }
        LabeledSlider("Road grade", "%.1f %%".format(grade), grade, -10f..10f) { grade = it; c.manualGradePct = it.toDouble() }

        Section("Events")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Hard brake", { c.impulse(Seg(3.0, 0.0, brake = 8.0)) }, small = true)
            PillButton("Floor it", { c.impulse(Seg(4.0, 130.0, accel = 4.5)) }, small = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Sharp right", { c.impulse(Seg(3.0, 50.0, yawDegS = 25.0)) }, small = true)
            PillButton("Sharp left", { c.impulse(Seg(3.0, 50.0, yawDegS = -25.0)) }, small = true)
        }
        PillButton("GPS dropout 10 s", { c.gpsDropout(10) }, small = true)

        Section("App state")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(settings.passengerMode, onCheckedChange = session.repo::setPassengerMode, colors = dashSwitchColors())
            Text("  Passenger mode (no speed lock)", color = Dash.text, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(settings.drivingLock, onCheckedChange = session.repo::setDrivingLock, colors = dashSwitchColors())
            Text("  Driving lock (Trips/Health above 5 km/h)", color = Dash.text, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(settings.showCluster, onCheckedChange = session.repo::setShowCluster, colors = dashSwitchColors())
            Text("  Cluster tab (Health setting, off by default)", color = Dash.text, fontSize = 13.sp)
        }
        PillButton("Reset sandbox data", onReset, small = true)

        Section("Cluster dongle (fake ELM327)")
        val dongleScenario by session.dongle.scenario.collectAsState()
        DongleScenario.entries.forEach { sc ->
            Column {
                PillButton(
                    sc.title, { session.dongle.select(sc) }, Modifier.fillMaxWidth(), small = true,
                    kind = if (dongleScenario == sc) PillKind.Primary else PillKind.Secondary,
                )
                if (dongleScenario == sc) Text(sc.blurb, color = ColorMuted, fontSize = 11.sp)
            }
        }
        Text("The failsafe stage persists across reconnects; use Reset sandbox data to start it over.", color = ColorMuted, fontSize = 11.sp)

        HorizontalDivider(color = Dash.line)
        Text(
            "Live: %.0f km/h · long %+.2f g · lat %+.2f g · REC %s".format(
                live.speedKmh, live.longG, live.latG, if (live.recording) "on" else "off",
            ),
            color = ColorMuted, fontSize = 11.sp,
        )
        Text("Esc = back out of a trip. Window is resizable; the truck screen keeps the chosen pane shape.", color = ColorMuted, fontSize = 11.sp)
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(color = Dash.line)
    Text(title.uppercase(), color = ColorMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun LabeledSlider(label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, color = Dash.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(value, color = Dash.text, fontSize = 12.sp)
        }
        Slider(
            v, onChange, valueRange = range, modifier = Modifier.height(24.dp),
            colors = SliderDefaults.colors(thumbColor = Dash.text, activeTrackColor = Dash.text, inactiveTrackColor = Dash.line),
        )
    }
}
