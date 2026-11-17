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
            distTotalM = prefs.getDouble("distTotalM"),
            odoBaseTotalM = prefs.getDouble("odoBaseTotalM"),
            drivingLock = prefs.getBoolean("drivingLock", false),
            showCluster = prefs.getBoolean("showCluster", false),
            obdAddress = prefs.getString("obdAddress", null),
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

    private fun android.content.SharedPreferences.getDouble(key: String): Double =
        if (contains(key)) java.lang.Double.longBitsToDouble(getLong(key, 0L)) else 0.0

    val flow: StateFlow<Settings> = _flow.asStateFlow()
    val value: Settings get() = _flow.value

    fun update(f: (Settings) -> Settings) {
        val s = f(_flow.value)
        _flow.value = s
        prefs.edit()
            .putFloat("odoBaseKm", s.odoBaseKm.toFloat())
            .remove("odoBaseMi")
            .putLong("odoBaseSetMs", s.odoBaseSetMs)
            .putLong("distTotalM", java.lang.Double.doubleToRawLongBits(s.distTotalM))
            .putLong("odoBaseTotalM", java.lang.Double.doubleToRawLongBits(s.odoBaseTotalM))
            .putBoolean("drivingLock", s.drivingLock)
            .putBoolean("showCluster", s.showCluster)
            .putString("obdAddress", s.obdAddress)
            .putBoolean("passengerMode", s.passengerMode)
            .putFloat("mountYawDeg", s.mountYawDeg)
            .apply()
    }

    fun addDistance(deltaM: Double) = update { it.copy(distTotalM = it.distTotalM + deltaM) }

    var distInitialised: Boolean
        get() = prefs.getBoolean("distInit", false)
        set(v) = prefs.edit().putBoolean("distInit", v).apply()

    var seeded: Boolean
        get() = prefs.getBoolean("seeded", false)
        set(v) = prefs.edit().putBoolean("seeded", v).apply()
}
