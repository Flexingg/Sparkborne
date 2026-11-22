package com.sparkborne.game.model

import kotlinx.serialization.Serializable

@Serializable
enum class CodexCategory(val displayName: String, val icon: String) {
    GUIDES("Guides", "📖"),
    WORLD("World & Towns", "🗺"),
    SHOPKEEPERS("Shopkeepers", "🛒"),
    BEASTS("Beasts & Eggs", "🐉"),
    GEAR("Gear & Spells", "⚔"),
    ACTIVITIES("Activities", "⛏"),
}

@Serializable
data class CodexEntry(
    val id: String,
    val category: CodexCategory,
    val title: String,
    val subtitle: String,
    val summary: String,
    val mechanicsDetail: String,
    val unlockHint: String,
)

@Serializable
enum class IntroQuestStep(
    val stepNumber: Int,
    val title: String,
    val objective: String,
    val tip: String,
) {
    SYNC_SPARKY(
        stepNumber = 1,
        title = "Attune the Conduit",
        objective = "Sync your SparkyFitness telemetry to awaken your baseline stats.",
        tip = "Tap ⚡ SYNC in the top bar (or configure your Cloudflare Tunnel URL & API key in the Sync tab). Pre-game syncs seed your baseline muscle & recovery stats while keeping you at Level 0.",
    ),
    VISIT_EMBERHEARTH(
        stepNumber = 2,
        title = "Meet Bram Ironbrow",
        objective = "Tap Emberhearth Outpost on the World Map and open Bram's Shop.",
        tip = "Every city has its own shopkeeper, regional wares, repeatable step activities, and local contracts.",
    ),
    START_FIRST_ACTIVITY(
        stepNumber = 3,
        title = "Honest Footsteps",
        objective = "Start any repeatable Town Activity in Emberhearth Outpost.",
        tip = "When you aren't traveling or delving, synced steps automatically grind your active town activity for Gold, Iron, Shards & XP.",
    ),
    ACCEPT_INTRO_QUEST(
        stepNumber = 4,
        title = "The Ember Nest",
        objective = "Accept 'The Ember Nest' intro delve contract to recover your first Monster Egg.",
        tip = "Clear the Ember Nest delve (or complete its single skirmish) to claim your first Monster Egg and reach Level 1!",
    ),
    COMPLETED(
        stepNumber = 5,
        title = "Sparkborne Awakened",
        objective = "You have claimed your first Monster Egg! Incubate it with Sleep & Hydration.",
        tip = "Check Sparkpedia anytime to review mechanics, evolution branches, and newly discovered towns.",
    ),
}

@Serializable
data class IntroQuestState(
    val currentStep: IntroQuestStep = IntroQuestStep.SYNC_SPARKY,
    val firstEggClaimed: Boolean = false,
) {
    val isComplete: Boolean
        get() = currentStep == IntroQuestStep.COMPLETED && firstEggClaimed
}
