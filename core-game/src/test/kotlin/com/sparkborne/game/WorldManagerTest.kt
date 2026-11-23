package com.sparkborne.game

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.TelemetryRulesEngine
import com.sparkborne.game.engine.WorldManager
import com.sparkborne.telemetry.dto.CheckInMeasurementDto
import com.sparkborne.telemetry.normalizer.SnapshotIdempotency
import com.sparkborne.telemetry.normalizer.TelemetryNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldManagerTest {

    @Test
    fun `walking steps completes travel and unlocks destination town`() {
        var state = GameCatalog.initialGameState()
        assertEquals("loc_emberhearth", state.currentLocationId)

        // Start traveling to Verdant Crossing (3500 steps)
        state = WorldManager.startTravel(state, "loc_verdant_crossing").getOrThrow()
        assertEquals(3500, state.activeTravel!!.stepsRequired)

        val snap = TelemetryNormalizer.normalize(
            date = "2026-11-22",
            dailySummary = null,
            checkIn = CheckInMeasurementDto(entryDate = "2026-11-22", steps = 4000.0),
            sleepEntries = emptyList(),
            syncedAtEpochMs = 1_000L,
        )
        val delta = SnapshotIdempotency.computeDelta(null, snap)
        val banked = TelemetryRulesEngine.applyTelemetry(state, snap, delta, null).state
        assertEquals(4000, banked.player.savedSteps)
        val arrived = com.sparkborne.game.engine.StepBankManager.spendBankedStepsOnTravel(banked).getOrThrow()

        assertEquals("loc_verdant_crossing", arrived.currentLocationId)
        assertNull(arrived.activeTravel)
        assertEquals(500, arrived.player.savedSteps)
        assertTrue("loc_verdant_crossing" in arrived.discoveredLocationIds)
    }

    @Test
    fun `synced steps complete multiple WalkScape town activity cycles and grant resources`() {
        var state = GameCatalog.initialGameState().let {
            it.copy(
                player = it.player.copy(
                    actionBanks = it.player.actionBanks + (com.sparkborne.game.model.ActionBankType.PUSH_VOLUME to 850),
                ),
            )
        }
        val initialIron = state.player.ironOre
        val initialGold = state.player.gold

        // Mine Basalt Seam: 250 kg Push Volume/action -> +6g, +4 iron
        state = WorldManager.startActivity(state, "act_ember_mining").getOrThrow()
        val result = com.sparkborne.game.engine.StepBankManager.spendBankedStepsOnActivity(state).getOrThrow()

        // 850 kg Push = 3 completions (750 kg) + 100 kg remainder
        assertEquals(3, result.activeActivity!!.completionsCount)
        assertEquals(100, result.activeActivity!!.stepsProgress)
        assertEquals(initialIron + 12, result.player.ironOre)
        assertEquals(initialGold + 18, result.player.gold)
    }

    @Test
    fun `town shop lets player buy local weapon or egg only when in that town`() {
        var state = GameCatalog.initialGameState()
        // In Emberhearth: buying Ashwood Spear (45g, 8 iron) works
        state = WorldManager.buyShopListing(state, "shop_ember_spear").getOrThrow()
        assertEquals("wpn_ash_spear", state.player.equippedWeaponId)

        // Buying Tidebreak Harbor shop item while still in Emberhearth must fail
        val remoteBuy = WorldManager.buyShopListing(state, "shop_tide_mend")
        assertTrue(remoteBuy.isFailure)
    }
}
