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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
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
            Hairline()
            Row(Modifier.fillMaxWidth().height(60.dp)) {
                DashTab.entries.forEach { t ->
                    val selected = t == tab
                    val disabled = locked && t != DashTab.Live
                    Box(
                        Modifier.weight(1f).fillMaxSize().clickable { if (!disabled) tab = t },
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
                            fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp,
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
        Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Dot(Dash.amber, 9)
        Text(
            "Available when parked", color = Dash.text, fontSize = 24.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            "${kmh.roundToInt()} km/h", color = Dash.dim, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "Stop to view this screen, or turn on Passenger mode in Health.",
            color = Dash.faint, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp),
        )
    }
}
