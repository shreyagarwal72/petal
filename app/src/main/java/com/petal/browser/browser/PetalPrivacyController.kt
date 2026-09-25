package com.petal.browser.browser

import android.content.Context
import androidx.preference.PreferenceManager
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import org.mozilla.geckoview.ContentBlocking

/**
 * PetalPrivacyController
 * ─────────────────────────────────────────────────────────────────────────
 * Manages Enhanced Tracking Protection (ETP), anti-tracking policies,
 * cookie behaviors, and site isolation for GeckoEngine.
 */
object PetalPrivacyController {

    enum class ProtectionLevel {
        STANDARD,
        STRICT,
        CUSTOM
    }

    private const val PREF_TRACKING_PROTECTION = "sp_tracking_protection_level"

    fun setProtectionLevel(context: Context, level: ProtectionLevel) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putString(PREF_TRACKING_PROTECTION, level.name).apply()

        val runtime = PetalGeckoRuntime.getOrCreate(context)
        val etpLevel = when (level) {
            ProtectionLevel.STANDARD -> ContentBlocking.EtpLevel.DEFAULT
            ProtectionLevel.STRICT -> ContentBlocking.EtpLevel.STRICT
            ProtectionLevel.CUSTOM -> ContentBlocking.EtpLevel.DEFAULT
        }

        try {
            runtime.settings.contentBlocking.enhancedTrackingProtectionLevel = etpLevel
            if (level == ProtectionLevel.STRICT) {
                runtime.settings.contentBlocking.strictSocialTrackingProtection = true
                runtime.settings.contentBlocking.cookieBehavior =
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
            } else {
                runtime.settings.contentBlocking.strictSocialTrackingProtection = false
                runtime.settings.contentBlocking.cookieBehavior =
                    ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
            }
        } catch (_: Throwable) {}
    }

    fun getProtectionLevel(context: Context): ProtectionLevel {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val raw = sp.getString(PREF_TRACKING_PROTECTION, ProtectionLevel.STRICT.name)
        return try {
            ProtectionLevel.valueOf(raw ?: ProtectionLevel.STRICT.name)
        } catch (_: Exception) {
            ProtectionLevel.STRICT
        }
    }
}
