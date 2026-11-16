package com.truckdash.sandbox

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.truckdash.ui.DashFonts

/** Loads the bundled Geist weights from the classpath (same files the Android app uses). */
fun loadDashFonts() {
    fun f(file: String, weight: FontWeight): androidx.compose.ui.text.font.Font {
        val bytes = Thread.currentThread().contextClassLoader.getResourceAsStream("font/$file.ttf")!!.readBytes()
        return Font(identity = file, data = bytes, weight = weight)
    }
    DashFonts.family = FontFamily(
        f("geist_light", FontWeight.Light), f("geist_regular", FontWeight.Normal),
        f("geist_medium", FontWeight.Medium), f("geist_semibold", FontWeight.SemiBold),
    )
}
