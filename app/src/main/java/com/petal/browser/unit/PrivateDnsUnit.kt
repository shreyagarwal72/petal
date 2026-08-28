package com.petal.browser.unit

import android.content.Context
import androidx.preference.PreferenceManager

object PrivateDnsUnit {
    const val DNS_OFF = "OFF"
    const val DNS_CLOUDFLARE = "CLOUDFLARE"
    const val DNS_GOOGLE = "GOOGLE"
    const val DNS_CLEANBROWSING = "CLEANBROWSING"
    const val DNS_OPENDNS = "OPENDNS"

    @JvmStatic
    fun getDnsProviderName(context: Context): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = sp.getString("sp_private_dns_mode", DNS_OFF) ?: return "System Default"
        return when (mode) {
            DNS_CLOUDFLARE -> "Cloudflare 1.1.1.1"
            DNS_GOOGLE -> "Google Public DNS"
            DNS_CLEANBROWSING -> "CleanBrowsing Family Filter"
            DNS_OPENDNS -> "OpenDNS"
            else -> "System Default"
        }
    }

    @JvmStatic
    fun getDnsHostname(context: Context): String? {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = sp.getString("sp_private_dns_mode", DNS_OFF) ?: return null
        return when (mode) {
            DNS_CLOUDFLARE -> "one.one.one.one"
            DNS_GOOGLE -> "dns.google"
            DNS_CLEANBROWSING -> "family-filter-dns.cleanbrowsing.org"
            DNS_OPENDNS -> "dns.opendns.com"
            else -> null
        }
    }
}
