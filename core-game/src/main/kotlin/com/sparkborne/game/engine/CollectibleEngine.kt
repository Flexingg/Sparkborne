package com.sparkborne.game.engine

import com.sparkborne.game.model.CollectibleBonusType
import com.sparkborne.game.model.CollectibleDefinition
import com.sparkborne.game.model.GameState

/**
 * Deterministic WalkScape-style Rare Activity Collectibles & Museum Engine.
 * Each collectible is tied to a specific Town Activity and grants a permanent
 * account-wide passive bonus once discovered.
 */
object CollectibleEngine {

    val catalog: List<CollectibleDefinition> = listOf(
        CollectibleDefinition(
            id = "col_basalt_heartstone",
            name = "Basalt Heartstone",
            icon = "🪨",
            activityId = "act_ember_mining",
            regionName = "Emberhearth Outpost",
            lore = "A warm volcanic core pulsing with iron-rich veins.",
            bonusType = CollectibleBonusType.IRON_SHARD_YIELD_PERCENT,
            bonusValue = 4,
            unlockCompletions = 4,
        ),
        CollectibleDefinition(
            id = "col_forge_ember_seal",
            name = "Bram's Ember Seal",
            icon = "🔥",
            activityId = "act_ember_bellows",
            regionName = "Emberhearth Outpost",
            lore = "An ancient guild stamp signifying masterwork forge-work.",
            bonusType = CollectibleBonusType.GOLD_YIELD_PERCENT,
            bonusValue = 4,
            unlockCompletions = 4,
        ),
        CollectibleDefinition(
            id = "col_moonleaf_crown",
            name = "Moonleaf Wreath",
            icon = "🌿",
            activityId = "act_verdant_foraging",
            regionName = "Verdant Crossing",
            lore = "Fragrant silver leaves that keep incubating monster eggs warm.",
            bonusType = CollectibleBonusType.EGG_INCUBATION_PERCENT,
            bonusValue = 5,
            unlockCompletions = 4,
        ),
        CollectibleDefinition(
            id = "col_ironwood_compass",
            name = "Ironwood Compass",
            icon = "🧭",
            activityId = "act_verdant_logging",
            regionName = "Verdant Crossing",
            lore = "Carved from heartwood that always points toward fresh seams.",
            bonusType = CollectibleBonusType.WORK_EFFICIENCY_PERCENT,
            bonusValue = 4,
            unlockCompletions = 4,
        ),
        CollectibleDefinition(
            id = "col_silverfin_lure",
            name = "Gilded Silverfin Lure",
            icon = "🎣",
            activityId = "act_tide_fishing",
            regionName = "Tidebreak Harbor",
            lore = "Prized by harbor captains for attracting rare silverfin.",
            bonusType = CollectibleBonusType.GOLD_YIELD_PERCENT,
            bonusValue = 5,
            unlockCompletions = 4,
        ),
        CollectibleDefinition(
            id = "col_abyssal_pearl",
            name = "Abyssal Pearl Lens",
            icon = "🦪",
            activityId = "act_tide_pearl",
            regionName = "Tidebreak Harbor",
            lore = "Refracts tide-dew into pure restorative clarity.",
            bonusType = CollectibleBonusType.EGG_INCUBATION_PERCENT,
            bonusValue = 5,
            unlockCompletions = 4,
        ),
        CollectibleDefinition(
            id = "col_spore_vial",
            name = "Luminous Spore Vial",
            icon = "🧪",
            activityId = "act_mire_alchemy",
            regionName = "Mirefen Village",
            lore = "Chartreuse mire spores that sharpen battle instincts.",
            bonusType = CollectibleBonusType.COMBAT_POWER_PERCENT,
            bonusValue = 4,
            unlockCompletions = 4,
        ),
        CollectibleDefinition(
            id = "col_volt_capacitor",
            name = "Stormspire Capacitor",
            icon = "⚡",
            activityId = "act_storm_conduit",
            regionName = "Stormspire Citadel",
            lore = "Stores crackles of alpine lightning inside crystal glass.",
            bonusType = CollectibleBonusType.COMBAT_POWER_PERCENT,
            bonusValue = 5,
            unlockCompletions = 5,
        ),
        CollectibleDefinition(
            id = "col_caldera_core",
            name = "Obsidian Caldera Core",
            icon = "🌋",
            activityId = "act_crag_excavation",
            regionName = "Obsidian Crag",
            lore = "Dense star-iron fused inside the volcanic caldera.",
            bonusType = CollectibleBonusType.IRON_SHARD_YIELD_PERCENT,
            bonusValue = 6,
            unlockCompletions = 5,
        ),
        CollectibleDefinition(
            id = "col_glacial_pocketwatch",
            name = "Glacial Pocket Watch",
            icon = "⏱",
            activityId = "act_frost_quarry",
            regionName = "Frostpeak Bastion",
            lore = "Keeps flawless alpine rhythm, speeding up every stride.",
            bonusType = CollectibleBonusType.WORK_EFFICIENCY_PERCENT,
            bonusValue = 6,
            unlockCompletions = 5,
        ),
        CollectibleDefinition(
            id = "col_fossilized_sunstone",
            name = "Fossilized Sunstone",
            icon = "☀",
            activityId = "act_sun_excavate",
            regionName = "Sunspire Oasis",
            lore = "Radiates ancient merchant luck across the golden dunes.",
            bonusType = CollectibleBonusType.GOLD_YIELD_PERCENT,
            bonusValue = 6,
            unlockCompletions = 5,
        ),
        CollectibleDefinition(
            id = "col_starforged_astrolabe",
            name = "Starforged Astrolabe",
            icon = "🌌",
            activityId = "act_ashen_starforge",
            regionName = "Ashen Sanctum",
            lore = "Maps the astral rifts to channel devastating mythic power.",
            bonusType = CollectibleBonusType.COMBAT_POWER_PERCENT,
            bonusValue = 6,
            unlockCompletions = 6,
        ),
    )

    fun byId(id: String): CollectibleDefinition? = catalog.firstOrNull { it.id == id }

    fun totalBonus(state: GameState, type: CollectibleBonusType): Int {
        val owned = state.player.discoveredCollectibleIds
        return catalog.filter { it.id in owned && it.bonusType == type }.sumOf { it.bonusValue }
    }

    /**
     * Checks if completing [activityId] unlocked any new Rare Collectibles.
     */
    fun checkAndAwardCollectibles(
        state: GameState,
        activityId: String,
        lifetimeCompletions: Int,
        events: MutableList<String>,
    ): GameState {
        val matching = catalog.filter {
            it.activityId == activityId &&
                it.id !in state.player.discoveredCollectibleIds &&
                lifetimeCompletions >= it.unlockCompletions
        }
        if (matching.isEmpty()) return state

        val newIds = matching.map { it.id }.toSet()
        matching.forEach { col ->
            events.add("🏆 RARE COLLECTIBLE FOUND: ${col.icon} ${col.name} (+${col.bonusValue}% ${col.bonusType.displayName})!")
        }

        return state.copy(
            player = state.player.copy(
                discoveredCollectibleIds = state.player.discoveredCollectibleIds + newIds,
            ),
        )
    }
}
