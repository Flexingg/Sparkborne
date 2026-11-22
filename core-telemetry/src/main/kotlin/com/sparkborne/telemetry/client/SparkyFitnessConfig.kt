package com.sparkborne.telemetry.client

/**
 * Connection settings for a self-hosted SparkyFitness instance behind HTTPS (e.g. Cloudflare Tunnel).
 *
 * Authentication uses the SparkyFitness `x-api-key` header.
 */
data class SparkyFitnessConfig(
    val baseUrl: String,
    val apiKey: String,
    val connectTimeoutSeconds: Long = 15,
    val readTimeoutSeconds: Long = 30,
) {
    /**
     * Base URL trimmed and without trailing slash (e.g., `https://fitness.example.com`).
     */
    val normalizedBaseUrl: String = baseUrl.trim().trimEnd('/')

    fun validate(): Result<Unit> {
        if (normalizedBaseUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("SparkyFitness URL must not be blank"))
        }
        if (!normalizedBaseUrl.startsWith("https://") && !normalizedBaseUrl.startsWith("http://")) {
            return Result.failure(IllegalArgumentException("SparkyFitness URL must start with https://"))
        }
        if (apiKey.trim().isBlank()) {
            return Result.failure(IllegalArgumentException("SparkyFitness API key must not be blank"))
        }
        return Result.success(Unit)
    }
}
