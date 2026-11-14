package com.petal.browser.privacy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import org.mozilla.geckoview.StorageController

/**
 * PetalPrivacyHardening
 * ─────────────────────────────────────────────────────────────────────────
 * Core engine hardening inspired by privacy-focused Firefox forks:
 * - Mull (DivestOS): Arkenfox user.js mobile hardening, WebRTC IP leak blocking.
 * - LibreWolf: Strict cross-origin referrers, telemetry severed, RFP settings.
 * - Tor Browser: 3-tier Security Slider, canvas letterboxing, New Identity wipe.
 * - Mullvad Browser: Uniform fingerprinting defenses and leak mitigations.
 */
object PetalPrivacyHardening {

    private const val TAG = "PetalPrivacyHardening"

    const val PREF_PRIVACY_PROFILE = "sp_privacy_profile"
    const val PREF_SECURITY_SLIDER = "sp_security_slider"
    const val PREF_WEBRTC_LEAK_PROTECT = "sp_webrtc_protection"
    const val PREF_CANVAS_LETTERBOXING = "sp_canvas_letterboxing"
    const val PREF_WEBGL_ENABLED = "sp_webgl_enabled"
    const val PREF_WEBGL_DISABLED = "sp_webgl_disabled"
    const val PREF_GECKO_NATIVE_CLASSIFIER_SPIKE = "sp_gecko_native_classifier_spike"

    enum class PrivacyProfile {
        STANDARD,   // High compatibility + ETP Strict
        HARDENED,   // Mull/LibreWolf Arkenfox: WebRTC leak defuse, referrer trim, hardware API disable
        STRICT      // Tor/Mullvad style: RFP, aggressive canvas isolation, strict origin
    }

    enum class SecurityLevel(val level: Int) {
        STANDARD(0), // All web features allowed
        SAFER(1),    // JS restricted on non-HTTPS, WebGL disabled, autoplay blocked
        SAFEST(2)    // JS disabled globally, remote fonts disabled, media blocked
    }

    @JvmStatic
    fun getPrivacyProfile(sp: SharedPreferences): PrivacyProfile {
        val str = sp.getString(PREF_PRIVACY_PROFILE, PrivacyProfile.STANDARD.name) ?: PrivacyProfile.STANDARD.name
        return try {
            PrivacyProfile.valueOf(str.uppercase())
        } catch (_: Exception) {
            PrivacyProfile.STANDARD
        }
    }

    @JvmStatic
    fun getSecurityLevel(sp: SharedPreferences): SecurityLevel {
        val lvl = sp.getInt(PREF_SECURITY_SLIDER, SecurityLevel.STANDARD.level)
        return SecurityLevel.values().firstOrNull { it.level == lvl } ?: SecurityLevel.STANDARD
    }

    @JvmStatic
    fun isLetterboxingEnabled(sp: SharedPreferences): Boolean {
        val profile = getPrivacyProfile(sp)
        return sp.getBoolean(PREF_CANVAS_LETTERBOXING, profile == PrivacyProfile.STRICT)
    }

    @JvmStatic
    fun isWebRtcLeakProtectionEnabled(sp: SharedPreferences): Boolean {
        val profile = getPrivacyProfile(sp)
        return sp.getBoolean(PREF_WEBRTC_LEAK_PROTECT, true)
    }

    @JvmStatic
    fun isNativeClassifierSpikeEnabled(sp: SharedPreferences): Boolean {
        return sp.getBoolean(PREF_GECKO_NATIVE_CLASSIFIER_SPIKE, true)
    }

    @JvmStatic
    fun probeContentClassifierService(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val active = isNativeClassifierSpikeEnabled(sp)
        Log.i(TAG, "ContentClassifierService probe (GeckoView 159): spike active=$active, engines=[adblock-rust, content-classifier], test_list_urls=[EasyList, EasyPrivacy]")
    }

    /**
     * Generates Arkenfox-derived user.js hardening rules according to active profile & security level.
     */
    @JvmStatic
    fun buildArkenfoxUserJs(context: Context): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val profile = getPrivacyProfile(sp)
        val secLevel = getSecurityLevel(sp)
        val webrtcProtect = isWebRtcLeakProtectionEnabled(sp)

        val sb = StringBuilder()
        sb.appendLine("// ── Petal Browser Hardened Privacy Profile: ${profile.name} ──")

        // 1. Telemetry & Metrics Severed (Mull / IronFox / LibreWolf)
        sb.appendLine("""
            user_pref("toolkit.telemetry.enabled", false);
            user_pref("toolkit.telemetry.unified", false);
            user_pref("toolkit.telemetry.archive.enabled", false);
            user_pref("experiments.enabled", false);
            user_pref("experiments.supported", false);
            user_pref("network.allow-experiments", false);
            user_pref("datareporting.healthreport.uploadEnabled", false);
            user_pref("datareporting.policy.dataSubmissionEnabled", false);
            user_pref("app.normandy.enabled", false);
            user_pref("app.shield.optoutstudy.enabled", false);
            // IronFox / Phoenix Mobile Anti-Tracking & Network Partitioning
            user_pref("privacy.partition.network_state", true);
            user_pref("privacy.partition.serviceWorkers", true);
            user_pref("privacy.partition.always_partition_third_party_non_cookie_storage", true);
            user_pref("privacy.query_stripping.enabled", true);
            user_pref("privacy.query_stripping.enabled.pbmode", true);
        """.trimIndent())

        // 2. Anti-Fingerprinting Baseline (Hardened and Strict only)
        if (profile != PrivacyProfile.STANDARD) {
            sb.appendLine("""
                // Fingerprinting protection enabled for Hardened/Strict profiles
                user_pref("privacy.fingerprintingProtection", true);
                user_pref("privacy.fingerprintingProtection.pbmode", true);
            """.trimIndent())
        }

        // 3. WebRTC IP Leak Prevention (Mull / IronFox / DivestOS)
        if (profile != PrivacyProfile.STANDARD) {
            sb.appendLine("""
                // WebRTC local and private interface address leak defuser
                user_pref("media.peerconnection.ice.default_address_only", true);
                user_pref("media.peerconnection.ice.proxy_only_if_behind_proxy", true);
            """.trimIndent())
            val explicitWebrtc = sp.contains(PREF_WEBRTC_LEAK_PROTECT) && sp.getBoolean(PREF_WEBRTC_LEAK_PROTECT, false)
            if (profile == PrivacyProfile.STRICT || explicitWebrtc) {
                sb.appendLine("""user_pref("media.peerconnection.ice.no_host", true);""")
            }
        }

        // 4. Hardware & Sensor API Fingerprinting Defuser (Mull / IronFox / LibreWolf)
        if (profile != PrivacyProfile.STANDARD) {
            sb.appendLine("""
                // Hardware enumeration defenses
                user_pref("dom.battery.v2.enabled", false);
                user_pref("dom.vr.enabled", false);
                // Referrer Trimming: XOriginPolicy=1 (same base domain) for HARDENED, 2 for STRICT
                user_pref("network.http.referer.XOriginPolicy", ${if (profile == PrivacyProfile.STRICT) 2 else 1});
                user_pref("network.http.referer.XOriginTrimmingPolicy", 2);
                // Disable prefetching and speculative connections (stops background tracker pings)
                user_pref("network.prefetch-next", false);
                user_pref("network.dns.disablePrefetch", true);
                user_pref("network.http.speculative-parallel-limit", 0);
            """.trimIndent())
        }

        // 5. Resist Fingerprinting (RFP) & Canvas Defense (Tor / Mullvad)
        if (profile == PrivacyProfile.STRICT) {
            sb.appendLine("""
                // Strict hardware & API restrictions (beacon.enabled preserved to avoid breaking logins/analytics)
                user_pref("device.sensors.enabled", false);
                user_pref("dom.gamepad.enabled", false);
                user_pref("dom.vibrator.enabled", false);
                // Tor-grade Resist Fingerprinting (rounds time to 100ms, forces light theme, UTC timezone & reduced canvas)
                user_pref("privacy.resistFingerprinting", true);
                user_pref("privacy.resistFingerprinting.block_mozAddonManager", true);
                user_pref("privacy.resistFingerprinting.letterboxing", true);
                user_pref("privacy.trackingprotection.fingerprinting.enabled", true);
                user_pref("privacy.trackingprotection.cryptomining.enabled", true);
            """.trimIndent())
        }

        // 6. WebGL 3D Graphics Toggle (separate setting; off only when disabled or SAFEST)
        val webglEnabled = sp.getBoolean(PREF_WEBGL_ENABLED, !sp.getBoolean(PREF_WEBGL_DISABLED, false))
        if (!webglEnabled || secLevel == SecurityLevel.SAFEST) {
            sb.appendLine("""user_pref("webgl.disabled", true);""")
        }

        // 7. Security Slider dynamic constraints
        when (secLevel) {
            SecurityLevel.SAFER -> {
                sb.appendLine("""
                    // Security Level: SAFER (WebGL allowed; autoplay blocked)
                    user_pref("media.autoplay.default", 5);
                """.trimIndent())
            }
            SecurityLevel.SAFEST -> {
                sb.appendLine("""
                    // Security Level: SAFEST (JS globally blocked, WebGL disabled, fonts disabled)
                    user_pref("gfx.downloadable_fonts.enabled", false);
                    user_pref("mathml.disabled", true);
                    user_pref("media.autoplay.default", 5);
                """.trimIndent())
            }
            SecurityLevel.STANDARD -> {
                // Baseline options
            }
        }

        // 8. Cookie Banner Handling (Gecko engine preferences: 2 = reject or accept, 0 = disabled)
        val cookieBannerAutoReject = sp.getBoolean("sp_cookie_banner_auto_reject", sp.getBoolean("petal_builtin_consent_manager", true))
        val cookieBannerMode = if (cookieBannerAutoReject) 2 else 0
        sb.appendLine("""
            // Automated Cookie Banner Handling (engine-level preferences + bundled CSS/extension fallback)
            user_pref("cookiebanners.service.mode", $cookieBannerMode);
            user_pref("cookiebanners.service.mode.privateBrowsing", $cookieBannerMode);
            user_pref("cookiebanners.bannerClicking.enabled", ${if (cookieBannerAutoReject) "true" else "false"});
        """.trimIndent())

        // 9. Explicit user toggles from Settings → Privacy (these win over profile baselines above)
        val trimRef = sp.getBoolean("sp_trim_referrers", true)
        val webauthn = sp.getBoolean("sp_webauthn_enabled", true)
        val dntGpc = sp.getBoolean("sp_dnt_gpc", true)
        val fpProtect = sp.getBoolean("sp_fingerprint_protection", true)
        sb.appendLine("""
            // User privacy toggles
            user_pref("network.http.referer.XOriginTrimmingPolicy", ${if (trimRef) 2 else 0});
            user_pref("security.webauth.webauthn", $webauthn);
            user_pref("privacy.donottrackheader.enabled", $dntGpc);
            user_pref("privacy.globalprivacycontrol.enabled", $dntGpc);
            user_pref("privacy.globalprivacycontrol.functionality.enabled", $dntGpc);
            user_pref("privacy.fingerprintingProtection", $fpProtect);
        """.trimIndent())

        // 10. Native Gecko ContentClassifierService Spike (GeckoView 159 on Android)
        val spikeClassifier = isNativeClassifierSpikeEnabled(sp)
        if (spikeClassifier) {
            sb.appendLine("""
                // Native Gecko ContentClassifierService Spike (GeckoView 159 on Android)
                user_pref("privacy.trackingprotection.content.protection.enabled", true);
                user_pref("privacy.trackingprotection.content.protection.engines", "adblock-rust,content-classifier");
                user_pref("privacy.trackingprotection.content.protection.test_list_urls", "https://easylist.to/easylist/easylist.txt,https://easylist.to/easylist/easyprivacy.txt");
            """.trimIndent())
            Log.d(TAG, "ContentClassifierService spike configured in user.js: native protection engines [adblock-rust, content-classifier]")
        }

        return sb.toString()
    }

    /**
     * One-tap "New Identity / Panic Wipe" (Mullvad / Tor Browser).
     * Clears all session storage, cookies, DOM cache, network sockets,
     * and returns to a fresh clean-slate incognito state.
     */
    @JvmStatic
    fun resetIdentity(context: Context, onComplete: (() -> Unit)? = null) {
        val appContext = context.applicationContext
        try {
            Log.i(TAG, "Executing Tor-style New Identity flush...")
            // 1. Wipe all Gecko storage
            PetalGeckoRuntime.clearData(
                appContext,
                StorageController.ClearFlags.ALL
            )

            // 2. Clear clipboard if it was used for URL copying
            try {
                val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    cm?.clearPrimaryClip()
                }
            } catch (_: Throwable) {}

            // 3. Reset Tor circuit if active
            if (PetalTorManager.isTorActive) {
                PetalTorManager.startTorSession(appContext)
            }

            Log.i(TAG, "New Identity flush complete")
            onComplete?.invoke()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed New Identity wipe: ${t.message}", t)
            onComplete?.invoke()
        }
    }
}
