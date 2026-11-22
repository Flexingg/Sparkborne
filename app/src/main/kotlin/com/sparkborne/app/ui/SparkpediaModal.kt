package com.sparkborne.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.ui.components.RpgActionButton
import com.sparkborne.app.ui.components.RpgPanel
import com.sparkborne.app.ui.components.RpgProgressBar
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.SparkpediaCatalog
import com.sparkborne.game.model.CodexCategory
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.IntroQuestStep

/**
 * WalkScape-style in-game encyclopedia ("Sparkpedia") that unlocks entries as you discover
 * mechanics, towns, shopkeepers, beasts, activities, and gear.
 */
@Composable
fun SparkpediaOverlay(
    state: GameState,
    onClose: () -> Unit,
) {
    val colors = RpgTheme.colors
    var selectedCategory by remember { mutableStateOf<CodexCategory?>(null) }
    var expandedId by remember { mutableStateOf<String?>("guide_welcome") }

    val allEntries = SparkpediaCatalog.entries
    val unlockedCount = allEntries.count { it.id in state.unlockedCodexIds }
    val filtered = if (selectedCategory == null) {
        allEntries
    } else {
        allEntries.filter { it.category == selectedCategory }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.obsidianBg.copy(alpha = 0.96f))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "📖 SPARKPEDIA",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = colors.accentPrimary,
                    )
                    Text(
                        text = "Living Codex · Unlocks as you explore & progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
                OutlinedButton(onClick = onClose) {
                    Text("CLOSE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }

            // Discovery Completion Bar
            RpgProgressBar(
                label = "Realm Discovery",
                valueText = "$unlockedCount / ${allEntries.size} Entries",
                progress = unlockedCount.toFloat() / allEntries.size.coerceAtLeast(1),
                fillColor = colors.accentPrimary,
                barHeight = 9.dp,
            )

            // Category Filter Pills
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    CodexFilterPill(
                        label = "All",
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                    )
                }
                items(CodexCategory.entries) { cat ->
                    CodexFilterPill(
                        label = "${cat.icon} ${cat.displayName}",
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                    )
                }
            }

            // Entries List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { entry ->
                    val unlocked = entry.id in state.unlockedCodexIds
                    val isExpanded = expandedId == entry.id

                    RpgPanel(
                        borderColor = if (unlocked) colors.pixelBorderBright else colors.pixelBorder,
                        containerColor = if (unlocked) colors.panelSurface else colors.obsidianBg,
                        modifier = Modifier.clickable {
                            if (unlocked) {
                                expandedId = if (isExpanded) null else entry.id
                            }
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (unlocked) "${entry.category.icon} ${entry.title}" else "◆ Undiscovered ${entry.category.displayName} ◆",
                                    fontWeight = FontWeight.Bold,
                                    color = if (unlocked) colors.accentPrimary else colors.textMuted,
                                )
                                Text(
                                    text = if (unlocked) entry.subtitle else "Hint: ${entry.unlockHint}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                            if (unlocked) {
                                Text(
                                    text = if (isExpanded) "▾" else "▸",
                                    color = colors.accentPrimary,
                                    fontSize = 16.sp,
                                )
                            }
                        }

                        AnimatedVisibility(visible = unlocked && isExpanded) {
                            Column(
                                modifier = Modifier.padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = entry.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textPrimary,
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1F1B14),
                                    border = BorderStroke(1.dp, Color(0xFF6B4423)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = entry.mechanicsDetail,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFF0D4A2),
                                        modifier = Modifier.padding(10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun CodexFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = RpgTheme.colors
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = if (selected) colors.panelElevated else colors.panelSurface,
        border = BorderStroke(1.5.dp, if (selected) colors.accentPrimary else colors.pixelBorder),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.accentPrimary else colors.textMuted,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * Compact, interactive Intro Quest & Tutorial banner floating below the Top HUD
 * until the player completes "The Ember Nest" and claims their first Monster Egg.
 */
@Composable
fun IntroQuestBanner(
    step: IntroQuestStep,
    onActionClick: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    val colors = RpgTheme.colors
    var showTip by remember(step) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(9.dp),
        color = colors.panelElevated.copy(alpha = 0.94f),
        border = BorderStroke(1.5.dp, colors.accentPrimary),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "✦ INTRO QUEST (${step.stepNumber}/4) · ${step.title.uppercase()}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.accentPrimary,
                    )
                    Text(
                        text = step.objective,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textPrimary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.obsidianBg,
                        border = BorderStroke(1.dp, colors.pixelBorderBright),
                        modifier = Modifier.clickable { showTip = !showTip },
                    ) {
                        Text(
                            text = "?",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                    }
                    RpgActionButton(
                        text = when (step) {
                            IntroQuestStep.SYNC_SPARKY -> "SYNC"
                            IntroQuestStep.VISIT_EMBERHEARTH -> "MAP"
                            IntroQuestStep.START_FIRST_ACTIVITY -> "START"
                            IntroQuestStep.ACCEPT_INTRO_QUEST -> "DELVE"
                            IntroQuestStep.COMPLETED -> "CODEX"
                        },
                        onClick = onActionClick,
                    )
                }
            }

            AnimatedVisibility(visible = showTip) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = step.tip,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                    Text(
                        text = "📖 Open Sparkpedia for full mechanics →",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.accentSecondary,
                        modifier = Modifier.clickable(onClick = onOpenGuide),
                    )
                }
            }
        }
    }
}
