package com.sparkborne.telemetry.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for `GET /api/sleep?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`.
 */
@Serializable
data class SleepEntryDto(
    val id: String? = null,
    @SerialName("entry_date")
    val entryDate: String? = null,
    val bedtime: String? = null,
    @SerialName("wake_time")
    val wakeTime: String? = null,
    @SerialName("duration_in_seconds")
    val durationInSeconds: Long? = null,
    @SerialName("stage_events")
    val stageEvents: List<SleepStageEventDto>? = null,
    val source: String? = null,
)

@Serializable
data class SleepStageEventDto(
    val stage: String? = null,
    @SerialName("stage_type")
    val stageType: String? = null,
    @SerialName("duration_in_seconds")
    val durationInSeconds: Long? = null,
    @SerialName("duration_seconds")
    val durationSeconds: Long? = null,
    @SerialName("start_time")
    val startTime: String? = null,
    @SerialName("end_time")
    val endTime: String? = null,
)
