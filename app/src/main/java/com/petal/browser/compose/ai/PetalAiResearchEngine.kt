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

    private val httpClient = OkHttpClient.Builder()
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

    fun getApiKey(context: Context, provider: AiProvider): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getString("sp_ai_key_${provider.id}", "") ?: ""
    }

    fun setApiKey(context: Context, provider: AiProvider, key: String) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putString("sp_ai_key_${provider.id}", key.trim()).apply()
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

    private fun isPrivateHost(host: String): Boolean {
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
                val responseText = when (provider) {
                    AiProvider.GEMINI -> {
                        val thinkingLevel = when (model) {
                            "gemini-3.5-flash-lite" -> "low"
                            "gemini-3.6-flash" -> "minimal"
                            else -> null
                        }
                        callGeminiApi(apiKey, model, systemPrompt, userPrompt, thinkingLevel)
                    }
                    AiProvider.GROQ -> {
                        val extraParams = when (model) {
                            "openai/gpt-oss-120b" -> mapOf("reasoning_effort" to "medium", "include_reasoning" to false)
                            "qwen/qwen3.6-27b" -> mapOf("reasoning_effort" to "none")
                            else -> emptyMap()
                        }
                        callOpenAiCompatibleApi("https://api.groq.com/openai/v1/chat/completions", apiKey, model, systemPrompt, userPrompt, extraParams = extraParams)
                    }
                    AiProvider.CUSTOM -> {
                        val rawEndpoint = getCustomEndpoint(context).trim()
                        if (!validateEndpoint(rawEndpoint)) {
                            throw IllegalArgumentException("Endpoint must be https:// or an http:// private-LAN address (e.g. localhost, 192.168.x.x)")
                        }
                        val endpoint = if (rawEndpoint.endsWith("/chat/completions")) {
                            rawEndpoint
                        } else {
                            rawEndpoint.trimEnd('/') + "/chat/completions"
                        }
                        callOpenAiCompatibleApi(endpoint, apiKey, model, systemPrompt, userPrompt)
                    }
                }
                mainHandler.post { onResult(Result.success(responseText)) }
            } catch (e: Exception) {
                mainHandler.post { onResult(Result.failure(e)) }
            }
        }.start()
    }

    private fun callOpenAiCompatibleApi(
        endpoint: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        extraHeaders: Map<String, String> = emptyMap(),
        extraParams: Map<String, Any> = emptyMap()
    ): String {
        val jsonPayload = JSONObject().apply {
            put("model", model)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }
            put("messages", messages)
            extraParams.forEach { (k, v) -> put(k, v) }
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        extraHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        val request = requestBuilder
            .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errMessage = try {
                val errJson = JSONObject(responseBody)
                errJson.optJSONObject("error")?.optString("message") ?: responseBody
            } catch (e: Exception) {
                "HTTP ${response.code}: ${response.message}"
            }
            throw RuntimeException("API Error ($errMessage)")
        }

        val resJson = JSONObject(responseBody)
        val choices = resJson.getJSONArray("choices")
        if (choices.length() == 0) throw RuntimeException("No response choices returned by API.")
        return choices.getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun callGeminiApi(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        thinkingLevel: String? = null
    ): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val jsonPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "$systemPrompt\n\n$userPrompt")
                        })
                    }
                    put("parts", parts)
                })
            })
            if (thinkingLevel != null) {
                put("generationConfig", JSONObject().apply {
                    put("thinkingConfig", JSONObject().apply {
                        put("thinkingLevel", thinkingLevel)
                    })
                })
            }
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errMessage = try {
                val errJson = JSONObject(responseBody)
                errJson.optJSONObject("error")?.optString("message") ?: responseBody
            } catch (e: Exception) {
                "HTTP ${response.code}: ${response.message}"
            }
            throw RuntimeException("Gemini API Error ($errMessage)")
        }

        val resJson = JSONObject(responseBody)
        val candidates = resJson.getJSONArray("candidates")
        if (candidates.length() == 0) throw RuntimeException("No candidates returned by Gemini API.")
        val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        return parts.getJSONObject(0).getString("text")
    }
}
