package com.sparkborne.telemetry.model

import kotlinx.serialization.Serializable

/**
 * Platform-agnostic daily health aggregate read locally from Android Health Connect.
 *
 * Used by [com.sparkborne.telemetry.normalizer.TelemetrySnapshotMerger] to provide
 * instantaneous local updates for steps, sleep, hydration, nutrition, and weight while
 * SparkyFitness remains the backup & authoritative source for goals and Liftosaur lifting.
 */
@Serializable
data class LocalHealthReading(
    val date: String,
    val steps: Int = 0,
    val hydrationMl: Int = 0,
    val sleepTotalMinutes: Int = 0,
    val sleepDeepMinutes: Int = 0,
    val sleepRemMinutes: Int = 0,
    val sleepLightMinutes: Int = 0,
    val sleepAwakeMinutes: Int = 0,
    val sleepSessionCount: Int = 0,
    val calories: Double = 0.0,
    val proteinGrams: Double = 0.0,
    val carbsGrams: Double = 0.0,
    val fatGrams: Double = 0.0,
    val nutritionEntryCount: Int = 0,
    val bodyweightKg: Double? = null,
    val syncedAtEpochMs: Long = 0L,
) {
    val hasAnyData: Boolean
        get() = steps > 0 ||
            hydrationMl > 0 ||
            sleepTotalMinutes > 0 ||
            calories > 0.0 ||
            proteinGrams > 0.0 ||
            carbsGrams > 0.0 ||
            fatGrams > 0.0 ||
            bodyweightKg != null
}
