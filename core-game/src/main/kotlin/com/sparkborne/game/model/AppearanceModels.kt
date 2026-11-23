package com.sparkborne.game.model

import kotlinx.serialization.Serializable

/**
 * Purely cosmetic hero look chosen at character creation (and at the mirror).
 * Each field is an index into a palette/style list drawn by the app's hero renderer.
 */
@Serializable
data class HeroAppearance(
    val name: String = "Sparkborne Knight",
    val created: Boolean = false,
    val skinTone: Int = 1,
    val build: Int = 1,
    val hairStyle: Int = 1,
    val hairColor: Int = 1,
    val eyeColor: Int = 1,
    val facialHair: Int = 0,
    val faceMark: Int = 0,
    val cape: Int = 1,
    val tunic: Int = 0,
) {
    companion object {
        const val NAME_MAX = 18
        const val SKIN_TONES = 6
        const val BUILDS = 3
        const val HAIR_STYLES = 10
        const val HAIR_COLORS = 10
        const val EYE_COLORS = 7
        const val FACIAL_HAIR = 6
        const val FACE_MARKS = 6
        const val CAPES = 8
        const val TUNICS = 6
    }
}
