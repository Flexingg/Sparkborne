package com.sparkborne.app.ui.quest

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.ui.hero.BannerTabs
import com.sparkborne.app.ui.hero.FrameStyle
import com.sparkborne.app.ui.hero.GoldTrim
import com.sparkborne.app.ui.hero.HeroSprite
import com.sparkborne.app.ui.hero.InkDark
import com.sparkborne.app.ui.hero.ItemSocket
import com.sparkborne.app.ui.hero.PixelArtIcon
import com.sparkborne.app.ui.hero.PixelButton
import com.sparkborne.app.ui.hero.RuneHeader
import com.sparkborne.app.ui.hero.SegmentBar
import com.sparkborne.app.ui.hero.SkillMedallion
import com.sparkborne.app.ui.hero.StatLine
import com.sparkborne.app.ui.hero.Text8
import com.sparkborne.app.ui.hero.chamfer
import com.sparkborne.app.ui.hero.chestArt
import com.sparkborne.app.ui.hero.coinArt
import com.sparkborne.app.ui.hero.heroLookOf
import com.sparkborne.app.ui.hero.ingotArt
import com.sparkborne.app.ui.hero.mixWith
import com.sparkborne.app.ui.hero.pixelFrame
import com.sparkborne.app.ui.hero.pixelRing
import com.sparkborne.app.ui.hero.shardArt
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.catalog.WorldCatalog
import com.sparkborne.game.engine.ActionBankManager
import com.sparkborne.game.engine.CollectibleEngine
import com.sparkborne.game.engine.CombatAction
import com.sparkborne.game.engine.CraftingEngine
import com.sparkborne.game.engine.SkillEngine
import com.sparkborne.game.engine.ToolbeltEngine
import com.sparkborne.game.model.ActionBankType
import com.sparkborne.game.model.CollectibleBonusType
import com.sparkborne.game.model.ExpeditionContract
import com.sparkborne.game.model.ExpeditionDifficulty
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.RegionElement
import com.sparkborne.game.model.SkillType
import com.sparkborne.game.model.TownActivity
import com.sparkborne.game.model.WorldLocation
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// =============================================================================================
// Wiring
// =============================================================================================

/** Every mutation the Activity tab can trigger. */
class QuestActions(
    val startActivity: (String) -> Unit = {},
    val stopActivity: () -> Unit = {},
    val cancelTravel: () -> Unit = {},
    val spendOnTravel: () -> Unit = {},
    val spendOnActivity: (Int?) -> Unit = {},
    val startContract: (String) -> Unit = {},
    val enterNode: () -> Unit = {},
    val combat: (CombatAction) -> Unit = {},
    val abandonDelve: () -> Unit = {},
    val clearFinishedDelve: () -> Unit = {},
    val spendOnDelve: () -> Unit = {},
    val openMap: () -> Unit = {},
)

/** Bitmap sprites supplied by the platform layer (companion & enemies). */
class QuestSprites(
    val companion: (@Composable (Dp) -> Unit)? = null,
    val enemy: @Composable (isBoss: Boolean, size: Dp) -> Unit = { _, s -> Box(Modifier.size(s)) },
)

fun elementColor(e: RegionElement): Color = when (e) {
    RegionElement.FIRE -> Color(0xFFFF7A3D)
    RegionElement.PLANT -> Color(0xFF5FD068)
    RegionElement.WATER -> Color(0xFF3FC6E8)
    RegionElement.POISON -> Color(0xFFA5E04A)
    RegionElement.LIGHTNING -> Color(0xFFFFD54A)
    RegionElement.VOID -> Color(0xFFB57CFF)
}

fun bankColor(bank: ActionBankType): Color = when (bank) {
    ActionBankType.STEPS -> Color(0xFF5FD068)
    ActionBankType.PUSH_VOLUME -> Color(0xFFF5B041)
    ActionBankType.PULL_LEGS_VOLUME -> Color(0xFF4FD1C5)
    ActionBankType.HYDRATION_ML -> Color(0xFF3FC6E8)
    ActionBankType.CALORIES_BURNT -> Color(0xFFEF5D50)
}

fun compact(v: Int): String = when {
    v >= 1_000_000 -> "%.1fM".format(v / 1_000_000.0)
    v >= 10_000 -> "%.0fk".format(v / 1_000.0)
    v >= 1_000 -> "%.1fk".format(v / 1_000.0)
    else -> v.toString()
}

private enum class HubMode(val label: String, val glyph: String) { TASKS("Town Tasks", "⚒"), DELVES("Delves", "💀") }

private sealed interface HubWindow {
    data object Banks : HubWindow
    data class Task(val id: String) : HubWindow
    data class Delve(val id: String) : HubWindow
}

// =============================================================================================
// Hub
// =============================================================================================

/**
 * The Activity tab as a game screen: a living town vista with your hero, action-bank reservoirs,
 * the current quest, a job board of town tasks and wanted posters for delves.
 * While a delve is underway the tab becomes the [DelveScreen].
 */
@Composable
fun QuestHubScreen(
    state: GameState,
    errorMessage: String?,
    actions: QuestActions,
    sprites: QuestSprites = QuestSprites(),
    previewWindow: String? = null,
) {
    if (state.activeExpedition != null) {
        DelveScreen(state, errorMessage, actions, sprites)
        return
    }
    val colors = RpgTheme.colors
    val town = WorldCatalog.locationById(state.currentLocationId)
    val contracts = remember(town.id) { town.contractIds.mapNotNull(GameCatalog::contractById) }
    var mode by remember { mutableStateOf(HubMode.TASKS) }
    var window by remember {
        mutableStateOf(
            when {
                previewWindow == null -> null
                previewWindow == "banks" -> HubWindow.Banks
                previewWindow.startsWith("exp_") -> HubWindow.Delve(previewWindow)
                else -> HubWindow.Task(previewWindow)
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            errorMessage?.let { ErrorScroll(it) }
            TownVista(state, town, actions.openMap)
            Reservoirs(state) { window = HubWindow.Banks }
            QuestScroll(state, actions) { window = HubWindow.Task(it) }
            BannerTabs(HubMode.entries, mode, { "${it.label} ${if (it == HubMode.TASKS) town.activities.size else contracts.size}" }, { it.glyph }) { mode = it }
            when (mode) {
                HubMode.TASKS -> JobBoard(state, town.activities) { window = HubWindow.Task(it) }
                HubMode.DELVES -> if (contracts.isEmpty()) {
                    Text8("No delves are anchored to ${town.name}. Open the map and travel to a frontier stronghold.", size = 11.sp, color = colors.textMuted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
                } else {
                    contracts.forEach { c -> WantedPoster(c, c.id in state.completedContractIds) { window = HubWindow.Delve(c.id) } }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        when (val w = window) {
            null -> Unit
            HubWindow.Banks -> PixelWindow(colors.accentSecondary, onClose = { window = null }) { BanksWindow(state) }
            is HubWindow.Task -> WorldCatalog.activityById(w.id)?.let { act ->
                PixelWindow(colors.vitalityEmerald, onClose = { window = null }) {
                    TaskWindow(state, act, actions) { window = null }
                }
            }
            is HubWindow.Delve -> GameCatalog.contractById(w.id)?.let { c ->
                PixelWindow(colors.crimsonHp, onClose = { window = null }) {
                    DelveBriefing(state, c, sprites) {
                        actions.startContract(c.id)
                        window = null
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorScroll(text: String) {
    val colors = RpgTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF2B1417), edge = colors.crimsonHp, style = FrameStyle.PLATE, unit = 2.dp)
            .padding(10.dp),
    ) { Text8("⚠ $text", size = 11.sp, color = colors.crimsonHp) }
}

// =============================================================================================
// Town vista — a procedural town scene with your hero in it
// =============================================================================================

@Composable
private fun TownVista(state: GameState, town: WorldLocation, onOpenMap: () -> Unit) {
    val colors = RpgTheme.colors
    val ec = elementColor(town.element)
    val tr = rememberInfiniteTransition(label = "vista")
    val t by tr.animateFloat(0f, 60f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "vista_t")
    val traveling = state.activeTravel != null
    val working = state.activeActivity != null
    val skill = state.activeActivity?.let { WorldCatalog.activityById(it.activityId) }?.let { SkillType.fromTag(it.skillTag) }

    Box(Modifier.fillMaxWidth().height(210.dp)) {
        Canvas(Modifier.matchParentSize()) { drawTownScene(town, t, traveling, ec) }

        // hero in the scene
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) {
            Box(contentAlignment = Alignment.TopCenter) {
                HeroSprite(heroLookOf(state.player), if (traveling) 118.dp else 112.dp)
                if (working && skill != null) WorkAura(skill.icon, t)
                if (traveling) Text8("🧭", size = 16.sp, modifier = Modifier.offset(x = 34.dp, y = 6.dp))
            }
        }

        // town plaque
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .pixelFrame(fill = InkDark.copy(alpha = 0.85f), edge = ec.mixWith(colors.pixelBorder, 0.4f), style = FrameStyle.PLATE, unit = 2.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text8("${town.element.displayName.uppercase()} REGION", size = 8.sp, color = ec, letterSpacing = 1.5.sp)
            Text8(town.name.uppercase(), size = 13.sp, color = Color.White, weight = FontWeight.ExtraBold, maxLines = 1)
            Text8(if (traveling) "on the road…" else town.subtitle, size = 8.sp, color = colors.textMuted, maxLines = 1)
        }
        PixelButton("🗺 MAP", colors.accentSecondary, small = true, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp), onClick = onOpenMap)
    }
}

@Composable
private fun WorkAura(icon: String, t: Float) {
    val ph = (t * 0.8f) % 1f
    Box(Modifier.offset(y = (-8 - ph * 14).dp).alpha(1f - ph * 0.6f)) { Text8(icon, size = 18.sp) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTownScene(town: WorldLocation, t: Float, traveling: Boolean, ec: Color) {
    val u = floor(3.dp.toPx())
    val w = size.width
    val h = size.height
    val skyTop: Color
    val skyBot: Color
    when (town.element) {
        RegionElement.FIRE -> { skyTop = Color(0xFF1A0C10); skyBot = Color(0xFF5A2418) }
        RegionElement.PLANT -> { skyTop = Color(0xFF0C1A16); skyBot = Color(0xFF2E5A3A) }
        RegionElement.WATER -> { skyTop = Color(0xFF0A1424); skyBot = Color(0xFF24506E) }
        RegionElement.POISON -> { skyTop = Color(0xFF12160C); skyBot = Color(0xFF3E4A1E) }
        RegionElement.LIGHTNING -> { skyTop = Color(0xFF0E1024); skyBot = Color(0xFF3A3A6E) }
        RegionElement.VOID -> { skyTop = Color(0xFF0E0A1A); skyBot = Color(0xFF3A2058) }
    }
    chamfer(0f, 0f, w, h, u, InkDark)
    // banded sky
    val bands = 7
    for (i in 0 until bands) {
        val c = skyTop.mixWith(skyBot, i / (bands - 1f))
        drawRect(c, Offset(u, u + i * (h * 0.7f) / bands), Size(w - 2 * u, h * 0.7f / bands + 1))
    }
    // stars / sun
    for (i in 0 until 26) {
        val sx = ((i * 97 + 13) % 100) / 100f * w
        val sy = ((i * 53 + 7) % 100) / 100f * h * 0.4f + 2 * u
        val tw = 0.5f + 0.5f * sin(t * (1.5f + i % 4) + i)
        drawRect(Color.White.copy(alpha = 0.25f + 0.55f * tw), Offset(floor(sx / u) * u, floor(sy / u) * u), Size(u, u))
    }
    // far hills (parallax when travelling)
    val scroll = if (traveling) t * 18f else t * 1.5f
    for (layer in 0..1) {
        val base = h * (0.62f + layer * 0.08f)
        val col = skyBot.mixWith(Color.Black, 0.35f + layer * 0.25f)
        val speed = if (layer == 0) 0.3f else 0.6f
        var x = -((scroll * speed) % (6 * u))
        while (x < w) {
            val xx = x + (scroll * speed)
            val hh = (10 + 7 * sin(xx * 0.012f + layer) + 4 * sin(xx * 0.031f)) * u * (1 - layer * 0.35f)
            drawRect(col, Offset(x, base - hh), Size(6 * u + 1, hh + h * 0.4f))
            x += 6 * u
        }
    }
    // town silhouette (hidden while travelling)
    if (!traveling) {
        val ground = h * 0.78f
        val roof = skyBot.mixWith(Color.Black, 0.7f)
        val houses = listOf(0.06f to 22, 0.17f to 30, 0.29f to 18, 0.71f to 26, 0.82f to 34, 0.93f to 20)
        houses.forEachIndexed { i, (fx, hu) ->
            val hx = w * fx
            val hw = (10 + (i % 3) * 3) * u
            val hy = ground - hu * u * 0.9f
            drawRect(roof, Offset(hx - hw / 2, hy), Size(hw, ground - hy))
            val p = Path().apply { moveTo(hx - hw / 2 - u, hy); lineTo(hx, hy - 6 * u); lineTo(hx + hw / 2 + u, hy); close() }
            drawPath(p, roof.mixWith(ec, 0.12f))
            val lit = sin(t * 0.7f + i * 1.7f) > -0.6f
            drawRect(if (lit) Color(0xFFFFC46B) else roof.mixWith(Color.White, 0.08f), Offset(hx - u, hy + 4 * u), Size(2 * u, 2 * u))
        }
        // tower with banner
        val tx = w * 0.5f
        drawRect(roof.mixWith(Color.Black, 0.2f), Offset(tx - 6 * u, ground - 40 * u), Size(12 * u, 40 * u))
        for (k in 0 until 4) drawRect(roof.mixWith(Color.Black, 0.2f), Offset(tx - 6 * u + k * 3.5f * u, ground - 43 * u), Size(2 * u, 3 * u))
        drawRect(Color(0xFFFFC46B).copy(alpha = 0.9f), Offset(tx - u, ground - 30 * u), Size(2 * u, 3 * u))
        drawRect(Color(0xFF6B5030), Offset(tx - u / 2, ground - 54 * u), Size(u, 11 * u))
        for (c in 0 until 6) {
            val wave = (sin(t * 6f - c * 0.8f) * u * (c / 5f)).roundToInt().toFloat()
            drawRect(ec, Offset(tx + u / 2 + c * u, ground - 54 * u + wave), Size(u, 4 * u))
        }
    }
    // ground + road
    val gy = h * 0.8f
    drawRect(skyBot.mixWith(Color.Black, 0.75f), Offset(u, gy), Size(w - 2 * u, h - gy - u))
    drawRect(Color(0xFF3A2E24), Offset(u, gy + 6 * u), Size(w - 2 * u, 5 * u))
    var dx = -((scroll * 2f) % (8 * u))
    while (dx < w) {
        drawRect(Color(0xFF5A4634), Offset(dx, gy + 8 * u), Size(4 * u, u))
        dx += 8 * u
    }
    // regional weather
    for (i in 0 until 18) {
        val seed = i * 37
        val ph = (t * (0.15f + (i % 5) * 0.04f) + (seed % 100) / 100f) % 1f
        val px0 = ((seed * 13) % 100) / 100f * w
        val p: Offset
        val c: Color
        when (town.element) {
            RegionElement.FIRE -> { p = Offset(px0 + sin(ph * 6f + i) * 8 * u, gy - ph * h * 0.75f); c = Color(0xFFFFB04A).copy(alpha = 1f - ph) }
            RegionElement.WATER -> { p = Offset(px0 - ph * 20 * u, ph * gy); c = Color(0xFF9EE0F5).copy(alpha = 0.7f) }
            RegionElement.PLANT -> { p = Offset(px0 + sin(ph * 9f + i) * 10 * u, ph * gy); c = Color(0xFF8BE38A).copy(alpha = 0.85f) }
            RegionElement.POISON -> { p = Offset(px0 + sin(ph * 4f) * 4 * u, gy - ph * h * 0.5f); c = Color(0xFFA5E04A).copy(alpha = 0.6f * (1f - ph)) }
            RegionElement.LIGHTNING -> { p = Offset(px0, ph * gy); c = Color(0xFFDDE6FF).copy(alpha = 0.35f) }
            RegionElement.VOID -> { p = Offset(px0 + cos(ph * 6.28f + i) * 12 * u, h * 0.3f + sin(ph * 6.28f + i) * 20 * u); c = Color(0xFFB57CFF).copy(alpha = 0.7f) }
        }
        drawRect(c, Offset(floor(p.x / u) * u, floor(p.y / u) * u), Size(u, if (town.element == RegionElement.WATER) 3 * u else u))
    }
    if (town.element == RegionElement.LIGHTNING && (t % 7f) < 0.12f) drawRect(Color.White.copy(alpha = 0.25f), Offset.Zero, size)
}

// =============================================================================================
// Action-bank reservoirs
// =============================================================================================

@Composable
private fun Reservoirs(state: GameState, onOpen: () -> Unit) {
    val colors = RpgTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF15121A), edge = Color(0xFF6B4A2C), rivet = GoldTrim)
            .clickable(onClick = onOpen)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ActionBankType.entries.forEach { bank ->
            val bal = ActionBankManager.balanceOf(state.player, bank)
            val cap = ActionBankManager.capacityOf(state.player, bank).coerceAtLeast(1)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Vial(bal.toFloat() / cap, bankColor(bank), Modifier.size(width = 34.dp, height = 46.dp))
                    com.sparkborne.app.ui.hero.AlertPip(if (bal >= cap) 1 else 0, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp), label = "FULL")
                }
                Text8("${bank.icon}${compact(bal)}", size = 9.sp, color = if (bal > 0) colors.textPrimary else colors.textMuted, maxLines = 1)
            }
        }
    }
}

@Composable
fun Vial(fill: Float, color: Color, modifier: Modifier) {
    val tr = rememberInfiniteTransition(label = "vial")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "vial_t")
    Canvas(modifier) {
        val u = floor(2.dp.toPx())
        val w = size.width
        val h = size.height
        val cx = w / 2
        val top = 5 * u
        chamfer(cx - 3 * u, 0f, cx + 3 * u, top + u, u, InkDark)
        drawRect(Color(0xFF8A5A34), Offset(cx - 2 * u, u), Size(4 * u, 2 * u))
        chamfer(cx - w / 2 + u, top, cx + w / 2 - u, h, u, InkDark)
        chamfer(cx - w / 2 + 2 * u, top + u, cx + w / 2 - 2 * u, h - u, u, Color(0xFF2A3040))
        val innerH = h - top - 3 * u
        val lv = fill.coerceIn(0f, 1f)
        val ly = h - 2 * u - innerH * lv
        if (lv > 0f) {
            drawRect(color.mixWith(Color.Black, 0.15f), Offset(cx - w / 2 + 3 * u, ly), Size(w - 6 * u, h - 2 * u - ly))
            val wave = sin(t * 6.283f) * u * 0.5f
            drawRect(color.mixWith(Color.White, 0.35f), Offset(cx - w / 2 + 3 * u, ly + wave), Size(w - 6 * u, u))
            val by = h - 3 * u - ((t * 1.3f) % 1f) * (h - 3 * u - ly)
            drawRect(Color.White.copy(alpha = 0.6f), Offset(cx, floor(by / u) * u), Size(u, u))
            if (lv >= 0.999f) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)), radius = w * 0.7f)
        }
        drawRect(Color.White.copy(alpha = 0.15f), Offset(cx - w / 2 + 3 * u, top + 3 * u), Size(u, innerH * 0.5f))
    }
}

@Composable
private fun BanksWindow(state: GameState) {
    val colors = RpgTheme.colors
    Text8("ACTION RESERVOIRS", size = 16.sp, color = GoldTrim, weight = FontWeight.ExtraBold, letterSpacing = 2.sp)
    Text8("Your real-world effort, bottled. Each reservoir fuels different deeds.", size = 10.sp, color = colors.textMuted, align = TextAlign.Center, italic = true, weight = FontWeight.Normal)
    ActionBankType.entries.forEach { bank ->
        val bal = ActionBankManager.balanceOf(state.player, bank)
        val cap = ActionBankManager.capacityOf(state.player, bank).coerceAtLeast(1)
        val c = bankColor(bank)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Vial(bal.toFloat() / cap, c, Modifier.size(width = 40.dp, height = 56.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text8("${bank.icon} ${bank.displayName.uppercase()}", size = 11.sp, color = c, weight = FontWeight.ExtraBold)
                SegmentBar(bal.toFloat() / cap, c, Modifier.fillMaxWidth(), segments = 16, height = 12.dp, label = "%,d / %,d %s".format(bal, cap, bank.unit))
                Text8("Fuels: ${bank.fuelsDescription}", size = 9.sp, color = colors.textMuted)
            }
        }
    }
}

// =============================================================================================
// The current quest
// =============================================================================================

@Composable
private fun QuestScroll(state: GameState, actions: QuestActions, onInspect: (String) -> Unit) {
    val colors = RpgTheme.colors
    val travel = state.activeTravel
    val active = state.activeActivity
    val def = active?.let { WorldCatalog.activityById(it.activityId) }
    Box(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF2B2620), edge = Color(0xFF8A6238), rivet = GoldTrim)
            .padding(14.dp),
    ) {
        when {
            travel != null -> {
                val from = WorldCatalog.locationById(travel.fromLocationId)
                val dest = WorldCatalog.locationById(travel.toLocationId)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text8("🧭 ON THE ROAD", size = 12.sp, color = GoldTrim, weight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                        Text8("✕ turn back", size = 10.sp, color = colors.textMuted, modifier = Modifier.clickable(onClick = actions.cancelTravel).padding(4.dp))
                    }
                    RoadMap(from, dest, travel.progressFraction)
                    Text8("%,d / %,d steps".format(travel.stepsWalked, travel.stepsRequired), size = 11.sp, color = colors.textPrimary, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (state.player.savedSteps > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            PixelButton("🥾 SPEND BANKED STEPS", colors.vitalityEmerald, onClick = actions.spendOnTravel)
                        }
                    }
                }
            }
            active != null && def != null -> {
                val skill = SkillType.fromTag(def.skillTag)
                val (bank, cost) = ActionBankManager.costPerCycle(state, def)
                val avail = ActionBankManager.balanceOf(state.player, bank)
                val frac = (active.stepsProgress.toFloat() / cost.coerceAtLeast(1)).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(78.dp).clickable { onInspect(def.id) }, contentAlignment = Alignment.Center) {
                            WorkRing(frac, bankColor(bank), skill.icon)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text8("NOW WORKING", size = 9.sp, color = colors.vitalityEmerald, letterSpacing = 2.sp)
                            Text8(def.name.uppercase(), size = 13.sp, color = Color.White, weight = FontWeight.ExtraBold, maxLines = 2)
                            Text8("${skill.displayName} Lv.${state.player.skillLevel(skill)} · cycle #${active.completionsCount + 1}", size = 10.sp, color = colors.textMuted)
                            Text8("%,d / %,d %s %s".format(active.stepsProgress, cost, bank.unit, bank.icon), size = 10.sp, color = bankColor(bank))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        if (avail > 0) {
                            PixelButton("SPEND 1×", colors.vitalityEmerald, small = true) { actions.spendOnActivity(1) }
                            PixelButton("SPEND ALL", GoldTrim, small = true) { actions.spendOnActivity(null) }
                        }
                        PixelButton("STOP", colors.pixelBorderBright, small = true, onClick = actions.stopActivity)
                    }
                    if (avail <= 0) Text8("${bank.icon} ${bank.displayName} reservoir is dry: sync or train to refill it.", size = 10.sp, color = colors.textMuted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Campfire(Modifier.size(width = 64.dp, height = 60.dp))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text8("RESTING AT THE CAMPFIRE", size = 12.sp, color = GoldTrim, weight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Text8("Pick a job from the board or take a delve poster to begin.", size = 10.sp, color = colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun RoadMap(from: WorldLocation, dest: WorldLocation, progress: Float) {
    val colors = RpgTheme.colors
    val tr = rememberInfiniteTransition(label = "road")
    val step by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "road_step")
    Box(Modifier.fillMaxWidth().height(70.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val u = floor(2.dp.toPx())
            val y = size.height * 0.55f
            val x0 = 24.dp.toPx()
            val x1 = size.width - 24.dp.toPx()
            var x = x0
            while (x < x1) {
                val wob = sin(x * 0.02f) * 6 * u
                val walked = (x - x0) / (x1 - x0) <= progress
                drawRect(if (walked) GoldTrim else Color(0xFF6B4A2C), Offset(x, y + wob), Size(3 * u, u * 1.5f))
                x += 5 * u
            }
            listOf(x0 to elementColor(from.element), x1 to elementColor(dest.element)).forEach { (px, c) ->
                drawCircle(InkDark, 9 * u, Offset(px, y + sin(px * 0.02f) * 6 * u))
                drawCircle(c, 7 * u, Offset(px, y + sin(px * 0.02f) * 6 * u))
            }
        }
        Text8(from.name.substringBefore(" ").uppercase(), size = 8.sp, color = colors.textMuted, modifier = Modifier.align(Alignment.BottomStart))
        Text8(dest.name.substringBefore(" ").uppercase(), size = 8.sp, color = Color.White, modifier = Modifier.align(Alignment.BottomEnd))
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.matchParentSize()) {
            val x = 24.dp + (maxWidth - 48.dp) * progress
            Text8("🥾", size = 16.sp, modifier = Modifier.offset(x = x - 10.dp, y = (6 + step * 3).dp))
        }
    }
}

@Composable
private fun WorkRing(progress: Float, color: Color, icon: String) {
    val tr = rememberInfiniteTransition(label = "workring")
    val spin by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "wr_spin")
    Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val u = floor(2.dp.toPx())
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
            drawCircle(InkDark, size.minDimension / 2 - 3 * u)
            pixelRing(progress, color, Color(0xFF232838), 4 * u)
            for (i in 0 until 3) {
                val a = Math.toRadians((spin + i * 120).toDouble())
                val r = size.minDimension / 2 - 2 * u
                drawRect(Color.White, Offset(center.x + (cos(a) * r).toFloat() - u, center.y + (sin(a) * r).toFloat() - u), Size(2 * u, 2 * u))
            }
        }
        Text8(icon, size = 26.sp)
    }
}

@Composable
fun Campfire(modifier: Modifier) {
    val tr = rememberInfiniteTransition(label = "fire")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "fire_t")
    Canvas(modifier) {
        val u = floor(3.dp.toPx())
        val cx = size.width / 2
        val base = size.height - 4 * u
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF9F43).copy(alpha = 0.35f), Color.Transparent), Offset(cx, base - 4 * u), size.width * 0.6f), size.width * 0.6f, Offset(cx, base - 4 * u))
        for (k in 0..6) {
            drawRect(Color(0xFF6B4A2C), Offset(cx - 7 * u + k * 2 * u, base + u - (k % 2) * u), Size(2 * u, 2 * u))
        }
        for (dx in -3..3) {
            val hh = ((6 - kotlin.math.abs(dx) * 1.5f) * (0.75f + 0.25f * sin(t * 6.283f * 2 + dx * 1.7f))).coerceAtLeast(1f)
            drawRect(Color(0xFFD6452A), Offset(cx + dx * u, base - hh * u), Size(u, hh * u))
            drawRect(Color(0xFFFF8A2A), Offset(cx + dx * u, base - hh * 0.7f * u), Size(u, hh * 0.7f * u))
            if (kotlin.math.abs(dx) <= 1) drawRect(Color(0xFFFFE07A), Offset(cx + dx * u, base - hh * 0.4f * u), Size(u, hh * 0.4f * u))
        }
    }
}

// =============================================================================================
// Job board (town tasks) & wanted posters (delves)
// =============================================================================================

@Composable
private fun JobBoard(state: GameState, activities: List<TownActivity>, onOpen: (String) -> Unit) {
    activities.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { act -> JobSign(state, act, Modifier.weight(1f)) { onOpen(act.id) } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun JobSign(state: GameState, act: TownActivity, modifier: Modifier, onClick: () -> Unit) {
    val colors = RpgTheme.colors
    val skill = SkillType.fromTag(act.skillTag)
    val lvl = state.player.skillLevel(skill)
    val (bank, cost) = ActionBankManager.costPerCycle(state, act)
    val ready = if (cost > 0) ActionBankManager.balanceOf(state.player, bank) / cost else 0
    val running = state.activeActivity?.activityId == act.id
    val sway = if (running) com.sparkborne.app.ui.hero.rememberBob(1.5f, 1600) else 0f
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        // chains
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val u = 2.dp.toPx()
            listOf(size.width * 0.25f, size.width * 0.75f).forEach { x ->
                var y = 0f
                while (y < size.height) { drawRect(Color(0xFF6B7280), Offset(x - u / 2, y), Size(u, u * 1.5f)); y += u * 2.5f }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .rotate(sway)
                .pixelFrame(fill = if (running) Color(0xFF4A3A22) else Color(0xFF3A2C1C), edge = if (running) colors.vitalityEmerald else Color(0xFF8A6238), rivet = if (running) colors.vitalityEmerald else GoldTrim)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text8(skill.icon, size = 22.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .pixelFrame(fill = if (running) colors.vitalityEmerald else InkDark, edge = if (running) colors.vitalityEmerald else Color(0xFF6B4A2C), style = FrameStyle.PLATE, unit = 1.dp)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) { Text8(if (running) "WORKING" else "LV.$lvl", size = 8.sp, color = if (running) InkDark else colors.textMuted, weight = FontWeight.ExtraBold) }
            }
            Text8(act.name.uppercase(), size = 11.sp, color = Color(0xFFF0DDB8), weight = FontWeight.ExtraBold, align = TextAlign.Center, maxLines = 2, letterSpacing = 0.5.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text8("${bank.icon}${compact(cost)}", size = 10.sp, color = bankColor(bank), modifier = Modifier.weight(1f))
                Text8(if (ready > 0) "×$ready" else "0×", size = 10.sp, color = if (ready > 0) colors.vitalityEmerald else colors.textMuted, weight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun skulls(d: ExpeditionDifficulty) = when (d) {
    ExpeditionDifficulty.SCOUT -> 1
    ExpeditionDifficulty.VANGUARD -> 2
    ExpeditionDifficulty.ABYSSAL -> 3
}

@Composable
fun WantedPoster(c: ExpeditionContract, cleared: Boolean, onClick: () -> Unit) {
    val colors = RpgTheme.colors
    val paper = Color(0xFFD9C49A)
    val ink = Color(0xFF3A2416)
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .pixelFrame(fill = paper, edge = Color(0xFF8A6238), style = FrameStyle.WINDOW, rivet = Color(0xFF6B7280))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text8("— WANTED —", size = 12.sp, color = ink, weight = FontWeight.ExtraBold, letterSpacing = 3.sp, italic = false)
            Text8(c.bossName.uppercase(), size = 16.sp, color = Color(0xFF7A1A14), weight = FontWeight.ExtraBold, align = TextAlign.Center)
            Text8(c.title, size = 10.sp, color = ink, align = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i -> Text8("💀", size = 14.sp, modifier = Modifier.alpha(if (i < skulls(c.difficulty)) 1f else 0.2f)) }
            }
            Text8(
                "${c.difficulty.displayName.uppercase()} · ${c.totalNodes} NODES · ${c.durationDays}D" + if (c.grantsEggOnClear) " · 🥚" else "",
                size = 9.sp,
                color = ink,
                letterSpacing = 1.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text8("REWARD", size = 9.sp, color = ink, weight = FontWeight.ExtraBold)
                RewardBit(coinArt, c.baseRewardGold, ink)
                RewardBit(ingotArt, c.baseRewardIron, ink)
                RewardBit(shardArt, c.baseRewardShards, ink)
            }
        }
        if (cleared) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 10.dp)
                    .rotate(-14f)
                    .pixelFrame(fill = Color.Transparent, edge = colors.crimsonHp, style = FrameStyle.PLATE, unit = 2.dp)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) { Text8("CLEARED", size = 12.sp, color = colors.crimsonHp, weight = FontWeight.ExtraBold, letterSpacing = 2.sp) }
        }
    }
}

@Composable
private fun RewardBit(art: com.sparkborne.app.ui.hero.PixelArt, n: Int, ink: Color) {
    if (n <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        PixelArtIcon(art, 14.dp)
        Spacer(Modifier.width(2.dp))
        Text8("$n", size = 11.sp, color = ink, weight = FontWeight.ExtraBold)
    }
}

// =============================================================================================
// Windows
// =============================================================================================

/** A pixel-framed overlay window that dims the screen; tap outside to close. */
@Composable
fun PixelWindow(accent: Color, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = RpgTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE0040508))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .pixelFrame(fill = colors.panelSurface, edge = accent.mixWith(colors.pixelBorderBright, 0.35f), rivet = accent),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
            CloseX(Modifier.align(Alignment.TopEnd), onClose)
        }
    }
}

@Composable
private fun BoxScope.CloseX(modifier: Modifier, onClose: () -> Unit) {
    val colors = RpgTheme.colors
    Box(
        modifier
            .padding(8.dp)
            .size(30.dp)
            .pixelFrame(fill = colors.obsidianBg, edge = colors.pixelBorderBright, style = FrameStyle.PLATE, unit = 2.dp)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) { Text8("✕", size = 13.sp, color = colors.textMuted) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.TaskWindow(state: GameState, act: TownActivity, actions: QuestActions, close: () -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val skill = SkillType.fromTag(act.skillTag)
    val xp = p.skillXp[skill] ?: 0
    val lvl = SkillEngine.levelForXp(xp)
    val (cur, need) = SkillEngine.xpToNextLevel(xp)
    val (bank, cost) = ActionBankManager.costPerCycle(state, act)
    val avail = ActionBankManager.balanceOf(p, bank)
    val cap = ActionBankManager.capacityOf(p, bank).coerceAtLeast(1)
    val running = state.activeActivity?.activityId == act.id
    val mat = CraftingEngine.materialForSkill(skill)
    val eff = SkillEngine.workEfficiencyPercent(lvl) + CollectibleEngine.totalBonus(state, CollectibleBonusType.WORK_EFFICIENCY_PERCENT) +
        ToolbeltEngine.workEfficiencyBonus(state, skill) + (if (p.activeStepBoostCharges > 0) CraftingEngine.STEP_BOOST_EFFICIENCY_PERCENT else 0) +
        com.sparkborne.game.engine.TalentEngine.stat(p, com.sparkborne.game.model.TalentStat.WORK_EFF_PCT)
    val done = p.activityCompletions[act.id] ?: 0
    val toChest = done % SkillEngine.COMPLETIONS_PER_CHEST

    SkillMedallion(skill, lvl, if (lvl >= 99) 1f else cur.toFloat() / need, 104.dp, colors.vitalityEmerald)
    Text8(act.name.uppercase(), size = 16.sp, color = Color.White, weight = FontWeight.ExtraBold, align = TextAlign.Center)
    Text8("${skill.displayName} · Lv.$lvl", size = 10.sp, color = colors.textMuted, letterSpacing = 1.5.sp)
    Text8("“${act.description}”", size = 11.sp, color = colors.textMuted, align = TextAlign.Center, italic = true, weight = FontWeight.Normal)

    RuneHeader("COST PER CYCLE", color = bankColor(bank))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Vial(avail.toFloat() / cap, bankColor(bank), Modifier.size(width = 40.dp, height = 56.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text8("%,d %s %s per cycle".format(cost, bank.unit, bank.icon), size = 12.sp, color = bankColor(bank), weight = FontWeight.ExtraBold)
            SegmentBar(avail.toFloat() / cap, bankColor(bank), Modifier.fillMaxWidth(), segments = 16, height = 12.dp, label = "%,d / %,d".format(avail, cap))
            Text8("Work efficiency +$eff% · ${if (cost > 0) avail / cost else 0} cycle(s) ready", size = 9.sp, color = colors.textMuted)
        }
    }

    RuneHeader("SPOILS PER CYCLE", color = GoldTrim)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (act.rewardGold > 0) Spoil({ PixelArtIcon(coinArt, 24.dp) }, "+${act.rewardGold}", GoldTrim)
        if (act.rewardIron > 0) Spoil({ PixelArtIcon(ingotArt, 24.dp) }, "+${act.rewardIron}", Color(0xFFC9D2DE))
        if (act.rewardShards > 0) Spoil({ PixelArtIcon(shardArt, 24.dp) }, "+${act.rewardShards}", Color(0xFFFF9A5E))
        Spoil({ Text8(mat.icon, size = 20.sp) }, "+1", colors.vitalityEmerald)
        Spoil({ Text8("⭐", size = 18.sp) }, "+${act.rewardXp}XP", colors.accentSecondary)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PixelArtIcon(chestArt, 22.dp)
        for (i in 0 until SkillEngine.COMPLETIONS_PER_CHEST) {
            val lit = i < toChest
            Box(Modifier.size(14.dp).pixelFrame(fill = if (lit) GoldTrim else colors.obsidianBg, edge = if (lit) GoldTrim else colors.pixelBorder, style = FrameStyle.SOCKET, unit = 1.dp))
        }
        Text8("$done done", size = 10.sp, color = colors.textMuted)
    }

    if (running) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (avail > 0) {
                PixelButton("SPEND 1×", colors.vitalityEmerald, small = true) { actions.spendOnActivity(1) }
                PixelButton("SPEND ALL", GoldTrim, small = true) { actions.spendOnActivity(null) }
            }
            PixelButton("STOP", colors.crimsonHp, small = true) {
                actions.stopActivity()
                close()
            }
        }
    } else {
        PixelButton("▶ TAKE THE JOB", colors.vitalityEmerald) {
            actions.startActivity(act.id)
            close()
        }
    }
}

@Composable
private fun Spoil(icon: @Composable () -> Unit, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ItemSocket(48.dp, glow = color) { icon() }
        Text8(label, size = 10.sp, color = color, weight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ColumnScope.DelveBriefing(state: GameState, c: ExpeditionContract, sprites: QuestSprites, onBegin: () -> Unit) {
    val colors = RpgTheme.colors
    val burn = ActionBankManager.balanceOf(state.player, ActionBankType.CALORIES_BURNT)
    Text8("— BOUNTY —", size = 12.sp, color = colors.crimsonHp, letterSpacing = 3.sp, weight = FontWeight.ExtraBold)
    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) { drawCircle(Brush.radialGradient(listOf(colors.crimsonHp.copy(alpha = 0.35f), Color.Transparent))) }
        sprites.enemy(true, 120.dp)
    }
    Text8(c.bossName.uppercase(), size = 17.sp, color = colors.crimsonHp, weight = FontWeight.ExtraBold, align = TextAlign.Center)
    Text8(c.title, size = 11.sp, color = Color.White, align = TextAlign.Center)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(3) { i -> Text8("💀", size = 16.sp, modifier = Modifier.alpha(if (i < skulls(c.difficulty)) 1f else 0.2f)) } }
    Text8("“${c.description}”", size = 11.sp, color = colors.textMuted, align = TextAlign.Center, italic = true, weight = FontWeight.Normal)
    StatLine("❤", "Boss HP", "${c.bossHp}", colors.crimsonHp)
    StatLine("⚔", "Boss Attack", "${c.bossAttack}", colors.accentPrimary)
    StatLine("⛨", "Boss Defense", "${c.bossDefense}", colors.accentSecondary)
    StatLine("🗺", "Trail", "${c.totalNodes} nodes × ${compact(c.stepsPerNode)} steps", colors.textPrimary)
    StatLine("⏳", "Time Limit", "${c.durationDays} day(s)", colors.textPrimary)
    StatLine("🔥", "Fight Cost", "50 / 100 boss kcal", if (burn >= 50) colors.vitalityEmerald else colors.crimsonHp)
    Text8("You have %,d kcal of Energy Burn".format(burn), size = 10.sp, color = colors.textMuted)
    RuneHeader("BOUNTY", color = GoldTrim)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Spoil({ PixelArtIcon(coinArt, 24.dp) }, "${c.baseRewardGold}", GoldTrim)
        Spoil({ PixelArtIcon(ingotArt, 24.dp) }, "${c.baseRewardIron}", Color(0xFFC9D2DE))
        Spoil({ PixelArtIcon(shardArt, 24.dp) }, "${c.baseRewardShards}", Color(0xFFFF9A5E))
        if (c.grantsEggOnClear) Spoil({ Text8("🥚", size = 20.sp) }, "EGG", colors.vitalityEmerald)
    }
    Text8("Fall in battle and every unbanked spoil is lost.", size = 10.sp, color = colors.crimsonHp, align = TextAlign.Center)
    PixelButton("⚔ ACCEPT THE BOUNTY", colors.crimsonHp, onClick = onBegin)
}
