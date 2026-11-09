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

    @JvmStatic
    fun getDnsProviderName(context: Context?): String {
        if (context == null) return "System Default"
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = sp.getString("sp_private_dns_mode", DNS_OFF) ?: return "System Default"
        return when (mode) {
            DNS_CLOUDFLARE -> "Cloudflare 1.1.1.1"
            DNS_GOOGLE -> "Google Public DNS"
            DNS_CLEANBROWSING -> "CleanBrowsing Family Filter"
            DNS_OPENDNS -> "OpenDNS"
            DNS_NEXTDNS -> "NextDNS"
            DNS_QUAD9 -> "Quad9"
            DNS_CUSTOM -> "Custom DoH"
            else -> "System Default"
        }
    }

    @JvmStatic
    fun getDnsHostname(context: Context?): String? {
        if (context == null) return null
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = sp.getString("sp_private_dns_mode", DNS_OFF) ?: return null
        return when (mode) {
            DNS_CLOUDFLARE -> "one.one.one.one"
            DNS_GOOGLE -> "dns.google"
            DNS_CLEANBROWSING -> "family-filter-dns.cleanbrowsing.org"
            DNS_OPENDNS -> "dns.opendns.com"
            DNS_NEXTDNS -> "dns.nextdns.io"
            DNS_QUAD9 -> "dns.quad9.net"
            else -> null
        }
    }

    @JvmStatic
    fun getDnsEndpointUrl(sp: SharedPreferences?): String? {
        if (sp == null) return null
        val mode = sp.getString("sp_private_dns_mode", DNS_OFF)
        if (mode == null || DNS_OFF == mode) return null
        return when (mode) {
            DNS_CLOUDFLARE -> "https://cloudflare-dns.com/dns-query"
            DNS_GOOGLE -> "https://dns.google/dns-query"
            DNS_CLEANBROWSING -> "https://doh.cleanbrowsing.org/doh/family-filter/"
            DNS_OPENDNS -> "https://doh.opendns.com/dns-query"
            DNS_NEXTDNS -> "https://dns.nextdns.io/dns-query"
            DNS_QUAD9 -> "https://dns.quad9.net/dns-query"
            DNS_CUSTOM -> {
                val custom = sp.getString("sp_custom_doh_url", sp.getString("sp_private_dns_custom_url", ""))
                if (!custom.isNullOrBlank()) custom.trim() else null
            }
            else -> null
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
