package com.sparkborne.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.sparkborne.app.ui.quest.QuestActions
import com.sparkborne.app.ui.quest.QuestHubScreen
import com.sparkborne.app.ui.quest.QuestSprites
import com.sparkborne.game.engine.CombatAction
import com.sparkborne.game.model.GameState

/**
 * Activity tab: the game-styled quest hub and delve screens from `ui/quest`. This wrapper only
 * wires ViewModel callbacks and supplies the bitmap sprites (companion & enemies).
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
    val actions = QuestActions(
        startActivity = onStartActivity,
        stopActivity = onStopActivity,
        cancelTravel = onCancelTravel,
        spendOnTravel = onSpendBankedStepsOnTravel,
        spendOnActivity = onSpendBankedStepsOnActivity,
        startContract = onStartContract,
        enterNode = onEnterNode,
        combat = onCombatAction,
        abandonDelve = onAbandonDelve,
        clearFinishedDelve = onClearFinishedDelve,
        spendOnDelve = onSpendBankedStepsOnDelve,
        openMap = onOpenMap,
    )
    val monster = state.activeMonster
    val companion: (@Composable (Dp) -> Unit)? = if (monster != null) {
        { size -> com.sparkborne.app.ui.hero.CompanionSprite(monster, size) }
    } else {
        null
    }
    val sprites = QuestSprites(companion = companion)
    QuestHubScreen(state = state, errorMessage = errorMessage, actions = actions, sprites = sprites)
}
