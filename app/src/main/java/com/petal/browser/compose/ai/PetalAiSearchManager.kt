package com.petal.browser.compose.ai

import android.content.Context
import androidx.preference.PreferenceManager

object PetalAiSearchManager {

    fun isAddressBarAiSearchEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean("sp_ai_search_address_bar", true)
    }

    fun setAddressBarAiSearchEnabled(context: Context, enabled: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean("sp_ai_search_address_bar", enabled).apply()
    }

    fun isWidgetAiSearchEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean("sp_ai_search_widget", true)
    }

    fun setWidgetAiSearchEnabled(context: Context, enabled: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean("sp_ai_search_widget", enabled).apply()
    }

    fun getPersonalContext(context: Context): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getString("sp_ai_search_personal_context", "") ?: ""
    }

    fun setPersonalContext(context: Context, text: String) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putString("sp_ai_search_personal_context", text.trim()).apply()
    }

    fun isGroundingEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean("sp_ai_search_grounding", true)
    }

    fun setGroundingEnabled(context: Context, enabled: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean("sp_ai_search_grounding", enabled).apply()
    }

    fun isThinkingEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean("sp_ai_search_thinking", false)
    }

    fun setThinkingEnabled(context: Context, enabled: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean("sp_ai_search_thinking", enabled).apply()
    }

    fun executeAiSearch(
        context: Context,
        query: String,
        onResult: (Result<String>) -> Unit
    ) {
        val provider = PetalAiResearchEngine.getSelectedProvider(context)
        val apiKey = PetalAiResearchEngine.getApiKey(context, provider)
        val model = PetalAiResearchEngine.getSelectedModel(context, provider)

        if (apiKey.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("No API key configured for ${provider.displayName}. Please add your API key in AI Settings.")))
            return
        }

        val personalContext = getPersonalContext(context)
        val isGrounding = isGroundingEnabled(context)
        val isThinking = isThinkingEnabled(context)

        val systemPromptBuilder = StringBuilder()
        systemPromptBuilder.append("You are Petal AI Search, a helpful real-time AI search assistant integrated into Petal Browser.")
        systemPromptBuilder.append("\nProvide clear, accurate, and structured answers formatted nicely in Markdown.")
        if (personalContext.isNotBlank()) {
            systemPromptBuilder.append("\nUser Personal Context: ").append(personalContext)
        }
        if (isGrounding) {
            systemPromptBuilder.append("\nGrounding instructions: Include web-sourced facts and cite sources where relevant.")
        }
        if (isThinking) {
            systemPromptBuilder.append("\nThinking mode: Break down reasoning step-by-step before arriving at the conclusion.")
        }

        val userPrompt = "SEARCH QUERY: $query"

        PetalAiResearchEngine.performResearch(
            context = context,
            pageTitle = "AI Search Query",
            pageUrl = "petal://ai-search",
            pageTextContent = query,
            mode = ResearchMode.CUSTOM,
            customPrompt = "$userPrompt\n\n(System instructions: ${systemPromptBuilder})",
            onResult = onResult
        )
    }
}
