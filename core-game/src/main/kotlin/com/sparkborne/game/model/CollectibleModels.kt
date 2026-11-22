package com.sparkborne.game.model

import kotlinx.serialization.Serializable

@Serializable
enum class CollectibleBonusType(val displayName: String, val shortLabel: String) {
    WORK_EFFICIENCY_PERCENT("Work Efficiency", "+% Eff"),
    GOLD_YIELD_PERCENT("Gold Yield", "+% Gold"),
    IRON_SHARD_YIELD_PERCENT("Iron & Shards", "+% Ore/Shards"),
    EGG_INCUBATION_PERCENT("Egg Incubation", "+% Egg Speed"),
    COMBAT_POWER_PERCENT("Combat Power", "+% ATK/Spell"),
}

@Serializable
data class CollectibleDefinition(
    val id: String,
    val name: String,
    val icon: String,
    val activityId: String,
    val regionName: String,
    val lore: String,
    val bonusType: CollectibleBonusType,
    val bonusValue: Int,
    val unlockCompletions: Int,
)
