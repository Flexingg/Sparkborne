package com.sparkborne.app.ui

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.R
import com.sparkborne.app.ui.components.PixelSprite
import com.sparkborne.app.ui.components.RpgActionButton
import com.sparkborne.app.ui.components.RpgPanel
import com.sparkborne.app.ui.components.RpgProgressBar
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.catalog.WorldCatalog
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.RegionElement
import com.sparkborne.game.model.WorldLocation
import kotlin.math.roundToInt

/**
 * Modular stitched tile map specification.
 * Adding new regions/continents simply appends tiles to this grid!
 */
private data class WorldMapTileSpec(
    val col: Int,
    val row: Int,
    @DrawableRes val drawableRes: Int,
)

private object WorldMapTileGrid {
    const val COLS = 2
    const val ROWS = 3
    val TILE_SIZE_DP = 360.dp

    val tiles: List<WorldMapTileSpec> = listOf(
        WorldMapTileSpec(col = 0, row = 0, drawableRes = R.drawable.map_tile_0_0),
        WorldMapTileSpec(col = 1, row = 0, drawableRes = R.drawable.map_tile_0_1),
        WorldMapTileSpec(col = 0, row = 1, drawableRes = R.drawable.map_tile_1_0),
        WorldMapTileSpec(col = 1, row = 1, drawableRes = R.drawable.map_tile_1_1),
        WorldMapTileSpec(col = 0, row = 2, drawableRes = R.drawable.map_tile_2_0),
        WorldMapTileSpec(col = 1, row = 2, drawableRes = R.drawable.map_tile_2_1),
    )
}

private enum class TownDrawerSection(val label: String) {
    TRAVEL("Roads"),
    ACTIVITIES("Activities"),
    SHOP("Shop"),
    QUESTS("Quests"),
}

private fun elementColor(element: RegionElement): Color = when (element) {
    RegionElement.FIRE -> Color(0xFFF5B041)
    RegionElement.PLANT -> Color(0xFF4ADE80)
    RegionElement.WATER -> Color(0xFF38BDF8)
    RegionElement.POISON -> Color(0xFFA3E635)
    RegionElement.LIGHTNING -> Color(0xFFFACC15)
    RegionElement.VOID -> Color(0xFFA78BFA)
}

/**
 * Full-screen, edge-to-edge WalkScape-style stitched pixel-art World Map with:
 * - Pinch-to-zoom (0.65x Realm Overview -> 2.4x Close-Up Town View) & smooth 2D panning
 * - Stitched 2x3 modular pixel-art tiles rendered with nearest-neighbor crispness (`FilterQuality.None`)
 * - Parchment scroll nameplates, radial town activity/shop/quest badges, and floating Hero avatar
 * - Slide-up RPG Town Drawer when tapping any location on the map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldTabScreen(
    state: GameState,
    errorMessage: String?,
    onStartTravel: (String) -> Unit,
    onCancelTravel: () -> Unit,
    onStartActivity: (String) -> Unit,
    onStopActivity: () -> Unit,
    onBuyShopItem: (String) -> Unit,
    onStartContractFromTown: (String) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = RpgTheme.colors

    val mapWidthDp = WorldMapTileGrid.TILE_SIZE_DP * WorldMapTileGrid.COLS
    val mapHeightDp = WorldMapTileGrid.TILE_SIZE_DP * WorldMapTileGrid.ROWS
    val mapWidthPx = with(density) { mapWidthDp.toPx() }
    val mapHeightPx = with(density) { mapHeightDp.toPx() }

    var viewportSize by remember { mutableStateOf(IntSize(1080, 1920)) }
    var scale by remember { mutableFloatStateOf(1.15f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var hasCenteredInitially by remember { mutableStateOf(false) }

    var selectedTownId by remember { mutableStateOf<String?>(null) }

    // Preload the 6 stitched pixel tiles once
    val tileBitmaps = remember {
        WorldMapTileGrid.tiles.associate { spec ->
            spec to BitmapFactory.decodeResource(context.resources, spec.drawableRes).asImageBitmap()
        }
    }

    // Hero world coordinate (interpolated along road during active travel!)
    val currentLoc = WorldCatalog.locationById(state.currentLocationId)
    val heroWorldFrac: Pair<Float, Float> = state.activeTravel?.let { travel ->
        val from = WorldCatalog.locationById(travel.fromLocationId)
        val to = WorldCatalog.locationById(travel.toLocationId)
        val t = travel.progressFraction
        (from.mapX + (to.mapX - from.mapX) * t) to (from.mapY + (to.mapY - from.mapY) * t)
    } ?: (currentLoc.mapX to currentLoc.mapY)

    fun clampOffset(candidate: Offset, currentScale: Float): Offset {
        val scaledW = mapWidthPx * currentScale
        val scaledH = mapHeightPx * currentScale
        val maxX = ((scaledW - viewportSize.width) / 2f).coerceAtLeast(160f)
        val maxY = ((scaledH - viewportSize.height) / 2f).coerceAtLeast(220f)
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    fun centerOnHero(targetScale: Float = scale) {
        val heroPxX = heroWorldFrac.first * mapWidthPx
        val heroPxY = heroWorldFrac.second * mapHeightPx
        val centered = Offset(
            x = (mapWidthPx / 2f - heroPxX) * targetScale,
            y = (mapHeightPx / 2f - heroPxY) * targetScale,
        )
        panOffset = clampOffset(centered, targetScale)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF36687E)) // Deep WalkScape sea color behind map edges
            .onSizeChanged { newSize ->
                viewportSize = newSize
                if (!hasCenteredInitially && newSize.width > 0 && newSize.height > 0) {
                    hasCenteredInitially = true
                    centerOnHero(1.15f)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(0.65f, 2.4f)
                    val nextPan = clampOffset(panOffset + pan, nextScale)
                    scale = nextScale
                    panOffset = nextPan
                }
            },
    ) {
        // Transformed Stitched World Layer
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = mapWidthDp, height = mapHeightDp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = panOffset.x
                    translationY = panOffset.y
                },
        ) {
            // 1. Stitched 2x3 Pixel Art Map Tiles (FilterQuality.None = zero blur!)
            WorldMapTileGrid.tiles.forEach { spec ->
                val bmp = tileBitmaps[spec] ?: return@forEach
                Image(
                    bitmap = bmp,
                    contentDescription = "Map tile ${spec.row}_${spec.col}",
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier
                        .offset(
                            x = WorldMapTileGrid.TILE_SIZE_DP * spec.col,
                            y = WorldMapTileGrid.TILE_SIZE_DP * spec.row,
                        )
                        .size(WorldMapTileGrid.TILE_SIZE_DP),
                )
            }

            // 2. Active Travel Animated Path Line
            state.activeTravel?.let { travel ->
                val from = WorldCatalog.locationById(travel.fromLocationId)
                val to = WorldCatalog.locationById(travel.toLocationId)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val start = Offset(from.mapX * size.width, from.mapY * size.height)
                    val end = Offset(to.mapX * size.width, to.mapY * size.height)
                    val current = Offset(heroWorldFrac.first * size.width, heroWorldFrac.second * size.height)

                    drawLine(
                        color = Color(0xFFFFD166).copy(alpha = 0.55f),
                        start = start,
                        end = end,
                        strokeWidth = 6f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    )
                    drawLine(
                        color = Color(0xFFFFB703),
                        start = start,
                        end = current,
                        strokeWidth = 8f,
                    )
                }
            }

            // 3. Interactive Town Markers, Radial Activity Icons & Parchment Scroll Nameplates
            val isRealmOverview = scale < 0.88f
            WorldCatalog.locations.forEach { loc ->
                val isDiscovered = loc.id in state.discoveredLocationIds
                val isHere = loc.id == state.currentLocationId && state.activeTravel == null
                val elemColor = elementColor(loc.element)

                val centerX = (loc.mapX * mapWidthPx).roundToInt()
                val centerY = (loc.mapY * mapHeightPx).roundToInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(centerX - 68.dp.roundToPx(), centerY - 46.dp.roundToPx()) }
                        .width(136.dp)
                        .clickable { selectedTownId = loc.id },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (!isRealmOverview) {
                            // Mini radial icon row above town beacon (Activities / Shop / Quests)
                            if (isDiscovered) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    MiniMapBadge("⛏", Color(0xFF4FD1C5))
                                    MiniMapBadge("🛒", Color(0xFFF5B041))
                                    if (loc.contractIds.isNotEmpty()) {
                                        MiniMapBadge("⚔", Color(0xFFEF5350))
                                    }
                                }
                            } else {
                                MiniMapBadge("?", Color(0xFFF5B041))
                            }
                        }

                        // Town Crest Node
                        Box(
                            modifier = Modifier
                                .size(if (isHere) 36.dp else 30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDiscovered) Color(0xFF1A222E) else Color(0xFF283445))
                                .border(
                                    width = if (isHere) 2.5.dp else 2.dp,
                                    color = if (isDiscovered) elemColor else Color(0xFF7D8FA6),
                                    shape = RoundedCornerShape(8.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = when (loc.element) {
                                    RegionElement.FIRE -> "🔥"
                                    RegionElement.PLANT -> "🌿"
                                    RegionElement.WATER -> "⚓"
                                    RegionElement.POISON -> "🧪"
                                    RegionElement.LIGHTNING -> "⚡"
                                    RegionElement.VOID -> "🏰"
                                },
                                fontSize = 15.sp,
                            )
                        }

                        // WalkScape-style Parchment Scroll Nameplate (or "◆ Unknown ◆" for fogged towns!)
                        ParchmentScrollLabel(
                            text = if (isDiscovered) loc.name else "◆ Unknown ◆",
                            isUnknown = !isDiscovered,
                            highlightBorder = if (isHere) elemColor else null,
                        )
                    }
                }
            }

            // 4. Floating Hero Avatar Standing on Current Town / Walking Along Road!
            val heroPxX = (heroWorldFrac.first * mapWidthPx).roundToInt()
            val heroPxY = (heroWorldFrac.second * mapHeightPx).roundToInt()
            Box(
                modifier = Modifier
                    .offset { IntOffset(heroPxX - 22.dp.roundToPx(), heroPxY - 76.dp.roundToPx()) }
                    .clickable { selectedTownId = state.currentLocationId },
            ) {
                PixelSprite(
                    drawableRes = R.drawable.pixel_hero_knight,
                    contentDescription = "Hero Location",
                    size = 44.dp,
                    borderColor = Color(0xFFFFD166),
                )
            }
        }

        // Floating Map Utility Controls (Zoom +/- and Center on Hero — WalkScape bottom-left style!)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapControlButton(label = "+", onClick = {
                val next = (scale + 0.35f).coerceAtMost(2.4f)
                scale = next
                panOffset = clampOffset(panOffset, next)
            })
            MapControlButton(label = "−", onClick = {
                val next = (scale - 0.35f).coerceAtLeast(0.65f)
                scale = next
                panOffset = clampOffset(panOffset, next)
            })
            MapControlButton(label = "📍", onClick = { centerOnHero(1.25f); scale = 1.25f })
        }

        // Floating Error Toast if any
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 112.dp, start = 16.dp, end = 16.dp),
        ) {
            errorMessage?.let { err ->
                RpgPanel(borderColor = colors.crimsonHp, containerColor = Color(0xE62B1417)) {
                    Text(err, color = colors.crimsonHp, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Slide-Up RPG Town Drawer when a city/town is tapped on the map
        selectedTownId?.let { townId ->
            val town = WorldCatalog.locationById(townId)
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { selectedTownId = null },
                sheetState = sheetState,
                containerColor = colors.panelSurface,
                contentColor = colors.textPrimary,
            ) {
                TownSheetContent(
                    state = state,
                    town = town,
                    onStartTravel = { dest ->
                        onStartTravel(dest)
                        selectedTownId = null
                    },
                    onCancelTravel = onCancelTravel,
                    onStartActivity = { act ->
                        onStartActivity(act)
                        selectedTownId = null
                    },
                    onStopActivity = onStopActivity,
                    onBuyShopItem = onBuyShopItem,
                    onStartContractFromTown = { contractId ->
                        onStartContractFromTown(contractId)
                        selectedTownId = null
                    },
                )
            }
        }
    }
}

@Composable
private fun MiniMapBadge(icon: String, tint: Color) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color(0xFF161C26))
            .border(1.5.dp, tint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, fontSize = 10.sp)
    }
}

@Composable
private fun ParchmentScrollLabel(
    text: String,
    isUnknown: Boolean,
    highlightBorder: Color? = null,
) {
    val bg = if (isUnknown) Color(0xFFDCE6F4) else Color(0xFFF0D4A2)
    val border = highlightBorder ?: if (isUnknown) Color(0xFF4A607A) else Color(0xFF6B4423)
    val textColor = if (isUnknown) Color(0xFF2B3A4E) else Color(0xFF3A2210)

    Surface(
        shape = RoundedCornerShape(5.dp),
        color = bg,
        border = BorderStroke(1.5.dp, border),
        shadowElevation = 4.dp,
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Serif,
            color = textColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MapControlButton(label: String, onClick: () -> Unit) {
    val colors = RpgTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.panelSurface.copy(alpha = 0.92f),
        border = BorderStroke(1.5.dp, colors.pixelBorderBright),
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun TownSheetContent(
    state: GameState,
    town: WorldLocation,
    onStartTravel: (String) -> Unit,
    onCancelTravel: () -> Unit,
    onStartActivity: (String) -> Unit,
    onStopActivity: () -> Unit,
    onBuyShopItem: (String) -> Unit,
    onStartContractFromTown: (String) -> Unit,
) {
    val colors = RpgTheme.colors
    val elemColor = elementColor(town.element)
    val currentLocation = WorldCatalog.locationById(state.currentLocationId)
    val isHere = town.id == currentLocation.id && state.activeTravel == null
    val directRoute = currentLocation.routes.firstOrNull { it.destinationId == town.id }
    var section by remember(town.id) { mutableStateOf(if (isHere) TownDrawerSection.ACTIVITIES else TownDrawerSection.TRAVEL) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Town Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = town.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = elemColor,
                )
                Text(
                    text = town.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = colors.obsidianBg,
                border = BorderStroke(1.dp, elemColor),
            ) {
                Text(
                    text = if (isHere) "YOU ARE HERE" else town.element.displayName.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = elemColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        // Quick Travel Action right at top if viewing another town
        if (!isHere) {
            if (directRoute != null) {
                RpgActionButton(
                    text = "WALK HERE (${directRoute.stepsRequired} STEPS)",
                    onClick = { onStartTravel(town.id) },
                    enabled = state.activeTravel == null,
                    containerColor = elemColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    "No direct road from ${currentLocation.name}. Travel to a connected town first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }

        // Section Pills
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            TownDrawerSection.entries.forEach { tab ->
                val active = tab == section
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (active) colors.panelElevated else colors.obsidianBg,
                    border = BorderStroke(1.5.dp, if (active) elemColor else colors.pixelBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { section = tab },
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) elemColor else colors.textMuted,
                        )
                    }
                }
            }
        }

        // Section Body
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (section) {
                TownDrawerSection.TRAVEL -> {
                    state.activeTravel?.let { travel ->
                        item {
                            RpgPanel(borderColor = colors.accentPrimary) {
                                Text("Currently walking to ${WorldCatalog.locationById(travel.toLocationId).name}")
                                RpgProgressBar(
                                    label = "Progress",
                                    valueText = "${travel.stepsWalked}/${travel.stepsRequired}",
                                    progress = travel.progressFraction,
                                    fillColor = colors.accentPrimary,
                                )
                                OutlinedButton(onClick = onCancelTravel, modifier = Modifier.fillMaxWidth()) {
                                    Text("Cancel Travel")
                                }
                            }
                        }
                    }
                    items(town.routes, key = { it.destinationId }) { route ->
                        val dest = WorldCatalog.locationById(route.destinationId)
                        RpgPanel {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dest.name, fontWeight = FontWeight.Bold)
                                    Text("${route.stepsRequired} steps · ${dest.element.displayName}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                }
                                RpgActionButton(
                                    text = "WALK",
                                    onClick = { onStartTravel(dest.id) },
                                    enabled = isHere && state.activeTravel == null,
                                )
                            }
                        }
                    }
                }

                TownDrawerSection.ACTIVITIES -> {
                    items(town.activities, key = { it.id }) { act ->
                        val isRunning = state.activeActivity?.activityId == act.id
                        RpgPanel(borderColor = if (isRunning) colors.accentSecondary else colors.pixelBorder) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${act.name} · ${act.skillTag}", fontWeight = FontWeight.Bold)
                                    Text(act.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                    Text(
                                        "${act.stepsPerAction} steps → +${act.rewardGold}g +${act.rewardIron}fe +${act.rewardShards}sh",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = colors.vitalityEmerald,
                                    )
                                }
                                if (isRunning) {
                                    OutlinedButton(onClick = onStopActivity) { Text("Stop") }
                                } else {
                                    RpgActionButton(
                                        text = "START",
                                        onClick = { onStartActivity(act.id) },
                                        enabled = isHere && state.activeTravel == null,
                                        containerColor = colors.accentSecondary,
                                    )
                                }
                            }
                        }
                    }
                }

                TownDrawerSection.SHOP -> {
                    items(town.shopListings, key = { it.id }) { listing ->
                        val costParts = buildList {
                            if (listing.goldCost > 0) add("${listing.goldCost}g")
                            if (listing.ironCost > 0) add("${listing.ironCost}fe")
                            if (listing.shardsCost > 0) add("${listing.shardsCost}sh")
                        }.joinToString(" · ")
                        RpgPanel {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(listing.title, fontWeight = FontWeight.Bold)
                                    Text(listing.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                    Text(costParts, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = colors.accentPrimary)
                                }
                                RpgActionButton(
                                    text = if (isHere) "BUY" else "VISIT",
                                    onClick = { onBuyShopItem(listing.id) },
                                    enabled = isHere,
                                )
                            }
                        }
                    }
                }

                TownDrawerSection.QUESTS -> {
                    val contracts = town.contractIds.mapNotNull(GameCatalog::contractById)
                    items(contracts, key = { it.id }) { contract ->
                        RpgPanel(borderColor = colors.crimsonHp.copy(alpha = 0.65f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contract.title, fontWeight = FontWeight.Bold)
                                    Text("Boss: ${contract.bossName} · ${contract.durationDays}d", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                }
                                RpgActionButton(
                                    text = "DELVE",
                                    onClick = { onStartContractFromTown(contract.id) },
                                    enabled = isHere && state.activeExpedition == null,
                                    containerColor = colors.crimsonHp,
                                    contentColor = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
