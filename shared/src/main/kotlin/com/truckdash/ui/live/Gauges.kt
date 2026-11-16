package com.truckdash.ui.live

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.truckdash.core.Geo
import com.truckdash.ui.Dash
import com.truckdash.ui.DashFonts
import com.truckdash.ui.Label
import com.truckdash.ui.LocalPaneSpec
import com.truckdash.ui.Tabular
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SCALE_MAX_KMH = 180f
private const val ARC_START = 135f
private const val ARC_SWEEP = 270f

/** One huge light numeral inside a hairline arc. The arc is white; nothing else competes with the number. */
@Composable
fun SpeedGauge(speedKmh: Float, hasFix: Boolean, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(speedKmh, tween(250), label = "speed")
    val compact = LocalPaneSpec.current.compact
    // the arc is sized by the smaller side, so in a short pane it is sized by height and the numeral scales with it
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val numeral = if (compact) minOf(124f, minOf(maxWidth.value, maxHeight.value) * 0.36f) else 124f
        Canvas(Modifier.fillMaxSize()) {
            val d = minOf(size.width, size.height)
            val stroke = d * 0.016f
            val arcSize = Size(d - stroke * 4, d - stroke * 4)
            val tl = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            drawArc(Dash.line, ARC_START, ARC_SWEEP, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val frac = (shown / SCALE_MAX_KMH).coerceIn(0f, 1f)
            if (hasFix && frac > 0f) {
                drawArc(Dash.text, ARC_START, ARC_SWEEP * frac, false, tl, arcSize, style = Stroke(stroke * 1.6f, cap = StrokeCap.Round))
                // small marker at the head of the arc
                val ang = Math.toRadians((ARC_START + ARC_SWEEP * frac).toDouble())
                val r = arcSize.width / 2
                drawCircle(Dash.text, stroke * 2.6f, Offset(center.x + (r * cos(ang)).toFloat(), center.y + (r * sin(ang)).toFloat()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (hasFix) shown.roundToInt().toString() else "--",
                color = if (hasFix) Dash.text else Dash.faint,
                fontSize = numeral.sp, fontWeight = FontWeight.Light, letterSpacing = (-numeral / 31f).sp, style = Tabular,
                lineHeight = if (compact) (numeral * 1.1f).sp else TextUnit.Unspecified,
            )
            if (hasFix) Label("km/h", color = Dash.dim) else Label("Searching for GPS", color = Dash.amber)
        }
    }
}

@Composable
fun CompassRose(headingDeg: Float?, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val heading by animateFloatAsState(headingDeg ?: 0f, tween(300), label = "hdg")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = minOf(size.width, size.height) / 2 * 0.82f
            rotate(-heading, c) {
                drawCircle(Dash.line, r, c, style = Stroke(r * 0.02f))
                for (deg in 0 until 360 step 30) {
                    rotate(deg.toFloat(), c) {
                        drawLine(Dash.faint, Offset(c.x, c.y - r), Offset(c.x, c.y - r + r * 0.09f), strokeWidth = r * 0.02f)
                    }
                }
                for ((label, deg) in listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)) {
                    rotate(deg, c) {
                        val layout = tm.measure(
                            label,
                            TextStyle(fontFamily = DashFonts.family, fontSize = (r * 0.19f).toSp(), fontWeight = FontWeight.Medium),
                        )
                        drawText(
                            layout,
                            color = if (label == "N") Dash.red else Dash.dim,
                            topLeft = Offset(c.x - layout.size.width / 2f, c.y - r + r * 0.15f),
                        )
                    }
                }
            }
            val tri = Path().apply {
                moveTo(c.x, c.y - r - r * 0.03f)
                lineTo(c.x - r * 0.07f, c.y - r - r * 0.15f)
                lineTo(c.x + r * 0.07f, c.y - r - r * 0.15f)
                close()
            }
            drawPath(tri, Dash.text)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                headingDeg?.let { Geo.compassLabel(it) } ?: "--",
                color = Dash.text, fontSize = 26.sp, fontWeight = FontWeight.Light,
            )
            Text(headingDeg?.let { "${it.roundToInt()}°" } ?: "", color = Dash.dim, fontSize = 13.sp)
        }
    }
}

private const val G_RING_MAX = 0.6f

@Composable
fun GBall(longG: Float, latG: Float, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = minOf(size.width, size.height) / 2 * 0.82f
            drawCircle(Dash.line, r, c, style = Stroke(r * 0.02f))
            drawCircle(Dash.line, r * 0.5f, c, style = Stroke(r * 0.015f))
            drawLine(Dash.line, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = r * 0.012f)
            drawLine(Dash.line, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = r * 0.012f)
            // dot shows direction of acceleration: up = accelerating, right = turning right
            var dx = latG / G_RING_MAX * r
            var dy = -longG / G_RING_MAX * r
            val m = hypot(dx, dy)
            if (m > r) { dx *= r / m; dy *= r / m }
            val hot = hypot(latG, longG) > 0.3f
            drawCircle(if (hot) Dash.amber else Dash.text, r * 0.12f, Offset(c.x + dx, c.y + dy))
        }
    }
}

@Composable
fun GReadout(longG: Float, latG: Float) {
    Text(
        "%+.2f long  %+.2f lat".format(longG, latG),
        color = Dash.dim, fontSize = 12.sp, lineHeight = 16.sp, style = Tabular,
    )
}
