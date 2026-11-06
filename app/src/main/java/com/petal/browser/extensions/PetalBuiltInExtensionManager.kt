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
import java.util.Collections
import java.util.WeakHashMap
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
    private const val DARK_WEBPAGES_ID = "petal-dark-webpages@petalbrowser.app"
    private const val SCRIPTLET_DEFUSER_ID = "petal-scriptlet-defuser@petalbrowser.app"
    private const val NATIVE_APP = "petalApp"

    private var appContext: Context? = null
    private val installedExtensions = ConcurrentHashMap<String, WebExtension>()
    private val activeSessions = Collections.newSetFromMap(WeakHashMap<GeckoSession, Boolean>())
    private val sessionPorts = Collections.synchronizedMap(WeakHashMap<GeckoSession, WebExtension.Port>())
    private val pendingEvaluations = ConcurrentHashMap<String, (String?) -> Unit>()

    private val defuserPortDelegate = object : WebExtension.PortDelegate {
        override fun onPortMessage(message: Any, port: WebExtension.Port) {
            val json = runCatching { if (message is JSONObject) message else JSONObject(message.toString()) }.getOrNull()
                ?: return
            if (json.optString("type") == "EVAL_RESULT") {
                val id = json.optString("id")
                val error = json.optString("error").takeIf { it.isNotBlank() }
                val result = json.optString("result").takeIf { it.isNotBlank() && it != "null" }
                val cb = pendingEvaluations.remove(id)
                if (error != null) {
                    cb?.invoke("ERROR: $error")
                } else {
                    cb?.invoke(result)
                }
            }
        }

        override fun onDisconnect(port: WebExtension.Port) {
            synchronized(sessionPorts) {
                val it = sessionPorts.entries.iterator()
                while (it.hasNext()) {
                    if (it.next().value == port) {
                        it.remove()
                    }
                }
            }
        }
    }

    private val scriptletMessageDelegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            port.setDelegate(defuserPortDelegate)
            val sess = port.sender?.session
            if (sess != null) {
                sessionPorts[sess] = port
            }
        }
    }

    private val darkMessageDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            if (nativeApp != NATIVE_APP) return null
            val json = runCatching { if (message is JSONObject) message else JSONObject(message.toString()) }.getOrNull()
                ?: return null
            val msgType = json.optString("type")
            val ctx = appContext ?: return null

            when (msgType) {
                "GET_DARK_SETTINGS" -> {
                    val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
                    val enabled = sp.getBoolean("petal_builtin_dark_webpages", true)
                    val contrast = getDarkWebpagesContrast(ctx)
                    val whitelist = getDarkWebpagesWhitelist(ctx)
                    val resp = JSONObject().apply {
                        put("type", "DARK_SETTINGS_RESPONSE")
                        put("enabled", enabled)
                        put("contrast", contrast)
                        put("whitelist", org.json.JSONArray(whitelist.toList()))
                    }
                    return GeckoResult.fromValue(resp)
                }
                "TOGGLE_DARK_WHITELIST" -> {
                    val host = json.optString("host", "").trim()
                    if (host.isNotBlank()) {
                        val current = getDarkWebpagesWhitelist(ctx)
                        if (isDomainInWhitelist(host, current)) {
                            removeDarkWebpageWhitelist(ctx, host)
                        } else {
                            addDarkWebpageWhitelist(ctx, host)
                        }
                    }
                    val resp = JSONObject().apply {
                        put("type", "DARK_SETTINGS_RESPONSE")
                        put("success", true)
                        put("whitelist", org.json.JSONArray(getDarkWebpagesWhitelist(ctx).toList()))
                    }
                    return GeckoResult.fromValue(resp)
                }
            }
            return null
        }
    }

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

            if (msgType == "PWA_MANIFEST_DETECTED") {
                val manifestUrl = json.optString("manifestUrl").takeIf { it.isNotBlank() }
                val pUrl = json.optString("pageUrl", "")
                val pTitle = json.optString("pageTitle", "")
                if (!manifestUrl.isNullOrBlank()) {
                    com.petal.browser.pwa.PetalPwaManager.onManifestDiscovered(manifestUrl, pUrl, pTitle)
                }
                return null
            }

            if (msgType == "READER_ELIGIBLE_DETECTED") {
                val pUrl = json.optString("pageUrl", "")
                val isArticle = json.optBoolean("isArticle", false)
                com.petal.browser.compose.reader.PetalReaderBridge.onReaderEligibilityDetected(pUrl, isArticle)
                return null
            }

            if (msgType == "READER_ARTICLE_DATA") {
                val pUrl = json.optString("pageUrl", "")
                val artObj = json.optJSONObject("article")
                if (artObj != null) {
                    val title = artObj.optString("title")
                    val author = artObj.optString("author")
                    val lead = artObj.optString("lead")
                    val text = artObj.optString("text")
                    com.petal.browser.compose.reader.PetalReaderBridge.onArticleReceived(pUrl, title, author, lead, text)
                }
                return null
            }

            val pageUrl = json.optString("pageUrl").takeIf { it.startsWith("http") }
            val cookies = json.optString("cookies").takeIf { it.isNotBlank() }
            if (pageUrl != null && PetalMediaSniffer.interceptor.isSearchEngineOrInternalUrl(pageUrl)) {
                return null
            }
            if (pageUrl != null && cookies != null) {
                PetalMediaSniffer.recordCookiesForUrl(pageUrl, cookies)
            }

            when (msgType) {
                "DOM_RESOURCES_SNIFFED" -> {
                    val items = json.optString("items")
                    val sniffedPageUrl = json.optString("pageUrl", "")
                    val tabId = json.optString("tabId").takeIf { it.isNotBlank() } ?: com.petal.browser.media.sniffer.PetalNativeSnifferEngine.activeTabId.value
                    if (!items.isNullOrBlank()) {
                        com.petal.browser.media.sniffer.PetalNativeSnifferEngine.parseDomScanResult(tabId, sniffedPageUrl, items)
                    }
                }
                "MEDIA_GRABBED", "REQUEST_DOWNLOAD", "SITE_DOWNLOAD_REQUEST" -> {
                    val url = json.optString("url").takeIf { it.startsWith("http") } ?: return null
                    if (PetalMediaSniffer.interceptor.isSearchEngineOrInternalUrl(url)) return null
                    val mime = json.optString("mimeType", "video/mp4")
                    val size = json.optLong("sizeBytes", -1L).takeIf { it > 0 }
                    PetalMediaSniffer.onAggressiveMedia(url, mime, cookies, size)
                    val tabId = json.optString("tabId").takeIf { it.isNotBlank() } ?: com.petal.browser.media.sniffer.PetalNativeSnifferEngine.activeTabId.value
                    com.petal.browser.media.sniffer.PetalNativeSnifferEngine.registerResource(
                        tabId = tabId,
                        url = url,
                        mimeType = mime,
                        sizeBytes = size,
                        pageUrl = pageUrl ?: ""
                    )
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
        val prefKey: String,
        val defaultOn: Boolean = true
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
            label       = "Petal Smart Dark Mode",
            description = "Intelligent website dark mode that inverts bright canvases while preserving images and videos.",
            prefKey     = "petal_builtin_dark_webpages"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_clean_link/",
            extensionId = "petal-clean-link@petalbrowser.app",
            label       = "Petal Clean Link",
            description = "Strips tracking parameters (UTM, fbclid, gclid, etc.) from clicked links in real time.",
            prefKey     = "petal_builtin_clean_link"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/google_search_fixer/",
            extensionId = "google-search-fixer@petalbrowser.app",
            label       = "Google Search Fixer",
            description = "Ensures modern full-featured Google Search experience on GeckoView.",
            prefKey     = "petal_builtin_google_search_fixer"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_scriptlet_defuser/",
            extensionId = "petal-scriptlet-defuser@petalbrowser.app",
            label       = "Petal Anti-Adblock Defuser",
            description = "Brave-style scriptlets: neutralizes adblock detection, paywall blockers, and ephemeral iframe storage isolation.",
            prefKey     = "petal_builtin_scriptlet_defuser"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/media_grabber/",
            extensionId = "petal-media-grabber@petalbrowser.app",
            label       = "Petal Resource Sniffer",
            description = "Native engine-level sniffer capturing videos, audios, high-res images, and documents.",
            prefKey     = "petal_builtin_media_grabber"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_consent_manager/",
            extensionId = "petal-consent-manager@petalbrowser.app",
            label       = "Auto Cookie Consent",
            description = "Automatically rejects non-essential GDPR cookie consent dialogs (OneTrust, CookieBot, Didomi, TrustArc, Quantcast, and more).",
            prefKey     = "petal_builtin_consent_manager"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_bionic_reading/",
            extensionId = "petal-bionic-reading@petalbrowser.app",
            label       = "Petal Bionic Reader",
            description = "Guides eyes through reading with artificial fixation points.",
            prefKey     = "petal_builtin_bionic_reading"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_boosts/",
            extensionId = "petal-boosts@petalbrowser.app",
            label       = "Petal Arc Boosts",
            description = "Custom webpage themer, font replacer, and interactive element zapper.",
            prefKey     = "petal_builtin_boosts"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_brave_news/",
            extensionId = "petal-brave-news@petalbrowser.app",
            label       = "Petal Feeds & News",
            description = "Brave-style privacy RSS news aggregator with custom sources and rich thumbnails.",
            prefKey     = "petal_builtin_brave_news"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_torrays/",
            extensionId = "petal-torrays@petalbrowser.app",
            label       = "Torrays",
            description = "Open-source, ad-free torrent and media search index with instant magnet links.",
            prefKey     = "petal_builtin_torrays",
            defaultOn   = true
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
        synchronized(activeSessions) {
            activeSessions.add(session)
        }
        val mediaGrabber = installedExtensions[MEDIA_GRABBER_ID]
        if (mediaGrabber != null) {
            try {
                session.webExtensionController.setMessageDelegate(mediaGrabber, mediaMessageDelegate, NATIVE_APP)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to attach media grabber delegate to session", t)
            }
        }
        val darkWebpages = installedExtensions[DARK_WEBPAGES_ID]
        if (darkWebpages != null) {
            try {
                session.webExtensionController.setMessageDelegate(darkWebpages, darkMessageDelegate, NATIVE_APP)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to attach dark webpages delegate to session", t)
            }
        }
        val scriptletDefuser = installedExtensions[SCRIPTLET_DEFUSER_ID]
        if (scriptletDefuser != null) {
            try {
                session.webExtensionController.setMessageDelegate(scriptletDefuser, scriptletMessageDelegate, NATIVE_APP)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to attach scriptlet defuser delegate to session", t)
            }
        }
    }

    /** Install and sync all built-in extensions. Called from BrowserActivity. */
    @JvmStatic
    fun installAll(context: Context) {
        appContext = context.applicationContext
        val runtime = try {
            PetalGeckoRuntime.getOrCreate(context.applicationContext)
        } catch (t: Throwable) {
            Log.e(TAG, "Cannot access GeckoRuntime for built-in extensions", t)
            return
        }
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val controller = runtime.webExtensionController
        for (spec in builtIns) {
            val enabled = sp.getBoolean(spec.prefKey, spec.defaultOn)
            installAndSync(controller, spec, enabled)
        }
    }

    /**
     * Installs a single built-in and applies its saved on/off preference. Used by callers that
     * run before BrowserActivity (e.g. Application start-up) so they honour the user's toggle
     * instead of force-enabling the extension and racing [installAll].
     */
    @JvmStatic
    fun ensureInstalled(context: Context, extensionId: String) {
        appContext = context.applicationContext
        val spec = builtIns.find { it.extensionId == extensionId } ?: return
        val runtime = try {
            PetalGeckoRuntime.getOrCreate(context.applicationContext)
        } catch (t: Throwable) {
            Log.e(TAG, "Cannot access GeckoRuntime for ${spec.label}", t)
            return
        }
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        installAndSync(runtime.webExtensionController, spec, sp.getBoolean(spec.prefKey, spec.defaultOn))
    }

    private fun installAndSync(
        controller: WebExtensionController,
        spec: BuiltInSpec,
        enabled: Boolean
    ) {
        // Special case for Mozilla WebCompat: it does not exist as an APK asset; ignore ensureBuiltIn error
        if (spec.extensionId == "webcompat@mozilla.org" || spec.assetPath.isEmpty()) {
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
                    val sessions = synchronized(activeSessions) { activeSessions.toList() }
                    for (session in sessions) {
                        try {
                            session.webExtensionController.setMessageDelegate(ext, mediaMessageDelegate, NATIVE_APP)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to retroactively attach media grabber delegate to active session", t)
                        }
                    }
                }
                if (spec.extensionId == DARK_WEBPAGES_ID) {
                    try {
                        ext.setMessageDelegate(darkMessageDelegate, NATIVE_APP)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to set global message delegate for dark webpages", t)
                    }
                    val sessions = synchronized(activeSessions) { activeSessions.toList() }
                    for (session in sessions) {
                        try {
                            session.webExtensionController.setMessageDelegate(ext, darkMessageDelegate, NATIVE_APP)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to retroactively attach dark webpages delegate to active session", t)
                        }
                    }
                }
                if (spec.extensionId == SCRIPTLET_DEFUSER_ID) {
                    try {
                        ext.setMessageDelegate(scriptletMessageDelegate, NATIVE_APP)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to set global message delegate for scriptlet defuser", t)
                    }
                    val sessions = synchronized(activeSessions) { activeSessions.toList() }
                    for (session in sessions) {
                        try {
                            session.webExtensionController.setMessageDelegate(ext, scriptletMessageDelegate, NATIVE_APP)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to retroactively attach scriptlet defuser delegate to active session", t)
                        }
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
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        sp.edit().putBoolean(prefKey, enabled).apply()
        if (prefKey == "petal_builtin_dark_webpages") {
            sp.edit().putBoolean("sp_force_dark_mode", enabled).apply()
        }
        if (spec.assetPath.isEmpty()) {
            return
        }
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
    fun isDomainInWhitelist(domain: String?, whitelist: Set<String>): Boolean {
        if (domain.isNullOrBlank()) return false
        val clean = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/")[0]
        return whitelist.any { item ->
            val cleanItem = item.trim().lowercase()
            clean == cleanItem || clean.endsWith(".$cleanItem")
        }
    }

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

    const val PREF_TORRAYS = "petal_builtin_torrays"

    @JvmStatic
    fun isTorraysEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return sp.getBoolean(PREF_TORRAYS, true)
    }

    @JvmStatic
    fun setTorraysEnabled(context: Context, enabled: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        sp.edit().putBoolean(PREF_TORRAYS, enabled).apply()
    }

    /**
     * Evaluates JavaScript in the given GeckoSession via the built-in scriptlet defuser
     * content script native messaging port. The result is returned asynchronously to the callback.
     * If the native port is not connected or session is unavailable, falls back to loadUri(javascript:...)
     * and honestly reports null to the callback without pretending an empty result was returned.
     */
    @JvmStatic
    fun evaluateJavascript(session: GeckoSession?, script: String, callback: ((String?) -> Unit)? = null) {
        if (session == null) {
            callback?.invoke(null)
            return
        }
        val port = sessionPorts[session]
        if (port != null) {
            val reqId = java.util.UUID.randomUUID().toString()
            if (callback != null) {
                pendingEvaluations[reqId] = callback
            }
            val json = JSONObject().apply {
                put("type", "EVAL_JS")
                put("id", reqId)
                put("script", script)
            }
            try {
                port.postMessage(json)
                return
            } catch (t: Throwable) {
                pendingEvaluations.remove(reqId)
                Log.w(TAG, "Failed to post EVAL_JS message to port", t)
            }
        }

        // Fallback: run via javascript: URL without a result path
        try {
            val cleanScript = script.trim().removeSuffix(";")
            val js = "javascript:(function(){ try { $cleanScript; } catch(e) { console.error('JS eval error:', e); } })()"
            session.loadUri(js)
        } catch (_: Throwable) {}
        callback?.invoke(null)
    }
}
