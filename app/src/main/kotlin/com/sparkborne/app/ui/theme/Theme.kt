package com.sparkborne.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Animated splash scenes (rendered procedurally in `ui/splash`). */
enum class SplashStyleId(val displayName: String) {
    TWILIGHT_CITADEL("Twilight Citadel"),
    CAMPFIRE_VIGIL("Campfire Vigil"),
    OBSIDIAN_EMBLEM("Obsidian Anvil"),
}

enum class ElementalParticleKind {
    EMBER,
    LIGHTNING,
    WATER,
    PLANT,
    POISON,
    ARCANE,
}

enum class SparkborneThemeId(
    val displayName: String,
    val elementTag: String,
    val particleKind: ElementalParticleKind,
    val defaultSplash: SplashStyleId,
    val palette: RpgPalette,
) {
    EMBER_OBSIDIAN(
        displayName = "Ember & Obsidian",
        elementTag = "Fire",
        particleKind = ElementalParticleKind.EMBER,
        defaultSplash = SplashStyleId.TWILIGHT_CITADEL,
        palette = RpgPalette(
            obsidianBg = Color(0xFF0C0F14),
            panelSurface = Color(0xFF151B24),
            panelElevated = Color(0xFF1D2533),
            pixelBorder = Color(0xFF2F3A4F),
            pixelBorderBright = Color(0xFF4A5B7A),
            accentPrimary = Color(0xFFF5B041),
            accentSecondary = Color(0xFF4FD1C5),
            accentTertiary = Color(0xFF68D391),
            crimsonHp = Color(0xFFEF5350),
            arcaneMana = Color(0xFFA78BFA),
            hydrationCyan = Color(0xFF4FD1C5),
            vitalityEmerald = Color(0xFF68D391),
            ironSteel = Color(0xFFA0AEC0),
            textPrimary = Color(0xFFF1F5F9),
            textMuted = Color(0xFF94A3B8),
            particlePrimary = Color(0xFFFFB74D),
            particleSecondary = Color(0xFFFF7043),
        ),
    ),
    TWILIGHT_MOSS(
        displayName = "Twilight Moss",
        elementTag = "Explorer",
        particleKind = ElementalParticleKind.PLANT,
        defaultSplash = SplashStyleId.CAMPFIRE_VIGIL,
        palette = RpgPalette(
            obsidianBg = Color(0xFF0A1212),
            panelSurface = Color(0xFF131F1F),
            panelElevated = Color(0xFF1B2B2A),
            pixelBorder = Color(0xFF2B4341),
            pixelBorderBright = Color(0xFF436864),
            accentPrimary = Color(0xFFF6AD55),
            accentSecondary = Color(0xFF52D18A),
            accentTertiary = Color(0xFF63E6BE),
            crimsonHp = Color(0xFFF06565),
            arcaneMana = Color(0xFF9F8FEF),
            hydrationCyan = Color(0xFF38D9A9),
            vitalityEmerald = Color(0xFF52D18A),
            ironSteel = Color(0xFF9BB0AD),
            textPrimary = Color(0xFFEDF7F5),
            textMuted = Color(0xFF8CA8A4),
            particlePrimary = Color(0xFF52D18A),
            particleSecondary = Color(0xFFF6AD55),
        ),
    ),
    TEMPEST_VOLT(
        displayName = "Tempest Volt",
        elementTag = "Lightning",
        particleKind = ElementalParticleKind.LIGHTNING,
        defaultSplash = SplashStyleId.TWILIGHT_CITADEL,
        palette = RpgPalette(
            obsidianBg = Color(0xFF080B14),
            panelSurface = Color(0xFF111626),
            panelElevated = Color(0xFF192038),
            pixelBorder = Color(0xFF2B3759),
            pixelBorderBright = Color(0xFF465B8F),
            accentPrimary = Color(0xFFFACC15),
            accentSecondary = Color(0xFF22D3EE),
            accentTertiary = Color(0xFF60A5FA),
            crimsonHp = Color(0xFFF87171),
            arcaneMana = Color(0xFF38BDF8),
            hydrationCyan = Color(0xFF22D3EE),
            vitalityEmerald = Color(0xFF4ADE80),
            ironSteel = Color(0xFF94A3B8),
            textPrimary = Color(0xFFF0F6FF),
            textMuted = Color(0xFF8FA3C4),
            particlePrimary = Color(0xFFFACC15),
            particleSecondary = Color(0xFF22D3EE),
        ),
    ),
    TIDAL_DEPTHS(
        displayName = "Tidal Depths",
        elementTag = "Water",
        particleKind = ElementalParticleKind.WATER,
        defaultSplash = SplashStyleId.CAMPFIRE_VIGIL,
        palette = RpgPalette(
            obsidianBg = Color(0xFF061118),
            panelSurface = Color(0xFF0E1D27),
            panelElevated = Color(0xFF152A38),
            pixelBorder = Color(0xFF234357),
            pixelBorderBright = Color(0xFF356785),
            accentPrimary = Color(0xFF38BDF8),
            accentSecondary = Color(0xFF2DD4BF),
            accentTertiary = Color(0xFF7DD3FC),
            crimsonHp = Color(0xFFFB7185),
            arcaneMana = Color(0xFF818CF8),
            hydrationCyan = Color(0xFF2DD4BF),
            vitalityEmerald = Color(0xFF34D399),
            ironSteel = Color(0xFF93B4C7),
            textPrimary = Color(0xFFECFAFF),
            textMuted = Color(0xFF7FA6BE),
            particlePrimary = Color(0xFF38BDF8),
            particleSecondary = Color(0xFF2DD4BF),
        ),
    ),
    VERDANT_GROVE(
        displayName = "Verdant Grove",
        elementTag = "Plant",
        particleKind = ElementalParticleKind.PLANT,
        defaultSplash = SplashStyleId.CAMPFIRE_VIGIL,
        palette = RpgPalette(
            obsidianBg = Color(0xFF08130B),
            panelSurface = Color(0xFF112116),
            panelElevated = Color(0xFF193020),
            pixelBorder = Color(0xFF2A4D34),
            pixelBorderBright = Color(0xFF427851),
            accentPrimary = Color(0xFF4ADE80),
            accentSecondary = Color(0xFFA3E635),
            accentTertiary = Color(0xFF34D399),
            crimsonHp = Color(0xFFF87171),
            arcaneMana = Color(0xFF67E8F9),
            hydrationCyan = Color(0xFF2DD4BF),
            vitalityEmerald = Color(0xFF4ADE80),
            ironSteel = Color(0xFF98B59E),
            textPrimary = Color(0xFFECFDF2),
            textMuted = Color(0xFF88AB91),
            particlePrimary = Color(0xFF4ADE80),
            particleSecondary = Color(0xFFA3E635),
        ),
    ),
    VENOM_BLIGHT(
        displayName = "Venom Blight",
        elementTag = "Poison",
        particleKind = ElementalParticleKind.POISON,
        defaultSplash = SplashStyleId.OBSIDIAN_EMBLEM,
        palette = RpgPalette(
            obsidianBg = Color(0xFF0D0914),
            panelSurface = Color(0xFF171122),
            panelElevated = Color(0xFF221A33),
            pixelBorder = Color(0xFF3C2B57),
            pixelBorderBright = Color(0xFF5D4287),
            accentPrimary = Color(0xFFA3E635),
            accentSecondary = Color(0xFFC084FC),
            accentTertiary = Color(0xFF4ADE80),
            crimsonHp = Color(0xFFF43F5E),
            arcaneMana = Color(0xFFC084FC),
            hydrationCyan = Color(0xFF38BDF8),
            vitalityEmerald = Color(0xFFA3E635),
            ironSteel = Color(0xFFA799BD),
            textPrimary = Color(0xFFF5F0FF),
            textMuted = Color(0xFF9E8FB8),
            particlePrimary = Color(0xFFA3E635),
            particleSecondary = Color(0xFFC084FC),
        ),
    ),
    ABYSSAL_ARCANE(
        displayName = "Abyssal Arcane",
        elementTag = "Void",
        particleKind = ElementalParticleKind.ARCANE,
        defaultSplash = SplashStyleId.OBSIDIAN_EMBLEM,
        palette = RpgPalette(
            obsidianBg = Color(0xFF090B10),
            panelSurface = Color(0xFF131722),
            panelElevated = Color(0xFF1C2233),
            pixelBorder = Color(0xFF2E3752),
            pixelBorderBright = Color(0xFF4C5B85),
            accentPrimary = Color(0xFFA78BFA),
            accentSecondary = Color(0xFF38BDF8),
            accentTertiary = Color(0xFFFBBF24),
            crimsonHp = Color(0xFFFB7185),
            arcaneMana = Color(0xFFA78BFA),
            hydrationCyan = Color(0xFF38BDF8),
            vitalityEmerald = Color(0xFF34D399),
            ironSteel = Color(0xFF9CA3AF),
            textPrimary = Color(0xFFF3F4F6),
            textMuted = Color(0xFF9CA3AF),
            particlePrimary = Color(0xFFA78BFA),
            particleSecondary = Color(0xFF38BDF8),
        ),
    ),
}

@Immutable
data class RpgPalette(
    val obsidianBg: Color,
    val panelSurface: Color,
    val panelElevated: Color,
    val pixelBorder: Color,
    val pixelBorderBright: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentTertiary: Color,
    val crimsonHp: Color,
    val arcaneMana: Color,
    val hydrationCyan: Color,
    val vitalityEmerald: Color,
    val ironSteel: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val particlePrimary: Color,
    val particleSecondary: Color,
)

val LocalSparkbornePalette = staticCompositionLocalOf {
    SparkborneThemeId.EMBER_OBSIDIAN.palette
}

/**
 * Convenience accessor for the active elemental palette inside any Composable.
 */
object RpgTheme {
    val colors: RpgPalette
        @Composable get() = LocalSparkbornePalette.current
}

@Composable
fun SparkborneTheme(
    themeId: SparkborneThemeId = SparkborneThemeId.EMBER_OBSIDIAN,
    content: @Composable () -> Unit,
) {
    val palette = themeId.palette
    val colorScheme = darkColorScheme(
        primary = palette.accentPrimary,
        onPrimary = Color(0xFF0B0E14),
        secondary = palette.accentSecondary,
        onSecondary = Color(0xFF061518),
        tertiary = palette.accentTertiary,
        onTertiary = Color(0xFF06180E),
        background = palette.obsidianBg,
        surface = palette.panelSurface,
        surfaceVariant = palette.panelElevated,
        onBackground = palette.textPrimary,
        onSurface = palette.textPrimary,
        onSurfaceVariant = palette.textMuted,
        error = palette.crimsonHp,
        outline = palette.pixelBorder,
    )

    CompositionLocalProvider(LocalSparkbornePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
