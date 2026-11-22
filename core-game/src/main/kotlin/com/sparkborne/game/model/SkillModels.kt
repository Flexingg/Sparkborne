package com.sparkborne.game.model

import kotlinx.serialization.Serializable

/**
 * WalkScape-inspired gathering, crafting, and trade skills (Level 1–99).
 * Training any Town Activity grants XP to its corresponding [SkillType], boosting
 * Work Efficiency (fewer steps needed per action) and awarding Skill Chests.
 */
@Serializable
enum class SkillType(
    val displayName: String,
    val icon: String,
    val description: String,
) {
    MINING("Mining", "⛏", "Extract iron ore, basalt, and glacial crystals from mountain seams."),
    SMITHING("Smithing", "🔨", "Stoke forges and smelt astral ingots into masterwork armaments."),
    FORAGING("Foraging", "🌿", "Gather restorative herbs and rare flora across canopy trails."),
    WOODCUTTING("Woodcutting", "🪓", "Harvest dense ironwood timber for town craftspeople."),
    FISHING("Fishing", "🎣", "Haul silverfin and frost-trout from harbors and glacial lakes."),
    DIVING("Diving", "🫧", "Dive deep reefs for arcane shards and sunken ship iron."),
    ALCHEMY("Alchemy", "🧪", "Distill bog spores and herbs into potent combat tonics and elixirs."),
    SALVAGE("Salvage", "⚓", "Dredge ancient ironwork and forgotten relics from misty bogs."),
    RUNECRAFTING("Runecrafting", "⚡", "Bind alpine lightning and astral rifts into ember shards."),
    ARCHAEOLOGY("Archaeology", "🏺", "Excavate buried sunstones and ancient desert artifacts."),
    TRADE("Trade", "🪙", "Escort frontier caravans for high gold yields and beast training.");

    companion object {
        fun fromTag(tag: String): SkillType = when (tag.trim().lowercase()) {
            "mining" -> MINING
            "smithing" -> SMITHING
            "foraging" -> FORAGING
            "woodcutting" -> WOODCUTTING
            "fishing" -> FISHING
            "diving" -> DIVING
            "alchemy" -> ALCHEMY
            "salvage" -> SALVAGE
            "runecrafting" -> RUNECRAFTING
            "archaeology" -> ARCHAEOLOGY
            "trade" -> TRADE
            else -> MINING
        }
    }
}

data class ChestOpenReward(
    val skill: SkillType,
    val gold: Int,
    val iron: Int,
    val shards: Int,
    val bonusSkillXp: Int,
    val eggAwarded: MonsterEgg? = null,
    val summary: String,
)
