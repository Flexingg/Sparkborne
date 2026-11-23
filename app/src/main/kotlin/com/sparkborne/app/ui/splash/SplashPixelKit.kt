package com.sparkborne.app.ui.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A chunky virtual-pixel canvas on top of a [DrawScope]. Every primitive snaps to an integer
 * grid of [unit]-sized device pixels so procedural animation stays crisp pixel art.
 */
internal class PixelGrid(
    private val scope: DrawScope,
    val unit: Float,
    val cols: Int,
    val rows: Int,
    private val baseX: Float = 0f,
    private val baseY: Float = 0f,
) {
    /** Whole-grid offset in virtual pixels (used for screen shake). */
    var ox: Int = 0
    var oy: Int = 0

    fun rect(x: Int, y: Int, w: Int, h: Int, color: Color) {
        if (w <= 0 || h <= 0 || color.alpha <= 0f) return
        scope.drawRect(
            color = color,
            topLeft = Offset(baseX + (x + ox) * unit, baseY + (y + oy) * unit),
            size = Size(w * unit, h * unit),
        )
    }

    fun rectF(x: Float, y: Float, w: Float, h: Float, color: Color) =
        rect(floor(x).toInt(), floor(y).toInt(), w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1), color)

    fun dot(x: Int, y: Int, color: Color) = rect(x, y, 1, 1, color)

    fun dotF(x: Float, y: Float, color: Color) = rect(floor(x).toInt(), floor(y).toInt(), 1, 1, color)

    /** Fills the entire device canvas, ignoring the grid. */
    fun fill(color: Color) {
        if (color.alpha > 0f) scope.drawRect(color)
    }

    fun disc(cx: Int, cy: Int, r: Int, color: Color) {
        for (dy in -r..r) {
            val half = sqrt((r * r - dy * dy).toFloat() + r * 0.8f).toInt()
            rect(cx - half, cy + dy, half * 2 + 1, 1, color)
        }
    }

    fun ellipse(cx: Int, cy: Int, rx: Float, ry: Float, color: Color) {
        if (rx < 1f || ry < 1f) return
        val ryi = ry.toInt()
        for (dy in -ryi..ryi) {
            val f = 1f - (dy * dy) / (ry * ry)
            if (f <= 0f) continue
            val half = (rx * sqrt(f)).toInt()
            rect(cx - half, cy + dy, half * 2 + 1, 1, color)
        }
    }

    /** Pixel-art sky: hard colour bands with a checkerboard dither on each seam. */
    fun skyBands(colors: List<Color>, top: Int, bottom: Int) {
        val n = colors.size
        val span = (bottom - top).toFloat() / n
        for (i in 0 until n) {
            val y0 = top + (i * span).toInt()
            val y1 = if (i == n - 1) bottom else top + ((i + 1) * span).toInt()
            rect(0, y0, cols, y1 - y0, colors[i])
        }
        for (i in 1 until n) {
            val y = top + (i * span).toInt()
            for (x in 0 until cols step 2) {
                dot(x + (i % 2), y, colors[i - 1])
            }
        }
    }

    fun bandColorAt(colors: List<Color>, top: Int, bottom: Int, y: Int): Color {
        val idx = (((y - top).toFloat() / (bottom - top)) * colors.size).toInt()
        return colors[idx.coerceIn(0, colors.lastIndex)]
    }
}

// ---------------------------------------------------------------------------------------------
// Math helpers
// ---------------------------------------------------------------------------------------------

/** Deterministic integer hash → [0, 1). Gives every particle/star a stable "random" identity. */
internal fun hash01(seed: Int): Float {
    var x = seed * -0x61c88647 + 0x165667b1
    x = (x xor (x ushr 15)) * 0x2c1b3c6d
    x = (x xor (x ushr 12)) * 0x297a2d39
    x = x xor (x ushr 15)
    return (x ushr 8) / 16_777_216f
}

internal fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

internal fun lerpF(a: Float, b: Float, f: Float): Float = a + (b - a) * f

internal fun easeOutCubic(p: Float): Float {
    val q = 1f - clamp01(p)
    return 1f - q * q * q
}

internal fun easeInQuad(p: Float): Float {
    val x = clamp01(p)
    return x * x
}

internal fun easeInOutCubic(p: Float): Float {
    val x = clamp01(p)
    return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
}

internal fun easeOutBounce(p: Float): Float {
    val x = clamp01(p)
    val n1 = 7.5625f
    val d1 = 2.75f
    return when {
        x < 1f / d1 -> n1 * x * x
        x < 2f / d1 -> { val y = x - 1.5f / d1; n1 * y * y + 0.75f }
        x < 2.5f / d1 -> { val y = x - 2.25f / d1; n1 * y * y + 0.9375f }
        else -> { val y = x - 2.625f / d1; n1 * y * y + 0.984375f }
    }
}

internal fun Color.mix(other: Color, f: Float): Color = lerp(this, other, clamp01(f))

/** Quantises time to a fixed frame-rate so flames & coals flicker like hand-drawn sprite frames. */
internal fun stepTime(t: Float, fps: Float): Float = floor(t * fps) / fps

// ---------------------------------------------------------------------------------------------
// Transitions
// ---------------------------------------------------------------------------------------------

private val BAYER_4 = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

/** Ordered-dither (Bayer) dissolve: [amount] 0 = invisible, 1 = solid [color]. */
internal fun PixelGrid.ditherCover(amount: Float, color: Color, block: Int = 4) {
    if (amount <= 0f) return
    if (amount >= 1f) {
        fill(color)
        return
    }
    var by = 0
    var y = 0
    while (y < rows) {
        var bx = 0
        var x = 0
        while (x < cols) {
            val threshold = (BAYER_4[(by % 4) * 4 + (bx % 4)] + 0.5f) / 16f
            if (threshold < amount) rect(x, y, block, block, color)
            x += block
            bx++
        }
        y += block
        by++
    }
}

// ---------------------------------------------------------------------------------------------
// 5x7 pixel font for the title crest
// ---------------------------------------------------------------------------------------------

internal val PIXEL_GLYPHS: Map<Char, Array<String>> = mapOf(
    'S' to arrayOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
    'P' to arrayOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
    'A' to arrayOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
    'R' to arrayOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
    'K' to arrayOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
    'B' to arrayOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
    'O' to arrayOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
    'N' to arrayOf("#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"),
    'E' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
)
