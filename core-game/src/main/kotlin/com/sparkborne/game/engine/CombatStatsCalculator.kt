package com.sparkborne.game.engine

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.MonsterAffinity
import com.sparkborne.game.model.TalentStat
import kotlin.math.roundToInt

/**
 * Synergistic combat stats computed deterministically from:
 * - Persistent character level + per-muscle-group lifting ranks
 * - Equipped weapon & armor
 * - Active monster companion stats & duo synergy passive
 * - Active delve telemetry buffs (sleep vitality, hydration clarity, macro attributes, bodyweight stance)
 */
data class EffectiveCombatStats(
    val maxHp: Int,
    val maxMana: Int,
    val weaponDamage: Int,
    val spellPower: Int,
    val defense: Int,
    val monsterSkillDamage: Int,
    val stepEfficiencyMultiplier: Double,
    val scalingMuscleAverageRank: Double,
)

object CombatStatsCalculator {

    fun calculate(state: GameState): EffectiveCombatStats {
        val player = state.player
        val buffs = player.activeBuffs
        val weapon = GameCatalog.weaponById(player.equippedWeaponId)
        val equippedArmor = player.equippedArmor.values.mapNotNull(GameCatalog::armorById)
        val monster = state.activeMonster

        val armorDefense = equippedArmor.sumOf { it.defense }
        val armorHp = equippedArmor.sumOf { it.bonusHp }
        val armorMana = equippedArmor.sumOf { it.bonusMana }
        val armorSpellPower = equippedArmor.sumOf { it.spellPowerBonus }

        // Per-muscle-group lifting strength scales weapon damage directly
        val muscleAvgRank = weapon.weaponClass.scalingMuscles
            .map { player.muscleStrengthRank(it).toDouble() }
            .average()
            .takeIf { !it.isNaN() } ?: 1.0

        val rawWeaponDamage = weapon.baseDamage +
            ((muscleAvgRank - 1.0) * weapon.muscleScalingFactor * 3.0) +
            (player.level * 1.5)

        val collectibleCombatMult = 1.0 + CollectibleEngine.totalBonus(state, com.sparkborne.game.model.CollectibleBonusType.COMBAT_POWER_PERCENT) / 100.0
        val monsterWeaponSynergy = if (monster?.affinity == MonsterAffinity.EMBERHEART) 1.15 else 1.0
        val talentAtk = TalentEngine.pct(player, TalentStat.ATTACK_PCT)
        val finalWeaponDamage = (rawWeaponDamage * buffs.momentumStance.attackMultiplier * monsterWeaponSynergy * collectibleCombatMult * talentAtk)
            .roundToInt()
            .coerceAtLeast(1)

        val baseHp = 80 + (player.level * 10) + armorHp + buffs.sleepVitalityHpBonus + TalentEngine.stat(player, TalentStat.MAX_HP)
        val monsterHpSynergy = if (monster?.affinity == MonsterAffinity.DEEPFORGE) 1.15 else 1.0
        val finalMaxHp = (baseHp * monsterHpSynergy).roundToInt()

        val finalMaxMana = 20 + (player.level * 2) + armorMana + buffs.hydrationClarityManaBonus + TalentEngine.stat(player, TalentStat.MAX_MANA)

        val baseDefense = armorDefense +
            buffs.proteinFortitudeDefenseBonus +
            buffs.momentumStance.defenseBonus +
            (monster?.baseDefense ?: 0) +
            TalentEngine.stat(player, TalentStat.DEFENSE)
        val monsterDefSynergy = if (monster?.affinity == MonsterAffinity.IRONCLAD) 1.20 else 1.0
        val finalDefense = (baseDefense * monsterDefSynergy).roundToInt()

        val baseSpellPower = 6 + (player.level * 2) + armorSpellPower + buffs.carbsSpellPowerBonus
        val monsterSpellSynergy = if (monster?.affinity == MonsterAffinity.STORMSTRIDER) 1.25 else 1.0
        val finalSpellPower = (baseSpellPower * buffs.momentumStance.spellMultiplier * monsterSpellSynergy * collectibleCombatMult * TalentEngine.pct(player, TalentStat.SPELL_PCT))
            .roundToInt()

        val monsterSkillDamage = if (monster != null) {
            ((monster.baseAttack * (1.0 + monster.stage.ordinal * 0.45) + monster.level * 2) * TalentEngine.pct(player, TalentStat.BEAST_PCT)).roundToInt()
        } else {
            0
        }

        return EffectiveCombatStats(
            maxHp = finalMaxHp,
            maxMana = finalMaxMana,
            weaponDamage = finalWeaponDamage,
            spellPower = finalSpellPower,
            defense = finalDefense,
            monsterSkillDamage = monsterSkillDamage,
            stepEfficiencyMultiplier = buffs.momentumStance.stepEfficiencyMultiplier,
            scalingMuscleAverageRank = muscleAvgRank,
        )
    }
}
