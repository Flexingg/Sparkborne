package com.sparkborne.game.engine

import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.IntroQuestStep
import com.sparkborne.game.model.MomentumStance
import com.sparkborne.game.model.MonsterAffinity
import com.sparkborne.game.model.MonsterEgg

/**
 * Deterministic engine managing WalkScape-style Sparkpedia progressive unlocks
 * and the guided Level 0 -> Level 1 Intro Quest ("The Ember Nest") to earn your first Monster Egg.
 */
object SparkpediaEngine {

    const val INTRO_CONTRACT_ID = "exp_intro_ember_nest"

    /**
     * Evaluates the current [GameState] and unlocks any newly encountered Sparkpedia entries,
     * keeping `unreadCodexIds` updated so the UI can show a subtle "NEW" badge.
     */
    fun syncCodexUnlocks(state: GameState): GameState {
        val newlyUnlocked = linkedSetOf<String>()

        fun unlock(id: String) {
            if (id !in state.unlockedCodexIds) {
                newlyUnlocked.add(id)
            }
        }

        // Always unlocked starter guides
        unlock("guide_welcome")
        unlock("guide_step_bank")
        unlock("guide_skills_chests")
        unlock("guide_collectibles_museum")
        unlock("guide_toolbelt_gearsets")
        unlock("guide_crafting_consumables")
        unlock("guide_health_connect")
        unlock("guide_eastern_frontier")
        unlock("loc_emberhearth")
        unlock("keeper_ember")

        // Telemetry & lifting unlocks
        if (state.processedDateHashes.isNotEmpty()) {
            unlock("guide_telemetry_map")
        }
        if (state.player.muscleStrengthXp.values.any { it > 0.0 }) {
            unlock("guide_muscle_scaling")
        }
        if (state.player.activeBuffs.momentumStance != MomentumStance.UNALIGNED) {
            unlock("guide_stances")
        }

        // Discovered towns & their shopkeepers
        val townToKeeper = mapOf(
            "loc_emberhearth" to "keeper_ember",
            "loc_verdant_crossing" to "keeper_verdant",
            "loc_tidebreak_harbor" to "keeper_tidebreak",
            "loc_mirefen_village" to "keeper_mirefen",
            "loc_stormspire_citadel" to "keeper_stormspire",
            "loc_obsidian_crag" to "keeper_obsidian",
            "loc_frostpeak_bastion" to "keeper_frostpeak",
            "loc_sunspire_oasis" to "keeper_sunspire",
            "loc_ashen_sanctum" to "keeper_ashen",
        )
        val frontierIds = setOf("loc_frostpeak_bastion", "loc_sunspire_oasis", "loc_ashen_sanctum")
        for (locId in state.discoveredLocationIds) {
            unlock(locId)
            townToKeeper[locId]?.let(::unlock)
            if (locId in frontierIds) {
                unlock("act_guide_frontier_skills")
            }
        }

        // Activities & Expeditions
        if (state.activeActivity != null) {
            unlock("act_guide_gathering")
        }
        if (state.activeExpedition != null || state.completedContractIds.isNotEmpty()) {
            unlock("guide_roguelike_stakes")
        }

        // Gear & Spells
        if (state.player.ownedWeaponIds.size > 1 || state.player.ownedArmorIds.size > 1 || state.player.unlockedSpellIds.size > 1) {
            unlock("gear_guide_forging")
        }

        // Eggs & Beast Lineages
        if (state.eggs.isNotEmpty() || state.monsters.isNotEmpty()) {
            unlock("guide_eggs_evolution")
        }
        for (mon in state.monsters) {
            when (mon.affinity) {
                MonsterAffinity.EMBERHEART -> unlock("beast_emberheart")
                MonsterAffinity.IRONCLAD -> unlock("beast_ironclad")
                MonsterAffinity.STORMSTRIDER -> unlock("beast_stormstrider")
                MonsterAffinity.DEEPFORGE -> unlock("beast_deepforge")
            }
        }

        if (newlyUnlocked.isEmpty()) return state

        return state.copy(
            unlockedCodexIds = state.unlockedCodexIds + newlyUnlocked,
            unreadCodexIds = state.unreadCodexIds + newlyUnlocked,
        )
    }

    fun markCodexRead(state: GameState): GameState {
        if (state.unreadCodexIds.isEmpty()) return state
        return state.copy(unreadCodexIds = emptySet())
    }

    /**
     * Advances the guided Intro Quest step deterministically when the player hits a milestone.
     */
    fun onTelemetrySynced(state: GameState): GameState {
        val next = if (state.introQuest.currentStep == IntroQuestStep.SYNC_SPARKY) {
            state.copy(
                introQuest = state.introQuest.copy(currentStep = IntroQuestStep.VISIT_EMBERHEARTH),
                recentLog = (listOf("Intro Quest: Telemetry attuned! Next, visit Bram Ironbrow's Shop in Emberhearth.") + state.recentLog).take(30),
            )
        } else {
            state
        }
        return syncCodexUnlocks(next)
    }

    fun onVisitedShop(state: GameState): GameState {
        val next = if (state.introQuest.currentStep == IntroQuestStep.VISIT_EMBERHEARTH) {
            state.copy(
                introQuest = state.introQuest.copy(currentStep = IntroQuestStep.START_FIRST_ACTIVITY),
                recentLog = (listOf("Intro Quest: Met Bram Ironbrow! Next, start a Town Activity in Emberhearth.") + state.recentLog).take(30),
            )
        } else {
            state
        }
        return syncCodexUnlocks(next)
    }

    fun onStartedActivity(state: GameState): GameState {
        val next = if (state.introQuest.currentStep == IntroQuestStep.START_FIRST_ACTIVITY) {
            state.copy(
                introQuest = state.introQuest.copy(currentStep = IntroQuestStep.ACCEPT_INTRO_QUEST),
                recentLog = (listOf("Intro Quest: Activity started! Now accept 'The Ember Nest' delve to claim your first Monster Egg.") + state.recentLog).take(30),
            )
        } else {
            state
        }
        return syncCodexUnlocks(next)
    }

    /**
     * Called when the player completes the Intro Quest delve ("The Ember Nest") —
     * promotes the player from Level 0 -> Level 1 and awards their very first Monster Egg!
     */
    fun onIntroContractCleared(state: GameState): GameState {
        if (state.introQuest.firstEggClaimed) return syncCodexUnlocks(state)

        val firstEgg = MonsterEgg(
            id = "egg_first_ember_nest",
            name = "Warm Ember Egg",
            speciesSeed = "intro_first_egg",
            requiredSleepMinutes = 900,
            requiredHydrationMl = 4500,
        )

        val promotedPlayer = state.player.copy(
            level = maxOf(1, state.player.level),
            gold = state.player.gold + 35,
            ironOre = state.player.ironOre + 8,
            emberShards = state.player.emberShards + 4,
        )

        val next = state.copy(
            player = promotedPlayer,
            eggs = state.eggs + firstEgg,
            introQuest = state.introQuest.copy(
                currentStep = IntroQuestStep.COMPLETED,
                firstEggClaimed = true,
            ),
            recentLog = (listOf("INTRO COMPLETE! Reached Level 1 and claimed your first Monster Egg (Warm Ember Egg)!") + state.recentLog).take(30),
        )
        return syncCodexUnlocks(next)
    }
}
