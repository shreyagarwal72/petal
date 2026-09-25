package com.petal.browser.browser

import android.content.Context
import androidx.preference.PreferenceManager
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import org.mozilla.geckoview.ContentBlocking

/**
 * PetalPrivacyController
 * ─────────────────────────────────────────────────────────────────────────
 * Manages Enhanced Tracking Protection (ETP) and content blocking policies
 * for Petal Browser via GeckoView's ContentBlocking settings.
 */
object PetalPrivacyController {

    enum class EtpMode {
        STANDARD,
        STRICT,
        CUSTOM
    }

    /**
     * Applies the designated ETP tracking protection level.
     */
    fun applyEtpMode(context: Context, mode: EtpMode) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val runtime = PetalGeckoRuntime.getOrCreate(context)

        val level = when (mode) {
            EtpMode.STANDARD -> ContentBlocking.EtpLevel.DEFAULT
            EtpMode.STRICT -> ContentBlocking.EtpLevel.STRICT
            EtpMode.CUSTOM -> ContentBlocking.EtpLevel.DEFAULT
        }

        val cookieBehavior = when (mode) {
            EtpMode.STRICT -> ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
            else -> ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
        }

        runtime.settings.contentBlocking.apply {
            enhancedTrackingProtectionLevel = level
            cookieBehavior = cookieBehavior
            antiTracking = ContentBlocking.AntiTracking.DEFAULT or
                    ContentBlocking.AntiTracking.STP or
                    ContentBlocking.AntiTracking.AD
            safeBrowsing = ContentBlocking.SafeBrowsing.DEFAULT
        }
        sp.edit().putString("sp_etp_mode", mode.name).apply()
    }
}
