package com.sparkborne.game.engine

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.model.ActiveExpedition
import com.sparkborne.game.model.DailyDelveBuffs
import com.sparkborne.game.model.ExpeditionStatus
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.MacroEvolutionProfile
import com.sparkborne.game.model.MomentumStance
import com.sparkborne.game.model.MonsterCompanion
import com.sparkborne.game.model.MonsterEgg
import com.sparkborne.game.model.MonsterStage
import com.sparkborne.telemetry.model.BodyweightTrend
import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import com.sparkborne.telemetry.model.TelemetryDelta
import com.sparkborne.telemetry.model.WeightTrendDirection
import kotlin.math.min
import kotlin.math.roundToInt

data class TelemetryApplyResult(
    val state: GameState,
    val applied: Boolean,
    val events: List<String>,
)

/**
 * Pure, deterministic bridge from Phase 1's [DailyTelemetrySnapshot] + [TelemetryDelta]
 * into Phase 2's [GameState].
 *
 * Strictly idempotent: if `processedDateHashes[snapshot.date] == snapshot.contentHash`,
 * no rewards, steps, or incubation progress are double-counted.
 */
object TelemetryRulesEngine {

    /**
     * Applies a historical backfill day (strictly prior to today):
     * - Seeds permanent Muscle Strength XP from historical lifting volume (idempotent via contentHash).
     * - Records `processedDateHashes` and `dailyCreditedSteps` high-water mark.
     * - Does NOT dump years of historical steps into the Step Bank or auto-hatch/complete quests.
     */
    fun applyHistoricalBackfill(
        state: GameState,
        snapshot: DailyTelemetrySnapshot,
        delta: TelemetryDelta,
    ): TelemetryApplyResult {
        if (state.processedDateHashes[snapshot.date] == snapshot.contentHash) {
            return TelemetryApplyResult(state = state, applied = false, events = emptyList())
        }

        val updatedMuscleXp = state.player.muscleStrengthXp.toMutableMap()
        if (delta.effectiveMuscleVolumeKgDeltas.isNotEmpty()) {
            for ((group, volDelta) in delta.effectiveMuscleVolumeKgDeltas) {
                if (volDelta > 0.0) {
                    updatedMuscleXp[group] = (updatedMuscleXp[group] ?: 0.0) + volDelta
                }
            }
        }

        val prevCredited = state.dailyCreditedSteps[snapshot.date] ?: 0
        val nextState = state.copy(
            player = state.player.copy(muscleStrengthXp = updatedMuscleXp),
            processedDateHashes = state.processedDateHashes + (snapshot.date to snapshot.contentHash),
            dailyCreditedSteps = state.dailyCreditedSteps + (snapshot.date to maxOf(prevCredited, snapshot.steps)),
        )
        return TelemetryApplyResult(state = nextState, applied = true, events = emptyList())
    }

    fun applyTelemetry(
        state: GameState,
        snapshot: DailyTelemetrySnapshot,
        delta: TelemetryDelta,
        trend: BodyweightTrend?,
    ): TelemetryApplyResult {
        if (state.processedDateHashes[snapshot.date] == snapshot.contentHash) {
            return TelemetryApplyResult(state = state, applied = false, events = emptyList())
        }

        val events = mutableListOf<String>()
        val isFirstTimeForDate = !state.processedDateHashes.containsKey(snapshot.date)

        // 1. Refresh daily delve buffs from latest snapshot + bodyweight trend
        val stance = when (trend?.direction) {
            WeightTrendDirection.GAINING -> MomentumStance.JUGGERNAUT
            WeightTrendDirection.LOSING -> MomentumStance.PROWLER
            WeightTrendDirection.STABLE -> MomentumStance.EQUILIBRIUM
            else -> MomentumStance.UNALIGNED
        }

        val newBuffs = DailyDelveBuffs(
            asOfDate = snapshot.date,
            sleepVitalityHpBonus = (snapshot.adherence.sleepRatio.coerceIn(0.0, 1.25) * 40.0).roundToInt(),
            hydrationClarityManaBonus = (snapshot.adherence.hydrationRatio.coerceIn(0.0, 1.25) * 16.0).roundToInt(),
            proteinFortitudeDefenseBonus = (snapshot.adherence.proteinRatio.coerceIn(0.0, 1.25) * 6.0).roundToInt(),
            carbsSpellPowerBonus = (snapshot.adherence.carbsRatio.coerceIn(0.0, 1.25) * 10.0).roundToInt(),
            fatEnduranceCritBonusPercent = (snapshot.adherence.fatRatio.coerceIn(0.0, 1.25) * 10.0).roundToInt(),
            momentumStance = stance,
        )

        // 2. Apply lifting muscle-group deltas -> Muscle Strength XP
        val updatedMuscleXp = state.player.muscleStrengthXp.toMutableMap()
        if (delta.effectiveMuscleVolumeKgDeltas.isNotEmpty()) {
            for ((group, volDelta) in delta.effectiveMuscleVolumeKgDeltas) {
                if (volDelta > 0.0) {
                    val prev = updatedMuscleXp[group] ?: 0.0
                    updatedMuscleXp[group] = prev + volDelta
                }
            }
            events.add("Lifting +${delta.totalLiftingVolumeKgDelta.roundToInt()} kg applied across ${delta.effectiveMuscleVolumeKgDeltas.size} muscle group(s).")
        }

        val updatedPlayer = state.player.copy(
            muscleStrengthXp = updatedMuscleXp,
            activeBuffs = newBuffs,
        )

        // 3. Apply sleep & hydration deltas -> Egg Incubation & Auto-Hatching
        val hatchedMonsters = mutableListOf<MonsterCompanion>()
        val remainingEggs = mutableListOf<MonsterEgg>()

        for (egg in state.eggs) {
            val updatedMacro = if (isFirstTimeForDate && snapshot.nutrition.calories > 0) {
                egg.macroProfile.recordDay(snapshot)
            } else {
                egg.macroProfile
            }
            val updatedEgg = egg.copy(
                incubatedSleepMinutes = egg.incubatedSleepMinutes + delta.sleepMinutesDelta,
                incubatedHydrationMl = egg.incubatedHydrationMl + delta.hydrationMlDelta,
                macroProfile = updatedMacro,
            )
            if (updatedEgg.isReadyToHatch) {
                val affinity = updatedEgg.macroProfile.dominantAffinity()
                val species = GameCatalog.monsterSpeciesName(affinity, MonsterStage.HATCHLING)
                val hatched = MonsterCompanion(
                    id = "mon_${updatedEgg.id}",
                    name = species,
                    speciesId = species.lowercase().replace(' ', '_'),
                    stage = MonsterStage.HATCHLING,
                    level = 1,
                    xp = 0,
                    affinity = affinity,
                    baseAttack = 8,
                    baseDefense = 4,
                    macroProfile = updatedEgg.macroProfile,
                    evolutionHistory = listOf(species),
                )
                hatchedMonsters.add(hatched)
                events.add("Egg hatched! ${hatched.name} (${affinity.displayName}) joined your party.")
            } else {
                remainingEggs.add(updatedEgg)
            }
        }

        // 4. Feed active monster macro profile & XP from daily macro adherence
        val updatedMonsters = (state.monsters + hatchedMonsters).map { mon ->
            if (mon.id == (state.activeMonsterId ?: state.monsters.firstOrNull()?.id) && isFirstTimeForDate) {
                val macroXp = computeMonsterDailyMacroXp(snapshot)
                val newProfile = if (snapshot.nutrition.calories > 0) {
                    mon.macroProfile.recordDay(snapshot)
                } else {
                    mon.macroProfile
                }
                if (macroXp > 0) {
                    events.add("${mon.name} gained +$macroXp XP from daily nutrition.")
                }
                mon.copy(
                    xp = mon.xp + macroXp,
                    level = 1 + ((mon.xp + macroXp) / 80),
                    macroProfile = newProfile,
                )
            } else {
                mon
            }
        }

        // 5. Reconcile synced steps against dailyCreditedSteps (prevents double-counting with live phone pedometer!)
        val prevCreditedSteps = state.dailyCreditedSteps[snapshot.date] ?: 0
        val netSyncedStepsDelta = if (prevCreditedSteps > 0) {
            (snapshot.steps - prevCreditedSteps).coerceAtLeast(0)
        } else {
            delta.stepsDelta
        }
        val reconciledDelta = delta.copy(stepsDelta = netSyncedStepsDelta)
        val newCreditedHighWater = maxOf(prevCreditedSteps, snapshot.steps, prevCreditedSteps + netSyncedStepsDelta)

        // Apply steps & contract progress to ActiveExpedition
        val updatedExpedition = state.activeExpedition?.let { exp ->
            if (exp.status == ExpeditionStatus.ACTIVE && snapshot.date >= exp.startedDate) {
                applyTelemetryToExpedition(
                    expedition = exp,
                    snapshot = snapshot,
                    delta = reconciledDelta,
                    isFirstTimeForDate = isFirstTimeForDate,
                    stepEfficiency = stance.stepEfficiencyMultiplier,
                    events = events,
                )
            } else {
                exp
            }
        }

        val updatedHashes = state.processedDateHashes + (snapshot.date to snapshot.contentHash)
        val updatedCreditedMap = state.dailyCreditedSteps + (snapshot.date to newCreditedHighWater)

        var nextState = state.copy(
            player = updatedPlayer,
            monsters = updatedMonsters,
            activeMonsterId = state.activeMonsterId ?: updatedMonsters.firstOrNull()?.id,
            eggs = remainingEggs,
            activeExpedition = updatedExpedition,
            processedDateHashes = updatedHashes,
            dailyCreditedSteps = updatedCreditedMap,
        )

        // 6. Apply reconciled steps: overflow from active delve or idle steps bank cleanly into Step Bank!
        val isDelveActive = updatedExpedition?.status == ExpeditionStatus.ACTIVE
        if (reconciledDelta.stepsDelta > 0) {
            val effectiveSteps = (reconciledDelta.stepsDelta * stance.stepEfficiencyMultiplier).roundToInt()
            if (isDelveActive) {
                val prevNode = state.activeExpedition?.currentNode
                val nextNode = updatedExpedition?.currentNode
                val consumedByNode = if (prevNode != null && nextNode != null && prevNode.index == nextNode.index) {
                    (nextNode.stepsApplied - prevNode.stepsApplied).coerceAtLeast(0)
                } else {
                    0
                }
                val overflow = (effectiveSteps - consumedByNode).coerceAtLeast(0)
                if (overflow > 0) {
                    val (bankedState, accepted) = StepBankManager.depositSteps(nextState, overflow)
                    nextState = bankedState
                    if (accepted > 0) {
                        events.add("Banked +$accepted extra steps (${nextState.player.savedSteps}/${nextState.player.maxStepBankCapacity()}).")
                    }
                }
            } else if (nextState.activeTravel != null || nextState.activeActivity != null) {
                nextState = WorldManager.applyStepsToWorld(nextState, effectiveSteps, events)
            } else {
                val (bankedState, accepted) = StepBankManager.depositSteps(nextState, effectiveSteps)
                nextState = bankedState
                if (accepted > 0) {
                    events.add("Banked +$accepted steps (${nextState.player.savedSteps}/${nextState.player.maxStepBankCapacity()}).")
                }
            }
        }

        val mergedLog = (events + state.recentLog).take(30)
        nextState = SparkpediaEngine.onTelemetrySynced(nextState.copy(recentLog = mergedLog))

        return TelemetryApplyResult(state = nextState, applied = true, events = events)
    }

    private fun applyTelemetryToExpedition(
        expedition: ActiveExpedition,
        snapshot: DailyTelemetrySnapshot,
        delta: TelemetryDelta,
        isFirstTimeForDate: Boolean,
        stepEfficiency: Double,
        events: MutableList<String>,
    ): ActiveExpedition {
        val effectiveSteps = (delta.stepsDelta * stepEfficiency).roundToInt()
        var remainingSteps = effectiveSteps

        val updatedNodes = expedition.nodes.map { node ->
            if (node.index == expedition.currentNodeIndex && !node.cleared && remainingSteps > 0) {
                val needed = (node.stepsRequired - node.stepsApplied).coerceAtLeast(0)
                val appliedHere = min(needed, remainingSteps)
                remainingSteps -= appliedHere
                val nextApplied = node.stepsApplied + appliedHere
                if (node.stepsApplied < node.stepsRequired && nextApplied >= node.stepsRequired) {
                    events.add("Delve node unlocked: ${node.title}!")
                }
                node.copy(stepsApplied = nextApplied)
            } else {
                node
            }
        }

        val prevProg = expedition.progress
        val updatedProgress = prevProg.copy(
            totalStepsLogged = prevProg.totalStepsLogged + delta.stepsDelta,
            totalLiftingVolumeKg = prevProg.totalLiftingVolumeKg + delta.totalLiftingVolumeKgDelta,
            proteinGoalDaysMet = prevProg.proteinGoalDaysMet +
                if (isFirstTimeForDate && snapshot.adherence.proteinRatio >= 0.90) 1 else 0,
            hydrationGoalDaysMet = prevProg.hydrationGoalDaysMet +
                if (isFirstTimeForDate && snapshot.adherence.hydrationRatio >= 0.85) 1 else 0,
            sleepGoalDaysMet = prevProg.sleepGoalDaysMet +
                if (isFirstTimeForDate && snapshot.adherence.sleepRatio >= 0.85) 1 else 0,
        )

        if (effectiveSteps > 0) {
            events.add("+$effectiveSteps adventure steps applied to ${expedition.contract.title}.")
        }

        return expedition.copy(
            nodes = updatedNodes,
            progress = updatedProgress,
        )
    }

    private fun computeMonsterDailyMacroXp(snapshot: DailyTelemetrySnapshot): Int {
        var xp = 0
        if (snapshot.adherence.proteinRatio >= 0.90) xp += 25
        if (snapshot.adherence.caloriesRatio in 0.85..1.15) xp += 20
        if (snapshot.adherence.hydrationRatio >= 0.85) xp += 15
        return xp
    }

    private fun MacroEvolutionProfile.recordDay(snapshot: DailyTelemetrySnapshot): MacroEvolutionProfile {
        return copy(
            proteinRatioSum = proteinRatioSum + snapshot.adherence.proteinRatio,
            carbsRatioSum = carbsRatioSum + snapshot.adherence.carbsRatio,
            fatRatioSum = fatRatioSum + snapshot.adherence.fatRatio,
            caloriesRatioSum = caloriesRatioSum + snapshot.adherence.caloriesRatio,
            daysTracked = daysTracked + 1,
        )
    }
}
