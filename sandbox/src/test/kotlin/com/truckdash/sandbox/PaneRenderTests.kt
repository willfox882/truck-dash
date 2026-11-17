package com.truckdash.sandbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import com.truckdash.ui.Dash
import com.truckdash.ui.DashTab
import com.truckdash.ui.DesignScale
import com.truckdash.ui.ParkedOnly
import com.truckdash.ui.health.HealthPresenter
import com.truckdash.ui.health.ModeSettingsCard
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.truckdash.ui.TruckDashRoot
import com.truckdash.ui.cluster.DonglePickerDialog
import androidx.compose.foundation.layout.Box
import com.truckdash.ui.TruckDashTheme
import com.truckdash.obd.ClusterPlatform
import com.truckdash.obd.ClusterPresenter
import com.truckdash.obd.ObdLink
import com.truckdash.obd.PairedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Renders every screen at the three pane shapes (plus 440 x 300 dp) and checks the pixels: not blank, nothing drawn in the
 * outer gutter (clipped or overflowing content), the tab bar is visible, and no hue outside red / amber / green appears.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PaneRenderTests {
    companion object {
        @JvmStatic @BeforeClass fun fonts() { loadDashFonts() }
        // width px x height px; 810 px = 440 dp, so these are 440 x 782, 440 x 391, 440 x 293 and 440 x 300 dp
        private val shapes = listOf("full" to (810 to 1440), "half" to (810 to 720), "strip" to (810 to 540), "dp300" to (810 to 553))
    }

    private val session = SandboxSession.create(nowMs = 1_760_000_000_000L).also {
        it.repo.setPassengerMode(true)
        it.controller.startScenario(Scenarios.hardEvents)
        it.controller.runFor(52.0)
    }
    private val hardTrip by lazy {
        session.controller.stopScenario(); session.controller.runFor(2.0)
        session.store.tripsFlow.value.first { it.harshBrake > 0 }.id
    }

    private fun render(w: Int, h: Int, content: @Composable () -> Unit): IntArray {
        val scene = ImageComposeScene(width = w, height = h, density = Density(1f)) { TruckDashTheme { content() } }
        for (i in 0 until 8) scene.render(i * 120_000_000L)
        val img = scene.render(2_000_000_000L)
        val px = IntArray(w * h)
        img.toComposeImageBitmap().readPixels(px, 0, 0, w, h)
        scene.close()
        return px
    }

    private fun bright(p: Int) = ((p shr 16) and 255) > 0x80 || ((p shr 8) and 255) > 0x80 || (p and 255) > 0x80

    private fun hueOk(p: Int): Boolean {
        val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
        if (mx - mn < 40) return true // grey
        val d = (mx - mn).toFloat()
        var hue = when (mx) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        if (hue < 0) hue += 360f
        fun near(c: Float) = minOf(kotlin.math.abs(hue - c), 360f - kotlin.math.abs(hue - c)) < 16f
        return near(3f) || near(37f) || near(147f)
    }

    private fun check(name: String, px: IntArray, w: Int, h: Int, tabBarPx: Int?, gutter: Boolean = true) {
        assertTrue("$name blank", px.count { bright(it) } > 200)
        val bad = px.count { !hueOk(it) }
        assertTrue("$name has $bad off-palette pixels", bad == 0)
        if (gutter) {
            for (y in 0 until h) for (x in listOf(0, 1, 2, 3, w - 4, w - 3, w - 2, w - 1)) {
                assertTrue("$name content touches the edge at $x,$y", !bright(px[y * w + x]))
            }
        }
        if (tabBarPx != null) {
            var found = false
            for (y in h - tabBarPx until h) for (x in 0 until w) if (bright(px[y * w + x])) found = true
            assertTrue("$name tab bar missing", found)
        }
    }

    private fun tabPx(h: Int) = when { h >= 1000 -> 60; h >= 600 -> 48; else -> 40 }.let { (it * 810 / 440f).toInt() - 8 }

    private fun all(screen: String, tab: DashTab, trip: Long? = null) {
        for ((shape, size) in shapes) {
            val (w, h) = size
            val px = render(w, h) { TruckDashRoot(session.repo, tab, trip) }
            check("$screen@$shape", px, w, h, tabPx(h))
        }
    }

    @Test fun live() = all("live", DashTab.Live)
    @Test fun trips() = all("trips", DashTab.Trips)
    @Test fun health() = all("health", DashTab.Health)
    @Test fun tripDetail() = all("trip-detail", DashTab.Trips, hardTrip)

    @Test fun healthSwitches() {
        for ((shape, size) in shapes) {
            val (w, h) = size
            val px = render(w, h) {
                DesignScale {
                    Column(Modifier.fillMaxSize().background(Dash.bg).padding(16.dp)) {
                        ModeSettingsCard(session.repo.settings.value, HealthPresenter(session.repo, kotlinx.coroutines.MainScope()))
                    }
                }
            }
            check("health-switches@$shape", px, w, h, null)
        }
    }

    @Test fun parkedOnly() {
        for ((shape, size) in shapes) {
            val (w, h) = size
            val px = render(w, h) { DesignScale { Column(Modifier.fillMaxSize().background(Dash.bg)) { ParkedOnly(87f) } } }
            check("parked@$shape", px, w, h, null)
        }
    }

    @Test fun clusterStatesRenderAtEveryShape() {
        for ((shape, size) in shapes) {
            val (w, h) = size
            for ((name, st) in ClusterPreview.states) {
                check("cluster-$name@$shape", render(w, h) { ClusterPreview.Shell(st) }, w, h, tabPx(h))
            }
        }
    }

    @Test fun clusterPickerRenders() {
        for ((shape, size) in shapes) {
            val (w, h) = size
            val px = render(w, h) {
                DesignScale {
                    Box(Modifier.fillMaxSize().background(Dash.bg)) {
                        DonglePickerDialog(session.cluster.pairedDevices(), null, {}, {})
                    }
                }
            }
            check("cluster-picker@$shape", px, w, h, null, gutter = false)
        }
    }

    @Test fun fourthTabAppearsOnlyWhenTheSettingIsOn() {
        val s = SandboxSession.create(seedHistory = false, nowMs = 1_760_000_000_000L)
        val w = 810; val h = 1440
        fun rightEdgeLabel(px: IntArray): Boolean {
            for (y in h - tabPx(h) until h) for (x in (w * 0.9).toInt() until w - 8) if (((px[y * w + x] shr 16) and 255) > 0x50) return true // unselected tab labels are the faint grey
            return false
        }
        val hidden = render(w, h) { TruckDashRoot(s.repo, DashTab.Live, cluster = s.cluster) }
        assertFalse("Cluster tab must be hidden by default", rightEdgeLabel(hidden))
        s.repo.setShowCluster(true)
        val shown = render(w, h) { TruckDashRoot(s.repo, DashTab.Live, cluster = s.cluster) }
        assertTrue("Cluster tab should show when enabled", rightEdgeLabel(shown))
        assertEquals("no Bluetooth until the tab is opened", 0, s.dongle.opens)
    }

    /** Wraps a platform and counts EVERY call (including reading the active flag). */
    private class CountingPlatform(private val inner: ClusterPlatform) : ClusterPlatform {
        val calls = AtomicInteger()
        override val appActive: StateFlow<Boolean> get() { calls.incrementAndGet(); return inner.appActive }
        override fun hasPermission(): Boolean { calls.incrementAndGet(); return inner.hasPermission() }
        override fun requestPermission(onResult: (Boolean) -> Unit) { calls.incrementAndGet(); inner.requestPermission(onResult) }
        override fun canRequestPermission(): Boolean { calls.incrementAndGet(); return inner.canRequestPermission() }
        override fun openPermissionSettings() { calls.incrementAndGet(); inner.openPermissionSettings() }
        override fun newLink(preferredAddress: String?): ObdLink { calls.incrementAndGet(); return inner.newLink(preferredAddress) }
        override fun pairedDevices(): List<PairedDevice> { calls.incrementAndGet(); return inner.pairedDevices() }
    }

    @Test fun clusterSettingOffMakesZeroPlatformCalls() {
        val s = SandboxSession.create(seedHistory = false, nowMs = 1_760_000_000_000L)
        assertFalse(s.repo.settings.value.showCluster)
        val viaPlatform = CountingPlatform(s.cluster)
        val viaPresenter = CountingPlatform(s.cluster)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val presenter = ClusterPresenter(viaPresenter, s.repo.settings, s.repo::setObdAddress, scope)
        for (tab in listOf(DashTab.Live, DashTab.Trips, DashTab.Health)) {
            for ((_, size) in shapes) {
                val (w, h) = size
                render(w, h) { TruckDashRoot(s.repo, tab, cluster = viaPlatform) }
                render(w, h) { TruckDashRoot(s.repo, tab, clusterPresenter = presenter) }
            }
        }
        // even asking for the Cluster tab while the setting is off must not reach Bluetooth
        render(810, 1440) { TruckDashRoot(s.repo, DashTab.Cluster, cluster = viaPlatform) }
        render(810, 1440) { TruckDashRoot(s.repo, DashTab.Cluster, clusterPresenter = presenter) }
        assertEquals("platform calls with the setting off", 0, viaPlatform.calls.get())
        assertEquals("platform calls with the setting off (shared presenter)", 0, viaPresenter.calls.get())
        assertEquals(0, s.dongle.opens)

        // control: turning the setting on and opening the tab does reach the platform (so the check above can fail)
        s.repo.setShowCluster(true)
        render(810, 1440) { TruckDashRoot(s.repo, DashTab.Cluster, cluster = viaPlatform) }
        assertTrue("the counter works", viaPlatform.calls.get() > 0)
        scope.cancel()
    }

    @Test fun liveWithoutFixAndParkedStatesRender() {
        val idle = SandboxSession.create(seedHistory = false)
        for ((shape, size) in shapes) {
            val (w, h) = size
            check("idle@$shape", render(w, h) { TruckDashRoot(idle.repo, DashTab.Live) }, w, h, tabPx(h))
        }
    }
}
