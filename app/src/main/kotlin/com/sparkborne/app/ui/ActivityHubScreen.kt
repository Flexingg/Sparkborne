package com.sparkborne.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sparkborne.app.R
import com.sparkborne.app.ui.components.RpgActionButton
import com.sparkborne.app.ui.components.RpgPanel
import com.sparkborne.app.ui.components.RpgProgressBar
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.catalog.WorldCatalog
import com.sparkborne.game.engine.ActionBankManager
import com.sparkborne.game.engine.CombatAction
import com.sparkborne.game.engine.CraftingEngine
import com.sparkborne.game.engine.SkillEngine
import com.sparkborne.game.model.ActionBankType
import com.sparkborne.game.model.ExpeditionContract
import com.sparkborne.game.model.ExpeditionDifficulty
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.RegionElement
import com.sparkborne.game.model.SkillType
import com.sparkborne.game.model.TownActivity
import com.sparkborne.game.model.WorldLocation

private enum class ActivityHubSection {
    TASKS,
    DELVES,
}

/**
 * Overhauled, game-first Activity Hub (bottom bar "Activity"):
 * - Illustrated Town Banner with compact clickable Fitness Action Bank pills (full details in modal)
 * - Focused "Current Action" Spotlight card (Travel, Active Task, or Campfire Idle)
 * - Clean 2-tab switcher separating Town Tasks (2-col RPG tile grid) and Local Delves (illustrated posters)
 * - Tap any Bank, Task, or Delve to open its dedicated RPG Detail Modal
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
    val localContracts = remember(currentTown.id) {
        currentTown.contractIds.mapNotNull(GameCatalog::contractById)
    }

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

    var selectedSection by remember { mutableStateOf(ActivityHubSection.TASKS) }
    var showBanksDialog by remember { mutableStateOf(false) }
    var inspectedActivityId by remember { mutableStateOf<String?>(null) }
    var inspectedContractId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(2.dp)) }

        errorMessage?.let { err ->
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.crimsonHp.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, colors.crimsonHp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        err,
                        color = colors.crimsonHp,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }

        // 1. Illustrated Town Stage Banner + Compact Clickable Action Bank Strip
        item {
            TownStageBanner(
                town = currentTown,
                state = state,
                onOpenMap = onOpenMap,
                onOpenBanks = { showBanksDialog = true },
            )
        }

        // 2. Focused Current Action Spotlight (Travel / Active Task / Idle)
        item {
            CurrentActionSpotlight(
                state = state,
                onSpendBankedStepsOnTravel = onSpendBankedStepsOnTravel,
                onCancelTravel = onCancelTravel,
                onSpendBankedStepsOnActivity = onSpendBankedStepsOnActivity,
                onStopActivity = onStopActivity,
                onInspectActivity = { inspectedActivityId = it },
            )
        }

        // 3. Clean 2-Tab Mode Switcher (Tasks vs Local Delves)
        item {
            ActivityHubModeBar(
                selected = selectedSection,
                tasksCount = currentTown.activities.size,
                delvesCount = localContracts.size,
                onSelect = { selectedSection = it },
            )
        }

        // 4. Focused Section Content
        when (selectedSection) {
            ActivityHubSection.TASKS -> {
                val rows = currentTown.activities.chunked(2)
                items(rows) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        pair.forEach { act ->
                            ActivityTileCard(
                                state = state,
                                activity = act,
                                isRunning = state.activeActivity?.activityId == act.id,
                                modifier = Modifier.weight(1f),
                                onClick = { inspectedActivityId = act.id },
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            ActivityHubSection.DELVES -> {
                if (localContracts.isEmpty()) {
                    item {
                        RpgPanel(borderColor = colors.pixelBorder) {
                            Text(
                                "No delves are anchored to ${currentTown.name}. Open the World Map to travel to a frontier stronghold.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                } else {
                    items(localContracts, key = { it.id }) { contract ->
                        DelvePosterCard(
                            contract = contract,
                            clearedBefore = contract.id in state.completedContractIds,
                            onClick = { inspectedContractId = contract.id },
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // --- Detail Modals (keeps main hub clean & breathable) ---
    if (showBanksDialog) {
        ActionBanksDetailDialog(
            state = state,
            onDismiss = { showBanksDialog = false },
        )
    }

    inspectedActivityId?.let { actId ->
        val act = WorldCatalog.activityById(actId)
        if (act != null) {
            ActivityDetailDialog(
                state = state,
                activity = act,
                onDismiss = { inspectedActivityId = null },
                onStart = {
                    onStartActivity(act.id)
                    inspectedActivityId = null
                },
                onStop = {
                    onStopActivity()
                    inspectedActivityId = null
                },
                onSpend = { cycles ->
                    onSpendBankedStepsOnActivity(cycles)
                },
            )
        }
    }

    inspectedContractId?.let { cid ->
        val contract = GameCatalog.contractById(cid)
        if (contract != null) {
            DelveBriefingDialog(
                state = state,
                contract = contract,
                clearedBefore = contract.id in state.completedContractIds,
                onDismiss = { inspectedContractId = null },
                onBegin = {
                    onStartContract(contract.id)
                    inspectedContractId = null
                },
            )
        }
    }
}

@Composable
private fun TownStageBanner(
    town: WorldLocation,
    state: GameState,
    onOpenMap: () -> Unit,
    onOpenBanks: () -> Unit,
) {
    val colors = RpgTheme.colors
    val backdropRes = backdropForElement(town.element)

    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, colors.accentPrimary.copy(alpha = 0.7f)),
        color = colors.panelSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(backdropRes),
                contentDescription = town.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize(),
            )
            // Rich atmospheric scrim for crisp readability
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xAA0C0F14),
                                Color(0xCC0C0F14),
                                Color(0xF20C0F14),
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${town.element.displayName.uppercase()} REGION",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentSecondary,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            town.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            town.subtitle,
                            fontSize = 11.sp,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.obsidianBg.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, colors.accentSecondary.copy(alpha = 0.7f)),
                        modifier = Modifier.clickable(onClick = onOpenMap),
                    ) {
                        Text(
                            "🗺 MAP",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accentSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }

                // Compact 5-Bank Strip (tap any pill for full Fitness Action Banks modal)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.obsidianBg.copy(alpha = 0.72f))
                        .clickable(onClick = onOpenBanks)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionBankType.entries.forEach { bank ->
                        val bal = ActionBankManager.balanceOf(state.player, bank)
                        val cap = ActionBankManager.capacityOf(state.player, bank).coerceAtLeast(1)
                        val frac = (bal.toFloat() / cap.toFloat()).coerceIn(0f, 1f)
                        val tint = bankAccentColor(bank)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                "${bank.icon} ${compactNumber(bal)}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (bal > 0) colors.textPrimary else colors.textMuted,
                                maxLines = 1,
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(colors.pixelBorder),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(frac)
                                        .height(3.dp)
                                        .background(tint),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentActionSpotlight(
    state: GameState,
    onSpendBankedStepsOnTravel: () -> Unit,
    onCancelTravel: () -> Unit,
    onSpendBankedStepsOnActivity: (Int?) -> Unit,
    onStopActivity: () -> Unit,
    onInspectActivity: (String) -> Unit,
) {
    val colors = RpgTheme.colors
    val travel = state.activeTravel
    val active = state.activeActivity
    val activeDef = active?.let { WorldCatalog.activityById(it.activityId) }

    when {
        travel != null -> {
            val dest = WorldCatalog.locationById(travel.toLocationId)
            val bankedSteps = state.player.savedSteps
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.panelSurface,
                border = BorderStroke(1.5.dp, colors.accentPrimary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🧭 ON THE ROAD",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentPrimary,
                            )
                            Text(
                                "Traveling to ${dest.name}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = colors.textPrimary,
                            )
                        }
                        Text(
                            "✕",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textMuted,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onCancelTravel)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    RpgProgressBar(
                        label = "Distance",
                        valueText = "%,d / %,d steps".format(travel.stepsWalked, travel.stepsRequired),
                        progress = travel.progressFraction,
                        fillColor = colors.accentPrimary,
                    )
                    if (bankedSteps > 0) {
                        RpgActionButton(
                            text = "🥾 SPEND BANKED STEPS",
                            onClick = onSpendBankedStepsOnTravel,
                            containerColor = colors.vitalityEmerald,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        active != null && activeDef != null -> {
            val skill = SkillType.fromTag(activeDef.skillTag)
            val (bankType, costPerCycle) = ActionBankManager.costPerCycle(state, activeDef)
            val bankAvail = ActionBankManager.balanceOf(state.player, bankType)
            val tint = bankAccentColor(bankType)

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.panelSurface,
                border = BorderStroke(1.5.dp, colors.vitalityEmerald),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onInspectActivity(activeDef.id) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.panelElevated),
                            ) {
                                Text(skill.icon, fontSize = 20.sp)
                            }
                            Column {
                                Text(
                                    "ACTIVE · ${skill.displayName.uppercase()} LV.${state.player.skillLevel(skill)}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.vitalityEmerald,
                                )
                                Text(
                                    activeDef.name,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            "STOP",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.panelElevated)
                                .clickable(onClick = onStopActivity)
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                    }

                    RpgProgressBar(
                        label = "Cycle #${active.completionsCount + 1} (${bankType.icon} ${bankType.displayName})",
                        valueText = "%,d / %,d %s".format(active.stepsProgress, costPerCycle, bankType.unit),
                        progress = (active.stepsProgress.toFloat() / costPerCycle.coerceAtLeast(1)).coerceIn(0f, 1f),
                        fillColor = tint,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (bankAvail > 0) {
                            RpgActionButton(
                                text = "SPEND 1× ${bankType.icon}",
                                onClick = { onSpendBankedStepsOnActivity(1) },
                                containerColor = colors.vitalityEmerald,
                                modifier = Modifier.weight(1f),
                            )
                            RpgActionButton(
                                text = "SPEND ALL",
                                onClick = { onSpendBankedStepsOnActivity(null) },
                                containerColor = colors.accentPrimary,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Text(
                                "${bankType.icon} ${bankType.displayName} bank empty — sync workouts or tap the bank bar above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                }
            }
        }

        else -> {
            // Clean, compact Idle Campfire card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.panelSurface,
                border = BorderStroke(1.dp, colors.pixelBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.pixel_hero_knight),
                        contentDescription = "Hero",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Resting at Campfire",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = colors.textPrimary,
                        )
                        Text(
                            "Tap any town task or local delve below to begin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityHubModeBar(
    selected: ActivityHubSection,
    tasksCount: Int,
    delvesCount: Int,
    onSelect: (ActivityHubSection) -> Unit,
) {
    val colors = RpgTheme.colors
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.panelSurface,
        border = BorderStroke(1.dp, colors.pixelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModePill(
                label = "⚒ TOWN TASKS ($tasksCount)",
                selected = selected == ActivityHubSection.TASKS,
                accent = colors.accentSecondary,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(ActivityHubSection.TASKS) },
            )
            ModePill(
                label = "⚔ LOCAL DELVES ($delvesCount)",
                selected = selected == ActivityHubSection.DELVES,
                accent = colors.accentPrimary,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(ActivityHubSection.DELVES) },
            )
        }
    }
}

@Composable
private fun ModePill(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = RpgTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) accent.copy(alpha = 0.18f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, accent) else null,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text(
                label,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (selected) accent else colors.textMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ActivityTileCard(
    state: GameState,
    activity: TownActivity,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = RpgTheme.colors
    val skill = SkillType.fromTag(activity.skillTag)
    val level = state.player.skillLevel(skill)
    val (bankType, costPerCycle) = ActionBankManager.costPerCycle(state, activity)
    val bankAvail = ActionBankManager.balanceOf(state.player, bankType)
    val cyclesReady = if (costPerCycle > 0) bankAvail / costPerCycle else 0
    val bankColor = bankAccentColor(bankType)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isRunning) colors.panelElevated else colors.panelSurface,
        border = BorderStroke(
            width = if (isRunning) 1.5.dp else 1.dp,
            color = if (isRunning) colors.vitalityEmerald else colors.pixelBorder,
        ),
        modifier = modifier
            .height(126.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(skill.icon, fontSize = 24.sp)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isRunning) colors.vitalityEmerald.copy(alpha = 0.2f) else colors.panelElevated,
                ) {
                    Text(
                        if (isRunning) "ACTIVE" else "Lv.$level",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isRunning) colors.vitalityEmerald else colors.textMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    activity.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${bankType.icon} ${compactNumber(costPerCycle)} ${bankType.unit}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = bankColor,
                    maxLines = 1,
                )
                Text(
                    if (cyclesReady > 0) "×$cyclesReady" else "0×",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (cyclesReady > 0) colors.vitalityEmerald else colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun DelvePosterCard(
    contract: ExpeditionContract,
    clearedBefore: Boolean,
    onClick: () -> Unit,
) {
    val colors = RpgTheme.colors
    val artRes = when (contract.difficulty) {
        ExpeditionDifficulty.ABYSSAL -> R.drawable.pixel_boss_encounter
        else -> R.drawable.pixel_banner_delve
    }
    val diffColor = when (contract.difficulty) {
        ExpeditionDifficulty.SCOUT -> colors.vitalityEmerald
        ExpeditionDifficulty.VANGUARD -> colors.accentPrimary
        ExpeditionDifficulty.ABYSSAL -> colors.crimsonHp
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (clearedBefore) colors.vitalityEmerald else diffColor.copy(alpha = 0.7f)),
        color = colors.panelSurface,
        modifier = Modifier
            .fillMaxWidth()
            .height(106.dp)
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(artRes),
                contentDescription = contract.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xF00C0F14),
                                Color(0xCC0C0F14),
                                Color(0x700C0F14),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = diffColor.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, diffColor.copy(alpha = 0.7f)),
                    ) {
                        Text(
                            contract.difficulty.displayName.uppercase(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = diffColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (clearedBefore) {
                        Text(
                            "✓ CLEARED",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.vitalityEmerald,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        contract.title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Boss: ${contract.bossName} · ${contract.totalNodes} Nodes · 🔥 50 kcal/fight" +
                            if (contract.grantsEggOnClear) " · 🥚 Egg" else "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = colors.accentSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionBanksDetailDialog(
    state: GameState,
    onDismiss: () -> Unit,
) {
    val colors = RpgTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.obsidianBg,
            border = BorderStroke(1.5.dp, colors.accentSecondary),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FitnessActionBanksGrid(state = state)
                RpgActionButton(
                    text = "CLOSE",
                    onClick = onDismiss,
                    containerColor = colors.ironSteel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ActivityDetailDialog(
    state: GameState,
    activity: TownActivity,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSpend: (Int?) -> Unit,
) {
    val colors = RpgTheme.colors
    val skill = SkillType.fromTag(activity.skillTag)
    val level = state.player.skillLevel(skill)
    val (bankType, costPerCycle) = ActionBankManager.costPerCycle(state, activity)
    val bankAvail = ActionBankManager.balanceOf(state.player, bankType)
    val bankCap = ActionBankManager.capacityOf(state.player, bankType)
    val isRunning = state.activeActivity?.activityId == activity.id
    val mat = CraftingEngine.materialForSkill(skill)
    val effPercent = SkillEngine.workEfficiencyPercent(level) +
        com.sparkborne.game.engine.CollectibleEngine.totalBonus(state, com.sparkborne.game.model.CollectibleBonusType.WORK_EFFICIENCY_PERCENT) +
        com.sparkborne.game.engine.ToolbeltEngine.workEfficiencyBonus(state, skill) +
        if (state.player.activeStepBoostCharges > 0) CraftingEngine.STEP_BOOST_EFFICIENCY_PERCENT else 0
    val completions = state.player.activityCompletions[activity.id] ?: 0
    val towardChest = completions % SkillEngine.COMPLETIONS_PER_CHEST

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.panelSurface,
            border = BorderStroke(1.5.dp, if (isRunning) colors.vitalityEmerald else colors.accentSecondary),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.panelElevated),
                    ) {
                        Text(skill.icon, fontSize = 24.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${skill.displayName.uppercase()} · LV.$level",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentSecondary,
                        )
                        Text(
                            activity.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = colors.textPrimary,
                        )
                    }
                }

                Text(
                    activity.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )

                // Action Bank Cost & Balance
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.panelElevated,
                    border = BorderStroke(1.dp, colors.pixelBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RpgProgressBar(
                            label = "${bankType.icon} ${bankType.displayName} Bank",
                            valueText = "%,d / %,d %s".format(bankAvail, bankCap, bankType.unit),
                            progress = if (bankCap > 0) (bankAvail.toFloat() / bankCap.toFloat()).coerceIn(0f, 1f) else 0f,
                            fillColor = bankAccentColor(bankType),
                        )
                        Text(
                            "Cost: %,d %s/cycle · Work Efficiency: +%d%%".format(costPerCycle, bankType.unit, effPercent),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = colors.accentPrimary,
                        )
                    }
                }

                // Rewards Breakdown
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.panelElevated,
                    border = BorderStroke(1.dp, colors.pixelBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "REWARDS PER CYCLE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accentPrimary,
                        )
                        val lootBits = buildList {
                            if (activity.rewardGold > 0) add("🪙 +${activity.rewardGold}g")
                            if (activity.rewardIron > 0) add("⛏ +${activity.rewardIron} Iron")
                            if (activity.rewardShards > 0) add("✨ +${activity.rewardShards} Shards")
                            add("${mat.icon} ${mat.displayName}")
                            add("⭐ +${activity.rewardXp} XP")
                        }
                        Text(
                            lootBits.joinToString("  ·  "),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colors.textPrimary,
                        )
                        Text(
                            "Skill Chest Progress: $towardChest / ${SkillEngine.COMPLETIONS_PER_CHEST} cycles  ·  Total Done: $completions",
                            fontSize = 10.sp,
                            color = colors.textMuted,
                        )
                    }
                }

                // Action CTAs
                if (isRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (bankAvail > 0) {
                            RpgActionButton(
                                text = "SPEND 1×",
                                onClick = { onSpend(1) },
                                containerColor = colors.vitalityEmerald,
                                modifier = Modifier.weight(1f),
                            )
                            RpgActionButton(
                                text = "SPEND ALL",
                                onClick = { onSpend(null) },
                                containerColor = colors.accentPrimary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RpgActionButton(
                            text = "STOP TASK",
                            onClick = onStop,
                            containerColor = colors.crimsonHp,
                            modifier = Modifier.weight(1f),
                        )
                        RpgActionButton(
                            text = "CLOSE",
                            onClick = onDismiss,
                            containerColor = colors.ironSteel,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RpgActionButton(
                            text = "CLOSE",
                            onClick = onDismiss,
                            containerColor = colors.ironSteel,
                            modifier = Modifier.weight(0.42f),
                        )
                        RpgActionButton(
                            text = "▶ START TASK",
                            onClick = onStart,
                            containerColor = colors.vitalityEmerald,
                            modifier = Modifier.weight(0.58f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DelveBriefingDialog(
    state: GameState,
    contract: ExpeditionContract,
    clearedBefore: Boolean,
    onDismiss: () -> Unit,
    onBegin: () -> Unit,
) {
    val colors = RpgTheme.colors
    val burnAvail = ActionBankManager.balanceOf(state.player, ActionBankType.CALORIES_BURNT)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.panelSurface,
            border = BorderStroke(1.5.dp, colors.accentPrimary),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "⚔ ${contract.title.uppercase()}" + if (clearedBefore) " ✓" else "",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.accentPrimary,
                )
                Text(
                    contract.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.panelElevated,
                    border = BorderStroke(1.dp, colors.pixelBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "BOSS: ${contract.bossName}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.crimsonHp,
                        )
                        Text(
                            "HP ${contract.bossHp} · ATK ${contract.bossAttack} · DEF ${contract.bossDefense}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = colors.textPrimary,
                        )
                        Text(
                            "${contract.totalNodes} Nodes (${contract.stepsPerNode} steps/node) · ${contract.durationDays}d Limit",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = colors.accentSecondary,
                        )
                        Text(
                            "Combat Cost: 🔥 50 kcal/fight · 100 kcal Boss (You have %,d kcal)".format(burnAvail),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = colors.accentPrimary,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.panelElevated,
                    border = BorderStroke(1.dp, colors.pixelBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "COMPLETION BOUNTY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.vitalityEmerald,
                        )
                        Text(
                            "🪙 ${contract.baseRewardGold}g  ·  ⛏ ${contract.baseRewardIron} Iron  ·  ✨ ${contract.baseRewardShards} Shards" +
                                if (contract.grantsEggOnClear) "  ·  🥚 Monster Egg" else "",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colors.textPrimary,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RpgActionButton(
                        text = "CANCEL",
                        onClick = onDismiss,
                        containerColor = colors.ironSteel,
                        modifier = Modifier.weight(0.4f),
                    )
                    RpgActionButton(
                        text = "⚔ BEGIN DELVE",
                        onClick = onBegin,
                        containerColor = colors.accentPrimary,
                        modifier = Modifier.weight(0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun bankAccentColor(bank: ActionBankType): Color {
    val colors = RpgTheme.colors
    return when (bank) {
        ActionBankType.STEPS -> colors.vitalityEmerald
        ActionBankType.PUSH_VOLUME -> colors.accentPrimary
        ActionBankType.PULL_LEGS_VOLUME -> colors.accentSecondary
        ActionBankType.HYDRATION_ML -> colors.hydrationCyan
        ActionBankType.CALORIES_BURNT -> colors.crimsonHp
    }
}

@DrawableRes
private fun backdropForElement(element: RegionElement): Int = when (element) {
    RegionElement.FIRE, RegionElement.VOID -> R.drawable.splash_obsidian_emblem
    RegionElement.LIGHTNING, RegionElement.WATER -> R.drawable.splash_twilight_citadel
    RegionElement.PLANT, RegionElement.POISON -> R.drawable.splash_campfire_vigil
}

private fun compactNumber(value: Int): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 10_000 -> "%.0fk".format(value / 1_000.0)
    value >= 1_000 -> "%.1fk".format(value / 1_000.0)
    else -> value.toString()
}
