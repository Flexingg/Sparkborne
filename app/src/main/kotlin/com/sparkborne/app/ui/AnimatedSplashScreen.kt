package com.sparkborne.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import com.sparkborne.app.ui.splash.SPLASH_TOTAL_SECONDS
import com.sparkborne.app.ui.splash.SplashScene
import com.sparkborne.app.ui.splash.drawSplashFrame
import com.sparkborne.app.ui.theme.SparkborneThemeId
import com.sparkborne.app.ui.theme.SplashStyleId

/**
 * Fully procedural, frame-by-frame animated pixel-art splash. Each [SplashStyleId] is a short
 * choreographed scene (see `ui/splash/SplashScenes.kt`):
 *
 * - **Twilight Citadel** – camera tilts down from the stars to a citadel; moon rises, windows
 *   light up one by one, the realm banner waves, a shooting star streaks past.
 * - **Campfire Vigil** – a flint spark ignites a campfire beside a resting hero; flames, embers,
 *   smoke, firelight and fireflies.
 * - **Obsidian Anvil** – a smith's hammer strikes a glowing ingot three times with sparks and
 *   screen shake; the final blow flashes the crest into place.
 *
 * The SPARKBORNE crest drops in letter by letter, the realm subtitle types out, and a pixel
 * progress bar tracks the timeline. Tap anywhere to skip.
 */
@Composable
fun AnimatedSplashScreen(
    themeId: SparkborneThemeId,
    splashStyle: SplashStyleId,
    versionLabel: String,
    onFinished: () -> Unit,
) {
    val palette = themeId.palette
    val scene = when (splashStyle) {
        SplashStyleId.TWILIGHT_CITADEL -> SplashScene.CITADEL
        SplashStyleId.CAMPFIRE_VIGIL -> SplashScene.CAMPFIRE
        SplashStyleId.OBSIDIAN_EMBLEM -> SplashScene.FORGE
    }
    val textMeasurer = rememberTextMeasurer()
    val finish by rememberUpdatedState(onFinished)
    var clock by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(scene) {
        clock = 0f
        var start = -1L
        while (clock < SPLASH_TOTAL_SECONDS) {
            withFrameNanos { now ->
                if (start < 0L) start = now
                clock = (now - start) / 1_000_000_000f
            }
        }
        finish()
    }

    val subtitle = "${themeId.displayName.uppercase()} · ${themeId.elementTag.uppercase()} REALM"
    val footer = "TAP TO ENTER · $versionLabel"

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { finish() } },
        contentDescription = "Sparkborne splash screen",
    ) {
        drawSplashFrame(scene, clock, palette, textMeasurer, subtitle, footer)
    }
}
