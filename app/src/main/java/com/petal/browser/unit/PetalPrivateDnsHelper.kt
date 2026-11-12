package com.petal.browser.unit

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Modern DNS-over-HTTPS (DoH) configuration helper for Petal Browser.
 * Modernized from legacy PrivateDnsUnit.
 */
object PetalPrivateDnsHelper {
    const val DNS_OFF = "OFF"
    const val DNS_CLOUDFLARE = "CLOUDFLARE"
    const val DNS_GOOGLE = "GOOGLE"
    const val DNS_CLEANBROWSING = "CLEANBROWSING"
    const val DNS_OPENDNS = "OPENDNS"
    const val DNS_NEXTDNS = "NEXTDNS"
    const val DNS_QUAD9 = "QUAD9"
    const val DNS_CUSTOM = "CUSTOM"

    const val PREF_MODE = "sp_private_dns_mode"
    const val PREF_NEXTDNS_PROFILE = "sp_nextdns_profile_id"

    @JvmStatic
    fun getDnsProviderName(context: Context?): String {
        if (context == null) return "System Default"
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = sp.getString(PREF_MODE, DNS_OFF) ?: return "System Default"
        if (mode == DNS_CUSTOM) return "Custom DoH"
        return PetalDnsCatalog.find(mode)?.title ?: "System Default"
    }

    @JvmStatic
    fun getDnsHostname(context: Context?): String? {
        if (context == null) return null
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = sp.getString(PREF_MODE, DNS_OFF) ?: return null
        return PetalDnsCatalog.find(mode)?.hostname
    }

    @JvmStatic
    fun getDnsEndpointUrl(sp: SharedPreferences?): String? {
        if (sp == null) return null
        val mode = sp.getString(PREF_MODE, DNS_OFF)
        if (mode == null || DNS_OFF == mode) return null
        return when (mode) {
            DNS_CUSTOM -> {
                val custom = sp.getString("sp_custom_doh_url", sp.getString("sp_private_dns_custom_url", ""))
                if (!custom.isNullOrBlank()) custom.trim() else null
            }
            DNS_NEXTDNS -> PetalDnsCatalog.nextDnsUrl(sp.getString(PREF_NEXTDNS_PROFILE, ""))
            else -> PetalDnsCatalog.find(mode)?.dohUrl
        }
    }

    @JvmStatic
    fun getDnsEndpointUrl(context: Context?): String? {
        if (context == null) return null
        return getDnsEndpointUrl(PreferenceManager.getDefaultSharedPreferences(context))
    }

    @JvmStatic
    fun isPrivateDnsActive(context: Context?): Boolean {
        if (context == null) return false
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = sp.getString("sp_private_dns_mode", DNS_OFF)
        return mode != null && DNS_OFF != mode
    }
}
