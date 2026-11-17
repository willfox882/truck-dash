package com.truckdash.ui.cluster

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckdash.obd.GaugeSpec
import com.truckdash.ui.Dash
import com.truckdash.ui.Label
import com.truckdash.ui.Numeral
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ARC_START = 135f
private const val ARC_SWEEP = 270f
private val OutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/** Colour is meaning: white normally, amber at the warn point, red past critical. */
internal fun levelColor(level: GaugeSpec.Level): Color = when (level) {
    GaugeSpec.Level.Warn -> Dash.amber
    GaugeSpec.Level.Critical -> Dash.red
    else -> Dash.text
}

internal fun formatValue(v: Float?): String = v?.roundToInt()?.toString() ?: "--"

/**
 * One hairline dial: 270 degree track, ticks, a thin zone arc outside the track from the warn point (amber) and the
 * critical point (red), a value arc and a needle that eases to each new reading (out-cubic, 450 ms).
 * The numeral sits in the middle and the label in the open wedge at the bottom (pass an empty [labelText] to place it yourself).
 */
@Composable
fun ClusterDial(
    spec: GaugeSpec,
    value: Float?,
    modifier: Modifier = Modifier,
    numeralSize: TextUnit = 44.sp,
    showUnit: Boolean = true,
    labelText: String = spec.label,
) {
    val target = if (value == null) 0f else spec.fraction(value)
    val frac by animateFloatAsState(target, tween(450, easing = OutCubic), label = "dial")
    val level = spec.level(value)
    val accent = levelColor(level)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) { drawDial(spec, frac, value != null, accent) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Numeral(
                formatValue(value), if (showUnit && value != null) spec.unit else "", numeralSize,
                color = if (value == null) Dash.faint else accent, unitSize = (numeralSize.value * 0.3f).coerceIn(10f, 14f).sp,
            )
        }
        if (labelText.isNotEmpty()) {
            Label(
                labelText, Modifier.align(Alignment.BottomCenter).padding(bottom = (numeralSize.value * 0.36f).dp),
                color = Dash.faint,
            )
        }
    }
}

private fun DrawScope.drawDial(spec: GaugeSpec, frac: Float, hasValue: Boolean, accent: Color) {
    val d = minOf(size.width, size.height)
    val stroke = d * 0.014f
    val c = Offset(size.width / 2, size.height / 2)
    val arcR = d / 2 - stroke * 5.5f
    val tl = Offset(c.x - arcR, c.y - arcR)
    val arcSize = Size(arcR * 2, arcR * 2)
    fun at(r: Float, deg: Float): Offset {
        val a = Math.toRadians(deg.toDouble())
        return Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
    }
    // track
    drawArc(Dash.line, ARC_START, ARC_SWEEP, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    // ticks inside the track, every 10 percent of the scale, longer every 50
    for (k in 0..10) {
        val deg = ARC_START + ARC_SWEEP * k / 10f
        val long = k % 5 == 0
        drawLine(Dash.faint.copy(alpha = if (long) 0.9f else 0.55f), at(arcR - stroke * (if (long) 6.5f else 5f), deg), at(arcR - stroke * 3f, deg), strokeWidth = stroke * 0.7f)
    }
    // zones sit outside the track on separate radii so amber and red never touch
    spec.warn?.let { w ->
        val f = spec.fraction(w)
        val r = arcR + stroke * 2.6f
        drawArc(Dash.amber.copy(alpha = 0.7f), ARC_START + ARC_SWEEP * f, ARC_SWEEP * (1f - f), false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(stroke * 0.7f))
    }
    spec.critical?.let { cr ->
        val f = spec.fraction(cr)
        val r = arcR + stroke * 4.0f
        drawArc(Dash.red.copy(alpha = 0.8f), ARC_START + ARC_SWEEP * f, ARC_SWEEP * (1f - f), false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(stroke * 0.7f))
    }
    if (!hasValue) return
    // value arc, needle and head marker
    if (frac > 0f) drawArc(accent, ARC_START, ARC_SWEEP * frac, false, tl, arcSize, style = Stroke(stroke * 1.7f, cap = StrokeCap.Round))
    val deg = ARC_START + ARC_SWEEP * frac
    drawLine(Dash.text, at(arcR * 0.72f, deg), at(arcR * 0.87f, deg), strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
    drawCircle(accent, stroke * 1.7f, at(arcR, deg))
}

/** Strip layout cell: label, numeral and a hairline bar showing the position in the scale. */
@Composable
fun ClusterReadout(spec: GaugeSpec, value: Float?, modifier: Modifier = Modifier, numeralSize: TextUnit = 30.sp, barWidth: Dp = 56.dp) {
    val target = if (value == null) 0f else spec.fraction(value)
    val frac by animateFloatAsState(target, tween(450, easing = OutCubic), label = "readout")
    val accent = levelColor(spec.level(value))
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Label(spec.short)
        Numeral(
            formatValue(value), if (value != null) spec.unit else "", numeralSize,
            color = if (value == null) Dash.faint else accent, modifier = Modifier.padding(top = 2.dp), unitSize = 11.sp,
        )
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(2.dp).background(Dash.line), contentAlignment = Alignment.CenterStart) {
            if (value != null) Box(Modifier.fillMaxWidth(frac).height(2.dp).background(accent))
        }
    }
}
