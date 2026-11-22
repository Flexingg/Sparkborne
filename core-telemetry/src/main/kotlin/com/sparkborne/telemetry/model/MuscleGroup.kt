package com.sparkborne.telemetry.model

import kotlinx.serialization.Serializable

/**
 * Canonical muscle groups used across Sparkborne.
 * Phase 2 maps per-muscle-group lifting volume into character strength and weapon damage scaling.
 */
@Serializable
enum class MuscleGroup(val displayName: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"),
    CALVES("Calves"),
    CORE("Core"),
    FULL_BODY("Full Body"),
    OTHER("Other"),
}

/**
 * Aggregated lifting volume for a single muscle group on a given day.
 *
 * - [primaryVolumeKg]: total `reps * weightKg` from exercises targeting this muscle primarily (1.0x).
 * - [secondaryVolumeKg]: total `reps * weightKg` from exercises targeting this muscle secondarily (raw).
 * - [effectiveVolumeKg]: `primaryVolumeKg + (0.5 * secondaryVolumeKg)`, ready for Phase 2 strength scaling.
 */
@Serializable
data class MuscleGroupVolume(
    val muscleGroup: MuscleGroup,
    val primaryVolumeKg: Double,
    val secondaryVolumeKg: Double,
    val effectiveVolumeKg: Double,
    val workingSets: Int,
    val totalReps: Int,
    val topSetWeightKg: Double,
    val exerciseNames: List<String>,
)
