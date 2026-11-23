package com.sparkborne.game

import com.sparkborne.game.catalog.GameCatalog
import com.sparkborne.game.engine.AppearanceEngine
import com.sparkborne.game.model.HeroAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceEngineTest {

    @Test
    fun `new heroes start uncreated so the creation screen shows`() {
        assertFalse(GameCatalog.initialGameState().player.appearance.created)
    }

    @Test
    fun `applying a look marks the hero created and tidies the name`() {
        val s = AppearanceEngine.apply(
            GameCatalog.initialGameState(),
            HeroAppearance(name = "   Aria    the   Bold   ", hairStyle = 4, cape = 3),
        ).getOrThrow()
        val a = s.player.appearance
        assertTrue(a.created)
        assertEquals("Aria the Bold", a.name)
        assertEquals(4, a.hairStyle)
        assertEquals(3, a.cape)
    }

    @Test
    fun `blank names are rejected and long names are trimmed`() {
        assertTrue(AppearanceEngine.apply(GameCatalog.initialGameState(), HeroAppearance(name = "   ")).isFailure)
        val long = AppearanceEngine.apply(GameCatalog.initialGameState(), HeroAppearance(name = "x".repeat(40))).getOrThrow()
        assertEquals(HeroAppearance.NAME_MAX, long.player.appearance.name.length)
    }

    @Test
    fun `out of range style indices are clamped`() {
        val s = AppearanceEngine.apply(
            GameCatalog.initialGameState(),
            HeroAppearance(skinTone = 99, hairStyle = -4, cape = 1000, tunic = 7),
        ).getOrThrow().player.appearance
        assertEquals(HeroAppearance.SKIN_TONES - 1, s.skinTone)
        assertEquals(0, s.hairStyle)
        assertEquals(HeroAppearance.CAPES - 1, s.cape)
        assertEquals(HeroAppearance.TUNICS - 1, s.tunic)
    }

    @Test
    fun `rebirth keeps the look but asks to confirm it again`() {
        val created = HeroAppearance(name = "Bram", created = true, hairColor = 5)
        val reborn = AppearanceEngine.forRebirth(created)
        assertFalse(reborn.created)
        assertEquals(5, reborn.hairColor)
        assertEquals("Bram", reborn.name)
    }
}
