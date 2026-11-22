package com.sparkborne.game.engine

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.model.ActiveCombatState
import com.sparkborne.game.model.ActiveExpedition
import com.sparkborne.game.model.ExpeditionStatus
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.LootBag
import com.sparkborne.game.model.MonsterAffinity
import com.sparkborne.game.model.MonsterEgg
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

sealed class CombatAction {
    data object Strike : CombatAction()
    data class CastSpell(val spellId: String) : CombatAction()
    data object Guard : CombatAction()
    data object MonsterSkill : CombatAction()
}

/**
 * Deterministic turn-based combat resolver for Sparkborne delves.
 *
 * - Victory adds node loot into `unbankedLoot`. Clearing the boss node banks all `unbankedLoot`.
 * - Defeat (`playerHp <= 0`) fails the expedition and WIPES all `unbankedLoot`.
 */
object CombatResolver {

    fun performAction(state: GameState, action: CombatAction): GameState {
        val expedition = state.activeExpedition ?: return state
        if (expedition.status != ExpeditionStatus.ACTIVE) return state
        val combat = expedition.activeCombat ?: return state

        val stats = CombatStatsCalculator.calculate(state)
        val monster = state.activeMonster
        val log = combat.combatLog.toMutableList()

        var enemyHp = combat.enemy.currentHp
        var playerHp = combat.playerHp
        var playerMana = combat.playerMana
        var playerShield = combat.playerShield
        var monsterCooldown = max(0, combat.monsterSkillCooldown - 1)
        var guardingThisTurn = false

        when (action) {
            is CombatAction.Strike -> {
                val raw = max(1, stats.weaponDamage - combat.enemy.defense)
                enemyHp = max(0, enemyHp - raw)
                playerMana = min(combat.playerMaxMana, playerMana + 2)
                log.add("Turn ${combat.turnNumber}: You strike for $raw dmg (+2 Mana).")
            }

            is CombatAction.CastSpell -> {
                val spell = GameCatalog.spellById(action.spellId)
                if (spell == null || action.spellId !in state.player.attunedSpellIds) {
                    log.add("Spell not attuned.")
                    return state
                }
                if (playerMana < spell.manaCost) {
                    log.add("Not enough Mana for ${spell.name} (need ${spell.manaCost}).")
                    return state
                }
                playerMana -= spell.manaCost
                if (spell.basePower > 0) {
                    val dmg = max(1, spell.basePower + stats.spellPower - (combat.enemy.defense / 2))
                    enemyHp = max(0, enemyHp - dmg)
                    log.add("Turn ${combat.turnNumber}: Cast ${spell.name} for $dmg magic dmg!")
                }
                if (spell.healAmount > 0) {
                    val healed = min(combat.playerMaxHp - playerHp, spell.healAmount + (stats.spellPower / 2))
                    playerHp += healed
                    log.add("Turn ${combat.turnNumber}: ${spell.name} restores +$healed HP.")
                }
                if (spell.shieldAmount > 0) {
                    playerShield += spell.shieldAmount
                    log.add("Turn ${combat.turnNumber}: ${spell.name} grants +${spell.shieldAmount} Shield.")
                }
            }

            is CombatAction.Guard -> {
                guardingThisTurn = true
                playerShield += max(4, stats.defense / 2)
                playerMana = min(combat.playerMaxMana, playerMana + 5)
                log.add("Turn ${combat.turnNumber}: You brace (+${max(4, stats.defense / 2)} Shield, +5 Mana).")
            }

            is CombatAction.MonsterSkill -> {
                if (monster == null || combat.monsterSkillCooldown > 0) {
                    log.add("Companion skill is on cooldown.")
                    return state
                }
                monsterCooldown = 3
                val skillDmg = max(1, stats.monsterSkillDamage - (combat.enemy.defense / 2))
                enemyHp = max(0, enemyHp - skillDmg)
                when (monster.affinity) {
                    MonsterAffinity.IRONCLAD -> {
                        val shield = 12 + monster.level * 2
                        playerShield += shield
                        log.add("Turn ${combat.turnNumber}: ${monster.name} slams for $skillDmg dmg & shields you (+$shield)!")
                    }
                    MonsterAffinity.STORMSTRIDER -> {
                        playerMana = min(combat.playerMaxMana, playerMana + 6)
                        log.add("Turn ${combat.turnNumber}: ${monster.name} unleashes arc lightning ($skillDmg dmg, +6 Mana)!")
                    }
                    MonsterAffinity.DEEPFORGE -> {
                        val heal = min(combat.playerMaxHp - playerHp, 14 + monster.level * 2)
                        playerHp += heal
                        log.add("Turn ${combat.turnNumber}: ${monster.name} strikes ($skillDmg dmg) & mends +$heal HP!")
                    }
                    MonsterAffinity.EMBERHEART -> {
                        val burst = (skillDmg * 0.4).roundToInt()
                        enemyHp = max(0, enemyHp - burst)
                        log.add("Turn ${combat.turnNumber}: ${monster.name} ignites for ${skillDmg + burst} total dmg!")
                    }
                }
            }
        }

        // Check enemy defeat
        if (enemyHp <= 0) {
            log.add("Victory! Defeated ${combat.enemy.name}.")
            return resolveNodeVictory(
                state = state,
                expedition = expedition,
                combat = combat.copy(
                    enemy = combat.enemy.copy(currentHp = 0),
                    playerHp = playerHp,
                    playerMana = playerMana,
                    combatLog = log.takeLast(12),
                ),
            )
        }

        // Enemy counter-attack
        val rawIncoming = max(1, combat.enemy.attack - (stats.defense / 2))
        val mitigatedIncoming = if (guardingThisTurn) max(1, rawIncoming / 2) else rawIncoming
        val absorbedByShield = min(playerShield, mitigatedIncoming)
        playerShield -= absorbedByShield
        val hpDamage = mitigatedIncoming - absorbedByShield
        playerHp = max(0, playerHp - hpDamage)
        log.add("${combat.enemy.name} hits for $hpDamage HP" + if (absorbedByShield > 0) " ($absorbedByShield blocked)." else ".")

        // Check player defeat -> roguelike wipe!
        if (playerHp <= 0) {
            return failExpeditionAndWipeLoot(
                state = state,
                expedition = expedition,
                reason = "Defeated by ${combat.enemy.name} on node ${combat.nodeIndex + 1}. All unbanked loot lost.",
            )
        }

        val nextCombat = combat.copy(
            enemy = combat.enemy.copy(currentHp = enemyHp),
            playerHp = playerHp,
            playerMana = playerMana,
            playerShield = playerShield,
            monsterSkillCooldown = monsterCooldown,
            turnNumber = combat.turnNumber + 1,
            combatLog = log.takeLast(12),
        )

        return state.copy(
            activeExpedition = expedition.copy(
                runPlayerHp = playerHp,
                runPlayerMana = playerMana,
                activeCombat = nextCombat,
            ),
        )
    }

    private fun resolveNodeVictory(
        state: GameState,
        expedition: ActiveExpedition,
        combat: ActiveCombatState,
    ): GameState {
        val mult = expedition.contract.difficulty.rewardMultiplier
        val nodeGold = (12 * mult).roundToInt()
        val nodeShards = (2 * mult).roundToInt()
        val nodeIron = (3 * mult).roundToInt()
        val nodeXp = (25 * mult).roundToInt()

        val updatedLoot = expedition.unbankedLoot.copy(
            gold = expedition.unbankedLoot.gold + nodeGold,
            emberShards = expedition.unbankedLoot.emberShards + nodeShards,
            ironOre = expedition.unbankedLoot.ironOre + nodeIron,
            xp = expedition.unbankedLoot.xp + nodeXp,
        )

        val updatedNodes = expedition.nodes.map { node ->
            if (node.index == combat.nodeIndex) node.copy(cleared = true) else node
        }

        val isFinalBoss = combat.enemy.isBoss || combat.nodeIndex == expedition.nodes.lastIndex
        if (isFinalBoss) {
            return completeExpeditionAndBankLoot(
                state = state,
                expedition = expedition.copy(
                    nodes = updatedNodes,
                    runPlayerHp = combat.playerHp,
                    runPlayerMana = combat.playerMana,
                    activeCombat = null,
                    unbankedLoot = updatedLoot,
                ),
            )
        }

        val nextExp = expedition.copy(
            nodes = updatedNodes,
            currentNodeIndex = min(expedition.nodes.lastIndex, combat.nodeIndex + 1),
            runPlayerHp = combat.playerHp,
            runPlayerMana = combat.playerMana,
            activeCombat = null,
            unbankedLoot = updatedLoot,
        )

        return state.copy(
            activeExpedition = nextExp,
            recentLog = (listOf("Cleared ${combat.enemy.name}! +${nodeGold}g, +$nodeShards shards, +$nodeIron iron (unbanked).") + state.recentLog).take(30),
        )
    }

    internal fun completeExpeditionAndBankLoot(
        state: GameState,
        expedition: ActiveExpedition,
    ): GameState {
        val contract = expedition.contract
        val bonusEgg = if (contract.grantsEggOnClear) {
            listOf(
                MonsterEgg(
                    id = "egg_${expedition.runId}",
                    name = "${contract.title} Egg",
                    speciesSeed = expedition.runId,
                    requiredSleepMinutes = 900,
                    requiredHydrationMl = 4500,
                ),
            )
        } else {
            emptyList()
        }

        val totalGold = expedition.unbankedLoot.gold + contract.baseRewardGold
        val totalShards = expedition.unbankedLoot.emberShards + contract.baseRewardShards
        val totalIron = expedition.unbankedLoot.ironOre + contract.baseRewardIron
        val totalXp = expedition.unbankedLoot.xp + (60 * contract.difficulty.rewardMultiplier).roundToInt()
        val totalEggs = expedition.unbankedLoot.eggs + bonusEgg

        var newLevel = state.player.level
        var newXp = state.player.xp + totalXp
        while (newXp >= (if (newLevel <= 0) 100 else newLevel * 150)) {
            newXp -= (if (newLevel <= 0) 100 else newLevel * 150)
            newLevel += 1
        }

        val updatedPlayer = state.player.copy(
            level = newLevel,
            xp = newXp,
            gold = state.player.gold + totalGold,
            emberShards = state.player.emberShards + totalShards,
            ironOre = state.player.ironOre + totalIron,
        )

        val updatedMonsters = state.monsters.map { mon ->
            if (mon.id == state.activeMonsterId) {
                val monXp = mon.xp + (totalXp / 2)
                mon.copy(xp = monXp, level = 1 + (monXp / 80))
            } else {
                mon
            }
        }

        val summary = "Expedition cleared: ${contract.title}! Banked +${totalGold}g, +$totalShards shards, +$totalIron iron" +
            if (totalEggs.isNotEmpty()) ", +${totalEggs.size} egg(s)!" else "."

        var nextState = state.copy(
            player = updatedPlayer,
            monsters = updatedMonsters,
            eggs = state.eggs + totalEggs,
            activeExpedition = expedition.copy(
                status = ExpeditionStatus.COMPLETED,
                activeCombat = null,
            ),
            completedContractIds = state.completedContractIds + contract.id,
            totalRunsCompleted = state.totalRunsCompleted + 1,
            recentLog = (listOf(summary) + state.recentLog).take(30),
        )

        if (contract.id == SparkpediaEngine.INTRO_CONTRACT_ID) {
            nextState = SparkpediaEngine.onIntroContractCleared(nextState)
        }

        return SparkpediaEngine.syncCodexUnlocks(nextState)
    }

    internal fun failExpeditionAndWipeLoot(
        state: GameState,
        expedition: ActiveExpedition,
        reason: String,
    ): GameState {
        return state.copy(
            activeExpedition = expedition.copy(
                status = ExpeditionStatus.FAILED,
                activeCombat = null,
                unbankedLoot = LootBag(),
                failureReason = reason,
            ),
            totalRunsFailed = state.totalRunsFailed + 1,
            recentLog = (listOf("RUN FAILED: $reason") + state.recentLog).take(30),
        )
    }
}
