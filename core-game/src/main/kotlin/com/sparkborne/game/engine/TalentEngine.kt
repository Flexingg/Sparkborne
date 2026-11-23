package com.sparkborne.game.engine

import com.sparkborne.game.catalog.TalentCatalog
import com.sparkborne.game.catalog.WorldCatalog
import com.sparkborne.game.model.ExpeditionStatus
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.PlayerState
import com.sparkborne.game.model.SkillType
import com.sparkborne.game.model.TalentAbility
import com.sparkborne.game.model.TalentNode
import com.sparkborne.game.model.TalentStat
import com.sparkborne.game.model.TalentState
import com.sparkborne.game.model.TalentTree
import kotlin.math.roundToInt

/**
 * Talent points, learning rules and the Shrine of Unbinding reset.
 *
 * Points: 1 per Hero Level + 1 per 5 Skill Levels gained (beyond Lv.1 in every skill).
 * Tier gate: a tier-N talent needs N x [TIER_GATE] points already spent in its tree.
 */
object TalentEngine {

    const val TIER_GATE = 2
    const val SKILL_LEVELS_PER_POINT = 5
    private const val RESET_BASE_COST = 60
    private const val RESET_MAX_COST = 400

    fun pointsFromHeroLevel(p: PlayerState): Int = p.level.coerceAtLeast(0)

    fun pointsFromSkills(p: PlayerState): Int =
        (p.totalSkillLevel() - SkillType.entries.size).coerceAtLeast(0) / SKILL_LEVELS_PER_POINT

    fun pointsEarned(p: PlayerState): Int = pointsFromHeroLevel(p) + pointsFromSkills(p)

    fun pointsSpent(p: PlayerState): Int = p.talents.ranks.values.sum()

    fun pointsAvailable(p: PlayerState): Int = (pointsEarned(p) - pointsSpent(p)).coerceAtLeast(0)

    fun rank(p: PlayerState, nodeId: String): Int = p.talents.ranks[nodeId] ?: 0

    fun spentInTree(p: PlayerState, tree: TalentTree): Int =
        TalentCatalog.nodesIn(tree).sumOf { rank(p, it.id) }

    fun pointsNeededForTier(tier: Int): Int = tier * TIER_GATE

    /** The node that sealed [node]'s exclusive group, if a rival was chosen. */
    fun sealedBy(p: PlayerState, node: TalentNode): TalentNode? {
        val group = node.exclusiveGroup ?: return null
        return TalentCatalog.nodes.firstOrNull { it.exclusiveGroup == group && it.id != node.id && rank(p, it.id) > 0 }
    }

    /** Why [node] cannot gain a rank right now, or null when it can. */
    fun lockReason(p: PlayerState, node: TalentNode): String? {
        val r = rank(p, node.id)
        val need = pointsNeededForTier(node.tier)
        val spent = spentInTree(p, node.tree)
        return when {
            r >= node.maxRank -> "Mastered"
            sealedBy(p, node) != null -> "Sealed: you walk the path of ${sealedBy(p, node)!!.name}"
            node.requires != null && rank(p, node.requires) <= 0 ->
                "Requires ${TalentCatalog.nodeById(node.requires)?.name ?: node.requires}"
            spent < need -> "Spend ${need - spent} more point(s) in ${node.tree.displayName}"
            pointsAvailable(p) <= 0 -> "No talent points available"
            else -> null
        }
    }

    fun learn(state: GameState, nodeId: String): Result<GameState> {
        val node = TalentCatalog.nodeById(nodeId)
            ?: return Result.failure(IllegalArgumentException("Unknown talent: $nodeId"))
        val p = state.player
        lockReason(p, node)?.let { return Result.failure(IllegalStateException(it)) }
        val nextRank = rank(p, node.id) + 1
        val msg = if (node.ability != null && nextRank == 1) {
            "${node.icon} Learned ${node.name}: ${node.ability.description}"
        } else {
            "${node.icon} ${node.name} rank $nextRank/${node.maxRank}."
        }
        return Result.success(
            state.copy(
                player = p.copy(talents = p.talents.copy(ranks = p.talents.ranks + (node.id to nextRank))),
                recentLog = (listOf(msg) + state.recentLog).take(30),
            ),
        )
    }

    fun resetCost(p: PlayerState): Int =
        if (p.talents.resets == 0) 0 else (RESET_BASE_COST * p.talents.resets).coerceAtMost(RESET_MAX_COST)

    fun isAtShrine(state: GameState): Boolean =
        state.currentLocationId == TalentCatalog.SHRINE_LOCATION_ID && state.activeTravel == null

    /** Why a reset is impossible right now, or null when the shrine will accept it. */
    fun resetBlocker(state: GameState): String? {
        val p = state.player
        val cost = resetCost(p)
        return when {
            !isAtShrine(state) ->
                "Travel to the ${TalentCatalog.SHRINE_NAME} in ${WorldCatalog.locationById(TalentCatalog.SHRINE_LOCATION_ID).name}."
            state.activeExpedition?.status == ExpeditionStatus.ACTIVE -> "The shrine is silent while you're on a delve."
            pointsSpent(p) == 0 -> "You have no talents to unbind."
            p.gold < cost -> "The shrine asks for ${cost}g (you have ${p.gold}g)."
            else -> null
        }
    }

    fun reset(state: GameState): Result<GameState> {
        resetBlocker(state)?.let { return Result.failure(IllegalStateException(it)) }
        val p = state.player
        val cost = resetCost(p)
        val refunded = pointsSpent(p)
        val msg = "⛩ The ${TalentCatalog.SHRINE_NAME} unbinds your talents: $refunded point(s) returned" +
            if (cost > 0) " for ${cost}g." else " (first unbinding is free)."
        return Result.success(
            state.copy(
                player = p.copy(
                    gold = p.gold - cost,
                    talents = TalentState(ranks = emptyMap(), resets = p.talents.resets + 1),
                ),
                recentLog = (listOf(msg) + state.recentLog).take(30),
            ),
        )
    }

    // --------------------------------------------------------------------------------------
    // Effects
    // --------------------------------------------------------------------------------------

    fun stat(p: PlayerState, stat: TalentStat): Int =
        p.talents.ranks.entries.sumOf { (id, r) -> (TalentCatalog.nodeById(id)?.perRank?.get(stat) ?: 0) * r }

    fun has(p: PlayerState, ability: TalentAbility): Boolean =
        p.talents.ranks.any { (id, r) -> r > 0 && TalentCatalog.nodeById(id)?.ability == ability }

    fun pct(p: PlayerState, stat: TalentStat): Double = 1.0 + stat(p, stat) / 100.0

    /** Travel cost after Pathfinder. */
    fun travelSteps(p: PlayerState, baseSteps: Int): Int =
        if (has(p, TalentAbility.PATHFINDER)) (baseSteps * 0.8).roundToInt() else baseSteps
}
