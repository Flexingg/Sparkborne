package com.sparkborne.telemetry.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for `GET /api/measurements/check-in-measurements-range/{startDate}/{endDate}`.
 */
@Serializable
data class CheckInMeasurementDto(
    val id: String? = null,
    @SerialName("entry_date")
    val entryDate: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val weight: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val steps: Double? = null,
    @SerialName("body_fat_percentage")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val bodyFatPercentage: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val waist: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val neck: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val hips: Double? = null,
)
