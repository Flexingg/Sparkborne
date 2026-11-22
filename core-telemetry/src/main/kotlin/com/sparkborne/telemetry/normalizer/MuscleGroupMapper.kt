package com.sparkborne.telemetry.normalizer

import com.sparkborne.telemetry.model.MuscleGroup

/**
 * Deterministically maps SparkyFitness / Free Exercise DB / Liftosaur muscle names
 * (and fallback exercise names) to canonical [MuscleGroup] buckets.
 */
object MuscleGroupMapper {

    private val exactAliases: Map<String, MuscleGroup> = mapOf(
        // Chest
        "chest" to MuscleGroup.CHEST,
        "pectorals" to MuscleGroup.CHEST,
        "pecs" to MuscleGroup.CHEST,
        "upper chest" to MuscleGroup.CHEST,
        "lower chest" to MuscleGroup.CHEST,
        // Back
        "back" to MuscleGroup.BACK,
        "lats" to MuscleGroup.BACK,
        "latissimus dorsi" to MuscleGroup.BACK,
        "middle back" to MuscleGroup.BACK,
        "upper back" to MuscleGroup.BACK,
        "lower back" to MuscleGroup.BACK,
        "traps" to MuscleGroup.BACK,
        "trapezius" to MuscleGroup.BACK,
        "rhomboids" to MuscleGroup.BACK,
        "erector spinae" to MuscleGroup.BACK,
        "neck" to MuscleGroup.BACK,
        // Shoulders
        "shoulders" to MuscleGroup.SHOULDERS,
        "deltoids" to MuscleGroup.SHOULDERS,
        "delts" to MuscleGroup.SHOULDERS,
        "front delts" to MuscleGroup.SHOULDERS,
        "side delts" to MuscleGroup.SHOULDERS,
        "rear delts" to MuscleGroup.SHOULDERS,
        "rotator cuff" to MuscleGroup.SHOULDERS,
        // Arms
        "biceps" to MuscleGroup.BICEPS,
        "brachialis" to MuscleGroup.BICEPS,
        "triceps" to MuscleGroup.TRICEPS,
        "forearms" to MuscleGroup.FOREARMS,
        "wrists" to MuscleGroup.FOREARMS,
        "grip" to MuscleGroup.FOREARMS,
        // Lower body
        "quadriceps" to MuscleGroup.QUADS,
        "quads" to MuscleGroup.QUADS,
        "thighs" to MuscleGroup.QUADS,
        "adductors" to MuscleGroup.QUADS,
        "hip flexors" to MuscleGroup.QUADS,
        "hamstrings" to MuscleGroup.HAMSTRINGS,
        "glutes" to MuscleGroup.GLUTES,
        "gluteus" to MuscleGroup.GLUTES,
        "abductors" to MuscleGroup.GLUTES,
        "calves" to MuscleGroup.CALVES,
        "soleus" to MuscleGroup.CALVES,
        "gastrocnemius" to MuscleGroup.CALVES,
        // Core
        "abdominals" to MuscleGroup.CORE,
        "abs" to MuscleGroup.CORE,
        "core" to MuscleGroup.CORE,
        "obliques" to MuscleGroup.CORE,
        // Full body
        "full body" to MuscleGroup.FULL_BODY,
        "full_body" to MuscleGroup.FULL_BODY,
        "olympic" to MuscleGroup.FULL_BODY,
    )

    fun mapMuscleName(raw: String): MuscleGroup {
        val normalized = raw.trim().lowercase().replace('_', ' ').replace('-', ' ')
        exactAliases[normalized]?.let { return it }

        return when {
            normalized.contains("pec") || normalized.contains("chest") -> MuscleGroup.CHEST
            normalized.contains("lat") || normalized.contains("back") || normalized.contains("trap") || normalized.contains("rhomboid") -> MuscleGroup.BACK
            normalized.contains("delt") || normalized.contains("shoulder") -> MuscleGroup.SHOULDERS
            normalized.contains("bicep") -> MuscleGroup.BICEPS
            normalized.contains("tricep") -> MuscleGroup.TRICEPS
            normalized.contains("forearm") || normalized.contains("wrist") -> MuscleGroup.FOREARMS
            normalized.contains("quad") || normalized.contains("adductor") -> MuscleGroup.QUADS
            normalized.contains("hamstring") -> MuscleGroup.HAMSTRINGS
            normalized.contains("glute") || normalized.contains("abductor") -> MuscleGroup.GLUTES
            normalized.contains("calf") || normalized.contains("calves") -> MuscleGroup.CALVES
            normalized.contains("ab") || normalized.contains("oblique") || normalized.contains("core") -> MuscleGroup.CORE
            normalized.contains("full") -> MuscleGroup.FULL_BODY
            else -> MuscleGroup.OTHER
        }
    }

    /**
     * Fallback inference from exercise name when SparkyFitness exercise metadata has no muscle list.
     */
    fun inferFromExerciseName(exerciseName: String?): List<MuscleGroup> {
        val name = exerciseName?.trim()?.lowercase() ?: return listOf(MuscleGroup.OTHER)
        return when {
            name.contains("bench") || name.contains("chest press") || name.contains("fly") || name.contains("push-up") || name.contains("pushup") ->
                listOf(MuscleGroup.CHEST)
            name.contains("deadlift") || name.contains("rdl") ->
                listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.BACK, MuscleGroup.GLUTES)
            name.contains("squat") || name.contains("leg press") || name.contains("leg extension") || name.contains("lunge") ->
                listOf(MuscleGroup.QUADS)
            name.contains("leg curl") || name.contains("hamstring") ->
                listOf(MuscleGroup.HAMSTRINGS)
            name.contains("hip thrust") || name.contains("glute") ->
                listOf(MuscleGroup.GLUTES)
            name.contains("calf") ->
                listOf(MuscleGroup.CALVES)
            name.contains("pull-up") || name.contains("pullup") || name.contains("chin-up") || name.contains("pulldown") || name.contains("row") ->
                listOf(MuscleGroup.BACK)
            name.contains("overhead press") || name.contains("ohp") || name.contains("shoulder press") || name.contains("lateral raise") || name.contains("face pull") ->
                listOf(MuscleGroup.SHOULDERS)
            name.contains("curl") ->
                listOf(MuscleGroup.BICEPS)
            name.contains("tricep") || name.contains("pushdown") || name.contains("skullcrusher") || name.contains("dip") ->
                listOf(MuscleGroup.TRICEPS)
            name.contains("plank") || name.contains("crunch") || name.contains("ab ") || name.contains("leg raise") ->
                listOf(MuscleGroup.CORE)
            else -> listOf(MuscleGroup.OTHER)
        }
    }
}
