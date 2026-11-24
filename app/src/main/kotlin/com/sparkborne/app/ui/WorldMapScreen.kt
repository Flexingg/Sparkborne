package com.sparkborne.app.ui

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.sparkborne.app.R
import com.sparkborne.app.ui.world.WorldActions
import com.sparkborne.app.ui.world.WorldMapView
import com.sparkborne.game.model.GameState

/** Row-major 3x3 painted map tiles. */
private val MAP_TILES = listOf(
    R.drawable.map_tile_0_0, R.drawable.map_tile_0_1, R.drawable.map_tile_0_2,
    R.drawable.map_tile_1_0, R.drawable.map_tile_1_1, R.drawable.map_tile_1_2,
    R.drawable.map_tile_2_0, R.drawable.map_tile_2_1, R.drawable.map_tile_2_2,
)

@DrawableRes
internal fun shopkeeperDrawableFor(shopkeeperId: String): Int = when (shopkeeperId) {
    "shopkeeper_ember" -> R.drawable.shopkeeper_ember
    "shopkeeper_verdant" -> R.drawable.shopkeeper_verdant
    "shopkeeper_tidebreak" -> R.drawable.shopkeeper_tidebreak
    "shopkeeper_mirefen" -> R.drawable.shopkeeper_mirefen
    "shopkeeper_stormspire" -> R.drawable.shopkeeper_stormspire
    "shopkeeper_obsidian" -> R.drawable.shopkeeper_obsidian
    "shopkeeper_frostpeak" -> R.drawable.shopkeeper_frostpeak
    "shopkeeper_sunspire" -> R.drawable.shopkeeper_sunspire
    "shopkeeper_ashen" -> R.drawable.shopkeeper_ashen
    else -> R.drawable.shopkeeper_ember
}

/**
 * World tab: the living painted map from `ui/world`. This wrapper only decodes the map tiles,
 * supplies shopkeeper portraits and wires ViewModel callbacks.
 */
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
    onVisitedShop: () -> Unit = {},
    onSpendBankedStepsOnTravel: () -> Unit = {},
    onSpendBankedStepsOnActivity: (Int?) -> Unit = {},
    onResetTalents: () -> Unit = {},
) {
    val context = LocalContext.current
    val tiles: List<ImageBitmap> = remember {
        MAP_TILES.map { BitmapFactory.decodeResource(context.resources, it).asImageBitmap() }
    }
    val actions = WorldActions(
        startTravel = onStartTravel,
        cancelTravel = onCancelTravel,
        startActivity = onStartActivity,
        stopActivity = onStopActivity,
        buyShopItem = onBuyShopItem,
        startContract = onStartContractFromTown,
        visitedShop = onVisitedShop,
        spendOnTravel = onSpendBankedStepsOnTravel,
        spendOnActivity = onSpendBankedStepsOnActivity,
        resetTalents = onResetTalents,
    )
    val shopkeeper: @Composable (String, Dp) -> Unit = { keeperId, size ->
        RawPixelSprite(shopkeeperDrawableFor(keeperId), "Shopkeeper", size)
    }
    WorldMapView(
        state = state,
        errorMessage = errorMessage,
        tiles = tiles,
        actions = actions,
        shopkeeper = shopkeeper,
    )
}
