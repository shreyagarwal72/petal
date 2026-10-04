package com.petal.browser.unit;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class PrivateDnsUnit {
    public static final String DNS_OFF = "OFF";
    public static final String DNS_CLOUDFLARE = "CLOUDFLARE";
    public static final String DNS_GOOGLE = "GOOGLE";
    public static final String DNS_CLEANBROWSING = "CLEANBROWSING";
    public static final String DNS_OPENDNS = "OPENDNS";
    public static final String DNS_NEXTDNS = "NEXTDNS";
    public static final String DNS_QUAD9 = "QUAD9";
    public static final String DNS_CUSTOM = "CUSTOM";

    public static String getDnsProviderName(Context context) {
        if (context == null) return "System Default";
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = sp.getString("sp_private_dns_mode", DNS_OFF);
        if (mode == null) return "System Default";
        switch (mode) {
            case DNS_CLOUDFLARE: return "Cloudflare 1.1.1.1";
            case DNS_GOOGLE: return "Google Public DNS";
            case DNS_CLEANBROWSING: return "CleanBrowsing Family Filter";
            case DNS_OPENDNS: return "OpenDNS";
            case DNS_NEXTDNS: return "NextDNS";
            case DNS_QUAD9: return "Quad9";
            case DNS_CUSTOM: return "Custom DoH";
            default: return "System Default";
        }
    }

    public static String getDnsHostname(Context context) {
        if (context == null) return null;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = sp.getString("sp_private_dns_mode", DNS_OFF);
        if (mode == null) return null;
        switch (mode) {
            case DNS_CLOUDFLARE: return "one.one.one.one";
            case DNS_GOOGLE: return "dns.google";
            case DNS_CLEANBROWSING: return "family-filter-dns.cleanbrowsing.org";
            case DNS_OPENDNS: return "dns.opendns.com";
            case DNS_NEXTDNS: return "dns.nextdns.io";
            case DNS_QUAD9: return "dns.quad9.net";
            default: return null;
        }
    }

    public static String getDnsEndpointUrl(SharedPreferences sp) {
        if (sp == null) return null;
        String mode = sp.getString("sp_private_dns_mode", DNS_OFF);
        if (mode == null || DNS_OFF.equals(mode)) return null;
        switch (mode) {
            case DNS_CLOUDFLARE: return "https://cloudflare-dns.com/dns-query";
            case DNS_GOOGLE: return "https://dns.google/dns-query";
            case DNS_CLEANBROWSING: return "https://doh.cleanbrowsing.org/doh/family-filter/";
            case DNS_OPENDNS: return "https://doh.opendns.com/dns-query";
            case DNS_NEXTDNS: return "https://dns.nextdns.io/dns-query";
            case DNS_QUAD9: return "https://dns.quad9.net/dns-query";
            case DNS_CUSTOM: {
                String custom = sp.getString("sp_custom_doh_url", sp.getString("sp_private_dns_custom_url", ""));
                return (custom != null && !custom.trim().isEmpty()) ? custom.trim() : null;
            }
            default: return null;
        }
    }

    public static String getDnsEndpointUrl(Context context) {
        if (context == null) return null;
        return getDnsEndpointUrl(PreferenceManager.getDefaultSharedPreferences(context));
    }

    public static boolean isPrivateDnsActive(Context context) {
        if (context == null) return false;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = sp.getString("sp_private_dns_mode", DNS_OFF);
        return mode != null && !DNS_OFF.equals(mode);
    }
}
