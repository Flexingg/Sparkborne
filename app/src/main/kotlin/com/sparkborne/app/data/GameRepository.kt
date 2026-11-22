package com.sparkborne.app.data

import com.sparkborne.app.data.db.GameStateDao
import com.sparkborne.app.data.db.GameStateEntity
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.ExpeditionManager
import com.sparkborne.game.engine.TelemetryRulesEngine
import com.sparkborne.game.model.GameState
import com.sparkborne.telemetry.client.SparkyFitnessApiClient
import com.sparkborne.telemetry.model.BodyweightTrend
import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import com.sparkborne.telemetry.model.TelemetryDelta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Persists [GameState] in Room and applies telemetry updates + player actions atomically.
 */
class GameRepository(
    private val dao: GameStateDao,
    private val json: Json = SparkyFitnessApiClient.defaultJson,
) {

    fun observeGameState(): Flow<GameState> {
        return dao.observeState().map { entity ->
            entity?.let { decode(it.stateJson) } ?: GameCatalog.initialGameState()
        }
    }

    suspend fun loadGameState(): GameState {
        val existing = dao.getState()
        if (existing != null) {
            return decode(existing.stateJson)
        }
        val initial = GameCatalog.initialGameState()
        saveGameState(initial)
        return initial
    }

    suspend fun saveGameState(state: GameState) {
        dao.upsert(
            GameStateEntity(
                id = 1,
                updatedAtEpochMs = System.currentTimeMillis(),
                stateJson = json.encodeToString(state),
            ),
        )
    }

    suspend fun updateState(transform: (GameState) -> Result<GameState>): Result<GameState> {
        val current = ExpeditionManager.checkContractDeadline(loadGameState(), LocalDate.now())
        val result = transform(current)
        result.onSuccess { saveGameState(it) }
        return result
    }

    suspend fun applySyncedTelemetry(
        snapshots: List<DailyTelemetrySnapshot>,
        deltas: List<TelemetryDelta>,
        trend: BodyweightTrend?,
    ): Pair<GameState, List<String>> {
        var state = ExpeditionManager.checkContractDeadline(loadGameState(), LocalDate.now())
        val deltaByDate = deltas.associateBy { it.date }
        val allEvents = mutableListOf<String>()

        for (snap in snapshots.sortedBy { it.date }) {
            val delta = deltaByDate[snap.date] ?: continue
            val applied = TelemetryRulesEngine.applyTelemetry(state, snap, delta, trend)
            state = applied.state
            allEvents.addAll(applied.events)
        }

        saveGameState(state)
        return state to allEvents
    }

    suspend fun applyHistoricalBackfill(
        snapshots: List<DailyTelemetrySnapshot>,
        deltas: List<TelemetryDelta>,
        trend: BodyweightTrend?,
    ): GameState {
        val todayStr = LocalDate.now().toString()
        var state = ExpeditionManager.checkContractDeadline(loadGameState(), LocalDate.now())
        val deltaByDate = deltas.associateBy { it.date }

        for (snap in snapshots.sortedBy { it.date }) {
            val delta = deltaByDate[snap.date] ?: continue
            state = if (snap.date >= todayStr) {
                TelemetryRulesEngine.applyTelemetry(state, snap, delta, trend).state
            } else {
                TelemetryRulesEngine.applyHistoricalBackfill(state, snap, delta).state
            }
        }

        saveGameState(state)
        return state
    }

    private fun decode(raw: String): GameState {
        return runCatching { json.decodeFromString<GameState>(raw) }
            .getOrElse { GameCatalog.initialGameState() }
    }
}
