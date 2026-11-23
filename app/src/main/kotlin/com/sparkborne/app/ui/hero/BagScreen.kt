package com.sparkborne.app.ui.hero

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.CraftingEngine
import com.sparkborne.game.engine.ToolbeltEngine
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.MaterialType
import kotlin.math.floor
import kotlin.math.sin

enum class BagFilter(val label: String, val glyph: String) {
    ALL("All", "◈"),
    MATS("Mats", "🪨"),
    BREWS("Brews", "⚗"),
    GEAR("Gear", "⚔"),
    SPOILS("Spoils", "🎁"),
}

private sealed interface BagEntry {
    val sortKey: Int
    data class Chest(val skill: com.sparkborne.game.model.SkillType, val n: Int) : BagEntry { override val sortKey = 0 }
    data class Brew(val recipeId: String, val n: Int) : BagEntry { override val sortKey = 1 }
    data class Mat(val type: MaterialType, val n: Int) : BagEntry { override val sortKey = 2 }
    data class Weapon(val id: String) : BagEntry { override val sortKey = 3 }
    data class Armor(val id: String) : BagEntry { override val sortKey = 4 }
    data class Tool(val id: String) : BagEntry { override val sortKey = 5 }
}

/**
 * The adventurer's pack: a coin purse, a classic slot-grid bag, and the cauldron workshop.
 */
@Composable
fun BagScreen(state: GameState, errorMessage: String?, actions: HeroActions, initialInspect: Inspect? = null) {
    var filter by remember { mutableStateOf(BagFilter.ALL) }
    var inspect by remember { mutableStateOf(initialInspect) }
    val open: (Inspect) -> Unit = { inspect = it }
    val colors = RpgTheme.colors
    val p = state.player

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CoinPurse(state)

            if (p.activeStepBoostCharges > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    RuneChip("⚡ Trailblazer Brew · ${p.activeStepBoostCharges} cycles left", colors.vitalityEmerald)
                }
            }

            BannerTabs(BagFilter.entries, filter, { it.label }, { it.glyph }) { filter = it }

            val entries = buildList {
                if (filter == BagFilter.ALL || filter == BagFilter.SPOILS) {
                    p.skillChests.filter { it.value > 0 }.forEach { (s, n) -> add(BagEntry.Chest(s, n)) }
                }
                if (filter == BagFilter.ALL || filter == BagFilter.BREWS) {
                    p.consumables.filter { it.value > 0 }.forEach { (id, n) -> add(BagEntry.Brew(id, n)) }
                }
                if (filter == BagFilter.ALL || filter == BagFilter.MATS) {
                    MaterialType.entries.forEach { m -> (p.materials[m] ?: 0).takeIf { it > 0 }?.let { add(BagEntry.Mat(m, it)) } }
                }
                if (filter == BagFilter.ALL || filter == BagFilter.GEAR) {
                    GameCatalog.weapons.filter { it.id in p.ownedWeaponIds || it.id == p.equippedWeaponId }.forEach { add(BagEntry.Weapon(it.id)) }
                    GameCatalog.armor.filter { it.id in p.ownedArmorIds }.forEach { add(BagEntry.Armor(it.id)) }
                    ToolbeltEngine.tools.filter { it.id in p.ownedToolIds }.forEach { add(BagEntry.Tool(it.id)) }
                }
            }.sortedBy { it.sortKey }

            BagGrid(state, entries, open)

            Workshop(state, open)
            Spacer(Modifier.height(24.dp))
        }
        inspect?.let { current ->
            InspectOverlay(current, state, actions, errorMessage, onNavigate = open, onClose = { inspect = null })
        }
    }
}

@Composable
private fun CoinPurse(state: GameState) {
    val colors = RpgTheme.colors
    val p = state.player
    Box(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = Color(0xFF3A2618), edge = Color(0xFF8A5A34), rivet = GoldTrim)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PurseCoin(coinArt, "%,d".format(p.gold), GoldTrim)
                PurseCoin(ingotArt, "%,d".format(p.ironOre), Color(0xFFC9D2DE))
                PurseCoin(shardArt, "%,d".format(p.emberShards), Color(0xFFFF9A5E))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelArtIcon(bootArt, 18.dp)
                Spacer(Modifier.width(6.dp))
                SegmentBar(
                    progress = p.savedSteps.toFloat() / p.maxStepBankCapacity().coerceAtLeast(1),
                    color = colors.vitalityEmerald,
                    segments = 20,
                    height = 14.dp,
                    label = "%,d / %,d STEPS".format(p.savedSteps, p.maxStepBankCapacity()),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PurseCoin(art: PixelArt, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PixelArtIcon(art, 24.dp)
        Spacer(Modifier.width(6.dp))
        Text8(value, size = 15.sp, color = color, weight = FontWeight.ExtraBold)
    }
}

@Composable
private fun BagGrid(state: GameState, entries: List<BagEntry>, open: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val cols = 5
    val minSlots = 25
    val total = maxOf(minSlots, ((entries.size + cols - 1) / cols) * cols)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = colors.panelSurface, edge = colors.pixelBorderBright)
            .padding(10.dp),
    ) {
        val gap = 6.dp
        val cell = (maxWidth - gap * (cols - 1)) / cols
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            (0 until total).chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { idx ->
                        val e = entries.getOrNull(idx)
                        when (e) {
                            null -> ItemSocket(cell) {}
                            is BagEntry.Chest -> ItemSocket(cell, glow = GoldTrim, qty = e.n, onClick = { open(Inspect.Chest(e.skill)) }) {
                                PixelArtIcon(chestArt, cell * 0.62f)
                            }
                            is BagEntry.Brew -> {
                                val r = CraftingEngine.recipeById(e.recipeId)
                                ItemSocket(cell, glow = colors.vitalityEmerald, qty = e.n, onClick = { open(Inspect.Brew(e.recipeId)) }) {
                                    Text8(r?.icon ?: "⚗", size = (cell.value * 0.4f).sp)
                                }
                            }
                            is BagEntry.Mat -> ItemSocket(cell, glow = Rarity.COMMON.color.copy(alpha = 0.6f), qty = e.n, onClick = { open(Inspect.Material(e.type)) }) {
                                Text8(e.type.icon, size = (cell.value * 0.4f).sp)
                            }
                            is BagEntry.Weapon -> {
                                val w = GameCatalog.weaponById(e.id)
                                val r = weaponRarity(w)
                                ItemSocket(cell, glow = r.color, tag = if (p.equippedWeaponId == e.id) "E" else null, onClick = { open(Inspect.Gear(GearSlot.WEAPON, e.id)) }) {
                                    PixelArtIcon(weaponArt(w.weaponClass, r), cell * 0.6f)
                                }
                            }
                            is BagEntry.Armor -> {
                                val a = GameCatalog.armorById(e.id)!!
                                val r = armorRarity(a)
                                val slot = GearSlot.entries.first { it.armorSlot == a.slot }
                                ItemSocket(cell, glow = r.color, tag = if (p.equippedArmor[a.slot] == e.id) "E" else null, onClick = { open(Inspect.Gear(slot, e.id)) }) {
                                    PixelArtIcon(armorArt(a.slot, r), cell * 0.6f)
                                }
                            }
                            is BagEntry.Tool -> {
                                val t = ToolbeltEngine.toolById(e.id)
                                ItemSocket(cell, glow = rarityForCost(t?.forgeGoldCost ?: 0).color, tag = if (e.id in p.equippedToolIds) "E" else null, onClick = { open(Inspect.Tool(e.id)) }) {
                                    Text8(t?.icon ?: "🧰", size = (cell.value * 0.4f).sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Cauldron workshop: bubbling pot + recipe sockets (glow green when brewable). */
@Composable
private fun Workshop(state: GameState, open: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    RuneHeader("CAULDRON", color = colors.vitalityEmerald)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Cauldron(Modifier.size(width = 110.dp, height = 110.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CraftingEngine.recipes.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { rcp ->
                        val ready = p.gold >= rcp.goldCost && rcp.requiredMaterials.all { (m, n) -> (p.materials[m] ?: 0) >= n }
                        val tier = CraftingEngine.qualityTierFor(state, rcp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(62.dp)) {
                            ItemSocket(
                                56.dp,
                                glow = if (ready) colors.vitalityEmerald else null,
                                dim = !ready,
                                qty = (p.consumables[rcp.id] ?: 0).takeIf { it > 0 },
                                onClick = { open(Inspect.Brew(rcp.id)) },
                            ) { Text8(rcp.icon, size = 24.sp) }
                            Text8(tier.displayName.uppercase(), size = 7.sp, color = if (ready) colors.vitalityEmerald else colors.textMuted, align = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cauldron(modifier: Modifier) {
    val colors = RpgTheme.colors
    val tr = rememberInfiniteTransition(label = "cauldron")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "c_t")
    val brew = colors.vitalityEmerald
    Canvas(modifier) {
        val u = floor(3.dp.toPx())
        val w = size.width
        val h = size.height
        val cx = w / 2
        // fire
        for (i in -3..3) {
            val fh = (3 + ((sin(t * 25f + i * 1.7f) + 1f) * 2f).toInt()) * u
            drawRect(Color(0xFFFF6A1A), Offset(cx + i * 2 * u, h - fh), Size(2 * u, fh))
            drawRect(Color(0xFFFFC44A), Offset(cx + i * 2 * u, h - fh / 2), Size(2 * u, fh / 2))
        }
        // pot
        val top = h * 0.38f
        val bottom = h - 6 * u
        val rows = ((bottom - top) / u).toInt()
        for (r in 0..rows) {
            val f = r.toFloat() / rows
            val half = (w * 0.36f) * kotlin.math.sqrt(1f - (f - 0.35f).let { it * it } * 1.6f).coerceAtLeast(0.4f)
            drawRect(InkDark, Offset(cx - half - u, top + r * u), Size(2 * half + 2 * u, u))
            drawRect(Color(0xFF2E3240), Offset(cx - half, top + r * u), Size(2 * half, u))
        }
        drawRect(Color(0xFF454A5C), Offset(cx - w * 0.36f, top), Size(w * 0.18f, u))
        // rim + brew
        drawRect(InkDark, Offset(cx - w * 0.42f, top - 2 * u), Size(w * 0.84f, 3 * u))
        drawRect(Color(0xFF3E4352), Offset(cx - w * 0.41f, top - 2 * u), Size(w * 0.82f, 2 * u))
        drawRect(brew, Offset(cx - w * 0.36f, top - 3 * u), Size(w * 0.72f, 2 * u))
        drawRect(brew.mixWith(Color.White, 0.4f), Offset(cx - w * 0.3f, top - 3 * u), Size(w * 0.2f, u))
        // bubbles
        for (i in 0 until 6) {
            val ph = (t * (1f + i * 0.17f) + i / 6f) % 1f
            val bx = cx + sin(i * 2.1f + ph * 4f) * w * 0.22f
            val by = top - 4 * u - ph * h * 0.36f
            val s = if (ph < 0.6f) 2 * u else u
            drawRect(brew.copy(alpha = 1f - ph), Offset(floor(bx / u) * u, floor(by / u) * u), Size(s, s))
        }
        drawCircle(Brush.radialGradient(listOf(brew.copy(alpha = 0.25f), Color.Transparent), Offset(cx, top), w * 0.5f), w * 0.5f, Offset(cx, top))
    }
}
