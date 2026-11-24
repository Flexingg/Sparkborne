package com.sparkborne.app.ui.world

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.ui.hero.FrameStyle
import com.sparkborne.app.ui.hero.GoldTrim
import com.sparkborne.app.ui.hero.HeroSprite
import com.sparkborne.app.ui.hero.InkDark
import com.sparkborne.app.ui.hero.Text8
import com.sparkborne.app.ui.hero.heroLookOf
import com.sparkborne.app.ui.hero.mixWith
import com.sparkborne.app.ui.hero.pixelFrame
import com.sparkborne.app.ui.quest.ErrorScroll
import com.sparkborne.app.ui.quest.elementColor
import com.sparkborne.game.catalog.WorldCatalog
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.RegionElement
import com.sparkborne.game.model.WorldLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Everything the map can ask the game to do. */
class WorldActions(
    val startTravel: (String) -> Unit = {},
    val cancelTravel: () -> Unit = {},
    val startActivity: (String) -> Unit = {},
    val stopActivity: () -> Unit = {},
    val buyShopItem: (String) -> Unit = {},
    val startContract: (String) -> Unit = {},
    val visitedShop: () -> Unit = {},
    val spendOnTravel: () -> Unit = {},
    val spendOnActivity: (Int?) -> Unit = {},
    val resetTalents: () -> Unit = {},
)

private val TILE = 360.dp
private const val GRID = 3

fun elementGlyph(e: RegionElement): String = when (e) {
    RegionElement.FIRE -> "🔥"
    RegionElement.PLANT -> "🌿"
    RegionElement.WATER -> "⚓"
    RegionElement.POISON -> "🧪"
    RegionElement.LIGHTNING -> "⚡"
    RegionElement.VOID -> "🌀"
}

private fun currentHour(): Float {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f
}

/**
 * The world map: the painted 3x3 map brought to life ([drawSea], [drawWeather], [drawFog],
 * [drawClouds], [drawDaylight]) with pixel banners for towns, the hero on the road, stone
 * controls, and a pixel town window.
 *
 * @param tiles row-major 3x3 map tiles. @param shopkeeper bitmap portraits from the platform.
 */
@Composable
fun WorldMapView(
    state: GameState,
    errorMessage: String?,
    tiles: List<ImageBitmap>,
    actions: WorldActions,
    shopkeeper: @Composable (keeperId: String, size: Dp) -> Unit = { _, s -> Box(Modifier.size(s)) },
    hourOverride: Float? = null,
    initialTownId: String? = null,
    initialScale: Float = 1.15f,
) {
    val density = LocalDensity.current
    val mapW = TILE * GRID
    val mapPx = with(density) { mapW.toPx() }

    var analysis by remember { mutableStateOf<MapAnalysis?>(null) }
    LaunchedEffect(tiles) { analysis = withContext(Dispatchers.Default) { analyzeMap(tiles) } }

    var hour by remember { mutableFloatStateOf(hourOverride ?: currentHour()) }
    LaunchedEffect(hourOverride) {
        if (hourOverride == null) {
            while (true) {
                hour = currentHour()
                kotlinx.coroutines.delay(60_000)
            }
        }
    }
    val light = daylightFor(hour)

    val tr = rememberInfiniteTransition(label = "map")
    val t by tr.animateFloat(0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing)), label = "map_t")

    var viewport by remember { mutableStateOf(IntSize(1080, 1920)) }
    var scale by remember { mutableFloatStateOf(initialScale) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var centered by remember { mutableStateOf(false) }
    var selectedTown by remember { mutableStateOf(initialTownId) }

    val here = WorldCatalog.locationById(state.currentLocationId)
    val heroFrac: Pair<Float, Float> = state.activeTravel?.let { tv ->
        val a = WorldCatalog.locationById(tv.fromLocationId)
        val b = WorldCatalog.locationById(tv.toLocationId)
        val f = tv.progressFraction
        (a.mapX + (b.mapX - a.mapX) * f) to (a.mapY + (b.mapY - a.mapY) * f)
    } ?: (here.mapX to here.mapY)

    fun clamp(c: Offset, s: Float): Offset {
        val maxX = ((mapPx * s - viewport.width) / 2f + 120f).coerceAtLeast(180f)
        val maxY = ((mapPx * s - viewport.height) / 2f + 160f).coerceAtLeast(240f)
        return Offset(c.x.coerceIn(-maxX, maxX), c.y.coerceIn(-maxY, maxY))
    }
    fun centerOnHero(s: Float) {
        scale = s
        pan = clamp(Offset((mapPx / 2f - heroFrac.first * mapPx) * s, (mapPx / 2f - heroFrac.second * mapPx) * s), s)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF21647D))
            .onSizeChanged {
                viewport = it
                if (!centered && it.width > 0) {
                    centered = true
                    centerOnHero(initialScale)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, p, zoom, _ ->
                    val ns = (scale * zoom).coerceIn(0.55f, 2.6f)
                    scale = ns
                    pan = clamp(pan + p, ns)
                }
            },
    ) {
        val mapLayer = Modifier
            .requiredSize(mapW, mapW)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = pan.x
                translationY = pan.y
            }
        Box(Modifier.align(Alignment.Center).then(mapLayer)) {
            // 1. the painting
            tiles.forEachIndexed { i, bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.offset(x = TILE * (i % GRID), y = TILE * (i / GRID)).size(TILE),
                )
            }
            // 2. the living layer
            Canvas(Modifier.matchParentSize()) {
                analysis?.let { drawSea(it, t, light) }
                drawWeather(t)
                drawRoutes(state, heroFrac, t)
                drawFog(state, t, light)
                drawClouds(t, light)
            }
        }
        // 3. dusk / night grading over the whole view, so the sea beyond the painting matches
        Canvas(Modifier.fillMaxSize()) { drawDaylightTint(light) }
        // 4. lights, markers and the hero sit above the grading
        Box(Modifier.align(Alignment.Center).then(mapLayer)) {
            Canvas(Modifier.matchParentSize()) {
                drawNightLights(state, analysis, t, light)
                drawHereRing(here, state.activeTravel == null, t)
            }
            val compact = scale < 0.9f
            WorldCatalog.locations.forEach { loc ->
                val known = loc.id in state.discoveredLocationIds
                val isHere = loc.id == state.currentLocationId && state.activeTravel == null
                val cx = (loc.mapX * mapPx).roundToInt()
                val cy = (loc.mapY * mapPx).roundToInt()
                Box(
                    Modifier
                        .offset { IntOffset(cx - 80.dp.roundToPx(), cy - 70.dp.roundToPx()) }
                        .width(160.dp)
                        .clickable { selectedTown = loc.id },
                    contentAlignment = Alignment.TopCenter,
                ) { TownBanner(loc, known, isHere, compact, t) }
            }
            // the hero: beside the town banner when resting, on the trail when travelling
            val hx = (heroFrac.first * mapPx).roundToInt()
            val hy = (heroFrac.second * mapPx).roundToInt()
            val inTown = state.activeTravel == null
            Box(
                Modifier
                    .offset { IntOffset(hx + (if (inTown) 52.dp else (-24).dp).roundToPx(), hy - (if (inTown) 50.dp else 58.dp).roundToPx()) }
                    .clickable { selectedTown = state.currentLocationId },
            ) { HeroSprite(heroLookOf(state.player), 60.dp) }
        }

        // stone controls
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MapStone("+") { scale = (scale + 0.35f).coerceAtMost(2.6f); pan = clamp(pan, scale) }
            MapStone("−") { scale = (scale - 0.35f).coerceAtLeast(0.6f); pan = clamp(pan, scale) }
            MapStone("⌖") { centerOnHero(1.3f) }
        }
        // time-of-day sigil
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 90.dp)
                .pixelFrame(fill = InkDark.copy(alpha = 0.85f), edge = GoldTrim.copy(alpha = 0.6f), style = FrameStyle.PLATE, unit = 2.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text8(
                when {
                    light.night > 0.6f -> "🌙 NIGHT"
                    light.dusk > 0.3f && hour > 12f -> "🌇 DUSK"
                    light.dusk > 0.3f -> "🌅 DAWN"
                    else -> "☀ DAY"
                },
                size = 9.sp,
                color = GoldTrim,
                letterSpacing = 1.5.sp,
            )
        }
        errorMessage?.let {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 112.dp, start = 16.dp, end = 16.dp)) { ErrorScroll(it) }
        }
        selectedTown?.let { id ->
            TownWindow(
                state = state,
                town = WorldCatalog.locationById(id),
                actions = actions,
                shopkeeper = shopkeeper,
                onClose = { selectedTown = null },
            )
        }
    }
}

@Composable
private fun MapStone(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .pixelFrame(fill = Color(0xFF4A4F5C), edge = Color(0xFF7A8294), rivet = Color(0xFF2A2E38))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text8(label, size = 18.sp, color = Color(0xFFE6E9F0), weight = FontWeight.ExtraBold) }
}

/** A swallow-tail banner on a pole with a pixel nameplate (fogged towns show a tattered grey flag). */
@Composable
private fun TownBanner(loc: WorldLocation, known: Boolean, isHere: Boolean, compact: Boolean, t: Float) {
    val ec = if (known) elementColor(loc.element) else Color(0xFF8A93A3)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 40.dp, height = 48.dp), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.matchParentSize()) {
                val u = floor(2.dp.toPx())
                val w = size.width
                val px = w * 0.28f
                drawRect(InkDark, Offset(px - u, 0f), Size(3 * u, size.height))
                drawRect(Color(0xFF8A6238), Offset(px, u), Size(u, size.height - u))
                drawRect(GoldTrim, Offset(px - u, 0f), Size(3 * u, 2 * u))
                val fx0 = px + 2 * u
                val fw = w - fx0 - u
                val fh = size.height * 0.62f
                val cols = (fw / u).toInt()
                for (c in 0 until cols) {
                    val wave = (sin(t * 5f - c * 0.6f) * u * 0.8f * (c / cols.toFloat())).roundToInt().toFloat()
                    val notch = if (c > cols * 0.65f) ((c - cols * 0.65f) / (cols * 0.35f)) * fh * 0.35f else 0f
                    val top = 2 * u + wave
                    val bottomEdge = top + fh - if (known) notch else (c % 3) * u
                    drawRect(InkDark, Offset(fx0 + c * u, top - u), Size(u, bottomEdge - top + 2 * u))
                    val col = if (c % 6 == 0) ec.mixWith(Color.Black, 0.2f) else ec
                    drawRect(col, Offset(fx0 + c * u, top), Size(u, bottomEdge - top))
                    if (c < cols - 1) drawRect(col.mixWith(Color.White, 0.35f), Offset(fx0 + c * u, top), Size(u, u))
                }
                if (isHere) {
                    val p = Path().apply { moveTo(px + u / 2, -2 * u); lineTo(px + 3 * u, -6 * u); lineTo(px - 2 * u, -6 * u); close() }
                    drawPath(p, GoldTrim)
                }
            }
            Text8(if (known) elementGlyph(loc.element) else "?", size = 12.sp, color = Color.White, weight = FontWeight.ExtraBold, modifier = Modifier.offset(x = 5.dp, y = 7.dp))
        }
        if (!compact || isHere) {
            Box(
                Modifier
                    .pixelFrame(
                        fill = if (known) Color(0xFF2B2620) else Color(0xFF232838),
                        edge = if (isHere) GoldTrim else if (known) Color(0xFF8A6238) else Color(0xFF4A5566),
                        style = FrameStyle.PLATE,
                        unit = 2.dp,
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text8(
                    if (known) loc.name.uppercase() else "UNCHARTED",
                    size = 8.sp,
                    color = if (isHere) GoldTrim else if (known) Color(0xFFF0DDB8) else Color(0xFF9AA4B8),
                    weight = FontWeight.ExtraBold,
                    maxLines = 1,
                    align = TextAlign.Center,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

/** Sea-lanes to frontier islands plus the active journey as an animated pixel trail. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoutes(state: GameState, heroFrac: Pair<Float, Float>, t: Float) {
    val cell = size.width / MAP_CELLS
    val frontier = setOf("loc_frostpeak_bastion", "loc_sunspire_oasis", "loc_ashen_sanctum")
    val drawn = mutableSetOf<String>()
    fun dotted(a: Offset, b: Offset, color: Color, gap: Int, flow: Float) {
        val len = (b - a).getDistance()
        val n = (len / (cell * gap)).toInt().coerceAtLeast(1)
        for (k in 0..n) {
            if (((k + flow.toInt()) % 3) == 0) continue
            val f = k / n.toFloat()
            val p = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
            drawRect(InkDark.copy(alpha = color.alpha), Offset(floor(p.x / cell) * cell - cell / 2, floor(p.y / cell) * cell - cell / 2), Size(cell * 2, cell * 2))
            drawRect(color, Offset(floor(p.x / cell) * cell, floor(p.y / cell) * cell), Size(cell, cell))
        }
    }
    WorldCatalog.locations.forEach { loc ->
        loc.routes.forEach { r ->
            val key = listOf(loc.id, r.destinationId).sorted().joinToString()
            if ((loc.id in frontier || r.destinationId in frontier) && drawn.add(key)) {
                val d = WorldCatalog.locationById(r.destinationId)
                dotted(Offset(loc.mapX * size.width, loc.mapY * size.height), Offset(d.mapX * size.width, d.mapY * size.height), Color(0xFFEBD9B4).copy(alpha = 0.7f), 3, t * 0.5f)
            }
        }
    }
    state.activeTravel?.let { tv ->
        val a = WorldCatalog.locationById(tv.fromLocationId)
        val b = WorldCatalog.locationById(tv.toLocationId)
        val start = Offset(a.mapX * size.width, a.mapY * size.height)
        val end = Offset(b.mapX * size.width, b.mapY * size.height)
        val cur = Offset(heroFrac.first * size.width, heroFrac.second * size.height)
        dotted(start, end, Color(0xFFFFD166).copy(alpha = 0.9f), 3, t * 3f)
        dotted(start, cur, GoldTrim, 2, 0f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHereRing(here: WorldLocation, show: Boolean, t: Float) {
    if (!show) return
    val c = Offset(here.mapX * size.width, here.mapY * size.height + 6.dp.toPx())
    val cell = size.width / MAP_CELLS
    val pulse = (t * 0.8f) % 1f
    val rx = cell * (6 + pulse * 10)
    val n = 28
    for (i in 0 until n) {
        val a = i / n.toFloat() * 6.283f
        drawRect(GoldTrim.copy(alpha = 1f - pulse), Offset(floor((c.x + kotlin.math.cos(a) * rx) / cell) * cell, floor((c.y + sin(a) * rx * 0.45f) / cell) * cell), Size(cell, cell))
    }
}
