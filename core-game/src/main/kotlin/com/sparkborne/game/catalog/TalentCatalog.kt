package com.sparkborne.game.catalog

import com.sparkborne.game.model.TalentAbility
import com.sparkborne.game.model.TalentNode
import com.sparkborne.game.model.TalentStat
import com.sparkborne.game.model.TalentTree

/**
 * Talent trees. Each tree:
 * - tier 0: a shared root
 * - tier 1: two branches (left / right)
 * - tier 2: a PATH CHOICE — left and right are mutually exclusive abilities
 * - tier 3: path-locked passives
 * - tier 4: a CAPSTONE CHOICE — mutually exclusive, each locked to its path
 */
object TalentCatalog {

    const val SHRINE_LOCATION_ID = "loc_stormspire_citadel"
    const val SHRINE_NAME = "Shrine of Unbinding"

    val nodes: List<TalentNode> = listOf(
        // ---------------- WARPATH ----------------
        TalentNode(
            id = "war_tempered", tree = TalentTree.WARPATH, name = "Tempered Strikes", icon = "🗡",
            tier = 0, column = 1, maxRank = 3, perRank = mapOf(TalentStat.ATTACK_PCT to 4),
            flavor = "The first lesson of the blade: hit harder.",
        ),
        TalentNode(
            id = "war_hide", tree = TalentTree.WARPATH, name = "Thick Hide", icon = "🛡",
            tier = 1, column = 0, maxRank = 3, perRank = mapOf(TalentStat.MAX_HP to 12),
            requires = "war_tempered", flavor = "Scars become armour.",
        ),
        TalentNode(
            id = "war_edge", tree = TalentTree.WARPATH, name = "Honed Edge", icon = "⚔",
            tier = 1, column = 2, maxRank = 3, perRank = mapOf(TalentStat.ATTACK_PCT to 4),
            requires = "war_tempered", flavor = "A whetstone in every pocket.",
        ),
        TalentNode(
            id = "war_bloodthirst", tree = TalentTree.WARPATH, name = "Bloodthirst", icon = "🩸",
            tier = 2, column = 0, ability = TalentAbility.BLOODTHIRST,
            requires = "war_hide", exclusiveGroup = "war_path", flavor = "Path of the Bulwark.",
        ),
        TalentNode(
            id = "war_cleave", tree = TalentTree.WARPATH, name = "Cleave Rhythm", icon = "🌀",
            tier = 2, column = 2, ability = TalentAbility.CLEAVE_RHYTHM,
            requires = "war_edge", exclusiveGroup = "war_path", flavor = "Path of the Reaver.",
        ),
        TalentNode(
            id = "war_ironskin", tree = TalentTree.WARPATH, name = "Iron Skin", icon = "⛨",
            tier = 3, column = 0, maxRank = 2, perRank = mapOf(TalentStat.DEFENSE to 3),
            requires = "war_bloodthirst",
        ),
        TalentNode(
            id = "war_fervor", tree = TalentTree.WARPATH, name = "Battle Fervor", icon = "🔥",
            tier = 3, column = 2, maxRank = 2, perRank = mapOf(TalentStat.ATTACK_PCT to 6),
            requires = "war_cleave",
        ),
        TalentNode(
            id = "war_unbreakable", tree = TalentTree.WARPATH, name = "Unbreakable", icon = "🗿",
            tier = 4, column = 0, ability = TalentAbility.UNBREAKABLE, perRank = mapOf(TalentStat.MAX_HP to 20),
            requires = "war_ironskin", exclusiveGroup = "war_capstone", flavor = "Bulwark capstone.",
        ),
        TalentNode(
            id = "war_executioner", tree = TalentTree.WARPATH, name = "Executioner", icon = "🪓",
            tier = 4, column = 2, ability = TalentAbility.EXECUTIONER,
            requires = "war_fervor", exclusiveGroup = "war_capstone", flavor = "Reaver capstone.",
        ),

        // ---------------- WAYFARER ----------------
        TalentNode(
            id = "way_trailborn", tree = TalentTree.WAYFARER, name = "Trailborn", icon = "👣",
            tier = 0, column = 1, maxRank = 3, perRank = mapOf(TalentStat.WORK_EFF_PCT to 3),
            flavor = "Born on the road, never lost on it.",
        ),
        TalentNode(
            id = "way_strider", tree = TalentTree.WAYFARER, name = "Long Strider", icon = "🎒",
            tier = 1, column = 0, maxRank = 2, perRank = mapOf(TalentStat.STEP_BANK_PCT to 10),
            requires = "way_trailborn", flavor = "Pack light, walk far.",
        ),
        TalentNode(
            id = "way_keeneye", tree = TalentTree.WAYFARER, name = "Keen Eye", icon = "👁",
            tier = 1, column = 2, maxRank = 3, perRank = mapOf(TalentStat.YIELD_PCT to 5),
            requires = "way_trailborn", flavor = "Notice the glint others miss.",
        ),
        TalentNode(
            id = "way_pathfinder", tree = TalentTree.WAYFARER, name = "Pathfinder", icon = "🧭",
            tier = 2, column = 0, ability = TalentAbility.PATHFINDER,
            requires = "way_strider", exclusiveGroup = "way_path", flavor = "Path of the Wanderer.",
        ),
        TalentNode(
            id = "way_prospector", tree = TalentTree.WAYFARER, name = "Prospector", icon = "⛏",
            tier = 2, column = 2, ability = TalentAbility.PROSPECTOR,
            requires = "way_keeneye", exclusiveGroup = "way_path", flavor = "Path of the Gatherer.",
        ),
        TalentNode(
            id = "way_seasoned", tree = TalentTree.WAYFARER, name = "Seasoned Walker", icon = "🌄",
            tier = 3, column = 0, maxRank = 2, perRank = mapOf(TalentStat.HERO_XP_PCT to 8),
            requires = "way_pathfinder",
        ),
        TalentNode(
            id = "way_quickstudy", tree = TalentTree.WAYFARER, name = "Quick Study", icon = "📜",
            tier = 3, column = 2, maxRank = 2, perRank = mapOf(TalentStat.SKILL_XP_PCT to 10),
            requires = "way_prospector",
        ),
        TalentNode(
            id = "way_fortune", tree = TalentTree.WAYFARER, name = "Fortune's Road", icon = "💰",
            tier = 4, column = 0, ability = TalentAbility.FORTUNES_ROAD, perRank = mapOf(TalentStat.GOLD_PCT to 25),
            requires = "way_seasoned", exclusiveGroup = "way_capstone", flavor = "Wanderer capstone.",
        ),
        TalentNode(
            id = "way_artisan", tree = TalentTree.WAYFARER, name = "Master Artisan", icon = "⚗",
            tier = 4, column = 2, ability = TalentAbility.MASTER_ARTISAN,
            requires = "way_quickstudy", exclusiveGroup = "way_capstone", flavor = "Gatherer capstone.",
        ),

        // ---------------- ARCANA ----------------
        TalentNode(
            id = "arc_kindled", tree = TalentTree.ARCANA, name = "Kindled Mind", icon = "🕯",
            tier = 0, column = 1, maxRank = 3, perRank = mapOf(TalentStat.SPELL_PCT to 5),
            flavor = "A spark behind the eyes.",
        ),
        TalentNode(
            id = "arc_deepwell", tree = TalentTree.ARCANA, name = "Deep Well", icon = "💧",
            tier = 1, column = 0, maxRank = 3, perRank = mapOf(TalentStat.MAX_MANA to 6),
            requires = "arc_kindled", flavor = "Draw from a deeper reservoir.",
        ),
        TalentNode(
            id = "arc_whisperer", tree = TalentTree.ARCANA, name = "Beast Whisperer", icon = "🐾",
            tier = 1, column = 2, maxRank = 3, perRank = mapOf(TalentStat.BEAST_PCT to 8),
            requires = "arc_kindled", flavor = "They hear you before you speak.",
        ),
        TalentNode(
            id = "arc_manafont", tree = TalentTree.ARCANA, name = "Mana Font", icon = "⛲",
            tier = 2, column = 0, ability = TalentAbility.MANA_FONT,
            requires = "arc_deepwell", exclusiveGroup = "arc_path", flavor = "Path of the Sorcerer.",
        ),
        TalentNode(
            id = "arc_beastbond", tree = TalentTree.ARCANA, name = "Beastbond", icon = "🤝",
            tier = 2, column = 2, ability = TalentAbility.BEASTBOND,
            requires = "arc_whisperer", exclusiveGroup = "arc_path", flavor = "Path of the Tamer.",
        ),
        TalentNode(
            id = "arc_weaver", tree = TalentTree.ARCANA, name = "Spell Weaver", icon = "🕸",
            tier = 3, column = 0, maxRank = 2, perRank = mapOf(TalentStat.SPELL_PCT to 6),
            requires = "arc_manafont",
        ),
        TalentNode(
            id = "arc_nurturer", tree = TalentTree.ARCANA, name = "Nurturer", icon = "🥚",
            tier = 3, column = 2, maxRank = 2, perRank = mapOf(TalentStat.EGG_PCT to 15),
            requires = "arc_beastbond",
        ),
        TalentNode(
            id = "arc_overflow", tree = TalentTree.ARCANA, name = "Arcane Overflow", icon = "🌌",
            tier = 4, column = 0, ability = TalentAbility.ARCANE_OVERFLOW,
            requires = "arc_weaver", exclusiveGroup = "arc_capstone", flavor = "Sorcerer capstone.",
        ),
        TalentNode(
            id = "arc_alpha", tree = TalentTree.ARCANA, name = "Pack Alpha", icon = "🐺",
            tier = 4, column = 2, ability = TalentAbility.PACK_ALPHA, perRank = mapOf(TalentStat.BEAST_PCT to 10),
            requires = "arc_nurturer", exclusiveGroup = "arc_capstone", flavor = "Tamer capstone.",
        ),
    )

    fun nodeById(id: String): TalentNode? = nodes.firstOrNull { it.id == id }

    fun nodesIn(tree: TalentTree): List<TalentNode> = nodes.filter { it.tree == tree }
}
