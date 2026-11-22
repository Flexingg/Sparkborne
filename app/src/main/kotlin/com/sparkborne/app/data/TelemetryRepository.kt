package com.sparkborne.app.data

import com.sparkborne.app.data.db.DailySnapshotDao
import com.sparkborne.app.data.db.DailySnapshotEntity
import com.sparkborne.telemetry.client.SparkyFitnessApiClient
import com.sparkborne.telemetry.client.SparkyFitnessConfig
import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import com.sparkborne.telemetry.model.TelemetryDelta
import com.sparkborne.telemetry.normalizer.SnapshotIdempotency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

data class SyncRangeResult(
    val snapshots: List<DailyTelemetrySnapshot>,
    val deltas: List<TelemetryDelta>,
    val updatedDays: Int,
    val unchangedDays: Int,
    val providerSync: com.sparkborne.telemetry.client.ProviderSyncReport? = null,
)

/**
 * Coordinates idempotent syncs from SparkyFitness into the local Room snapshot cache.
 */
class TelemetryRepository(
    private val dao: DailySnapshotDao,
    private val json: Json = SparkyFitnessApiClient.defaultJson,
) {

    fun observeSnapshots(): Flow<List<DailyTelemetrySnapshot>> {
        return dao.observeAll().map { entities ->
            entities.mapNotNull { entity ->
                runCatching { json.decodeFromString<DailyTelemetrySnapshot>(entity.snapshotJson) }.getOrNull()
            }
        }
    }

    suspend fun testConnection(config: SparkyFitnessConfig): Result<String> {
        config.validate().onFailure { return Result.failure(it) }
        return SparkyFitnessApiClient(config).ping()
    }

    suspend fun syncRange(
        config: SparkyFitnessConfig,
        startDate: LocalDate,
        endDate: LocalDate,
        triggerUpstreamProviders: Boolean = true,
    ): Result<SyncRangeResult> = runCatching {
        config.validate().getOrThrow()

        val client = SparkyFitnessApiClient(
            config.copy(readTimeoutSeconds = maxOf(config.readTimeoutSeconds, 60L)),
        )

        val providerReport = if (triggerUpstreamProviders) {
            client.triggerUpstreamProviderSyncs(startDate, endDate)
        } else {
            null
        }

        val fetched = client.fetchNormalizedRange(startDate, endDate)
        val existingByDate = dao.getRange(startDate.toString(), endDate.toString())
            .associate { entity ->
                entity.date to runCatching {
                    json.decodeFromString<DailyTelemetrySnapshot>(entity.snapshotJson)
                }.getOrNull()
            }

        val deltas = mutableListOf<TelemetryDelta>()
        val toUpsert = mutableListOf<DailySnapshotEntity>()
        var updatedCount = 0
        var unchangedCount = 0

        for (snapshot in fetched) {
            val previous = existingByDate[snapshot.date]
            val delta = SnapshotIdempotency.computeDelta(previous, snapshot)
            deltas.add(delta)

            if (delta.hasChanged || previous == null) {
                updatedCount += 1
                toUpsert.add(snapshot.toEntity())
            } else {
                unchangedCount += 1
            }
        }

        if (toUpsert.isNotEmpty()) {
            dao.upsertAll(toUpsert)
        }

        SyncRangeResult(
            snapshots = fetched,
            deltas = deltas,
            updatedDays = updatedCount,
            unchangedDays = unchangedCount,
            providerSync = providerReport,
        )
    }

    private fun DailyTelemetrySnapshot.toEntity(): DailySnapshotEntity {
        return DailySnapshotEntity(
            date = date,
            contentHash = contentHash,
            syncedAtEpochMs = syncedAtEpochMs,
            steps = steps,
            calories = nutrition.calories,
            proteinGrams = nutrition.proteinGrams,
            hydrationMl = hydration.totalMl,
            sleepMinutes = sleep.totalMinutes,
            totalLiftingVolumeKg = lifting.totalVolumeKg,
            bodyweightKg = bodyweightKg,
            snapshotJson = json.encodeToString(this),
        )
    }
}
