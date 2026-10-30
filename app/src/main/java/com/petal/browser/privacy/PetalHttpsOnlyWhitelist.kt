package com.petal.browser.privacy

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * PetalHttpsOnlyWhitelist
 * Manages per-domain bypass exceptions for HTTPS-Only mode matching Mozilla Firefox.
 * When an insecure origin cannot be upgraded or when the user explicitly chooses to load HTTP,
 * the domain is whitelisted so subsequent navigations are not blocked.
 */
object PetalHttpsOnlyWhitelist {
    private const val PREF_KEY = "sp_https_only_bypass_hosts"
    private val memoryBypass = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    @JvmStatic
    fun isBypassed(context: Context, host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val clean = host.lowercase(Locale.ROOT).removePrefix("www.")
        if (memoryBypass.contains(clean)) return true
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val set = sp.getStringSet(PREF_KEY, null)
        return set?.contains(clean) == true
    }

    @JvmStatic
    fun addBypass(context: Context, host: String?) {
        if (host.isNullOrBlank()) return
        val clean = host.lowercase(Locale.ROOT).removePrefix("www.")
        memoryBypass.add(clean)
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val currentSet = sp.getStringSet(PREF_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(clean)
        sp.edit().putStringSet(PREF_KEY, currentSet).apply()
    }

    @JvmStatic
    fun removeBypass(context: Context, host: String?) {
        if (host.isNullOrBlank()) return
        val clean = host.lowercase(Locale.ROOT).removePrefix("www.")
        memoryBypass.remove(clean)
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val currentSet = sp.getStringSet(PREF_KEY, null)?.toMutableSet()
        if (currentSet != null && currentSet.remove(clean)) {
            sp.edit().putStringSet(PREF_KEY, currentSet).apply()
        }
    }
}
