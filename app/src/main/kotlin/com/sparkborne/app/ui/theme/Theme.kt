package com.sparkborne.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SparkborneDarkColors = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF1F1608),
    secondary = Color(0xFF64B5F6),
    onSecondary = Color(0xFF0A1929),
    tertiary = Color(0xFF81C784),
    background = Color(0xFF121418),
    surface = Color(0xFF1A1D24),
    surfaceVariant = Color(0xFF232833),
    onBackground = Color(0xFFECEFF4),
    onSurface = Color(0xFFECEFF4),
    onSurfaceVariant = Color(0xFFB0B8C8),
    error = Color(0xFFEF5350),
)

@Composable
fun SparkborneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SparkborneDarkColors,
        content = content,
    )
}
