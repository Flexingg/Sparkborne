package com.sparkborne.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.data.HealthConnectReader
import com.sparkborne.app.data.HealthConnectStatus
import com.sparkborne.app.ui.components.ElementalAppearanceCard
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.app.ui.theme.SparkborneThemeId
import com.sparkborne.app.ui.theme.SplashStyleId
import com.sparkborne.telemetry.model.BodyweightTrend
import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import kotlin.math.roundToInt

private enum class SyncSubTab(val label: String) {
    DATA("📊 Health & Data"),
    SOURCES("⚡ Sync"),
    SETTINGS("⚙ Settings"),
}

@OptIn(ExperimentalLayoutApi::class)
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
    val colors = RpgTheme.colors
    var activeSubTab by remember { mutableStateOf(SyncSubTab.DATA) }
    var showAudit by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
    ) {
        // Clean 3-way segmented sub-tab switcher so Settings / Sync / Health Data never feel crowded
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.panelSurface,
            border = BorderStroke(1.dp, colors.pixelBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SyncSubTab.entries.forEach { tab ->
                    val selected = tab == activeSubTab
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = if (selected) colors.accentPrimary.copy(alpha = 0.22f) else Color.Transparent,
                        border = if (selected) BorderStroke(1.dp, colors.accentPrimary) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeSubTab = tab },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            Text(
                                text = tab.label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (selected) colors.accentPrimary else colors.textMuted,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (activeSubTab) {
                SyncSubTab.DATA -> {
                    // 1. Generalized Stats & Averages (7-Day vs All-Time toggle)
                    item {
                        MetricAveragesCard(
                            snapshots = state.snapshots,
                            bankedSteps = state.gameState.player.savedSteps,
                            stepBankCap = state.gameState.player.maxStepBankCapacity(),
                        )
                    }

                    // 2. Data Provenance Matrix ("Where Your Data Comes From")
                    item {
                        DataSourceMatrixCard(
                            snapshot = state.selectedSnapshot,
                            hcStatus = state.healthConnectStatus,
                            sparkyConfigured = state.baseUrl.isNotBlank() && state.apiKey.isNotBlank(),
                            onManageSources = { activeSubTab = SyncSubTab.SOURCES },
                        )
                    }

                    // 3. Bodyweight Trend (if present)
                    state.bodyweightTrend?.let { trend ->
                        if (trend.latestWeightKg != null) {
                            item { BodyweightTrendCard(trend) }
                        }
                    }

                    // 4. Daily Log Inspector
                    if (state.snapshots.isNotEmpty()) {
                        item {
                            Text(
                                "DAILY LOGS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textMuted,
                                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.snapshots, key = { it.date }) { snap ->
                                    FilterChip(
                                        selected = snap.date == state.selectedSnapshot?.date,
                                        onClick = { onSelectDate(snap.date) },
                                        label = { Text(snap.date, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            CleanRpgCard {
                                Text(
                                    "No telemetry synced yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    "Open the ⚡ Sync tab to connect Health Connect or SparkyFitness.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                        }
                    }

                    state.selectedSnapshot?.let { snapshot ->
                        item { DailyOverviewCard(snapshot) }
                        item { LiftingMuscleGroupsCard(snapshot) }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { showAudit = !showAudit }) {
                                    Text(
                                        if (showAudit) "Hide Technical Audit" else "Show Technical Audit",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                            if (showAudit) {
                                IdempotencyAuditCard(snapshot)
                            }
                        }
                    }
                }

                SyncSubTab.SOURCES -> {
                    item {
                        HealthConnectSourceCard(
                            hc = state.healthConnectStatus,
                            message = state.healthConnectMessage,
                            onRequestPermissions = onRequestHealthConnectPermissions,
                            onPullNow = onSyncHealthConnect,
                        )
                    }

                    item {
                        SparkyConnectionCleanCard(
                            baseUrl = state.baseUrl,
                            apiKey = state.apiKey,
                            isSyncing = state.isSyncing,
                            statusMessage = state.statusMessage,
                            errorMessage = state.errorMessage,
                            onBaseUrlChange = onBaseUrlChange,
                            onApiKeyChange = onApiKeyChange,
                            onPing = onPing,
                            onSyncToday = onSyncToday,
                            onSync14Days = onSync14Days,
                        )
                    }

                    item {
                        CleanRpgCard {
                            Text(
                                "MULTI-YEAR / ALL-TIME BACKFILL",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentPrimary,
                            )
                            Text(
                                "Pulls historical Health Connect + SparkyFitness data into your Averages & Muscle Strength baseline without dumping old steps into today's Step Bank.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                            val running = state.backfillProgress != null
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onRunBackfill(1) }, enabled = !running) {
                                    Text("1 Year")
                                }
                                OutlinedButton(onClick = { onRunBackfill(3) }, enabled = !running) {
                                    Text("3 Years")
                                }
                                Button(onClick = { onRunBackfill(5) }, enabled = !running) {
                                    Text("All-Time (5y)")
                                }
                            }
                            state.backfillProgress?.let { progress ->
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            state.backfillStatus?.let { msg ->
                                Text(
                                    msg,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = colors.accentSecondary,
                                )
                            }
                        }
                    }
                }

                SyncSubTab.SETTINGS -> {
                    item {
                        ElementalAppearanceCard(
                            selectedTheme = state.selectedTheme,
                            selectedSplash = state.selectedSplash,
                            onSelectTheme = onSelectTheme,
                            onSelectSplash = onSelectSplash,
                            onReplaySplash = onReplaySplash,
                        )
                    }

                    item {
                        CleanRpgCard {
                            Text(
                                "APP & UPDATES",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentPrimary,
                            )
                            Text(
                                "Installed: ${state.installedVersionLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textPrimary,
                            )
                            state.updateStatusMessage?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.accentSecondary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onCheckUpdates) {
                                    Text("Check Updates")
                                }
                                OutlinedButton(onClick = onReplaySplash) {
                                    Text("Replay Intro")
                                }
                            }
                        }
                    }

                    item {
                        CleanRpgCard {
                            Text(
                                "JOURNEY CONTROLS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.crimsonHp,
                            )
                            Text(
                                "Restart from Level 0 with the Intro Egg Quest while keeping your synced health history.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!confirmReset) {
                                    OutlinedButton(onClick = { confirmReset = true }) {
                                        Text("Reset to Lv.0 Intro")
                                    }
                                } else {
                                    Button(onClick = {
                                        confirmReset = false
                                        onResetNewJourney()
                                    }) {
                                        Text("Confirm Reset")
                                    }
                                    TextButton(onClick = { confirmReset = false }) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CleanRpgCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = RpgTheme.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.panelSurface,
        border = BorderStroke(1.dp, colors.pixelBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun MetricAveragesCard(
    snapshots: List<DailyTelemetrySnapshot>,
    bankedSteps: Int,
    stepBankCap: Int,
) {
    val colors = RpgTheme.colors
    var window7d by remember { mutableStateOf(true) }

    val slice = remember(snapshots, window7d) {
        if (window7d) snapshots.take(7) else snapshots
    }

    val daysCount = slice.size
    val avgSteps = slice.map { it.steps }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0
    val totalSteps = slice.sumOf { it.steps }
    val avgSleepMin = slice.map { it.sleep.totalMinutes }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0
    val avgHydrationMl = slice.map { it.hydration.totalMl }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0
    val avgCalories = slice.map { it.nutrition.calories }.filter { it > 0.0 }.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0
    val avgProtein = slice.map { it.nutrition.proteinGrams }.filter { it > 0.0 }.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0
    val totalLiftingKg = slice.sumOf { it.lifting.totalVolumeKg }.roundToInt()
    val totalWorkouts = slice.sumOf { it.lifting.workoutCount }
    val avgAdherencePct = if (slice.isNotEmpty()) {
        slice.map { s ->
            val ratios = listOf(
                s.adherence.stepsRatio,
                s.adherence.sleepRatio,
                s.adherence.hydrationRatio,
                s.adherence.proteinRatio,
            ).map { it.coerceIn(0.0, 1.0) }
            ratios.average()
        }.average().times(100.0).roundToInt()
    } else {
        0
    }

    CleanRpgCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "HEALTH OVERVIEW & AVERAGES",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentPrimary,
                )
                Text(
                    if (daysCount > 0) "Across $daysCount synced day(s)" else "Waiting for first sync",
                    fontSize = 11.sp,
                    color = colors.textMuted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniToggleChip("7D", selected = window7d) { window7d = true }
                MiniToggleChip("ALL", selected = !window7d) { window7d = false }
            }
        }

        // 2x3 stat tile grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "AVG STEPS",
                value = "%,d".format(avgSteps),
                sub = "%,d total".format(totalSteps),
                accent = colors.accentPrimary,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "AVG SLEEP",
                value = if (avgSleepMin > 0) "${avgSleepMin / 60}h ${avgSleepMin % 60}m" else "—",
                sub = "Egg & HP buff",
                accent = colors.arcaneMana,
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "AVG HYDRATION",
                value = if (avgHydrationMl > 0) "%,d mL".format(avgHydrationMl) else "—",
                sub = "Mana Clarity",
                accent = colors.accentSecondary,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "AVG NUTRITION",
                value = if (avgCalories > 0) "%,d kcal".format(avgCalories) else "—",
                sub = if (avgProtein > 0) "${avgProtein}g protein/d" else "No logs",
                accent = colors.vitalityEmerald,
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "LIFTING VOL",
                value = "%,d kg".format(totalLiftingKg),
                sub = "$totalWorkouts workout(s)",
                accent = colors.accentPrimary,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "GOAL SCORE",
                value = "$avgAdherencePct%",
                sub = "Bank %,d/%,d".format(bankedSteps, stepBankCap),
                accent = colors.accentSecondary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = RpgTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.panelElevated,
        border = BorderStroke(1.dp, colors.pixelBorder),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textMuted,
            )
            Text(
                value,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
            )
            Text(
                sub,
                fontSize = 10.sp,
                color = colors.textMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MiniToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = RpgTheme.colors
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) colors.accentPrimary.copy(alpha = 0.22f) else colors.panelElevated,
        border = BorderStroke(1.dp, if (selected) colors.accentPrimary else colors.pixelBorder),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) colors.accentPrimary else colors.textMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DataSourceMatrixCard(
    snapshot: DailyTelemetrySnapshot?,
    hcStatus: HealthConnectStatus,
    sparkyConfigured: Boolean,
    onManageSources: () -> Unit,
) {
    val colors = RpgTheme.colors
    val sources = snapshot?.metricSources.orEmpty()

    data class SourceRowInfo(
        val metric: String,
        val currentVal: String,
        val activeSource: String,
        val hcPermGranted: Boolean?,
    )

    val rows = listOf(
        SourceRowInfo(
            metric = "Steps",
            currentVal = snapshot?.let { "%,d".format(it.steps) } ?: "—",
            activeSource = sources["steps"] ?: if (HealthConnectReader.PERM_STEPS in hcStatus.grantedPermissions) "Phone + Health Connect" else if (sparkyConfigured) "Phone + Sparky" else "Phone Sensor",
            hcPermGranted = HealthConnectReader.PERM_STEPS in hcStatus.grantedPermissions,
        ),
        SourceRowInfo(
            metric = "Sleep",
            currentVal = snapshot?.takeIf { it.sleep.totalMinutes > 0 }?.let { "${it.sleep.totalMinutes / 60}h ${it.sleep.totalMinutes % 60}m" } ?: "—",
            activeSource = sources["sleep"] ?: if (HealthConnectReader.PERM_SLEEP in hcStatus.grantedPermissions) "Health Connect" else "SparkyFitness",
            hcPermGranted = HealthConnectReader.PERM_SLEEP in hcStatus.grantedPermissions,
        ),
        SourceRowInfo(
            metric = "Hydration",
            currentVal = snapshot?.takeIf { it.hydration.totalMl > 0 }?.let { "%,d mL".format(it.hydration.totalMl) } ?: "—",
            activeSource = sources["hydration"] ?: if (HealthConnectReader.PERM_HYDRATION in hcStatus.grantedPermissions) "Health Connect" else "SparkyFitness",
            hcPermGranted = HealthConnectReader.PERM_HYDRATION in hcStatus.grantedPermissions,
        ),
        SourceRowInfo(
            metric = "Nutrition",
            currentVal = snapshot?.takeIf { it.nutrition.calories > 0 }?.let { "${it.nutrition.calories.roundToInt()} kcal" } ?: "—",
            activeSource = sources["nutrition"] ?: if (HealthConnectReader.PERM_NUTRITION in hcStatus.grantedPermissions) "Health Connect" else "SparkyFitness",
            hcPermGranted = HealthConnectReader.PERM_NUTRITION in hcStatus.grantedPermissions,
        ),
        SourceRowInfo(
            metric = "Weight",
            currentVal = snapshot?.bodyweightKg?.let { "%.1f kg".format(it) } ?: "—",
            activeSource = sources["weight"] ?: if (HealthConnectReader.PERM_WEIGHT in hcStatus.grantedPermissions) "Health Connect" else "SparkyFitness",
            hcPermGranted = HealthConnectReader.PERM_WEIGHT in hcStatus.grantedPermissions,
        ),
        SourceRowInfo(
            metric = "Lifting & Goals",
            currentVal = snapshot?.takeIf { it.lifting.totalVolumeKg > 0 }?.let { "%,d kg".format(it.lifting.totalVolumeKg.roundToInt()) } ?: "Goals Only",
            activeSource = sources["lifting"] ?: "SparkyFitness (Liftosaur)",
            hcPermGranted = null, // Always SparkyFitness
        ),
    )

    CleanRpgCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "DATA SOURCES & PROVENANCE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentPrimary,
                )
                Text(
                    snapshot?.let { "Showing ${it.date} · High-water mark (no double-count)" } ?: "Live pipeline routing",
                    fontSize = 10.sp,
                    color = colors.textMuted,
                )
            }
            MiniToggleChip("Manage", selected = false, onClick = onManageSources)
        }

        rows.forEachIndexed { idx, row ->
            if (idx > 0) {
                HorizontalDivider(color = colors.pixelBorder.copy(alpha = 0.5f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.metric,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Text(
                        row.currentVal,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = colors.textMuted,
                    )
                }

                val pillColor = when {
                    row.activeSource.contains("Health Connect") || row.activeSource.startsWith("HC") -> colors.vitalityEmerald
                    row.activeSource.contains("Liftosaur") -> colors.accentPrimary
                    else -> colors.accentSecondary
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = pillColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, pillColor.copy(alpha = 0.5f)),
                ) {
                    val hcBadge = when (row.hcPermGranted) {
                        true -> "✓ "
                        false -> "○ "
                        null -> ""
                    }
                    Text(
                        "$hcBadge${row.activeSource}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = pillColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HealthConnectSourceCard(
    hc: HealthConnectStatus,
    message: String?,
    onRequestPermissions: () -> Unit,
    onPullNow: () -> Unit,
) {
    val colors = RpgTheme.colors
    CleanRpgCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "HEALTH CONNECT (INSTANT LOCAL)",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentPrimary,
            )
            val badgeColor = if (hc.hasAllPermissions) colors.vitalityEmerald else if (hc.hasAnyReadPermission) colors.accentPrimary else colors.textMuted
            Text(
                "${hc.grantedPermissions.size}/${hc.totalPermissions}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor,
            )
        }

        val perms = listOf(
            "Steps" to (HealthConnectReader.PERM_STEPS in hc.grantedPermissions),
            "Sleep" to (HealthConnectReader.PERM_SLEEP in hc.grantedPermissions),
            "Hydration" to (HealthConnectReader.PERM_HYDRATION in hc.grantedPermissions),
            "Nutrition" to (HealthConnectReader.PERM_NUTRITION in hc.grantedPermissions),
            "Weight" to (HealthConnectReader.PERM_WEIGHT in hc.grantedPermissions),
            "Active Cal" to (HealthConnectReader.PERM_ACTIVE_CALORIES in hc.grantedPermissions),
            "Background" to hc.hasBackgroundPermission,
            "History" to hc.hasHistoryPermission,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            perms.forEach { (name, granted) ->
                val c = if (granted) colors.vitalityEmerald else colors.textMuted
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = c.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, c.copy(alpha = 0.45f)),
                ) {
                    Text(
                        "${if (granted) "✓" else "○"} $name",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = c,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.accentSecondary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRequestPermissions) {
                Text(if (hc.hasAllPermissions) "Manage Perms" else "Connect Health Connect")
            }
            if (hc.hasAnyReadPermission) {
                OutlinedButton(onClick = onPullNow) {
                    Text("Pull HC Now")
                }
            }
        }
    }
}

@Composable
private fun SparkyConnectionCleanCard(
    baseUrl: String,
    apiKey: String,
    isSyncing: Boolean,
    statusMessage: String?,
    errorMessage: String?,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onPing: () -> Unit,
    onSyncToday: () -> Unit,
    onSync14Days: () -> Unit,
) {
    val colors = RpgTheme.colors
    val hasCredentials = baseUrl.isNotBlank() && apiKey.isNotBlank()
    var showCredentialsEditor by remember { mutableStateOf(!hasCredentials) }

    CleanRpgCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "SPARKYFITNESS BACKUP & LIFTOSAUR",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentSecondary,
                )
                Text(
                    if (hasCredentials) baseUrl.removePrefix("https://").removeSuffix("/") else "Not configured",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = colors.textMuted,
                )
            }
            MiniToggleChip(
                label = if (showCredentialsEditor) "Done" else "Edit",
                selected = showCredentialsEditor,
                onClick = { showCredentialsEditor = !showCredentialsEditor },
            )
        }

        if (showCredentialsEditor) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("Cloudflare Tunnel HTTPS URL") },
                placeholder = { Text("https://fitness.yourdomain.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("SparkyFitness API Key (x-api-key)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPing, enabled = !isSyncing) {
                Text("Ping")
            }
            Button(onClick = onSyncToday, enabled = !isSyncing) {
                Text("Sync Today")
            }
            OutlinedButton(onClick = onSync14Days, enabled = !isSyncing) {
                Text("Sync 14d")
            }
        }

        if (isSyncing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.accentSecondary)
        }
        errorMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.crimsonHp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelemetryInspectorScreen(viewModel: TelemetryInspectorViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sparkborne", fontWeight = FontWeight.Bold)
                        Text(
                            "Phase 1 · SparkyFitness Telemetry Inspector",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SparkyConnectionCleanCard(
                    baseUrl = state.baseUrl,
                    apiKey = state.apiKey,
                    isSyncing = state.isSyncing,
                    statusMessage = state.statusMessage,
                    errorMessage = state.errorMessage,
                    onBaseUrlChange = viewModel::onBaseUrlChanged,
                    onApiKeyChange = viewModel::onApiKeyChanged,
                    onPing = viewModel::testConnection,
                    onSyncToday = { viewModel.syncDays(1) },
                    onSync14Days = { viewModel.syncDays(14) },
                )
            }

            state.bodyweightTrend?.let { trend ->
                if (trend.latestWeightKg != null) {
                    item { BodyweightTrendCard(trend) }
                }
            }

            if (state.snapshots.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.snapshots, key = { it.date }) { snap ->
                            FilterChip(
                                selected = snap.date == state.selectedSnapshot?.date,
                                onClick = { viewModel.onSelectDate(snap.date) },
                                label = { Text(snap.date) },
                            )
                        }
                    }
                }
            }

            state.selectedSnapshot?.let { snapshot ->
                item { DailyOverviewCard(snapshot) }
                item { LiftingMuscleGroupsCard(snapshot) }
                item { IdempotencyAuditCard(snapshot) }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun BodyweightTrendCard(trend: BodyweightTrend) {
    val colors = RpgTheme.colors
    CleanRpgCard {
        Text(
            "BODYWEIGHT TREND · ${trend.asOfDate}",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accentPrimary,
        )
        Text(
            "Latest: ${trend.latestWeightKg?.let { "%.1f kg".format(it) } ?: "—"}  ·  " +
                "7d Avg: ${trend.rolling7DayAvgKg?.let { "%.1f kg".format(it) } ?: "—"}  ·  " +
                "14d Avg: ${trend.rolling14DayAvgKg?.let { "%.1f kg".format(it) } ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
        Text(
            "7d Delta: ${trend.delta7DayKg?.let { "%+.2f kg".format(it) } ?: "—"}  ·  " +
                "Trend: ${trend.direction.name} (${trend.sampleCount14d} logs)",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun DailyOverviewCard(snapshot: DailyTelemetrySnapshot) {
    val colors = RpgTheme.colors
    CleanRpgCard {
        Text(
            "DAILY TELEMETRY · ${snapshot.date}",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accentPrimary,
        )

        MetricRow("Steps (Adventure)", "%,d / %,d".format(snapshot.steps, snapshot.goals.stepsTarget), snapshot.adherence.stepsRatio)
        MetricRow(
            "Sleep (Vitality / Eggs)",
            "${snapshot.sleep.totalMinutes / 60}h ${snapshot.sleep.totalMinutes % 60}m / ${snapshot.goals.sleepMinutesTarget / 60}h",
            snapshot.adherence.sleepRatio,
        )
        MetricRow(
            "Hydration",
            "%,d / %,d mL".format(snapshot.hydration.totalMl, snapshot.goals.waterMlTarget),
            snapshot.adherence.hydrationRatio,
        )
        MetricRow(
            "Calories",
            "%,d / %,d kcal".format(snapshot.nutrition.calories.roundToInt(), snapshot.goals.caloriesTarget),
            snapshot.adherence.caloriesRatio,
        )
        MetricRow(
            "Protein",
            "${snapshot.nutrition.proteinGrams.roundToInt()} / ${snapshot.goals.proteinGramsTarget.roundToInt()} g",
            snapshot.adherence.proteinRatio,
        )
        Text(
            "Carbs: ${snapshot.nutrition.carbsGrams.roundToInt()}g / ${snapshot.goals.carbsGramsTarget.roundToInt()}g  ·  " +
                "Fat: ${snapshot.nutrition.fatGrams.roundToInt()}g / ${snapshot.goals.fatGramsTarget.roundToInt()}g",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String, ratio: Double) {
    val colors = RpgTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            Text(
                "$value (${(ratio * 100).roundToInt()}%)",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
        }
        LinearProgressIndicator(
            progress = { ratio.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LiftingMuscleGroupsCard(snapshot: DailyTelemetrySnapshot) {
    val colors = RpgTheme.colors
    val lifting = snapshot.lifting
    CleanRpgCard {
        Text(
            "LIFTING · %,d kg (%d sets)".format(lifting.totalVolumeKg.roundToInt(), lifting.totalWorkingSets),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accentPrimary,
        )
        if (lifting.byMuscleGroup.isEmpty()) {
            Text(
                "No lifting sets logged for ${snapshot.date}.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        } else {
            lifting.byMuscleGroup.values
                .sortedByDescending { it.effectiveVolumeKg }
                .forEach { vol ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${vol.muscleGroup.displayName} (${vol.workingSets} sets)",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textPrimary,
                        )
                        Text(
                            "%,d kg eff · top ${vol.topSetWeightKg} kg".format(vol.effectiveVolumeKg.roundToInt()),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colors.accentPrimary,
                        )
                    }
                }
        }
    }
}

@Composable
private fun IdempotencyAuditCard(snapshot: DailyTelemetrySnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Idempotency Audit", style = MaterialTheme.typography.labelMedium)
            Text(
                "SHA-256: ${snapshot.contentHash.take(24)}...",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
