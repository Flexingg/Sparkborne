package com.sparkborne.game.engine

import com.sparkborne.game.model.ActionBankType
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.PlayerState
import com.sparkborne.game.model.SkillType
import com.sparkborne.game.model.TownActivity
import com.sparkborne.telemetry.model.MuscleGroup
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic manager for all 5 real-world fitness Action Point Banks:
 * - [ActionBankType.STEPS] (Travel, Foraging, Archaeology, Trade, Delve Nodes)
 * - [ActionBankType.PUSH_VOLUME] (Mining, Smithing)
 * - [ActionBankType.PULL_LEGS_VOLUME] (Woodcutting, Fishing, Salvage)
 * - [ActionBankType.HYDRATION_ML] (Diving / Swimming, Alchemy)
 * - [ActionBankType.CALORIES_BURNT] (Delve Monster Combat, Runecrafting)
 *
 * Leveling up each bank's matching skills permanently increases that bank's maximum capacity!
 */
object ActionBankManager {

    private val PUSH_MUSCLES = setOf(
        MuscleGroup.CHEST,
        MuscleGroup.SHOULDERS,
        MuscleGroup.TRICEPS,
    )

    fun isPushMuscle(group: MuscleGroup): Boolean = group in PUSH_MUSCLES

    fun bankForSkill(skill: SkillType): ActionBankType = when (skill) {
        SkillType.MINING, SkillType.SMITHING -> ActionBankType.PUSH_VOLUME
        SkillType.WOODCUTTING, SkillType.FISHING, SkillType.SALVAGE -> ActionBankType.PULL_LEGS_VOLUME
        SkillType.DIVING, SkillType.ALCHEMY -> ActionBankType.HYDRATION_ML
        SkillType.RUNECRAFTING -> ActionBankType.CALORIES_BURNT
        SkillType.FORAGING, SkillType.ARCHAEOLOGY, SkillType.TRADE -> ActionBankType.STEPS
    }

    fun balanceOf(player: PlayerState, bank: ActionBankType): Int = when (bank) {
        ActionBankType.STEPS -> player.savedSteps
        else -> player.actionBanks[bank] ?: 0
    }

    /**
     * Computes maximum bank capacity, scaling cleanly with the specific skills that consume that bank!
     */
    fun capacityOf(player: PlayerState, bank: ActionBankType): Int = when (bank) {
        ActionBankType.STEPS -> {
            val skillBonus = (
                (player.skillLevel(SkillType.FORAGING) - 1) +
                    (player.skillLevel(SkillType.ARCHAEOLOGY) - 1) +
                    (player.skillLevel(SkillType.TRADE) - 1)
                ) * 15_000L
            (player.maxStepBankCapacity().toLong() + skillBonus)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }

        ActionBankType.PUSH_VOLUME -> {
            val m = player.skillLevel(SkillType.MINING).toLong()
            val s = player.skillLevel(SkillType.SMITHING).toLong()
            val bonus = ((m - 1) + (s - 1)) * 5_000L + ((m - 1) * (m - 1) + (s - 1) * (s - 1)) * 250L
            (bank.baseCapacity.toLong() + bonus).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        ActionBankType.PULL_LEGS_VOLUME -> {
            val w = player.skillLevel(SkillType.WOODCUTTING).toLong()
            val f = player.skillLevel(SkillType.FISHING).toLong()
            val sv = player.skillLevel(SkillType.SALVAGE).toLong()
            val bonus = ((w - 1) + (f - 1) + (sv - 1)) * 4_500L +
                ((w - 1) * (w - 1) + (f - 1) * (f - 1) + (sv - 1) * (sv - 1)) * 200L
            (bank.baseCapacity.toLong() + bonus).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        ActionBankType.HYDRATION_ML -> {
            val d = player.skillLevel(SkillType.DIVING).toLong()
            val a = player.skillLevel(SkillType.ALCHEMY).toLong()
            val bonus = ((d - 1) + (a - 1)) * 3_000L + ((d - 1) * (d - 1) + (a - 1) * (a - 1)) * 150L
            (bank.baseCapacity.toLong() + bonus).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        ActionBankType.CALORIES_BURNT -> {
            val r = player.skillLevel(SkillType.RUNECRAFTING).toLong()
            val lvl = player.level.coerceAtLeast(0).toLong()
            val bonus = (r - 1) * 2_500L + lvl * 1_500L + (r - 1) * (r - 1) * 120L
            (bank.baseCapacity.toLong() + bonus).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    /**
     * Effective Action Point cost per completion cycle of [activity] in its native [ActionBankType],
     * reduced by Skill Work Efficiency, Collectibles, Toolbelt, and Trailblazer Brew!
     */
    fun costPerCycle(state: GameState, activity: TownActivity): Pair<ActionBankType, Int> {
        val skill = SkillType.fromTag(activity.skillTag)
        val bank = bankForSkill(skill)
        val effSteps = SkillEngine.effectiveStepsPerAction(activity.stepsPerAction, state, skill)
        val effRatio = effSteps.toDouble() / activity.stepsPerAction.coerceAtLeast(1).toDouble()

        val baseCost = when (bank) {
            ActionBankType.STEPS -> activity.stepsPerAction
            ActionBankType.PUSH_VOLUME -> (activity.stepsPerAction * 0.25).roundToInt().coerceAtLeast(150)
            ActionBankType.PULL_LEGS_VOLUME -> (activity.stepsPerAction * 0.25).roundToInt().coerceAtLeast(150)
            ActionBankType.HYDRATION_ML -> (activity.stepsPerAction * 0.45).roundToInt().coerceAtLeast(350)
            ActionBankType.CALORIES_BURNT -> (activity.stepsPerAction * 0.10).roundToInt().coerceAtLeast(80)
        }
        val finalCost = (baseCost * effRatio).roundToInt().coerceAtLeast(40)
        return bank to finalCost
    }

    /**
     * Energy Burn (kcal) cost to engage a monster encounter in a Delve.
     */
    fun combatEntryBurnCost(isBoss: Boolean): Int = if (isBoss) 100 else 50

    fun deposit(state: GameState, bank: ActionBankType, amount: Int): Pair<GameState, Int> {
        if (amount <= 0) return state to 0
        if (bank == ActionBankType.STEPS) {
            return StepBankManager.depositSteps(state, amount)
        }
        val cap = capacityOf(state.player, bank)
        val current = balanceOf(state.player, bank)
        val accepted = min(amount, (cap - current).coerceAtLeast(0))
        if (accepted <= 0) return state to 0
        val nextBanks = state.player.actionBanks + (bank to (current + accepted))
        return state.copy(player = state.player.copy(actionBanks = nextBanks)) to accepted
    }

    fun withdraw(state: GameState, bank: ActionBankType, amount: Int): Result<GameState> {
        if (amount <= 0) return Result.success(state)
        val current = balanceOf(state.player, bank)
        if (current < amount) {
            return Result.failure(
                IllegalStateException(
                    "Need $amount ${bank.unit} in your ${bank.icon} ${bank.displayName} Bank (have $current ${bank.unit}).",
                ),
            )
        }
        return if (bank == ActionBankType.STEPS) {
            Result.success(state.copy(player = state.player.copy(savedSteps = current - amount)))
        } else {
            val nextBanks = state.player.actionBanks + (bank to (current - amount))
            Result.success(state.copy(player = state.player.copy(actionBanks = nextBanks)))
        }
    }
}
