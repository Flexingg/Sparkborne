package com.sparkborne.app.ui.hero

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.ActionBankManager
import com.sparkborne.game.engine.CraftingEngine
import com.sparkborne.game.engine.TalentEngine
import com.sparkborne.game.engine.ToolbeltEngine
import com.sparkborne.game.model.ActionBankType
import com.sparkborne.game.model.ArmorSlot
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.PlayerState

/**
 * Everything currently waiting on the player, used to light alert pips across the UI.
 */
data class Attention(
    val talentPoints: Int,
    val chests: Int,
    val eggsReady: Int,
    val gearUpgrades: Int,
    val brewable: Int,
    val fullReservoirs: List<ActionBankType>,
    val idle: Boolean,
) {
    /** Hero avatar: things to do on the character screen. */
    val hero: Int get() = talentPoints + chests + gearUpgrades
    val inventory: Int get() = chests + brewable
    val beasts: Int get() = eggsReady
    /** Activity tab: a full reservoir is wasting effort, or the hero is doing nothing. */
    val activity: Int get() = fullReservoirs.size + if (idle) 1 else 0
}

private fun affordable(p: PlayerState, gold: Int, iron: Int, shards: Int) =
    p.gold >= gold && p.ironOre >= iron && p.emberShards >= shards

/** True when the player owns or can afford gear for [slot] that beats what's equipped. */
fun upgradeAvailable(state: GameState, slot: GearSlot): Boolean {
    val p = state.player
    return if (slot == GearSlot.WEAPON) {
        val eq = GameCatalog.weaponById(p.equippedWeaponId)
        GameCatalog.weapons.any { w ->
            w.id != eq.id && w.baseDamage > eq.baseDamage &&
                (w.id in p.ownedWeaponIds || affordable(p, w.forgeGoldCost, w.forgeIronCost, 0))
        }
    } else {
        val s = slot.armorSlot ?: return false
        val eq = p.equippedArmor[s]?.let(GameCatalog::armorById)
        GameCatalog.armor.any { a ->
            a.slot == s && a.id != eq?.id &&
                (a.defense + a.bonusHp / 4) > ((eq?.defense ?: 0) + (eq?.bonusHp ?: 0) / 4) &&
                (a.id in p.ownedArmorIds || affordable(p, a.forgeGoldCost, a.forgeIronCost, a.forgeShardsCost))
        }
    }
}

fun attentionOf(state: GameState): Attention {
    val p = state.player
    val upgrades = GearSlot.entries.count { upgradeAvailable(state, it) } +
        ToolbeltEngine.tools.count { it.id !in p.ownedToolIds && affordable(p, it.forgeGoldCost, it.forgeIronCost, it.forgeShardsCost) && p.equippedToolIds.size < p.maxToolbeltSlots() }.coerceAtMost(1)
    val brewable = CraftingEngine.recipes.count { r ->
        p.gold >= r.goldCost && r.requiredMaterials.all { (m, n) -> (p.materials[m] ?: 0) >= n }
    }
    val full = ActionBankType.entries.filter { b ->
        val cap = ActionBankManager.capacityOf(p, b)
        cap > 0 && ActionBankManager.balanceOf(p, b) >= cap
    }
    val idle = state.activeActivity == null && state.activeTravel == null && state.activeExpedition == null
    return Attention(
        talentPoints = TalentEngine.pointsAvailable(p),
        chests = p.totalSkillChests(),
        eggsReady = state.eggs.count { it.isReadyToHatch },
        gearUpgrades = upgrades,
        brewable = brewable,
        fullReservoirs = full,
        idle = idle,
    )
}

/**
 * A small pulsing pixel gem. Shows [count] when > 1 (or [label] if given); hidden when count is 0.
 */
@Composable
fun AlertPip(count: Int, modifier: Modifier = Modifier, color: Color = Color(0xFFFF5A4E), label: String? = null) {
    if (count <= 0) return
    val tr = rememberInfiniteTransition(label = "pip")
    val pulse by tr.animateFloat(0.88f, 1.12f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pip_p")
    Box(
        modifier
            .scale(pulse)
            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
            .pixelFrame(fill = color, edge = color.mixWith(Color.White, 0.45f), style = FrameStyle.PLATE, unit = 1.dp)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text8(label ?: if (count > 9) "9+" else "$count", size = 8.sp, color = Color.White, weight = FontWeight.ExtraBold)
    }
}

/** A tiny "▲" marker for an available upgrade on a gear socket. */
@Composable
fun UpgradePip(modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "up")
    val bob by tr.animateFloat(-1.5f, 1.5f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "up_b")
    Box(
        modifier
            .padding(top = (2 + bob).coerceAtLeast(0f).dp)
            .size(16.dp)
            .pixelFrame(fill = Color(0xFF3FAE5A), edge = Color(0xFF9BF0A8), style = FrameStyle.PLATE, unit = 1.dp),
        contentAlignment = Alignment.Center,
    ) { Text8("▲", size = 8.sp, color = Color.White, weight = FontWeight.ExtraBold) }
}
