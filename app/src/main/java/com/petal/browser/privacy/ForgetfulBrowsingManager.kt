package com.petal.browser.privacy

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.unit.CacheManager

/**
 * ForgetfulBrowsingManager
 * ─────────────────────────────────────────────────────────────────────────
 * Brave "Forgetful Browsing" implementation for Petal Browser.
 * When enabled for specific domains, all site data (cookies, cache, indexedDB, localStorage)
 * is immediately expunged when all tabs for that domain are closed.
 */
object ForgetfulBrowsingManager {
    private const val TAG = "ForgetfulBrowsing"
    private const val PREF_FORGETFUL_DOMAINS = "sp_forgetful_browsing_domains"

    @JvmStatic
    fun isDomainForgetful(context: Context, domain: String?): Boolean {
        if (domain.isNullOrBlank()) return false
        val cleanHost = domain.trim().lowercase().removePrefix("www.")
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val set = sp.getStringSet(PREF_FORGETFUL_DOMAINS, emptySet()) ?: emptySet()
        return set.contains(cleanHost)
    }

    @JvmStatic
    fun setDomainForgetful(context: Context, domain: String, forgetful: Boolean) {
        val cleanHost = domain.trim().lowercase().removePrefix("www.")
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val currentSet = sp.getStringSet(PREF_FORGETFUL_DOMAINS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (forgetful) {
            currentSet.add(cleanHost)
        } else {
            currentSet.remove(cleanHost)
        }
        sp.edit().putStringSet(PREF_FORGETFUL_DOMAINS, currentSet).apply()
        Log.i(TAG, "Forgetful status for $cleanHost set to $forgetful")
    }

    @JvmStatic
    fun onDomainTabsClosed(context: Context, domain: String?) {
        if (domain.isNullOrBlank()) return
        if (isDomainForgetful(context, domain)) {
            Log.i(TAG, "All tabs for forgetful domain $domain closed. Wiping site data.")
            try {
                CacheManager.clearSiteData(context, domain)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to purge forgetful domain $domain: ${t.message}")
            }
        }
    }
}
