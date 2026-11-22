package com.sparkborne.telemetry.normalizer

import com.sparkborne.telemetry.model.BodyweightTrend
import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import com.sparkborne.telemetry.model.WeightTrendDirection
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.round

/**
 * Computes deterministic 7-day and 14-day rolling bodyweight averages and trend direction
 * from synced [DailyTelemetrySnapshot] history.
 */
object BodyweightTrendCalculator {

    private const val STABLE_THRESHOLD_KG = 0.25

    fun calculate(
        snapshots: List<DailyTelemetrySnapshot>,
        asOfDate: LocalDate,
    ): BodyweightTrend {
        val windowStart14 = asOfDate.minusDays(13)
        val windowStart7 = asOfDate.minusDays(6)
        val prev7Start = asOfDate.minusDays(13)
        val prev7End = asOfDate.minusDays(7)

        val datedWeights = snapshots
            .mapNotNull { snap ->
                val weight = snap.bodyweightKg?.takeIf { it > 0.0 } ?: return@mapNotNull null
                val date = runCatching { LocalDate.parse(snap.date) }.getOrNull() ?: return@mapNotNull null
                if (date.isAfter(asOfDate)) return@mapNotNull null
                date to weight
            }
            .sortedBy { it.first }

        val latestWeight = datedWeights.lastOrNull()?.second
        val last14 = datedWeights.filter { !it.first.isBefore(windowStart14) }
        val last7 = datedWeights.filter { !it.first.isBefore(windowStart7) }
        val prior7 = datedWeights.filter { !it.first.isBefore(prev7Start) && !it.first.isAfter(prev7End) }

        val avg7 = last7.takeIf { it.isNotEmpty() }?.map { it.second }?.average()?.let(::round2)
        val avg14 = last14.takeIf { it.isNotEmpty() }?.map { it.second }?.average()?.let(::round2)
        val priorAvg7 = prior7.takeIf { it.isNotEmpty() }?.map { it.second }?.average()

        val delta7 = if (avg7 != null && priorAvg7 != null) {
            round2(avg7 - priorAvg7)
        } else {
            null
        }

        val direction = when {
            delta7 == null -> WeightTrendDirection.INSUFFICIENT_DATA
            abs(delta7) < STABLE_THRESHOLD_KG -> WeightTrendDirection.STABLE
            delta7 > 0.0 -> WeightTrendDirection.GAINING
            else -> WeightTrendDirection.LOSING
        }

        return BodyweightTrend(
            asOfDate = asOfDate.toString(),
            latestWeightKg = latestWeight?.let(::round2),
            rolling7DayAvgKg = avg7,
            rolling14DayAvgKg = avg14,
            delta7DayKg = delta7,
            direction = direction,
            sampleCount14d = last14.size,
        )
    }

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0
}
