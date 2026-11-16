package com.truckdash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small uppercase caption used above values and as section headers. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Dash.faint) {
    Text(text.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp, modifier = modifier)
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Dash.text, fontSize = 34.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp, modifier = modifier)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Dash.line))
}

@Composable
fun Dot(color: Color, size: Int = 7, modifier: Modifier = Modifier) {
    Box(modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun DashCard(modifier: Modifier = Modifier, padding: Int = 18, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Dash.surface, shape = RoundedCornerShape(22.dp), modifier = modifier) {
        Column(Modifier.padding(padding.dp), content = content)
    }
}

/** A large value with a small unit, e.g. "12.4 km". Light weight numerals are the signature of the UI. */
@Composable
fun Numeral(value: String, unit: String = "", size: TextUnit = 30.sp, color: Color = Dash.text, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            value, color = color, fontSize = size, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false,
            letterSpacing = (-0.5).sp, style = Tabular,
        )
        if (unit.isNotEmpty()) {
            Text(" $unit", color = Dash.dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = (size.value * 0.12f).dp))
        }
    }
}

data class StatItem(
    val label: String,
    val value: String,
    val unit: String = "",
    val color: Color = Dash.text,
    /** Optional small line under the value, for units too long to sit beside it (e.g. "L/100 km"). */
    val sub: String = "",
)

/** Several stats in one matte card, separated by hairlines (no individual boxes). */
@Composable
fun StatGrid(items: List<StatItem>, modifier: Modifier = Modifier, columns: Int = 3, valueSize: TextUnit = 28.sp) {
    DashCard(modifier.fillMaxWidth(), padding = 0) {
        items.chunked(columns).forEachIndexed { row, cells ->
            if (row > 0) Hairline()
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                for (i in 0 until columns) {
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Dash.line))
                    val it = cells.getOrNull(i)
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        if (it != null) {
                            Label(it.label)
                            Numeral(it.value, it.unit, valueSize, it.color, Modifier.padding(top = 4.dp))
                            if (it.sub.isNotEmpty()) Text(it.sub, color = Dash.dim, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

enum class PillKind { Primary, Secondary, Danger }

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: PillKind = PillKind.Secondary, small: Boolean = false) {
    val bg = when (kind) {
        PillKind.Primary -> Dash.text
        PillKind.Secondary -> Dash.surfaceHigh
        PillKind.Danger -> Dash.surfaceHigh
    }
    val fg = when (kind) {
        PillKind.Primary -> Dash.onLight
        PillKind.Secondary -> Dash.text
        PillKind.Danger -> Dash.red
    }
    Surface(
        color = bg, shape = RoundedCornerShape(50), onClick = onClick, modifier = modifier,
        border = if (kind == PillKind.Primary) null else BorderStroke(1.dp, Dash.line),
    ) {
        Box(
            Modifier.padding(horizontal = if (small) 16.dp else 22.dp, vertical = if (small) 8.dp else 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = fg, fontSize = if (small) 14.sp else 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Dash.dim) {
    Text(
        text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(title, Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun dashSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White, checkedTrackColor = Dash.green, checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Dash.dim, uncheckedTrackColor = Dash.surfaceHigh, uncheckedBorderColor = Dash.line,
)

@Composable
fun dashCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = Dash.text, checkmarkColor = Dash.onLight, uncheckedColor = Dash.faint,
)

@Composable
fun dashFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Dash.text, unfocusedTextColor = Dash.text,
    focusedBorderColor = Dash.text, unfocusedBorderColor = Dash.line,
    focusedLabelColor = Dash.dim, unfocusedLabelColor = Dash.faint,
    cursorColor = Dash.text,
)

/** Consistent dialog chrome for every dialog in the app. */
@Composable
fun DashDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String = "Cancel",
    danger: Boolean = false,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Dash.surfaceHigh,
        shape = RoundedCornerShape(26.dp),
        title = { Text(title, color = Dash.text, fontWeight = FontWeight.Medium, fontSize = 20.sp) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } },
        confirmButton = { TextAction(confirmText, onConfirm, color = if (danger) Dash.red else Dash.text) },
        dismissButton = { TextAction(dismissText, onDismiss) },
    )
}
