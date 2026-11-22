package com.sparkborne.game.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExpeditionCadence(val displayName: String) {
    DAILY("Daily Contract"),
    MULTI_DAY("Multi-Day Delve"),
}

@Serializable
enum class ExpeditionDifficulty(val displayName: String, val rewardMultiplier: Double) {
    SCOUT("Scout", 1.0),
    VANGUARD("Vanguard", 1.5),
    ABYSSAL("Abyssal", 2.2),
}

@Serializable
data class ContractRequirement(
    val minTotalSteps: Int,
    val minLiftingVolumeKg: Double = 0.0,
    val minProteinDays: Int = 0,
    val minHydrationDays: Int = 0,
    val minSleepDays: Int = 0,
)

@Serializable
data class ExpeditionContract(
    val id: String,
    val title: String,
    val description: String,
    val cadence: ExpeditionCadence,
    val durationDays: Int,
    val difficulty: ExpeditionDifficulty,
    val totalNodes: Int,
    val stepsPerNode: Int,
    val requirement: ContractRequirement,
    val baseRewardGold: Int,
    val baseRewardShards: Int,
    val baseRewardIron: Int,
    val grantsEggOnClear: Boolean,
    val bossName: String,
    val bossHp: Int,
    val bossAttack: Int,
    val bossDefense: Int,
)

@Serializable
enum class DelveNodeType(val displayName: String) {
    COMBAT("Skirmish"),
    FORGE_SHRINE("Forge Shrine"),
    REST_CAMP("Campfire"),
    TREASURE("Relic Cache"),
    BOSS("Boss Lair"),
}

@Serializable
data class CombatEnemy(
    val name: String,
    val maxHp: Int,
    val currentHp: Int,
    val attack: Int,
    val defense: Int,
    val isBoss: Boolean = false,
)

@Serializable
data class DelveNode(
    val index: Int,
    val type: DelveNodeType,
    val title: String,
    val stepsRequired: Int,
    val stepsApplied: Int = 0,
    val cleared: Boolean = false,
    val enemy: CombatEnemy? = null,
) {
    val isUnlocked: Boolean
        get() = stepsApplied >= stepsRequired
}

@Serializable
data class ActiveCombatState(
    val nodeIndex: Int,
    val enemy: CombatEnemy,
    val playerHp: Int,
    val playerMaxHp: Int,
    val playerMana: Int,
    val playerMaxMana: Int,
    val playerShield: Int = 0,
    val monsterSkillCooldown: Int = 0,
    val turnNumber: Int = 1,
    val combatLog: List<String> = emptyList(),
)

/**
 * Unbanked loot accumulated during an active expedition.
 * Roguelike stakes: banked on clear, completely LOST on failure or abandonment.
 */
@Serializable
data class LootBag(
    val gold: Int = 0,
    val emberShards: Int = 0,
    val ironOre: Int = 0,
    val xp: Int = 0,
    val eggs: List<MonsterEgg> = emptyList(),
)

@Serializable
enum class ExpeditionStatus {
    ACTIVE,
    COMPLETED,
    FAILED,
}

@Serializable
data class ContractProgress(
    val totalStepsLogged: Int = 0,
    val totalLiftingVolumeKg: Double = 0.0,
    val proteinGoalDaysMet: Int = 0,
    val hydrationGoalDaysMet: Int = 0,
    val sleepGoalDaysMet: Int = 0,
)

@Serializable
data class ActiveExpedition(
    val runId: String,
    val contract: ExpeditionContract,
    val startedDate: String,
    val deadlineDate: String,
    val seed: Long,
    val nodes: List<DelveNode>,
    val currentNodeIndex: Int = 0,
    val runPlayerHp: Int,
    val runPlayerMaxHp: Int,
    val runPlayerMana: Int,
    val runPlayerMaxMana: Int,
    val activeCombat: ActiveCombatState? = null,
    val unbankedLoot: LootBag = LootBag(),
    val progress: ContractProgress = ContractProgress(),
    val status: ExpeditionStatus = ExpeditionStatus.ACTIVE,
    val failureReason: String? = null,
) {
    val currentNode: DelveNode?
        get() = nodes.getOrNull(currentNodeIndex)

    fun meetsContractRequirements(): Boolean {
        val req = contract.requirement
        return progress.totalStepsLogged >= req.minTotalSteps &&
            progress.totalLiftingVolumeKg >= req.minLiftingVolumeKg &&
            progress.proteinGoalDaysMet >= req.minProteinDays &&
            progress.hydrationGoalDaysMet >= req.minHydrationDays &&
            progress.sleepGoalDaysMet >= req.minSleepDays
    }
}

@Serializable
data class GameState(
    val player: PlayerState,
    val monsters: List<MonsterCompanion> = emptyList(),
    val activeMonsterId: String? = null,
    val eggs: List<MonsterEgg> = emptyList(),
    val activeExpedition: ActiveExpedition? = null,
    val currentLocationId: String = "loc_emberhearth",
    val discoveredLocationIds: Set<String> = setOf("loc_emberhearth"),
    val activeTravel: ActiveTravel? = null,
    val activeActivity: ActiveTownActivity? = null,
    val completedContractIds: Set<String> = emptySet(),
    val processedDateHashes: Map<String, String> = emptyMap(),
    val dailyCreditedSteps: Map<String, Int> = emptyMap(),
    val introQuest: IntroQuestState = IntroQuestState(),
    val unlockedCodexIds: Set<String> = setOf("guide_welcome", "loc_emberhearth", "keeper_ember"),
    val unreadCodexIds: Set<String> = emptySet(),
    val totalRunsCompleted: Int = 0,
    val totalRunsFailed: Int = 0,
    val recentLog: List<String> = emptyList(),
) {
    val activeMonster: MonsterCompanion?
        get() = monsters.firstOrNull { it.id == activeMonsterId } ?: monsters.firstOrNull()
}
