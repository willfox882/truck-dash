package com.truckdash.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckdash.model.LiveState
import com.truckdash.ui.Dash
import com.truckdash.ui.StatTile
import com.truckdash.ui.formatClock
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val clockFmt = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun LiveScreen(s: LiveState, modifier: Modifier = Modifier) {
    val clock by produceState(LocalTime.now().format(clockFmt)) {
        while (true) {
            value = LocalTime.now().format(clockFmt)
            delay(1_000)
        }
    }
    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(clock, color = Dash.text, fontSize = 26.sp)
            Box(Modifier.weight(1f))
            if (s.recording) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Dash.red))
                Text("  REC", color = Dash.red, fontSize = 16.sp)
                Box(Modifier.padding(horizontal = 8.dp))
            }
            Text(
                when {
                    !s.hasFix -> "GPS: searching"
                    s.accuracyM != null -> "GPS ±${s.accuracyM.roundToInt()} m"
                    else -> "GPS"
                },
                color = if (s.hasFix) Dash.green else Dash.amber, fontSize = 16.sp,
            )
        }

        SpeedGauge(s.speedKmh, s.hasFix, Modifier.fillMaxWidth().weight(1.5f))

        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompassRose(s.bearingDeg, Modifier.weight(1f).fillMaxSize())
            Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                GBall(s.longG, s.latG, Modifier.weight(1f).fillMaxWidth())
                GReadout(s.longG, s.latG)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("TRIP", "%.1f".format(s.tripKm), "km", Modifier.weight(1f))
            StatTile("MOVING", formatClock(s.tripMovingSec).first, formatClock(s.tripMovingSec).second, Modifier.weight(1f))
            StatTile("AVG", s.tripAvgKmh.roundToInt().toString(), "km/h", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("MAX", s.tripMaxKmh.roundToInt().toString(), "km/h", Modifier.weight(1f))
            StatTile("ALT", s.altM?.roundToInt()?.toString() ?: "--", "m", Modifier.weight(1f))
            StatTile("GRADE", s.gradePct?.let { "%+.1f".format(it) } ?: "--", "%", Modifier.weight(1f))
        }
    }
}
