package com.sparkborne.app.ui.hero

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.ui.theme.RpgTheme
import kotlin.math.floor

// =============================================================================================
// Rarity
// =============================================================================================

enum class Rarity(val label: String, val color: Color) {
    COMMON("Common", Color(0xFFA7B0BE)),
    UNCOMMON("Uncommon", Color(0xFF5FD068)),
    RARE("Rare", Color(0xFF4FA3FF)),
    EPIC("Epic", Color(0xFFB57CFF)),
    LEGENDARY("Legendary", Color(0xFFFFB84D)),
}

fun rarityForCost(gold: Int): Rarity = when {
    gold <= 0 -> Rarity.COMMON
    gold < 60 -> Rarity.UNCOMMON
    gold < 130 -> Rarity.RARE
    gold < 200 -> Rarity.EPIC
    else -> Rarity.LEGENDARY
}

internal fun Color.mixWith(other: Color, f: Float): Color = lerp(this, other, f.coerceIn(0f, 1f))

internal val InkDark = Color(0xFF07080C)
internal val GoldTrim = Color(0xFFE8B64C)

// =============================================================================================
// Pixel frames — stepped-corner, bevelled "game window" borders drawn directly (no Material cards)
// =============================================================================================

/** Fills a rect with 2-step pixel-chamfered corners. */
internal fun DrawScope.chamfer(l: Float, t: Float, r: Float, b: Float, u: Float, color: Color) {
    if (r - l < u * 4 || b - t < u * 4) {
        drawRect(color, Offset(l, t), Size(r - l, b - t))
        return
    }
    drawRect(color, Offset(l + 2 * u, t), Size(r - l - 4 * u, b - t))
    drawRect(color, Offset(l, t + 2 * u), Size(r - l, b - t - 4 * u))
    drawRect(color, Offset(l + u, t + u), Size(r - l - 2 * u, b - t - 2 * u))
}

enum class FrameStyle { WINDOW, SOCKET, PLATE }

/**
 * Draws a pixel-art frame behind the content.
 * - WINDOW: raised panel with bright top bevel, dark bottom bevel and corner rivets.
 * - SOCKET: sunken slot (inverse bevel) for items.
 * - PLATE: flat engraved plate, no rivets.
 */
fun Modifier.pixelFrame(
    fill: Color,
    edge: Color,
    style: FrameStyle = FrameStyle.WINDOW,
    unit: Dp = 3.dp,
    rivet: Color? = null,
    glow: Color? = null,
): Modifier = this.drawBehind {
    val u = floor(unit.toPx()).coerceAtLeast(1f)
    val w = size.width
    val h = size.height
    if (glow != null) {
        drawRect(
            Brush.radialGradient(
                listOf(glow.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(w / 2, h / 2),
                radius = maxOf(w, h) * 0.75f,
            ),
        )
    }
    chamfer(0f, 0f, w, h, u, InkDark)
    chamfer(u, u, w - u, h - u, u, edge)
    if (fill.alpha < 1f) chamfer(2 * u, 2 * u, w - 2 * u, h - 2 * u, u, InkDark.mixWith(edge, 0.12f))
    chamfer(2 * u, 2 * u, w - 2 * u, h - 2 * u, u, fill)
    val hi = Color.White.copy(alpha = if (style == FrameStyle.SOCKET) 0.05f else 0.10f)
    val lo = Color.Black.copy(alpha = if (style == FrameStyle.SOCKET) 0.45f else 0.30f)
    val top = if (style == FrameStyle.SOCKET) lo else hi
    val bottom = if (style == FrameStyle.SOCKET) hi else lo
    drawRect(top, Offset(4 * u, 2 * u), Size(w - 8 * u, u))
    drawRect(top, Offset(2 * u, 4 * u), Size(u, h - 8 * u))
    drawRect(bottom, Offset(4 * u, h - 3 * u), Size(w - 8 * u, u))
    drawRect(bottom, Offset(w - 3 * u, 4 * u), Size(u, h - 8 * u))
    if (style == FrameStyle.WINDOW) {
        val rc = rivet ?: edge.mixWith(Color.White, 0.35f)
        listOf(
            Offset(3 * u, 3 * u), Offset(w - 5 * u, 3 * u),
            Offset(3 * u, h - 5 * u), Offset(w - 5 * u, h - 5 * u),
        ).forEach { o ->
            drawRect(InkDark, o + Offset(u * 0.5f, u * 0.5f), Size(2 * u, 2 * u))
            drawRect(rc, o, Size(2 * u, 2 * u))
        }
    }
}

// =============================================================================================
// Pixel button
// =============================================================================================

@Composable
fun PixelButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = if (enabled) color else RpgTheme.colors.pixelBorder
    val fill = if (pressed) base.mixWith(Color.Black, 0.2f) else base
    Box(
        modifier = modifier
            .offset(y = if (pressed) 2.dp else 0.dp)
            .drawBehind {
                val u = floor(2.dp.toPx())
                if (!pressed) chamfer(0f, u * 2, size.width, size.height + u * 2, u, InkDark.copy(alpha = 0.6f))
            }
            .pixelFrame(fill = fill, edge = base.mixWith(Color.White, 0.25f), style = FrameStyle.PLATE, unit = 2.dp)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 10.dp else 16.dp, vertical = if (small) 6.dp else 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text8(
            text = text,
            size = if (small) 10.sp else 12.sp,
            color = if (enabled) InkDark else RpgTheme.colors.textMuted,
            weight = FontWeight.ExtraBold,
        )
    }
}

// =============================================================================================
// Typography helpers
// =============================================================================================

@Composable
fun Text8(
    text: String,
    size: TextUnit = 11.sp,
    color: Color = RpgTheme.colors.textPrimary,
    weight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    letterSpacing: TextUnit = 0.5.sp,
    italic: Boolean = false,
) {
    androidx.compose.material3.Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = size,
        fontWeight = weight,
        color = color,
        modifier = modifier,
        textAlign = align,
        maxLines = maxLines,
        letterSpacing = letterSpacing,
        lineHeight = size * 1.3f,
        fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
        style = androidx.compose.ui.text.TextStyle(
            shadow = androidx.compose.ui.graphics.Shadow(InkDark, Offset(0f, 2f), 0f),
        ),
    )
}

/** Section title flanked by pixel ornaments: ◆━━ TITLE ━━◆ */
@Composable
fun RuneHeader(title: String, color: Color = RpgTheme.colors.accentPrimary, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ornament(color, Modifier.weight(1f), flip = false)
        Text8(title, size = 12.sp, color = color, weight = FontWeight.ExtraBold, letterSpacing = 2.sp)
        if (trailing != null) Text8(trailing, size = 10.sp, color = RpgTheme.colors.textMuted)
        Ornament(color, Modifier.weight(1f), flip = true)
    }
}

@Composable
private fun Ornament(color: Color, modifier: Modifier, flip: Boolean) {
    Canvas(modifier.height(10.dp)) {
        val u = floor(2.dp.toPx())
        val cy = floor(size.height / 2 / u) * u
        val w = size.width
        val diamondX = if (flip) w - 4 * u else 0f
        drawRect(color.copy(alpha = 0.55f), Offset(if (flip) 0f else 4 * u, cy), Size(w - 5 * u, u))
        drawRect(color.copy(alpha = 0.25f), Offset(if (flip) 0f else 4 * u, cy + u), Size(w - 5 * u, u))
        drawRect(color, Offset(diamondX + u, cy - u), Size(u, 3 * u))
        drawRect(color, Offset(diamondX, cy), Size(3 * u, u))
    }
}

// =============================================================================================
// Item socket
// =============================================================================================

/**
 * A sunken inventory slot. [rarity] tints the inner glow; [selected] adds a pulsing gold rim;
 * [qty] shows a stack count bottom-right; [tag] a tiny corner tag (e.g. "E" equipped).
 */
@Composable
fun ItemSocket(
    size: Dp,
    modifier: Modifier = Modifier,
    glow: Color? = null,
    selected: Boolean = false,
    dim: Boolean = false,
    qty: Int? = null,
    tag: String? = null,
    tagColor: Color = RpgTheme.colors.vitalityEmerald,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = RpgTheme.colors
    val pulse = if (selected) {
        val tr = rememberInfiniteTransition(label = "socket_sel")
        tr.animateFloat(0.45f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "socket_pulse").value
    } else {
        0f
    }
    val edge = when {
        selected -> GoldTrim.mixWith(Color.White, pulse * 0.3f)
        glow != null -> glow.mixWith(colors.pixelBorder, 0.45f)
        else -> colors.pixelBorder
    }
    Box(
        modifier = modifier
            .size(size)
            .pixelFrame(fill = colors.obsidianBg.mixWith(Color.Black, 0.25f), edge = edge, style = FrameStyle.SOCKET, unit = 2.dp)
            .drawBehind {
                if (glow != null && !dim) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(glow.copy(alpha = 0.38f), Color.Transparent),
                            center = Offset(this.size.width / 2, this.size.height / 2),
                            radius = this.size.minDimension * 0.55f,
                        ),
                    )
                }
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.matchParentSize().padding(4.dp).alpha(if (dim) 0.35f else 1f),
            contentAlignment = Alignment.Center,
            content = content,
        )
        if (qty != null) {
            Text8(
                "x$qty",
                size = 9.sp,
                color = Color.White,
                weight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
            )
        }
        if (tag != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .pixelFrame(fill = tagColor, edge = tagColor, style = FrameStyle.PLATE, unit = 1.dp)
                    .padding(horizontal = 3.dp),
            ) { Text8(tag, size = 9.sp, color = InkDark, weight = FontWeight.ExtraBold) }
        }
    }
}

// =============================================================================================
// Bars & rings
// =============================================================================================

/** Segmented pixel bar (HP crystals, XP, etc.) with optional centered label. */
@Composable
fun SegmentBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    segments: Int = 20,
    height: Dp = 12.dp,
    label: String? = null,
) {
    val colors = RpgTheme.colors
    Box(modifier.height(height), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val u = floor(2.dp.toPx()).coerceAtLeast(1f)
            chamfer(0f, 0f, size.width, size.height, u, InkDark)
            val innerL = 2 * u
            val innerW = size.width - 4 * u
            val segW = innerW / segments
            val filled = (progress.coerceIn(0f, 1f) * segments)
            for (s in 0 until segments) {
                val x = innerL + s * segW
                val c = when {
                    s < floor(filled) -> color
                    s.toFloat() < filled -> color.copy(alpha = 0.5f)
                    else -> colors.panelElevated
                }
                drawRect(c, Offset(x, 2 * u), Size(segW - u, size.height - 4 * u))
                if (s < filled) drawRect(Color.White.copy(alpha = 0.28f), Offset(x, 2 * u), Size(segW - u, u))
            }
        }
        if (label != null) Text8(label, size = (height.value * 0.62f).sp, color = Color.White, weight = FontWeight.ExtraBold)
    }
}

/** Stepped ring progress around content (skill medallions, sigils). */
fun DrawScope.pixelRing(progress: Float, color: Color, track: Color, strokePx: Float) {
    val inset = strokePx / 2
    val sz = Size(size.width - strokePx, size.height - strokePx)
    drawArc(track, -90f, 360f, false, Offset(inset, inset), sz, style = Stroke(strokePx))
    // draw in 24 discrete ticks for a pixel-art feel
    val ticks = 24
    val lit = (progress.coerceIn(0f, 1f) * ticks).toInt()
    for (i in 0 until lit) {
        drawArc(color, -90f + i * 15f + 1.5f, 12f, false, Offset(inset, inset), sz, style = Stroke(strokePx))
    }
}

// =============================================================================================
// Pixel-art sprites (procedural, palette-driven)
// =============================================================================================

class PixelArt(val rows: List<String>, val palette: Map<Char, Color>)

@Composable
fun PixelArtIcon(art: PixelArt, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawPixelArt(art) }
}

fun DrawScope.drawPixelArt(art: PixelArt, origin: Offset = Offset.Zero, boxSize: Size = size) {
    val n = art.rows.size
    val cols = art.rows.maxOf { it.length }
    val px = floor(minOf(boxSize.width / cols, boxSize.height / n)).coerceAtLeast(1f)
    val ox = origin.x + floor((boxSize.width - px * cols) / 2)
    val oy = origin.y + floor((boxSize.height - px * n) / 2)
    // drop shadow
    art.rows.forEachIndexed { r, row ->
        row.forEachIndexed { c, ch ->
            if (ch != '.' && art.palette[ch] != null) {
                drawRect(Color.Black.copy(alpha = 0.45f), Offset(ox + (c + 1) * px, oy + (r + 1) * px), Size(px, px))
            }
        }
    }
    art.rows.forEachIndexed { r, row ->
        row.forEachIndexed { c, ch ->
            val color = art.palette[ch] ?: return@forEachIndexed
            drawRect(color, Offset(ox + c * px, oy + r * px), Size(px, px))
        }
    }
}

/** Animated idle bob (used for showcase sprites and the hero). */
@Composable
fun rememberBob(amplitude: Float = 3f, periodMs: Int = 1400): Float {
    val tr = rememberInfiniteTransition(label = "bob")
    return tr.animateFloat(-amplitude, amplitude, infiniteRepeatable(tween(periodMs), RepeatMode.Reverse), label = "bob_v").value
}

@Composable
fun rememberSpin(periodMs: Int = 6000): Float {
    val tr = rememberInfiniteTransition(label = "spin")
    return tr.animateFloat(0f, 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "spin_v").value
}

/** Small icon + value inline token, e.g. ⚔ 24 — not a card, just glyph and number. */
@Composable
fun StatGlyph(icon: String, value: String, color: Color, modifier: Modifier = Modifier, caption: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text8(icon, size = 13.sp, color = color)
            Spacer(Modifier.width(4.dp))
            Text8(value, size = 15.sp, color = color, weight = FontWeight.ExtraBold)
        }
        if (caption != null) Text8(caption, size = 8.sp, color = RpgTheme.colors.textMuted, letterSpacing = 1.5.sp)
    }
}
