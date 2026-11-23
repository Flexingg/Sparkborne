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
import com.sparkborne.game.engine.CombatAction
import com.sparkborne.game.engine.CraftingEngine
import com.sparkborne.game.engine.SkillEngine
import com.sparkborne.game.engine.ToolbeltEngine
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.MaterialType
import com.sparkborne.game.model.SkillType

/**
 * Location-scoped Activity Hub (bottom bar "Activity"):
 * - Shows active Delve combat/nodes if currently on a delve
 * - Shows active Travel (with 1-tap Spend Banked Steps)
 * - Shows Town Activities available at your CURRENT location only
 * - Shows Local Delves/Quests available at your CURRENT location only
 */
@Composable
fun ActivityTabScreen(
    state: GameState,
    errorMessage: String?,
    onStartActivity: (String) -> Unit,
    onStopActivity: () -> Unit,
    onCancelTravel: () -> Unit,
    onSpendBankedStepsOnTravel: () -> Unit,
    onSpendBankedStepsOnActivity: (Int?) -> Unit,
    onStartContract: (String) -> Unit,
    onEnterNode: () -> Unit,
    onCombatAction: (CombatAction) -> Unit,
    onAbandonDelve: () -> Unit,
    onClearFinishedDelve: () -> Unit,
    onSpendBankedStepsOnDelve: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val colors = RpgTheme.colors
    val currentTown = WorldCatalog.locationById(state.currentLocationId)
    val bankedSteps = state.player.savedSteps

    // If an Expedition/Delve is currently active (or just finished), show the full Battle/Delve screen!
    if (state.activeExpedition != null) {
        DelveTabScreen(
            state = state,
            errorMessage = errorMessage,
            onStartContract = onStartContract,
            onEnterNode = onEnterNode,
            onCombatAction = onCombatAction,
            onAbandon = onAbandonDelve,
            onClearFinished = onClearFinishedDelve,
            onSpendBankedStepsOnDelve = onSpendBankedStepsOnDelve,
        )
        return
    }

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

        // Current Location Header + Banked Steps AP
        item {
            RpgPanel(borderColor = colors.accentPrimary) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            currentTown.name.uppercase(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accentPrimary,
                        )
                        Text(currentTown.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    RpgActionButton(
                        text = "MAP",
                        onClick = onOpenMap,
                        containerColor = colors.accentSecondary,
                    )
                }
                Text(
                    "🥾 Step Bank: %,d / %,d AP ready to spend".format(bankedSteps, state.player.maxStepBankCapacity()),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.vitalityEmerald,
                )
            }
        }

        // Active Travel Card (if currently on a road)
        state.activeTravel?.let { travel ->
            val dest = WorldCatalog.locationById(travel.toLocationId)
            item {
                RpgPanel(borderColor = colors.accentPrimary) {
                    Text(
                        "🧭 TRAVELING TO ${dest.name.uppercase()}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.accentPrimary,
                    )
                    RpgProgressBar(
                        label = "Road Progress",
                        valueText = "${travel.stepsWalked} / ${travel.stepsRequired}",
                        progress = travel.progressFraction,
                        fillColor = colors.accentPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (bankedSteps > 0) {
                            RpgActionButton(
                                text = "SPEND STEPS",
                                onClick = onSpendBankedStepsOnTravel,
                                containerColor = colors.vitalityEmerald,
                            )
                        }
                        RpgActionButton(
                            text = "CANCEL",
                            onClick = onCancelTravel,
                            containerColor = colors.ironSteel,
                        )
                    }
                }
            }
        }

        // Town Activities at Current Location
        item {
            Text(
                "LOCAL ACTIVITIES · ${currentTown.name.uppercase()}",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentSecondary,
                fontFamily = FontFamily.Monospace,
            )
        }

        items(currentTown.activities, key = { it.id }) { act ->
            val isRunning = state.activeActivity?.activityId == act.id
            val skill = SkillType.fromTag(act.skillTag)
            val effSteps = SkillEngine.effectiveStepsPerAction(act.stepsPerAction, state, skill)
            val mat = CraftingEngine.materialForSkill(skill)

            RpgPanel(borderColor = if (isRunning) colors.vitalityEmerald else colors.pixelBorder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "${skill.icon} ${act.name} (${skill.displayName} Lv.${state.player.skillLevel(skill)})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Text(act.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        Text(
                            "Cost: %,d steps/cycle · Drops: ${mat.icon} ${mat.displayName} +${act.rewardXp}XP".format(effSteps),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = colors.accentSecondary,
                        )
                    }
                }

                if (isRunning) {
                    val active = state.activeActivity!!
                    RpgProgressBar(
                        label = "Cycle #${active.completionsCount + 1}",
                        valueText = "${active.stepsProgress} / $effSteps",
                        progress = (active.stepsProgress.toFloat() / effSteps.coerceAtLeast(1)).coerceIn(0f, 1f),
                        fillColor = colors.vitalityEmerald,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (bankedSteps > 0) {
                            RpgActionButton(
                                text = "SPEND 1x",
                                onClick = { onSpendBankedStepsOnActivity(1) },
                                containerColor = colors.vitalityEmerald,
                            )
                            RpgActionButton(
                                text = "SPEND ALL",
                                onClick = { onSpendBankedStepsOnActivity(null) },
                                containerColor = colors.accentPrimary,
                            )
                        }
                        RpgActionButton(
                            text = "STOP",
                            onClick = onStopActivity,
                            containerColor = colors.ironSteel,
                        )
                    }
                } else {
                    RpgActionButton(
                        text = "START ACTIVITY",
                        onClick = { onStartActivity(act.id) },
                        containerColor = colors.accentSecondary,
                    )
                }
            }
        }

        // Local Delves Available ONLY at Current Location
        item {
            Text(
                "LOCAL DELVES HERE (${currentTown.contractIds.size})",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentPrimary,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "Only delves tied to ${currentTown.name} appear here. Travel on the Map to unlock delves in other regions.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }

        val localContracts = currentTown.contractIds.mapNotNull(GameCatalog::contractById)
        items(localContracts, key = { it.id }) { contract ->
            val clearedBefore = contract.id in state.completedContractIds
            RpgPanel(borderColor = if (clearedBefore) colors.vitalityEmerald else colors.accentPrimary) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "⚔ ${contract.title}" + if (clearedBefore) " ✓" else "",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = colors.accentPrimary,
                        )
                        Text(contract.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        Text(
                            "Boss: ${contract.bossName} · ${contract.totalNodes} Nodes · ${contract.durationDays}d",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = colors.accentSecondary,
                        )
                    }
                    RpgActionButton(
                        text = "BEGIN",
                        onClick = { onStartContract(contract.id) },
                        containerColor = colors.accentPrimary,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

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
