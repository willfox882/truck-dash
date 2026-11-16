package com.truckdash.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
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
import com.truckdash.core.Geo
import com.truckdash.ui.DashCard
import com.truckdash.ui.Dot
import com.truckdash.ui.Hairline
import com.truckdash.ui.LocalPaneSpec
import com.truckdash.ui.Numeral
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
    val pane = LocalPaneSpec.current
    when {
        pane.tiny -> LiveTiny(s, modifier)
        pane.compact -> LiveCompact(s, clock, modifier)
        else -> LiveFull(s, clock, modifier)
    }
}

@Composable
private fun StatusRow(s: LiveState, clock: String) {
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
}

@Composable
private fun LiveFull(s: LiveState, clock: String, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusRow(s, clock)

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

private fun tripStat(s: LiveState) = StatItem("Trip", "%.1f".format(s.tripKm), "km")
private fun avgStat(s: LiveState) = StatItem("Average", s.tripAvgKmh.roundToInt().toString(), "km/h")
private fun maxStat(s: LiveState) = StatItem("Max", s.tripMaxKmh.roundToInt().toString(), "km/h")
private fun gradeStat(s: LiveState) = StatItem("Grade", s.gradePct?.let { "%+.1f".format(it) } ?: "--", "%")

/** Half-height pane: speed arc on the left (sized by the pane height), 2x2 stats and a heading + g strip on the right. */
@Composable
private fun LiveCompact(s: LiveState, clock: String, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StatusRow(s, clock)
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpeedGauge(s.speedKmh, s.hasFix, Modifier.weight(0.95f).fillMaxHeight())
            Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatGrid(listOf(tripStat(s), avgStat(s), maxStat(s), gradeStat(s)), columns = 2)
                DashCard(Modifier.fillMaxWidth(), padding = 0) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Label("Heading")
                            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
                                Text(
                                    s.bearingDeg?.let { Geo.compassLabel(it) } ?: "--",
                                    color = Dash.text, fontSize = 20.sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false,
                                )
                                s.bearingDeg?.let {
                                    Text(
                                        " ${it.roundToInt()}\u00B0", color = Dash.dim, fontSize = 12.sp, maxLines = 1, softWrap = false,
                                        modifier = Modifier.padding(bottom = 3.dp),
                                    )
                                }
                            }
                        }
                        Box(Modifier.width(1.dp).fillMaxHeight().background(Dash.line))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Label("G-force")
                            Text(
                                "%+.2f long".format(s.longG), color = Dash.text, fontSize = 12.sp, lineHeight = 15.sp,
                                maxLines = 1, softWrap = false, style = Tabular, modifier = Modifier.padding(top = 2.dp),
                            )
                            Text(
                                "%+.2f lat".format(s.latG), color = Dash.text, fontSize = 12.sp, lineHeight = 15.sp,
                                maxLines = 1, softWrap = false, style = Tabular,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Short strip: speed, REC/GPS dots, Trip and Max. Nothing else. */
@Composable
private fun LiveTiny(s: LiveState, modifier: Modifier) {
    Row(
        modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeedGauge(s.speedKmh, s.hasFix, Modifier.weight(1.05f).fillMaxHeight())
        Column(Modifier.weight(0.95f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (s.recording) {
                    Dot(Dash.red, 9)
                    Box(Modifier.width(14.dp))
                }
                Dot(if (s.hasFix) Dash.green else Dash.amber, 9)
            }
            StatGrid(listOf(tripStat(s), maxStat(s)), columns = 1)
        }
    }
}
