package com.petal.browser.engine.gecko

import android.content.SharedPreferences
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Per-site color-scheme override: follow the global setting, force dark, or force light.
 *
 * Stored as `sp_force_dark_$domain` (String). The global `sp_force_dark_mode` key is never written
 * from here. GeckoView only exposes a runtime-wide `preferredColorScheme`, so the active tab applies
 * its override while it is selected and gives it back when it is left (see
 * [PetalGeckoRuntime.setSiteColorScheme]).
 */
object PetalSiteColorScheme {
    const val FOLLOW = "FOLLOW"
    const val DARK = "DARK"
    const val LIGHT = "LIGHT"

    fun key(domain: String) = "sp_force_dark_$domain"

    fun get(sp: SharedPreferences, domain: String): String {
        if (domain.isBlank()) return FOLLOW
        return try {
            when (sp.getString(key(domain), FOLLOW)?.uppercase()) {
                DARK -> DARK
                LIGHT -> LIGHT
                else -> FOLLOW
            }
        } catch (_: ClassCastException) {
            FOLLOW
        }
    }

    fun set(sp: SharedPreferences, domain: String, value: String) {
        if (domain.isBlank()) return
        val editor = sp.edit()
        if (value == FOLLOW) editor.remove(key(domain)) else editor.putString(key(domain), value)
        editor.apply()
    }

    /** Gecko color scheme constant for [value], or null when the tab should follow the global setting. */
    fun toGecko(value: String): Int? = when (value) {
        DARK -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
        LIGHT -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        else -> null
    }
}
