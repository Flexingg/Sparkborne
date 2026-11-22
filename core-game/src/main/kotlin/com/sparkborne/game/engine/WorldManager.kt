package com.sparkborne.game.engine

import com.sparkborne.game.catalog.WorldCatalog
import com.sparkborne.game.model.ActiveTownActivity
import com.sparkborne.game.model.ActiveTravel
import com.sparkborne.game.model.ExpeditionStatus
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.MonsterEgg
import com.sparkborne.game.model.ShopItemType

/**
 * Deterministic manager for WalkScape-style world travel, repeatable town step-activities,
 * and regional town shop purchases.
 */
object WorldManager {

    fun startTravel(state: GameState, destinationId: String): Result<GameState> {
        if (state.activeExpedition?.status == ExpeditionStatus.ACTIVE) {
            return Result.failure(IllegalStateException("Finish or abandon your active delve before traveling."))
        }
        val current = WorldCatalog.locationById(state.currentLocationId)
        val route = current.routes.firstOrNull { it.destinationId == destinationId }
            ?: return Result.failure(IllegalStateException("No direct road from ${current.name} to that destination."))
        val dest = WorldCatalog.locationById(destinationId)

        return Result.success(
            state.copy(
                activeTravel = ActiveTravel(
                    fromLocationId = current.id,
                    toLocationId = dest.id,
                    stepsRequired = route.stepsRequired,
                    stepsWalked = 0,
                ),
                activeActivity = null,
                recentLog = (listOf("Set out on the road to ${dest.name} (${route.stepsRequired} steps).") + state.recentLog).take(30),
            ),
        )
    }

    fun cancelTravel(state: GameState): GameState {
        if (state.activeTravel == null) return state
        return state.copy(
            activeTravel = null,
            recentLog = (listOf("Returned to ${WorldCatalog.locationById(state.currentLocationId).name}.") + state.recentLog).take(30),
        )
    }

    fun startActivity(state: GameState, activityId: String): Result<GameState> {
        if (state.activeTravel != null) {
            return Result.failure(IllegalStateException("Finish or cancel travel before starting a town activity."))
        }
        val activity = WorldCatalog.activityById(activityId)
            ?: return Result.failure(IllegalArgumentException("Unknown activity: $activityId"))
        if (activity.locationId != state.currentLocationId) {
            return Result.failure(IllegalStateException("Travel to ${WorldCatalog.locationById(activity.locationId).name} to do ${activity.name}."))
        }

        val started = state.copy(
            activeActivity = ActiveTownActivity(
                activityId = activity.id,
                locationId = activity.locationId,
                stepsProgress = 0,
                completionsCount = 0,
            ),
            recentLog = (listOf("Started ${activity.name} (${activity.stepsPerAction} steps/action).") + state.recentLog).take(30),
        )
        return Result.success(SparkpediaEngine.onStartedActivity(started))
    }

    fun stopActivity(state: GameState): GameState {
        if (state.activeActivity == null) return state
        return state.copy(activeActivity = null)
    }

    fun buyShopListing(state: GameState, listingId: String): Result<GameState> {
        val listing = WorldCatalog.shopListingById(listingId)
            ?: return Result.failure(IllegalArgumentException("Unknown shop item: $listingId"))
        if (listing.locationId != state.currentLocationId) {
            return Result.failure(IllegalStateException("You must be in ${WorldCatalog.locationById(listing.locationId).name} to shop here."))
        }

        val result = when (listing.type) {
            ShopItemType.WEAPON -> ExpeditionManager.forgeAndEquipWeapon(state, listing.targetItemId)
            ShopItemType.ARMOR -> ExpeditionManager.forgeAndEquipArmor(state, listing.targetItemId)
            ShopItemType.SPELL -> ExpeditionManager.unlockAndAttuneSpell(state, listing.targetItemId)
            ShopItemType.EGG -> {
                val p = state.player
                if (p.gold < listing.goldCost || p.ironOre < listing.ironCost || p.emberShards < listing.shardsCost) {
                    return Result.failure(IllegalStateException("Insufficient resources to buy ${listing.title}."))
                }
                val egg = MonsterEgg(
                    id = "${listing.targetItemId}_${state.eggs.size + state.monsters.size + 1}",
                    name = listing.title,
                    speciesSeed = listing.targetItemId,
                    requiredSleepMinutes = 900,
                    requiredHydrationMl = 4500,
                )
                Result.success(
                    state.copy(
                        player = p.copy(
                            gold = p.gold - listing.goldCost,
                            ironOre = p.ironOre - listing.ironCost,
                            emberShards = p.emberShards - listing.shardsCost,
                        ),
                        eggs = state.eggs + egg,
                        recentLog = (listOf("Purchased ${listing.title} in ${WorldCatalog.locationById(listing.locationId).name}!") + state.recentLog).take(30),
                    ),
                )
            }
            ShopItemType.SUPPLIES -> Result.success(state)
        }
        return result.map(SparkpediaEngine::syncCodexUnlocks)
    }

    /**
     * Applies synced real-world steps (scaled by momentum stance) to either ActiveTravel
     * or ActiveTownActivity, returning the updated GameState and event log entries.
     */
    internal fun applyStepsToWorld(
        state: GameState,
        effectiveSteps: Int,
        events: MutableList<String>,
    ): GameState {
        if (effectiveSteps <= 0) return state

        // 1. Travel takes priority over town activity
        val travel = state.activeTravel
        if (travel != null) {
            val totalWalked = travel.stepsWalked + effectiveSteps
            return if (totalWalked >= travel.stepsRequired) {
                val dest = WorldCatalog.locationById(travel.toLocationId)
                events.add("Arrived at ${dest.name}!")
                SparkpediaEngine.syncCodexUnlocks(
                    state.copy(
                        currentLocationId = dest.id,
                        discoveredLocationIds = state.discoveredLocationIds + dest.id,
                        activeTravel = null,
                    ),
                )
            } else {
                events.add("Walked +$effectiveSteps steps toward ${WorldCatalog.locationById(travel.toLocationId).name} ($totalWalked/${travel.stepsRequired}).")
                state.copy(activeTravel = travel.copy(stepsWalked = totalWalked))
            }
        }

        // 2. Repeatable Town Activity (WalkScape-style multi-completion loop with Skill Efficiency & Chests)
        val activeAct = state.activeActivity ?: return state
        val def = WorldCatalog.activityById(activeAct.activityId) ?: return state
        val skill = com.sparkborne.game.model.SkillType.fromTag(def.skillTag)
        val effectiveStepsPerAction = SkillEngine.effectiveStepsPerAction(def.stepsPerAction, state, skill)
        val totalProgress = activeAct.stepsProgress + effectiveSteps
        val completions = totalProgress / effectiveStepsPerAction
        val remainder = totalProgress % effectiveStepsPerAction

        if (completions <= 0) {
            return state.copy(activeActivity = activeAct.copy(stepsProgress = remainder))
        }

        val goldGain = completions * def.rewardGold
        val ironGain = completions * def.rewardIron
        val shardGain = completions * def.rewardShards
        val xpGain = completions * def.rewardXp
        val monXpGain = completions * def.rewardMonsterXp

        var newLevel = state.player.level
        var newXp = state.player.xp + xpGain
        while (newXp >= (if (newLevel <= 0) 100 else newLevel * 150)) {
            newXp -= (if (newLevel <= 0) 100 else newLevel * 150)
            newLevel += 1
        }

        val updatedPlayer = state.player.copy(
            level = newLevel,
            xp = newXp,
            gold = state.player.gold + goldGain,
            ironOre = state.player.ironOre + ironGain,
            emberShards = state.player.emberShards + shardGain,
        )

        val updatedMonsters = if (monXpGain > 0) {
            state.monsters.map { mon ->
                if (mon.id == state.activeMonsterId) {
                    val mXp = mon.xp + monXpGain
                    mon.copy(xp = mXp, level = 1 + (mXp / 80))
                } else {
                    mon
                }
            }
        } else {
            state.monsters
        }

        events.add("${def.name} x$completions: +${goldGain}g, +${ironGain}fe, +${shardGain}sh, +${xpGain}XP.")

        val prevCompletions = activeAct.completionsCount
        val nextCompletions = prevCompletions + completions
        val baseNext = state.copy(
            player = updatedPlayer,
            monsters = updatedMonsters,
            activeActivity = activeAct.copy(
                stepsProgress = remainder,
                completionsCount = nextCompletions,
            ),
        )

        val withSkill = SkillEngine.awardSkillProgress(
            state = baseNext,
            skill = skill,
            xpGain = xpGain,
            previousCompletions = prevCompletions,
            newCompletions = nextCompletions,
            events = events,
        )
        return SparkpediaEngine.syncCodexUnlocks(withSkill)
    }
}
