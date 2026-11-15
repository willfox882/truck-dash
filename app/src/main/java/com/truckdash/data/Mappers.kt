package com.truckdash.data

import com.truckdash.model.DriveEvent
import com.truckdash.model.FuelLog
import com.truckdash.model.MaintItem
import com.truckdash.model.TrackPoint
import com.truckdash.model.Trip

fun TripEntity.toModel() = Trip(
    id, startMs, endMs, distanceM, maxSpeedMps, movingMs, stoppedMs, elevGainM, harshAccel, harshBrake, harshCorner,
)

fun Trip.toEntity(id: Long = this.id) = TripEntity(
    id, startMs, endMs, distanceM, maxSpeedMps, movingMs, stoppedMs, elevGainM, harshAccel, harshBrake, harshCorner,
)

fun PointEntity.toModel() = TrackPoint(tMs, lat, lon, altM, speedMps)
fun TrackPoint.toEntity(tripId: Long) = PointEntity(tripId = tripId, tMs = tMs, lat = lat, lon = lon, altM = altM, speedMps = speedMps)

fun EventEntity.toModel() = DriveEvent(tMs, type, lat, lon, g)
fun DriveEvent.toEntity(tripId: Long) = EventEntity(tripId = tripId, tMs = tMs, type = type, lat = lat, lon = lon, g = g)

fun FuelLogEntity.toModel() = FuelLog(id, dateMs, odometerMi, gallons, pricePerGal, fullTank)
fun FuelLog.toEntity() = FuelLogEntity(id, dateMs, odometerMi, gallons, pricePerGal, fullTank)

fun MaintenanceItemEntity.toModel() = MaintItem(id, name, intervalMiles, intervalDays, lastOdoMi, lastDateMs)
fun MaintItem.toEntity() = MaintenanceItemEntity(id, name, intervalMiles, intervalDays, lastOdoMi, lastDateMs)
