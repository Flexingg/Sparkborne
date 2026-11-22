package com.sparkborne.telemetry.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * SparkyFitness PostgreSQL numeric columns can be serialized as either JSON numbers
 * or numeric strings depending on the endpoint. This serializer safely parses both.
 */
object FlexibleDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Double? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return decoder.decodeString().toDoubleOrNull()
        val element = jsonDecoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.doubleOrNull ?: primitive.contentOrNull?.trim()?.toDoubleOrNull()
    }

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) {
            encoder.encodeString("")
        } else {
            encoder.encodeDouble(value)
        }
    }
}

@Serializable
data class DailySummaryResponseDto(
    val goals: UserGoalDto? = null,
    val adjustedGoals: AdjustedGoalDto? = null,
    val foodEntries: List<FoodEntryDto> = emptyList(),
    val exerciseSessions: List<ExerciseSessionDto> = emptyList(),
    @Serializable(with = FlexibleDoubleSerializer::class)
    val waterIntake: Double? = null,
    val waterIntakeBreakdown: WaterIntakeBreakdownDto? = null,
    val calorieBalance: CalorieBalanceDto? = null,
    val calorieInputs: CalorieInputsDto? = null,
    val supplementTotals: SupplementTotalsDto? = null,
)

@Serializable
data class UserGoalDto(
    @Serializable(with = FlexibleDoubleSerializer::class)
    val calories: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val protein: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val carbs: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val fat: Double? = null,
    @SerialName("water_goal_ml")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val waterGoalMl: Double? = null,
    @SerialName("water_goal")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val waterGoalLegacy: Double? = null,
    @SerialName("steps_goal")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val stepsGoal: Double? = null,
    @SerialName("step_goal")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val stepGoalAlt: Double? = null,
    @SerialName("sleep_goal_minutes")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val sleepGoalMinutes: Double? = null,
    @SerialName("sleep_goal_hours")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val sleepGoalHours: Double? = null,
)

@Serializable
data class AdjustedGoalDto(
    @Serializable(with = FlexibleDoubleSerializer::class)
    val calories: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val protein: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val carbs: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val fat: Double? = null,
    @SerialName("water_goal_ml")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val waterGoalMl: Double? = null,
)

@Serializable
data class FoodEntryDto(
    val id: String? = null,
    @SerialName("food_name")
    val foodName: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val calories: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val protein: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val carbs: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val fat: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val quantity: Double? = null,
)

@Serializable
data class SupplementTotalsDto(
    @Serializable(with = FlexibleDoubleSerializer::class)
    val calories: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val protein: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val carbs: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val fat: Double? = null,
)

@Serializable
data class WaterIntakeBreakdownDto(
    @SerialName("water_ml")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val waterMl: Double? = null,
    @SerialName("manual_ml")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val manualMl: Double? = null,
    @SerialName("ledger_ml")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val ledgerMl: Double? = null,
    @SerialName("food_ml")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val foodMl: Double? = null,
)

@Serializable
data class CalorieBalanceDto(
    @Serializable(with = FlexibleDoubleSerializer::class)
    val eaten: Double? = null,
)

@Serializable
data class CalorieInputsDto(
    @Serializable(with = FlexibleDoubleSerializer::class)
    val weightKg: Double? = null,
)

/**
 * SparkyFitness returns exerciseSessions as a union of preset workout sessions
 * (`type == "preset"` with nested `exercises`) and standalone exercise entries
 * (with `sets` and `exercise_snapshot` directly on the session object).
 */
@Serializable
data class ExerciseSessionDto(
    val id: String? = null,
    val type: String? = null,
    val name: String? = null,
    val status: String? = null,
    @SerialName("entry_date")
    val entryDate: String? = null,
    @SerialName("total_duration_minutes")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val totalDurationMinutes: Double? = null,
    @SerialName("duration_minutes")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val durationMinutes: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val steps: Double? = null,
    val exercises: List<ExerciseEntryDto> = emptyList(),
    // Standalone entry fields
    val sets: List<ExerciseSetDto> = emptyList(),
    @Serializable(with = FlexibleDoubleSerializer::class)
    val reps: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val weight: Double? = null,
    @SerialName("exercise_snapshot")
    val exerciseSnapshot: ExerciseSnapshotDto? = null,
)

@Serializable
data class ExerciseEntryDto(
    val id: String? = null,
    val name: String? = null,
    @SerialName("duration_minutes")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val durationMinutes: Double? = null,
    @SerialName("calories_burned")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val caloriesBurned: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val steps: Double? = null,
    val sets: List<ExerciseSetDto> = emptyList(),
    @Serializable(with = FlexibleDoubleSerializer::class)
    val reps: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val weight: Double? = null,
    @SerialName("exercise_snapshot")
    val exerciseSnapshot: ExerciseSnapshotDto? = null,
)

@Serializable
data class ExerciseSetDto(
    val id: String? = null,
    @SerialName("set_number")
    val setNumber: Int? = null,
    @SerialName("set_type")
    val setType: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val reps: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val weight: Double? = null,
    val duration: Int? = null,
)

@Serializable
data class ExerciseSnapshotDto(
    val id: String? = null,
    val name: String? = null,
    val category: String? = null,
    val modality: String? = null,
    @SerialName("primary_muscles")
    val primaryMuscles: List<String>? = null,
    @SerialName("secondary_muscles")
    val secondaryMuscles: List<String>? = null,
    val equipment: List<String>? = null,
)
