package com.sparkborne.game.model

import kotlinx.serialization.Serializable

@Serializable
enum class MonsterStage(val displayName: String, val requiredXpToEvolve: Int?) {
    HATCHLING("Hatchling", 120),
    JUVENILE("Juvenile", 360),
    APEX("Apex", null),
}

/**
 * Deterministic monster affinity driven by the player's macro & calorie profile.
 */
@Serializable
enum class MonsterAffinity(
    val displayName: String,
    val synergyTitle: String,
    val synergyDescription: String,
) {
    IRONCLAD(
        displayName = "Ironclad",
        synergyTitle = "Bulwark Bond",
        synergyDescription = "Protein-forged: +20% armor defense synergy & shields you on companion skill.",
    ),
    STORMSTRIDER(
        displayName = "Stormstrider",
        synergyTitle = "Arcane Surge",
        synergyDescription = "Carb-charged: +25% spell damage synergy & restores 4 Mana on companion skill.",
    ),
    DEEPFORGE(
        displayName = "Deepforge",
        synergyTitle = "Vital Roots",
        synergyDescription = "Balanced endurance: +15% Max HP synergy & heals you on companion skill.",
    ),
    EMBERHEART(
        displayName = "Emberheart",
        synergyTitle = "Fury Ignition",
        synergyDescription = "High-drive fuel: +15% weapon strike synergy & deals heavy ember burst.",
    ),
}

/**
 * Tracks cumulative nutrition adherence ratios while raising an egg or monster so
 * evolution branches are 100% deterministic and transparent.
 */
@Serializable
data class MacroEvolutionProfile(
    val proteinRatioSum: Double = 0.0,
    val carbsRatioSum: Double = 0.0,
    val fatRatioSum: Double = 0.0,
    val caloriesRatioSum: Double = 0.0,
    val daysTracked: Int = 0,
) {
    fun dominantAffinity(): MonsterAffinity {
        if (daysTracked == 0) return MonsterAffinity.DEEPFORGE
        val avgProtein = proteinRatioSum / daysTracked
        val avgCarbs = carbsRatioSum / daysTracked
        val avgFat = fatRatioSum / daysTracked
        val avgCalories = caloriesRatioSum / daysTracked

        return when {
            avgProtein >= avgCarbs && avgProtein >= avgFat && avgProtein >= 0.85 -> MonsterAffinity.IRONCLAD
            avgCarbs > avgProtein && avgCarbs >= avgFat && avgCarbs >= 0.85 -> MonsterAffinity.STORMSTRIDER
            avgCalories >= 1.05 -> MonsterAffinity.EMBERHEART
            else -> MonsterAffinity.DEEPFORGE
        }
    }
}

/**
 * Incubated primarily by real-world Sleep (vitality warmth) and Hydration (clarity dew).
 */
@Serializable
data class MonsterEgg(
    val id: String,
    val name: String,
    val speciesSeed: String,
    val requiredSleepMinutes: Int,
    val incubatedSleepMinutes: Int = 0,
    val requiredHydrationMl: Int,
    val incubatedHydrationMl: Int = 0,
    val macroProfile: MacroEvolutionProfile = MacroEvolutionProfile(),
) {
    val isReadyToHatch: Boolean
        get() = incubatedSleepMinutes >= requiredSleepMinutes &&
            incubatedHydrationMl >= requiredHydrationMl

    val progressFraction: Double
        get() {
            val sleepFrac = (incubatedSleepMinutes.toDouble() / requiredSleepMinutes.coerceAtLeast(1)).coerceIn(0.0, 1.0)
            val hydroFrac = (incubatedHydrationMl.toDouble() / requiredHydrationMl.coerceAtLeast(1)).coerceIn(0.0, 1.0)
            return (sleepFrac + hydroFrac) / 2.0
        }
}

@Serializable
data class MonsterCompanion(
    val id: String,
    val name: String,
    val speciesId: String,
    val stage: MonsterStage,
    val level: Int,
    val xp: Int,
    val affinity: MonsterAffinity,
    val baseAttack: Int,
    val baseDefense: Int,
    val macroProfile: MacroEvolutionProfile = MacroEvolutionProfile(),
    val evolutionHistory: List<String> = emptyList(),
) {
    val canEvolve: Boolean
        get() {
            val req = stage.requiredXpToEvolve ?: return false
            return xp >= req
        }
}
