package com.petal.browser.extensions

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.util.zip.ZipFile
import org.json.JSONObject
import androidx.preference.PreferenceManager
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.BrowserContainer
import com.petal.browser.view.PetalGeckoView
import com.petal.browser.activity.BrowserActivity

/**
 * PetalExtensionManager
 * ─────────────────────────────────────────────────────────────────────────
 * Real Firefox add-on support for Petal.
 *
 * Petal's rendering engine is Mozilla GeckoView (see [PetalGeckoRuntime]) - the exact same
 * engine that powers Firefox for Android. GeckoView exposes [WebExtensionController], the
 * very same WebExtension/API surface Firefox itself uses, which means signed `.xpi` packages
 * published on addons.mozilla.org (AMO) - the Firefox Add-ons store - install and run in Petal
 * completely unmodified. There is no shim, no polyfill, and no separate "Petal extension
 * format": an add-on installed here is byte-for-byte the same package Firefox would install,
 * so anything AMO marks as "Available on Firefox for Android" works here too.
 *
 * This object is a thin, app-facing wrapper around that controller:
 *  - install/uninstall/enable/disable, backed by GeckoView's own persistent extension store
 *    (extensions survive app restarts automatically - GeckoView owns that storage, Petal
 *    does not duplicate it)
 *  - a reactive [extensions] list for Compose UI
 *  - an install/permission confirmation flow surfaced as [pendingPrompt] so the UI can render
 *    a Material 3 dialog instead of Gecko's own native prompt
 *  - toolbar action (browser/page action) plumbing so extensions with a popup UI (uBlock
 *    Origin, Bitwarden, etc.) can present that popup
 */
object PetalExtensionManager {

    private const val TAG = "PetalExtensionManager"

    /** A permission confirmation the UI must resolve before install/update proceeds. */
    data class PendingPrompt(
        val extension: WebExtension,
        val permissions: List<String>,
        val origins: List<String>,
        val isUpdate: Boolean,
        val respond: (granted: Boolean) -> Unit
    )

    /** A request from an extension to show its browser/page action popup. */
    data class PendingPopup(
        val extensionId: String,
        val extensionName: String,
        val session: GeckoSession,
        /** The real browsing tab that opened this popup, when Gecko supplied one. */
        val sourceSession: GeckoSession? = null
    )

    data class InstalledExtension(
        val id: String,
        val name: String,
        val description: String,
        val version: String,
        val enabled: Boolean,
        val temporary: Boolean,
        val allowedInPrivateBrowsing: Boolean,
        val homepageUrl: String?,
        val amoListingUrl: String?,
        val optionsPageUrl: String?,
        val supportsPopup: Boolean,
        val icon: Bitmap?,
        val raw: WebExtension
    )

    /** Small curated set of well-known, Android-compatible extensions from addons.mozilla.org. */
    data class CatalogEntry(
        val id: String,
        val name: String,
        val description: String,
        val amoSlug: String,
        val amoListingUrl: String
    ) {
        /**
         * AMO's permanent "always the latest signed build" redirect for this add-on.
         * NOTE: this must be the "/firefox/downloads/latest/" path - AMO has no
         * "/android/downloads/" endpoint. Firefox and Firefox for Android share the
         * same add-on catalog/redirect; only the *listing* pages have an "/android/"
         * variant, not the download links.
         */
        val downloadUrl: String get() = "https://addons.mozilla.org/firefox/downloads/latest/$amoSlug/latest.xpi"
    }

    val catalog: List<CatalogEntry> = listOf(
        CatalogEntry(
            id = "ublock-origin",
            name = "uBlock Origin",
            description = "Efficient, wide-spectrum ad & tracker content blocker.",
            amoSlug = "ublock-origin",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/ublock-origin/"
        ),
        CatalogEntry(
            id = "darkreader",
            name = "Dark Reader",
            description = "Dark mode for every website, with brightness & contrast controls.",
            amoSlug = "darkreader",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/darkreader/"
        ),
        CatalogEntry(
            id = "privacy-badger17",
            name = "Privacy Badger",
            description = "Automatically learns to block invisible trackers.",
            amoSlug = "privacy-badger17",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/privacy-badger17/"
        ),
        CatalogEntry(
            id = "bitwarden-password-manager",
            name = "Bitwarden Password Manager",
            description = "Secure password, passkey, and vault manager.",
            amoSlug = "bitwarden-password-manager",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/bitwarden-password-manager/"
        ),
        CatalogEntry(
            id = "istilldontcareaboutcookies",
            name = "I still don't care about cookies",
            description = "Automatically dismisses cookie consent popups.",
            amoSlug = "istilldontcareaboutcookies",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/istilldontcareaboutcookies/"
        ),
        CatalogEntry(
            id = "youtube-high-definition",
            name = "YouTube High Definition",
            description = "Automatically plays YouTube videos in HD, resizes the player, and adds auto-stop and mute.",
            amoSlug = "youtube-high-definition",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/youtube-high-definition/"
        ),
        CatalogEntry(
            id = "view-page-archive",
            name = "Web Archives",
            description = "View archived and cached versions of web pages, such as the Wayback Machine and Archive.is.",
            amoSlug = "view-page-archive",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/view-page-archive/"
        ),
        CatalogEntry(
            id = "tomato-clock",
            name = "Tomato Clock",
            description = "A simple Pomodoro-style timer for managing your productivity.",
            amoSlug = "tomato-clock",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/tomato-clock/"
        ),
        CatalogEntry(
            id = "video-background-play-fix",
            name = "Video Background Play Fix",
            description = "Keeps videos playing in the background by blocking the Page Visibility and Fullscreen APIs.",
            amoSlug = "video-background-play-fix",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/video-background-play-fix/"
        ),
        CatalogEntry(
            id = "google-search-fixer",
            name = "Google Search Fixer",
            description = "Overrides the user-agent on Google Search so it serves the Chrome-style search experience.",
            amoSlug = "google-search-fixer",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/google-search-fixer/"
        ),
        CatalogEntry(
            id = "cookie-editor",
            name = "Cookie-Editor",
            description = "Create, edit, search, and delete cookies for the current tab.",
            amoSlug = "cookie-editor",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/cookie-editor/"
        ),
        CatalogEntry(
            id = "adguard-adblocker",
            name = "AdGuard AdBlocker",
            description = "Blocks ads and pop-ups on Facebook, YouTube, and every other website.",
            amoSlug = "adguard-adblocker",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/adguard-adblocker/"
        )
    )

    /** AMO's Android extensions catalog - opened by "Find more extensions". */
    const val amoAndroidBrowseUrl = "https://addons.mozilla.org/en-US/android/"

    private val _extensions = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val extensions: StateFlow<List<InstalledExtension>> = _extensions.asStateFlow()

    private val _pendingPrompt = MutableStateFlow<PendingPrompt?>(null)
    val pendingPrompt: StateFlow<PendingPrompt?> = _pendingPrompt.asStateFlow()

    private val _pendingPopup = MutableStateFlow<PendingPopup?>(null)
    val pendingPopup: StateFlow<PendingPopup?> = _pendingPopup.asStateFlow()

    fun interface PopupRequestListener {
        fun onPopupRequest(popup: PendingPopup)
    }

    private var onPopupRequestListener: PopupRequestListener? = null

    @JvmStatic
    fun setPopupRequestListener(listener: PopupRequestListener?) {
        onPopupRequestListener = listener
    }

    private fun notifyPopupRequested(popup: PendingPopup) {
        val listener = onPopupRequestListener ?: return
        Handler(Looper.getMainLooper()).post {
            listener.onPopupRequest(popup)
        }
    }

    /** Latest default browser/page action for each installed extension. */
    private val actionByExtensionId = mutableMapOf<String, WebExtension.Action>()
    /** Per-tab actions are important for extensions such as uBlock that expose different
     * state/badges depending on the current website. */
    private val sessionActionByExtensionId = java.util.concurrent.ConcurrentHashMap<GeckoSession, MutableMap<String, WebExtension.Action>>()

    private fun rememberAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
        // GeckoView may deliver the action with the originating tab session. Keep the
        // per-session action authoritative so a stale action from another tab cannot be
        // clicked when the user opens an extension from the current tab.
        if (session != null) {
            sessionActionByExtensionId
                .computeIfAbsent(session) { java.util.concurrent.ConcurrentHashMap() }[extension.id] = action
        } else {
            synchronized(actionByExtensionId) { actionByExtensionId[extension.id] = action }
        }
    }

    /**
     * Keeps GeckoView's WebExtension tab dispatcher in sync with Petal's foreground tab.
     * This is required for Action.click()/tabs APIs to target the same tab the user sees.
     */
    @JvmStatic
    fun setActiveBrowserSession(oldSession: GeckoSession?, newSession: GeckoSession?) {
        val ctx = appContext ?: return
        val controller = try {
            PetalGeckoRuntime.getOrCreate(ctx).webExtensionController
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to access WebExtensionController while changing active tab", t)
            return
        }

        if (oldSession != null && oldSession !== newSession && oldSession.isOpen) {
            try { controller.setTabActive(oldSession, false) }
            catch (t: Throwable) { Log.d(TAG, "Failed to deactivate extension tab session", t) }
        }
        if (newSession != null && newSession.isOpen) {
            try {
                // Make sure delegates exist even when the tab was created before the
                // extension list finished loading during a cold start.
                attachSession(newSession)
                controller.setTabActive(newSession, true)
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to activate extension tab session", t)
            }
        }
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile private var attached = false
    @Volatile private var appContext: Context? = null

    /** Idempotent - safe to call from every Activity#onCreate. */
    @JvmStatic
    @Synchronized
    fun attach(context: Context) {
        appContext = context.applicationContext
        if (attached) {
            refresh()
            return
        }
        attached = true

        val runtime = PetalGeckoRuntime.getOrCreate(context.applicationContext)
        val controller = runtime.webExtensionController

        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                _pendingPrompt.value = PendingPrompt(
                    extension = extension,
                    permissions = permissions.toList(),
                    origins = origins.toList(),
                    isUpdate = false
                ) { granted ->
                    _pendingPrompt.value = null
                    result.complete(
                        WebExtension.PermissionPromptResponse(
                            granted, granted, granted
                        )
                    )
                }
                return result
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<out String>,
                newOrigins: Array<out String>,
                newDataCollectionPermissions: Array<out String>
            ): GeckoResult<AllowOrDeny>? {
                val result = GeckoResult<AllowOrDeny>()
                _pendingPrompt.value = PendingPrompt(
                    extension = extension,
                    permissions = newPermissions.toList(),
                    origins = newOrigins.toList(),
                    isUpdate = true
                ) { granted ->
                    _pendingPrompt.value = null
                    result.complete(if (granted) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                }
                return result
            }

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<AllowOrDeny>? {
                // Runtime-requested optional permissions (permissions.request()) - allow
                // silently for already-installed, user-approved extensions.
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
        })

        controller.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) {
                attachActionDelegate(extension)
                attachExtensionToOpenSessions(extension)
                refresh()
            }

            override fun onInstallationFailed(
                extension: WebExtension?,
                installException: WebExtension.InstallException
            ) {
                _busy.value = false
                _lastError.value = describeInstallError(installException)
                Log.w(TAG, "Extension installation failed: ${installException.code}")
            }

            override fun onUninstalled(extension: WebExtension) {
                refresh()
            }

            override fun onEnabling(extension: WebExtension) { refresh() }
            override fun onEnabled(extension: WebExtension) {
                attachActionDelegate(extension)
                attachExtensionToOpenSessions(extension)
                refresh()
            }
            override fun onDisabling(extension: WebExtension) { refresh() }
            override fun onDisabled(extension: WebExtension) { refresh() }
            override fun onReady(extension: WebExtension) {
                attachActionDelegate(extension)
                attachExtensionToOpenSessions(extension)
                refresh()
            }
        })

        refresh()
    }

    private fun describeInstallError(e: WebExtension.InstallException): String {
        return when (e.code) {
            WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE ->
                "Couldn't download the extension - check your connection and try again."
            WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_HASH ->
                "The downloaded file didn't match its expected signature."
            WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE ->
                "That extension package is corrupt or not a valid .xpi file."
            WebExtension.InstallException.ErrorCodes.ERROR_FILE_ACCESS ->
                "Petal couldn't access that file."
            WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED ->
                "That extension isn't signed by Mozilla, so it can't be installed."
            WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_TYPE,
            WebExtension.InstallException.ErrorCodes.ERROR_UNSUPPORTED_ADDON_TYPE ->
                "This Firefox add-on type is not supported on Android. Choose an extension listed for Firefox Android."
            WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED ->
                "This extension has been blocklisted by Mozilla for safety reasons."
            WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE ->
                "This extension isn't compatible with Petal's engine version."
            WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED ->
                "Installation was canceled."
            else -> "Couldn't install that extension (error ${e.code})."
        }
    }

    /** Attaches the popup/action bridge to a given extension so its toolbar button works. */
    private fun attachActionDelegate(extension: WebExtension) {
        try {
            extension.setActionDelegate(object : WebExtension.ActionDelegate {
                override fun onBrowserAction(
                    ext: WebExtension,
                    session: GeckoSession?,
                    action: WebExtension.Action
                ) {
                    rememberAction(ext, session, action)
                }

                override fun onPageAction(
                    ext: WebExtension,
                    session: GeckoSession?,
                    action: WebExtension.Action
                ) {
                    rememberAction(ext, session, action)
                }

                override fun onOpenPopup(
                    ext: WebExtension,
                    action: WebExtension.Action
                ): GeckoResult<GeckoSession>? = createPopupSession(ext)

                override fun onTogglePopup(
                    ext: WebExtension,
                    action: WebExtension.Action
                ): GeckoResult<GeckoSession>? = createPopupSession(ext)
            })
        } catch (e: Exception) {
            Log.d(TAG, "Failed to attach action delegate to ${extension.id}", e)
        }
    }

    /**
     * Attaches action delegates for all active extensions to a given tab's GeckoSession,
     * connecting tab-specific extension actions and badges to the active browsing context.
     */
    private fun attachExtensionToOpenSessions(extension: WebExtension) {
        val ctx = appContext ?: return
        BrowserContainer.list().forEach { controller ->
            val gecko = controller as? PetalGeckoView ?: return@forEach
            val session = gecko.session
            if (!session.isOpen) return@forEach
            try {
                session.webExtensionController.setActionDelegate(extension, object : WebExtension.ActionDelegate {
                    override fun onBrowserAction(ext: WebExtension, eventSession: GeckoSession?, action: WebExtension.Action) {
                        rememberAction(ext, eventSession ?: session, action)
                    }
                    override fun onPageAction(ext: WebExtension, eventSession: GeckoSession?, action: WebExtension.Action) {
                        rememberAction(ext, eventSession ?: session, action)
                    }
                    override fun onOpenPopup(ext: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? = createPopupSession(ext, session)
                    override fun onTogglePopup(ext: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? = createPopupSession(ext, session)
                })
                session.webExtensionController.setTabDelegate(extension, createSessionTabDelegate(ctx))
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to attach newly-installed extension ${extension.id} to tab", t)
            }
        }
    }

    /**
     * Gecko's SessionTabDelegate is the missing piece between a WebExtension popup and
     * Petal's real tab model. Without it, APIs such as browser.tabs.update/remove can
     * execute in Gecko but have no effect on the visible Petal tab.
     */
    private fun createSessionTabDelegate(context: Context): WebExtension.SessionTabDelegate {
        return object : WebExtension.SessionTabDelegate {
            override fun onCloseTab(extension: WebExtension?, session: GeckoSession): GeckoResult<AllowOrDeny> {
                Handler(Looper.getMainLooper()).post {
                    try {
                        val activity = findBrowserActivity(context) ?: return@post
                        val controller = BrowserContainer.list().firstOrNull {
                            (it as? PetalGeckoView)?.session === session
                        }
                        if (controller != null) activity.removeAlbum(controller)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Extension tab close request failed", t)
                    }
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onUpdateTab(
                extension: WebExtension,
                session: GeckoSession,
                details: WebExtension.UpdateTabDetails
            ): GeckoResult<AllowOrDeny> {
                Handler(Looper.getMainLooper()).post {
                    try {
                        val activity = findBrowserActivity(context) ?: return@post
                        val controller = BrowserContainer.list().firstOrNull {
                            (it as? PetalGeckoView)?.session === session
                        }
                        val gecko = controller as? PetalGeckoView
                        if (gecko != null) {
                            details.url?.takeIf { it.isNotBlank() }?.let(gecko::loadUrl)
                            if (details.active == true) activity.showAlbum(gecko)
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Extension tab update request failed", t)
                    }
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
        }
    }

    private fun attachTabDelegate(session: GeckoSession, context: Context) {
        for (extItem in _extensions.value) {
            try {
                session.webExtensionController.setTabDelegate(extItem.raw, createSessionTabDelegate(context))
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to attach tab delegate for ${extItem.id}", t)
            }
        }
    }

    @JvmStatic
    fun attachSession(session: GeckoSession?) {
        if (session == null) return
        val ctx = appContext ?: return
        for (extItem in _extensions.value) {
            val ext = extItem.raw
            try {
                session.webExtensionController.setActionDelegate(ext, object : WebExtension.ActionDelegate {
                    override fun onBrowserAction(ext: WebExtension, geckoSession: GeckoSession?, action: WebExtension.Action) {
                        rememberAction(ext, geckoSession ?: session, action)
                    }
                    override fun onPageAction(ext: WebExtension, geckoSession: GeckoSession?, action: WebExtension.Action) {
                        rememberAction(ext, geckoSession ?: session, action)
                    }
                    override fun onOpenPopup(ext: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? = createPopupSession(ext, session)
                    override fun onTogglePopup(ext: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? = createPopupSession(ext, session)
                })
                session.webExtensionController.setTabDelegate(ext, createSessionTabDelegate(ctx))
            } catch (e: Exception) {
                Log.d(TAG, "Failed to attach extension delegates for ${ext.id}", e)
            }
        }
    }

    private fun createPopupSession(extension: WebExtension, sourceSession: GeckoSession? = null): GeckoResult<GeckoSession>? {
        _pendingPopup.value?.session?.let { existing ->
            try {
                existing.setActive(false)
                existing.close()
            } catch (ignored: Exception) {}
        }
        _pendingPopup.value = null

        val popupSettings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .allowJavascript(true)
            .viewportMode(org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
        val popupSession = GeckoSession(popupSettings)
        val ctx = appContext
        if (ctx != null) {
            val runtime = PetalGeckoRuntime.getOrCreate(ctx)
            if (!popupSession.isOpen) {
                popupSession.open(runtime)
            }
        }

        // Inject mobile-responsive CSS when the extension popup page finishes loading.
        // Without this, many extension popups (uBlock Origin, Bitwarden, AdGuard, etc.)
        // render at their desktop fixed width and overflow the phone screen.
        popupSession.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                injectExtensionPopupResponsiveFix(session)
            }
        }

        val popup = PendingPopup(
            extensionId = extension.id,
            extensionName = extension.metaData.name ?: extension.id,
            session = popupSession,
            sourceSession = sourceSession
        )
        _pendingPopup.value = popup
        notifyPopupRequested(popup)
        return GeckoResult.fromValue(popupSession)
    }

    /** Injects a mobile-responsive CSS + viewport fix into extension popup sessions.
     *  Matches omni's injectExtensionPopupResponsiveFix so all extension popups look
     *  correct on phone-sized screens instead of overflowing at their desktop widths. */
    private fun injectExtensionPopupResponsiveFix(session: GeckoSession) {
        val js = """
            (function() {
                try {
                    var existing = document.querySelector('meta[name="viewport"]');
                    if (!existing) {
                        var meta = document.createElement('meta');
                        meta.name    = 'viewport';
                        meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';
                        (document.head || document.documentElement).appendChild(meta);
                    } else if (!existing.content || existing.content.indexOf('width=device-width') === -1) {
                        existing.content = 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';
                    }
                    if (document.getElementById('petal-ext-popup-responsive')) return;
                    var style = document.createElement('style');
                    style.id = 'petal-ext-popup-responsive';
                    style.innerHTML = [
                        'html, body { max-width: 100vw !important; width: 100% !important; min-width: unset !important; overflow-x: hidden !important; box-sizing: border-box !important; }',
                        '*, *::before, *::after { box-sizing: border-box !important; }',
                        '.container, .wrapper, .content, .inner, .card, .panel, .notification, .popup, .popup-container, .popup-inner, .app, .app-container, .main, main, [role="main"], [class*="container"], [class*="wrapper"], [class*="card"], [class*="notification"], [class*="popup"], [class*="panel"], [class*="dialog"], [class*="modal"], [id*="container"], [id*="wrapper"], [id*="notification"], [id*="popup"] { max-width: calc(100vw - 8px) !important; width: auto !important; min-width: unset !important; margin-left: auto !important; margin-right: auto !important; overflow-x: hidden !important; }',
                        'button, input, select, textarea, a { max-width: 100% !important; word-break: break-word !important; }',
                        '[style*="position: fixed"], [style*="position:fixed"] { max-width: 100vw !important; width: 100% !important; left: 0 !important; right: 0 !important; }'
                    ].join(' ');
                    (document.head || document.documentElement).appendChild(style);
                } catch(e) {}
            })();
        """.trimIndent().replace("\n", " ")
        try {
            session.loadUri("javascript:$js")
        } catch (ignored: Exception) {}
    }


    @JvmStatic
    fun dismissPopup() {
        _pendingPopup.value?.session?.let { session ->
            try {
                session.setActive(false)
                session.close()
            } catch (ignored: Exception) {}
        }
        _pendingPopup.value = null
    }

    /**
     * Manually triggers the browser-action (toolbar popup) for an installed extension from the UI.
     *
     * In Firefox/Fennec, clicking an extension triggers its Action.click(), opening the
     * real interactive popup (e.g. AdGuard toggle, uBlock Origin power button).
     * If the action delegate isn't dispatched yet, this seamlessly falls back to resolving
     * the extension's default_popup URL from manifest.json via its baseUrl.
     */
    @JvmOverloads
    fun triggerBrowserAction(extensionId: String, context: Context? = null) {
        val extItem = _extensions.value.find { it.id == extensionId } ?: run {
            _lastError.value = "Extension not found."
            return
        }
        if (!extItem.enabled) return
        val rawExt = extItem.raw
        val ctx = context ?: appContext ?: return

        // 1. Try action.click() if an action was captured and registered
        val activeSession = currentBrowserSession(ctx)
        val sessionAction = activeSession?.let { sessionActionByExtensionId[it]?.get(extensionId) }
        val fallbackAction = synchronized(actionByExtensionId) { actionByExtensionId[extensionId] }

        // Prefer the action belonging to the visible tab. Only use the global action when
        // there is no live active tab action at all (for example during very early startup).
        val action = sessionAction ?: if (activeSession == null) fallbackAction else null
        if (action != null) {
            try {
                action.click()
                // Some GeckoView/extension combinations deliver the popup asynchronously.
                // If no popup was produced, the manifest URL fallback below will recover it.
                Handler(Looper.getMainLooper()).postDelayed({
                    if (_pendingPopup.value == null) {
                        try {
                            val stillActive = currentBrowserSession(ctx)
                            if (stillActive === activeSession) {
                                val directUrl = resolveExtensionPopupUrl(rawExt)
                                if (!directUrl.isNullOrBlank()) openDirectPopup(rawExt, directUrl, ctx)
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Delayed extension popup fallback failed for $extensionId", t)
                        }
                    }
                }, 700L)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to click extension action for $extensionId, falling back to direct popup load", e)
            }
        }

        // 2. Direct popup resolution: load the real extension popup directly in a GeckoSession!
        val popupUrl = resolveExtensionPopupUrl(rawExt)
        if (popupUrl != null && ctx != null) {
            openDirectPopup(rawExt, popupUrl, ctx)
            return
        }

        // 3. Fallback: If extension truly does not have an action popup, open options page if present
        val optionsUrl = extItem.optionsPageUrl ?: resolveExtensionOptionsUrl(rawExt)
        if (optionsUrl != null && ctx != null) {
            openOptionsPage(extensionId, ctx)
        } else {
            val msg = "${extItem.name} does not have a popup interface."
            _lastError.value = msg
            if (ctx != null) {
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openDirectPopup(extension: WebExtension, popupUrl: String, context: Context) {
        _pendingPopup.value?.session?.let { existing ->
            try {
                existing.setActive(false)
                existing.close()
            } catch (ignored: Exception) {}
        }
        _pendingPopup.value = null

        val popupSettings = org.mozilla.geckoview.GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .allowJavascript(true)
            .viewportMode(org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
        val popupSession = GeckoSession(popupSettings)
        val runtime = PetalGeckoRuntime.getOrCreate(context.applicationContext)
        if (!popupSession.isOpen) {
            popupSession.open(runtime)
        }
        popupSession.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                injectExtensionPopupResponsiveFix(session)
            }
        }
        popupSession.loadUri(popupUrl)

        val popup = PendingPopup(
            extensionId = extension.id,
            extensionName = extension.metaData.name ?: extension.id,
            session = popupSession
        )
        _pendingPopup.value = popup
        notifyPopupRequested(popup)
    }

    /**
     * Opens the options/settings page of an extension in a real foreground browser tab,
     * matching Firefox for Android (Fennec) tabHandler.onNewTab() behavior.
     * If no separate settings page exists, gracefully falls back to opening its popup.
     */
    fun openOptionsPage(extensionId: String, context: Context) {
        val extItem = _extensions.value.find { it.id == extensionId } ?: run {
            _lastError.value = "Extension not found."
            return
        }
        val optionsUrl = extItem.optionsPageUrl ?: resolveExtensionOptionsUrl(extItem.raw)
        if (optionsUrl.isNullOrBlank()) {
            val popupUrl = resolveExtensionPopupUrl(extItem.raw)
            if (popupUrl != null) {
                triggerBrowserAction(extensionId, context)
            } else {
                val msg = "No settings page available for ${extItem.name}."
                _lastError.value = msg
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            return
        }

        val activity = findBrowserActivity(context)
        if (activity != null) {
            activity.runOnUiThread {
                activity.addAlbum("${extItem.name} Settings", optionsUrl, true)
            }
        } else {
            openDirectPopup(extItem.raw, optionsUrl, context)
        }
    }

    private fun currentBrowserSession(context: Context): GeckoSession? {
        val activity = findBrowserActivity(context) ?: return null
        return (activity.currentAlbumController as? PetalGeckoView)?.session
    }

    private fun findBrowserActivity(context: Context): com.petal.browser.activity.BrowserActivity? {
        var ctx: Context? = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is com.petal.browser.activity.BrowserActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun getManifest(extension: WebExtension): JSONObject? {
        return try {
            val loc = extension.location ?: return null
            val rawPath = when {
                loc.startsWith("jar:file:", ignoreCase = true) ->
                    loc.removePrefix("jar:file:").substringBefore("!")
                loc.startsWith("file:", ignoreCase = true) ->
                    loc.removePrefix("file:").substringBefore("!")
                else -> loc.substringBefore("!")
            }
            val cleanPath = if (rawPath.startsWith("//")) rawPath.removePrefix("//") else rawPath
            val decodedPath = Uri.decode(cleanPath)
            val file = File(decodedPath)
            val manifestText = if (file.isDirectory) {
                File(file, "manifest.json").takeIf { it.isFile }?.readText()
            } else if (file.isFile) {
                ZipFile(file).use { zip ->
                    val entry = zip.getEntry("manifest.json") ?: return null
                    zip.getInputStream(entry).bufferedReader().use { it.readText() }
                }
            } else null
            manifestText?.let { JSONObject(it) }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to get manifest for ${extension.id}", e)
            null
        }
    }

    fun resolveExtensionPopupUrl(extension: WebExtension): String? {
        val manifest = getManifest(extension) ?: return null
        val popupPath = manifest.optJSONObject("browser_action")?.optString("default_popup")?.takeIf { it.isNotBlank() }
            ?: manifest.optJSONObject("action")?.optString("default_popup")?.takeIf { it.isNotBlank() }
            ?: manifest.optJSONObject("page_action")?.optString("default_popup")?.takeIf { it.isNotBlank() }
            ?: return null

        val baseUrl = extension.metaData.baseUrl ?: return null
        val cleanRel = popupPath.trim().removePrefix("/")
        return if (baseUrl.endsWith("/")) "$baseUrl$cleanRel" else "$baseUrl/$cleanRel"
    }

    fun resolveExtensionOptionsUrl(extension: WebExtension): String? {
        val metaUrl = extension.metaData.optionsPageUrl
        if (!metaUrl.isNullOrBlank()) return metaUrl

        val manifest = getManifest(extension) ?: return null
        val optionsPath = manifest.optJSONObject("options_ui")?.optString("page")?.takeIf { it.isNotBlank() }
            ?: manifest.optString("options_page").takeIf { it.isNotBlank() }
            ?: return null

        val baseUrl = extension.metaData.baseUrl ?: return null
        val cleanRel = optionsPath.trim().removePrefix("/")
        return if (baseUrl.endsWith("/")) "$baseUrl$cleanRel" else "$baseUrl/$cleanRel"
    }


    /**
     * Installs a `.xpi` file the user opened from *outside* the browser - tapped in Downloads,
     * a file manager, shared from another app, etc. The incoming [uri] is almost always a
     * `content://` Uri whose read permission is only granted for the lifetime of the original
     * VIEW/SEND intent; GeckoView's native (Necko) downloader has no access to that grant and
     * can't resolve arbitrary content providers, so the bytes are copied into the app's own
     * cache directory first and a plain `file://` path (which GeckoView can always read) is
     * installed instead. This is what makes `.xpi` files "just work" natively from anywhere on
     * the device, not only from https:// download links followed inside Petal itself.
     */
    @JvmStatic
    @JvmOverloads
    fun installFromContentUri(context: Context, uri: Uri, onResult: (success: Boolean, message: String?) -> Unit = { _, _ -> }) {
        val appCtx = context.applicationContext
        attach(appCtx)
        _busy.value = true
        _lastError.value = null
        try {
            val resolver = appCtx.contentResolver
            var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "extension.xpi"
            var mimeType: String? = null

            if (uri.scheme.equals("content", ignoreCase = true)) {
                try {
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx != -1) cursor.getString(nameIdx)?.let { displayName = it }
                            val typeIdx = cursor.getColumnIndex("mime_type")
                            if (typeIdx != -1) cursor.getString(typeIdx)?.let { mimeType = it }
                        }
                    }
                } catch (ignored: Exception) {}
                mimeType = mimeType ?: resolver.getType(uri)
            }

            val cacheDir = File(appCtx.cacheDir, "extension-installs").apply { mkdirs() }
            val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").let {
                if (it.endsWith(".xpi", ignoreCase = true)) it else "$it.xpi"
            }
            val destFile = File(cacheDir, "install_${System.currentTimeMillis()}_$safeName")

            val input = if (uri.scheme.equals("file", ignoreCase = true)) {
                java.io.FileInputStream(File(requireNotNull(uri.path)))
            } else {
                resolver.openInputStream(uri)
            }
            if (input == null) {
                _busy.value = false
                val message = "Petal couldn't access that file."
                _lastError.value = message
                onResult(false, message)
                return
            }

            input.use { stream ->
                destFile.outputStream().use { output -> stream.copyTo(output) }
            }
            try { destFile.setReadable(true, false) } catch (_: Exception) {}

            // Validate package format: A valid WebExtension XPI is a ZIP archive containing manifest.json.
            // Also accept if name, URI path, or mime-type explicitly declared xpi.
            val isKnownXpiZip = try {
                java.util.zip.ZipFile(destFile).use { zip -> zip.getEntry("manifest.json") != null }
            } catch (_: Exception) {
                false
            }

            val isXpi = isKnownXpiZip ||
                displayName.endsWith(".xpi", ignoreCase = true) ||
                uri.path?.endsWith(".xpi", ignoreCase = true) == true ||
                uri.lastPathSegment?.endsWith(".xpi", ignoreCase = true) == true ||
                mimeType.equals("application/x-xpinstall", ignoreCase = true)

            if (!isXpi) {
                try { destFile.delete() } catch (_: Exception) {}
                val message = "Please select a valid Firefox extension file (.xpi)."
                _busy.value = false
                _lastError.value = message
                onResult(false, message)
                return
            }

            // GeckoView explicitly distinguishes local-file installation from an add-on
            // manager/remote installation. Use the correct method so imported .xpi files
            // follow the native local-package path.
            val fileUri = Uri.fromFile(destFile.canonicalFile).toString()
            install(
                fileUri,
                WebExtensionController.INSTALLATION_METHOD_FROM_FILE
            ) { success, message ->
                try { destFile.delete() } catch (ignored: Exception) {}
                onResult(success, message)
            }
        } catch (e: Exception) {
            _busy.value = false
            val message = "Couldn't read that extension file."
            _lastError.value = message
            Log.w(TAG, "installFromContentUri failed", e)
            onResult(false, message)
        }
    }

    /** Installs a `.xpi` from any https URL (an AMO listing's download link, or a direct file). */
    fun install(uri: String, onResult: (success: Boolean, message: String?) -> Unit = { _, _ -> }) {
        install(uri, WebExtensionController.INSTALLATION_METHOD_MANAGER, onResult)
    }

    private fun install(
        uri: String,
        installationMethod: String,
        onResult: (success: Boolean, message: String?) -> Unit = { _, _ -> }
    ) {
        val installUri = normalizeInstallUri(uri)
        if (installUri == null) {
            val message = "Use a secure .xpi download link or an addons.mozilla.org add-on page."
            _lastError.value = message
            onResult(false, message)
            return
        }
        val ctx = appContext ?: return
        attach(ctx)
        val controller = PetalGeckoRuntime.getOrCreate(ctx).webExtensionController
        _busy.value = true
        _lastError.value = null
        controller.install(installUri, installationMethod)
            .accept({ extension ->
                _busy.value = false
                if (extension != null) {
                    attachActionDelegate(extension)
                }
                refresh()
                onResult(true, extension?.metaData?.name ?: "Extension installed")
            }, { throwable ->
                // If installation with INSTALLATION_METHOD_FROM_FILE failed on GeckoView,
                // retry once with INSTALLATION_METHOD_MANAGER as fallback for local package compatibility
                if (installationMethod == WebExtensionController.INSTALLATION_METHOD_FROM_FILE &&
                    (throwable !is WebExtension.InstallException ||
                     throwable.code == WebExtension.InstallException.ErrorCodes.ERROR_FILE_ACCESS)
                ) {
                    Log.i(TAG, "Retrying local XPI install with INSTALLATION_METHOD_MANAGER fallback")
                    controller.install(installUri, WebExtensionController.INSTALLATION_METHOD_MANAGER)
                        .accept({ extension ->
                            _busy.value = false
                            if (extension != null) attachActionDelegate(extension)
                            refresh()
                            onResult(true, extension?.metaData?.name ?: "Extension installed")
                        }, { retryThrowable ->
                            _busy.value = false
                            val message = if (retryThrowable is WebExtension.InstallException) {
                                describeInstallError(retryThrowable)
                            } else {
                                retryThrowable?.message ?: "Installation failed."
                            }
                            _lastError.value = message
                            onResult(false, message)
                        })
                    return@accept
                }
                _busy.value = false
                val message = if (throwable is WebExtension.InstallException) {
                    describeInstallError(throwable)
                } else {
                    throwable?.message ?: "Installation failed."
                }
                _lastError.value = message
                onResult(false, message)
            })
    }

    fun uninstall(extension: WebExtension, onResult: (Boolean) -> Unit = {}) {
        val ctx = appContext ?: return
        val controller = PetalGeckoRuntime.getOrCreate(ctx).webExtensionController
        controller.uninstall(extension)
            .accept({
                refresh()
                onResult(true)
            }, {
                onResult(false)
            })
    }

    fun setEnabled(extension: WebExtension, enabled: Boolean) {
        val ctx = appContext ?: return
        val controller = PetalGeckoRuntime.getOrCreate(ctx).webExtensionController
        val op = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        op.accept({ refresh() }, { refresh() })
    }

    fun setAllowedInPrivateBrowsing(extension: WebExtension, allowed: Boolean) {
        val ctx = appContext ?: return
        val controller = PetalGeckoRuntime.getOrCreate(ctx).webExtensionController
        controller.setAllowedInPrivateBrowsing(extension, allowed)
            .accept({ refresh() }, { refresh() })
    }

    fun refresh() {
        val ctx = appContext ?: return
        val controller = PetalGeckoRuntime.getOrCreate(ctx).webExtensionController
        controller.list().accept({ list ->
            list?.forEach { ext ->
                attachActionDelegate(ext)
            }
            val items = (list ?: emptyList()).map { ext -> toInstalled(ext) }
            _extensions.value = items
            synchronized(actionByExtensionId) {
                actionByExtensionId.keys.retainAll(items.map { it.id }.toSet())
            }
            // Load icons asynchronously and patch them in once ready (best-effort, 96px).
            list?.forEach { ext ->
                try {
                    ext.metaData.icon.getBitmap(96).accept({ bmp ->
                        if (bmp != null) {
                            _extensions.value = _extensions.value.map {
                                if (it.id == ext.id) it.copy(icon = bmp) else it
                            }
                        }
                    }, { /* icon optional */ })

                } catch (ignored: Exception) {}
            }
        }, {
            Log.w(TAG, "Failed to list extensions", it)
        })
    }

    private fun extensionHasPopup(extension: WebExtension): Boolean {
        val manifest = getManifest(extension) ?: return false
        val popup = manifest.optJSONObject("browser_action")?.optString("default_popup")?.takeIf { it.isNotBlank() }
            ?: manifest.optJSONObject("action")?.optString("default_popup")?.takeIf { it.isNotBlank() }
            ?: manifest.optJSONObject("page_action")?.optString("default_popup")?.takeIf { it.isNotBlank() }
        return popup != null
    }

    private fun normalizeInstallUri(value: String): String? {
        val parsed = try { Uri.parse(value.trim()) } catch (_: Exception) { return null }

        // A local `.xpi` package already resolved to a real file path (see
        // installFromContentUri, used when the user opens a downloaded/shared .xpi from
        // outside the browser) - GeckoView's installer reads these directly. Never rewrite
        // this into an AMO url.
        if (parsed.scheme.equals("file", ignoreCase = true)) {
            return parsed.toString()
        }

        if (!parsed.scheme.equals("https", ignoreCase = true)) return null

        // Already a real, resolved package link (e.g. GeckoView's own onExternalResponse
        // handed us the final "/firefox/downloads/file/<id>/name.xpi" URL after following
        // AMO's redirect chain, or the user gave a direct .xpi link). Never rewrite this -
        // it is already correct, and reconstructing a "slug" out of its path segments
        // (previous bug: grabbed the numeric file id and built a dead /android/downloads/
        // URL from it) just replaces a working link with a broken one.
        if (parsed.path?.endsWith(".xpi", ignoreCase = true) == true) {
            return parsed.toString()
        }

        val host = parsed.host?.lowercase() ?: return null
        if (host != "addons.mozilla.org" && host != "www.addons.mozilla.org") return null

        // An AMO *listing* page (.../addon/<slug>/ or .../android/addon/<slug>/) - resolve
        // it to the permanent "latest signed build" redirect. This is always the
        // "/firefox/downloads/latest/" path: AMO has no "/android/downloads/" endpoint:
        // Firefox and Firefox for Android share one add-on catalog, and only the listing
        // pages (not downloads) have a separate "/android/" URL variant.
        val segments = parsed.pathSegments
        val addonIndex = segments.indexOf("addon")
        val slug = segments.getOrNull(addonIndex + 1)?.takeIf { it.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_-]*")) }
        return if (addonIndex >= 0 && slug != null) {
            "https://addons.mozilla.org/firefox/downloads/latest/$slug/latest.xpi"
        } else null
    }

    private fun toInstalled(ext: WebExtension): InstalledExtension {
        val meta = ext.metaData
        return InstalledExtension(
            id = ext.id,
            name = meta.name ?: ext.id,
            description = meta.description ?: "",
            version = meta.version,
            enabled = meta.enabled,
            temporary = meta.temporary,
            allowedInPrivateBrowsing = meta.allowedInPrivateBrowsing,
            homepageUrl = meta.homepageUrl,
            amoListingUrl = meta.amoListingUrl,
            optionsPageUrl = resolveExtensionOptionsUrl(ext),
            supportsPopup = resolveExtensionPopupUrl(ext) != null || extensionHasPopup(ext),
            icon = null,
            raw = ext
        )
    }
}
