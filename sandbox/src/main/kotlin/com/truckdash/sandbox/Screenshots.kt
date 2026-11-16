package com.truckdash.sandbox

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.truckdash.ui.DashTab
import com.truckdash.ui.TruckDashRoot
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
    shot("health", 810, 2300, DashTab.Health)

    // 3) parked & idle live screen
    val idle = SandboxSession.create(seedHistory = false)
    idle.controller.runFor(8.0)
    val s2 = ImageComposeScene(width = 810, height = 1440, density = Density(1f)) {
        TruckDashTheme { TruckDashRoot(idle.repo, DashTab.Live) }
    }
    for (i in 0 until 8) s2.render(i * 120_000_000L)
    File(out, "live-parked.png").writeBytes(s2.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    s2.close()
    println("done")
}
