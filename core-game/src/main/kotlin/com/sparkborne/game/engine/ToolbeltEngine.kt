package com.sparkborne.game.engine

import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.GearSetPreset
import com.sparkborne.game.model.SkillType
import com.sparkborne.game.model.ToolItem

/**
 * Deterministic manager for Skilling Toolbelt slots, Equipment Attributes (+Work Efficiency &
 * +Bonus Yield), and 1-tap Gear Set Presets.
 */
object ToolbeltEngine {

    val tools: List<ToolItem> = listOf(
        ToolItem(
            id = "tool_iron_pickaxe",
            name = "Iron Pickaxe",
            icon = "⛏",
            description = "Mining & Smithing: +12% Work Efficiency, +10% Yield.",
            targetSkills = setOf(SkillType.MINING, SkillType.SMITHING),
            workEfficiencyBonusPercent = 12,
            bonusYieldPercent = 10,
            forgeGoldCost = 55,
            forgeIronCost = 12,
        ),
        ToolItem(
            id = "tool_ranger_sickle",
            name = "Moonleaf Sickle",
            icon = "🌿",
            description = "Foraging & Woodcutting: +14% Work Efficiency, +12% Yield.",
            targetSkills = setOf(SkillType.FORAGING, SkillType.WOODCUTTING),
            workEfficiencyBonusPercent = 14,
            bonusYieldPercent = 12,
            forgeGoldCost = 65,
            forgeIronCost = 10,
            forgeShardsCost = 2,
        ),
        ToolItem(
            id = "tool_harbor_rod",
            name = "Tidecaller Rod",
            icon = "🎣",
            description = "Fishing & Diving: +14% Work Efficiency, +12% Yield.",
            targetSkills = setOf(SkillType.FISHING, SkillType.DIVING),
            workEfficiencyBonusPercent = 14,
            bonusYieldPercent = 12,
            forgeGoldCost = 70,
            forgeIronCost = 8,
            forgeShardsCost = 3,
        ),
        ToolItem(
            id = "tool_alchemist_alembic",
            name = "Mire Alembic",
            icon = "🧪",
            description = "Alchemy, Salvage & Runecrafting: +15% Work Efficiency, +15% Yield.",
            targetSkills = setOf(SkillType.ALCHEMY, SkillType.SALVAGE, SkillType.RUNECRAFTING),
            workEfficiencyBonusPercent = 15,
            bonusYieldPercent = 15,
            forgeGoldCost = 95,
            forgeIronCost = 12,
            forgeShardsCost = 6,
        ),
        ToolItem(
            id = "tool_sunstone_trowel",
            name = "Sunstone Trowel",
            icon = "🏺",
            description = "Archaeology & Trade: +16% Work Efficiency, +15% Yield.",
            targetSkills = setOf(SkillType.ARCHAEOLOGY, SkillType.TRADE),
            workEfficiencyBonusPercent = 16,
            bonusYieldPercent = 15,
            forgeGoldCost = 120,
            forgeIronCost = 18,
            forgeShardsCost = 6,
        ),
        ToolItem(
            id = "tool_astral_compass",
            name = "Starbound Multi-Tool",
            icon = "🧭",
            description = "ALL Skills: +18% Work Efficiency, +18% Yield.",
            targetSkills = emptySet(),
            workEfficiencyBonusPercent = 18,
            bonusYieldPercent = 18,
            forgeGoldCost = 210,
            forgeIronCost = 35,
            forgeShardsCost = 15,
        ),
    )

    fun toolById(id: String): ToolItem? = tools.firstOrNull { it.id == id }

    fun workEfficiencyBonus(state: GameState, skill: SkillType): Int {
        return state.player.equippedToolIds
            .mapNotNull(::toolById)
            .filter { it.appliesTo(skill) }
            .sumOf { it.workEfficiencyBonusPercent }
    }

    fun bonusYieldPercent(state: GameState, skill: SkillType): Int {
        return state.player.equippedToolIds
            .mapNotNull(::toolById)
            .filter { it.appliesTo(skill) }
            .sumOf { it.bonusYieldPercent }
    }

    /**
     * Forges an unowned tool (deducting resources) and equips it onto the Toolbelt,
     * OR toggles equip/unequip if already owned.
     */
    fun forgeOrToggleTool(state: GameState, toolId: String): Result<GameState> {
        val tool = toolById(toolId)
            ?: return Result.failure(IllegalArgumentException("Unknown tool: $toolId"))
        val p = state.player
        val maxSlots = p.maxToolbeltSlots()

        // Already owned -> toggle equip/unequip
        if (toolId in p.ownedToolIds) {
            return if (toolId in p.equippedToolIds) {
                Result.success(
                    state.copy(
                        player = p.copy(equippedToolIds = p.equippedToolIds - toolId),
                        recentLog = (listOf("Unequipped ${tool.icon} ${tool.name} from Toolbelt.") + state.recentLog).take(30),
                    ),
                )
            } else {
                val updatedEquipped = (p.equippedToolIds + toolId).takeLast(maxSlots)
                Result.success(
                    state.copy(
                        player = p.copy(equippedToolIds = updatedEquipped),
                        recentLog = (listOf("Equipped ${tool.icon} ${tool.name} on Toolbelt.") + state.recentLog).take(30),
                    ),
                )
            }
        }

        // Forge new tool
        if (p.gold < tool.forgeGoldCost || p.ironOre < tool.forgeIronCost || p.emberShards < tool.forgeShardsCost) {
            return Result.failure(
                IllegalStateException("Need ${tool.forgeGoldCost}g, ${tool.forgeIronCost} iron, ${tool.forgeShardsCost} shards to forge ${tool.name}."),
            )
        }

        val nextEquipped = (p.equippedToolIds + toolId).takeLast(maxSlots)
        val nextState = state.copy(
            player = p.copy(
                gold = p.gold - tool.forgeGoldCost,
                ironOre = p.ironOre - tool.forgeIronCost,
                emberShards = p.emberShards - tool.forgeShardsCost,
                ownedToolIds = p.ownedToolIds + toolId,
                equippedToolIds = nextEquipped,
            ),
            recentLog = (listOf("Forged & equipped ${tool.icon} ${tool.name} on Toolbelt!") + state.recentLog).take(30),
        )
        return Result.success(SparkpediaEngine.syncCodexUnlocks(nextState))
    }

    /**
     * Saves the hero's current Weapon, Armor, Attuned Spells, and Equipped Tools into preset slot [slotIndex] (0..2).
     */
    fun saveGearSet(state: GameState, slotIndex: Int, name: String): Result<GameState> {
        if (slotIndex !in 0..2) {
            return Result.failure(IllegalArgumentException("Gear set slot must be 0..2."))
        }
        val p = state.player
        val preset = GearSetPreset(
            slotIndex = slotIndex,
            name = name.ifBlank { "Set ${slotIndex + 1}" },
            weaponId = p.equippedWeaponId,
            armor = p.equippedArmor,
            attunedSpellIds = p.attunedSpellIds,
            equippedToolIds = p.equippedToolIds,
        )
        val updatedSets = (p.gearSets.filterNot { it.slotIndex == slotIndex } + preset).sortedBy { it.slotIndex }
        return Result.success(
            state.copy(
                player = p.copy(gearSets = updatedSets),
                recentLog = (listOf("Saved Gear Set '${preset.name}' (Slot ${slotIndex + 1}).") + state.recentLog).take(30),
            ),
        )
    }

    /**
     * 1-tap equips all owned items saved in Gear Set [slotIndex].
     */
    fun equipGearSet(state: GameState, slotIndex: Int): Result<GameState> {
        val p = state.player
        val preset = p.gearSets.firstOrNull { it.slotIndex == slotIndex }
            ?: return Result.failure(IllegalStateException("Gear Set slot ${slotIndex + 1} is empty. Save your current loadout first."))

        val weapon = if (preset.weaponId in p.ownedWeaponIds) preset.weaponId else p.equippedWeaponId
        val armor = preset.armor.filterValues { it in p.ownedArmorIds }
        val spells = preset.attunedSpellIds.filter { it in p.unlockedSpellIds }.take(3)
        val tools = preset.equippedToolIds.filter { it in p.ownedToolIds }.take(p.maxToolbeltSlots())

        return Result.success(
            state.copy(
                player = p.copy(
                    equippedWeaponId = weapon,
                    equippedArmor = armor,
                    attunedSpellIds = spells,
                    equippedToolIds = tools,
                ),
                recentLog = (listOf("Swapped to Gear Set '${preset.name}'!") + state.recentLog).take(30),
            ),
        )
    }
}
