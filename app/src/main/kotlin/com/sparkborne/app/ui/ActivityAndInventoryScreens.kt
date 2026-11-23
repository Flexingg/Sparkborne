package com.sparkborne.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.ui.components.RpgActionButton
import com.sparkborne.app.ui.components.RpgCurrencyRow
import com.sparkborne.app.ui.components.RpgPanel
import com.sparkborne.app.ui.components.RpgProgressBar
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.catalog.WorldCatalog
import com.sparkborne.game.engine.ActionBankManager
import com.sparkborne.game.engine.CombatAction
import com.sparkborne.game.engine.CraftingEngine
import com.sparkborne.game.engine.SkillEngine
import com.sparkborne.game.engine.ToolbeltEngine
import com.sparkborne.game.model.ActionBankType
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.MaterialType
import com.sparkborne.game.model.SkillType

/**
 * Dedicated Inventory, Materials, Skill Chests, Crafting & Toolbelt Screen (bottom bar "Inventory").
 */
@Composable
fun InventoryTabScreen(
    state: GameState,
    errorMessage: String?,
    onOpenSkillChest: (SkillType) -> Unit,
    onCraftRecipe: (String) -> Unit,
    onUseConsumable: (String) -> Unit,
    onForgeOrToggleTool: (String) -> Unit,
    onSaveGearSet: (Int, String) -> Unit,
    onEquipGearSet: (Int) -> Unit,
) {
    val colors = RpgTheme.colors
    val player = state.player

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

        // 1. Pouch Summary (Currencies + Step Bank)
        item {
            RpgPanel(borderColor = colors.accentPrimary) {
                Text(
                    "🎒 ADVENTURER'S POUCH",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.accentPrimary,
                )
                RpgCurrencyRow(
                    gold = player.gold,
                    iron = player.ironOre,
                    shards = player.emberShards,
                    showZeros = true,
                    compact = false,
                )
                Text(
                    "🥾 Step Bank: %,d / %,d AP".format(player.savedSteps, player.maxStepBankCapacity()),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = colors.vitalityEmerald,
                )
            }
        }

        // 2. Skill Chests (if any ready)
        if (player.totalSkillChests() > 0) {
            item {
                RpgPanel(borderColor = colors.vitalityEmerald) {
                    Text(
                        "🎁 SKILL CHESTS (${player.totalSkillChests()} READY · EGG EVERY 5TH)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.vitalityEmerald,
                    )
                    player.skillChests.entries.filter { it.value > 0 }.forEach { (skill, count) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${skill.icon} ${skill.displayName} Chest x$count", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            RpgActionButton(
                                text = "OPEN",
                                onClick = { onOpenSkillChest(skill) },
                                containerColor = colors.vitalityEmerald,
                            )
                        }
                    }
                }
            }
        }

        // 3. Materials Satchel
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
                MaterialType.entries.chunked(2).forEach { pair ->
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

        // 4. Consumables & Crafting Workshop
        item {
            Text(
                "COOKING, ALCHEMY & CONSUMABLES",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentPrimary,
                fontFamily = FontFamily.Monospace,
            )
        }

        items(CraftingEngine.recipes, key = { it.id }) { recipe ->
            val ownedQty = player.consumables[recipe.id] ?: 0
            val tier = CraftingEngine.qualityTierFor(state, recipe)
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

        // 5. Toolbelt & Gear Set Presets
        item {
            val presetNames = listOf("Combat", "Skilling", "Boss")
            RpgPanel(borderColor = colors.accentSecondary) {
                Text(
                    "🧰 TOOLBELT (${player.equippedToolIds.size}/${player.maxToolbeltSlots()}) & GEAR SETS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.accentSecondary,
                )
                if (player.equippedToolIds.isNotEmpty()) {
                    player.equippedToolIds.mapNotNull(ToolbeltEngine::toolById).forEach { tool ->
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

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * Compact 2-column grid of all 5 Fitness Action Point Banks (Steps, Push, Pull/Legs,
 * Hydration, Active Burn) showing live balance, skill-grown capacity, and what each fuels.
 */
@Composable
fun FitnessActionBanksGrid(state: GameState) {
    val colors = RpgTheme.colors
    val banks = ActionBankType.entries
    RpgPanel(borderColor = colors.accentSecondary) {
        Text(
            "⚡ FITNESS ACTION POINT BANKS",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.accentSecondary,
        )
        Text(
            "Real workouts & hydration fill each bank. Leveling matching skills expands your bank capacity.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        banks.chunked(2).forEach { rowBanks ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowBanks.forEach { bank ->
                    val bal = ActionBankManager.balanceOf(state.player, bank)
                    val cap = ActionBankManager.capacityOf(state.player, bank)
                    val fillColor = when (bank) {
                        ActionBankType.STEPS -> colors.vitalityEmerald
                        ActionBankType.PUSH_VOLUME -> colors.accentPrimary
                        ActionBankType.PULL_LEGS_VOLUME -> colors.accentSecondary
                        ActionBankType.HYDRATION_ML -> colors.hydrationCyan
                        ActionBankType.CALORIES_BURNT -> colors.crimsonHp
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.panelElevated,
                        border = BorderStroke(1.dp, colors.pixelBorder),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            RpgProgressBar(
                                label = "${bank.icon} ${bank.displayName}",
                                valueText = "%,d/%,d %s".format(bal, cap, bank.unit),
                                progress = if (cap > 0) (bal.toFloat() / cap.toFloat()).coerceIn(0f, 1f) else 0f,
                                fillColor = fillColor,
                            )
                            Text(
                                bank.fuelsDescription,
                                fontSize = 9.sp,
                                color = colors.textMuted,
                                maxLines = 2,
                            )
                        }
                    }
                }
                if (rowBanks.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

