package com.truckdash.model

/** Plain models shared by the Android app and the desktop sandbox (no Room/Android types). */

data class Trip(
    val id: Long = 0,
    val startMs: Long,
    val endMs: Long? = null,
    val distanceM: Double = 0.0,
    val maxSpeedMps: Float = 0f,
    val movingMs: Long = 0,
    val stoppedMs: Long = 0,
    val elevGainM: Float = 0f,
    val harshAccel: Int = 0,
    val harshBrake: Int = 0,
    val harshCorner: Int = 0,
)

data class TrackPoint(val tMs: Long, val lat: Double, val lon: Double, val altM: Double, val speedMps: Float)

/** [type] is a HarshType name. */
data class DriveEvent(val tMs: Long, val type: String, val lat: Double, val lon: Double, val g: Float)

data class TripDetail(val trip: Trip, val points: List<TrackPoint>, val events: List<DriveEvent>)

data class FuelLog(
    val id: Long = 0,
    val dateMs: Long,
    val odometerKm: Double,
    val litres: Double,
    val pricePerLitre: Double,
    val fullTank: Boolean = true,
)

data class MaintItem(
    val id: Long = 0,
    val name: String,
    val intervalKm: Int? = null,
    val intervalDays: Int? = null,
    val lastOdoKm: Double = 0.0,
    val lastDateMs: Long,
)

data class Settings(
    /** Odometer reading (km) the user entered, at [odoBaseSetMs]. Live odometer = base + trips since. */
    val odoBaseKm: Double = 0.0,
    /** Wall time the odometer was set (0 = never set). */
    val odoBaseSetMs: Long = 0L,
    /** Lifetime GPS-driven metres, maintained independently of trip rows (deleting a trip can't change it). */
    val distTotalM: Double = 0.0,
    /** [distTotalM] at the moment the odometer was set. Odometer = odoBaseKm + (distTotalM - odoBaseTotalM) / 1000. */
    val odoBaseTotalM: Double = 0.0,
    /** Passenger mode disables the "parked only" lock on the Trips/Health tabs. */
    val passengerMode: Boolean = false,
    /** Extra yaw correction (deg) if the unit is mounted rotated so forward is not screen -Z. */
    val mountYawDeg: Float = 0f,
)

data class LiveState(
    val serviceRunning: Boolean = false,
    val hasFix: Boolean = false,
    val speedKmh: Float = 0f,
    val bearingDeg: Float? = null,
    val altM: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    val longG: Float = 0f,
    val latG: Float = 0f,
    val gradePct: Float? = null,
    val recording: Boolean = false,
    val tripKm: Double = 0.0,
    val tripMovingSec: Long = 0,
    val tripMaxKmh: Float = 0f,
) {
    val tripAvgKmh: Float
        get() = if (tripMovingSec > 0) (tripKm / (tripMovingSec / 3600.0)).toFloat() else 0f
}
