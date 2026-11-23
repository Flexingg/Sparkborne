package com.sparkborne.app.ui.hero

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.CollectibleEngine
import com.sparkborne.game.engine.CombatStatsCalculator
import com.sparkborne.game.engine.SkillEngine
import com.sparkborne.game.engine.ToolbeltEngine
import com.sparkborne.game.model.CollectibleBonusType
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.SkillType
import com.sparkborne.telemetry.model.MuscleGroup
import kotlin.math.floor
import kotlin.math.sin

enum class CharacterPage(val label: String, val glyph: String) {
    GEAR("Gear", "⚔"),
    SKILLS("Skills", "⚒"),
    TALENTS("Talents", "❖"),
    SPELLS("Spells", "✦"),
    RELICS("Relics", "♜"),
}

/**
 * The hero's character screen: a game-style equipment stage, skill medallions, a spellbook and
 * relic shelves. Everything opens into a full inspect window.
 *
 * [heroPortrait] and [companionPortrait] are supplied by the platform layer (bitmap sprites).
 */
@Composable
fun CharacterScreen(
    state: GameState,
    errorMessage: String?,
    actions: HeroActions,
    heroPortrait: @Composable (Dp) -> Unit,
    companionPortrait: (@Composable (Dp) -> Unit)?,
    initialPage: CharacterPage = CharacterPage.GEAR,
    initialInspect: Inspect? = null,
) {
    var page by remember { mutableStateOf(initialPage) }
    var inspect by remember { mutableStateOf(initialInspect) }
    val open: (Inspect) -> Unit = { inspect = it }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BannerTabs(CharacterPage.entries, page, { it.label }, { it.glyph }) { page = it }
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "char_page",
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
                        CharacterPage.GEAR -> GearPage(state, heroPortrait, companionPortrait, open)
                        CharacterPage.SKILLS -> SkillsPage(state, open)
                        CharacterPage.TALENTS -> TalentsPage(state, actions, open)
                        CharacterPage.SPELLS -> SpellsPage(state, open)
                        CharacterPage.RELICS -> RelicsPage(state, open)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        inspect?.let { current ->
            InspectOverlay(current, state, actions, errorMessage, onNavigate = open, onClose = { inspect = null })
        }
    }
}

// =============================================================================================
// Hanging banner tabs
// =============================================================================================

@Composable
fun <T> BannerTabs(items: List<T>, selected: T, label: (T) -> String, glyph: (T) -> String, onSelect: (T) -> Unit) {
    val colors = RpgTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(58.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            val active = item == selected
            val c = if (active) colors.accentPrimary else colors.pixelBorderBright
            Box(
                Modifier
                    .weight(1f)
                    .height(if (active) 58.dp else 48.dp)
                    .clickable { onSelect(item) },
                contentAlignment = Alignment.TopCenter,
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val u = floor(2.dp.toPx())
                    val w = size.width
                    val h = size.height
                    // rod
                    drawRect(Color(0xFF6B5030), Offset(0f, 0f), Size(w, 2 * u))
                    val notch = h - 4 * u
                    val outer = Path().apply { moveTo(u, 2 * u); lineTo(w - u, 2 * u); lineTo(w - u, h); lineTo(w / 2, notch); lineTo(u, h); close() }
                    drawPath(outer, InkDark)
                    val inner = Path().apply { moveTo(3 * u, 2 * u); lineTo(w - 3 * u, 2 * u); lineTo(w - 3 * u, h - 3 * u); lineTo(w / 2, notch - 3 * u); lineTo(3 * u, h - 3 * u); close() }
                    drawPath(inner, if (active) c.mixWith(Color.Black, 0.55f) else colors.panelSurface)
                    drawRect(c, Offset(3 * u, 2 * u), Size(w - 6 * u, u))
                    if (active) drawRect(c.copy(alpha = 0.6f), Offset(3 * u, h - 9 * u), Size(w - 6 * u, u))
                }
                Column(Modifier.padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text8(glyph(item), size = 14.sp, color = if (active) c else colors.textMuted)
                    Text8(label(item).uppercase(), size = 8.sp, color = if (active) c else colors.textMuted, letterSpacing = 1.sp)
                }
            }
        }
    }
}

// =============================================================================================
// GEAR PAGE
// =============================================================================================

@Composable
private fun GearPage(
    state: GameState,
    heroPortrait: @Composable (Dp) -> Unit,
    companionPortrait: (@Composable (Dp) -> Unit)?,
    open: (Inspect) -> Unit,
) {
    val colors = RpgTheme.colors
    val p = state.player
    val stats = CombatStatsCalculator.calculate(state)

    HeroStage(state, heroPortrait, companionPortrait, open)

    // Vitals: crystal bars + power glyphs, sitting directly on the page
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text8("❤", size = 14.sp, color = colors.crimsonHp, modifier = Modifier.width(22.dp))
            SegmentBar(1f, colors.crimsonHp, Modifier.weight(1f), segments = 16, height = 16.dp, label = "${stats.maxHp} HP")
            Spacer(Modifier.width(10.dp))
            Text8("✦", size = 14.sp, color = colors.arcaneMana, modifier = Modifier.width(22.dp))
            SegmentBar(1f, colors.arcaneMana, Modifier.weight(0.8f), segments = 10, height = 16.dp, label = "${stats.maxMana} MP")
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatGlyph("⚔", "${stats.weaponDamage}", colors.accentPrimary, caption = "ATTACK")
            StatGlyph("✧", "${stats.spellPower}", colors.arcaneMana, caption = "SPELL")
            StatGlyph("⛨", "${stats.defense}", colors.accentSecondary, caption = "DEFENSE")
            if (stats.monsterSkillDamage > 0) StatGlyph("🐾", "${stats.monsterSkillDamage}", colors.vitalityEmerald, caption = "BEAST")
        }
    }

    // Blessings from today's real-world telemetry
    val b = p.activeBuffs
    val blessings = listOfNotNull(
        b.sleepVitalityHpBonus.takeIf { it > 0 }?.let { "💤 +$it HP" },
        b.hydrationClarityManaBonus.takeIf { it > 0 }?.let { "💧 +$it MP" },
        b.proteinFortitudeDefenseBonus.takeIf { it > 0 }?.let { "🥩 +$it DEF" },
        b.carbsSpellPowerBonus.takeIf { it > 0 }?.let { "🌾 +$it SPL" },
        b.fatEnduranceCritBonusPercent.takeIf { it > 0 }?.let { "🥑 +$it% CRIT" },
    )
    RuneHeader("BLESSINGS", color = colors.vitalityEmerald, trailing = b.momentumStance.displayName.uppercase())
    if (blessings.isEmpty()) {
        Text8("Sync sleep, water and meals to gain today's blessings.", size = 10.sp, color = colors.textMuted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            blessings.forEach { RuneChip(it, colors.vitalityEmerald) }
        }
    }

    // Spell sigils
    RuneHeader("ATTUNED SIGILS", color = colors.arcaneMana)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
        for (i in 0 until 3) {
            val s = p.attunedSpellIds.getOrNull(i)?.let(GameCatalog::spellById)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GemSocket(s?.let { elementColor(it.element) }, 58.dp, onClick = { open(Inspect.Spell(s?.id ?: GameCatalog.spells.first().id)) }) {
                    if (s != null) PixelArtIcon(spellOrbArt(s.element), 30.dp) else Text8("◇", size = 16.sp, color = colors.textMuted)
                }
                Text8(s?.name?.substringBefore(" ") ?: "EMPTY", size = 8.sp, color = if (s != null) colors.arcaneMana else colors.textMuted, maxLines = 1)
            }
        }
    }

    // Toolbelt
    RuneHeader("TOOLBELT", color = colors.vitalityEmerald, trailing = "${p.equippedToolIds.size}/${p.maxToolbeltSlots()}")
    Toolbelt(state, open)

    // Loadouts
    RuneHeader("LOADOUTS", color = GoldTrim)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        LOADOUT_NAMES.forEachIndexed { idx, name ->
            val saved = p.gearSets.firstOrNull { it.slotIndex == idx }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { open(Inspect.Loadout(idx)) }.alpha(if (saved != null) 1f else 0.55f),
            ) {
                BannerFlag(name.first().toString(), if (saved != null) GoldTrim else colors.pixelBorderBright, 62.dp)
                Text8(name.uppercase(), size = 9.sp, color = if (saved != null) GoldTrim else colors.textMuted, letterSpacing = 1.5.sp)
            }
        }
    }

    // Strength sigils
    val weapon = GameCatalog.weaponById(p.equippedWeaponId)
    RuneHeader("STRENGTH SIGILS", color = colors.accentPrimary)
    val tracked = MuscleGroup.entries.filter { it != MuscleGroup.OTHER }
    tracked.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { g ->
                val (rank, _, frac) = muscleProgress(p, g)
                val lit = g in weapon.weaponClass.scalingMuscles
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(76.dp).clickable { open(Inspect.Muscle(g)) },
                ) {
                    MuscleSigil(rank, frac, if (lit) colors.accentPrimary else colors.accentSecondary, 56.dp, lit)
                    Text8(g.displayName.uppercase(), size = 8.sp, color = if (lit) colors.accentPrimary else colors.textMuted, maxLines = 1, letterSpacing = 1.sp)
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.width(76.dp)) }
        }
    }
}

/** Torch-lit dungeon alcove: hero on a rune circle, equipment sockets wired to the hero. */
@Composable
private fun HeroStage(
    state: GameState,
    heroPortrait: @Composable (Dp) -> Unit,
    companionPortrait: (@Composable (Dp) -> Unit)?,
    open: (Inspect) -> Unit,
) {
    val colors = RpgTheme.colors
    val p = state.player
    val tr = rememberInfiniteTransition(label = "stage")
    val clock by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "stage_clock")
    val bob = rememberBob(3f, 1300)
    val accent = colors.accentPrimary
    val socket = 62.dp

    BoxWithConstraints(Modifier.fillMaxWidth().height(380.dp)) {
        val wPx = constraints.maxWidth.toFloat()
        Canvas(Modifier.matchParentSize()) {
            val u = floor(3.dp.toPx())
            val w = size.width
            val h = size.height
            // back wall
            chamfer(0f, 0f, w, h, u, InkDark)
            drawRect(Brush.verticalGradient(listOf(Color(0xFF14161F), Color(0xFF0B0C12))), Offset(u, u), Size(w - 2 * u, h - 2 * u))
            // bricks
            var y = u
            var row = 0
            while (y < h * 0.72f) {
                var x = if (row % 2 == 0) u else u - 8 * u
                while (x < w) {
                    drawRect(Color.White.copy(alpha = 0.035f), Offset(x, y), Size(15 * u, 6 * u))
                    drawRect(Color.Black.copy(alpha = 0.35f), Offset(x, y + 6 * u), Size(16 * u, u))
                    x += 16 * u
                }
                y += 7 * u
                row++
            }
            // arch window
            val archW = w * 0.34f
            val archTop = h * 0.08f
            drawRect(Color(0xFF1C2033), Offset(w / 2 - archW / 2, archTop + archW / 2), Size(archW, h * 0.5f))
            drawCircle(Color(0xFF1C2033), archW / 2, Offset(w / 2, archTop + archW / 2))
            drawCircle(accent.copy(alpha = 0.10f), archW / 2 - 2 * u, Offset(w / 2, archTop + archW / 2))
            // floor
            val floorY = h * 0.72f
            drawRect(Color(0xFF181A24), Offset(u, floorY), Size(w - 2 * u, h - floorY - u))
            drawRect(Color.White.copy(alpha = 0.06f), Offset(u, floorY), Size(w - 2 * u, u))
            // rune circle (pulsing, rotating ticks)
            val cx = w / 2
            val cy = floorY + (h - floorY) * 0.42f
            val pulse = 0.6f + 0.4f * sin(clock * 6.283f)
            drawOval(accent.copy(alpha = 0.18f * pulse), Offset(cx - w * 0.22f, cy - h * 0.05f), Size(w * 0.44f, h * 0.10f))
            drawOval(accent.copy(alpha = 0.7f), Offset(cx - w * 0.2f, cy - h * 0.045f), Size(w * 0.4f, h * 0.09f), style = Stroke(u))
            for (i in 0 until 12) {
                val a = (i / 12f + clock / 3f) * 6.283f
                val px = cx + kotlin.math.cos(a) * w * 0.2f
                val py = cy + sin(a) * h * 0.045f
                drawRect(accent, Offset(floor(px / u) * u, floor(py / u) * u), Size(u, u))
            }
            // torches with flicker
            listOf(w * 0.13f, w * 0.87f).forEachIndexed { i, tx ->
                val ty = h * 0.2f
                val fl = 0.75f + 0.25f * sin(clock * 6.283f * (3 + i) + i)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFA040).copy(alpha = 0.28f * fl), Color.Transparent), Offset(tx, ty), w * 0.22f), w * 0.22f, Offset(tx, ty))
                drawRect(Color(0xFF4A3420), Offset(tx - u, ty + 2 * u), Size(2 * u, 6 * u))
                drawRect(Color(0xFF2A2A33), Offset(tx - 2 * u, ty + 2 * u), Size(4 * u, u))
                val fh = (3 + (fl * 3).toInt()) * u
                drawRect(Color(0xFFFF6A1A), Offset(tx - 2 * u, ty + 2 * u - fh), Size(4 * u, fh))
                drawRect(Color(0xFFFFC44A), Offset(tx - u, ty + 2 * u - fh + u), Size(2 * u, fh - u))
            }
            // tether lines from sockets to hero
            val sockPx = socket.toPx()
            val leftX = 12.dp.toPx() + sockPx
            val rightX = w - 12.dp.toPx() - sockPx
            val heroC = Offset(cx, h * 0.45f)
            val ys = listOf(0.16f, 0.42f, 0.68f).map { it * h + sockPx / 2 - 12.dp.toPx() }
            val dash = PathEffect.dashPathEffect(floatArrayOf(2 * u, 2 * u), clock * 8 * u)
            ys.forEach { yy ->
                drawLine(accent.copy(alpha = 0.28f), Offset(leftX, yy), heroC, strokeWidth = u * 0.7f, pathEffect = dash)
                drawLine(accent.copy(alpha = 0.28f), Offset(rightX, yy), heroC, strokeWidth = u * 0.7f, pathEffect = dash)
            }
        }

        // Level crest + name plate
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LevelCrest(p.level, accent)
        }

        // Hero
        Box(Modifier.align(Alignment.Center).offset(y = (bob - 8).dp)) { heroPortrait(132.dp) }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).width(200.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text8("SPARKBORNE KNIGHT", size = 12.sp, color = colors.textPrimary, weight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            SegmentBar(
                progress = p.xp.toFloat() / p.xpToNextLevel().coerceAtLeast(1),
                color = accent,
                segments = 14,
                height = 12.dp,
                label = "${p.xp}/${p.xpToNextLevel()} XP",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Left sockets: Helm, Chest, Legs
        val maxH = this.maxHeight
        listOf(GearSlot.HELM, GearSlot.CHEST, GearSlot.LEGS).forEachIndexed { i, slot ->
            Box(Modifier.offset(x = 12.dp, y = maxH * listOf(0.16f, 0.42f, 0.68f)[i] - 12.dp)) {
                GearSocket(slot, state, socket) { open(Inspect.Gear(slot)) }
            }
        }
        // Right sockets: Weapon, Relic, Companion
        listOf(GearSlot.WEAPON, GearSlot.RELIC).forEachIndexed { i, slot ->
            Box(Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = maxH * listOf(0.16f, 0.42f)[i] - 12.dp)) {
                GearSocket(slot, state, socket) { open(Inspect.Gear(slot)) }
            }
        }
        val mon = state.activeMonster
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = maxH * 0.68f - 12.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ItemSocket(socket, glow = if (mon != null) colors.vitalityEmerald else null) {
                    if (mon != null && companionPortrait != null) companionPortrait(44.dp) else Text8("🥚", size = 22.sp, modifier = Modifier.alpha(0.4f))
                }
                Text8(mon?.name?.uppercase() ?: "BEAST", size = 8.sp, color = if (mon != null) colors.vitalityEmerald else colors.textMuted, maxLines = 1)
            }
        }
        if (wPx <= 0f) Spacer(Modifier)
    }
}

@Composable
private fun GearSocket(slot: GearSlot, state: GameState, size: Dp, onClick: () -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val id = equippedIdFor(slot, p)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (slot == GearSlot.WEAPON) {
            val w = GameCatalog.weaponById(p.equippedWeaponId)
            val r = weaponRarity(w)
            ItemSocket(size, glow = r.color, onClick = onClick) { PixelArtIcon(weaponArt(w.weaponClass, r), size * 0.62f) }
        } else {
            val a = id?.let(GameCatalog::armorById)
            ItemSocket(size, glow = a?.let { armorRarity(it).color }, onClick = onClick) {
                val r = a?.let(::armorRarity) ?: Rarity.COMMON
                PixelArtIcon(armorArt(slot.armorSlot!!, r), size * 0.62f, Modifier.alpha(if (a != null) 1f else 0.18f))
            }
        }
        Text8(slot.label.uppercase(), size = 8.sp, color = if (id != null) colors.textPrimary else colors.textMuted, letterSpacing = 1.sp)
    }
}

@Composable
private fun LevelCrest(level: Int, color: Color) {
    Box(Modifier.size(width = 52.dp, height = 58.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val shield = Path().apply {
                moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h * 0.55f); lineTo(w / 2, h); lineTo(0f, h * 0.55f); close()
            }
            drawPath(shield, InkDark)
            val u = floor(3.dp.toPx())
            val inner = Path().apply {
                moveTo(u, u); lineTo(w - u, u); lineTo(w - u, h * 0.55f - u * 0.5f); lineTo(w / 2, h - 2 * u); lineTo(u, h * 0.55f - u * 0.5f); close()
            }
            drawPath(inner, color.mixWith(Color.Black, 0.5f))
            drawRect(color, Offset(u, u), Size(w - 2 * u, u))
            drawPath(inner, color, style = Stroke(u * 0.7f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-4).dp)) {
            Text8("LV", size = 8.sp, color = color, letterSpacing = 1.sp)
            Text8("$level", size = 17.sp, color = Color.White, weight = FontWeight.ExtraBold)
        }
    }
}

/** Diamond-shaped gem socket. */
@Composable
fun GemSocket(glow: Color?, size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = RpgTheme.colors
    Box(Modifier.size(size).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.minDimension / 2
            fun diamond(rr: Float) = Path().apply { moveTo(c.x, c.y - rr); lineTo(c.x + rr, c.y); lineTo(c.x, c.y + rr); lineTo(c.x - rr, c.y); close() }
            if (glow != null) drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), Color.Transparent), c, r), r, c)
            drawPath(diamond(r), InkDark)
            drawPath(diamond(r - 3.dp.toPx()), glow?.mixWith(colors.pixelBorder, 0.4f) ?: colors.pixelBorder)
            drawPath(diamond(r - 6.dp.toPx()), colors.obsidianBg)
        }
        content()
    }
}

/** A stitched leather belt with pouches; locked slots show padlocks. */
@Composable
private fun Toolbelt(state: GameState, open: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val maxSlots = 4
    Box(Modifier.fillMaxWidth().height(86.dp)) {
        Canvas(Modifier.fillMaxWidth().height(26.dp).align(Alignment.Center)) {
            val u = floor(2.dp.toPx())
            val w = size.width
            val h = size.height
            drawRect(InkDark, Offset(0f, 0f), Size(w, h))
            drawRect(Color(0xFF6B4226), Offset(0f, u), Size(w, h - 2 * u))
            drawRect(Color(0xFF85552F), Offset(0f, u), Size(w, u))
            var x = 0f
            while (x < w) {
                drawRect(Color(0xFFC89A5E).copy(alpha = 0.6f), Offset(x, 3 * u), Size(2 * u, u))
                drawRect(Color(0xFFC89A5E).copy(alpha = 0.6f), Offset(x, h - 4 * u), Size(2 * u, u))
                x += 5 * u
            }
            // buckle
            val bx = w - 16 * u
            drawRect(GoldTrim, Offset(bx, -u), Size(10 * u, h + 2 * u))
            drawRect(Color(0xFF6B4226), Offset(bx + 2 * u, u), Size(6 * u, h - 2 * u))
            drawRect(GoldTrim.mixWith(Color.Black, 0.3f), Offset(bx + 4 * u, 0f), Size(2 * u, h))
        }
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 8.dp, end = 52.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (i in 0 until maxSlots) {
                val unlocked = i < p.maxToolbeltSlots()
                val tool = p.equippedToolIds.getOrNull(i)?.let(ToolbeltEngine::toolById)
                ItemSocket(
                    60.dp,
                    glow = if (tool != null) colors.vitalityEmerald else null,
                    onClick = if (unlocked) ({ open(Inspect.Tool(tool?.id)) }) else null,
                ) {
                    when {
                        !unlocked -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PixelArtIcon(lockArt, 22.dp)
                            Text8("LV.${if (i == 2) 5 else 10}", size = 7.sp, color = colors.textMuted)
                        }
                        tool != null -> Text8(tool.icon, size = 24.sp)
                        else -> Text8("+", size = 18.sp, color = colors.textMuted)
                    }
                }
            }
        }
    }
}

// =============================================================================================
// SKILLS PAGE
// =============================================================================================

@Composable
private fun SkillsPage(state: GameState, open: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val total = p.totalSkillLevel()
    val max = SkillType.entries.size * 99

    // Mastery emblem
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            val spin = rememberSpin(20000)
            Canvas(Modifier.matchParentSize()) {
                val u = floor(2.dp.toPx())
                drawCircle(Brush.radialGradient(listOf(colors.accentPrimary.copy(alpha = 0.3f), Color.Transparent)))
                for (i in 0 until 16) {
                    val a = Math.toRadians((spin + i * 22.5).toDouble())
                    val r = size.minDimension / 2 - u
                    drawRect(colors.accentPrimary.copy(alpha = 0.6f), Offset(center.x + (kotlin.math.cos(a) * r).toFloat() - u, center.y + (kotlin.math.sin(a) * r).toFloat() - u), Size(2 * u, 2 * u))
                }
                drawCircle(InkDark, radius = size.minDimension / 2 - 5 * u)
                pixelRing(total.toFloat() / max, colors.accentPrimary, colors.panelElevated, 3 * u)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text8("$total", size = 24.sp, color = Color.White, weight = FontWeight.ExtraBold)
                Text8("/ $max", size = 9.sp, color = colors.textMuted)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text8("TOTAL MASTERY", size = 13.sp, color = colors.accentPrimary, weight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Text8("Every 2 levels: +1% efficiency", size = 10.sp, color = colors.textMuted)
            Text8("Every 5 cycles: a skill chest", size = 10.sp, color = colors.textMuted)
        }
    }

    // Chest hoard
    val chests = p.skillChests.filter { it.value > 0 }
    if (chests.isNotEmpty()) {
        RuneHeader("SPOILS TO OPEN", color = GoldTrim, trailing = "${p.totalSkillChests()}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            val bounce = rememberBob(2f, 500)
            chests.entries.take(5).forEach { (skill, n) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { open(Inspect.Chest(skill)) }) {
                    Box(Modifier.offset(y = bounce.dp)) { PixelArtIcon(chestArt, 44.dp) }
                    Text8("${skill.icon}×$n", size = 10.sp, color = GoldTrim)
                }
            }
        }
    }

    RuneHeader("SKILLS", color = colors.accentSecondary)
    SkillType.entries.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { skill ->
                val xp = p.skillXp[skill] ?: 0
                val lvl = SkillEngine.levelForXp(xp)
                val (cur, need) = SkillEngine.xpToNextLevel(xp)
                val glow = if (lvl > 1) colors.accentSecondary else colors.pixelBorderBright
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(100.dp).clickable { open(Inspect.Skill(skill)) }.padding(vertical = 6.dp),
                ) {
                    SkillMedallion(skill, lvl, if (lvl >= 99) 1f else cur.toFloat() / need, 76.dp, glow, chests = p.skillChests[skill] ?: 0)
                    Spacer(Modifier.height(8.dp))
                    Text8(skill.displayName.uppercase(), size = 9.sp, color = if (lvl > 1) colors.textPrimary else colors.textMuted, letterSpacing = 1.sp)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.width(100.dp)) }
        }
    }
}

// =============================================================================================
// SPELLS PAGE — an open spellbook
// =============================================================================================

@Composable
private fun SpellsPage(state: GameState, open: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player

    RuneHeader("ATTUNED", color = colors.arcaneMana, trailing = "${p.attunedSpellIds.size}/3")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
        for (i in 0 until 3) {
            val s = p.attunedSpellIds.getOrNull(i)?.let(GameCatalog::spellById)
            GemSocket(s?.let { elementColor(it.element) }, 66.dp, onClick = { s?.let { open(Inspect.Spell(it.id)) } }) {
                if (s != null) PixelArtIcon(spellOrbArt(s.element), 34.dp) else Text8("◇", size = 18.sp, color = colors.textMuted)
            }
        }
    }

    val spells = GameCatalog.spells
    val pages = spells.chunked((spells.size + 1) / 2)
    Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val u = floor(3.dp.toPx())
            val w = size.width
            val h = size.height
            // cover
            chamfer(0f, 0f, w, h, u, InkDark)
            chamfer(u, u, w - u, h - u, u, Color(0xFF5A2E1E))
            // pages
            val parchment = Color(0xFF2B2620)
            chamfer(3 * u, 3 * u, w / 2 - u, h - 3 * u, u, parchment)
            chamfer(w / 2 + u, 3 * u, w - 3 * u, h - 3 * u, u, parchment)
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f), Color.Transparent), w / 2 - 8 * u, w / 2 + 8 * u), Offset(w / 2 - 8 * u, 3 * u), Size(16 * u, h - 6 * u))
            drawRect(InkDark, Offset(w / 2 - u / 2, 2 * u), Size(u, h - 4 * u))
            // ruled lines
            var y = 8 * u
            while (y < h - 6 * u) {
                drawRect(Color.White.copy(alpha = 0.03f), Offset(5 * u, y), Size(w / 2 - 8 * u, u * 0.5f))
                drawRect(Color.White.copy(alpha = 0.03f), Offset(w / 2 + 3 * u, y), Size(w / 2 - 8 * u, u * 0.5f))
                y += 6 * u
            }
            // ribbon bookmark
            drawRect(colors.arcaneMana, Offset(w * 0.72f, 0f), Size(3 * u, 9 * u))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {
            pages.forEach { pageSpells ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    pageSpells.forEach { s ->
                        val unlocked = s.id in p.unlockedSpellIds || s.unlockShardsCost == 0
                        val attuned = s.id in p.attunedSpellIds
                        val c = elementColor(s.element)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { open(Inspect.Spell(s.id)) },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (attuned) {
                                    val spin = rememberSpin(5000)
                                    Canvas(Modifier.size(64.dp)) {
                                        for (i in 0 until 8) {
                                            val a = Math.toRadians((spin + i * 45).toDouble())
                                            val r = size.minDimension / 2 - 2.dp.toPx()
                                            drawRect(c, Offset(center.x + (kotlin.math.cos(a) * r).toFloat(), center.y + (kotlin.math.sin(a) * r).toFloat()), Size(3.dp.toPx(), 3.dp.toPx()))
                                        }
                                    }
                                }
                                PixelArtIcon(spellOrbArt(s.element, dim = !unlocked), 46.dp)
                                if (!unlocked) PixelArtIcon(lockArt, 20.dp, Modifier.align(Alignment.BottomEnd))
                            }
                            Text8(s.name, size = 9.sp, color = if (unlocked) c else colors.textMuted, align = TextAlign.Center, maxLines = 2)
                            Text8("${s.manaCost} MP", size = 8.sp, color = colors.textMuted)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================================
// RELICS PAGE — museum shelves
// =============================================================================================

@Composable
private fun RelicsPage(state: GameState, open: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val catalog = CollectibleEngine.catalog
    val found = p.discoveredCollectibleIds.size

    Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text8("THE RELIC HALL", size = 15.sp, color = GoldTrim, weight = FontWeight.ExtraBold, letterSpacing = 3.sp)
        Text8("$found of ${catalog.size} enshrined", size = 10.sp, color = colors.textMuted)
        val passives = CollectibleBonusType.entries.mapNotNull { t ->
            CollectibleEngine.totalBonus(state, t).takeIf { it > 0 }?.let { "+$it% ${t.displayName}" }
        }
        if (passives.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text8(passives.joinToString("  ·  "), size = 10.sp, color = colors.vitalityEmerald, align = TextAlign.Center)
        }
    }

    catalog.chunked(3).forEach { row ->
        Box(Modifier.fillMaxWidth().height(118.dp)) {
            // shelf plank
            Canvas(Modifier.matchParentSize()) {
                val u = floor(3.dp.toPx())
                val y = size.height - 22.dp.toPx()
                drawRect(InkDark, Offset(0f, y), Size(size.width, 4 * u))
                drawRect(Color(0xFF6B4A2C), Offset(0f, y), Size(size.width, 3 * u))
                drawRect(Color(0xFF8A6238), Offset(0f, y), Size(size.width, u))
                drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, y + 4 * u), Size(size.width, 3 * u))
                listOf(0.06f, 0.94f).forEach { fx -> drawRect(Color(0xFF4A3220), Offset(size.width * fx - u, y + 4 * u), Size(2 * u, 4 * u)) }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 22.dp).align(Alignment.BottomCenter), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                row.forEach { col ->
                    val isFound = col.id in p.discoveredCollectibleIds
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(96.dp).clickable { open(Inspect.Relic(col.id)) },
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                            if (isFound) {
                                Canvas(Modifier.matchParentSize()) { drawCircle(Brush.radialGradient(listOf(GoldTrim.copy(alpha = 0.4f), Color.Transparent))) }
                                Text8(col.icon, size = 30.sp)
                            } else {
                                Text8("?", size = 30.sp, color = colors.pixelBorderBright, weight = FontWeight.ExtraBold)
                            }
                        }
                        // little pedestal
                        Box(Modifier.size(width = 44.dp, height = 10.dp).pixelFrame(fill = Color(0xFF2B2E3A), edge = if (isFound) GoldTrim else colors.pixelBorder, style = FrameStyle.PLATE, unit = 1.dp))
                        Text8(if (isFound) col.name else "???", size = 8.sp, color = if (isFound) GoldTrim else colors.textMuted, maxLines = 1, align = TextAlign.Center)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.width(96.dp)) }
            }
        }
    }
}
