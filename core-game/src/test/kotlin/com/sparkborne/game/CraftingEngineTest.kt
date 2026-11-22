package com.sparkborne.game

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.CraftingEngine
import com.sparkborne.game.engine.SkillEngine
import com.sparkborne.game.engine.StepBankManager
import com.sparkborne.game.engine.WorldManager
import com.sparkborne.game.model.MasterworkTier
import com.sparkborne.game.model.MaterialType
import com.sparkborne.game.model.MonsterEgg
import com.sparkborne.game.model.SkillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CraftingEngineTest {

    @Test
    fun `activities drop materials, crafting makes consumables, and Trailblazer Brew boosts efficiency`() {
        var state = GameCatalog.initialGameState().let {
            it.copy(
                player = it.player.copy(
                    savedSteps = 10_000,
                    materials = mapOf(
                        MaterialType.MOONLEAF_HERB to 4,
                        MaterialType.SILVERFIN_FISH to 2,
                        MaterialType.SUNSTONE_DUST to 2,
                        MaterialType.MIRE_SPORE to 1,
                    ),
                ),
                eggs = listOf(
                    MonsterEgg(
                        id = "egg_test",
                        name = "Test Egg",
                        speciesSeed = "test",
                        requiredSleepMinutes = 900,
                        requiredHydrationMl = 4500,
                    ),
                ),
            )
        }

        // Mining activity drops Basalt Ore
        state = WorldManager.startActivity(state, "act_ember_mining").getOrThrow()
        state = StepBankManager.spendBankedStepsOnActivity(state, maxCycles = 2).getOrThrow()
        assertTrue((state.player.materials[MaterialType.BASALT_ORE] ?: 0) >= 2)

        // Craft & drink Trailblazer Brew -> +25% Work Efficiency for 10 cycles
        val stepsBefore = SkillEngine.effectiveStepsPerAction(1000, state, SkillType.MINING)
        state = CraftingEngine.craftRecipe(state, "cons_trailblazer_brew").getOrThrow()
        assertEquals(1, state.player.consumables["cons_trailblazer_brew"])
        state = CraftingEngine.useConsumable(state, "cons_trailblazer_brew").getOrThrow()
        assertEquals(10, state.player.activeStepBoostCharges)
        val stepsBoosted = SkillEngine.effectiveStepsPerAction(1000, state, SkillType.MINING)
        assertTrue(stepsBoosted < stepsBefore)

        // Craft & use Warmth & Dew Elixir on incubating egg
        state = CraftingEngine.craftRecipe(state, "cons_egg_elixir").getOrThrow()
        state = CraftingEngine.useConsumable(state, "cons_egg_elixir").getOrThrow()
        assertEquals(180, state.eggs.first().incubatedSleepMinutes)
        assertEquals(900, state.eggs.first().incubatedHydrationMl)

        // Masterwork tier at Alchemy Lv 25 crafts 2x items!
        val masterState = state.copy(
            player = state.player.copy(
                skillXp = mapOf(SkillType.ALCHEMY to SkillEngine.xpForLevel(25)),
                materials = mapOf(MaterialType.MOONLEAF_HERB to 2, MaterialType.SILVERFIN_FISH to 1),
            ),
        )
        assertEquals(MasterworkTier.MASTERWORK, CraftingEngine.qualityTierFor(masterState, CraftingEngine.recipeById("cons_trailblazer_brew")!!))
        val afterMaster = CraftingEngine.craftRecipe(masterState, "cons_trailblazer_brew").getOrThrow()
        assertEquals(2, afterMaster.player.consumables["cons_trailblazer_brew"])
    }
}
