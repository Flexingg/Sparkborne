package com.sparkborne.game

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.CollectibleEngine
import com.sparkborne.game.engine.SkillEngine
import com.sparkborne.game.engine.StepBankManager
import com.sparkborne.game.engine.WorldManager
import com.sparkborne.game.model.CollectibleBonusType
import com.sparkborne.game.model.SkillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectibleEngineTest {

    @Test
    fun `completing activity milestone discovers rare collectible and applies passive bonus`() {
        var state = GameCatalog.initialGameState().let {
            it.copy(player = it.player.copy(savedSteps = 10_000))
        }
        state = WorldManager.startActivity(state, "act_ember_mining").getOrThrow()

        // 4 completions of Basalt Seam unlocks Basalt Heartstone (+4% Iron & Shards)
        state = StepBankManager.spendBankedStepsOnActivity(state, maxCycles = 4).getOrThrow()
        assertTrue("col_basalt_heartstone" in state.player.discoveredCollectibleIds)
        assertEquals(4, CollectibleEngine.totalBonus(state, CollectibleBonusType.IRON_SHARD_YIELD_PERCENT))

        // Work efficiency collectibles reduce steps needed per action
        val withWatch = state.copy(
            player = state.player.copy(
                discoveredCollectibleIds = state.player.discoveredCollectibleIds + "col_glacial_pocketwatch",
            ),
        )
        val baseSteps = SkillEngine.effectiveStepsPerAction(1000, state, SkillType.MINING)
        val boostedSteps = SkillEngine.effectiveStepsPerAction(1000, withWatch, SkillType.MINING)
        assertTrue(boostedSteps < baseSteps)
    }
}
