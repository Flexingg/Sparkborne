package com.sparkborne.telemetry

import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import com.sparkborne.telemetry.model.GoalAdherenceSummary
import com.sparkborne.telemetry.model.HydrationSummary
import com.sparkborne.telemetry.model.LiftingSummary
import com.sparkborne.telemetry.model.LocalHealthReading
import com.sparkborne.telemetry.model.MuscleGroup
import com.sparkborne.telemetry.model.MuscleGroupVolume
import com.sparkborne.telemetry.model.NutritionSummary
import com.sparkborne.telemetry.model.SleepSummary
import com.sparkborne.telemetry.model.TelemetryGoals
import com.sparkborne.telemetry.normalizer.SnapshotIdempotency
import com.sparkborne.telemetry.normalizer.TelemetrySnapshotMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetrySnapshotMergerTest {

    @Test
    fun `health connect leads and later sparky sync adds lifting without double counting`() {
        val customGoals = TelemetryGoals(
            caloriesTarget = 2400,
            proteinGramsTarget = 180.0,
            carbsGramsTarget = 250.0,
            fatGramsTarget = 70.0,
            waterMlTarget = 3000,
            stepsTarget = 10000,
            sleepMinutesTarget = 480,
        )

        // 1. Morning Health Connect reading (instant local read)
        val hcReading = LocalHealthReading(
            date = "2026-11-22",
            steps = 6500,
            hydrationMl = 1500,
            sleepTotalMinutes = 450,
            sleepDeepMinutes = 95,
            sleepRemMinutes = 110,
            sleepLightMinutes = 245,
            sleepSessionCount = 1,
            calories = 900.0,
            proteinGrams = 75.0,
            carbsGrams = 80.0,
            fatGrams = 30.0,
            nutritionEntryCount = 2,
            bodyweightKg = 82.4,
            syncedAtEpochMs = 1000L,
        )

        val afterHc = TelemetrySnapshotMerger.mergeWithLocalHealth(
            existing = null,
            reading = hcReading,
            fallbackGoals = customGoals,
        )
        val delta1 = SnapshotIdempotency.computeDelta(null, afterHc)
        assertTrue(delta1.hasChanged)
        assertEquals(6500, delta1.stepsDelta)
        assertEquals(1500, delta1.hydrationMlDelta)
        assertEquals(450, delta1.sleepMinutesDelta)
        assertEquals(82.4, afterHc.bodyweightKg!!, 0.01)

        // 2. Later SparkyFitness cloud sync arrives: lagging steps (4000), same sleep (450),
        //    extra Sparky-logged water (2000 ml), and Liftosaur Chest workout (4500 kg)
        val sparkyBase = DailyTelemetrySnapshot(
            date = "2026-11-22",
            goals = customGoals,
            nutrition = NutritionSummary(900.0, 75.0, 80.0, 30.0, 2),
            hydration = HydrationSummary(2000, 2000, 0, 0),
            sleep = SleepSummary(450, 95, 110, 245, 0, 1),
            steps = 4000, // lagging behind HC
            lifting = LiftingSummary(
                totalVolumeKg = 4500.0,
                totalWorkingSets = 12,
                totalReps = 96,
                workoutCount = 1,
                byMuscleGroup = mapOf(
                    MuscleGroup.CHEST to MuscleGroupVolume(
                        muscleGroup = MuscleGroup.CHEST,
                        primaryVolumeKg = 4500.0,
                        secondaryVolumeKg = 0.0,
                        effectiveVolumeKg = 4500.0,
                        workingSets = 12,
                        totalReps = 96,
                        topSetWeightKg = 100.0,
                        exerciseNames = listOf("Bench Press"),
                    ),
                ),
            ),
            bodyweightKg = null,
            adherence = GoalAdherenceSummary(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            contentHash = "",
            syncedAtEpochMs = 2000L,
        )
        val sparkySnapshot = sparkyBase.copy(contentHash = SnapshotIdempotency.computeContentHash(sparkyBase))

        val mergedAfterSparky = TelemetrySnapshotMerger.mergeSparkyWithExisting(afterHc, sparkySnapshot)
        val delta2 = SnapshotIdempotency.computeDelta(afterHc, mergedAfterSparky)

        assertTrue(delta2.hasChanged)
        // Steps did NOT regress and were NOT double-counted
        assertEquals(6500, mergedAfterSparky.steps)
        assertEquals(0, delta2.stepsDelta)
        // Sleep NOT double-counted
        assertEquals(0, delta2.sleepMinutesDelta)
        // Hydration only credited net +500ml
        assertEquals(2000, mergedAfterSparky.hydration.totalMl)
        assertEquals(500, delta2.hydrationMlDelta)
        // Liftosaur lifting applied cleanly
        assertEquals(4500.0, delta2.totalLiftingVolumeKgDelta, 0.01)
        assertEquals(4500.0, delta2.effectiveMuscleVolumeKgDeltas[MuscleGroup.CHEST]!!, 0.01)
        // Bodyweight preserved from HC since Sparky didn't have it yet
        assertEquals(82.4, mergedAfterSparky.bodyweightKg!!, 0.01)

        // 3. Re-reading identical Health Connect data is a complete no-op
        val afterSecondHc = TelemetrySnapshotMerger.mergeWithLocalHealth(
            existing = mergedAfterSparky,
            reading = hcReading.copy(syncedAtEpochMs = 3000L),
            fallbackGoals = customGoals,
        )
        val delta3 = SnapshotIdempotency.computeDelta(mergedAfterSparky, afterSecondHc)
        assertFalse(delta3.hasChanged)
        assertEquals(0, delta3.stepsDelta)
        assertEquals(0, delta3.hydrationMlDelta)
        assertEquals(0, delta3.sleepMinutesDelta)
        assertEquals(0.0, delta3.totalLiftingVolumeKgDelta, 0.01)
    }
}
