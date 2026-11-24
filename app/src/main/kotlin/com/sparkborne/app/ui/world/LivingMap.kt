package com.sparkborne.app.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import com.sparkborne.app.ui.hero.mixWith
import com.sparkborne.game.catalog.WorldCatalog
import com.sparkborne.game.model.GameState
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The painted world map is brought to life by an animated layer:
 * sea glints, breathing coast foam, drifting clouds with shadows, regional weather,
 * fog of war over undiscovered towns, and a real-time day/night cycle with town lights.
 *
 * [MapAnalysis] is computed once from the map tiles: a coarse water mask plus the
 * coastline and open-sea cells used to place foam and glints exactly on the painting.
 */
class MapAnalysis(
    val cols: Int,
    val rows: Int,
    val water: BooleanArray,
    val coast: IntArray,
    val openSea: IntArray,
    /** Cells that glow at night (lava, lanterns, crystals, windows) and their colours. */
    val emissive: IntArray,
    val emissiveColor: IntArray,
)

const val MAP_CELLS = 288

private fun glowOf(c: Color): Boolean {
    val mx = maxOf(c.red, c.green, c.blue)
    val mn = minOf(c.red, c.green, c.blue)
    val sat = if (mx <= 0f) 0f else (mx - mn) / mx
    val warm = c.red > 0.85f && c.green in 0.35f..0.85f && c.blue < 0.35f // lava, fire, windows
    val lantern = c.green > 0.8f && c.red in 0.45f..0.85f && c.blue < 0.45f && sat > 0.5f // swamp lights
    val arcane = c.red > 0.6f && c.blue > 0.8f && c.green < 0.45f // void crystals
    return mx > 0.8f && sat > 0.55f && (warm || lantern || arcane)
}

private fun isWater(c: Color): Boolean {
    val r = c.red
    val g = c.green
    val b = c.blue
    return r < 0.36f && b > r + 0.12f && b >= g - 0.04f && b in 0.28f..0.8f && g > 0.2f
}

/** Builds the water/coast mask from a row-major 3x3 grid of square map tiles. */
fun analyzeMap(tiles: List<ImageBitmap>, gridCols: Int = 3, gridRows: Int = 3): MapAnalysis {
    val cols = MAP_CELLS
    val rows = MAP_CELLS
    val water = BooleanArray(cols * rows)
    val colors = arrayOfNulls<Color>(cols * rows)
    val lum = FloatArray(cols * rows)
    val emissive = ArrayList<Int>()
    val emissiveColor = ArrayList<Int>()
    val maps = tiles.map { it.toPixelMap() }
    val tw = tiles.first().width
    val th = tiles.first().height
    val totalW = tw * gridCols
    val totalH = th * gridRows
    for (cy in 0 until rows) {
        for (cx in 0 until cols) {
            val px = ((cx + 0.5f) / cols * totalW).toInt().coerceIn(0, totalW - 1)
            val py = ((cy + 0.5f) / rows * totalH).toInt().coerceIn(0, totalH - 1)
            val tile = maps[(py / th) * gridCols + (px / tw)]
            val c = tile[px % tw, py % th]
            water[cy * cols + cx] = isWater(c)
            colors[cy * cols + cx] = c
            lum[cy * cols + cx] = 0.3f * c.red + 0.59f * c.green + 0.11f * c.blue
        }
    }
    // Glowing pixels must stand out from their surroundings (lava on rock, lanterns in the swamp),
    // which rules out sunlit sand and autumn leaves that merely share the colour.
    for (cy in 3 until rows - 3) {
        for (cx in 3 until cols - 3) {
            val i = cy * cols + cx
            val c = colors[i] ?: continue
            if (!glowOf(c)) continue
            var sum = 0f
            for (dy in -3..3) for (dx in -3..3) sum += lum[(cy + dy) * cols + cx + dx]
            if (lum[i] - sum / 49f > 0.2f) {
                emissive += i
                emissiveColor += c.toArgbInt()
            }
        }
    }
    val coast = ArrayList<Int>()
    val open = ArrayList<Int>()
    for (cy in 1 until rows - 1) {
        for (cx in 1 until cols - 1) {
            val i = cy * cols + cx
            if (!water[i]) continue
            val nearLand = !water[i - 1] || !water[i + 1] || !water[i - cols] || !water[i + cols]
            if (nearLand) {
                coast += i
            } else {
                // open sea needs a little clearance so glints never touch islands
                var clear = true
                for (dy in -3..3) for (dx in -3..3) {
                    val x = cx + dx
                    val y = cy + dy
                    if (x in 0 until cols && y in 0 until rows && !water[y * cols + x]) clear = false
                }
                if (clear) open += i
            }
        }
    }
    // deterministic thinning so per-frame work stays bounded
    fun thin(src: List<Int>, max: Int): IntArray {
        if (src.size <= max) return src.toIntArray()
        val step = src.size.toFloat() / max
        return IntArray(max) { k -> src[(k * step).toInt()] }
    }
    val keep = thin(emissive.indices.toList(), 2600)
    return MapAnalysis(
        cols, rows, water, thin(coast, 2200), thin(open, 420),
        IntArray(keep.size) { emissive[keep[it]] },
        IntArray(keep.size) { emissiveColor[keep[it]] },
    )
}

private fun Color.toArgbInt(): Int =
    ((alpha * 255).toInt() shl 24) or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()

private fun hash01(n: Int): Float {
    var x = n * -0x61c88647 + 0x165667b1
    x = (x xor (x ushr 15)) * 0x2c1b3c6d
    x = (x xor (x ushr 12)) * 0x297a2d39
    x = x xor (x ushr 15)
    return (x ushr 8) / 16_777_216f
}

/** 0 = full day … 1 = deepest night; plus a warm dusk/dawn amount. */
data class Daylight(val night: Float, val dusk: Float)

fun daylightFor(hour: Float): Daylight {
    val h = ((hour % 24f) + 24f) % 24f
    val night = when {
        h < 5f -> 1f
        h < 7f -> 1f - (h - 5f) / 2f
        h < 18.5f -> 0f
        h < 20.5f -> (h - 18.5f) / 2f
        else -> 1f
    }
    val dusk = when {
        h in 5f..8f -> 1f - kotlin.math.abs(h - 6.3f) / 1.7f
        h in 17f..20.5f -> 1f - kotlin.math.abs(h - 19f) / 1.6f
        else -> 0f
    }.coerceIn(0f, 1f)
    return Daylight(night, dusk)
}

/** Sea glints and coast foam (drawn just above the painted map). */
fun DrawScope.drawSea(a: MapAnalysis, t: Float, light: Daylight) {
    val cw = size.width / a.cols
    val ch = size.height / a.rows
    val foam = Color.White.mixWith(Color(0xFFBFE6F5), 0.3f).mixWith(Color(0xFF8AA6D8), light.night * 0.6f)
    for (k in a.coast.indices step 1) {
        val i = a.coast[k]
        val cx = i % a.cols
        val cy = i / a.cols
        val wave = sin(t * 1.6f - cx * 0.22f - cy * 0.17f)
        if (wave < 0.15f) continue
        drawRect(foam.copy(alpha = (wave - 0.15f) * 0.55f), Offset(cx * cw, cy * ch), Size(cw, ch))
    }
    val glint = if (light.night > 0.5f) Color(0xFFDDE6FF) else Color.White
    a.openSea.forEachIndexed { k, i ->
        val cx = i % a.cols
        val cy = i / a.cols
        val ph = (t * (0.35f + hash01(k) * 0.35f) + hash01(k + 999)) % 1f
        if (ph > 0.18f) return@forEachIndexed
        val strength = 1f - kotlin.math.abs(ph - 0.09f) / 0.09f
        val p = Offset(cx * cw, cy * ch)
        drawRect(glint.copy(alpha = 0.85f * strength), p, Size(cw, ch))
        if (strength > 0.6f) {
            drawRect(glint.copy(alpha = 0.45f * strength), Offset(p.x - cw, p.y), Size(cw, ch))
            drawRect(glint.copy(alpha = 0.45f * strength), Offset(p.x + cw, p.y), Size(cw, ch))
            drawRect(glint.copy(alpha = 0.3f * strength), Offset(p.x, p.y - ch), Size(cw, ch))
        }
        // short wave crest drifting beside the glint
        drawRect(Color.White.copy(alpha = 0.18f * strength), Offset(p.x + 2 * cw, p.y + 2 * ch), Size(3 * cw, ch))
    }
}

private enum class Weather { EMBERS, LEAVES, GULLS, BUBBLES, SNOW, ASH, SAND, MOTES }

private fun weatherFor(locationId: String): Weather = when (locationId) {
    "loc_emberhearth" -> Weather.EMBERS
    "loc_verdant_crossing" -> Weather.LEAVES
    "loc_tidebreak_harbor" -> Weather.GULLS
    "loc_mirefen_village" -> Weather.BUBBLES
    "loc_stormspire_citadel", "loc_frostpeak_bastion" -> Weather.SNOW
    "loc_obsidian_crag" -> Weather.ASH
    "loc_sunspire_oasis" -> Weather.SAND
    else -> Weather.MOTES
}

/** Per-region weather particles clustered around each town. */
fun DrawScope.drawWeather(t: Float) {
    val cell = size.width / MAP_CELLS
    val radius = size.width * 0.11f
    WorldCatalog.locations.forEachIndexed { li, loc ->
        val center = Offset(loc.mapX * size.width, loc.mapY * size.height)
        val w = weatherFor(loc.id)
        val n = if (w == Weather.GULLS) 5 else 26
        for (k in 0 until n) {
            val seed = li * 131 + k * 17
            val speed = 0.06f + hash01(seed) * 0.08f
            val ph = (t * speed + hash01(seed + 5)) % 1f
            val ox = (hash01(seed + 1) - 0.5f) * 2f * radius
            val oy = (hash01(seed + 2) - 0.5f) * 2f * radius
            var p: Offset
            var c: Color
            var sz = Size(cell, cell)
            when (w) {
                Weather.EMBERS -> {
                    p = Offset(center.x + ox + sin(ph * 9f + k) * cell * 3, center.y + oy * 0.6f - ph * radius)
                    c = Color(0xFFFFB04A).mixWith(Color(0xFFD6452A), ph).copy(alpha = 1f - ph)
                }
                Weather.ASH -> {
                    p = Offset(center.x + ox + ph * radius * 0.6f, center.y + oy * 0.5f - ph * radius * 0.8f)
                    c = (if (k % 4 == 0) Color(0xFFFF7A3D) else Color(0xFF3A3440)).copy(alpha = 0.9f * (1f - ph))
                }
                Weather.SNOW -> {
                    p = Offset(center.x + ox + sin(ph * 7f + k) * cell * 4, center.y - radius + ph * radius * 2f)
                    c = Color.White.copy(alpha = 0.9f * sin(ph * 3.14f))
                }
                Weather.LEAVES -> {
                    p = Offset(center.x + ox + sin(ph * 10f + k) * cell * 6, center.y - radius * 0.6f + ph * radius * 1.4f)
                    c = (if (k % 3 == 0) Color(0xFFE0A030) else Color(0xFF6FBF4A)).copy(alpha = sin(ph * 3.14f))
                    if (k % 5 == 0) { // firefly
                        p = Offset(center.x + ox + sin(t * 0.7f + k) * cell * 5, center.y + oy + cos(t * 0.9f + k) * cell * 4)
                        c = Color(0xFFE8FF8A).copy(alpha = (0.5f + 0.5f * sin(t * 3f + k)).coerceIn(0f, 1f))
                    }
                }
                Weather.BUBBLES -> {
                    p = Offset(center.x + ox, center.y + oy * 0.6f - ph * cell * 10)
                    c = Color(0xFFA5E04A).copy(alpha = 0.8f * (1f - ph))
                    if (ph > 0.85f) sz = Size(cell * 2, cell * 2)
                }
                Weather.SAND -> {
                    p = Offset(center.x - radius + ph * radius * 2f, center.y + oy * 0.7f + sin(ph * 6f + k) * cell * 2)
                    c = Color(0xFFF2D49A).copy(alpha = 0.7f * sin(ph * 3.14f))
                    sz = Size(cell * 3, cell)
                }
                Weather.MOTES -> {
                    val a = ph * 6.283f + k
                    p = Offset(center.x + cos(a) * radius * (0.3f + hash01(seed + 3) * 0.6f), center.y + sin(a) * radius * 0.5f * (0.3f + hash01(seed + 4) * 0.6f))
                    c = Color(0xFFB57CFF).copy(alpha = 0.5f + 0.5f * sin(t * 2f + k))
                }
                Weather.GULLS -> {
                    val gx = center.x - radius * 1.2f + ((t * 0.04f * (1 + k % 2) + hash01(seed)) % 1f) * radius * 2.4f
                    val gy = center.y - radius * 0.4f + oy * 0.4f + sin(t * 1.5f + k) * cell * 2
                    val flap = if (sin(t * 8f + k) > 0f) 1 else 0
                    val gc = Color(0xFFF4F7FB)
                    drawRect(gc, Offset(gx - 2 * cell, gy - flap * cell), Size(cell * 2, cell))
                    drawRect(gc, Offset(gx, gy), Size(cell, cell))
                    drawRect(gc, Offset(gx + cell, gy - flap * cell), Size(cell * 2, cell))
                    continue
                }
            }
            drawRect(c, Offset(floor(p.x / cell) * cell, floor(p.y / cell) * cell), sz)
        }
    }
}

/** Drifting pixel clouds with soft shadows on the land. */
fun DrawScope.drawClouds(t: Float, light: Daylight) {
    val cell = size.width / MAP_CELLS * 1.5f
    val tint = Color.White.mixWith(Color(0xFFFFC09A), light.dusk * 0.5f).mixWith(Color(0xFF6E7AA8), light.night * 0.75f)
    for (k in 0 until 5) {
        val span = size.width + 120 * cell
        val x = ((hash01(k * 7) * span + t * cell * (0.25f + hash01(k * 3) * 0.3f)) % span) - 60 * cell
        val y = size.height * (0.05f + hash01(k * 11) * 0.9f)
        val puffs = 3 + (k % 3)
        for (pass in 0..1) {
            val shadow = pass == 0
            val off = if (shadow) Offset(10 * cell, 14 * cell) else Offset.Zero
            for (p in 0 until puffs) {
                val pr = (3 + (hash01(k * 31 + p) * 4)).toInt()
                val pcx = x + p * 5 * cell + off.x
                val pcy = y + (hash01(k * 17 + p) - 0.5f) * 6 * cell + off.y
                for (dy in -pr..pr) {
                    val half = sqrt((pr * pr - dy * dy).toFloat()).toInt()
                    val c = when {
                        shadow -> Color.Black.copy(alpha = 0.09f)
                        dy > pr / 3 -> tint.mixWith(Color(0xFF9AA6C0), 0.35f).copy(alpha = 0.62f)
                        dy < -pr / 2 -> tint.copy(alpha = 0.78f)
                        else -> tint.copy(alpha = 0.7f)
                    }
                    drawRect(c, Offset(floor((pcx - half * cell) / cell) * cell, floor((pcy + dy * cell) / cell) * cell), Size((2 * half + 1) * cell, cell))
                }
            }
        }
    }
}

/** Swirling clusters of pixel fog over every town you have not discovered. */
fun DrawScope.drawFog(state: GameState, t: Float, light: Daylight) {
    val cell = size.width / MAP_CELLS * 1.5f
    val top = Color(0xFFF2F4FA).mixWith(Color(0xFF8C96B8), light.night * 0.7f)
    val mid = Color(0xFFCDD4E4).mixWith(Color(0xFF5C6688), light.night * 0.7f)
    val low = Color(0xFF9AA3BC).mixWith(Color(0xFF3A4260), light.night * 0.7f)
    WorldCatalog.locations.filter { it.id !in state.discoveredLocationIds }.forEachIndexed { li, loc ->
        val center = Offset(loc.mapX * size.width, loc.mapY * size.height)
        val r = size.width * 0.075f / cell
        val puffs = 9
        for (pass in 0..1) {
            for (p in 0 until puffs) {
                val a = p / puffs.toFloat() * 6.283f + li
                val dist = if (p == 0) 0f else r * (0.35f + 0.2f * hash01(li * 31 + p))
                val drift = sin(t * 0.35f + p * 1.7f + li) * 2f
                val pcx = center.x / cell + cos(a) * dist + drift
                val pcy = center.y / cell + sin(a) * dist * 0.6f + cos(t * 0.3f + p) * 1.2f
                val pr = (r * (0.38f + 0.18f * hash01(li * 17 + p))).toInt().coerceAtLeast(3)
                for (dy in -pr..pr) {
                    val half = sqrt((pr * pr - dy * dy).toFloat()).toInt()
                    val y = floor(pcy + dy) * cell
                    val x0 = floor(pcx - half) * cell
                    if (pass == 0) {
                        drawRect(Color.Black.copy(alpha = 0.10f), Offset(x0 + 4 * cell, y + 6 * cell), Size((2 * half + 1) * cell, cell))
                    } else {
                        val c = when {
                            dy < -pr / 2 -> top
                            dy > pr / 3 -> low
                            else -> mid
                        }
                        drawRect(c.copy(alpha = 0.92f), Offset(x0, y), Size((2 * half + 1) * cell, cell))
                    }
                }
            }
        }
        // a slowly turning "?" rune at the heart of the fog
        val qa = 0.5f + 0.5f * sin(t * 1.3f + li)
        drawCircle(Color(0xFFF5B041).copy(alpha = 0.25f * qa), cell * 5, center)
    }
}

/** Day/night colour grading for the whole viewport (map and surrounding sea alike). */
fun DrawScope.drawDaylightTint(light: Daylight) {
    if (light.dusk > 0f) drawRect(Color(0xFFFF8A4A).copy(alpha = 0.16f * light.dusk))
    if (light.night > 0f) drawRect(Color(0xFF0A1030).copy(alpha = 0.5f * light.night))
}

/** Things that glow through the dark: lava, lanterns, crystals (from [MapAnalysis]) and lit towns. */
fun DrawScope.drawNightLights(state: GameState, a: MapAnalysis?, t: Float, light: Daylight) {
    if (light.night <= 0f) return
    if (a != null) {
        val cw = size.width / a.cols
        for (k in a.emissive.indices) {
            val i = a.emissive[k]
            val c = Color(a.emissiveColor[k])
            val flick = 0.75f + 0.25f * sin(t * 3f + k * 0.7f)
            val p = Offset((i % a.cols) * cw, (i / a.cols) * cw)
            if (k % 9 == 0) drawCircle(c.copy(alpha = 0.16f * light.night * flick), cw * 3.5f, Offset(p.x + cw / 2, p.y + cw / 2))
            drawRect(c.copy(alpha = light.night * flick), p, Size(cw, cw))
        }
    }
    WorldCatalog.locations.filter { it.id in state.discoveredLocationIds }.forEachIndexed { i, loc ->
        val c = Offset(loc.mapX * size.width, loc.mapY * size.height)
        val flick = 0.85f + 0.15f * sin(t * 5f + i * 2f)
        val r = size.width * 0.06f
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB45A).copy(alpha = 0.6f * light.night * flick), Color.Transparent), c, r), r, c)
    }
}

/** Tint plus lights in one pass (for a map drawn without a separate viewport layer). */
fun DrawScope.drawDaylight(state: GameState, a: MapAnalysis?, t: Float, light: Daylight) {
    drawDaylightTint(light)
    drawNightLights(state, a, t, light)
}
