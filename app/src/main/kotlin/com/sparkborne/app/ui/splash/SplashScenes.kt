package com.sparkborne.app.ui.splash

import androidx.compose.ui.graphics.Color
import com.sparkborne.app.ui.theme.RpgPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private fun colors(vararg v: Long): List<Color> = v.map { Color(it) }

// =============================================================================================
// 1. TWILIGHT CITADEL
//    Camera tilts down from a twinkling sky to a moonlit citadel. The moon rises, clouds drift,
//    castle windows light up one by one, the realm banner waves and a shooting star streaks by.
// =============================================================================================

private val CITADEL_SKY = colors(
    0xFF070A1F, 0xFF0B1030, 0xFF111641, 0xFF1A1C4E, 0xFF252458,
    0xFF332B61, 0xFF473369, 0xFF5F3B6E, 0xFF7A4571,
)

internal fun PixelGrid.drawCitadelScene(t: Float, palette: RpgPalette) {
    val w = cols
    val h = rows
    val cx = w / 2
    val gy = (h * 0.70f).toInt()
    val pan = (1f - easeInOutCubic(t / 1.8f)) * 70f
    val horizon = gy + (pan * 0.8f).toInt()

    skyBands(CITADEL_SKY, 0, horizon)
    rect(0, horizon, w, h - horizon, CITADEL_SKY.last())

    // Stars (slow parallax)
    val starOff = (pan * 0.25f).toInt()
    repeat(90) { i ->
        val sx = (hash01(i * 3 + 1) * w).toInt()
        val sy = (hash01(i * 3 + 2) * gy * 0.85f).toInt() + starOff
        if (sy >= horizon - 12) return@repeat
        val tw = 0.5f + 0.5f * sin(t * (1.5f + hash01(i * 3 + 3) * 4f) + i)
        val c = Color(0xFF5B5F9A).mix(Color.White, tw)
        dot(sx, sy, c)
        if (i % 6 == 0 && tw > 0.75f) {
            val d = c.copy(alpha = 0.45f)
            dot(sx - 1, sy, d); dot(sx + 1, sy, d); dot(sx, sy - 1, d); dot(sx, sy + 1, d)
        }
    }

    // Shooting star
    val ss = (t - 2.05f) / 0.5f
    if (ss in 0f..1f) {
        val x0 = w * 0.92f; val y0 = h * 0.05f
        val x1 = w * 0.48f; val y1 = h * 0.21f
        val hx = lerpF(x0, x1, ss); val hy = lerpF(y0, y1, ss)
        val len = hypot(x1 - x0, y1 - y0)
        val ux = (x1 - x0) / len; val uy = (y1 - y0) / len
        val fade = 1f - clamp01((ss - 0.75f) / 0.25f)
        for (k in 0 until 16) {
            val a = (1f - k / 16f) * fade
            dotF(hx - ux * k, hy - uy * k, Color(0xFFE8E4FF).copy(alpha = a))
        }
        dotF(hx, hy - 1, Color.White.copy(alpha = 0.5f * fade))
        dotF(hx, hy + 1, Color.White.copy(alpha = 0.5f * fade))
    }

    // Rising moon
    val mx = (w * 0.76f).toInt()
    val my = (h * 0.31f + (1f - easeOutCubic(t / 2.6f)) * 26f + pan * 0.5f).toInt()
    val moon = Color(0xFFF6EED2)
    disc(mx, my, 13, moon.copy(alpha = 0.05f))
    disc(mx, my, 10, moon.copy(alpha = 0.09f))
    disc(mx, my, 7, moon)
    val crater = Color(0xFFD9C99B)
    rect(mx - 3, my - 2, 2, 2, crater); rect(mx + 1, my + 1, 2, 2, crater)
    rect(mx - 1, my + 4, 2, 1, crater); dot(mx + 2, my - 4, crater)

    // Drifting clouds
    val cloud = Color(0xFF2B2758).copy(alpha = 0.92f)
    val cloudHi = Color(0xFF4A3F7C)
    repeat(4) { i ->
        val span = w + 50
        val speed = 2.5f + hash01(i + 50) * 3.5f
        val x = ((hash01(i + 60) * span + t * speed) % span) - 25f
        val y = h * 0.09f + hash01(i + 70) * h * 0.24f + pan * 0.4f
        rectF(x + 4, y - 2, 9f, 2f, cloud)
        rectF(x, y, 18f, 2f, cloud)
        rectF(x - 5, y + 2, 28f, 2f, cloud)
        rectF(x + 5, y - 2, 8f, 1f, cloudHi)
        rectF(x + 13, y, 5f, 1f, cloudHi)
        rectF(x + 18, y + 2, 5f, 1f, cloudHi)
    }

    // Far mountains + near hills (parallax)
    val mBase = gy + (pan * 0.7f).toInt()
    for (x in 0 until w) {
        val mh = 20f + 9f * sin(x * 0.065f + 1f) + 6f * sin(x * 0.17f + 0.4f) + 3f * sin(x * 0.37f + 2f)
        val top = mBase - mh.toInt()
        rect(x, top, 1, h - top, Color(0xFF221E47))
        dot(x, top, Color(0xFF3A3272))
        if (mh > 31f) dot(x, top + 1, Color(0xFF4C4185))
    }
    val hBase = gy + (pan * 0.85f).toInt()
    for (x in 0 until w) {
        val hh = 9f + 4f * sin(x * 0.09f + 3f) + 3f * sin(x * 0.21f)
        val top = hBase - hh.toInt()
        rect(x, top, 1, h - top, Color(0xFF16132F))
        dot(x, top, Color(0xFF241F45))
    }

    drawCitadel(t, cx, gy + pan.toInt(), palette)
}

private fun PixelGrid.drawCitadel(t: Float, cx: Int, base: Int, palette: RpgPalette) {
    val w = cols
    val h = rows
    val body = Color(0xFF110D22)
    val rim = Color(0xFF2D2650)
    val roofC = Color(0xFF1A1431)

    // Mound + ground
    val moundW = w * 0.30f
    for (x in 0 until w) {
        val d = (x - cx) / moundW
        val mound = if (abs(d) < 1f) (7f * (1f - d * d)).toInt() else 0
        val top = base - mound
        rect(x, top, 1, h - top, Color(0xFF0B0919))
        dot(x, top, Color(0xFF1B1636))
    }
    val g0 = base - 7

    // Winding path from the gate
    for (y in g0 + 1 until h) {
        val f = (y - g0).toFloat() / (h - g0)
        val half = (3f + f * 22f).toInt()
        val px = cx + (sin(f * 3.5f) * 9f * f).toInt()
        rect(px - half, y, half * 2 + 1, 1, Color(0xFF15112B))
        dot(px - half - 1, y, Color(0xFF1E1838))
        dot(px + half + 1, y, Color(0xFF1E1838))
    }

    fun tower(x0: Int, tw: Int, top: Int, crenel: Boolean, roof: Boolean): Int {
        rect(x0, top, tw, g0 - top + 1, body)
        rect(x0 + tw - 1, top, 1, g0 - top, rim)
        if (crenel) {
            var k = 0
            while (k < tw) {
                rect(x0 + k, top - 2, 2, 2, body)
                k += 3
            }
        }
        if (!roof) return top
        val peak = tw / 2 + 1
        for (r in 0..peak) {
            val half = peak - r
            val y = top - 1 - r
            rect(x0 + tw / 2 - half, y, half * 2 + 1, 1, roofC)
            dot(x0 + tw / 2 + half, y, rim)
        }
        return top - 1 - peak
    }

    // Outer towers & walls
    tower(cx - 41, 7, g0 - 26, crenel = false, roof = true)
    tower(cx + 35, 7, g0 - 26, crenel = false, roof = true)
    tower(cx - 34, 13, g0 - 15, crenel = true, roof = false)
    tower(cx + 21, 14, g0 - 15, crenel = true, roof = false)
    // Keep, spire, inner towers
    val keepTop = g0 - 38
    tower(cx - 12, 24, keepTop, crenel = true, roof = false)
    val spireTop = g0 - 50
    tower(cx - 2, 5, spireTop, crenel = false, roof = true)
    val ltTop = g0 - 54
    val flagTip = tower(cx - 21, 9, ltTop, crenel = false, roof = true)
    val rtTop = g0 - 48
    tower(cx + 12, 9, rtTop, crenel = false, roof = true)

    // Gate with portcullis
    rect(cx - 3, g0 - 9, 7, 10, Color(0xFF06050C))
    rect(cx - 2, g0 - 10, 5, 1, Color(0xFF06050C))
    var px = cx - 2
    while (px <= cx + 2) {
        rect(px, g0 - 9, 1, 9, Color(0xFF1A1530))
        px += 2
    }

    // Windows light up one by one
    val windows = listOf(
        intArrayOf(cx - 7, keepTop + 8, 2, 3), intArrayOf(cx + 5, keepTop + 8, 2, 3),
        intArrayOf(cx - 1, keepTop + 16, 2, 4), intArrayOf(cx - 7, keepTop + 23, 2, 3),
        intArrayOf(cx + 5, keepTop + 23, 2, 3), intArrayOf(cx - 18, ltTop + 7, 2, 3),
        intArrayOf(cx - 18, ltTop + 20, 2, 3), intArrayOf(cx + 15, rtTop + 9, 2, 3),
        intArrayOf(cx + 15, rtTop + 23, 2, 3), intArrayOf(cx - 1, spireTop + 4, 1, 2),
        intArrayOf(cx - 39, g0 - 20, 2, 2), intArrayOf(cx + 37, g0 - 20, 2, 2),
    )
    var litCount = 0
    windows.forEachIndexed { i, wd ->
        val on = 0.55f + hash01(i + 200) * 1.3f
        if (t < on) {
            rect(wd[0], wd[1], wd[2], wd[3], Color(0xFF1C1636))
        } else {
            litCount++
            val pop = clamp01(1f - (t - on) / 0.3f)
            rect(wd[0] - 1, wd[1] - 1, wd[2] + 2, wd[3] + 2, Color(0xFFFFB45A).copy(alpha = 0.16f + pop * 0.5f))
            val fl = 0.7f + 0.3f * sin(t * (6f + i) + i * 2f)
            rect(wd[0], wd[1], wd[2], wd[3], Color(0xFFFF9F43).mix(Color(0xFFFFE6A3), fl))
        }
    }
    // Gate warmth grows as the citadel wakes
    rect(cx - 3, g0 - 3, 7, 3, Color(0xFFFF9F43).copy(alpha = 0.06f * litCount))

    // Realm banner (theme accent) waving from the tallest tower
    val poleX = cx - 21 + 4
    val poleTop = flagTip - 9
    rect(poleX, poleTop, 1, 9, Color(0xFF3B3560))
    val accent = palette.accentPrimary
    val accentDark = accent.mix(Color.Black, 0.35f)
    for (c in 0 until 9) {
        val wave = sin(t * 7f - c * 0.75f) * 1.4f * (c / 8f)
        val yy = poleTop + wave.roundToInt()
        val tall = if (c == 8) 3 else 4
        rect(poleX + 1 + c, yy, 1, 2, accent)
        rect(poleX + 1 + c, yy + 2, 1, tall - 2, accentDark)
    }

    // Foreground grass tufts swaying in the night breeze
    val grass = Color(0xFF1C1838)
    val tip = Color(0xFF2B2550)
    repeat(46) { i ->
        val x = (hash01(i + 300) * w).toInt()
        val y = base + 6 + (hash01(i + 400) * (h - base - 10)).toInt()
        for (b in 0..2) {
            val bx = x + b * 2 - 2
            val bh = 2 + ((hash01(i * 7 + b) * 3).toInt())
            val lean = if (sin(t * 2.2f + x * 0.25f + b) > 0.35f) 1 else 0
            rect(bx, y - bh, 1, bh, grass)
            dot(bx + lean, y - bh - 1, tip)
        }
    }
}

// =============================================================================================
// 2. CAMPFIRE VIGIL
//    A flint spark ignites a campfire in a pine clearing. Flames roar to life frame by frame,
//    firelight blooms across the ground, embers & smoke rise, a hooded hero breathes beside the
//    fire and fireflies blink among the trees.
// =============================================================================================

private val CAMP_SKY = colors(0xFF040C10, 0xFF06131A, 0xFF0A1A21, 0xFF0E2229, 0xFF132A30, 0xFF183237)

private val HERO_SPRITE = arrayOf(
    "....hhhh....",
    "...hhhhhh...",
    "..hhhhhhff..",
    "..hhhhhfff..",
    "..hhhhhhh...",
    "...ccccccc..",
    "..cccccccll.",
    ".ccccccccll.",
    ".cccccccccl.",
    ".cccccccccll",
    ".cccccccclll",
    "..ccccllllll",
    "..cccc.lllll",
    "..bbb...bbbb",
)

internal fun PixelGrid.drawCampfireScene(t: Float, palette: RpgPalette) {
    val w = cols
    val h = rows
    val cx = w / 2
    val gy = (h * 0.66f).toInt()
    val fx = cx + 6
    val fy = gy + 22

    val ignite = clamp01((t - 0.42f) / 0.75f)
    val flameScale = if (ignite <= 0f) 0f else easeOutCubic(ignite) * (1f + 0.12f * sin(ignite * PI.toFloat()))
    val tq = stepTime(t, 12f)
    val flicker = 0.85f + 0.15f * sin(tq * 23f) * sin(tq * 7f)
    val light = flameScale * flicker

    // Sky, stars, crescent moon
    skyBands(CAMP_SKY, 0, gy)
    repeat(55) { i ->
        val sx = (hash01(i * 5 + 11) * w).toInt()
        val sy = (hash01(i * 5 + 12) * gy * 0.75f).toInt()
        val tw = 0.5f + 0.5f * sin(t * (2f + hash01(i + 9) * 3f) + i * 1.7f)
        dot(sx, sy, Color(0xFF4F6E73).mix(Color(0xFFE6FFF6), tw))
    }
    val mx = (w * 0.2f).toInt()
    val my = (h * 0.24f).toInt()
    disc(mx, my, 8, Color(0xFFE8F0D8).copy(alpha = 0.06f))
    disc(mx, my, 5, Color(0xFFE8F0D8))
    disc(mx + 2, my - 1, 4, bandColorAt(CAMP_SKY, 0, gy, my))

    // Far pine ridge
    var i = 0
    var x = -3
    while (x < w + 6) {
        val ph = 12 + (hash01(i + 500) * 12).toInt()
        pine(x + (hash01(i + 600) * 3).toInt(), gy + 2, ph, Color(0xFF0B1D22), 0f)
        x += 5
        i++
    }
    // Ground
    rect(0, gy + 2, w, h - gy - 2, Color(0xFF0A1712))
    repeat(70) { k ->
        val gx = (hash01(k + 700) * w).toInt()
        val gyy = gy + 4 + (hash01(k + 800) * (h - gy - 6)).toInt()
        dot(gx, gyy, Color(0xFF11231A))
    }

    // Firelight pool on the ground
    if (light > 0f) {
        ellipse(fx, fy + 1, 52f * light, 15f * light, Color(0xFFFF8A2A).copy(alpha = 0.07f))
        ellipse(fx, fy + 1, 36f * light, 10f * light, Color(0xFFFF9F43).copy(alpha = 0.10f))
        ellipse(fx, fy + 1, 20f * light, 6f * light, Color(0xFFFFC15A).copy(alpha = 0.14f))
    }

    // Near pines framing the clearing (sway gently, tinted by firelight)
    val nearPine = Color(0xFF06121A).mix(Color(0xFF2A1A12), light * 0.25f)
    pine(4, gy + 14, 70, nearPine, sin(t * 1.1f) * 1.2f)
    pine(18, gy + 10, 52, nearPine, sin(t * 1.3f + 1f) * 1.2f)
    pine(w - 6, gy + 15, 74, nearPine, sin(t * 1.0f + 2f) * 1.2f)
    pine(w - 20, gy + 9, 48, nearPine, sin(t * 1.4f + 3f) * 1.2f)

    // Fireflies (theme tinted)
    val fly = Color(0xFFD4F57A).mix(palette.particlePrimary, 0.35f)
    repeat(12) { k ->
        val bx = hash01(k + 900) * w
        val by = gy - 10 + hash01(k + 950) * 34f
        val s = 0.6f + hash01(k + 990)
        val px = bx + sin(t * 0.7f * s + k) * 10f
        val py = by + cos(t * 0.9f * s + k * 2f) * 6f
        val blink = sin(t * 3f * s + k * 1.3f)
        if (blink > 0.1f) {
            rectF(px - 1, py - 1, 3f, 3f, fly.copy(alpha = 0.18f))
            dotF(px, py, fly.copy(alpha = 0.5f + blink * 0.5f))
        }
    }

    // Hero sitting by the fire (breathing bob, warm rim light)
    drawHero(fx - 22, fy - 14, t, light)

    // Sword planted in the ground, catching the firelight
    val sx = fx - 30
    val blade = Color(0xFF6B7684).mix(Color(0xFFFFD9A0), light * 0.4f)
    rect(sx, fy - 12, 1, 12, blade)
    rect(sx - 2, fy - 13, 5, 1, Color(0xFF7A5A2E))
    rect(sx, fy - 16, 1, 3, Color(0xFF3B2A1A))
    dot(sx, fy - 17, Color(0xFFB08A3E))
    val glint = (t - 2.1f) / 0.35f
    if (glint in 0f..1f) {
        val gy2 = fy - 1 - (glint * 11).toInt()
        dot(sx, gy2, Color.White)
        dot(sx - 1, gy2, Color.White.copy(alpha = 0.5f))
        dot(sx + 1, gy2, Color.White.copy(alpha = 0.5f))
    }

    // Stones & logs
    for (k in 0 until 9) {
        val a = k / 9f * 2f * PI.toFloat()
        val stx = fx + (cos(a) * 10f).roundToInt()
        val sty = fy + (sin(a) * 2.5f).roundToInt()
        rect(stx - 1, sty - 1, 3, 2, Color(0xFF3A3F44).mix(Color(0xFFB0703A), light * 0.35f))
        dot(stx - 1, sty - 1, Color(0xFF5A6066))
    }
    val logLit = Color(0xFF7A4A28).mix(Color(0xFFC07030), light * 0.5f)
    for (k in 0..14) {
        dot(fx - 7 + k, fy - 1 - k / 4, Color(0xFF4A2E1C))
        dot(fx - 7 + k, fy - 2 - k / 4, logLit)
        dot(fx + 7 - k, fy - 1 - k / 4, Color(0xFF3B2516))
        dot(fx + 7 - k, fy - 2 - k / 4, logLit)
    }

    // Flint spark that ignites the fire
    if (t in 0.15f..0.55f) {
        val st = (t - 0.15f) / 0.4f
        repeat(6) { k ->
            val a = -PI.toFloat() * (0.15f + hash01(k + 40) * 0.7f)
            val r = st * (4f + hash01(k + 41) * 7f)
            dotF(fx + cos(a) * r, fy - 3 + sin(a) * r + st * st * 6f, Color(0xFFFFF3B0).copy(alpha = 1f - st))
        }
    }

    // The flame — per-column procedural sprite at 12 fps
    if (flameScale > 0.02f) {
        val core = Color(0xFFFFF6C9)
        val yellow = Color(0xFFFFC94A)
        val orange = Color(0xFFFF8A2A).mix(palette.particlePrimary, 0.25f)
        val red = Color(0xFFD6452A).mix(palette.particleSecondary, 0.25f)
        val baseY = fy - 3
        for (dx in -6..6) {
            val nx = dx / 7f
            val base = 19f * (1f - nx * nx)
            val n = 0.7f + 0.2f * sin(tq * 11f + dx * 1.9f) + 0.14f * sin(tq * 17.3f - dx * 3.1f)
            val fh = (base * n * flameScale).toInt()
            if (fh <= 0) continue
            val col = fx + dx
            val redH = fh
            val orangeH = (fh * 0.78f).toInt()
            val yellowH = (fh * 0.52f).toInt()
            val coreH = if (abs(dx) <= 2) (fh * 0.32f).toInt() else 0
            rect(col, baseY - redH, 1, redH, red)
            rect(col, baseY - orangeH, 1, orangeH, orange)
            rect(col, baseY - yellowH, 1, yellowH, yellow)
            rect(col, baseY - coreH, 1, coreH, core)
            if (hash01(dx * 31 + (tq * 12f).toInt() * 7) > 0.72f) {
                dot(col, baseY - fh - 2 - (hash01(dx + (tq * 12f).toInt()) * 3).toInt(), red)
            }
        }
    }

    // Embers rising on the heat
    if (t > 0.6f) {
        repeat(26) { k ->
            val period = 1.4f + hash01(k + 1000) * 1.2f
            val life = ((t - 0.6f) / period + hash01(k + 1100)) % 1f
            if (life * period > t - 0.6f) return@repeat // not born yet
            val ex = fx + (hash01(k + 1200) - 0.5f) * 8f + sin(life * 8f + k) * 3f * life + life * 6f
            val ey = fy - 8 - life * (55f + hash01(k + 1300) * 35f)
            val c = palette.particlePrimary.mix(palette.particleSecondary, life).copy(alpha = (1f - life) * flameScale)
            if (life < 0.2f) rectF(ex, ey, 2f, 2f, c) else dotF(ex, ey, c)
        }
    }

    // Smoke curling upward
    repeat(8) { k ->
        val life = ((t * 0.32f) + k / 8f) % 1f
        val sx2 = fx + sin(life * 5f + k) * 3f + life * 14f
        val sy2 = fy - 24 - life * 70f
        val sz = 2f + life * 3f
        rectF(sx2, sy2, sz, sz, Color(0xFF6A7B7C).copy(alpha = 0.16f * (1f - life) * flameScale))
    }
}

private fun PixelGrid.pine(x: Int, baseY: Int, ph: Int, c: Color, sway: Float) {
    for (r in 0 until ph) {
        val frac = r.toFloat() / ph
        val half = (frac * ph * 0.3f).toInt() + (if (r % 4 == 3) 1 else 0)
        val shift = (sway * (1f - frac)).roundToInt()
        rect(x - half + shift, baseY - ph + r, half * 2 + 1, 1, c)
    }
    rect(x, baseY, 1, 2, c)
}

private fun PixelGrid.drawHero(x0: Int, y0: Int, t: Float, light: Float) {
    val breath = if (sin(t * 2.6f) > 0.2f) -1 else 0
    val hood = Color(0xFF24363A)
    val face = Color(0xFF6A4A38).mix(Color(0xFFE6A070), light)
    val cloak = Color(0xFF1A2A2B)
    val lit = Color(0xFF2A3233).mix(Color(0xFF9A5E36), light)
    val boots = Color(0xFF120C08)
    HERO_SPRITE.forEachIndexed { r, row ->
        val yOff = if (r <= 5) breath else 0
        row.forEachIndexed { c, ch ->
            val color = when (ch) {
                'h' -> hood
                'f' -> face
                'c' -> cloak
                'l' -> lit
                'b' -> boots
                else -> null
            }
            if (color != null) dot(x0 + c, y0 + r + yOff, color)
        }
    }
}

// =============================================================================================
// 3. OBSIDIAN ANVIL
//    Inside a forge: bellows-breathing coals light the brick wall, a smith's hammer swings in a
//    full arc and strikes a glowing ingot three times — sparks burst and bounce, the screen
//    shakes, and the final blow flashes the crest into existence.
// =============================================================================================

private val FORGE_STRIKES = floatArrayOf(0.7f, 1.3f, 1.95f)
private const val HAMMER_RAISED = 250f
private const val HAMMER_HIT = 143.5f

private fun hammerAngle(t: Float): Float {
    for ((i, ts) in FORGE_STRIKES.withIndex()) {
        val last = i == FORGE_STRIKES.lastIndex
        when {
            t < ts - 0.2f -> return HAMMER_RAISED
            t < ts -> return lerpF(HAMMER_RAISED, HAMMER_HIT, easeInQuad((t - (ts - 0.2f)) / 0.2f))
            last -> return HAMMER_HIT + 7f * sin(clamp01((t - ts) / 0.22f) * PI.toFloat())
            t < ts + 0.06f -> return HAMMER_HIT + 3f * ((t - ts) / 0.06f)
            t < ts + 0.38f -> return lerpF(HAMMER_HIT + 3f, HAMMER_RAISED, easeOutCubic((t - ts - 0.06f) / 0.32f))
        }
    }
    return HAMMER_HIT
}

internal fun PixelGrid.drawForgeScene(t: Float, palette: RpgPalette) {
    val w = cols
    val h = rows
    val cx = w / 2
    val floorY = (h * 0.66f).toInt() + 10
    val ax = cx + 4
    val faceY = floorY - 22
    val ix = ax + 1
    val iy = faceY - 3

    // Impact bookkeeping
    var lastDt = Float.MAX_VALUE
    var lastIdx = -1
    for ((i, ts) in FORGE_STRIKES.withIndex()) {
        if (t >= ts) { lastDt = t - ts; lastIdx = i }
    }
    val impactGlow = if (lastIdx >= 0) exp(-lastDt * 3f) else 0f

    // Screen shake on every strike
    if (lastIdx >= 0) {
        val amp = (if (lastIdx == FORGE_STRIKES.lastIndex) 3.2f else 1.6f) * exp(-lastDt * 12f)
        ox = (sin(lastDt * 90f) * amp).roundToInt()
        oy = (cos(lastDt * 77f) * amp).roundToInt()
    }

    val tq = stepTime(t, 10f)
    val bellows = 0.8f + 0.2f * sin(t * 3f)
    val heat = (bellows + impactGlow * 0.3f).coerceAtMost(1.2f)
    val forgeX = (w * 0.2f).toInt()
    val forgeY = floorY - 14

    // Brick wall, warmed by the forge
    rect(-4, -4, w + 8, floorY + 4, Color(0xFF0D0B10))
    val bricks = colors(0xFF1C1820, 0xFF201B25, 0xFF18151D)
    var row = 0
    var y = -4
    while (y < floorY) {
        val off = if (row % 2 == 0) 0 else 6
        var x = -off - 4
        var k = 0
        while (x < w + 4) {
            val hh = hash01(row * 131 + k * 17)
            val d = hypot(x + 5.5f - forgeX, y + 2f - forgeY)
            val glow = clamp01(1f - d / 62f) * heat
            val c = bricks[(hh * 3).toInt().coerceAtMost(2)].mix(Color(0xFF5A2C18), glow * 0.7f)
            rect(x, y, 11, 4, c)
            rect(x, y, 11, 1, c.mix(Color.White, 0.04f))
            x += 12
            k++
        }
        y += 5
        row++
    }

    // Tool rack
    val rackY = floorY - 58
    val rackX0 = (w * 0.60f).toInt()
    val rackX1 = (w * 0.95f).toInt()
    val toolC = Color(0xFF19161D)
    val toolRim = Color(0xFF3A2E2E)
    rect(rackX0, rackY, rackX1 - rackX0, 2, Color(0xFF2A2026))
    val tongX = rackX0 + 6
    for (k in 0 until 16) { dot(tongX - k / 5, rackY + 2 + k, toolC); dot(tongX + 2 + k / 5, rackY + 2 + k, toolC) }
    val hx = rackX0 + 18
    rect(hx, rackY + 2, 1, 13, Color(0xFF3A2A1E)); rect(hx - 2, rackY + 15, 5, 3, toolC); dot(hx - 2, rackY + 15, toolRim)
    val fileX = rackX0 + 28
    rect(fileX, rackY + 2, 1, 15, toolC); rect(fileX, rackY + 2, 1, 4, Color(0xFF3A2A1E))

    // Forge: hood, stone block, glowing mouth
    for (yy in 0 until floorY - 30) {
        val f = yy.toFloat() / (floorY - 30)
        val half = (7 + f * 13).toInt()
        rect(forgeX - half, yy, half * 2 + 1, 1, Color(0xFF1D1A22))
        dot(forgeX + half, yy, Color(0xFF2E2834))
    }
    rect(forgeX - 16, floorY - 30, 33, 30, Color(0xFF2A2530))
    rect(forgeX - 16, floorY - 30, 33, 1, Color(0xFF3D3644))
    rect(forgeX - 17, floorY - 31, 35, 2, Color(0xFF241F2A))
    for (r in 0 until 16) {
        val inset = if (r < 4) ((4 - r) * 1.5f).toInt() else 0
        val f = r / 15f
        val c = Color(0xFF5A1A10).mix(Color(0xFFB8401A), f * 1.6f).mix(Color(0xFFFF7A1E), (f - 0.6f) * 2.5f * heat)
        rect(forgeX - 10 + inset, floorY - 22 + r, 21 - inset * 2, 1, c)
    }
    val coalCols = colors(0xFFFFE08A, 0xFFFFB03B, 0xFFFF7A1E, 0xFFC0401A)
    for (cxp in forgeX - 9..forgeX + 9) {
        for (cyp in floorY - 9..floorY - 7) {
            val idx = (hash01(cxp * 7 + cyp * 13 + (tq * 10f).toInt() * 101) * 4f).toInt().coerceAtMost(3)
            dot(cxp, cyp, coalCols[idx])
        }
    }
    // Forge embers up the chimney
    repeat(12) { k ->
        val life = (t * (0.5f + hash01(k + 77) * 0.5f) + hash01(k + 78)) % 1f
        val ex = forgeX + (hash01(k + 79) - 0.5f) * 14f + sin(life * 9f + k) * 2f
        val ey = floorY - 18 - life * 70f
        dotF(ex, ey, palette.particlePrimary.mix(palette.particleSecondary, life).copy(alpha = 1f - life))
    }

    // Floor
    rect(-4, floorY, w + 8, h - floorY + 4, Color(0xFF141118))
    var fy = floorY + 6
    var fr = 0
    while (fy < h + 4) {
        rect(-4, fy, w + 8, 1, Color(0xFF0E0C11))
        var vx = if (fr % 2 == 0) 0 else 8
        while (vx < w) { rect(vx, fy - 6, 1, 6, Color(0xFF0E0C11)); vx += 16 }
        fy += 7
        fr++
    }
    rect(-4, floorY, w + 8, 1, Color(0xFF221E28))
    ellipse(forgeX, floorY + 3, 30f * heat, 6f * heat, Color(0xFFFF7A1E).copy(alpha = 0.10f))
    ellipse(ix, floorY + 2, 26f, 4f, Color(0xFFFF9F43).copy(alpha = 0.05f + impactGlow * 0.12f))

    // Quench barrel
    val bx = (w * 0.80f).toInt()
    rect(bx, floorY - 14, 14, 15, Color(0xFF2B1E15))
    rect(bx + 13, floorY - 14, 1, 15, Color(0xFF3B2A1D))
    rect(bx, floorY - 12, 14, 1, Color(0xFF474A55))
    rect(bx, floorY - 4, 14, 1, Color(0xFF474A55))
    rect(bx + 1, floorY - 15, 12, 1, Color(0xFF1E3C4A))
    dot(bx + 1 + ((t * 6f).toInt() % 12), floorY - 15, Color(0xFF4A7C8C))

    // Anvil
    ellipse(ax, floorY + 1, 17f, 2f, Color.Black.copy(alpha = 0.45f))
    val anvilRows = arrayOf(
        intArrayOf(-12, 11), intArrayOf(-17, 11), intArrayOf(-20, 11), intArrayOf(-15, 10), intArrayOf(-9, 9),
        intArrayOf(-7, 7), intArrayOf(-5, 5), intArrayOf(-4, 4), intArrayOf(-4, 4), intArrayOf(-4, 4),
        intArrayOf(-4, 4), intArrayOf(-5, 5), intArrayOf(-6, 6), intArrayOf(-7, 7), intArrayOf(-8, 8),
        intArrayOf(-9, 9), intArrayOf(-11, 11), intArrayOf(-11, 11), intArrayOf(-11, 11),
        intArrayOf(-12, 12), intArrayOf(-12, 12), intArrayOf(-12, 12),
    )
    val anvilBody = Color(0xFF2A2D36)
    val warm = Color(0xFF8A4A2A)
    anvilRows.forEachIndexed { r, span ->
        val yy = faceY + r
        val c = when (r) {
            0 -> Color(0xFF7A8394).mix(Color(0xFFFFC27A), impactGlow * 0.4f)
            1 -> Color(0xFF4A505E)
            else -> anvilBody
        }
        rect(ax + span[0], yy, span[1] - span[0] + 1, 1, c)
        rect(ax + span[0], yy, 2, 1, c.mix(warm, heat * 0.6f))
        if (r >= 20) rect(ax - 4, yy, 9, 1, Color(0xFF141118))
    }

    // Glowing ingot on the anvil face
    val barHeat = clamp01(0.55f + impactGlow * 0.45f)
    val barC = when {
        barHeat > 0.85f -> Color(0xFFFF8A2A).mix(Color(0xFFFFF0A0), (barHeat - 0.85f) / 0.15f)
        else -> Color(0xFFC0401A).mix(Color(0xFFFF8A2A), (barHeat - 0.55f) / 0.3f)
    }
    rect(ax - 8, faceY - 4, 17, 5, barC.copy(alpha = 0.12f + impactGlow * 0.15f))
    rect(ax - 6, faceY - 2, 13, 2, barC)
    rect(ax - 6, faceY - 2, 13, 1, barC.mix(Color.White, 0.25f))
    // Tongs holding the ingot
    for (k in 0 until 20) dot(ax - 7 - k, faceY - 1 + k / 2, Color(0xFF3A3E48))

    // Impact effects (rings + sparks) for every strike so far
    for ((i, ts) in FORGE_STRIKES.withIndex()) {
        val dt = t - ts
        if (dt < 0f || dt > 1.3f) continue
        val big = i == FORGE_STRIKES.lastIndex
        if (dt < 0.2f) {
            val r = 3f + dt / 0.2f * (if (big) 20f else 12f)
            val a = 1f - dt / 0.2f
            for (s in 0 until 28) {
                val ang = s / 28f * 2f * PI.toFloat()
                dotF(ix + cos(ang) * r, iy + sin(ang) * r * 0.5f, Color(0xFFFFF6D0).copy(alpha = a))
            }
        }
        val n = if (big) 44 else 24
        for (k in 0 until n) {
            val seed = i * 977 + k * 13
            val life = 0.35f + hash01(seed + 1) * 0.65f
            if (dt > life) continue
            val vx = (hash01(seed + 2) - 0.5f) * 2f * 60f * (if (big) 1.4f else 1f)
            val vy = -(25f + hash01(seed + 3) * 75f) * (if (big) 1.2f else 1f)
            fun posAt(tt: Float): Pair<Float, Float> {
                var py = iy + vy * tt + 0.5f * 170f * tt * tt
                if (py > floorY) py = floorY - (py - floorY) * 0.3f
                return Pair(ix + vx * tt, py)
            }
            val (sx, sy) = posAt(dt)
            val (tx, ty) = posAt((dt - 0.03f).coerceAtLeast(0f))
            val age = dt / life
            val c = when {
                age < 0.25f -> Color(0xFFFFFBE0)
                age < 0.6f -> palette.particlePrimary
                else -> palette.particleSecondary
            }
            dotF(tx, ty, c.copy(alpha = 0.45f * (1f - age)))
            dotF(sx, sy, c.copy(alpha = 1f - age * 0.6f))
        }
    }

    // Hammer + the smith's forearm (off-screen right)
    val ang = hammerAngle(t)
    val swing = clamp01((HAMMER_RAISED - ang) / (HAMMER_RAISED - HAMMER_HIT))
    val pivX = ax + 24f
    val pivY = faceY - 20f + swing * 3f
    val rad = ang / 180f * PI.toFloat()
    val dx = cos(rad)
    val dy = sin(rad)
    val handleLen = 26f
    var s = 0f
    while (s <= handleLen) {
        dotF(pivX + dx * s, pivY + dy * s, Color(0xFF6B4A2E))
        s += 0.5f
    }
    val hx2 = pivX + dx * handleLen
    val hy2 = pivY + dy * handleLen
    val px = -dy
    val py = dx
    var u = -4f
    while (u <= 4f) {
        var v = -2f
        while (v <= 2f) {
            val c = if (v < -1f) Color(0xFF7C8494) else Color(0xFF3E434E)
            dotF(hx2 + px * u + dx * v, hy2 + py * u + dy * v, c)
            v += 0.5f
        }
        u += 0.5f
    }
    // Motion smear while swinging down
    if (ang < HAMMER_RAISED - 5f && ang > HAMMER_HIT + 8f) {
        for (k in 1..3) {
            val ra = (ang + k * 9f) / 180f * PI.toFloat()
            dotF(pivX + cos(ra) * handleLen, pivY + sin(ra) * handleLen, Color(0xFFFFE6B0).copy(alpha = 0.3f / k))
        }
    }
    val armAng = atan2(pivY - (pivY + 20f), pivX - (w + 8f))
    var a = 0f
    val armLen = hypot(w + 8f - pivX, 20f)
    while (a <= armLen) {
        val axp = pivX + 2f - cos(armAng) * a
        val ayp = pivY - sin(armAng) * a
        rectF(axp - 2f, ayp - 2f, 5f, 5f, Color(0xFF3A2A22))
        dotF(axp, ayp + 2f, Color(0xFF241A15))
        a += 1f
    }
    rectF(pivX - 3f, pivY - 3f, 5f, 5f, Color(0xFFC98E62))
    dotF(pivX - 3f, pivY - 3f, Color(0xFFE6B08A))

    // Ambient dust motes in the forge light
    repeat(14) { k ->
        val mx = (hash01(k + 1500) * w + t * (2f + k % 3)) % w
        val my = hash01(k + 1600) * floorY + sin(t + k) * 3f
        dotF(mx, my, Color(0xFFFFC27A).copy(alpha = 0.12f + 0.1f * sin(t * 2f + k)))
    }

    ox = 0
    oy = 0

    // Final blow: white-hot flash
    if (lastIdx == FORGE_STRIKES.lastIndex) {
        fill(Color(0xFFFFF4E0).copy(alpha = 0.32f * exp(-lastDt * 8f)))
    }
}
