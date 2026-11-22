package com.sparkborne.game.model

import kotlinx.serialization.Serializable

/**
 * WalkScape-inspired Skilling Tool equipped on the Hero's Toolbelt.
 * Grants real deterministic attributes ([workEfficiencyBonusPercent] and [bonusYieldPercent])
 * to matching [targetSkills] (or all skills when [targetSkills] is empty).
 */
@Serializable
data class ToolItem(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val targetSkills: Set<SkillType> = emptySet(),
    val workEfficiencyBonusPercent: Int = 0,
    val bonusYieldPercent: Int = 0,
    val forgeGoldCost: Int = 0,
    val forgeIronCost: Int = 0,
    val forgeShardsCost: Int = 0,
) {
    fun appliesTo(skill: SkillType): Boolean = targetSkills.isEmpty() || skill in targetSkills
}

/**
 * Saved Gear Set Preset for 1-tap swapping between Combat, Skilling, and Boss loadouts.
 */
@Serializable
data class GearSetPreset(
    val slotIndex: Int,
    val name: String,
    val weaponId: String,
    val armor: Map<ArmorSlot, String> = emptyMap(),
    val attunedSpellIds: List<String> = emptyList(),
    val equippedToolIds: List<String> = emptyList(),
)
