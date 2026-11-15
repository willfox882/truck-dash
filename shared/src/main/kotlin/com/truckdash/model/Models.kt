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
    val odometerMi: Double,
    val gallons: Double,
    val pricePerGal: Double,
    val fullTank: Boolean = true,
)

data class MaintItem(
    val id: Long = 0,
    val name: String,
    val intervalMiles: Int? = null,
    val intervalDays: Int? = null,
    val lastOdoMi: Double = 0.0,
    val lastDateMs: Long,
)

data class Settings(
    /** Odometer reading the user entered, at [odoBaseSetMs]. Live odometer = base + trips since. */
    val odoBaseMi: Double = 0.0,
    val odoBaseSetMs: Long = 0L,
    /** Passenger mode disables the "parked only" lock on the Trips/Health tabs. */
    val passengerMode: Boolean = false,
    /** Extra yaw correction (deg) if the unit is mounted rotated so forward is not screen -Z. */
    val mountYawDeg: Float = 0f,
)

data class LiveState(
    val serviceRunning: Boolean = false,
    val hasFix: Boolean = false,
    val speedMph: Float = 0f,
    val bearingDeg: Float? = null,
    val altFt: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    val longG: Float = 0f,
    val latG: Float = 0f,
    val gradePct: Float? = null,
    val recording: Boolean = false,
    val tripMiles: Double = 0.0,
    val tripMovingSec: Long = 0,
    val tripMaxMph: Float = 0f,
) {
    val tripAvgMph: Float
        get() = if (tripMovingSec > 0) (tripMiles / (tripMovingSec / 3600.0)).toFloat() else 0f
}
