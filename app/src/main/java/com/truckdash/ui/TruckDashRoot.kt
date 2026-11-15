package com.truckdash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truckdash.data.AppGraph
import com.truckdash.service.TelemetryHub
import com.truckdash.ui.health.HealthScreen
import com.truckdash.ui.live.LiveScreen
import com.truckdash.ui.trips.TripsScreen
import kotlin.math.roundToInt

private enum class Tab(val label: String) { Live("LIVE"), Trips("TRIPS"), Health("HEALTH") }

/** Speed (mph) above which Trips/Health are locked (unless passenger mode). */
private const val LOCK_MPH = 3f

@Composable
fun TruckDashRoot() {
    val live by TelemetryHub.state.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.flow.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Live) }
    val locked = live.speedMph > LOCK_MPH && !settings.passengerMode

    LaunchedEffect(locked) { if (locked) tab = Tab.Live }

    Column(Modifier.fillMaxSize().background(Dash.bg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.Live -> LiveScreen(live)
                Tab.Trips -> if (locked) ParkedOnly(live.speedMph) else TripsScreen()
                Tab.Health -> if (locked) ParkedOnly(live.speedMph) else HealthScreen()
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp)) {
            Tab.entries.forEach { t ->
                val selected = t == tab
                Box(
                    Modifier.weight(1f).fillMaxSize()
                        .background(if (selected) Dash.card else Dash.bg)
                        .clickable { if (!(locked && t != Tab.Live)) tab = t },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t.label,
                        color = when {
                            selected -> Dash.cyan
                            locked && t != Tab.Live -> Dash.track
                            else -> Dash.dim
                        },
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParkedOnly(mph: Float) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PARKED ONLY", color = Dash.amber, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("${mph.roundToInt()} mph", color = Dash.text, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Stop to view this screen, or enable Passenger mode in Health settings.",
            color = Dash.dim, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp),
        )
    }
}
