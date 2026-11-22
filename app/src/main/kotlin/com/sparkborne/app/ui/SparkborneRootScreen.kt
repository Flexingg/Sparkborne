package com.sparkborne.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sparkborne.app.ui.components.UpdateAvailableBanner
import com.sparkborne.app.ui.theme.RpgTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparkborneRootScreen(viewModel: SparkborneViewModel) {
    val state by viewModel.uiState.collectAsState()
    val colors = RpgTheme.colors

    Scaffold(
        containerColor = colors.obsidianBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.panelSurface,
                    titleContentColor = colors.textPrimary,
                ),
                title = {
                    Column {
                        Text(
                            "SPARKBORNE",
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = colors.accentPrimary,
                        )
                        Text(
                            "${state.selectedTab.label} · ${state.selectedTheme.elementTag} Realm · ${state.installedVersionLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = colors.panelSurface) {
                SparkborneTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Text(tab.badge) },
                        label = { Text(tab.label, fontFamily = FontFamily.Monospace) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            state.availableUpdate?.let { update ->
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    UpdateAvailableBanner(
                        update = update,
                        downloadProgress = state.updateDownloadProgress,
                        onDownloadAndInstall = viewModel::downloadAndInstallUpdate,
                    )
                }
            }

            AnimatedContent(
                targetState = state.selectedTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(tween(240)) { it / 8 * direction }) togetherWith
                        (fadeOut(tween(180)) + slideOutHorizontally(tween(200)) { -it / 8 * direction })
                },
                modifier = Modifier.weight(1f),
                label = "tab_transition",
            ) { currentTab ->
                when (currentTab) {
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
                        onCheckUpdates = { viewModel.checkForUpdates(silent = false) },
                        onSelectTheme = viewModel::selectTheme,
                        onSelectSplash = viewModel::selectSplash,
                        onReplaySplash = viewModel::replaySplash,
                    )
                }
            }
        }
    }
}
