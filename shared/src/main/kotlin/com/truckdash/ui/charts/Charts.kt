package com.truckdash.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.truckdash.ui.Dash
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
    color: Color,
    modifier: Modifier = Modifier,
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
        fun y(f: Float) = h - (f - lo) / (hi - lo) * h * 0.92f - h * 0.04f
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
            drawPath(area, color.copy(alpha = 0.15f))
        }
        drawPath(line, color, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun BarChart(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        if (values.isEmpty()) return@Canvas
        val hi = (values.max()).coerceAtLeast(1f)
        val gap = 12f
        val bw = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val bh = v / hi * size.height * 0.95f
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (bw + gap), size.height - bh),
                size = Size(bw, bh.coerceAtLeast(2f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            )
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
        val pad = 24f
        val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val offX = (size.width - spanX * scale) / 2
        val offY = (size.height - spanY * scale) / 2
        fun map(p: GeoPt) = Offset(
            (offX + (p.lon * k - minX) * scale).toFloat(),
            (size.height - offY - (p.lat - minY) * scale).toFloat(),
        )
        val path = Path()
        points.forEachIndexed { i, p -> map(p).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
        drawPath(path, Dash.cyan, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Dash.green, 11f, map(points.first()))
        drawCircle(Dash.red, 11f, map(points.last()))
        markers.forEach { drawCircle(it.color, 9f, map(it.at)) }
    }
}
