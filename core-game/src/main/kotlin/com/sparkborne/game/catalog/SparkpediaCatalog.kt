package com.sparkborne.game.catalog

import com.sparkborne.game.model.CodexCategory
import com.sparkborne.game.model.CodexEntry

/**
 * WalkScape-inspired "Sparkpedia" — an in-game encyclopedia that unlocks entries
 * as you discover towns, shopkeepers, beasts, gear, activities, and mechanics.
 */
object SparkpediaCatalog {

    val entries: List<CodexEntry> = listOf(
        // --- GUIDES ---
        CodexEntry(
            id = "guide_welcome",
            category = CodexCategory.GUIDES,
            title = "Welcome to Sparkborne",
            subtitle = "Real-World Telemetry Roguelike",
            summary = "Your real-world steps, lifting, sleep, hydration, macros, and bodyweight trends power every mechanic deterministically — no AI randomness.",
            mechanicsDetail = "You begin at Level 0 with no pets and no quests completed. Syncing historical SparkyFitness data seeds your baseline muscle strength and recovery buffs, while all quests, levels, gear, and monster eggs are earned through play.",
            unlockHint = "Unlocked from the start.",
        ),
        CodexEntry(
            id = "guide_telemetry_map",
            category = CodexCategory.GUIDES,
            title = "How Telemetry Maps to Power",
            subtitle = "Steps · Lifting · Sleep · Water · Macros",
            summary = "Every SparkyFitness metric fuels a distinct pillar of your character and companion.",
            mechanicsDetail = "• Steps → World travel, Town Activities, and Delve node unlocking.\n• Lifting (per muscle group) → Permanent Muscle Strength Ranks that scale matching Weapon Classes.\n• Sleep → Vitality Max HP buff & Egg Incubation Warmth.\n• Hydration → Clarity Max Mana buff & Egg Incubation Dew.\n• Calories & Macros → Fortitude/Spell Power buffs & deterministic Monster Evolution affinity.\n• Bodyweight Trend → Passive Momentum Stance (Juggernaut / Prowler / Equilibrium).",
            unlockHint = "Sync SparkyFitness telemetry.",
        ),
        CodexEntry(
            id = "guide_muscle_scaling",
            category = CodexCategory.GUIDES,
            title = "Muscle Strength & Weapons",
            subtitle = "Lifting Volume → Weapon Damage",
            summary = "Every exercise logged in SparkyFitness credits primary muscles (100%) and secondary muscles (50%) to build permanent Muscle Ranks.",
            mechanicsDetail = "• Greatswords scale with Chest, Back, Shoulders.\n• Warhammers scale with Back, Quads, Glutes, Core.\n• Spears scale with Quads, Hamstrings, Glutes, Calves.\n• Twin Blades scale with Biceps, Triceps, Forearms, Core.\nRank curve: Rank = floor(sqrt(effectiveKg / 250)) + 1.",
            unlockHint = "Log lifting volume in SparkyFitness.",
        ),
        CodexEntry(
            id = "guide_roguelike_stakes",
            category = CodexCategory.GUIDES,
            title = "Expeditions & Roguelike Stakes",
            subtitle = "Risk, Reward & Unbanked Loot",
            summary = "Expeditions are self-selected 1-day or multi-day contracts with real stakes.",
            mechanicsDetail = "Loot found inside delve nodes stays in your Unbanked Loot Pouch until you defeat the Boss and clear the contract. Wiping in combat, abandoning the run, or missing the contract deadline forfeits ALL unbanked loot, while keeping your persistent hero level, gear, and companions.",
            unlockHint = "Accept your first expedition contract.",
        ),
        CodexEntry(
            id = "guide_eggs_evolution",
            category = CodexCategory.GUIDES,
            title = "Egg Incubation & Evolution",
            subtitle = "Sleep, Hydration & Macro Branches",
            summary = "Monster Eggs hatch through real-world recovery and evolve deterministically based on your nutrition profile.",
            mechanicsDetail = "Sleep minutes fill Sleep Warmth; Water intake fills Hydration Dew. When both reach 100%, the egg hatches into a companion whose Affinity (Ironclad, Stormstrider, Deepforge, or Emberheart) is determined by your macro ratios while incubating!",
            unlockHint = "Claim your first Monster Egg.",
        ),
        CodexEntry(
            id = "guide_stances",
            category = CodexCategory.GUIDES,
            title = "Bodyweight Momentum Stances",
            subtitle = "7-Day & 14-Day Rolling Trends",
            summary = "Your 7-day rolling bodyweight trend automatically activates a passive combat & travel stance.",
            mechanicsDetail = "• Gaining (+0.25kg/wk) → Juggernaut (+12% weapon dmg, +3 DEF).\n• Losing (-0.25kg/wk) → Prowler (+15% step efficiency, +8% spell power).\n• Stable → Equilibrium (+8% weapon dmg, +10% spell power, +2 DEF, +5% steps).",
            unlockHint = "Log bodyweight in SparkyFitness.",
        ),

        // --- WORLD & TOWNS ---
        CodexEntry(
            id = "loc_emberhearth",
            category = CodexCategory.WORLD,
            title = "Emberhearth Outpost",
            subtitle = "Fire Region · Starting Safe Haven",
            summary = "A warm basalt forge-town perched on the southern highlands where all Sparkborne begin their journey.",
            mechanicsDetail = "Home to Bram Ironbrow's forge, Basalt Mining, Forge Bellows, and the Ashlands Patrol & Ember Nest contracts.",
            unlockHint = "Unlocked from the start.",
        ),
        CodexEntry(
            id = "loc_verdant_crossing",
            category = CodexCategory.WORLD,
            title = "Verdant Crossing",
            subtitle = "Plant Region · Canopy Trade Town",
            summary = "A bustling timber town woven beneath ancient ironwood trees, crossroads of the central realm.",
            mechanicsDetail = "Offers Moonleaf Foraging, Ironwood Woodcutting, Strider Greaves, and Iron Twin Blades. Roads lead to Mirefen and Stormspire.",
            unlockHint = "Walk to Verdant Crossing on the World Map.",
        ),
        CodexEntry(
            id = "loc_tidebreak_harbor",
            category = CodexCategory.WORLD,
            title = "Tidebreak Harbor",
            subtitle = "Water Region · Coastal Port City",
            summary = "A sun-bleached harbor town famous for deep-water anglers, pearl divers, and tide-mending magic.",
            mechanicsDetail = "Offers Deep-Water Netting, Pearl Reef Diving, the Tidal Mend spell tome, and Abyssal Tide Eggs.",
            unlockHint = "Walk to Tidebreak Harbor on the World Map.",
        ),
        CodexEntry(
            id = "loc_mirefen_village",
            category = CodexCategory.WORLD,
            title = "Mirefen Village",
            subtitle = "Poison Region · Alchemist Bog Outpost",
            summary = "Stilt-houses illuminated by chartreuse spore lanterns in the mist-choked eastern bogs.",
            mechanicsDetail = "Offers Venom Distilling, Bog Salvage, Iron Aegis spell tome, and the Emberbound Relic.",
            unlockHint = "Walk to Mirefen Village on the World Map.",
        ),
        CodexEntry(
            id = "loc_stormspire_citadel",
            category = CodexCategory.WORLD,
            title = "Stormspire Citadel",
            subtitle = "Lightning Region · Frozen Mountain City",
            summary = "High-altitude arcane spires that harness alpine lightning into potent runic crystals.",
            mechanicsDetail = "Offers Volt Crystal Runecrafting, the Tempest Nova spell tome, and the Titanforged Warhammer.",
            unlockHint = "Walk to Stormspire Citadel on the World Map.",
        ),
        CodexEntry(
            id = "loc_obsidian_crag",
            category = CodexCategory.WORLD,
            title = "Obsidian Crag",
            subtitle = "Void Region · Endgame Caldera Fortress",
            summary = "A forbidding basalt citadel ringed by magma fissures, housing the realm's toughest 7-day campaign.",
            mechanicsDetail = "Offers Abyssal Seam Excavation, Vanguard Breastplate, Embersteel Greatsword, and the Abyssal Citadel Campaign.",
            unlockHint = "Walk to Obsidian Crag on the World Map.",
        ),

        // --- SHOPKEEPERS ---
        CodexEntry(
            id = "keeper_ember",
            category = CodexCategory.SHOPKEEPERS,
            title = "Bram Ironbrow",
            subtitle = "Master Forgekeeper · Emberhearth",
            summary = "Veteran smith who outfits newly awakened Sparkborne with sturdy iron and warm cinder eggs.",
            mechanicsDetail = "Stocks Ironbark Helm, Ashwood Spear, and Cinder Nest Eggs.",
            unlockHint = "Visit Emberhearth Outpost.",
        ),
        CodexEntry(
            id = "keeper_verdant",
            category = CodexCategory.SHOPKEEPERS,
            title = "Sylva Briarwood",
            subtitle = "Canopy Herbalist · Verdant Crossing",
            summary = "Ranger-herbalist who trades nimble greaves and twin blades forged from ironwood and steel.",
            mechanicsDetail = "Stocks Strider Greaves and Iron Twin Blades.",
            unlockHint = "Discover Verdant Crossing.",
        ),
        CodexEntry(
            id = "keeper_tidebreak",
            category = CodexCategory.SHOPKEEPERS,
            title = "Capt. Orin Saltwake",
            subtitle = "Harbor Quartermaster · Tidebreak Harbor",
            summary = "Weathered sea captain who trades restorative tide magic and rare coastal monster eggs.",
            mechanicsDetail = "Stocks Tome: Tidal Mend and Abyssal Tide Eggs.",
            unlockHint = "Discover Tidebreak Harbor.",
        ),
        CodexEntry(
            id = "keeper_mirefen",
            category = CodexCategory.SHOPKEEPERS,
            title = "Vexis Bogbrew",
            subtitle = "Venom Apothecary · Mirefen Village",
            summary = "Eccentric alchemist specializing in protective barriers and arcane relics distilled from bog spores.",
            mechanicsDetail = "Stocks Tome: Iron Aegis and Emberbound Relic.",
            unlockHint = "Discover Mirefen Village.",
        ),
        CodexEntry(
            id = "keeper_stormspire",
            category = CodexCategory.SHOPKEEPERS,
            title = "Archivist Lyra Volt",
            subtitle = "Conduit Weaver · Stormspire Citadel",
            summary = "Scholar of high-voltage runecrafting who guards storm magic and heavy warhammers.",
            mechanicsDetail = "Stocks Tome: Tempest Nova and Titanforged Warhammer.",
            unlockHint = "Discover Stormspire Citadel.",
        ),
        CodexEntry(
            id = "keeper_obsidian",
            category = CodexCategory.SHOPKEEPERS,
            title = "Kaelen the Ash-Forged",
            subtitle = "Vault Warden · Obsidian Crag",
            summary = "Keeper of the caldera's masterwork vault, selling apex armor and embersteel blades.",
            mechanicsDetail = "Stocks Vanguard Breastplate and Embersteel Greatsword.",
            unlockHint = "Discover Obsidian Crag.",
        ),

        // --- BEASTS & AFFINITIES ---
        CodexEntry(
            id = "beast_emberheart",
            category = CodexCategory.BEASTS,
            title = "Emberheart Lineage",
            subtitle = "Spark Kit → Pyre Wyvern → Solaris Dragon",
            summary = "High-drive companions born from caloric surplus and fierce delve momentum.",
            mechanicsDetail = "Duo Synergy (Fury Ignition): +15% weapon strike damage & heavy ember burst on companion skill.",
            unlockHint = "Hatch or bond with an Emberheart beast.",
        ),
        CodexEntry(
            id = "beast_ironclad",
            category = CodexCategory.BEASTS,
            title = "Ironclad Lineage",
            subtitle = "Ferrite Cub → Bastion Drake → Aegis Behemoth",
            summary = "Armored stone-and-steel guardians shaped by high-protein consistency.",
            mechanicsDetail = "Duo Synergy (Bulwark Bond): +20% armor defense & grants a protective shield on companion skill.",
            unlockHint = "Hatch or bond with an Ironclad beast.",
        ),
        CodexEntry(
            id = "beast_stormstrider",
            category = CodexCategory.BEASTS,
            title = "Stormstrider Lineage",
            subtitle = "Volt Wisp → Gale Lynx → Tempest Sovereign",
            summary = "Crackling electric beasts energized by high-carb fueling.",
            mechanicsDetail = "Duo Synergy (Arcane Surge): +25% spell damage & restores +6 Mana on companion skill.",
            unlockHint = "Hatch or bond with a Stormstrider beast.",
        ),
        CodexEntry(
            id = "beast_deepforge",
            category = CodexCategory.BEASTS,
            title = "Deepforge Lineage",
            subtitle = "Moss Tortoise → Rune Stag → Worldroot Leviathan",
            summary = "Enduring forest-stone spirits nurtured by balanced macros and steady recovery.",
            mechanicsDetail = "Duo Synergy (Vital Roots): +15% Max HP & heals you on companion skill.",
            unlockHint = "Hatch or bond with a Deepforge beast.",
        ),

        // --- ACTIVITIES & GEAR ---
        CodexEntry(
            id = "act_guide_gathering",
            category = CodexCategory.ACTIVITIES,
            title = "Town Step Activities",
            subtitle = "Mining · Foraging · Fishing · Alchemy · Runecrafting",
            summary = "Repeatable WalkScape-style activities that turn everyday steps into steady Gold, Iron, Shards, and XP.",
            mechanicsDetail = "Multiple cycles complete automatically in a single SparkyFitness sync — 3,400 steps on a 1,000-step Mining seam yields 3 instant drops plus 400 steps carried over!",
            unlockHint = "Start your first town activity.",
        ),
        CodexEntry(
            id = "gear_guide_forging",
            category = CodexCategory.GEAR,
            title = "Forging & Attuning Spells",
            subtitle = "Armor Slots · Weapon Classes · Spellbook",
            summary = "Equip a Weapon, 4 Armor pieces (Helm, Chest, Legs, Relic), and up to 3 Attuned Spells.",
            mechanicsDetail = "Weapons and Armor can be forged directly in the Hero Forge or purchased from regional Town Shops. Spells require Ember Shards to unlock and spend Hydration-boosted Mana in battle.",
            unlockHint = "Forge gear or unlock a spell.",
        ),
    )

    fun entryById(id: String): CodexEntry? = entries.firstOrNull { it.id == id }
}
