package com.sparkborne.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sparkborne.app.ui.components.RpgActionButton
import com.sparkborne.app.ui.components.RpgPanel
import com.sparkborne.app.ui.components.RpgProgressBar
import com.sparkborne.app.ui.components.RpgStatChip
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.catalog.WorldCatalog
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.RegionElement
import com.sparkborne.game.model.WorldLocation

private fun elementColor(element: RegionElement): Color = when (element) {
    RegionElement.FIRE -> Color(0xFFF5B041)
    RegionElement.PLANT -> Color(0xFF4ADE80)
    RegionElement.WATER -> Color(0xFF38BDF8)
    RegionElement.POISON -> Color(0xFFA3E635)
    RegionElement.LIGHTNING -> Color(0xFFFACC15)
    RegionElement.VOID -> Color(0xFFA78BFA)
}

@Composable
fun WorldTabScreen(
    state: GameState,
    errorMessage: String?,
    onStartTravel: (String) -> Unit,
    onCancelTravel: () -> Unit,
    onStartActivity: (String) -> Unit,
    onStopActivity: () -> Unit,
    onBuyShopItem: (String) -> Unit,
    onStartContractFromTown: (String) -> Unit,
) {
    val colors = RpgTheme.colors
    val currentLocation = WorldCatalog.locationById(state.currentLocationId)
    var inspectedLocationId by remember(state.currentLocationId) { mutableStateOf(state.currentLocationId) }
    val inspectedLocation = WorldCatalog.locationById(inspectedLocationId)
    val isHere = inspectedLocation.id == currentLocation.id
    val directRoute = currentLocation.routes.firstOrNull { it.destinationId == inspectedLocation.id }

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

        // Active WalkScape Travel or Town Activity Status Banner
        state.activeTravel?.let { travel ->
            val dest = WorldCatalog.locationById(travel.toLocationId)
            item {
                RpgPanel(borderColor = colors.accentPrimary, containerColor = colors.panelElevated) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ON THE ROAD → ${dest.name}", fontWeight = FontWeight.Bold, color = colors.accentPrimary)
                        Text("TRAVELING", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = colors.accentSecondary)
                    }
                    RpgProgressBar(
                        label = "Travel Steps",
                        valueText = "${travel.stepsWalked} / ${travel.stepsRequired}",
                        progress = travel.progressFraction,
                        fillColor = colors.accentPrimary,
                    )
                    OutlinedButton(onClick = onCancelTravel, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel Travel (Return to ${currentLocation.name})")
                    }
                }
            }
        }

        state.activeActivity?.let { activeAct ->
            val def = WorldCatalog.activityById(activeAct.activityId)
            if (def != null) {
                item {
                    RpgPanel(borderColor = colors.accentSecondary, containerColor = colors.panelElevated) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${def.name} (${def.skillTag})", fontWeight = FontWeight.Bold, color = colors.accentSecondary)
                            Text("x${activeAct.completionsCount} DONE", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = colors.vitalityEmerald)
                        }
                        RpgProgressBar(
                            label = "Action Progress",
                            valueText = "${activeAct.stepsProgress} / ${def.stepsPerAction} steps",
                            progress = activeAct.stepsProgress.toFloat() / def.stepsPerAction.coerceAtLeast(1),
                            fillColor = colors.accentSecondary,
                        )
                        OutlinedButton(onClick = onStopActivity, modifier = Modifier.fillMaxWidth()) {
                            Text("Stop Activity")
                        }
                    }
                }
            }
        }

        // Interactive 2D World Map
        item {
            RpgPanel(borderColor = colors.pixelBorderBright) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "WORLD MAP · REALM OF SPARKBORNE",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        color = colors.accentPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${state.discoveredLocationIds.size}/${WorldCatalog.locations.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textMuted,
                    )
                }

                InteractiveWorldMapCanvas(
                    locations = WorldCatalog.locations,
                    currentLocationId = state.currentLocationId,
                    inspectedLocationId = inspectedLocationId,
                    discoveredIds = state.discoveredLocationIds,
                    activeTravelToId = state.activeTravel?.toLocationId,
                    onSelectLocation = { inspectedLocationId = it },
                )

                Text(
                    "Tap any town beacon to inspect its roads, shops, activities & contracts.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
        }

        // Selected Town Overview & Travel Action
        item {
            val elemColor = elementColor(inspectedLocation.element)
            RpgPanel(borderColor = elemColor) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            inspectedLocation.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = elemColor,
                        )
                        Text(
                            inspectedLocation.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                    RpgStatChip(
                        label = inspectedLocation.element.displayName.uppercase(),
                        value = if (isHere) "HERE" else if (inspectedLocation.id in state.discoveredLocationIds) "KNOWN" else "FOG",
                        accent = elemColor,
                    )
                }

                if (!isHere) {
                    if (directRoute != null) {
                        RpgActionButton(
                            text = "TRAVEL HERE (${directRoute.stepsRequired} STEPS)",
                            onClick = { onStartTravel(inspectedLocation.id) },
                            enabled = state.activeTravel == null,
                            containerColor = elemColor,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            "No direct road from ${currentLocation.name}. Travel to a connected town first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }
        }

        // Town Activities
        item {
            Text(
                "TOWN ACTIVITIES (STEP-DRIVEN)",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentSecondary,
                fontFamily = FontFamily.Monospace,
            )
        }
        items(inspectedLocation.activities, key = { it.id }) { act ->
            val isRunning = state.activeActivity?.activityId == act.id
            RpgPanel(borderColor = if (isRunning) colors.accentSecondary else colors.pixelBorder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${act.name} · ${act.skillTag}", fontWeight = FontWeight.Bold)
                        Text(act.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        Text(
                            "${act.stepsPerAction} steps → +${act.rewardGold}g +${act.rewardIron}fe +${act.rewardShards}sh +${act.rewardXp}XP",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = colors.vitalityEmerald,
                        )
                    }
                    RpgActionButton(
                        text = if (isRunning) "ACTIVE" else "START",
                        onClick = { onStartActivity(act.id) },
                        enabled = isHere && !isRunning && state.activeTravel == null,
                        containerColor = colors.accentSecondary,
                    )
                }
            }
        }

        // Town Shop
        item {
            Text(
                "REGIONAL SHOP · ${inspectedLocation.name.uppercase()}",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentPrimary,
                fontFamily = FontFamily.Monospace,
            )
        }
        items(inspectedLocation.shopListings, key = { it.id }) { listing ->
            val costParts = buildList {
                if (listing.goldCost > 0) add("${listing.goldCost}g")
                if (listing.ironCost > 0) add("${listing.ironCost}fe")
                if (listing.shardsCost > 0) add("${listing.shardsCost}sh")
            }.joinToString(" · ")

            RpgPanel {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(listing.title, fontWeight = FontWeight.Bold)
                        Text(listing.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        Text(costParts, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = colors.accentPrimary)
                    }
                    RpgActionButton(
                        text = if (isHere) "BUY" else "VISIT",
                        onClick = { onBuyShopItem(listing.id) },
                        enabled = isHere,
                    )
                }
            }
        }

        // Town Quests & Fights
        val townContracts = inspectedLocation.contractIds.mapNotNull(GameCatalog::contractById)
        if (townContracts.isNotEmpty()) {
            item {
                Text(
                    "LOCAL CONTRACTS & FIGHTS",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.crimsonHp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            items(townContracts, key = { "${inspectedLocation.id}_${it.id}" }) { contract ->
                RpgPanel(borderColor = colors.crimsonHp.copy(alpha = 0.6f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(contract.title, fontWeight = FontWeight.Bold)
                            Text(
                                "Boss: ${contract.bossName} · ${contract.durationDays}d · ${contract.difficulty.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                        RpgActionButton(
                            text = "DELVE",
                            onClick = { onStartContractFromTown(contract.id) },
                            enabled = isHere && state.activeExpedition == null && state.activeTravel == null,
                            containerColor = colors.crimsonHp,
                            contentColor = Color.White,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun InteractiveWorldMapCanvas(
    locations: List<WorldLocation>,
    currentLocationId: String,
    inspectedLocationId: String,
    discoveredIds: Set<String>,
    activeTravelToId: String?,
    onSelectLocation: (String) -> Unit,
) {
    val colors = RpgTheme.colors
    val byId = remember(locations) { locations.associateBy { it.id } }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.obsidianBg)
            .border(1.dp, colors.pixelBorder, RoundedCornerShape(8.dp)),
    ) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight

        // Draw subtle grid + roads between connected cities
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Subtle tactical map grid
            val step = 28.dp.toPx()
            var gx = step
            while (gx < w) {
                drawLine(colors.pixelBorder.copy(alpha = 0.25f), Offset(gx, 0f), Offset(gx, h), 1f)
                gx += step
            }
            var gy = step
            while (gy < h) {
                drawLine(colors.pixelBorder.copy(alpha = 0.25f), Offset(0f, gy), Offset(w, gy), 1f)
                gy += step
            }

            // Roads
            val drawnEdges = mutableSetOf<String>()
            for (loc in locations) {
                val start = Offset(loc.mapX * w, loc.mapY * h)
                for (route in loc.routes) {
                    val key = listOf(loc.id, route.destinationId).sorted().joinToString("-")
                    if (!drawnEdges.add(key)) continue
                    val dest = byId[route.destinationId] ?: continue
                    val end = Offset(dest.mapX * w, dest.mapY * h)

                    val isActiveRoad =
                        (loc.id == currentLocationId && dest.id == activeTravelToId) ||
                            (dest.id == currentLocationId && loc.id == activeTravelToId)
                    val bothKnown = loc.id in discoveredIds && dest.id in discoveredIds

                    drawLine(
                        color = when {
                            isActiveRoad -> colors.accentPrimary
                            bothKnown -> colors.pixelBorderBright
                            else -> colors.pixelBorder.copy(alpha = 0.6f)
                        },
                        start = start,
                        end = end,
                        strokeWidth = if (isActiveRoad) 5f else 3f,
                        pathEffect = if (!bothKnown && !isActiveRoad) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                    )
                }
            }
        }

        // Interactive City Beacons
        locations.forEach { loc ->
            val isCurrent = loc.id == currentLocationId
            val isInspected = loc.id == inspectedLocationId
            val elemColor = elementColor(loc.element)

            val xOffset = boxWidth * loc.mapX - 44.dp
            val yOffset = boxHeight * loc.mapY - 18.dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(x = xOffset, y = yOffset)
                    .clickable { onSelectLocation(loc.id) },
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 22.dp else 18.dp)
                        .clip(CircleShape)
                        .background(elemColor)
                        .border(
                            width = if (isInspected || isCurrent) 2.5.dp else 1.dp,
                            color = if (isCurrent) Color.White else colors.obsidianBg,
                            shape = CircleShape,
                        ),
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isInspected) colors.panelElevated else colors.panelSurface.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        text = loc.name.substringBefore(" "),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isCurrent || isInspected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) elemColor else colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}
