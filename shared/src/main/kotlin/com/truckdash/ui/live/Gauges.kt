package com.truckdash.ui.live

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.truckdash.core.Geo
import com.truckdash.ui.Dash
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val SCALE_MAX_MPH = 120f

@Composable
fun SpeedGauge(speedMph: Float, hasFix: Boolean, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(speedMph, tween(250), label = "speed")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val d = minOf(size.width, size.height)
            val stroke = d * 0.06f
            val arcSize = Size(d - stroke, d - stroke)
            val tl = Offset((size.width - d) / 2 + stroke / 2, (size.height - d) / 2 + stroke / 2)
            drawArc(Dash.track, 135f, 270f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val frac = (shown / SCALE_MAX_MPH).coerceIn(0f, 1f)
            val color = if (frac < 0.5f) lerp(Dash.cyan, Dash.amber, frac * 2) else lerp(Dash.amber, Dash.red, (frac - 0.5f) * 2)
            if (frac > 0f) drawArc(color, 135f, 270f * frac, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // ticks every 20 mph
            val c = center
            val rOuter = d / 2 - stroke * 1.4f
            for (mph in 0..SCALE_MAX_MPH.toInt() step 20) {
                val ang = 135f + 270f * mph / SCALE_MAX_MPH
                rotate(ang, c) {
                    drawLine(Dash.dim, Offset(c.x + rOuter, c.y), Offset(c.x + rOuter - stroke * 0.7f, c.y), strokeWidth = 4f)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (hasFix) shown.roundToInt().toString() else "--",
                color = Dash.text, fontSize = 100.sp, fontWeight = FontWeight.Bold,
            )
            Text(if (hasFix) "MPH" else "NO GPS FIX", color = if (hasFix) Dash.dim else Dash.amber, fontSize = 20.sp)
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
            val r = minOf(size.width, size.height) / 2 * 0.84f
            rotate(-heading, c) {
                drawCircle(Dash.track, r, c, style = Stroke(r * 0.03f))
                for (deg in 0 until 360 step 10) {
                    val len = if (deg % 30 == 0) r * 0.13f else r * 0.07f
                    rotate(deg.toFloat(), c) {
                        drawLine(Dash.dim, Offset(c.x, c.y - r), Offset(c.x, c.y - r + len), strokeWidth = r * 0.025f)
                    }
                }
                for ((label, deg) in listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)) {
                    rotate(deg, c) {
                        val layout = tm.measure(label, TextStyle(fontSize = (r * 0.2f).toSp(), fontWeight = FontWeight.Bold))
                        drawText(
                            layout,
                            color = if (label == "N") Dash.red else Dash.text,
                            topLeft = Offset(c.x - layout.size.width / 2f, c.y - r + r * 0.17f),
                        )
                    }
                }
            }
            // fixed pointer just outside the ring at 12 o'clock
            val tri = Path().apply {
                moveTo(c.x, c.y - r - r * 0.02f)
                lineTo(c.x - r * 0.09f, c.y - r - r * 0.17f)
                lineTo(c.x + r * 0.09f, c.y - r - r * 0.17f)
                close()
            }
            drawPath(tri, Dash.cyan)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                headingDeg?.let { Geo.compassLabel(it) } ?: "--",
                color = Dash.text, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            )
            Text(headingDeg?.let { "${it.roundToInt()}°" } ?: "", color = Dash.dim, fontSize = 14.sp)
        }
    }
}

private const val G_RING_MAX = 0.6f

@Composable
fun GBall(longG: Float, latG: Float, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = minOf(size.width, size.height) / 2 * 0.88f
            for (f in listOf(1f, 0.66f, 0.33f)) drawCircle(Dash.track, r * f, c, style = Stroke(3f))
            drawLine(Dash.track, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 2f)
            drawLine(Dash.track, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 2f)
            // dot shows direction of acceleration: up = accelerating, right = turning right
            var dx = latG / G_RING_MAX * r
            var dy = -longG / G_RING_MAX * r
            val m = hypot(dx, dy)
            if (m > r) { dx *= r / m; dy *= r / m }
            val hot = hypot(latG, longG) > 0.3f
            drawCircle(if (hot) Dash.amber else Dash.cyan, 16f, Offset(c.x + dx, c.y + dy))
        }
    }
}

@Composable
fun GReadout(longG: Float, latG: Float, color: Color = Dash.dim) {
    Text(
        "%+.2f long   %+.2f lat  g".format(longG, latG),
        color = color, fontSize = 15.sp,
    )
}
