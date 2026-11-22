package com.sparkborne.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparkborneRootScreen(viewModel: SparkborneViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sparkborne", fontWeight = FontWeight.Bold)
                        Text(
                            "Phase 2 · Deterministic Engine · ${state.selectedTab.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                SparkborneTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Text(tab.label.take(1), fontWeight = FontWeight.Bold) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state.selectedTab) {
                SparkborneTab.DELVE -> DelveTabScreen(
                    state = state.gameState,
                    errorMessage = state.errorMessage,
                    onStartContract = viewModel::startExpedition,
                    onEnterNode = viewModel::enterCurrentNode,
                    onCombatAction = viewModel::performCombatAction,
                    onAbandon = viewModel::abandonExpedition,
                    onClearFinished = viewModel::clearFinishedExpedition,
                )

                SparkborneTab.HERO -> HeroTabScreen(
                    state = state.gameState,
                    errorMessage = state.errorMessage,
                    onForgeOrEquipWeapon = viewModel::forgeOrEquipWeapon,
                    onForgeOrEquipArmor = viewModel::forgeOrEquipArmor,
                    onUnlockOrAttuneSpell = viewModel::unlockOrAttuneSpell,
                )

                SparkborneTab.MONSTERS -> MonstersTabScreen(
                    state = state.gameState,
                    errorMessage = state.errorMessage,
                    onSelectActive = viewModel::selectActiveMonster,
                    onEvolve = viewModel::evolveMonster,
                )

                SparkborneTab.SYNC -> TelemetrySyncTabContent(
                    state = state,
                    onBaseUrlChange = viewModel::onBaseUrlChanged,
                    onApiKeyChange = viewModel::onApiKeyChanged,
                    onSelectDate = viewModel::onSelectDate,
                    onPing = viewModel::testConnection,
                    onSyncToday = { viewModel.syncDays(1) },
                    onSync14Days = { viewModel.syncDays(14) },
                )
            }
        }
    }
}
