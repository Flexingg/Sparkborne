package com.sparkborne.app.ui.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.ui.theme.RpgPalette
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.animateFloat

/** Seconds of choreographed scene before the outro dissolve begins. */
internal const val SPLASH_SCENE_SECONDS = 3.7f
internal const val SPLASH_OUTRO_SECONDS = 0.35f
internal const val SPLASH_TOTAL_SECONDS = SPLASH_SCENE_SECONDS + SPLASH_OUTRO_SECONDS
private const val SPLASH_INTRO_SECONDS = 0.45f

/** Virtual pixel columns across the screen — sets the chunkiness of the pixel art. */
private const val VIRTUAL_COLS = 120f

private const val TITLE = "SPARKBORNE"

/** The three animated splash scenes. [titleStart] is when the crest begins to drop in. */
internal enum class SplashScene(val titleStart: Float) {
    CITADEL(1.45f),
    CAMPFIRE(1.15f),
    FORGE(1.95f), // lands on the final hammer blow
}

/**
 * Renders one frame of the splash at time [t] seconds. Pure function of time: the caller just
 * advances the clock every frame.
 */
internal fun DrawScope.drawSplashFrame(
    scene: SplashScene,
    t: Float,
    palette: RpgPalette,
    textMeasurer: TextMeasurer,
    subtitle: String,
    footer: String,
) {
    drawRect(palette.obsidianBg)
    val unit = max(2f, floor(size.width / VIRTUAL_COLS))
    val cols = ceil(size.width / unit).toInt()
    val rows = ceil(size.height / unit).toInt()
    val grid = PixelGrid(this, unit, cols, rows)

    when (scene) {
        SplashScene.CITADEL -> grid.drawCitadelScene(t, palette)
        SplashScene.CAMPFIRE -> grid.drawCampfireScene(t, palette)
        SplashScene.FORGE -> grid.drawForgeScene(t, palette)
    }

    // Soft top scrim so the crest always reads, whatever the scene.
    val scrimRows = (rows * 0.30f).toInt()
    for (r in 0 until scrimRows step 2) {
        grid.rect(0, r, cols, 2, palette.obsidianBg.copy(alpha = 0.38f * (1f - r.toFloat() / scrimRows)))
    }

    val titleT = t - scene.titleStart
    val titleBottom = drawPixelTitle(titleT, palette.accentPrimary)
    drawTypewriterSubtitle(textMeasurer, subtitle, titleT - 0.75f, titleBottom + 16.dp.toPx(), palette)
    drawFooter(grid, textMeasurer, footer, t, palette)

    // Pixel dissolve in/out (Bayer ordered dither).
    grid.ditherCover(1f - clamp01(t / SPLASH_INTRO_SECONDS), palette.obsidianBg)
    grid.ditherCover(clamp01((t - SPLASH_SCENE_SECONDS) / SPLASH_OUTRO_SECONDS), palette.obsidianBg)
}

/** Drops the SPARKBORNE crest in letter by letter with a bounce, then sweeps a shimmer across. */
private fun DrawScope.drawPixelTitle(t: Float, accent: Color): Float {
    val glyphW = 5
    val gap = 1
    val totalCols = TITLE.length * (glyphW + gap) - gap
    val tu = max(2f, floor(size.width * 0.80f / totalCols))
    val left = floor((size.width - totalCols * tu) / 2f)
    val top = floor(size.height * 0.115f)
    val bottom = top + 8 * tu
    if (t <= 0f) return bottom

    val g = PixelGrid(this, tu, totalCols, 7, left, top)
    val outline = Color(0xFF07060C)
    val light = accent.mix(Color.White, 0.5f)
    val dark = accent.mix(Color.Black, 0.4f)

    // Shimmer band repeats every 2.4s once the crest has landed.
    val shimmerT = t - 1.05f
    val shimmerPos = if (shimmerT > 0f) (shimmerT % 2.4f) * 60f - 8f else -100f

    TITLE.forEachIndexed { i, ch ->
        val lt = t - i * 0.06f
        if (lt <= 0f) return@forEachIndexed
        val glyph = PIXEL_GLYPHS[ch] ?: return@forEachIndexed
        val dropY = (-(1f - easeOutBounce(lt / 0.5f)) * 14f).roundToInt()
        val gx = i * (glyphW + gap)

        for (r in 0 until 7) for (c in 0 until glyphW) {
            if (glyph[r][c] != '#') continue
            g.rect(gx + c - 1, r + dropY - 1, 3, 3, outline)
            g.dot(gx + c, r + dropY + 2, outline)
        }
        for (r in 0 until 7) for (c in 0 until glyphW) {
            if (glyph[r][c] != '#') continue
            var col = when {
                r <= 1 -> light
                r >= 5 -> dark
                else -> accent
            }
            val d = (gx + c) + (6 - r) * 0.6f - shimmerPos
            if (d > -1.2f && d < 1.2f) col = col.mix(Color.White, 0.85f)
            g.dot(gx + c, r + dropY, col)
        }
        // Landing dust puff
        if (lt in 0.18f..0.45f) {
            val p = (lt - 0.18f) / 0.27f
            val dust = Color.White.copy(alpha = 0.35f * (1f - p))
            g.dotF(gx - p * 2f, 7.5f, dust)
            g.dotF(gx + glyphW + p * 2f - 1f, 7.5f, dust)
        }
    }
    return bottom
}

private fun DrawScope.drawTypewriterSubtitle(
    textMeasurer: TextMeasurer,
    subtitle: String,
    t: Float,
    y: Float,
    palette: RpgPalette,
) {
    if (t <= 0f) return
    val style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 2.sp,
        color = palette.textPrimary.copy(alpha = 0.92f),
        shadow = Shadow(Color.Black, Offset(0f, 3f), 0f),
    )
    val fullWidth = textMeasurer.measure(subtitle, style).size.width
    val shown = (t * 38f).toInt().coerceAtMost(subtitle.length)
    if (shown <= 0) return
    val x = (size.width - fullWidth) / 2f
    val text = subtitle.substring(0, shown)
    drawText(textMeasurer, text, Offset(x, y), style)
    if (shown < subtitle.length || sin(t * 9f) > 0f) {
        val caretX = x + textMeasurer.measure(text, style).size.width + 2.dp.toPx()
        drawRect(
            palette.accentPrimary,
            topLeft = Offset(caretX, y + 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 12.dp.toPx()),
        )
    }
}

/** Segmented pixel loading bar tracking the real splash timeline, plus a pulsing tap hint. */
private fun DrawScope.drawFooter(
    grid: PixelGrid,
    textMeasurer: TextMeasurer,
    footer: String,
    t: Float,
    palette: RpgPalette,
) {
    val fade = clamp01((t - 0.5f) / 0.4f)
    if (fade <= 0f) return
    val u = grid.unit
    val segments = 16
    val barCols = segments * 3 - 1
    val barTop = floor((size.height - 84.dp.toPx()) / u) * u
    val barLeft = floor((size.width - barCols * u) / 2f / u) * u
    val bar = PixelGrid(this, u, barCols, 2, barLeft, barTop)
    val progress = clamp01(t / SPLASH_SCENE_SECONDS)
    val filled = (progress * segments).toInt()
    bar.rect(-1, -1, barCols + 2, 4, Color(0xFF07060C).copy(alpha = fade))
    for (s in 0 until segments) {
        val c = if (s < filled) palette.accentPrimary else palette.panelElevated
        bar.rect(s * 3, 0, 2, 2, c.copy(alpha = fade))
        if (s < filled) bar.rect(s * 3, 0, 2, 1, palette.accentPrimary.mix(Color.White, 0.4f).copy(alpha = fade))
    }
    if (filled in 0 until segments && sin(t * 20f) > 0f) {
        bar.rect(filled * 3, 0, 2, 2, Color.White.copy(alpha = 0.6f * fade))
    }

    val style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = palette.textMuted.copy(alpha = fade * (0.6f + 0.4f * sin(t * 4f))),
    )
    val layout = textMeasurer.measure(footer, style)
    drawText(
        layout,
        topLeft = Offset((size.width - layout.size.width) / 2f, barTop + 2 * u + 14.dp.toPx()),
    )
}

/**
 * A small looping "living painting" of a splash scene (no title/UI), for the scene picker.
 * Loops the settled part of the scene so the preview is always lively.
 */
@androidx.compose.runtime.Composable
internal fun SplashScenePreview(
    scene: SplashScene,
    palette: RpgPalette,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "splash_preview")
    val t by tr.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(2600, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "splash_preview_t",
    )
    androidx.compose.foundation.Canvas(modifier.then(androidx.compose.ui.Modifier.clipToBounds())) {
        drawRect(palette.obsidianBg)
        val unit = max(2f, floor(size.width / 64f))
        val cols = ceil(size.width / unit).toInt()
        val rows = ceil(size.height / unit).toInt()
        val grid = PixelGrid(this, unit, cols, rows)
        val time = 1.6f + t * 2.6f
        when (scene) {
            SplashScene.CITADEL -> grid.drawCitadelScene(time, palette)
            SplashScene.CAMPFIRE -> grid.drawCampfireScene(time, palette)
            SplashScene.FORGE -> grid.drawForgeScene(0.45f + t * 1.75f, palette)
        }
    }
}
