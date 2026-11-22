package com.sparkborne.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySnapshotDao {

    @Query("SELECT * FROM daily_telemetry_snapshots ORDER BY date DESC")
    fun observeAll(): Flow<List<DailySnapshotEntity>>

    @Query("SELECT * FROM daily_telemetry_snapshots WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailySnapshotEntity?

    @Query("SELECT * FROM daily_telemetry_snapshots WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getRange(startDate: String, endDate: String): List<DailySnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DailySnapshotEntity>)
}
