package com.sparkborne.telemetry.client

import com.sparkborne.telemetry.dto.CheckInMeasurementDto
import com.sparkborne.telemetry.dto.DailySummaryResponseDto
import com.sparkborne.telemetry.dto.SleepEntryDto
import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import com.sparkborne.telemetry.normalizer.TelemetryNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Strongly typed REST client for SparkyFitness.
 *
 * Uses `x-api-key` header authentication over direct HTTPS (Cloudflare Tunnel).
 */
class SparkyFitnessApiClient(
    private val config: SparkyFitnessConfig,
    private val httpClient: OkHttpClient = defaultHttpClient(config),
    private val json: Json = defaultJson,
    private val clockEpochMs: () -> Long = System::currentTimeMillis,
) {

    /**
     * Verifies reachability of the SparkyFitness server via `GET /api/ping`.
     */
    suspend fun ping(): Result<String> = runCatching {
        executeGet("/api/ping")
    }

    /**
     * Fetches consolidated goals, nutrition, lifting sessions, and hydration for a single date.
     */
    suspend fun fetchDailySummary(date: LocalDate): DailySummaryResponseDto {
        val path = "/api/daily-summary?date=$date"
        val body = executeGet(path)
        return decodeJson(path, body)
    }

    /**
     * Fetches daily check-in measurements (bodyweight, steps, body fat) for an inclusive date range.
     */
    suspend fun fetchCheckInMeasurementsRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<CheckInMeasurementDto> {
        val path = "/api/measurements/check-in-measurements-range/$startDate/$endDate"
        val body = executeGet(path)
        return decodeJson(path, body)
    }

    /**
     * Fetches sleep entries (duration + stages) for an inclusive date range.
     */
    suspend fun fetchSleepRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<SleepEntryDto> {
        val path = "/api/sleep?startDate=$startDate&endDate=$endDate"
        val body = executeGet(path)
        return decodeJson(path, body)
    }

    /**
     * Fetches and normalizes a complete inclusive date range from SparkyFitness into deterministic,
     * idempotent [DailyTelemetrySnapshot] objects ordered chronologically.
     */
    suspend fun fetchNormalizedRange(
        startDate: LocalDate,
        endDate: LocalDate,
        maxConcurrency: Int = 4,
    ): List<DailyTelemetrySnapshot> = coroutineScope {
        require(!startDate.isAfter(endDate)) {
            "startDate ($startDate) must not be after endDate ($endDate)"
        }

        val checkInsDeferred = async {
            runCatching { fetchCheckInMeasurementsRange(startDate, endDate) }.getOrElse { ex ->
                if (ex is SparkyFitnessApiException.Unauthorized || ex is SparkyFitnessApiException.Forbidden) {
                    throw ex
                }
                emptyList()
            }
        }

        val sleepDeferred = async {
            runCatching { fetchSleepRange(startDate, endDate) }.getOrElse { ex ->
                if (ex is SparkyFitnessApiException.Unauthorized || ex is SparkyFitnessApiException.Forbidden) {
                    throw ex
                }
                emptyList()
            }
        }

        val dates = generateSequence(startDate) { current ->
            if (current.isBefore(endDate)) current.plusDays(1) else null
        }.toList()

        val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
        val summariesByDate = dates.map { date ->
            async {
                semaphore.withPermit {
                    date to fetchDailySummary(date)
                }
            }
        }.awaitAll().toMap()

        val checkInsByDate = checkInsDeferred.await()
            .filter { !it.entryDate.isNullOrBlank() }
            .associateBy { normalizeIsoDate(it.entryDate!!) }

        val sleepByDate = sleepDeferred.await()
            .filter { !it.entryDate.isNullOrBlank() }
            .groupBy { normalizeIsoDate(it.entryDate!!) }

        val syncedAt = clockEpochMs()

        dates.map { date ->
            val dateStr = date.toString()
            TelemetryNormalizer.normalize(
                date = dateStr,
                dailySummary = summariesByDate[date],
                checkIn = checkInsByDate[dateStr],
                sleepEntries = sleepByDate[dateStr].orEmpty(),
                syncedAtEpochMs = syncedAt,
            )
        }
    }

    private suspend fun executeGet(path: String): String = withContext(Dispatchers.IO) {
        val url = "${config.normalizedBaseUrl}$path"
        val request = Request.Builder()
            .url(url)
            .header("x-api-key", config.apiKey.trim())
            .header("Accept", "application/json")
            .get()
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw SparkyFitnessApiException.NetworkError("Network error calling $path: ${e.message}", e)
        }

        response.use { res ->
            val bodyString = res.body?.string().orEmpty()
            when (res.code) {
                in 200..299 -> bodyString
                401 -> throw SparkyFitnessApiException.Unauthorized()
                403 -> throw SparkyFitnessApiException.Forbidden()
                404 -> throw SparkyFitnessApiException.NotFound(path)
                else -> throw SparkyFitnessApiException.HttpError(res.code, bodyString)
            }
        }
    }

    private inline fun <reified T> decodeJson(path: String, body: String): T {
        return try {
            json.decodeFromString(body)
        } catch (e: Exception) {
            throw SparkyFitnessApiException.ParseError(path, e)
        }
    }

    private fun normalizeIsoDate(raw: String): String {
        return raw.trim().take(10)
    }

    companion object {
        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

        fun defaultHttpClient(config: SparkyFitnessConfig): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
                .build()
        }
    }
}
