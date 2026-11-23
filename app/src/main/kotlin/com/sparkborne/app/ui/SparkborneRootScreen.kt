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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.R
import com.sparkborne.app.ui.components.PixelSprite
import com.sparkborne.app.ui.components.RpgActionButton
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
                            .padding(top = 66.dp, bottom = 76.dp)
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

                SparkborneTab.ACTIVITY -> ActivityTabScreen(
                    state = game,
                    errorMessage = state.errorMessage,
                    onStartActivity = viewModel::startActivity,
                    onStopActivity = viewModel::stopActivity,
                    onCancelTravel = viewModel::cancelTravel,
                    onSpendBankedStepsOnTravel = viewModel::spendBankedStepsOnTravel,
                    onSpendBankedStepsOnActivity = viewModel::spendBankedStepsOnActivity,
                    onStartContract = viewModel::startExpedition,
                    onEnterNode = viewModel::enterCurrentNode,
                    onCombatAction = viewModel::performCombatAction,
                    onAbandonDelve = viewModel::abandonExpedition,
                    onClearFinishedDelve = viewModel::clearFinishedExpedition,
                    onSpendBankedStepsOnDelve = viewModel::spendBankedStepsOnDelve,
                    onOpenMap = { viewModel.selectTab(SparkborneTab.WORLD) },
                )

                SparkborneTab.INVENTORY -> InventoryTabScreen(
                    state = game,
                    errorMessage = state.errorMessage,
                    onOpenSkillChest = viewModel::openSkillChest,
                    onCraftRecipe = viewModel::craftRecipe,
                    onUseConsumable = viewModel::useConsumable,
                    onForgeOrToggleTool = viewModel::forgeOrToggleTool,
                    onSaveGearSet = viewModel::saveGearSet,
                    onEquipGearSet = viewModel::equipGearSet,
                )

                SparkborneTab.HERO -> HeroTabScreen(
                    state = game,
                    errorMessage = state.errorMessage,
                    onForgeOrEquipWeapon = viewModel::forgeOrEquipWeapon,
                    onForgeOrEquipArmor = viewModel::forgeOrEquipArmor,
                    onUnlockOrAttuneSpell = viewModel::unlockOrAttuneSpell,
                    onOpenSkillChest = viewModel::openSkillChest,
                    onForgeOrToggleTool = viewModel::forgeOrToggleTool,
                    onSaveGearSet = viewModel::saveGearSet,
                    onEquipGearSet = viewModel::equipGearSet,
                    onCraftRecipe = viewModel::craftRecipe,
                    onUseConsumable = viewModel::useConsumable,
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
                    onRunBackfill = viewModel::runHistoricalBackfill,
                )
            }
        }

        // 2. Pinned WalkScape-Style Top Game HUD Bar + Dismissible Notifications Drawer
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            com.sparkborne.app.ui.components.WalkscapeTopHudBar(
                game = game,
                alertCount = alertCount,
                showNotificationsPanel = state.showNotificationsPanel,
                isSyncing = state.isSyncing,
                onAvatarClick = { viewModel.selectTab(SparkborneTab.HERO) },
                onMiniMapClick = { viewModel.selectTab(SparkborneTab.WORLD) },
                onToggleNotifications = viewModel::toggleNotificationsPanel,
                onOpenSparkpedia = viewModel::openSparkpedia,
                onSyncClick = { viewModel.syncDays(1) },
            )

            // Dismissible & Re-Openable Notifications / Quests Drawer!
            AnimatedVisibility(
                visible = state.showNotificationsPanel,
                enter = fadeIn(tween(160)) + expandVertically(),
                exit = fadeOut(tween(140)) + shrinkVertically(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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

        // 3. Pinned WalkScape-Style 4-Tab Bottom Bar (Activity, Inventory, Beasts, Settings)
        Surface(
            color = colors.obsidianBg.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, colors.pixelBorderBright.copy(alpha = 0.7f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SparkborneTab.entries.filter { it.showInBottomBar }.forEach { tab ->
                    val selected = state.selectedTab == tab
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) colors.panelElevated else Color.Transparent,
                        border = if (selected) BorderStroke(1.dp, colors.accentPrimary.copy(alpha = 0.85f)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectTab(tab) },
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
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
        }

        // 4. Full-Screen WalkScape-Style Sparkpedia Codex Overlay
        if (state.showSparkpedia) {
            SparkpediaOverlay(
                state = game,
                onClose = viewModel::closeSparkpedia,
            )
        }

        // 5. "While You Were Away" RPG Chronicle Modal
        val away = game.pendingAwaySummary
        if (!state.showSparkpedia && away != null && away.hasAnythingToShow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB806080D))
                    .clickable(onClick = viewModel::dismissAwaySummary)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.panelSurface,
                    border = BorderStroke(2.dp, colors.accentPrimary),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) {},
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "🌙 WHILE YOU WERE AWAY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accentPrimary,
                        )
                        Text(
                            "Your real-world progress has been synced and safely stored in your Step Bank!",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )

                        if (away.stepsBanked > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.panelElevated,
                                border = BorderStroke(1.dp, colors.vitalityEmerald),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("🥾 Steps Banked (AP)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = colors.textPrimary)
                                    Text(
                                        "+%,d".format(away.stepsBanked),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = colors.vitalityEmerald,
                                    )
                                }
                            }
                        }

                        if (away.liftingKgAdded > 0.0) {
                            Text(
                                "🏋️ Lifting Volume: +%,d kg applied to Muscle Ranks".format(away.liftingKgAdded.toInt()),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = colors.accentPrimary,
                            )
                        }
                        if (away.sleepMinutesAdded > 0 || away.hydrationMlAdded > 0) {
                            Text(
                                "💧 Recovery: +${away.sleepMinutesAdded / 60}h ${away.sleepMinutesAdded % 60}m Sleep · +%,d mL Dew".format(away.hydrationMlAdded),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = colors.accentSecondary,
                            )
                        }
                        if (away.eggsHatched.isNotEmpty()) {
                            Text(
                                "🥚 HATCHED: ${away.eggsHatched.joinToString(", ")}!",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.vitalityEmerald,
                            )
                        }

                        if (away.highlights.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                away.highlights.take(4).forEach { line ->
                                    Text("• $line", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                }
                            }
                        }

                        RpgActionButton(
                            text = "CLAIM & SPEND STEPS (%,d AP)".format(game.player.savedSteps),
                            onClick = viewModel::dismissAwaySummary,
                            containerColor = colors.accentPrimary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
