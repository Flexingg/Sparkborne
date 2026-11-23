package com.sparkborne.app.ui.components

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sparkborne.app.R
import com.sparkborne.app.data.AppUpdateInfo
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.app.ui.theme.SparkborneThemeId
import com.sparkborne.app.ui.theme.SplashStyleId
import com.sparkborne.game.model.DelveNode
import com.sparkborne.game.model.MonsterAffinity

@DrawableRes
fun monsterDrawableFor(affinity: MonsterAffinity): Int {
    return when (affinity) {
        MonsterAffinity.EMBERHEART -> R.drawable.pixel_monster_ember
        MonsterAffinity.IRONCLAD -> R.drawable.pixel_monster_ironclad
        MonsterAffinity.STORMSTRIDER -> R.drawable.pixel_monster_storm
        MonsterAffinity.DEEPFORGE -> R.drawable.pixel_monster_deepforge
    }
}

/**
 * Renders a crisp 64-bit pixel art sprite using nearest-neighbor sampling ([FilterQuality.None])
 * inside a WalkScape-inspired beveled dark frame.
 */
@Composable
fun PixelSprite(
    @DrawableRes drawableRes: Int,
    contentDescription: String,
    size: Dp,
    modifier: Modifier = Modifier,
    borderColor: Color = RpgTheme.colors.pixelBorderBright,
) {
    val context = LocalContext.current
    val bitmap = remember(drawableRes) {
        BitmapFactory.decodeResource(context.resources, drawableRes).asImageBitmap()
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(RpgTheme.colors.panelSurface)
            .border(2.dp, borderColor, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.None,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
fun PixelBannerImage(
    @DrawableRes drawableRes: Int,
    contentDescription: String,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = remember(drawableRes) {
        BitmapFactory.decodeResource(context.resources, drawableRes).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.None,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    )
}

/**
 * WalkScape-style dark slate RPG panel with crisp theme-aware pixel border.
 */
@Composable
fun RpgPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = RpgTheme.colors.pixelBorder,
    containerColor: Color = RpgTheme.colors.panelSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(1.5.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/**
 * Tactile spring-pressed RPG button with subtle scale compression and haptic tick.
 */
@Composable
fun RpgActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = RpgTheme.colors.accentPrimary,
    contentColor: Color = Color(0xFF0B0E14),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "btn_spring",
    )
    val haptics = LocalHapticFeedback.current

    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(7.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Chunky animated RPG progress bar with crisp border and monospace value readout.
 */
@Composable
fun RpgProgressBar(
    label: String,
    valueText: String,
    progress: Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
    barHeight: Dp = 10.dp,
) {
    val colors = RpgTheme.colors
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 380),
        label = "rpg_bar",
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = fillColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.obsidianBg)
                .border(1.dp, colors.pixelBorder, RoundedCornerShape(4.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(fillColor),
            )
        }
    }
}

/**
 * WalkScape-inspired horizontal connected node trail showing expedition progress at a glance.
 */
@Composable
fun NodeTrailIndicator(
    nodes: List<DelveNode>,
    currentNodeIndex: Int,
    modifier: Modifier = Modifier,
) {
    val colors = RpgTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        nodes.forEachIndexed { idx, node ->
            val nodeColor = when {
                node.cleared -> colors.vitalityEmerald
                idx == currentNodeIndex && node.isUnlocked -> colors.accentPrimary
                idx == currentNodeIndex -> colors.accentSecondary
                else -> colors.pixelBorder
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(nodeColor),
            )
        }
    }
}

@Composable
fun RpgStatChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = RpgTheme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = colors.obsidianBg,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

enum class CurrencyKind(
    val label: String,
    @DrawableRes val iconRes: Int,
    val tintColor: Color,
) {
    GOLD("Gold", R.drawable.icon_currency_gold, Color(0xFFF5B041)),
    IRON("Iron", R.drawable.icon_currency_iron, Color(0xFFB0C4DE)),
    SHARDS("Shards", R.drawable.icon_currency_shards, Color(0xFFA78BFA)),
}

/**
 * Gleaming pixel-art currency badge with beveled border, crisp nearest-neighbor icon,
 * and tabular monospace readout.
 */
@Composable
fun RpgCurrencyBadge(
    kind: CurrencyKind,
    amount: Int,
    modifier: Modifier = Modifier,
    prefix: String = "",
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val bitmap = remember(kind.iconRes) {
        BitmapFactory.decodeResource(context.resources, kind.iconRes).asImageBitmap()
    }
    val colors = RpgTheme.colors
    val formatted = "%,d".format(amount)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 6.dp else 7.dp),
        color = colors.obsidianBg.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, kind.tintColor.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 3.dp else 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = kind.label,
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(if (compact) 14.dp else 17.dp),
            )
            Text(
                text = "$prefix$formatted",
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = kind.tintColor,
            )
        }
    }
}

/**
 * Displays any non-zero combination of Gold, Iron Ore, and Ember Shards as gleaming pixel currency pills.
 */
@Composable
fun RpgCurrencyRow(
    gold: Int = 0,
    iron: Int = 0,
    shards: Int = 0,
    modifier: Modifier = Modifier,
    showZeros: Boolean = false,
    prefix: String = "",
    compact: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showZeros || gold > 0) {
            RpgCurrencyBadge(CurrencyKind.GOLD, gold, prefix = prefix, compact = compact)
        }
        if (showZeros || iron > 0) {
            RpgCurrencyBadge(CurrencyKind.IRON, iron, prefix = prefix, compact = compact)
        }
        if (showZeros || shards > 0) {
            RpgCurrencyBadge(CurrencyKind.SHARDS, shards, prefix = prefix, compact = compact)
        }
    }
}

@Composable
fun UpdateAvailableBanner(
    update: AppUpdateInfo,
    downloadProgress: Float?,
    onDownloadAndInstall: () -> Unit,
) {
    val colors = RpgTheme.colors
    RpgPanel(
        borderColor = colors.accentPrimary,
        containerColor = colors.panelElevated,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Update Ready · ${update.tagName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.accentPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Updates in-place without losing your save or API key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            RpgActionButton(
                text = if (downloadProgress == null) "UPDATE" else "${(downloadProgress * 100).toInt()}%",
                onClick = onDownloadAndInstall,
                enabled = downloadProgress == null,
            )
        }
        if (downloadProgress != null) {
            LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier.fillMaxWidth(),
                color = colors.accentPrimary,
            )
        }
    }
}

/**
 * Interactive 7-theme elemental picker (Fire, Explorer, Lightning, Water, Plant, Poison, Void)
 * + 3 animated splash screen backdrops + instant "Replay Splash" preview button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ElementalAppearanceCard(
    selectedTheme: SparkborneThemeId,
    selectedSplash: SplashStyleId,
    onSelectTheme: (SparkborneThemeId) -> Unit,
    onSelectSplash: (SplashStyleId) -> Unit,
    onReplaySplash: () -> Unit,
) {
    val colors = RpgTheme.colors
    RpgPanel(borderColor = colors.pixelBorderBright) {
        Text(
            "ELEMENTAL THEME & SPLASH",
            style = MaterialTheme.typography.labelLarge,
            color = colors.accentPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Switch realm palettes & live splash particle elements anytime:",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SparkborneThemeId.entries.forEach { theme ->
                val active = theme == selectedTheme
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = if (active) theme.palette.panelElevated else colors.obsidianBg,
                    border = BorderStroke(
                        width = if (active) 2.dp else 1.dp,
                        color = if (active) theme.palette.accentPrimary else colors.pixelBorder,
                    ),
                    modifier = Modifier.clickable { onSelectTheme(theme) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(theme.palette.accentPrimary),
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(theme.palette.accentSecondary),
                        )
                        Text(
                            text = "${theme.elementTag} · ${theme.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) theme.palette.accentPrimary else colors.textPrimary,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        Text(
            "ANIMATED SPLASH SCENE",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted,
            fontFamily = FontFamily.Monospace,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SplashStyleId.entries.forEach { splash ->
                val active = splash == selectedSplash
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (active) colors.panelElevated else colors.obsidianBg,
                    border = BorderStroke(
                        width = if (active) 2.dp else 1.dp,
                        color = if (active) colors.accentPrimary else colors.pixelBorder,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectSplash(splash) },
                ) {
                    Text(
                        text = splash.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) colors.accentPrimary else colors.textPrimary,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }

        OutlinedButton(onClick = onReplaySplash, modifier = Modifier.fillMaxWidth()) {
            Text("Preview Animated Splash Screen", fontFamily = FontFamily.Monospace)
        }
    }
}
