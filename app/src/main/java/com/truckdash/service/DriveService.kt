package com.truckdash.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.truckdash.MainActivity
import com.truckdash.core.GForce
import com.truckdash.core.GravityTracker
import com.truckdash.core.Vec3
import com.truckdash.data.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Thin Android adapter: real GPS + IMU -> [com.truckdash.engine.DriveEngine].
 * All logic (trip recording, smoothing, harsh events) lives in the shared engine.
 * Callbacks arrive on the main thread, which is the engine's single feeding thread.
 */
class DriveService : Service(), LocationListener, SensorEventListener {

    private val tickScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val engine get() = AppGraph.engine

    private lateinit var lm: LocationManager
    private lateinit var sm: SensorManager

    private var gravity: Vec3? = null
    private val gravityTracker = GravityTracker()
    private var useFusedSensors = false

    /** wall time = elapsedRealtime + this. Anchored to GPS UTC when available: head units often boot with a wrong clock. */
    private var wallOffsetMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(LOCATION_SERVICE) as LocationManager
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
        // start() verifies the permission before starting us; on Android 14+ a location-type foreground
        // service without it throws, so never let that escape and kill the app.
        val ok = hasLocationPermission() && runCatching { startAsForeground() }.isSuccess
        if (!ok) {
            stopSelf()
            return
        }
        registerLocation()
        registerSensors()
        engine.setServiceRunning(true)
        tickScope.launch {
            while (true) {
                delay(1_000)
                engine.tick(SystemClock.elapsedRealtime())
            }
        }
    }

    private fun hasLocationPermission() = hasFineLocation(this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        val now = SystemClock.elapsedRealtime()
        engine.stop(now, now + wallOffsetMs)
        runCatching { lm.removeUpdates(this) }
        runCatching { sm.unregisterListener(this) }
        tickScope.cancel()
        super.onDestroy()
    }

    // ---------- setup ----------

    private fun registerLocation() {
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, this, Looper.getMainLooper())
        } catch (_: SecurityException) {
            stopSelf()
        } catch (_: IllegalArgumentException) {
            // no GPS provider on this device
        }
    }

    private fun registerSensors() {
        val g = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val l = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (g != null && l != null) {
            useFusedSensors = true
            sm.registerListener(this, g, SensorManager.SENSOR_DELAY_GAME)
            sm.registerListener(this, l, SensorManager.SENSOR_DELAY_GAME)
        } else {
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    // ---------- location ----------

    override fun onLocationChanged(loc: Location) {
        val clock = if (loc.elapsedRealtimeNanos > 0) loc.elapsedRealtimeNanos / 1_000_000 else SystemClock.elapsedRealtime()
        if (loc.time > MIN_SANE_EPOCH_MS) wallOffsetMs = loc.time - clock // trust GPS UTC over the unit's clock
        engine.onLocation(
            clockMs = clock,
            wallMs = clock + wallOffsetMs,
            lat = loc.latitude,
            lon = loc.longitude,
            altM = if (loc.hasAltitude()) loc.altitude else null,
            speedMps = if (loc.hasSpeed()) loc.speed else 0f,
            bearingDeg = if (loc.hasBearing()) loc.bearing else null,
            accuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
        )
    }

    @Deprecated("Required on API < 30")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = engine.onProviderDisabled()

    // ---------- sensors ----------

    override fun onSensorChanged(e: SensorEvent) {
        val v = Vec3(e.values[0], e.values[1], e.values[2])
        val linear: Vec3
        if (useFusedSensors) {
            when (e.sensor.type) {
                Sensor.TYPE_GRAVITY -> { gravity = v; return }
                Sensor.TYPE_LINEAR_ACCELERATION -> linear = v
                else -> return
            }
        } else {
            if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            linear = gravityTracker.update(SystemClock.elapsedRealtime(), v) ?: return
            gravity = gravityTracker.gravity
        }
        val grav = gravity ?: return
        val g = GForce.toVehicle(grav, linear, AppGraph.settings.value.mountYawDeg) ?: return
        val now = SystemClock.elapsedRealtime()
        engine.onAcceleration(now, now + wallOffsetMs, g)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ---------- notification ----------

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Drive recording", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Truck Dash")
            .setContentText("Recording drive data")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    companion object {
        private const val CHANNEL = "drive"
        private const val NOTIF_ID = 1

        private const val MIN_SANE_EPOCH_MS = 1_577_836_800_000L // 2020-01-01

        fun hasFineLocation(ctx: Context) =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Starts recording; silently does nothing without location permission (would crash on Android 14+). */
        fun start(ctx: Context) {
            if (!hasFineLocation(ctx)) return
            ContextCompat.startForegroundService(ctx, Intent(ctx, DriveService::class.java))
        }
    }
}
