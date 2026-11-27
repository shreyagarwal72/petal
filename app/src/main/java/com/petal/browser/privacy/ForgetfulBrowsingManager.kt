package com.petal.browser.privacy

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.unit.PetalCacheManager

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
    fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        if (trimmed.startsWith("about:", ignoreCase = true) || trimmed.startsWith("petal://", ignoreCase = true)) {
            return null
        }
        val withoutScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed.substring(8)
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed.substring(7)
            else -> return null
        }
        val rawHost = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        val clean = rawHost.lowercase().removePrefix("www.").trim()
        return clean.takeIf { it.isNotBlank() }
    }

    /** Pure rule: returns unique normalized domains in [closedUrls] absent from [remainingUrls]. */
    @JvmStatic
    fun domainsNoLongerOpen(
        closedUrls: Collection<String>,
        remainingUrls: Collection<String>,
        isForgetful: (String) -> Boolean
    ): List<String> {
        if (closedUrls.isEmpty()) return emptyList()
        val remainingHosts = remainingUrls.mapNotNullTo(HashSet()) { hostOf(it) }
        val result = ArrayList<String>()
        val seen = HashSet<String>()
        for (url in closedUrls) {
            val host = hostOf(url) ?: continue
            if (host !in remainingHosts && seen.add(host) && isForgetful(host)) {
                result.add(host)
            }
        }
        return result
    }

    @JvmStatic
    fun purgeIfClosed(context: Context, closedUrls: Collection<String>, remainingUrls: Collection<String>) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val forgetfulSet = sp.getStringSet(PREF_FORGETFUL_DOMAINS, emptySet()) ?: emptySet()
        if (forgetfulSet.isEmpty()) return
        val toPurge = domainsNoLongerOpen(closedUrls, remainingUrls) { it in forgetfulSet }
        for (domain in toPurge) {
            onDomainTabsClosed(context, domain)
        }
    }

    @JvmStatic
    fun onDomainTabsClosed(context: Context, domain: String?) {
        if (domain.isNullOrBlank()) return
        if (isDomainForgetful(context, domain)) {
            Log.i(TAG, "All tabs for forgetful domain $domain closed. Wiping site data.")
            try {
                PetalCacheManager.clearSiteData(context, domain)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to purge forgetful domain $domain: ${t.message}")
            }
        }
    }
}
