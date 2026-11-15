package com.truckdash.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(
    tableName = "points",
    foreignKeys = [ForeignKey(TripEntity::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val tMs: Long,
    val lat: Double,
    val lon: Double,
    val altM: Double,
    val speedMps: Float,
)

@Entity(
    tableName = "events",
    foreignKeys = [ForeignKey(TripEntity::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val tMs: Long,
    /** HarshType name */
    val type: String,
    val lat: Double,
    val lon: Double,
    val g: Float,
)

@Entity(tableName = "fuel_logs")
data class FuelLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMs: Long,
    val odometerMi: Double,
    val gallons: Double,
    val pricePerGal: Double,
    val fullTank: Boolean = true,
)

@Entity(tableName = "maintenance_items")
data class MaintenanceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val intervalMiles: Int? = null,
    val intervalDays: Int? = null,
    val lastOdoMi: Double = 0.0,
    val lastDateMs: Long,
)
