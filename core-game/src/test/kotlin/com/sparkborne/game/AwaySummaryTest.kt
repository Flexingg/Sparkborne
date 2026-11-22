package com.sparkborne.game

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.StepBankManager
import com.sparkborne.game.engine.TelemetryRulesEngine
import com.sparkborne.telemetry.dto.CheckInMeasurementDto
import com.sparkborne.telemetry.normalizer.SnapshotIdempotency
import com.sparkborne.telemetry.normalizer.TelemetryNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AwaySummaryTest {

    @Test
    fun `telemetry sync populates pendingAwaySummary and dismissAwaySummary clears it`() {
        var state = GameCatalog.initialGameState()
        val snap = TelemetryNormalizer.normalize(
            date = "2026-11-22",
            dailySummary = null,
            checkIn = CheckInMeasurementDto(entryDate = "2026-11-22", steps = 4200.0),
            sleepEntries = emptyList(),
            syncedAtEpochMs = 1_000L,
        )
        val delta = SnapshotIdempotency.computeDelta(null, snap)
        state = TelemetryRulesEngine.applyTelemetry(state, snap, delta, null).state

        val summary = state.pendingAwaySummary
        assertNotNull(summary)
        assertTrue(summary!!.hasAnythingToShow)
        assertEquals(4200, summary.stepsBanked)

        state = StepBankManager.dismissAwaySummary(state)
        assertNull(state.pendingAwaySummary)
    }
}
