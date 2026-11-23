package com.sparkborne.app.ui.hero

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.TalentCatalog
import com.sparkborne.game.catalog.WorldCatalog
import com.sparkborne.game.engine.TalentEngine
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.TalentNode
import com.sparkborne.game.model.TalentTree
import kotlin.math.floor
import kotlin.math.sin

fun treeColor(tree: TalentTree): Color = when (tree) {
    TalentTree.WARPATH -> Color(0xFFEF5D50)
    TalentTree.WAYFARER -> Color(0xFF5FD068)
    TalentTree.ARCANA -> Color(0xFFB57CFF)
}

private enum class NodeState { LEARNED, AVAILABLE, LOCKED, SEALED }

private fun nodeState(state: GameState, node: TalentNode): NodeState {
    val p = state.player
    return when {
        TalentEngine.rank(p, node.id) > 0 -> NodeState.LEARNED
        TalentEngine.sealedBy(p, node) != null -> NodeState.SEALED
        TalentEngine.lockReason(p, node) == null -> NodeState.AVAILABLE
        else -> NodeState.LOCKED
    }
}

// =============================================================================================
// TALENTS PAGE
// =============================================================================================

@Composable
fun TalentsPage(state: GameState, actions: HeroActions, open: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    var tree by remember { mutableStateOf(TalentTree.WARPATH) }
    val available = TalentEngine.pointsAvailable(p)

    // Points crest
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        PointsGem(available)
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text8(if (available == 1) "TALENT POINT" else "TALENT POINTS", size = 13.sp, color = GoldTrim, weight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Text8("Hero Lv.${p.level} → ${TalentEngine.pointsFromHeroLevel(p)}", size = 10.sp, color = colors.textMuted)
            Text8("Skill mastery → ${TalentEngine.pointsFromSkills(p)}  (1 per ${TalentEngine.SKILL_LEVELS_PER_POINT} lv)", size = 10.sp, color = colors.textMuted)
            Text8("Spent ${TalentEngine.pointsSpent(p)} of ${TalentEngine.pointsEarned(p)}", size = 10.sp, color = colors.textMuted)
        }
    }

    BannerTabs(TalentTree.entries, tree, { "${it.displayName} ${TalentEngine.spentInTree(p, it)}" }, { it.icon }) { tree = it }

    Text8(tree.tagline, size = 11.sp, color = treeColor(tree), align = TextAlign.Center, italic = true, weight = FontWeight.Normal, modifier = Modifier.fillMaxWidth())

    TalentTreeBoard(state, tree, open)

    RuneHeader("SHRINE OF UNBINDING", color = Color(0xFF7FD6FF))
    ShrinePanel(state, onReset = actions.resetTalents, compact = true)
}

@Composable
private fun PointsGem(points: Int) {
    val spin = rememberSpin(8000)
    val glow = if (points > 0) GoldTrim else RpgTheme.colors.pixelBorderBright
    Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val c = center
            val r = size.minDimension / 2
            if (points > 0) drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), Color.Transparent), c, r), r, c)
            for (i in 0 until 8) {
                val a = Math.toRadians((spin + i * 45).toDouble())
                val rr = r - 3.dp.toPx()
                drawRect(glow.copy(alpha = 0.7f), Offset(c.x + (kotlin.math.cos(a) * rr).toFloat() - 2.dp.toPx(), c.y + (kotlin.math.sin(a) * rr).toFloat() - 2.dp.toPx()), Size(4.dp.toPx(), 4.dp.toPx()))
            }
            fun diamond(k: Float) = Path().apply { moveTo(c.x, c.y - k); lineTo(c.x + k, c.y); lineTo(c.x, c.y + k); lineTo(c.x - k, c.y); close() }
            drawPath(diamond(r * 0.72f), InkDark)
            drawPath(diamond(r * 0.64f), glow.mixWith(Color.Black, 0.45f))
            drawPath(diamond(r * 0.64f), Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), c - Offset(r, r), c))
        }
        Text8("$points", size = 26.sp, color = Color.White, weight = FontWeight.ExtraBold)
    }
}

/** The branching tree: nodes on a 3-column grid with glowing links and OR seals on path choices. */
@Composable
private fun TalentTreeBoard(state: GameState, tree: TalentTree, open: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val nodes = TalentCatalog.nodesIn(tree)
    val tc = treeColor(tree)
    val rowH = 124.dp
    val top = 46.dp
    val tiers = (nodes.maxOf { it.tier }) + 1
    val tr = rememberInfiniteTransition(label = "tree")
    val flow by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "flow")

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(top + rowH * tiers)
            .pixelFrame(fill = colors.obsidianBg.mixWith(Color.Black, 0.15f), edge = tc.mixWith(colors.pixelBorder, 0.6f), rivet = tc),
    ) {
        val w = maxWidth
        fun cx(col: Int) = w * (0.18f + col * 0.32f)
        fun cy(tier: Int) = top + rowH * tier

        Canvas(Modifier.matchParentSize()) {
            val u = floor(2.dp.toPx())
            // tier bands + gate labels area
            for (t in 0 until tiers) {
                val y = cy(t).toPx()
                drawRect(Color.White.copy(alpha = if (t % 2 == 0) 0.015f else 0.03f), Offset(3 * u, y - rowH.toPx() / 2), Size(size.width - 6 * u, rowH.toPx()))
            }
            // links
            nodes.forEach { n ->
                val parent = n.requires?.let(TalentCatalog::nodeById) ?: return@forEach
                val a = Offset(cx(parent.column).toPx(), cy(parent.tier).toPx())
                val b = Offset(cx(n.column).toPx(), cy(n.tier).toPx())
                val parentLearned = TalentEngine.rank(p, parent.id) > 0
                val childLearned = TalentEngine.rank(p, n.id) > 0
                val sealed = TalentEngine.sealedBy(p, n) != null
                val c = when {
                    childLearned -> tc
                    sealed -> colors.pixelBorder.copy(alpha = 0.4f)
                    parentLearned -> tc.copy(alpha = 0.55f)
                    else -> colors.pixelBorder
                }
                val mid = Offset(b.x, a.y + (b.y - a.y) * 0.5f)
                // elbow: down from parent, across, down to child
                val pathEffect = if (parentLearned && !childLearned && !sealed) PathEffect.dashPathEffect(floatArrayOf(3 * u, 2 * u), -flow * 10 * u) else null
                drawLine(InkDark, a, Offset(a.x, mid.y), strokeWidth = 5 * u)
                drawLine(InkDark, Offset(a.x, mid.y), mid, strokeWidth = 5 * u)
                drawLine(InkDark, mid, b, strokeWidth = 5 * u)
                drawLine(c, a, Offset(a.x, mid.y), strokeWidth = 2 * u, pathEffect = pathEffect)
                drawLine(c, Offset(a.x, mid.y), mid, strokeWidth = 2 * u, pathEffect = pathEffect)
                drawLine(c, mid, b, strokeWidth = 2 * u, pathEffect = pathEffect)
            }
        }

        // Tier gate labels
        for (t in 1 until tiers) {
            val need = TalentEngine.pointsNeededForTier(t)
            val met = TalentEngine.spentInTree(p, tree) >= need
            Text8(
                "◆$need",
                size = 9.sp,
                color = if (met) tc else colors.textMuted,
                modifier = Modifier.offset(x = 8.dp, y = cy(t) - rowH / 2 + 6.dp),
            )
        }

        // OR seals between exclusive pairs
        nodes.filter { it.exclusiveGroup != null && it.column == 0 }.forEach { n ->
            Box(Modifier.offset(x = cx(1) - 22.dp, y = cy(n.tier) - 22.dp).size(44.dp), contentAlignment = Alignment.Center) {
                OrSeal(tc, decided = nodes.any { it.exclusiveGroup == n.exclusiveGroup && TalentEngine.rank(p, it.id) > 0 })
            }
        }

        // Nodes
        nodes.forEach { n ->
            val st = nodeState(state, n)
            val size = if (n.ability != null) 66.dp else 58.dp
            Box(Modifier.offset(x = cx(n.column) - 48.dp, y = cy(n.tier) - size / 2).width(96.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().clickable { open(Inspect.Talent(n.id)) }) {
                    TalentNodeIcon(n, st, tc, size)
                    Spacer(Modifier.height(2.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.background(InkDark.copy(alpha = 0.85f)).padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                    RankPips(TalentEngine.rank(p, n.id), n.maxRank, tc)
                    Text8(
                        n.name.uppercase(),
                        size = 8.sp,
                        color = when (st) {
                            NodeState.LEARNED -> tc
                            NodeState.AVAILABLE -> GoldTrim
                            else -> colors.textMuted
                        },
                        align = TextAlign.Center,
                        maxLines = 2,
                        letterSpacing = 0.5.sp,
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrSeal(color: Color, decided: Boolean) {
    val spin = if (!decided) rememberSpin(7000) else 0f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val c = center
            val r = size.minDimension / 2
            val u = 2.dp.toPx()
            for (i in 0 until 6) {
                val a = Math.toRadians((spin + i * 60).toDouble())
                drawRect(color.copy(alpha = if (decided) 0.25f else 0.6f), Offset(c.x + (kotlin.math.cos(a) * (r - u)).toFloat() - u, c.y + (kotlin.math.sin(a) * (r - u)).toFloat() - u), Size(2 * u, 2 * u))
            }
            drawCircle(InkDark, r * 0.62f, c)
            drawCircle(color.copy(alpha = if (decided) 0.3f else 0.8f), r * 0.62f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(u))
        }
        Text8("OR", size = 10.sp, color = if (decided) RpgTheme.colors.textMuted else color, weight = FontWeight.ExtraBold)
    }
}

@Composable
private fun TalentNodeIcon(n: TalentNode, st: NodeState, tc: Color, size: androidx.compose.ui.unit.Dp) {
    val colors = RpgTheme.colors
    val glow = when (st) {
        NodeState.LEARNED -> tc
        NodeState.AVAILABLE -> GoldTrim
        else -> null
    }
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            Text8(n.icon, size = (size.value * 0.36f).sp, modifier = Modifier.alpha(if (st == NodeState.LEARNED || st == NodeState.AVAILABLE) 1f else 0.35f))
            if (st == NodeState.SEALED) Text8("✕", size = (size.value * 0.42f).sp, color = colors.crimsonHp.copy(alpha = 0.8f), weight = FontWeight.ExtraBold)
            if (st == NodeState.LOCKED) PixelArtIcon(lockArt, 16.dp, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp))
        }
    }
    if (n.ability != null) {
        GemSocket(glow, size, onClick = {}) { content() }
    } else {
        ItemSocket(size, glow = glow, selected = st == NodeState.AVAILABLE) { content() }
    }
}

@Composable
private fun RankPips(rank: Int, max: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(8.dp)) {
        for (i in 0 until max) {
            Box(
                Modifier
                    .size(7.dp)
                    .pixelFrame(fill = if (i < rank) color else Color(0xFF15161D), edge = if (i < rank) color else RpgTheme.colors.pixelBorder, style = FrameStyle.PLATE, unit = 1.dp),
            )
        }
    }
}

// =============================================================================================
// TALENT INSPECT
// =============================================================================================

@Composable
fun TalentInspect(nodeId: String, state: GameState, actions: HeroActions, onNavigate: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val n = TalentCatalog.nodeById(nodeId) ?: return
    val tc = treeColor(n.tree)
    val rank = TalentEngine.rank(p, n.id)
    val st = nodeState(state, n)
    val reason = TalentEngine.lockReason(p, n)

    Showcase(if (st == NodeState.LEARNED || st == NodeState.AVAILABLE) tc else colors.pixelBorder) {
        if (n.ability != null) {
            GemSocket(tc, 96.dp, onClick = {}) { Text8(n.icon, size = 40.sp) }
        } else {
            Text8(n.icon, size = 60.sp)
        }
    }
    val kind = when {
        n.ability != null && n.tier == 4 -> "Capstone"
        n.ability != null -> "Path Ability"
        else -> "Passive"
    }
    ItemTitle(n.name, tc, "${n.tree.displayName} · Tier ${n.tier + 1} · $kind")
    when (st) {
        NodeState.LEARNED -> StatusRibbon(if (rank >= n.maxRank) "★ MASTERED" else "LEARNED $rank/${n.maxRank}", tc)
        NodeState.AVAILABLE -> StatusRibbon("READY TO LEARN", GoldTrim)
        NodeState.SEALED -> StatusRibbon("PATH SEALED", colors.crimsonHp)
        NodeState.LOCKED -> StatusRibbon("LOCKED", colors.textMuted)
    }
    if (n.flavor.isNotBlank()) Flavor(n.flavor)

    n.ability?.let { ab ->
        Box(
            Modifier
                .fillMaxWidth()
                .pixelFrame(fill = tc.copy(alpha = 0.10f), edge = tc.copy(alpha = 0.7f), style = FrameStyle.PLATE, unit = 2.dp)
                .padding(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text8("ABILITY · ${ab.displayName.uppercase()}", size = 10.sp, color = tc, letterSpacing = 1.5.sp)
                Text8(ab.description, size = 12.sp, color = colors.textPrimary)
            }
        }
    }
    n.perRank.forEach { (stat, per) ->
        val unit = if (stat.isPercent) "%" else ""
        StatLine("✚", "${stat.label} / rank", "+$per$unit", tc)
        if (n.maxRank > 1) {
            Text8(
                "Now +${per * rank}$unit" + if (rank < n.maxRank) "  →  Next +${per * (rank + 1)}$unit" else "",
                size = 10.sp,
                color = colors.textMuted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    RuneHeader("REQUIREMENTS", color = colors.textMuted)
    val spent = TalentEngine.spentInTree(p, n.tree)
    val need = TalentEngine.pointsNeededForTier(n.tier)
    if (need > 0) RequirementLine(spent >= need, "$need points in ${n.tree.displayName} ($spent/$need)")
    n.requires?.let(TalentCatalog::nodeById)?.let { req ->
        RequirementLine(TalentEngine.rank(p, req.id) > 0, "${req.icon} ${req.name}") { onNavigate(Inspect.Talent(req.id)) }
    }
    n.exclusiveGroup?.let { g ->
        TalentCatalog.nodes.filter { it.exclusiveGroup == g && it.id != n.id }.forEach { rival ->
            val chosen = TalentEngine.rank(p, rival.id) > 0
            RequirementLine(!chosen, if (chosen) "Sealed by ${rival.name}" else "Seals ${rival.icon} ${rival.name} until unbound") { onNavigate(Inspect.Talent(rival.id)) }
        }
    }

    Text8("${TalentEngine.pointsAvailable(p)} talent point(s) available", size = 10.sp, color = GoldTrim)
    PixelButton(
        text = if (rank == 0) "❖ LEARN" else if (rank < n.maxRank) "❖ RANK UP" else "MASTERED",
        color = tc,
        enabled = reason == null,
        onClick = { actions.learnTalent(n.id) },
    )
    if (reason != null && reason != "Mastered") Text8(reason, size = 10.sp, color = colors.textMuted, align = TextAlign.Center)
}

@Composable
private fun RequirementLine(ok: Boolean, text: String, onClick: (() -> Unit)? = null) {
    val colors = RpgTheme.colors
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text8(if (ok) "✓" else "✗", size = 13.sp, color = if (ok) colors.vitalityEmerald else colors.crimsonHp, modifier = Modifier.width(22.dp))
        Text8(text, size = 11.sp, color = if (ok) colors.textPrimary else colors.textMuted)
    }
}

// =============================================================================================
// SHRINE OF UNBINDING
// =============================================================================================

private val ShrineBlue = Color(0xFF7FD6FF)

/**
 * The Shrine of Unbinding: an animated pixel shrine plus the reset ritual.
 * Usable only in Stormspire Citadel (see [TalentEngine.resetBlocker]).
 */
@Composable
fun ShrinePanel(state: GameState, onReset: () -> Unit, compact: Boolean = false) {
    val colors = RpgTheme.colors
    val p = state.player
    val blocker = TalentEngine.resetBlocker(state)
    val cost = TalentEngine.resetCost(p)
    val here = TalentEngine.isAtShrine(state)
    Box(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF101A26), edge = ShrineBlue.mixWith(colors.pixelBorder, 0.5f), rivet = ShrineBlue)
            .padding(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ShrineArt(Modifier.fillMaxWidth().height(if (compact) 120.dp else 150.dp), active = here)
            Text8(TalentCatalog.SHRINE_NAME.uppercase(), size = 14.sp, color = ShrineBlue, weight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Text8(
                if (here) "Lay your talents upon the altar and walk a new path."
                else "Stands atop ${WorldCatalog.locationById(TalentCatalog.SHRINE_LOCATION_ID).name}.",
                size = 11.sp,
                color = colors.textMuted,
                align = TextAlign.Center,
                italic = true,
                weight = FontWeight.Normal,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text8("OFFERING", size = 9.sp, color = colors.textMuted, letterSpacing = 2.sp)
                if (cost == 0) Text8("FREE (FIRST)", size = 12.sp, color = colors.vitalityEmerald, weight = FontWeight.ExtraBold) else CostRow(p, gold = cost)
                Text8("· ${p.talents.resets} unbound", size = 9.sp, color = colors.textMuted)
            }
            PixelButton(
                text = "⛩ UNBIND ${TalentEngine.pointsSpent(p)} POINTS",
                color = ShrineBlue,
                enabled = blocker == null,
                onClick = onReset,
            )
            if (blocker != null) Text8(blocker, size = 10.sp, color = colors.textMuted, align = TextAlign.Center)
        }
    }
}

@Composable
private fun ShrineArt(modifier: Modifier, active: Boolean) {
    val tr = rememberInfiniteTransition(label = "shrine")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "shrine_t")
    val pulse by tr.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "shrine_p")
    Canvas(modifier) {
        val u = floor(size.height / 50f).coerceAtLeast(1f)
        val w = size.width
        val h = size.height
        val cx = w / 2
        val glow = ShrineBlue.copy(alpha = if (active) 1f else 0.45f)
        val stone = Color(0xFF3A4154)
        val stoneHi = Color(0xFF59627A)
        val stoneLo = Color(0xFF232838)
        // aura
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.35f * pulse), Color.Transparent), Offset(cx, h * 0.42f), h * 0.6f), h * 0.6f, Offset(cx, h * 0.42f))
        // stepped base
        val baseY = h - 4 * u
        listOf(26, 20, 14).forEachIndexed { i, half ->
            val y = baseY - (i + 1) * 3 * u
            chamfer(cx - half * u, y, cx + half * u, y + 3 * u, u, stone)
            drawRect(stoneHi, Offset(cx - half * u + u, y), Size((2 * half - 2) * u, u))
        }
        val topStep = baseY - 9 * u
        // pillars
        val pillarTop = topStep - 22 * u
        listOf(-11, 9).forEach { px ->
            drawRect(InkDark, Offset(cx + (px - 1) * u, pillarTop), Size(4 * u, 22 * u))
            drawRect(stone, Offset(cx + px * u, pillarTop), Size(2 * u, 22 * u))
            drawRect(stoneHi, Offset(cx + px * u, pillarTop), Size(u, 22 * u))
            // rune on pillar
            drawRect(glow.copy(alpha = 0.8f * pulse), Offset(cx + px * u, pillarTop + 8 * u), Size(2 * u, u))
            drawRect(glow.copy(alpha = 0.8f * pulse), Offset(cx + px * u, pillarTop + 12 * u), Size(2 * u, u))
        }
        // lintel (torii-like)
        chamfer(cx - 17 * u, pillarTop - 4 * u, cx + 17 * u, pillarTop - u, u, stoneLo)
        drawRect(stoneHi, Offset(cx - 16 * u, pillarTop - 4 * u), Size(32 * u, u))
        drawRect(stone, Offset(cx - 13 * u, pillarTop + u), Size(26 * u, 2 * u))
        // altar
        chamfer(cx - 6 * u, topStep - 5 * u, cx + 6 * u, topStep, u, stoneLo)
        drawRect(glow.copy(alpha = 0.6f), Offset(cx - 4 * u, topStep - 5 * u), Size(8 * u, u))
        // floating orb
        val orbY = topStep - 13 * u + sin(t * 6.283f) * 2 * u
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.8f), Color.Transparent), Offset(cx, orbY), 9 * u), 9 * u, Offset(cx, orbY))
        drawRect(Color.White, Offset(cx - 2 * u, orbY - 2 * u), Size(4 * u, 4 * u))
        drawRect(glow, Offset(cx - u, orbY - 3 * u), Size(2 * u, 6 * u))
        drawRect(glow, Offset(cx - 3 * u, orbY - u), Size(6 * u, 2 * u))
        // rising motes
        for (i in 0 until 10) {
            val ph = (t * (0.7f + (i % 4) * 0.15f) + i / 10f) % 1f
            val mx = cx + sin(i * 1.9f + ph * 4f) * 16 * u
            val my = baseY - ph * h * 0.85f
            drawRect(glow.copy(alpha = (1f - ph) * 0.9f), Offset(floor(mx / u) * u, floor(my / u) * u), Size(u, u))
        }
    }
}

/** Compact shrine banner for the Stormspire Citadel town sheet. */
@Composable
fun ShrineTownRow(state: GameState, onReset: () -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val blocker = TalentEngine.resetBlocker(state)
    val cost = TalentEngine.resetCost(p)
    Row(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF101A26), edge = ShrineBlue.mixWith(colors.pixelBorder, 0.5f), rivet = ShrineBlue)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShrineArt(Modifier.size(width = 64.dp, height = 64.dp), active = TalentEngine.isAtShrine(state))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text8("⛩ ${TalentCatalog.SHRINE_NAME.uppercase()}", size = 11.sp, color = ShrineBlue, weight = FontWeight.ExtraBold)
            Text8(
                blocker ?: if (cost == 0) "Unbind ${TalentEngine.pointsSpent(p)} talent points · first time free" else "Unbind ${TalentEngine.pointsSpent(p)} talent points for ${cost}g",
                size = 9.sp,
                color = colors.textMuted,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(6.dp))
        PixelButton("UNBIND", ShrineBlue, enabled = blocker == null, small = true, onClick = onReset)
    }
}
