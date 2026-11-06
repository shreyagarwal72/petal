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
    fun isPrivateNetworkHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val clean = host.lowercase(Locale.ROOT).trim().removePrefix("www.")

        // Loopback / localhost
        if (clean == "localhost" || clean == "127.0.0.1" || clean == "::1" || clean == "[::1]") {
            return true
        }
        if (clean.startsWith("127.")) {
            return true
        }

        // .local mDNS or localhost subdomains
        if (clean.endsWith(".local") || clean == "local" || clean.endsWith(".localhost")) {
            return true
        }

        // Bare hostnames (e.g. router, nas, pihole with no dot or colon)
        if (!clean.contains('.') && !clean.contains(':')) {
            return true
        }

        // RFC1918 Private IPv4 ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16) + Link-Local
        val parts = clean.split('.')
        if (parts.size == 4 && parts.all { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }) {
            val octets = parts.mapNotNull { it.toIntOrNull() }
            if (octets.size == 4) {
                val (o1, o2, _, _) = octets
                if (o1 == 10) return true
                if (o1 == 172 && o2 in 16..31) return true
                if (o1 == 192 && o2 == 168) return true
                if (o1 == 127) return true
                if (o1 == 169 && o2 == 254) return true
            }
        }

        return false
    }

    @JvmStatic
    fun isBypassed(context: Context, host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val clean = host.lowercase(Locale.ROOT).removePrefix("www.")
        if (isPrivateNetworkHost(clean)) return true
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
