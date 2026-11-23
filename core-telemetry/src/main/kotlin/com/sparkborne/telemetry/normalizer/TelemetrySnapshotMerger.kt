package com.sparkborne.telemetry.normalizer

import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import com.sparkborne.telemetry.model.GoalAdherenceSummary
import com.sparkborne.telemetry.model.HydrationSummary
import com.sparkborne.telemetry.model.LiftingSummary
import com.sparkborne.telemetry.model.LocalHealthReading
import com.sparkborne.telemetry.model.NutritionSummary
import com.sparkborne.telemetry.model.SleepSummary
import com.sparkborne.telemetry.model.TelemetryGoals
import kotlin.math.max
import kotlin.math.round

/**
 * Pure, deterministic high-water-mark merger combining instantaneous local Health Connect
 * readings with SparkyFitness snapshots.
 *
 * Guarantees:
 * - Local Health Connect immediately advances steps, sleep, hydration, nutrition, and weight.
 * - SparkyFitness remains authoritative for daily goals and Liftosaur per-muscle-group lifting.
 * - Neither source can ever regress already-recorded progress, and [SnapshotIdempotency.computeDelta]
 *   ensures strictly non-negative deltas with zero double-counting.
 */
object TelemetrySnapshotMerger {

    fun defaultGoals(): TelemetryGoals = TelemetryGoals(
        caloriesTarget = TelemetryNormalizer.DEFAULT_CALORIES_TARGET,
        proteinGramsTarget = TelemetryNormalizer.DEFAULT_PROTEIN_TARGET_G,
        carbsGramsTarget = TelemetryNormalizer.DEFAULT_CARBS_TARGET_G,
        fatGramsTarget = TelemetryNormalizer.DEFAULT_FAT_TARGET_G,
        waterMlTarget = TelemetryNormalizer.DEFAULT_WATER_TARGET_ML,
        stepsTarget = TelemetryNormalizer.DEFAULT_STEPS_TARGET,
        sleepMinutesTarget = TelemetryNormalizer.DEFAULT_SLEEP_TARGET_MIN,
    )

    private fun emptyLifting(): LiftingSummary = LiftingSummary(
        totalVolumeKg = 0.0,
        totalWorkingSets = 0,
        totalReps = 0,
        workoutCount = 0,
        byMuscleGroup = emptyMap(),
    )

    /**
     * Merges a local Health Connect [reading] into an [existing] Room snapshot (if any).
     * Preserves SparkyFitness [TelemetryGoals] and [LiftingSummary].
     */
    fun mergeWithLocalHealth(
        existing: DailyTelemetrySnapshot?,
        reading: LocalHealthReading,
        fallbackGoals: TelemetryGoals? = null,
    ): DailyTelemetrySnapshot {
        val goals = existing?.goals ?: fallbackGoals ?: defaultGoals()

        val prevNutrition = existing?.nutrition
        val nutrition = NutritionSummary(
            calories = round2(max(prevNutrition?.calories ?: 0.0, reading.calories)),
            proteinGrams = round2(max(prevNutrition?.proteinGrams ?: 0.0, reading.proteinGrams)),
            carbsGrams = round2(max(prevNutrition?.carbsGrams ?: 0.0, reading.carbsGrams)),
            fatGrams = round2(max(prevNutrition?.fatGrams ?: 0.0, reading.fatGrams)),
            foodEntryCount = max(prevNutrition?.foodEntryCount ?: 0, reading.nutritionEntryCount),
        )

        val prevHydration = existing?.hydration
        val mergedHydrationTotal = max(prevHydration?.totalMl ?: 0, reading.hydrationMl)
        val hydration = HydrationSummary(
            totalMl = mergedHydrationTotal,
            manualMl = max(prevHydration?.manualMl ?: 0, reading.hydrationMl),
            ledgerMl = prevHydration?.ledgerMl ?: 0,
            foodMl = prevHydration?.foodMl ?: 0,
        )

        val prevSleep = existing?.sleep
        val sleep = mergeSleep(
            a = prevSleep,
            b = SleepSummary(
                totalMinutes = reading.sleepTotalMinutes,
                deepMinutes = reading.sleepDeepMinutes,
                remMinutes = reading.sleepRemMinutes,
                lightMinutes = reading.sleepLightMinutes,
                awakeMinutes = reading.sleepAwakeMinutes,
                sessionCount = reading.sleepSessionCount,
            ),
        )

        val steps = max(existing?.steps ?: 0, reading.steps)
        val lifting = existing?.lifting ?: emptyLifting()
        val bodyweightKg = (reading.bodyweightKg ?: existing?.bodyweightKg)?.let(::round2)

        val sources = existing?.metricSources?.toMutableMap() ?: mutableMapOf()
        fun tagHc(key: String, hcVal: Double, existingVal: Double) {
            if (hcVal <= 0.0) return
            val prev = sources[key]
            sources[key] = when {
                existingVal <= 0.0 -> "Health Connect"
                hcVal > existingVal -> "Health Connect"
                prev == "SparkyFitness" || prev == "HC + Sparky" -> "HC + Sparky"
                else -> "Health Connect"
            }
        }
        tagHc("steps", reading.steps.toDouble(), (existing?.steps ?: 0).toDouble())
        tagHc("sleep", reading.sleepTotalMinutes.toDouble(), (existing?.sleep?.totalMinutes ?: 0).toDouble())
        tagHc("hydration", reading.hydrationMl.toDouble(), (existing?.hydration?.totalMl ?: 0).toDouble())
        tagHc("nutrition", reading.calories, existing?.nutrition?.calories ?: 0.0)
        if (reading.bodyweightKg != null) {
            sources["weight"] = if (existing?.bodyweightKg != null && sources["weight"] == "SparkyFitness") "HC + Sparky" else "Health Connect"
        }

        val activeBurn = round2(
            max(
                existing?.activeCaloriesBurned ?: 0.0,
                max(reading.activeCaloriesBurned, steps * 0.04 + lifting.totalVolumeKg * 0.025),
            ),
        )

        return buildSnapshot(
            date = reading.date,
            goals = goals,
            nutrition = nutrition,
            hydration = hydration,
            sleep = sleep,
            steps = steps,
            lifting = lifting,
            bodyweightKg = bodyweightKg,
            syncedAtEpochMs = max(existing?.syncedAtEpochMs ?: 0L, reading.syncedAtEpochMs),
            metricSources = sources,
            activeCaloriesBurned = activeBurn,
        )
    }

    /**
     * Merges a newly fetched SparkyFitness snapshot into an [existing] Room snapshot that may
     * already contain fresher local Health Connect data.
     *
     * SparkyFitness is authoritative for [TelemetryGoals] and [LiftingSummary], while steps,
     * hydration, sleep, and nutrition take the high-water mark so lagging cloud syncs never
     * regress local Health Connect progress.
     */
    fun mergeSparkyWithExisting(
        existing: DailyTelemetrySnapshot?,
        sparky: DailyTelemetrySnapshot,
    ): DailyTelemetrySnapshot {
        if (existing == null) return sparky

        val nutrition = NutritionSummary(
            calories = round2(max(existing.nutrition.calories, sparky.nutrition.calories)),
            proteinGrams = round2(max(existing.nutrition.proteinGrams, sparky.nutrition.proteinGrams)),
            carbsGrams = round2(max(existing.nutrition.carbsGrams, sparky.nutrition.carbsGrams)),
            fatGrams = round2(max(existing.nutrition.fatGrams, sparky.nutrition.fatGrams)),
            foodEntryCount = max(existing.nutrition.foodEntryCount, sparky.nutrition.foodEntryCount),
        )

        val mergedHydrationTotal = max(existing.hydration.totalMl, sparky.hydration.totalMl)
        val hydration = if (sparky.hydration.totalMl >= existing.hydration.totalMl) {
            sparky.hydration
        } else {
            sparky.hydration.copy(totalMl = mergedHydrationTotal)
        }

        val sleep = mergeSleep(existing.sleep, sparky.sleep)
        val steps = max(existing.steps, sparky.steps)

        // SparkyFitness is authoritative for Liftosaur lifting unless Sparky returned empty
        val lifting = if (sparky.lifting.totalVolumeKg >= existing.lifting.totalVolumeKg ||
            sparky.lifting.byMuscleGroup.isNotEmpty()
        ) {
            sparky.lifting
        } else {
            existing.lifting
        }

        val bodyweightKg = (sparky.bodyweightKg ?: existing.bodyweightKg)?.let(::round2)

        val sources = existing.metricSources.toMutableMap()
        fun tagSparky(key: String, sparkyVal: Double, existingVal: Double) {
            if (sparkyVal <= 0.0) return
            val prev = sources[key]
            sources[key] = when {
                existingVal <= 0.0 -> "SparkyFitness"
                prev == "Health Connect" || prev == "HC + Sparky" -> "HC + Sparky"
                else -> "SparkyFitness"
            }
        }
        tagSparky("steps", sparky.steps.toDouble(), existing.steps.toDouble())
        tagSparky("sleep", sparky.sleep.totalMinutes.toDouble(), existing.sleep.totalMinutes.toDouble())
        tagSparky("hydration", sparky.hydration.totalMl.toDouble(), existing.hydration.totalMl.toDouble())
        tagSparky("nutrition", sparky.nutrition.calories, existing.nutrition.calories)
        if (sparky.bodyweightKg != null) {
            sources["weight"] = if (existing.metricSources["weight"] == "Health Connect") "HC + Sparky" else "SparkyFitness"
        }
        if (lifting.totalVolumeKg > 0.0) {
            sources["lifting"] = "SparkyFitness (Liftosaur)"
        }

        val activeBurn = round2(
            max(
                max(existing.activeCaloriesBurned, sparky.activeCaloriesBurned),
                steps * 0.04 + lifting.totalVolumeKg * 0.025,
            ),
        )

        return buildSnapshot(
            date = sparky.date,
            goals = sparky.goals,
            nutrition = nutrition,
            hydration = hydration,
            sleep = sleep,
            steps = steps,
            lifting = lifting,
            bodyweightKg = bodyweightKg,
            syncedAtEpochMs = max(existing.syncedAtEpochMs, sparky.syncedAtEpochMs),
            metricSources = sources,
            activeCaloriesBurned = activeBurn,
        )
    }

    private fun mergeSleep(a: SleepSummary?, b: SleepSummary): SleepSummary {
        if (a == null) return b
        return when {
            b.totalMinutes > a.totalMinutes -> b
            a.totalMinutes > b.totalMinutes -> a
            else -> SleepSummary(
                totalMinutes = max(a.totalMinutes, b.totalMinutes),
                deepMinutes = max(a.deepMinutes, b.deepMinutes),
                remMinutes = max(a.remMinutes, b.remMinutes),
                lightMinutes = max(a.lightMinutes, b.lightMinutes),
                awakeMinutes = max(a.awakeMinutes, b.awakeMinutes),
                sessionCount = max(a.sessionCount, b.sessionCount),
            )
        }
    }

    private fun buildSnapshot(
        date: String,
        goals: TelemetryGoals,
        nutrition: NutritionSummary,
        hydration: HydrationSummary,
        sleep: SleepSummary,
        steps: Int,
        lifting: LiftingSummary,
        bodyweightKg: Double?,
        syncedAtEpochMs: Long,
        metricSources: Map<String, String> = emptyMap(),
        activeCaloriesBurned: Double = 0.0,
    ): DailyTelemetrySnapshot {
        val adherence = GoalAdherenceSummary(
            caloriesRatio = safeRatio(nutrition.calories, goals.caloriesTarget.toDouble()),
            proteinRatio = safeRatio(nutrition.proteinGrams, goals.proteinGramsTarget),
            carbsRatio = safeRatio(nutrition.carbsGrams, goals.carbsGramsTarget),
            fatRatio = safeRatio(nutrition.fatGrams, goals.fatGramsTarget),
            hydrationRatio = safeRatio(hydration.totalMl.toDouble(), goals.waterMlTarget.toDouble()),
            stepsRatio = safeRatio(steps.toDouble(), goals.stepsTarget.toDouble()),
            sleepRatio = safeRatio(sleep.totalMinutes.toDouble(), goals.sleepMinutesTarget.toDouble()),
        )

        val base = DailyTelemetrySnapshot(
            date = date,
            goals = goals,
            nutrition = nutrition,
            hydration = hydration,
            sleep = sleep,
            steps = steps,
            lifting = lifting,
            bodyweightKg = bodyweightKg,
            adherence = adherence,
            contentHash = "",
            syncedAtEpochMs = syncedAtEpochMs,
            metricSources = metricSources,
            activeCaloriesBurned = activeCaloriesBurned,
        )

        return base.copy(contentHash = SnapshotIdempotency.computeContentHash(base))
    }

    private fun safeRatio(actual: Double, target: Double): Double {
        if (target <= 0.0 || actual <= 0.0) return 0.0
        return round2(actual / target)
    }

    private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0
}
