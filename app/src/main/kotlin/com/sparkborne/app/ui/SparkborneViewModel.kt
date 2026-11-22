package com.sparkborne.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sparkborne.app.data.CredentialsStore
import com.sparkborne.app.data.GameRepository
import com.sparkborne.app.data.TelemetryRepository
import com.sparkborne.app.data.db.SparkborneDatabase
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.CombatAction
import com.sparkborne.game.engine.CombatResolver
import com.sparkborne.game.engine.ExpeditionManager
import com.sparkborne.game.model.GameState
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

enum class SparkborneTab(val label: String) {
    DELVE("Delve"),
    HERO("Hero"),
    MONSTERS("Monsters"),
    SYNC("Sync"),
}

data class SparkborneUiState(
    val selectedTab: SparkborneTab = SparkborneTab.DELVE,
    val baseUrl: String = "",
    val apiKey: String = "",
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val snapshots: List<DailyTelemetrySnapshot> = emptyList(),
    val selectedDate: String? = null,
    val bodyweightTrend: BodyweightTrend? = null,
    val gameState: GameState = GameCatalog.initialGameState(),
) {
    val selectedSnapshot: DailyTelemetrySnapshot?
        get() = snapshots.firstOrNull { it.date == selectedDate } ?: snapshots.firstOrNull()
}

class SparkborneViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialsStore = CredentialsStore(application)
    private val db = SparkborneDatabase.getInstance(application)
    private val telemetryRepository = TelemetryRepository(db.dailySnapshotDao())
    private val gameRepository = GameRepository(db.gameStateDao())

    private val _uiState = MutableStateFlow(SparkborneUiState())
    val uiState: StateFlow<SparkborneUiState> = _uiState.asStateFlow()

    init {
        val saved = credentialsStore.load()
        _uiState.update { it.copy(baseUrl = saved.baseUrl, apiKey = saved.apiKey) }

        viewModelScope.launch {
            telemetryRepository.observeSnapshots().collect { snapshots ->
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

        viewModelScope.launch {
            gameRepository.observeGameState().collect { gameState ->
                _uiState.update { it.copy(gameState = gameState) }
            }
        }
    }

    fun selectTab(tab: SparkborneTab) {
        _uiState.update { it.copy(selectedTab = tab, errorMessage = null) }
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
            telemetryRepository.testConnection(config)
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
                        it.copy(isSyncing = false, statusMessage = null, errorMessage = ex.message ?: "Connection failed")
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
                it.copy(isSyncing = true, statusMessage = "Syncing $startDate to $endDate...", errorMessage = null)
            }
            telemetryRepository.syncRange(config, startDate, endDate)
                .onSuccess { result ->
                    val allSnapshots = (_uiState.value.snapshots + result.snapshots)
                        .associateBy { it.date }
                        .values
                        .sortedByDescending { it.date }
                    val trend = BodyweightTrendCalculator.calculate(allSnapshots, endDate)
                    val (_, events) = gameRepository.applySyncedTelemetry(
                        snapshots = result.snapshots,
                        deltas = result.deltas,
                        trend = trend,
                    )
                    val eventSummary = if (events.isNotEmpty()) " · ${events.size} game update(s)" else ""
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            statusMessage = "Synced ${result.snapshots.size}d (${result.updatedDays} updated)$eventSummary",
                            errorMessage = null,
                            selectedDate = endDate.toString(),
                        )
                    }
                }
                .onFailure { ex ->
                    _uiState.update {
                        it.copy(isSyncing = false, statusMessage = null, errorMessage = ex.message ?: "Sync failed")
                    }
                }
        }
    }

    // --- Phase 2 Game Actions ---

    fun startExpedition(contractId: String) = runGameMutation { state ->
        ExpeditionManager.startExpedition(state, contractId, LocalDate.now())
    }

    fun enterCurrentNode() = runGameMutation { state ->
        ExpeditionManager.enterCurrentNode(state)
    }

    fun performCombatAction(action: CombatAction) = runGameMutation { state ->
        Result.success(CombatResolver.performAction(state, action))
    }

    fun abandonExpedition() = runGameMutation { state ->
        Result.success(ExpeditionManager.abandonExpedition(state))
    }

    fun clearFinishedExpedition() = runGameMutation { state ->
        Result.success(ExpeditionManager.clearFinishedExpedition(state))
    }

    fun forgeOrEquipWeapon(weaponId: String) = runGameMutation { state ->
        ExpeditionManager.forgeAndEquipWeapon(state, weaponId)
    }

    fun forgeOrEquipArmor(armorId: String) = runGameMutation { state ->
        ExpeditionManager.forgeAndEquipArmor(state, armorId)
    }

    fun unlockOrAttuneSpell(spellId: String) = runGameMutation { state ->
        ExpeditionManager.unlockAndAttuneSpell(state, spellId)
    }

    fun selectActiveMonster(monsterId: String) = runGameMutation { state ->
        Result.success(ExpeditionManager.selectActiveMonster(state, monsterId))
    }

    fun evolveMonster(monsterId: String) = runGameMutation { state ->
        ExpeditionManager.evolveMonster(state, monsterId)
    }

    private fun runGameMutation(transform: (GameState) -> Result<GameState>) {
        viewModelScope.launch {
            gameRepository.updateState(transform)
                .onSuccess { _uiState.update { it.copy(errorMessage = null) } }
                .onFailure { ex ->
                    _uiState.update { it.copy(errorMessage = ex.message ?: "Action failed") }
                }
        }
    }

    private fun currentConfig(): SparkyFitnessConfig {
        val state = _uiState.value
        return SparkyFitnessConfig(baseUrl = state.baseUrl, apiKey = state.apiKey)
    }
}
