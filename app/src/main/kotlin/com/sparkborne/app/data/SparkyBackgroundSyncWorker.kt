package com.sparkborne.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sparkborne.app.data.db.SparkborneDatabase
import com.sparkborne.telemetry.normalizer.BodyweightTrendCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager job (every 15 minutes when online) that triggers Garmin + Liftosaur
 * syncs on SparkyFitness and banks any new telemetry into Sparkborne's Room database.
 */
class SparkyBackgroundSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = SparkborneDatabase.getInstance(applicationContext)
        val telemetryRepo = TelemetryRepository(db.dailySnapshotDao())
        val gameRepo = GameRepository(db.gameStateDao())
        val today = LocalDate.now()

        // 1. Instant local Health Connect pull (works offline & in background on Android 15+)
        val hcReader = HealthConnectReader(applicationContext)
        val hcStatus = hcReader.getStatus()
        if (hcStatus.hasAnyReadPermission && hcStatus.hasBackgroundPermission) {
            runCatching {
                val readings = hcReader.readRange(today, today)
                if (readings.isNotEmpty()) {
                    val hcRes = telemetryRepo.syncLocalHealthConnect(readings)
                    if (hcRes.updatedDays > 0) {
                        val allSnaps = telemetryRepo.observeSnapshots().first()
                        val trend = BodyweightTrendCalculator.calculate(allSnaps, today)
                        gameRepo.applySyncedTelemetry(hcRes.snapshots, hcRes.deltas, trend)
                    }
                }
            }
        }

        // 2. SparkyFitness cloud backup (Garmin + Liftosaur lifting & goals)
        val creds = CredentialsStore(applicationContext).load()
        if (creds.validate().isFailure) {
            return Result.success()
        }

        val syncResult = telemetryRepo.syncRange(
            config = creds,
            startDate = today,
            endDate = today,
            triggerUpstreamProviders = true,
        )

        return syncResult.fold(
            onSuccess = { res ->
                val allSnaps = telemetryRepo.observeSnapshots().first()
                val trend = BodyweightTrendCalculator.calculate(allSnaps, today)
                gameRepo.applySyncedTelemetry(res.snapshots, res.deltas, trend)
                Result.success()
            },
            onFailure = {
                Result.retry()
            },
        )
    }

    companion object {
        private const val WORK_NAME = "sparkborne_periodic_sparky_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SparkyBackgroundSyncWorker>(
                15,
                TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
