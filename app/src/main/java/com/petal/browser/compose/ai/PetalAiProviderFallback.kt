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
     */
    fun getCandidateProviders(context: Context, primary: AiProvider): List<AiProvider> {
        val all = listOf(AiProvider.GEMINI, AiProvider.GROQ, AiProvider.CUSTOM, AiProvider.LOCAL_ON_DEVICE)
        val ordered = listOf(primary) + (all - primary)

        return ordered.filter { provider ->
            when (provider) {
                AiProvider.LOCAL_ON_DEVICE -> com.petal.browser.compose.ai.ondevice.PetalOnDeviceConfig.isSupported()
                AiProvider.CUSTOM -> PetalAiResearchEngine.isLocalEndpoint(context) || PetalAiResearchEngine.getApiKey(context, provider).isNotBlank()
                else -> PetalAiResearchEngine.getApiKey(context, provider).isNotBlank()
            }
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
        if (!isFallbackEnabled(context)) {
            val backend = AiBackendFactory.getBackend(context, initialProvider)
            val model = if (thinkHarder) PetalAiResearchEngine.getStrongerModel(initialProvider) else initialModel
            backend.stream(messages, model).collect { chunk ->
                emit(Pair(chunk, initialProvider))
            }
            return@flow
        }

        val candidates = getCandidateProviders(context, initialProvider)
        var lastException: Throwable? = null

        for (provider in candidates) {
            val backend = AiBackendFactory.getBackend(context, provider)
            val model = if (thinkHarder) PetalAiResearchEngine.getStrongerModel(provider) else PetalAiResearchEngine.getSelectedModel(context, provider)

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
