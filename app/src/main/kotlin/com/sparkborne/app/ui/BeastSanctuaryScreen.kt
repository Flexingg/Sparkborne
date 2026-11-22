package com.sparkborne.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.R
import com.sparkborne.app.ui.components.PixelSprite
import com.sparkborne.app.ui.components.RpgActionButton
import com.sparkborne.app.ui.components.RpgPanel
import com.sparkborne.app.ui.components.RpgProgressBar
import com.sparkborne.app.ui.components.RpgStatChip
import com.sparkborne.app.ui.components.monsterDrawableFor
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.MonsterAffinity
import com.sparkborne.game.model.MonsterStage
import kotlin.math.roundToInt

private fun affinityColor(affinity: MonsterAffinity): Color = when (affinity) {
    MonsterAffinity.EMBERHEART -> Color(0xFFF5B041)
    MonsterAffinity.IRONCLAD -> Color(0xFF94A3B8)
    MonsterAffinity.STORMSTRIDER -> Color(0xFF38BDF8)
    MonsterAffinity.DEEPFORGE -> Color(0xFF4ADE80)
}

/**
 * Component 4: Living Beast Sanctuary Enclosure, Visual Evolution Track,
 * Glowing Egg Incubator Pedestal, and 2-Column Companion Roster.
 */
@Composable
fun MonstersTabScreen(
    state: GameState,
    errorMessage: String?,
    onSelectActive: (String) -> Unit,
    onEvolve: (String) -> Unit,
) {
    val colors = RpgTheme.colors
    val activeMon = state.activeMonster

    val anim = rememberInfiniteTransition(label = "sanctuary_anim")
    val beastBob by anim.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "beast_bob",
    )
    val eggWobble by anim.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse),
        label = "egg_wobble",
    )
    val auraPulse by anim.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "aura_pulse",
    )

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

        // 1. Living Active Companion Sanctuary Enclosure
        activeMon?.let { mon ->
            val affColor = affinityColor(mon.affinity)
            item {
                RpgPanel(borderColor = affColor, containerColor = colors.panelElevated) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "ACTIVE PARTNER · ${mon.name.uppercase()}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = affColor,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = "Lv.${mon.level} ${mon.stage.displayName} · ${mon.affinity.displayName} Affinity",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.obsidianBg,
                            border = BorderStroke(1.dp, affColor),
                        ) {
                            Text(
                                text = mon.affinity.synergyTitle.uppercase(),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = affColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // Sanctuary Diorama Stage
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(142.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(affColor.copy(alpha = auraPulse * 0.45f), colors.obsidianBg),
                                ),
                            )
                            .border(1.5.dp, affColor.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Pedestal shadow
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 14.dp)
                                .fillMaxWidth(0.55f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(50))
                                .background(affColor.copy(alpha = 0.22f)),
                        )
                        PixelSprite(
                            drawableRes = monsterDrawableFor(mon.affinity),
                            contentDescription = mon.name,
                            size = 104.dp,
                            borderColor = affColor,
                            modifier = Modifier.offset { IntOffset(0, beastBob.roundToInt()) },
                        )
                    }

                    // Visual 3-Stage Evolution Track (Hatchling -> Juvenile -> Apex)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MonsterStage.entries.forEach { stage ->
                            val reached = mon.stage.ordinal >= stage.ordinal
                            val isCurrent = mon.stage == stage
                            val species = GameCatalog.monsterSpeciesName(mon.affinity, stage)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isCurrent) colors.obsidianBg else colors.panelSurface,
                                border = BorderStroke(
                                    width = if (isCurrent) 1.5.dp else 1.dp,
                                    color = if (reached) affColor else colors.pixelBorder,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = stage.displayName.uppercase(),
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (reached) affColor else colors.textMuted,
                                    )
                                    Text(
                                        text = if (reached) species else "???",
                                        fontSize = 9.sp,
                                        color = if (reached) colors.textPrimary else colors.textMuted,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    // XP to Next Evolution + Stats
                    val reqXp = mon.stage.requiredXpToEvolve
                    if (reqXp != null) {
                        RpgProgressBar(
                            label = "Evolution XP (Macros + Delves)",
                            valueText = "${mon.xp} / $reqXp",
                            progress = mon.xp.toFloat() / reqXp.coerceAtLeast(1),
                            fillColor = affColor,
                            barHeight = 9.dp,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RpgStatChip("ATK", "${mon.baseAttack}", colors.accentPrimary, Modifier.weight(1f))
                        RpgStatChip("DEF", "${mon.baseDefense}", colors.accentSecondary, Modifier.weight(1f))
                        RpgStatChip("DAYS FED", "${mon.macroProfile.daysTracked}", colors.vitalityEmerald, Modifier.weight(1f))
                    }
                    Text(
                        text = "Duo Synergy: ${mon.affinity.synergyDescription}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accentSecondary,
                    )

                    if (mon.canEvolve) {
                        RpgActionButton(
                            text = "✦ EVOLVE ${mon.name.uppercase()} NOW!",
                            onClick = { onEvolve(mon.id) },
                            containerColor = affColor,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // 2. Glowing Egg Incubator Pedestal (Sleep + Hydration)
        item {
            Text(
                "EGG INCUBATOR PEDESTAL (SLEEP + HYDRATION)",
                style = MaterialTheme.typography.labelLarge,
                color = colors.vitalityEmerald,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        if (state.eggs.isEmpty()) {
            item {
                RpgPanel {
                    Text(
                        "Incubator empty. Clear an egg-bearing delve contract or visit a Town Shop to acquire a new Monster Egg.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
        } else {
            items(state.eggs, key = { it.id }) { egg ->
                val hatchPct = (egg.progressFraction * 100).roundToInt()
                RpgPanel(borderColor = colors.vitalityEmerald, containerColor = colors.panelElevated) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.radialGradient(
                                        listOf(colors.vitalityEmerald.copy(alpha = auraPulse * 0.45f), colors.obsidianBg),
                                    ),
                                )
                                .padding(6.dp),
                        ) {
                            PixelSprite(
                                drawableRes = R.drawable.pixel_egg_incubator,
                                contentDescription = egg.name,
                                size = 74.dp,
                                borderColor = colors.vitalityEmerald,
                                modifier = Modifier.graphicsLayer { rotationZ = eggWobble },
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(egg.name, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                                Text(
                                    "$hatchPct%",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.vitalityEmerald,
                                )
                            }
                            Text(
                                "Macro Forecast: ${egg.macroProfile.dominantAffinity().displayName}",
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

        // 3. Companion Roster Grid (Switch Active Partner)
        if (state.monsters.size > 1) {
            item {
                Text(
                    "SANCTUARY ROSTER (${state.monsters.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accentPrimary,
                    fontFamily = FontFamily.Monospace,
                )
            }
            val pairs = state.monsters.chunked(2)
            items(pairs) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { mon ->
                        val isActive = mon.id == activeMon?.id
                        val affColor = affinityColor(mon.affinity)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isActive) colors.panelElevated else colors.panelSurface,
                            border = BorderStroke(if (isActive) 2.dp else 1.dp, if (isActive) affColor else colors.pixelBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectActive(mon.id) },
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PixelSprite(
                                    drawableRes = monsterDrawableFor(mon.affinity),
                                    contentDescription = mon.name,
                                    size = 44.dp,
                                    borderColor = affColor,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mon.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                                    Text("Lv.${mon.level} ${mon.stage.displayName}", fontSize = 10.sp, color = colors.textMuted)
                                    Text(
                                        if (isActive) "ACTIVE" else "TAP TO BOND",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isActive) affColor else colors.textMuted,
                                    )
                                }
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
