package com.sparkborne.app.ui.hero

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.model.MonsterCompanion
import com.sparkborne.game.model.MonsterStage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

// =============================================================================================
// Bestiary: procedurally drawn companions, foes and bosses. Every creature is composed from
// shaded pixel primitives in a 64x56 buffer (facing left), auto-outlined like the hero.
// =============================================================================================

private const val CW = 64
private const val CH = 56

private val OUTLINE = Color(0xFF0B0B10)
private val BAYER = floatArrayOf(0f, 8f, 2f, 10f, 12f, 4f, 14f, 6f, 3f, 11f, 1f, 9f, 15f, 7f, 13f, 5f)

private fun bayer(x: Int, y: Int) = BAYER[(y and 3) * 4 + (x and 3)] / 16f - 0.5f
private fun col(v: Long) = Color(v)
private fun Color.dk(f: Float = 0.25f) = this.mixWith(Color.Black, f)
private fun Color.lt(f: Float = 0.25f) = this.mixWith(Color.White, f)

private fun tone(c: Color, l: Float): Color = when {
    l > 0.42f -> c.lt(0.3f)
    l > 0.06f -> c
    l > -0.32f -> c.dk(0.22f)
    else -> c.dk(0.44f)
}

private class CBuf {
    val px = arrayOfNulls<Color>(CW * CH)
    var dx = 0
    var dy = 0

    fun set(x: Int, y: Int, c: Color) {
        val bx = x + dx
        val by = y + dy
        if (bx in 0 until CW && by in 0 until CH) px[by * CW + bx] = c
    }

    fun set(x: Number, y: Number, c: Color) = set(x.toFloat().toInt(), y.toFloat().toInt(), c)

    fun rect(x0: Number, y0: Number, x1: Number, y1: Number, c: Color) {
        for (y in y0.toInt()..y1.toInt()) for (x in x0.toInt()..x1.toInt()) set(x, y, c)
    }

    /** Shaded ellipse, lit from the top-left, with ordered dithering between tones. */
    fun blob(cx: Number, cy: Number, rx: Number, ry: Number, c: Color, shade: Boolean = true) {
        val fx = cx.toFloat(); val fy = cy.toFloat(); val frx = rx.toFloat().coerceAtLeast(0.5f); val fry = ry.toFloat().coerceAtLeast(0.5f)
        for (y in floor(fy - fry).toInt()..ceil(fy + fry).toInt()) {
            for (x in floor(fx - frx).toInt()..ceil(fx + frx).toInt()) {
                val nx = (x + 0.5f - fx) / frx
                val ny = (y + 0.5f - fy) / fry
                val d = nx * nx + ny * ny
                if (d > 1f) continue
                if (!shade) { set(x, y, c); continue }
                val nz = sqrt(1f - d)
                val l = (nx * -0.55f + ny * -0.8f) * 0.8f + nz * 0.5f - 0.35f + bayer(x, y) * 0.2f
                set(x, y, tone(c, l))
            }
        }
    }

    /** Filled polygon (flattened x,y pairs) shaded top-to-bottom. */
    fun poly(c: Color, vararg p: Number, shade: Boolean = true) {
        val xs = FloatArray(p.size / 2) { p[it * 2].toFloat() }
        val ys = FloatArray(p.size / 2) { p[it * 2 + 1].toFloat() }
        val minY = floor(ys.min()).toInt(); val maxY = ceil(ys.max()).toInt()
        val minX = floor(xs.min()).toInt(); val maxX = ceil(xs.max()).toInt()
        val h = (maxY - minY).coerceAtLeast(1)
        for (y in minY..maxY) for (x in minX..maxX) {
            val px = x + 0.5f; val py = y + 0.5f
            var inside = false
            var j = xs.size - 1
            for (i in xs.indices) {
                if ((ys[i] > py) != (ys[j] > py) && px < (xs[j] - xs[i]) * (py - ys[i]) / (ys[j] - ys[i]) + xs[i]) inside = !inside
                j = i
            }
            if (!inside) continue
            if (!shade) { set(x, y, c); continue }
            val l = 0.5f - (y - minY).toFloat() / h * 0.95f + bayer(x, y) * 0.2f
            set(x, y, tone(c, l))
        }
    }

    fun line(c: Color, x0: Number, y0: Number, x1: Number, y1: Number) {
        val ax = x0.toFloat(); val ay = y0.toFloat(); val bx = x1.toFloat(); val by = y1.toFloat()
        val n = maxOf(abs(bx - ax), abs(by - ay)).toInt().coerceAtLeast(1)
        for (i in 0..n) {
            val f = i / n.toFloat()
            set(floor(ax + (bx - ax) * f + 0.5f).toInt(), floor(ay + (by - ay) * f + 0.5f).toInt(), c)
        }
    }

    /** A shaded limb through (x, y, radius) triples. */
    fun tube(c: Color, vararg p: Number) {
        val pts = (0 until p.size / 3).map { Triple(p[it * 3].toFloat(), p[it * 3 + 1].toFloat(), p[it * 3 + 2].toFloat()) }
        for (k in 0 until pts.size - 1) {
            val (ax, ay, ar) = pts[k]
            val (bx, by, br) = pts[k + 1]
            val n = (maxOf(abs(bx - ax), abs(by - ay)) * 1.5f).toInt().coerceAtLeast(1)
            for (i in 0..n) {
                val f = i / n.toFloat()
                val r = ar + (br - ar) * f
                blob(ax + (bx - ax) * f, ay + (by - ay) * f, r, r, c)
            }
        }
    }

    /** A tapered spike from a base (width w) to a tip. */
    fun spike(c: Color, x0: Number, y0: Number, x1: Number, y1: Number, w: Number = 2) {
        val ax = x0.toFloat(); val ay = y0.toFloat(); val bx = x1.toFloat(); val by = y1.toFloat()
        val len = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay)).coerceAtLeast(0.01f)
        val nx = -(by - ay) / len * w.toFloat() / 2f
        val ny = (bx - ax) / len * w.toFloat() / 2f
        poly(c, ax + nx, ay + ny, bx, by, ax - nx, ay - ny)
    }

    fun eye(x: Int, y: Int, c: Color, blink: Boolean = false, big: Boolean = false) {
        if (blink) { set(x, y, OUTLINE); if (big) set(x + 1, y, OUTLINE); return }
        if (big) {
            rect(x, y - 1, x + 1, y, c.dk(0.2f)); set(x, y - 1, Color.White); set(x + 1, y, c.lt(0.3f))
        } else {
            set(x, y, c); set(x, y - 1, c.lt(0.6f))
        }
    }
}

private class CFx {
    val dots = mutableListOf<Triple<Float, Float, Color>>()
    val glows = mutableListOf<Triple<Float, Float, Pair<Float, Color>>>()
    fun dot(x: Number, y: Number, c: Color) { dots += Triple(x.toFloat(), y.toFloat(), c) }
    fun glow(x: Number, y: Number, r: Number, c: Color) { glows += Triple(x.toFloat(), y.toFloat(), r.toFloat() to c) }

    /** Rising particles (embers, bubbles, motes) from a strip starting at x with width w. */
    fun rise(t: Float, x: Number, y: Number, w: Int, c: Color, n: Int = 6, height: Float = 14f, speed: Float = 0.6f) {
        for (i in 0 until n) {
            val ph = (t * speed + i * 0.37f) % 1f
            val px = x.toFloat() + (i * 7) % w.coerceAtLeast(1) + sin(t * 2f + i) * 1.5f
            dot(px, y.toFloat() - ph * height, c.copy(alpha = (1f - ph).coerceIn(0f, 1f)))
        }
    }

    /** Drifting particles (snow, mist, sand) falling or blowing across the creature. */
    fun drift(t: Float, c: Color, n: Int = 10, fall: Boolean = true) {
        for (i in 0 until n) {
            val ph = (t * 0.25f + i * 0.173f) % 1f
            val x = ((i * 23) % CW) + sin(t + i) * 2f
            dot(if (fall) x else (CW * ph + i * 5) % CW, if (fall) ph * CH else 10f + (i * 11) % 40, c)
        }
    }
}

private class Anim(val t: Float) {
    /** 0/1 breathing bob. */
    fun bob(speed: Float = 1f, phase: Float = 0f) = if (sin((t + phase) * speed * 3.1416f) > 0f) 0 else 1
    fun wave(speed: Float = 1f, phase: Float = 0f) = sin((t + phase) * speed * 3.1416f)
    fun blink(period: Float = 4.1f) = (t % period) > period - 0.14f
    fun pulse(speed: Float = 1f) = 0.5f + 0.5f * sin(t * speed * 3.1416f)
}

private typealias Painter = (CBuf, CFx, Anim) -> Unit

// =============================================================================================
// Companions (affinity × stage)
// =============================================================================================

private val ferriteCub: Painter = { b, fx, a ->
    val steel = col(0xFF7A8698); val pale = col(0xFFB4BFCC); val amber = col(0xFFFFA43A)
    b.blob(43, 44, 2.5, 2, steel)
    b.blob(36, 50, 3, 2.5, steel.dk(0.2f))
    b.blob(34, 44, 9, 7, steel)
    b.blob(33, 47, 5, 3, pale, shade = false)
    listOf(30 to 40, 35 to 39, 39 to 41).forEach { (x, y) -> b.set(x, y, col(0xFFD9A436)) }
    b.blob(28, 50, 3, 2.5, steel)
    b.set(26, 52, pale); b.set(28, 52, pale)
    b.dy = a.bob(0.8f)
    b.blob(21, 32, 2.5, 2.5, steel); b.set(21, 32, col(0xFFD98A6A))
    b.blob(29, 31, 2.5, 2.5, steel); b.set(29, 31, col(0xFFD98A6A))
    b.blob(25, 37, 7, 6, steel)
    b.rect(20, 34, 30, 34, steel.lt(0.25f))
    b.blob(20, 40, 3.5, 2.2, pale)
    b.set(17, 39, OUTLINE)
    b.eye(23, 37, amber, a.blink())
    b.dy = 0
}

private val bastionDrake: Painter = { b, fx, a ->
    val hide = col(0xFF4F6A8E); val plate = col(0xFF9AB0CC); val gold = col(0xFFD9A436); val cyan = col(0xFF6FE3FF)
    b.tube(hide.dk(0.2f), 44, 42, 3, 46, 51, 2.5)
    b.tube(hide.dk(0.2f), 31, 42, 3, 31, 51, 2.5)
    b.tube(hide, 47, 40, 3.2, 54, 44, 2.4, 59, 41, 1.6, 62, 37, 1)
    b.spike(plate, 61, 38, 63, 33, 3)
    b.poly(hide.dk(0.35f), 34, 31, 50, 22, 52, 30, 46, 36)
    b.blob(37, 38, 12, 8, hide)
    b.blob(35, 42, 8, 3.5, col(0xFFC9B88A))
    for (i in 0 until 4) {
        val x = 30 + i * 5; val y = 30 + abs(i - 1.5f).toInt()
        b.poly(gold, x - 2.5f, y + 1, x, y - 4, x + 2.5f, y + 1, x, y + 3, shade = false)
        b.poly(plate, x - 1.5f, y + 1, x, y - 2.5f, x + 1.5f, y + 1, x, y + 2)
    }
    b.tube(hide, 41, 43, 3.2, 42, 52, 2.6)
    b.tube(hide, 27, 42, 3.2, 26, 52, 2.6)
    listOf(24, 26, 28, 40, 42, 44).forEach { b.set(it, 52, col(0xFFE8E0C8)) }
    b.dy = a.bob(0.7f)
    b.tube(hide, 29, 34, 4, 22, 25, 3.2)
    b.spike(plate, 21, 19, 29, 12, 3)
    b.blob(18, 23, 6, 4.5, hide)
    b.blob(13, 25, 4, 2.2, hide.lt(0.1f))
    b.rect(11, 26, 16, 26, OUTLINE)
    b.set(10, 24, OUTLINE)
    b.eye(16, 22, cyan, a.blink(), big = true)
    fx.glow(16.5f, 22, 3, cyan.copy(alpha = 0.25f))
    b.dy = 0
}

private val aegisBehemoth: Painter = { b, fx, a ->
    val hide = col(0xFF3E4F66); val plate = col(0xFF9AAFC8); val gold = col(0xFFD9A436); val rune = col(0xFF6FF0FF)
    b.tube(hide.dk(0.25f), 30, 44, 4.5, 30, 52, 4.2)
    b.tube(hide.dk(0.25f), 50, 44, 4.5, 52, 52, 4.2)
    b.tube(hide, 54, 34, 4.5, 61, 40, 2.5, 63, 44, 1.5)
    b.blob(35, 35, 21, 13, hide)
    b.blob(33, 43, 15, 4, hide.lt(0.15f))
    val glow = 0.35f + 0.4f * a.pulse(0.8f)
    listOf(Triple(25, 24, 7), Triple(37, 21, 8), Triple(49, 25, 7)).forEachIndexed { i, (x, y, r) ->
        b.blob(x, y, r + 1, r * 0.72f + 1, gold, shade = false)
        b.blob(x, y, r, r * 0.72f, plate)
        b.line(rune, x - 2, y, x + 2, y); b.line(rune, x, y - 2, x, y + 2)
        b.set(x - 2, y - 2, rune); b.set(x + 2, y + 2, rune)
        fx.glow(x, y, 5, rune.copy(alpha = glow * 0.35f))
    }
    b.tube(hide, 22, 42, 5.5, 22, 52, 5)
    b.tube(hide, 44, 42, 5.5, 45, 52, 5)
    listOf(18, 21, 24, 41, 44, 47).forEach { b.set(it, 53, col(0xFFE8E0C8)) }
    b.dy = a.bob(0.5f)
    b.blob(13, 36, 9, 7.5, hide)
    b.poly(plate, 6, 30, 18, 28, 20, 34, 8, 36)
    b.rect(8, 30, 18, 30, gold)
    b.spike(col(0xFFEDE3C8), 7, 32, 2, 18, 5)
    b.spike(col(0xFFEDE3C8), 13, 29, 13, 22, 3)
    b.blob(6, 40, 3.5, 2.5, hide.lt(0.1f))
    b.rect(3, 42, 9, 42, OUTLINE)
    b.eye(11, 35, rune, a.blink(5f), big = true)
    fx.glow(11.5f, 35, 4, rune.copy(alpha = 0.35f))
    b.dy = 0
}

private val voltWisp: Painter = { b, fx, a ->
    val core = col(0xFFFFE36A); val cyan = col(0xFF7FE8FF)
    val fy = (a.wave(0.6f) * 2f)
    fx.glow(32, 34 + fy, 14, core.copy(alpha = 0.3f))
    for (i in 0 until 5) {
        val ang = i / 5f * 6.283f + a.t * 0.8f
        var x = 32f; var y = 34f + fy
        for (s in 0 until 5) {
            val nx = x + cos(ang) * 2.2f + (if (s % 2 == 0) 1 else -1) * sin(ang) * 1.2f
            val ny = y + sin(ang) * 2.2f - (if (s % 2 == 0) 1 else -1) * cos(ang) * 1.2f
            if (s > 2) b.line(cyan, x, y, nx, ny)
            x = nx; y = ny
        }
    }
    b.tube(cyan, 35, 40 + fy, 2.2, 39, 45 + fy + a.wave(1.2f), 1.4, 44, 47 + fy - a.wave(1.2f), 0.7)
    b.blob(32, 34 + fy, 8, 8, core)
    b.blob(30, 32 + fy, 3.5, 3.5, col(0xFFFFFBE0), shade = false)
    if (!a.blink(3.3f)) {
        b.rect(28, 34 + fy, 28, 35 + fy, col(0xFF1E2A5A)); b.rect(34, 34 + fy, 34, 35 + fy, col(0xFF1E2A5A))
    } else {
        b.set(28, 35 + fy, col(0xFF1E2A5A)); b.set(34, 35 + fy, col(0xFF1E2A5A))
    }
    b.set(31, 38 + fy, col(0xFF1E2A5A))
    for (i in 0 until 6) if (((a.t * 3f + i * 0.31f) % 1f) < 0.3f) fx.dot(32 + cos(i * 1.7f) * 13f, 34 + fy + sin(i * 2.3f) * 11f, Color.White)
}

private val galeLynx: Painter = { b, fx, a ->
    val fur = col(0xFF8FC9C8); val white = col(0xFFEAF6F4); val bolt = col(0xFFFFE36A)
    val sw = a.wave(1.2f)
    b.tube(fur, 46, 38, 2.5, 51, 33 + sw, 2, 54, 29 + sw, 1.6)
    b.spike(bolt, 54, 29 + sw, 58, 24 + sw, 2)
    b.tube(fur.dk(0.2f), 42, 42, 2, 44, 51, 1.6)
    b.tube(fur.dk(0.2f), 29, 42, 2, 28, 51, 1.6)
    b.blob(36, 39, 11, 6, fur)
    b.blob(34, 43, 7, 2.5, white, shade = false)
    listOf(32 to 36, 37 to 35, 42 to 37, 39 to 39).forEach { (x, y) -> b.set(x, y, fur.dk(0.45f)) }
    b.tube(fur, 39, 42, 2, 40, 52, 1.6)
    b.tube(fur, 26, 42, 2, 24, 52, 1.6)
    b.dy = a.bob(0.9f)
    b.spike(fur, 20, 32, 18, 24, 4); b.set(18, 23, OUTLINE); b.set(18, 22, OUTLINE)
    b.spike(fur, 26, 31, 28, 23, 4); b.set(28, 22, OUTLINE); b.set(28, 21, OUTLINE)
    b.blob(23, 35, 6.5, 5, fur)
    b.poly(white, 18, 37, 22, 41, 26, 41, 28, 37)
    b.blob(18, 37, 2.5, 1.8, white)
    b.set(16, 37, OUTLINE)
    b.eye(21, 34, bolt, a.blink(), big = true)
    b.dy = 0
    for (i in 0 until 4) {
        val ph = (a.t * 0.9f + i * 0.25f) % 1f
        val y = 30 + i * 5
        for (k in 0 until 4) fx.dot(60 - ph * 50f + k, y + sin(ph * 6f) * 1.5f, white.copy(alpha = (1f - ph) * 0.8f))
    }
}

private val tempestSovereign: Painter = { b, fx, a ->
    val body = col(0xFF3B4A7A); val chest = col(0xFFC9D4F0); val gold = col(0xFFE8C04E); val bolt = col(0xFFFFF27A)
    val f = a.wave(0.8f) * 4f
    b.poly(body.dk(0.35f), 38, 24, 58, 4 + f, 62, 10 + f, 46, 32)
    for (k in 0 until 6) {
        val px = 58f - k * 2.4f; val py = 6f + f + k * 4.2f
        b.spike(if (k % 2 == 0) body.dk(0.3f) else body.dk(0.45f), px - 2, py, px + 5 - k * 0.3f, py + 6 + k * 0.4f, 3)
    }
    b.poly(body, 36, 44, 46, 54, 40, 54, 34, 50, 30, 54, 32, 44)
    b.blob(34, 34, 10, 12, body)
    b.blob(30, 37, 6, 8, chest)
    for (y in 32 until 44 step 3) b.line(chest.dk(0.2f), 27, y, 32, y + 1)
    b.tube(gold.dk(0.2f), 31, 45, 1.5, 30, 51, 1.2); b.tube(gold.dk(0.2f), 37, 45, 1.5, 38, 51, 1.2)
    b.rect(27, 52, 31, 52, gold); b.rect(36, 52, 40, 52, gold)
    for (k in 0 until 7) {
        val fk = k / 6f
        val px = 30f - fk * 25f; val py = 27f - fk * 18f - f * fk
        b.spike(if (k % 2 == 0) body.lt(0.12f) else body, px, py, px + 1 + fk * 2, py + 12 - fk * 2, 4)
    }
    b.tube(body.lt(0.2f), 31, 27, 2.4, 18, 18 - f * 0.5f, 1.8, 5, 9 - f, 1.2)
    b.line(gold.dk(0.1f), 29, 26, 6, 9 - f)
    b.dy = a.bob(0.6f)
    b.spike(bolt, 28, 16, 36, 6, 3); b.spike(bolt, 26, 15, 29, 4, 3); b.spike(bolt, 30, 17, 40, 12, 2)
    b.blob(25, 20, 6, 5, body)
    b.poly(gold, 20, 18, 13, 22, 20, 24)
    b.line(gold.dk(0.4f), 15, 22, 20, 22)
    b.eye(23, 19, col(0xFF7FE8FF), a.blink(), big = true)
    b.dy = 0
    fx.glow(32, 8, 8, bolt.copy(alpha = 0.18f + 0.15f * a.pulse(2f)))
    if (((a.t * 0.7f) % 1f) < 0.16f) {
        var x = 50f; var y = 2f
        repeat(7) { val nx = x + if (it % 2 == 0) -2f else 1f; val ny = y + 3f; fx.dot(nx, ny, bolt); fx.dot(nx, ny + 1, Color.White); x = nx; y = ny }
    }
}

private val mossTortoise: Painter = { b, fx, a ->
    val shell = col(0xFF5E7A3A); val moss = col(0xFF8EC25A); val skin = col(0xFF9DB37A)
    b.blob(28, 49, 2.5, 2.5, skin.dk(0.2f)); b.blob(41, 49, 2.5, 2.5, skin.dk(0.2f))
    b.blob(46, 46, 2, 1.5, skin)
    b.blob(34, 42, 11, 7.5, shell)
    b.rect(24, 47, 44, 47, col(0xFFC9B060))
    listOf(29 to 41, 35 to 38, 39 to 43, 32 to 45).forEach { (x, y) ->
        b.line(shell.dk(0.4f), x - 2, y, x + 2, y); b.line(shell.dk(0.4f), x - 2, y, x - 3, y - 2); b.line(shell.dk(0.4f), x + 2, y, x + 3, y - 2)
    }
    listOf(28 to 37, 31 to 36, 36 to 35, 40 to 37, 33 to 38).forEach { (x, y) -> b.set(x, y, moss); b.set(x + 1, y, moss.lt(0.3f)) }
    val s = a.wave(0.5f)
    b.line(col(0xFF6B8E3A), 34, 34, 34, 30)
    b.blob(32 + s * 0.5f, 30, 2, 1.2, moss); b.blob(36 + s * 0.5f, 29, 2, 1.2, moss)
    b.blob(27, 50, 2.5, 2.5, skin); b.blob(40, 50, 2.5, 2.5, skin)
    b.dy = a.bob(0.5f)
    b.blob(21, 44, 4.5, 3.8, skin)
    b.set(17, 45, OUTLINE)
    b.eye(19, 43, OUTLINE, a.blink())
    b.set(19, 42, Color.White)
    b.dy = 0
}

private val runeStag: Painter = { b, fx, a ->
    val hide = col(0xFF7A5A3A); val belly = col(0xFFD9C49A); val rune = col(0xFF7CF0A0)
    b.tube(hide.dk(0.25f), 44, 40, 2, 46, 51, 1.4); b.tube(hide.dk(0.25f), 30, 40, 2, 30, 51, 1.4)
    b.blob(37, 36, 11, 6.5, hide)
    b.blob(35, 40, 7, 2.5, belly, shade = false)
    b.blob(48, 33, 1.5, 1.5, Color.White, shade = false)
    listOf(33 to 32, 38 to 31, 43 to 33).forEach { (x, y) -> b.set(x, y, belly.lt(0.3f)) }
    val glow = 0.4f + 0.5f * a.pulse(0.7f)
    b.line(rune, 36, 35, 38, 33); b.line(rune, 38, 33, 40, 35); b.line(rune, 38, 33, 38, 37)
    fx.glow(38, 35, 4, rune.copy(alpha = glow * 0.35f))
    b.tube(hide, 41, 40, 2, 42, 52, 1.4); b.tube(hide, 28, 40, 2, 27, 52, 1.4)
    listOf(26, 27, 41, 42, 45, 46, 29, 30).forEach { b.set(it, 52, OUTLINE.lt(0.15f)) }
    b.dy = a.bob(0.6f)
    b.tube(hide, 29, 33, 3.5, 24, 24, 3)
    b.blob(21, 22, 4.5, 3.5, hide)
    b.blob(17, 24, 2.8, 2, hide.lt(0.1f)); b.set(15, 24, OUTLINE)
    b.spike(hide, 24, 19, 28, 17, 2)
    b.eye(20, 21, OUTLINE, a.blink()); b.set(20, 20, Color.White)
    listOf(
        intArrayOf(21, 18, 19, 9), intArrayOf(20, 12, 15, 9), intArrayOf(19, 9, 16, 4), intArrayOf(20, 13, 22, 7),
        intArrayOf(23, 18, 27, 8), intArrayOf(25, 13, 31, 11), intArrayOf(27, 8, 31, 4), intArrayOf(27, 9, 25, 4),
    ).forEach { (x0, y0, x1, y1) -> b.line(rune.dk(0.15f), x0, y0, x1, y1) }
    listOf(19 to 9, 16 to 4, 22 to 7, 31 to 11, 31 to 4, 25 to 4, 15 to 9).forEach { (x, y) -> fx.dot(x, y, rune.lt(0.5f)) }
    fx.glow(23, 9, 9, rune.copy(alpha = glow * 0.25f))
    b.dy = 0
}

private val worldrootLeviathan: Painter = { b, fx, a ->
    val bark = col(0xFF5A4A32); val skin = col(0xFF4E6E5A); val leaf = col(0xFF4E9E4A); val gold = col(0xFFFFD86A)
    b.tube(skin.dk(0.25f), 26, 42, 4, 25, 52, 3.5); b.tube(skin.dk(0.25f), 52, 42, 4, 54, 52, 3.5)
    b.tube(skin, 54, 36, 4, 61, 42, 2.5, 63, 46, 1.2)
    b.blob(34, 40, 19, 7, skin)
    b.blob(35, 31, 20, 12, bark)
    for (i in 0 until 6) b.line(bark.dk(0.4f), 20 + i * 6, 24 + abs(i - 2.5f).toInt(), 18 + i * 6, 38)
    b.line(leaf.dk(0.2f), 16, 36, 54, 36)
    listOf(20 to 27, 26 to 22, 44 to 22, 50 to 28).forEach { (x, y) -> b.blob(x, y, 2.5, 1.5, leaf.dk(0.15f)) }
    val sw = a.wave(0.4f)
    b.rect(33, 9, 36, 22, col(0xFF6B4A2A)); b.line(col(0xFF4A321C), 33, 9, 33, 22)
    b.line(col(0xFF6B4A2A), 36, 14, 41, 10); b.line(col(0xFF6B4A2A), 33, 15, 28, 11)
    b.blob(27 + sw * 0.5f, 9, 7, 5, leaf); b.blob(41 + sw * 0.5f, 8, 8, 5, leaf); b.blob(34 + sw, 3, 7, 4, leaf.lt(0.1f))
    listOf(25 to 9, 40 to 6, 44 to 10, 34 to 4).forEach { (x, y) -> b.set(x, y, gold); fx.glow(x, y, 2, gold.copy(alpha = 0.3f)) }
    b.tube(skin, 20, 42, 4.5, 18, 52, 4); b.tube(skin, 46, 42, 4.5, 48, 52, 4)
    listOf(14 to 53, 22 to 53, 44 to 53, 52 to 53).forEach { (x, y) -> b.line(bark, x, y, x + if (x < 30) -2 else 2, y - 1) }
    b.dy = a.bob(0.4f)
    b.blob(12, 37, 8, 6, skin)
    b.blob(7, 40, 4, 2.5, skin.lt(0.15f))
    b.rect(4, 41, 10, 41, OUTLINE)
    b.eye(9, 35, gold, a.blink(5f), big = true)
    b.dy = 0
    for (i in 0 until 5) {
        val ph = (a.t * 0.3f + i * 0.2f) % 1f
        if (sin(a.t * 3f + i) > 0f) fx.dot(10 + i * 11 + sin(a.t + i) * 3f, 20 - ph * 12f, col(0xFFE6FF8A))
    }
}

private val sparkKit: Painter = { b, fx, a ->
    val fur = col(0xFFE0703A); val cream = col(0xFFF6E6C8); val flame = col(0xFFFFB347)
    val sw = a.wave(1.1f)
    b.tube(fur, 40, 45, 2.5, 45, 41 + sw * 0.5f, 3, 47, 36 + sw, 2.4)
    b.blob(47, 34 + sw, 2.2, 2.2, cream)
    b.blob(47, 31 + sw, 2.2, 2.8, flame); b.blob(47, 29 + sw, 1.2, 1.8, col(0xFFFFF0A0), shade = false)
    fx.rise(a.t, 45, 29 + sw, 4, flame, n = 4, height = 8f, speed = 0.9f)
    fx.glow(47, 31 + sw, 5, flame.copy(alpha = 0.3f))
    b.blob(37, 50, 2, 2, fur.dk(0.2f))
    b.blob(34, 45, 7, 5, fur)
    b.blob(32, 48, 4, 2, cream, shade = false)
    b.blob(29, 50, 2, 2, fur); b.set(28, 52, OUTLINE.lt(0.2f))
    b.dy = a.bob(1.0f)
    b.spike(fur, 23, 38, 21, 31, 4); b.set(21, 33, OUTLINE)
    b.spike(fur, 29, 37, 31, 31, 4); b.set(30, 33, OUTLINE)
    b.blob(26, 40, 6, 4.8, fur)
    b.blob(22, 42, 3, 2, cream)
    b.set(19, 42, OUTLINE)
    b.eye(24, 39, OUTLINE, a.blink(3.6f), big = true)
    b.dy = 0
}

private val pyreWyvern: Painter = { b, fx, a ->
    val hide = col(0xFFB3352A); val belly = col(0xFFF0A050); val wing = col(0xFF7A1E1E); val horn = col(0xFFE8DCC0)
    val f = a.wave(0.9f) * 3f
    b.poly(wing.dk(0.3f), 38, 30, 54, 8 + f, 62, 16 + f, 58, 26 + f, 50, 34)
    b.tube(hide, 46, 40, 3, 54, 44, 2.2, 60, 42, 1.4)
    b.poly(hide.dk(0.2f), 59, 42, 63, 38, 63, 45)
    b.tube(hide.dk(0.2f), 40, 42, 2.5, 42, 51, 2)
    b.blob(37, 37, 10, 7, hide)
    b.blob(34, 40, 6, 3.5, belly)
    for (x in 30..38 step 3) b.line(belly.dk(0.25f), x, 38, x, 43)
    b.tube(hide, 34, 42, 2.6, 33, 52, 2)
    b.rect(31, 52, 35, 52, horn)
    b.poly(wing, 34, 30, 14, 6 - f, 8, 14 - f, 12, 22 - f, 28, 36)
    listOf(14 to 6, 8 to 14, 12 to 22).forEach { (x, y) -> b.line(hide.dk(0.4f), 33, 31, x, y - f) }
    b.dy = a.bob(0.7f)
    b.tube(hide, 30, 33, 3.5, 23, 25, 3)
    b.spike(horn, 21, 18, 28, 13, 3); b.spike(horn, 18, 18, 22, 12, 2)
    b.blob(18, 21, 6, 4, hide)
    b.blob(13, 23, 3.5, 2, hide.lt(0.1f))
    b.rect(10, 24, 16, 24, OUTLINE); b.set(11, 25, horn); b.set(14, 25, horn)
    b.eye(17, 20, col(0xFFFFE36A), a.blink(), big = true)
    b.dy = 0
    val breath = (a.t * 0.5f) % 1f
    if (breath < 0.35f) for (i in 0 until 9) {
        val p = (breath / 0.35f + i * 0.11f) % 1f
        fx.dot(10 - p * 10f, 24 + sin(i * 2.1f) * p * 3f, (if (i % 3 == 0) Color.White else if (i % 2 == 0) col(0xFFFFE36A) else col(0xFFFF7A2A)).copy(alpha = 1f - p))
    } else fx.rise(a.t, 9, 22, 3, col(0xFF6A6A6A), n = 2, height = 6f)
}

private val solarisDragon: Painter = { b, fx, a ->
    val scale = col(0xFFE0A93A); val belly = col(0xFFFFEAB8); val wing = col(0xFFFF8A2A); val horn = col(0xFFFFF6D8)
    val f = a.wave(0.6f) * 3f
    fx.glow(14, 14, 16, col(0xFFFFD86A).copy(alpha = 0.22f + 0.12f * a.pulse(0.6f)))
    for (i in 0 until 12) {
        val ang = i / 12f * 6.283f + a.t * 0.3f
        fx.dot(14 + cos(ang) * 12f, 15 + sin(ang) * 12f, col(0xFFFFE89A).copy(alpha = 0.8f))
    }
    b.poly(wing.dk(0.25f), 40, 26, 54, 2 + f, 63, 6 + f, 62, 18 + f, 52, 30)
    b.tube(scale, 48, 38, 4, 56, 44, 3, 62, 40, 1.8)
    b.blob(62, 37, 1.8, 2.8, col(0xFFFFE36A)); fx.rise(a.t, 61, 35, 3, col(0xFFFFB347), n = 3, height = 7f)
    b.tube(scale.dk(0.2f), 30, 42, 3.5, 30, 51, 3); b.tube(scale.dk(0.2f), 46, 42, 3.5, 48, 51, 3)
    b.blob(38, 36, 14, 9, scale)
    b.blob(34, 40, 9, 4, belly)
    for (x in 27..41 step 3) b.line(belly.dk(0.22f), x, 37, x, 44)
    b.tube(scale, 26, 42, 3.6, 25, 52, 3); b.tube(scale, 42, 42, 3.6, 43, 52, 3)
    listOf(22, 24, 26, 40, 42, 44).forEach { b.set(it, 53, horn) }
    b.poly(wing, 36, 28, 22, 0 - f, 12, 4 - f, 14, 14 - f, 28, 34)
    listOf(22 to 0, 12 to 4, 14 to 14).forEach { (x, y) -> b.line(scale.dk(0.35f), 35, 29, x, y - f) }
    b.dy = a.bob(0.5f)
    b.tube(scale, 29, 32, 5, 20, 21, 4)
    for (i in 0 until 4) b.spike(wing, 22 + i * 2, 18 + i * 3, 28 + i * 2, 15 + i * 3, 2)
    b.spike(horn, 18, 12, 27, 3, 3); b.spike(horn, 15, 12, 19, 4, 2)
    b.blob(15, 16, 6.5, 5, scale)
    b.blob(9, 18, 4.5, 2.6, scale.lt(0.1f))
    b.rect(5, 20, 12, 20, OUTLINE); b.set(6, 21, horn); b.set(9, 21, horn)
    b.eye(14, 15, Color.White, a.blink(), big = true)
    fx.glow(14.5f, 15, 3, Color.White.copy(alpha = 0.4f))
    b.dy = 0
}

// =============================================================================================
// Bosses
// =============================================================================================

private val ashWhelp: Painter = { b, fx, a ->
    val hide = col(0xFF5A5560); val ember = col(0xFFFF8A3A); val shell = col(0xFFE6DCC4)
    val f = a.wave(1.4f) * 2f
    b.poly(hide.dk(0.3f), 38, 32, 48, 22 + f, 50, 30, 44, 36)
    b.tube(hide, 42, 44, 2.5, 50, 46, 1.8, 54, 42, 1)
    b.blob(34, 39, 9, 8, hide)
    b.blob(32, 42, 5, 4, col(0xFFA89A86))
    listOf(30 to 36, 36 to 38, 38 to 34, 33 to 40).forEach { (x, y) -> b.set(x, y, ember); b.set(x + 1, y + 1, ember.dk(0.3f)) }
    b.poly(hide.lt(0.1f), 30, 33, 20, 24 - f, 22, 32)
    b.dy = a.bob(0.8f)
    b.blob(25, 30, 6.5, 5.5, hide)
    b.blob(20, 32, 3, 2, hide.lt(0.15f)); b.set(17, 32, OUTLINE); b.rect(18, 34, 22, 34, OUTLINE)
    b.spike(shell, 26, 25, 30, 20, 2); b.spike(shell, 23, 25, 23, 20, 2)
    b.eye(23, 29, ember, a.blink(), big = true)
    fx.glow(23.5f, 29, 3, ember.copy(alpha = 0.35f))
    b.dy = 0
    b.poly(shell, 21, 44, 24, 53, 46, 53, 48, 44, 45, 47, 42, 43, 38, 47, 34, 44, 30, 47, 26, 43)
    b.line(shell.dk(0.35f), 28, 48, 31, 52); b.line(shell.dk(0.35f), 40, 47, 38, 52)
    b.poly(shell.dk(0.1f), 50, 50, 54, 48, 56, 52, 52, 53)
    fx.rise(a.t, 24, 30, 20, ember, n = 7, height = 20f, speed = 0.5f)
    fx.rise(a.t * 0.7f, 18, 28, 5, col(0xFF8A8A90), n = 3, height = 12f, speed = 0.4f)
}

private val cinderHound: Painter = { b, fx, a ->
    val hide = col(0xFF2A2426); val lava = col(0xFFFF6A2A); val fire = col(0xFFFFC04A)
    val glow = 0.5f + 0.5f * a.pulse(1.2f)
    val crack = lava.mixWith(fire, glow)
    b.tube(hide, 47, 34, 2.5, 54, 30, 2.5, 58, 24, 2)
    b.blob(58, 21, 2.5, 3.5, lava); b.blob(58, 19, 1.5, 2.5, fire, shade = false)
    b.tube(hide.lt(0.06f), 44, 38, 3, 47, 51, 2.2); b.tube(hide.lt(0.06f), 30, 38, 3, 31, 51, 2.2)
    b.blob(38, 35, 13, 7, hide)
    b.line(crack, 30, 33, 34, 36); b.line(crack, 34, 36, 39, 34); b.line(crack, 39, 34, 44, 37); b.line(crack, 42, 31, 46, 33)
    b.line(crack, 33, 39, 37, 41)
    for (i in 0 until 6) b.spike(if (i % 2 == 0) lava else fire, 28 + i * 3.5f, 30, 26 + i * 3.5f + a.wave(2f, i * 0.2f), 22 - (i % 3) * 2, 3)
    b.tube(hide, 41, 39, 3, 42, 52, 2.2); b.tube(hide, 27, 38, 3, 25, 52, 2.2)
    listOf(23, 25, 27, 40, 42, 44).forEach { b.set(it, 53, col(0xFFDDD6C8)) }
    b.dy = a.bob(0.9f)
    b.spike(hide, 22, 25, 30, 20, 4)
    b.blob(20, 30, 7, 5.5, hide)
    b.blob(12, 33, 6, 2.8, hide.lt(0.05f))
    b.rect(7, 35, 16, 35, OUTLINE); b.rect(8, 36, 15, 37, col(0xFF4A0A0A))
    listOf(8, 10, 12, 14).forEach { b.set(it, 36, Color.White) }
    b.set(6, 33, OUTLINE)
    b.eye(17, 29, fire, a.blink(), big = true)
    fx.glow(17.5f, 29, 4, lava.copy(alpha = 0.45f))
    b.dy = 0
    fx.rise(a.t, 26, 22, 20, fire, n = 8, height = 16f, speed = 0.8f)
    fx.glow(38, 34, 14, lava.copy(alpha = 0.12f * glow))
}

private val forgeWarden: Painter = { b, fx, a ->
    val iron = col(0xFF5A5E68); val brass = col(0xFFB07A3A); val heat = col(0xFFFF8A2A); val coal = col(0xFF2A2A30)
    b.rect(43, 4, 46, 16, coal); b.rect(42, 3, 47, 4, iron)
    fx.rise(a.t, 43, 2, 4, col(0xFF8A8A90), n = 4, height = 12f, speed = 0.35f)
    b.tube(iron.dk(0.2f), 28, 40, 4, 27, 52, 4); b.tube(iron.dk(0.2f), 40, 40, 4, 41, 52, 4)
    b.rect(22, 51, 31, 53, coal); b.rect(37, 51, 46, 53, coal)
    b.blob(34, 30, 13, 12, iron)
    listOf(24 to 22, 44 to 22, 24 to 38, 44 to 38).forEach { (x, y) -> b.set(x, y, brass.lt(0.3f)) }
    val glow = 0.5f + 0.5f * a.pulse(1.5f)
    b.rect(28, 26, 40, 36, coal)
    for (x in 29..39 step 2) b.rect(x, 27, x, 35, heat.mixWith(col(0xFFFFE07A), glow * 0.7f))
    b.rect(27, 25, 41, 25, brass); b.rect(27, 37, 41, 37, brass)
    fx.glow(34, 31, 10, heat.copy(alpha = 0.3f + 0.2f * glow))
    b.blob(20, 21, 6, 5, brass); b.blob(48, 21, 6, 5, brass)
    val sw = a.wave(0.5f) * 1.5f
    b.tube(iron, 18, 24, 3.5, 14, 34 + sw, 3)
    b.tube(iron, 50, 24, 3.5, 54, 34 - sw, 3)
    b.rect(8, 36 + sw, 20, 39 + sw, iron.lt(0.1f)); b.rect(10, 40 + sw, 18, 43 + sw, iron.dk(0.2f)); b.rect(6, 36 + sw, 8, 37 + sw, iron.lt(0.25f))
    b.rect(48, 36 - sw, 60, 39 - sw, iron.lt(0.1f)); b.rect(50, 40 - sw, 58, 43 - sw, iron.dk(0.2f)); b.rect(60, 36 - sw, 62, 37 - sw, iron.lt(0.25f))
    b.dy = a.bob(0.5f)
    b.blob(34, 14, 6, 5.5, iron)
    b.spike(brass, 30, 10, 27, 4, 3); b.spike(brass, 38, 10, 41, 4, 3)
    b.rect(30, 14, 38, 15, coal)
    b.rect(31, 14, 37, 14, if (a.blink(3f)) heat.dk(0.4f) else col(0xFFFFE07A))
    fx.glow(34, 14, 5, heat.copy(alpha = 0.35f))
    b.dy = 0
}

private val mistStalker: Painter = { b, fx, a ->
    val robe = col(0xFF4A5A6E); val pale = col(0xFFBCE6F0); val void = col(0xFF0E1018)
    val w = a.wave(0.8f)
    fx.glow(32, 30, 22, pale.copy(alpha = 0.08f))
    val hem = (0..8).flatMap { i -> listOf(18 + i * 3.5f, 50 + sin(a.t * 3f + i) * 2f) }
    b.poly(robe.copy(alpha = 0.9f), 26, 16, 38, 16, 46 + w, 30, 48, 50, *hem.reversed().chunked(2).flatMap { listOf(it[1], it[0]) }.toTypedArray(), 16, 50, 20 - w, 30)
    for (i in 0 until 4) b.line(robe.dk(0.3f), 28 + i * 3, 22, 24 + i * 5, 48)
    b.tube(robe, 22, 22, 2.5, 14, 30 + w, 2, 10, 36 + w, 1.6)
    for (k in 0 until 3) b.line(pale, 10, 36 + w, 4 + k, 42 + w + k)
    b.tube(robe.dk(0.15f), 42, 22, 2.5, 50, 32 - w, 2, 52, 38 - w, 1.6)
    for (k in 0 until 3) b.line(pale.dk(0.2f), 52, 38 - w, 56 + k, 44 - w + k)
    b.dy = a.bob(0.4f)
    b.blob(32, 15, 8, 8, robe.dk(0.1f))
    b.spike(robe.dk(0.1f), 34, 9, 40, 2, 4)
    b.blob(31, 17, 5, 5, void, shade = false)
    if (!a.blink(3.1f)) { b.rect(28, 16, 29, 16, pale); b.rect(33, 16, 34, 16, pale) }
    fx.glow(31, 16, 6, pale.copy(alpha = 0.3f))
    b.dy = 0
    for (i in 0 until 12) {
        val ph = (a.t * 0.2f + i * 0.083f) % 1f
        fx.dot(4 + ph * 56f, 40 + sin(i * 1.7f + a.t) * 8f, pale.copy(alpha = 0.25f + 0.25f * sin(ph * 3.14f)))
    }
}

private val obsidianColossus: Painter = { b, fx, a ->
    val glass = col(0xFF2A2436); val sheen = col(0xFF5A4A7A); val magma = col(0xFFB34AE0)
    val glow = 0.4f + 0.6f * a.pulse(0.6f)
    val vein = magma.mixWith(col(0xFFFFB0FF), glow * 0.5f)
    b.tube(glass, 24, 38, 5, 23, 52, 5); b.tube(glass, 42, 38, 5, 43, 52, 5)
    b.poly(glass, 14, 16, 50, 16, 46, 40, 18, 40)
    b.poly(sheen, 18, 18, 30, 18, 24, 30)
    b.poly(sheen.dk(0.2f), 36, 22, 46, 18, 44, 30)
    b.line(vein, 22, 22, 28, 30); b.line(vein, 28, 30, 26, 38); b.line(vein, 28, 30, 36, 32); b.line(vein, 36, 32, 42, 26); b.line(vein, 36, 32, 38, 39)
    fx.glow(32, 30, 12, magma.copy(alpha = 0.25f * glow))
    b.spike(sheen, 16, 16, 10, 6, 5); b.spike(sheen, 20, 15, 18, 6, 4)
    b.spike(sheen, 48, 16, 54, 6, 5); b.spike(sheen, 44, 15, 46, 6, 4)
    val sw = a.wave(0.4f)
    b.tube(glass, 14, 20, 5, 9, 32 + sw, 4.5, 8, 40 + sw, 4)
    b.poly(glass.lt(0.08f), 2, 40 + sw, 14, 38 + sw, 15, 48 + sw, 3, 50 + sw)
    b.line(vein, 4, 44 + sw, 13, 43 + sw)
    b.tube(glass, 50, 20, 5, 55, 32 - sw, 4.5, 56, 40 - sw, 4)
    b.poly(glass.lt(0.08f), 50, 38 - sw, 62, 40 - sw, 61, 50 - sw, 49, 48 - sw)
    b.line(vein, 51, 43 - sw, 60, 44 - sw)
    b.rect(17, 52, 29, 53, glass.dk(0.2f)); b.rect(37, 52, 49, 53, glass.dk(0.2f))
    b.dy = a.bob(0.35f)
    b.poly(glass, 26, 4, 38, 4, 40, 14, 24, 14)
    b.poly(sheen, 27, 5, 32, 5, 28, 10)
    b.rect(27, 9, 37, 9, OUTLINE)
    if (!a.blink(5f)) { b.rect(28, 9, 29, 9, vein); b.rect(35, 9, 36, 9, vein) }
    fx.glow(32, 9, 6, magma.copy(alpha = 0.35f))
    b.dy = 0
    fx.rise(a.t, 10, 12, 44, magma.lt(0.3f), n = 6, height = 10f, speed = 0.3f)
}

private val archonOfTheDeep: Painter = { b, fx, a ->
    val skin = col(0xFF3A2A6A); val teal = col(0xFF3AA8A0); val gold = col(0xFFE8C04E); val abyss = col(0xFF7FFFE8)
    fx.glow(32, 22, 24, teal.copy(alpha = 0.12f))
    for (i in 0 until 6) {
        val x0 = 22 + i * 4f
        val ph = a.t * 1.4f + i * 0.9f
        val dir = if (i < 3) -1 else 1
        b.tube(
            if (i % 2 == 0) skin else skin.lt(0.12f),
            x0, 32, 2.6, x0 + dir * (3 + i % 3) + sin(ph) * 2f, 42, 2, x0 + dir * (6 + i * 1.5f) + sin(ph + 1f) * 3f, 50, 1.4,
            x0 + dir * (10 + i * 1.5f) + sin(ph + 2f) * 2f, 48 + cos(ph) * 2f, 0.8,
        )
    }
    b.poly(skin.dk(0.2f), 22, 26, 42, 26, 46, 40, 18, 40)
    b.poly(gold, 22, 30, 42, 30, 41, 32, 23, 32, shade = false)
    b.rect(31, 32, 33, 40, gold.dk(0.2f))
    b.dy = a.bob(0.4f)
    b.blob(32, 16, 10, 12, skin)
    listOf(27 to 10, 37 to 11, 30 to 22, 35 to 21).forEach { (x, y) -> b.set(x, y, teal.lt(0.2f)) }
    for (i in 0 until 5) b.spike(gold, 25 + i * 3.5f, 6 - abs(i - 2) * 0.5f, 24 + i * 3.5f + (i - 2) * 0.6f, -1 + abs(i - 2) * 1.5f, 3)
    b.rect(23, 5, 41, 6, gold)
    b.set(32, 5, abyss)
    if (!a.blink(4.4f)) {
        b.eye(27, 15, abyss, big = true); b.eye(36, 15, abyss, big = true); b.eye(32, 11, abyss)
    } else { b.rect(27, 15, 28, 15, OUTLINE); b.rect(36, 15, 37, 15, OUTLINE) }
    fx.glow(32, 14, 8, abyss.copy(alpha = 0.25f + 0.1f * a.pulse(1f)))
    b.dy = 0
    for (i in 0 until 7) {
        val ph = (a.t * 0.35f + i * 0.143f) % 1f
        val x = 8 + (i * 9) % 48 + sin(a.t * 2f + i) * 1.5f
        fx.dot(x, 52 - ph * 50f, abyss.copy(alpha = (1f - ph) * 0.7f))
    }
}

private val rimeYeti: Painter = { b, fx, a ->
    val fur = col(0xFFE6EEF5); val face = col(0xFF6A8AA8); val ice = col(0xFF9FE6FF)
    b.tube(fur.dk(0.1f), 25, 42, 5, 24, 52, 4.5); b.tube(fur.dk(0.1f), 39, 42, 5, 40, 52, 4.5)
    b.rect(19, 52, 29, 53, face.dk(0.3f)); b.rect(35, 52, 45, 53, face.dk(0.3f))
    b.blob(32, 32, 14, 15, fur)
    for (i in 0 until 14) { val x = 21 + (i * 5) % 22; val y = 22 + (i * 7) % 22; b.line(fur.dk(0.18f), x, y, x - 1, y + 2) }
    b.blob(32, 37, 7, 7, fur.dk(0.06f))
    val sw = a.wave(0.5f) * 1.5f
    b.tube(fur, 19, 24, 5, 13, 36 + sw, 4.2, 11, 46 + sw, 3.6)
    b.blob(11, 48 + sw, 4, 3, face)
    b.spike(ice, 14, 34 + sw, 15, 42 + sw, 2); b.spike(ice, 11, 38 + sw, 11, 44 + sw, 2)
    b.tube(fur.dk(0.08f), 45, 24, 5, 51, 36 - sw, 4.2, 53, 46 - sw, 3.6)
    b.blob(53, 48 - sw, 4, 3, face.dk(0.1f))
    b.spike(ice, 50, 34 - sw, 49, 42 - sw, 2)
    b.dy = a.bob(0.5f)
    b.blob(30, 18, 9, 8, fur)
    b.tube(ice, 24, 13, 2, 19, 9, 1.6, 17, 3, 1)
    b.tube(ice, 36, 12, 2, 41, 8, 1.6, 43, 2, 1)
    b.blob(28, 21, 6, 4.5, face)
    b.rect(24, 23, 32, 24, OUTLINE)
    b.spike(Color.White, 25, 24, 24, 20, 2); b.spike(Color.White, 31, 24, 32, 20, 2)
    b.eye(25, 19, ice, a.blink()); b.eye(30, 19, ice, a.blink())
    fx.glow(28, 19, 5, ice.copy(alpha = 0.25f))
    b.dy = 0
    fx.drift(a.t, Color.White.copy(alpha = 0.85f), n = 14)
}

private val duneScorpion: Painter = { b, fx, a ->
    val shell = col(0xFFC9963A); val band = col(0xFF7A5420); val venom = col(0xFF8CFF6A)
    val sw = a.wave(0.7f) * 1.5f
    for (i in 0 until 4) {
        val x = 30 + i * 4
        b.line(band, x, 44, x - 3, 50); b.line(band, x - 3, 50, x - 4, 53)
        b.line(band.lt(0.2f), x + 1, 45, x + 3, 50); b.line(band.lt(0.2f), x + 3, 50, x + 4, 53)
    }
    val tail = listOf(48f to 42f, 54f to 37f, 57f to 30f, 56f to 22f + sw, 51f to 15f + sw, 44f to 12f + sw * 1.5f)
    tail.forEachIndexed { i, (x, y) -> b.blob(x, y, 4.2f - i * 0.4f, 3.6f - i * 0.3f, if (i % 2 == 0) shell else shell.dk(0.12f)) }
    b.spike(shell.lt(0.2f), 42, 12 + sw * 1.5f, 36, 18 + sw * 1.5f, 3)
    b.set(36, 18 + sw * 1.5f, venom)
    val drip = (a.t * 0.6f) % 1f
    fx.dot(36, 19 + sw * 1.5f + drip * 8f, venom.copy(alpha = 1f - drip))
    fx.glow(37, 17 + sw * 1.5f, 4, venom.copy(alpha = 0.35f))
    for (i in 0 until 4) { b.blob(32 + i * 5, 43, 5, 4.2, shell); b.line(band, 35 + i * 5, 40, 35 + i * 5, 46) }
    b.blob(23, 42, 7, 5, shell)
    b.line(band, 18, 40, 28, 40)
    listOf(19 to 39, 21 to 38, 23 to 38).forEach { (x, y) -> b.set(x, y, OUTLINE); b.set(x, y - 1, Color.White.copy(alpha = 0.6f)) }
    val pinch = if (sin(a.t * 2.2f) > 0.6f) 2f else 0f
    b.tube(shell.dk(0.1f), 20, 44, 2.5, 13, 47, 2.2)
    b.blob(7, 47, 6, 4.5, shell.dk(0.1f))
    b.spike(shell.dk(0.1f), 4, 45, -1, 43 + pinch, 3); b.spike(shell.dk(0.1f), 4, 49, -1, 51 - pinch, 3)
    b.tube(shell, 19, 39, 2.5, 13, 34 + sw, 2.2)
    b.blob(8, 32 + sw, 6.5, 5, shell)
    b.spike(shell, 4, 30 + sw, -1, 27 + sw + pinch, 3); b.spike(shell, 4, 34 + sw, 0, 36 + sw - pinch, 3)
    fx.drift(a.t * 0.8f, col(0xFFE8C88A).copy(alpha = 0.6f), n = 8, fall = false)
}

private val glacialWyrm: Painter = { b, fx, a ->
    val scale = col(0xFF7FB7D8); val belly = col(0xFFDCF2FF); val ice = col(0xFFBFF4FF)
    val path = (0..70).map { i ->
        val f = i / 70f
        if (f < 0.6f) {
            val g = f / 0.6f
            val ang = 0.3f + g * 5.6f
            val rad = 15f - g * 6f
            Triple(38f + cos(ang) * rad, 44f + sin(ang) * rad * 0.42f + sin(a.t * 1.5f + g * 4f) * 0.6f, 1.6f + g * 3.4f)
        } else {
            val g = (f - 0.6f) / 0.4f
            val sx = 38f + cos(5.9f) * 9f; val sy = 44f + sin(5.9f) * 9f * 0.42f
            Triple(sx + (20f - sx) * g + sin(g * 5f + a.t * 1.2f) * 2.5f * (1f - g), sy + (16f - sy) * g, 5f - g * 1.2f)
        }
    }
    path.forEach { (x, y, r) -> b.blob(x, y, r, r, scale) }
    path.forEachIndexed { i, (x, y, r) -> if (i % 2 == 0) b.set(x - 1, y + r - 1, belly) }
    path.forEachIndexed { i, (x, y, r) -> if (i % 5 == 2 && i < 66 && i > 8) b.spike(ice, x, y - r + 1, x + 2, y - r - 2 - r * 0.6f, 3) }
    val (hx, hy0) = path.last().let { it.first to it.second }
    b.dy = a.bob(0.5f)
    val hy = hy0 - 2
    b.blob(hx - 3, hy, 6.5, 4.5, scale)
    b.blob(hx - 9, hy + 2, 4, 2.2, scale.lt(0.1f))
    b.rect(hx - 13, hy + 3, hx - 6, hy + 3, OUTLINE)
    b.spike(ice, hx - 1, hy - 4, hx + 6, hy - 11, 3); b.spike(ice, hx - 4, hy - 4, hx - 3, hy - 11, 2)
    b.eye((hx - 5).toInt(), (hy - 1).toInt(), Color.White, a.blink(), big = true)
    fx.glow(hx - 4.5f, hy - 1, 3, ice.copy(alpha = 0.5f))
    b.dy = 0
    val br = (a.t * 0.45f) % 1f
    if (br < 0.4f) for (i in 0 until 10) {
        val p = (br / 0.4f + i * 0.1f) % 1f
        fx.dot(hx - 13 - p * 12f, hy + 3 + sin(i * 1.9f) * p * 4f + 1, ice.copy(alpha = 1f - p))
    }
    fx.drift(a.t, Color.White.copy(alpha = 0.7f), n = 8)
}

private val starboundTitan: Painter = { b, fx, a ->
    val body = col(0xFF1E2246); val trim = col(0xFF8A6BE0); val star = col(0xFFFFF4C8); val gold = col(0xFFE8C04E)
    for (i in 0 until 26) {
        val ang = i / 26f * 6.283f
        b.set((32 + cos(ang) * 11f).toInt(), (9 + sin(ang) * 4f + a.bob(0.4f)).toInt(), gold)
    }
    b.tube(body, 26, 38, 4, 25, 52, 3.6); b.tube(body, 38, 38, 4, 39, 52, 3.6)
    b.rect(20, 52, 29, 53, trim.dk(0.2f)); b.rect(35, 52, 44, 53, trim.dk(0.2f))
    b.poly(body, 20, 18, 44, 18, 41, 40, 23, 40)
    b.blob(20, 19, 5, 4.5, trim.dk(0.25f)); b.blob(44, 19, 5, 4.5, trim.dk(0.25f))
    val sw = a.wave(0.4f)
    b.tube(body, 18, 22, 3.6, 13, 32 + sw, 3.2, 12, 40 + sw, 3)
    b.blob(12, 42 + sw, 3.5, 3, trim)
    b.tube(body, 46, 22, 3.6, 51, 32 - sw, 3.2, 52, 40 - sw, 3)
    b.blob(52, 42 - sw, 3.5, 3, trim)
    val stars = listOf(25 to 24, 30 to 21, 37 to 26, 33 to 31, 28 to 35, 38 to 35, 14 to 30, 50 to 30, 26 to 46, 39 to 47)
    stars.forEachIndexed { i, (x, y) -> b.set(x, y, if (((a.t * 1.5f + i * 0.37f) % 1f) < 0.65f) star else trim) }
    listOf(0 to 1, 1 to 3, 3 to 2, 3 to 4, 3 to 5).forEach { (i, j) -> val (x0, y0) = stars[i]; val (x1, y1) = stars[j]; b.line(trim.copy(alpha = 0.55f), x0, y0, x1, y1) }
    stars.take(6).forEach { (x, y) -> b.set(x, y, star) }
    fx.glow(32, 28, 14, trim.copy(alpha = 0.18f))
    b.dy = a.bob(0.4f)
    b.blob(32, 11, 5.5, 5.5, body)
    b.rect(28, 11, 36, 11, OUTLINE)
    if (!a.blink(4.6f)) { b.rect(29, 11, 30, 11, star); b.rect(34, 11, 35, 11, star) }
    fx.glow(32, 11, 5, star.copy(alpha = 0.35f))
    b.dy = 0
    for (k in 0 until 2) {
        val ang = a.t * (0.8f + k * 0.5f) + k * 3f
        fx.dot(32 + cos(ang) * (22f + k * 5), 28 + sin(ang) * 7f, if (k == 0) col(0xFF7FE8FF) else col(0xFFFF9A6A))
        fx.glow(32 + cos(ang) * (22f + k * 5), 28 + sin(ang) * 7f, 2, Color.White.copy(alpha = 0.3f))
    }
}

// =============================================================================================
// Delve skirmish foes
// =============================================================================================

private val ashenStalker: Painter = { b, fx, a ->
    val hide = col(0xFF6E6A6A); val bone = col(0xFFD8D0C0); val ember = col(0xFFFF7A3A)
    b.tube(hide, 48, 36, 2, 55, 40, 1.6, 60, 38, 1)
    b.tube(hide.dk(0.2f), 46, 38, 2.2, 49, 51, 1.6); b.tube(hide.dk(0.2f), 32, 36, 2.2, 32, 51, 1.6)
    b.blob(40, 33, 11, 6, hide)
    for (x in 35..45 step 3) b.line(hide.dk(0.4f), x, 34, x - 1, 38)
    for (i in 0 until 5) b.spike(bone, 33 + i * 3.5f, 28 + abs(i - 2) * 0.5f, 34 + i * 3.5f, 22 + abs(i - 2), 2)
    b.tube(hide, 43, 37, 2.2, 45, 52, 1.6); b.tube(hide, 29, 36, 2.2, 27, 52, 1.6)
    b.dy = a.bob(0.8f)
    b.blob(24, 34, 6, 5, hide)
    b.blob(17, 37, 5, 2.4, hide.lt(0.06f)); b.set(12, 37, OUTLINE)
    b.rect(13, 39, 20, 39, OUTLINE); b.set(15, 40, bone); b.set(18, 40, bone)
    b.spike(hide, 26, 30, 30, 24, 3)
    b.eye(21, 33, ember, a.blink(), big = true)
    fx.glow(21.5f, 33, 3, ember.copy(alpha = 0.4f))
    b.dy = 0
    fx.rise(a.t, 30, 24, 20, col(0xFF9A9A9A).copy(alpha = 0.7f), n = 4, height = 10f, speed = 0.3f)
}

private val ashImp: Painter = { b, fx, a ->
    val skin = col(0xFFA83A2E); val wing = col(0xFF5A1E1E); val horn = col(0xFFE8DCC0)
    val f = a.wave(2.2f) * 2f
    b.poly(wing, 34, 32, 46, 22 + f, 48, 32, 40, 36); b.poly(wing.dk(0.2f), 30, 32, 18, 22 + f, 16, 32, 24, 36)
    b.tube(skin, 37, 44, 1.2, 43, 46, 1, 46, 42, 0.8); b.spike(skin, 46, 42, 49, 39, 3)
    b.tube(skin.dk(0.2f), 30, 44, 1.8, 29, 51, 1.5); b.tube(skin.dk(0.2f), 35, 44, 1.8, 36, 51, 1.5)
    b.blob(32, 40, 5, 6, skin)
    b.line(col(0xFF5A3A22), 22, 52, 26, 22); b.spike(col(0xFF9AA0AA), 25, 26, 27, 18, 2); b.spike(col(0xFF9AA0AA), 24, 24, 23, 19, 2); b.spike(col(0xFF9AA0AA), 27, 24, 29, 20, 1.5)
    b.tube(skin, 28, 38, 1.6, 25, 36, 1.4)
    b.dy = a.bob(1.2f)
    b.blob(32, 30, 5.5, 4.8, skin)
    b.spike(horn, 29, 27, 26, 21, 2); b.spike(horn, 35, 27, 38, 21, 2)
    b.eye(30, 30, col(0xFFFFE36A), a.blink(2.8f)); b.eye(34, 30, col(0xFFFFE36A), a.blink(2.8f))
    b.rect(30, 33, 34, 33, OUTLINE); b.set(31, 34, Color.White)
    b.dy = 0
    fx.rise(a.t, 28, 40, 8, col(0xFFFF8A3A), n = 3, height = 10f)
}

private val cinderBeetle: Painter = { b, fx, a ->
    val shell = col(0xFF2E2A2A); val glow = col(0xFFFF8A2A)
    val g = 0.5f + 0.5f * a.pulse(1f)
    for (i in 0 until 3) {
        val x = 30 + i * 6; val st = a.bob(2f, i * 0.3f)
        b.line(shell.lt(0.15f), x, 46, x - 3, 50 + st); b.line(shell.lt(0.15f), x - 3, 50 + st, x - 4, 53)
    }
    b.blob(36, 41, 12, 7.5, shell)
    b.line(glow.mixWith(col(0xFFFFE07A), g), 36, 34, 37, 48)
    listOf(30 to 39, 42 to 38, 33 to 44, 44 to 43).forEach { (x, y) -> b.set(x, y, glow.dk(0.2f)) }
    fx.glow(36, 41, 8, glow.copy(alpha = 0.22f * g))
    b.blob(32, 36, 4, 2, shell.lt(0.4f), shade = false)
    b.dy = a.bob(0.6f)
    b.blob(23, 44, 5, 4, shell.lt(0.08f))
    b.spike(col(0xFF8A6A4A), 19, 42, 13, 40 + a.wave(1.5f), 2); b.spike(col(0xFF8A6A4A), 19, 46, 13, 48 - a.wave(1.5f), 2)
    b.eye(21, 43, glow, a.blink(3f))
    b.line(shell.lt(0.2f), 22, 41, 17, 35); b.line(shell.lt(0.2f), 24, 41, 21, 34)
    b.dy = 0
    fx.rise(a.t, 30, 34, 12, glow, n = 3, height = 8f)
}

private val ironSentry: Painter = { b, fx, a ->
    val iron = col(0xFF6E7480); val brass = col(0xFFB07A3A); val lens = col(0xFFFF4A3A)
    val st = a.bob(1.5f)
    b.line(iron.dk(0.2f), 28, 42, 24, 48 + st); b.line(iron.dk(0.2f), 24, 48 + st, 22, 53)
    b.line(iron.dk(0.2f), 40, 42, 44, 48 - st + 1); b.line(iron.dk(0.2f), 44, 48 - st + 1, 46, 53)
    b.line(iron, 31, 43, 30, 53); b.line(iron, 37, 43, 38, 53)
    b.dy = a.bob(1.5f)
    b.poly(iron, 24, 26, 42, 26, 44, 42, 22, 42)
    b.rect(24, 32, 42, 33, brass)
    listOf(25 to 28, 41 to 28, 24 to 40, 42 to 40).forEach { (x, y) -> b.set(x, y, brass.lt(0.3f)) }
    val rot = (a.t * 4f).toInt() % 2
    b.blob(44, 36, 3.5, 3.5, brass); b.set(44, 36, OUTLINE); b.set(44 + rot, 33, brass.dk(0.3f)); b.set(47, 36 - rot, brass.dk(0.3f))
    b.blob(33, 30, 5, 5, iron.dk(0.3f))
    b.blob(33, 30, 3.5, 3.5, lens)
    b.set(32, 29, Color.White)
    fx.glow(33, 30, 6, lens.copy(alpha = 0.3f + 0.2f * a.pulse(2f)))
    b.line(iron, 33, 26, 33, 20); b.blob(33, 19, 1.5, 1.5, lens, shade = false)
    b.dy = 0
}

private val fogWraith: Painter = { b, fx, a ->
    val mist = col(0xFFC8DCE8); val void = col(0xFF1A2230)
    val w = a.wave(0.7f)
    fx.glow(32, 34, 16, mist.copy(alpha = 0.12f))
    b.tube(mist.copy(alpha = 0.85f), 32, 34 + w, 8, 35, 42 + w, 6, 40 + sin(a.t * 2f) * 2f, 48, 3.5, 46 + sin(a.t * 2f + 1f) * 3f, 50, 1.5)
    b.blob(32, 30 + w, 9, 9, mist.copy(alpha = 0.9f))
    b.tube(mist.copy(alpha = 0.8f), 25, 34 + w, 2, 18, 38 + w * 2, 1.4, 14, 36 + w * 3, 0.8)
    b.blob(28, 30 + w, 2, 3, void, shade = false); b.blob(35, 30 + w, 2, 3, void, shade = false)
    if (!a.blink(3.4f)) { b.set(28, 30 + w, col(0xFF7FE8FF)); b.set(35, 30 + w, col(0xFF7FE8FF)) }
    b.blob(31.5f, 36 + w, 2, 2.5, void, shade = false)
    fx.drift(a.t, mist.copy(alpha = 0.4f), n = 8, fall = false)
}

private val rimeWolf: Painter = { b, fx, a ->
    val fur = col(0xFFDCE6EE); val ice = col(0xFF8FD8F0)
    b.tube(fur, 48, 34, 2.5, 54, 32, 2.6, 58, 28, 2)
    b.tube(fur.dk(0.15f), 46, 38, 2.4, 48, 51, 1.8); b.tube(fur.dk(0.15f), 32, 37, 2.4, 32, 51, 1.8)
    b.blob(40, 34, 11, 6, fur)
    for (i in 0 until 5) b.spike(ice, 31 + i * 3f, 30, 33 + i * 3f, 24 - (i % 2) * 2, 3)
    b.tube(fur, 43, 38, 2.4, 44, 52, 1.8); b.tube(fur, 29, 37, 2.4, 27, 52, 1.8)
    b.dy = a.bob(0.8f)
    b.spike(fur, 24, 28, 25, 22, 3); b.spike(fur, 28, 28, 30, 22, 3)
    b.blob(25, 32, 6, 5, fur)
    b.blob(18, 35, 5, 2.4, fur.lt(0.1f)); b.set(13, 34, OUTLINE)
    b.rect(14, 37, 20, 37, OUTLINE)
    b.eye(22, 31, ice, a.blink(), big = true)
    fx.glow(22.5f, 31, 3, ice.copy(alpha = 0.4f))
    b.dy = 0
    fx.drift(a.t, Color.White.copy(alpha = 0.6f), n = 6)
}

private val duneSkink: Painter = { b, fx, a ->
    val hide = col(0xFFD9A050); val spot = col(0xFF8A5420); val frill = col(0xFFE05A3A)
    val sw = a.wave(1f) * 2f
    b.tube(hide, 44, 46, 3, 52, 48 + sw * 0.5f, 2, 58, 46 + sw, 1.2, 62, 42 + sw, 0.7)
    b.tube(hide.dk(0.2f), 40, 47, 1.6, 43, 52, 1.2); b.tube(hide.dk(0.2f), 28, 47, 1.6, 26, 52, 1.2)
    b.blob(35, 45, 11, 5, hide)
    listOf(30 to 42, 35 to 41, 40 to 43, 33 to 45, 43 to 46).forEach { (x, y) -> b.set(x, y, spot); b.set(x + 1, y, spot) }
    b.tube(hide, 38, 48, 1.6, 40, 53, 1.2); b.tube(hide, 27, 48, 1.6, 25, 53, 1.2)
    b.dy = a.bob(1.1f)
    val open = if (sin(a.t * 1.3f) > 0.3f) 1 else 0
    b.poly(frill, 24, 36 - open * 2, 30, 38, 30, 48, 24, 50 + open * 2, 20, 43)
    for (i in 0 until 3) b.line(frill.dk(0.3f), 26, 43, 23 + i, 37 + i * 5)
    b.blob(20, 44, 5, 3.5, hide)
    b.rect(15, 46, 20, 46, OUTLINE)
    b.eye(19, 42, OUTLINE, a.blink()); b.set(19, 41, Color.White)
    b.dy = 0
    if (((a.t * 0.8f) % 1f) < 0.2f) { b.set(14, 46, frill); b.set(13, 47, frill) }
    fx.drift(a.t * 0.8f, col(0xFFE8C88A).copy(alpha = 0.5f), n = 5, fall = false)
}

private val abyssCrab: Painter = { b, fx, a ->
    val shell = col(0xFF5A3A8A); val teal = col(0xFF3AA8A0); val eye = col(0xFF7FFFE8)
    for (i in 0 until 3) {
        val st = a.bob(2.2f, i * 0.4f)
        b.line(shell.dk(0.2f), 28 + i * 4, 45, 24 + i * 4, 50 + st); b.line(shell.dk(0.2f), 24 + i * 4, 50 + st, 23 + i * 4, 53)
        b.line(shell.dk(0.1f), 38 + i * 4, 45, 42 + i * 4, 50 - st + 1); b.line(shell.dk(0.1f), 42 + i * 4, 50 - st + 1, 43 + i * 4, 53)
    }
    b.blob(36, 41, 12, 7, shell)
    listOf(30 to 38, 36 to 36, 42 to 38).forEach { (x, y) -> b.set(x, y, teal); b.set(x + 1, y, teal.lt(0.3f)) }
    b.line(eye.copy(alpha = 0.6f), 26, 44, 46, 44)
    b.dy = a.bob(0.7f)
    b.line(shell, 31, 35, 30, 29); b.line(shell, 37, 35, 38, 29)
    b.blob(30, 28, 1.8, 1.8, eye, shade = false); b.blob(38, 28, 1.8, 1.8, eye, shade = false)
    if (!a.blink(3.7f)) { b.set(30, 28, OUTLINE); b.set(38, 28, OUTLINE) }
    b.dy = 0
    val pinch = if (sin(a.t * 2.5f) > 0.5f) 2f else 0f
    b.tube(shell, 26, 40, 2.4, 19, 36, 2.2)
    b.blob(13, 34, 6.5, 5, shell.lt(0.08f))
    b.spike(shell.lt(0.08f), 9, 31, 3, 28 + pinch, 4); b.spike(shell.lt(0.08f), 9, 37, 4, 39 - pinch, 3)
    b.tube(shell.dk(0.1f), 46, 40, 2, 51, 37, 1.8); b.blob(54, 36, 3, 2.5, shell.dk(0.1f))
    fx.rise(a.t, 20, 30, 24, eye.copy(alpha = 0.6f), n = 4, height = 18f, speed = 0.35f)
}

private val starShade: Painter = { b, fx, a ->
    val shade = col(0xFF2E2858); val trim = col(0xFF8A6BE0); val star = col(0xFFFFF4C8)
    val w = a.wave(0.6f) * 1.5f
    fx.glow(32, 32, 16, trim.copy(alpha = 0.18f))
    b.poly(shade, 32, 18 + w, 42, 30 + w, 40, 48, 36, 44, 32, 50, 28, 44, 24, 48, 22, 30 + w)
    b.blob(32, 20 + w, 6, 6, shade)
    b.line(trim.dk(0.1f), 27, 16 + w, 30, 14 + w); b.line(trim.dk(0.1f), 23, 31 + w, 22, 40)
    b.tube(shade, 24, 30 + w, 2, 16, 34 + w, 1.4, 12, 30 + w, 0.8)
    b.tube(shade, 40, 30 + w, 2, 48, 34 + w, 1.4, 52, 30 + w, 0.8)
    listOf(28 to 32, 34 to 36, 30 to 40, 36 to 28, 32 to 44).forEachIndexed { i, (x, y) ->
        b.set(x, y + w, if (((a.t * 1.6f + i * 0.3f) % 1f) < 0.6f) star else trim)
    }
    if (!a.blink(3.2f)) { b.set(29, 20 + w, star); b.set(35, 20 + w, star) }
    for (k in 0 until 6) {
        val ang = a.t * 1.2f + k * 1.047f
        fx.dot(32 + cos(ang) * 14f, 32 + sin(ang) * 6f, if (k % 2 == 0) star else trim)
    }
}

// =============================================================================================
// Fallback: a seeded beast for any name without a bespoke painter (older saves, new content).
// =============================================================================================

private fun seededBeast(seed: Int): Painter = { b, fx, a ->
    val hues = listOf(0xFF7A5A8E, 0xFF4E7A5A, 0xFF8E5A3A, 0xFF5A6E8E, 0xFF8E3A4A, 0xFF6E6A4A).map(::Color)
    val eyes = listOf(0xFFFFE36A, 0xFF7FE8FF, 0xFFFF7A3A, 0xFF8CFF6A).map(::Color)
    val hide = hues[(seed ushr 2).mod(hues.size)]
    val eye = eyes[(seed ushr 5).mod(eyes.size)]
    val big = (seed ushr 8).mod(3)
    val horns = (seed ushr 11).mod(3)
    val rx = 9 + big * 2
    b.tube(hide, 36 + rx, 38, 2.5, 42 + rx, 42, 1.8, 46 + rx, 38, 1)
    b.tube(hide.dk(0.2f), 32 + rx - 2, 40, 2.6, 33 + rx - 2, 51, 2); b.tube(hide.dk(0.2f), 30, 40, 2.6, 30, 51, 2)
    b.blob(36, 36, rx, 6 + big, hide)
    b.blob(34, 40 + big, rx - 3, 2.5, hide.lt(0.25f))
    b.tube(hide, 30 + rx - 4, 40, 2.6, 30 + rx - 4, 52, 2); b.tube(hide, 27, 40, 2.6, 26, 52, 2)
    b.dy = a.bob(0.8f)
    b.blob(36 - rx - 3, 32, 6, 5, hide)
    when (horns) {
        1 -> { b.spike(col(0xFFE8DCC0), 36 - rx - 4, 28, 36 - rx - 2, 21, 3); b.spike(col(0xFFE8DCC0), 36 - rx, 28, 36 - rx + 4, 22, 3) }
        2 -> for (i in 0 until 4) b.spike(hide.lt(0.3f), 30 - rx + i * 5, 31 - big, 31 - rx + i * 5, 25 - big, 2)
    }
    b.eye(36 - rx - 5, 31, eye, a.blink(), big = true)
    b.rect(36 - rx - 9, 35, 36 - rx - 3, 35, OUTLINE)
    b.dy = 0
}

// =============================================================================================
// Registry, drawing and composables
// =============================================================================================

private val PAINTERS: Map<String, Painter> = mapOf(
    "ferrite_cub" to ferriteCub, "bastion_drake" to bastionDrake, "aegis_behemoth" to aegisBehemoth,
    "volt_wisp" to voltWisp, "gale_lynx" to galeLynx, "tempest_sovereign" to tempestSovereign,
    "moss_tortoise" to mossTortoise, "rune_stag" to runeStag, "worldroot_leviathan" to worldrootLeviathan,
    "spark_kit" to sparkKit, "pyre_wyvern" to pyreWyvern, "solaris_dragon" to solarisDragon,
    "nestling_ash_whelp" to ashWhelp, "cinder_hound" to cinderHound, "forge_warden" to forgeWarden,
    "mist_stalker" to mistStalker, "obsidian_colossus" to obsidianColossus, "archon_of_the_deep" to archonOfTheDeep,
    "rime_yeti" to rimeYeti, "dune_scorpion_matriarch" to duneScorpion, "glacial_wyrm" to glacialWyrm,
    "starbound_titan" to starboundTitan,
    "ashen_stalker" to ashenStalker, "ash_imp" to ashImp, "cinder_beetle" to cinderBeetle, "iron_sentry" to ironSentry,
    "fog_wraith" to fogWraith, "rime_wolf" to rimeWolf, "dune_skink" to duneSkink, "abyss_crab" to abyssCrab,
    "star_shade" to starShade,
)

/** Every creature with a bespoke painter (used by previews and the bestiary). */
val BESTIARY_KEYS: List<String> get() = PAINTERS.keys.toList()

/** Normalises a display name ("Ashen Stalker Mk.3") to a painter key ("ashen_stalker"). */
fun creatureKey(name: String): String = name.lowercase()
    .replace(Regex("\\s+mk\\.?\\s*\\d+$"), "")
    .replace(Regex("[^a-z0-9]+"), "_")
    .trim('_')

private fun painterFor(key: String): Painter = PAINTERS[key] ?: seededBeast(key.hashCode())

private class Rendered(val buf: CBuf, val fx: CFx)

private fun render(key: String, t: Float): Rendered {
    val b = CBuf(); val fx = CFx()
    painterFor(key)(b, fx, Anim(t))
    return Rendered(b, fx)
}

private val boundsCache = HashMap<String, IntArray>()

/** Bounding box (x0, y0, x1, y1) of a creature's resting pose; used to fit it to its box. */
private fun boundsOf(key: String): IntArray = boundsCache.getOrPut(key) {
    var x0 = CW; var y0 = CH; var x1 = -1; var y1 = -1
    for (t in listOf(0f, 0.55f, 1.3f)) {
        val r = render(key, t)
        for (y in 0 until CH) for (x in 0 until CW) if (r.buf.px[y * CW + x] != null) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
    }
    if (x1 < 0) intArrayOf(0, 0, CW - 1, CH - 1) else intArrayOf(x0, y0, x1, y1)
}

/**
 * Draws a creature centred in this DrawScope, scaled to fill [fill] of the box (integer pixel
 * scale). Creatures face left; [mirror] turns them to face right. [flash] whitens the sprite.
 */
fun DrawScope.drawCreature(key: String, t: Float, flash: Float = 0f, mirror: Boolean = false, fill: Float = 1f, shadow: Boolean = true, silhouette: Color? = null) {
    val r = render(key, t)
    val (bx0, by0, bx1, by1) = boundsOf(key)
    val bw = (bx1 - bx0 + 3).toFloat(); val bh = (by1 - by0 + 3).toFloat()
    val u = floor(minOf(size.width * fill / bw, size.height * fill / bh)).coerceAtLeast(1f)
    val ox = floor((size.width - u * bw) / 2f) - (bx0 - 1) * u
    val oy = floor(size.height - u * bh - (size.height * (1f - fill)) * 0.15f) - (by0 - 1) * u
    fun sx(x: Float) = if (mirror) size.width - (ox + x * u) - u else ox + x * u
    fun screen(x: Float, y: Float) = Offset(sx(x), oy + y * u)

    if (shadow) {
        val cx = (bx0 + bx1) / 2f + 0.5f
        val c = screen(cx, by1 + 1f)
        val w = (bx1 - bx0) * 0.8f * u
        drawOval(Color.Black.copy(alpha = 0.35f), Offset(c.x - w / 2f + if (mirror) u else 0f, c.y - u), Size(w, 3 * u))
    }
    if (silhouette == null) r.fx.glows.forEach { (x, y, rc) ->
        val c = screen(x + 0.5f, y + 0.5f)
        drawCircle(Brush.radialGradient(listOf(rc.second, Color.Transparent), c, rc.first * u), rc.first * u, c)
    }
    val px = r.buf.px
    for (y in 0 until CH) for (x in 0 until CW) {
        val c = px[y * CW + x]
        if (c == null) {
            val near = (x > 0 && px[y * CW + x - 1] != null) || (x < CW - 1 && px[y * CW + x + 1] != null) ||
                (y > 0 && px[(y - 1) * CW + x] != null) || (y < CH - 1 && px[(y + 1) * CW + x] != null)
            if (near) drawRect(OUTLINE, screen(x.toFloat(), y.toFloat()), Size(u, u))
        } else {
            val fillC = when {
                silhouette != null -> silhouette
                flash > 0f -> c.mixWith(Color.White, flash * 0.8f)
                else -> c
            }
            drawRect(fillC, screen(x.toFloat(), y.toFloat()), Size(u, u))
        }
    }
    if (silhouette == null) r.fx.dots.forEach { (x, y, c) -> drawRect(c, screen(floor(x), floor(y)), Size(u, u)) }
}

@Composable
private fun rememberCreatureClock(animated: Boolean): Float {
    if (!animated) return 0.3f
    val tr = rememberInfiniteTransition(label = "creature_clock")
    val t by tr.animateFloat(0f, 60f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "creature_t")
    return t
}

/**
 * A named creature (foe, boss or companion species), drawn procedurally. [silhouette] paints
 * it as a solid shape in that colour — used for undiscovered evolutions.
 */
@Composable
fun CreatureSprite(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
    fill: Float = 1f,
    flash: Float = 0f,
    animated: Boolean = true,
    shadow: Boolean = true,
    silhouette: Color? = null,
) {
    val key = creatureKey(name)
    val t = rememberCreatureClock(animated)
    Canvas(modifier.size(size)) { drawCreature(key, t, flash, mirror, fill, shadow, silhouette) }
}

/** How much of its box a companion fills at each stage, so evolution reads as growth. */
fun stageFill(stage: MonsterStage): Float = when (stage) {
    MonsterStage.HATCHLING -> 0.62f
    MonsterStage.JUVENILE -> 0.82f
    MonsterStage.APEX -> 1f
}

/** Painter key for a companion's current form (affinity × stage). */
fun companionKey(m: MonsterCompanion): String = creatureKey(GameCatalog.monsterSpeciesName(m.affinity, m.stage))

/** The player's companion in its current evolved form, facing right by default. */
@Composable
fun CompanionSprite(monster: MonsterCompanion, size: Dp, modifier: Modifier = Modifier, mirror: Boolean = true, animated: Boolean = true) {
    val key = companionKey(monster)
    val t = rememberCreatureClock(animated)
    Canvas(modifier.size(size)) { drawCreature(key, t, mirror = mirror, fill = stageFill(monster.stage)) }
}
