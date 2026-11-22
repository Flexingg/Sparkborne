package com.sparkborne.game

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.CombatAction
import com.sparkborne.game.engine.CombatResolver
import com.sparkborne.game.engine.ExpeditionManager
import com.sparkborne.game.engine.TelemetryRulesEngine
import com.sparkborne.game.model.ExpeditionStatus
import com.sparkborne.telemetry.dto.CheckInMeasurementDto
import com.sparkborne.telemetry.dto.DailySummaryResponseDto
import com.sparkborne.telemetry.normalizer.SnapshotIdempotency
import com.sparkborne.telemetry.normalizer.TelemetryNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpeditionAndCombatTest {

    @Test
    fun `completing a daily expedition clears nodes and banks all unbanked loot`() {
        val startDate = LocalDate.of(2026, 11, 22)
        var state = GameCatalog.initialGameState()
        val initialGold = state.player.gold

        state = ExpeditionManager.startExpedition(state, "exp_daily_ashlands_patrol", startDate).getOrThrow()

        // Walk 2000 steps per node (3 nodes total)
        var previousSnap = TelemetryNormalizer.normalize(startDate.toString(), null, null, emptyList(), 1_000L)
        for (nodeIdx in 0 until 3) {
            val cumulativeSteps = (nodeIdx + 1) * 2000.0
            val snap = TelemetryNormalizer.normalize(
                date = startDate.toString(),
                dailySummary = DailySummaryResponseDto(waterIntake = 2500.0),
                checkIn = CheckInMeasurementDto(entryDate = startDate.toString(), steps = cumulativeSteps),
                sleepEntries = emptyList(),
                syncedAtEpochMs = 2_000L + nodeIdx,
            )
            val delta = SnapshotIdempotency.computeDelta(if (nodeIdx == 0) null else previousSnap, snap)
            previousSnap = snap

            state = TelemetryRulesEngine.applyTelemetry(state, snap, delta, null).state
            state = ExpeditionManager.enterCurrentNode(state).getOrThrow()

            // If combat started, fight deterministically until victory
            var guardTurns = 0
            while (state.activeExpedition?.activeCombat != null && guardTurns < 25) {
                val combat = state.activeExpedition!!.activeCombat!!
                val action = when {
                    combat.monsterSkillCooldown == 0 -> CombatAction.MonsterSkill
                    combat.playerMana >= 5 -> CombatAction.CastSpell("spl_spark_bolt")
                    else -> CombatAction.Strike
                }
                state = CombatResolver.performAction(state, action)
                guardTurns++
            }
        }

        val exp = state.activeExpedition!!
        assertEquals(ExpeditionStatus.COMPLETED, exp.status)
        assertEquals(1, state.totalRunsCompleted)
        assertTrue("Gold should be banked on expedition clear", state.player.gold > initialGold)
    }

    @Test
    fun `missing contract deadline fails expedition and wipes all unbanked loot`() {
        val startDate = LocalDate.of(2026, 11, 20)
        var state = GameCatalog.initialGameState()
        val startingGold = state.player.gold

        state = ExpeditionManager.startExpedition(state, "exp_daily_ashlands_patrol", startDate).getOrThrow()

        // Accumulate some unbanked loot on node 0
        val expWithLoot = state.activeExpedition!!.copy(
            unbankedLoot = state.activeExpedition!!.unbankedLoot.copy(gold = 99, emberShards = 12, ironOre = 20),
        )
        state = state.copy(activeExpedition = expWithLoot)

        // Advance past deadline (2026-11-20 -> 2026-11-21)
        state = ExpeditionManager.checkContractDeadline(state, LocalDate.of(2026, 11, 21))

        val failedExp = state.activeExpedition!!
        assertEquals(ExpeditionStatus.FAILED, failedExp.status)
        assertNotNull(failedExp.failureReason)
        assertEquals(0, failedExp.unbankedLoot.gold)
        assertEquals(0, failedExp.unbankedLoot.emberShards)
        assertEquals(0, failedExp.unbankedLoot.ironOre)
        assertEquals(startingGold, state.player.gold)
        assertEquals(1, state.totalRunsFailed)
    }

    @Test
    fun `forging weapon and unlocking spell consumes materials and equips them`() {
        var state = GameCatalog.initialGameState()
        // Starter has 50g, 10 iron, 5 shards
        state = ExpeditionManager.forgeAndEquipWeapon(state, "wpn_ash_spear").getOrThrow()
        assertEquals("wpn_ash_spear", state.player.equippedWeaponId)
        assertEquals(5, state.player.gold)
        assertEquals(2, state.player.ironOre)

        state = ExpeditionManager.unlockAndAttuneSpell(state, "spl_tidal_mend").getOrThrow()
        assertTrue("spl_tidal_mend" in state.player.attunedSpellIds)
        assertEquals(1, state.player.emberShards)
    }
}
