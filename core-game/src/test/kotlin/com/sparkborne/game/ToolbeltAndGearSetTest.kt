package com.sparkborne.game

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.SkillEngine
import com.sparkborne.game.engine.ToolbeltEngine
import com.sparkborne.game.model.SkillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbeltAndGearSetTest {

    @Test
    fun `forging toolbelt item reduces steps needed and gear sets swap loadouts cleanly`() {
        var state = GameCatalog.initialGameState().let {
            it.copy(
                player = it.player.copy(
                    gold = 300,
                    ironOre = 60,
                    emberShards = 20,
                    ownedWeaponIds = it.player.ownedWeaponIds + "wpn_ash_spear",
                ),
            )
        }

        val stepsBefore = SkillEngine.effectiveStepsPerAction(1000, state, SkillType.MINING)
        state = ToolbeltEngine.forgeOrToggleTool(state, "tool_iron_pickaxe").getOrThrow()
        assertTrue("tool_iron_pickaxe" in state.player.equippedToolIds)
        val stepsAfter = SkillEngine.effectiveStepsPerAction(1000, state, SkillType.MINING)
        assertTrue(stepsAfter < stepsBefore)

        // Save Skilling loadout to Slot 0
        state = ToolbeltEngine.saveGearSet(state, 0, "Mining Set").getOrThrow()

        // Change weapon & unequip pickaxe, then save Combat loadout to Slot 1
        state = state.copy(player = state.player.copy(equippedWeaponId = "wpn_ash_spear"))
        state = ToolbeltEngine.forgeOrToggleTool(state, "tool_iron_pickaxe").getOrThrow()
        assertTrue(state.player.equippedToolIds.isEmpty())
        state = ToolbeltEngine.saveGearSet(state, 1, "Combat Set").getOrThrow()

        // 1-tap swap back to Slot 0 restores Iron Greatsword + Iron Pickaxe!
        state = ToolbeltEngine.equipGearSet(state, 0).getOrThrow()
        assertEquals("wpn_iron_greatsword", state.player.equippedWeaponId)
        assertEquals(listOf("tool_iron_pickaxe"), state.player.equippedToolIds)
    }
}
