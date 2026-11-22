package com.sparkborne.game.model

import com.sparkborne.telemetry.model.MuscleGroup
import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Each weapon class scales its damage directly from specific real-world lifting [MuscleGroup] ranks.
 */
@Serializable
enum class WeaponClass(
    val displayName: String,
    val scalingMuscles: List<MuscleGroup>,
) {
    GREATSWORD(
        displayName = "Greatsword",
        scalingMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS),
    ),
    WARHAMMER(
        displayName = "Warhammer",
        scalingMuscles = listOf(MuscleGroup.BACK, MuscleGroup.QUADS, MuscleGroup.GLUTES, MuscleGroup.CORE),
    ),
    SPEAR(
        displayName = "Spear",
        scalingMuscles = listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES),
    ),
    TWIN_BLADES(
        displayName = "Twin Blades",
        scalingMuscles = listOf(MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.FOREARMS, MuscleGroup.CORE),
    ),
}

@Serializable
enum class ArmorSlot(val displayName: String) {
    HELM("Helm"),
    CHEST("Chestplate"),
    LEGS("Greaves"),
    RELIC("Relic"),
}

@Serializable
enum class SpellElement(val displayName: String) {
    EMBER("Ember"),
    TIDE("Tide"),
    IRON("Iron"),
    GALE("Gale"),
}

/**
 * Passive aura derived deterministically from the player's 7d/14d bodyweight trend.
 */
@Serializable
enum class MomentumStance(
    val displayName: String,
    val description: String,
    val attackMultiplier: Double,
    val defenseBonus: Int,
    val spellMultiplier: Double,
    val stepEfficiencyMultiplier: Double,
) {
    JUGGERNAUT(
        displayName = "Juggernaut",
        description = "Gaining trend: +12% weapon damage & +3 defense.",
        attackMultiplier = 1.12,
        defenseBonus = 3,
        spellMultiplier = 1.0,
        stepEfficiencyMultiplier = 1.0,
    ),
    PROWLER(
        displayName = "Prowler",
        description = "Losing trend: +15% step progress & +8% spell power.",
        attackMultiplier = 1.0,
        defenseBonus = 0,
        spellMultiplier = 1.08,
        stepEfficiencyMultiplier = 1.15,
    ),
    EQUILIBRIUM(
        displayName = "Equilibrium",
        description = "Stable trend: +8% weapon damage, +10% spell power & +2 defense.",
        attackMultiplier = 1.08,
        defenseBonus = 2,
        spellMultiplier = 1.10,
        stepEfficiencyMultiplier = 1.05,
    ),
    UNALIGNED(
        displayName = "Unaligned",
        description = "Log more bodyweight entries to awaken a Momentum Stance.",
        attackMultiplier = 1.0,
        defenseBonus = 0,
        spellMultiplier = 1.0,
        stepEfficiencyMultiplier = 1.0,
    ),
}

@Serializable
data class WeaponItem(
    val id: String,
    val name: String,
    val weaponClass: WeaponClass,
    val baseDamage: Int,
    val muscleScalingFactor: Double,
    val forgeGoldCost: Int = 0,
    val forgeIronCost: Int = 0,
)

@Serializable
data class ArmorItem(
    val id: String,
    val name: String,
    val slot: ArmorSlot,
    val defense: Int,
    val bonusHp: Int,
    val bonusMana: Int = 0,
    val spellPowerBonus: Int = 0,
    val forgeGoldCost: Int = 0,
    val forgeIronCost: Int = 0,
    val forgeShardsCost: Int = 0,
)

@Serializable
data class SpellDefinition(
    val id: String,
    val name: String,
    val element: SpellElement,
    val manaCost: Int,
    val basePower: Int,
    val healAmount: Int = 0,
    val shieldAmount: Int = 0,
    val description: String,
    val unlockShardsCost: Int = 0,
)

/**
 * Active delve buffs computed from the latest synced daily telemetry snapshot + bodyweight trend.
 * Combines with persistent gear, muscle ranks, and monster companions during delves.
 */
@Serializable
data class DailyDelveBuffs(
    val asOfDate: String = "",
    val sleepVitalityHpBonus: Int = 0,
    val hydrationClarityManaBonus: Int = 0,
    val proteinFortitudeDefenseBonus: Int = 0,
    val carbsSpellPowerBonus: Int = 0,
    val fatEnduranceCritBonusPercent: Int = 0,
    val momentumStance: MomentumStance = MomentumStance.UNALIGNED,
)

@Serializable
data class PlayerState(
    val level: Int = 0,
    val xp: Int = 0,
    val gold: Int = 50,
    val emberShards: Int = 5,
    val ironOre: Int = 10,
    val savedSteps: Int = 0,
    val muscleStrengthXp: Map<MuscleGroup, Double> = emptyMap(),
    val equippedWeaponId: String,
    val equippedArmor: Map<ArmorSlot, String> = emptyMap(),
    val attunedSpellIds: List<String> = emptyList(),
    val ownedWeaponIds: Set<String> = emptySet(),
    val ownedArmorIds: Set<String> = emptySet(),
    val unlockedSpellIds: Set<String> = emptySet(),
    val activeBuffs: DailyDelveBuffs = DailyDelveBuffs(),
    val skillXp: Map<SkillType, Int> = emptyMap(),
    val skillChests: Map<SkillType, Int> = emptyMap(),
    val totalChestsOpened: Int = 0,
    val activityCompletions: Map<String, Int> = emptyMap(),
    val discoveredCollectibleIds: Set<String> = emptySet(),
) {
    fun skillLevel(skill: SkillType): Int =
        com.sparkborne.game.engine.SkillEngine.levelForXp(skillXp[skill] ?: 0)

    fun totalSkillLevel(): Int =
        SkillType.entries.sumOf { skillLevel(it) }

    fun totalSkillChests(): Int =
        skillChests.values.sum()

    /**
     * Deterministic rank curve from accumulated effective lifting volume (kg) for a muscle group.
     * Rank 1 starts at 0 kg; Rank 2 at 250 kg; Rank 3 at 1000 kg; Rank 4 at 2250 kg; etc.
     */
    fun muscleStrengthRank(group: MuscleGroup): Int {
        val volumeXp = muscleStrengthXp[group] ?: 0.0
        if (volumeXp <= 0.0) return 1
        return floor(sqrt(volumeXp / 250.0)).toInt() + 1
    }

    /**
     * WalkScape-style Step Bank capacity:
     * Starts at 50,000 steps at Level 0 and scales cleanly into the millions with Hero Level
     * and accumulated Muscle Strength Ranks.
     */
    fun maxStepBankCapacity(): Int {
        val base = 50_000L
        val lvl = level.coerceAtLeast(0).toLong()
        val levelBonus = (lvl * 25_000L) + (lvl * lvl * 5_000L)
        val extraMuscleRanks = MuscleGroup.entries
            .filter { it != MuscleGroup.OTHER }
            .sumOf { (muscleStrengthRank(it) - 1).coerceAtLeast(0).toLong() }
        val muscleBonus = extraMuscleRanks * 10_000L
        return (base + levelBonus + muscleBonus).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun xpToNextLevel(): Int = if (level <= 0) 100 else level * 150
}
