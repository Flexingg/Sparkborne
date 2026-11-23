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
 * Inventory tab: the adventurer's pack (coin purse, slot-grid bag and cauldron workshop) from
 * `ui/hero/BagScreen.kt`.
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
    onForgeOrEquipWeapon: (String) -> Unit = {},
    onForgeOrEquipArmor: (String) -> Unit = {},
    onUnlockOrAttuneSpell: (String) -> Unit = {},
) {
    val actions = com.sparkborne.app.ui.hero.HeroActions(
            forgeOrEquipWeapon = onForgeOrEquipWeapon,
            forgeOrEquipArmor = onForgeOrEquipArmor,
            unlockOrAttuneSpell = onUnlockOrAttuneSpell,
            openSkillChest = onOpenSkillChest,
            forgeOrToggleTool = onForgeOrToggleTool,
            saveGearSet = onSaveGearSet,
            equipGearSet = onEquipGearSet,
            craftRecipe = onCraftRecipe,
            useConsumable = onUseConsumable,
        )
    com.sparkborne.app.ui.hero.BagScreen(state = state, errorMessage = errorMessage, actions = actions)
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

