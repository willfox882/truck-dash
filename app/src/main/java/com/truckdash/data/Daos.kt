package com.truckdash.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert suspend fun insertTrip(t: TripEntity): Long
    @Update suspend fun updateTrip(t: TripEntity)
    @Insert suspend fun insertPoints(p: List<PointEntity>)
    @Insert suspend fun insertEvent(e: EventEntity)

    @Query("SELECT * FROM trips ORDER BY startMs DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun trip(id: Long): TripEntity?

    @Query("SELECT * FROM points WHERE tripId = :id ORDER BY tMs")
    suspend fun points(id: Long): List<PointEntity>

    @Query("SELECT * FROM events WHERE tripId = :id ORDER BY tMs")
    suspend fun events(id: Long): List<EventEntity>

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    @Query("SELECT COALESCE(SUM(distanceM), 0) FROM trips WHERE startMs >= :sinceMs")
    fun observeDistanceSince(sinceMs: Long): Flow<Double>

    @Query("SELECT * FROM trips WHERE endMs IS NULL")
    suspend fun openTrips(): List<TripEntity>

    @Query("SELECT MAX(tMs) FROM points WHERE tripId = :id")
    suspend fun lastPointTime(id: Long): Long?
}

@Dao
interface FuelDao {
    @Insert suspend fun insert(e: FuelLogEntity)
    @Delete suspend fun delete(e: FuelLogEntity)

    @Query("SELECT * FROM fuel_logs ORDER BY odometerMi DESC")
    fun observeAll(): Flow<List<FuelLogEntity>>
}

@Dao
interface MaintDao {
    @Insert suspend fun insert(e: MaintenanceItemEntity)
    @Update suspend fun update(e: MaintenanceItemEntity)
    @Delete suspend fun delete(e: MaintenanceItemEntity)

    @Query("SELECT * FROM maintenance_items ORDER BY name")
    fun observeAll(): Flow<List<MaintenanceItemEntity>>

    @Query("SELECT COUNT(*) FROM maintenance_items")
    suspend fun count(): Int
}
