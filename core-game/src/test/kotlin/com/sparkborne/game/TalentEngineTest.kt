package com.sparkborne.game

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.catalog.TalentCatalog
import com.sparkborne.game.catalog.WorldCatalog
import com.sparkborne.game.engine.CombatAction
import com.sparkborne.game.engine.CombatResolver
import com.sparkborne.game.engine.CombatStatsCalculator
import com.sparkborne.game.engine.CraftingEngine
import com.sparkborne.game.engine.ExpeditionManager
import com.sparkborne.game.engine.TalentEngine
import com.sparkborne.game.engine.WorldManager
import com.sparkborne.game.model.ActiveCombatState
import com.sparkborne.game.model.CombatEnemy
import com.sparkborne.game.model.ExpeditionStatus
import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.MaterialType
import com.sparkborne.game.model.SkillType
import com.sparkborne.game.model.TalentState
import com.sparkborne.game.model.TalentTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToInt

class TalentEngineTest {

    private fun base(level: Int = 10, gold: Int = 500): GameState {
        val s = GameCatalog.initialGameState()
        return s.copy(player = s.player.copy(level = level, gold = gold))
    }

    private fun GameState.withTalents(vararg ids: Pair<String, Int>) =
        copy(player = player.copy(talents = TalentState(ranks = ids.toMap())))

    private fun GameState.learn(vararg ids: String): GameState =
        ids.fold(this) { s, id -> TalentEngine.learn(s, id).getOrThrow() }

    // ---------------------------------------------------------------- points & rules

    @Test
    fun `points come from hero level plus every five skill levels gained`() {
        val s = base(level = 3)
        assertEquals(3, TalentEngine.pointsEarned(s.player))
        // Mining Lv.11 = 10 levels gained -> +2 points
        val skilled = s.copy(player = s.player.copy(skillXp = mapOf(SkillType.MINING to com.sparkborne.game.engine.SkillEngine.xpForLevel(11))))
        assertEquals(5, TalentEngine.pointsEarned(skilled.player))
    }

    @Test
    fun `every tree is a valid two-path layout`() {
        TalentTree.entries.forEach { tree ->
            val nodes = TalentCatalog.nodesIn(tree)
            assertEquals(9, nodes.size)
            nodes.forEach { n -> n.requires?.let { assertEquals(tree, TalentCatalog.nodeById(it)!!.tree) } }
            assertEquals(2, nodes.count { it.tier == 2 && it.exclusiveGroup != null })
            assertEquals(2, nodes.count { it.tier == 4 && it.exclusiveGroup != null })
        }
    }

    @Test
    fun `tier gates and prerequisites are enforced`() {
        val s = base()
        // Tier 1 needs 2 points in the tree and the root
        assertTrue(TalentEngine.learn(s, "war_hide").isFailure)
        val s1 = s.learn("war_tempered")
        assertTrue(TalentEngine.learn(s1, "war_hide").isFailure) // only 1 point spent
        val s2 = s1.learn("war_tempered", "war_hide")
        assertEquals(2, TalentEngine.rank(s2.player, "war_tempered"))
        assertEquals(1, TalentEngine.rank(s2.player, "war_hide"))
        // Cannot exceed max rank
        val s3 = s2.learn("war_tempered")
        assertEquals("Mastered", TalentEngine.lockReason(s3.player, TalentCatalog.nodeById("war_tempered")!!))
    }

    @Test
    fun `choosing a path seals the rival path until the shrine`() {
        val s = base().learn("war_tempered", "war_tempered", "war_hide", "war_edge", "war_bloodthirst")
        val cleave = TalentCatalog.nodeById("war_cleave")!!
        assertTrue(TalentEngine.lockReason(s.player, cleave)!!.startsWith("Sealed"))
        assertTrue(TalentEngine.learn(s, "war_cleave").isFailure)
    }

    @Test
    fun `cannot spend more points than earned`() {
        val s = base(level = 1).learn("arc_kindled")
        assertEquals(0, TalentEngine.pointsAvailable(s.player))
        assertTrue(TalentEngine.learn(s, "arc_kindled").isFailure)
    }

    // ---------------------------------------------------------------- shrine

    @Test
    fun `shrine reset only works at Stormspire and gets pricier`() {
        val learned = base().learn("way_trailborn", "way_trailborn")
        assertTrue(TalentEngine.reset(learned).isFailure) // not at the shrine

        val atShrine = learned.copy(currentLocationId = TalentCatalog.SHRINE_LOCATION_ID)
        assertNull(TalentEngine.resetBlocker(atShrine))
        val first = TalentEngine.reset(atShrine).getOrThrow()
        assertEquals(0, TalentEngine.pointsSpent(first.player))
        assertEquals(atShrine.player.gold, first.player.gold) // first unbinding is free
        assertEquals(1, first.player.talents.resets)

        val again = first.learn("way_trailborn")
        val cost = TalentEngine.resetCost(again.player)
        assertEquals(60, cost)
        val second = TalentEngine.reset(again).getOrThrow()
        assertEquals(again.player.gold - 60, second.player.gold)
        assertTrue(WorldCatalog.locations.any { it.id == TalentCatalog.SHRINE_LOCATION_ID })
    }

    // ---------------------------------------------------------------- stat effects

    @Test
    fun `passive talents raise combat stats`() {
        val s = base()
        val before = CombatStatsCalculator.calculate(s)
        val after = CombatStatsCalculator.calculate(
            s.withTalents("war_tempered" to 3, "war_hide" to 3, "arc_deepwell" to 3, "war_ironskin" to 2),
        )
        assertEquals(before.weaponDamage * 1.12, after.weaponDamage.toDouble(), 1.0)
        assertEquals(before.maxHp + 36, after.maxHp)
        assertEquals(before.maxMana + 18, after.maxMana)
        assertEquals(before.defense + 6, after.defense)
    }

    @Test
    fun `step bank capacity scales with Long Strider`() {
        val s = base()
        val cap = s.player.maxStepBankCapacity()
        assertEquals((cap * 1.2).toInt(), s.withTalents("way_strider" to 2).player.maxStepBankCapacity())
    }

    // ---------------------------------------------------------------- combat abilities

    private fun inCombat(
        state: GameState,
        enemyHp: Int = 500,
        enemyMaxHp: Int = 500,
        enemyAtk: Int = 5,
        playerHp: Int = 100,
        mana: Int = 10,
        turn: Int = 1,
    ): GameState {
        val started = ExpeditionManager.startExpedition(state, "exp_daily_ashlands_patrol", LocalDate.of(2026, 11, 22)).getOrThrow()
        val combat = ActiveCombatState(
            nodeIndex = 0,
            enemy = CombatEnemy("Training Dummy", enemyMaxHp, enemyHp, enemyAtk, 0),
            playerHp = playerHp,
            playerMaxHp = 200,
            playerMana = mana,
            playerMaxMana = 50,
            turnNumber = turn,
        )
        return started.copy(activeExpedition = started.activeExpedition!!.copy(activeCombat = combat))
    }

    private fun GameState.combat() = activeExpedition!!.activeCombat!!

    @Test
    fun `Cleave Rhythm empowers every third strike`() {
        val s = base().withTalents("war_cleave" to 1)
        val dmg = CombatStatsCalculator.calculate(s).weaponDamage
        val normal = CombatResolver.performAction(inCombat(s, turn = 2), CombatAction.Strike)
        assertEquals(500 - dmg, normal.combat().enemy.currentHp)
        val third = CombatResolver.performAction(inCombat(s, turn = 3), CombatAction.Strike)
        assertEquals(500 - (dmg * 1.6).roundToInt(), third.combat().enemy.currentHp)
    }

    @Test
    fun `Executioner doubles damage on wounded enemies`() {
        val s = base().withTalents("war_executioner" to 1)
        val dmg = CombatStatsCalculator.calculate(s).weaponDamage
        val after = CombatResolver.performAction(inCombat(s, enemyHp = 140, enemyMaxHp = 500), CombatAction.Strike)
        assertEquals(140 - dmg * 2, after.combat().enemy.currentHp)
    }

    @Test
    fun `Bloodthirst heals on strike`() {
        val s = base().withTalents("war_bloodthirst" to 1)
        val dmg = CombatStatsCalculator.calculate(s).weaponDamage
        val after = CombatResolver.performAction(inCombat(s, playerHp = 100, enemyAtk = 1), CombatAction.Strike)
        // Enemy deals the minimum 1 damage back.
        assertEquals(100 + maxOf(1, dmg / 5) - 1, after.combat().playerHp)
    }

    @Test
    fun `Unbreakable survives exactly one killing blow per fight`() {
        val s = base().withTalents("war_unbreakable" to 1)
        val once = CombatResolver.performAction(inCombat(s, playerHp = 5, enemyAtk = 999), CombatAction.Guard)
        assertEquals(1, once.combat().playerHp)
        assertTrue(once.combat().lastStandUsed)
        val twice = CombatResolver.performAction(once, CombatAction.Guard)
        assertNotEquals(ExpeditionStatus.ACTIVE, twice.activeExpedition?.status)

        val without = CombatResolver.performAction(inCombat(base(), playerHp = 5, enemyAtk = 999), CombatAction.Guard)
        assertNotEquals(ExpeditionStatus.ACTIVE, without.activeExpedition?.status)
    }

    @Test
    fun `Mana Font restores mana each turn and Arcane Overflow refunds spells`() {
        val font = CombatResolver.performAction(inCombat(base().withTalents("arc_manafont" to 1), mana = 10, enemyAtk = 1), CombatAction.Strike)
        assertEquals(10 + 2 + 3, font.combat().playerMana)

        val s = base().withTalents("arc_overflow" to 1)
        val spell = GameCatalog.spellById("spl_spark_bolt")!!
        val cast = CombatResolver.performAction(
            inCombat(s.copy(player = s.player.copy(attunedSpellIds = listOf(spell.id))), mana = 20, enemyAtk = 1),
            CombatAction.CastSpell(spell.id),
        )
        assertEquals(20 - spell.manaCost + (spell.manaCost * 0.4).roundToInt(), cast.combat().playerMana)
    }

    // ---------------------------------------------------------------- world abilities

    @Test
    fun `Pathfinder shortens roads`() {
        val s = base().withTalents("way_pathfinder" to 1)
        val route = WorldCatalog.locationById(s.currentLocationId).routes.first()
        val travelling = WorldManager.startTravel(s, route.destinationId).getOrThrow()
        assertEquals((route.stepsRequired * 0.8).roundToInt(), travelling.activeTravel!!.stepsRequired)
        val plain = WorldManager.startTravel(base(), route.destinationId).getOrThrow()
        assertEquals(route.stepsRequired, plain.activeTravel!!.stepsRequired)
    }

    @Test
    fun `Master Artisan crafts an extra brew`() {
        val recipe = CraftingEngine.recipes.first()
        val stocked = base(gold = 1000).let { s ->
            s.copy(player = s.player.copy(materials = MaterialType.entries.associateWith { 99 }))
        }
        val plain = CraftingEngine.craftRecipe(stocked, recipe.id).getOrThrow()
        val artisan = CraftingEngine.craftRecipe(stocked.withTalents("way_artisan" to 1), recipe.id).getOrThrow()
        assertEquals((plain.player.consumables[recipe.id] ?: 0) + 1, artisan.player.consumables[recipe.id])
        assertFalse(TalentEngine.has(plain.player, com.sparkborne.game.model.TalentAbility.MASTER_ARTISAN))
    }
}
