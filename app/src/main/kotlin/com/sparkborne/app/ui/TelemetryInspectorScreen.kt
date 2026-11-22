package com.sparkborne.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sparkborne.telemetry.model.BodyweightTrend
import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import kotlin.math.roundToInt

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
                ConnectionCard(
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
private fun ConnectionCard(
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("SparkyFitness Connection", style = MaterialTheme.typography.titleMedium)

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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPing, enabled = !isSyncing) {
                    Text("Ping")
                }
                Button(onClick = onSyncToday, enabled = !isSyncing) {
                    Text("Sync Today")
                }
                Button(onClick = onSync14Days, enabled = !isSyncing) {
                    Text("Sync 14d")
                }
            }

            if (isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            statusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun BodyweightTrendCard(trend: BodyweightTrend) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Bodyweight Trend (as of ${trend.asOfDate})", style = MaterialTheme.typography.titleMedium)
            Text(
                "Latest: ${trend.latestWeightKg?.let { "%.1f kg".format(it) } ?: "—"}  ·  " +
                    "7d Avg: ${trend.rolling7DayAvgKg?.let { "%.1f kg".format(it) } ?: "—"}  ·  " +
                    "14d Avg: ${trend.rolling14DayAvgKg?.let { "%.1f kg".format(it) } ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "7d Delta: ${trend.delta7DayKg?.let { "%+.2f kg".format(it) } ?: "—"}  ·  " +
                    "Trend: ${trend.direction.name} (${trend.sampleCount14d} logs)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DailyOverviewCard(snapshot: DailyTelemetrySnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Daily Telemetry · ${snapshot.date}", style = MaterialTheme.typography.titleMedium)

            MetricRow("Steps (Adventure)", "${snapshot.steps} / ${snapshot.goals.stepsTarget}", snapshot.adherence.stepsRatio)
            MetricRow(
                "Sleep (Vitality / Eggs)",
                "${snapshot.sleep.totalMinutes / 60}h ${snapshot.sleep.totalMinutes % 60}m / ${snapshot.goals.sleepMinutesTarget / 60}h",
                snapshot.adherence.sleepRatio,
            )
            MetricRow(
                "Hydration",
                "${snapshot.hydration.totalMl} / ${snapshot.goals.waterMlTarget} mL",
                snapshot.adherence.hydrationRatio,
            )
            MetricRow(
                "Calories",
                "${snapshot.nutrition.calories.roundToInt()} / ${snapshot.goals.caloriesTarget} kcal",
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, ratio: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$value (${(ratio * 100).roundToInt()}%)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
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
    val lifting = snapshot.lifting
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Lifting · ${lifting.totalVolumeKg.roundToInt()} kg (${lifting.totalWorkingSets} sets)",
                style = MaterialTheme.typography.titleMedium,
            )
            if (lifting.byMuscleGroup.isEmpty()) {
                Text(
                    "No lifting sets logged for ${snapshot.date}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${vol.effectiveVolumeKg.roundToInt()} kg eff · top ${vol.topSetWeightKg} kg",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
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
