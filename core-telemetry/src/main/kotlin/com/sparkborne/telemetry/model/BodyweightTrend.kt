package com.sparkborne.telemetry.model

import kotlinx.serialization.Serializable

@Serializable
enum class WeightTrendDirection {
    GAINING,
    LOSING,
    STABLE,
    INSUFFICIENT_DATA,
}

/**
 * Rolling bodyweight trend derived deterministically from synced daily snapshots.
 */
@Serializable
data class BodyweightTrend(
    val asOfDate: String,
    val latestWeightKg: Double?,
    val rolling7DayAvgKg: Double?,
    val rolling14DayAvgKg: Double?,
    val delta7DayKg: Double?,
    val direction: WeightTrendDirection,
    val sampleCount14d: Int,
)
