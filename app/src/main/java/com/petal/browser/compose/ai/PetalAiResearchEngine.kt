package com.petal.browser.compose.ai

import android.content.Context
import androidx.preference.PreferenceManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class AiProvider(val id: String, val displayName: String, val keyUrl: String, val defaultModel: String, val availableModels: List<String>) {
    OPENROUTER(
        "openrouter",
        "OpenRouter",
        "https://openrouter.ai/keys",
        "google/gemini-2.0-flash-001",
        listOf(
            "google/gemini-2.0-flash-001",
            "anthropic/claude-3.5-sonnet",
            "openai/gpt-4o-mini",
            "meta-llama/llama-3.3-70b-instruct",
            "deepseek/deepseek-r1"
        )
    ),
    GEMINI(
        "gemini",
        "Google Gemini",
        "https://aistudio.google.com/app/apikey",
        "gemini-2.0-flash",
        listOf("gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash")
    ),
    GROK(
        "grok",
        "xAI Grok",
        "https://console.x.ai/",
        "grok-2-latest",
        listOf("grok-2-latest", "grok-beta")
    ),
    OPENAI(
        "openai",
        "OpenAI",
        "https://platform.openai.com/api-keys",
        "gpt-4o-mini",
        listOf("gpt-4o-mini", "gpt-4o", "o3-mini")
    ),
    GROQ(
        "groq",
        "Groq",
        "https://console.groq.com/keys",
        "llama-3.3-70b-versatile",
        listOf("llama-3.3-70b-versatile", "mixtral-8x7b-32768")
    );

    companion object {
        fun fromId(id: String): AiProvider {
            return entries.find { it.id == id } ?: OPENROUTER
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
        val id = sp.getString("sp_ai_provider", AiProvider.OPENROUTER.id) ?: AiProvider.OPENROUTER.id
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
        return sp.getString("sp_ai_model_${provider.id}", provider.defaultModel) ?: provider.defaultModel
    }

    fun setSelectedModel(context: Context, provider: AiProvider, model: String) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putString("sp_ai_model_${provider.id}", model.trim()).apply()
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

        if (apiKey.isBlank()) {
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

        Thread {
            try {
                val responseText = when (provider) {
                    AiProvider.OPENROUTER -> callOpenAiCompatibleApi("https://openrouter.ai/api/v1/chat/completions", apiKey, model, systemPrompt, userPrompt, mapOf("HTTP-Referer" to "https://github.com/shreyagarwal72/petal", "X-Title" to "Petal Browser"))
                    AiProvider.GEMINI -> callGeminiApi(apiKey, model, systemPrompt, userPrompt)
                    AiProvider.GROK -> callOpenAiCompatibleApi("https://api.x.ai/v1/chat/completions", apiKey, model, systemPrompt, userPrompt)
                    AiProvider.OPENAI -> callOpenAiCompatibleApi("https://api.openai.com/v1/chat/completions", apiKey, model, systemPrompt, userPrompt)
                    AiProvider.GROQ -> callOpenAiCompatibleApi("https://api.groq.com/openai/v1/chat/completions", apiKey, model, systemPrompt, userPrompt)
                }
                onResult(Result.success(responseText))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }.start()
    }

    private fun callOpenAiCompatibleApi(
        endpoint: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        extraHeaders: Map<String, String> = emptyMap()
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
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")

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
        userPrompt: String
    ): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val jsonPayload = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "$systemPrompt\n\n$userPrompt")
                        })
                    }
                    put("parts", parts)
                })
            }
            put("contents", contents)
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
