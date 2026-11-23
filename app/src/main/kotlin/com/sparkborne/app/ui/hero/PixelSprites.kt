package com.sparkborne.app.ui.hero

import androidx.compose.ui.graphics.Color
import com.sparkborne.game.model.ArmorSlot
import com.sparkborne.game.model.SpellElement
import com.sparkborne.game.model.WeaponClass

/**
 * Hand-authored 12x12 pixel sprites. Palette keys:
 * D outline · M metal · L light · W edge · S shade · G gold · H handle · P pommel · A accent · K black · B band · O wood
 */
private val OUTLINE = Color(0xFF15161D)
private val HANDLE = Color(0xFF7A4E2D)
private val GOLD = Color(0xFFE8B64C)

private fun metalFor(r: Rarity): Color = when (r) {
    Rarity.COMMON -> Color(0xFF8C96A6)
    Rarity.UNCOMMON -> Color(0xFF9FB4A0)
    Rarity.RARE -> Color(0xFF8FB2D8)
    Rarity.EPIC -> Color(0xFFA996D4)
    Rarity.LEGENDARY -> Color(0xFFE0B870)
}

private fun gearPalette(r: Rarity): Map<Char, Color> {
    val m = metalFor(r)
    return mapOf(
        'D' to OUTLINE,
        'M' to m,
        'L' to m.mixWith(Color.White, 0.55f),
        'W' to Color(0xFFF4F7FB),
        'S' to m.mixWith(Color.Black, 0.35f),
        'G' to GOLD,
        'H' to HANDLE,
        'P' to GOLD.mixWith(Color.White, 0.2f),
        'A' to r.color,
        'K' to Color(0xFF050508),
    )
}

private val SWORD = listOf(
    "..........WS",
    ".........WLS",
    "........WLS.",
    ".......WLS..",
    "......WLS...",
    ".....WLS....",
    "..G.WLS.....",
    "...GLS......",
    "...HGG......",
    "..H...G.....",
    ".H..........",
    "P...........",
)

private val HAMMER = listOf(
    "..DDDDDDDD..",
    "..DMMMMMMD..",
    "..DMLLLLMD..",
    "..DMMAAMMD..",
    "..DSSSSSSD..",
    "..DDDDDDDD..",
    ".....HH.....",
    ".....HH.....",
    ".....GG.....",
    ".....HH.....",
    ".....HH.....",
    ".....PP.....",
)

private val SPEAR = listOf(
    "............",
    ".........WWW",
    "........WLLW",
    ".......WLLW.",
    "......GWW...",
    ".....HG.....",
    "....H.......",
    "...H........",
    "..H.........",
    ".H..........",
    "P...........",
    "............",
)

private val TWIN = listOf(
    "W..........W",
    ".W........W.",
    "..L......L..",
    "...L....L...",
    "....L..L....",
    ".....LL.....",
    ".....GG.....",
    "....G..G....",
    "...H....H...",
    "..H......H..",
    ".P........P.",
    "............",
)

private val HELM = listOf(
    ".....AA.....",
    "....DDDD....",
    "...DMLLMD...",
    "..DMLMMMMD..",
    "..DMMMMMMD..",
    "..DMDDDDMD..",
    "..DMKKKKMD..",
    "..DMMMMMMD..",
    "..DMM..MMD..",
    "...DD..DD...",
    "............",
    "............",
)

private val CHEST = listOf(
    "............",
    "..DD....DD..",
    ".DMMD..DMMD.",
    ".DMMMDDMMMD.",
    ".DMLMMMMLMD.",
    "..DMLMMLMD..",
    "..DMMAAMMD..",
    "..DMMMMMMD..",
    "..DSMMMMSD..",
    "...DMMMMD...",
    "....DDDD....",
    "............",
)

private val GREAVES = listOf(
    "............",
    "..DDD..DDD..",
    "..DMD..DMD..",
    "..DLD..DLD..",
    "..DMD..DMD..",
    "..DAD..DAD..",
    "..DMD..DMD..",
    "..DMD..DMD..",
    "..DMMD.DMMD.",
    "..DMMMDDMMMD",
    "..DDDDDDDDDD",
    "............",
)

private val RELIC = listOf(
    "...D....D...",
    "....D..D....",
    ".....DD.....",
    ".....GG.....",
    "....GAAG....",
    "...GALLAG...",
    "...GAAAAG...",
    "...GAAAAG...",
    "....GAAG....",
    ".....GG.....",
    "............",
    "............",
)

private val ORB = listOf(
    "............",
    "....AAAA....",
    "...ALLAAA...",
    "..ALLAAAAA..",
    "..ALAAAAAA..",
    "..AAAAAAAA..",
    "..AAAAAAAS..",
    "..AAAAAASS..",
    "...AAAASS...",
    "....SSSS....",
    "............",
    "............",
)

private val CHEST_LOOT = listOf(
    "............",
    "..DDDDDDDD..",
    ".DOOOOOOOOD.",
    ".DOLOOOOOOD.",
    ".GGGGGGGGGG.",
    ".DOOOGGOOOD.",
    ".DOOOKKOOOD.",
    ".DOOOOOOOOD.",
    ".GGGGGGGGGG.",
    ".DDDDDDDDDD.",
    "............",
    "............",
)

private val LOCK = listOf(
    "............",
    "....DDDD....",
    "...D....D...",
    "...D....D...",
    "..GGGGGGGG..",
    "..GMMMMMMG..",
    "..GMMKKMMG..",
    "..GMMKKMMG..",
    "..GMMMMMMG..",
    "..GGGGGGGG..",
    "............",
    "............",
)

private val COIN = listOf(
    "..GGGG..",
    ".GLLGGG.",
    "GLGGGGSG",
    "GLGSSGSG",
    "GGGSSGSG",
    "GGGGGGSG",
    ".GGGGSS.",
    "..SSSS..",
)

private val INGOT = listOf(
    "........",
    "..LLLL..",
    ".LMMMML.",
    "LMMMMMMS",
    "MMMMMMSS",
    "SSSSSSS.",
    "........",
    "........",
)

private val SHARD = listOf(
    "...LA...",
    "..LAAA..",
    "..LAAA..",
    ".LAAAAS.",
    ".LAAAAS.",
    "..AAAS..",
    "..AAS...",
    "...S....",
)

private val BOOT = listOf(
    "..DDD...",
    "..DMD...",
    "..DMD...",
    "..DMD...",
    "..DMMDD.",
    ".DMMMMMD",
    ".DDDDDDD",
    "........",
)

fun weaponArt(cls: WeaponClass, rarity: Rarity): PixelArt = PixelArt(
    when (cls) {
        WeaponClass.GREATSWORD -> SWORD
        WeaponClass.WARHAMMER -> HAMMER
        WeaponClass.SPEAR -> SPEAR
        WeaponClass.TWIN_BLADES -> TWIN
    },
    gearPalette(rarity),
)

fun armorArt(slot: ArmorSlot, rarity: Rarity): PixelArt = PixelArt(
    when (slot) {
        ArmorSlot.HELM -> HELM
        ArmorSlot.CHEST -> CHEST
        ArmorSlot.LEGS -> GREAVES
        ArmorSlot.RELIC -> RELIC
    },
    gearPalette(rarity),
)

fun elementColor(e: SpellElement): Color = when (e) {
    SpellElement.EMBER -> Color(0xFFFF7A3D)
    SpellElement.TIDE -> Color(0xFF3FC6E8)
    SpellElement.IRON -> Color(0xFFB8C2D0)
    SpellElement.GALE -> Color(0xFF8BE38A)
}

fun spellOrbArt(e: SpellElement, dim: Boolean = false): PixelArt {
    val c = elementColor(e).let { if (dim) it.mixWith(Color(0xFF30333D), 0.7f) else it }
    return PixelArt(ORB, mapOf('A' to c, 'L' to c.mixWith(Color.White, 0.6f), 'S' to c.mixWith(Color.Black, 0.4f)))
}

val chestArt: PixelArt = PixelArt(
    CHEST_LOOT,
    mapOf(
        'D' to OUTLINE, 'O' to Color(0xFF8A5530), 'L' to Color(0xFFB27744),
        'G' to GOLD, 'K' to Color(0xFF2A1A10),
    ),
)

val lockArt: PixelArt = PixelArt(
    LOCK,
    mapOf('D' to Color(0xFF6B7280), 'G' to Color(0xFF4A4F5C), 'M' to Color(0xFF353944), 'K' to Color(0xFF0B0C10)),
)

val coinArt = PixelArt(COIN, mapOf('G' to GOLD, 'L' to Color(0xFFFFF1B8), 'S' to Color(0xFFA8781E)))
val ingotArt = PixelArt(INGOT, mapOf('M' to Color(0xFF9AA4B4), 'L' to Color(0xFFD9E0EA), 'S' to Color(0xFF5B6372)))
val shardArt = PixelArt(SHARD, mapOf('A' to Color(0xFFFF7A3D), 'L' to Color(0xFFFFD3B0), 'S' to Color(0xFFA83A1A)))
val bootArt = PixelArt(BOOT, mapOf('D' to OUTLINE, 'M' to Color(0xFF7FD68A)))
