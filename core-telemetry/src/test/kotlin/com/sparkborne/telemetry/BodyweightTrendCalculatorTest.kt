package com.sparkborne.telemetry

import com.sparkborne.telemetry.dto.CheckInMeasurementDto
import com.sparkborne.telemetry.model.WeightTrendDirection
import com.sparkborne.telemetry.normalizer.BodyweightTrendCalculator
import com.sparkborne.telemetry.normalizer.TelemetryNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BodyweightTrendCalculatorTest {

    private fun snapshotWithWeight(date: String, weightKg: Double?) = TelemetryNormalizer.normalize(
        date = date,
        dailySummary = null,
        checkIn = CheckInMeasurementDto(entryDate = date, weight = weightKg),
        sleepEntries = emptyList(),
        syncedAtEpochMs = 0L,
    )

    @Test
    fun `detects losing trend across 14-day window`() {
        val asOf = LocalDate.of(2026, 11, 22)
        val snapshots = (0L..13L).map { offset ->
            val date = asOf.minusDays(13L - offset)
            val weight = if (offset < 7) 85.0 else 84.0
            snapshotWithWeight(date.toString(), weight)
        }

        val trend = BodyweightTrendCalculator.calculate(snapshots, asOf)
        assertEquals(84.0, trend.latestWeightKg!!, 0.01)
        assertEquals(84.0, trend.rolling7DayAvgKg!!, 0.01)
        assertEquals(84.5, trend.rolling14DayAvgKg!!, 0.01)
        assertEquals(-1.0, trend.delta7DayKg!!, 0.01)
        assertEquals(WeightTrendDirection.LOSING, trend.direction)
        assertEquals(14, trend.sampleCount14d)
    }

    @Test
    fun `detects stable trend when delta is within threshold`() {
        val asOf = LocalDate.of(2026, 11, 22)
        val snapshots = listOf(
            snapshotWithWeight("2026-11-10", 80.0),
            snapshotWithWeight("2026-11-12", 80.1),
            snapshotWithWeight("2026-11-18", 80.1),
            snapshotWithWeight("2026-11-22", 80.2),
        )

        val trend = BodyweightTrendCalculator.calculate(snapshots, asOf)
        assertEquals(WeightTrendDirection.STABLE, trend.direction)
        assertEquals(4, trend.sampleCount14d)
    }

    @Test
    fun `returns insufficient data when prior 7-day window has no weights`() {
        val asOf = LocalDate.of(2026, 11, 22)
        val snapshots = listOf(
            snapshotWithWeight("2026-11-21", 81.0),
            snapshotWithWeight("2026-11-22", 81.2),
        )

        val trend = BodyweightTrendCalculator.calculate(snapshots, asOf)
        assertEquals(WeightTrendDirection.INSUFFICIENT_DATA, trend.direction)
        assertNull(trend.delta7DayKg)
        assertEquals(81.1, trend.rolling7DayAvgKg!!, 0.01)
    }
}
