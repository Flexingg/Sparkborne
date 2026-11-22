package com.sparkborne.telemetry.model

import kotlinx.serialization.Serializable

/**
 * Incremental, idempotent delta between a previously processed snapshot and a newly synced
 * snapshot for the same date.
 *
 * Phase 2 consumes only positive deltas so re-syncing SparkyFitness never double-counts steps,
 * lifting volume, nutrition, hydration, or sleep.
 */
@Serializable
data class TelemetryDelta(
    val date: String,
    val hasChanged: Boolean,
    val stepsDelta: Int,
    val hydrationMlDelta: Int,
    val sleepMinutesDelta: Int,
    val caloriesDelta: Double,
    val proteinGramsDelta: Double,
    val totalLiftingVolumeKgDelta: Double,
    val effectiveMuscleVolumeKgDeltas: Map<MuscleGroup, Double>,
)
