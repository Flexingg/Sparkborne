package com.sparkborne.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.R
import com.sparkborne.app.ui.components.PixelSprite
import com.sparkborne.app.ui.components.RpgActionButton
import com.sparkborne.app.ui.components.RpgPanel
import com.sparkborne.app.ui.components.RpgProgressBar
import com.sparkborne.app.ui.components.RpgStatChip
import com.sparkborne.app.ui.components.monsterDrawableFor
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.CombatStatsCalculator
import com.sparkborne.game.model.ArmorSlot
import com.sparkborne.game.model.GameState
import com.sparkborne.telemetry.model.MuscleGroup
import kotlin.math.roundToInt

private enum class HeroSubTab(val label: String) {
    PAPER_DOLL("Gear"),
    SKILLS("Skills"),
    FORGE("Forge"),
    CRAFT("Craft"),
    SPELLS("Spells"),
}

/**
 * Component 3: Game-style RPG Paper-Doll Hero Equipment Sheet + Skills (1-99) & Skill Chests + Crafting & Consumables.
 */
@Composable
fun HeroTabScreen(
    state: GameState,
    errorMessage: String?,
    onForgeOrEquipWeapon: (String) -> Unit,
    onForgeOrEquipArmor: (String) -> Unit,
    onUnlockOrAttuneSpell: (String) -> Unit,
    onOpenSkillChest: (com.sparkborne.game.model.SkillType) -> Unit = {},
    onForgeOrToggleTool: (String) -> Unit = {},
    onSaveGearSet: (Int, String) -> Unit = { _, _ -> },
    onEquipGearSet: (Int) -> Unit = {},
    onCraftRecipe: (String) -> Unit = {},
    onUseConsumable: (String) -> Unit = {},
) {
    val stats = CombatStatsCalculator.calculate(state)
    val player = state.player
    val colors = RpgTheme.colors
    val equippedWeapon = GameCatalog.weaponById(player.equippedWeaponId)
    val scalingMuscles = equippedWeapon.weaponClass.scalingMuscles.toSet()

    var subTab by remember { mutableStateOf(HeroSubTab.PAPER_DOLL) }

    val idleTransition = rememberInfiniteTransition(label = "hero_sheet_idle")
    val heroBob by idleTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "hero_bob",
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        errorMessage?.let { err ->
            item {
                RpgPanel(borderColor = colors.crimsonHp) {
                    Text(err, color = colors.crimsonHp, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Sub-Tab Selector (Equipment Paper-Doll | Forge | Spellbook)
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HeroSubTab.entries.forEach { tab ->
                    val active = tab == subTab
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) colors.panelElevated else colors.panelSurface,
                        border = BorderStroke(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) colors.accentPrimary else colors.pixelBorder,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { subTab = tab },
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = tab.label.uppercase(),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (active) colors.accentPrimary else colors.textMuted,
                            )
                        }
                    }
                }
            }
        }

        when (subTab) {
            HeroSubTab.PAPER_DOLL -> {
                // 1. RPG Paper-Doll Loadout Stage
                item {
                    RpgPanel(borderColor = colors.accentPrimary) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "SPARKBORNE KNIGHT · LV.${player.level}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.accentPrimary,
                                fontSize = 13.sp,
                            )
                            Text(
                                player.activeBuffs.momentumStance.displayName.uppercase(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.vitalityEmerald,
                            )
                        }

                        // Paper-Doll 3-Column Stage: Left Armor | Center Hero | Right Weapon/Relic/Beast
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(colors.obsidianBg, colors.panelElevated.copy(alpha = 0.7f)),
                                    ),
                                )
                                .border(1.5.dp, colors.pixelBorderBright, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Left Column: Helm, Chest, Legs
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PaperDollSlotTile(
                                        slotLabel = "HELM",
                                        icon = "🪖",
                                        itemName = player.equippedArmor[ArmorSlot.HELM]?.let { GameCatalog.armorById(it)?.name },
                                        accent = colors.accentSecondary,
                                        onClick = { subTab = HeroSubTab.FORGE },
                                    )
                                    PaperDollSlotTile(
                                        slotLabel = "CHEST",
                                        icon = "🛡",
                                        itemName = player.equippedArmor[ArmorSlot.CHEST]?.let { GameCatalog.armorById(it)?.name },
                                        accent = colors.accentSecondary,
                                        onClick = { subTab = HeroSubTab.FORGE },
                                    )
                                    PaperDollSlotTile(
                                        slotLabel = "LEGS",
                                        icon = "🥾",
                                        itemName = player.equippedArmor[ArmorSlot.LEGS]?.let { GameCatalog.armorById(it)?.name },
                                        accent = colors.accentSecondary,
                                        onClick = { subTab = HeroSubTab.FORGE },
                                    )
                                }

                                // Centerpiece: Breathing Hero Pixel Portrait + XP bar
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                ) {
                                    PixelSprite(
                                        drawableRes = R.drawable.pixel_hero_knight,
                                        contentDescription = "Equipped Hero",
                                        size = 110.dp,
                                        borderColor = colors.accentPrimary,
                                        modifier = Modifier.offset { IntOffset(0, heroBob.roundToInt()) },
                                    )
                                    RpgProgressBar(
                                        label = "XP",
                                        valueText = "${player.xp}/${player.xpToNextLevel()}",
                                        progress = player.xp.toFloat() / player.xpToNextLevel().coerceAtLeast(1),
                                        fillColor = colors.accentPrimary,
                                        barHeight = 7.dp,
                                    )
                                }

                                // Right Column: Weapon, Relic, Active Companion
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PaperDollSlotTile(
                                        slotLabel = "WEAPON",
                                        icon = "⚔",
                                        itemName = equippedWeapon.name,
                                        accent = colors.accentPrimary,
                                        onClick = { subTab = HeroSubTab.FORGE },
                                    )
                                    PaperDollSlotTile(
                                        slotLabel = "RELIC",
                                        icon = "💍",
                                        itemName = player.equippedArmor[ArmorSlot.RELIC]?.let { GameCatalog.armorById(it)?.name },
                                        accent = colors.arcaneMana,
                                        onClick = { subTab = HeroSubTab.FORGE },
                                    )
                                    // Companion Bond Slot
                                    val mon = state.activeMonster
                                    Surface(
                                        shape = RoundedCornerShape(7.dp),
                                        color = colors.panelSurface,
                                        border = BorderStroke(1.5.dp, colors.vitalityEmerald),
                                        modifier = Modifier.size(width = 76.dp, height = 56.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            if (mon != null) {
                                                PixelSprite(
                                                    drawableRes = monsterDrawableFor(mon.affinity),
                                                    contentDescription = mon.name,
                                                    size = 28.dp,
                                                    borderColor = colors.vitalityEmerald,
                                                )
                                                Text(
                                                    text = mon.name,
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = colors.vitalityEmerald,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            } else {
                                                Text("NO PET", fontSize = 8.sp, color = colors.textMuted)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Attuned Spell Crystal Row (3 slots)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            for (slotIdx in 0 until 3) {
                                val spellId = player.attunedSpellIds.getOrNull(slotIdx)
                                val spell = spellId?.let(GameCatalog::spellById)
                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = colors.obsidianBg,
                                    border = BorderStroke(
                                        1.dp,
                                        if (spell != null) colors.arcaneMana else colors.pixelBorder,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { subTab = HeroSubTab.SPELLS },
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = if (spell != null) "✨ ${spell.name}" else "◇ Empty Spell",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = if (spell != null) colors.arcaneMana else colors.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (spell != null) "${spell.manaCost} Mana" else "Tap to attune",
                                            fontSize = 8.sp,
                                            color = colors.textMuted,
                                        )
                                    }
                                }
                            }
                        }

                        // Combat Power Stats Row
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RpgStatChip("HP", "${stats.maxHp}", colors.crimsonHp, Modifier.weight(1f))
                            RpgStatChip("MANA", "${stats.maxMana}", colors.arcaneMana, Modifier.weight(1f))
                            RpgStatChip("ATK", "${stats.weaponDamage}", colors.accentPrimary, Modifier.weight(1f))
                            RpgStatChip("SPL", "${stats.spellPower}", colors.arcaneMana, Modifier.weight(1f))
                            RpgStatChip("DEF", "${stats.defense}", colors.accentSecondary, Modifier.weight(1f))
                        }

                        // Active Daily Telemetry Buffs Pill Row
                        val b = player.activeBuffs
                        Text(
                            text = "TODAY'S BUFFS · Sleep +${b.sleepVitalityHpBonus}HP · Water +${b.hydrationClarityManaBonus}MP · Prot +${b.proteinFortitudeDefenseBonus}DEF · Carbs +${b.carbsSpellPowerBonus}SPL",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textMuted,
                        )

                        // WalkScape Step Bank Capacity Bar (scales to millions with Level + Muscle Ranks!)
                        val bankCap = player.maxStepBankCapacity()
                        RpgProgressBar(
                            label = "🥾 Step Bank (Scales w/ Lv + Muscle Ranks)",
                            valueText = "%,d / %,d".format(player.savedSteps, bankCap),
                            progress = player.savedSteps.toFloat() / bankCap.coerceAtLeast(1),
                            fillColor = colors.vitalityEmerald,
                            barHeight = 8.dp,
                        )
                    }
                }

                // 2. Visual 2-Column Muscle-Group Strength Matrix (Highlighting Equipped Weapon Scaling!)
                item {
                    RpgPanel {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "MUSCLE STRENGTH MATRIX",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.accentPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "⚡ ${equippedWeapon.weaponClass.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = colors.vitalityEmerald,
                            )
                        }
                        Text(
                            "Highlighted muscles actively scale your equipped ${equippedWeapon.name}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )

                        val tracked = MuscleGroup.entries.filter { it != MuscleGroup.OTHER }
                        val rows = tracked.chunked(2)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rows.forEach { pair ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    pair.forEach { group ->
                                        val isScaling = group in scalingMuscles
                                        val xp = player.muscleStrengthXp[group] ?: 0.0
                                        val rank = player.muscleStrengthRank(group)
                                        val nextVol = (rank * rank) * 250.0
                                        val prevVol = ((rank - 1) * (rank - 1)) * 250.0
                                        val frac = ((xp - prevVol) / (nextVol - prevVol).coerceAtLeast(1.0)).toFloat()

                                        Surface(
                                            shape = RoundedCornerShape(7.dp),
                                            color = if (isScaling) colors.panelElevated else colors.obsidianBg,
                                            border = BorderStroke(
                                                width = if (isScaling) 1.5.dp else 1.dp,
                                                color = if (isScaling) colors.accentPrimary else colors.pixelBorder,
                                            ),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Text(
                                                        text = (if (isScaling) "⚡" else "") + group.displayName,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isScaling) colors.accentPrimary else colors.textPrimary,
                                                        maxLines = 1,
                                                    )
                                                    Text(
                                                        text = "R$rank",
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (isScaling) colors.vitalityEmerald else colors.textMuted,
                                                    )
                                                }
                                                RpgProgressBar(
                                                    label = "${xp.roundToInt()} kg",
                                                    valueText = "${(frac * 100).roundToInt()}%",
                                                    progress = frac,
                                                    fillColor = if (isScaling) colors.accentPrimary else colors.ironSteel,
                                                    barHeight = 6.dp,
                                                )
                                            }
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Toolbelt Slots & 1-Tap Gear Set Presets
                item {
                    val maxTools = player.maxToolbeltSlots()
                    RpgPanel(borderColor = colors.accentSecondary) {
                        Text(
                            "🧰 TOOLBELT (${player.equippedToolIds.size} / $maxTools SLOTS)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accentSecondary,
                        )
                        if (player.equippedToolIds.isEmpty()) {
                            Text(
                                "No tools equipped yet. Forge Skilling Tools in Hero → Forge for +Work Efficiency & +Bonus Yield! (3rd slot at Lv.5, 4th at Lv.10)",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        } else {
                            player.equippedToolIds.mapNotNull(com.sparkborne.game.engine.ToolbeltEngine::toolById).forEach { tool ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("${tool.icon} ${tool.name} (+${tool.workEfficiencyBonusPercent}% Eff)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    RpgActionButton(
                                        text = "REMOVE",
                                        onClick = { onForgeOrToggleTool(tool.id) },
                                        containerColor = colors.ironSteel,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    val presetNames = listOf("Combat", "Skilling", "Boss")
                    RpgPanel(borderColor = colors.accentPrimary) {
                        Text(
                            "🎒 GEAR SET PRESETS (1-TAP SWAP)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accentPrimary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            presetNames.forEachIndexed { idx, defaultName ->
                                val saved = player.gearSets.firstOrNull { it.slotIndex == idx }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.panelElevated,
                                    border = BorderStroke(1.dp, if (saved != null) colors.accentPrimary else colors.pixelBorder),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            saved?.name ?: defaultName,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (saved != null) colors.accentPrimary else colors.textMuted,
                                            maxLines = 1,
                                        )
                                        if (saved != null) {
                                            RpgActionButton(
                                                text = "EQUIP",
                                                onClick = { onEquipGearSet(idx) },
                                                containerColor = colors.vitalityEmerald,
                                            )
                                        }
                                        RpgActionButton(
                                            text = "SAVE",
                                            onClick = { onSaveGearSet(idx, defaultName) },
                                            containerColor = colors.accentSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HeroSubTab.FORGE -> {
                item {
                    Text("SKILLING TOOLS (TOOLBELT)", style = MaterialTheme.typography.labelLarge, color = colors.vitalityEmerald, fontFamily = FontFamily.Monospace)
                }
                items(com.sparkborne.game.engine.ToolbeltEngine.tools, key = { it.id }) { tool ->
                    val owned = tool.id in player.ownedToolIds
                    val equipped = tool.id in player.equippedToolIds
                    RpgPanel(borderColor = if (equipped) colors.vitalityEmerald else colors.pixelBorder) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${tool.icon} ${tool.name}", fontWeight = FontWeight.Bold)
                                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                if (!owned) {
                                    com.sparkborne.app.ui.components.RpgCurrencyRow(
                                        gold = tool.forgeGoldCost,
                                        iron = tool.forgeIronCost,
                                        shards = tool.forgeShardsCost,
                                        compact = true,
                                    )
                                }
                            }
                            RpgActionButton(
                                text = when {
                                    equipped -> "UNEQUIP"
                                    owned -> "EQUIP"
                                    else -> "FORGE"
                                },
                                onClick = { onForgeOrToggleTool(tool.id) },
                                containerColor = if (equipped) colors.ironSteel else colors.vitalityEmerald,
                            )
                        }
                    }
                }

                item {
                    Text("WEAPONS", style = MaterialTheme.typography.labelLarge, color = colors.accentPrimary, fontFamily = FontFamily.Monospace)
                }
                items(GameCatalog.weapons, key = { it.id }) { weapon ->
                    val owned = weapon.id in player.ownedWeaponIds
                    val equipped = player.equippedWeaponId == weapon.id
                    RpgPanel(borderColor = if (equipped) colors.accentPrimary else colors.pixelBorder) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(weapon.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${weapon.weaponClass.displayName} · Base ${weapon.baseDamage} · ${weapon.weaponClass.scalingMuscles.joinToString { it.displayName }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                                if (!owned && (weapon.forgeGoldCost > 0 || weapon.forgeIronCost > 0)) {
                                    com.sparkborne.app.ui.components.RpgCurrencyRow(
                                        gold = weapon.forgeGoldCost,
                                        iron = weapon.forgeIronCost,
                                        compact = true,
                                    )
                                }
                            }
                            RpgActionButton(
                                text = when {
                                    equipped -> "EQUIPPED"
                                    owned -> "EQUIP"
                                    else -> "FORGE"
                                },
                                onClick = { onForgeOrEquipWeapon(weapon.id) },
                                enabled = !equipped,
                            )
                        }
                    }
                }

                item {
                    Text("ARMOR FORGE", style = MaterialTheme.typography.labelLarge, color = colors.accentSecondary, fontFamily = FontFamily.Monospace)
                }
                items(GameCatalog.armor, key = { it.id }) { armor ->
                    val owned = armor.id in player.ownedArmorIds
                    val equipped = player.equippedArmor[armor.slot] == armor.id
                    RpgPanel(borderColor = if (equipped) colors.accentSecondary else colors.pixelBorder) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${armor.name} (${armor.slot.displayName})", fontWeight = FontWeight.Bold)
                                Text(
                                    "DEF +${armor.defense} · HP +${armor.bonusHp} · Mana +${armor.bonusMana}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                                if (!owned && (armor.forgeGoldCost > 0 || armor.forgeIronCost > 0 || armor.forgeShardsCost > 0)) {
                                    com.sparkborne.app.ui.components.RpgCurrencyRow(
                                        gold = armor.forgeGoldCost,
                                        iron = armor.forgeIronCost,
                                        shards = armor.forgeShardsCost,
                                        compact = true,
                                    )
                                }
                            }
                            RpgActionButton(
                                text = when {
                                    equipped -> "EQUIPPED"
                                    owned -> "EQUIP"
                                    else -> "FORGE"
                                },
                                onClick = { onForgeOrEquipArmor(armor.id) },
                                enabled = !equipped,
                                containerColor = colors.accentSecondary,
                            )
                        }
                    }
                }
            }

            HeroSubTab.SKILLS -> {
                item {
                    val totalChests = player.totalSkillChests()
                    RpgPanel(borderColor = colors.accentPrimary) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    "TOTAL SKILL LEVEL · ${player.totalSkillLevel()} / ${com.sparkborne.game.model.SkillType.entries.size * 99}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.accentPrimary,
                                )
                                Text(
                                    "Every 2 Skill Levels grants +1% Work Efficiency (fewer steps per cycle). Every 5 cycles drops a Skill Chest!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                        }
                        if (totalChests > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "🎁 SKILL CHESTS READY ($totalChests) · Opened: ${player.totalChestsOpened} (Egg every 5th!)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.vitalityEmerald,
                            )
                            player.skillChests.entries.filter { it.value > 0 }.forEach { (skill, count) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${skill.icon} ${skill.displayName} Chest x$count",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                    )
                                    RpgActionButton(
                                        text = "OPEN CHEST",
                                        onClick = { onOpenSkillChest(skill) },
                                        containerColor = colors.vitalityEmerald,
                                    )
                                }
                            }
                        }
                    }
                }

                items(com.sparkborne.game.model.SkillType.entries, key = { it.name }) { skill ->
                    val xp = player.skillXp[skill] ?: 0
                    val lvl = com.sparkborne.game.engine.SkillEngine.levelForXp(xp)
                    val eff = com.sparkborne.game.engine.SkillEngine.workEfficiencyPercent(lvl)
                    val (curXp, needXp) = com.sparkborne.game.engine.SkillEngine.xpToNextLevel(xp)
                    val chests = player.skillChests[skill] ?: 0
                    RpgPanel(borderColor = if (lvl > 1) colors.accentSecondary else colors.pixelBorder) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${skill.icon} ${skill.displayName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = colors.textPrimary,
                            )
                            Text(
                                "LV.$lvl/99 · +$eff% Eff" + if (chests > 0) " · 🎁$chests" else "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (lvl > 1) colors.accentPrimary else colors.textMuted,
                            )
                        }
                        Text(skill.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        RpgProgressBar(
                            label = if (lvl >= 99) "MAX LEVEL" else "XP",
                            valueText = if (lvl >= 99) "99/99" else "$curXp / $needXp",
                            progress = (curXp.toFloat() / needXp.toFloat()).coerceIn(0f, 1f),
                            fillColor = colors.accentSecondary,
                        )
                    }
                }

                item {
                    val discoveredCount = player.discoveredCollectibleIds.size
                    val totalCount = com.sparkborne.game.engine.CollectibleEngine.catalog.size
                    Spacer(Modifier.height(6.dp))
                    RpgPanel(borderColor = colors.accentPrimary) {
                        Text(
                            "🏆 COLLECTION LOG (MUSEUM) · $discoveredCount / $totalCount",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accentPrimary,
                        )
                        val activeBonuses = com.sparkborne.game.model.CollectibleBonusType.entries
                            .mapNotNull { t ->
                                val v = com.sparkborne.game.engine.CollectibleEngine.totalBonus(state, t)
                                if (v > 0) "+$v% ${t.displayName}" else null
                            }
                        Text(
                            if (activeBonuses.isEmpty()) {
                                "Complete Town Activity milestones to uncover rare permanent account relics!"
                            } else {
                                "Active Passives: ${activeBonuses.joinToString(" · ")}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (activeBonuses.isEmpty()) colors.textMuted else colors.vitalityEmerald,
                        )
                    }
                }

                items(com.sparkborne.game.engine.CollectibleEngine.catalog, key = { it.id }) { col ->
                    val found = col.id in player.discoveredCollectibleIds
                    val done = player.activityCompletions[col.activityId] ?: 0
                    RpgPanel(borderColor = if (found) colors.accentPrimary else colors.pixelBorder) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (found) "${col.icon} ${col.name}" else "❓ ??? (${col.regionName})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (found) colors.accentPrimary else colors.textMuted,
                                )
                                Text(
                                    if (found) col.lore else "Progress: $done / ${col.unlockCompletions} cycles in ${col.regionName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                            Text(
                                "+${col.bonusValue}% ${col.bonusType.shortLabel.removePrefix("+% ")}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (found) colors.vitalityEmerald else colors.textMuted,
                            )
                        }
                    }
                }
            }

            HeroSubTab.CRAFT -> {
                item {
                    RpgPanel(borderColor = colors.accentSecondary) {
                        Text(
                            "🧺 MATERIALS SATCHEL",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accentSecondary,
                        )
                        if (player.activeStepBoostCharges > 0) {
                            Text(
                                "⚡ Trailblazer Brew Active: +25% Work Efficiency (${player.activeStepBoostCharges} cycles left)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.vitalityEmerald,
                            )
                        }
                        com.sparkborne.game.model.MaterialType.entries.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                pair.forEach { mat ->
                                    val count = player.materials[mat] ?: 0
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.panelElevated,
                                        border = BorderStroke(1.dp, if (count > 0) colors.accentSecondary else colors.pixelBorder),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text("${mat.icon} ${mat.displayName.substringBefore(" ")}", fontSize = 11.sp, color = colors.textPrimary)
                                            Text(
                                                "x$count",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (count > 0) colors.accentPrimary else colors.textMuted,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        "COOKING & ALCHEMY WORKSHOP",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accentPrimary,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                items(com.sparkborne.game.engine.CraftingEngine.recipes, key = { it.id }) { recipe ->
                    val ownedQty = player.consumables[recipe.id] ?: 0
                    val tier = com.sparkborne.game.engine.CraftingEngine.qualityTierFor(state, recipe)
                    val reqText = recipe.requiredMaterials.entries.joinToString(" · ") { (m, qty) ->
                        val have = player.materials[m] ?: 0
                        "${m.icon}$have/$qty"
                    } + if (recipe.goldCost > 0) " · ${recipe.goldCost}g" else ""

                    RpgPanel(borderColor = if (ownedQty > 0) colors.vitalityEmerald else colors.pixelBorder) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    "${recipe.icon} ${recipe.name}" + if (ownedQty > 0) " (x$ownedQty)" else "",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    "${tier.displayName} Quality (${recipe.craftSkill.displayName} Lv.${player.skillLevel(recipe.craftSkill)})",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = colors.accentPrimary,
                                )
                                Text(recipe.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                Text(reqText, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = colors.accentSecondary)
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                RpgActionButton(
                                    text = "CRAFT",
                                    onClick = { onCraftRecipe(recipe.id) },
                                    containerColor = colors.accentPrimary,
                                )
                                if (ownedQty > 0) {
                                    RpgActionButton(
                                        text = "USE",
                                        onClick = { onUseConsumable(recipe.id) },
                                        containerColor = colors.vitalityEmerald,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HeroSubTab.SPELLS -> {
                item {
                    Text("SPELLBOOK (ATTUNE UP TO 3)", style = MaterialTheme.typography.labelLarge, color = colors.arcaneMana, fontFamily = FontFamily.Monospace)
                }
                items(GameCatalog.spells, key = { it.id }) { spell ->
                    val unlocked = spell.id in player.unlockedSpellIds
                    val attuned = spell.id in player.attunedSpellIds
                    RpgPanel(borderColor = if (attuned) colors.arcaneMana else colors.pixelBorder) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("✨ ${spell.name} (${spell.manaCost} Mana)", fontWeight = FontWeight.Bold)
                                Text(spell.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                if (!unlocked && spell.unlockShardsCost > 0) {
                                    com.sparkborne.app.ui.components.RpgCurrencyRow(
                                        shards = spell.unlockShardsCost,
                                        compact = true,
                                    )
                                }
                            }
                            RpgActionButton(
                                text = when {
                                    attuned -> "ATTUNED"
                                    unlocked -> "ATTUNE"
                                    else -> "UNLOCK"
                                },
                                onClick = { onUnlockOrAttuneSpell(spell.id) },
                                enabled = !attuned,
                                containerColor = colors.arcaneMana,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PaperDollSlotTile(
    slotLabel: String,
    icon: String,
    itemName: String?,
    accent: Color,
    onClick: () -> Unit,
) {
    val colors = RpgTheme.colors
    val filled = itemName != null
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = if (filled) colors.panelSurface else colors.obsidianBg,
        border = BorderStroke(
            width = if (filled) 1.5.dp else 1.dp,
            color = if (filled) accent else colors.pixelBorder,
        ),
        modifier = Modifier
            .size(width = 76.dp, height = 56.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(icon, fontSize = 15.sp)
            Text(
                text = itemName?.substringBefore(" ") ?: slotLabel,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (filled) FontWeight.Bold else FontWeight.Normal,
                color = if (filled) accent else colors.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
