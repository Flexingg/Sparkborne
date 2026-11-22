package com.sparkborne.game

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.CombatAction
import com.sparkborne.game.engine.CombatResolver
import com.sparkborne.game.engine.ExpeditionManager
import com.sparkborne.game.engine.SparkpediaEngine
import com.sparkborne.game.engine.TelemetryRulesEngine
import com.sparkborne.game.engine.WorldManager
import com.sparkborne.game.model.IntroQuestStep
import com.sparkborne.telemetry.dto.CheckInMeasurementDto
import com.sparkborne.telemetry.normalizer.SnapshotIdempotency
import com.sparkborne.telemetry.normalizer.TelemetryNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SparkpediaAndIntroTest {

    @Test
    fun `fresh start is strictly Level 0 with no quests done, no pets, and no eggs even after initial Sparky sync`() {
        val fresh = GameCatalog.initialGameState()
        assertEquals(0, fresh.player.level)
        assertEquals(0, fresh.player.xp)
        assertTrue(fresh.monsters.isEmpty())
        assertTrue(fresh.eggs.isEmpty())
        assertNull(fresh.activeMonsterId)
        assertTrue(fresh.completedContractIds.isEmpty())
        assertEquals(IntroQuestStep.SYNC_SPARKY, fresh.introQuest.currentStep)

        // Syncing SparkyFitness before starting quests seeds baseline stats/buffs only
        val snap = TelemetryNormalizer.normalize(
            date = "2026-11-22",
            dailySummary = null,
            checkIn = CheckInMeasurementDto(entryDate = "2026-11-22", steps = 9000.0, weight = 85.0),
            sleepEntries = emptyList(),
            syncedAtEpochMs = 1_000L,
        )
        val delta = SnapshotIdempotency.computeDelta(null, snap)
        val synced = TelemetryRulesEngine.applyTelemetry(fresh, snap, delta, null).state

        assertEquals(0, synced.player.level)
        assertTrue(synced.monsters.isEmpty())
        assertTrue(synced.eggs.isEmpty())
        assertTrue(synced.completedContractIds.isEmpty())
        assertEquals(IntroQuestStep.VISIT_EMBERHEARTH, synced.introQuest.currentStep)
        assertTrue("guide_telemetry_map" in synced.unlockedCodexIds)
    }

    @Test
    fun `completing intro quest promotes Level 0 to Level 1 and awards first Monster Egg`() {
        val startDate = LocalDate.of(2026, 11, 22)
        var state = GameCatalog.initialGameState()

        // Step 1 -> 2 -> 3
        state = SparkpediaEngine.onTelemetrySynced(state)
        state = SparkpediaEngine.onVisitedShop(state)
        state = WorldManager.startActivity(state, "act_ember_mining").getOrThrow()
        assertEquals(IntroQuestStep.ACCEPT_INTRO_QUEST, state.introQuest.currentStep)

        // Step 4: Accept & clear "The Ember Nest" (2 nodes x 750 steps = 1500 steps)
        state = ExpeditionManager.startExpedition(state, SparkpediaEngine.INTRO_CONTRACT_ID, startDate).getOrThrow()

        var prevSnap = TelemetryNormalizer.normalize(startDate.toString(), null, null, emptyList(), 1_000L)
        for (i in 0 until 2) {
            val snap = TelemetryNormalizer.normalize(
                date = startDate.toString(),
                dailySummary = null,
                checkIn = CheckInMeasurementDto(entryDate = startDate.toString(), steps = (i + 1) * 750.0),
                sleepEntries = emptyList(),
                syncedAtEpochMs = 2_000L + i,
            )
            val delta = SnapshotIdempotency.computeDelta(if (i == 0) null else prevSnap, snap)
            prevSnap = snap
            state = TelemetryRulesEngine.applyTelemetry(state, snap, delta, null).state
            state = com.sparkborne.game.engine.StepBankManager.spendBankedStepsOnDelve(state).getOrThrow()
            state = ExpeditionManager.enterCurrentNode(state).getOrThrow()

            var turns = 0
            while (state.activeExpedition?.activeCombat != null && turns < 20) {
                state = CombatResolver.performAction(state, CombatAction.Strike)
                turns++
            }
        }

        assertTrue(state.introQuest.isComplete)
        assertTrue(state.player.level >= 1)
        assertEquals(1, state.eggs.size)
        assertEquals("Warm Ember Egg", state.eggs.single().name)
        assertTrue("guide_eggs_evolution" in state.unlockedCodexIds)
    }
}
