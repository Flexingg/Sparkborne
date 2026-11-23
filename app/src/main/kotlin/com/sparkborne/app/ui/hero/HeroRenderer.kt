package com.sparkborne.app.ui.hero

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.model.ArmorSlot
import com.sparkborne.game.model.HeroAppearance
import com.sparkborne.game.model.PlayerState
import com.sparkborne.game.model.WeaponClass
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// =============================================================================================
// Palettes & option names (indices match HeroAppearance)
// =============================================================================================

object HeroPalette {
    val skin = listOf(0xFFF6D7B8, 0xFFEBC09A, 0xFFD49A6A, 0xFFA86F45, 0xFF7A4B2C, 0xFF4E301C).map(::Color)
    val skinNames = listOf("Porcelain", "Fair", "Honey", "Bronze", "Umber", "Ebony")
    val hair = listOf(
        0xFF2A2222, 0xFF5A3A22, 0xFF8A3B1E, 0xFFE1C16E, 0xFFC8642B,
        0xFFE8E8EE, 0xFF6C8FB8, 0xFFB3263A, 0xFF3E9E5C, 0xFF7A4FB8,
    ).map(::Color)
    val hairNames = listOf("Raven", "Chestnut", "Auburn", "Golden", "Copper", "Silver", "Frost", "Crimson", "Moss", "Violet")
    val eyes = listOf(0xFF5A3A22, 0xFF3F7FD8, 0xFF3E9E5C, 0xFF8A93A3, 0xFFE0A030, 0xFF9B6BE0, 0xFFD03A3A).map(::Color)
    val eyeNames = listOf("Hazel", "Sky", "Jade", "Storm", "Amber", "Arcane", "Ember")
    val capes: List<Color?> = listOf(null) + listOf(0xFFB3263A, 0xFF2F5FB8, 0xFF2E7D4F, 0xFFD9A436, 0xFF6B3FA0, 0xFF2A2A34, 0xFFE6E2D6).map(::Color)
    val capeNames = listOf("None", "Crimson", "Royal", "Forest", "Gilded", "Mystic", "Shadow", "Ivory")
    val tunics = listOf(0xFFCFC2A0, 0xFF5E7A4A, 0xFF5A6478, 0xFF7A2E3A, 0xFF2E3F6E, 0xFF7A5A3A).map(::Color)
    val tunicNames = listOf("Linen", "Moss", "Slate", "Wine", "Navy", "Umber")
    val buildNames = listOf("Slim", "Average", "Broad")
    val hairStyleNames = listOf("Bald", "Cropped", "Spiky", "Swept", "Long", "Ponytail", "Braids", "Mohawk", "Topknot", "Curly")
    val facialHairNames = listOf("None", "Stubble", "Moustache", "Full Beard", "Braided Beard", "Goatee")
    val faceMarkNames = listOf("None", "Scar", "Freckles", "War Paint", "Eyepatch", "Rune Tattoo")

    init {
        require(skin.size == HeroAppearance.SKIN_TONES && skinNames.size == skin.size)
        require(hair.size == HeroAppearance.HAIR_COLORS && hairNames.size == hair.size)
        require(eyes.size == HeroAppearance.EYE_COLORS && eyeNames.size == eyes.size)
        require(capes.size == HeroAppearance.CAPES && capeNames.size == capes.size)
        require(tunics.size == HeroAppearance.TUNICS && tunicNames.size == tunics.size)
        require(buildNames.size == HeroAppearance.BUILDS)
        require(hairStyleNames.size == HeroAppearance.HAIR_STYLES)
        require(facialHairNames.size == HeroAppearance.FACIAL_HAIR)
        require(faceMarkNames.size == HeroAppearance.FACE_MARKS)
    }
}

/** Everything that determines how the hero looks. */
data class HeroLook(
    val appearance: HeroAppearance,
    val weaponId: String? = null,
    val helmId: String? = null,
    val chestId: String? = null,
    val legsId: String? = null,
    val relicId: String? = null,
) {
    /** The same hero trying on [itemId] (weapon or armor) — used for gear previews. */
    fun tryOn(itemId: String): HeroLook {
        if (GameCatalog.weapons.any { it.id == itemId }) return copy(weaponId = itemId)
        val a = GameCatalog.armorById(itemId) ?: return this
        return when (a.slot) {
            ArmorSlot.HELM -> copy(helmId = itemId)
            ArmorSlot.CHEST -> copy(chestId = itemId)
            ArmorSlot.LEGS -> copy(legsId = itemId)
            ArmorSlot.RELIC -> copy(relicId = itemId)
        }
    }

    fun bare(): HeroLook = HeroLook(appearance)

    val gearKey: String get() = "$weaponId|$helmId|$chestId|$legsId|$relicId"
}

fun heroLookOf(p: PlayerState): HeroLook = HeroLook(
    appearance = p.appearance,
    weaponId = p.equippedWeaponId,
    helmId = p.equippedArmor[ArmorSlot.HELM],
    chestId = p.equippedArmor[ArmorSlot.CHEST],
    legsId = p.equippedArmor[ArmorSlot.LEGS],
    relicId = p.equippedArmor[ArmorSlot.RELIC],
)

// =============================================================================================
// Pixel buffer
// =============================================================================================

private const val BW = 40
private const val BH = 50
private const val OX = 4
private const val OY = 4

private class HeroBuf {
    val px = arrayOfNulls<Color>(BW * BH)

    fun set(x: Int, y: Int, c: Color) {
        val bx = x + OX
        val by = y + OY
        if (bx in 0 until BW && by in 0 until BH) px[by * BW + bx] = c
    }

    fun rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Color) {
        for (y in y0..y1) for (x in x0..x1) set(x, y, c)
    }

    fun h(x0: Int, x1: Int, y: Int, c: Color) = rect(x0, y, x1, y, c)
    fun v(x: Int, y0: Int, y1: Int, c: Color) = rect(x, y0, x, y1, c)
}

private fun Color.dk(f: Float = 0.25f) = this.mixWith(Color.Black, f)
private fun Color.lt(f: Float = 0.25f) = this.mixWith(Color.White, f)

private data class Geo(val l: Int, val r: Int, val legW: Int) {
    val armL0 get() = l - 3
    val armR0 get() = r + 1
    val handLc get() = l - 2
    val handRc get() = r + 2
}

private fun geoFor(build: Int): Geo {
    val w = when (build) { 0 -> 10; 2 -> 14; else -> 12 }
    return Geo(l = 16 - w / 2, r = 15 + w / 2, legW = w / 2 - 1)
}

/** Particles & glows drawn after the outlined sprite (in body coordinates). */
private class Fx {
    val dots = mutableListOf<Triple<Float, Float, Color>>()
    val glows = mutableListOf<Triple<Float, Float, Pair<Float, Color>>>()
    fun dot(x: Float, y: Float, c: Color) { dots += Triple(x, y, c) }
    fun glow(x: Float, y: Float, r: Float, c: Color) { glows += Triple(x, y, r to c) }
}

// =============================================================================================
// Composition
// =============================================================================================

private fun composeHero(look: HeroLook, t: Float, buf: HeroBuf, fx: Fx) {
    val a = look.appearance
    val g = geoFor(a.build)
    val skin = HeroPalette.skin.getOrElse(a.skinTone) { HeroPalette.skin[1] }
    val hair = HeroPalette.hair.getOrElse(a.hairColor) { HeroPalette.hair[1] }
    val tunic = HeroPalette.tunics.getOrElse(a.tunic) { HeroPalette.tunics[0] }
    val cape = HeroPalette.capes.getOrNull(a.cape)
    val breathe = if ((t * 0.8f) % 1f < 0.5f) 0 else -1
    val blink = (t % 3.7f) > 3.55f

    if (cape != null) drawCape(buf, g, cape, t)
    drawHairBack(buf, a.hairStyle, hair, breathe)
    drawLegsBase(buf, g, tunic)
    drawGreaves(buf, g, look.legsId, t)
    drawTorsoBase(buf, g, tunic, skin)
    drawChest(buf, g, look.chestId)
    drawHead(buf, skin, hair, a, breathe, blink)
    drawFacialHair(buf, a.facialHair, hair, skin, breathe, look.chestId)
    val hideHairFront = look.helmId != null && look.helmId != "arm_glacier_crown"
    if (!hideHairFront) drawHairFront(buf, a.hairStyle, hair, breathe)
    drawHelm(buf, look.helmId, breathe, t, fx)
    if (cape != null) {
        buf.set(g.l + 1, 16, GoldTrim)
        buf.set(g.r - 1, 16, GoldTrim)
    }
    drawRelic(buf, look.relicId, t, fx)
    drawWeapon(buf, g, look.weaponId, t, fx)
    drawHands(buf, g, skin, look.chestId)
}

private fun drawCape(b: HeroBuf, g: Geo, c: Color, t: Float) {
    val sway = (sin(t * 2.2f) * 1.2f).roundToInt()
    for (y in 15..39) {
        val off = if (y >= 34) sway * (y - 33) / 3 else 0
        val x0 = g.l - 3 + off
        val x1 = g.r + 3 + off
        b.h(x0, x1, y, c.dk(0.35f))
        b.set(x0, y, c.dk(0.5f))
        b.set(x1, y, c.dk(0.5f))
    }
    // visible side flaps + hem
    for (y in 17..38) {
        b.set(g.l - 3, y, c)
        b.set(g.r + 3, y, c.dk(0.15f))
    }
    b.h(g.l - 3 + sway, g.r + 3 + sway, 40, c.dk(0.55f))
}

private fun drawLegsBase(b: HeroBuf, g: Geo, tunic: Color) {
    val pants = tunic.mixWith(Color(0xFF3A2E28), 0.6f)
    b.rect(g.l, 29, g.r, 30, pants)
    val lx0 = 15 - g.legW
    val rx1 = 16 + g.legW
    b.rect(lx0, 29, 14, 37, pants)
    b.rect(17, 29, rx1, 37, pants)
    b.v(14, 29, 37, pants.dk(0.2f))
    b.v(rx1, 29, 37, pants.dk(0.25f))
    val boot = Color(0xFF4A3020)
    b.rect(lx0, 38, 14, 40, boot)
    b.rect(17, 38, rx1, 40, boot)
    b.h(lx0 - 1, 14, 41, boot.dk(0.2f))
    b.h(17, rx1 + 1, 41, boot.dk(0.2f))
    b.h(lx0, 14, 38, boot.lt(0.2f))
    b.h(17, rx1, 38, boot.lt(0.2f))
}

private fun drawTorsoBase(b: HeroBuf, g: Geo, tunic: Color, skin: Color) {
    b.rect(g.l, 16, g.r, 28, tunic)
    b.v(g.r, 16, 28, tunic.dk(0.2f))
    b.v(g.l, 17, 27, tunic.lt(0.12f))
    // collar V
    b.set(15, 16, skin.dk(0.15f)); b.set(16, 16, skin.dk(0.15f)); b.set(15, 17, tunic.dk(0.3f)); b.set(16, 17, tunic.dk(0.3f))
    // belt
    b.h(g.l, g.r, 26, Color(0xFF5A3A22))
    b.set(15, 26, GoldTrim); b.set(16, 26, GoldTrim)
    // sleeves + forearms
    for (x0 in listOf(g.armL0, g.armR0)) {
        b.rect(x0, 16, x0 + 2, 23, tunic)
        b.v(if (x0 < 16) x0 else x0 + 2, 16, 23, tunic.dk(0.2f))
        b.rect(x0, 24, x0 + 2, 27, skin)
        b.v(if (x0 < 16) x0 else x0 + 2, 24, 27, skin.dk(0.15f))
    }
}

private fun drawHands(b: HeroBuf, g: Geo, skin: Color, chestId: String?) {
    val gauntlet = chestId == "arm_vanguard_plate"
    val c = if (gauntlet) Color(0xFFA9B3C2) else skin
    for (x0 in listOf(g.armL0, g.armR0)) {
        b.rect(x0, 28, x0 + 2, 29, c)
        b.h(x0, x0 + 2, 29, c.dk(0.2f))
    }
}

private fun drawHead(b: HeroBuf, skin: Color, hair: Color, a: HeroAppearance, dy: Int, blink: Boolean) {
    val sh = skin.dk(0.22f)
    val rows = listOf(3 to (12..19), 4 to (11..20)) + (5..12).map { it to (10..21) } + listOf(13 to (11..20), 14 to (12..19))
    rows.forEach { (y, xs) -> b.h(xs.first, xs.last, y + dy, skin) }
    (5..12).forEach { b.set(21, it + dy, sh) }
    b.set(20, 4 + dy, sh); b.set(20, 13 + dy, sh); b.set(19, 14 + dy, sh)
    (6..8).forEach { b.set(10, it + dy, skin.lt(0.15f)) }
    // ears
    b.set(9, 9 + dy, skin); b.set(9, 10 + dy, sh); b.set(22, 9 + dy, sh); b.set(22, 10 + dy, sh)
    // neck
    b.h(14, 17, 15 + dy, sh); b.h(14, 17, 15, sh)
    // brows
    val brow = hair.dk(0.35f)
    b.h(12, 13, 7 + dy, brow); b.h(18, 19, 7 + dy, brow)
    // eyes
    val iris = HeroPalette.eyes.getOrElse(a.eyeColor) { HeroPalette.eyes[0] }
    if (blink) {
        b.h(12, 13, 10 + dy, Color(0xFF2A1A14)); b.h(18, 19, 10 + dy, Color(0xFF2A1A14))
    } else {
        b.set(12, 9 + dy, Color.White); b.set(13, 9 + dy, iris); b.set(12, 10 + dy, iris.dk(0.2f)); b.set(13, 10 + dy, Color(0xFF14101A))
        b.set(19, 9 + dy, Color.White); b.set(18, 9 + dy, iris); b.set(19, 10 + dy, iris.dk(0.2f)); b.set(18, 10 + dy, Color(0xFF14101A))
    }
    // nose & mouth
    b.set(15, 11 + dy, sh); b.set(16, 11 + dy, sh.dk(0.1f))
    b.h(14, 17, 13 + dy, skin.mixWith(Color(0xFF8A3B3B), 0.45f))
    // marks
    when (a.faceMark) {
        1 -> { b.set(18, 11 + dy, Color(0xFFE3A0A0)); b.set(19, 12 + dy, Color(0xFFE3A0A0)); b.set(20, 13 + dy, Color(0xFFE3A0A0)); b.set(19, 11 + dy, skin.dk(0.3f)) }
        2 -> listOf(12 to 11, 13 to 12, 11 to 12, 18 to 12, 19 to 11, 20 to 12).forEach { (x, y) -> b.set(x, y + dy, skin.mixWith(Color(0xFF6B3A1E), 0.4f)) }
        3 -> { b.h(11, 13, 11 + dy, Color(0xFFC0392B)); b.h(18, 20, 11 + dy, Color(0xFFC0392B)); b.v(15, 5 + dy, 6 + dy, Color(0xFFC0392B)); b.v(16, 5 + dy, 6 + dy, Color(0xFFC0392B)) }
        4 -> { b.rect(11, 8 + dy, 14, 10 + dy, Color(0xFF15121A)); b.h(10, 21, 7 + dy, Color(0xFF2A2230)); b.set(13, 8 + dy, Color(0xFF3A3440)) }
        5 -> listOf(15 to 4, 16 to 5, 15 to 6, 11 to 11, 11 to 12, 20 to 11).forEach { (x, y) -> b.set(x, y + dy, Color(0xFF4FA3E0)) }
    }
}

private fun drawFacialHair(b: HeroBuf, style: Int, hair: Color, skin: Color, dy: Int, chestId: String?) {
    val c = hair.dk(0.1f)
    val sh = hair.dk(0.35f)
    fun moustache() { b.h(13, 18, 12 + dy, c); b.set(13, 13 + dy, c); b.set(18, 13 + dy, c) }
    when (style) {
        1 -> listOf(13 to 12, 15 to 14, 17 to 12, 18 to 13, 14 to 14, 16 to 12, 12 to 13, 19 to 13).forEach { (x, y) -> b.set(x, y + dy, skin.mixWith(hair, 0.5f)) }
        2 -> moustache()
        3, 4 -> {
            b.rect(10, 11 + dy, 11, 12 + dy, c); b.rect(20, 11 + dy, 21, 12 + dy, sh)
            b.h(11, 20, 14 + dy, c)
            b.h(11, 13, 13 + dy, c); b.h(18, 20, 13 + dy, c)
            b.h(12, 19, 15 + dy, c); b.h(13, 18, 16 + dy, c); b.h(14, 17, 17 + dy, sh)
            b.v(20, 13 + dy, 14 + dy, sh)
            moustache()
            if (style == 4) {
                b.rect(15, 18 + dy, 16, 22 + dy, c)
                b.h(15, 16, 20 + dy, GoldTrim)
                b.set(16, 22 + dy, sh)
            }
        }
        5 -> { b.h(14, 17, 12 + dy, c); b.rect(14, 14 + dy, 17, 15 + dy, c); b.h(15, 16, 16 + dy, sh) }
    }
}

private fun drawHairBack(b: HeroBuf, style: Int, c: Color, dy: Int) {
    val sh = c.dk(0.3f)
    when (style) {
        4 -> { b.rect(8, 5 + dy, 23, 22 + dy, sh); b.rect(9, 5 + dy, 22, 21 + dy, c.dk(0.15f)) }
        5 -> { b.rect(22, 6 + dy, 24, 19 + dy, c.dk(0.1f)); b.v(24, 7 + dy, 19 + dy, sh); b.set(23, 20 + dy, sh) }
        9 -> b.rect(8, 3 + dy, 23, 12 + dy, sh)
    }
}

private fun capHair(b: HeroBuf, c: Color, dy: Int) {
    val lt = c.lt(0.25f)
    val sh = c.dk(0.3f)
    b.h(12, 19, 2 + dy, c); b.h(11, 20, 3 + dy, c); b.h(10, 21, 4 + dy, c)
    b.h(10, 12, 5 + dy, c); b.h(19, 21, 5 + dy, sh)
    b.set(10, 6 + dy, c); b.set(21, 6 + dy, sh)
    b.set(13, 5 + dy, c); b.set(14, 5 + dy, c); b.set(17, 5 + dy, c)
    b.h(13, 14, 3 + dy, lt)
    b.h(18, 21, 4 + dy, sh)
}

private fun drawHairFront(b: HeroBuf, style: Int, c: Color, dy: Int) {
    val lt = c.lt(0.25f)
    val sh = c.dk(0.3f)
    when (style) {
        0 -> b.set(13, 4 + dy, Color.White.copy(alpha = 0.6f))
        1 -> capHair(b, c, dy)
        2 -> {
            capHair(b, c, dy)
            listOf(11, 14, 17, 20).forEachIndexed { i, x -> b.v(x, 0 + dy + (i % 2), 2 + dy, if (i % 2 == 0) c else lt); b.set(x + 1, 1 + dy + (i % 2), sh) }
        }
        3 -> {
            capHair(b, c, dy)
            b.h(10, 17, 5 + dy, c); b.h(10, 14, 6 + dy, c); b.rect(10, 7 + dy, 11, 8 + dy, c); b.h(14, 17, 5 + dy, lt)
        }
        4 -> {
            capHair(b, c, dy)
            b.rect(9, 5 + dy, 10, 14 + dy, c); b.rect(21, 5 + dy, 22, 14 + dy, sh); b.set(9, 15 + dy, c); b.set(22, 15 + dy, sh)
        }
        5 -> { capHair(b, c, dy); b.rect(21, 5 + dy, 22, 7 + dy, c); b.set(22, 6 + dy, GoldTrim) }
        6 -> {
            capHair(b, c, dy)
            for (side in listOf(9, 21)) {
                for (y in 8..19) b.rect(side, y + dy, side + 1, y + dy, if (y % 2 == 0) c else sh)
                b.h(side, side + 1, 20 + dy, GoldTrim)
                b.set(side, 21 + dy, c)
            }
        }
        7 -> { b.rect(14, -1 + dy, 17, 5 + dy, c); b.v(17, -1 + dy, 5 + dy, sh); b.v(14, 0 + dy, 3 + dy, lt) }
        8 -> {
            capHair(b, c, dy)
            b.rect(14, -2 + dy, 17, 1 + dy, c); b.h(15, 16, -3 + dy, c); b.h(14, 17, 1 + dy, GoldTrim); b.set(14, -1 + dy, lt)
        }
        9 -> {
            for (y in 1..7) for (x in 9..22) {
                val inside = !(y == 1 && (x < 11 || x > 20)) && !(y == 7 && x in 12..19)
                if (inside) b.set(x, y + dy, if ((x + y) % 3 == 0) sh else if ((x * y) % 5 == 0) lt else c)
            }
            b.rect(9, 8 + dy, 10, 11 + dy, c); b.rect(21, 8 + dy, 22, 11 + dy, sh)
        }
    }
}

// =============================================================================================
// Gear — every item has its own look
// =============================================================================================

private val IRON = Color(0xFF8C96A6)
private val STEEL = Color(0xFFA9B3C2)
private val LEATHER = Color(0xFF8A5A34)
private val WOOD = Color(0xFF8A6238)

private fun drawHelm(b: HeroBuf, id: String?, dy: Int, t: Float, fx: Fx) {
    when (id) {
        null -> Unit
        "arm_glacier_crown" -> {
            val ice = Color(0xFFBFE6F5)
            val iceSh = Color(0xFF7FB8D6)
            b.h(10, 21, 3 + dy, ice); b.h(10, 21, 4 + dy, iceSh)
            listOf(10 to 1, 13 to 0, 18 to 0, 21 to 1).forEach { (x, top) -> b.v(x, top + dy, 2 + dy, ice) }
            b.rect(15, -2 + dy, 16, 2 + dy, ice); b.v(16, -2 + dy, 2 + dy, iceSh)
            b.h(15, 16, 3 + dy, Color(0xFF3FC6E8))
            val shimmer = 10 + ((t * 6f).toInt() % 12)
            b.set(shimmer, 3 + dy, Color.White)
            if (((t * 3f) % 1f) < 0.5f) fx.dot(16f, -3f + dy, Color(0xFFE8FAFF))
        }
        else -> { // Ironbark Helm (and fallback)
            val iron = IRON
            b.h(12, 19, 1 + dy, iron); b.h(10, 21, 2 + dy, iron)
            b.rect(9, 3 + dy, 22, 6 + dy, iron)
            b.rect(9, 7 + dy, 10, 11 + dy, iron.dk(0.1f)); b.rect(21, 7 + dy, 22, 11 + dy, iron.dk(0.3f))
            b.h(9, 22, 7 + dy, Color(0xFF6B4A2C))
            listOf(11, 15, 19).forEach { b.set(it, 7 + dy, Color(0xFF4A3220)) }
            b.v(15, 8 + dy, 11 + dy, iron.dk(0.15f)); b.v(16, 8 + dy, 11 + dy, iron.dk(0.3f))
            b.h(12, 15, 2 + dy, iron.lt(0.4f)); b.v(10, 3 + dy, 5 + dy, iron.lt(0.25f)); b.v(22, 3 + dy, 6 + dy, iron.dk(0.3f))
            listOf(11 to 4, 20 to 4, 15 to 3).forEach { (x, y) -> b.set(x, y + dy, iron.lt(0.55f)) }
        }
    }
}

private fun drawChest(b: HeroBuf, g: Geo, id: String?) {
    when (id) {
        null -> Unit
        "arm_vanguard_plate" -> {
            val st = STEEL
            b.rect(g.l, 16, g.r, 27, st)
            b.v(15, 17, 25, st.lt(0.35f)); b.v(16, 17, 25, st.lt(0.15f))
            b.v(g.r, 16, 27, st.dk(0.3f)); b.v(g.r - 1, 17, 27, st.dk(0.12f))
            b.h(g.l, g.r, 21, st.dk(0.25f)); b.h(g.l, g.r, 24, st.dk(0.25f))
            b.h(g.l, g.r, 16, GoldTrim); b.h(g.l, g.r, 27, GoldTrim)
            // tabard
            b.rect(14, 27, 17, 34, Color(0xFFB3263A)); b.v(17, 27, 34, Color(0xFF7A1A28)); b.h(14, 17, 34, GoldTrim)
            // pauldrons
            for ((x0, x1, shade) in listOf(Triple(g.l - 4, g.l + 1, false), Triple(g.r - 1, g.r + 4, true))) {
                b.rect(x0, 15, x1, 18, if (shade) st.dk(0.15f) else st)
                b.h(x0 + 1, x1 - 1, 14, if (shade) st.dk(0.1f) else st.lt(0.2f))
                b.h(x0, x1, 18, GoldTrim)
            }
            // plated arms
            for (x0 in listOf(g.armL0, g.armR0)) {
                b.rect(x0, 19, x0 + 2, 23, st.dk(0.1f))
                b.rect(x0, 24, x0 + 2, 27, st)
                b.h(x0, x0 + 2, 24, GoldTrim)
            }
        }
        else -> { // Scout's Leather Jerkin (and fallback)
            val le = LEATHER
            b.rect(g.l, 16, 14, 27, le); b.rect(17, 16, g.r, 27, le.dk(0.1f))
            b.v(g.l, 17, 26, le.lt(0.2f)); b.v(g.r, 16, 27, le.dk(0.3f))
            listOf(17, 19, 21).forEach { y -> b.set(15, y, Color(0xFF3A2416)); b.set(16, y + 1, Color(0xFF3A2416)) }
            b.h(g.l, g.r, 26, Color(0xFF3A2416)); b.set(g.r - 2, 27, Color(0xFF6B4A2C)); b.rect(g.r - 2, 27, g.r, 28, Color(0xFF6B4A2C))
            b.set(g.l + 2, 18, Color(0xFFC89A5E)); b.set(g.r - 2, 18, Color(0xFFC89A5E))
            for (x0 in listOf(g.armL0, g.armR0)) {
                b.rect(x0, 24, x0 + 2, 27, le.dk(0.15f))
                b.h(x0, x0 + 2, 25, Color(0xFFC89A5E))
            }
        }
    }
}

private fun drawGreaves(b: HeroBuf, g: Geo, id: String?, t: Float) {
    val lx0 = 15 - g.legW
    val rx1 = 16 + g.legW
    when (id) {
        null -> Unit
        "arm_dune_greaves" -> {
            val sand = Color(0xFFD8C08A)
            for (y in 29..39) {
                val c = if ((y % 2) == 0) sand else sand.dk(0.12f)
                b.h(lx0, 14, y, c); b.h(17, rx1, y, c)
            }
            b.rect(g.l, 29, g.r, 30, sand.dk(0.08f))
            val bronze = Color(0xFFB07A3A)
            b.rect(lx0 + 1, 33, 13, 37, bronze); b.rect(18, 33, rx1 - 1, 37, bronze.dk(0.1f))
            b.v(lx0 + 1, 33, 37, bronze.lt(0.3f))
            b.h(lx0 - 1, 14, 41, Color(0xFF6B4A2C)); b.h(17, rx1 + 1, 41, Color(0xFF6B4A2C))
            b.rect(lx0, 39, 14, 40, sand.dk(0.2f)); b.rect(17, 39, rx1, 40, sand.dk(0.25f))
            val sway = (sin(t * 3f) * 1.0f).roundToInt()
            val teal = Color(0xFF3FA5A0)
            b.v(g.l + 1, 29, 34, teal); b.set(g.l + 1 + sway, 35, teal)
            b.v(g.r - 1, 29, 33, teal.dk(0.2f)); b.set(g.r - 1 + sway, 34, teal.dk(0.2f))
        }
        else -> { // Strider Greaves (and fallback)
            val cloth = Color(0xFF4E5A48)
            b.rect(lx0, 29, 14, 34, cloth); b.rect(17, 29, rx1, 34, cloth.dk(0.1f))
            b.rect(g.l, 29, g.r, 30, cloth)
            b.rect(lx0, 31, 14, 32, LEATHER); b.rect(17, 31, rx1, 32, LEATHER.dk(0.1f))
            val bootC = Color(0xFF9A6A3C)
            b.rect(lx0, 35, 14, 40, bootC); b.rect(17, 35, rx1, 40, bootC.dk(0.12f))
            b.h(lx0, 14, 35, bootC.lt(0.3f)); b.h(17, rx1, 35, bootC.lt(0.2f))
            listOf(37, 39).forEach { y -> b.set(lx0 + 2, y, Color(0xFF3A2416)); b.set(rx1 - 2, y, Color(0xFF3A2416)) }
            b.h(lx0 - 1, 14, 41, bootC.dk(0.35f)); b.h(17, rx1 + 1, 41, bootC.dk(0.35f))
        }
    }
}

private fun drawRelic(b: HeroBuf, id: String?, t: Float, fx: Fx) {
    when (id) {
        null -> Unit
        "arm_astral_relic" -> {
            val v = Color(0xFFB57CFF)
            val bob = if ((t * 1.2f) % 1f < 0.5f) 0 else -1
            b.set(14, 18, GoldTrim); b.set(17, 18, GoldTrim); b.set(15, 19, GoldTrim); b.set(16, 19, GoldTrim)
            b.rect(15, 20 + bob, 16, 21 + bob, v)
            b.h(15, 16, 19 + bob, v.lt(0.4f)); b.set(14, 21 + bob, v.dk(0.2f)); b.set(17, 20 + bob, v.dk(0.2f)); b.h(15, 16, 22 + bob, v.dk(0.3f))
            b.set(15, 20 + bob, Color.White)
            fx.glow(16f, 21f, 4f, v.copy(alpha = 0.45f))
            val a = t * 3f
            fx.dot(16f + kotlin.math.cos(a) * 4f, 21f + sin(a) * 2f, v.lt(0.5f))
        }
        else -> { // Emberbound Relic (and fallback)
            val o = Color(0xFFFF7A3D)
            b.set(13, 17, GoldTrim); b.set(14, 18, GoldTrim); b.set(18, 17, GoldTrim); b.set(17, 18, GoldTrim)
            b.rect(15, 19, 16, 20, o); b.set(15, 19, o.lt(0.5f)); b.h(15, 16, 21, GoldTrim)
            val pulse = 0.35f + 0.25f * sin(t * 4f)
            fx.glow(16f, 20f, 3.5f, o.copy(alpha = pulse))
            val ph = (t * 0.9f) % 1f
            fx.dot(15.5f + sin(t * 5f) * 1.2f, 19f - ph * 7f, o.copy(alpha = 1f - ph))
        }
    }
}

private fun weaponClassFor(id: String?): WeaponClass =
    GameCatalog.weapons.firstOrNull { it.id == id }?.weaponClass ?: WeaponClass.GREATSWORD

private fun drawWeapon(b: HeroBuf, g: Geo, id: String?, t: Float, fx: Fx) {
    val hc = g.handRc
    when (id) {
        "wpn_ember_claymore" -> {
            val core = Color(0xFF3A3A44)
            val edge = Color(0xFFFF7A3D)
            b.rect(hc - 1, 4, hc + 1, 25, core); b.v(hc - 1, 4, 25, edge); b.v(hc + 1, 4, 25, edge.dk(0.2f))
            b.set(hc, 3, edge.lt(0.4f)); b.set(hc, 2, Color(0xFFFFD08A))
            b.h(hc - 4, hc + 4, 26, Color(0xFFB3263A)); b.set(hc - 4, 25, Color(0xFFB3263A)); b.set(hc + 4, 25, Color(0xFFB3263A))
            b.h(hc - 3, hc + 3, 26, GoldTrim.dk(0.1f))
            b.v(hc, 27, 30, Color(0xFF5A1A14)); b.rect(hc - 1, 31, hc + 1, 31, edge)
            for (i in 0 until 3) {
                val ph = (t * 0.8f + i / 3f) % 1f
                fx.dot(hc + sin(t * 4f + i * 2f) * 2f, 24f - ph * 22f, Color(0xFFFFB04A).copy(alpha = 1f - ph))
            }
            fx.glow(hc.toFloat(), 14f, 5f, edge.copy(alpha = 0.18f))
        }
        "wpn_ash_spear" -> {
            for (y in 2..40) b.set(hc, y, if (y % 5 == 0) WOOD.dk(0.25f) else WOOD)
            val st = Color(0xFFC9D2DE)
            b.set(hc, -4, st.lt(0.4f)); b.rect(hc - 1, -3, hc + 1, 0, st); b.v(hc + 1, -3, 0, st.dk(0.25f)); b.set(hc, 1, st)
            b.rect(hc - 1, 2, hc + 1, 3, Color(0xFF4A3220))
        }
        "wpn_frostfang_halberd" -> {
            val shaft = Color(0xFF6E8AA0)
            b.v(hc, 0, 40, shaft); b.set(hc, 20, shaft.lt(0.3f)); b.set(hc, 30, shaft.lt(0.3f))
            val ice = Color(0xFF9EE0F5)
            b.v(hc, -5, -1, Color(0xFFE8FAFF))
            b.rect(hc + 1, 1, hc + 3, 8, ice); b.v(hc + 4, 2, 7, Color.White); b.set(hc + 1, 0, ice); b.set(hc + 1, 9, ice)
            b.set(hc - 1, 3, ice.dk(0.2f)); b.set(hc - 2, 2, ice.dk(0.2f))
            b.rect(hc - 1, 9, hc + 1, 10, Color(0xFF3A4A5A))
            for (i in 0 until 3) {
                val ph = (t * 0.5f + i / 3f) % 1f
                fx.dot(hc + 2f + sin(i * 2f + t) * 3f, 2f + ph * 12f, Color(0xFFE8FAFF).copy(alpha = 1f - ph))
            }
        }
        "wpn_twin_fangs" -> {
            // reverse-grip fangs angled out and down from each fist
            for ((c, dir) in listOf(g.handLc to -1, hc to 1)) {
                b.set(c, 27, Color(0xFF4A3220))
                b.h(c - 1, c + 1, 30, IRON.dk(0.35f))
                for (k in 0..7) {
                    val x = c + dir * (1 + (k * 2) / 3)
                    val y = 31 + k
                    b.set(x, y, IRON.lt(0.25f))
                    b.set(x + dir, y, IRON.dk(0.2f))
                }
                b.set(c + dir * 6, 39, IRON.lt(0.6f))
            }
        }
        "wpn_solaris_scimitars" -> {
            val gold = Color(0xFFF2C14E)
            for ((c, dir) in listOf(hc to 1, g.handLc to -1)) {
                val pts = listOf(0 to 27, 0 to 26, 1 to 25, 1 to 24, 2 to 23, 2 to 22, 3 to 21, 3 to 20, 4 to 19)
                pts.forEach { (dx, y) -> b.set(c + dx * dir, y, gold); b.set(c + (dx + 1) * dir, y, gold.lt(0.45f)) }
                b.set(c + 5 * dir, 18, Color(0xFFFFF1B8))
                b.h(c - 1, c + 1, 28, Color(0xFFB3263A))
            }
            if (((t * 0.7f) % 1f) < 0.12f) fx.dot(hc + 5f, 18f, Color.White)
        }
        "wpn_titan_maul" -> {
            val shaft = Color(0xFF5A3A22)
            b.v(hc, 9, 33, shaft); b.set(hc, 15, IRON); b.set(hc, 22, IRON)
            val head = Color(0xFF6E7480)
            b.rect(hc - 3, 2, hc + 3, 8, head)
            b.v(hc - 3, 2, 8, Color(0xFFB07A3A)); b.v(hc + 3, 2, 8, Color(0xFFB07A3A).dk(0.2f))
            b.h(hc - 2, hc + 2, 2, head.lt(0.3f)); b.h(hc - 2, hc + 2, 8, head.dk(0.3f))
            b.v(hc, 0, 1, head.lt(0.4f))
        }
        "wpn_starforged_maul" -> {
            val cosmos = Color(0xFF2A1E4A)
            val edge = Color(0xFF8A6BE0)
            b.v(hc, 10, 35, Color(0xFF1E1A2A)); listOf(16, 24, 32).forEach { b.set(hc, it, edge) }
            b.rect(hc - 4, 0, hc + 4, 9, cosmos)
            b.h(hc - 4, hc + 4, 0, edge); b.h(hc - 4, hc + 4, 9, edge.dk(0.3f)); b.v(hc - 4, 0, 9, edge.lt(0.2f)); b.v(hc + 4, 0, 9, edge.dk(0.2f))
            val stars = listOf(-2 to 2, 1 to 4, 2 to 2, -1 to 6, 3 to 7, -3 to 5, 0 to 3)
            stars.forEachIndexed { i, (dx, y) ->
                val on = ((t * 2f + i * 0.37f) % 1f) < 0.6f
                b.set(hc + dx, y, if (on) Color(0xFFFFF4C8) else edge.dk(0.2f))
            }
            fx.glow(hc.toFloat(), 4.5f, 7f, edge.copy(alpha = 0.22f))
            val a = t * 2f
            fx.dot(hc + kotlin.math.cos(a) * 6f, 4.5f + sin(a) * 6f, Color(0xFFFFF4C8))
        }
        "wpn_iron_greatsword" -> greatsword(b, hc, IRON)
        else -> when (weaponClassFor(id)) {
            WeaponClass.GREATSWORD -> greatsword(b, hc, IRON)
            WeaponClass.SPEAR -> drawWeapon(b, g, "wpn_ash_spear", t, fx)
            WeaponClass.TWIN_BLADES -> drawWeapon(b, g, "wpn_twin_fangs", t, fx)
            WeaponClass.WARHAMMER -> drawWeapon(b, g, "wpn_titan_maul", t, fx)
        }
    }
}

private fun greatsword(b: HeroBuf, hc: Int, metal: Color) {
    b.rect(hc - 1, 5, hc + 1, 25, metal)
    b.v(hc - 1, 5, 25, metal.lt(0.35f)); b.v(hc + 1, 5, 25, metal.dk(0.25f))
    b.set(hc, 4, metal.lt(0.2f)); b.set(hc, 3, metal.lt(0.5f))
    b.v(hc, 8, 24, metal.dk(0.1f))
    b.h(hc - 3, hc + 3, 26, metal.dk(0.35f)); b.set(hc - 3, 25, metal.dk(0.35f)); b.set(hc + 3, 25, metal.dk(0.35f))
    b.v(hc, 27, 30, Color(0xFF5A3A22))
    b.h(hc - 1, hc + 1, 31, metal.dk(0.2f))
}

// =============================================================================================
// Drawing
// =============================================================================================

/**
 * Draws the hero into this DrawScope. [crop] selects a buffer region (x, y, w, h) for busts;
 * null draws the whole figure. [flash] (0..1) whitens the sprite for the equip flourish.
 */
fun DrawScope.drawHero(look: HeroLook, t: Float, flash: Float = 0f, crop: IntArray? = null, shadow: Boolean = true) {
    val buf = HeroBuf()
    val fx = Fx()
    composeHero(look, t, buf, fx)

    val sx = crop?.get(0) ?: 0
    val sy = crop?.get(1) ?: 0
    val sw = crop?.get(2) ?: BW
    val sh = crop?.get(3) ?: BH
    val u = floor(minOf(size.width / sw, size.height / sh)).coerceAtLeast(1f)
    val ox = floor((size.width - u * sw) / 2f)
    val oy = floor((size.height - u * sh) / 2f)
    fun screen(bx: Float, by: Float) = Offset(ox + (bx - sx) * u, oy + (by - sy) * u)

    if (shadow && crop == null) {
        val c = screen(16f + OX, 42f + OY)
        drawOval(Color.Black.copy(alpha = 0.35f), Offset(c.x - 9 * u, c.y - u), Size(18 * u, 3 * u))
    }
    fx.glows.forEach { (x, y, rc) ->
        val c = screen(x + OX + 0.5f, y + OY + 0.5f)
        drawCircle(Brush.radialGradient(listOf(rc.second, Color.Transparent), c, rc.first * u), rc.first * u, c)
    }
    val outline = Color(0xFF0B0B10)
    for (y in sy until (sy + sh).coerceAtMost(BH)) {
        for (x in sx until (sx + sw).coerceAtMost(BW)) {
            val c = buf.px[y * BW + x]
            if (c == null) {
                val near = (x > 0 && buf.px[y * BW + x - 1] != null) || (x < BW - 1 && buf.px[y * BW + x + 1] != null) ||
                    (y > 0 && buf.px[(y - 1) * BW + x] != null) || (y < BH - 1 && buf.px[(y + 1) * BW + x] != null)
                if (near) drawRect(outline, screen(x.toFloat(), y.toFloat()), Size(u, u))
            } else {
                drawRect(if (flash > 0f) c.mixWith(Color.White, flash * 0.7f) else c, screen(x.toFloat(), y.toFloat()), Size(u, u))
            }
        }
    }
    fx.dots.forEach { (x, y, c) ->
        val p = screen(floor(x) + OX, floor(y) + OY)
        drawRect(c, p, Size(u, u))
    }
    if (flash > 0f && crop == null) {
        val c = screen(16f + OX, 22f + OY)
        val r = (1f - flash) * 26f * u + 6 * u
        for (i in 0 until 16) {
            val a = i / 16f * 6.283f
            drawRect(GoldTrim.copy(alpha = flash), Offset(c.x + kotlin.math.cos(a) * r, c.y + sin(a) * r), Size(u, u))
        }
    }
}

private val BUST_CROP = intArrayOf(OX + 3, OY - 4, 26, 26)

@Composable
private fun rememberHeroClock(animated: Boolean): Float {
    if (!animated) return 0.2f
    val tr = rememberInfiniteTransition(label = "hero_clock")
    val t by tr.animateFloat(0f, 37f, infiniteRepeatable(tween(37_000, easing = LinearEasing)), label = "hero_t")
    return t
}

/** Full-body animated hero. Plays a gold flourish whenever the equipped gear changes. */
@Composable
fun HeroSprite(look: HeroLook, height: Dp, modifier: Modifier = Modifier, animated: Boolean = true, shadow: Boolean = true) {
    val t = rememberHeroClock(animated)
    val flash = remember { Animatable(0f) }
    var lastKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(look.gearKey) {
        if (lastKey != null && lastKey != look.gearKey) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(700))
        }
        lastKey = look.gearKey
    }
    Canvas(modifier.size(width = height * (BW.toFloat() / BH), height = height)) {
        drawHero(look, t, flash.value, shadow = shadow)
    }
}

/** Head-and-shoulders portrait (for avatars and medallions). */
@Composable
fun HeroBust(look: HeroLook, modifier: Modifier = Modifier, animated: Boolean = true) {
    val t = rememberHeroClock(animated)
    Canvas(modifier) { drawHero(look, t, crop = BUST_CROP, shadow = false) }
}
