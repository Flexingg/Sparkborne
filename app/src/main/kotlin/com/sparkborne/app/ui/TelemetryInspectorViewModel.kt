package com.sparkborne.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sparkborne.app.data.CredentialsStore
import com.sparkborne.app.data.TelemetryRepository
import com.sparkborne.app.data.db.SparkborneDatabase
import com.sparkborne.telemetry.client.SparkyFitnessConfig
import com.sparkborne.telemetry.model.BodyweightTrend
import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import com.sparkborne.telemetry.normalizer.BodyweightTrendCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TelemetryInspectorUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val snapshots: List<DailyTelemetrySnapshot> = emptyList(),
    val selectedDate: String? = null,
    val bodyweightTrend: BodyweightTrend? = null,
) {
    val selectedSnapshot: DailyTelemetrySnapshot?
        get() = snapshots.firstOrNull { it.date == selectedDate } ?: snapshots.firstOrNull()
}

class TelemetryInspectorViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialsStore = CredentialsStore(application)
    private val repository = TelemetryRepository(
        dao = SparkborneDatabase.getInstance(application).dailySnapshotDao(),
    )

    private val _uiState = MutableStateFlow(TelemetryInspectorUiState())
    val uiState: StateFlow<TelemetryInspectorUiState> = _uiState.asStateFlow()

    init {
        val saved = credentialsStore.load()
        _uiState.update {
            it.copy(baseUrl = saved.baseUrl, apiKey = saved.apiKey)
        }

        viewModelScope.launch {
            repository.observeSnapshots().collect { snapshots ->
                val latestDate = snapshots.firstOrNull()?.date?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                } ?: LocalDate.now()
                val trend = BodyweightTrendCalculator.calculate(snapshots, latestDate)
                _uiState.update { state ->
                    state.copy(
                        snapshots = snapshots,
                        selectedDate = state.selectedDate?.takeIf { d -> snapshots.any { it.date == d } }
                            ?: snapshots.firstOrNull()?.date,
                        bodyweightTrend = trend,
                    )
                }
            }
        }
    }

    fun onBaseUrlChanged(value: String) {
        _uiState.update { it.copy(baseUrl = value, errorMessage = null) }
    }

    fun onApiKeyChanged(value: String) {
        _uiState.update { it.copy(apiKey = value, errorMessage = null) }
    }

    fun onSelectDate(date: String) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun testConnection() {
        val config = currentConfig()
        credentialsStore.save(config.baseUrl, config.apiKey)

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, statusMessage = "Pinging SparkyFitness...", errorMessage = null) }
            repository.testConnection(config)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            statusMessage = "Connected to ${config.normalizedBaseUrl}",
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { ex ->
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            statusMessage = null,
                            errorMessage = ex.message ?: "Connection failed",
                        )
                    }
                }
        }
    }

    fun syncDays(days: Long) {
        val config = currentConfig()
        credentialsStore.save(config.baseUrl, config.apiKey)

        val endDate = LocalDate.now()
        val startDate = endDate.minusDays((days - 1).coerceAtLeast(0))

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSyncing = true,
                    statusMessage = "Syncing $startDate to $endDate...",
                    errorMessage = null,
                )
            }
            repository.syncRange(config, startDate, endDate)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            statusMessage = "Synced ${result.snapshots.size}d (${result.updatedDays} updated, ${result.unchangedDays} unchanged)",
                            errorMessage = null,
                            selectedDate = endDate.toString(),
                        )
                    }
                }
                .onFailure { ex ->
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            statusMessage = null,
                            errorMessage = ex.message ?: "Sync failed",
                        )
                    }
                }
        }
    }

    private fun currentConfig(): SparkyFitnessConfig {
        val state = _uiState.value
        return SparkyFitnessConfig(baseUrl = state.baseUrl, apiKey = state.apiKey)
    }
}
