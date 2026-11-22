package com.sparkborne.telemetry

import com.sparkborne.telemetry.model.MuscleGroup
import com.sparkborne.telemetry.normalizer.MuscleGroupMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class MuscleGroupMapperTest {

    @Test
    fun `maps standard SparkyFitness and Free Exercise DB muscle names`() {
        assertEquals(MuscleGroup.CHEST, MuscleGroupMapper.mapMuscleName("pectorals"))
        assertEquals(MuscleGroup.BACK, MuscleGroupMapper.mapMuscleName("middle back"))
        assertEquals(MuscleGroup.BACK, MuscleGroupMapper.mapMuscleName("Lats"))
        assertEquals(MuscleGroup.SHOULDERS, MuscleGroupMapper.mapMuscleName("deltoids"))
        assertEquals(MuscleGroup.QUADS, MuscleGroupMapper.mapMuscleName("quadriceps"))
        assertEquals(MuscleGroup.HAMSTRINGS, MuscleGroupMapper.mapMuscleName("hamstrings"))
        assertEquals(MuscleGroup.CORE, MuscleGroupMapper.mapMuscleName("abdominals"))
        assertEquals(MuscleGroup.TRICEPS, MuscleGroupMapper.mapMuscleName("triceps"))
        assertEquals(MuscleGroup.BICEPS, MuscleGroupMapper.mapMuscleName("biceps"))
        assertEquals(MuscleGroup.GLUTES, MuscleGroupMapper.mapMuscleName("glutes"))
    }

    @Test
    fun `infers muscle groups from common Liftosaur exercise names when metadata is missing`() {
        assertEquals(listOf(MuscleGroup.CHEST), MuscleGroupMapper.inferFromExerciseName("Barbell Bench Press"))
        assertEquals(listOf(MuscleGroup.QUADS), MuscleGroupMapper.inferFromExerciseName("Back Squat"))
        assertEquals(
            listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.BACK, MuscleGroup.GLUTES),
            MuscleGroupMapper.inferFromExerciseName("Romanian Deadlift (RDL)"),
        )
        assertEquals(listOf(MuscleGroup.BACK), MuscleGroupMapper.inferFromExerciseName("Weighted Pull-Up"))
        assertEquals(listOf(MuscleGroup.SHOULDERS), MuscleGroupMapper.inferFromExerciseName("Overhead Press"))
        assertEquals(listOf(MuscleGroup.OTHER), MuscleGroupMapper.inferFromExerciseName("Unknown Movement"))
    }
}
