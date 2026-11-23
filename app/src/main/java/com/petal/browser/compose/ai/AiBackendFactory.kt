package com.petal.browser.compose.ai

import android.content.Context
import okhttp3.OkHttpClient

/**
 * Factory for creating [AiBackend] instances based on selected or explicitly specified providers.
 */
object AiBackendFactory {

    /**
     * Returns an [AiBackend] for the user's currently selected provider stored in SharedPreferences.
     */
    fun getBackend(context: Context): AiBackend {
        val provider = PetalAiResearchEngine.getSelectedProvider(context)
        return getBackend(context, provider)
    }

    /**
     * Returns an [AiBackend] for the given [AiProvider], reading keys and endpoints from SharedPreferences.
     */
    fun getBackend(context: Context, provider: AiProvider): AiBackend {
        val apiKey = PetalAiResearchEngine.getApiKey(context, provider)
        return when (provider) {
            AiProvider.GEMINI -> GeminiBackend(
                apiKey = apiKey,
                httpClient = PetalAiResearchEngine.httpClient
            )
            AiProvider.GROQ -> OpenAiCompatibleBackend(
                provider = AiProvider.GROQ,
                endpoint = OpenAiCompatibleBackend.GROQ_ENDPOINT,
                apiKey = apiKey,
                httpClient = PetalAiResearchEngine.httpClient
            )
            AiProvider.CUSTOM -> {
                val endpoint = PetalAiResearchEngine.getCustomEndpoint(context)
                OpenAiCompatibleBackend(
                    provider = AiProvider.CUSTOM,
                    endpoint = endpoint,
                    apiKey = apiKey,
                    httpClient = PetalAiResearchEngine.httpClient
                )
            }
            AiProvider.LOCAL_ON_DEVICE -> {
                com.petal.browser.compose.ai.ondevice.OnDeviceGemmaBackend(context)
            }
        }
    }

    /**
     * Instantiates an [AiBackend] without requiring an Android [Context].
     * Suitable for direct creation and JVM unit tests.
     */
    fun create(
        provider: AiProvider,
        apiKey: String = "",
        customEndpoint: String = OpenAiCompatibleBackend.DEFAULT_CUSTOM_ENDPOINT,
        httpClient: OkHttpClient = PetalAiResearchEngine.httpClient,
        context: Context? = null
    ): AiBackend {
        return when (provider) {
            AiProvider.GEMINI -> GeminiBackend(
                apiKey = apiKey,
                httpClient = httpClient
            )
            AiProvider.GROQ -> OpenAiCompatibleBackend(
                provider = AiProvider.GROQ,
                endpoint = OpenAiCompatibleBackend.GROQ_ENDPOINT,
                apiKey = apiKey,
                httpClient = httpClient
            )
            AiProvider.CUSTOM -> OpenAiCompatibleBackend(
                provider = AiProvider.CUSTOM,
                endpoint = customEndpoint,
                apiKey = apiKey,
                httpClient = httpClient
            )
            AiProvider.LOCAL_ON_DEVICE -> {
                com.petal.browser.compose.ai.ondevice.OnDeviceGemmaBackend(context ?: error("Context required for on-device Gemma"))
            }
        }
    }
}
