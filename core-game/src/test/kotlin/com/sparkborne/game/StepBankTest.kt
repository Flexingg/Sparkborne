package com.sparkborne.game

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.ExpeditionManager
import com.sparkborne.game.engine.StepBankManager
import com.sparkborne.game.engine.TelemetryRulesEngine
import com.sparkborne.game.engine.WorldManager
import com.sparkborne.telemetry.dto.CheckInMeasurementDto
import com.sparkborne.telemetry.normalizer.SnapshotIdempotency
import com.sparkborne.telemetry.normalizer.TelemetryNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StepBankTest {

    @Test
    fun `step bank starts at 50000 capacity and scales into the millions with level`() {
        val fresh = GameCatalog.initialGameState()
        assertEquals(50_000, fresh.player.maxStepBankCapacity())

        val lv20 = fresh.player.copy(level = 20)
        assertTrue("Level 20 step bank should exceed 2,000,000 steps", lv20.maxStepBankCapacity() > 2_000_000)
    }

    @Test
    fun `idle synced steps bank automatically and can be spent on travel, activities, and delves`() {
        var state = GameCatalog.initialGameState()

        // Sync 12,000 steps while idle -> banks into savedSteps
        val snap = TelemetryNormalizer.normalize(
            date = "2026-11-22",
            dailySummary = null,
            checkIn = CheckInMeasurementDto(entryDate = "2026-11-22", steps = 12_000.0),
            sleepEntries = emptyList(),
            syncedAtEpochMs = 1_000L,
        )
        val delta = SnapshotIdempotency.computeDelta(null, snap)
        state = TelemetryRulesEngine.applyTelemetry(state, snap, delta, null).state
        assertEquals(12_000, state.player.savedSteps)

        // Spend banked steps to travel to Verdant Crossing (3,500 steps)
        state = WorldManager.startTravel(state, "loc_verdant_crossing").getOrThrow()
        state = StepBankManager.spendBankedStepsOnTravel(state).getOrThrow()
        assertEquals("loc_verdant_crossing", state.currentLocationId)
        assertNull(state.activeTravel)
        assertEquals(8_500, state.player.savedSteps)

        // Spend 1 cycle of Forage Moonleaf Herbs (900 steps)
        state = WorldManager.startActivity(state, "act_verdant_foraging").getOrThrow()
        state = StepBankManager.spendBankedStepsOnActivity(state, maxCycles = 1).getOrThrow()
        assertEquals(1, state.activeActivity!!.completionsCount)
        assertEquals(7_600, state.player.savedSteps)

        // Spend banked steps to unlock a Delve node (2,000 steps)
        state = ExpeditionManager.startExpedition(state, "exp_daily_ashlands_patrol", LocalDate.of(2026, 11, 22)).getOrThrow()
        state = StepBankManager.spendBankedStepsOnDelve(state).getOrThrow()
        assertTrue(state.activeExpedition!!.currentNode!!.isUnlocked)
        assertEquals(5_600, state.player.savedSteps)
    }

    @Test
    fun `live phone pedometer steps reconcile with Garmin syncs without double-counting`() {
        var state = GameCatalog.initialGameState()
        val date = "2026-11-22"

        // 1. Live phone pedometer counts 3,000 steps instantaneously
        val (afterPhone, addedPhone) = StepBankManager.applyLivePhoneSteps(state, date, 3_000)
        state = afterPhone
        assertEquals(3_000, addedPhone)
        assertEquals(3_000, state.player.savedSteps)

        // 2. Garmin syncs 2,800 steps (lower than phone) -> ZERO double-counting!
        val garminLow = TelemetryNormalizer.normalize(
            date = date,
            dailySummary = null,
            checkIn = CheckInMeasurementDto(entryDate = date, steps = 2_800.0),
            sleepEntries = emptyList(),
            syncedAtEpochMs = 2_000L,
        )
        val deltaLow = SnapshotIdempotency.computeDelta(null, garminLow)
        state = TelemetryRulesEngine.applyTelemetry(state, garminLow, deltaLow, null).state
        assertEquals(3_000, state.player.savedSteps)

        // 3. Later, user goes on a watch-only run and Garmin syncs 5,500 steps -> ONLY +2,500 net new steps credited!
        val garminHigh = TelemetryNormalizer.normalize(
            date = date,
            dailySummary = null,
            checkIn = CheckInMeasurementDto(entryDate = date, steps = 5_500.0),
            sleepEntries = emptyList(),
            syncedAtEpochMs = 3_000L,
        )
        val deltaHigh = SnapshotIdempotency.computeDelta(garminLow, garminHigh)
        state = TelemetryRulesEngine.applyTelemetry(state, garminHigh, deltaHigh, null).state
        assertEquals(5_500, state.player.savedSteps)
    }
}
