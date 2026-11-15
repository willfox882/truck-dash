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
            odoBaseMi = prefs.getFloat("odoBaseMi", 0f).toDouble(),
            odoBaseSetMs = prefs.getLong("odoBaseSetMs", 0L),
            passengerMode = prefs.getBoolean("passengerMode", false),
            mountYawDeg = prefs.getFloat("mountYawDeg", 0f),
        ),
    )
    val flow: StateFlow<Settings> = _flow.asStateFlow()
    val value: Settings get() = _flow.value

    fun update(f: (Settings) -> Settings) {
        val s = f(_flow.value)
        _flow.value = s
        prefs.edit()
            .putFloat("odoBaseMi", s.odoBaseMi.toFloat())
            .putLong("odoBaseSetMs", s.odoBaseSetMs)
            .putBoolean("passengerMode", s.passengerMode)
            .putFloat("mountYawDeg", s.mountYawDeg)
            .apply()
    }

    var seeded: Boolean
        get() = prefs.getBoolean("seeded", false)
        set(v) = prefs.edit().putBoolean("seeded", v).apply()
}
