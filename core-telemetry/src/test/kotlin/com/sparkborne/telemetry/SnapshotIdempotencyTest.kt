package com.sparkborne.telemetry

import com.sparkborne.telemetry.dto.CheckInMeasurementDto
import com.sparkborne.telemetry.dto.DailySummaryResponseDto
import com.sparkborne.telemetry.dto.ExerciseEntryDto
import com.sparkborne.telemetry.dto.ExerciseSessionDto
import com.sparkborne.telemetry.dto.ExerciseSetDto
import com.sparkborne.telemetry.dto.ExerciseSnapshotDto
import com.sparkborne.telemetry.model.MuscleGroup
import com.sparkborne.telemetry.normalizer.SnapshotIdempotency
import com.sparkborne.telemetry.normalizer.TelemetryNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotIdempotencyTest {

    @Test
    fun `identical telemetry synced at different times yields identical contentHash and zero delta`() {
        val checkIn = CheckInMeasurementDto(entryDate = "2026-11-22", steps = 6500.0, weight = 80.5)
        val summary = DailySummaryResponseDto(waterIntake = 1500.0)

        val first = TelemetryNormalizer.normalize("2026-11-22", summary, checkIn, emptyList(), 1_000L)
        val second = TelemetryNormalizer.normalize("2026-11-22", summary, checkIn, emptyList(), 9_999L)

        assertEquals(first.contentHash, second.contentHash)

        val delta = SnapshotIdempotency.computeDelta(first, second)
        assertFalse(delta.hasChanged)
        assertEquals(0, delta.stepsDelta)
        assertEquals(0, delta.hydrationMlDelta)
        assertEquals(0.0, delta.totalLiftingVolumeKgDelta, 0.001)
        assertTrue(delta.effectiveMuscleVolumeKgDeltas.isEmpty())
    }

    @Test
    fun `incremental telemetry updates yield exact non-negative deltas`() {
        val morning = TelemetryNormalizer.normalize(
            date = "2026-11-22",
            dailySummary = DailySummaryResponseDto(waterIntake = 1000.0),
            checkIn = CheckInMeasurementDto(entryDate = "2026-11-22", steps = 4000.0),
            sleepEntries = emptyList(),
            syncedAtEpochMs = 1_000L,
        )

        val evening = TelemetryNormalizer.normalize(
            date = "2026-11-22",
            dailySummary = DailySummaryResponseDto(
                waterIntake = 2750.0,
                exerciseSessions = listOf(
                    ExerciseSessionDto(
                        id = "s1",
                        type = "preset",
                        exercises = listOf(
                            ExerciseEntryDto(
                                name = "Squat",
                                sets = listOf(ExerciseSetDto(reps = 5.0, weight = 120.0)),
                                exerciseSnapshot = ExerciseSnapshotDto(
                                    name = "Back Squat",
                                    primaryMuscles = listOf("quadriceps"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            checkIn = CheckInMeasurementDto(entryDate = "2026-11-22", steps = 9200.0),
            sleepEntries = emptyList(),
            syncedAtEpochMs = 2_000L,
        )

        val delta = SnapshotIdempotency.computeDelta(morning, evening)
        assertTrue(delta.hasChanged)
        assertEquals(5200, delta.stepsDelta)
        assertEquals(1750, delta.hydrationMlDelta)
        assertEquals(600.0, delta.totalLiftingVolumeKgDelta, 0.01)
        assertEquals(600.0, delta.effectiveMuscleVolumeKgDeltas[MuscleGroup.QUADS]!!, 0.01)
    }
}
