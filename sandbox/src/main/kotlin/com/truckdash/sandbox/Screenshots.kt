package com.truckdash.sandbox

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.truckdash.ui.Dash
import com.truckdash.ui.DashDialog
import com.truckdash.ui.DashTab
import com.truckdash.ui.DesignScale
import com.truckdash.ui.ParkedOnly
import com.truckdash.ui.health.HealthPresenter
import com.truckdash.ui.health.ModeSettingsCard
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.truckdash.ui.dashFieldColors
import com.truckdash.ui.TruckDashRoot
import com.truckdash.ui.cluster.DonglePickerDialog
import com.truckdash.ui.TruckDashTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Headless renders of the real UI (no window needed): ./gradlew :sandbox:screenshots
 * Output PNGs go to the directory given as the first argument.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    loadDashFonts()
    val out = File(args.firstOrNull() ?: "build/screenshots").apply { mkdirs() }
    val session = SandboxSession.create()
    val c = session.controller

    fun shot(name: String, w: Int, h: Int, tab: DashTab, tripId: Long? = null) {
        val scene = ImageComposeScene(width = w, height = h, density = Density(1f)) {
            TruckDashTheme { TruckDashRoot(session.repo, tab, tripId) }
        }
        for (i in 0 until 8) scene.render(i * 120_000_000L) // let animations/flows settle
        val img = scene.render(2_000_000_000L)
        File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        println("wrote $name.png (${w}x$h)")
    }

    // 1) mid-drive: highway at speed with the G-ball moving
    c.startScenario(Scenarios.hardEvents)
    c.runFor(52.0) // just after the hard launch, into the corner
    shot("live-driving", 810, 1440, DashTab.Live)

    c.stopScenario()
    c.runFor(2.0)
    // 2) parked views (passenger mode lets us see Trips/Health regardless)
    session.repo.setPassengerMode(true)
    val hardTrip = session.store.tripsFlow.value.firstOrNull { it.harshBrake > 0 }?.id
    shot("trips", 810, 2000, DashTab.Trips)
    shot("trip-detail", 810, 2300, DashTab.Trips, hardTrip)
    shot("health", 810, 3000, DashTab.Health)

    // 3) parked & idle live screen
    val idle = SandboxSession.create(seedHistory = false)
    idle.controller.runFor(8.0)
    val s2 = ImageComposeScene(width = 810, height = 1440, density = Density(1f)) {
        TruckDashTheme { TruckDashRoot(idle.repo, DashTab.Live) }
    }
    for (i in 0 until 8) s2.render(i * 120_000_000L)
    File(out, "live-parked.png").writeBytes(s2.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    s2.close()

    // 4) split-screen panes: every screen at full (9:16), half (9:8) and strip (3:2) shapes
    fun paneShot(name: String, w: Int, h: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        val sc = ImageComposeScene(width = w, height = h, density = Density(1f)) { TruckDashTheme { content() } }
        for (i in 0 until 8) sc.render(i * 120_000_000L)
        File(out, "$name.png").writeBytes(sc.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        sc.close()
        println("wrote $name.png (${w}x$h)")
    }
    val panes = listOf("full" to 1440, "half" to 720, "strip" to 540)
    for ((shape, h) in panes) {
        c.startScenario(Scenarios.hardEvents); c.runFor(52.0)
        paneShot("pane-$shape-live", 810, h) { TruckDashRoot(session.repo, DashTab.Live) }
        c.stopScenario(); c.runFor(2.0)
        paneShot("pane-$shape-trips", 810, h) { TruckDashRoot(session.repo, DashTab.Trips) }
        paneShot("pane-$shape-trip-detail", 810, h) { TruckDashRoot(session.repo, DashTab.Trips, hardTrip) }
        paneShot("pane-$shape-health", 810, h) { TruckDashRoot(session.repo, DashTab.Health) }
        paneShot("pane-$shape-health-switches", 810, h) {
            DesignScale {
                Column(Modifier.fillMaxSize().background(Dash.bg).padding(16.dp)) {
                    ModeSettingsCard(session.repo.settings.value, HealthPresenter(session.repo, kotlinx.coroutines.MainScope()))
                }
            }
        }
        paneShot("pane-$shape-parked-only", 810, h) { DesignScale { Column(Modifier.fillMaxSize().background(Dash.bg)) { ParkedOnly(87f) } } }
        paneShot("pane-$shape-dialog", 810, h) {
            DesignScale {
                Box(Modifier.fillMaxSize().background(Dash.bg)) {
                    DashDialog("Add fill-up", {}, "Save", {}) {
                        for (l in listOf("Litres", "Price per litre ($/L)", "Odometer (km)")) {
                            OutlinedTextField("12.5", {}, label = { Text(l) }, singleLine = true, colors = dashFieldColors(), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
    // 5) Cluster tab (hidden by default; shown here as if switched on): every state at every pane shape
    for ((shape, h) in panes) {
        for ((name, st) in ClusterPreview.states) paneShot("pane-$shape-cluster-$name", 810, h) { ClusterPreview.Shell(st) }
        paneShot("pane-$shape-cluster-picker", 810, h) {
            DesignScale {
                Box(Modifier.fillMaxSize().background(Dash.bg)) {
                    DonglePickerDialog(session.cluster.pairedDevices(), "00:1D:A5:68:98:8B", {}, {})
                }
            }
        }
    }
    println("done")
}
