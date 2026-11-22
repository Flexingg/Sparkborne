package com.sparkborne.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room persistence row for a normalized [com.sparkborne.telemetry.model.DailyTelemetrySnapshot].
 *
 * Keyed by ISO date (`YYYY-MM-DD`). Stores both indexed summary columns for fast queries and
 * the full canonical JSON payload + `content_hash` for strict idempotency.
 */
@Entity(tableName = "daily_telemetry_snapshots")
data class DailySnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,
    @ColumnInfo(name = "content_hash")
    val contentHash: String,
    @ColumnInfo(name = "synced_at_epoch_ms")
    val syncedAtEpochMs: Long,
    @ColumnInfo(name = "steps")
    val steps: Int,
    @ColumnInfo(name = "calories")
    val calories: Double,
    @ColumnInfo(name = "protein_grams")
    val proteinGrams: Double,
    @ColumnInfo(name = "hydration_ml")
    val hydrationMl: Int,
    @ColumnInfo(name = "sleep_minutes")
    val sleepMinutes: Int,
    @ColumnInfo(name = "total_lifting_volume_kg")
    val totalLiftingVolumeKg: Double,
    @ColumnInfo(name = "bodyweight_kg")
    val bodyweightKg: Double?,
    @ColumnInfo(name = "snapshot_json")
    val snapshotJson: String,
)
