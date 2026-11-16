package com.truckdash.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckdash.model.LiveState
import com.truckdash.ui.Dash
import com.truckdash.ui.Dot
import com.truckdash.ui.Label
import com.truckdash.ui.StatGrid
import com.truckdash.ui.StatItem
import com.truckdash.ui.Tabular
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
        modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // status row
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(clock, color = Dash.dim, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Box(Modifier.weight(1f))
            if (s.recording) {
                Dot(Dash.red)
                Label("Rec", Modifier.padding(start = 6.dp, end = 16.dp), color = Dash.red)
            }
            Dot(if (s.hasFix) Dash.green else Dash.amber)
            Label(if (s.hasFix) "GPS" else "No GPS", Modifier.padding(start = 6.dp), color = Dash.dim)
            if (s.hasFix && s.accuracyM != null) {
                Text("  \u00B1${s.accuracyM!!.roundToInt()} m", color = Dash.faint, fontSize = 12.sp)
            }
        }

        SpeedGauge(s.speedKmh, s.hasFix, Modifier.fillMaxWidth().weight(1.5f))

        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                CompassRose(s.bearingDeg, Modifier.weight(1f).fillMaxWidth())
                Label("Heading", Modifier.padding(top = 4.dp))
                Text("\u00A0", fontSize = 12.sp, lineHeight = 16.sp, style = Tabular)
            }
            Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                GBall(s.longG, s.latG, Modifier.weight(1f).fillMaxWidth())
                Label("G-force", Modifier.padding(top = 4.dp))
                GReadout(s.longG, s.latG)
            }
        }

        val moving = formatClock(s.tripMovingSec)
        StatGrid(
            listOf(
                StatItem("Trip", "%.1f".format(s.tripKm), "km"),
                StatItem("Moving", moving.first, moving.second),
                StatItem("Average", s.tripAvgKmh.roundToInt().toString(), "km/h"),
                StatItem("Max", s.tripMaxKmh.roundToInt().toString(), "km/h"),
                StatItem("Altitude", s.altM?.roundToInt()?.toString() ?: "--", "m"),
                StatItem("Grade", s.gradePct?.let { "%+.1f".format(it) } ?: "--", "%"),
            ),
        )
    }
}
