package com.sparkborne.telemetry.client

sealed class SparkyFitnessApiException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    class Unauthorized(message: String = "Invalid or missing SparkyFitness API key (401)") :
        SparkyFitnessApiException(message)

    class Forbidden(message: String = "API key lacks permission for this SparkyFitness endpoint (403)") :
        SparkyFitnessApiException(message)

    class NotFound(val endpoint: String, message: String = "Endpoint not found: $endpoint (404)") :
        SparkyFitnessApiException(message)

    class HttpError(val code: Int, val body: String?) :
        SparkyFitnessApiException("SparkyFitness HTTP $code: ${body?.take(200) ?: "no body"}")

    class NetworkError(message: String, cause: Throwable) :
        SparkyFitnessApiException(message, cause)

    class ParseError(val endpoint: String, cause: Throwable) :
        SparkyFitnessApiException("Failed to parse SparkyFitness response from $endpoint", cause)
}
