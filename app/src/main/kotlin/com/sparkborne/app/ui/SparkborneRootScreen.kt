package com.sparkborne.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.sparkborne.app.ui.components.UpdateAvailableBanner
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.WorldCatalog

/**
 * Game-first root shell:
 * - Full-bleed viewport (especially for the interactive stitched pixel World Map!)
 * - Floating compact top RPG HUD pill (Hero portrait, Level, Gold/Shards/Iron, live WalkScape activity/travel bar, 1-tap Sync)
 * - Floating WalkScape-style bottom icon dock (no heavy Material3 app bars!).
 */
@Composable
fun SparkborneRootScreen(viewModel: SparkborneViewModel) {
    val state by viewModel.uiState.collectAsState()
    val colors = RpgTheme.colors
    val game = state.gameState

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
                            .padding(top = 86.dp, bottom = 78.dp)
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
                )

                SparkborneTab.DELVE -> DelveTabScreen(
                    state = game,
                    errorMessage = state.errorMessage,
                    onStartContract = viewModel::startExpedition,
                    onEnterNode = viewModel::enterCurrentNode,
                    onCombatAction = viewModel::performCombatAction,
                    onAbandon = viewModel::abandonExpedition,
                    onClearFinished = viewModel::clearFinishedExpedition,
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
                )
            }
        }

        // 2. Floating Top Game HUD Strip (compact, translucent, zero app-bar bulk!)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.panelSurface.copy(alpha = 0.92f),
                border = BorderStroke(1.5.dp, colors.pixelBorderBright),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.clickable { viewModel.selectTab(SparkborneTab.HERO) },
                        ) {
                            PixelSprite(
                                drawableRes = R.drawable.pixel_hero_knight,
                                contentDescription = "Hero",
                                size = 36.dp,
                                borderColor = colors.accentPrimary,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "LV.${game.player.level} · ${WorldCatalog.locationById(game.currentLocationId).name.substringBefore(" ")}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.accentPrimary,
                                )
                                com.sparkborne.app.ui.components.RpgCurrencyRow(
                                    gold = game.player.gold,
                                    iron = game.player.ironOre,
                                    shards = game.player.emberShards,
                                    showZeros = true,
                                    compact = true,
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            // 📖 Sparkpedia Codex Button with unread badge!
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
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                )
                            }

                            // Quick 1-Tap SparkyFitness Step Sync pill right on the HUD!
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = colors.panelElevated,
                                border = BorderStroke(1.dp, colors.accentSecondary),
                                modifier = Modifier.clickable(enabled = !state.isSyncing) { viewModel.syncDays(1) },
                            ) {
                                Text(
                                    text = if (state.isSyncing) "SYNC..." else "⚡ SYNC",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accentSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    // Live WalkScape Activity / Travel Ticker inside the Top HUD
                    val activeTravel = game.activeTravel
                    val activeActivity = game.activeActivity
                    if (activeTravel != null) {
                        val dest = WorldCatalog.locationById(activeTravel.toLocationId)
                        val pct = (activeTravel.progressFraction * 100).toInt()
                        val remaining = (activeTravel.stepsRequired - activeTravel.stepsWalked).coerceAtLeast(0)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "🥾 Walking → ${dest.name} ($pct%)",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentPrimary,
                            )
                            Text(
                                text = "$remaining left",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textPrimary,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { activeTravel.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 3.dp),
                            color = colors.accentPrimary,
                            trackColor = colors.obsidianBg,
                        )
                    } else if (activeActivity != null) {
                        val def = WorldCatalog.activityById(activeActivity.activityId)
                        if (def != null) {
                            val frac = (activeActivity.stepsProgress.toFloat() / def.stepsPerAction.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val pct = (frac * 100).toInt()
                            val remaining = (def.stepsPerAction - activeActivity.stepsProgress).coerceAtLeast(0)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${skillEmojiFor(def.skillTag)} ${def.name} · x${activeActivity.completionsCount} ($pct%)",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accentSecondary,
                                )
                                Text(
                                    text = "$remaining to drop",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colors.vitalityEmerald,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { frac },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 3.dp),
                                color = colors.accentSecondary,
                                trackColor = colors.obsidianBg,
                            )
                        }
                    }
                }
            }

            if (!game.introQuest.isComplete) {
                IntroQuestBanner(
                    step = game.introQuest.currentStep,
                    onActionClick = viewModel::handleIntroQuestAction,
                    onOpenGuide = viewModel::openSparkpedia,
                )
            }

            state.availableUpdate?.let { update ->
                UpdateAvailableBanner(
                    update = update,
                    downloadProgress = state.updateDownloadProgress,
                    onDownloadAndInstall = viewModel::downloadAndInstallUpdate,
                )
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
