package com.sparkborne.telemetry.model

import kotlinx.serialization.Serializable

/**
 * Normalized, immutable daily telemetry snapshot sourced exclusively from SparkyFitness.
 *
 * Keyed by ISO date (`YYYY-MM-DD`). [contentHash] is a deterministic SHA-256 hash of all
 * telemetry fields excluding [syncedAtEpochMs], guaranteeing idempotent re-syncs.
 */
@Serializable
data class DailyTelemetrySnapshot(
    val date: String,
    val goals: TelemetryGoals,
    val nutrition: NutritionSummary,
    val hydration: HydrationSummary,
    val sleep: SleepSummary,
    val steps: Int,
    val lifting: LiftingSummary,
    val bodyweightKg: Double?,
    val adherence: GoalAdherenceSummary,
    val contentHash: String,
    val syncedAtEpochMs: Long,
    val metricSources: Map<String, String> = emptyMap(),
)

@Serializable
data class TelemetryGoals(
    val caloriesTarget: Int,
    val proteinGramsTarget: Double,
    val carbsGramsTarget: Double,
    val fatGramsTarget: Double,
    val waterMlTarget: Int,
    val stepsTarget: Int,
    val sleepMinutesTarget: Int,
)

@Serializable
data class NutritionSummary(
    val calories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val foodEntryCount: Int,
)

@Serializable
data class HydrationSummary(
    val totalMl: Int,
    val manualMl: Int,
    val ledgerMl: Int,
    val foodMl: Int,
)

@Serializable
data class SleepSummary(
    val totalMinutes: Int,
    val deepMinutes: Int,
    val remMinutes: Int,
    val lightMinutes: Int,
    val awakeMinutes: Int,
    val sessionCount: Int,
)

@Serializable
data class LiftingSummary(
    val totalVolumeKg: Double,
    val totalWorkingSets: Int,
    val totalReps: Int,
    val workoutCount: Int,
    val byMuscleGroup: Map<MuscleGroup, MuscleGroupVolume>,
)

/**
 * Actual-to-target adherence ratios (0.0 .. unbounded; e.g. 1.0 = 100% of SparkyFitness goal).
 */
@Serializable
data class GoalAdherenceSummary(
    val caloriesRatio: Double,
    val proteinRatio: Double,
    val carbsRatio: Double,
    val fatRatio: Double,
    val hydrationRatio: Double,
    val stepsRatio: Double,
    val sleepRatio: Double,
)
