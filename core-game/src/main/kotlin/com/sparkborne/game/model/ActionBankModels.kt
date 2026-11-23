package com.sparkborne.game.model

import kotlinx.serialization.Serializable

/**
 * Bankable real-world fitness & recovery Action Point pools.
 * Each pool accumulates from synced Health Connect / SparkyFitness / Phone Sensor telemetry
 * and can be spent on-demand like Action Points on matching RPG skills and combat.
 *
 * Leveling up the matching skills permanently expands each bank's maximum capacity!
 */
@Serializable
enum class ActionBankType(
    val displayName: String,
    val icon: String,
    val unit: String,
    val baseCapacity: Int,
    val fuelsDescription: String,
) {
    STEPS(
        displayName = "Steps",
        icon = "🥾",
        unit = "steps",
        baseCapacity = 50_000,
        fuelsDescription = "World Travel, Foraging, Archaeology, Trade & Delve Exploration",
    ),
    PUSH_VOLUME(
        displayName = "Push Volume",
        icon = "💪",
        unit = "kg",
        baseCapacity = 25_000,
        fuelsDescription = "Mining & Smithing (from Chest, Shoulders & Triceps lifting)",
    ),
    PULL_LEGS_VOLUME(
        displayName = "Pull & Legs",
        icon = "🪓",
        unit = "kg",
        baseCapacity = 25_000,
        fuelsDescription = "Woodcutting, Fishing & Salvage (from Back, Arms, Legs & Core lifting)",
    ),
    HYDRATION_ML(
        displayName = "Hydration",
        icon = "💧",
        unit = "mL",
        baseCapacity = 15_000,
        fuelsDescription = "Diving / Swimming & Alchemy (from daily water intake)",
    ),
    CALORIES_BURNT(
        displayName = "Energy Burn",
        icon = "🔥",
        unit = "kcal",
        baseCapacity = 10_000,
        fuelsDescription = "Fighting Monsters in Delves & Runecrafting (from active calories burned)",
    ),
}
