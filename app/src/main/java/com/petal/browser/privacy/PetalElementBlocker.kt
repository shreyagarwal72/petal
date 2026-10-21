package com.petal.browser.privacy

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * PetalElementBlocker
 * ─────────────────────────────────────────────────────────────────────────
 * Brave "Cosmetic Element Blocker" implementation for Petal Browser.
 * Stores per-domain custom CSS selector hide rules and injects them into web pages.
 */
object PetalElementBlocker {
    private const val TAG = "PetalElementBlocker"
    private const val PREF_KEY_RULES = "sp_brave_element_blocker_rules"

    @JvmStatic
    fun addSelector(context: Context, domain: String, cssSelector: String) {
        val cleanDomain = domain.trim().lowercase().removePrefix("www.")
        val cleanSelector = cssSelector.trim()
        if (cleanDomain.isBlank() || cleanSelector.isBlank()) return

        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val existing = getRulesForDomain(context, cleanDomain).toMutableList()
        if (!existing.contains(cleanSelector)) {
            existing.add(cleanSelector)
            val allRules = getAllRulesJson(sp)
            allRules.put(cleanDomain, JSONArray(existing))
            sp.edit().putString(PREF_KEY_RULES, allRules.toString()).apply()
            Log.i(TAG, "Added custom hide rule for $cleanDomain: $cleanSelector")
        }
    }

    @JvmStatic
    fun getRulesForDomain(context: Context, domain: String): List<String> {
        val cleanDomain = domain.trim().lowercase().removePrefix("www.")
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val allRules = getAllRulesJson(sp)
        val arr = allRules.optJSONArray(cleanDomain) ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
        return list
    }

    @JvmStatic
    fun getInjectionCss(context: Context, domain: String): String {
        val rules = getRulesForDomain(context, domain)
        if (rules.isEmpty()) return ""
        val selectors = rules.joinToString(", ")
        return "$selectors { display: none !important; visibility: hidden !important; opacity: 0 !important; pointer-events: none !important; }"
    }

    private fun getAllRulesJson(sp: android.content.SharedPreferences): JSONObject {
        val str = sp.getString(PREF_KEY_RULES, null) ?: return JSONObject()
        return try {
            JSONObject(str)
        } catch (_: Exception) {
            JSONObject()
        }
    }
}
