package com.sparkborne.app.data

import android.content.Context
import com.sparkborne.app.ui.theme.SparkborneThemeId
import com.sparkborne.app.ui.theme.SplashStyleId

/**
 * Persists the player's chosen elemental app theme and splash screen backdrop.
 */
class AppearanceStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadTheme(): SparkborneThemeId {
        val raw = prefs.getString(KEY_THEME, SparkborneThemeId.EMBER_OBSIDIAN.name)
        return SparkborneThemeId.entries.firstOrNull { it.name == raw }
            ?: SparkborneThemeId.EMBER_OBSIDIAN
    }

    fun saveTheme(themeId: SparkborneThemeId) {
        prefs.edit().putString(KEY_THEME, themeId.name).apply()
    }

    fun loadSplash(): SplashStyleId {
        val raw = prefs.getString(KEY_SPLASH, SplashStyleId.TWILIGHT_CITADEL.name)
        return SplashStyleId.entries.firstOrNull { it.name == raw }
            ?: SplashStyleId.TWILIGHT_CITADEL
    }

    fun saveSplash(splashId: SplashStyleId) {
        prefs.edit().putString(KEY_SPLASH, splashId.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "sparkborne_appearance"
        private const val KEY_THEME = "theme_id"
        private const val KEY_SPLASH = "splash_id"
    }
}
