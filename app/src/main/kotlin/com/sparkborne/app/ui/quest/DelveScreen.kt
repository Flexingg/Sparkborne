package com.sparkborne.app.ui.quest

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.ui.hero.FrameStyle
import com.sparkborne.app.ui.hero.GemSocket
import com.sparkborne.app.ui.hero.GoldTrim
import com.sparkborne.app.ui.hero.HeroSprite
import com.sparkborne.app.ui.hero.InkDark
import com.sparkborne.app.ui.hero.ItemSocket
import com.sparkborne.app.ui.hero.PixelArtIcon
import com.sparkborne.app.ui.hero.PixelButton
import com.sparkborne.app.ui.hero.RuneHeader
import com.sparkborne.app.ui.hero.SegmentBar
import com.sparkborne.app.ui.hero.Text8
import com.sparkborne.app.ui.hero.chamfer
import com.sparkborne.app.ui.hero.coinArt
import com.sparkborne.app.ui.hero.heroLookOf
import com.sparkborne.app.ui.hero.ingotArt
import com.sparkborne.app.ui.hero.mixWith
import com.sparkborne.app.ui.hero.pixelFrame
import com.sparkborne.app.ui.hero.rememberBob
import com.sparkborne.app.ui.hero.shardArt
import com.sparkborne.app.ui.hero.spellOrbArt
import com.sparkborne.app.ui.hero.elementColor as spellColor
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.CombatAction
import com.sparkborne.game.model.ActiveCombatState
import com.sparkborne.game.model.ActiveExpedition
import com.sparkborne.game.model.DelveNode
import com.sparkborne.game.model.DelveNodeType
import com.sparkborne.game.model.ExpeditionStatus
import com.sparkborne.game.model.GameState
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

fun nodeGlyph(t: DelveNodeType): String = when (t) {
    DelveNodeType.COMBAT -> "⚔"
    DelveNodeType.FORGE_SHRINE -> "⚒"
    DelveNodeType.REST_CAMP -> "🔥"
    DelveNodeType.TREASURE -> "💰"
    DelveNodeType.BOSS -> "💀"
}

/**
 * The delve as a game screen: a winding trail map, an at-risk loot pouch, the current chamber,
 * and a torch-lit dungeon arena for combat with a pixel command deck.
 */
@Composable
fun DelveScreen(state: GameState, errorMessage: String?, actions: QuestActions, sprites: QuestSprites = QuestSprites()) {
    val colors = RpgTheme.colors
    val exp = state.activeExpedition
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(2.dp))
        errorMessage?.let { ErrorScroll(it) }
        if (exp == null) {
            RuneHeader("BOUNTY BOARD", color = colors.crimsonHp)
            GameCatalog.contracts.forEach { c -> WantedPoster(c, c.id in state.completedContractIds) { actions.startContract(c.id) } }
        } else {
            TrailMap(state, exp)
            when (exp.status) {
                ExpeditionStatus.COMPLETED, ExpeditionStatus.FAILED -> OutcomeBanner(exp, actions)
                ExpeditionStatus.ACTIVE -> {
                    RunVitals(exp)
                    val combat = exp.activeCombat
                    if (combat != null) {
                        CombatArena(state, combat, actions, sprites)
                    } else {
                        exp.currentNode?.let { ChamberCard(state, it, actions) }
                    }
                    AbandonRow(actions.abandonDelve)
                }
            }
            if (state.recentLog.isNotEmpty()) ChronicleScroll(state.recentLog.take(6))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// =============================================================================================
// Trail map
// =============================================================================================

@Composable
private fun TrailMap(state: GameState, exp: ActiveExpedition) {
    val colors = RpgTheme.colors
    val tr = rememberInfiniteTransition(label = "trail")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "trail_t")
    val n = exp.nodes.size.coerceAtLeast(1)
    Column(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF15121A), edge = colors.crimsonHp.mixWith(colors.pixelBorder, 0.55f), rivet = colors.crimsonHp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text8(exp.contract.title.uppercase(), size = 13.sp, color = Color.White, weight = FontWeight.ExtraBold, maxLines = 1)
                Text8("${exp.contract.difficulty.displayName} · due ${exp.deadlineDate}", size = 9.sp, color = colors.textMuted)
            }
            Text8(
                when (exp.status) {
                    ExpeditionStatus.ACTIVE -> "NODE ${exp.currentNodeIndex + 1}/$n"
                    ExpeditionStatus.COMPLETED -> "CLEARED"
                    ExpeditionStatus.FAILED -> "FALLEN"
                },
                size = 10.sp,
                color = when (exp.status) {
                    ExpeditionStatus.ACTIVE -> GoldTrim
                    ExpeditionStatus.COMPLETED -> colors.vitalityEmerald
                    ExpeditionStatus.FAILED -> colors.crimsonHp
                },
                weight = FontWeight.ExtraBold,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(118.dp)) {
            val w = maxWidth
            fun nx(i: Int) = w * (0.08f + 0.84f * i / (n - 1).coerceAtLeast(1))
            fun ny(i: Int) = if (i % 2 == 0) 78.dp else 38.dp
            Canvas(Modifier.matchParentSize()) {
                val u = floor(2.dp.toPx())
                for (i in 0 until n - 1) {
                    val a = Offset(nx(i).toPx(), ny(i).toPx())
                    val b = Offset(nx(i + 1).toPx(), ny(i + 1).toPx())
                    val steps = 14
                    for (k in 0..steps) {
                        val f = k / steps.toFloat()
                        val p = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
                        val walked = exp.nodes[i + 1].cleared || (i + 1 == exp.currentNodeIndex && f <= 1f && exp.nodes[i].cleared)
                        val dash = ((k + (t * 4).toInt()) % 3) != 0
                        if (dash) drawRect(if (walked) GoldTrim else Color(0xFF4A3A2C), Offset(floor(p.x / u) * u, floor(p.y / u) * u), Size(2 * u, 2 * u))
                    }
                }
            }
            exp.nodes.forEach { node ->
                val cur = node.index == exp.currentNodeIndex && exp.status == ExpeditionStatus.ACTIVE
                val c = when {
                    node.cleared -> colors.vitalityEmerald
                    cur -> GoldTrim
                    node.type == DelveNodeType.BOSS -> colors.crimsonHp
                    else -> colors.pixelBorderBright
                }
                val s = if (cur) 40.dp else 32.dp
                Box(Modifier.offset(x = nx(node.index) - s / 2, y = ny(node.index) - s / 2).size(s), contentAlignment = Alignment.Center) {
                    ItemSocket(s, glow = if (node.cleared || cur) c else null, selected = cur) {
                        Text8(if (node.cleared) "✓" else nodeGlyph(node.type), size = if (cur) 15.sp else 12.sp, color = if (node.cleared) c else Color.White)
                    }
                }
                if (cur) {
                    Box(Modifier.offset(x = nx(node.index) - 18.dp, y = ny(node.index) - 66.dp)) {
                        HeroSprite(heroLookOf(state.player), 44.dp, shadow = false)
                    }
                }
            }
        }
    }
}

// =============================================================================================
// Vitals, chamber, outcome
// =============================================================================================

@Composable
private fun RunVitals(exp: ActiveExpedition) {
    val colors = RpgTheme.colors
    val shake = if (exp.unbankedLoot.gold + exp.unbankedLoot.ironOre + exp.unbankedLoot.emberShards > 0) rememberBob(1f, 260) else 0f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text8("❤", size = 13.sp, color = colors.crimsonHp, modifier = Modifier.width(20.dp))
                SegmentBar(exp.runPlayerHp.toFloat() / exp.runPlayerMaxHp.coerceAtLeast(1), colors.crimsonHp, Modifier.weight(1f), segments = 16, height = 14.dp, label = "${exp.runPlayerHp}/${exp.runPlayerMaxHp}")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text8("✦", size = 13.sp, color = colors.arcaneMana, modifier = Modifier.width(20.dp))
                SegmentBar(exp.runPlayerMana.toFloat() / exp.runPlayerMaxMana.coerceAtLeast(1), colors.arcaneMana, Modifier.weight(1f), segments = 12, height = 14.dp, label = "${exp.runPlayerMana}/${exp.runPlayerMaxMana}")
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(x = shake.dp)) {
            Text8("AT RISK", size = 8.sp, color = colors.crimsonHp, letterSpacing = 1.5.sp, weight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ItemSocket(36.dp, glow = GoldTrim, qty = exp.unbankedLoot.gold) { PixelArtIcon(coinArt, 18.dp) }
                ItemSocket(36.dp, qty = exp.unbankedLoot.ironOre) { PixelArtIcon(ingotArt, 18.dp) }
                ItemSocket(36.dp, qty = exp.unbankedLoot.emberShards) { PixelArtIcon(shardArt, 18.dp) }
            }
        }
    }
    val req = exp.contract.requirement
    Text8(
        "Trail steps ${"%,d".format(exp.progress.totalStepsLogged)}/${"%,d".format(req.minTotalSteps)}" +
            if (req.minLiftingVolumeKg > 0) " · Lift ${exp.progress.totalLiftingVolumeKg.roundToInt()}/${req.minLiftingVolumeKg.roundToInt()}kg" else "",
        size = 9.sp,
        color = colors.textMuted,
        align = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChamberCard(state: GameState, node: DelveNode, actions: QuestActions) {
    val colors = RpgTheme.colors
    val glow = if (node.type == DelveNodeType.BOSS) colors.crimsonHp else GoldTrim
    val bob = rememberBob(3f, 1400)
    Column(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF1A1620), edge = glow.mixWith(colors.pixelBorder, 0.4f), rivet = glow)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(90.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) { drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.4f), Color.Transparent))) }
            Text8(nodeGlyph(node.type), size = 44.sp, modifier = Modifier.offset(y = bob.dp))
        }
        Text8(node.title.uppercase(), size = 14.sp, color = Color.White, weight = FontWeight.ExtraBold, align = TextAlign.Center)
        Text8(node.type.displayName, size = 10.sp, color = glow, letterSpacing = 2.sp)
        SegmentBar(node.stepsApplied.toFloat() / node.stepsRequired.coerceAtLeast(1), colors.vitalityEmerald, Modifier.fillMaxWidth(), segments = 18, height = 14.dp, label = "%,d / %,d STEPS".format(node.stepsApplied, node.stepsRequired))
        if (!node.isUnlocked && state.player.savedSteps > 0) {
            val toSpend = minOf(state.player.savedSteps, (node.stepsRequired - node.stepsApplied).coerceAtLeast(0))
            PixelButton("🥾 SPEND %,d BANKED STEPS".format(toSpend), colors.vitalityEmerald, small = true, onClick = actions.spendOnDelve)
        }
        PixelButton(
            if (node.isUnlocked) "ENTER THE ${node.type.displayName.uppercase()}" else "WALK ON TO UNLOCK",
            glow,
            enabled = node.isUnlocked,
            onClick = actions.enterNode,
        )
    }
}

@Composable
private fun OutcomeBanner(exp: ActiveExpedition, actions: QuestActions) {
    val colors = RpgTheme.colors
    val won = exp.status == ExpeditionStatus.COMPLETED
    val c = if (won) GoldTrim else colors.crimsonHp
    Column(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = if (won) Color(0xFF2B2410) else Color(0xFF2B1417), edge = c, rivet = c)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text8(if (won) "🏆" else "☠", size = 44.sp)
        Text8(if (won) "VICTORY" else "YOU HAVE FALLEN", size = 20.sp, color = c, weight = FontWeight.ExtraBold, letterSpacing = 3.sp)
        Text8(
            exp.failureReason ?: if (won) "The bounty is claimed and your spoils are banked." else "The wilds keep what you carried.",
            size = 11.sp,
            color = colors.textMuted,
            align = TextAlign.Center,
        )
        PixelButton("RETURN TO TOWN", c, onClick = actions.clearFinishedDelve)
    }
}

@Composable
private fun AbandonRow(onAbandon: () -> Unit) {
    val colors = RpgTheme.colors
    var armed by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (!armed) {
            Text8("⚑ abandon the run", size = 10.sp, color = colors.textMuted, modifier = Modifier.clickable { armed = true }.padding(6.dp))
        } else {
            Text8("Forfeit all unbanked spoils?", size = 10.sp, color = colors.crimsonHp)
            Spacer(Modifier.width(8.dp))
            PixelButton("FLEE", colors.crimsonHp, small = true) {
                armed = false
                onAbandon()
            }
            Spacer(Modifier.width(6.dp))
            PixelButton("STAY", colors.pixelBorderBright, small = true) { armed = false }
        }
    }
}

@Composable
private fun ChronicleScroll(lines: List<String>) {
    val colors = RpgTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF2B2620), edge = Color(0xFF8A6238), rivet = GoldTrim)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text8("CHRONICLE", size = 10.sp, color = GoldTrim, letterSpacing = 2.sp, weight = FontWeight.ExtraBold)
        lines.forEach { Text8("▸ $it", size = 10.sp, color = colors.textPrimary, weight = FontWeight.Normal) }
    }
}

// =============================================================================================
// Combat
// =============================================================================================

private fun damageFrom(line: String?): Int? =
    line?.let { Regex("for (\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }

@Composable
private fun CombatArena(state: GameState, combat: ActiveCombatState, actions: QuestActions, sprites: QuestSprites) {
    val colors = RpgTheme.colors
    val lunge = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    var lastDamage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(combat.turnNumber) {
        if (combat.turnNumber > 1) {
            lastDamage = damageFrom(combat.combatLog.dropLast(1).lastOrNull { it.contains("for ") && it.startsWith("Turn") })
            lunge.snapTo(22f); lunge.animateTo(0f, tween(180))
            flash.snapTo(0.6f); flash.animateTo(0f, tween(260))
            pop.snapTo(1f)
            shake.snapTo(12f); shake.animateTo(-8f, tween(50)); shake.animateTo(5f, tween(50)); shake.animateTo(0f, tween(50))
            pop.animateTo(0f, tween(700))
        }
    }
    val tr = rememberInfiniteTransition(label = "arena")
    val t by tr.animateFloat(0f, 10f, infiniteRepeatable(tween(10_000, easing = LinearEasing)), label = "arena_t")
    val enemy = combat.enemy

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text8("⚔ TURN ${combat.turnNumber}", size = 12.sp, color = GoldTrim, weight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            if (combat.playerShield > 0) Text8("⛨ ${combat.playerShield}", size = 12.sp, color = colors.accentSecondary, weight = FontWeight.ExtraBold)
        }
        Box(Modifier.fillMaxWidth().height(230.dp)) {
            Canvas(Modifier.matchParentSize()) { drawDungeon(t) }
            // enemy HP plate
            Column(
                Modifier.align(Alignment.TopEnd).padding(10.dp).width(150.dp)
                    .pixelFrame(fill = InkDark.copy(alpha = 0.85f), edge = colors.crimsonHp.mixWith(colors.pixelBorder, 0.4f), style = FrameStyle.PLATE, unit = 2.dp)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text8((if (enemy.isBoss) "💀 " else "") + enemy.name.uppercase(), size = 9.sp, color = colors.crimsonHp, weight = FontWeight.ExtraBold, maxLines = 1)
                SegmentBar(enemy.currentHp.toFloat() / enemy.maxHp.coerceAtLeast(1), colors.crimsonHp, Modifier.fillMaxWidth(), segments = 12, height = 10.dp, label = "${enemy.currentHp}/${enemy.maxHp}")
                Text8("ATK ${enemy.attack} · DEF ${enemy.defense}", size = 8.sp, color = colors.textMuted)
            }
            // hero + companion
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 22.dp).offset(x = lunge.value.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                HeroSprite(heroLookOf(state.player), 120.dp)
                sprites.companion?.let { c -> Box(Modifier.padding(bottom = 4.dp)) { c(52.dp) } }
            }
            // enemy
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp).offset(x = shake.value.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    sprites.enemy(enemy.isBoss, if (enemy.isBoss) 112.dp else 96.dp)
                    if (flash.value > 0.01f) {
                        Box(Modifier.size(if (enemy.isBoss) 112.dp else 96.dp).pixelFrame(fill = Color.White.copy(alpha = flash.value * 0.7f), edge = Color.Transparent, style = FrameStyle.PLATE, unit = 1.dp))
                    }
                }
                lastDamage?.let { d ->
                    if (pop.value > 0.01f) {
                        Text8("-$d", size = 22.sp, color = Color(0xFFFFE07A), weight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-20 - (1f - pop.value) * 30).dp).alpha(pop.value))
                    }
                }
            }
            // latest log line as a callout
            combat.combatLog.lastOrNull()?.let { latest ->
                Box(
                    Modifier.align(Alignment.TopStart).padding(10.dp).width(180.dp)
                        .pixelFrame(fill = Color(0xFF2B2620).copy(alpha = 0.92f), edge = Color(0xFF8A6238), style = FrameStyle.PLATE, unit = 2.dp)
                        .padding(6.dp),
                ) { Text8(latest, size = 9.sp, color = Color(0xFFF0DDB8), maxLines = 3, weight = FontWeight.Normal) }
            }
        }
        CommandDeck(state, combat, actions)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDungeon(t: Float) {
    val u = floor(3.dp.toPx())
    val w = size.width
    val h = size.height
    chamfer(0f, 0f, w, h, u, InkDark)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF181A26), Color(0xFF0E0F16))), Offset(u, u), Size(w - 2 * u, h - 2 * u))
    var y = u
    var row = 0
    val floorY = h * 0.78f
    while (y < floorY) {
        var x = if (row % 2 == 0) u else u - 7 * u
        while (x < w) {
            drawRect(Color.White.copy(alpha = 0.035f), Offset(x, y), Size(13 * u, 5 * u))
            drawRect(Color.Black.copy(alpha = 0.4f), Offset(x, y + 5 * u), Size(14 * u, u))
            x += 14 * u
        }
        y += 6 * u
        row++
    }
    listOf(w * 0.32f, w * 0.68f).forEachIndexed { i, tx ->
        val ty = h * 0.22f
        val fl = 0.75f + 0.25f * sin(t * 6.283f * (2 + i) + i)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFA040).copy(alpha = 0.3f * fl), Color.Transparent), Offset(tx, ty), w * 0.25f), w * 0.25f, Offset(tx, ty))
        drawRect(Color(0xFF4A3420), Offset(tx - u, ty + 2 * u), Size(2 * u, 6 * u))
        val fh = (3 + (fl * 3).toInt()) * u
        drawRect(Color(0xFFFF6A1A), Offset(tx - 2 * u, ty + 2 * u - fh), Size(4 * u, fh))
        drawRect(Color(0xFFFFC44A), Offset(tx - u, ty + 3 * u - fh), Size(2 * u, fh - u))
    }
    drawRect(Color(0xFF1C1A24), Offset(u, floorY), Size(w - 2 * u, h - floorY - u))
    drawRect(Color.White.copy(alpha = 0.07f), Offset(u, floorY), Size(w - 2 * u, u))
    var fx = u
    while (fx < w) {
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(fx, floorY + u), Size(u, h - floorY - 2 * u))
        fx += 16 * u
    }
}

@Composable
private fun CommandDeck(state: GameState, combat: ActiveCombatState, actions: QuestActions) {
    val colors = RpgTheme.colors
    val mon = state.activeMonster
    Column(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF15121A), edge = colors.pixelBorderBright)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton("⚔ STRIKE", colors.accentPrimary, modifier = Modifier.weight(1f)) { actions.combat(CombatAction.Strike) }
            PixelButton("⛨ GUARD", colors.ironSteel, modifier = Modifier.weight(1f)) { actions.combat(CombatAction.Guard) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PixelButton(
                if (mon == null) "NO COMPANION" else "🐾 ${mon.name.uppercase()}",
                colors.accentSecondary,
                enabled = mon != null && combat.monsterSkillCooldown == 0,
                modifier = Modifier.weight(1f),
            ) { actions.combat(CombatAction.MonsterSkill) }
            if (mon != null) {
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (i in 0 until 3) {
                        val charged = i >= combat.monsterSkillCooldown
                        Box(Modifier.size(12.dp).pixelFrame(fill = if (charged) colors.accentSecondary else colors.obsidianBg, edge = if (charged) colors.accentSecondary else colors.pixelBorder, style = FrameStyle.SOCKET, unit = 1.dp))
                    }
                }
            }
        }
        val spells = state.player.attunedSpellIds.mapNotNull(GameCatalog::spellById)
        if (spells.isNotEmpty()) {
            Text8("SIGILS · ${combat.playerMana} MANA", size = 9.sp, color = colors.arcaneMana, letterSpacing = 1.5.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                spells.forEach { s ->
                    val can = combat.playerMana >= s.manaCost
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(if (can) 1f else 0.4f)) {
                        GemSocket(if (can) spellColor(s.element) else null, 58.dp, onClick = { if (can) actions.combat(CombatAction.CastSpell(s.id)) }) {
                            PixelArtIcon(spellOrbArt(s.element, dim = !can), 30.dp)
                        }
                        Text8(s.name.substringBefore(" "), size = 8.sp, color = spellColor(s.element), maxLines = 1)
                        Text8("${s.manaCost} MP", size = 8.sp, color = colors.textMuted)
                    }
                }
            }
        }
    }
}
