package com.sparkborne.telemetry.normalizer

import com.sparkborne.telemetry.dto.CheckInMeasurementDto
import com.sparkborne.telemetry.dto.DailySummaryResponseDto
import com.sparkborne.telemetry.dto.ExerciseEntryDto
import com.sparkborne.telemetry.dto.ExerciseSessionDto
import com.sparkborne.telemetry.dto.SleepEntryDto
import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import com.sparkborne.telemetry.model.GoalAdherenceSummary
import com.sparkborne.telemetry.model.HydrationSummary
import com.sparkborne.telemetry.model.LiftingSummary
import com.sparkborne.telemetry.model.MuscleGroup
import com.sparkborne.telemetry.model.MuscleGroupVolume
import com.sparkborne.telemetry.model.NutritionSummary
import com.sparkborne.telemetry.model.SleepSummary
import com.sparkborne.telemetry.model.TelemetryGoals
import java.time.Instant
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Pure, deterministic normalizer transforming raw SparkyFitness REST payloads into a canonical
 * [DailyTelemetrySnapshot].
 */
object TelemetryNormalizer {

    const val DEFAULT_CALORIES_TARGET = 2000
    const val DEFAULT_PROTEIN_TARGET_G = 150.0
    const val DEFAULT_CARBS_TARGET_G = 200.0
    const val DEFAULT_FAT_TARGET_G = 65.0
    const val DEFAULT_WATER_TARGET_ML = 2500
    const val DEFAULT_STEPS_TARGET = 10000
    const val DEFAULT_SLEEP_TARGET_MIN = 480
    const val SECONDARY_MUSCLE_WEIGHT = 0.5

    fun normalize(
        date: String,
        dailySummary: DailySummaryResponseDto?,
        checkIn: CheckInMeasurementDto?,
        sleepEntries: List<SleepEntryDto>,
        syncedAtEpochMs: Long,
    ): DailyTelemetrySnapshot {
        val goals = normalizeGoals(dailySummary)
        val nutrition = normalizeNutrition(dailySummary)
        val hydration = normalizeHydration(dailySummary)
        val sleep = normalizeSleep(sleepEntries)
        val lifting = normalizeLifting(dailySummary?.exerciseSessions.orEmpty())

        val workoutSteps = dailySummary?.exerciseSessions
            .orEmpty()
            .sumOf { session ->
                val sessionSteps = session.steps?.roundToInt() ?: 0
                val exerciseSteps = session.exercises.sumOf { it.steps?.roundToInt() ?: 0 }
                max(sessionSteps, exerciseSteps)
            }
        val steps = max(0, checkIn?.steps?.roundToInt() ?: workoutSteps)

        val bodyweightKg = checkIn?.weight?.takeIf { it > 0.0 }
            ?: dailySummary?.calorieInputs?.weightKg?.takeIf { it > 0.0 }

        val adherence = GoalAdherenceSummary(
            caloriesRatio = safeRatio(nutrition.calories, goals.caloriesTarget.toDouble()),
            proteinRatio = safeRatio(nutrition.proteinGrams, goals.proteinGramsTarget),
            carbsRatio = safeRatio(nutrition.carbsGrams, goals.carbsGramsTarget),
            fatRatio = safeRatio(nutrition.fatGrams, goals.fatGramsTarget),
            hydrationRatio = safeRatio(hydration.totalMl.toDouble(), goals.waterMlTarget.toDouble()),
            stepsRatio = safeRatio(steps.toDouble(), goals.stepsTarget.toDouble()),
            sleepRatio = safeRatio(sleep.totalMinutes.toDouble(), goals.sleepMinutesTarget.toDouble()),
        )

        val sources = buildMap {
            if (steps > 0) put("steps", "SparkyFitness")
            if (sleep.totalMinutes > 0) put("sleep", "SparkyFitness")
            if (hydration.totalMl > 0) put("hydration", "SparkyFitness")
            if (nutrition.calories > 0.0 || nutrition.proteinGrams > 0.0) put("nutrition", "SparkyFitness")
            if (bodyweightKg != null) put("weight", "SparkyFitness")
            if (lifting.totalVolumeKg > 0.0) put("lifting", "SparkyFitness (Liftosaur)")
        }

        val base = DailyTelemetrySnapshot(
            date = date,
            goals = goals,
            nutrition = nutrition,
            hydration = hydration,
            sleep = sleep,
            steps = steps,
            lifting = lifting,
            bodyweightKg = bodyweightKg?.let(::round2),
            adherence = adherence,
            contentHash = "",
            syncedAtEpochMs = syncedAtEpochMs,
            metricSources = sources,
        )

        return base.copy(contentHash = SnapshotIdempotency.computeContentHash(base))
    }

    private fun normalizeGoals(summary: DailySummaryResponseDto?): TelemetryGoals {
        val raw = summary?.goals
        val adj = summary?.adjustedGoals

        val calories = (adj?.calories ?: raw?.calories)?.takeIf { it > 0 }?.roundToInt()
            ?: DEFAULT_CALORIES_TARGET
        val protein = (adj?.protein ?: raw?.protein)?.takeIf { it > 0 } ?: DEFAULT_PROTEIN_TARGET_G
        val carbs = (adj?.carbs ?: raw?.carbs)?.takeIf { it > 0 } ?: DEFAULT_CARBS_TARGET_G
        val fat = (adj?.fat ?: raw?.fat)?.takeIf { it > 0 } ?: DEFAULT_FAT_TARGET_G
        val waterMl = (adj?.waterGoalMl ?: raw?.waterGoalMl ?: raw?.waterGoalLegacy)
            ?.takeIf { it > 0 }
            ?.roundToInt()
            ?: DEFAULT_WATER_TARGET_ML
        val steps = (raw?.stepsGoal ?: raw?.stepGoalAlt)?.takeIf { it > 0 }?.roundToInt()
            ?: DEFAULT_STEPS_TARGET
        val sleepMinutes = raw?.sleepGoalMinutes?.takeIf { it > 0 }?.roundToInt()
            ?: raw?.sleepGoalHours?.takeIf { it > 0 }?.let { (it * 60.0).roundToInt() }
            ?: DEFAULT_SLEEP_TARGET_MIN

        return TelemetryGoals(
            caloriesTarget = calories,
            proteinGramsTarget = round2(protein),
            carbsGramsTarget = round2(carbs),
            fatGramsTarget = round2(fat),
            waterMlTarget = waterMl,
            stepsTarget = steps,
            sleepMinutesTarget = sleepMinutes,
        )
    }

    private fun normalizeNutrition(summary: DailySummaryResponseDto?): NutritionSummary {
        if (summary == null) {
            return NutritionSummary(0.0, 0.0, 0.0, 0.0, 0)
        }
        val foods = summary.foodEntries
        val supp = summary.supplementTotals

        val foodCalories = foods.sumOf { max(0.0, it.calories ?: 0.0) }
        val foodProtein = foods.sumOf { max(0.0, it.protein ?: 0.0) }
        val foodCarbs = foods.sumOf { max(0.0, it.carbs ?: 0.0) }
        val foodFat = foods.sumOf { max(0.0, it.fat ?: 0.0) }

        val totalCalories = if (foods.isNotEmpty() || supp != null) {
            foodCalories + max(0.0, supp?.calories ?: 0.0)
        } else {
            max(0.0, summary.calorieBalance?.eaten ?: 0.0)
        }

        return NutritionSummary(
            calories = round2(totalCalories),
            proteinGrams = round2(foodProtein + max(0.0, supp?.protein ?: 0.0)),
            carbsGrams = round2(foodCarbs + max(0.0, supp?.carbs ?: 0.0)),
            fatGrams = round2(foodFat + max(0.0, supp?.fat ?: 0.0)),
            foodEntryCount = foods.size,
        )
    }

    private fun normalizeHydration(summary: DailySummaryResponseDto?): HydrationSummary {
        val breakdown = summary?.waterIntakeBreakdown
        val total = max(0, (breakdown?.waterMl ?: summary?.waterIntake ?: 0.0).roundToInt())
        return HydrationSummary(
            totalMl = total,
            manualMl = max(0, (breakdown?.manualMl ?: 0.0).roundToInt()),
            ledgerMl = max(0, (breakdown?.ledgerMl ?: 0.0).roundToInt()),
            foodMl = max(0, (breakdown?.foodMl ?: 0.0).roundToInt()),
        )
    }

    private fun normalizeSleep(entries: List<SleepEntryDto>): SleepSummary {
        if (entries.isEmpty()) {
            return SleepSummary(0, 0, 0, 0, 0, 0)
        }

        var totalSeconds = 0L
        var deepSeconds = 0L
        var remSeconds = 0L
        var lightSeconds = 0L
        var awakeSeconds = 0L

        for (entry in entries) {
            totalSeconds += max(0L, entry.durationInSeconds ?: 0L)
            for (stageEvent in entry.stageEvents.orEmpty()) {
                val stageDuration = resolveStageDurationSeconds(stageEvent)
                val stageName = (stageEvent.stage ?: stageEvent.stageType).orEmpty().trim().lowercase()
                when {
                    stageName.contains("deep") -> deepSeconds += stageDuration
                    stageName.contains("rem") -> remSeconds += stageDuration
                    stageName.contains("light") || stageName.contains("core") -> lightSeconds += stageDuration
                    stageName.contains("awake") || stageName.contains("wake") -> awakeSeconds += stageDuration
                }
            }
        }

        return SleepSummary(
            totalMinutes = (totalSeconds / 60L).toInt(),
            deepMinutes = (deepSeconds / 60L).toInt(),
            remMinutes = (remSeconds / 60L).toInt(),
            lightMinutes = (lightSeconds / 60L).toInt(),
            awakeMinutes = (awakeSeconds / 60L).toInt(),
            sessionCount = entries.size,
        )
    }

    private fun resolveStageDurationSeconds(event: com.sparkborne.telemetry.dto.SleepStageEventDto): Long {
        event.durationInSeconds?.let { return max(0L, it) }
        event.durationSeconds?.let { return max(0L, it) }
        if (!event.startTime.isNullOrBlank() && !event.endTime.isNullOrBlank()) {
            return runCatching {
                val start = Instant.parse(event.startTime)
                val end = Instant.parse(event.endTime)
                max(0L, end.epochSecond - start.epochSecond)
            }.getOrDefault(0L)
        }
        return 0L
    }

    private class MutableMuscleAccumulator {
        var primaryVolumeKg: Double = 0.0
        var secondaryVolumeKg: Double = 0.0
        var workingSets: Int = 0
        var totalReps: Int = 0
        var topSetWeightKg: Double = 0.0
        val exerciseNames: LinkedHashSet<String> = linkedSetOf()
    }

    private fun normalizeLifting(sessions: List<ExerciseSessionDto>): LiftingSummary {
        if (sessions.isEmpty()) {
            return LiftingSummary(0.0, 0, 0, 0, emptyMap())
        }

        val accumulators = LinkedHashMap<MuscleGroup, MutableMuscleAccumulator>()
        var totalVolumeKg = 0.0
        var totalWorkingSets = 0
        var totalReps = 0
        var liftingSessionCount = 0

        for (session in sessions) {
            val entries = extractExerciseEntries(session)
            var sessionHadLifting = false

            for (entry in entries) {
                val workingSets = extractWorkingSets(entry)
                if (workingSets.isEmpty()) continue

                sessionHadLifting = true
                val exerciseName = entry.exerciseSnapshot?.name?.takeIf { it.isNotBlank() }
                    ?: entry.name?.takeIf { it.isNotBlank() }
                    ?: "Unknown Exercise"

                var entryVolume = 0.0
                var entryReps = 0
                var entryTopWeight = 0.0
                for ((reps, weight) in workingSets) {
                    entryVolume += reps * weight
                    entryReps += reps
                    if (weight > entryTopWeight) entryTopWeight = weight
                }

                totalVolumeKg += entryVolume
                totalWorkingSets += workingSets.size
                totalReps += entryReps

                val primaryGroups = entry.exerciseSnapshot?.primaryMuscles
                    ?.filter { it.isNotBlank() }
                    ?.map(MuscleGroupMapper::mapMuscleName)
                    ?.distinct()
                    ?.takeIf { it.isNotEmpty() }
                    ?: MuscleGroupMapper.inferFromExerciseName(exerciseName)

                val secondaryGroups = entry.exerciseSnapshot?.secondaryMuscles
                    ?.filter { it.isNotBlank() }
                    ?.map(MuscleGroupMapper::mapMuscleName)
                    ?.distinct()
                    ?.filterNot { it in primaryGroups }
                    .orEmpty()

                val primaryShare = entryVolume / primaryGroups.size.toDouble()
                for (group in primaryGroups) {
                    val acc = accumulators.getOrPut(group) { MutableMuscleAccumulator() }
                    acc.primaryVolumeKg += primaryShare
                    acc.workingSets += workingSets.size
                    acc.totalReps += entryReps
                    if (entryTopWeight > acc.topSetWeightKg) acc.topSetWeightKg = entryTopWeight
                    acc.exerciseNames.add(exerciseName)
                }

                if (secondaryGroups.isNotEmpty()) {
                    val secondaryShare = entryVolume / secondaryGroups.size.toDouble()
                    for (group in secondaryGroups) {
                        val acc = accumulators.getOrPut(group) { MutableMuscleAccumulator() }
                        acc.secondaryVolumeKg += secondaryShare
                        acc.exerciseNames.add(exerciseName)
                    }
                }
            }

            if (sessionHadLifting) {
                liftingSessionCount += 1
            }
        }

        val sortedMuscleMap = accumulators
            .entries
            .sortedBy { it.key.ordinal }
            .associate { (group, acc) ->
                val primary = round2(acc.primaryVolumeKg)
                val secondary = round2(acc.secondaryVolumeKg)
                val effective = round2(primary + (SECONDARY_MUSCLE_WEIGHT * secondary))
                group to MuscleGroupVolume(
                    muscleGroup = group,
                    primaryVolumeKg = primary,
                    secondaryVolumeKg = secondary,
                    effectiveVolumeKg = effective,
                    workingSets = acc.workingSets,
                    totalReps = acc.totalReps,
                    topSetWeightKg = round2(acc.topSetWeightKg),
                    exerciseNames = acc.exerciseNames.sorted(),
                )
            }

        return LiftingSummary(
            totalVolumeKg = round2(totalVolumeKg),
            totalWorkingSets = totalWorkingSets,
            totalReps = totalReps,
            workoutCount = liftingSessionCount,
            byMuscleGroup = sortedMuscleMap,
        )
    }

    private fun extractExerciseEntries(session: ExerciseSessionDto): List<ExerciseEntryDto> {
        if (session.exercises.isNotEmpty()) {
            return session.exercises
        }
        return listOf(
            ExerciseEntryDto(
                id = session.id,
                name = session.name,
                durationMinutes = session.durationMinutes ?: session.totalDurationMinutes,
                steps = session.steps,
                sets = session.sets,
                reps = session.reps,
                weight = session.weight,
                exerciseSnapshot = session.exerciseSnapshot,
            ),
        )
    }

    private fun extractWorkingSets(entry: ExerciseEntryDto): List<Pair<Int, Double>> {
        if (entry.sets.isNotEmpty()) {
            return entry.sets.mapNotNull { set ->
                val isWarmup = set.setType?.equals("warmup", ignoreCase = true) == true
                if (isWarmup) return@mapNotNull null
                val reps = set.reps?.roundToInt() ?: 0
                val weight = max(0.0, set.weight ?: 0.0)
                if (reps <= 0) null else (reps to weight)
            }
        }
        val reps = entry.reps?.roundToInt() ?: 0
        val weight = max(0.0, entry.weight ?: 0.0)
        return if (reps > 0) listOf(reps to weight) else emptyList()
    }

    private fun safeRatio(actual: Double, target: Double): Double {
        if (target <= 0.0) return 0.0
        return round2(max(0.0, actual / target))
    }

    private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0
}
