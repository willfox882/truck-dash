package com.truckdash.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * Design tokens. Matte near-black, hairlines instead of boxes, white as the primary accent.
 * Colour is reserved for meaning: green = ok/live, amber = caution, red = critical/recording.
 */
object Dash {
    val bg = Color(0xFF0A0A0B)
    val surface = Color(0xFF131315)
    val surfaceHigh = Color(0xFF1C1C1F)
    val line = Color(0xFF28282C)
    val text = Color(0xFFF2F2F4)
    val dim = Color(0xFF9A9AA1)
    val faint = Color(0xFF64646B)
    val green = Color(0xFF55D08A)
    val amber = Color(0xFFE9B25A)
    val red = Color(0xFFEF6A64)
    val onLight = Color(0xFF0A0A0B)
}

/** Tabular figures so live numbers don't jitter as digits change. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** Platform code sets this at startup (Android: res/font, desktop: classpath font/). */
object DashFonts {
    var family: FontFamily = FontFamily.Default
}

private fun typography(f: FontFamily): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = f), displayMedium = b.displayMedium.copy(fontFamily = f),
        displaySmall = b.displaySmall.copy(fontFamily = f), headlineLarge = b.headlineLarge.copy(fontFamily = f),
        headlineMedium = b.headlineMedium.copy(fontFamily = f), headlineSmall = b.headlineSmall.copy(fontFamily = f),
        titleLarge = b.titleLarge.copy(fontFamily = f), titleMedium = b.titleMedium.copy(fontFamily = f),
        titleSmall = b.titleSmall.copy(fontFamily = f), bodyLarge = b.bodyLarge.copy(fontFamily = f),
        bodyMedium = b.bodyMedium.copy(fontFamily = f), bodySmall = b.bodySmall.copy(fontFamily = f),
        labelLarge = b.labelLarge.copy(fontFamily = f), labelMedium = b.labelMedium.copy(fontFamily = f),
        labelSmall = b.labelSmall.copy(fontFamily = f),
    )
}

@Composable
fun TruckDashTheme(content: @Composable () -> Unit) {
    val family = DashFonts.family
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Dash.text,
            onPrimary = Dash.onLight,
            background = Dash.bg,
            surface = Dash.surface,
            onSurface = Dash.text,
            onBackground = Dash.text,
            surfaceVariant = Dash.surfaceHigh,
            secondaryContainer = Dash.surfaceHigh,
            onSecondaryContainer = Dash.text,
            outline = Dash.line,
            outlineVariant = Dash.line,
            error = Dash.red,
        ),
        typography = typography(family),
    ) {
        Surface(color = Dash.bg) {
            ProvideTextStyle(TextStyle(fontFamily = family, color = Dash.text), content = content)
        }
    }
}
