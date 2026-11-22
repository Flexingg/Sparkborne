package com.sparkborne.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.R
import com.sparkborne.app.ui.components.PixelSprite
import com.sparkborne.app.ui.components.RpgCurrencyRow
import com.sparkborne.app.ui.components.RpgPanel
import com.sparkborne.app.ui.components.UpdateAvailableBanner
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.WorldCatalog

private fun formatCompactSteps(steps: Int): String = when {
    steps >= 1_000_000 -> "%.1fM".format(steps / 1_000_000.0)
    steps >= 10_000 -> "%.1fk".format(steps / 1_000.0)
    else -> "%,d".format(steps)
}

/**
 * Game-first root shell:
 * - Compact, non-blocking Top Game Bar showing Hero LV, Pixel Currencies, Step Bank,
 *   and quick utility buttons (🔔 Dismissible Notifications/Quests, 📖 Sparkpedia, ⚡ Sync).
 * - Zero permanently stuck banners: Intro Quest, App Updates, and Sync notices live cleanly inside
 *   the toggleable 🔔 Notifications Drawer so you can dismiss or re-open them anytime.
 */
@Composable
fun SparkborneRootScreen(
    viewModel: SparkborneViewModel,
    onRequestHealthConnectPermissions: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val colors = RpgTheme.colors
    val game = state.gameState

    val alertCount = buildList {
        if (!game.introQuest.isComplete) add("intro")
        if (state.availableUpdate != null) add("update")
        if (state.errorMessage != null) add("error")
    }.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.obsidianBg),
    ) {
        // 1. Main Full-Bleed Content Layer
        val isMapTab = state.selectedTab == SparkborneTab.WORLD
        AnimatedContent(
            targetState = state.selectedTab,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(180)) },
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isMapTab) {
                        Modifier
                    } else {
                        Modifier
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(top = 98.dp, bottom = 76.dp)
                    },
                ),
            label = "game_view_switch",
        ) { currentTab ->
            when (currentTab) {
                SparkborneTab.WORLD -> WorldTabScreen(
                    state = game,
                    errorMessage = state.errorMessage,
                    onStartTravel = viewModel::startTravel,
                    onCancelTravel = viewModel::cancelTravel,
                    onStartActivity = viewModel::startActivity,
                    onStopActivity = viewModel::stopActivity,
                    onBuyShopItem = viewModel::buyShopListing,
                    onStartContractFromTown = viewModel::startContractFromTown,
                    onVisitedShop = viewModel::onVisitedTownShop,
                    onSpendBankedStepsOnTravel = viewModel::spendBankedStepsOnTravel,
                    onSpendBankedStepsOnActivity = viewModel::spendBankedStepsOnActivity,
                )

                SparkborneTab.DELVE -> DelveTabScreen(
                    state = game,
                    errorMessage = state.errorMessage,
                    onStartContract = viewModel::startExpedition,
                    onEnterNode = viewModel::enterCurrentNode,
                    onCombatAction = viewModel::performCombatAction,
                    onAbandon = viewModel::abandonExpedition,
                    onClearFinished = viewModel::clearFinishedExpedition,
                    onSpendBankedStepsOnDelve = viewModel::spendBankedStepsOnDelve,
                )

                SparkborneTab.HERO -> HeroTabScreen(
                    state = game,
                    errorMessage = state.errorMessage,
                    onForgeOrEquipWeapon = viewModel::forgeOrEquipWeapon,
                    onForgeOrEquipArmor = viewModel::forgeOrEquipArmor,
                    onUnlockOrAttuneSpell = viewModel::unlockOrAttuneSpell,
                )

                SparkborneTab.MONSTERS -> MonstersTabScreen(
                    state = game,
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
                    onResetNewJourney = viewModel::resetNewJourney,
                    onRequestHealthConnectPermissions = onRequestHealthConnectPermissions,
                    onSyncHealthConnect = { viewModel.syncHealthConnect(days = 1, silent = false) },
                )
            }
        }

        // 2. Clean Top Game Bar + Dismissible / Re-Openable Notifications Drawer
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.panelSurface.copy(alpha = 0.94f),
                border = BorderStroke(1.5.dp, colors.pixelBorderBright),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    // Row 1: Hero Avatar + Level + Currencies + Utility Buttons (🔔, 📖, ⚡)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.clickable { viewModel.selectTab(SparkborneTab.HERO) },
                        ) {
                            PixelSprite(
                                drawableRes = R.drawable.pixel_hero_knight,
                                contentDescription = "Hero",
                                size = 34.dp,
                                borderColor = colors.accentPrimary,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "LV.${game.player.level} · ${WorldCatalog.locationById(game.currentLocationId).name.substringBefore(" ")}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.accentPrimary,
                                )
                                RpgCurrencyRow(
                                    gold = game.player.gold,
                                    iron = game.player.ironOre,
                                    shards = game.player.emberShards,
                                    showZeros = true,
                                    compact = true,
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            // 🔔 Dismissible & Re-Openable Notifications / Quest Drawer Toggle
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = if (state.showNotificationsPanel) colors.accentPrimary.copy(alpha = 0.22f) else colors.panelElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (alertCount > 0 || state.showNotificationsPanel) colors.accentPrimary else colors.pixelBorderBright,
                                ),
                                modifier = Modifier.clickable { viewModel.toggleNotificationsPanel() },
                            ) {
                                Text(
                                    text = if (alertCount > 0) "🔔$alertCount" else "🔔",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (alertCount > 0) colors.accentPrimary else colors.textPrimary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                )
                            }

                            // 📖 Sparkpedia Codex
                            val hasUnread = game.unreadCodexIds.isNotEmpty()
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = colors.panelElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (hasUnread) colors.accentPrimary else colors.pixelBorderBright,
                                ),
                                modifier = Modifier.clickable { viewModel.openSparkpedia() },
                            ) {
                                Text(
                                    text = if (hasUnread) "📖•" else "📖",
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                )
                            }

                            // ⚡ 1-Tap SparkyFitness + Garmin + Liftosaur Sync
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = colors.panelElevated,
                                border = BorderStroke(1.dp, colors.accentSecondary),
                                modifier = Modifier.clickable(enabled = !state.isSyncing) { viewModel.syncDays(1) },
                            ) {
                                Text(
                                    text = if (state.isSyncing) "..." else "⚡",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accentSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }

                    // Row 2: Compact Step Bank + Live Task Strip
                    val stepCap = game.player.maxStepBankCapacity()
                    val activeTravel = game.activeTravel
                    val activeActivity = game.activeActivity

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "🥾 BANK: ${formatCompactSteps(game.player.savedSteps)} / ${formatCompactSteps(stepCap)}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = colors.vitalityEmerald,
                        )
                        val taskLabel = when {
                            activeTravel != null -> {
                                val dest = WorldCatalog.locationById(activeTravel.toLocationId).name.substringBefore(" ")
                                "→ $dest (${(activeTravel.progressFraction * 100).toInt()}%)"
                            }
                            activeActivity != null -> {
                                val def = WorldCatalog.activityById(activeActivity.activityId)
                                if (def != null) "${skillEmojiFor(def.skillTag)} ${def.skillTag} x${activeActivity.completionsCount}" else ""
                            }
                            game.activeExpedition != null -> "⚔ Delving"
                            else -> "Idle · Banking Steps"
                        }
                        Text(
                            text = taskLabel,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.accentSecondary,
                        )
                    }

                    // Slim progress bar for active travel or activity
                    if (activeTravel != null) {
                        LinearProgressIndicator(
                            progress = { activeTravel.progressFraction },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.accentPrimary,
                            trackColor = colors.obsidianBg,
                        )
                    } else if (activeActivity != null) {
                        val def = WorldCatalog.activityById(activeActivity.activityId)
                        if (def != null) {
                            LinearProgressIndicator(
                                progress = {
                                    (activeActivity.stepsProgress.toFloat() / def.stepsPerAction.coerceAtLeast(1)).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.accentSecondary,
                                trackColor = colors.obsidianBg,
                            )
                        }
                    }
                }
            }

            // Dismissible & Re-Openable Notifications / Quests Drawer!
            AnimatedVisibility(
                visible = state.showNotificationsPanel,
                enter = fadeIn(tween(160)) + expandVertically(),
                exit = fadeOut(tween(140)) + shrinkVertically(),
            ) {
                RpgPanel(
                    borderColor = colors.accentPrimary,
                    containerColor = colors.panelSurface.copy(alpha = 0.97f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "🔔 NOTIFICATIONS & QUESTS",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accentPrimary,
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.obsidianBg,
                            border = BorderStroke(1.dp, colors.pixelBorderBright),
                            modifier = Modifier.clickable { viewModel.dismissNotificationsPanel() },
                        ) {
                            Text(
                                text = "✕ DISMISS",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = colors.textMuted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    if (!game.introQuest.isComplete) {
                        IntroQuestBanner(
                            step = game.introQuest.currentStep,
                            onActionClick = {
                                viewModel.dismissNotificationsPanel()
                                viewModel.handleIntroQuestAction()
                            },
                            onOpenGuide = {
                                viewModel.dismissNotificationsPanel()
                                viewModel.openSparkpedia()
                            },
                        )
                    }

                    state.availableUpdate?.let { update ->
                        UpdateAvailableBanner(
                            update = update,
                            downloadProgress = state.updateDownloadProgress,
                            onDownloadAndInstall = viewModel::downloadAndInstallUpdate,
                        )
                    }

                    state.statusMessage?.let { msg ->
                        Text(
                            text = "⚡ $msg",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.vitalityEmerald,
                        )
                    }

                    state.errorMessage?.let { err ->
                        Text(
                            text = "⚠ $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.crimsonHp,
                        )
                    }

                    if (game.recentLog.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "RECENT LOG",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textMuted,
                            )
                            game.recentLog.take(4).forEach { line ->
                                Text("▸ $line", style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                            }
                        }
                    }
                }
            }
        }

        // 3. Floating WalkScape-Style Bottom Icon Dock
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            SparkborneTab.entries.forEach { tab ->
                val selected = state.selectedTab == tab
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) colors.panelElevated else colors.panelSurface.copy(alpha = 0.92f),
                    border = BorderStroke(
                        width = if (selected) 2.dp else 1.5.dp,
                        color = if (selected) colors.accentPrimary else colors.pixelBorderBright,
                    ),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.selectTab(tab) },
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(tab.badge, fontSize = 16.sp)
                        Text(
                            text = tab.label.uppercase(),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (selected) colors.accentPrimary else colors.textMuted,
                        )
                    }
                }
            }
        }

        // 4. Full-Screen WalkScape-Style Sparkpedia Codex Overlay
        if (state.showSparkpedia) {
            SparkpediaOverlay(
                state = game,
                onClose = viewModel::closeSparkpedia,
            )
        }
    }
}
