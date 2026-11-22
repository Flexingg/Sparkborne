package com.sparkborne.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.sparkborne.telemetry.model.LocalHealthReading
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.math.roundToInt

data class HealthConnectStatus(
    val isAvailable: Boolean = false,
    val grantedPermissions: Set<String> = emptySet(),
    val totalPermissions: Int = HealthConnectReader.ALL_PERMISSIONS.size,
) {
    val hasAnyReadPermission: Boolean
        get() = grantedPermissions.any {
            it != HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND &&
                it != HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        }

    val hasAllPermissions: Boolean
        get() = grantedPermissions.containsAll(HealthConnectReader.ALL_PERMISSIONS)

    val hasBackgroundPermission: Boolean
        get() = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in grantedPermissions

    val hasHistoryPermission: Boolean
        get() = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in grantedPermissions
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
     * For short ranges (<= 14d), uses per-day reads; for larger ranges, delegates to [readRangeChunked].
     */
    suspend fun readRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<LocalHealthReading> {
        return readRangeChunked(startDate, endDate)
    }

    /**
     * Fast chunked Health Connect reader capable of multi-year / all-time backfills in seconds.
     * Processes 90-day chunks using `aggregateGroupByPeriod(Period.ofDays(1))` for Steps &
     * Hydration and batched `readRecords` for Sleep, Nutrition, and Weight.
     */
    suspend fun readRangeChunked(
        startDate: LocalDate,
        endDate: LocalDate,
        onChunkProgress: (suspend (chunkStart: LocalDate, chunkEnd: LocalDate, chunkReadings: List<LocalHealthReading>) -> Unit)? = null,
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

        // Respect Android's 30-day window if READ_HEALTH_DATA_HISTORY is not granted yet
        val effectiveStart = if (PERM_HISTORY in granted) {
            startDate
        } else {
            val thirtyDaysAgo = LocalDate.now().minusDays(29)
            if (startDate.isBefore(thirtyDaysAgo)) thirtyDaysAgo else startDate
        }
        if (effectiveStart.isAfter(endDate)) return emptyList()

        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        val allReadings = mutableListOf<LocalHealthReading>()

        // Walk newest -> oldest in 90-day chunks
        var chunkEnd = endDate
        while (!chunkEnd.isBefore(effectiveStart)) {
            val candidateStart = chunkEnd.minusDays(89)
            val chunkStart = if (candidateStart.isBefore(effectiveStart)) effectiveStart else candidateStart

            val localStart = chunkStart.atStartOfDay()
            val localEndExclusive = chunkEnd.plusDays(1).atStartOfDay()
            val instantStart = localStart.atZone(zone).toInstant()
            val instantEnd = localEndExclusive.atZone(zone).toInstant()

            val stepsByDate = mutableMapOf<String, Int>()
            val hydrationByDate = mutableMapOf<String, Int>()
            val sleepByDate = mutableMapOf<String, IntArray>() // [total, deep, rem, light, awake, sessions]
            val nutritionByDate = mutableMapOf<String, DoubleArray>() // [kcal, prot, carbs, fat, count]
            val weightByDate = mutableMapOf<String, Pair<Long, Double>>() // epochMs -> kg

            // 1. Steps (batched 1-day periods)
            if (canSteps) {
                runCatching {
                    val buckets = client.aggregateGroupByPeriod(
                        AggregateGroupByPeriodRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(localStart, localEndExclusive),
                            timeRangeSlicer = Period.ofDays(1),
                        ),
                    )
                    for (bucket in buckets) {
                        val count = bucket.result[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
                        if (count > 0) {
                            val key = bucket.startTime.toLocalDate().toString()
                            stepsByDate[key] = (stepsByDate[key] ?: 0) + count
                        }
                    }
                }
            }

            // 2. Hydration (batched 1-day periods)
            if (canHydration) {
                runCatching {
                    val buckets = client.aggregateGroupByPeriod(
                        AggregateGroupByPeriodRequest(
                            metrics = setOf(HydrationRecord.VOLUME_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(localStart, localEndExclusive),
                            timeRangeSlicer = Period.ofDays(1),
                        ),
                    )
                    for (bucket in buckets) {
                        val ml = bucket.result[HydrationRecord.VOLUME_TOTAL]?.inMilliliters?.roundToInt() ?: 0
                        if (ml > 0) {
                            val key = bucket.startTime.toLocalDate().toString()
                            hydrationByDate[key] = (hydrationByDate[key] ?: 0) + ml
                        }
                    }
                }
            }

            // 3. Sleep (attribute overnight sessions to their wake-up LocalDate)
            if (canSleep) {
                runCatching {
                    val sleepWindowStart = chunkStart.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
                    var pageToken: String? = null
                    do {
                        val response = client.readRecords(
                            ReadRecordsRequest(
                                recordType = SleepSessionRecord::class,
                                timeRangeFilter = TimeRangeFilter.between(sleepWindowStart, instantEnd),
                                pageToken = pageToken,
                            ),
                        )
                        for (session in response.records) {
                            val wakeDate = session.endTime.atZone(zone).toLocalDate()
                            if (wakeDate.isBefore(chunkStart) || wakeDate.isAfter(chunkEnd)) continue
                            val key = wakeDate.toString()
                            val acc = sleepByDate.getOrPut(key) { IntArray(6) }
                            acc[5] += 1
                            if (session.stages.isNotEmpty()) {
                                var sessionAsleepMin = 0
                                for (stage in session.stages) {
                                    val mins = Duration.between(stage.startTime, stage.endTime)
                                        .toMinutes()
                                        .toInt()
                                        .coerceAtLeast(0)
                                    when (stage.stage) {
                                        SleepSessionRecord.STAGE_TYPE_DEEP -> {
                                            acc[1] += mins
                                            sessionAsleepMin += mins
                                        }
                                        SleepSessionRecord.STAGE_TYPE_REM -> {
                                            acc[2] += mins
                                            sessionAsleepMin += mins
                                        }
                                        SleepSessionRecord.STAGE_TYPE_LIGHT,
                                        SleepSessionRecord.STAGE_TYPE_SLEEPING -> {
                                            acc[3] += mins
                                            sessionAsleepMin += mins
                                        }
                                        SleepSessionRecord.STAGE_TYPE_AWAKE,
                                        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                                        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> {
                                            acc[4] += mins
                                        }
                                        else -> {
                                            sessionAsleepMin += mins
                                        }
                                    }
                                }
                                acc[0] += if (sessionAsleepMin > 0) {
                                    sessionAsleepMin
                                } else {
                                    Duration.between(session.startTime, session.endTime)
                                        .toMinutes()
                                        .toInt()
                                        .coerceAtLeast(0)
                                }
                            } else {
                                acc[0] += Duration.between(session.startTime, session.endTime)
                                    .toMinutes()
                                    .toInt()
                                    .coerceAtLeast(0)
                            }
                        }
                        pageToken = response.pageToken
                    } while (pageToken != null)
                }
            }

            // 4. Nutrition
            if (canNutrition) {
                runCatching {
                    var pageToken: String? = null
                    do {
                        val response = client.readRecords(
                            ReadRecordsRequest(
                                recordType = NutritionRecord::class,
                                timeRangeFilter = TimeRangeFilter.between(instantStart, instantEnd),
                                pageToken = pageToken,
                            ),
                        )
                        for (rec in response.records) {
                            val key = rec.startTime.atZone(zone).toLocalDate().toString()
                            val acc = nutritionByDate.getOrPut(key) { DoubleArray(5) }
                            acc[0] += rec.energy?.inKilocalories ?: 0.0
                            acc[1] += rec.protein?.inGrams ?: 0.0
                            acc[2] += rec.totalCarbohydrate?.inGrams ?: 0.0
                            acc[3] += rec.totalFat?.inGrams ?: 0.0
                            acc[4] += 1.0
                        }
                        pageToken = response.pageToken
                    } while (pageToken != null)
                }
            }

            // 5. Bodyweight (latest reading per day)
            if (canWeight) {
                runCatching {
                    var pageToken: String? = null
                    do {
                        val response = client.readRecords(
                            ReadRecordsRequest(
                                recordType = WeightRecord::class,
                                timeRangeFilter = TimeRangeFilter.between(instantStart, instantEnd),
                                pageToken = pageToken,
                            ),
                        )
                        for (rec in response.records) {
                            val key = rec.time.atZone(zone).toLocalDate().toString()
                            val ts = rec.time.toEpochMilli()
                            val prev = weightByDate[key]
                            if (prev == null || ts >= prev.first) {
                                weightByDate[key] = ts to rec.weight.inKilograms
                            }
                        }
                        pageToken = response.pageToken
                    } while (pageToken != null)
                }
            }

            val datesWithData = (stepsByDate.keys + hydrationByDate.keys + sleepByDate.keys + nutritionByDate.keys + weightByDate.keys)
                .sorted()

            val chunkReadings = datesWithData.mapNotNull { dateKey ->
                val sleepArr = sleepByDate[dateKey]
                val nutArr = nutritionByDate[dateKey]
                val reading = LocalHealthReading(
                    date = dateKey,
                    steps = stepsByDate[dateKey] ?: 0,
                    hydrationMl = hydrationByDate[dateKey] ?: 0,
                    sleepTotalMinutes = sleepArr?.get(0) ?: 0,
                    sleepDeepMinutes = sleepArr?.get(1) ?: 0,
                    sleepRemMinutes = sleepArr?.get(2) ?: 0,
                    sleepLightMinutes = sleepArr?.get(3) ?: 0,
                    sleepAwakeMinutes = sleepArr?.get(4) ?: 0,
                    sleepSessionCount = sleepArr?.get(5) ?: 0,
                    calories = nutArr?.get(0) ?: 0.0,
                    proteinGrams = nutArr?.get(1) ?: 0.0,
                    carbsGrams = nutArr?.get(2) ?: 0.0,
                    fatGrams = nutArr?.get(3) ?: 0.0,
                    nutritionEntryCount = nutArr?.get(4)?.roundToInt() ?: 0,
                    bodyweightKg = weightByDate[dateKey]?.second,
                    syncedAtEpochMs = nowMs,
                )
                reading.takeIf { it.hasAnyData }
            }

            allReadings.addAll(chunkReadings)
            onChunkProgress?.invoke(chunkStart, chunkEnd, chunkReadings)

            chunkEnd = chunkStart.minusDays(1)
        }

        return allReadings.sortedBy { it.date }
    }

    companion object {
        val PERM_STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)
        val PERM_HYDRATION: String = HealthPermission.getReadPermission(HydrationRecord::class)
        val PERM_SLEEP: String = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val PERM_NUTRITION: String = HealthPermission.getReadPermission(NutritionRecord::class)
        val PERM_WEIGHT: String = HealthPermission.getReadPermission(WeightRecord::class)
        val PERM_BACKGROUND: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        val PERM_HISTORY: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

        val ALL_PERMISSIONS: Set<String> = setOf(
            PERM_STEPS,
            PERM_HYDRATION,
            PERM_SLEEP,
            PERM_NUTRITION,
            PERM_WEIGHT,
            PERM_BACKGROUND,
            PERM_HISTORY,
        )
    }
}
