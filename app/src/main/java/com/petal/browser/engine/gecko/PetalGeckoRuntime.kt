package com.petal.browser.engine.gecko

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * PetalGeckoRuntime
 * ─────────────────────────────────────────────────────────────────────────
 * Thread-safe process-wide singleton managing Mozilla GeckoView's GeckoRuntime.
 * Configures tracking protection, ad & content blocking, cookie policies,
 * hardware acceleration, and process priority.
 */
object PetalGeckoRuntime {
    private const val TAG = "PetalGeckoRuntime"

    @Volatile
    private var runtime: GeckoRuntime? = null

    private var geckoAvailable: Boolean? = null

    @JvmStatic
    fun isGeckoAvailable(context: Context): Boolean {
        geckoAvailable?.let { return it }
        return try {
            val hasRuntimeClass = try {
                Class.forName("org.mozilla.geckoview.GeckoRuntime")
                true
            } catch (t: Throwable) {
                false
            }
            if (!hasRuntimeClass) {
                geckoAvailable = false
                return false
            }
            // Check if native libxul is present in the APK's native library path
            val appInfo = context.applicationInfo
            val nativeDir = java.io.File(appInfo.nativeLibraryDir)
            val libXul = java.io.File(nativeDir, "libxul.so")
            // In stripped/lite builds, libxul.so is excluded
            val available = if (nativeDir.exists() && nativeDir.isDirectory) {
                val soFiles = nativeDir.list()
                if (soFiles != null && soFiles.isNotEmpty()) {
                    libXul.exists()
                } else {
                    // Fallback check
                    try {
                        System.loadLibrary("mozglue")
                        true
                    } catch (t: Throwable) {
                        false
                    }
                }
            } else {
                true
            }
            geckoAvailable = available
            available
        } catch (t: Throwable) {
            Log.w(TAG, "GeckoView native libraries not available: ${t.message}")
            geckoAvailable = false
            false
        }
    }

    @JvmStatic
    @Synchronized
    fun getOrCreate(context: Context): GeckoRuntime {
        runtime?.let { return it }

        if (!isGeckoAvailable(context)) {
            throw IllegalStateException("GeckoView native libraries are not available in this build (Lite / WebView mode)")
        }

        val appContext = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)

        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val isLowRamDevice = am?.isLowRamDevice ?: false

        val isDebug = try {
            (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Throwable) { false }

        val blockThirdPartyCookies = sp.getBoolean("sp_block_third_party_cookies", false)
        val totalCookieProtection = sp.getBoolean("sp_total_cookie_protection", sp.getBoolean("sp_cookies_isolate", true))
        val adBlockEnabled = sp.getBoolean("sp_ad_block", sp.getBoolean("profileStandard_adBlock", true))
        val etpStrict = sp.getBoolean("sp_etp_strict", true)
        val defaultCookieBehavior = if (totalCookieProtection || blockThirdPartyCookies || adBlockEnabled || etpStrict) {
            ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
        } else {
            ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
        }

        var antiTrackingFlags = ContentBlocking.AntiTracking.DEFAULT
        if (adBlockEnabled || etpStrict) {
            antiTrackingFlags = antiTrackingFlags or ContentBlocking.AntiTracking.AD or ContentBlocking.AntiTracking.STP
        }
        // Match official Firefox default: STRICT when strict/ad-blocking is requested,
        // or DEFAULT (standard tracking protection) baseline instead of NONE.
        val etpLevel = if (etpStrict || adBlockEnabled) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT

        val privateDnsMode = sp.getString("sp_private_dns_mode", com.petal.browser.unit.PrivateDnsUnit.DNS_OFF)
        val dohUri = com.petal.browser.unit.PrivateDnsUnit.getDnsEndpointUrl(sp)
        val trrMode = if (!dohUri.isNullOrBlank() && privateDnsMode != com.petal.browser.unit.PrivateDnsUnit.DNS_OFF) {
            GeckoRuntimeSettings.TRR_MODE_FIRST
        } else {
            GeckoRuntimeSettings.TRR_MODE_OFF
        }

        val forceDark = sp.getBoolean("sp_force_dark_mode", false) ||
            sp.getBoolean("petal_builtin_dark_webpages", true)
        val themeConfig = sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
        val targetColorScheme = when (themeConfig.uppercase()) {
            "LIGHT" -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            "DARK" -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
            else -> if (forceDark) GeckoRuntimeSettings.COLOR_SCHEME_DARK else GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
        }

        val allowAboutConfig = sp.getBoolean("sp_enable_about_config", false) || isDebug
        val settingsBuilder = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(allowAboutConfig)
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
            .javaScriptEnabled(sp.getBoolean("sp_javascript", sp.getBoolean("profileStandard_javascript", true)))
            .fontSizeFactor(sp.getFloat("sp_font_size_scale", 1.0f))
            .consoleOutput(isDebug)
            .remoteDebuggingEnabled(isDebug)
            .webManifest(true)
            .extensionsProcessEnabled(!isLowRamDevice)
            .extensionsWebAPIEnabled(true)
            // Enable login autofill API so password manager extensions (Bitwarden, etc.) can
            // intercept login forms via the WebExtension loginAutofill API. Without this,
            // extensions receive the form events but cannot fill credentials.
            .loginAutofillEnabled(sp.getBoolean("sp_webauthn_enabled", true))

        if (!dohUri.isNullOrBlank() && trrMode != GeckoRuntimeSettings.TRR_MODE_OFF) {
            settingsBuilder.trustedRecursiveResolverUri(dohUri)
        }

        // Android 16 (SDK 36) / modern Android media stability configuration
        // Prevents native SIGSEGV in GMPThread ConditionVariableImpl::wait by disabling
        // unsupported desktop GMP plugins and directing media through native MediaCodec.
        configureMediaAndGmpStability(appContext, settingsBuilder)

        // Firefox official memory and performance optimizations
        try {
            // Low-RAM device tuning: constrain content process count and memory caches
            if (isLowRamDevice) {
                Log.i(TAG, "Configuring GeckoRuntime for low-RAM device profile (in-process extensions, constrained memory limits)")
                // Apply low-RAM content process and cache limits reflectively or via config
                try {
                    val method = settingsBuilder.javaClass.getMethod("setWebContentIsolationStrategy", Int::class.javaPrimitiveType)
                    method.invoke(settingsBuilder, 0)
                } catch (_: Throwable) {}
                try {
                    val method = settingsBuilder.javaClass.getMethod("setContentProcessCount", Int::class.javaPrimitiveType)
                    method.invoke(settingsBuilder, 1)
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not apply optional runtime settings: ${t.message}")
        }

        val newRuntime = GeckoRuntime.create(appContext, settingsBuilder.build())

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

    @JvmStatic
    fun syncPreferences(sp: SharedPreferences) {
        runtime?.let { rt ->
            try {
                // 1. JavaScript
                rt.settings.javaScriptEnabled = sp.getBoolean("sp_javascript", sp.getBoolean("profileStandard_javascript", true))

                // Text zoom / font scale factor
                try {
                    val fontScale = sp.getFloat("sp_font_size_scale", 1.0f)
                    rt.settings.fontSizeFactor = fontScale
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not sync fontSizeFactor: ${t.message}")
                }

                // 2. Enhanced Tracking Protection & AdBlock
                val adBlockEnabled = sp.getBoolean("sp_ad_block", sp.getBoolean("profileStandard_adBlock", true))
                val etpStrict = sp.getBoolean("sp_etp_strict", true)
                val etpLevel = if (etpStrict || adBlockEnabled) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT
                rt.settings.contentBlocking.setEnhancedTrackingProtectionLevel(etpLevel)
                rt.settings.contentBlocking.setStrictSocialTrackingProtection(adBlockEnabled || etpStrict)

                // 3. Block Third-Party / Tracking Cookies (Firefox ETP Cookie Isolation & Total Cookie Protection)
                val blockThirdPartyCookies = sp.getBoolean("sp_block_third_party_cookies", false)
                val totalCookieProtection = sp.getBoolean("sp_total_cookie_protection", sp.getBoolean("sp_cookies_isolate", true))
                val sessionPartitioning = sp.getBoolean("sp_session_partitioning", true)
                val cookieBehavior = if (sessionPartitioning || totalCookieProtection || blockThirdPartyCookies || adBlockEnabled || etpStrict) {
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
                } else {
                    ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
                }
                rt.settings.contentBlocking.setCookieBehavior(cookieBehavior)

                // 4. Anti-Tracking Flags (STP, Social, Cryptominers, Ads)
                var antiTrackingFlags = ContentBlocking.AntiTracking.DEFAULT
                if (adBlockEnabled || etpStrict) {
                    antiTrackingFlags = antiTrackingFlags or ContentBlocking.AntiTracking.AD or ContentBlocking.AntiTracking.STP
                }
                rt.settings.contentBlocking.setAntiTracking(antiTrackingFlags)

                // 5. Safe Browsing Malware & Phishing Shield
                rt.settings.contentBlocking.setSafeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)

                // 6. WebAuthn & Passkeys Autofill
                val webauthnEnabled = sp.getBoolean("sp_webauthn_enabled", true)
                rt.settings.loginAutofillEnabled = webauthnEnabled

                // 7. Private DNS / Trusted Recursive Resolver (TRR / DoH)
                val privateDnsMode = sp.getString("sp_private_dns_mode", com.petal.browser.unit.PrivateDnsUnit.DNS_OFF)
                val dohUri = com.petal.browser.unit.PrivateDnsUnit.getDnsEndpointUrl(sp)
                val trrMode = if (!dohUri.isNullOrBlank() && privateDnsMode != com.petal.browser.unit.PrivateDnsUnit.DNS_OFF) {
                    GeckoRuntimeSettings.TRR_MODE_FIRST
                } else {
                    GeckoRuntimeSettings.TRR_MODE_OFF
                }
                rt.settings.setTrustedRecursiveResolverMode(trrMode)
                if (!dohUri.isNullOrBlank() && trrMode != GeckoRuntimeSettings.TRR_MODE_OFF) {
                    rt.settings.setTrustedRecursiveResolverUri(dohUri)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error synchronizing GeckoRuntime preferences: ${e.message}")
            }
        }
    }

    /**
     * Android 16 (SDK 36) / modern Android media stability configuration.
     * Prevents native crashes (SEGV_ACCERR in GMPThread ConditionVariableImpl::wait) by:
     * 1. Disabling Gecko Media Plugins (GMP) which are unsupported on Android and cause thread stack overflows.
     * 2. Directing media playback exclusively to Android's native MediaCodec hardware pipeline.
     * 3. Pre-seeding user.js into all Gecko profile directories so GMP preferences are applied before GMPThread spawns.
     * Note: Avoid using GeckoView's configFilePath YAML mechanism as SnakeYAML's static initializer
     * crashes with NullPointerException on Package.getName() in Android release builds.
     */
    private fun configureMediaAndGmpStability(appContext: Context, settingsBuilder: GeckoRuntimeSettings.Builder) {
        try {
            // Set environment variable directly to avoid openh264 without relying on SnakeYAML/DebugConfig
            try {
                android.system.Os.setenv("MOZ_AVOID_OPENH264", "1", true)
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to setenv MOZ_AVOID_OPENH264: ${t.message}")
            }

            // Clean up any stale YAML config file to prevent GeckoView DebugConfig from attempting to parse it
            try {
                val staleYaml = java.io.File(appContext.filesDir, "petal-geckoview-config.yaml")
                if (staleYaml.exists()) {
                    staleYaml.delete()
                }
            } catch (_: Throwable) {}

            // Pre-seed user.js in all profile directories
            seedProfileUserJs(appContext)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to apply media & GMP stability settings: ${t.message}")
        }
    }

    private fun seedProfileUserJs(appContext: Context) {
        try {
            val userJsContent = """
                // Petal Browser Android 16+ Native Stability & GMP Crash Prevention
                user_pref("media.gmp-provider.enabled", false);
                user_pref("media.gmp.decoder.enabled", false);
                user_pref("media.gmp-manager.url", "");
                user_pref("media.gmp-manager.updateEnabled", false);
                user_pref("media.gmp-widevinecdm.visible", false);
                user_pref("media.gmp-widevinecdm.enabled", false);
                user_pref("media.gmp-widevinecdm.autoupdate", false);
                user_pref("media.gmp-gmpopenh264.visible", false);
                user_pref("media.gmp-gmpopenh264.enabled", false);
                user_pref("media.gmp-gmpopenh264.autoupdate", false);
                user_pref("media.eme.enabled", false);
            """.trimIndent()

            val possibleDirs = listOf(
                java.io.File(appContext.filesDir, "mozilla"),
                appContext.getDir("mozilla", Context.MODE_PRIVATE),
                java.io.File(appContext.filesDir, "geckoview")
            )

            for (dir in possibleDirs) {
                if (dir.exists() && dir.isDirectory) {
                    val subdirs = dir.listFiles { f -> f.isDirectory } ?: emptyArray()
                    for (sub in subdirs) {
                        try {
                            val userJs = java.io.File(sub, "user.js")
                            if (!userJs.exists() || !userJs.readText().contains("media.gmp-provider.enabled")) {
                                userJs.writeText(userJsContent)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (_: Throwable) {}
    }
}
