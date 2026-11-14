package com.petal.browser.engine.gecko

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.lang.ref.WeakReference

/**
 * PetalGeckoRuntime
 * ─────────────────────────────────────────────────────────────────────────
 * Thread-safe process-wide singleton managing Mozilla GeckoView's GeckoRuntime.
 * Configures tracking protection, ad & content blocking, cookie policies,
 * hardware acceleration, and process priority.
 */
object PetalGeckoRuntime {
    private const val TAG = "PetalGeckoRuntime"
    private const val REQUEST_CODE_GECKO_ACTIVITY = 8600

    @Volatile
    private var runtime: GeckoRuntime? = null

    private var pendingActivityResult: GeckoResult<Intent>? = null
    private var foregroundActivity: WeakReference<Activity>? = null
    private var appContextRef: WeakReference<Context>? = null

    @JvmStatic
    fun warmUp() {
        runtime?.warmUp()
    }

    @Deprecated(
        message = "Dead fallback removed in Stage 2; GeckoView is the sole browser engine in Petal",
        replaceWith = ReplaceWith("true")
    )
    @JvmStatic
    fun isGeckoAvailable(context: Context? = null): Boolean = true

    @JvmStatic
    fun setForegroundActivity(activity: Activity?) {
        foregroundActivity = if (activity != null) WeakReference(activity) else null
    }

    @JvmStatic
    fun getForegroundActivity(): Activity? = foregroundActivity?.get()

    @JvmStatic
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == REQUEST_CODE_GECKO_ACTIVITY) {
            val pending = pendingActivityResult
            pendingActivityResult = null
            if (pending != null) {
                if (resultCode == Activity.RESULT_OK) {
                    Log.i(TAG, "ActivityDelegate onActivityResult completed successfully with RESULT_OK")
                    pending.complete(data ?: Intent())
                } else {
                    Log.w(TAG, "ActivityDelegate onActivityResult cancelled or failed with resultCode $resultCode")
                    pending.completeExceptionally(
                        IllegalStateException("Activity request cancelled or failed (resultCode=$resultCode)")
                    )
                }
            }
            return true
        }
        return false
    }

    @JvmStatic
    @Synchronized
    fun getOrCreate(context: Context): GeckoRuntime {
        runtime?.let { return it }

        val appContext = context.applicationContext
        appContextRef = WeakReference(appContext)
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)

        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val isLowRamDevice = am?.isLowRamDevice ?: false

        val isDebug = try {
            (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Throwable) { false }

        val privacyProfile = com.petal.browser.privacy.PetalPrivacyHardening.getPrivacyProfile(sp)
        val securityLevel = com.petal.browser.privacy.PetalPrivacyHardening.getSecurityLevel(sp)
        val isStrictOrHardened = privacyProfile != com.petal.browser.privacy.PetalPrivacyHardening.PrivacyProfile.STANDARD

        val blockThirdPartyCookies = sp.getBoolean("sp_block_third_party_cookies", false)
        val totalCookieProtection = sp.getBoolean("sp_total_cookie_protection", sp.getBoolean("sp_cookies_isolate", true))
        val etpStrict = sp.getBoolean("sp_etp_strict", false) || (privacyProfile == com.petal.browser.privacy.PetalPrivacyHardening.PrivacyProfile.STRICT)
        // Same rule as syncPreferences() so startup and later syncs never disagree.
        val sessionPartitioningAtStart = sp.getBoolean("sp_session_partitioning", true)
        val defaultCookieBehavior = if (sessionPartitioningAtStart || totalCookieProtection || blockThirdPartyCookies || isStrictOrHardened) {
            ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
        } else {
            ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
        }

        var antiTrackingFlags = ContentBlocking.AntiTracking.DEFAULT
        if (etpStrict || privacyProfile == com.petal.browser.privacy.PetalPrivacyHardening.PrivacyProfile.STRICT) {
            antiTrackingFlags = antiTrackingFlags or ContentBlocking.AntiTracking.AD or
                    ContentBlocking.AntiTracking.ANALYTIC or ContentBlocking.AntiTracking.SOCIAL or
                    ContentBlocking.AntiTracking.CONTENT or ContentBlocking.AntiTracking.STP or
                    ContentBlocking.AntiTracking.CRYPTOMINING or ContentBlocking.AntiTracking.FINGERPRINTING
        }
        // Match official Firefox standard: DEFAULT for standard browsing, STRICT when strictly requested.
        val etpLevel = if (etpStrict) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT

        val privateDnsMode = sp.getString("sp_private_dns_mode", com.petal.browser.unit.PetalPrivateDnsHelper.DNS_OFF)
        val dohUri = com.petal.browser.unit.PetalPrivateDnsHelper.getDnsEndpointUrl(sp)
        val trrMode = if (!dohUri.isNullOrBlank() && privateDnsMode != com.petal.browser.unit.PetalPrivateDnsHelper.DNS_OFF) {
            GeckoRuntimeSettings.TRR_MODE_FIRST
        } else {
            GeckoRuntimeSettings.TRR_MODE_OFF
        }

        val themeConfig = sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
        val forceDark = sp.getBoolean("sp_force_dark_mode", false)
        val targetColorScheme = if (forceDark) {
            GeckoRuntimeSettings.COLOR_SCHEME_DARK
        } else {
            when (themeConfig.uppercase()) {
                "LIGHT" -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
                "DARK" -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
                else -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
            }
        }

        val allowAboutConfig = sp.getBoolean("sp_enable_about_config", isDebug)
        val userChangedFontSize = sp.contains("sp_font_size_scale")
        val fontScale = sp.getFloat("sp_font_size_scale", 1.0f)

        val httpsOnly = sp.getBoolean("sp_https_only", sp.getBoolean("profileStandard_httpsOnly", false))
        val insecureMode = if (httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL

        val settingsBuilder = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(allowAboutConfig)
            .allowInsecureConnections(insecureMode)
            .trustedRecursiveResolverMode(trrMode)
            .preferredColorScheme(targetColorScheme)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .antiTracking(antiTrackingFlags)
                    .cookieBehavior(defaultCookieBehavior)
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .enhancedTrackingProtectionLevel(etpLevel)
                    .build()
            )
            .javaScriptEnabled(
                if (securityLevel == com.petal.browser.privacy.PetalPrivacyHardening.SecurityLevel.SAFEST) false
                else sp.getBoolean("sp_javascript", sp.getBoolean("profileStandard_javascript", true))
            )
            .consoleOutput(isDebug || com.petal.browser.logger.PetalLiveLogEngine.isEnabled)
            .remoteDebuggingEnabled(isDebug || com.petal.browser.logger.PetalLiveLogEngine.isEnabled)
            .webManifest(true)
            .extensionsProcessEnabled(!isLowRamDevice)
            .extensionsWebAPIEnabled(true)
            .isolatedProcessEnabled(false)
            .lowMemoryDetection(true)
            .crashHandler(PetalCrashHandlerService::class.java)
            // Enable login autofill API so password manager extensions (Bitwarden, etc.) can
            // intercept login forms via the WebExtension loginAutofill API. Without this,
            // extensions receive the form events but cannot fill credentials.
            .loginAutofillEnabled(sp.getBoolean("sp_login_autofill_enabled", true))
            .forceUserScalableEnabled(sp.getBoolean("sp_force_enable_zoom", true))

        if (userChangedFontSize && fontScale != 1.0f) {
            settingsBuilder.automaticFontSizeAdjustment(false)
            settingsBuilder.fontSizeFactor(fontScale)
        } else {
            settingsBuilder.automaticFontSizeAdjustment(true)
        }

        if (!dohUri.isNullOrBlank() && trrMode != GeckoRuntimeSettings.TRR_MODE_OFF) {
            settingsBuilder.trustedRecursiveResolverUri(dohUri)
        }

        // Android 16 (SDK 36) / modern Android media stability configuration
        // Prevents native SIGSEGV in GMPThread ConditionVariableImpl::wait by disabling
        // unsupported desktop GMP plugins and directing media through native MediaCodec.
        configureMediaAndGmpStability(appContext, settingsBuilder)

        // isolatedProcessEnabled(false) and lowMemoryDetection(true) are already set on the builder above
        // (isolated processes crash on Android 16 / some OEM ROMs due to ART image restrictions).

        val newRuntime = GeckoRuntime.create(appContext, settingsBuilder.build())

        // Passkey / WebAuthn support:
        // GeckoRuntime uses ActivityDelegate to launch the Google Play Services FIDO / security key activity.
        try {
            newRuntime.setActivityDelegate(object : GeckoRuntime.ActivityDelegate {
                override fun onStartActivityForResult(intent: PendingIntent): GeckoResult<Intent> {
                    val result = GeckoResult<Intent>()
                    val activity = foregroundActivity?.get()
                    if (activity == null || activity.isFinishing || activity.isDestroyed) {
                        Log.w(TAG, "Cannot launch WebAuthn activity: no active foreground activity")
                        result.completeExceptionally(
                            IllegalStateException("No foreground activity available to launch WebAuthn intent")
                        )
                        return result
                    }
                    pendingActivityResult?.completeExceptionally(
                        IllegalStateException("Superseded by new activity request")
                    )
                    pendingActivityResult = result
                    activity.runOnUiThread {
                        try {
                            activity.startIntentSenderForResult(
                                intent.intentSender,
                                REQUEST_CODE_GECKO_ACTIVITY,
                                null,
                                0,
                                0,
                                0
                            )
                            Log.i(TAG, "Launched WebAuthn ActivityDelegate intent via startIntentSenderForResult")
                        } catch (t: Throwable) {
                            Log.e(TAG, "Failed to launch WebAuthn IntentSender", t)
                            pendingActivityResult = null
                            result.completeExceptionally(t)
                        }
                    }
                    return result
                }
            })
            Log.i(TAG, "Registered GeckoRuntime.ActivityDelegate for FIDO / WebAuthn passkeys")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not register GeckoRuntime.ActivityDelegate: ${t.message}")
        }

        // Connect Petal's local password vault to GeckoView's autocomplete system.
        // This allows the browser engine to:
        //   • fetch saved logins when a login form is detected (onLoginFetch)
        //   • save new credentials after form submission (onLoginSave)
        //   • track last-used timestamps (onLoginUsed)
        try {
            newRuntime.autocompleteStorageDelegate =
                com.petal.browser.passwords.PetalAutofillGeckoDelegate(appContext)
            Log.i(TAG, "Registered PetalAutofillGeckoDelegate as autocomplete storage delegate")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not register autocomplete delegate: ${t.message}")
        }

        try {
            newRuntime.webNotificationDelegate =
                PetalWebNotificationDelegate(appContext)
            Log.i(TAG, "Registered PetalWebNotificationDelegate as web notification delegate")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not register web notification delegate: ${t.message}")
        }

        try {
            newRuntime.serviceWorkerDelegate = object : GeckoRuntime.ServiceWorkerDelegate {
                override fun onOpenWindow(url: String): GeckoResult<org.mozilla.geckoview.GeckoSession>? {
                    val activity = foregroundActivity?.get() ?: return null
                    val result = GeckoResult<org.mozilla.geckoview.GeckoSession>()
                    activity.runOnUiThread {
                        try {
                            val pair = com.petal.browser.engine.gecko.PetalEngineStore.createTabSession(
                                context = activity,
                                tabId = java.util.UUID.randomUUID().toString(),
                                url = url,
                                isIncognito = false,
                                select = true
                            )
                            val gs = (pair.second as? mozilla.components.browser.engine.gecko.GeckoEngineSession)?.geckoSession
                            result.complete(gs)
                        } catch (t: Throwable) {
                            result.completeExceptionally(t)
                        }
                    }
                    return result
                }
            }
            Log.i(TAG, "Registered GeckoRuntime.ServiceWorkerDelegate for openWindow")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not register serviceWorkerDelegate: ${t.message}")
        }

        runtime = newRuntime
        Log.i(TAG, "Initialized GeckoRuntime with standard tracking protection and high-performance pipeline")
        return newRuntime
    }

    /**
     * Official Firefox GeckoView memory management bridge.
     * Purges only volatile image decodes at TRIM_MEMORY_RUNNING_LOW and avoids clearing
     * all caches (preserving network cache, bytecode, and DOM cache for Gecko to manage).
     */
    @JvmStatic
    fun onTrimMemory(context: Context, level: Int) {
        val rt = runtime ?: return
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            try {
                // Purge volatile image caches in StorageController; let Gecko manage the rest
                rt.storageController.clearData(
                    org.mozilla.geckoview.StorageController.ClearFlags.IMAGE_CACHE
                )
                Log.d(TAG, "Purged Gecko image cache on memory trim level $level")
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to purge image cache on low memory: ${t.message}")
            }
        }
    }

    /**
     * Handles system onLowMemory callback by purging only volatile image cache.
     */
    @JvmStatic
    fun onLowMemory(context: Context) {
        onTrimMemory(context, android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
    }

    @JvmStatic
    fun clearData(context: Context, flags: Long = org.mozilla.geckoview.StorageController.ClearFlags.ALL) {
        try {
            getOrCreate(context).storageController.clearData(flags)
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing Gecko data: ${e.message}")
        }
    }

    /** Fingerprint of the last applied runtime settings; avoids re-writing ~15 Gecko prefs on every navigation. */
    @Volatile
    private var lastSyncFingerprint: String? = null

    private fun syncFingerprint(sp: SharedPreferences): String {
        val keys = listOf(
            "sp_javascript", "profileStandard_javascript", "sp_font_size_scale", "sp_ad_block",
            "profileStandard_adBlock", "sp_etp_strict", "sp_block_third_party_cookies",
            "sp_total_cookie_protection", "sp_cookies_isolate", "sp_session_partitioning",
            "sp_login_autofill_enabled", "sp_private_dns_mode", "sp_custom_doh_url",
            "sp_private_dns_custom_url", "sp_nextdns_profile_id", "sp_privacy_profile", "sp_security_slider",
            "sp_https_only", "profileStandard_httpsOnly", "sp_theme_config", "sp_force_dark_mode",
            "sp_trim_referrers", "sp_webauthn_enabled", "sp_fingerprint_protection", "sp_dnt_gpc",
            "sp_cookie_banner_auto_reject"
        )
        val sb = StringBuilder()
        for (k in keys) sb.append(k).append('=').append(sp.all[k]).append(';')
        sb.append("profile=").append(com.petal.browser.privacy.PetalPrivacyHardening.getPrivacyProfile(sp))
        sb.append(";sec=").append(com.petal.browser.privacy.PetalPrivacyHardening.getSecurityLevel(sp))
        sb.append(";doh=").append(com.petal.browser.unit.PetalPrivateDnsHelper.getDnsEndpointUrl(sp))
        return sb.toString()
    }

    @JvmStatic
    fun syncPreferences(sp: SharedPreferences) {
        runtime?.let { rt ->
            try {
                val fingerprint = syncFingerprint(sp)
                if (fingerprint == lastSyncFingerprint) return
                lastSyncFingerprint = fingerprint
                val privacyProfile = com.petal.browser.privacy.PetalPrivacyHardening.getPrivacyProfile(sp)
                val securityLevel = com.petal.browser.privacy.PetalPrivacyHardening.getSecurityLevel(sp)
                val isStrictOrHardened = privacyProfile != com.petal.browser.privacy.PetalPrivacyHardening.PrivacyProfile.STANDARD

                // 1. JavaScript
                val jsAllowed = if (securityLevel == com.petal.browser.privacy.PetalPrivacyHardening.SecurityLevel.SAFEST) {
                    false
                } else {
                    sp.getBoolean("sp_javascript", sp.getBoolean("profileStandard_javascript", true))
                }
                rt.settings.javaScriptEnabled = jsAllowed

                // Text zoom / font scale factor
                try {
                    val userChanged = sp.contains("sp_font_size_scale")
                    val fontScale = sp.getFloat("sp_font_size_scale", 1.0f)
                    if (userChanged && fontScale != 1.0f) {
                        rt.settings.automaticFontSizeAdjustment = false
                        rt.settings.fontSizeFactor = fontScale
                    } else {
                        rt.settings.automaticFontSizeAdjustment = true
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not sync fontSizeFactor: ${t.message}")
                }

                try {
                    rt.settings.forceUserScalableEnabled = sp.getBoolean("sp_force_enable_zoom", true)
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not sync forceUserScalableEnabled: ${t.message}")
                }

                // 2. Enhanced Tracking Protection & AdBlock
                val etpStrict = sp.getBoolean("sp_etp_strict", false) || (privacyProfile == com.petal.browser.privacy.PetalPrivacyHardening.PrivacyProfile.STRICT)
                val etpLevel = if (etpStrict) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT
                rt.settings.contentBlocking.setEnhancedTrackingProtectionLevel(etpLevel)
                rt.settings.contentBlocking.setStrictSocialTrackingProtection(etpStrict)

                // 3. Block Third-Party / Tracking Cookies (Firefox ETP Cookie Isolation & Total Cookie Protection)
                val blockThirdPartyCookies = sp.getBoolean("sp_block_third_party_cookies", false)
                val totalCookieProtection = sp.getBoolean("sp_total_cookie_protection", sp.getBoolean("sp_cookies_isolate", true))
                val sessionPartitioning = sp.getBoolean("sp_session_partitioning", true)
                val cookieBehavior = if (sessionPartitioning || totalCookieProtection || blockThirdPartyCookies || isStrictOrHardened) {
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
                } else {
                    ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
                }
                rt.settings.contentBlocking.setCookieBehavior(cookieBehavior)

                // 4. Anti-Tracking Flags (STP, Social, Cryptominers, Ads)
                var antiTrackingFlags = ContentBlocking.AntiTracking.DEFAULT
                if (etpStrict || privacyProfile == com.petal.browser.privacy.PetalPrivacyHardening.PrivacyProfile.STRICT) {
                    antiTrackingFlags = antiTrackingFlags or ContentBlocking.AntiTracking.AD or
                            ContentBlocking.AntiTracking.ANALYTIC or ContentBlocking.AntiTracking.SOCIAL or
                            ContentBlocking.AntiTracking.CONTENT or ContentBlocking.AntiTracking.STP or
                            ContentBlocking.AntiTracking.CRYPTOMINING or ContentBlocking.AntiTracking.FINGERPRINTING
                }
                if (sp.getBoolean("sp_fingerprint_protection", false)) {
                    antiTrackingFlags = antiTrackingFlags or ContentBlocking.AntiTracking.FINGERPRINTING
                }
                rt.settings.contentBlocking.setAntiTracking(antiTrackingFlags)

                // 4b. Privacy toggles applied live through Gecko prefs (no restart needed)
                applyLivePrivacyPrefs(sp)

                // 5. Safe Browsing Malware & Phishing Shield
                rt.settings.contentBlocking.setSafeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)

                // 6. Login Autofill & WebAuthn / Passkeys
                val loginAutofill = sp.getBoolean("sp_login_autofill_enabled", true)
                rt.settings.loginAutofillEnabled = loginAutofill

                // 7. Private DNS / Trusted Recursive Resolver (TRR / DoH)
                val privateDnsMode = sp.getString("sp_private_dns_mode", com.petal.browser.unit.PetalPrivateDnsHelper.DNS_OFF)
                val dohUri = com.petal.browser.unit.PetalPrivateDnsHelper.getDnsEndpointUrl(sp)
                val trrMode = if (!dohUri.isNullOrBlank() && privateDnsMode != com.petal.browser.unit.PetalPrivateDnsHelper.DNS_OFF) {
                    GeckoRuntimeSettings.TRR_MODE_FIRST
                } else {
                    GeckoRuntimeSettings.TRR_MODE_OFF
                }
                rt.settings.setTrustedRecursiveResolverMode(trrMode)
                if (!dohUri.isNullOrBlank() && trrMode != GeckoRuntimeSettings.TRR_MODE_OFF) {
                    rt.settings.setTrustedRecursiveResolverUri(dohUri)
                }

                // 8. HTTPS-Only / Insecure Connection Mode
                val httpsOnly = sp.getBoolean("sp_https_only", sp.getBoolean("profileStandard_httpsOnly", false))
                val insecureMode = if (httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL
                try {
                    rt.settings.setAllowInsecureConnections(insecureMode)
                } catch (_: Throwable) {}

                // 9. Theme / Preferred Color Scheme
                syncTheme(sp)
            } catch (e: Exception) {
                Log.w(TAG, "Error synchronizing GeckoRuntime preferences: ${e.message}")
            }
        }
    }

    /** Sets a Gecko pref at runtime via GeckoPreferenceController (reflection keeps older engines safe). */
    private fun setGeckoPref(name: String, value: Any) {
        try {
            val cls = Class.forName("org.mozilla.geckoview.GeckoPreferenceController")
            val valueType = when (value) {
                is Boolean -> java.lang.Boolean::class.java
                is Int -> java.lang.Integer::class.java
                else -> String::class.java
            }
            val m = cls.getMethod("setGeckoPref", String::class.java, valueType, Int::class.javaPrimitiveType)
            m.invoke(null, name, value, 1 /* PREF_BRANCH_USER */)
        } catch (t: Throwable) {
            Log.d(TAG, "setGeckoPref($name) unavailable: ${t.message}")
        }
    }

    /**
     * Applies the privacy toggles that have no dedicated GeckoRuntimeSettings API:
     * referrer trimming, WebAuthn, DNT/GPC, fingerprint protection and cookie-banner handling.
     */
    private fun applyLivePrivacyPrefs(sp: SharedPreferences) {
        val trim = sp.getBoolean("sp_trim_referrers", true)
        setGeckoPref("network.http.referer.XOriginTrimmingPolicy", if (trim) 2 else 0)

        setGeckoPref("security.webauth.webauthn", sp.getBoolean("sp_webauthn_enabled", true))

        val dnt = sp.getBoolean("sp_dnt_gpc", true)
        setGeckoPref("privacy.donottrackheader.enabled", dnt)
        setGeckoPref("privacy.globalprivacycontrol.enabled", dnt)
        setGeckoPref("privacy.globalprivacycontrol.functionality.enabled", dnt)
        try {
            rtSettingsGpc(dnt)
        } catch (_: Throwable) {}

        val fp = sp.getBoolean("sp_fingerprint_protection", false)
        setGeckoPref("privacy.fingerprintingProtection", fp)

        val banner = sp.getBoolean("sp_cookie_banner_auto_reject", sp.getBoolean("petal_builtin_consent_manager", true))
        setGeckoPref("cookiebanners.service.mode", if (banner) 2 else 0)
        setGeckoPref("cookiebanners.service.mode.privateBrowsing", if (banner) 2 else 0)
        setGeckoPref("cookiebanners.bannerClicking.enabled", banner)

        // Keep the on-disk user.js in step so the same values survive the next cold start.
        try {
            com.petal.browser.flags.PetalFlagsStore.syncToUserJs(appContextRef?.get() ?: return)
        } catch (_: Throwable) {}
    }

    private fun rtSettingsGpc(enabled: Boolean) {
        val rt = runtime ?: return
        val m = rt.settings.javaClass.getMethod("setGlobalPrivacyControl", Boolean::class.javaPrimitiveType)
        m.invoke(rt.settings, enabled)
    }

    /**
     * Synchronizes the runtime-wide preferred color scheme with user theme settings.
     * New rule: LIGHT gives light, DARK gives dark, and FOLLOW_SYSTEM gives COLOR_SCHEME_SYSTEM.
     * Force dark (sp_force_dark_mode) applies only when explicitly enabled by user.
     */
    @JvmStatic
    fun syncTheme(sp: SharedPreferences) {
        runtime?.let { rt ->
            val themeConfig = sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
            val forceDark = sp.getBoolean("sp_force_dark_mode", false)
            val targetColorScheme = if (forceDark) {
                GeckoRuntimeSettings.COLOR_SCHEME_DARK
            } else {
                when (themeConfig.uppercase()) {
                    "LIGHT" -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
                    "DARK" -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
                    else -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
                }
            }
            try {
                rt.settings.preferredColorScheme = targetColorScheme
                Log.d(TAG, "Synchronized runtime preferredColorScheme to $targetColorScheme (themeConfig=$themeConfig, forceDark=$forceDark)")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to update preferredColorScheme: ${t.message}")
            }
        }
    }

    /**
     * Android 16 (SDK 36) / modern Android media stability configuration.
     * Prevents native crashes (SEGV_ACCERR in GMPThread ConditionVariableImpl::wait) by:
     * 1. Setting MOZ_AVOID_OPENH264=1 to avoid openh264 GMP decoders.
     * 2. Setting configFilePath("") to disable unsolicited config file loading.
     * 3. Cleaning up any stale temporary YAML files.
     */
    private fun configureMediaAndGmpStability(appContext: Context, settingsBuilder: GeckoRuntimeSettings.Builder) {
        try {
            // Set environment variable directly to avoid openh264 without relying on SnakeYAML/DebugConfig
            try {
                android.system.Os.setenv("MOZ_AVOID_OPENH264", "1", true)
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to setenv MOZ_AVOID_OPENH264: ${t.message}")
            }

            // Stage 3: Disable unsolicited config file loading by explicitly setting an empty path
            settingsBuilder.configFilePath("")

            // Clean up any stale YAML config file to prevent GeckoView DebugConfig from attempting to parse it
            try {
                val staleYaml = java.io.File(appContext.filesDir, "petal-geckoview-config.yaml")
                if (staleYaml.exists()) {
                    staleYaml.delete()
                }
            } catch (_: Throwable) {}
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to apply media & GMP stability settings: ${t.message}")
        }
    }
}
