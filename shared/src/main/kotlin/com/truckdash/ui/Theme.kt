package com.truckdash.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Dash {
    val bg = Color(0xFF0A0E13)
    val card = Color(0xFF131A22)
    val track = Color(0xFF243040)
    val text = Color(0xFFE8EEF5)
    val dim = Color(0xFF8A9AAD)
    val cyan = Color(0xFF00E5FF)
    val amber = Color(0xFFFFB300)
    val red = Color(0xFFFF5252)
    val green = Color(0xFF3DDC84)
}

@Composable
fun TruckDashTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Dash.cyan,
            onPrimary = Color(0xFF00252B),
            secondaryContainer = Dash.track,
            onSecondaryContainer = Dash.text,
            outline = Dash.dim,
            background = Dash.bg,
            surface = Dash.card,
            onSurface = Dash.text,
            onBackground = Dash.text,
            error = Dash.red,
        ),
    ) {
        Surface(color = Dash.bg, content = content)
    }
}

@Composable
fun DashCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Dash.card, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun StatTile(label: String, value: String, unit: String = "", modifier: Modifier = Modifier, valueColor: Color = Dash.text) {
    DashCard(modifier) {
        Text(label, color = Dash.dim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(
            value, color = valueColor, fontSize = if (value.length > 4) 26.sp else 34.sp,
            fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
        )
        Text(unit.ifEmpty { " " }, color = Dash.dim, fontSize = 13.sp)
    }
}
