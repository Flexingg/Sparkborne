package com.sparkborne.game.engine

import com.sparkborne.game.model.GameState
import com.sparkborne.game.model.HeroAppearance

/** Validates and saves the hero's cosmetic appearance. */
object AppearanceEngine {

    fun apply(state: GameState, look: HeroAppearance): Result<GameState> {
        val name = look.name.trim().replace(Regex("\\s+"), " ").take(HeroAppearance.NAME_MAX)
        if (name.isEmpty()) return Result.failure(IllegalArgumentException("Your hero needs a name."))
        val clean = look.copy(
            name = name,
            created = true,
            skinTone = look.skinTone.coerceIn(0, HeroAppearance.SKIN_TONES - 1),
            build = look.build.coerceIn(0, HeroAppearance.BUILDS - 1),
            hairStyle = look.hairStyle.coerceIn(0, HeroAppearance.HAIR_STYLES - 1),
            hairColor = look.hairColor.coerceIn(0, HeroAppearance.HAIR_COLORS - 1),
            eyeColor = look.eyeColor.coerceIn(0, HeroAppearance.EYE_COLORS - 1),
            facialHair = look.facialHair.coerceIn(0, HeroAppearance.FACIAL_HAIR - 1),
            faceMark = look.faceMark.coerceIn(0, HeroAppearance.FACE_MARKS - 1),
            cape = look.cape.coerceIn(0, HeroAppearance.CAPES - 1),
            tunic = look.tunic.coerceIn(0, HeroAppearance.TUNICS - 1),
        )
        val isNew = !state.player.appearance.created
        val msg = if (isNew) "⚔ ${clean.name} steps into the realm of Sparkborne!" else "🪞 ${clean.name} admires a fresh new look."
        return Result.success(
            state.copy(
                player = state.player.copy(appearance = clean),
                recentLog = (listOf(msg) + state.recentLog).take(30),
            ),
        )
    }

    /** For a fresh journey: keep the old look (pre-filled) but ask the player to confirm it again. */
    fun forRebirth(previous: HeroAppearance): HeroAppearance = previous.copy(created = false)
}
