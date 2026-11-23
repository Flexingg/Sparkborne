package com.sparkborne.app.ui.sanctum

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.ui.hero.BannerTabs
import com.sparkborne.app.ui.hero.FrameStyle
import com.sparkborne.app.ui.hero.GoldTrim
import com.sparkborne.app.ui.hero.InkDark
import com.sparkborne.app.ui.hero.PixelArtIcon
import com.sparkborne.app.ui.hero.PixelButton
import com.sparkborne.app.ui.hero.RuneChip
import com.sparkborne.app.ui.hero.RuneHeader
import com.sparkborne.app.ui.hero.SegmentBar
import com.sparkborne.app.ui.hero.Text8
import com.sparkborne.app.ui.hero.bootArt
import com.sparkborne.app.ui.hero.chamfer
import com.sparkborne.app.ui.hero.mixWith
import com.sparkborne.app.ui.hero.pixelFrame
import com.sparkborne.app.ui.hero.rememberBob
import com.sparkborne.app.ui.hero.rememberSpin
import com.sparkborne.app.ui.splash.SplashScene
import com.sparkborne.app.ui.splash.SplashScenePreview
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.app.ui.theme.SparkborneThemeId
import com.sparkborne.app.ui.theme.SplashStyleId
import com.sparkborne.telemetry.model.BodyweightTrend
import com.sparkborne.telemetry.model.DailyTelemetrySnapshot
import com.sparkborne.telemetry.model.WeightTrendDirection
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// =============================================================================================
// State & actions (platform-free; the Android layer fills these in)
// =============================================================================================

enum class LinkKind { PHONE, HEALTH_CONNECT, SPARKY, LIFTOSAUR }

/** Where one health metric currently flows from. [granted] = Health Connect permission (null = n/a). */
data class SourceLink(
    val metric: String,
    val glyph: String,
    val value: String,
    val sourceLabel: String,
    val kind: LinkKind,
    val granted: Boolean?,
)

data class SanctumState(
    val snapshots: List<DailyTelemetrySnapshot> = emptyList(),
    val selectedDate: String? = null,
    val bodyweightTrend: BodyweightTrend? = null,
    val bankedSteps: Int = 0,
    val stepBankCap: Int = 1,
    val links: List<SourceLink> = emptyList(),
    val hcAvailable: Boolean = false,
    val hcPermissions: List<Pair<String, Boolean>> = emptyList(),
    val hcHasAny: Boolean = false,
    val hcHasAll: Boolean = false,
    val hcMessage: String? = null,
    val baseUrl: String = "",
    val apiKey: String = "",
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val backfillProgress: Float? = null,
    val backfillStatus: String? = null,
    val theme: SparkborneThemeId = SparkborneThemeId.EMBER_OBSIDIAN,
    val splash: SplashStyleId = SplashStyleId.TWILIGHT_CITADEL,
    val versionLabel: String = "",
    val updateStatus: String? = null,
) {
    val selectedSnapshot: DailyTelemetrySnapshot?
        get() = snapshots.firstOrNull { it.date == selectedDate } ?: snapshots.firstOrNull()
}

class SanctumActions(
    val onBaseUrlChange: (String) -> Unit = {},
    val onApiKeyChange: (String) -> Unit = {},
    val onSelectDate: (String) -> Unit = {},
    val onPing: () -> Unit = {},
    val onSyncToday: () -> Unit = {},
    val onSync14Days: () -> Unit = {},
    val onCheckUpdates: () -> Unit = {},
    val onSelectTheme: (SparkborneThemeId) -> Unit = {},
    val onSelectSplash: (SplashStyleId) -> Unit = {},
    val onReplaySplash: () -> Unit = {},
    val onResetJourney: () -> Unit = {},
    val onRequestHealthConnect: () -> Unit = {},
    val onSyncHealthConnect: () -> Unit = {},
    val onRunBackfill: (Int) -> Unit = {},
)

enum class SanctumPage(val label: String, val glyph: String) {
    CHRONICLE("Chronicle", "📜"),
    LEYLINES("Ley Lines", "🔮"),
    OPTIONS("Options", "⚙"),
}

private val LeyBlue = Color(0xFF7FD6FF)
private val Parchment = Color(0xFF2B2620)

internal fun splashScene(id: SplashStyleId): SplashScene = when (id) {
    SplashStyleId.TWILIGHT_CITADEL -> SplashScene.CITADEL
    SplashStyleId.CAMPFIRE_VIGIL -> SplashScene.CAMPFIRE
    SplashStyleId.OBSIDIAN_EMBLEM -> SplashScene.FORGE
}

/**
 * The Sanctum: the game-styled home for health data, sync sources and options.
 */
@Composable
fun SanctumScreen(state: SanctumState, actions: SanctumActions, initialPage: SanctumPage = SanctumPage.CHRONICLE) {
    var page by remember { mutableStateOf(initialPage) }
    Column(Modifier.fillMaxSize()) {
        BannerTabs(SanctumPage.entries, page, { it.label }, { it.glyph }) { page = it }
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "sanctum_page",
            modifier = Modifier.fillMaxSize(),
        ) { pg ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (pg) {
                    SanctumPage.CHRONICLE -> ChroniclePage(state, actions) { page = SanctumPage.LEYLINES }
                    SanctumPage.LEYLINES -> LeyLinesPage(state, actions)
                    SanctumPage.OPTIONS -> OptionsPage(state, actions)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// =============================================================================================
// CHRONICLE — health data as alchemy flasks, ley lines, a momentum scale and day stones
// =============================================================================================

private data class Essence(
    val name: String,
    val glyph: String,
    val value: String,
    val caption: String,
    val fill: Float,
    val color: Color,
)

@Composable
private fun ChroniclePage(state: SanctumState, actions: SanctumActions, onManageSources: () -> Unit) {
    val colors = RpgTheme.colors
    var weekly by remember { mutableStateOf(true) }
    val slice = if (weekly) state.snapshots.take(7) else state.snapshots

    fun avgInt(xs: List<Double>) = xs.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0
    fun avgRatio(xs: List<Double>) = xs.takeIf { it.isNotEmpty() }?.map { it.coerceIn(0.0, 1.0) }?.average()?.toFloat() ?: 0f

    val avgSteps = avgInt(slice.map { it.steps.toDouble() })
    val avgSleep = avgInt(slice.map { it.sleep.totalMinutes.toDouble() })
    val avgWater = avgInt(slice.map { it.hydration.totalMl.toDouble() })
    val avgKcal = avgInt(slice.map { it.nutrition.calories })
    val avgProtein = avgInt(slice.map { it.nutrition.proteinGrams })
    val liftKg = slice.sumOf { it.lifting.totalVolumeKg }.roundToInt()
    val workouts = slice.sumOf { it.lifting.workoutCount }
    val goalScore = if (slice.isEmpty()) 0f else slice.map { s ->
        listOf(s.adherence.stepsRatio, s.adherence.sleepRatio, s.adherence.hydrationRatio, s.adherence.proteinRatio)
            .map { it.coerceIn(0.0, 1.0) }.average()
    }.average().toFloat()

    val essences = listOf(
        Essence("Wanderlust", "🥾", "%,d".format(avgSteps), "steps / day", avgRatio(slice.map { it.adherence.stepsRatio }), colors.accentPrimary),
        Essence("Moonrest", "🌙", if (avgSleep > 0) "${avgSleep / 60}h${avgSleep % 60}m" else "—", "sleep", avgRatio(slice.map { it.adherence.sleepRatio }), colors.arcaneMana),
        Essence("Clarity", "💧", if (avgWater > 0) "%,d".format(avgWater) else "—", "mL water", avgRatio(slice.map { it.adherence.hydrationRatio }), colors.accentSecondary),
        Essence("Hearth", "🍖", if (avgKcal > 0) "%,d".format(avgKcal) else "—", if (avgProtein > 0) "kcal · ${avgProtein}g pro" else "kcal", avgRatio(slice.map { it.adherence.caloriesRatio }), colors.vitalityEmerald),
        Essence("Iron Will", "🏋", "%,d".format(liftKg), "kg · $workouts sessions", if (liftKg > 0) 1f else 0f, Color(0xFFC9D2DE)),
        Essence("Devotion", "✦", "${(goalScore * 100).roundToInt()}%", "goal score", goalScore, GoldTrim),
    )

    // Header with 7D / ALL lever
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text8("ADVENTURER'S LEDGER", size = 13.sp, color = GoldTrim, weight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Text8(if (slice.isEmpty()) "No days chronicled yet" else "Averaged over ${slice.size} day(s)", size = 10.sp, color = colors.textMuted)
        }
        LeverSwitch(leftLabel = "7D", rightLabel = "ALL", left = weekly) { weekly = it }
    }

    // Alchemy shelf of essence flasks
    Box(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF15121A), edge = Color(0xFF6B4A2C), rivet = GoldTrim)
            .padding(horizontal = 6.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            essences.chunked(3).forEach { row ->
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        row.forEach { e -> EssenceFlask(e, Modifier.width(100.dp)) }
                    }
                    ShelfPlank()
                }
            }
        }
    }

    // Bank of steps
    Row(verticalAlignment = Alignment.CenterVertically) {
        PixelArtIcon(bootArt, 18.dp)
        Spacer(Modifier.width(6.dp))
        SegmentBar(
            state.bankedSteps.toFloat() / state.stepBankCap.coerceAtLeast(1),
            colors.vitalityEmerald,
            Modifier.weight(1f),
            segments = 20,
            height = 14.dp,
            label = "%,d / %,d BANKED".format(state.bankedSteps, state.stepBankCap),
        )
    }

    // Momentum scale (bodyweight trend)
    state.bodyweightTrend?.takeIf { it.latestWeightKg != null }?.let { trend ->
        RuneHeader("MOMENTUM SCALE", color = colors.accentSecondary, trailing = trend.asOfDate)
        MomentumScale(trend)
    }

    // Ley lines (data provenance)
    RuneHeader("LEY LINES", color = LeyBlue, trailing = "where your essence flows from")
    LeyLineMap(state.links)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        PixelButton("🔮 TEND THE LEY LINES", LeyBlue, small = true, onClick = onManageSources)
    }

    // Day stones + the selected day's log
    RuneHeader("DAY STONES", color = GoldTrim)
    if (state.snapshots.isEmpty()) {
        Text8("No days chronicled yet. Open the Ley Lines to draw your first essence.", size = 11.sp, color = colors.textMuted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    } else {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.snapshots.forEach { s ->
                DayStone(s, selected = s.date == state.selectedSnapshot?.date) { actions.onSelectDate(s.date) }
            }
        }
        state.selectedSnapshot?.let { DayLog(it) }
    }
}

@Composable
private fun LeverSwitch(leftLabel: String, rightLabel: String, left: Boolean, onChange: (Boolean) -> Unit) {
    val colors = RpgTheme.colors
    Row(
        Modifier
            .pixelFrame(fill = InkDark, edge = colors.pixelBorderBright, style = FrameStyle.SOCKET, unit = 2.dp)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(true to leftLabel, false to rightLabel).forEach { (isLeft, label) ->
            val on = isLeft == left
            Box(
                Modifier
                    .then(if (on) Modifier.pixelFrame(fill = GoldTrim, edge = GoldTrim.mixWith(Color.White, 0.3f), style = FrameStyle.PLATE, unit = 1.dp) else Modifier)
                    .clickable { onChange(isLeft) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Text8(label, size = 10.sp, color = if (on) InkDark else colors.textMuted, weight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
private fun EssenceFlask(e: Essence, modifier: Modifier) {
    val colors = RpgTheme.colors
    val tr = rememberInfiniteTransition(label = "flask")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "flask_t")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 64.dp, height = 82.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val u = floor(3.dp.toPx())
                val w = size.width
                val h = size.height
                val cx = w / 2
                // flask body rows (round bottom, narrow neck)
                val neckW = 6 * u
                val neckTop = 2 * u
                val bodyTop = 10 * u
                val bodyBottom = h - 2 * u
                val bodyH = bodyBottom - bodyTop
                val rows = (bodyH / u).toInt()
                fun halfAt(r: Int): Float {
                    val f = r.toFloat() / rows
                    val base = 9 * u
                    return base * (0.55f + 0.45f * kotlin.math.sqrt((1f - (f - 0.62f).let { it * it } / 0.39f).coerceAtLeast(0f)))
                }
                // glow
                if (e.fill > 0f) drawCircle(Brush.radialGradient(listOf(e.color.copy(alpha = 0.35f), Color.Transparent), Offset(cx, bodyTop + bodyH * 0.6f), w * 0.6f), w * 0.6f, Offset(cx, bodyTop + bodyH * 0.6f))
                // outline + glass
                drawRect(InkDark, Offset(cx - neckW / 2 - u, neckTop - u), Size(neckW + 2 * u, bodyTop - neckTop + u))
                for (r in 0 until rows) {
                    val hw = halfAt(r)
                    drawRect(InkDark, Offset(cx - hw - u, bodyTop + r * u), Size(2 * hw + 2 * u, u))
                }
                drawRect(InkDark, Offset(cx - halfAt(rows - 1), bodyBottom), Size(2 * halfAt(rows - 1), u))
                val glass = Color(0xFF2A3040)
                drawRect(glass, Offset(cx - neckW / 2, neckTop), Size(neckW, bodyTop - neckTop))
                for (r in 0 until rows) {
                    val hw = halfAt(r)
                    drawRect(glass, Offset(cx - hw, bodyTop + r * u), Size(2 * hw, u))
                }
                // liquid with wavy surface
                val level = e.fill.coerceIn(0f, 1f)
                val liquidTop = bodyBottom - bodyH * level
                for (r in 0 until rows) {
                    val y = bodyTop + r * u
                    val wave = sin((t * 6.283f) + r * 0.0f) * u * 0.6f
                    if (y + u < liquidTop + wave) continue
                    val hw = halfAt(r) - u * 0.0f
                    val c = if (y < liquidTop + 2 * u) e.color.mixWith(Color.White, 0.35f) else e.color.mixWith(Color.Black, 0.15f)
                    drawRect(c, Offset(cx - hw, y), Size(2 * hw, u))
                }
                // bubbles
                if (level > 0.05f) {
                    for (i in 0 until 3) {
                        val ph = (t + i / 3f) % 1f
                        val by = bodyBottom - ph * bodyH * level
                        val bx = cx + sin(i * 2.1f + ph * 5f) * 4 * u
                        drawRect(Color.White.copy(alpha = 0.6f * (1f - ph)), Offset(floor(bx / u) * u, floor(by / u) * u), Size(u, u))
                    }
                }
                // shine + cork
                drawRect(Color.White.copy(alpha = 0.18f), Offset(cx - 6 * u, bodyTop + 3 * u), Size(u, bodyH * 0.45f))
                drawRect(Color(0xFF8A5A34), Offset(cx - neckW / 2 - u, 0f), Size(neckW + 2 * u, 2 * u))
            }
            Text8(e.glyph, size = 14.sp, modifier = Modifier.padding(top = 18.dp))
        }
        Text8(e.value, size = 13.sp, color = e.color, weight = FontWeight.ExtraBold, maxLines = 1)
        Text8(e.name.uppercase(), size = 8.sp, color = colors.textPrimary, letterSpacing = 1.sp)
        Text8(e.caption, size = 8.sp, color = colors.textMuted, maxLines = 1)
    }
}

@Composable
private fun ShelfPlank() {
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val u = floor(2.dp.toPx())
        drawRect(InkDark, Offset(0f, 0f), Size(size.width, 4 * u))
        drawRect(Color(0xFF6B4A2C), Offset(0f, 0f), Size(size.width, 3 * u))
        drawRect(Color(0xFF8A6238), Offset(0f, 0f), Size(size.width, u))
    }
}

@Composable
private fun MomentumScale(trend: BodyweightTrend) {
    val colors = RpgTheme.colors
    val tilt = when (trend.direction) {
        WeightTrendDirection.GAINING -> -1f
        WeightTrendDirection.LOSING -> 1f
        else -> 0f
    }
    val sway = rememberBob(1.5f, 2200) / 10f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 120.dp, height = 90.dp)) {
            val u = floor(3.dp.toPx())
            val w = size.width
            val h = size.height
            val cx = w / 2
            val pivot = Offset(cx, h * 0.28f)
            // post + base
            drawRect(InkDark, Offset(cx - 2 * u, pivot.y), Size(4 * u, h * 0.62f))
            drawRect(Color(0xFF8A6238), Offset(cx - u, pivot.y), Size(2 * u, h * 0.62f))
            chamfer(cx - 10 * u, h - 4 * u, cx + 10 * u, h, u, Color(0xFF6B4A2C))
            // beam
            val ang = (tilt * 0.22f) + sway * 0.05f
            val arm = w * 0.34f
            val dx = kotlin.math.cos(ang) * arm
            val dy = kotlin.math.sin(ang) * arm
            val left = Offset(pivot.x - dx, pivot.y - dy)
            val right = Offset(pivot.x + dx, pivot.y + dy)
            drawLine(InkDark, left, right, strokeWidth = 3 * u)
            drawLine(GoldTrim, left, right, strokeWidth = 1.5f * u)
            drawCircle(GoldTrim, 2 * u, pivot)
            // pans
            listOf(left to colors.accentPrimary, right to colors.vitalityEmerald).forEach { (p, c) ->
                drawLine(Color(0xFF8A8F9C), p, p + Offset(-5 * u, 8 * u), strokeWidth = u * 0.6f)
                drawLine(Color(0xFF8A8F9C), p, p + Offset(5 * u, 8 * u), strokeWidth = u * 0.6f)
                chamfer(p.x - 7 * u, p.y + 8 * u, p.x + 7 * u, p.y + 11 * u, u, c.mixWith(Color.Black, 0.3f))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text8("%.1f kg".format(trend.latestWeightKg ?: 0.0), size = 18.sp, color = colors.textPrimary, weight = FontWeight.ExtraBold)
            val (label, c) = when (trend.direction) {
                WeightTrendDirection.GAINING -> "▲ GAINING · Juggernaut" to colors.accentPrimary
                WeightTrendDirection.LOSING -> "▼ LOSING · Prowler" to colors.vitalityEmerald
                WeightTrendDirection.STABLE -> "◆ STABLE · Equilibrium" to colors.accentSecondary
                WeightTrendDirection.INSUFFICIENT_DATA -> "… gathering omens" to colors.textMuted
            }
            Text8(label, size = 10.sp, color = c)
            Text8(
                "7d ${trend.rolling7DayAvgKg?.let { "%.1f".format(it) } ?: "—"} · 14d ${trend.rolling14DayAvgKg?.let { "%.1f".format(it) } ?: "—"} · Δ ${trend.delta7DayKg?.let { "%+.2f".format(it) } ?: "—"}",
                size = 9.sp,
                color = colors.textMuted,
            )
            Text8("${trend.sampleCount14d} weigh-ins in 14 days", size = 9.sp, color = colors.textMuted)
        }
    }
}

private fun linkColor(kind: LinkKind, fallback: Color): Color = when (kind) {
    LinkKind.HEALTH_CONNECT -> Color(0xFF5FD068)
    LinkKind.SPARKY -> Color(0xFF7FD6FF)
    LinkKind.LIFTOSAUR -> Color(0xFFF5B041)
    LinkKind.PHONE -> fallback
}

private fun linkSigil(kind: LinkKind): String = when (kind) {
    LinkKind.HEALTH_CONNECT -> "💠"
    LinkKind.SPARKY -> "☁"
    LinkKind.LIFTOSAUR -> "🏋"
    LinkKind.PHONE -> "📱"
}

/** Each metric glyph on the left, linked by an animated ley line to its source sigil on the right. */
@Composable
private fun LeyLineMap(links: List<SourceLink>) {
    val colors = RpgTheme.colors
    val tr = rememberInfiniteTransition(label = "ley")
    val flow by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "ley_flow")
    Column(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF0E1620), edge = LeyBlue.mixWith(colors.pixelBorder, 0.6f), rivet = LeyBlue)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        links.forEach { l ->
            val c = linkColor(l.kind, colors.accentPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).pixelFrame(fill = InkDark, edge = c.copy(alpha = 0.6f), style = FrameStyle.SOCKET, unit = 2.dp), contentAlignment = Alignment.Center) {
                    Text8(l.glyph, size = 14.sp)
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.width(78.dp)) {
                    Text8(l.metric.uppercase(), size = 9.sp, color = colors.textPrimary, maxLines = 1, letterSpacing = 1.sp)
                    Text8(l.value, size = 10.sp, color = c, maxLines = 1)
                }
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    val u = 2.dp.toPx()
                    val y = size.height / 2
                    drawLine(c.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), strokeWidth = u * 2)
                    drawLine(
                        c,
                        Offset(0f, y),
                        Offset(size.width, y),
                        strokeWidth = u,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * u, 3 * u), -flow * 6 * u),
                    )
                }
                Box(
                    Modifier
                        .pixelFrame(fill = c.copy(alpha = 0.14f), edge = c.copy(alpha = 0.8f), style = FrameStyle.PLATE, unit = 1.dp)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    val badge = when (l.granted) {
                        true -> "✓"
                        false -> "○"
                        null -> ""
                    }
                    Text8("${linkSigil(l.kind)} $badge${l.sourceLabel}".trim(), size = 9.sp, color = c, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DayStone(s: DailyTelemetrySnapshot, selected: Boolean, onClick: () -> Unit) {
    val colors = RpgTheme.colors
    val score = listOf(s.adherence.stepsRatio, s.adherence.sleepRatio, s.adherence.hydrationRatio, s.adherence.proteinRatio)
        .map { it.coerceIn(0.0, 1.0) }.average().toFloat()
    val glow = when {
        score >= 0.9f -> GoldTrim
        score >= 0.6f -> colors.vitalityEmerald
        score > 0f -> colors.accentSecondary
        else -> colors.pixelBorderBright
    }
    val parts = s.date.split("-")
    val month = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC").getOrElse((parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1) { "" }
    Column(
        Modifier
            .width(56.dp)
            .pixelFrame(
                fill = if (selected) glow.mixWith(Color.Black, 0.6f) else Color(0xFF1C1F2A),
                edge = if (selected) GoldTrim else glow.mixWith(colors.pixelBorder, 0.5f),
                style = if (selected) FrameStyle.WINDOW else FrameStyle.PLATE,
                unit = 2.dp,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text8(month, size = 8.sp, color = colors.textMuted, letterSpacing = 1.sp)
        Text8(parts.getOrNull(2) ?: s.date, size = 17.sp, color = if (selected) Color.White else colors.textPrimary, weight = FontWeight.ExtraBold)
        Box(Modifier.padding(top = 3.dp).size(width = 30.dp, height = 4.dp).background(InkDark)) {
            Box(Modifier.size(width = (30 * score).dp, height = 4.dp).background(glow))
        }
    }
}

@Composable
private fun DayLog(s: DailyTelemetrySnapshot) {
    val colors = RpgTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Parchment, edge = Color(0xFF8A6238), rivet = GoldTrim)
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text8("QUEST LOG · ${s.date}", size = 12.sp, color = GoldTrim, weight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
            QuestBar("🥾", "Steps", "%,d / %,d".format(s.steps, s.goals.stepsTarget), s.adherence.stepsRatio, colors.accentPrimary)
            QuestBar("🌙", "Sleep", "${s.sleep.totalMinutes / 60}h${s.sleep.totalMinutes % 60}m / ${s.goals.sleepMinutesTarget / 60}h", s.adherence.sleepRatio, colors.arcaneMana)
            QuestBar("💧", "Water", "%,d / %,d mL".format(s.hydration.totalMl, s.goals.waterMlTarget), s.adherence.hydrationRatio, colors.accentSecondary)
            QuestBar("🍖", "Calories", "%,d / %,d".format(s.nutrition.calories.roundToInt(), s.goals.caloriesTarget), s.adherence.caloriesRatio, colors.vitalityEmerald)
            QuestBar("🥩", "Protein", "${s.nutrition.proteinGrams.roundToInt()} / ${s.goals.proteinGramsTarget.roundToInt()} g", s.adherence.proteinRatio, colors.crimsonHp)
            Text8(
                "Carbs ${s.nutrition.carbsGrams.roundToInt()}/${s.goals.carbsGramsTarget.roundToInt()}g · Fat ${s.nutrition.fatGrams.roundToInt()}/${s.goals.fatGramsTarget.roundToInt()}g",
                size = 9.sp,
                color = colors.textMuted,
            )
            if (s.lifting.byMuscleGroup.isNotEmpty()) {
                RuneHeader("IRON OFFERINGS", color = Color(0xFFC9D2DE), trailing = "%,d kg".format(s.lifting.totalVolumeKg.roundToInt()))
                val top = s.lifting.byMuscleGroup.values.maxOf { it.effectiveVolumeKg }.coerceAtLeast(1.0)
                s.lifting.byMuscleGroup.values.sortedByDescending { it.effectiveVolumeKg }.forEach { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text8(v.muscleGroup.displayName.uppercase(), size = 9.sp, color = colors.textPrimary, modifier = Modifier.width(86.dp), maxLines = 1)
                        SegmentBar((v.effectiveVolumeKg / top).toFloat(), Color(0xFFC9D2DE), Modifier.weight(1f), segments = 12, height = 10.dp)
                        Text8(" ${v.effectiveVolumeKg.roundToInt()}kg·${v.workingSets}s", size = 9.sp, color = colors.textMuted)
                    }
                }
            } else {
                Text8("No iron offered this day.", size = 10.sp, color = colors.textMuted)
            }
            Text8("seal ${s.contentHash.take(12)}", size = 8.sp, color = colors.textMuted.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun QuestBar(glyph: String, label: String, value: String, ratio: Double, color: Color) {
    val colors = RpgTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text8(glyph, size = 12.sp, modifier = Modifier.width(22.dp))
            Text8(label.uppercase(), size = 9.sp, color = colors.textMuted, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            Text8(value, size = 10.sp, color = colors.textPrimary)
            if (ratio >= 1.0) Text8(" ★", size = 10.sp, color = GoldTrim)
        }
        SegmentBar(ratio.toFloat().coerceIn(0f, 1f), color, Modifier.fillMaxWidth(), segments = 20, height = 10.dp)
    }
}

// =============================================================================================
// LEY LINES — Health Connect portal, Sparky sky-relay, chronomancy backfill
// =============================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeyLinesPage(state: SanctumState, actions: SanctumActions) {
    val colors = RpgTheme.colors
    val hcGreen = Color(0xFF5FD068)
    val granted = state.hcPermissions.count { it.second }

    // Health Connect portal
    RuneHeader("PHONE CRYSTAL PORTAL", color = hcGreen, trailing = "$granted/${state.hcPermissions.size}")
    Box(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF0F1A14), edge = hcGreen.mixWith(colors.pixelBorder, 0.5f), rivet = hcGreen)
            .padding(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Portal(state.hcPermissions.map { it.second }, hcGreen, Modifier.size(150.dp))
            Text8("HEALTH CONNECT", size = 13.sp, color = hcGreen, weight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Text8(
                when {
                    !state.hcAvailable -> "The crystal is dormant: Health Connect isn't available on this device."
                    state.hcHasAll -> "Every rune is lit. Essence flows instantly from your phone."
                    state.hcHasAny -> "Some runes are dark. Attune the rest for full power."
                    else -> "Attune the portal to draw steps, sleep, water and meals from your phone."
                },
                size = 10.sp,
                color = colors.textMuted,
                align = TextAlign.Center,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                state.hcPermissions.forEach { (name, ok) -> RuneChip("${if (ok) "✓" else "○"} $name", if (ok) hcGreen else colors.textMuted) }
            }
            state.hcMessage?.let { Text8(it, size = 10.sp, color = colors.accentSecondary, align = TextAlign.Center) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton(if (state.hcHasAll) "MANAGE RUNES" else "✦ ATTUNE PORTAL", hcGreen, onClick = actions.onRequestHealthConnect)
                if (state.hcHasAny) PixelButton("DRAW ESSENCE", colors.accentSecondary, onClick = actions.onSyncHealthConnect)
            }
        }
    }

    // SparkyFitness sky relay
    val hasCreds = state.baseUrl.isNotBlank() && state.apiKey.isNotBlank()
    var editing by remember { mutableStateOf(!hasCreds) }
    RuneHeader("SKY RELAY", color = LeyBlue, trailing = if (hasCreds) "LINKED" else "UNLINKED")
    Box(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF0E1620), edge = LeyBlue.mixWith(colors.pixelBorder, 0.5f), rivet = LeyBlue)
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RelayTower(state.isSyncing, hasCreds, Modifier.size(width = 70.dp, height = 96.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text8("SPARKYFITNESS", size = 13.sp, color = LeyBlue, weight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text8("Backup relay for meals, water, goals & Liftosaur lifting.", size = 10.sp, color = colors.textMuted)
                    Text8(
                        if (hasCreds) state.baseUrl.removePrefix("https://").removeSuffix("/") else "No relay address set",
                        size = 10.sp,
                        color = if (hasCreds) colors.textPrimary else colors.textMuted,
                        maxLines = 1,
                    )
                    Text8(
                        if (editing) "▾ hide relay runes" else "▸ edit relay runes",
                        size = 10.sp,
                        color = LeyBlue,
                        modifier = Modifier.clickable { editing = !editing }.padding(vertical = 2.dp),
                    )
                }
            }
            if (editing) {
                PixelTextField(state.baseUrl, actions.onBaseUrlChange, "RELAY ADDRESS", "https://fitness.yourdomain.com")
                PixelTextField(state.apiKey, actions.onApiKeyChange, "RELAY SIGIL (API KEY)", "x-api-key", secret = true)
            }
            if (state.isSyncing) SyncRunes()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                PixelButton("PING", colors.pixelBorderBright, enabled = !state.isSyncing, small = true, onClick = actions.onPing)
                PixelButton("⚡ SYNC TODAY", LeyBlue, enabled = !state.isSyncing, small = true, onClick = actions.onSyncToday)
                PixelButton("SYNC 14D", colors.accentSecondary, enabled = !state.isSyncing, small = true, onClick = actions.onSync14Days)
            }
            state.statusMessage?.let { Text8("⚡ $it", size = 10.sp, color = colors.vitalityEmerald) }
            state.errorMessage?.let { Text8("⚠ $it", size = 10.sp, color = colors.crimsonHp) }
        }
    }

    // Chronomancy backfill
    RuneHeader("CHRONOMANCY", color = GoldTrim)
    Box(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF1A1610), edge = GoldTrim.mixWith(colors.pixelBorder, 0.5f), rivet = GoldTrim)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hourglass(running = state.backfillProgress != null, progress = state.backfillProgress ?: 0f, modifier = Modifier.size(width = 60.dp, height = 90.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text8("Pull years of history into your averages and strength sigils without flooding today's step bank.", size = 10.sp, color = colors.textMuted)
                val running = state.backfillProgress != null
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PixelButton("1 YR", GoldTrim, enabled = !running, small = true) { actions.onRunBackfill(1) }
                    PixelButton("3 YRS", GoldTrim, enabled = !running, small = true) { actions.onRunBackfill(3) }
                    PixelButton("ALL", GoldTrim, enabled = !running, small = true) { actions.onRunBackfill(5) }
                }
                state.backfillProgress?.let { SegmentBar(it, GoldTrim, Modifier.fillMaxWidth(), segments = 16, height = 12.dp, label = "${(it * 100).roundToInt()}%") }
                state.backfillStatus?.let { Text8(it, size = 9.sp, color = colors.accentSecondary) }
            }
        }
    }
}

@Composable
private fun Portal(runes: List<Boolean>, color: Color, modifier: Modifier) {
    val spin = rememberSpin(9000)
    val tr = rememberInfiniteTransition(label = "portal")
    val pulse by tr.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "portal_p")
    val lit = runes.count { it }
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2
        val u = floor(3.dp.toPx())
        val strength = if (runes.isEmpty()) 0f else lit.toFloat() / runes.size
        // vortex
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f * strength * pulse), Color(0xFF0A120D)), c, r * 0.66f), r * 0.66f, c)
        for (i in 0 until 3) {
            val rr = r * (0.2f + i * 0.15f)
            drawArc(color.copy(alpha = 0.3f * strength), spin * (1 + i) + i * 40f, 220f, false, Offset(c.x - rr, c.y - rr), Size(2 * rr, 2 * rr), style = Stroke(u))
        }
        // stone ring
        drawCircle(InkDark, r * 0.86f, c, style = Stroke(5 * u))
        drawCircle(Color(0xFF3A4154), r * 0.86f, c, style = Stroke(3 * u))
        // rune segments
        val n = runes.size.coerceAtLeast(1)
        runes.forEachIndexed { i, on ->
            val start = -90f + i * 360f / n + 4f
            val sweep = 360f / n - 8f
            drawArc(if (on) color.copy(alpha = pulse) else Color(0xFF232838), start, sweep, false, Offset(c.x - r * 0.86f, c.y - r * 0.86f), Size(r * 1.72f, r * 1.72f), style = Stroke(2 * u))
        }
        // orbiting motes
        for (i in 0 until 6) {
            val a = Math.toRadians((spin * 2 + i * 60).toDouble())
            val rr = r * 0.95f
            drawRect(color.copy(alpha = strength.coerceAtLeast(0.2f)), Offset(c.x + (kotlin.math.cos(a) * rr).toFloat() - u / 2, c.y + (kotlin.math.sin(a) * rr).toFloat() - u / 2), Size(u, u))
        }
    }
}

@Composable
private fun RelayTower(syncing: Boolean, linked: Boolean, modifier: Modifier) {
    val tr = rememberInfiniteTransition(label = "tower")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(if (syncing) 700 else 2400, easing = LinearEasing)), label = "tower_t")
    Canvas(modifier) {
        val u = floor(3.dp.toPx())
        val w = size.width
        val h = size.height
        val cx = w / 2
        val stone = Color(0xFF3A4154)
        // tower
        for (r in 0 until ((h * 0.7f) / u).toInt()) {
            val y = h - r * u
            val half = (6 - r / 8).coerceAtLeast(3) * u
            drawRect(InkDark, Offset(cx - half - u, y - u), Size(2 * half + 2 * u, u))
            drawRect(if (r % 4 == 0) Color(0xFF2E3444) else stone, Offset(cx - half, y - u), Size(2 * half, u))
        }
        val topY = h * 0.3f
        // crystal
        val glow = if (linked) LeyBlue else Color(0xFF4A5566)
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.6f), Color.Transparent), Offset(cx, topY - 4 * u), 9 * u), 9 * u, Offset(cx, topY - 4 * u))
        val p = Path().apply { moveTo(cx, topY - 9 * u); lineTo(cx + 3 * u, topY - 4 * u); lineTo(cx, topY); lineTo(cx - 3 * u, topY - 4 * u); close() }
        drawPath(p, glow)
        // signal rings rising
        if (linked) {
            for (i in 0 until 3) {
                val ph = (t + i / 3f) % 1f
                val rr = (4 + ph * 14) * u
                drawArc(LeyBlue.copy(alpha = (1f - ph) * 0.8f), 200f, 140f, false, Offset(cx - rr, topY - 4 * u - rr), Size(2 * rr, 2 * rr), style = Stroke(u))
            }
        }
    }
}

@Composable
private fun SyncRunes() {
    val tr = rememberInfiniteTransition(label = "syncrunes")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "sr_t")
    val runes = "ᚠᚢᚦᚨᚱᚲᚷᚹᚺᚾᛁᛃᛇᛈᛉᛊ"
    Row(Modifier.fillMaxWidth().clipToBounds(), horizontalArrangement = Arrangement.Center) {
        runes.forEachIndexed { i, ch ->
            val a = ((sin((t * 6.283f) - i * 0.5f) + 1f) / 2f).coerceIn(0.15f, 1f)
            Text8(ch.toString(), size = 14.sp, color = LeyBlue.copy(alpha = a))
        }
    }
}

@Composable
private fun Hourglass(running: Boolean, progress: Float, modifier: Modifier) {
    val tr = rememberInfiniteTransition(label = "hourglass")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "hg_t")
    Canvas(modifier) {
        val u = floor(3.dp.toPx())
        val w = size.width
        val h = size.height
        val cx = w / 2
        val wood = Color(0xFF8A6238)
        chamfer(cx - 9 * u, 0f, cx + 9 * u, 3 * u, u, wood)
        chamfer(cx - 9 * u, h - 3 * u, cx + 9 * u, h, u, wood)
        drawRect(wood, Offset(cx - 8 * u, 3 * u), Size(u, h - 6 * u))
        drawRect(wood, Offset(cx + 7 * u, 3 * u), Size(u, h - 6 * u))
        val midY = h / 2
        val glass = Color(0xFF2A3040)
        val rows = ((midY - 4 * u) / u).toInt()
        val sandTopLevel = if (running) 1f - progress else 0.85f
        for (r in 0 until rows) {
            val f = r.toFloat() / rows
            val half = (6 * u * (1f - f)).coerceAtLeast(u * 0.5f)
            val yTop = 4 * u + r * u
            val yBot = h - 4 * u - (r + 1) * u
            drawRect(glass, Offset(cx - half, yTop), Size(2 * half, u))
            drawRect(glass, Offset(cx - half, yBot), Size(2 * half, u))
            if (f > 1f - sandTopLevel) drawRect(GoldTrim, Offset(cx - half, yTop), Size(2 * half, u))
            if (f > sandTopLevel) drawRect(GoldTrim.mixWith(Color.Black, 0.15f), Offset(cx - half, yBot), Size(2 * half, u))
        }
        if (running) {
            for (i in 0 until 4) {
                val ph = (t + i / 4f) % 1f
                drawRect(GoldTrim, Offset(cx - u / 2, midY - 2 * u + ph * (midY - 6 * u)), Size(u, u))
            }
        }
    }
}

@Composable
private fun PixelTextField(value: String, onChange: (String) -> Unit, label: String, hint: String, secret: Boolean = false) {
    val colors = RpgTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text8(label, size = 9.sp, color = colors.textMuted, letterSpacing = 1.5.sp)
        Box(
            Modifier
                .fillMaxWidth()
                .pixelFrame(fill = InkDark, edge = LeyBlue.copy(alpha = 0.6f), style = FrameStyle.SOCKET, unit = 2.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (value.isEmpty()) Text8(hint, size = 12.sp, color = colors.textMuted.copy(alpha = 0.5f), weight = FontWeight.Normal)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = colors.textPrimary),
                cursorBrush = SolidColor(LeyBlue),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// =============================================================================================
// OPTIONS — realm crests, living-painting splash picker, version plaque, rite of rebirth
// =============================================================================================

@Composable
private fun OptionsPage(state: SanctumState, actions: SanctumActions) {
    val colors = RpgTheme.colors

    RuneHeader("REALM CRESTS", color = colors.accentPrimary, trailing = state.theme.displayName.uppercase())
    SparkborneThemeId.entries.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { t -> RealmCrest(t, selected = t == state.theme) { actions.onSelectTheme(t) } }
            repeat(4 - row.size) { Spacer(Modifier.width(78.dp)) }
        }
    }

    RuneHeader("SPLASH PAINTINGS", color = GoldTrim)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SplashStyleId.entries.forEach { s ->
            val selected = s == state.splash
            Column(
                Modifier.weight(1f).clickable { actions.onSelectSplash(s) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .pixelFrame(fill = InkDark, edge = if (selected) GoldTrim else Color(0xFF6B4A2C), rivet = if (selected) GoldTrim else Color(0xFF8A6238))
                        .padding(7.dp),
                ) {
                    SplashScenePreview(splashScene(s), state.theme.palette, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(4.dp))
                Text8(s.displayName.uppercase(), size = 8.sp, color = if (selected) GoldTrim else colors.textMuted, align = TextAlign.Center, maxLines = 1, letterSpacing = 0.5.sp)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        PixelButton("▶ REPLAY SPLASH", GoldTrim, small = true, onClick = actions.onReplaySplash)
    }

    RuneHeader("CHRONICLE OF VERSIONS", color = colors.accentSecondary)
    Box(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF1C1F2A), edge = colors.accentSecondary.mixWith(colors.pixelBorder, 0.5f), rivet = colors.accentSecondary)
            .padding(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .pixelFrame(fill = Color(0xFF3A3020), edge = GoldTrim, style = FrameStyle.PLATE, unit = 2.dp)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) { Text8(if (state.versionLabel.startsWith("v")) state.versionLabel else "v${state.versionLabel}", size = 16.sp, color = GoldTrim, weight = FontWeight.ExtraBold, letterSpacing = 2.sp) }
            state.updateStatus?.let { Text8(it, size = 10.sp, color = colors.accentSecondary, align = TextAlign.Center) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton("⟳ SEEK UPDATES", colors.accentSecondary, small = true, onClick = actions.onCheckUpdates)
                PixelButton("REPLAY INTRO", colors.pixelBorderBright, small = true, onClick = actions.onReplaySplash)
            }
        }
    }

    RuneHeader("RITE OF REBIRTH", color = colors.crimsonHp)
    RiteOfRebirth(actions.onResetJourney)
}

@Composable
private fun RealmCrest(t: SparkborneThemeId, selected: Boolean, onClick: () -> Unit) {
    val colors = RpgTheme.colors
    val p = t.palette
    val bob = if (selected) rememberBob(2f, 1200) else 0f
    Column(Modifier.width(78.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 58.dp, height = 66.dp).padding(top = (2 + bob).coerceAtLeast(0f).dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val u = floor(3.dp.toPx())
                fun shield(i: Float) = Path().apply {
                    moveTo(i, i); lineTo(w - i, i); lineTo(w - i, h * 0.55f); lineTo(w / 2, h - i); lineTo(i, h * 0.55f); close()
                }
                if (selected) drawCircle(Brush.radialGradient(listOf(p.accentPrimary.copy(alpha = 0.5f), Color.Transparent)), radius = w * 0.75f)
                drawPath(shield(0f), InkDark)
                drawPath(shield(u), if (selected) GoldTrim else colors.pixelBorderBright)
                drawPath(shield(2 * u), p.obsidianBg)
                // quartered colours
                drawRect(p.accentPrimary, Offset(2 * u, 2 * u), Size(w / 2 - 2 * u, h * 0.3f))
                drawRect(p.accentSecondary, Offset(w / 2, 2 * u), Size(w / 2 - 2 * u, h * 0.3f))
                drawRect(p.particlePrimary.copy(alpha = 0.6f), Offset(w / 2 - u, 2 * u), Size(2 * u, h * 0.7f))
            }
            Text8(elementGlyph(t), size = 16.sp, modifier = Modifier.padding(top = 18.dp))
        }
        Text8(t.elementTag.uppercase(), size = 8.sp, color = if (selected) GoldTrim else colors.textPrimary, letterSpacing = 1.sp)
        Text8(t.displayName, size = 7.sp, color = colors.textMuted, maxLines = 1, align = TextAlign.Center)
    }
}

private fun elementGlyph(t: SparkborneThemeId): String = when (t.elementTag.lowercase()) {
    "fire" -> "🔥"
    "lightning" -> "⚡"
    "water" -> "🌊"
    "plant" -> "🌿"
    "poison" -> "☠"
    "void" -> "🌀"
    else -> "🧭"
}

@Composable
private fun RiteOfRebirth(onConfirm: () -> Unit) {
    val colors = RpgTheme.colors
    var armed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF1E1012), edge = colors.crimsonHp.mixWith(colors.pixelBorder, 0.4f), rivet = colors.crimsonHp)
            .padding(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text8(if (armed) "☠" else "🕯", size = 34.sp)
            Text8(
                "Begin again from Level 0 and the Intro Egg Quest. Your synced health history is kept.",
                size = 10.sp,
                color = colors.textMuted,
                align = TextAlign.Center,
            )
            if (!armed) {
                PixelButton("BEGIN THE RITE", colors.crimsonHp, small = true) { armed = true }
            } else {
                Text8("This erases your hero's progress. Are you certain?", size = 11.sp, color = colors.crimsonHp, align = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton("☠ BE REBORN", colors.crimsonHp) {
                        armed = false
                        onConfirm()
                    }
                    PixelButton("STAY MY HAND", colors.pixelBorderBright) { armed = false }
                }
            }
        }
    }
}
