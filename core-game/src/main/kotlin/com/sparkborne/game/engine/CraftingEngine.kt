package com.sparkborne.game.engine

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.model.ConsumableEffectType
import com.sparkborne.game.model.CraftingRecipe
import com.sparkborne.game.model.ExpeditionStatus
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.MasterworkTier
import com.sparkborne.game.model.MaterialType
import com.sparkborne.game.model.MonsterCompanion
import com.sparkborne.game.model.MonsterStage
import com.sparkborne.game.model.SkillType
import kotlin.math.roundToInt

/**
 * Deterministic Crafting, Cooking & Consumables engine with Skill-scaled Masterwork Quality Tiers.
 */
object CraftingEngine {

    const val STEP_BOOST_EFFICIENCY_PERCENT = 25

    val recipes: List<CraftingRecipe> = listOf(
        CraftingRecipe(
            id = "cons_trailblazer_brew",
            name = "Trailblazer Brew",
            icon = "⚡",
            description = "+25% Work Efficiency for your next 10 Town Activity cycles.",
            craftSkill = SkillType.ALCHEMY,
            requiredMaterials = mapOf(
                MaterialType.MOONLEAF_HERB to 2,
                MaterialType.SILVERFIN_FISH to 1,
            ),
            goldCost = 5,
            rewardCraftXp = 30,
            effectType = ConsumableEffectType.STEP_EFFICIENCY_BOOST,
            basePotency = 10,
        ),
        CraftingRecipe(
            id = "cons_vitality_stew",
            name = "Hearty Silverfin Stew",
            icon = "🍲",
            description = "Cooked meal: restores 45 HP during an active Delve.",
            craftSkill = SkillType.FISHING,
            requiredMaterials = mapOf(
                MaterialType.SILVERFIN_FISH to 2,
                MaterialType.MOONLEAF_HERB to 1,
            ),
            goldCost = 5,
            rewardCraftXp = 28,
            effectType = ConsumableEffectType.DELVE_HEAL_HP,
            basePotency = 45,
        ),
        CraftingRecipe(
            id = "cons_mana_tonic",
            name = "Spore Mana Tonic",
            icon = "🧪",
            description = "Alchemical tonic: restores 18 Mana during an active Delve.",
            craftSkill = SkillType.ALCHEMY,
            requiredMaterials = mapOf(
                MaterialType.MIRE_SPORE to 2,
                MaterialType.MOONLEAF_HERB to 1,
            ),
            goldCost = 8,
            rewardCraftXp = 32,
            effectType = ConsumableEffectType.DELVE_RESTORE_MANA,
            basePotency = 18,
        ),
        CraftingRecipe(
            id = "cons_egg_elixir",
            name = "Warmth & Dew Elixir",
            icon = "🥚",
            description = "Infuses +180m Sleep Warmth & +900mL Dew into your first incubating Egg.",
            craftSkill = SkillType.ALCHEMY,
            requiredMaterials = mapOf(
                MaterialType.SUNSTONE_DUST to 2,
                MaterialType.MIRE_SPORE to 1,
            ),
            goldCost = 12,
            rewardCraftXp = 38,
            effectType = ConsumableEffectType.EGG_INCUBATION_SURGE,
            basePotency = 180,
        ),
        CraftingRecipe(
            id = "cons_astral_flask",
            name = "Astral Surge Flask",
            icon = "🌌",
            description = "Masterwork brew: full HP & Mana restore + 20 Shield in an active Delve.",
            craftSkill = SkillType.SMITHING,
            requiredMaterials = mapOf(
                MaterialType.ASTRAL_ESSENCE to 2,
                MaterialType.BASALT_ORE to 2,
            ),
            goldCost = 25,
            rewardCraftXp = 50,
            effectType = ConsumableEffectType.DELVE_FULL_RESTORE_SHIELD,
            basePotency = 20,
        ),
    )

    fun recipeById(id: String): CraftingRecipe? = recipes.firstOrNull { it.id == id }

    fun materialForSkill(skill: SkillType): MaterialType = when (skill) {
        SkillType.MINING, SkillType.SMITHING, SkillType.SALVAGE -> MaterialType.BASALT_ORE
        SkillType.FORAGING, SkillType.WOODCUTTING -> MaterialType.MOONLEAF_HERB
        SkillType.FISHING, SkillType.DIVING -> MaterialType.SILVERFIN_FISH
        SkillType.ALCHEMY -> MaterialType.MIRE_SPORE
        SkillType.ARCHAEOLOGY, SkillType.TRADE -> MaterialType.SUNSTONE_DUST
        SkillType.RUNECRAFTING -> MaterialType.ASTRAL_ESSENCE
    }

    fun qualityTierFor(state: GameState, recipe: CraftingRecipe): MasterworkTier {
        val lvl = state.player.skillLevel(recipe.craftSkill)
        return MasterworkTier.forLevel(lvl)
    }

    /**
     * Crafts 1 (or 2 at Masterwork tier!) of [recipeId], consuming Materials + Gold and granting Skill XP.
     */
    fun craftRecipe(state: GameState, recipeId: String): Result<GameState> {
        val recipe = recipeById(recipeId)
            ?: return Result.failure(IllegalArgumentException("Unknown recipe: $recipeId"))
        val p = state.player
        if (p.gold < recipe.goldCost) {
            return Result.failure(IllegalStateException("Need ${recipe.goldCost}g to craft ${recipe.name}."))
        }
        for ((mat, needed) in recipe.requiredMaterials) {
            val have = p.materials[mat] ?: 0
            if (have < needed) {
                return Result.failure(IllegalStateException("Need $needed ${mat.displayName} (have $have)."))
            }
        }

        val updatedMaterials = p.materials.toMutableMap()
        for ((mat, needed) in recipe.requiredMaterials) {
            val left = (updatedMaterials[mat] ?: 0) - needed
            if (left > 0) updatedMaterials[mat] = left else updatedMaterials.remove(mat)
        }

        val tier = qualityTierFor(state, recipe)
        val qty = 1 + tier.bonusCraftQty
        val nextConsumables = p.consumables + (recipe.id to ((p.consumables[recipe.id] ?: 0) + qty))
        val prevSkillXp = p.skillXp[recipe.craftSkill] ?: 0
        val nextSkillXp = prevSkillXp + recipe.rewardCraftXp

        val msg = "Crafted ${recipe.icon} ${tier.displayName} ${recipe.name} x$qty (+${recipe.rewardCraftXp} ${recipe.craftSkill.displayName} XP)!"
        val nextState = state.copy(
            player = p.copy(
                gold = p.gold - recipe.goldCost,
                materials = updatedMaterials,
                consumables = nextConsumables,
                skillXp = p.skillXp + (recipe.craftSkill to nextSkillXp),
            ),
            recentLog = (listOf(msg) + state.recentLog).take(30),
        )
        return Result.success(SparkpediaEngine.syncCodexUnlocks(nextState))
    }

    /**
     * Uses 1 of [recipeId] from the player's Consumables pouch, scaling potency by Masterwork tier!
     */
    fun useConsumable(state: GameState, recipeId: String): Result<GameState> {
        val recipe = recipeById(recipeId)
            ?: return Result.failure(IllegalArgumentException("Unknown consumable: $recipeId"))
        val p = state.player
        val count = p.consumables[recipeId] ?: 0
        if (count <= 0) {
            return Result.failure(IllegalStateException("You have no ${recipe.name} left. Craft more in Hero → Craft!"))
        }

        val tier = qualityTierFor(state, recipe)
        val potency = (recipe.basePotency * tier.potencyMultiplier).roundToInt()

        val nextConsumables = if (count > 1) {
            p.consumables + (recipeId to (count - 1))
        } else {
            p.consumables - recipeId
        }

        return when (recipe.effectType) {
            ConsumableEffectType.STEP_EFFICIENCY_BOOST -> {
                val msg = "Drank ${recipe.icon} ${recipe.name}: +$STEP_BOOST_EFFICIENCY_PERCENT% Work Efficiency for next $potency cycles!"
                Result.success(
                    state.copy(
                        player = p.copy(
                            consumables = nextConsumables,
                            activeStepBoostCharges = p.activeStepBoostCharges + potency,
                        ),
                        recentLog = (listOf(msg) + state.recentLog).take(30),
                    ),
                )
            }

            ConsumableEffectType.DELVE_HEAL_HP -> {
                val exp = state.activeExpedition
                    ?: return Result.failure(IllegalStateException("Start an active Delve before eating ${recipe.name}."))
                if (exp.status != ExpeditionStatus.ACTIVE) {
                    return Result.failure(IllegalStateException("No active Delve to heal in."))
                }
                val nextRunHp = (exp.runPlayerHp + potency).coerceAtMost(exp.runPlayerMaxHp)
                val nextCombat = exp.activeCombat?.let { c ->
                    c.copy(
                        playerHp = (c.playerHp + potency).coerceAtMost(c.playerMaxHp),
                        combatLog = (listOf("Ate ${recipe.name}: +$potency HP!") + c.combatLog).take(12),
                    )
                }
                val msg = "Ate ${recipe.icon} ${recipe.name}: restored +$potency HP in Delve!"
                Result.success(
                    state.copy(
                        player = p.copy(consumables = nextConsumables),
                        activeExpedition = exp.copy(runPlayerHp = nextRunHp, activeCombat = nextCombat),
                        recentLog = (listOf(msg) + state.recentLog).take(30),
                    ),
                )
            }

            ConsumableEffectType.DELVE_RESTORE_MANA -> {
                val exp = state.activeExpedition
                    ?: return Result.failure(IllegalStateException("Start an active Delve before drinking ${recipe.name}."))
                if (exp.status != ExpeditionStatus.ACTIVE) {
                    return Result.failure(IllegalStateException("No active Delve to restore Mana in."))
                }
                val nextRunMana = (exp.runPlayerMana + potency).coerceAtMost(exp.runPlayerMaxMana)
                val nextCombat = exp.activeCombat?.let { c ->
                    c.copy(
                        playerMana = (c.playerMana + potency).coerceAtMost(c.playerMaxMana),
                        combatLog = (listOf("Drank ${recipe.name}: +$potency Mana!") + c.combatLog).take(12),
                    )
                }
                val msg = "Drank ${recipe.icon} ${recipe.name}: restored +$potency Mana in Delve!"
                Result.success(
                    state.copy(
                        player = p.copy(consumables = nextConsumables),
                        activeExpedition = exp.copy(runPlayerMana = nextRunMana, activeCombat = nextCombat),
                        recentLog = (listOf(msg) + state.recentLog).take(30),
                    ),
                )
            }

            ConsumableEffectType.EGG_INCUBATION_SURGE -> {
                val firstEgg = state.eggs.firstOrNull()
                    ?: return Result.failure(IllegalStateException("You have no incubating Monster Eggs."))
                val sleepBoost = potency
                val waterBoost = potency * 5
                val boosted = firstEgg.copy(
                    incubatedSleepMinutes = firstEgg.incubatedSleepMinutes + sleepBoost,
                    incubatedHydrationMl = firstEgg.incubatedHydrationMl + waterBoost,
                )
                val remaining = state.eggs.drop(1)
                if (boosted.isReadyToHatch) {
                    val affinity = boosted.macroProfile.dominantAffinity()
                    val species = GameCatalog.monsterSpeciesName(affinity, MonsterStage.HATCHLING)
                    val hatched = MonsterCompanion(
                        id = "mon_${boosted.id}",
                        name = species,
                        speciesId = species.lowercase().replace(' ', '_'),
                        stage = MonsterStage.HATCHLING,
                        level = 1,
                        xp = 0,
                        affinity = affinity,
                        baseAttack = 8,
                        baseDefense = 4,
                        macroProfile = boosted.macroProfile,
                        evolutionHistory = listOf(species),
                    )
                    val msg = "Used ${recipe.icon} ${recipe.name}: ${hatched.name} HATCHED!"
                    Result.success(
                        SparkpediaEngine.syncCodexUnlocks(
                            state.copy(
                                player = p.copy(consumables = nextConsumables),
                                eggs = remaining,
                                monsters = state.monsters + hatched,
                                activeMonsterId = state.activeMonsterId ?: hatched.id,
                                recentLog = (listOf(msg) + state.recentLog).take(30),
                            ),
                        ),
                    )
                } else {
                    val msg = "Used ${recipe.icon} ${recipe.name}: +${sleepBoost}m Sleep & +${waterBoost}mL Dew on ${firstEgg.name}!"
                    Result.success(
                        state.copy(
                            player = p.copy(consumables = nextConsumables),
                            eggs = listOf(boosted) + remaining,
                            recentLog = (listOf(msg) + state.recentLog).take(30),
                        ),
                    )
                }
            }

            ConsumableEffectType.DELVE_FULL_RESTORE_SHIELD -> {
                val exp = state.activeExpedition
                    ?: return Result.failure(IllegalStateException("Start an active Delve before drinking ${recipe.name}."))
                if (exp.status != ExpeditionStatus.ACTIVE) {
                    return Result.failure(IllegalStateException("No active Delve to use ${recipe.name} in."))
                }
                val nextCombat = exp.activeCombat?.let { c ->
                    c.copy(
                        playerHp = c.playerMaxHp,
                        playerMana = c.playerMaxMana,
                        playerShield = c.playerShield + potency,
                        combatLog = (listOf("Drank ${recipe.name}: Full HP/Mana + $potency Shield!") + c.combatLog).take(12),
                    )
                }
                val msg = "Drank ${recipe.icon} ${recipe.name}: Full HP & Mana + $potency Shield!"
                Result.success(
                    state.copy(
                        player = p.copy(consumables = nextConsumables),
                        activeExpedition = exp.copy(
                            runPlayerHp = exp.runPlayerMaxHp,
                            runPlayerMana = exp.runPlayerMaxMana,
                            activeCombat = nextCombat,
                        ),
                        recentLog = (listOf(msg) + state.recentLog).take(30),
                    ),
                )
            }
        }
    }
}
