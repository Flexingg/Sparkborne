package com.sparkborne.app.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.ui.theme.ElementalParticleKind
import com.sparkborne.app.ui.theme.SparkborneThemeId
import com.sparkborne.app.ui.theme.SplashStyleId
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class PixelParticleSeed(
    val xFrac: Float,
    val phaseOffset: Float,
    val speed: Float,
    val sizeDp: Float,
    val swayAmplitude: Float,
    val isPrimary: Boolean,
)

/**
 * Animated 64-bit pixel art splash screen with:
 * - Smooth Ken Burns zoom on the crisp pixel art backdrop (`FilterQuality.None`)
 * - Live procedural elemental pixel particles matching the active theme
 *   (Embers, Lightning arcs, Water droplets, Plant leaves/spores, Poison bubbles, Arcane stars)
 * - Staggered title crest reveal and tap-to-skip / auto-dismiss transition.
 */
@Composable
fun AnimatedSplashScreen(
    themeId: SparkborneThemeId,
    splashStyle: SplashStyleId,
    versionLabel: String,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val palette = themeId.palette
    val backdrop = remember(splashStyle.drawableRes) {
        BitmapFactory.decodeResource(context.resources, splashStyle.drawableRes).asImageBitmap()
    }

    val zoom = remember { Animatable(1.0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(24f) }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_particles")
    val particleClock by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particle_clock",
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_pulse",
    )

    val seeds = remember {
        List(32) { i ->
            PixelParticleSeed(
                xFrac = ((i * 37 + 13) % 100) / 100f,
                phaseOffset = ((i * 53) % 100) / 100f,
                speed = 0.7f + ((i * 19) % 10) * 0.08f,
                sizeDp = 3f + (i % 4) * 1.5f,
                swayAmplitude = 10f + (i % 5) * 6f,
                isPrimary = i % 2 == 0,
            )
        }
    }

    LaunchedEffect(Unit) {
        zoom.animateTo(1.08f, animationSpec = tween(durationMillis = 2200, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(180)
        titleAlpha.animateTo(1f, animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(180)
        titleOffsetY.animateTo(0f, animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(2150)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.obsidianBg)
            .clickable(onClick = onFinished),
    ) {
        // 1. Crisp 64-bit pixel art backdrop with gentle Ken Burns drift
        Image(
            bitmap = backdrop,
            contentDescription = "Sparkborne Splash",
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                },
        )

        // 2. Atmospheric vignette gradient tinted by active elemental theme
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to palette.obsidianBg.copy(alpha = 0.82f),
                        0.32f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1.0f to palette.obsidianBg.copy(alpha = 0.94f),
                    ),
                ),
        )

        // 3. Procedural Elemental Pixel Particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val kind = themeId.particleKind

            for (p in seeds) {
                val t = (particleClock * p.speed + p.phaseOffset) % 1f
                val color = if (p.isPrimary) palette.particlePrimary else palette.particleSecondary
                val pxSize = p.sizeDp.dp.toPx()

                when (kind) {
                    ElementalParticleKind.EMBER,
                    ElementalParticleKind.ARCANE,
                    -> {
                        val y = h * (1f - t)
                        val x = w * p.xFrac + sin(t * 2f * PI.toFloat() + p.phaseOffset * 6f) * p.swayAmplitude
                        val alpha = (sin(t * PI.toFloat()) * 0.9f).coerceIn(0f, 1f)
                        drawRect(
                            color = color.copy(alpha = alpha),
                            topLeft = Offset(x, y),
                            size = Size(pxSize, pxSize),
                        )
                    }

                    ElementalParticleKind.WATER -> {
                        val y = h * t
                        val x = w * p.xFrac + sin(t * PI.toFloat()) * 6f
                        val alpha = (sin(t * PI.toFloat()) * 0.75f).coerceIn(0f, 1f)
                        drawRect(
                            color = color.copy(alpha = alpha),
                            topLeft = Offset(x, y),
                            size = Size(pxSize * 0.65f, pxSize * 2.2f),
                        )
                    }

                    ElementalParticleKind.PLANT -> {
                        val y = h * t
                        val x = w * p.xFrac + cos(t * 2f * PI.toFloat() + p.phaseOffset * 8f) * (p.swayAmplitude * 1.4f)
                        val alpha = (sin(t * PI.toFloat()) * 0.85f).coerceIn(0f, 1f)
                        drawRect(
                            color = color.copy(alpha = alpha),
                            topLeft = Offset(x, y),
                            size = Size(pxSize * 1.4f, pxSize * 0.85f),
                        )
                    }

                    ElementalParticleKind.POISON -> {
                        val y = h * (1f - t)
                        val x = w * p.xFrac + sin(t * 4f * PI.toFloat() + p.phaseOffset * 5f) * 8f
                        val alpha = (sin(t * PI.toFloat()) * 0.85f).coerceIn(0f, 1f)
                        drawCircle(
                            color = color.copy(alpha = alpha * 0.45f),
                            radius = pxSize * 0.9f,
                            center = Offset(x, y),
                        )
                        drawCircle(
                            color = color.copy(alpha = alpha),
                            radius = pxSize * 0.9f,
                            center = Offset(x, y),
                            style = Stroke(width = 2f),
                        )
                    }

                    ElementalParticleKind.LIGHTNING -> {
                        val y = h * (1f - t)
                        val x = w * p.xFrac
                        val alpha = (sin(t * PI.toFloat()) * 0.9f).coerceIn(0f, 1f)
                        drawRect(
                            color = color.copy(alpha = alpha),
                            topLeft = Offset(x, y),
                            size = Size(pxSize, pxSize),
                        )
                    }
                }
            }

            // Periodic jagged lightning bolt flash for Lightning theme
            if (kind == ElementalParticleKind.LIGHTNING && glowPulse > 0.88f) {
                val boltPath = Path().apply {
                    moveTo(w * 0.72f, 0f)
                    lineTo(w * 0.66f, h * 0.14f)
                    lineTo(w * 0.74f, h * 0.18f)
                    lineTo(w * 0.62f, h * 0.34f)
                }
                drawPath(
                    path = boltPath,
                    color = palette.particlePrimary.copy(alpha = (glowPulse - 0.85f) * 5f),
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
        }

        // 4. Title & Elemental Tagline in Upper Safe Area
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 74.dp, start = 24.dp, end = 24.dp)
                .alpha(titleAlpha.value)
                .graphicsLayer { translationY = titleOffsetY.value },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "SPARKBORNE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp,
                letterSpacing = 5.sp,
                color = palette.accentPrimary,
            )
            Text(
                text = "${themeId.displayName.uppercase()} · ${themeId.elementTag.uppercase()} REALM",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                color = palette.textPrimary.copy(alpha = 0.88f),
            )
        }

        // 5. Bottom subtle shimmer bar + version
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 44.dp, start = 48.dp, end = 48.dp)
                .alpha(titleAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.panelElevated),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(glowPulse)
                        .height(4.dp)
                        .background(palette.accentPrimary),
                )
            }
            Text(
                text = "TAP TO ENTER · $versionLabel",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = palette.textMuted,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
