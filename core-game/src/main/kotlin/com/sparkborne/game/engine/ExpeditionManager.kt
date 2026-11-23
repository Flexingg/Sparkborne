package com.sparkborne.game.engine

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.model.ActiveCombatState
import com.sparkborne.game.model.ActiveExpedition
import com.sparkborne.game.model.CombatEnemy
import com.sparkborne.game.model.DelveNode
import com.sparkborne.game.model.DelveNodeType
import com.sparkborne.game.model.ExpeditionContract
import com.sparkborne.game.model.ExpeditionStatus
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.MonsterStage
import java.time.LocalDate
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic manager for starting/advancing expeditions, enforcing deadlines,
 * forging weapons/armor, unlocking/attuning spells, and evolving monsters.
 */
object ExpeditionManager {

    fun startExpedition(
        state: GameState,
        contractId: String,
        startDate: LocalDate,
        seed: Long = startDate.toEpochDay(),
    ): Result<GameState> {
        if (state.activeExpedition?.status == ExpeditionStatus.ACTIVE) {
            return Result.failure(IllegalStateException("An expedition is already active. Complete or abandon it first."))
        }
        val contract = GameCatalog.contractById(contractId)
            ?: return Result.failure(IllegalArgumentException("Unknown contract: $contractId"))

        val stats = CombatStatsCalculator.calculate(state)
        val deadline = startDate.plusDays((contract.durationDays - 1).toLong().coerceAtLeast(0L))
        val nodes = buildNodes(contract)

        val run = ActiveExpedition(
            runId = "${contract.id}_${startDate}_$seed",
            contract = contract,
            startedDate = startDate.toString(),
            deadlineDate = deadline.toString(),
            seed = seed,
            nodes = nodes,
            currentNodeIndex = 0,
            runPlayerHp = stats.maxHp,
            runPlayerMaxHp = stats.maxHp,
            runPlayerMana = stats.maxMana,
            runPlayerMaxMana = stats.maxMana,
        )

        return Result.success(
            state.copy(
                activeExpedition = run,
                recentLog = (listOf("Accepted contract: ${contract.title} (deadline $deadline).") + state.recentLog).take(30),
            ),
        )
    }

    /**
     * Enters the current unlocked node: starts combat for COMBAT/BOSS or resolves shrines/camps/treasure.
     */
    fun enterCurrentNode(state: GameState): Result<GameState> {
        val exp = state.activeExpedition
            ?: return Result.failure(IllegalStateException("No active expedition."))
        if (exp.status != ExpeditionStatus.ACTIVE) {
            return Result.failure(IllegalStateException("Expedition is not active."))
        }
        if (exp.activeCombat != null) {
            return Result.failure(IllegalStateException("Combat is already in progress."))
        }
        val node = exp.currentNode
            ?: return Result.failure(IllegalStateException("No remaining nodes."))
        if (!node.isUnlocked) {
            return Result.failure(
                IllegalStateException("Need ${node.stepsRequired - node.stepsApplied} more steps to unlock ${node.title}."),
            )
        }

        if (node.type == DelveNodeType.BOSS && !exp.meetsContractRequirements()) {
            return Result.failure(
                IllegalStateException("Contract requirements not yet met to challenge ${exp.contract.bossName}."),
            )
        }

        return when (node.type) {
            DelveNodeType.COMBAT, DelveNodeType.BOSS -> {
                val enemy = node.enemy ?: return Result.failure(IllegalStateException("Missing enemy on combat node."))
                val burnCost = ActionBankManager.combatEntryBurnCost(isBoss = node.type == DelveNodeType.BOSS)
                val afterBurn = ActionBankManager.withdraw(
                    state = state,
                    bank = com.sparkborne.game.model.ActionBankType.CALORIES_BURNT,
                    amount = burnCost,
                ).getOrElse { return Result.failure(it) }

                val stats = CombatStatsCalculator.calculate(afterBurn)
                val maxHp = maxOf(exp.runPlayerMaxHp, stats.maxHp)
                val maxMana = maxOf(exp.runPlayerMaxMana, stats.maxMana)
                val combat = ActiveCombatState(
                    nodeIndex = node.index,
                    enemy = enemy,
                    playerHp = min(exp.runPlayerHp, maxHp),
                    playerMaxHp = maxHp,
                    playerMana = min(exp.runPlayerMana, maxMana),
                    playerMaxMana = maxMana,
                    combatLog = listOf("Spent 🔥$burnCost kcal · Encountered ${enemy.name} (HP ${enemy.maxHp}, ATK ${enemy.attack})!"),
                )
                Result.success(
                    afterBurn.copy(
                        activeExpedition = exp.copy(
                            runPlayerMaxHp = maxHp,
                            runPlayerMaxMana = maxMana,
                            activeCombat = combat,
                        ),
                    ),
                )
            }

            DelveNodeType.REST_CAMP -> {
                val healedHp = min(exp.runPlayerMaxHp, exp.runPlayerHp + (exp.runPlayerMaxHp * 0.40).roundToInt())
                val restoredMana = exp.runPlayerMaxMana
                val updatedNodes = exp.nodes.map { if (it.index == node.index) it.copy(cleared = true) else it }
                Result.success(
                    state.copy(
                        activeExpedition = exp.copy(
                            nodes = updatedNodes,
                            currentNodeIndex = min(exp.nodes.lastIndex, node.index + 1),
                            runPlayerHp = healedHp,
                            runPlayerMana = restoredMana,
                        ),
                        recentLog = (listOf("Rested at ${node.title}: restored HP to $healedHp & full Mana.") + state.recentLog).take(30),
                    ),
                )
            }

            DelveNodeType.FORGE_SHRINE, DelveNodeType.TREASURE -> {
                val mult = exp.contract.difficulty.rewardMultiplier
                val gold = (20 * mult).roundToInt()
                val iron = (6 * mult).roundToInt()
                val shards = (3 * mult).roundToInt()
                val updatedNodes = exp.nodes.map { if (it.index == node.index) it.copy(cleared = true) else it }
                Result.success(
                    state.copy(
                        activeExpedition = exp.copy(
                            nodes = updatedNodes,
                            currentNodeIndex = min(exp.nodes.lastIndex, node.index + 1),
                            unbankedLoot = exp.unbankedLoot.copy(
                                gold = exp.unbankedLoot.gold + gold,
                                ironOre = exp.unbankedLoot.ironOre + iron,
                                emberShards = exp.unbankedLoot.emberShards + shards,
                            ),
                        ),
                        recentLog = (listOf("Looted ${node.title}: +${gold}g, +$iron iron, +$shards shards (unbanked).") + state.recentLog).take(30),
                    ),
                )
            }
        }
    }

    /**
     * Enforces contract deadline: if `currentDate > deadlineDate` while active, fails the run
     * and wipes all unbanked loot.
     */
    fun checkContractDeadline(state: GameState, currentDate: LocalDate): GameState {
        val exp = state.activeExpedition ?: return state
        if (exp.status != ExpeditionStatus.ACTIVE) return state
        val deadline = LocalDate.parse(exp.deadlineDate)
        return if (currentDate.isAfter(deadline)) {
            CombatResolver.failExpeditionAndWipeLoot(
                state = state,
                expedition = exp,
                reason = "Missed contract deadline (${exp.deadlineDate}) for ${exp.contract.title}. All unbanked loot lost.",
            )
        } else {
            state
        }
    }

    fun abandonExpedition(state: GameState): GameState {
        val exp = state.activeExpedition ?: return state
        if (exp.status != ExpeditionStatus.ACTIVE) return state
        return CombatResolver.failExpeditionAndWipeLoot(
            state = state,
            expedition = exp,
            reason = "Abandoned ${exp.contract.title}. All unbanked loot forfeited.",
        )
    }

    fun clearFinishedExpedition(state: GameState): GameState {
        val exp = state.activeExpedition ?: return state
        if (exp.status == ExpeditionStatus.ACTIVE) return state
        return state.copy(activeExpedition = null)
    }

    fun forgeAndEquipWeapon(state: GameState, weaponId: String): Result<GameState> {
        val weapon = GameCatalog.weaponById(weaponId)
        val player = state.player
        if (weapon.id in player.ownedWeaponIds) {
            return Result.success(
                state.copy(
                    player = player.copy(equippedWeaponId = weapon.id),
                    recentLog = (listOf("Equipped ${weapon.name}.") + state.recentLog).take(30),
                ),
            )
        }
        if (player.gold < weapon.forgeGoldCost || player.ironOre < weapon.forgeIronCost) {
            return Result.failure(
                IllegalStateException("Need ${weapon.forgeGoldCost}g & ${weapon.forgeIronCost} iron to forge ${weapon.name}."),
            )
        }
        return Result.success(
            state.copy(
                player = player.copy(
                    gold = player.gold - weapon.forgeGoldCost,
                    ironOre = player.ironOre - weapon.forgeIronCost,
                    ownedWeaponIds = player.ownedWeaponIds + weapon.id,
                    equippedWeaponId = weapon.id,
                ),
                recentLog = (listOf("Forged & equipped ${weapon.name}!") + state.recentLog).take(30),
            ),
        )
    }

    fun forgeAndEquipArmor(state: GameState, armorId: String): Result<GameState> {
        val armor = GameCatalog.armorById(armorId)
            ?: return Result.failure(IllegalArgumentException("Unknown armor: $armorId"))
        val player = state.player
        if (armor.id in player.ownedArmorIds) {
            return Result.success(
                state.copy(
                    player = player.copy(equippedArmor = player.equippedArmor + (armor.slot to armor.id)),
                    recentLog = (listOf("Equipped ${armor.name}.") + state.recentLog).take(30),
                ),
            )
        }
        if (player.gold < armor.forgeGoldCost ||
            player.ironOre < armor.forgeIronCost ||
            player.emberShards < armor.forgeShardsCost
        ) {
            return Result.failure(
                IllegalStateException("Insufficient materials for ${armor.name}."),
            )
        }
        return Result.success(
            state.copy(
                player = player.copy(
                    gold = player.gold - armor.forgeGoldCost,
                    ironOre = player.ironOre - armor.forgeIronCost,
                    emberShards = player.emberShards - armor.forgeShardsCost,
                    ownedArmorIds = player.ownedArmorIds + armor.id,
                    equippedArmor = player.equippedArmor + (armor.slot to armor.id),
                ),
                recentLog = (listOf("Forged & equipped ${armor.name}!") + state.recentLog).take(30),
            ),
        )
    }

    fun unlockAndAttuneSpell(state: GameState, spellId: String): Result<GameState> {
        val spell = GameCatalog.spellById(spellId)
            ?: return Result.failure(IllegalArgumentException("Unknown spell: $spellId"))
        val player = state.player
        if (spell.id in player.unlockedSpellIds) {
            val nextAttuned = if (spell.id in player.attunedSpellIds) {
                player.attunedSpellIds
            } else {
                (player.attunedSpellIds + spell.id).takeLast(3)
            }
            return Result.success(state.copy(player = player.copy(attunedSpellIds = nextAttuned)))
        }
        if (player.emberShards < spell.unlockShardsCost) {
            return Result.failure(
                IllegalStateException("Need ${spell.unlockShardsCost} Ember Shards to unlock ${spell.name}."),
            )
        }
        return Result.success(
            state.copy(
                player = player.copy(
                    emberShards = player.emberShards - spell.unlockShardsCost,
                    unlockedSpellIds = player.unlockedSpellIds + spell.id,
                    attunedSpellIds = (player.attunedSpellIds + spell.id).takeLast(3),
                ),
                recentLog = (listOf("Unlocked & attuned spell: ${spell.name}!") + state.recentLog).take(30),
            ),
        )
    }

    fun selectActiveMonster(state: GameState, monsterId: String): GameState {
        if (state.monsters.none { it.id == monsterId }) return state
        return state.copy(activeMonsterId = monsterId)
    }

    fun evolveMonster(state: GameState, monsterId: String): Result<GameState> {
        val monster = state.monsters.firstOrNull { it.id == monsterId }
            ?: return Result.failure(IllegalArgumentException("Monster not found."))
        if (!monster.canEvolve) {
            return Result.failure(IllegalStateException("${monster.name} does not have enough XP to evolve yet."))
        }
        val nextStage = when (monster.stage) {
            MonsterStage.HATCHLING -> MonsterStage.JUVENILE
            MonsterStage.JUVENILE -> MonsterStage.APEX
            MonsterStage.APEX -> return Result.failure(IllegalStateException("${monster.name} is already Apex."))
        }
        val nextAffinity = if (monster.macroProfile.daysTracked > 0) {
            monster.macroProfile.dominantAffinity()
        } else {
            monster.affinity
        }
        val nextSpecies = GameCatalog.monsterSpeciesName(nextAffinity, nextStage)
        val evolved = monster.copy(
            name = nextSpecies,
            speciesId = nextSpecies.lowercase().replace(' ', '_'),
            stage = nextStage,
            affinity = nextAffinity,
            baseAttack = monster.baseAttack + 5,
            baseDefense = monster.baseDefense + 3,
            evolutionHistory = monster.evolutionHistory + nextSpecies,
        )

        return Result.success(
            state.copy(
                monsters = state.monsters.map { if (it.id == monsterId) evolved else it },
                recentLog = (listOf("Evolution! ${monster.name} evolved into $nextSpecies (${nextAffinity.displayName})!") + state.recentLog).take(30),
            ),
        )
    }

    private fun buildNodes(contract: ExpeditionContract): List<DelveNode> {
        val mult = contract.difficulty.rewardMultiplier
        return (0 until contract.totalNodes).map { idx ->
            when {
                idx == contract.totalNodes - 1 -> DelveNode(
                    index = idx,
                    type = DelveNodeType.BOSS,
                    title = "Boss: ${contract.bossName}",
                    stepsRequired = contract.stepsPerNode,
                    enemy = CombatEnemy(
                        name = contract.bossName,
                        maxHp = contract.bossHp,
                        currentHp = contract.bossHp,
                        attack = contract.bossAttack,
                        defense = contract.bossDefense,
                        isBoss = true,
                    ),
                )
                idx > 0 && idx % 3 == 2 -> DelveNode(
                    index = idx,
                    type = DelveNodeType.REST_CAMP,
                    title = "Ember Campfire",
                    stepsRequired = contract.stepsPerNode,
                )
                idx % 2 == 1 -> DelveNode(
                    index = idx,
                    type = DelveNodeType.FORGE_SHRINE,
                    title = "Ancient Forge Cache",
                    stepsRequired = contract.stepsPerNode,
                )
                else -> {
                    val hp = (36 * mult + idx * 8).roundToInt()
                    val atk = (8 * mult + idx * 2).roundToInt()
                    val def = (2 * mult + idx).roundToInt()
                    DelveNode(
                        index = idx,
                        type = DelveNodeType.COMBAT,
                        title = "Delve Skirmish #${idx + 1}",
                        stepsRequired = contract.stepsPerNode,
                        enemy = CombatEnemy(
                            name = "Ashen Stalker Mk.${idx + 1}",
                            maxHp = hp,
                            currentHp = hp,
                            attack = atk,
                            defense = def,
                            isBoss = false,
                        ),
                    )
                }
            }
        }
    }
}
