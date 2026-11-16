package com.truckdash.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.truckdash.ui.Dash
import com.truckdash.ui.DashFonts
import kotlin.math.cos

/** Reduce [values] to at most [max] points by striding (keeps shape, cheap). */
fun downsample(values: List<Float>, max: Int = 300): List<Float> {
    if (values.size <= max) return values
    val step = values.size / max.toFloat()
    return List(max) { values[(it * step).toInt()] }
}

@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Dash.text,
    fill: Boolean = true,
    minOverride: Float? = null,
    /** Minimum value range shown, so tiny noise isn't blown up into a mountain. */
    minSpan: Float = 0f,
) {
    Canvas(modifier.fillMaxSize()) {
        val v = downsample(values)
        if (v.size < 2) return@Canvas
        val lo = minOverride ?: v.min()
        var hi = v.max()
        if (hi - lo < minSpan) hi = lo + minSpan
        if (hi - lo < 1e-3f) hi = lo + 1f
        val w = size.width
        val h = size.height
        fun x(i: Int) = i * w / (v.size - 1)
        fun y(f: Float) = h - (f - lo) / (hi - lo) * h * 0.9f - h * 0.05f
        val line = Path().apply {
            moveTo(x(0), y(v[0]))
            for (i in 1 until v.size) lineTo(x(i), y(v[i]))
        }
        if (fill) {
            val area = Path().apply {
                addPath(line)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), Color.Transparent)))
        }
        drawPath(line, color, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Bars with small value labels on top; the last bar is highlighted (the current period). */
@Composable
fun BarChart(values: List<Float>, modifier: Modifier = Modifier, highlightLast: Boolean = true) {
    val tm = rememberTextMeasurer()
    Canvas(modifier.fillMaxSize()) {
        if (values.isEmpty()) return@Canvas
        val hi = values.max().coerceAtLeast(1f)
        val gap = size.width * 0.04f
        val bw = (size.width - gap * (values.size - 1)) / values.size
        val labelH = 16.sp.toPx()
        val plotH = size.height - labelH
        values.forEachIndexed { i, v ->
            val bh = (v / hi * plotH * 0.96f).coerceAtLeast(3f)
            val last = highlightLast && i == values.lastIndex
            val x = i * (bw + gap)
            drawRoundRect(
                color = if (last) Dash.text else Color(0xFF34343A),
                topLeft = Offset(x, size.height - bh),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(bw * 0.28f, bw * 0.28f),
            )
            if (v >= 1f) {
                val layout = tm.measure(
                    "%.0f".format(v),
                    TextStyle(fontFamily = DashFonts.family, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                )
                drawText(
                    layout, color = if (last) Dash.text else Dash.faint,
                    topLeft = Offset(x + (bw - layout.size.width) / 2f, size.height - bh - layout.size.height - 3f),
                )
            }
        }
    }
}

data class GeoPt(val lat: Double, val lon: Double)
data class RouteMarker(val at: GeoPt, val color: Color)

@Composable
fun RouteMap(points: List<GeoPt>, markers: List<RouteMarker>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        if (points.size < 2) return@Canvas
        val latMid = points.map { it.lat }.average()
        val k = cos(Math.toRadians(latMid))
        val xs = points.map { it.lon * k }
        val ys = points.map { it.lat }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val spanX = (maxX - minX).coerceAtLeast(1e-6)
        val spanY = (maxY - minY).coerceAtLeast(1e-6)
        val pad = 28f
        val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val offX = (size.width - spanX * scale) / 2
        val offY = (size.height - spanY * scale) / 2
        fun map(p: GeoPt) = Offset(
            (offX + (p.lon * k - minX) * scale).toFloat(),
            (size.height - offY - (p.lat - minY) * scale).toFloat(),
        )
        val path = Path()
        points.forEachIndexed { i, p -> map(p).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
        drawPath(path, Dash.text, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // start = ring, end = filled dot
        drawCircle(Dash.bg, 11f, map(points.first()))
        drawCircle(Dash.text, 11f, map(points.first()), style = Stroke(3f))
        drawCircle(Dash.text, 8f, map(points.last()))
        markers.forEach {
            drawCircle(Dash.bg, 10f, map(it.at))
            drawCircle(it.color, 7f, map(it.at))
        }
    }
}
