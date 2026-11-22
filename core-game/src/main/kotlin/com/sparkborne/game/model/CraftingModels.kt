package com.sparkborne.game.model

import kotlinx.serialization.Serializable

@Serializable
enum class MaterialType(
    val displayName: String,
    val icon: String,
    val sourceHint: String,
) {
    BASALT_ORE("Basalt Ore", "🪨", "Mining, Smithing & Salvage"),
    MOONLEAF_HERB("Moonleaf Herb", "🌿", "Foraging & Woodcutting"),
    SILVERFIN_FISH("Silverfin Fish", "🐟", "Fishing & Diving"),
    MIRE_SPORE("Mire Spore", "🍄", "Alchemy"),
    SUNSTONE_DUST("Sunstone Dust", "✨", "Archaeology & Trade"),
    ASTRAL_ESSENCE("Astral Essence", "🔮", "Runecrafting"),
}

@Serializable
enum class MasterworkTier(
    val displayName: String,
    val minSkillLevel: Int,
    val potencyMultiplier: Double,
    val bonusCraftQty: Int,
) {
    STANDARD("Standard", 1, 1.0, 0),
    FINE("Fine", 5, 1.15, 0),
    SUPERIOR("Superior", 12, 1.30, 0),
    MASTERWORK("Masterwork", 25, 1.50, 1);

    companion object {
        fun forLevel(level: Int): MasterworkTier =
            entries.lastOrNull { level >= it.minSkillLevel } ?: STANDARD
    }
}

@Serializable
enum class ConsumableEffectType {
    STEP_EFFICIENCY_BOOST,
    DELVE_HEAL_HP,
    DELVE_RESTORE_MANA,
    EGG_INCUBATION_SURGE,
    DELVE_FULL_RESTORE_SHIELD,
}

@Serializable
data class CraftingRecipe(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val craftSkill: SkillType,
    val requiredMaterials: Map<MaterialType, Int>,
    val goldCost: Int = 0,
    val rewardCraftXp: Int = 25,
    val effectType: ConsumableEffectType,
    val basePotency: Int,
)
