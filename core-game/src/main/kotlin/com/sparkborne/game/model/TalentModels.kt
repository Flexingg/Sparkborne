package com.sparkborne.game.model

import kotlinx.serialization.Serializable

/** The three talent trees. Each splits into two exclusive paths with exclusive capstones. */
@Serializable
enum class TalentTree(val displayName: String, val icon: String, val tagline: String) {
    WARPATH("Warpath", "⚔", "Steel, blood and the rhythm of battle."),
    WAYFARER("Wayfarer", "🥾", "Every road walked makes the next one shorter."),
    ARCANA("Arcana", "✦", "Mana, sigils and the bond with your beast."),
}

/** Passive, numeric bonuses granted per rank. */
@Serializable
enum class TalentStat(val label: String, val isPercent: Boolean) {
    ATTACK_PCT("Attack", true),
    MAX_HP("Max HP", false),
    DEFENSE("Defense", false),
    SPELL_PCT("Spell Power", true),
    MAX_MANA("Max Mana", false),
    BEAST_PCT("Beast Power", true),
    WORK_EFF_PCT("Work Efficiency", true),
    YIELD_PCT("Bonus Yield", true),
    GOLD_PCT("Gold Yield", true),
    STEP_BANK_PCT("Step Bank Capacity", true),
    HERO_XP_PCT("Hero XP", true),
    SKILL_XP_PCT("Skill XP", true),
    EGG_PCT("Egg Incubation", true),
}

/** Behaviour-changing abilities unlocked by path and capstone talents. */
@Serializable
enum class TalentAbility(val displayName: String, val description: String) {
    BLOODTHIRST("Bloodthirst", "Your Strikes heal you for 20% of the damage they deal."),
    CLEAVE_RHYTHM("Cleave Rhythm", "Every 3rd turn of a fight, your Strike deals +60% damage."),
    UNBREAKABLE("Unbreakable", "Once per fight, a killing blow leaves you standing at 1 HP."),
    EXECUTIONER("Executioner", "Strikes deal double damage to enemies below 30% HP."),
    PATHFINDER("Pathfinder", "Roads between towns take 20% fewer steps."),
    PROSPECTOR("Prospector", "Every activity cycle yields 1 extra crafting material."),
    FORTUNES_ROAD("Fortune's Road", "+25% gold from activities and cleared delves."),
    MASTER_ARTISAN("Master Artisan", "Every brew you craft yields 1 extra."),
    MANA_FONT("Mana Font", "Regain 3 Mana at the end of every combat turn."),
    BEASTBOND("Beastbond", "Your companion's skill recharges in 2 turns instead of 3."),
    ARCANE_OVERFLOW("Arcane Overflow", "Spells refund 40% of their Mana cost."),
    PACK_ALPHA("Pack Alpha", "Companion skills deal +50% damage and grant you 8 Shield."),
}

/**
 * A node in a talent tree, placed on a 3-column grid ([column] 0..2) at [tier] 0..4.
 * - [requires]: another node that must have at least one rank first.
 * - [exclusiveGroup]: only one node per group may ever be learned (a path choice) until reset.
 */
data class TalentNode(
    val id: String,
    val tree: TalentTree,
    val name: String,
    val icon: String,
    val tier: Int,
    val column: Int,
    val maxRank: Int = 1,
    val perRank: Map<TalentStat, Int> = emptyMap(),
    val ability: TalentAbility? = null,
    val requires: String? = null,
    val exclusiveGroup: String? = null,
    val flavor: String = "",
)

/** The player's learned talents. */
@Serializable
data class TalentState(
    val ranks: Map<String, Int> = emptyMap(),
    val resets: Int = 0,
)
