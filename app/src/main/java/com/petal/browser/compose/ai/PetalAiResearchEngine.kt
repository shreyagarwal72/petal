package com.petal.browser.compose.ai

import android.content.Context
import androidx.preference.PreferenceManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class AiProvider(
    val id: String,
    val displayName: String,
    val keyUrl: String,
    val defaultModel: String,
    val availableModels: List<String>
) {
    GEMINI(
        "gemini",
        "Google Gemini",
        "https://aistudio.google.com/app/apikey",
        "gemini-3.5-flash-lite",
        listOf("gemini-3.5-flash-lite", "gemini-3.6-flash")
    ),
    GROQ(
        "groq",
        "Groq",
        "https://console.groq.com/keys",
        "openai/gpt-oss-120b",
        listOf("openai/gpt-oss-120b", "qwen/qwen3.6-27b")
    ),
    CUSTOM(
        "custom",
        "Custom AI",
        "",
        "custom-model",
        listOf("custom-model")
    ),
    LOCAL_ON_DEVICE(
        "local_on_device",
        "On-Device (Gemma)",
        "",
        "gemma-2b-it",
        listOf("gemma-2b-it", "gemma-2-2b-it")
    );

    companion object {
        fun fromId(id: String): AiProvider {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: GEMINI
        }
    }
}

enum class ResearchMode(val title: String, val promptPrefix: String) {
    SUMMARY(
        "Executive Summary",
        "Provide a concise, highly structured executive summary of this webpage. Include key bullet points, main purpose, and core conclusions."
    ),
    DEEP_RESEARCH(
        "Deep Analysis",
        "Perform a deep, comprehensive research analysis of this webpage content. Evaluate arguments, list key data points, identify target audience, and detail core insights."
    ),
    KEY_QA(
        "Key Q&A",
        "Identify and answer the top 5 essential questions that this webpage addresses."
    ),
    CRITIQUE(
        "Fact Check & Critique",
        "Critically evaluate this webpage content. Assess accuracy, potential bias, tone, methodology, and missing context."
    ),
    CUSTOM(
        "Custom Prompt",
        ""
    )
}

object PetalAiResearchEngine {

    internal val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getSelectedProvider(context: Context): AiProvider {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val id = sp.getString("sp_ai_provider", AiProvider.GEMINI.id) ?: AiProvider.GEMINI.id
        return AiProvider.fromId(id)
    }

    fun setSelectedProvider(context: Context, provider: AiProvider) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putString("sp_ai_provider", provider.id).apply()
    }

    const val KEY_AUTO_PAGE_CONTEXT = "sp_ai_auto_page_context"
    const val KEY_GENERATE_PAGE_SUGGESTIONS = "sp_ai_generate_page_suggestions"
    const val KEY_CLOUD_CONSENT_ACCEPTED = "sp_ai_cloud_consent_accepted"

    fun getApiKey(context: Context, provider: AiProvider): String {
        return PetalAiKeyStorage.getKey(context, provider.id)
    }

    fun setApiKey(context: Context, provider: AiProvider, key: String) {
        PetalAiKeyStorage.setKey(context, provider.id, key)
    }

    fun isAutoPageContextEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(KEY_AUTO_PAGE_CONTEXT, true)
    }

    fun setAutoPageContextEnabled(context: Context, enabled: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean(KEY_AUTO_PAGE_CONTEXT, enabled).apply()
    }

    fun isGeneratePageSuggestionsEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(KEY_GENERATE_PAGE_SUGGESTIONS, false)
    }

    fun setGeneratePageSuggestionsEnabled(context: Context, enabled: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean(KEY_GENERATE_PAGE_SUGGESTIONS, enabled).apply()
    }

    fun isCloudConsentAccepted(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(KEY_CLOUD_CONSENT_ACCEPTED, false)
    }

    fun setCloudConsentAccepted(context: Context, accepted: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean(KEY_CLOUD_CONSENT_ACCEPTED, accepted).apply()
    }

    fun isLocalEndpoint(context: Context): Boolean {
        val provider = getSelectedProvider(context)
        if (provider == AiProvider.LOCAL_ON_DEVICE) return true
        if (provider != AiProvider.CUSTOM) return false
        val endpoint = getCustomEndpoint(context).lowercase()
        return endpoint.contains("localhost") ||
                endpoint.contains("127.0.0.1") ||
                endpoint.contains("10.") ||
                endpoint.contains("192.168.") ||
                endpoint.contains(".local") ||
                Regex("""^https?://172\.(1[6-9]|2[0-9]|3[0-1])\.""").containsMatchIn(endpoint)
    }

    fun getFastModel(provider: AiProvider): String {
        return when (provider) {
            AiProvider.GEMINI -> "gemini-3.5-flash-lite"
            AiProvider.GROQ -> "qwen/qwen3.6-27b"
            AiProvider.CUSTOM -> "llama3.2:3b"
            AiProvider.LOCAL_ON_DEVICE -> "gemma-2b-it"
        }
    }

    fun getStrongerModel(provider: AiProvider): String {
        return when (provider) {
            AiProvider.GEMINI -> "gemini-3.6-flash"
            AiProvider.GROQ -> "openai/gpt-oss-120b"
            AiProvider.CUSTOM -> "llama3.3:70b"
            AiProvider.LOCAL_ON_DEVICE -> "gemma-2-2b-it"
        }
    }

    fun isVisionSupported(model: String): Boolean {
        val m = model.lowercase(Locale.ROOT)
        return m.contains("gemini") || m.contains("gpt-4") || m.contains("vision") ||
                m.contains("vl") || m.contains("llava") || m.contains("minicpm")
    }

    fun isVisionSupported(provider: AiProvider, model: String): Boolean {
        return when (provider) {
            AiProvider.GEMINI -> true
            AiProvider.GROQ -> model.contains("vision") || model.contains("vl")
            AiProvider.CUSTOM -> isVisionSupported(model)
            AiProvider.LOCAL_ON_DEVICE -> false
        }
    }

    fun getSelectedModel(context: Context, provider: AiProvider): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val stored = sp.getString("sp_ai_model_${provider.id}", provider.defaultModel) ?: provider.defaultModel
        // Migrate retired or legacy models to provider defaults
        return when (provider) {
            AiProvider.GEMINI -> {
                if (stored == "gemini-2.0-flash" || stored == "gemini-1.5-flash" || stored == "gemini-1.5-pro" || stored == "gemini-2.5-flash-lite") {
                    provider.defaultModel
                } else {
                    stored
                }
            }
            AiProvider.GROQ -> {
                if (stored == "llama-3.3-70b-versatile" || stored == "mixtral-8x7b-32768" || stored == "llama-4-scout") {
                    provider.defaultModel
                } else {
                    stored
                }
            }
            AiProvider.CUSTOM -> stored.ifBlank { provider.defaultModel }
            AiProvider.LOCAL_ON_DEVICE -> stored.ifBlank { provider.defaultModel }
        }
    }

    fun setSelectedModel(context: Context, provider: AiProvider, model: String) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putString("sp_ai_model_${provider.id}", model.trim()).apply()
    }

    fun getCustomEndpoint(context: Context): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getString("sp_ai_custom_endpoint", "http://localhost:11434/v1") ?: "http://localhost:11434/v1"
    }

    fun setCustomEndpoint(context: Context, endpoint: String) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putString("sp_ai_custom_endpoint", endpoint.trim()).apply()
    }

    fun validateEndpoint(endpoint: String): Boolean {
        val trimmed = endpoint.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.startsWith("https://")) return true
        if (!trimmed.startsWith("http://")) return false
        val host = try { URI(trimmed).host } catch (_: Exception) { return false }
        if (host.isNullOrEmpty()) return false
        return isPrivateHost(host)
    }

    internal fun isPrivateHost(host: String): Boolean {
        val h = host.trim().lowercase(Locale.ROOT).removeSurrounding("[", "]").removeSuffix(".")
        if (h == "localhost" || h == "::1") return true
        if (h.endsWith(".local") || h.endsWith(".lan")) return true
        val parts = h.split('.')
        if (parts.size != 4) return false
        val ip = IntArray(4)
        for (i in parts.indices) {
            val n = parts[i].toIntOrNull() ?: return false
            if (n !in 0..255) return false
            ip[i] = n
        }
        if (ip[0] == 127) return true
        if (ip[0] == 10) return true
        if (ip[0] == 172 && ip[1] in 16..31) return true
        if (ip[0] == 192 && ip[1] == 168) return true
        if (ip[0] == 169 && ip[1] == 254) return true
        if (ip[0] == 100 && ip[1] in 64..127) return true
        return false
    }

    fun fetchCustomModels(context: Context, onResult: (Result<List<String>>) -> Unit) {
        val endpoint = getCustomEndpoint(context)
        val apiKey = getApiKey(context, AiProvider.CUSTOM)
        Thread {
            try {
                if (!validateEndpoint(endpoint)) {
                    throw IllegalArgumentException("Endpoint must be https:// or an http:// private-LAN address (e.g. localhost, 192.168.x.x)")
                }
                val baseUrl = endpoint.trimEnd('/')
                var models: List<String> = emptyList()
                val candidatePaths = listOf(
                    "$baseUrl/chat/completions" to "$baseUrl/models",
                    "$baseUrl/models" to "$baseUrl/models",
                    "$baseUrl/v1/models" to "$baseUrl/v1/models",
                    "$baseUrl/api/tags" to "$baseUrl/api/tags"
                )
                val pathsToTry = if (baseUrl.endsWith("/v1")) {
                    listOf("$baseUrl/models", "$baseUrl/api/tags")
                } else {
                    listOf("$baseUrl/v1/models", "$baseUrl/models", "$baseUrl/api/tags", "$baseUrl/api/models")
                }

                for (path in pathsToTry) {
                    try {
                        val reqBuilder = Request.Builder().url(path).get()
                        if (apiKey.isNotBlank()) {
                            reqBuilder.addHeader("Authorization", "Bearer $apiKey")
                        }
                        val resp = httpClient.newCall(reqBuilder.build()).execute()
                        val body = resp.body?.string() ?: ""
                        if (resp.isSuccessful && body.isNotBlank()) {
                            val parsed = parseModelIds(body)
                            if (parsed.isNotEmpty()) {
                                models = parsed
                                break
                            }
                        }
                    } catch (_: Exception) {}
                }

                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                mainHandler.post { onResult(Result.success(models)) }
            } catch (e: Exception) {
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                mainHandler.post { onResult(Result.failure(e)) }
            }
        }.start()
    }

    private fun parseModelIds(body: String): List<String> {
        return try {
            val json = JSONObject(body)
            val list = mutableListOf<String>()
            val dataArr = json.optJSONArray("data") ?: json.optJSONArray("models")
            if (dataArr != null) {
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.optJSONObject(i)
                    val id = item?.optString("id")?.takeIf { it.isNotBlank() }
                        ?: item?.optString("name")?.takeIf { it.isNotBlank() }
                    if (id != null) list.add(id)
                }
            } else {
                // Ollama /api/tags
                val tagsArr = json.optJSONArray("models")
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        val name = tagsArr.optJSONObject(i)?.optString("name")
                        if (!name.isNullOrBlank()) list.add(name)
                    }
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isProperWebSite(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.trim().lowercase(Locale.ROOT)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false

        // Exclude search engine results pages like https://www.google.com/search?q=ii
        if (lower.contains("google.") && (lower.contains("/search") || lower.contains("q="))) return false
        if (lower.contains("bing.com/search")) return false
        if (lower.contains("duckduckgo.com") && lower.contains("q=")) return false
        if (lower.contains("search.yahoo.com")) return false
        if (lower.contains("yandex.") && lower.contains("search")) return false
        if (lower.contains("baidu.com/s")) return false

        return true
    }

    fun performResearch(
        context: Context,
        pageTitle: String,
        pageUrl: String,
        pageTextContent: String,
        mode: ResearchMode,
        customPrompt: String = "",
        onResult: (Result<String>) -> Unit
    ) {
        val provider = getSelectedProvider(context)
        val apiKey = getApiKey(context, provider)
        val model = getSelectedModel(context, provider)

        // For Custom provider, API key can be optional (e.g. Ollama or local LM Studio without auth)
        if (apiKey.isBlank() && provider != AiProvider.CUSTOM) {
            onResult(Result.failure(IllegalArgumentException("No API key configured for ${provider.displayName}. Please add your API key in AI Settings.")))
            return
        }

        val truncatedContent = if (pageTextContent.length > 12000) {
            pageTextContent.substring(0, 12000) + "\n...[Content truncated for length]"
        } else {
            pageTextContent
        }

        val userPrompt = if (mode == ResearchMode.CUSTOM) {
            """
            WEBPAGE METADATA:
            Title: $pageTitle
            URL: $pageUrl

            WEBPAGE CONTENT:
            $truncatedContent

            USER QUESTION / PROMPT:
            $customPrompt
            """.trimIndent()
        } else {
            """
            WEBPAGE METADATA:
            Title: $pageTitle
            URL: $pageUrl

            WEBPAGE CONTENT:
            $truncatedContent

            INSTRUCTIONS:
            ${mode.promptPrefix}
            """.trimIndent()
        }

        val systemPrompt = "You are Petal AI Research, an elite real-time Web & Research Assistant embedded in Petal Browser. Analyze the provided webpage content accurately, objectively, and concisely using Markdown formatting."

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            try {
                val backend = AiBackendFactory.getBackend(context, provider)
                val messages = listOf(
                    AiChatMessage.system(systemPrompt),
                    AiChatMessage.user(userPrompt)
                )
                val responseBuilder = StringBuilder()
                kotlinx.coroutines.runBlocking {
                    backend.stream(messages, model).collect { chunk ->
                        responseBuilder.append(chunk)
                    }
                }
                val responseText = responseBuilder.toString()
                if (responseText.isBlank()) {
                    throw AiBackendException.EmptyReplyException("The model returned an empty response.")
                }
                mainHandler.post { onResult(Result.success(responseText)) }
            } catch (e: Exception) {
                mainHandler.post { onResult(Result.failure(e)) }
            }
        }.start()
    }

    internal fun normalizeOpenAiChatEndpoint(value: String): String =
        OpenAiCompatibleBackend.normalizeEndpoint(value)
}
