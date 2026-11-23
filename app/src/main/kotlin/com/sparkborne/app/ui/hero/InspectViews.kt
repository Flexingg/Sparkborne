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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.CollectibleEngine
import com.sparkborne.game.engine.CraftingEngine
import com.sparkborne.game.engine.SkillEngine
import com.sparkborne.game.engine.ToolbeltEngine
import com.sparkborne.game.model.ArmorItem
import com.sparkborne.game.model.ArmorSlot
import com.sparkborne.game.model.ConsumableEffectType
import com.sparkborne.game.model.CraftingRecipe
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.MaterialType
import com.sparkborne.game.model.PlayerState
import com.sparkborne.game.model.SkillType
import com.sparkborne.game.model.WeaponClass
import com.sparkborne.game.model.WeaponItem
import com.sparkborne.telemetry.model.MuscleGroup
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// =============================================================================================
// What can be inspected
// =============================================================================================

enum class GearSlot(val label: String, val armorSlot: ArmorSlot?) {
    WEAPON("Weapon", null),
    HELM("Helm", ArmorSlot.HELM),
    CHEST("Chest", ArmorSlot.CHEST),
    LEGS("Greaves", ArmorSlot.LEGS),
    RELIC("Relic", ArmorSlot.RELIC),
}

sealed interface Inspect {
    data class Gear(val slot: GearSlot, val itemId: String? = null) : Inspect
    data class Tool(val toolId: String? = null) : Inspect
    data class Skill(val skill: SkillType) : Inspect
    data class Spell(val spellId: String) : Inspect
    data class Relic(val id: String) : Inspect
    data class Material(val type: MaterialType) : Inspect
    data class Brew(val recipeId: String) : Inspect
    data class Chest(val skill: SkillType) : Inspect
    data class Muscle(val group: MuscleGroup) : Inspect
    data class Loadout(val index: Int) : Inspect
}

/** All game mutations the character screens can trigger. */
class HeroActions(
    val forgeOrEquipWeapon: (String) -> Unit = {},
    val forgeOrEquipArmor: (String) -> Unit = {},
    val unlockOrAttuneSpell: (String) -> Unit = {},
    val openSkillChest: (SkillType) -> Unit = {},
    val forgeOrToggleTool: (String) -> Unit = {},
    val saveGearSet: (Int, String) -> Unit = { _, _ -> },
    val equipGearSet: (Int) -> Unit = {},
    val craftRecipe: (String) -> Unit = {},
    val useConsumable: (String) -> Unit = {},
)

val LOADOUT_NAMES = listOf("Combat", "Skilling", "Boss")

fun weaponRarity(w: WeaponItem) = rarityForCost(w.forgeGoldCost)
fun armorRarity(a: ArmorItem) = rarityForCost(a.forgeGoldCost)

private fun weaponFlavor(cls: WeaponClass): String = when (cls) {
    WeaponClass.GREATSWORD -> "A broad blade that rewards a strong chest, back and shoulders. Every press and row sharpens its edge."
    WeaponClass.WARHAMMER -> "Ruinous weight. Deadlifts and squats turn this maul into a siege engine."
    WeaponClass.SPEAR -> "Reach and footwork. Powerful legs drive every thrust of the polearm."
    WeaponClass.TWIN_BLADES -> "Twin fangs for quick, relentless strikes. Arm and grip strength set their tempo."
}

private fun armorFlavor(slot: ArmorSlot): String = when (slot) {
    ArmorSlot.HELM -> "Forged to turn aside blows aimed at the crown."
    ArmorSlot.CHEST -> "Plates layered over the heart of a Sparkborne knight."
    ArmorSlot.LEGS -> "Greaves for long marches between the realm's towns."
    ArmorSlot.RELIC -> "A charm humming with old magic. It feeds your spellcraft."
}

fun brewEffectText(recipe: CraftingRecipe, potency: Int): String = when (recipe.effectType) {
    ConsumableEffectType.STEP_EFFICIENCY_BOOST -> "+${CraftingEngine.STEP_BOOST_EFFICIENCY_PERCENT}% Work Efficiency for $potency cycles"
    ConsumableEffectType.DELVE_HEAL_HP -> "Restores $potency HP during a Delve"
    ConsumableEffectType.DELVE_RESTORE_MANA -> "Restores $potency Mana during a Delve"
    ConsumableEffectType.EGG_INCUBATION_SURGE -> "Surges your first egg's incubation by $potency"
    ConsumableEffectType.DELVE_FULL_RESTORE_SHIELD -> "Full restore + $potency shield during a Delve"
}

// =============================================================================================
// Overlay shell
// =============================================================================================

@Composable
fun InspectOverlay(
    inspect: Inspect,
    state: GameState,
    actions: HeroActions,
    errorMessage: String?,
    onNavigate: (Inspect) -> Unit,
    onClose: () -> Unit,
) {
    val colors = RpgTheme.colors
    val accent = accentFor(inspect, state)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE0040508))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .pixelFrame(fill = colors.panelSurface, edge = accent.mixWith(colors.pixelBorderBright, 0.35f), rivet = accent),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (inspect) {
                    is Inspect.Gear -> GearInspect(inspect, state, actions, onNavigate)
                    is Inspect.Tool -> ToolInspect(inspect, state, actions, onNavigate)
                    is Inspect.Skill -> SkillInspect(inspect.skill, state, actions, onNavigate)
                    is Inspect.Spell -> SpellInspect(inspect.spellId, state, actions, onNavigate)
                    is Inspect.Relic -> RelicInspect(inspect.id, state)
                    is Inspect.Material -> MaterialInspect(inspect.type, state, onNavigate)
                    is Inspect.Brew -> BrewInspect(inspect.recipeId, state, actions, onNavigate)
                    is Inspect.Chest -> ChestInspect(inspect.skill, state, actions, onNavigate)
                    is Inspect.Muscle -> MuscleInspect(inspect.group, state)
                    is Inspect.Loadout -> LoadoutInspect(inspect.index, state, actions, onNavigate)
                }
                if (errorMessage != null) {
                    Text8("⚠ $errorMessage", size = 10.sp, color = colors.crimsonHp, align = TextAlign.Center)
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .pixelFrame(fill = colors.obsidianBg, edge = colors.pixelBorderBright, style = FrameStyle.PLATE, unit = 2.dp)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Text8("✕", size = 13.sp, color = colors.textMuted) }
        }
    }
}

private fun accentFor(inspect: Inspect, state: GameState): Color = when (inspect) {
    is Inspect.Gear -> {
        val id = inspect.itemId ?: equippedIdFor(inspect.slot, state.player)
        if (inspect.slot == GearSlot.WEAPON) {
            GameCatalog.weapons.firstOrNull { it.id == id }?.let { weaponRarity(it).color } ?: Rarity.COMMON.color
        } else {
            GameCatalog.armor.firstOrNull { it.id == id }?.let { armorRarity(it).color } ?: Rarity.COMMON.color
        }
    }
    is Inspect.Tool -> (inspect.toolId?.let(ToolbeltEngine::toolById))?.let { rarityForCost(it.forgeGoldCost).color } ?: Rarity.UNCOMMON.color
    is Inspect.Spell -> GameCatalog.spellById(inspect.spellId)?.let { elementColor(it.element) } ?: Rarity.EPIC.color
    is Inspect.Relic -> GoldTrim
    is Inspect.Chest -> Color(0xFFB27744)
    is Inspect.Skill, is Inspect.Muscle -> Color(0xFF4FD1C5)
    is Inspect.Material, is Inspect.Brew -> Color(0xFF5FD068)
    is Inspect.Loadout -> GoldTrim
}

fun equippedIdFor(slot: GearSlot, p: PlayerState): String? =
    if (slot == GearSlot.WEAPON) p.equippedWeaponId else p.equippedArmor[slot.armorSlot]

// =============================================================================================
// Shared building blocks
// =============================================================================================

/** Glowing pedestal with rotating light rays and rising sparkles; [content] floats above it. */
@Composable
fun Showcase(glow: Color, height: Dp = 150.dp, shake: Boolean = false, content: @Composable () -> Unit) {
    val tr = rememberInfiniteTransition(label = "showcase")
    val clock by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "clk")
    val bob = rememberBob(4f, 1500)
    val shakeX = if (shake) (sin(clock * 2 * PI * 18).toFloat() * (if ((clock * 4) % 1f < 0.25f) 3f else 0f)) else 0f
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val cx = size.width / 2
            val cy = size.height * 0.44f
            val u = floor(3.dp.toPx())
            // light rays
            val rays = 10
            for (i in 0 until rays) {
                val a = (clock * 360f + i * 360f / rays) * (PI / 180).toFloat()
                val a2 = a + 0.12f
                val r = size.height * 0.9f
                val p = Path().apply {
                    moveTo(cx, cy)
                    lineTo(cx + cos(a) * r, cy + sin(a) * r)
                    lineTo(cx + cos(a2) * r, cy + sin(a2) * r)
                    close()
                }
                drawPath(p, glow.copy(alpha = 0.07f))
            }
            drawCircle(
                Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), Color.Transparent), Offset(cx, cy), size.height * 0.45f),
                radius = size.height * 0.45f,
                center = Offset(cx, cy),
            )
            // pedestal
            val pw = size.width * 0.36f
            val py = size.height - 7 * u
            chamfer(cx - pw / 2, py, cx + pw / 2, py + 4 * u, u, Color(0xFF2B2E3A))
            drawRect(Color(0xFF454A5C), Offset(cx - pw / 2 + 2 * u, py), Size(pw - 4 * u, u))
            chamfer(cx - pw / 2 - 2 * u, py + 4 * u, cx + pw / 2 + 2 * u, py + 7 * u, u, Color(0xFF1F222C))
            drawRect(glow.copy(alpha = 0.55f), Offset(cx - pw / 2 + 3 * u, py + u), Size(pw - 6 * u, u))
            // sparkles
            for (i in 0 until 8) {
                val ph = (clock * (1.2f + (i % 3) * 0.3f) + i / 8f) % 1f
                val sx = cx + sin(i * 2.3f + ph * 3f) * size.width * 0.18f
                val sy = py - ph * size.height * 0.75f
                drawRect(glow.mixWith(Color.White, 0.5f).copy(alpha = (1f - ph) * 0.9f), Offset(floor(sx / u) * u, floor(sy / u) * u), Size(u, u))
            }
        }
        Box(Modifier.offset(x = shakeX.dp, y = (bob - 10).dp)) { content() }
    }
}

@Composable
fun ItemTitle(name: String, color: Color, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text8(name.uppercase(), size = 17.sp, color = color, weight = FontWeight.ExtraBold, align = TextAlign.Center, letterSpacing = 1.sp)
        Text8(subtitle, size = 10.sp, color = RpgTheme.colors.textMuted, align = TextAlign.Center, letterSpacing = 1.5.sp)
    }
}

@Composable
fun Flavor(text: String) {
    Text8(
        "“$text”",
        size = 11.sp,
        color = RpgTheme.colors.textMuted,
        weight = FontWeight.Normal,
        align = TextAlign.Center,
        italic = true,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

/** One stat line: icon · label ........ value (▲ delta). */
@Composable
fun StatLine(icon: String, label: String, value: String, color: Color, delta: Int? = null) {
    val colors = RpgTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text8(icon, size = 13.sp, color = color, modifier = Modifier.width(24.dp))
        Text8(label.uppercase(), size = 10.sp, color = colors.textMuted, letterSpacing = 1.5.sp)
        DotLeader(Modifier.weight(1f).padding(horizontal = 6.dp))
        Text8(value, size = 13.sp, color = color, weight = FontWeight.ExtraBold)
        if (delta != null && delta != 0) {
            Spacer(Modifier.width(6.dp))
            Text8(
                if (delta > 0) "▲$delta" else "▼${-delta}",
                size = 10.sp,
                color = if (delta > 0) colors.vitalityEmerald else colors.crimsonHp,
                weight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun DotLeader(modifier: Modifier) {
    val c = RpgTheme.colors.pixelBorder
    Canvas(modifier.height(8.dp)) {
        val u = floor(2.dp.toPx())
        var x = 0f
        while (x < size.width) {
            drawRect(c, Offset(x, size.height - u), Size(u, u))
            x += 3 * u
        }
    }
}

/** Forge cost as coin/ingot/shard sprites; unaffordable amounts glow red. */
@Composable
fun CostRow(player: PlayerState, gold: Int = 0, iron: Int = 0, shards: Int = 0) {
    val colors = RpgTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Triple(coinArt, gold, player.gold), Triple(ingotArt, iron, player.ironOre), Triple(shardArt, shards, player.emberShards))
            .filter { it.second > 0 }
            .forEach { (art, need, have) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelArtIcon(art, 18.dp)
                    Spacer(Modifier.width(4.dp))
                    Text8("$need", size = 13.sp, color = if (have >= need) colors.textPrimary else colors.crimsonHp, weight = FontWeight.ExtraBold)
                }
            }
    }
}

@Composable
fun StatusRibbon(text: String, color: Color) {
    Box(
        Modifier
            .pixelFrame(fill = color.copy(alpha = 0.18f), edge = color, style = FrameStyle.PLATE, unit = 2.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) { Text8(text, size = 10.sp, color = color, weight = FontWeight.ExtraBold, letterSpacing = 2.sp) }
}

@Composable
fun MiniLabel(text: String) {
    Text8(text, size = 9.sp, color = RpgTheme.colors.textMuted, letterSpacing = 2.sp, modifier = Modifier.fillMaxWidth())
}

// =============================================================================================
// GEAR (weapon & armor armory)
// =============================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GearInspect(g: Inspect.Gear, state: GameState, actions: HeroActions, onNavigate: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val equippedId = equippedIdFor(g.slot, p)
    if (g.slot == GearSlot.WEAPON) {
        val all = GameCatalog.weapons
        val w = all.firstOrNull { it.id == (g.itemId ?: equippedId) } ?: all.first()
        val eq = GameCatalog.weaponById(p.equippedWeaponId)
        val owned = w.id in p.ownedWeaponIds || (w.forgeGoldCost == 0 && w.forgeIronCost == 0)
        val equipped = w.id == p.equippedWeaponId
        val r = weaponRarity(w)
        Showcase(r.color) { PixelArtIcon(weaponArt(w.weaponClass, r), 96.dp) }
        ItemTitle(w.name, r.color, "${r.label} ${w.weaponClass.displayName} · Weapon")
        GearStatus(equipped, owned)
        Flavor(weaponFlavor(w.weaponClass))
        StatLine("⚔", "Base Damage", "${w.baseDamage}", colors.accentPrimary, delta = if (equipped) null else w.baseDamage - eq.baseDamage)
        StatLine("⚡", "Muscle Scaling", "×${"%.1f".format(w.muscleScalingFactor)}", colors.vitalityEmerald, delta = null)
        MiniLabel("EMPOWERED BY")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            w.weaponClass.scalingMuscles.forEach { m ->
                RuneChip("${m.displayName} R${p.muscleStrengthRank(m)}", colors.vitalityEmerald) { onNavigate(Inspect.Muscle(m)) }
            }
        }
        GearAction(equipped, owned, p, w.forgeGoldCost, w.forgeIronCost, 0) { actions.forgeOrEquipWeapon(w.id) }
        Armory(
            title = "ARMORY · WEAPONS",
            ids = all.map { it.id },
            focusedId = w.id,
            equippedId = p.equippedWeaponId,
            slotContent = { id ->
                val item = all.first { it.id == id }
                val rr = weaponRarity(item)
                ArmorySlotContent(weaponArt(item.weaponClass, rr), id in p.ownedWeaponIds || item.forgeGoldCost == 0)
            },
            onPick = { onNavigate(Inspect.Gear(GearSlot.WEAPON, it)) },
        )
    } else {
        val slot = g.slot.armorSlot!!
        val all = GameCatalog.armor.filter { it.slot == slot }
        val a = all.firstOrNull { it.id == (g.itemId ?: equippedId) } ?: all.first()
        val eq = equippedId?.let(GameCatalog::armorById)
        val owned = a.id in p.ownedArmorIds || (a.forgeGoldCost == 0 && a.forgeIronCost == 0 && a.forgeShardsCost == 0)
        val equipped = a.id == equippedId
        val r = armorRarity(a)
        Showcase(r.color) { PixelArtIcon(armorArt(slot, r), 96.dp) }
        ItemTitle(a.name, r.color, "${r.label} ${slot.displayName} · Armor")
        GearStatus(equipped, owned)
        Flavor(armorFlavor(slot))
        fun d(v: Int, e: Int?) = if (equipped) null else v - (e ?: 0)
        StatLine("⛨", "Defense", "${a.defense}", colors.accentSecondary, d(a.defense, eq?.defense))
        StatLine("❤", "Vitality", "+${a.bonusHp}", colors.crimsonHp, d(a.bonusHp, eq?.bonusHp))
        if (a.bonusMana > 0 || (eq?.bonusMana ?: 0) > 0) StatLine("✦", "Mana", "+${a.bonusMana}", colors.arcaneMana, d(a.bonusMana, eq?.bonusMana))
        if (a.spellPowerBonus > 0 || (eq?.spellPowerBonus ?: 0) > 0) StatLine("✧", "Spell Power", "+${a.spellPowerBonus}", colors.arcaneMana, d(a.spellPowerBonus, eq?.spellPowerBonus))
        GearAction(equipped, owned, p, a.forgeGoldCost, a.forgeIronCost, a.forgeShardsCost) { actions.forgeOrEquipArmor(a.id) }
        Armory(
            title = "ARMORY · ${slot.displayName.uppercase()}",
            ids = all.map { it.id },
            focusedId = a.id,
            equippedId = equippedId,
            slotContent = { id ->
                val item = all.first { it.id == id }
                val rr = armorRarity(item)
                ArmorySlotContent(armorArt(slot, rr), id in p.ownedArmorIds || item.forgeGoldCost == 0)
            },
            onPick = { onNavigate(Inspect.Gear(g.slot, it)) },
        )
    }
}

@Composable
private fun GearStatus(equipped: Boolean, owned: Boolean) {
    val colors = RpgTheme.colors
    when {
        equipped -> StatusRibbon("★ EQUIPPED", colors.vitalityEmerald)
        owned -> StatusRibbon("IN YOUR ARMORY", colors.accentSecondary)
        else -> StatusRibbon("UNFORGED", colors.textMuted)
    }
}

@Composable
private fun GearAction(equipped: Boolean, owned: Boolean, p: PlayerState, gold: Int, iron: Int, shards: Int, onClick: () -> Unit) {
    val colors = RpgTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!owned) {
            MiniLabel("FORGE COST")
            CostRow(p, gold, iron, shards)
        }
        val affordable = p.gold >= gold && p.ironOre >= iron && p.emberShards >= shards
        PixelButton(
            text = when {
                equipped -> "EQUIPPED"
                owned -> "EQUIP"
                else -> "⚒ FORGE & EQUIP"
            },
            color = if (owned) colors.vitalityEmerald else colors.accentPrimary,
            enabled = !equipped && (owned || affordable),
            onClick = onClick,
        )
    }
}

@Composable
private fun ArmorySlotContent(art: PixelArt, owned: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        PixelArtIcon(art, 34.dp, Modifier.alpha(if (owned) 1f else 0.4f))
        if (!owned) PixelArtIcon(lockArt, 16.dp, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Armory(
    title: String,
    ids: List<String>,
    focusedId: String,
    equippedId: String?,
    slotContent: @Composable (String) -> Unit,
    onPick: (String) -> Unit,
) {
    RuneHeader(title, color = RpgTheme.colors.textMuted)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ids.forEach { id ->
            ItemSocket(
                size = 54.dp,
                selected = id == focusedId,
                tag = if (id == equippedId) "E" else null,
                onClick = { onPick(id) },
            ) { slotContent(id) }
        }
    }
}

@Composable
fun RuneChip(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .pixelFrame(fill = color.copy(alpha = 0.12f), edge = color.copy(alpha = 0.7f), style = FrameStyle.PLATE, unit = 1.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) { Text8(text, size = 10.sp, color = color) }
}

// =============================================================================================
// TOOLS
// =============================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolInspect(t: Inspect.Tool, state: GameState, actions: HeroActions, onNavigate: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val all = ToolbeltEngine.tools
    val tool = all.firstOrNull { it.id == t.toolId } ?: p.equippedToolIds.firstOrNull()?.let(ToolbeltEngine::toolById) ?: all.first()
    val owned = tool.id in p.ownedToolIds
    val equipped = tool.id in p.equippedToolIds
    val r = rarityForCost(tool.forgeGoldCost)
    Showcase(r.color) { Text8(tool.icon, size = 64.sp) }
    ItemTitle(tool.name, r.color, "${r.label} Skilling Tool")
    when {
        equipped -> StatusRibbon("★ ON YOUR BELT", colors.vitalityEmerald)
        owned -> StatusRibbon("IN YOUR PACK", colors.accentSecondary)
        else -> StatusRibbon("UNFORGED", colors.textMuted)
    }
    Flavor(tool.description)
    StatLine("⚙", "Work Efficiency", "+${tool.workEfficiencyBonusPercent}%", colors.vitalityEmerald)
    StatLine("✚", "Bonus Yield", "+${tool.bonusYieldPercent}%", colors.accentPrimary)
    MiniLabel("WORKS WITH")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        val skills = tool.targetSkills.ifEmpty { SkillType.entries.toSet() }
        skills.forEach { s -> RuneChip("${s.icon} ${s.displayName}", colors.accentSecondary) { onNavigate(Inspect.Skill(s)) } }
    }
    Text8("BELT ${p.equippedToolIds.size}/${p.maxToolbeltSlots()}", size = 10.sp, color = colors.textMuted, letterSpacing = 2.sp)
    if (!owned) {
        MiniLabel("FORGE COST")
        CostRow(p, tool.forgeGoldCost, tool.forgeIronCost, tool.forgeShardsCost)
    }
    PixelButton(
        text = when {
            equipped -> "UNEQUIP"
            owned -> "STRAP ON BELT"
            else -> "⚒ FORGE TOOL"
        },
        color = when {
            equipped -> colors.ironSteel
            owned -> colors.vitalityEmerald
            else -> colors.accentPrimary
        },
        onClick = { actions.forgeOrToggleTool(tool.id) },
    )
    Armory("TOOL RACK", all.map { it.id }, tool.id, null, slotContent = { id ->
        val it2 = all.first { x -> x.id == id }
        Box(contentAlignment = Alignment.Center) {
            Text8(it2.icon, size = 22.sp)
            if (id !in p.ownedToolIds) PixelArtIcon(lockArt, 14.dp, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
            if (id in p.equippedToolIds) Text8("E", size = 8.sp, color = colors.vitalityEmerald, modifier = Modifier.align(Alignment.TopStart))
        }
    }) { onNavigate(Inspect.Tool(it)) }
}

// =============================================================================================
// SKILLS
// =============================================================================================

@Composable
fun SkillMedallion(skill: SkillType, level: Int, progress: Float, size: Dp, glow: Color, chests: Int = 0) {
    val colors = RpgTheme.colors
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val u = floor(2.dp.toPx())
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.3f), Color.Transparent)), radius = this.size.minDimension / 2)
            drawCircle(InkDark, radius = this.size.minDimension / 2 - 3 * u)
            drawCircle(colors.panelElevated, radius = this.size.minDimension / 2 - 4 * u)
            pixelRing(progress, glow, colors.obsidianBg, 3 * u)
        }
        Text8(skill.icon, size = (size.value * 0.32f).sp)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 4.dp)
                .pixelFrame(fill = InkDark, edge = glow.copy(alpha = 0.8f), style = FrameStyle.PLATE, unit = 1.dp)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) { Text8("$level", size = (size.value * 0.14f).coerceAtLeast(9f).sp, color = glow, weight = FontWeight.ExtraBold) }
        if (chests > 0) {
            PixelArtIcon(chestArt, (size.value * 0.34f).dp, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillInspect(skill: SkillType, state: GameState, actions: HeroActions, onNavigate: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val xp = p.skillXp[skill] ?: 0
    val lvl = SkillEngine.levelForXp(xp)
    val (cur, need) = SkillEngine.xpToNextLevel(xp)
    val eff = SkillEngine.workEfficiencyPercent(lvl)
    val chests = p.skillChests[skill] ?: 0
    val glow = colors.accentSecondary
    Showcase(glow, height = 170.dp) { SkillMedallion(skill, lvl, if (lvl >= 99) 1f else cur.toFloat() / need, 120.dp, glow) }
    ItemTitle(skill.displayName, glow, "Skill · Level $lvl / 99")
    Flavor(skill.description)
    SegmentBar(
        progress = if (lvl >= 99) 1f else cur.toFloat() / need,
        color = glow,
        segments = 24,
        height = 16.dp,
        label = if (lvl >= 99) "MASTERED" else "$cur / $need XP",
        modifier = Modifier.fillMaxWidth(),
    )
    StatLine("⚙", "Work Efficiency", "+$eff%", colors.vitalityEmerald)
    val toolEff = ToolbeltEngine.workEfficiencyBonus(state, skill)
    if (toolEff > 0) StatLine("🧰", "Belt Bonus", "+$toolEff%", colors.vitalityEmerald)
    StatLine("✦", "Total XP", "%,d".format(xp), colors.textPrimary)
    if (lvl < 99) {
        val nextEffLvl = (lvl + 1..99).first { SkillEngine.workEfficiencyPercent(it) > eff }
        Text8("Next +1% efficiency at Lv.$nextEffLvl", size = 10.sp, color = colors.textMuted)
    }
    if (chests > 0) {
        RuneHeader("SPOILS", color = Color(0xFFB27744))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ItemSocket(56.dp, glow = GoldTrim, qty = chests, onClick = { onNavigate(Inspect.Chest(skill)) }) { PixelArtIcon(chestArt, 38.dp) }
            PixelButton("OPEN CHEST", GoldTrim) { actions.openSkillChest(skill) }
        }
    }
    RuneHeader("GATHERS", color = colors.textMuted)
    val mat = CraftingEngine.materialForSkill(skill)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ItemSocket(50.dp, glow = colors.vitalityEmerald, qty = p.materials[mat] ?: 0, onClick = { onNavigate(Inspect.Material(mat)) }) { Text8(mat.icon, size = 22.sp) }
        Text8(mat.displayName, size = 11.sp)
    }
    val tools = ToolbeltEngine.tools.filter { it.appliesTo(skill) }
    if (tools.isNotEmpty()) {
        RuneHeader("TOOLS", color = colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tools.forEach { tool ->
                ItemSocket(
                    50.dp,
                    glow = if (tool.id in p.equippedToolIds) colors.vitalityEmerald else null,
                    dim = tool.id !in p.ownedToolIds,
                    tag = if (tool.id in p.equippedToolIds) "E" else null,
                    onClick = { onNavigate(Inspect.Tool(tool.id)) },
                ) { Text8(tool.icon, size = 22.sp) }
            }
        }
    }
    val recipes = CraftingEngine.recipes.filter { it.craftSkill == skill }
    if (recipes.isNotEmpty()) {
        RuneHeader("RECIPES", color = colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            recipes.forEach { rcp ->
                ItemSocket(50.dp, glow = colors.accentPrimary, onClick = { onNavigate(Inspect.Brew(rcp.id)) }) { Text8(rcp.icon, size = 22.sp) }
            }
        }
    }
}

// =============================================================================================
// SPELLS
// =============================================================================================

@Composable
private fun SpellInspect(spellId: String, state: GameState, actions: HeroActions, onNavigate: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val spell = GameCatalog.spellById(spellId) ?: return
    val unlocked = spell.id in p.unlockedSpellIds || spell.unlockShardsCost == 0
    val attuned = spell.id in p.attunedSpellIds
    val c = elementColor(spell.element)
    Showcase(c) { PixelArtIcon(spellOrbArt(spell.element, dim = !unlocked), 92.dp) }
    ItemTitle(spell.name, c, "${spell.element.displayName} Sigil · Spell")
    when {
        attuned -> StatusRibbon("★ ATTUNED", c)
        unlocked -> StatusRibbon("INSCRIBED", colors.accentSecondary)
        else -> StatusRibbon("SEALED", colors.textMuted)
    }
    Flavor(spell.description)
    StatLine("✦", "Mana Cost", "${spell.manaCost}", colors.arcaneMana)
    if (spell.basePower > 0) StatLine("✧", "Power", "${spell.basePower}", c)
    if (spell.healAmount > 0) StatLine("❤", "Heal", "+${spell.healAmount}", colors.crimsonHp)
    if (spell.shieldAmount > 0) StatLine("⛨", "Shield", "+${spell.shieldAmount}", colors.accentSecondary)
    if (!unlocked) {
        MiniLabel("UNSEAL COST")
        CostRow(p, shards = spell.unlockShardsCost)
    }
    PixelButton(
        text = when {
            attuned -> "ATTUNED"
            unlocked -> "ATTUNE"
            else -> "✦ UNSEAL"
        },
        color = c,
        enabled = !attuned,
        onClick = { actions.unlockOrAttuneSpell(spell.id) },
    )
    RuneHeader("ATTUNEMENT", color = colors.textMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in 0 until 3) {
            val s = p.attunedSpellIds.getOrNull(i)?.let(GameCatalog::spellById)
            ItemSocket(52.dp, glow = s?.let { elementColor(it.element) }, selected = s?.id == spell.id, onClick = s?.let { { onNavigate(Inspect.Spell(it.id)) } }) {
                if (s != null) PixelArtIcon(spellOrbArt(s.element), 34.dp) else Text8("◇", size = 18.sp, color = colors.textMuted)
            }
        }
    }
}

// =============================================================================================
// RELICS
// =============================================================================================

@Composable
private fun RelicInspect(id: String, state: GameState) {
    val colors = RpgTheme.colors
    val p = state.player
    val col = CollectibleEngine.byId(id) ?: return
    val found = col.id in p.discoveredCollectibleIds
    val done = p.activityCompletions[col.activityId] ?: 0
    Showcase(if (found) GoldTrim else colors.pixelBorder) {
        if (found) Text8(col.icon, size = 64.sp) else Text8("?", size = 64.sp, color = colors.pixelBorderBright, weight = FontWeight.ExtraBold)
    }
    ItemTitle(if (found) col.name else "Unknown Relic", if (found) GoldTrim else colors.textMuted, "Relic · ${col.regionName}")
    if (found) {
        StatusRibbon("★ ENSHRINED", GoldTrim)
        Flavor(col.lore)
    } else {
        StatusRibbon("UNDISCOVERED", colors.textMuted)
        Flavor("Rumours say it surfaces after many labours in ${col.regionName}…")
    }
    StatLine("✚", col.bonusType.displayName, "+${col.bonusValue}%", if (found) colors.vitalityEmerald else colors.textMuted)
    SegmentBar(
        progress = done.toFloat() / col.unlockCompletions.coerceAtLeast(1),
        color = GoldTrim,
        segments = col.unlockCompletions.coerceIn(5, 25),
        height = 14.dp,
        label = "${done.coerceAtMost(col.unlockCompletions)} / ${col.unlockCompletions} CYCLES",
        modifier = Modifier.fillMaxWidth(),
    )
}

// =============================================================================================
// MATERIALS & BREWS
// =============================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaterialInspect(type: MaterialType, state: GameState, onNavigate: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val count = state.player.materials[type] ?: 0
    Showcase(colors.vitalityEmerald) { Text8(type.icon, size = 64.sp) }
    ItemTitle(type.displayName, colors.vitalityEmerald, "Crafting Material")
    StatusRibbon("× $count IN SATCHEL", if (count > 0) colors.vitalityEmerald else colors.textMuted)
    Flavor("Gathered through ${type.sourceHint}.")
    val sources = SkillType.entries.filter { CraftingEngine.materialForSkill(it) == type }
    MiniLabel("GATHERED BY")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        sources.forEach { s -> RuneChip("${s.icon} ${s.displayName} Lv.${state.player.skillLevel(s)}", colors.accentSecondary) { onNavigate(Inspect.Skill(s)) } }
    }
    val uses = CraftingEngine.recipes.filter { type in it.requiredMaterials }
    if (uses.isNotEmpty()) {
        RuneHeader("USED IN", color = colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            uses.forEach { rcp ->
                ItemSocket(50.dp, glow = colors.accentPrimary, onClick = { onNavigate(Inspect.Brew(rcp.id)) }) { Text8(rcp.icon, size = 22.sp) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrewInspect(recipeId: String, state: GameState, actions: HeroActions, onNavigate: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val recipe = CraftingEngine.recipeById(recipeId) ?: return
    val tier = CraftingEngine.qualityTierFor(state, recipe)
    val owned = p.consumables[recipe.id] ?: 0
    val potency = (recipe.basePotency * tier.potencyMultiplier).roundToInt()
    val tierColor = when (tier.ordinal) {
        0 -> Rarity.COMMON.color
        1 -> Rarity.UNCOMMON.color
        2 -> Rarity.RARE.color
        else -> Rarity.LEGENDARY.color
    }
    Showcase(tierColor) { Text8(recipe.icon, size = 64.sp) }
    ItemTitle(recipe.name, tierColor, "${tier.displayName} Quality · ${recipe.craftSkill.displayName}")
    StatusRibbon(if (owned > 0) "× $owned IN POUCH" else "NONE IN POUCH", if (owned > 0) colors.vitalityEmerald else colors.textMuted)
    Flavor(recipe.description)
    StatLine("✦", "Effect", "", tierColor)
    Text8(brewEffectText(recipe, potency), size = 12.sp, color = colors.textPrimary, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    StatLine("⚗", "Potency", "×${"%.2f".format(tier.potencyMultiplier)}", tierColor)
    StatLine("✚", "Craft XP", "+${recipe.rewardCraftXp}", colors.accentSecondary)
    RuneHeader("INGREDIENTS", color = colors.textMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        recipe.requiredMaterials.forEach { (mat, need) ->
            val have = p.materials[mat] ?: 0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ItemSocket(50.dp, glow = if (have >= need) colors.vitalityEmerald else colors.crimsonHp, onClick = { onNavigate(Inspect.Material(mat)) }) {
                    Text8(mat.icon, size = 22.sp)
                }
                Text8("$have/$need", size = 10.sp, color = if (have >= need) colors.vitalityEmerald else colors.crimsonHp)
            }
        }
        if (recipe.goldCost > 0) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ItemSocket(50.dp, glow = GoldTrim) { PixelArtIcon(coinArt, 26.dp) }
                Text8("${recipe.goldCost}g", size = 10.sp, color = if (p.gold >= recipe.goldCost) GoldTrim else colors.crimsonHp)
            }
        }
    }
    val craftable = p.gold >= recipe.goldCost && recipe.requiredMaterials.all { (m, n) -> (p.materials[m] ?: 0) >= n }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PixelButton("⚗ BREW", colors.accentPrimary, enabled = craftable) { actions.craftRecipe(recipe.id) }
        if (owned > 0) PixelButton("USE", colors.vitalityEmerald) { actions.useConsumable(recipe.id) }
    }
}

// =============================================================================================
// CHESTS
// =============================================================================================

@Composable
private fun ChestInspect(skill: SkillType, state: GameState, actions: HeroActions, onNavigate: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val count = p.skillChests[skill] ?: 0
    Showcase(GoldTrim, shake = count > 0) { PixelArtIcon(chestArt, 100.dp) }
    ItemTitle("${skill.displayName} Chest", GoldTrim, "Skill Spoils")
    StatusRibbon(if (count > 0) "× $count SEALED" else "ALL OPENED", if (count > 0) GoldTrim else colors.textMuted)
    Flavor("Earned every 5 cycles of ${skill.displayName}. Every 5th chest you open cradles a beast egg.")
    val toEgg = 5 - (p.totalChestsOpened % 5)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 5) {
            val lit = i < p.totalChestsOpened % 5
            Box(
                Modifier
                    .size(16.dp)
                    .pixelFrame(fill = if (lit) GoldTrim else colors.obsidianBg, edge = if (lit) GoldTrim else colors.pixelBorder, style = FrameStyle.SOCKET, unit = 1.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text8("🥚 in $toEgg", size = 11.sp, color = colors.textMuted)
    }
    PixelButton("OPEN CHEST", GoldTrim, enabled = count > 0) { actions.openSkillChest(skill) }
    Text8("View ${skill.displayName} ›", size = 11.sp, color = colors.accentSecondary, modifier = Modifier.clickable { onNavigate(Inspect.Skill(skill)) }.padding(4.dp))
}

// =============================================================================================
// MUSCLE SIGILS
// =============================================================================================

fun romanNumeral(n: Int): String {
    if (n <= 0) return "0"
    val vals = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
    var x = n
    return buildString { vals.forEach { (v, s) -> while (x >= v) { append(s); x -= v } } }
}

fun muscleProgress(p: PlayerState, group: MuscleGroup): Triple<Int, Double, Float> {
    val xp = p.muscleStrengthXp[group] ?: 0.0
    val rank = p.muscleStrengthRank(group)
    val next = (rank * rank) * 250.0
    val prev = ((rank - 1) * (rank - 1)) * 250.0
    val frac = ((xp - prev) / (next - prev).coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    return Triple(rank, xp, frac)
}

@Composable
fun MuscleSigil(rank: Int, progress: Float, color: Color, size: Dp, lit: Boolean) {
    val colors = RpgTheme.colors
    val spin = if (lit) rememberSpin(9000) else 0f
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val u = floor(2.dp.toPx())
            if (lit) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)), radius = this.size.minDimension / 2)
            drawCircle(InkDark, radius = this.size.minDimension / 2 - 2 * u)
            pixelRing(progress, color, colors.panelElevated, 2 * u)
        }
        Canvas(Modifier.matchParentSize().padding(size * 0.2f).rotate(spin)) {
            // rune diamond
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.minDimension / 2
            val p = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }
            drawPath(p, color.copy(alpha = if (lit) 0.22f else 0.08f))
        }
        Text8(romanNumeral(rank), size = (size.value * 0.24f).sp, color = if (lit) color else colors.textPrimary, weight = FontWeight.ExtraBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuscleInspect(group: MuscleGroup, state: GameState) {
    val colors = RpgTheme.colors
    val p = state.player
    val (rank, xp, frac) = muscleProgress(p, group)
    val weaponCls = GameCatalog.weaponById(p.equippedWeaponId).weaponClass
    val lit = group in weaponCls.scalingMuscles
    val c = if (lit) colors.accentPrimary else colors.accentSecondary
    Showcase(c, height = 160.dp) { MuscleSigil(rank, frac, c, 110.dp, true) }
    ItemTitle("${group.displayName} Sigil", c, "Strength Rank ${romanNumeral(rank)}")
    if (lit) StatusRibbon("⚡ EMPOWERS YOUR WEAPON", colors.accentPrimary)
    Flavor("Every kilogram you lift with your ${group.displayName.lowercase()} is etched into this sigil.")
    val nextKg = rank * rank * 250.0
    SegmentBar(frac, c, Modifier.fillMaxWidth(), segments = 20, height = 16.dp, label = "${xp.roundToInt()} / ${nextKg.roundToInt()} KG")
    StatLine("🥾", "Step Bank Bonus", "+%,d".format((rank - 1) * 10_000), colors.vitalityEmerald)
    MiniLabel("WEAPONS THAT DRAW ON IT")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        WeaponClass.entries.filter { group in it.scalingMuscles }.forEach { cls ->
            RuneChip(cls.displayName, if (cls == weaponCls) colors.accentPrimary else colors.textMuted)
        }
    }
}

// =============================================================================================
// LOADOUTS
// =============================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoadoutInspect(index: Int, state: GameState, actions: HeroActions, onNavigate: (Inspect) -> Unit) {
    val colors = RpgTheme.colors
    val p = state.player
    val name = LOADOUT_NAMES.getOrElse(index) { "Set ${index + 1}" }
    val saved = p.gearSets.firstOrNull { it.slotIndex == index }
    Showcase(GoldTrim, height = 130.dp) { BannerFlag(name.first().toString(), GoldTrim, 64.dp) }
    ItemTitle("${saved?.name ?: name} Loadout", GoldTrim, if (saved != null) "Saved Gear Set" else "Empty Gear Set")
    Flavor(if (saved != null) "Swap your whole kit in a single motion." else "Save your current gear, spells and tools here.")
    if (saved != null) {
        val w = GameCatalog.weaponById(saved.weaponId)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ItemSocket(50.dp, glow = weaponRarity(w).color, onClick = { onNavigate(Inspect.Gear(GearSlot.WEAPON, w.id)) }) { PixelArtIcon(weaponArt(w.weaponClass, weaponRarity(w)), 32.dp) }
            ArmorSlot.entries.forEach { s ->
                val a = saved.armor[s]?.let(GameCatalog::armorById)
                val gs = GearSlot.entries.first { it.armorSlot == s }
                ItemSocket(50.dp, glow = a?.let { armorRarity(it).color }, onClick = a?.let { { onNavigate(Inspect.Gear(gs, it.id)) } }) {
                    if (a != null) PixelArtIcon(armorArt(s, armorRarity(a)), 32.dp) else Text8("·", color = colors.textMuted)
                }
            }
            saved.attunedSpellIds.mapNotNull(GameCatalog::spellById).forEach { s ->
                ItemSocket(50.dp, glow = elementColor(s.element), onClick = { onNavigate(Inspect.Spell(s.id)) }) { PixelArtIcon(spellOrbArt(s.element), 30.dp) }
            }
            saved.equippedToolIds.mapNotNull(ToolbeltEngine::toolById).forEach { t ->
                ItemSocket(50.dp, glow = colors.vitalityEmerald, onClick = { onNavigate(Inspect.Tool(t.id)) }) { Text8(t.icon, size = 20.sp) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (saved != null) PixelButton("EQUIP SET", colors.vitalityEmerald) { actions.equipGearSet(index) }
        PixelButton(if (saved != null) "OVERWRITE" else "SAVE CURRENT", colors.accentSecondary) { actions.saveGearSet(index, name) }
    }
}

/** A hanging swallow-tail pennant with a letter. */
@Composable
fun BannerFlag(letter: String, color: Color, height: Dp, sway: Boolean = true) {
    val rot = if (sway) rememberBob(2.5f, 1800) else 0f
    Box(Modifier.size(width = height * 0.7f, height = height).rotate(rot), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val u = floor(2.dp.toPx())
            drawRect(Color(0xFF8A6A3A), Offset(0f, 0f), Size(w, 2 * u))
            val p = Path().apply { moveTo(u, 2 * u); lineTo(w - u, 2 * u); lineTo(w - u, h); lineTo(w / 2, h * 0.78f); lineTo(u, h); close() }
            drawPath(p, InkDark)
            val p2 = Path().apply { moveTo(3 * u, 3 * u); lineTo(w - 3 * u, 3 * u); lineTo(w - 3 * u, h - 3 * u); lineTo(w / 2, h * 0.78f - 2 * u); lineTo(3 * u, h - 3 * u); close() }
            drawPath(p2, color.mixWith(Color.Black, 0.35f))
            drawRect(color, Offset(3 * u, 3 * u), Size(w - 6 * u, u))
            drawRect(color.copy(alpha = 0.5f), Offset(3 * u, h * 0.62f), Size(w - 6 * u, u))
        }
        Text8(letter, size = (height.value * 0.3f).sp, color = color, weight = FontWeight.ExtraBold, modifier = Modifier.offset(y = (-height * 0.06f)))
    }
}
