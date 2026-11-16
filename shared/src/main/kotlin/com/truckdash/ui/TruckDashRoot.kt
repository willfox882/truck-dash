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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckdash.repo.DashRepository
import com.truckdash.ui.health.HealthPresenter
import com.truckdash.ui.health.HealthScreen
import com.truckdash.ui.live.LiveScreen
import com.truckdash.ui.trips.TripsPresenter
import com.truckdash.ui.trips.TripsScreen
import kotlin.math.roundToInt

enum class DashTab(val label: String) { Live("LIVE"), Trips("TRIPS"), Health("HEALTH") }

/** Speed (km/h) above which Trips/Health are locked (unless passenger mode). */
const val LOCK_KMH = 5f

/** Platform hook for the system back button. Android supplies a BackHandler; default is a no-op. */
val LocalBackHook = staticCompositionLocalOf<@Composable (enabled: Boolean, onBack: () -> Unit) -> Unit> {
    { _, _ -> }
}

/**
 * Lays everything out as if the screen were [designWidthDp] wide, whatever the real pixel density is.
 * Head units ship with arbitrary density settings; this keeps the UI proportions identical everywhere.
 */
@Composable
fun DesignScale(designWidthDp: Float = 440f, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = Density(density = constraints.maxWidth / designWidthDp, fontScale = 1f)
        CompositionLocalProvider(LocalDensity provides d) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
fun TruckDashRoot(
    repo: DashRepository,
    initialTab: DashTab = DashTab.Live,
    initialTripId: Long? = null,
) {
    val scope = rememberCoroutineScope()
    val trips = remember(repo) { TripsPresenter(repo, scope) }
    val health = remember(repo) { HealthPresenter(repo, scope) }
    val live by repo.live.collectAsState()
    val settings by repo.settings.collectAsState()
    var tab by remember { mutableStateOf(initialTab) }
    val locked = live.speedKmh > LOCK_KMH && !settings.passengerMode

    LaunchedEffect(locked) { if (locked) tab = DashTab.Live }
    LaunchedEffect(initialTripId) { initialTripId?.let { trips.open(it) } }

    DesignScale {
        Column(Modifier.fillMaxSize().background(Dash.bg)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    DashTab.Live -> LiveScreen(live)
                    DashTab.Trips -> if (locked) ParkedOnly(live.speedKmh) else TripsScreen(trips)
                    DashTab.Health -> if (locked) ParkedOnly(live.speedKmh) else HealthScreen(health)
                }
            }
            Row(Modifier.fillMaxWidth().height(64.dp)) {
                DashTab.entries.forEach { t ->
                    val selected = t == tab
                    Box(
                        Modifier.weight(1f).fillMaxSize()
                            .background(if (selected) Dash.card else Dash.bg)
                            .clickable { if (!(locked && t != DashTab.Live)) tab = t },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            t.label,
                            color = when {
                                selected -> Dash.cyan
                                locked && t != DashTab.Live -> Dash.track
                                else -> Dash.dim
                            },
                            fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParkedOnly(kmh: Float) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PARKED ONLY", color = Dash.amber, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("${kmh.roundToInt()} km/h", color = Dash.text, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Stop to view this screen, or enable Passenger mode in Health settings.",
            color = Dash.dim, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp),
        )
    }
}
