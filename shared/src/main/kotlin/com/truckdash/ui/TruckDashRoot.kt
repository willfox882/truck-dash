package com.truckdash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckdash.core.PaneTracker
import com.truckdash.core.SpeedLock
import com.truckdash.obd.ClusterPlatform
import com.truckdash.obd.ClusterPresenter
import com.truckdash.ui.cluster.ClusterScreen
import com.truckdash.repo.DashRepository
import com.truckdash.ui.health.HealthPresenter
import com.truckdash.ui.health.HealthScreen
import com.truckdash.ui.live.LiveScreen
import com.truckdash.ui.trips.TripsPresenter
import com.truckdash.ui.trips.TripsScreen
import kotlin.math.roundToInt

enum class DashTab(val label: String) {
    Live("LIVE"), Trips("TRIPS"), Health("HEALTH"), Cluster("CLUSTER");

    companion object {
        /** The tab bar: Cluster only when the owner turned it on (Health > Cluster tab) and the platform can do Bluetooth. */
        fun visible(clusterShown: Boolean): List<DashTab> = entries.filter { it != Cluster || clusterShown }
    }
}

/** Platform hook for the system back button. Android supplies a BackHandler; default is a no-op. */
val LocalBackHook = staticCompositionLocalOf<@Composable (enabled: Boolean, onBack: () -> Unit) -> Unit> {
    { _, _ -> }
}

/**
 * Lays everything out as if the screen were [designWidthDp] wide, whatever the real pixel density is.
 * Head units ship with arbitrary density settings; this keeps the UI proportions identical everywhere.
 * Also provides [LocalPaneSpec] (compact / tiny) from the real aspect ratio, so split-screen panes get denser layouts.
 */
@Composable
fun DesignScale(designWidthDp: Float = 440f, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = Density(density = constraints.maxWidth / designWidthDp, fontScale = 1f)
        val tracker = remember { PaneTracker() }
        val pane = tracker.update(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        CompositionLocalProvider(LocalDensity provides d, LocalPaneSpec provides pane) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
fun TruckDashRoot(
    repo: DashRepository,
    initialTab: DashTab = DashTab.Live,
    initialTripId: Long? = null,
    cluster: ClusterPlatform? = null,
) {
    val scope = rememberCoroutineScope()
    val trips = remember(repo) { TripsPresenter(repo, scope) }
    val health = remember(repo) { HealthPresenter(repo, scope) }
    // inert until the Cluster tab is opened: constructing it makes no Bluetooth call
    val clusterVm = remember(repo, cluster) { cluster?.let { ClusterPresenter(it, repo.settings, repo::setObdAddress, scope) } }
    val live by repo.live.collectAsState()
    val settings by repo.settings.collectAsState()
    var tab by remember { mutableStateOf(initialTab) }
    val locked = SpeedLock.isLocked(live.speedKmh, settings.drivingLock, settings.passengerMode)
    val clusterShown = settings.showCluster && clusterVm != null
    val tabs = DashTab.visible(clusterShown)

    // the Cluster tab has no driving restriction; the lock only affects Trips and Health
    LaunchedEffect(locked) { if (locked && tab != DashTab.Cluster) tab = DashTab.Live }
    LaunchedEffect(clusterShown) { if (!clusterShown && tab == DashTab.Cluster) tab = DashTab.Live }
    // connect only while the Cluster tab is on screen (the presenter also waits for the app to be active)
    val clusterVisible = clusterShown && tab == DashTab.Cluster
    LaunchedEffect(clusterVm, clusterVisible) { clusterVm?.setTabVisible(clusterVisible) }
    DisposableEffect(clusterVm) { onDispose { clusterVm?.setTabVisible(false) } }
    LaunchedEffect(initialTripId) { initialTripId?.let { trips.open(it) } }

    DesignScale {
        Column(Modifier.fillMaxSize().background(Dash.bg)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    DashTab.Live -> LiveScreen(live)
                    DashTab.Trips -> if (locked) ParkedOnly(live.speedKmh) else TripsScreen(trips)
                    DashTab.Health -> if (locked) ParkedOnly(live.speedKmh) else HealthScreen(health)
                    DashTab.Cluster -> clusterVm?.let { ClusterScreen(it) }
                }
            }
            Hairline()
            TabBar(tabs, tab, locked) { tab = it }
        }
    }
}

@Composable
internal fun TabBar(tabs: List<DashTab>, selectedTab: DashTab, locked: Boolean, onSelect: (DashTab) -> Unit) {
    Row(Modifier.fillMaxWidth().height(pane(60.dp, 48.dp, 40.dp))) {
        tabs.forEach { t ->
            val selected = t == selectedTab
            val disabled = locked && (t == DashTab.Trips || t == DashTab.Health)
            Box(
                Modifier.weight(1f).fillMaxSize().clickable { if (!disabled) onSelect(t) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.align(Alignment.TopCenter).width(28.dp).height(2.dp).background(Dash.text))
                Text(
                    t.label,
                    color = when {
                        selected -> Dash.text
                        disabled -> Color(0xFF3A3A40)
                        else -> Dash.faint
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = if (tabs.size > 3) 1.2.sp else 1.6.sp,
                )
            }
        }
    }
}

@Composable
internal fun ParkedOnly(kmh: Float) {
    val p = LocalPaneSpec.current
    Column(
        Modifier.fillMaxSize().padding(pane(40.dp, 16.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Dot(Dash.amber, pane(9, 8))
        Text(
            "Available when parked", color = Dash.text, fontSize = pane(24.sp, 18.sp, 16.sp), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = pane(18.dp, 10.dp, 8.dp)),
        )
        Text(
            "${kmh.roundToInt()} km/h", color = Dash.dim, fontSize = pane(15.sp, 14.sp), modifier = Modifier.padding(top = pane(6.dp, 4.dp)),
        )
        if (!p.tiny) {
            Text(
                "Stop to view this screen, or turn on Passenger mode or turn off Driving lock in Health.",
                color = Dash.faint, fontSize = pane(14.sp, 13.sp), textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = pane(18.dp, 8.dp)),
            )
        }
    }
}
