package com.sparkborne.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.sparkborne.telemetry.model.LocalHealthReading
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class HealthConnectStatus(
    val isAvailable: Boolean = false,
    val grantedPermissions: Set<String> = emptySet(),
    val totalPermissions: Int = HealthConnectReader.ALL_PERMISSIONS.size,
) {
    val hasAnyReadPermission: Boolean
        get() = grantedPermissions.any { it != HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND }

    val hasAllPermissions: Boolean
        get() = grantedPermissions.containsAll(HealthConnectReader.ALL_PERMISSIONS)

    val hasBackgroundPermission: Boolean
        get() = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in grantedPermissions
}

/**
 * Reads local health metrics directly from Android Health Connect on-device for instant
 * updates (zero server round-trip) across steps, sleep, hydration, nutrition, and weight.
 */
class HealthConnectReader(private val context: Context) {

    fun isAvailable(): Boolean {
        return runCatching {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        }.getOrDefault(false)
    }

    private fun clientOrNull(): HealthConnectClient? {
        if (!isAvailable()) return null
        return runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    suspend fun getStatus(): HealthConnectStatus {
        val client = clientOrNull() ?: return HealthConnectStatus(isAvailable = false)
        val granted = runCatching {
            client.permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())
        return HealthConnectStatus(
            isAvailable = true,
            grantedPermissions = granted.intersect(ALL_PERMISSIONS),
            totalPermissions = ALL_PERMISSIONS.size,
        )
    }

    /**
     * Reads daily Health Connect aggregates for [startDate]..[endDate] inclusive.
     * Only queries metrics whose permissions are currently granted.
     */
    suspend fun readRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<LocalHealthReading> {
        val client = clientOrNull() ?: return emptyList()
        val granted = runCatching {
            client.permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())

        val canSteps = PERM_STEPS in granted
        val canHydration = PERM_HYDRATION in granted
        val canSleep = PERM_SLEEP in granted
        val canNutrition = PERM_NUTRITION in granted
        val canWeight = PERM_WEIGHT in granted

        if (!canSteps && !canHydration && !canSleep && !canNutrition && !canWeight) {
            return emptyList()
        }

        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        val readings = mutableListOf<LocalHealthReading>()

        var day = startDate
        while (!day.isAfter(endDate)) {
            val dayStart = day.atStartOfDay(zone).toInstant()
            val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
            val dayRange = TimeRangeFilter.between(dayStart, dayEnd)

            // 1. Steps (deduplicated aggregate across Garmin/phone/etc.)
            val steps = if (canSteps) {
                runCatching {
                    client.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = dayRange,
                        ),
                    )[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
                }.getOrDefault(0)
            } else {
                0
            }

            // 2. Hydration (mL)
            val hydrationMl = if (canHydration) {
                runCatching {
                    client.aggregate(
                        AggregateRequest(
                            metrics = setOf(HydrationRecord.VOLUME_TOTAL),
                            timeRangeFilter = dayRange,
                        ),
                    )[HydrationRecord.VOLUME_TOTAL]?.inMilliliters?.roundToInt() ?: 0
                }.getOrDefault(0)
            } else {
                0
            }

            // 3. Sleep (attribute overnight sessions ending on `day`)
            var sleepTotalMin = 0
            var sleepDeepMin = 0
            var sleepRemMin = 0
            var sleepLightMin = 0
            var sleepAwakeMin = 0
            var sleepSessions = 0
            if (canSleep) {
                runCatching {
                    val sleepWindowStart = day.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
                    val response = client.readRecords(
                        ReadRecordsRequest(
                            recordType = SleepSessionRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(sleepWindowStart, dayEnd),
                        ),
                    )
                    val matching = response.records.filter { rec ->
                        !rec.endTime.isBefore(dayStart) && rec.endTime.isBefore(dayEnd)
                    }
                    sleepSessions = matching.size
                    for (session in matching) {
                        if (session.stages.isNotEmpty()) {
                            var sessionAsleepMin = 0
                            for (stage in session.stages) {
                                val mins = Duration.between(stage.startTime, stage.endTime)
                                    .toMinutes()
                                    .toInt()
                                    .coerceAtLeast(0)
                                when (stage.stage) {
                                    SleepSessionRecord.STAGE_TYPE_DEEP -> {
                                        sleepDeepMin += mins
                                        sessionAsleepMin += mins
                                    }
                                    SleepSessionRecord.STAGE_TYPE_REM -> {
                                        sleepRemMin += mins
                                        sessionAsleepMin += mins
                                    }
                                    SleepSessionRecord.STAGE_TYPE_LIGHT,
                                    SleepSessionRecord.STAGE_TYPE_SLEEPING -> {
                                        sleepLightMin += mins
                                        sessionAsleepMin += mins
                                    }
                                    SleepSessionRecord.STAGE_TYPE_AWAKE,
                                    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                                    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> {
                                        sleepAwakeMin += mins
                                    }
                                    else -> {
                                        sessionAsleepMin += mins
                                    }
                                }
                            }
                            sleepTotalMin += if (sessionAsleepMin > 0) {
                                sessionAsleepMin
                            } else {
                                Duration.between(session.startTime, session.endTime)
                                    .toMinutes()
                                    .toInt()
                                    .coerceAtLeast(0)
                            }
                        } else {
                            val totalMin = Duration.between(session.startTime, session.endTime)
                                .toMinutes()
                                .toInt()
                                .coerceAtLeast(0)
                            sleepTotalMin += totalMin
                        }
                    }
                }
            }

            // 4. Nutrition / Macros
            var calories = 0.0
            var protein = 0.0
            var carbs = 0.0
            var fat = 0.0
            var nutritionCount = 0
            if (canNutrition) {
                runCatching {
                    val response = client.readRecords(
                        ReadRecordsRequest(
                            recordType = NutritionRecord::class,
                            timeRangeFilter = dayRange,
                        ),
                    )
                    nutritionCount = response.records.size
                    for (rec in response.records) {
                        calories += rec.energy?.inKilocalories ?: 0.0
                        protein += rec.protein?.inGrams ?: 0.0
                        carbs += rec.totalCarbohydrate?.inGrams ?: 0.0
                        fat += rec.totalFat?.inGrams ?: 0.0
                    }
                }
            }

            // 5. Bodyweight (latest reading of the day)
            val weightKg = if (canWeight) {
                runCatching {
                    val response = client.readRecords(
                        ReadRecordsRequest(
                            recordType = WeightRecord::class,
                            timeRangeFilter = dayRange,
                        ),
                    )
                    response.records.maxByOrNull { it.time }?.weight?.inKilograms
                }.getOrNull()
            } else {
                null
            }

            val reading = LocalHealthReading(
                date = day.toString(),
                steps = steps,
                hydrationMl = hydrationMl,
                sleepTotalMinutes = sleepTotalMin,
                sleepDeepMinutes = sleepDeepMin,
                sleepRemMinutes = sleepRemMin,
                sleepLightMinutes = sleepLightMin,
                sleepAwakeMinutes = sleepAwakeMin,
                sleepSessionCount = sleepSessions,
                calories = calories,
                proteinGrams = protein,
                carbsGrams = carbs,
                fatGrams = fat,
                nutritionEntryCount = nutritionCount,
                bodyweightKg = weightKg,
                syncedAtEpochMs = nowMs,
            )
            if (reading.hasAnyData) {
                readings.add(reading)
            }
            day = day.plusDays(1)
        }

        return readings
    }

    companion object {
        val PERM_STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)
        val PERM_HYDRATION: String = HealthPermission.getReadPermission(HydrationRecord::class)
        val PERM_SLEEP: String = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val PERM_NUTRITION: String = HealthPermission.getReadPermission(NutritionRecord::class)
        val PERM_WEIGHT: String = HealthPermission.getReadPermission(WeightRecord::class)
        val PERM_BACKGROUND: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

        val ALL_PERMISSIONS: Set<String> = setOf(
            PERM_STEPS,
            PERM_HYDRATION,
            PERM_SLEEP,
            PERM_NUTRITION,
            PERM_WEIGHT,
            PERM_BACKGROUND,
        )
    }
}
