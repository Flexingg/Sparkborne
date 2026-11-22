package com.sparkborne.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sparkborne.app.R
import com.sparkborne.app.ui.components.NodeTrailIndicator
import com.sparkborne.app.ui.components.PixelBannerImage
import com.sparkborne.app.ui.components.PixelSprite
import com.sparkborne.app.ui.components.RpgActionButton
import com.sparkborne.app.ui.components.RpgPanel
import com.sparkborne.app.ui.components.RpgProgressBar
import com.sparkborne.app.ui.components.RpgStatChip
import com.sparkborne.app.ui.components.monsterDrawableFor
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.CombatAction
import com.sparkborne.game.engine.CombatStatsCalculator
import com.sparkborne.game.model.ActiveCombatState
import com.sparkborne.game.model.ActiveExpedition
import com.sparkborne.game.model.ExpeditionStatus
import com.sparkborne.game.model.GameState
import com.sparkborne.telemetry.model.MuscleGroup
import kotlin.math.roundToInt

@Composable
fun DelveTabScreen(
    state: GameState,
    errorMessage: String?,
    onStartContract: (String) -> Unit,
    onEnterNode: () -> Unit,
    onCombatAction: (CombatAction) -> Unit,
    onAbandon: () -> Unit,
    onClearFinished: () -> Unit,
    onSpendBankedStepsOnDelve: () -> Unit = {},
) {
    val active = state.activeExpedition
    val colors = RpgTheme.colors
    val haptics = LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // WalkScape-style pixel art trail header banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(2.dp, colors.pixelBorderBright, RoundedCornerShape(10.dp)),
            ) {
                PixelBannerImage(
                    drawableRes = R.drawable.pixel_banner_delve,
                    contentDescription = "Sparkborne Expedition Trail",
                    height = 148.dp,
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, colors.obsidianBg.copy(alpha = 0.94f)),
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = active?.contract?.title ?: "Sparkborne Wilds",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.accentPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        if (active != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                active.nodes.forEach { n ->
                                    val isCur = n.index == active.currentNodeIndex
                                    val badgeColor = when {
                                        n.cleared -> colors.vitalityEmerald
                                        isCur -> colors.accentPrimary
                                        else -> colors.pixelBorderBright
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(if (isCur) 26.dp else 20.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(colors.obsidianBg.copy(alpha = 0.9f))
                                            .border(1.5.dp, badgeColor, androidx.compose.foundation.shape.CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = when (n.type) {
                                                com.sparkborne.game.model.DelveNodeType.COMBAT -> "⚔"
                                                com.sparkborne.game.model.DelveNodeType.FORGE_SHRINE -> "⚒"
                                                com.sparkborne.game.model.DelveNodeType.REST_CAMP -> "🔥"
                                                com.sparkborne.game.model.DelveNodeType.TREASURE -> "💰"
                                                com.sparkborne.game.model.DelveNodeType.BOSS -> "💀"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Select a roguelike contract to begin your delve",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textPrimary,
                            )
                        }
                    }
                    state.activeMonster?.let { mon ->
                        PixelSprite(
                            drawableRes = monsterDrawableFor(mon.affinity),
                            contentDescription = mon.name,
                            size = 46.dp,
                            borderColor = colors.accentPrimary,
                        )
                    }
                }
            }
        }

        errorMessage?.let { err ->
            item {
                RpgPanel(borderColor = colors.crimsonHp, containerColor = Color(0xFF2B1417)) {
                    Text(err, color = colors.crimsonHp, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (active == null) {
            item {
                Text(
                    "CONTRACT BOARD",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accentPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(GameCatalog.contracts, key = { it.id }) { contract ->
                RpgPanel(borderColor = colors.pixelBorderBright) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            contract.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        Text(
                            "${contract.cadence.displayName} · ${contract.difficulty.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accentPrimary,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Text(contract.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.sparkborne.app.ui.components.RpgCurrencyRow(
                            gold = contract.baseRewardGold,
                            iron = contract.baseRewardIron,
                            shards = contract.baseRewardShards,
                        )
                        if (contract.grantsEggOnClear) {
                            RpgStatChip("BONUS", "🥚 EGG", colors.vitalityEmerald)
                        }
                    }
                    RpgActionButton(
                        text = "ACCEPT CONTRACT (${contract.durationDays}d)",
                        onClick = { onStartContract(contract.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            item {
                ActiveExpeditionHeader(active, onAbandon, onClearFinished)
            }

            active.activeCombat?.let { combat ->
                item {
                    CombatEncounterArena(
                        state = state,
                        combat = combat,
                        onAction = { action ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCombatAction(action)
                        },
                    )
                }
            } ?: run {
                if (active.status == ExpeditionStatus.ACTIVE) {
                    items(active.nodes, key = { it.index }) { node ->
                        val isCurrent = node.index == active.currentNodeIndex
                        RpgPanel(
                            borderColor = when {
                                node.cleared -> colors.vitalityEmerald.copy(alpha = 0.6f)
                                isCurrent -> colors.accentPrimary
                                else -> colors.pixelBorder
                            },
                            containerColor = if (isCurrent) colors.panelElevated else colors.panelSurface,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "${node.index + 1}. ${node.title}",
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (node.cleared) colors.textMuted else colors.textPrimary,
                                )
                                Text(
                                    when {
                                        node.cleared -> "CLEARED"
                                        node.isUnlocked -> "READY"
                                        else -> "${node.stepsApplied}/${node.stepsRequired}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = when {
                                        node.cleared -> colors.vitalityEmerald
                                        node.isUnlocked -> colors.accentPrimary
                                        else -> colors.accentSecondary
                                    },
                                )
                            }
                            if (isCurrent && !node.cleared) {
                                RpgProgressBar(
                                    label = "Trail Steps",
                                    valueText = "${node.stepsApplied} / ${node.stepsRequired}",
                                    progress = node.stepsApplied.toFloat() / node.stepsRequired.coerceAtLeast(1),
                                    fillColor = colors.accentSecondary,
                                )
                                if (!node.isUnlocked && state.player.savedSteps > 0) {
                                    val needed = (node.stepsRequired - node.stepsApplied).coerceAtLeast(0)
                                    val toSpend = minOf(state.player.savedSteps, needed)
                                    RpgActionButton(
                                        text = "🥾 USE %,d BANKED STEPS".format(toSpend),
                                        onClick = onSpendBankedStepsOnDelve,
                                        containerColor = colors.vitalityEmerald,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                RpgActionButton(
                                    text = if (node.isUnlocked) "ENTER ${node.type.displayName.uppercase()}" else "SYNC OR SPEND STEPS TO UNLOCK",
                                    onClick = onEnterNode,
                                    enabled = node.isUnlocked,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.recentLog.isNotEmpty()) {
            item {
                RpgPanel {
                    Text(
                        "CHRONICLE",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textMuted,
                        fontFamily = FontFamily.Monospace,
                    )
                    state.recentLog.take(6).forEach { entry ->
                        Text("▸ $entry", style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ActiveExpeditionHeader(
    exp: ActiveExpedition,
    onAbandon: () -> Unit,
    onClearFinished: () -> Unit,
) {
    val colors = RpgTheme.colors
    val statusColor = when (exp.status) {
        ExpeditionStatus.ACTIVE -> colors.accentPrimary
        ExpeditionStatus.COMPLETED -> colors.vitalityEmerald
        ExpeditionStatus.FAILED -> colors.crimsonHp
    }

    RpgPanel(borderColor = statusColor) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(exp.contract.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(exp.status.name, color = statusColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        NodeTrailIndicator(nodes = exp.nodes, currentNodeIndex = exp.currentNodeIndex)
        RpgProgressBar(
            label = "HP",
            valueText = "${exp.runPlayerHp} / ${exp.runPlayerMaxHp}",
            progress = exp.runPlayerHp.toFloat() / exp.runPlayerMaxHp.coerceAtLeast(1),
            fillColor = colors.crimsonHp,
        )
        RpgProgressBar(
            label = "Mana",
            valueText = "${exp.runPlayerMana} / ${exp.runPlayerMaxMana}",
            progress = exp.runPlayerMana.toFloat() / exp.runPlayerMaxMana.coerceAtLeast(1),
            fillColor = colors.arcaneMana,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "UNBANKED LOOT (AT RISK)",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = colors.crimsonHp,
                fontWeight = FontWeight.Bold,
            )
            com.sparkborne.app.ui.components.RpgCurrencyRow(
                gold = exp.unbankedLoot.gold,
                iron = exp.unbankedLoot.ironOre,
                shards = exp.unbankedLoot.emberShards,
                showZeros = true,
                compact = true,
            )
        }
        Text(
            "Deadline ${exp.deadlineDate} · Steps ${exp.progress.totalStepsLogged}/${exp.contract.requirement.minTotalSteps} · " +
                "Lift ${exp.progress.totalLiftingVolumeKg.roundToInt()}/${exp.contract.requirement.minLiftingVolumeKg.roundToInt()} kg",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            fontFamily = FontFamily.Monospace,
        )
        exp.failureReason?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.crimsonHp)
        }
        if (exp.status == ExpeditionStatus.ACTIVE) {
            OutlinedButton(onClick = onAbandon, modifier = Modifier.fillMaxWidth()) {
                Text("Abandon Run (Forfeit Unbanked Loot)", color = colors.crimsonHp)
            }
        } else {
            RpgActionButton(
                text = "RETURN TO CONTRACT BOARD",
                onClick = onClearFinished,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CombatEncounterArena(
    state: GameState,
    combat: ActiveCombatState,
    onAction: (CombatAction) -> Unit,
) {
    val colors = RpgTheme.colors
    val heroLunge = remember { Animatable(0f) }
    val enemyShake = remember { Animatable(0f) }
    val hitFlashAlpha = remember { Animatable(0f) }

    // Continuous idle breathing/bobbing animation so the battle stage feels alive!
    val idleTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "combat_idle")
    val idleBob by idleTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "idle_bob",
    )

    LaunchedEffect(combat.turnNumber) {
        if (combat.turnNumber > 1) {
            heroLunge.snapTo(18f)
            heroLunge.animateTo(0f, tween(170))
            hitFlashAlpha.snapTo(0.45f)
            hitFlashAlpha.animateTo(0f, tween(260))
            enemyShake.snapTo(14f)
            enemyShake.animateTo(-10f, tween(55))
            enemyShake.animateTo(6f, tween(55))
            enemyShake.animateTo(0f, tween(55))
        }
    }

    RpgPanel(borderColor = colors.crimsonHp, containerColor = colors.panelElevated) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "⚔ TURN ${combat.turnNumber} · ${combat.enemy.name.uppercase()}",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "SHIELD ${combat.playerShield}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = colors.accentSecondary,
                fontWeight = FontWeight.Bold,
            )
        }

        // Visual Dungeon Battle Stage with breathing sprites, shadow floor, lunge & crimson hit flash!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF141824), Color(0xFF0A0D14)),
                    ),
                )
                .border(2.dp, colors.pixelBorderBright, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            // Subtle dungeon floor horizon line
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.panelSurface.copy(alpha = 0.65f)),
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.offset {
                        IntOffset(heroLunge.value.roundToInt(), idleBob.roundToInt())
                    },
                ) {
                    PixelSprite(
                        drawableRes = R.drawable.pixel_hero_knight,
                        contentDescription = "Hero",
                        size = 78.dp,
                        borderColor = colors.accentPrimary,
                    )
                    state.activeMonster?.let { mon ->
                        PixelSprite(
                            drawableRes = monsterDrawableFor(mon.affinity),
                            contentDescription = mon.name,
                            size = 50.dp,
                            borderColor = colors.accentSecondary,
                        )
                    }
                }

                Box(
                    modifier = Modifier.offset {
                        IntOffset(enemyShake.value.roundToInt(), (-idleBob).roundToInt())
                    },
                ) {
                    PixelSprite(
                        drawableRes = R.drawable.pixel_boss_encounter,
                        contentDescription = combat.enemy.name,
                        size = 86.dp,
                        borderColor = colors.crimsonHp,
                    )
                    if (hitFlashAlpha.value > 0.01f) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.crimsonHp.copy(alpha = hitFlashAlpha.value)),
                        )
                    }
                }
            }

            // Floating Turn Callout Pill at Top-Center of Battle Stage
            combat.combatLog.lastOrNull()?.let { latest ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.obsidianBg.copy(alpha = 0.90f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentPrimary),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Text(
                        text = latest,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = colors.accentPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        RpgProgressBar(
            label = "${combat.enemy.name} (ATK ${combat.enemy.attack} · DEF ${combat.enemy.defense})",
            valueText = "${combat.enemy.currentHp} / ${combat.enemy.maxHp} HP",
            progress = combat.enemy.currentHp.toFloat() / combat.enemy.maxHp.coerceAtLeast(1),
            fillColor = colors.crimsonHp,
            barHeight = 11.dp,
        )

        // Crisp 2x2 Tactical Command Deck
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RpgActionButton(
                text = "⚔ STRIKE",
                onClick = { onAction(CombatAction.Strike) },
                modifier = Modifier.weight(1f),
            )
            RpgActionButton(
                text = "🛡 GUARD",
                onClick = { onAction(CombatAction.Guard) },
                containerColor = colors.ironSteel,
                modifier = Modifier.weight(1f),
            )
        }

        RpgActionButton(
            text = if (combat.monsterSkillCooldown == 0) {
                "🐉 ${state.activeMonster?.name?.uppercase() ?: "BEAST"} SKILL"
            } else {
                "🐉 COMPANION COOLDOWN (${combat.monsterSkillCooldown}T)"
            },
            onClick = { onAction(CombatAction.MonsterSkill) },
            enabled = state.activeMonster != null && combat.monsterSkillCooldown == 0,
            containerColor = colors.accentSecondary,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            state.player.attunedSpellIds.mapNotNull(GameCatalog::spellById).forEach { spell ->
                val canCast = combat.playerMana >= spell.manaCost
                RpgActionButton(
                    text = "✨ ${spell.name} (${spell.manaCost}m)",
                    onClick = { onAction(CombatAction.CastSpell(spell.id)) },
                    enabled = canCast,
                    containerColor = colors.arcaneMana,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
