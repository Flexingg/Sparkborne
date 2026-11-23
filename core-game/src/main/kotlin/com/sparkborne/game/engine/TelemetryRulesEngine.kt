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
            val eggSpeedMult = 1.0 + (
                CollectibleEngine.totalBonus(state, com.sparkborne.game.model.CollectibleBonusType.EGG_INCUBATION_PERCENT) +
                    TalentEngine.stat(state.player, com.sparkborne.game.model.TalentStat.EGG_PCT)
                ) / 100.0
            val updatedEgg = egg.copy(
                incubatedSleepMinutes = egg.incubatedSleepMinutes + (delta.sleepMinutesDelta * eggSpeedMult).roundToInt(),
                incubatedHydrationMl = egg.incubatedHydrationMl + (delta.hydrationMlDelta * eggSpeedMult).roundToInt(),
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

        // Apply non-step contract progress (lifting/protein/hydration/sleep) to ActiveExpedition;
        // Steps always build up cleanly in the Step Bank so the player can spend them like Action Points!
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

        // 6. Bank ALL reconciled real-world fitness metrics into their Action Point Banks!
        var stepsAccepted = 0
        if (reconciledDelta.stepsDelta > 0) {
            val effectiveSteps = (reconciledDelta.stepsDelta * stance.stepEfficiencyMultiplier).roundToInt()
            val (bankedState, accepted) = StepBankManager.depositSteps(nextState, effectiveSteps)
            nextState = bankedState
            stepsAccepted = accepted
            if (accepted > 0) {
                events.add("Banked +$accepted steps (${nextState.player.savedSteps}/${ActionBankManager.capacityOf(nextState.player, com.sparkborne.game.model.ActionBankType.STEPS)}).")
            }
        }

        // 6b. Bank Push Volume (Chest/Shoulders/Triceps) & Pull/Legs Volume from lifting deltas
        var pushDeltaKg = 0.0
        var pullLegsDeltaKg = 0.0
        for ((group, volDelta) in delta.effectiveMuscleVolumeKgDeltas) {
            if (volDelta > 0.0) {
                if (ActionBankManager.isPushMuscle(group)) {
                    pushDeltaKg += volDelta
                } else {
                    pullLegsDeltaKg += volDelta
                }
            }
        }
        if (pushDeltaKg > 0.0) {
            val (s, acc) = ActionBankManager.deposit(nextState, com.sparkborne.game.model.ActionBankType.PUSH_VOLUME, pushDeltaKg.roundToInt())
            nextState = s
            if (acc > 0) events.add("💪 Banked +${acc}kg Push Volume (Mining/Smithing AP).")
        }
        if (pullLegsDeltaKg > 0.0) {
            val (s, acc) = ActionBankManager.deposit(nextState, com.sparkborne.game.model.ActionBankType.PULL_LEGS_VOLUME, pullLegsDeltaKg.roundToInt())
            nextState = s
            if (acc > 0) events.add("🪓 Banked +${acc}kg Pull/Legs Volume (Woodcutting/Fishing AP).")
        }
        if (delta.hydrationMlDelta > 0) {
            val (s, acc) = ActionBankManager.deposit(nextState, com.sparkborne.game.model.ActionBankType.HYDRATION_ML, delta.hydrationMlDelta)
            nextState = s
            if (acc > 0) events.add("💧 Banked +${acc}mL Hydration (Diving/Alchemy AP).")
        }
        if (delta.activeCaloriesBurnedDelta > 0.0) {
            val (s, acc) = ActionBankManager.deposit(nextState, com.sparkborne.game.model.ActionBankType.CALORIES_BURNT, delta.activeCaloriesBurnedDelta.roundToInt())
            nextState = s
            if (acc > 0) events.add("🔥 Banked +${acc}kcal Energy Burn (Combat/Runecrafting AP).")
        }

        // 7. Accumulate "While You Were Away" Chronicle Summary
        val incomingSummary = com.sparkborne.game.model.AwaySummary(
            stepsBanked = stepsAccepted,
            liftingKgAdded = delta.totalLiftingVolumeKgDelta,
            sleepMinutesAdded = delta.sleepMinutesDelta,
            hydrationMlAdded = delta.hydrationMlDelta,
            eggsHatched = hatchedMonsters.map { it.name },
            highlights = events.take(6),
        )
        val mergedAway = if (incomingSummary.hasAnythingToShow) {
            state.pendingAwaySummary?.merge(incomingSummary) ?: incomingSummary
        } else {
            state.pendingAwaySummary
        }

        val mergedLog = (events + state.recentLog).take(30)
        nextState = SparkpediaEngine.onTelemetrySynced(
            nextState.copy(
                recentLog = mergedLog,
                pendingAwaySummary = mergedAway,
            ),
        )

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
        val updatedNodes = expedition.nodes

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
