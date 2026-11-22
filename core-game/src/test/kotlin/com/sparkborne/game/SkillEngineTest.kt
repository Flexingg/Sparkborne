package com.sparkborne.game

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.SkillEngine
import com.sparkborne.game.engine.StepBankManager
import com.sparkborne.game.engine.WorldManager
import com.sparkborne.game.model.SkillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillEngineTest {

    @Test
    fun `skill levels 1 to 99 increase work efficiency and reduce steps per action`() {
        assertEquals(1, SkillEngine.levelForXp(0))
        assertEquals(2, SkillEngine.levelForXp(50))
        assertEquals(99, SkillEngine.levelForXp(500_000))

        val baseState = GameCatalog.initialGameState()
        val stepsLv1 = SkillEngine.effectiveStepsPerAction(1000, baseState, SkillType.MINING)
        assertEquals(1000, stepsLv1)

        val highLevelState = baseState.copy(
            player = baseState.player.copy(
                skillXp = mapOf(SkillType.MINING to SkillEngine.xpForLevel(51)),
            ),
        )
        assertEquals(51, highLevelState.player.skillLevel(SkillType.MINING))
        assertEquals(25, SkillEngine.workEfficiencyPercent(51))
        val stepsLv51 = SkillEngine.effectiveStepsPerAction(1000, highLevelState, SkillType.MINING)
        assertEquals(800, stepsLv51)
    }

    @Test
    fun `completing 5 activity cycles awards a skill chest and every 5th chest opened guarantees an egg`() {
        var state = GameCatalog.initialGameState().let {
            it.copy(player = it.player.copy(savedSteps = 30_000))
        }
        state = WorldManager.startActivity(state, "act_ember_mining").getOrThrow()

        // Spend 5 cycles -> should earn 1 Mining Skill Chest + Mining XP
        state = StepBankManager.spendBankedStepsOnActivity(state, maxCycles = 5).getOrThrow()
        assertEquals(1, state.player.skillChests[SkillType.MINING])
        assertTrue((state.player.skillXp[SkillType.MINING] ?: 0) >= 70)

        // Open 1st chest -> awards gold, iron, shards, bonus XP
        val goldBefore = state.player.gold
        val (afterOpen1, reward1) = SkillEngine.openSkillChest(state, SkillType.MINING).getOrThrow()
        assertTrue(afterOpen1.player.gold > goldBefore)
        assertEquals(0, afterOpen1.player.skillChests[SkillType.MINING] ?: 0)
        assertEquals(1, afterOpen1.player.totalChestsOpened)
        assertTrue(reward1.gold > 0)

        // 5th chest opened guarantees a rare Skill-Themed Monster Egg
        val readyForFifth = afterOpen1.copy(
            player = afterOpen1.player.copy(
                skillChests = mapOf(SkillType.MINING to 1),
                totalChestsOpened = 4,
            ),
        )
        val (afterFifth, reward5) = SkillEngine.openSkillChest(readyForFifth, SkillType.MINING).getOrThrow()
        assertNotNull(reward5.eggAwarded)
        assertEquals(1, afterFifth.eggs.size)
    }
}
