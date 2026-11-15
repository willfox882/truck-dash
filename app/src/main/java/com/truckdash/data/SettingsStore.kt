package com.truckdash.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    /** Odometer reading the user entered, at [odoBaseSetMs]. Live odometer = base + trips since. */
    val odoBaseMi: Double = 0.0,
    val odoBaseSetMs: Long = 0L,
    /** Passenger mode disables the "parked only" lock on the Trips/Health tabs. */
    val passengerMode: Boolean = false,
    /** Extra yaw correction (deg) if the unit is mounted rotated so forward is not screen -Z. */
    val mountYawDeg: Float = 0f,
)

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
