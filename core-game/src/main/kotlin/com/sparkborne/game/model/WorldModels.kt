package com.sparkborne.game.model

import kotlinx.serialization.Serializable

@Serializable
enum class RegionElement(val displayName: String) {
    FIRE("Fire"),
    PLANT("Plant"),
    WATER("Water"),
    POISON("Poison"),
    LIGHTNING("Lightning"),
    VOID("Void"),
}

@Serializable
data class TravelRoute(
    val destinationId: String,
    val stepsRequired: Int,
)

@Serializable
data class TownActivity(
    val id: String,
    val locationId: String,
    val name: String,
    val skillTag: String,
    val description: String,
    val stepsPerAction: Int,
    val rewardGold: Int = 0,
    val rewardIron: Int = 0,
    val rewardShards: Int = 0,
    val rewardXp: Int = 10,
    val rewardMonsterXp: Int = 5,
)

@Serializable
enum class ShopItemType {
    WEAPON,
    ARMOR,
    SPELL,
    EGG,
    SUPPLIES,
}

@Serializable
data class TownShopListing(
    val id: String,
    val locationId: String,
    val title: String,
    val subtitle: String,
    val type: ShopItemType,
    val targetItemId: String,
    val goldCost: Int = 0,
    val ironCost: Int = 0,
    val shardsCost: Int = 0,
)

@Serializable
data class TownShopkeeper(
    val id: String,
    val name: String,
    val title: String,
    val loreQuote: String,
)

@Serializable
data class WorldLocation(
    val id: String,
    val name: String,
    val subtitle: String,
    val element: RegionElement,
    val mapX: Float, // 0.0..1.0 normalized map coordinate
    val mapY: Float, // 0.0..1.0 normalized map coordinate
    val shopkeeper: TownShopkeeper,
    val routes: List<TravelRoute>,
    val contractIds: List<String>,
    val activities: List<TownActivity>,
    val shopListings: List<TownShopListing>,
)

@Serializable
data class ActiveTravel(
    val fromLocationId: String,
    val toLocationId: String,
    val stepsRequired: Int,
    val stepsWalked: Int = 0,
) {
    val progressFraction: Float
        get() = (stepsWalked.toFloat() / stepsRequired.coerceAtLeast(1)).coerceIn(0f, 1f)
}

@Serializable
data class ActiveTownActivity(
    val activityId: String,
    val locationId: String,
    val stepsProgress: Int = 0,
    val completionsCount: Int = 0,
)
