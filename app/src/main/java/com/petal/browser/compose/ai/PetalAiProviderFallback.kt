package com.petal.browser.compose.ai

import android.content.Context
import androidx.preference.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

/**
 * Manages automatic failover across configured AI providers.
 * If the primary provider encounters a network error, 429 rate limit, or 5xx server error,
 * it retries once with exponential backoff and then falls back to the next eligible provider,
 * notifying the caller which provider successfully fulfilled the request.
 */
object PetalAiProviderFallback {

    const val KEY_FALLBACK_ENABLED = "sp_ai_provider_fallback_enabled"

    fun isFallbackEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(KEY_FALLBACK_ENABLED, true)
    }

    fun setFallbackEnabled(context: Context, enabled: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean(KEY_FALLBACK_ENABLED, enabled).apply()
    }

    /**
     * Returns an ordered list of candidate providers starting with [primary].
     * Enforces API key configuration, cloud consent for non-local providers, and optional vision support.
     */
    fun getCandidateProviders(
        context: Context,
        primary: AiProvider,
        requireVision: Boolean = false,
        thinkHarder: Boolean = false
    ): List<AiProvider> {
        val all = listOf(AiProvider.GEMINI, AiProvider.GROQ, AiProvider.CUSTOM, AiProvider.LOCAL_ON_DEVICE)
        val ordered = listOf(primary) + (all - primary)

        return ordered.filter { provider ->
            val configuredAndConsented = when (provider) {
                AiProvider.LOCAL_ON_DEVICE ->
                    com.petal.browser.compose.ai.ondevice.PetalOnDeviceConfig.isSupported()
                AiProvider.CUSTOM -> {
                    val isLocal = PetalAiResearchEngine.isCustomEndpointLocal(context)
                    val hasAuth = isLocal || PetalAiResearchEngine.getApiKey(context, provider).isNotBlank()
                    val hasConsent = isLocal || PetalAiResearchEngine.isCloudConsentAccepted(context)
                    hasAuth && hasConsent
                }
                AiProvider.GEMINI, AiProvider.GROQ ->
                    PetalAiResearchEngine.getApiKey(context, provider).isNotBlank() &&
                        PetalAiResearchEngine.isCloudConsentAccepted(context)
            }
            if (!configuredAndConsented) return@filter false
            if (!requireVision) return@filter true
            PetalAiResearchEngine.resolveModelForRequest(
                context = context,
                provider = provider,
                thinkHarder = thinkHarder,
                requireVision = true
            ) != null
        }
    }

    data class StreamResult(
        val provider: AiProvider,
        val flow: Flow<String>,
        val wasFallback: Boolean = false,
        val failedProvider: AiProvider? = null
    )

    /**
     * Executes streaming chat with automatic one-retry backoff and multi-provider fallback.
     */
    fun streamWithFallback(
        context: Context,
        messages: List<AiChatMessage>,
        initialProvider: AiProvider,
        initialModel: String,
        thinkHarder: Boolean = false
    ): Flow<Pair<String, AiProvider>> = flow {
        val hasImage = messages.any { msg -> msg.parts.any { it is AiMessagePart.Image } }

        if (!isFallbackEnabled(context)) {
            val model = PetalAiResearchEngine.resolveModelForRequest(
                context = context,
                provider = initialProvider,
                thinkHarder = thinkHarder,
                requireVision = hasImage,
                preferredModel = initialModel
            ) ?: throw AiBackendException.VisionUnsupportedException()
            val backend = AiBackendFactory.getBackend(context, initialProvider)
            backend.stream(messages, model).collect { chunk ->
                emit(Pair(chunk, initialProvider))
            }
            return@flow
        }

        val candidates = getCandidateProviders(
            context = context,
            primary = initialProvider,
            requireVision = hasImage,
            thinkHarder = thinkHarder
        )
        if (candidates.isEmpty()) {
            if (hasImage) {
                throw AiBackendException.VisionUnsupportedException()
            }
            throw AiBackendException.EmptyReplyException("No configured AI provider is available.")
        }

        var lastException: Throwable? = null

        for (provider in candidates) {
            val model = PetalAiResearchEngine.resolveModelForRequest(
                context = context,
                provider = provider,
                thinkHarder = thinkHarder,
                requireVision = hasImage,
                preferredModel = if (provider == initialProvider) initialModel else null
            ) ?: continue
            val backend = AiBackendFactory.getBackend(context, provider)

            var succeeded = false
            var attempt = 0
            val maxAttempts = 2

            while (attempt < maxAttempts && !succeeded) {
                attempt++
                try {
                    var receivedAnyChunk = false
                    backend.stream(messages, model).collect { chunk ->
                        receivedAnyChunk = true
                        emit(Pair(chunk, provider))
                    }
                    succeeded = true
                } catch (e: Exception) {
                    lastException = e
                    if (isRetryable(e) && attempt < maxAttempts) {
                        delay(600L) // Exponential backoff retry
                    } else {
                        break // Fall to next provider
                    }
                }
            }

            if (succeeded) {
                return@flow
            }
        }

        if (lastException != null) {
            throw lastException
        } else {
            throw AiBackendException.EmptyReplyException("No response received from any provider")
        }
    }

    private fun isRetryable(t: Throwable): Boolean {
        return t is AiBackendException.RateLimitException ||
                t is AiBackendException.ServerException ||
                t is AiBackendException.NetworkException
    }
}
