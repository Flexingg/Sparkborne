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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.model.MonsterCompanion
import com.sparkborne.game.model.MonsterStage
import kotlin.math.floor
import kotlin.math.sin

// =============================================================================================
// Bestiary: companions, foes and bosses drawn from 64x64 pixel-art sprites bundled under
// assets/beasts/<key>.png (see assets/beasts/CREDITS.md). All sprites face left.
// =============================================================================================

/** Sprite loading for creatures. The platform sets [loader] once at startup. */
object BeastArt {
    /** Loads an asset path such as "beasts/cinder_hound.png", or returns null if missing. */
    @Volatile
    var loader: (String) -> ImageBitmap? = { null }

    private val cache = HashMap<String, Sprite?>()

    class Sprite(val image: ImageBitmap, val x0: Int, val y0: Int, val w: Int, val h: Int)

    fun get(key: String): Sprite? = synchronized(cache) {
        cache[key] ?: (loader("beasts/$key.png") ?: loader("beasts/unknown.png"))?.let(::trimmed)?.also { cache[key] = it }
    }

    /** Crops to the opaque pixels so every creature can be fitted to its box. */
    private fun trimmed(img: ImageBitmap): Sprite {
        val px = IntArray(img.width * img.height)
        img.readPixels(px)
        var x0 = img.width; var y0 = img.height; var x1 = -1; var y1 = -1
        for (y in 0 until img.height) for (x in 0 until img.width) {
            if ((px[y * img.width + x] ushr 24) > 16) {
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
            }
        }
        return if (x1 < 0) Sprite(img, 0, 0, img.width, img.height) else Sprite(img, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }
}

/** Every creature with its own sprite. */
val BESTIARY_KEYS: List<String> = listOf(
    "spark_kit", "pyre_wyvern", "solaris_dragon", "volt_wisp", "gale_lynx", "tempest_sovereign",
    "ferrite_cub", "bastion_drake", "aegis_behemoth", "moss_tortoise", "rune_stag", "worldroot_leviathan",
    "nestling_ash_whelp", "cinder_hound", "forge_warden", "mist_stalker", "obsidian_colossus", "archon_of_the_deep",
    "rime_yeti", "dune_scorpion_matriarch", "glacial_wyrm", "starbound_titan",
    "ashen_stalker", "ash_imp", "cinder_beetle", "iron_sentry", "fog_wraith", "rime_wolf", "dune_skink",
    "abyss_crab", "star_shade",
)

/** Normalises a display name ("Ashen Stalker Mk.3") to a sprite key ("ashen_stalker"). */
fun creatureKey(name: String): String = name.lowercase()
    .replace(Regex("\\s+mk\\.?\\s*\\d+$"), "")
    .replace(Regex("[^a-z0-9]+"), "_")
    .trim('_')

/**
 * Draws a creature fitted to [fill] of this DrawScope at an integer pixel scale, standing on the
 * bottom edge. Sprites face left; [mirror] turns them right. [flash] whitens it (hit), and
 * [silhouette] paints it as a solid shape (undiscovered forms). [t] drives a slow idle breath.
 */
fun DrawScope.drawCreature(
    key: String,
    t: Float,
    flash: Float = 0f,
    mirror: Boolean = false,
    fill: Float = 1f,
    shadow: Boolean = true,
    silhouette: Color? = null,
) {
    val s = BeastArt.get(key) ?: return
    val bw = s.w + 2f
    val bh = s.h + 2f
    val u = floor(minOf(size.width * fill / bw, size.height * fill / bh)).coerceAtLeast(1f)
    val dw = s.w * u
    val breath = if (sin(t * 2.4f + key.length) > 0.2f) u else 0f
    val dh = s.h * u - breath
    val left = floor((size.width - dw) / 2f)
    val bottom = floor(size.height - u - (size.height * (1f - fill)) * 0.15f)
    val top = bottom - dh

    if (shadow && silhouette == null) {
        drawOval(Color.Black.copy(alpha = 0.32f), Offset(left + dw * 0.12f, bottom - u * 1.5f), Size(dw * 0.76f, u * 3f))
    }
    fun paint(filter: ColorFilter?) = drawImage(
        image = s.image,
        srcOffset = IntOffset(s.x0, s.y0),
        srcSize = IntSize(s.w, s.h),
        dstOffset = IntOffset(left.toInt(), top.toInt()),
        dstSize = IntSize(dw.toInt(), dh.toInt()),
        colorFilter = filter,
        filterQuality = FilterQuality.None,
    )
    scale(if (mirror) -1f else 1f, 1f, pivot = Offset(left + dw / 2f, 0f)) {
        if (silhouette != null) {
            paint(ColorFilter.tint(silhouette, BlendMode.SrcIn))
        } else {
            paint(null)
            if (flash > 0.01f) paint(ColorFilter.tint(Color.White.copy(alpha = (flash * 0.85f).coerceIn(0f, 1f)), BlendMode.SrcAtop))
        }
    }
}

@Composable
private fun rememberCreatureClock(animated: Boolean): Float {
    if (!animated) return 0f
    val tr = rememberInfiniteTransition(label = "creature_clock")
    val t by tr.animateFloat(0f, 60f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "creature_t")
    return t
}

/** A named creature (foe, boss or companion species). */
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

/** Sprite key for a companion's current form (affinity × stage). */
fun companionKey(m: MonsterCompanion): String = creatureKey(GameCatalog.monsterSpeciesName(m.affinity, m.stage))

/** The player's companion in its current evolved form, facing right by default. */
@Composable
fun CompanionSprite(monster: MonsterCompanion, size: Dp, modifier: Modifier = Modifier, mirror: Boolean = true, animated: Boolean = true) {
    val key = companionKey(monster)
    val t = rememberCreatureClock(animated)
    Canvas(modifier.size(size)) { drawCreature(key, t, mirror = mirror, fill = stageFill(monster.stage)) }
}
