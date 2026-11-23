package com.sparkborne.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.R
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.WorldCatalog
import com.sparkborne.game.model.GameState
import kotlin.math.roundToInt

/**
 * WalkScape-inspired pinned edge-to-edge RPG Top HUD Bar:
 * - Pinned flush to the top of the screen (extends behind Android status bar)
 * - Oversized circular Hero Avatar Medallion on the top-left ringed by a live XP progress arc
 * - Oversized circular Live Mini-Map Medallion on the top-right showing the actual cropped
 *   pixel-art world map centered on the Hero's current position (or travel road position!)
 * - Compact middle strip with Level badge, Location name, Currencies, Step Bank AP, and
 *   tiny utility triggers (🔔 Alerts, 📖 Sparkpedia, ⚡ Sync).
 */
@Composable
fun WalkscapeTopHudBar(
    game: GameState,
    alertCount: Int,
    showNotificationsPanel: Boolean,
    isSyncing: Boolean,
    onAvatarClick: () -> Unit,
    onMiniMapClick: () -> Unit,
    onToggleNotifications: () -> Unit,
    onOpenSparkpedia: () -> Unit,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RpgTheme.colors
    val player = game.player
    val xpNeeded = player.xpToNextLevel().coerceAtLeast(1)
    val xpFraction = (player.xp.toFloat() / xpNeeded.toFloat()).coerceIn(0f, 1f)
    val stepCap = player.maxStepBankCapacity()

    val currentLoc = WorldCatalog.locationById(game.currentLocationId)
    val heroWorldFrac: Pair<Float, Float> = game.activeTravel?.let { travel ->
        val from = WorldCatalog.locationById(travel.fromLocationId)
        val to = WorldCatalog.locationById(travel.toLocationId)
        val t = travel.progressFraction
        (from.mapX + (to.mapX - from.mapX) * t) to (from.mapY + (to.mapY - from.mapY) * t)
    } ?: (currentLoc.mapX to currentLoc.mapY)

    val locationTitle = game.activeTravel?.let { travel ->
        "→ ${WorldCatalog.locationById(travel.toLocationId).name.substringBefore(" ")}"
    } ?: currentLoc.name.substringBefore(" ")

    val activeProgress: Float? = when {
        game.activeTravel != null -> game.activeTravel!!.progressFraction
        game.activeActivity != null -> {
            val act = game.activeActivity!!
            val def = WorldCatalog.activityById(act.activityId)
            if (def != null && def.stepsPerAction > 0) {
                (act.stepsProgress.toFloat() / def.stepsPerAction.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
        }
        else -> null
    }

    val stats = com.sparkborne.game.engine.CombatStatsCalculator.calculate(game)
    val currentHp = game.activeExpedition?.runPlayerHp ?: stats.maxHp
    val maxHp = game.activeExpedition?.runPlayerMaxHp ?: stats.maxHp
    val context = LocalContext.current
    val goldIcon = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.icon_currency_gold).asImageBitmap()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent),
    ) {
        // 1. Pinned Full-Width Dark RPG Header Strip (extends behind Android status bar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.obsidianBg.copy(alpha = 0.96f))
                .statusBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(start = 72.dp, end = 72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    // Top Row: Level + Location Name + Clean Borderless Icon Triggers (🔔, 📖, ⚡)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "LV.${player.level}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.accentPrimary,
                            )
                            Text(
                                text = locationTitle,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (alertCount > 0) "🔔$alertCount" else "🔔",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (alertCount > 0 || showNotificationsPanel) colors.accentPrimary else colors.textMuted,
                                modifier = Modifier.clickable(onClick = onToggleNotifications),
                            )
                            Text(
                                text = if (game.unreadCodexIds.isNotEmpty()) "📖•" else "📖",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = if (game.unreadCodexIds.isNotEmpty()) colors.accentPrimary else colors.textMuted,
                                modifier = Modifier.clickable(onClick = onOpenSparkpedia),
                            )
                            Text(
                                text = if (isSyncing) "…" else "⚡",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentSecondary,
                                modifier = Modifier.clickable(enabled = !isSyncing, onClick = onSyncClick),
                            )
                        }
                    }

                    // Bottom Row: Borderless Coins, Health, Combat Stats & Step Bank AP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Bare pixel gold coin + amount
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Image(
                                bitmap = goldIcon,
                                contentDescription = "Gold",
                                filterQuality = FilterQuality.None,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "${player.gold}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFD166),
                            )
                        }

                        // Health
                        Text(
                            text = "❤$currentHp/$maxHp",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.crimsonHp,
                        )

                        // Attack & Defense
                        Text(
                            text = "⚔${stats.weaponDamage} 🛡${stats.defense}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )

                        // Step Bank AP
                        Text(
                            text = "🥾${compactSteps(player.savedSteps)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.vitalityEmerald,
                        )
                    }
                }
            }

            // Crisp Bottom Border + Optional 2dp Activity/Travel Hairline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.pixelBorderBright.copy(alpha = 0.7f)),
            ) {
                if (activeProgress != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(activeProgress.coerceIn(0.03f, 1f))
                            .height(2.dp)
                            .background(if (game.activeTravel != null) colors.accentPrimary else colors.accentSecondary),
                    )
                }
            }
        }

        // 2. Top-Left Oversized Circular Hero Avatar Medallion (with live XP Ring, overhanging bottom edge!)
        HeroAvatarMedallion(
            xpFraction = xpFraction,
            accentColor = colors.accentPrimary,
            bgColor = colors.panelSurface,
            trackColor = colors.pixelBorderBright,
            onClick = onAvatarClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 6.dp, top = 2.dp),
        )

        // 3. Top-Right Oversized Circular Live Mini-Map Medallion (real zoomed map centered on Hero!)
        LiveMiniMapMedallion(
            heroWorldX = heroWorldFrac.first,
            heroWorldY = heroWorldFrac.second,
            borderColor = colors.pixelBorderBright,
            heroDotColor = colors.accentPrimary,
            onClick = onMiniMapClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 6.dp, top = 2.dp),
        )
    }
}

@Composable
private fun HudMiniIconButton(
    label: String,
    highlighted: Boolean,
    highlightColor: Color,
    onClick: () -> Unit,
) {
    val colors = RpgTheme.colors
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (highlighted) highlightColor.copy(alpha = 0.20f) else colors.panelElevated,
        border = BorderStroke(1.dp, if (highlighted) highlightColor else colors.pixelBorder),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) highlightColor else colors.textPrimary,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun HeroAvatarMedallion(
    xpFraction: Float,
    accentColor: Color,
    bgColor: Color,
    trackColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val heroBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.pixel_hero_knight).asImageBitmap()
    }

    Box(
        modifier = modifier
            .size(60.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = heroBitmap,
            contentDescription = "Hero Avatar",
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(CircleShape),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 3.dp.toPx()
            val inset = strokePx / 2f + 1.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            // Outer dark metallic ring
            drawCircle(
                color = Color(0xFF0A0D12),
                radius = size.minDimension / 2f,
                style = Stroke(width = 2.dp.toPx()),
            )
            // Track ring
            drawArc(
                color = trackColor.copy(alpha = 0.65f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            // Live XP Progress Arc
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = (360f * xpFraction).coerceAtLeast(18f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun LiveMiniMapMedallion(
    heroWorldX: Float,
    heroWorldY: Float,
    borderColor: Color,
    heroDotColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val col = (heroWorldX * 3f).toInt().coerceIn(0, 2)
    val row = (heroWorldY * 3f).toInt().coerceIn(0, 2)

    val tileRes = when (row * 3 + col) {
        0 -> R.drawable.map_tile_0_0
        1 -> R.drawable.map_tile_0_1
        2 -> R.drawable.map_tile_0_2
        3 -> R.drawable.map_tile_1_0
        4 -> R.drawable.map_tile_1_1
        5 -> R.drawable.map_tile_1_2
        6 -> R.drawable.map_tile_2_0
        7 -> R.drawable.map_tile_2_1
        else -> R.drawable.map_tile_2_2
    }

    val tileBitmap = remember(tileRes) {
        BitmapFactory.decodeResource(context.resources, tileRes).asImageBitmap()
    }

    // Local coordinate inside the selected 1/3 x 1/3 tile
    val localX = (heroWorldX * 3f - col).coerceIn(0f, 1f)
    val localY = (heroWorldY * 3f - row).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .size(60.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFF21647D))
            .border(2.5.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bw = tileBitmap.width
            val bh = tileBitmap.height
            // Crop a crisp zoomed window (~55% of tile) centered around the Hero's exact tile position
            val cropSize = (minOf(bw, bh) * 0.56f).roundToInt().coerceAtLeast(64)
            val centerX = (localX * bw).roundToInt()
            val centerY = (localY * bh).roundToInt()
            val srcLeft = (centerX - cropSize / 2).coerceIn(0, (bw - cropSize).coerceAtLeast(0))
            val srcTop = (centerY - cropSize / 2).coerceIn(0, (bh - cropSize).coerceAtLeast(0))

            drawImage(
                image = tileBitmap,
                srcOffset = IntOffset(srcLeft, srcTop),
                srcSize = IntSize(cropSize, cropSize),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.None,
            )

            // Exact hero pin position within the cropped window
            val dotX = ((centerX - srcLeft).toFloat() / cropSize.toFloat()).coerceIn(0.15f, 0.85f) * size.width
            val dotY = ((centerY - srcTop).toFloat() / cropSize.toFloat()).coerceIn(0.15f, 0.85f) * size.height

            // Glowing radar ring + bright golden player dot
            drawCircle(
                color = heroDotColor.copy(alpha = 0.35f),
                radius = 7.dp.toPx(),
                center = Offset(dotX, dotY),
            )
            drawCircle(
                color = Color(0xFF0A0D12),
                radius = 4.2.dp.toPx(),
                center = Offset(dotX, dotY),
            )
            drawCircle(
                color = heroDotColor,
                radius = 3.dp.toPx(),
                center = Offset(dotX, dotY),
            )
        }
    }
}

private fun compactSteps(steps: Int): String = when {
    steps >= 1_000_000 -> "%.1fM".format(steps / 1_000_000.0)
    steps >= 10_000 -> "%.1fk".format(steps / 1_000.0)
    else -> "%,d".format(steps)
}
