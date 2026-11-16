package com.truckdash.data

import android.content.Context
import com.truckdash.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("truckdash", Context.MODE_PRIVATE)

    private val _flow = MutableStateFlow(
        Settings(
            odoBaseKm = loadOdoKm(),
            odoBaseSetMs = prefs.getLong("odoBaseSetMs", 0L),
            passengerMode = prefs.getBoolean("passengerMode", false),
            mountYawDeg = prefs.getFloat("mountYawDeg", 0f),
        ),
    )
    /** Reads the km odometer base; converts the miles value saved by v0.1.0 if that is all there is. */
    private fun loadOdoKm(): Double = when {
        prefs.contains("odoBaseKm") -> prefs.getFloat("odoBaseKm", 0f).toDouble()
        prefs.contains("odoBaseMi") -> prefs.getFloat("odoBaseMi", 0f) * 1.609344
        else -> 0.0
    }

    val flow: StateFlow<Settings> = _flow.asStateFlow()
    val value: Settings get() = _flow.value

    fun update(f: (Settings) -> Settings) {
        val s = f(_flow.value)
        _flow.value = s
        prefs.edit()
            .putFloat("odoBaseKm", s.odoBaseKm.toFloat())
            .remove("odoBaseMi")
            .putLong("odoBaseSetMs", s.odoBaseSetMs)
            .putBoolean("passengerMode", s.passengerMode)
            .putFloat("mountYawDeg", s.mountYawDeg)
            .apply()
    }

    var seeded: Boolean
        get() = prefs.getBoolean("seeded", false)
        set(v) = prefs.edit().putBoolean("seeded", v).apply()
}
