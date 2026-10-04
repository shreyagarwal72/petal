package com.petal.browser.extensions

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import com.petal.browser.media.sniffer.PetalMediaSniffer
import java.util.concurrent.ConcurrentHashMap

/**
 * PetalBuiltInExtensionManager
 *
 * Manages Petal's bundled GeckoView WebExtensions that ship inside the APK
 * (as opposed to user-installed AMO extensions managed by PetalExtensionManager).
 *
 * Each built-in extension lives at assets/web_extensions/<assetPath>/ and is
 * loaded via resource://android/assets/... — GeckoView's built-in mechanism.
 *
 * Safe to call on every startup — ensureBuiltIn() is idempotent.
 */
object PetalBuiltInExtensionManager {

    private const val TAG = "PetalBuiltInExt"
    private const val MEDIA_GRABBER_ID = "petal-media-grabber@petalbrowser.app"
    private const val NATIVE_APP = "petalApp"

    private val installedExtensions = ConcurrentHashMap<String, WebExtension>()

    private val mediaMessageDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            if (nativeApp != NATIVE_APP) return null
            val json = runCatching { if (message is JSONObject) message else JSONObject(message.toString()) }.getOrNull()
                ?: return null
            val msgType = json.optString("type")

            if (msgType == "GET_NATIVE_PLAYER_STATE") {
                val resp = JSONObject().apply {
                    put("enabled", true)
                    put("youtubeEnabled", true)
                }
                return GeckoResult.fromValue(resp)
            }

            val pageUrl = json.optString("pageUrl").takeIf { it.startsWith("http") }
            val cookies = json.optString("cookies").takeIf { it.isNotBlank() }
            if (PetalMediaSniffer.interceptor.isSearchEngineOrInternalUrl(pageUrl)) {
                return null
            }
            if (pageUrl != null && cookies != null) {
                PetalMediaSniffer.recordCookiesForUrl(pageUrl, cookies)
            }

            when (msgType) {
                "MEDIA_GRABBED" -> {
                    val url = json.optString("url").takeIf { it.startsWith("http") } ?: return null
                    if (PetalMediaSniffer.interceptor.isSearchEngineOrInternalUrl(url)) return null
                    val mime = json.optString("mimeType", "video/mp4")
                    val size = json.optLong("sizeBytes", -1L).takeIf { it > 0 }
                    PetalMediaSniffer.onAggressiveMedia(url, mime, cookies, size)
                }
                "REQUEST_DOWNLOAD", "SITE_DOWNLOAD_REQUEST" -> {
                    val url = json.optString("url").takeIf { it.startsWith("http") } ?: return null
                    if (PetalMediaSniffer.interceptor.isSearchEngineOrInternalUrl(url)) return null
                    val mime = json.optString("mimeType", "video/mp4")
                    PetalMediaSniffer.onAggressiveMedia(url, mime, cookies, null)
                }
            }
            return null
        }
    }

    data class BuiltInSpec(
        val assetPath: String,
        val extensionId: String,
        val label: String,
        val description: String,
        val prefKey: String
    )

    val builtIns: List<BuiltInSpec> = listOf(
        // Mozilla WebCompat — bundled in the GeckoView AAR, fixes site compatibility breakages.
        // This is the same extension Firefox for Android ships as a default built-in.
        // The resource URI points to GeckoView's own bundled copy (no asset to ship).
        BuiltInSpec(
            assetPath   = "extensions/webcompat/",
            extensionId = "webcompat@mozilla.org",
            label       = "Mozilla WebCompat",
            description = "Fixes compatibility quirks and site breakages for mobile Firefox engine.",
            prefKey     = "petal_builtin_webcompat"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_dark_webpages/",
            extensionId = "petal-dark-webpages@petalbrowser.app",
            label       = "Petal Dark Webpages",
            description = "Smart night mode for all websites with automatic inversion and per-site whitelist.",
            prefKey     = "petal_builtin_dark_webpages"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_clean_link/",
            extensionId = "petal-clean-link@petalbrowser.app",
            label       = "Petal Clean Link",
            description = "Automatically strips tracking tokens (UTM, fbclid, gclid) from links.",
            prefKey     = "petal_builtin_clean_link"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_universal_copy/",
            extensionId = "petal-universal-copy@petalbrowser.app",
            label       = "Petal Universal Copy",
            description = "Bypasses copy restrictions and unlocks text selection on all websites.",
            prefKey     = "petal_builtin_universal_copy"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_translate/",
            extensionId = "petal-translate@petalbrowser.app",
            label       = "Petal Translate",
            description = "Real-time in-page translation engine powered by Petal on-device models.",
            prefKey     = "petal_builtin_translate"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/google_search_fixer/",
            extensionId = "google-search-fixer@petalbrowser.app",
            label       = "Google Search Fixer",
            description = "Ensures modern full-featured Google Search experience on GeckoView.",
            prefKey     = "petal_builtin_google_search_fixer"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/media_grabber/",
            extensionId = "petal-media-grabber@petalbrowser.app",
            label       = "Petal Media Grabber",
            description = "Detects and captures media streams (video/audio) playing on any website for download.",
            prefKey     = "petal_builtin_media_grabber"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_password_autofill/",
            extensionId = "petal-password-autofill@petalbrowser.app",
            label       = "Petal Password Manager & Autofill",
            description = "Detects login forms and integrates with Petal's encrypted local password vault.",
            prefKey     = "petal_builtin_password_autofill"
        )
    )


    /** Check if an extension ID belongs to a Petal built-in extension */
    @JvmStatic
    fun isBuiltIn(extensionId: String?): Boolean {
        if (extensionId.isNullOrBlank()) return false
        val clean = extensionId.trim()
        return builtIns.any { it.extensionId.equals(clean, ignoreCase = true) }
    }

    /** Attach built-in extension delegates to a specific GeckoSession (e.g. content script messaging) */
    @JvmStatic
    fun attachSession(session: GeckoSession?) {
        if (session == null) return
        val mediaGrabber = installedExtensions[MEDIA_GRABBER_ID]
        if (mediaGrabber != null) {
            try {
                session.webExtensionController.setMessageDelegate(mediaGrabber, mediaMessageDelegate, NATIVE_APP)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to attach media grabber delegate to session", t)
            }
        }
    }

    /** Install and sync all built-in extensions. Called from BrowserActivity. */
    @JvmStatic
    fun installAll(context: Context) {
        val runtime = try {
            PetalGeckoRuntime.getOrCreate(context.applicationContext)
        } catch (t: Throwable) {
            Log.e(TAG, "Cannot access GeckoRuntime for built-in extensions", t)
            return
        }
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val controller = runtime.webExtensionController
        for (spec in builtIns) {
            val defaultEnabled = spec.prefKey != "petal_builtin_google_search_fixer"
            val enabled = sp.getBoolean(spec.prefKey, defaultEnabled)
            installAndSync(controller, spec, enabled)
        }
    }

    private fun installAndSync(
        controller: WebExtensionController,
        spec: BuiltInSpec,
        enabled: Boolean
    ) {
        // Special case for Mozilla WebCompat: it does not exist as an APK asset; ignore ensureBuiltIn error
        if (spec.extensionId == "webcompat@mozilla.org") {
            return
        }

        val uri = "resource://android/assets/${spec.assetPath}"
        controller.ensureBuiltIn(uri, spec.extensionId).accept(
            { ext ->
                if (ext == null) return@accept
                installedExtensions[spec.extensionId] = ext
                if (spec.extensionId == MEDIA_GRABBER_ID) {
                    try {
                        ext.setMessageDelegate(mediaMessageDelegate, NATIVE_APP)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to set global message delegate for media grabber", t)
                    }
                }
                controller.setAllowedInPrivateBrowsing(ext, true)
                val action = if (enabled)
                    controller.enable(ext, WebExtensionController.EnableSource.APP)
                else
                    controller.disable(ext, WebExtensionController.EnableSource.APP)
                action.accept(
                    { Log.d(TAG, "${spec.label} → ${if (enabled) "enabled" else "disabled"}") },
                    { e -> Log.e(TAG, "Failed to set state for ${spec.label}", e) }
                )
                Log.i(TAG, "${spec.label} installed (${spec.extensionId})")
            },
            { e -> Log.e(TAG, "Failed to install ${spec.label}", e) }
        )
    }

    /** Toggle a built-in extension at runtime. */
    @JvmStatic
    fun setEnabled(context: Context, prefKey: String, enabled: Boolean) {
        val spec = builtIns.find { it.prefKey == prefKey } ?: return
        val runtime = try { PetalGeckoRuntime.getOrCreate(context.applicationContext) }
                      catch (_: Throwable) { return }
        runtime.webExtensionController.list().accept({ list ->
            val ext = list?.find { it.id == spec.extensionId } ?: return@accept
            val action = if (enabled)
                runtime.webExtensionController.enable(ext, WebExtensionController.EnableSource.APP)
            else
                runtime.webExtensionController.disable(ext, WebExtensionController.EnableSource.APP)
            action.accept(
                { Log.d(TAG, "${spec.label} runtime toggle → ${if (enabled) "on" else "off"}") },
                { e -> Log.e(TAG, "Toggle failed for ${spec.label}", e) }
            )
        }, { e -> Log.e(TAG, "Cannot list extensions for toggle", e) })
    }

    private const val PREF_DARK_WHITELIST = "petal_dark_webpages_whitelist"
    private const val PREF_DARK_CONTRAST = "petal_dark_webpages_contrast"

    @JvmStatic
    fun getDarkWebpagesWhitelist(context: Context): Set<String> {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return sp.getStringSet(PREF_DARK_WHITELIST, emptySet()) ?: emptySet()
    }

    @JvmStatic
    fun addDarkWebpageWhitelist(context: Context, domain: String) {
        val clean = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/")[0]
        if (clean.isBlank()) return
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val current = sp.getStringSet(PREF_DARK_WHITELIST, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(clean)
        sp.edit().putStringSet(PREF_DARK_WHITELIST, current).apply()
    }

    @JvmStatic
    fun removeDarkWebpageWhitelist(context: Context, domain: String) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val current = sp.getStringSet(PREF_DARK_WHITELIST, emptySet())?.toMutableSet() ?: return
        current.remove(domain)
        sp.edit().putStringSet(PREF_DARK_WHITELIST, current).apply()
    }

    @JvmStatic
    fun getDarkWebpagesContrast(context: Context): Int {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return sp.getInt(PREF_DARK_CONTRAST, 90)
    }

    @JvmStatic
    fun setDarkWebpagesContrast(context: Context, contrast: Int) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        sp.edit().putInt(PREF_DARK_CONTRAST, contrast).apply()
    }
}
