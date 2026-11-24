package com.sparkborne.app.ui.world

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.ui.hero.BannerTabs
import com.sparkborne.app.ui.hero.CostRow
import com.sparkborne.app.ui.hero.FrameStyle
import com.sparkborne.app.ui.hero.GoldTrim
import com.sparkborne.app.ui.hero.InkDark
import com.sparkborne.app.ui.hero.ItemSocket
import com.sparkborne.app.ui.hero.PixelArtIcon
import com.sparkborne.app.ui.hero.PixelButton
import com.sparkborne.app.ui.hero.SegmentBar
import com.sparkborne.app.ui.hero.ShrineTownRow
import com.sparkborne.app.ui.hero.StatusRibbon
import com.sparkborne.app.ui.hero.Text8
import com.sparkborne.app.ui.hero.armorArt
import com.sparkborne.app.ui.hero.armorRarity
import com.sparkborne.app.ui.hero.coinArt
import com.sparkborne.app.ui.hero.ingotArt
import com.sparkborne.app.ui.hero.mixWith
import com.sparkborne.app.ui.hero.pixelFrame
import com.sparkborne.app.ui.hero.rarityForCost
import com.sparkborne.app.ui.hero.shardArt
import com.sparkborne.app.ui.hero.spellOrbArt
import com.sparkborne.app.ui.hero.weaponArt
import com.sparkborne.app.ui.hero.weaponRarity
import com.sparkborne.app.ui.quest.PixelWindow
import com.sparkborne.app.ui.quest.WantedPoster
import com.sparkborne.app.ui.quest.elementColor
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.catalog.TalentCatalog
import com.sparkborne.game.catalog.WorldCatalog
import com.sparkborne.game.engine.TalentEngine
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.ShopItemType
import com.sparkborne.game.model.SkillType
import com.sparkborne.game.model.TownShopListing
import com.sparkborne.game.model.WorldLocation

private enum class TownTab(val label: String, val glyph: String) {
    ROADS("Roads", "🪧"),
    TASKS("Tasks", "⚒"),
    SHOP("Shop", "🛒"),
    QUESTS("Quests", "💀"),
}

@Composable
fun TownWindow(
    state: GameState,
    town: WorldLocation,
    actions: WorldActions,
    shopkeeper: @Composable (String, Dp) -> Unit,
    onClose: () -> Unit,
) {
    val ec = elementColor(town.element)
    val known = town.id in state.discoveredLocationIds
    val isHere = town.id == state.currentLocationId && state.activeTravel == null
    var tab by remember(town.id) { mutableStateOf(if (isHere) TownTab.TASKS else TownTab.ROADS) }
    PixelWindow(ec, onClose = onClose) {
        TownHeader(state, town, known, isHere, actions, onClose)
        if (town.id == TalentCatalog.SHRINE_LOCATION_ID) ShrineTownRow(state, actions.resetTalents)
        BannerTabs(TownTab.entries, tab, { it.label }, { it.glyph }) {
            tab = it
            if (it == TownTab.SHOP) actions.visitedShop()
        }
        when (tab) {
            TownTab.ROADS -> Roads(state, town, isHere, actions, onClose)
            TownTab.TASKS -> Tasks(state, town, isHere, actions, onClose)
            TownTab.SHOP -> Shop(state, town, isHere, actions, shopkeeper)
            TownTab.QUESTS -> Quests(state, town, isHere, actions, onClose)
        }
    }
}

@Composable
private fun ColumnScope.TownHeader(state: GameState, town: WorldLocation, known: Boolean, isHere: Boolean, actions: WorldActions, onClose: () -> Unit) {
    val colors = RpgTheme.colors
    val ec = elementColor(town.element)
    Text8("${elementGlyph(town.element)} ${town.element.displayName.uppercase()} REGION", size = 10.sp, color = ec, letterSpacing = 2.sp)
    Text8(if (known) town.name.uppercase() else "UNCHARTED LANDS", size = 18.sp, color = Color.White, weight = FontWeight.ExtraBold, align = TextAlign.Center)
    Text8(if (known) town.subtitle else "Walk the road to reveal what lies here.", size = 10.sp, color = colors.textMuted, align = TextAlign.Center, italic = true, weight = FontWeight.Normal)
    when {
        isHere -> StatusRibbon("★ YOU ARE HERE", GoldTrim)
        state.activeTravel?.toLocationId == town.id -> StatusRibbon("🧭 ON THE ROAD HERE", colors.accentSecondary)
        else -> {
            val from = WorldCatalog.locationById(state.currentLocationId)
            val route = from.routes.firstOrNull { it.destinationId == town.id }
            if (route != null) {
                val steps = TalentEngine.travelSteps(state.player, route.stepsRequired)
                PixelButton("🥾 WALK HERE · %,d STEPS".format(steps), ec, enabled = state.activeTravel == null) {
                    actions.startTravel(town.id)
                    onClose()
                }
            } else {
                Text8("No road from ${from.name}. Travel to a neighbouring town first.", size = 10.sp, color = colors.textMuted, align = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Roads(state: GameState, town: WorldLocation, isHere: Boolean, actions: WorldActions, onClose: () -> Unit) {
    val colors = RpgTheme.colors
    state.activeTravel?.let { tv ->
        val dest = WorldCatalog.locationById(tv.toLocationId)
        Column(
            Modifier.fillMaxWidth().pixelFrame(fill = Color(0xFF2B2620), edge = Color(0xFF8A6238), rivet = GoldTrim).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text8("🧭 BOUND FOR ${dest.name.uppercase()}", size = 11.sp, color = GoldTrim, weight = FontWeight.ExtraBold)
            SegmentBar(tv.progressFraction, GoldTrim, Modifier.fillMaxWidth(), segments = 18, height = 12.dp, label = "%,d / %,d".format(tv.stepsWalked, tv.stepsRequired))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.player.savedSteps > 0) PixelButton("🥾 SPEND BANKED", colors.vitalityEmerald, small = true, onClick = actions.spendOnTravel)
                PixelButton("TURN BACK", colors.pixelBorderBright, small = true, onClick = actions.cancelTravel)
            }
        }
    }
    town.routes.forEach { r ->
        val d = WorldCatalog.locationById(r.destinationId)
        val steps = TalentEngine.travelSteps(state.player, r.stepsRequired)
        val known = d.id in state.discoveredLocationIds
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // wooden arrow sign
            Row(
                Modifier.weight(1f).pixelFrame(fill = Color(0xFF6B4A2C), edge = Color(0xFF8A6238), style = FrameStyle.PLATE, unit = 2.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text8(if (known) elementGlyph(d.element) else "?", size = 14.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text8(if (known) d.name.uppercase() else "UNCHARTED", size = 11.sp, color = Color(0xFFF0DDB8), weight = FontWeight.ExtraBold, maxLines = 1)
                    Text8("%,d steps".format(steps), size = 9.sp, color = Color(0xFFD9C49A))
                }
                Text8("➤", size = 14.sp, color = Color(0xFFF0DDB8))
            }
            Spacer(Modifier.width(8.dp))
            PixelButton("WALK", elementColor(d.element), small = true, enabled = isHere && state.activeTravel == null) {
                actions.startTravel(d.id)
                onClose()
            }
        }
    }
}

@Composable
private fun Tasks(state: GameState, town: WorldLocation, isHere: Boolean, actions: WorldActions, onClose: () -> Unit) {
    val colors = RpgTheme.colors
    town.activities.forEach { act ->
        val running = state.activeActivity?.activityId == act.id
        val skill = SkillType.fromTag(act.skillTag)
        Column(
            Modifier.fillMaxWidth()
                .pixelFrame(fill = if (running) Color(0xFF4A3A22) else Color(0xFF3A2C1C), edge = if (running) colors.vitalityEmerald else Color(0xFF8A6238), rivet = if (running) colors.vitalityEmerald else GoldTrim)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemSocket(44.dp, glow = if (running) colors.vitalityEmerald else null) { Text8(skill.icon, size = 20.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text8(act.name.uppercase(), size = 11.sp, color = Color(0xFFF0DDB8), weight = FontWeight.ExtraBold)
                    Text8("${skill.displayName} Lv.${state.player.skillLevel(skill)}" + if (running) " · ×${state.activeActivity!!.completionsCount} done" else "", size = 9.sp, color = if (running) colors.vitalityEmerald else colors.textMuted)
                }
            }
            Text8(act.description, size = 10.sp, color = Color(0xFFD9C49A), weight = FontWeight.Normal)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Spoils(act.rewardGold, act.rewardIron, act.rewardShards)
                Text8("⭐+${act.rewardXp}", size = 10.sp, color = colors.accentSecondary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (running) {
                    PixelButton("SPEND 1×", colors.vitalityEmerald, small = true) { actions.spendOnActivity(1) }
                    PixelButton("STOP", colors.crimsonHp, small = true, onClick = actions.stopActivity)
                } else {
                    PixelButton(if (isHere) "▶ TAKE THE JOB" else "TRAVEL HERE FIRST", colors.vitalityEmerald, small = true, enabled = isHere && state.activeTravel == null) {
                        actions.startActivity(act.id)
                        onClose()
                    }
                }
            }
        }
    }
}

@Composable
private fun Spoils(gold: Int, iron: Int, shards: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Triple(coinArt, gold, GoldTrim), Triple(ingotArt, iron, Color(0xFFC9D2DE)), Triple(shardArt, shards, Color(0xFFFF9A5E))).filter { it.second > 0 }.forEach { (art, n, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelArtIcon(art, 14.dp)
                Text8("+$n", size = 10.sp, color = c)
            }
        }
    }
}

@Composable
private fun Shop(state: GameState, town: WorldLocation, isHere: Boolean, actions: WorldActions, shopkeeper: @Composable (String, Dp) -> Unit) {
    val colors = RpgTheme.colors
    val keeper = town.shopkeeper
    val ec = elementColor(town.element)
    // the counter: portrait + parchment speech
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp).pixelFrame(fill = InkDark, edge = ec.mixWith(colors.pixelBorder, 0.4f), style = FrameStyle.SOCKET, unit = 2.dp).padding(6.dp), contentAlignment = Alignment.Center) {
                shopkeeper(keeper.id, 72.dp)
            }
            Text8(keeper.name.uppercase(), size = 9.sp, color = ec, weight = FontWeight.ExtraBold, align = TextAlign.Center, modifier = Modifier.width(90.dp))
            Text8(keeper.title, size = 8.sp, color = colors.textMuted, align = TextAlign.Center, modifier = Modifier.width(90.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).pixelFrame(fill = Color(0xFFD9C49A), edge = Color(0xFF8A6238), style = FrameStyle.PLATE, unit = 2.dp).padding(10.dp)) {
            Text8(keeper.loreQuote, size = 10.sp, color = Color(0xFF3A2416), italic = true, weight = FontWeight.Normal)
        }
    }
    if (!isHere) Text8("Travel here to trade with ${keeper.name.substringBefore(" ")}.", size = 10.sp, color = colors.textMuted, align = TextAlign.Center)
    town.shopListings.forEach { l -> ShopRow(state, l, isHere, actions) }
}

@Composable
private fun ShopRow(state: GameState, l: TownShopListing, isHere: Boolean, actions: WorldActions) {
    val colors = RpgTheme.colors
    val rarity = rarityForCost(l.goldCost)
    Row(
        Modifier.fillMaxWidth().pixelFrame(fill = colors.panelElevated, edge = rarity.color.mixWith(colors.pixelBorder, 0.5f), style = FrameStyle.PLATE, unit = 2.dp).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemSocket(52.dp, glow = rarity.color) { ShopItemIcon(l) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text8(l.title.uppercase(), size = 11.sp, color = rarity.color, weight = FontWeight.ExtraBold, maxLines = 1)
            Text8(l.subtitle, size = 9.sp, color = colors.textMuted, maxLines = 2)
            CostRow(state.player, l.goldCost, l.ironCost, l.shardsCost)
        }
        Spacer(Modifier.width(6.dp))
        PixelButton("BUY", GoldTrim, small = true, enabled = isHere) { actions.buyShopItem(l.id) }
    }
}

@Composable
private fun ShopItemIcon(l: TownShopListing) {
    when (l.type) {
        ShopItemType.WEAPON -> GameCatalog.weapons.firstOrNull { it.id == l.targetItemId }?.let { PixelArtIcon(weaponArt(it.weaponClass, weaponRarity(it)), 34.dp) } ?: Text8("⚔", size = 20.sp)
        ShopItemType.ARMOR -> GameCatalog.armorById(l.targetItemId)?.let { PixelArtIcon(armorArt(it.slot, armorRarity(it)), 34.dp) } ?: Text8("⛨", size = 20.sp)
        ShopItemType.SPELL -> GameCatalog.spellById(l.targetItemId)?.let { PixelArtIcon(spellOrbArt(it.element), 32.dp) } ?: Text8("✦", size = 20.sp)
        ShopItemType.EGG -> Text8("🥚", size = 22.sp)
        ShopItemType.SUPPLIES -> Text8("🎒", size = 22.sp)
    }
}

@Composable
private fun Quests(state: GameState, town: WorldLocation, isHere: Boolean, actions: WorldActions, onClose: () -> Unit) {
    val colors = RpgTheme.colors
    val contracts = town.contractIds.mapNotNull(GameCatalog::contractById)
    if (contracts.isEmpty()) {
        Text8("No bounties posted in this town.", size = 11.sp, color = colors.textMuted, align = TextAlign.Center)
    }
    contracts.forEach { c ->
        WantedPoster(c, c.id in state.completedContractIds) {}
        PixelButton(
            if (isHere) "⚔ ACCEPT BOUNTY" else "TRAVEL HERE FIRST",
            colors.crimsonHp,
            small = true,
            enabled = isHere && state.activeExpedition == null,
        ) {
            actions.startContract(c.id)
            onClose()
        }
    }
}
