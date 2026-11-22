package com.sparkborne.telemetry

import com.sparkborne.telemetry.client.SparkyFitnessApiClient
import com.sparkborne.telemetry.dto.CheckInMeasurementDto
import com.sparkborne.telemetry.dto.DailySummaryResponseDto
import com.sparkborne.telemetry.dto.SleepEntryDto
import com.sparkborne.telemetry.model.MuscleGroup
import com.sparkborne.telemetry.normalizer.TelemetryNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SparkyFitnessNormalizerTest {

    private val json = SparkyFitnessApiClient.defaultJson

    @Test
    fun `normalizes full SparkyFitness daily summary, check-in, and sleep payloads`() {
        val dailySummaryJson = """
            {
              "goals": {
                "calories": "2400",
                "protein": 180,
                "carbs": 250,
                "fat": 70,
                "water_goal_ml": 3000,
                "steps_goal": 10000,
                "sleep_goal_minutes": 480
              },
              "adjustedGoals": {
                "calories": 2600,
                "protein": 185,
                "carbs": 290,
                "fat": 70,
                "water_goal_ml": 3200
              },
              "foodEntries": [
                { "id": "f1", "food_name": "Steak & Rice", "calories": "950.5", "protein": "75", "carbs": "95", "fat": "28" },
                { "id": "f2", "food_name": "Whey Shake", "calories": 240, "protein": 48, "carbs": 6, "fat": 2 }
              ],
              "supplementTotals": {
                "calories": 20,
                "protein": 5,
                "carbs": 0,
                "fat": 0
              },
              "waterIntake": 2800,
              "waterIntakeBreakdown": {
                "water_ml": 2800,
                "manual_ml": 2000,
                "ledger_ml": 500,
                "food_ml": 300
              },
              "exerciseSessions": [
                {
                  "id": "sess-1",
                  "type": "preset",
                  "name": "Upper A",
                  "entry_date": "2026-11-22",
                  "exercises": [
                    {
                      "id": "ex-1",
                      "name": "Bench Press",
                      "sets": [
                        { "set_number": 1, "set_type": "warmup", "reps": 10, "weight": 40 },
                        { "set_number": 2, "set_type": "normal", "reps": 5, "weight": 100 },
                        { "set_number": 3, "set_type": "normal", "reps": 5, "weight": 100 }
                      ],
                      "exercise_snapshot": {
                        "id": "snap-1",
                        "name": "Barbell Bench Press",
                        "primary_muscles": ["pectorals"],
                        "secondary_muscles": ["triceps", "shoulders"]
                      }
                    }
                  ]
                }
              ],
              "calorieInputs": {
                "weightKg": 82.4
              }
            }
        """.trimIndent()

        val checkInJson = """
            {
              "id": "ci-1",
              "entry_date": "2026-11-22",
              "weight": "82.1",
              "steps": "11450"
            }
        """.trimIndent()

        val sleepJson = """
            [
              {
                "id": "sl-1",
                "entry_date": "2026-11-22",
                "duration_in_seconds": 27000,
                "stage_events": [
                  { "stage": "deep", "duration_in_seconds": 5400 },
                  { "stage": "rem", "duration_in_seconds": 6000 },
                  { "stage": "light", "duration_in_seconds": 14400 },
                  { "stage": "awake", "duration_in_seconds": 1200 }
                ]
              }
            ]
        """.trimIndent()

        val summaryDto = json.decodeFromString<DailySummaryResponseDto>(dailySummaryJson)
        val checkInDto = json.decodeFromString<CheckInMeasurementDto>(checkInJson)
        val sleepDtos = json.decodeFromString<List<SleepEntryDto>>(sleepJson)

        val snapshot = TelemetryNormalizer.normalize(
            date = "2026-11-22",
            dailySummary = summaryDto,
            checkIn = checkInDto,
            sleepEntries = sleepDtos,
            syncedAtEpochMs = 1_700_000_000_000L,
        )

        // Adjusted goals take precedence
        assertEquals(2600, snapshot.goals.caloriesTarget)
        assertEquals(185.0, snapshot.goals.proteinGramsTarget, 0.01)
        assertEquals(3200, snapshot.goals.waterMlTarget)
        assertEquals(10000, snapshot.goals.stepsTarget)
        assertEquals(480, snapshot.goals.sleepMinutesTarget)

        // Nutrition includes food + supplementTotals
        assertEquals(1210.5, snapshot.nutrition.calories, 0.01)
        assertEquals(128.0, snapshot.nutrition.proteinGrams, 0.01)
        assertEquals(101.0, snapshot.nutrition.carbsGrams, 0.01)
        assertEquals(30.0, snapshot.nutrition.fatGrams, 0.01)
        assertEquals(2, snapshot.nutrition.foodEntryCount)

        // Hydration
        assertEquals(2800, snapshot.hydration.totalMl)
        assertEquals(2000, snapshot.hydration.manualMl)

        // Sleep
        assertEquals(450, snapshot.sleep.totalMinutes)
        assertEquals(90, snapshot.sleep.deepMinutes)
        assertEquals(100, snapshot.sleep.remMinutes)
        assertEquals(240, snapshot.sleep.lightMinutes)
        assertEquals(20, snapshot.sleep.awakeMinutes)

        // Steps & Weight (check-in preferred over calorieInputs)
        assertEquals(11450, snapshot.steps)
        assertEquals(82.1, snapshot.bodyweightKg!!, 0.01)

        // Lifting: warmup excluded -> 2 working sets of 5x100kg = 1000kg
        assertEquals(1000.0, snapshot.lifting.totalVolumeKg, 0.01)
        assertEquals(2, snapshot.lifting.totalWorkingSets)
        assertEquals(10, snapshot.lifting.totalReps)
        assertEquals(1, snapshot.lifting.workoutCount)

        val chest = snapshot.lifting.byMuscleGroup[MuscleGroup.CHEST]!!
        assertEquals(1000.0, chest.primaryVolumeKg, 0.01)
        assertEquals(1000.0, chest.effectiveVolumeKg, 0.01)
        assertEquals(100.0, chest.topSetWeightKg, 0.01)

        // Secondary muscles split 1000kg across 2 muscles (500kg raw each -> 250kg effective at 0.5x)
        val triceps = snapshot.lifting.byMuscleGroup[MuscleGroup.TRICEPS]!!
        assertEquals(500.0, triceps.secondaryVolumeKg, 0.01)
        assertEquals(250.0, triceps.effectiveVolumeKg, 0.01)

        val shoulders = snapshot.lifting.byMuscleGroup[MuscleGroup.SHOULDERS]!!
        assertEquals(500.0, shoulders.secondaryVolumeKg, 0.01)
        assertEquals(250.0, shoulders.effectiveVolumeKg, 0.01)

        // Adherence
        assertEquals(1.15, snapshot.adherence.stepsRatio, 0.01)
        assertEquals(0.88, snapshot.adherence.hydrationRatio, 0.01)
    }

    @Test
    fun `handles empty and partial day safely with default goals`() {
        val snapshot = TelemetryNormalizer.normalize(
            date = "2026-11-21",
            dailySummary = null,
            checkIn = null,
            sleepEntries = emptyList(),
            syncedAtEpochMs = 1_700_000_000_000L,
        )

        assertEquals(TelemetryNormalizer.DEFAULT_CALORIES_TARGET, snapshot.goals.caloriesTarget)
        assertEquals(0, snapshot.steps)
        assertEquals(0, snapshot.hydration.totalMl)
        assertEquals(0, snapshot.sleep.totalMinutes)
        assertEquals(0.0, snapshot.lifting.totalVolumeKg, 0.01)
        assertNull(snapshot.bodyweightKg)
    }
}
