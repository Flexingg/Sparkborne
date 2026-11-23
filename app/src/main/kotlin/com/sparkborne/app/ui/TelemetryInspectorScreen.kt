package com.sparkborne.app.ui

import androidx.compose.runtime.Composable
import com.sparkborne.app.data.HealthConnectReader
import com.sparkborne.app.data.HealthConnectStatus
import com.sparkborne.app.ui.sanctum.LinkKind
import com.sparkborne.app.ui.sanctum.SanctumActions
import com.sparkborne.app.ui.sanctum.SanctumScreen
import com.sparkborne.app.ui.sanctum.SanctumState
import com.sparkborne.app.ui.sanctum.SourceLink
import com.sparkborne.app.ui.theme.SparkborneThemeId
import com.sparkborne.app.ui.theme.SplashStyleId
import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import kotlin.math.roundToInt

/**
 * Settings tab: the game-styled Sanctum (`ui/sanctum`). This wrapper only translates app state
 * (including Android-only Health Connect permission constants) into a platform-free [SanctumState].
 */
@Composable
fun TelemetrySyncTabContent(
    state: SparkborneUiState,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSelectDate: (String) -> Unit,
    onPing: () -> Unit,
    onSyncToday: () -> Unit,
    onSync14Days: () -> Unit,
    onCheckUpdates: () -> Unit,
    onSelectTheme: (SparkborneThemeId) -> Unit,
    onSelectSplash: (SplashStyleId) -> Unit,
    onReplaySplash: () -> Unit,
    onResetNewJourney: () -> Unit,
    onRequestHealthConnectPermissions: () -> Unit = {},
    onSyncHealthConnect: () -> Unit = {},
    onRunBackfill: (Int) -> Unit = {},
) {
    val hc: HealthConnectStatus = state.healthConnectStatus
    val sanctumState = SanctumState(
        snapshots = state.snapshots,
        selectedDate = state.selectedDate,
        bodyweightTrend = state.bodyweightTrend,
        bankedSteps = state.gameState.player.savedSteps,
        stepBankCap = state.gameState.player.maxStepBankCapacity(),
        links = sourceLinks(state.selectedSnapshot, hc, state.baseUrl.isNotBlank() && state.apiKey.isNotBlank()),
        hcAvailable = hc.isAvailable,
        hcPermissions = listOf(
            "Steps" to (HealthConnectReader.PERM_STEPS in hc.grantedPermissions),
            "Sleep" to (HealthConnectReader.PERM_SLEEP in hc.grantedPermissions),
            "Hydration" to (HealthConnectReader.PERM_HYDRATION in hc.grantedPermissions),
            "Nutrition" to (HealthConnectReader.PERM_NUTRITION in hc.grantedPermissions),
            "Weight" to (HealthConnectReader.PERM_WEIGHT in hc.grantedPermissions),
            "Active Cal" to (HealthConnectReader.PERM_ACTIVE_CALORIES in hc.grantedPermissions),
            "Background" to hc.hasBackgroundPermission,
            "History" to hc.hasHistoryPermission,
        ),
        hcHasAny = hc.hasAnyReadPermission,
        hcHasAll = hc.hasAllPermissions,
        hcMessage = state.healthConnectMessage,
        baseUrl = state.baseUrl,
        apiKey = state.apiKey,
        isSyncing = state.isSyncing,
        statusMessage = state.statusMessage,
        errorMessage = state.errorMessage,
        backfillProgress = state.backfillProgress,
        backfillStatus = state.backfillStatus,
        theme = state.selectedTheme,
        splash = state.selectedSplash,
        versionLabel = state.installedVersionLabel,
        updateStatus = state.updateStatusMessage,
    )
    val actions = SanctumActions(
        onBaseUrlChange = onBaseUrlChange,
        onApiKeyChange = onApiKeyChange,
        onSelectDate = onSelectDate,
        onPing = onPing,
        onSyncToday = onSyncToday,
        onSync14Days = onSync14Days,
        onCheckUpdates = onCheckUpdates,
        onSelectTheme = onSelectTheme,
        onSelectSplash = onSelectSplash,
        onReplaySplash = onReplaySplash,
        onResetJourney = onResetNewJourney,
        onRequestHealthConnect = onRequestHealthConnectPermissions,
        onSyncHealthConnect = onSyncHealthConnect,
        onRunBackfill = onRunBackfill,
    )
    SanctumScreen(state = sanctumState, actions = actions)
}

/** Where each metric currently comes from (same routing rules as the previous data-source matrix). */
private fun sourceLinks(
    snapshot: DailyTelemetrySnapshot?,
    hc: HealthConnectStatus,
    sparkyConfigured: Boolean,
): List<SourceLink> {
    val sources: Map<String, String> = snapshot?.metricSources.orEmpty()
    val granted: Set<String> = hc.grantedPermissions

    fun kindOf(label: String): LinkKind = when {
        label.contains("Liftosaur") -> LinkKind.LIFTOSAUR
        label.contains("Health Connect") || label.startsWith("HC") -> LinkKind.HEALTH_CONNECT
        label.contains("Sparky") -> LinkKind.SPARKY
        else -> LinkKind.PHONE
    }

    fun link(metric: String, glyph: String, value: String, label: String, perm: Boolean?): SourceLink =
        SourceLink(metric = metric, glyph = glyph, value = value, sourceLabel = label, kind = kindOf(label), granted = perm)

    val stepsGranted = HealthConnectReader.PERM_STEPS in granted
    val sleepGranted = HealthConnectReader.PERM_SLEEP in granted
    val waterGranted = HealthConnectReader.PERM_HYDRATION in granted
    val foodGranted = HealthConnectReader.PERM_NUTRITION in granted
    val weightGranted = HealthConnectReader.PERM_WEIGHT in granted

    return listOf(
        link(
            "Steps", "🥾",
            snapshot?.let { "%,d".format(it.steps) } ?: "—",
            sources["steps"] ?: if (stepsGranted) "Phone + Health Connect" else if (sparkyConfigured) "Phone + Sparky" else "Phone Sensor",
            stepsGranted,
        ),
        link(
            "Sleep", "🌙",
            snapshot?.takeIf { it.sleep.totalMinutes > 0 }?.let { "${it.sleep.totalMinutes / 60}h ${it.sleep.totalMinutes % 60}m" } ?: "—",
            sources["sleep"] ?: if (sleepGranted) "Health Connect" else "SparkyFitness",
            sleepGranted,
        ),
        link(
            "Water", "💧",
            snapshot?.takeIf { it.hydration.totalMl > 0 }?.let { "%,d mL".format(it.hydration.totalMl) } ?: "—",
            sources["hydration"] ?: if (waterGranted) "Health Connect" else "SparkyFitness",
            waterGranted,
        ),
        link(
            "Meals", "🍖",
            snapshot?.takeIf { it.nutrition.calories > 0 }?.let { "${it.nutrition.calories.roundToInt()} kcal" } ?: "—",
            sources["nutrition"] ?: if (foodGranted) "Health Connect" else "SparkyFitness",
            foodGranted,
        ),
        link(
            "Weight", "⚖",
            snapshot?.bodyweightKg?.let { "%.1f kg".format(it) } ?: "—",
            sources["weight"] ?: if (weightGranted) "Health Connect" else "SparkyFitness",
            weightGranted,
        ),
        link(
            "Lifting", "🏋",
            snapshot?.takeIf { it.lifting.totalVolumeKg > 0 }?.let { "%,d kg".format(it.lifting.totalVolumeKg.roundToInt()) } ?: "Goals only",
            sources["lifting"] ?: "SparkyFitness (Liftosaur)",
            null,
        ),
    )
}
