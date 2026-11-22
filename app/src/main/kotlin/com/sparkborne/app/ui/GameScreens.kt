package com.sparkborne.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.CombatAction
import com.sparkborne.game.engine.CombatStatsCalculator
import com.sparkborne.game.model.ActiveCombatState
import com.sparkborne.game.model.ActiveExpedition
import com.sparkborne.game.model.ExpeditionStatus
import com.sparkborne.game.model.GameState
import com.sparkborne.telemetry.model.MuscleGroup
import kotlin.math.roundToInt

@Composable
fun DelveTabScreen(
    state: GameState,
    errorMessage: String?,
    onStartContract: (String) -> Unit,
    onEnterNode: () -> Unit,
    onCombatAction: (CombatAction) -> Unit,
    onAbandon: () -> Unit,
    onClearFinished: () -> Unit,
) {
    val active = state.activeExpedition

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        errorMessage?.let { err ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        text = err,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (active == null) {
            item {
                Text(
                    "Choose a Self-Selected Contract",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Sync SparkyFitness steps, lifts, macros & recovery to advance. Fail or miss the deadline and all unbanked run loot is lost.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(GameCatalog.contracts, key = { it.id }) { contract ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(contract.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${contract.cadence.displayName} · ${contract.difficulty.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(contract.description, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Rewards: ${contract.baseRewardGold}g · ${contract.baseRewardShards} shards · ${contract.baseRewardIron} iron" +
                                if (contract.grantsEggOnClear) " · +1 Monster Egg" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Button(onClick = { onStartContract(contract.id) }) {
                            Text("Accept Contract (${contract.durationDays}d)")
                        }
                    }
                }
            }
        } else {
            item {
                ActiveExpeditionHeader(active, onAbandon, onClearFinished)
            }

            active.activeCombat?.let { combat ->
                item {
                    CombatEncounterCard(
                        state = state,
                        combat = combat,
                        onAction = onCombatAction,
                    )
                }
            } ?: run {
                if (active.status == ExpeditionStatus.ACTIVE) {
                    items(active.nodes, key = { it.index }) { node ->
                        val isCurrent = node.index == active.currentNodeIndex
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "${node.index + 1}. ${node.title}",
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(
                                        when {
                                            node.cleared -> "CLEARED"
                                            node.isUnlocked -> "READY"
                                            else -> "${node.stepsApplied}/${node.stepsRequired} steps"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (node.cleared) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (isCurrent && !node.cleared) {
                                    LinearProgressIndicator(
                                        progress = {
                                            (node.stepsApplied.toFloat() / node.stepsRequired.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Button(
                                        onClick = onEnterNode,
                                        enabled = node.isUnlocked,
                                    ) {
                                        Text(if (node.isUnlocked) "Enter ${node.type.displayName}" else "Sync Steps to Unlock")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.recentLog.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Adventure Log", style = MaterialTheme.typography.titleSmall)
                        state.recentLog.take(6).forEach { entry ->
                            Text("• $entry", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ActiveExpeditionHeader(
    exp: ActiveExpedition,
    onAbandon: () -> Unit,
    onClearFinished: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(exp.contract.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    exp.status.name,
                    color = when (exp.status) {
                        ExpeditionStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                        ExpeditionStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                        ExpeditionStatus.FAILED -> MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "HP ${exp.runPlayerHp}/${exp.runPlayerMaxHp} · Mana ${exp.runPlayerMana}/${exp.runPlayerMaxMana} · Deadline ${exp.deadlineDate}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Unbanked Loot (at risk): ${exp.unbankedLoot.gold}g · ${exp.unbankedLoot.emberShards} shards · ${exp.unbankedLoot.ironOre} iron",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Contract Progress: ${exp.progress.totalStepsLogged}/${exp.contract.requirement.minTotalSteps} steps · " +
                    "${exp.progress.totalLiftingVolumeKg.roundToInt()}/${exp.contract.requirement.minLiftingVolumeKg.roundToInt()} kg lifted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            exp.failureReason?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (exp.status == ExpeditionStatus.ACTIVE) {
                OutlinedButton(onClick = onAbandon) { Text("Abandon Run (Forfeit Loot)") }
            } else {
                Button(onClick = onClearFinished) { Text("Return to Contract Board") }
            }
        }
    }
}

@Composable
private fun CombatEncounterCard(
    state: GameState,
    combat: ActiveCombatState,
    onAction: (CombatAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Turn ${combat.turnNumber} · ${combat.enemy.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("Enemy HP: ${combat.enemy.currentHp} / ${combat.enemy.maxHp} (ATK ${combat.enemy.attack}, DEF ${combat.enemy.defense})")
            LinearProgressIndicator(
                progress = { (combat.enemy.currentHp.toFloat() / combat.enemy.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "You: HP ${combat.playerHp}/${combat.playerMaxHp} · Shield ${combat.playerShield} · Mana ${combat.playerMana}/${combat.playerMaxMana}",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAction(CombatAction.Strike) }) { Text("Strike") }
                OutlinedButton(onClick = { onAction(CombatAction.Guard) }) { Text("Guard") }
                Button(
                    onClick = { onAction(CombatAction.MonsterSkill) },
                    enabled = state.activeMonster != null && combat.monsterSkillCooldown == 0,
                ) {
                    Text(
                        if (combat.monsterSkillCooldown == 0) {
                            "${state.activeMonster?.name ?: "Pet"} Skill"
                        } else {
                            "Pet CD (${combat.monsterSkillCooldown})"
                        },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.player.attunedSpellIds.mapNotNull(GameCatalog::spellById).forEach { spell ->
                    OutlinedButton(
                        onClick = { onAction(CombatAction.CastSpell(spell.id)) },
                        enabled = combat.playerMana >= spell.manaCost,
                    ) {
                        Text("${spell.name} (${spell.manaCost}m)")
                    }
                }
            }

            combat.combatLog.takeLast(5).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun HeroTabScreen(
    state: GameState,
    errorMessage: String?,
    onForgeOrEquipWeapon: (String) -> Unit,
    onForgeOrEquipArmor: (String) -> Unit,
    onUnlockOrAttuneSpell: (String) -> Unit,
) {
    val stats = CombatStatsCalculator.calculate(state)
    val player = state.player

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        errorMessage?.let { err ->
            item { Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Hero · Level ${player.level} (${player.xp}/${player.xpToNextLevel()} XP)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Gold: ${player.gold} · Ember Shards: ${player.emberShards} · Iron Ore: ${player.ironOre}", color = MaterialTheme.colorScheme.primary)
                    Text("Effective Stats: HP ${stats.maxHp} · Mana ${stats.maxMana} · Weapon DMG ${stats.weaponDamage} · Spell Power ${stats.spellPower} · DEF ${stats.defense}")
                    Text(
                        "Momentum Stance: ${player.activeBuffs.momentumStance.displayName} — ${player.activeBuffs.momentumStance.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Muscle Group Strength Ranks (Lifting)", style = MaterialTheme.typography.titleMedium)
                    val tracked = MuscleGroup.entries.filter { it != MuscleGroup.OTHER }
                    tracked.forEach { group ->
                        val xp = player.muscleStrengthXp[group] ?: 0.0
                        val rank = player.muscleStrengthRank(group)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(group.displayName, style = MaterialTheme.typography.bodySmall)
                            Text("Rank $rank (${xp.roundToInt()} kg eff)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        item { Text("Weapons (Scale by Muscle Groups)", style = MaterialTheme.typography.titleMedium) }
        items(GameCatalog.weapons, key = { it.id }) { weapon ->
            val owned = weapon.id in player.ownedWeaponIds
            val equipped = player.equippedWeaponId == weapon.id
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(weapon.name, fontWeight = FontWeight.Bold)
                        Text(
                            "${weapon.weaponClass.displayName} · Base ${weapon.baseDamage} · Scales: ${weapon.weaponClass.scalingMuscles.joinToString { it.displayName }}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = { onForgeOrEquipWeapon(weapon.id) },
                        enabled = !equipped,
                    ) {
                        Text(
                            when {
                                equipped -> "Equipped"
                                owned -> "Equip"
                                else -> "Forge (${weapon.forgeGoldCost}g/${weapon.forgeIronCost}fe)"
                            },
                        )
                    }
                }
            }
        }

        item { Text("Armor Forge", style = MaterialTheme.typography.titleMedium) }
        items(GameCatalog.armor, key = { it.id }) { armor ->
            val owned = armor.id in player.ownedArmorIds
            val equipped = player.equippedArmor[armor.slot] == armor.id
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${armor.name} (${armor.slot.displayName})", fontWeight = FontWeight.Bold)
                        Text("DEF +${armor.defense} · HP +${armor.bonusHp} · Mana +${armor.bonusMana}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { onForgeOrEquipArmor(armor.id) },
                        enabled = !equipped,
                    ) {
                        Text(
                            when {
                                equipped -> "Equipped"
                                owned -> "Equip"
                                else -> "Forge (${armor.forgeGoldCost}g)"
                            },
                        )
                    }
                }
            }
        }

        item { Text("Spells (Attune up to 3)", style = MaterialTheme.typography.titleMedium) }
        items(GameCatalog.spells, key = { it.id }) { spell ->
            val unlocked = spell.id in player.unlockedSpellIds
            val attuned = spell.id in player.attunedSpellIds
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${spell.name} (${spell.manaCost} Mana)", fontWeight = FontWeight.Bold)
                        Text(spell.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { onUnlockOrAttuneSpell(spell.id) },
                        enabled = !attuned,
                    ) {
                        Text(
                            when {
                                attuned -> "Attuned"
                                unlocked -> "Attune"
                                else -> "Unlock (${spell.unlockShardsCost}sh)"
                            },
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun MonstersTabScreen(
    state: GameState,
    errorMessage: String?,
    onSelectActive: (String) -> Unit,
    onEvolve: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        errorMessage?.let { err ->
            item { Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        item { Text("Monster Companions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(state.monsters, key = { it.id }) { mon ->
            val isActive = mon.id == state.activeMonster?.id
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${mon.name} · Lv.${mon.level} ${mon.stage.displayName}", fontWeight = FontWeight.Bold)
                        Text(mon.affinity.displayName, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("ATK ${mon.baseAttack} · DEF ${mon.baseDefense} · XP ${mon.xp}/${mon.stage.requiredXpToEvolve ?: "MAX"}", style = MaterialTheme.typography.bodySmall)
                    Text("Synergy (${mon.affinity.synergyTitle}): ${mon.affinity.synergyDescription}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onSelectActive(mon.id) }, enabled = !isActive) {
                            Text(if (isActive) "Active Partner" else "Set Active")
                        }
                        if (mon.canEvolve) {
                            Button(onClick = { onEvolve(mon.id) }) {
                                Text("Evolve!")
                            }
                        }
                    }
                }
            }
        }

        item { Text("Egg Incubator (Sleep + Hydration)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (state.eggs.isEmpty()) {
            item {
                Text(
                    "No eggs incubating. Clear an egg-bearing contract to earn a new Monster Egg.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.eggs, key = { it.id }) { egg ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(egg.name, fontWeight = FontWeight.Bold)
                        Text(
                            "Sleep: ${egg.incubatedSleepMinutes}/${egg.requiredSleepMinutes} min · Hydration: ${egg.incubatedHydrationMl}/${egg.requiredHydrationMl} mL",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        LinearProgressIndicator(
                            progress = { egg.progressFraction.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Projected Affinity: ${egg.macroProfile.dominantAffinity().displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
