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

    public static String getDnsProviderName(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = sp.getString("sp_private_dns_mode", DNS_OFF);
        if (mode == null) return "System Default";
        switch (mode) {
            case DNS_CLOUDFLARE: return "Cloudflare 1.1.1.1";
            case DNS_GOOGLE: return "Google Public DNS";
            case DNS_CLEANBROWSING: return "CleanBrowsing Family Filter";
            case DNS_OPENDNS: return "OpenDNS";
            default: return "System Default";
        }
    }

    public static String getDnsHostname(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = sp.getString("sp_private_dns_mode", DNS_OFF);
        if (mode == null) return null;
        switch (mode) {
            case DNS_CLOUDFLARE: return "one.one.one.one";
            case DNS_GOOGLE: return "dns.google";
            case DNS_CLEANBROWSING: return "family-filter-dns.cleanbrowsing.org";
            case DNS_OPENDNS: return "dns.opendns.com";
            default: return null;
        }
    }
}
