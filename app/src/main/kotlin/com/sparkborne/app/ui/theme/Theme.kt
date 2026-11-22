package com.sparkborne.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * WalkScape-inspired dark slate & ember RPG color palette.
 */
object RpgColors {
    val ObsidianBg = Color(0xFF0E1117)
    val PanelSurface = Color(0xFF161B24)
    val PanelElevated = Color(0xFF1D2432)
    val PixelBorder = Color(0xFF2F3A4F)
    val PixelBorderBright = Color(0xFF475775)

    val EmberGold = Color(0xFFF5B041)
    val EmberOrange = Color(0xFFE67E22)
    val CrimsonHp = Color(0xFFEF5350)
    val ArcaneMana = Color(0xFFA78BFA)
    val HydrationCyan = Color(0xFF4FD1C5)
    val VitalityEmerald = Color(0xFF68D391)
    val IronSteel = Color(0xFFA0AEC0)

    val TextPrimary = Color(0xFFF1F5F9)
    val TextMuted = Color(0xFF94A3B8)
}

private val SparkborneDarkColors = darkColorScheme(
    primary = RpgColors.EmberGold,
    onPrimary = Color(0xFF1A1205),
    secondary = RpgColors.HydrationCyan,
    onSecondary = Color(0xFF061B1A),
    tertiary = RpgColors.VitalityEmerald,
    onTertiary = Color(0xFF061B0E),
    background = RpgColors.ObsidianBg,
    surface = RpgColors.PanelSurface,
    surfaceVariant = RpgColors.PanelElevated,
    onBackground = RpgColors.TextPrimary,
    onSurface = RpgColors.TextPrimary,
    onSurfaceVariant = RpgColors.TextMuted,
    error = RpgColors.CrimsonHp,
    outline = RpgColors.PixelBorder,
)

@Composable
fun SparkborneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SparkborneDarkColors,
        content = content,
    )
}
