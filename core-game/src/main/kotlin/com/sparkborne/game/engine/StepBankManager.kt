package com.sparkborne.game.engine

import com.sparkborne.game.catalog.WorldCatalog
import com.sparkborne.game.model.ExpeditionStatus
import com.sparkborne.game.model.GameState
import kotlin.math.min

/**
 * WalkScape-style Step Bank manager:
 * - Real-world synced steps are banked into `player.savedSteps` up to `player.maxStepBankCapacity()`.
 * - Players can spend banked steps on-demand during a single playthrough to unlock Delve nodes,
 *   complete World Map travel, or run repeatable Town Activity cycles.
 */
object StepBankManager {

    fun depositSteps(state: GameState, steps: Int): Pair<GameState, Int> {
        if (steps <= 0) return state to 0
        val cap = state.player.maxStepBankCapacity()
        val current = state.player.savedSteps
        val accepted = min(steps, (cap - current).coerceAtLeast(0))
        if (accepted <= 0) return state to 0
        return state.copy(
            player = state.player.copy(savedSteps = current + accepted),
        ) to accepted
    }

    /**
     * Reconciles live Android hardware pedometer steps (`totalPhoneStepsToday`) against the
     * per-day high-water mark (`dailyCreditedSteps[date]`), depositing ONLY the net new steps
     * into the Step Bank so Garmin/SparkyFitness syncs never double-count.
     */
    fun applyLivePhoneSteps(
        state: GameState,
        date: String,
        totalPhoneStepsToday: Int,
    ): Pair<GameState, Int> {
        val prevCredited = state.dailyCreditedSteps[date] ?: 0
        val netNew = (totalPhoneStepsToday - prevCredited).coerceAtLeast(0)
        if (netNew <= 0) return state to 0

        val (bankedState, accepted) = depositSteps(state, netNew)
        val updatedWatermarks = bankedState.dailyCreditedSteps + (date to totalPhoneStepsToday)
        val nextAway = if (accepted >= 250) {
            val liveSummary = com.sparkborne.game.model.AwaySummary(
                stepsBanked = accepted,
                highlights = listOf("Live phone pedometer banked +$accepted steps."),
            )
            state.pendingAwaySummary?.merge(liveSummary) ?: liveSummary
        } else {
            state.pendingAwaySummary
        }
        return bankedState.copy(
            dailyCreditedSteps = updatedWatermarks,
            pendingAwaySummary = nextAway,
        ) to accepted
    }

    fun dismissAwaySummary(state: GameState): GameState =
        state.copy(pendingAwaySummary = null)

    /**
     * Spends banked steps toward the current locked Delve node (up to what's needed to unlock it).
     */
    fun spendBankedStepsOnDelve(state: GameState): Result<GameState> {
        val exp = state.activeExpedition
            ?: return Result.failure(IllegalStateException("No active delve to spend steps on."))
        if (exp.status != ExpeditionStatus.ACTIVE) {
            return Result.failure(IllegalStateException("Expedition is not active."))
        }
        val node = exp.currentNode
            ?: return Result.failure(IllegalStateException("No remaining delve nodes."))
        if (node.isUnlocked) {
            return Result.failure(IllegalStateException("${node.title} is already unlocked!"))
        }
        val available = state.player.savedSteps
        if (available <= 0) {
            return Result.failure(IllegalStateException("Your Step Bank is empty. Sync SparkyFitness to bank steps."))
        }

        val needed = (node.stepsRequired - node.stepsApplied).coerceAtLeast(0)
        val spent = min(available, needed)
        val updatedNodes = exp.nodes.map { n ->
            if (n.index == node.index) n.copy(stepsApplied = n.stepsApplied + spent) else n
        }
        val updatedExp = exp.copy(
            nodes = updatedNodes,
            progress = exp.progress.copy(
                totalStepsLogged = exp.progress.totalStepsLogged + spent,
            ),
        )
        val unlockedMsg = if (node.stepsApplied + spent >= node.stepsRequired) {
            "Unlocked ${node.title} using $spent banked steps!"
        } else {
            "Applied $spent banked steps to ${node.title}."
        }

        return Result.success(
            state.copy(
                player = state.player.copy(savedSteps = available - spent),
                activeExpedition = updatedExp,
                recentLog = (listOf(unlockedMsg) + state.recentLog).take(30),
            ),
        )
    }

    /**
     * Spends banked steps toward active World Map travel (up to arrival).
     */
    fun spendBankedStepsOnTravel(state: GameState): Result<GameState> {
        val travel = state.activeTravel
            ?: return Result.failure(IllegalStateException("You are not currently traveling."))
        val available = state.player.savedSteps
        if (available <= 0) {
            return Result.failure(IllegalStateException("Your Step Bank is empty. Sync SparkyFitness to bank steps."))
        }
        val needed = (travel.stepsRequired - travel.stepsWalked).coerceAtLeast(0)
        val spent = min(available, needed)
        val events = mutableListOf<String>()
        val afterSpend = state.copy(player = state.player.copy(savedSteps = available - spent))
        val updated = WorldManager.applyStepsToWorld(afterSpend, spent, events)
        return Result.success(
            updated.copy(recentLog = (events + updated.recentLog).take(30)),
        )
    }

    /**
     * Spends banked Action Points from the active Town Activity's native [ActionBankType]
     * (e.g. Push Volume for Mining/Smithing, Hydration for Diving/Alchemy, Pull/Legs for
     * Woodcutting/Fishing/Salvage, Energy Burn for Runecrafting, Steps for Foraging/Archaeology/Trade).
     * - If [maxCycles] is specified (e.g. `1`), spends just enough for that many cycles.
     * - If [maxCycles] is null, spends all available balance in that bank!
     */
    fun spendBankedStepsOnActivity(state: GameState, maxCycles: Int? = null): Result<GameState> {
        val activeAct = state.activeActivity
            ?: return Result.failure(IllegalStateException("No active town activity."))
        val def = WorldCatalog.activityById(activeAct.activityId)
            ?: return Result.failure(IllegalStateException("Unknown town activity."))

        val (bankType, costPerCycle) = ActionBankManager.costPerCycle(state, def)
        val available = ActionBankManager.balanceOf(state.player, bankType)
        if (available <= 0) {
            return Result.failure(
                IllegalStateException(
                    "Your ${bankType.icon} ${bankType.displayName} Bank is empty! Sync ${bankType.displayName.lowercase()} to fuel ${def.name}.",
                ),
            )
        }

        val toSpend = if (maxCycles != null && maxCycles > 0) {
            val neededForCycles = (maxCycles * costPerCycle - activeAct.stepsProgress).coerceAtLeast(1)
            min(available, neededForCycles)
        } else {
            available
        }

        val afterWithdraw = ActionBankManager.withdraw(state, bankType, toSpend)
            .getOrElse { return Result.failure(it) }
        val events = mutableListOf<String>()
        val updated = WorldManager.applyStepsToWorld(afterWithdraw, toSpend, events)
        return Result.success(
            updated.copy(recentLog = (events + updated.recentLog).take(30)),
        )
    }
}
