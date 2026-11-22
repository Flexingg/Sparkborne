package com.sparkborne.telemetry.normalizer

import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import com.sparkborne.telemetry.model.MuscleGroup
import com.sparkborne.telemetry.model.TelemetryDelta
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.round

/**
 * Guarantees idempotent telemetry ingestion:
 * - [computeContentHash] hashes all normalized telemetry fields (excluding `syncedAtEpochMs`
 *   and `contentHash`), so re-syncing unchanged data produces the exact same SHA-256 digest.
 * - [computeDelta] produces strictly non-negative incremental deltas between a previous
 *   processed snapshot and a newly synced snapshot so Phase 2 never double-counts rewards.
 */
object SnapshotIdempotency {

    private val canonicalJson = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    fun computeContentHash(snapshot: DailyTelemetrySnapshot): String {
        val normalizedForHash = snapshot.copy(
            contentHash = "",
            syncedAtEpochMs = 0L,
        )
        val payload = canonicalJson.encodeToString(normalizedForHash)
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    fun computeDelta(
        previous: DailyTelemetrySnapshot?,
        current: DailyTelemetrySnapshot,
    ): TelemetryDelta {
        if (previous != null && previous.contentHash == current.contentHash) {
            return TelemetryDelta(
                date = current.date,
                hasChanged = false,
                stepsDelta = 0,
                hydrationMlDelta = 0,
                sleepMinutesDelta = 0,
                caloriesDelta = 0.0,
                proteinGramsDelta = 0.0,
                totalLiftingVolumeKgDelta = 0.0,
                effectiveMuscleVolumeKgDeltas = emptyMap(),
            )
        }

        val prevSteps = previous?.steps ?: 0
        val prevHydration = previous?.hydration?.totalMl ?: 0
        val prevSleep = previous?.sleep?.totalMinutes ?: 0
        val prevCalories = previous?.nutrition?.calories ?: 0.0
        val prevProtein = previous?.nutrition?.proteinGrams ?: 0.0
        val prevTotalLift = previous?.lifting?.totalVolumeKg ?: 0.0

        val muscleDeltas = buildMap {
            for ((group, currentVol) in current.lifting.byMuscleGroup) {
                val prevEffective = previous?.lifting?.byMuscleGroup?.get(group)?.effectiveVolumeKg ?: 0.0
                val delta = round2(max(0.0, currentVol.effectiveVolumeKg - prevEffective))
                if (delta > 0.0) {
                    put(group, delta)
                }
            }
        }

        return TelemetryDelta(
            date = current.date,
            hasChanged = true,
            stepsDelta = max(0, current.steps - prevSteps),
            hydrationMlDelta = max(0, current.hydration.totalMl - prevHydration),
            sleepMinutesDelta = max(0, current.sleep.totalMinutes - prevSleep),
            caloriesDelta = round2(max(0.0, current.nutrition.calories - prevCalories)),
            proteinGramsDelta = round2(max(0.0, current.nutrition.proteinGrams - prevProtein)),
            totalLiftingVolumeKgDelta = round2(max(0.0, current.lifting.totalVolumeKg - prevTotalLift)),
            effectiveMuscleVolumeKgDeltas = muscleDeltas,
        )
    }

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0
}
