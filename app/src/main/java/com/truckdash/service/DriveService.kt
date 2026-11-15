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
import com.truckdash.core.ElevationAccumulator
import com.truckdash.core.GForce
import com.truckdash.core.GradeEstimator
import com.truckdash.core.Geo
import com.truckdash.core.HarshEventDetector
import com.truckdash.core.HarshType
import com.truckdash.core.SegEvent
import com.truckdash.core.TripSegmenter
import com.truckdash.core.Vec3
import com.truckdash.data.AppGraph
import com.truckdash.data.EventEntity
import com.truckdash.data.PointEntity
import com.truckdash.data.TripEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service: GPS + IMU -> [TelemetryHub] (live UI) and Room (trips).
 * All mutable trip state is touched only on the main thread (location/sensor callbacks);
 * DB writes are serialised on a single-thread dispatcher.
 */
class DriveService : Service(), LocationListener, SensorEventListener {

    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val tickScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dao get() = AppGraph.db.tripDao()

    private lateinit var lm: LocationManager
    private lateinit var sm: SensorManager

    private val segmenter = TripSegmenter()
    private val harsh = HarshEventDetector()
    private var elev = ElevationAccumulator()
    private val grade = GradeEstimator()

    // live location state
    private var lastLoc: Location? = null
    private var lastFixElapsed = 0L
    private var altEma: Double? = null

    // sensor state
    private var gravity: Vec3? = null
    private var gravityLpf: Vec3? = null
    private var useFusedSensors = false
    private var longEma = 0f
    private var latEma = 0f
    private var lastHubPush = 0L

    // trip state
    private var tripDeferred: CompletableDeferred<Long>? = null
    private var tripStartWall = 0L
    private var distM = 0.0
    private var movingMs = 0L
    private var stoppedMs = 0L
    private var maxMps = 0f
    private var nAccel = 0
    private var nBrake = 0
    private var nCorner = 0
    private var lastTripSave = 0L
    private val pointBuf = ArrayList<PointEntity>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(LOCATION_SERVICE) as LocationManager
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
        startAsForeground()

        if (!hasLocationPermission()) {
            stopSelf()
            return
        }
        closeOrphanTrips()
        registerLocation()
        registerSensors()
        TelemetryHub.update { it.copy(serviceRunning = true) }
        tickScope.launch {
            while (true) {
                delay(1_000)
                if (SystemClock.elapsedRealtime() - lastFixElapsed > 5_000) {
                    TelemetryHub.update { it.copy(hasFix = false, speedMph = 0f) }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        if (segmenter.forceEnd(SystemClock.elapsedRealtime())) finishTrip(System.currentTimeMillis())
        runCatching { lm.removeUpdates(this) }
        runCatching { sm.unregisterListener(this) }
        TelemetryHub.update { LiveState() }
        tickScope.cancel()
        // let queued DB writes drain before cancelling
        dbScope.launch { dbScope.cancel() }
        super.onDestroy()
    }

    // ---------- setup ----------

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

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

    private fun closeOrphanTrips() {
        dbScope.launch {
            dao.openTrips().forEach { t ->
                val end = dao.lastPointTime(t.id) ?: t.startMs
                dao.updateTrip(t.copy(endMs = end))
            }
        }
    }

    // ---------- location ----------

    override fun onLocationChanged(loc: Location) {
        val nowEl = SystemClock.elapsedRealtime()
        val dtMs = if (lastFixElapsed > 0) (nowEl - lastFixElapsed).coerceAtMost(5_000) else 0L
        lastFixElapsed = nowEl
        lastLoc = loc

        val speed = if (loc.hasSpeed()) loc.speed.coerceAtLeast(0f) else 0f
        val alt = if (loc.hasAltitude()) loc.altitude else null
        if (alt != null) altEma = altEma?.let { it * 0.8 + alt * 0.2 } ?: alt
        val moving = speed > 0.5f

        when (segmenter.update(nowEl, speed)) {
            SegEvent.START -> startTrip()
            SegEvent.END -> finishTrip(System.currentTimeMillis() - (nowEl - segmenter.endedAtMs))
            SegEvent.NONE -> Unit
        }

        var gradeNow: Float? = null
        if (moving && altEma != null) gradeNow = grade.add(speed * dtMs / 1000.0, altEma!!)

        if (segmenter.active) {
            if (moving) {
                distM += speed * dtMs / 1000.0
                movingMs += dtMs
                altEma?.let { elev.add(it) }
            } else {
                stoppedMs += dtMs
            }
            if (speed > maxMps) maxMps = speed
            pointBuf += PointEntity(
                tripId = 0, tMs = System.currentTimeMillis(), lat = loc.latitude, lon = loc.longitude,
                altM = altEma ?: 0.0, speedMps = speed,
            )
            if (pointBuf.size >= 5) flushPoints()
            if (nowEl - lastTripSave > 10_000) saveTrip(final = false, endWall = null)
        }

        TelemetryHub.update {
            it.copy(
                hasFix = true,
                speedMph = (speed * Geo.MPS_TO_MPH).toFloat(),
                bearingDeg = if (loc.hasBearing() && speed > 1f) loc.bearing else it.bearingDeg,
                altFt = altEma?.times(Geo.M_TO_FT),
                lat = loc.latitude,
                lon = loc.longitude,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                gradePct = gradeNow ?: it.gradePct,
                recording = segmenter.active,
                tripMiles = distM * Geo.M_TO_MI,
                tripMovingSec = movingMs / 1000,
                tripMaxMph = (maxMps * Geo.MPS_TO_MPH).toFloat(),
            )
        }
    }

    @Deprecated("Required on API < 30")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) {
        TelemetryHub.update { it.copy(hasFix = false) }
    }

    // ---------- trip lifecycle ----------

    private fun startTrip() {
        distM = 0.0; movingMs = 0; stoppedMs = 0; maxMps = 0f
        nAccel = 0; nBrake = 0; nCorner = 0
        elev = ElevationAccumulator()
        harsh.reset()
        pointBuf.clear()
        tripStartWall = System.currentTimeMillis()
        val d = CompletableDeferred<Long>()
        tripDeferred = d
        val start = tripStartWall
        dbScope.launch { d.complete(dao.insertTrip(TripEntity(startMs = start))) }
        lastTripSave = SystemClock.elapsedRealtime()
    }

    private fun finishTrip(endWall: Long) {
        flushPoints()
        saveTrip(final = true, endWall = endWall)
        tripDeferred = null
        TelemetryHub.update { it.copy(recording = false) }
    }

    private fun flushPoints() {
        val d = tripDeferred ?: return
        if (pointBuf.isEmpty()) return
        val batch = pointBuf.toList()
        pointBuf.clear()
        dbScope.launch { dao.insertPoints(batch.map { it.copy(tripId = d.await()) }) }
    }

    private fun saveTrip(final: Boolean, endWall: Long?) {
        val d = tripDeferred ?: return
        lastTripSave = SystemClock.elapsedRealtime()
        val snap = TripEntity(
            startMs = tripStartWall, endMs = if (final) endWall else null,
            distanceM = distM, maxSpeedMps = maxMps, movingMs = movingMs, stoppedMs = stoppedMs,
            elevGainM = elev.gainM.toFloat(), harshAccel = nAccel, harshBrake = nBrake, harshCorner = nCorner,
        )
        dbScope.launch { dao.updateTrip(snap.copy(id = d.await())) }
    }

    private fun recordHarsh(type: HarshType, g: Float) {
        when (type) {
            HarshType.ACCEL -> nAccel++
            HarshType.BRAKE -> nBrake++
            HarshType.CORNER -> nCorner++
        }
        val d = tripDeferred ?: return
        val loc = lastLoc ?: return
        val ev = EventEntity(
            tripId = 0, tMs = System.currentTimeMillis(), type = type.name,
            lat = loc.latitude, lon = loc.longitude, g = g,
        )
        dbScope.launch { dao.insertEvent(ev.copy(tripId = d.await())) }
    }

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
            val gl = gravityLpf?.let { it * 0.98f + v * 0.02f } ?: v
            gravityLpf = gl
            gravity = gl
            linear = v - gl
        }
        val grav = gravity ?: return
        val g = GForce.toVehicle(grav, linear, AppGraph.settings.value.mountYawDeg) ?: return

        longEma += (g.longG - longEma) * 0.15f
        latEma += (g.latG - latEma) * 0.15f

        val nowEl = SystemClock.elapsedRealtime()
        if (segmenter.active) {
            val speed = lastLoc?.takeIf { it.hasSpeed() }?.speed ?: 0f
            harsh.update(nowEl, longEma, latEma, speed)?.let { type ->
                recordHarsh(type, if (type == HarshType.CORNER) latEma else longEma)
            }
        }
        if (nowEl - lastHubPush >= 100) {
            lastHubPush = nowEl
            TelemetryHub.update { it.copy(longG = longEma, latG = latEma) }
        }
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

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, DriveService::class.java))
        }
    }
}
