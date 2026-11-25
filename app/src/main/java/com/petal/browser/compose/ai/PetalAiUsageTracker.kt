package com.petal.browser.compose.ai

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records input and output token consumption per AI provider.
 * Uses exact token counts from API response metadata when provided;
 * otherwise estimates token count based on character heuristics (~4 characters per token).
 *
 * Tracks consumption for:
 * - Today (resets at local midnight)
 * - This Month (resets at first of the month)
 *
 * Supports an optional soft daily limit with user confirmation.
 */
object PetalAiUsageTracker {

    private const val PREFS_NAME = "petal_ai_usage_prefs"
    const val KEY_DAILY_LIMIT = "sp_ai_soft_daily_token_limit"
    const val DEFAULT_DAILY_LIMIT = 0L // 0 means disabled / no limit
    const val TOKENS_PER_IMAGE = 300

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun todayKey(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    }

    private fun monthKey(): String {
        return SimpleDateFormat("yyyyMM", Locale.US).format(Date())
    }

    /**
     * Estimates tokens from character count if exact tokens are unavailable (~4 chars/token).
     */
    fun estimateTokens(chars: Int): Int {
        if (chars <= 0) return 0
        return (chars + 3) / 4
    }

    /**
     * Estimates input tokens for [messages], adding [TOKENS_PER_IMAGE] for every image part sent.
     */
    fun estimateInputTokens(messages: List<AiChatMessage>): Int {
        val textChars = messages.sumOf { it.textContent.length }
        val imageParts = messages.sumOf { msg -> msg.parts.count { it is AiMessagePart.Image } }
        return estimateTokens(textChars) + (imageParts * TOKENS_PER_IMAGE)
    }

    /**
     * Records token usage from estimated or exact token counts.
     */
    fun recordUsage(
        context: Context,
        inputTokens: Int,
        outputTokens: Int
    ) {
        val provider = PetalAiResearchEngine.getSelectedProvider(context)
        recordUsage(
            context = context,
            providerId = provider.id,
            inputChars = inputTokens * 4,
            outputChars = outputTokens * 4,
            exactInputTokens = inputTokens,
            exactOutputTokens = outputTokens
        )
    }

    /**
     * Records token usage for a completed chat response.
     */
    @Synchronized
    fun recordUsage(
        context: Context,
        providerId: String,
        inputChars: Int,
        outputChars: Int,
        exactInputTokens: Int? = null,
        exactOutputTokens: Int? = null,
        imageCount: Int = 0
    ) {
        val sp = prefs(context)
        val inTokens = exactInputTokens
            ?: (estimateTokens(inputChars) + (imageCount.coerceAtLeast(0) * TOKENS_PER_IMAGE))
        val outTokens = exactOutputTokens ?: estimateTokens(outputChars)
        val total = (inTokens + outTokens).toLong()

        val today = todayKey()
        val month = monthKey()

        val todayTotal = sp.getLong("tokens_day_$today", 0L) + total
        val monthTotal = sp.getLong("tokens_month_$month", 0L) + total

        val providerDayKey = "tokens_p_${providerId}_day_$today"
        val providerMonthKey = "tokens_p_${providerId}_month_$month"

        val pDayTotal = sp.getLong(providerDayKey, 0L) + total
        val pMonthTotal = sp.getLong(providerMonthKey, 0L) + total

        sp.edit()
            .putLong("tokens_day_$today", todayTotal)
            .putLong("tokens_month_$month", monthTotal)
            .putLong(providerDayKey, pDayTotal)
            .putLong(providerMonthKey, pMonthTotal)
            .apply()
    }

    /**
     * Gets total tokens consumed today across all providers or for a specific [providerId].
     */
    fun getTodayTokens(context: Context, providerId: String? = null): Long {
        val sp = prefs(context)
        val today = todayKey()
        return if (providerId != null) {
            sp.getLong("tokens_p_${providerId}_day_$today", 0L)
        } else {
            sp.getLong("tokens_day_$today", 0L)
        }
    }

    /**
     * Gets total tokens consumed this month across all providers or for a specific [providerId].
     */
    fun getMonthTokens(context: Context, providerId: String? = null): Long {
        val sp = prefs(context)
        val month = monthKey()
        return if (providerId != null) {
            sp.getLong("tokens_p_${providerId}_month_$month", 0L)
        } else {
            sp.getLong("tokens_month_$month", 0L)
        }
    }

    /**
     * Checks if today's token consumption exceeds the configured soft daily limit.
     */
    fun isDailyLimitExceeded(context: Context): Boolean {
        val limit = getDailyLimit(context)
        if (limit <= 0L) return false
        return getTodayTokens(context) >= limit
    }

    fun getDailyLimit(context: Context): Long {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getLong(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT)
    }

    fun setDailyLimit(context: Context, limit: Long) {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putLong(KEY_DAILY_LIMIT, limit).apply()
    }

    /**
     * Clears all recorded usage statistics.
     */
    @Synchronized
    fun resetUsage(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
