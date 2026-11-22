package com.sparkborne.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sparkborne.telemetry.client.SparkyFitnessConfig

/**
 * Stores the SparkyFitness Cloudflare Tunnel URL and `x-api-key` in Android Keystore-backed
 * EncryptedSharedPreferences.
 */
class CredentialsStore(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        // Fallback only if Keystore is unavailable on an emulator/test device
        context.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
    }

    fun load(): SparkyFitnessConfig {
        return SparkyFitnessConfig(
            baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
        )
    }

    fun save(baseUrl: String, apiKey: String) {
        prefs.edit()
            .putString(KEY_BASE_URL, baseUrl.trim().trimEnd('/'))
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "sparkborne_sparky_credentials"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
    }
}
