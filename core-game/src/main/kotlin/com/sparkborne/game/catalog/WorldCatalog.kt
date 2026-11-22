package com.sparkborne.game.catalog

import com.sparkborne.game.model.RegionElement
import com.sparkborne.game.model.ShopItemType
import com.sparkborne.game.model.TownActivity
import com.sparkborne.game.model.TownShopListing
import com.sparkborne.game.model.TownShopkeeper
import com.sparkborne.game.model.TravelRoute
import com.sparkborne.game.model.WorldLocation

/**
 * Deterministic World Map of Sparkborne: 6 elemental cities & towns connected by step-trails,
 * each with its own shopkeeper (portrait & lore), repeatable WalkScape activities, regional shops, and local contracts.
 */
object WorldCatalog {

    const val START_LOCATION_ID = "loc_emberhearth"

    val locations: List<WorldLocation> = listOf(
        WorldLocation(
            id = "loc_emberhearth",
            name = "Emberhearth Outpost",
            subtitle = "Starting Forge Town · Safe Haven",
            element = RegionElement.FIRE,
            mapX = 0.22f,
            mapY = 0.78f,
            shopkeeper = TownShopkeeper(
                id = "shopkeeper_ember",
                name = "Bram Ironbrow",
                title = "Master Forgekeeper",
                loreQuote = "\"Steel remembers the sweat that tempered it. Bring me iron and coin, and I'll arm you for the wilds.\"",
            ),
            routes = listOf(
                TravelRoute("loc_verdant_crossing", 3500),
                TravelRoute("loc_tidebreak_harbor", 4500),
            ),
            contractIds = listOf("exp_daily_ashlands_patrol"),
            activities = listOf(
                TownActivity(
                    id = "act_ember_mining",
                    locationId = "loc_emberhearth",
                    name = "Mine Basalt Seam",
                    skillTag = "Mining",
                    description = "Chip raw iron ore from warm volcanic outcrops along the ridge.",
                    stepsPerAction = 1000,
                    rewardGold = 6,
                    rewardIron = 4,
                    rewardXp = 14,
                ),
                TownActivity(
                    id = "act_ember_bellows",
                    locationId = "loc_emberhearth",
                    name = "Tend Forge Bellows",
                    skillTag = "Smithing",
                    description = "Stoke Bram's outpost furnace for steady gold and ember dust.",
                    stepsPerAction = 1200,
                    rewardGold = 12,
                    rewardShards = 1,
                    rewardXp = 16,
                ),
            ),
            shopListings = listOf(
                TownShopListing(
                    id = "shop_ember_helm",
                    locationId = "loc_emberhearth",
                    title = "Ironbark Helm",
                    subtitle = "DEF +4 · HP +12 · Mana +4",
                    type = ShopItemType.ARMOR,
                    targetItemId = "arm_iron_helm",
                    goldCost = 40,
                    ironCost = 8,
                ),
                TownShopListing(
                    id = "shop_ember_spear",
                    locationId = "loc_emberhearth",
                    title = "Ashwood Spear",
                    subtitle = "Scales w/ Quads, Hamstrings, Glutes, Calves",
                    type = ShopItemType.WEAPON,
                    targetItemId = "wpn_ash_spear",
                    goldCost = 45,
                    ironCost = 8,
                ),
                TownShopListing(
                    id = "shop_ember_egg",
                    locationId = "loc_emberhearth",
                    title = "Cinder Nest Egg",
                    subtitle = "Incubates with Sleep & Hydration",
                    type = ShopItemType.EGG,
                    targetItemId = "egg_shop_cinder",
                    goldCost = 65,
                    shardsCost = 4,
                ),
            ),
        ),
        WorldLocation(
            id = "loc_verdant_crossing",
            name = "Verdant Crossing",
            subtitle = "Canopy Trade Town · Herbalists & Rangers",
            element = RegionElement.PLANT,
            mapX = 0.28f,
            mapY = 0.44f,
            shopkeeper = TownShopkeeper(
                id = "shopkeeper_verdant",
                name = "Sylva Briarwood",
                title = "Canopy Herbalist",
                loreQuote = "\"Walk softly beneath the ironwoods, traveler — the old roots trade rare favors for honest footsteps.\"",
            ),
            routes = listOf(
                TravelRoute("loc_emberhearth", 3500),
                TravelRoute("loc_mirefen_village", 4000),
                TravelRoute("loc_stormspire_citadel", 5500),
            ),
            contractIds = listOf("exp_daily_ashlands_patrol", "exp_daily_clarity_vigil"),
            activities = listOf(
                TownActivity(
                    id = "act_verdant_foraging",
                    locationId = "loc_verdant_crossing",
                    name = "Forage Moonleaf Herbs",
                    skillTag = "Foraging",
                    description = "Gather restorative canopy flora while walking mossy forest trails.",
                    stepsPerAction = 900,
                    rewardGold = 10,
                    rewardShards = 1,
                    rewardXp = 14,
                    rewardMonsterXp = 10,
                ),
                TownActivity(
                    id = "act_verdant_logging",
                    locationId = "loc_verdant_crossing",
                    name = "Harvest Ironwood",
                    skillTag = "Woodcutting",
                    description = "Fell dense ironwood boughs prized by rangers and smiths alike.",
                    stepsPerAction = 1100,
                    rewardGold = 14,
                    rewardIron = 3,
                    rewardXp = 16,
                ),
            ),
            shopListings = listOf(
                TownShopListing(
                    id = "shop_verdant_greaves",
                    locationId = "loc_verdant_crossing",
                    title = "Strider Greaves",
                    subtitle = "DEF +6 · HP +20 · Mana +6",
                    type = ShopItemType.ARMOR,
                    targetItemId = "arm_strider_greaves",
                    goldCost = 70,
                    ironCost = 14,
                    shardsCost = 3,
                ),
                TownShopListing(
                    id = "shop_verdant_blades",
                    locationId = "loc_verdant_crossing",
                    title = "Iron Twin Blades",
                    subtitle = "Scales w/ Biceps, Triceps, Forearms, Core",
                    type = ShopItemType.WEAPON,
                    targetItemId = "wpn_twin_fangs",
                    goldCost = 50,
                    ironCost = 10,
                ),
            ),
        ),
        WorldLocation(
            id = "loc_tidebreak_harbor",
            name = "Tidebreak Harbor",
            subtitle = "Coastal Port City · Tidecallers & Anglers",
            element = RegionElement.WATER,
            mapX = 0.72f,
            mapY = 0.80f,
            shopkeeper = TownShopkeeper(
                id = "shopkeeper_tidebreak",
                name = "Capt. Orin Saltwake",
                title = "Harbor Quartermaster",
                loreQuote = "\"Tides rise, tides fall. Keep your flask full, your compass true, and your boots moving.\"",
            ),
            routes = listOf(
                TravelRoute("loc_emberhearth", 4500),
                TravelRoute("loc_mirefen_village", 5000),
            ),
            contractIds = listOf("exp_daily_clarity_vigil"),
            activities = listOf(
                TownActivity(
                    id = "act_tide_fishing",
                    locationId = "loc_tidebreak_harbor",
                    name = "Deep-Water Netting",
                    skillTag = "Fishing",
                    description = "Haul silverfin along the piers to earn coin and feed your beast.",
                    stepsPerAction = 1000,
                    rewardGold = 15,
                    rewardXp = 15,
                    rewardMonsterXp = 12,
                ),
                TownActivity(
                    id = "act_tide_pearl",
                    locationId = "loc_tidebreak_harbor",
                    name = "Pearl Reef Diving",
                    skillTag = "Diving",
                    description = "Search tidepools for arcane shards and sunken ship iron.",
                    stepsPerAction = 1400,
                    rewardGold = 10,
                    rewardShards = 3,
                    rewardIron = 2,
                    rewardXp = 20,
                ),
            ),
            shopListings = listOf(
                TownShopListing(
                    id = "shop_tide_mend",
                    locationId = "loc_tidebreak_harbor",
                    title = "Tome: Tidal Mend",
                    subtitle = "Water Spell · Restores HP in delve combat",
                    type = ShopItemType.SPELL,
                    targetItemId = "spl_tidal_mend",
                    shardsCost = 4,
                ),
                TownShopListing(
                    id = "shop_tide_egg",
                    locationId = "loc_tidebreak_harbor",
                    title = "Abyssal Tide Egg",
                    subtitle = "Rare coastal monster egg",
                    type = ShopItemType.EGG,
                    targetItemId = "egg_shop_tide",
                    goldCost = 75,
                    shardsCost = 5,
                ),
            ),
        ),
        WorldLocation(
            id = "loc_mirefen_village",
            name = "Mirefen Village",
            subtitle = "Alchemist Bog Outpost · Venom & Tonics",
            element = RegionElement.POISON,
            mapX = 0.68f,
            mapY = 0.48f,
            shopkeeper = TownShopkeeper(
                id = "shopkeeper_mirefen",
                name = "Vexis Bogbrew",
                title = "Venom Apothecary",
                loreQuote = "\"One drop of mire-dew sharpens the blade; two drops wake the sleeping egg. Care to barter?\"",
            ),
            routes = listOf(
                TravelRoute("loc_verdant_crossing", 4000),
                TravelRoute("loc_tidebreak_harbor", 5000),
                TravelRoute("loc_obsidian_crag", 6000),
            ),
            contractIds = listOf("exp_daily_iron_gauntlet", "exp_multi_3d_obsidian_crag"),
            activities = listOf(
                TownActivity(
                    id = "act_mire_alchemy",
                    locationId = "loc_mirefen_village",
                    name = "Distill Venom Extract",
                    skillTag = "Alchemy",
                    description = "Refine glowing bog spores into potent shards and coin.",
                    stepsPerAction = 1200,
                    rewardGold = 12,
                    rewardShards = 3,
                    rewardXp = 18,
                ),
                TownActivity(
                    id = "act_mire_dredge",
                    locationId = "loc_mirefen_village",
                    name = "Dredge Sunken Relics",
                    skillTag = "Salvage",
                    description = "Pull ancient iron plates out of the peat bogs.",
                    stepsPerAction = 1300,
                    rewardGold = 14,
                    rewardIron = 6,
                    rewardXp = 19,
                ),
            ),
            shopListings = listOf(
                TownShopListing(
                    id = "shop_mire_aegis",
                    locationId = "loc_mirefen_village",
                    title = "Tome: Iron Aegis",
                    subtitle = "Defensive Spell · Strike + Shield barrier",
                    type = ShopItemType.SPELL,
                    targetItemId = "spl_iron_aegis",
                    shardsCost = 6,
                ),
                TownShopListing(
                    id = "shop_mire_relic",
                    locationId = "loc_mirefen_village",
                    title = "Emberbound Relic",
                    subtitle = "DEF +3 · Mana +10 · Spell Power +8",
                    type = ShopItemType.ARMOR,
                    targetItemId = "arm_ember_signet",
                    goldCost = 90,
                    ironCost = 6,
                    shardsCost = 10,
                ),
            ),
        ),
        WorldLocation(
            id = "loc_stormspire_citadel",
            name = "Stormspire Citadel",
            subtitle = "High-Peak Lightning City · Arcane Smiths",
            element = RegionElement.LIGHTNING,
            mapX = 0.25f,
            mapY = 0.16f,
            shopkeeper = TownShopkeeper(
                id = "shopkeeper_stormspire",
                name = "Archivist Lyra Volt",
                title = "Conduit Weaver",
                loreQuote = "\"Lightning favors the relentless. Climb the frozen peaks, and the storm will answer your call.\"",
            ),
            routes = listOf(
                TravelRoute("loc_verdant_crossing", 5500),
                TravelRoute("loc_obsidian_crag", 5000),
            ),
            contractIds = listOf("exp_daily_iron_gauntlet", "exp_multi_3d_obsidian_crag"),
            activities = listOf(
                TownActivity(
                    id = "act_storm_conduit",
                    locationId = "loc_stormspire_citadel",
                    name = "Harvest Volt Crystals",
                    skillTag = "Runecrafting",
                    description = "Capture lightning-charged shards along the high spire walkways.",
                    stepsPerAction = 1500,
                    rewardGold = 18,
                    rewardShards = 4,
                    rewardXp = 24,
                ),
            ),
            shopListings = listOf(
                TownShopListing(
                    id = "shop_storm_nova",
                    locationId = "loc_stormspire_citadel",
                    title = "Tome: Tempest Nova",
                    subtitle = "Lightning Spell · Massive arcane burst",
                    type = ShopItemType.SPELL,
                    targetItemId = "spl_tempest_nova",
                    shardsCost = 12,
                ),
                TownShopListing(
                    id = "shop_storm_maul",
                    locationId = "loc_stormspire_citadel",
                    title = "Titanforged Warhammer",
                    subtitle = "Scales w/ Back, Quads, Glutes, Core",
                    type = ShopItemType.WEAPON,
                    targetItemId = "wpn_titan_maul",
                    goldCost = 120,
                    ironCost = 24,
                ),
            ),
        ),
        WorldLocation(
            id = "loc_obsidian_crag",
            name = "Obsidian Crag",
            subtitle = "Endgame Abyssal Fortress · Masterwork Forge",
            element = RegionElement.VOID,
            mapX = 0.74f,
            mapY = 0.18f,
            shopkeeper = TownShopkeeper(
                id = "shopkeeper_obsidian",
                name = "Kaelen the Ash-Forged",
                title = "Vault Warden",
                loreQuote = "\"Only those who carried iron through the dark earn what lies in this vault. Show me your proof.\"",
            ),
            routes = listOf(
                TravelRoute("loc_stormspire_citadel", 5000),
                TravelRoute("loc_mirefen_village", 6000),
            ),
            contractIds = listOf("exp_multi_3d_obsidian_crag", "exp_multi_7d_abyssal_citadel"),
            activities = listOf(
                TownActivity(
                    id = "act_crag_excavation",
                    locationId = "loc_obsidian_crag",
                    name = "Abyssal Seam Excavation",
                    skillTag = "Mining",
                    description = "Extract dense star-iron and void shards from the volcanic caldera.",
                    stepsPerAction = 1600,
                    rewardGold = 24,
                    rewardIron = 8,
                    rewardShards = 4,
                    rewardXp = 30,
                ),
            ),
            shopListings = listOf(
                TownShopListing(
                    id = "shop_crag_plate",
                    locationId = "loc_obsidian_crag",
                    title = "Vanguard Breastplate",
                    subtitle = "DEF +10 · HP +35",
                    type = ShopItemType.ARMOR,
                    targetItemId = "arm_vanguard_plate",
                    goldCost = 110,
                    ironCost = 20,
                    shardsCost = 6,
                ),
                TownShopListing(
                    id = "shop_crag_claymore",
                    locationId = "loc_obsidian_crag",
                    title = "Embersteel Greatsword",
                    subtitle = "Scales w/ Chest, Back, Shoulders",
                    type = ShopItemType.WEAPON,
                    targetItemId = "wpn_ember_claymore",
                    goldCost = 140,
                    ironCost = 28,
                ),
            ),
        ),
    )

    fun locationById(id: String): WorldLocation =
        locations.firstOrNull { it.id == id } ?: locations.first()

    fun activityById(id: String): TownActivity? =
        locations.asSequence().flatMap { it.activities.asSequence() }.firstOrNull { it.id == id }

    fun shopListingById(id: String): TownShopListing? =
        locations.asSequence().flatMap { it.shopListings.asSequence() }.firstOrNull { it.id == id }
}
