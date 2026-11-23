package com.sparkborne.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sparkborne.app.BuildConfig
import com.sparkborne.app.data.AppUpdateInfo
import com.sparkborne.app.data.AppUpdater
import com.sparkborne.app.data.AppearanceStore
import com.sparkborne.app.data.CredentialsStore
import com.sparkborne.app.data.GameRepository
import com.sparkborne.app.data.TelemetryRepository
import com.sparkborne.app.data.db.SparkborneDatabase
import com.sparkborne.app.ui.theme.SparkborneThemeId
import com.sparkborne.app.ui.theme.SplashStyleId
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

enum class SparkborneTab(val label: String, val badge: String, val showInBottomBar: Boolean) {
    WORLD("Map", "🗺", false),
    HERO("Profile", "🛡", false),
    ACTIVITY("Activity", "🏃", true),
    INVENTORY("Inventory", "🎒", true),
    MONSTERS("Beasts", "🐉", true),
    SYNC("Settings", "⚙", true),
}

data class SparkborneUiState(
    val selectedTab: SparkborneTab = SparkborneTab.WORLD,
    val selectedTheme: SparkborneThemeId = SparkborneThemeId.EMBER_OBSIDIAN,
    val selectedSplash: SplashStyleId = SplashStyleId.TWILIGHT_CITADEL,
    val showSplash: Boolean = true,
    val showSparkpedia: Boolean = false,
    val showNotificationsPanel: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val snapshots: List<DailyTelemetrySnapshot> = emptyList(),
    val selectedDate: String? = null,
    val bodyweightTrend: BodyweightTrend? = null,
    val gameState: GameState = GameCatalog.initialGameState(),
    /** True once the saved game has been read from storage (guards the character-creation gate). */
    val gameLoaded: Boolean = false,
    val availableUpdate: AppUpdateInfo? = null,
    val updateDownloadProgress: Float? = null,
    val updateStatusMessage: String? = null,
    val installedVersionLabel: String = "${BuildConfig.VERSION_NAME} (#${BuildConfig.VERSION_CODE})",
    val healthConnectStatus: com.sparkborne.app.data.HealthConnectStatus = com.sparkborne.app.data.HealthConnectStatus(),
    val healthConnectMessage: String? = null,
    val backfillProgress: Float? = null,
    val backfillStatus: String? = null,
) {
    val selectedSnapshot: DailyTelemetrySnapshot?
        get() = snapshots.firstOrNull { it.date == selectedDate } ?: snapshots.firstOrNull()
}

class SparkborneViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialsStore = CredentialsStore(application)
    private val appearanceStore = AppearanceStore(application)
    private val db = SparkborneDatabase.getInstance(application)
    private val telemetryRepository = TelemetryRepository(db.dailySnapshotDao())
    private val gameRepository = GameRepository(db.gameStateDao())
    private val appUpdater = AppUpdater(application)
    private val healthConnectReader = com.sparkborne.app.data.HealthConnectReader(application)
    private val liveStepTracker = com.sparkborne.app.data.LiveStepTracker(application) { date, totalToday ->
        onLivePhoneStepsUpdated(date, totalToday)
    }

    private val _uiState = MutableStateFlow(SparkborneUiState())
    val uiState: StateFlow<SparkborneUiState> = _uiState.asStateFlow()

    init {
        val savedCreds = credentialsStore.load()
        val savedTheme = appearanceStore.loadTheme()
        val savedSplash = appearanceStore.loadSplash()
        _uiState.update {
            it.copy(
                baseUrl = savedCreds.baseUrl,
                apiKey = savedCreds.apiKey,
                selectedTheme = savedTheme,
                selectedSplash = savedSplash,
            )
        }

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
                _uiState.update { it.copy(gameState = gameState, gameLoaded = true) }
            }
        }

        // Start hardware step counter if permission already granted
        liveStepTracker.start()

        // Check Health Connect status & pull local metrics instantaneously
        refreshHealthConnectStatus(syncIfGranted = true)

        // Schedule 15-minute periodic background sync & quiet auto-sync on launch
        com.sparkborne.app.data.SparkyBackgroundSyncWorker.schedule(application)
        if (savedCreds.validate().isSuccess) {
            syncDays(1)
        }

        checkForUpdates(silent = true)
    }

    fun refreshLiveStepTracker() {
        liveStepTracker.start()
    }

    fun refreshHealthConnectStatus(syncIfGranted: Boolean = true) {
        viewModelScope.launch {
            val status = healthConnectReader.getStatus()
            _uiState.update { it.copy(healthConnectStatus = status) }
            if (syncIfGranted && status.hasAnyReadPermission) {
                syncHealthConnect(days = 1, silent = true)
            }
        }
    }

    fun syncHealthConnect(days: Long = 1, silent: Boolean = false) {
        viewModelScope.launch {
            val status = healthConnectReader.getStatus()
            _uiState.update { it.copy(healthConnectStatus = status) }
            if (!status.isAvailable) {
                if (!silent) {
                    _uiState.update { it.copy(healthConnectMessage = "Health Connect is not available on this device.") }
                }
                return@launch
            }
            if (!status.hasAnyReadPermission) {
                if (!silent) {
                    _uiState.update { it.copy(healthConnectMessage = "Grant Health Connect permissions first.") }
                }
                return@launch
            }

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays((days - 1).coerceAtLeast(0))
            runCatching {
                val readings = healthConnectReader.readRange(startDate, endDate)
                val hcResult = telemetryRepository.syncLocalHealthConnect(readings)
                if (hcResult.snapshots.isNotEmpty()) {
                    val allSnapshots = (_uiState.value.snapshots + hcResult.snapshots)
                        .associateBy { it.date }
                        .values
                        .sortedByDescending { it.date }
                    val trend = BodyweightTrendCalculator.calculate(allSnapshots, endDate)
                    val (_, events) = gameRepository.applySyncedTelemetry(
                        snapshots = hcResult.snapshots,
                        deltas = hcResult.deltas,
                        trend = trend,
                    )
                    val eventSuffix = if (events.isNotEmpty()) " · ${events.size} game update(s)" else ""
                    _uiState.update {
                        it.copy(
                            healthConnectMessage = "Health Connect synced ${hcResult.snapshots.size}d (${hcResult.updatedDays} updated)$eventSuffix",
                            selectedDate = endDate.toString(),
                        )
                    }
                } else if (!silent) {
                    _uiState.update {
                        it.copy(healthConnectMessage = "Health Connect checked — no new records for $startDate → $endDate.")
                    }
                }
            }.onFailure { ex ->
                if (!silent) {
                    _uiState.update {
                        it.copy(healthConnectMessage = "Health Connect sync error: ${ex.message}")
                    }
                }
            }
        }
    }

    private fun onLivePhoneStepsUpdated(date: String, totalPhoneStepsToday: Int) {
        viewModelScope.launch {
            gameRepository.updateState { state ->
                val (updated, _) = com.sparkborne.game.engine.StepBankManager.applyLivePhoneSteps(
                    state = state,
                    date = date,
                    totalPhoneStepsToday = totalPhoneStepsToday,
                )
                Result.success(updated)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveStepTracker.stop()
    }

    fun selectTab(tab: SparkborneTab) {
        _uiState.update { it.copy(selectedTab = tab, errorMessage = null) }
    }

    fun selectTheme(themeId: SparkborneThemeId) {
        appearanceStore.saveTheme(themeId)
        _uiState.update { it.copy(selectedTheme = themeId) }
    }

    fun selectSplash(splashId: SplashStyleId) {
        appearanceStore.saveSplash(splashId)
        _uiState.update { it.copy(selectedSplash = splashId) }
    }

    fun dismissSplash() {
        _uiState.update { it.copy(showSplash = false) }
    }

    fun replaySplash() {
        _uiState.update { it.copy(showSplash = true) }
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

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(updateStatusMessage = "Checking GitHub Releases...") }
            }
            appUpdater.checkForUpdate()
                .onSuccess { update ->
                    _uiState.update {
                        it.copy(
                            availableUpdate = update,
                            updateStatusMessage = when {
                                update != null -> "Update available: ${update.tagName}"
                                !silent -> "Up to date (${it.installedVersionLabel})"
                                else -> it.updateStatusMessage
                            },
                        )
                    }
                }
                .onFailure { ex ->
                    if (!silent) {
                        _uiState.update {
                            it.copy(updateStatusMessage = "Update check failed: ${ex.message}")
                        }
                    }
                }
        }
    }

    fun downloadAndInstallUpdate() {
        val update = _uiState.value.availableUpdate ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(updateDownloadProgress = 0f, updateStatusMessage = "Downloading ${update.tagName}...") }
            appUpdater.downloadAndInstall(update) { progress ->
                _uiState.update { it.copy(updateDownloadProgress = progress) }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        updateDownloadProgress = null,
                        updateStatusMessage = "Installer opened for ${update.tagName}.",
                    )
                }
            }.onFailure { ex ->
                _uiState.update {
                    it.copy(
                        updateDownloadProgress = null,
                        updateStatusMessage = "Install failed: ${ex.message}",
                    )
                }
            }
        }
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
                it.copy(
                    isSyncing = true,
                    statusMessage = "Reading Health Connect & triggering Garmin/Liftosaur sync ($startDate → $endDate)...",
                    errorMessage = null,
                )
            }

            // 1. Instant local Health Connect pull first (steps, sleep, hydration, nutrition, weight)
            var hcSummary = ""
            runCatching {
                val hcStatus = healthConnectReader.getStatus()
                _uiState.update { it.copy(healthConnectStatus = hcStatus) }
                if (hcStatus.hasAnyReadPermission) {
                    val readings = healthConnectReader.readRange(startDate, endDate)
                    if (readings.isNotEmpty()) {
                        val hcRes = telemetryRepository.syncLocalHealthConnect(readings)
                        val allSnaps = (_uiState.value.snapshots + hcRes.snapshots)
                            .associateBy { it.date }
                            .values
                            .sortedByDescending { it.date }
                        val trend = BodyweightTrendCalculator.calculate(allSnaps, endDate)
                        gameRepository.applySyncedTelemetry(hcRes.snapshots, hcRes.deltas, trend)
                        hcSummary = "HC ${hcRes.updatedDays}d · "
                    }
                }
            }

            // 2. SparkyFitness backup & Liftosaur lifting / goals sync
            telemetryRepository.syncRange(config, startDate, endDate, triggerUpstreamProviders = true)
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
                    val providerText = result.providerSync?.shortSummary()?.let { "$it · " }.orEmpty()
                    val eventSummary = if (events.isNotEmpty()) " · ${events.size} game update(s)" else ""
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            statusMessage = "${hcSummary}${providerText}Synced ${result.snapshots.size}d (${result.updatedDays} updated)$eventSummary",
                            errorMessage = null,
                            selectedDate = endDate.toString(),
                        )
                    }
                }
                .onFailure { ex ->
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            statusMessage = if (hcSummary.isNotEmpty()) "${hcSummary}Sparky offline (using local Health Connect)" else null,
                            errorMessage = ex.message ?: "Sync failed",
                        )
                    }
                }
        }
    }

    /**
     * Multi-year / All-Time backfill across BOTH local Health Connect (90-day batched chunks)
     * and SparkyFitness (30-day chunks, stopping early after 4 consecutive empty months).
     * Historical days populate Room averages & baseline Muscle Strength XP without dumping
     * years of old steps into the Step Bank or auto-completing active quests.
     */
    fun runHistoricalBackfill(years: Int) {
        if (_uiState.value.backfillProgress != null) return
        val config = currentConfig()
        val endDate = LocalDate.now()
        val startDate = endDate.minusYears(years.toLong())
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(1).toFloat()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    backfillProgress = 0.02f,
                    backfillStatus = "Starting ${years}y backfill ($startDate → $endDate)...",
                    errorMessage = null,
                )
            }

            var hcDaysImported = 0
            // 1. Fast chunked Health Connect backfill (first 40% of progress bar)
            runCatching {
                val hcStatus = healthConnectReader.getStatus()
                _uiState.update { it.copy(healthConnectStatus = hcStatus) }
                if (hcStatus.hasAnyReadPermission) {
                    healthConnectReader.readRangeChunked(startDate, endDate) { chunkStart, chunkEnd, chunkReadings ->
                        if (chunkReadings.isNotEmpty()) {
                            val res = telemetryRepository.syncLocalHealthConnect(chunkReadings)
                            hcDaysImported += res.updatedDays
                            val trend = BodyweightTrendCalculator.calculate(_uiState.value.snapshots, endDate)
                            gameRepository.applyHistoricalBackfill(res.snapshots, res.deltas, trend)
                        }
                        val doneDays = java.time.temporal.ChronoUnit.DAYS.between(chunkStart, endDate).toFloat()
                        val pct = (doneDays / totalDays).coerceIn(0f, 1f) * 0.40f
                        _uiState.update {
                            it.copy(
                                backfillProgress = pct.coerceAtLeast(0.05f),
                                backfillStatus = "Health Connect: $chunkStart → $chunkEnd ($hcDaysImported days updated)",
                            )
                        }
                    }
                }
            }

            // 2. Chunked SparkyFitness backfill (remaining 60%, 30-day chunks newest -> oldest)
            var sparkyDaysImported = 0
            if (config.validate().isSuccess) {
                var chunkEnd = endDate
                var emptyConsecutiveChunks = 0
                while (!chunkEnd.isBefore(startDate) && emptyConsecutiveChunks < 4) {
                    val candidateStart = chunkEnd.minusDays(29)
                    val chunkStart = if (candidateStart.isBefore(startDate)) startDate else candidateStart

                    val doneDays = java.time.temporal.ChronoUnit.DAYS.between(chunkStart, endDate).toFloat()
                    val pct = 0.40f + (doneDays / totalDays).coerceIn(0f, 1f) * 0.60f
                    _uiState.update {
                        it.copy(
                            backfillProgress = pct.coerceAtMost(0.98f),
                            backfillStatus = "SparkyFitness: $chunkStart → $chunkEnd ($sparkyDaysImported days updated)",
                        )
                    }

                    val res = telemetryRepository.syncRange(
                        config = config,
                        startDate = chunkStart,
                        endDate = chunkEnd,
                        triggerUpstreamProviders = false,
                    )
                    res.onSuccess { syncRes ->
                        val hasData = syncRes.snapshots.any { s ->
                            s.steps > 0 || s.sleep.totalMinutes > 0 || s.hydration.totalMl > 0 ||
                                s.nutrition.calories > 0.0 || s.lifting.totalVolumeKg > 0.0 || s.bodyweightKg != null
                        }
                        if (hasData) {
                            emptyConsecutiveChunks = 0
                            sparkyDaysImported += syncRes.updatedDays
                            val trend = BodyweightTrendCalculator.calculate(_uiState.value.snapshots, endDate)
                            gameRepository.applyHistoricalBackfill(syncRes.snapshots, syncRes.deltas, trend)
                        } else {
                            emptyConsecutiveChunks += 1
                        }
                    }.onFailure {
                        emptyConsecutiveChunks += 1
                    }

                    chunkEnd = chunkStart.minusDays(1)
                }
            }

            _uiState.update {
                it.copy(
                    backfillProgress = null,
                    backfillStatus = "Backfill complete (${years}y): HC +$hcDaysImported d · Sparky +$sparkyDaysImported d",
                )
            }
        }
    }

    // --- Game Engine Actions ---

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

    fun openSkillChest(skill: com.sparkborne.game.model.SkillType) = runGameMutation { state ->
        com.sparkborne.game.engine.SkillEngine.openSkillChest(state, skill).map { (next, _) -> next }
    }

    fun forgeOrToggleTool(toolId: String) = runGameMutation { state ->
        com.sparkborne.game.engine.ToolbeltEngine.forgeOrToggleTool(state, toolId)
    }

    fun saveGearSet(slotIndex: Int, name: String) = runGameMutation { state ->
        com.sparkborne.game.engine.ToolbeltEngine.saveGearSet(state, slotIndex, name)
    }

    fun equipGearSet(slotIndex: Int) = runGameMutation { state ->
        com.sparkborne.game.engine.ToolbeltEngine.equipGearSet(state, slotIndex)
    }

    fun craftRecipe(recipeId: String) = runGameMutation { state ->
        com.sparkborne.game.engine.CraftingEngine.craftRecipe(state, recipeId)
    }

    fun updateAppearance(look: com.sparkborne.game.model.HeroAppearance) = runGameMutation { state ->
        com.sparkborne.game.engine.AppearanceEngine.apply(state, look)
    }

    fun learnTalent(nodeId: String) = runGameMutation { state ->
        com.sparkborne.game.engine.TalentEngine.learn(state, nodeId)
    }

    fun resetTalents() = runGameMutation { state ->
        com.sparkborne.game.engine.TalentEngine.reset(state)
    }

    fun useConsumable(recipeId: String) = runGameMutation { state ->
        com.sparkborne.game.engine.CraftingEngine.useConsumable(state, recipeId)
    }

    fun dismissAwaySummary() = runGameMutation { state ->
        Result.success(com.sparkborne.game.engine.StepBankManager.dismissAwaySummary(state))
    }

    // --- WalkScape World, Travel, Activities & Shops ---

    fun startTravel(destinationId: String) = runGameMutation { state ->
        com.sparkborne.game.engine.WorldManager.startTravel(state, destinationId)
    }

    fun cancelTravel() = runGameMutation { state ->
        Result.success(com.sparkborne.game.engine.WorldManager.cancelTravel(state))
    }

    fun startActivity(activityId: String) = runGameMutation { state ->
        com.sparkborne.game.engine.WorldManager.startActivity(state, activityId)
    }

    fun stopActivity() = runGameMutation { state ->
        Result.success(com.sparkborne.game.engine.WorldManager.stopActivity(state))
    }

    fun buyShopListing(listingId: String) = runGameMutation { state ->
        com.sparkborne.game.engine.WorldManager.buyShopListing(state, listingId)
    }

    fun startContractFromTown(contractId: String) {
        viewModelScope.launch {
            gameRepository.updateState { state ->
                ExpeditionManager.startExpedition(state, contractId, LocalDate.now())
            }.onSuccess {
                _uiState.update { it.copy(selectedTab = SparkborneTab.ACTIVITY, errorMessage = null) }
            }.onFailure { ex ->
                _uiState.update { it.copy(errorMessage = ex.message ?: "Could not start contract") }
            }
        }
    }

    // --- Sparkpedia & Intro Quest Tutorial ---

    fun openSparkpedia() {
        _uiState.update { it.copy(showSparkpedia = true) }
        runGameMutation { state ->
            Result.success(com.sparkborne.game.engine.SparkpediaEngine.markCodexRead(state))
        }
    }

    fun closeSparkpedia() {
        _uiState.update { it.copy(showSparkpedia = false) }
    }

    fun onVisitedTownShop() = runGameMutation { state ->
        Result.success(com.sparkborne.game.engine.SparkpediaEngine.onVisitedShop(state))
    }

    fun handleIntroQuestAction() {
        val step = _uiState.value.gameState.introQuest.currentStep
        when (step) {
            com.sparkborne.game.model.IntroQuestStep.SYNC_SPARKY -> {
                if (_uiState.value.baseUrl.isBlank() || _uiState.value.apiKey.isBlank()) {
                    selectTab(SparkborneTab.SYNC)
                } else {
                    syncDays(1)
                }
            }
            com.sparkborne.game.model.IntroQuestStep.VISIT_EMBERHEARTH,
            com.sparkborne.game.model.IntroQuestStep.START_FIRST_ACTIVITY,
            -> selectTab(SparkborneTab.WORLD)
            com.sparkborne.game.model.IntroQuestStep.ACCEPT_INTRO_QUEST -> {
                if (_uiState.value.gameState.activeExpedition == null) {
                    startContractFromTown(com.sparkborne.game.engine.SparkpediaEngine.INTRO_CONTRACT_ID)
                } else {
                    selectTab(SparkborneTab.ACTIVITY)
                }
            }
            com.sparkborne.game.model.IntroQuestStep.COMPLETED -> openSparkpedia()
        }
    }

    /**
     * Resets game progress to a fresh Level 0 journey (0 quests, 0 pets, 0 eggs)
     * while preserving your SparkyFitness credentials and synced telemetry snapshots.
     */
    fun resetNewJourney() {
        viewModelScope.launch {
            val previousLook = _uiState.value.gameState.player.appearance
            val base = GameCatalog.initialGameState()
            val fresh = base.copy(
                player = base.player.copy(
                    appearance = com.sparkborne.game.engine.AppearanceEngine.forRebirth(previousLook),
                ),
            )
            gameRepository.saveGameState(fresh)
            _uiState.update {
                it.copy(
                    selectedTab = SparkborneTab.WORLD,
                    statusMessage = "Started fresh Level 0 journey! Follow the Intro Quest to claim your first Egg.",
                    errorMessage = null,
                )
            }
        }
    }

    // --- Notifications Drawer & Step Bank ---

    fun toggleNotificationsPanel() {
        _uiState.update { it.copy(showNotificationsPanel = !it.showNotificationsPanel) }
    }

    fun dismissNotificationsPanel() {
        _uiState.update { it.copy(showNotificationsPanel = false) }
    }

    fun spendBankedStepsOnDelve() = runGameMutation { state ->
        com.sparkborne.game.engine.StepBankManager.spendBankedStepsOnDelve(state)
    }

    fun spendBankedStepsOnTravel() = runGameMutation { state ->
        com.sparkborne.game.engine.StepBankManager.spendBankedStepsOnTravel(state)
    }

    fun spendBankedStepsOnActivity(maxCycles: Int? = null) = runGameMutation { state ->
        com.sparkborne.game.engine.StepBankManager.spendBankedStepsOnActivity(state, maxCycles)
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
