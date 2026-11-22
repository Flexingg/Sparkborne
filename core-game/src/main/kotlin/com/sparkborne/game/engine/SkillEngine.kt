package com.sparkborne.game.engine

import com.sparkborne.game.model.ChestOpenReward
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.MonsterEgg
import com.sparkborne.game.model.SkillType
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Deterministic engine for WalkScape-style Skill Levels (1–99), Work Efficiency scaling,
 * and Skill Chest rewards.
 */
object SkillEngine {

    const val MAX_SKILL_LEVEL = 99
    const val COMPLETIONS_PER_CHEST = 5

    /**
     * Smooth quadratic XP curve:
     * - Lv 1 = 0 XP
     * - Lv 2 = 50 XP
     * - Lv 10 = ~1,800 XP
     * - Lv 50 = ~60,000 XP
     * - Lv 99 = ~240,100 XP
     */
    fun xpForLevel(level: Int): Int {
        val clamped = level.coerceIn(1, MAX_SKILL_LEVEL)
        if (clamped <= 1) return 0
        val n = clamped - 1
        return 25 * n * n + 25 * n
    }

    fun levelForXp(xp: Int): Int {
        if (xp <= 0) return 1
        // Solve 25*n^2 + 25*n <= xp -> n = floor((-1 + sqrt(1 + 4*xp/25)) / 2)
        val n = floor((-1.0 + sqrt(1.0 + (4.0 * xp.toDouble()) / 25.0)) / 2.0).toInt()
        return (n + 1).coerceIn(1, MAX_SKILL_LEVEL)
    }

    fun xpToNextLevel(xp: Int): Pair<Int, Int> {
        val level = levelForXp(xp)
        if (level >= MAX_SKILL_LEVEL) return 1 to 1
        val currentFloor = xpForLevel(level)
        val nextFloor = xpForLevel(level + 1)
        return (xp - currentFloor).coerceAtLeast(0) to (nextFloor - currentFloor).coerceAtLeast(1)
    }

    /**
     * Every 2 skill levels grants +1% Work Efficiency (up to +49% at Lv 99).
     */
    fun workEfficiencyPercent(level: Int): Int = ((level - 1) / 2).coerceIn(0, 49)

    /**
     * Computes effective steps required per action cycle after Skill Work Efficiency
     * (and future Toolbelt / Collectible / Consumable reductions).
     * Always floors at 35% of base steps to keep progression balanced.
     */
    fun effectiveStepsPerAction(
        baseSteps: Int,
        state: GameState,
        skill: SkillType,
    ): Int {
        val level = levelForXp(state.player.skillXp[skill] ?: 0)
        val effPct = workEfficiencyPercent(level) +
            CollectibleEngine.totalBonus(state, com.sparkborne.game.model.CollectibleBonusType.WORK_EFFICIENCY_PERCENT) +
            ToolbeltEngine.workEfficiencyBonus(state, skill)
        val multiplier = (100.0 / (100.0 + effPct)).coerceAtLeast(0.35)
        return (baseSteps * multiplier).roundToInt().coerceAtLeast((baseSteps * 0.35).roundToInt().coerceAtLeast(100))
    }

    /**
     * Awards skill XP and deterministic Skill Chests (1 chest per 5 completions in an activity).
     */
    fun awardSkillProgress(
        state: GameState,
        skill: SkillType,
        xpGain: Int,
        previousCompletions: Int,
        newCompletions: Int,
        events: MutableList<String>,
    ): GameState {
        val prevXp = state.player.skillXp[skill] ?: 0
        val prevLevel = levelForXp(prevXp)
        val nextXp = prevXp + xpGain
        val nextLevel = levelForXp(nextXp)

        val prevChestBuckets = previousCompletions / COMPLETIONS_PER_CHEST
        val nextChestBuckets = newCompletions / COMPLETIONS_PER_CHEST
        val chestsEarned = (nextChestBuckets - prevChestBuckets).coerceAtLeast(0)

        if (nextLevel > prevLevel) {
            events.add("${skill.icon} ${skill.displayName} leveled up to Lv.$nextLevel! (+${workEfficiencyPercent(nextLevel)}% Work Efficiency)")
        }
        if (chestsEarned > 0) {
            events.add("🎁 Found $chestsEarned ${skill.displayName} Skill Chest(s)!")
        }

        val updatedChests = if (chestsEarned > 0) {
            state.player.skillChests + (skill to ((state.player.skillChests[skill] ?: 0) + chestsEarned))
        } else {
            state.player.skillChests
        }

        return state.copy(
            player = state.player.copy(
                skillXp = state.player.skillXp + (skill to nextXp),
                skillChests = updatedChests,
            ),
        )
    }

    /**
     * Deterministically opens 1 Skill Chest for [skill], scaling rewards with your skill level.
     * Every 5th chest opened across all skills guarantees a rare Skill-Themed Monster Egg!
     */
    fun openSkillChest(state: GameState, skill: SkillType): Result<Pair<GameState, ChestOpenReward>> {
        val count = state.player.skillChests[skill] ?: 0
        if (count <= 0) {
            return Result.failure(IllegalStateException("No ${skill.displayName} Skill Chests to open."))
        }

        val skillLevel = levelForXp(state.player.skillXp[skill] ?: 0)
        val openIndex = state.player.totalChestsOpened + 1
        val tierScale = 1.0 + (skillLevel - 1) * 0.04

        val baseGold = when (skill) {
            SkillType.TRADE, SkillType.ARCHAEOLOGY -> 55
            SkillType.SMITHING, SkillType.RUNECRAFTING -> 40
            else -> 32
        }
        val baseIron = when (skill) {
            SkillType.MINING, SkillType.SMITHING, SkillType.SALVAGE -> 12
            SkillType.WOODCUTTING, SkillType.DIVING -> 6
            else -> 3
        }
        val baseShards = when (skill) {
            SkillType.RUNECRAFTING, SkillType.ALCHEMY, SkillType.ARCHAEOLOGY -> 8
            SkillType.DIVING, SkillType.FORAGING -> 5
            else -> 3
        }

        val gold = (baseGold * tierScale).roundToInt()
        val iron = (baseIron * tierScale).roundToInt()
        val shards = (baseShards * tierScale).roundToInt()
        val bonusXp = (40 + skillLevel * 6)

        val eggAwarded = if (openIndex % 5 == 0) {
            MonsterEgg(
                id = "egg_chest_${skill.name.lowercase()}_$openIndex",
                name = "${skill.displayName} Relic Egg",
                speciesSeed = "chest_${skill.name.lowercase()}_$openIndex",
                requiredSleepMinutes = 840,
                requiredHydrationMl = 4200,
            )
        } else {
            null
        }

        val remainingMap = if (count > 1) {
            state.player.skillChests + (skill to (count - 1))
        } else {
            state.player.skillChests - skill
        }

        val nextSkillXp = (state.player.skillXp[skill] ?: 0) + bonusXp
        val summary = buildString {
            append("Opened ${skill.icon} ${skill.displayName} Chest: +${gold}g, +$iron Iron, +$shards Shards, +$bonusXp ${skill.displayName} XP")
            if (eggAwarded != null) append(" + RARE ${eggAwarded.name}!")
        }

        val nextState = state.copy(
            player = state.player.copy(
                gold = state.player.gold + gold,
                ironOre = state.player.ironOre + iron,
                emberShards = state.player.emberShards + shards,
                skillXp = state.player.skillXp + (skill to nextSkillXp),
                skillChests = remainingMap,
                totalChestsOpened = openIndex,
            ),
            eggs = if (eggAwarded != null) state.eggs + eggAwarded else state.eggs,
            recentLog = (listOf(summary) + state.recentLog).take(30),
        )

        val reward = ChestOpenReward(
            skill = skill,
            gold = gold,
            iron = iron,
            shards = shards,
            bonusSkillXp = bonusXp,
            eggAwarded = eggAwarded,
            summary = summary,
        )
        return Result.success(SparkpediaEngine.syncCodexUnlocks(nextState) to reward)
    }
}
