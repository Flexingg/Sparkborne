package com.sparkborne.telemetry

import com.sparkborne.telemetry.client.SparkyFitnessApiClient
import com.sparkborne.telemetry.client.SparkyFitnessApiException
import com.sparkborne.telemetry.client.SparkyFitnessConfig
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class SparkyFitnessApiClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sends x-api-key header and fetches normalized range cleanly`() = runTest {
        val observedKeys = mutableListOf<String?>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                observedKeys.add(request.getHeader("x-api-key"))
                return when {
                    request.path == "/api/ping" ->
                        MockResponse().setResponseCode(200).setBody("""{"status":"ok"}""")
                    request.path?.startsWith("/api/daily-summary?date=2026-11-22") == true ->
                        MockResponse().setResponseCode(200).setBody(
                            """{"goals":{"calories":2300,"protein":170,"water_goal_ml":2800},"foodEntries":[{"calories":800,"protein":60}],"waterIntake":2100,"exerciseSessions":[]}""",
                        )
                    request.path == "/api/measurements/check-in-measurements-range/2026-11-22/2026-11-22" ->
                        MockResponse().setResponseCode(200).setBody(
                            """[{"entry_date":"2026-11-22","steps":8400,"weight":81.5}]""",
                        )
                    request.path == "/api/sleep?startDate=2026-11-22&endDate=2026-11-22" ->
                        MockResponse().setResponseCode(200).setBody(
                            """[{"entry_date":"2026-11-22","duration_in_seconds":28800}]""",
                        )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = SparkyFitnessApiClient(
            config = SparkyFitnessConfig(
                baseUrl = server.url("/").toString(),
                apiKey = "sk-test-key",
            ),
            clockEpochMs = { 12345L },
        )

        assertTrue(client.ping().isSuccess)

        val date = LocalDate.of(2026, 11, 22)
        val snapshots = client.fetchNormalizedRange(date, date)
        assertEquals(1, snapshots.size)

        val snap = snapshots.single()
        assertEquals("2026-11-22", snap.date)
        assertEquals(8400, snap.steps)
        assertEquals(81.5, snap.bodyweightKg!!, 0.01)
        assertEquals(480, snap.sleep.totalMinutes)
        assertEquals(2100, snap.hydration.totalMl)
        assertEquals(800.0, snap.nutrition.calories, 0.01)
        assertTrue(observedKeys.all { it == "sk-test-key" })
    }

    @Test(expected = SparkyFitnessApiException.Unauthorized::class)
    fun `maps 401 to SparkyFitnessApiException Unauthorized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Unauthorized"}"""))
        val client = SparkyFitnessApiClient(
            config = SparkyFitnessConfig(baseUrl = server.url("/").toString(), apiKey = "bad-key"),
        )
        client.fetchDailySummary(LocalDate.of(2026, 11, 22))
    }
}
