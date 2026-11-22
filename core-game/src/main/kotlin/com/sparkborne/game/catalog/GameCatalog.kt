package com.sparkborne.game.catalog

import com.sparkborne.game.model.ArmorItem
import com.sparkborne.game.model.ArmorSlot
import com.sparkborne.game.model.ContractRequirement
import com.sparkborne.game.model.ExpeditionCadence
import com.sparkborne.game.model.ExpeditionContract
import com.sparkborne.game.model.ExpeditionDifficulty
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.MonsterAffinity
import com.sparkborne.game.model.MonsterCompanion
import com.sparkborne.game.model.MonsterEgg
import com.sparkborne.game.model.MonsterStage
import com.sparkborne.game.model.PlayerState
import com.sparkborne.game.model.SpellDefinition
import com.sparkborne.game.model.SpellElement
import com.sparkborne.game.model.WeaponClass
import com.sparkborne.game.model.WeaponItem

/**
 * Deterministic, rule-based content catalog for Sparkborne Phase 2.
 */
object GameCatalog {

    val weapons: List<WeaponItem> = listOf(
        WeaponItem(
            id = "wpn_iron_greatsword",
            name = "Iron Greatsword",
            weaponClass = WeaponClass.GREATSWORD,
            baseDamage = 14,
            muscleScalingFactor = 2.5,
        ),
        WeaponItem(
            id = "wpn_ash_spear",
            name = "Ashwood Spear",
            weaponClass = WeaponClass.SPEAR,
            baseDamage = 13,
            muscleScalingFactor = 2.6,
            forgeGoldCost = 45,
            forgeIronCost = 8,
        ),
        WeaponItem(
            id = "wpn_twin_fangs",
            name = "Iron Twin Blades",
            weaponClass = WeaponClass.TWIN_BLADES,
            baseDamage = 12,
            muscleScalingFactor = 2.8,
            forgeGoldCost = 50,
            forgeIronCost = 10,
        ),
        WeaponItem(
            id = "wpn_titan_maul",
            name = "Titanforged Warhammer",
            weaponClass = WeaponClass.WARHAMMER,
            baseDamage = 20,
            muscleScalingFactor = 3.2,
            forgeGoldCost = 120,
            forgeIronCost = 24,
        ),
        WeaponItem(
            id = "wpn_ember_claymore",
            name = "Embersteel Greatsword",
            weaponClass = WeaponClass.GREATSWORD,
            baseDamage = 22,
            muscleScalingFactor = 3.4,
            forgeGoldCost = 140,
            forgeIronCost = 28,
        ),
        WeaponItem(
            id = "wpn_frostfang_halberd",
            name = "Frostfang Halberd",
            weaponClass = WeaponClass.SPEAR,
            baseDamage = 24,
            muscleScalingFactor = 3.3,
            forgeGoldCost = 165,
            forgeIronCost = 30,
        ),
        WeaponItem(
            id = "wpn_solaris_scimitars",
            name = "Solaris Twin Scimitars",
            weaponClass = WeaponClass.TWIN_BLADES,
            baseDamage = 23,
            muscleScalingFactor = 3.5,
            forgeGoldCost = 175,
            forgeIronCost = 32,
        ),
        WeaponItem(
            id = "wpn_starforged_maul",
            name = "Starforged Colossus Maul",
            weaponClass = WeaponClass.WARHAMMER,
            baseDamage = 28,
            muscleScalingFactor = 3.8,
            forgeGoldCost = 240,
            forgeIronCost = 45,
        ),
    )

    val armor: List<ArmorItem> = listOf(
        ArmorItem(
            id = "arm_scout_tunic",
            name = "Scout's Leather Jerkin",
            slot = ArmorSlot.CHEST,
            defense = 4,
            bonusHp = 15,
        ),
        ArmorItem(
            id = "arm_iron_helm",
            name = "Ironbark Helm",
            slot = ArmorSlot.HELM,
            defense = 4,
            bonusHp = 12,
            bonusMana = 4,
            forgeGoldCost = 40,
            forgeIronCost = 8,
        ),
        ArmorItem(
            id = "arm_vanguard_plate",
            name = "Vanguard Breastplate",
            slot = ArmorSlot.CHEST,
            defense = 10,
            bonusHp = 35,
            forgeGoldCost = 110,
            forgeIronCost = 20,
            forgeShardsCost = 6,
        ),
        ArmorItem(
            id = "arm_strider_greaves",
            name = "Strider Greaves",
            slot = ArmorSlot.LEGS,
            defense = 6,
            bonusHp = 20,
            bonusMana = 6,
            forgeGoldCost = 70,
            forgeIronCost = 14,
            forgeShardsCost = 3,
        ),
        ArmorItem(
            id = "arm_ember_signet",
            name = "Emberbound Relic",
            slot = ArmorSlot.RELIC,
            defense = 3,
            bonusHp = 10,
            bonusMana = 10,
            spellPowerBonus = 8,
            forgeGoldCost = 90,
            forgeIronCost = 6,
            forgeShardsCost = 10,
        ),
        ArmorItem(
            id = "arm_glacier_crown",
            name = "Glacier Crown",
            slot = ArmorSlot.HELM,
            defense = 8,
            bonusHp = 24,
            bonusMana = 12,
            forgeGoldCost = 125,
            forgeIronCost = 22,
            forgeShardsCost = 8,
        ),
        ArmorItem(
            id = "arm_dune_greaves",
            name = "Dune-Runner Greaves",
            slot = ArmorSlot.LEGS,
            defense = 10,
            bonusHp = 32,
            bonusMana = 10,
            forgeGoldCost = 135,
            forgeIronCost = 24,
            forgeShardsCost = 8,
        ),
        ArmorItem(
            id = "arm_astral_relic",
            name = "Astral Heart Relic",
            slot = ArmorSlot.RELIC,
            defense = 6,
            bonusHp = 20,
            bonusMana = 18,
            spellPowerBonus = 15,
            forgeGoldCost = 180,
            forgeIronCost = 16,
            forgeShardsCost = 18,
        ),
    )

    val spells: List<SpellDefinition> = listOf(
        SpellDefinition(
            id = "spl_spark_bolt",
            name = "Spark Bolt",
            element = SpellElement.EMBER,
            manaCost = 5,
            basePower = 20,
            description = "Hurl a focused bolt of ember energy scaled by Carbs & Spell Power.",
        ),
        SpellDefinition(
            id = "spl_tidal_mend",
            name = "Tidal Mend",
            element = SpellElement.TIDE,
            manaCost = 6,
            basePower = 0,
            healAmount = 26,
            description = "Channel hydration clarity to restore HP during a delve.",
            unlockShardsCost = 4,
        ),
        SpellDefinition(
            id = "spl_iron_aegis",
            name = "Iron Aegis",
            element = SpellElement.IRON,
            manaCost = 5,
            basePower = 8,
            shieldAmount = 22,
            description = "Strike lightly and raise an iron barrier absorbing incoming hits.",
            unlockShardsCost = 6,
        ),
        SpellDefinition(
            id = "spl_tempest_nova",
            name = "Tempest Nova",
            element = SpellElement.GALE,
            manaCost = 10,
            basePower = 38,
            description = "Unleash a high-voltage surge of arcane force.",
            unlockShardsCost = 12,
        ),
        SpellDefinition(
            id = "spl_glacial_ward",
            name = "Glacial Ward",
            element = SpellElement.TIDE,
            manaCost = 7,
            basePower = 0,
            healAmount = 16,
            shieldAmount = 28,
            description = "Weave glacial ice into a heavy barrier while soothing wounds.",
            unlockShardsCost = 10,
        ),
        SpellDefinition(
            id = "spl_solar_cataclysm",
            name = "Solar Cataclysm",
            element = SpellElement.EMBER,
            manaCost = 14,
            basePower = 56,
            description = "Call down an astral solar flare from the Ashen Sanctum.",
            unlockShardsCost = 18,
        ),
    )

    val contracts: List<ExpeditionContract> = listOf(
        ExpeditionContract(
            id = "exp_intro_ember_nest",
            title = "The Ember Nest (Intro)",
            description = "Intro Trial: Walk 1,500 steps to reach the ancient ember nest, defeat the Ash-Whelp, and claim your very first Monster Egg!",
            cadence = ExpeditionCadence.DAILY,
            durationDays = 3,
            difficulty = ExpeditionDifficulty.SCOUT,
            totalNodes = 2,
            stepsPerNode = 750,
            requirement = ContractRequirement(minTotalSteps = 1500),
            baseRewardGold = 30,
            baseRewardShards = 3,
            baseRewardIron = 6,
            grantsEggOnClear = false,
            bossName = "Nestling Ash-Whelp",
            bossHp = 34,
            bossAttack = 7,
            bossDefense = 1,
        ),
        ExpeditionContract(
            id = "exp_daily_ashlands_patrol",
            title = "Ashlands Patrol",
            description = "A brisk 1-day scout contract. Hit 6,000 steps to clear 3 nodes and face the Cinder Hound.",
            cadence = ExpeditionCadence.DAILY,
            durationDays = 1,
            difficulty = ExpeditionDifficulty.SCOUT,
            totalNodes = 3,
            stepsPerNode = 2000,
            requirement = ContractRequirement(minTotalSteps = 6000),
            baseRewardGold = 45,
            baseRewardShards = 5,
            baseRewardIron = 8,
            grantsEggOnClear = false,
            bossName = "Cinder Hound",
            bossHp = 65,
            bossAttack = 11,
            bossDefense = 3,
        ),
        ExpeditionContract(
            id = "exp_daily_iron_gauntlet",
            title = "Iron Gauntlet",
            description = "1-day vanguard contract: 8,000 steps + at least 2,000 kg lifting volume.",
            cadence = ExpeditionCadence.DAILY,
            durationDays = 1,
            difficulty = ExpeditionDifficulty.VANGUARD,
            totalNodes = 4,
            stepsPerNode = 2000,
            requirement = ContractRequirement(
                minTotalSteps = 8000,
                minLiftingVolumeKg = 2000.0,
            ),
            baseRewardGold = 80,
            baseRewardShards = 9,
            baseRewardIron = 16,
            grantsEggOnClear = false,
            bossName = "Forge Warden",
            bossHp = 95,
            bossAttack = 15,
            bossDefense = 6,
        ),
        ExpeditionContract(
            id = "exp_daily_clarity_vigil",
            title = "Clarity Vigil",
            description = "1-day recovery & discipline run: 7,500 steps + hit protein & hydration goals.",
            cadence = ExpeditionCadence.DAILY,
            durationDays = 1,
            difficulty = ExpeditionDifficulty.VANGUARD,
            totalNodes = 3,
            stepsPerNode = 2500,
            requirement = ContractRequirement(
                minTotalSteps = 7500,
                minProteinDays = 1,
                minHydrationDays = 1,
            ),
            baseRewardGold = 75,
            baseRewardShards = 12,
            baseRewardIron = 10,
            grantsEggOnClear = true,
            bossName = "Mist Stalker",
            bossHp = 85,
            bossAttack = 14,
            bossDefense = 5,
        ),
        ExpeditionContract(
            id = "exp_multi_3d_obsidian_crag",
            title = "Obsidian Crag Delve",
            description = "3-day roguelike delve: 24,000 total steps, 4,000 kg lifted, 2 protein days. Drops a Monster Egg.",
            cadence = ExpeditionCadence.MULTI_DAY,
            durationDays = 3,
            difficulty = ExpeditionDifficulty.VANGUARD,
            totalNodes = 6,
            stepsPerNode = 4000,
            requirement = ContractRequirement(
                minTotalSteps = 24000,
                minLiftingVolumeKg = 4000.0,
                minProteinDays = 2,
            ),
            baseRewardGold = 180,
            baseRewardShards = 22,
            baseRewardIron = 30,
            grantsEggOnClear = true,
            bossName = "Obsidian Colossus",
            bossHp = 140,
            bossAttack = 20,
            bossDefense = 9,
        ),
        ExpeditionContract(
            id = "exp_multi_7d_abyssal_citadel",
            title = "Abyssal Citadel Campaign",
            description = "7-day abyssal expedition: 60,000 steps, 10,000 kg lifted, 5 protein & 5 hydration days. Massive haul, total loot loss on failure.",
            cadence = ExpeditionCadence.MULTI_DAY,
            durationDays = 7,
            difficulty = ExpeditionDifficulty.ABYSSAL,
            totalNodes = 10,
            stepsPerNode = 6000,
            requirement = ContractRequirement(
                minTotalSteps = 60000,
                minLiftingVolumeKg = 10000.0,
                minProteinDays = 5,
                minHydrationDays = 5,
                minSleepDays = 4,
            ),
            baseRewardGold = 450,
            baseRewardShards = 55,
            baseRewardIron = 70,
            grantsEggOnClear = true,
            bossName = "Archon of the Deep",
            bossHp = 230,
            bossAttack = 28,
            bossDefense = 13,
        ),
        ExpeditionContract(
            id = "exp_daily_frost_march",
            title = "Frostpeak March",
            description = "1-day alpine contract: 9,000 steps + hit your sleep target. Defeat the Rime Yeti for a Monster Egg.",
            cadence = ExpeditionCadence.DAILY,
            durationDays = 1,
            difficulty = ExpeditionDifficulty.VANGUARD,
            totalNodes = 4,
            stepsPerNode = 2250,
            requirement = ContractRequirement(
                minTotalSteps = 9000,
                minSleepDays = 1,
            ),
            baseRewardGold = 95,
            baseRewardShards = 12,
            baseRewardIron = 18,
            grantsEggOnClear = true,
            bossName = "Rime Yeti",
            bossHp = 115,
            bossAttack = 17,
            bossDefense = 7,
        ),
        ExpeditionContract(
            id = "exp_daily_sunstone_caravan",
            title = "Sunstone Caravan",
            description = "1-day desert run: 10,000 steps + hit your hydration target across the golden dunes.",
            cadence = ExpeditionCadence.DAILY,
            durationDays = 1,
            difficulty = ExpeditionDifficulty.VANGUARD,
            totalNodes = 4,
            stepsPerNode = 2500,
            requirement = ContractRequirement(
                minTotalSteps = 10000,
                minHydrationDays = 1,
            ),
            baseRewardGold = 120,
            baseRewardShards = 14,
            baseRewardIron = 16,
            grantsEggOnClear = false,
            bossName = "Dune Scorpion Matriarch",
            bossHp = 125,
            bossAttack = 19,
            bossDefense = 8,
        ),
        ExpeditionContract(
            id = "exp_multi_3d_glacier_rift",
            title = "Glacier Rift Expedition",
            description = "3-day frontier delve: 28,000 steps, 6,000 kg lifted, and 2 sleep days. Drops a Monster Egg.",
            cadence = ExpeditionCadence.MULTI_DAY,
            durationDays = 3,
            difficulty = ExpeditionDifficulty.ABYSSAL,
            totalNodes = 6,
            stepsPerNode = 4600,
            requirement = ContractRequirement(
                minTotalSteps = 28000,
                minLiftingVolumeKg = 6000.0,
                minSleepDays = 2,
            ),
            baseRewardGold = 240,
            baseRewardShards = 28,
            baseRewardIron = 40,
            grantsEggOnClear = true,
            bossName = "Glacial Wyrm",
            bossHp = 175,
            bossAttack = 23,
            bossDefense = 11,
        ),
        ExpeditionContract(
            id = "exp_multi_5d_ashen_gauntlet",
            title = "The Ashen Sanctum Trial",
            description = "5-day mythic frontier campaign: 50,000 steps, 12,000 kg lifted, 4 protein & 4 hydration days. Massive treasure & guaranteed Egg.",
            cadence = ExpeditionCadence.MULTI_DAY,
            durationDays = 5,
            difficulty = ExpeditionDifficulty.ABYSSAL,
            totalNodes = 8,
            stepsPerNode = 6250,
            requirement = ContractRequirement(
                minTotalSteps = 50000,
                minLiftingVolumeKg = 12000.0,
                minProteinDays = 4,
                minHydrationDays = 4,
            ),
            baseRewardGold = 420,
            baseRewardShards = 52,
            baseRewardIron = 65,
            grantsEggOnClear = true,
            bossName = "Starbound Titan",
            bossHp = 260,
            bossAttack = 31,
            bossDefense = 15,
        ),
    )

    fun weaponById(id: String): WeaponItem =
        weapons.firstOrNull { it.id == id } ?: weapons.first()

    fun armorById(id: String): ArmorItem? =
        armor.firstOrNull { it.id == id }

    fun spellById(id: String): SpellDefinition? =
        spells.firstOrNull { it.id == id }

    fun contractById(id: String): ExpeditionContract? =
        contracts.firstOrNull { it.id == id }

    fun monsterSpeciesName(affinity: MonsterAffinity, stage: MonsterStage): String {
        return when (affinity) {
            MonsterAffinity.IRONCLAD -> when (stage) {
                MonsterStage.HATCHLING -> "Ferrite Cub"
                MonsterStage.JUVENILE -> "Bastion Drake"
                MonsterStage.APEX -> "Aegis Behemoth"
            }
            MonsterAffinity.STORMSTRIDER -> when (stage) {
                MonsterStage.HATCHLING -> "Volt Wisp"
                MonsterStage.JUVENILE -> "Gale Lynx"
                MonsterStage.APEX -> "Tempest Sovereign"
            }
            MonsterAffinity.DEEPFORGE -> when (stage) {
                MonsterStage.HATCHLING -> "Moss Tortoise"
                MonsterStage.JUVENILE -> "Rune Stag"
                MonsterStage.APEX -> "Worldroot Leviathan"
            }
            MonsterAffinity.EMBERHEART -> when (stage) {
                MonsterStage.HATCHLING -> "Spark Kit"
                MonsterStage.JUVENILE -> "Pyre Wyvern"
                MonsterStage.APEX -> "Solaris Dragon"
            }
        }
    }

    fun initialGameState(): GameState {
        return GameState(
            player = PlayerState(
                level = 0,
                xp = 0,
                gold = 50,
                emberShards = 5,
                ironOre = 10,
                equippedWeaponId = "wpn_iron_greatsword",
                equippedArmor = mapOf(ArmorSlot.CHEST to "arm_scout_tunic"),
                attunedSpellIds = listOf("spl_spark_bolt"),
                ownedWeaponIds = setOf("wpn_iron_greatsword"),
                ownedArmorIds = setOf("arm_scout_tunic"),
                unlockedSpellIds = setOf("spl_spark_bolt"),
            ),
            monsters = emptyList(),
            activeMonsterId = null,
            eggs = emptyList(),
            recentLog = listOf("You awaken in Emberhearth Outpost at Level 0. Sync SparkyFitness and follow the Intro Quest to claim your first Monster Egg!"),
        )
    }
}
