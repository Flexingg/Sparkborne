package com.sparkborne.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = active?.contract?.title ?: "Sparkborne Wilds",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.accentPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = active?.let {
                                "Node ${it.currentNodeIndex + 1}/${it.nodes.size} · ${it.contract.difficulty.displayName}"
                            } ?: "Select a roguelike contract to begin your delve",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textPrimary,
                        )
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RpgStatChip("GOLD", "${contract.baseRewardGold}", colors.accentPrimary)
                        RpgStatChip("SHARDS", "${contract.baseRewardShards}", colors.arcaneMana)
                        RpgStatChip("IRON", "${contract.baseRewardIron}", colors.ironSteel)
                        if (contract.grantsEggOnClear) {
                            RpgStatChip("BONUS", "EGG", colors.vitalityEmerald)
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
                                RpgActionButton(
                                    text = if (node.isUnlocked) "ENTER ${node.type.displayName.uppercase()}" else "SYNC STEPS TO UNLOCK",
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RpgStatChip("AT RISK GOLD", "${exp.unbankedLoot.gold}", colors.accentPrimary, Modifier.weight(1f))
            RpgStatChip("SHARDS", "${exp.unbankedLoot.emberShards}", colors.arcaneMana, Modifier.weight(1f))
            RpgStatChip("IRON", "${exp.unbankedLoot.ironOre}", colors.ironSteel, Modifier.weight(1f))
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

    LaunchedEffect(combat.turnNumber) {
        if (combat.turnNumber > 1) {
            heroLunge.snapTo(14f)
            heroLunge.animateTo(0f, tween(160))
            enemyShake.snapTo(12f)
            enemyShake.animateTo(-10f, tween(55))
            enemyShake.animateTo(6f, tween(55))
            enemyShake.animateTo(0f, tween(55))
        }
    }

    RpgPanel(borderColor = colors.crimsonHp, containerColor = colors.panelElevated) {
        Text(
            "TURN ${combat.turnNumber} · ${combat.enemy.name.uppercase()}",
            style = MaterialTheme.typography.labelLarge,
            color = colors.accentPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )

        // Pixel Combat Stage: Hero lunge + Companion vs Shaking Boss/Enemy
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.obsidianBg)
                .border(1.dp, colors.pixelBorder, RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.offset { IntOffset(heroLunge.value.roundToInt(), 0) },
                ) {
                    PixelSprite(
                        drawableRes = R.drawable.pixel_hero_knight,
                        contentDescription = "Hero",
                        size = 72.dp,
                        borderColor = colors.accentPrimary,
                    )
                    state.activeMonster?.let { mon ->
                        PixelSprite(
                            drawableRes = monsterDrawableFor(mon.affinity),
                            contentDescription = mon.name,
                            size = 48.dp,
                            borderColor = colors.accentSecondary,
                        )
                    }
                }
                Text("VS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = colors.crimsonHp)
                PixelSprite(
                    drawableRes = R.drawable.pixel_boss_encounter,
                    contentDescription = combat.enemy.name,
                    size = 76.dp,
                    borderColor = colors.crimsonHp,
                    modifier = Modifier.offset { IntOffset(enemyShake.value.roundToInt(), 0) },
                )
            }
        }

        // Latest turn floating callout banner
        val latestEvent = combat.combatLog.lastOrNull()
        AnimatedVisibility(
            visible = latestEvent != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            if (latestEvent != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.obsidianBg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = latestEvent,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = colors.accentPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        RpgProgressBar(
            label = combat.enemy.name,
            valueText = "${combat.enemy.currentHp} / ${combat.enemy.maxHp} HP",
            progress = combat.enemy.currentHp.toFloat() / combat.enemy.maxHp.coerceAtLeast(1),
            fillColor = colors.crimsonHp,
        )
        Text(
            "Shield: ${combat.playerShield} · Enemy ATK ${combat.enemy.attack} · DEF ${combat.enemy.defense}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            fontFamily = FontFamily.Monospace,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RpgActionButton(
                text = "STRIKE",
                onClick = { onAction(CombatAction.Strike) },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { onAction(CombatAction.Guard) },
                modifier = Modifier.weight(1f),
            ) {
                Text("GUARD", fontFamily = FontFamily.Monospace)
            }
        }

        RpgActionButton(
            text = if (combat.monsterSkillCooldown == 0) {
                "COMPANION · ${state.activeMonster?.name?.uppercase() ?: "NONE"}"
            } else {
                "COMPANION COOLDOWN (${combat.monsterSkillCooldown}T)"
            },
            onClick = { onAction(CombatAction.MonsterSkill) },
            enabled = state.activeMonster != null && combat.monsterSkillCooldown == 0,
            containerColor = colors.accentSecondary,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            state.player.attunedSpellIds.mapNotNull(GameCatalog::spellById).forEach { spell ->
                OutlinedButton(
                    onClick = { onAction(CombatAction.CastSpell(spell.id)) },
                    enabled = combat.playerMana >= spell.manaCost,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("${spell.name} (${spell.manaCost}m)", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun HeroTabScreen(
    state: GameState,
    errorMessage: String?,
    onForgeOrEquipWeapon: (String) -> Unit,
    onForgeOrEquipArmor: (String) -> Unit,
    onUnlockOrAttuneSpell: (String) -> Unit,
) {
    val stats = CombatStatsCalculator.calculate(state)
    val player = state.player
    val colors = RpgTheme.colors

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        errorMessage?.let { err ->
            item {
                RpgPanel(borderColor = colors.crimsonHp) {
                    Text(err, color = colors.crimsonHp, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            RpgPanel(borderColor = colors.accentPrimary) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelSprite(
                        drawableRes = R.drawable.pixel_hero_knight,
                        contentDescription = "Sparkborne Hero",
                        size = 82.dp,
                        borderColor = colors.accentPrimary,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Sparkborne Knight · Lv.${player.level}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentPrimary,
                        )
                        RpgProgressBar(
                            label = "XP",
                            valueText = "${player.xp} / ${player.xpToNextLevel()}",
                            progress = player.xp.toFloat() / player.xpToNextLevel().coerceAtLeast(1),
                            fillColor = colors.accentPrimary,
                            barHeight = 8.dp,
                        )
                        Text(
                            "Stance: ${player.activeBuffs.momentumStance.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.vitalityEmerald,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RpgStatChip("GOLD", "${player.gold}", colors.accentPrimary, Modifier.weight(1f))
                    RpgStatChip("SHARDS", "${player.emberShards}", colors.arcaneMana, Modifier.weight(1f))
                    RpgStatChip("IRON", "${player.ironOre}", colors.ironSteel, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RpgStatChip("HP", "${stats.maxHp}", colors.crimsonHp, Modifier.weight(1f))
                    RpgStatChip("ATK", "${stats.weaponDamage}", colors.accentPrimary, Modifier.weight(1f))
                    RpgStatChip("SPELL", "${stats.spellPower}", colors.arcaneMana, Modifier.weight(1f))
                    RpgStatChip("DEF", "${stats.defense}", colors.accentSecondary, Modifier.weight(1f))
                }
            }
        }

        item {
            RpgPanel {
                Text(
                    "MUSCLE GROUP STRENGTH (LIFTING → WEAPON DMG)",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accentPrimary,
                    fontFamily = FontFamily.Monospace,
                )
                val tracked = MuscleGroup.entries.filter { it != MuscleGroup.OTHER }
                tracked.forEach { group ->
                    val xp = player.muscleStrengthXp[group] ?: 0.0
                    val rank = player.muscleStrengthRank(group)
                    val nextRankVolume = (rank * rank) * 250.0
                    val prevRankVolume = ((rank - 1) * (rank - 1)) * 250.0
                    val frac = ((xp - prevRankVolume) / (nextRankVolume - prevRankVolume).coerceAtLeast(1.0)).toFloat()
                    RpgProgressBar(
                        label = "${group.displayName} · Rank $rank",
                        valueText = "${xp.roundToInt()} kg",
                        progress = frac,
                        fillColor = colors.accentPrimary,
                        barHeight = 7.dp,
                    )
                }
            }
        }

        item {
            Text("WEAPONS", style = MaterialTheme.typography.labelLarge, color = colors.accentPrimary, fontFamily = FontFamily.Monospace)
        }
        items(GameCatalog.weapons, key = { it.id }) { weapon ->
            val owned = weapon.id in player.ownedWeaponIds
            val equipped = player.equippedWeaponId == weapon.id
            RpgPanel(borderColor = if (equipped) colors.accentPrimary else colors.pixelBorder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(weapon.name, fontWeight = FontWeight.Bold)
                        Text(
                            "${weapon.weaponClass.displayName} · Base ${weapon.baseDamage} · ${weapon.weaponClass.scalingMuscles.joinToString { it.displayName }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                    RpgActionButton(
                        text = when {
                            equipped -> "EQUIPPED"
                            owned -> "EQUIP"
                            else -> "${weapon.forgeGoldCost}g/${weapon.forgeIronCost}fe"
                        },
                        onClick = { onForgeOrEquipWeapon(weapon.id) },
                        enabled = !equipped,
                    )
                }
            }
        }

        item {
            Text("ARMOR FORGE", style = MaterialTheme.typography.labelLarge, color = colors.accentSecondary, fontFamily = FontFamily.Monospace)
        }
        items(GameCatalog.armor, key = { it.id }) { armor ->
            val owned = armor.id in player.ownedArmorIds
            val equipped = player.equippedArmor[armor.slot] == armor.id
            RpgPanel(borderColor = if (equipped) colors.accentSecondary else colors.pixelBorder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${armor.name} (${armor.slot.displayName})", fontWeight = FontWeight.Bold)
                        Text(
                            "DEF +${armor.defense} · HP +${armor.bonusHp} · Mana +${armor.bonusMana}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                    RpgActionButton(
                        text = when {
                            equipped -> "EQUIPPED"
                            owned -> "EQUIP"
                            else -> "FORGE ${armor.forgeGoldCost}g"
                        },
                        onClick = { onForgeOrEquipArmor(armor.id) },
                        enabled = !equipped,
                        containerColor = colors.accentSecondary,
                    )
                }
            }
        }

        item {
            Text("SPELLBOOK (ATTUNE UP TO 3)", style = MaterialTheme.typography.labelLarge, color = colors.arcaneMana, fontFamily = FontFamily.Monospace)
        }
        items(GameCatalog.spells, key = { it.id }) { spell ->
            val unlocked = spell.id in player.unlockedSpellIds
            val attuned = spell.id in player.attunedSpellIds
            RpgPanel(borderColor = if (attuned) colors.arcaneMana else colors.pixelBorder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${spell.name} (${spell.manaCost} Mana)", fontWeight = FontWeight.Bold)
                        Text(spell.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    RpgActionButton(
                        text = when {
                            attuned -> "ATTUNED"
                            unlocked -> "ATTUNE"
                            else -> "${spell.unlockShardsCost}sh"
                        },
                        onClick = { onUnlockOrAttuneSpell(spell.id) },
                        enabled = !attuned,
                        containerColor = colors.arcaneMana,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun MonstersTabScreen(
    state: GameState,
    errorMessage: String?,
    onSelectActive: (String) -> Unit,
    onEvolve: (String) -> Unit,
) {
    val colors = RpgTheme.colors

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        errorMessage?.let { err ->
            item {
                RpgPanel(borderColor = colors.crimsonHp) {
                    Text(err, color = colors.crimsonHp, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Text("COMPANION SANCTUARY", style = MaterialTheme.typography.labelLarge, color = colors.accentPrimary, fontFamily = FontFamily.Monospace)
        }
        items(state.monsters, key = { it.id }) { mon ->
            val isActive = mon.id == state.activeMonster?.id
            RpgPanel(borderColor = if (isActive) colors.accentPrimary else colors.pixelBorder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelSprite(
                        drawableRes = monsterDrawableFor(mon.affinity),
                        contentDescription = mon.name,
                        size = 76.dp,
                        borderColor = if (isActive) colors.accentPrimary else colors.pixelBorderBright,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${mon.name} · Lv.${mon.level}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${mon.stage.displayName} · ${mon.affinity.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accentPrimary,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "ATK ${mon.baseAttack} · DEF ${mon.baseDefense} · XP ${mon.xp}/${mon.stage.requiredXpToEvolve ?: "MAX"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
                Text(
                    "Duo Synergy (${mon.affinity.synergyTitle}): ${mon.affinity.synergyDescription}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accentSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSelectActive(mon.id) }, enabled = !isActive) {
                        Text(if (isActive) "Active Partner" else "Set Active")
                    }
                    if (mon.canEvolve) {
                        RpgActionButton(text = "EVOLVE!", onClick = { onEvolve(mon.id) })
                    }
                }
            }
        }

        item {
            Text("EGG INCUBATOR (SLEEP + HYDRATION)", style = MaterialTheme.typography.labelLarge, color = colors.vitalityEmerald, fontFamily = FontFamily.Monospace)
        }
        if (state.eggs.isEmpty()) {
            item {
                RpgPanel {
                    Text(
                        "No eggs incubating. Clear an egg-bearing contract to earn a new Monster Egg.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
        } else {
            items(state.eggs, key = { it.id }) { egg ->
                RpgPanel(borderColor = colors.vitalityEmerald) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelSprite(
                            drawableRes = R.drawable.pixel_egg_incubator,
                            contentDescription = egg.name,
                            size = 72.dp,
                            borderColor = colors.vitalityEmerald,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(egg.name, fontWeight = FontWeight.Bold)
                            Text(
                                "Projected Affinity: ${egg.macroProfile.dominantAffinity().displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accentPrimary,
                                fontFamily = FontFamily.Monospace,
                            )
                            RpgProgressBar(
                                label = "Sleep Warmth",
                                valueText = "${egg.incubatedSleepMinutes}/${egg.requiredSleepMinutes}m",
                                progress = egg.incubatedSleepMinutes.toFloat() / egg.requiredSleepMinutes.coerceAtLeast(1),
                                fillColor = colors.vitalityEmerald,
                                barHeight = 8.dp,
                            )
                            RpgProgressBar(
                                label = "Hydration Dew",
                                valueText = "${egg.incubatedHydrationMl}/${egg.requiredHydrationMl}mL",
                                progress = egg.incubatedHydrationMl.toFloat() / egg.requiredHydrationMl.coerceAtLeast(1),
                                fillColor = colors.hydrationCyan,
                                barHeight = 8.dp,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
