package com.sparkborne.game

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.ActionBankManager
import com.sparkborne.game.engine.ExpeditionManager
import com.sparkborne.game.engine.SkillEngine
import com.sparkborne.game.engine.StepBankManager
import com.sparkborne.game.engine.TelemetryRulesEngine
import com.sparkborne.game.engine.WorldManager
import com.sparkborne.game.model.ActionBankType
import com.sparkborne.game.model.SkillType
import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import com.sparkborne.telemetry.model.GoalAdherenceSummary
import com.sparkborne.telemetry.model.HydrationSummary
import com.sparkborne.telemetry.model.LiftingSummary
import com.sparkborne.telemetry.model.MuscleGroup
import com.sparkborne.telemetry.model.MuscleGroupVolume
import com.sparkborne.telemetry.model.NutritionSummary
import com.sparkborne.telemetry.model.SleepSummary
import com.sparkborne.telemetry.model.TelemetryGoals
import com.sparkborne.telemetry.normalizer.SnapshotIdempotency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionBankTest {

    @Test
    fun `push volume fuels mining, hydration fuels diving, calories burned fuels combat, and skill levels expand bank caps`() {
        var state = GameCatalog.initialGameState()

        // 1. Sync a day with Push lifting (Chest 2,000 kg), Pull lifting (Back 1,500 kg),
        //    Hydration (1,500 mL), Active Burn (400 kcal), and 5,000 steps
        val snap = DailyTelemetrySnapshot(
            date = "2026-11-23",
            goals = TelemetryGoals(2500, 180.0, 250.0, 70.0, 3000, 10000, 480),
            nutrition = NutritionSummary(2400.0, 180.0, 240.0, 70.0, 4),
            hydration = HydrationSummary(totalMl = 1500, manualMl = 1500, ledgerMl = 0, foodMl = 0),
            sleep = SleepSummary(480, 90, 90, 280, 20, 1),
            steps = 5000,
            lifting = LiftingSummary(
                totalVolumeKg = 3500.0,
                totalWorkingSets = 7,
                totalReps = 70,
                workoutCount = 1,
                byMuscleGroup = mapOf(
                    MuscleGroup.CHEST to MuscleGroupVolume(MuscleGroup.CHEST, 2000.0, 0.0, 2000.0, 4, 40, 50.0, listOf("Bench Press")),
                    MuscleGroup.BACK to MuscleGroupVolume(MuscleGroup.BACK, 1500.0, 0.0, 1500.0, 3, 30, 50.0, listOf("Barbell Row")),
                ),
            ),
            bodyweightKg = 80.0,
            adherence = GoalAdherenceSummary(0.96, 1.0, 0.96, 1.0, 0.5, 0.5, 1.0),
            contentHash = "hash-action-bank-test",
            syncedAtEpochMs = 1000L,
            activeCaloriesBurned = 400.0,
        )
        val delta = SnapshotIdempotency.computeDelta(null, snap)
        state = TelemetryRulesEngine.applyTelemetry(state, snap, delta, null).state

        // Push bank gained +2000 kg (starter 500 + 2000 = 2500)
        assertEquals(2500, ActionBankManager.balanceOf(state.player, ActionBankType.PUSH_VOLUME))
        // Pull & Legs bank gained +1500 kg (starter 500 + 1500 = 2000)
        assertEquals(2000, ActionBankManager.balanceOf(state.player, ActionBankType.PULL_LEGS_VOLUME))
        // Hydration bank gained +1500 mL (starter 1000 + 1500 = 2500)
        assertEquals(2500, ActionBankManager.balanceOf(state.player, ActionBankType.HYDRATION_ML))
        // Energy Burn bank gained +400 kcal (starter 300 + 400 = 700)
        assertEquals(700, ActionBankManager.balanceOf(state.player, ActionBankType.CALORIES_BURNT))

        // 2. Spend Push Volume on Mining (Emberhearth: Mine Basalt Seam -> 250 kg/cycle)
        val pushBefore = ActionBankManager.balanceOf(state.player, ActionBankType.PUSH_VOLUME)
        state = WorldManager.startActivity(state, "act_ember_mining").getOrThrow()
        state = StepBankManager.spendBankedStepsOnActivity(state, maxCycles = 2).getOrThrow()
        assertEquals(2, state.activeActivity!!.completionsCount)
        assertTrue(ActionBankManager.balanceOf(state.player, ActionBankType.PUSH_VOLUME) < pushBefore)

        // 3. Fighting monsters in a delve spends Energy Burn (50 kcal skirmish)
        val burnBefore = ActionBankManager.balanceOf(state.player, ActionBankType.CALORIES_BURNT)
        val contractId = GameCatalog.contracts.first().id
        val delveStart = ExpeditionManager.startExpedition(state, contractId, java.time.LocalDate.of(2026, 11, 23)).getOrThrow()
        val unlocked = StepBankManager.spendBankedStepsOnDelve(delveStart).getOrElse { delveStart }
        val entered = ExpeditionManager.enterCurrentNode(unlocked).getOrThrow()
        assertTrue(ActionBankManager.balanceOf(entered.player, ActionBankType.CALORIES_BURNT) < burnBefore)

        // 4. Leveling up Mining expands Push Volume Bank capacity!
        val capLv1 = ActionBankManager.capacityOf(state.player, ActionBankType.PUSH_VOLUME)
        val leveled = state.player.copy(skillXp = mapOf(SkillType.MINING to SkillEngine.xpForLevel(20)))
        val capLv20 = ActionBankManager.capacityOf(leveled, ActionBankType.PUSH_VOLUME)
        assertTrue(capLv20 > capLv1)
    }
}
