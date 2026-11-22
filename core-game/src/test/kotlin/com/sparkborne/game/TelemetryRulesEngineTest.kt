package com.sparkborne.game

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.CombatStatsCalculator
import com.sparkborne.game.engine.ExpeditionManager
import com.sparkborne.game.engine.TelemetryRulesEngine
import com.sparkborne.game.model.MomentumStance
import com.sparkborne.game.model.MonsterAffinity
import com.sparkborne.telemetry.dto.CheckInMeasurementDto
import com.sparkborne.telemetry.dto.DailySummaryResponseDto
import com.sparkborne.telemetry.dto.ExerciseEntryDto
import com.sparkborne.telemetry.dto.ExerciseSessionDto
import com.sparkborne.telemetry.dto.ExerciseSetDto
import com.sparkborne.telemetry.dto.ExerciseSnapshotDto
import com.sparkborne.telemetry.dto.FoodEntryDto
import com.sparkborne.telemetry.dto.SleepEntryDto
import com.sparkborne.telemetry.dto.UserGoalDto
import com.sparkborne.telemetry.model.BodyweightTrend
import com.sparkborne.telemetry.model.MuscleGroup
import com.sparkborne.telemetry.model.WeightTrendDirection
import com.sparkborne.telemetry.normalizer.SnapshotIdempotency
import com.sparkborne.telemetry.normalizer.TelemetryNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryRulesEngineTest {

    @Test
    fun `lifting muscle-group volume scales matching weapon damage and is strictly idempotent`() {
        val initial = GameCatalog.initialGameState()
        val beforeStats = CombatStatsCalculator.calculate(initial)

        val summary = DailySummaryResponseDto(
            goals = UserGoalDto(calories = 2400.0, protein = 180.0, carbs = 240.0, fat = 70.0, waterGoalMl = 3000.0),
            exerciseSessions = listOf(
                ExerciseSessionDto(
                    id = "s1",
                    type = "preset",
                    exercises = listOf(
                        ExerciseEntryDto(
                            name = "Bench Press",
                            sets = listOf(
                                ExerciseSetDto(reps = 10.0, weight = 100.0),
                                ExerciseSetDto(reps = 10.0, weight = 100.0),
                                ExerciseSetDto(reps = 10.0, weight = 100.0),
                            ),
                            exerciseSnapshot = ExerciseSnapshotDto(
                                name = "Bench Press",
                                primaryMuscles = listOf("pectorals"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val snap = TelemetryNormalizer.normalize("2026-11-22", summary, null, emptyList(), 1_000L)
        val delta = SnapshotIdempotency.computeDelta(null, snap)
        val trend = BodyweightTrend("2026-11-22", 82.0, 82.0, 81.5, 0.5, WeightTrendDirection.GAINING, 10)

        val first = TelemetryRulesEngine.applyTelemetry(initial, snap, delta, trend)
        assertTrue(first.applied)
        assertEquals(3000.0, first.state.player.muscleStrengthXp[MuscleGroup.CHEST]!!, 0.01)
        assertEquals(MomentumStance.JUGGERNAUT, first.state.player.activeBuffs.momentumStance)

        val afterStats = CombatStatsCalculator.calculate(first.state)
        assertTrue("Greatsword damage should increase from Chest lifting volume", afterStats.weaponDamage > beforeStats.weaponDamage)

        // Re-applying identical snapshot hash must be a strict no-op
        val second = TelemetryRulesEngine.applyTelemetry(first.state, snap, delta, trend)
        assertFalse(second.applied)
        assertEquals(first.state.player.muscleStrengthXp, second.state.player.muscleStrengthXp)
    }

    @Test
    fun `sleep and hydration incubate and auto-hatch starter egg with macro-driven affinity`() {
        var state = GameCatalog.initialGameState()
        assertEquals(1, state.eggs.size)
        assertEquals(1, state.monsters.size)

        // Day 1 & Day 2 with high protein, 8h sleep, 2500mL water -> exceeds starter egg 900m sleep & 4500mL water
        for (day in listOf("2026-11-21", "2026-11-22")) {
            val summary = DailySummaryResponseDto(
                goals = UserGoalDto(calories = 2200.0, protein = 160.0, carbs = 220.0, fat = 70.0, waterGoalMl = 2500.0),
                foodEntries = listOf(FoodEntryDto(calories = 2100.0, protein = 175.0, carbs = 140.0, fat = 50.0)),
                waterIntake = 2500.0,
            )
            val sleep = listOf(SleepEntryDto(entryDate = day, durationInSeconds = 28_800L)) // 480 min
            val snap = TelemetryNormalizer.normalize(day, summary, CheckInMeasurementDto(entryDate = day, steps = 5000.0), sleep, 1_000L)
            val delta = SnapshotIdempotency.computeDelta(null, snap)
            state = TelemetryRulesEngine.applyTelemetry(state, snap, delta, null).state
        }

        assertTrue("Starter egg should have hatched", state.eggs.isEmpty())
        assertEquals(2, state.monsters.size)
        val hatched = state.monsters.last()
        assertEquals(MonsterAffinity.IRONCLAD, hatched.affinity)
        assertEquals("Ferrite Cub", hatched.name)
    }

    @Test
    fun `monster evolves deterministically once XP threshold is reached`() {
        var state = GameCatalog.initialGameState()
        val starter = state.monsters.first()
        state = state.copy(
            monsters = listOf(starter.copy(xp = 140)),
        )

        val evolved = ExpeditionManager.evolveMonster(state, starter.id).getOrThrow()
        val updated = evolved.monsters.first()
        assertEquals("Pyre Wyvern", updated.name)
        assertTrue(updated.baseAttack > starter.baseAttack)
        assertEquals(2, updated.evolutionHistory.size)
    }
}
