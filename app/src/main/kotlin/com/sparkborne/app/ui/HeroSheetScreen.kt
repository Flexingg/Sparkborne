package com.sparkborne.app.ui

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.sparkborne.app.R
import com.sparkborne.app.ui.components.monsterDrawableFor
import com.sparkborne.app.ui.hero.CharacterScreen
import com.sparkborne.app.ui.hero.HeroActions
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.SkillType

/**
 * Hero tab: the game-style character screen (gear stage, skills, spellbook, relic hall) from
 * `ui/hero`. This wrapper only supplies the bitmap sprites, which need Android resources.
 */
@Composable
fun HeroTabScreen(
    state: GameState,
    errorMessage: String?,
    onForgeOrEquipWeapon: (String) -> Unit,
    onForgeOrEquipArmor: (String) -> Unit,
    onUnlockOrAttuneSpell: (String) -> Unit,
    onOpenSkillChest: (SkillType) -> Unit = {},
    onForgeOrToggleTool: (String) -> Unit = {},
    onSaveGearSet: (Int, String) -> Unit = { _, _ -> },
    onEquipGearSet: (Int) -> Unit = {},
    onCraftRecipe: (String) -> Unit = {},
    onUseConsumable: (String) -> Unit = {},
) {
    val actions = remember(
        onForgeOrEquipWeapon, onForgeOrEquipArmor, onUnlockOrAttuneSpell, onOpenSkillChest,
        onForgeOrToggleTool, onSaveGearSet, onEquipGearSet, onCraftRecipe, onUseConsumable,
    ) {
        HeroActions(
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
    }
    val monster = state.activeMonster
    CharacterScreen(
        state = state,
        errorMessage = errorMessage,
        actions = actions,
        heroPortrait = { size -> RawPixelSprite(R.drawable.pixel_hero_knight, "Your hero", size) },
        companionPortrait = monster?.let { m -> { size: Dp -> RawPixelSprite(monsterDrawableFor(m.affinity), m.name, size) } },
    )
}

/** Unframed nearest-neighbour bitmap sprite. */
@Composable
fun RawPixelSprite(@DrawableRes res: Int, description: String, size: Dp) {
    val context = LocalContext.current
    val bitmap = remember(res) { BitmapFactory.decodeResource(context.resources, res).asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
        modifier = Modifier.size(size),
    )
}
