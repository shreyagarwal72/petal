package com.petal.browser.view

import com.petal.browser.view.PetalToast;
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.graphics.Region
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.petal.browser.R
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.BrowserController
import com.petal.browser.database.FaviconHelper
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import com.petal.browser.media.PetalMediaBridge
import com.petal.browser.pwa.PetalPwaManager
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.HelperUnit
import com.petal.browser.unit.TabThumbnailCache
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.WebResponse
import org.mozilla.geckoview.MediaSession
import kotlinx.coroutines.launch
import java.util.function.Consumer

/**
 * PetalGeckoView
 * ─────────────────────────────────────────────────────────────────────────
 * Standalone, top-level GeckoView tab controller for Petal Browser.
 * Encapsulates [org.mozilla.geckoview.GeckoView] and [org.mozilla.geckoview.GeckoSession],
 * implementing [AlbumController] and [NestedScrollingChild3] to preserve all Material 3 browser
 * UI features, pull-to-refresh arbitration, predictive back edge gestures, and thumbnail caching.
 */
class PetalGeckoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    adoptedSession: GeckoSession? = null,
    initialIncognito: Boolean = false,
    val engineSession: mozilla.components.concept.engine.EngineSession? = null
) : FrameLayout(context, attrs, defStyleAttr), AlbumController, NestedScrollingChild3 {

    companion object {
        /** Max device-pixel scroll offset still considered "at top". */
        const val PAGE_TOP_TOLERANCE_PX = 3

        private const val TAG = "PetalGeckoView"

        /**
         * Returns the GeckoSession that backs a Mozilla GeckoEngineSession, or null if unavailable.
         *
         * GeckoEngineSession.geckoSession is `internal` to Mozilla's module, so Kotlin 2.4 no longer
         * lets us reference it directly. There is no public accessor, so it is read reflectively.
         * If Mozilla renames or removes the field this logs a warning and returns null, and the caller
         * falls back to creating its own GeckoSession.
         */
        private fun extractGeckoSession(
            engineSession: mozilla.components.concept.engine.EngineSession?
        ): GeckoSession? {
            if (engineSession !is mozilla.components.browser.engine.gecko.GeckoEngineSession) return null
            return try {
                // Kotlin compiles an `internal var` getter as `getGeckoSession$<module>`, so try both.
                val cls = engineSession.javaClass
                val getter = cls.methods.firstOrNull {
                    it.parameterCount == 0 && it.name.startsWith("getGeckoSession")
                }
                val fromGetter = getter?.let {
                    it.isAccessible = true
                    it.invoke(engineSession) as? GeckoSession
                }
                fromGetter ?: cls.getDeclaredField("geckoSession").let {
                    it.isAccessible = true
                    it.get(engineSession) as? GeckoSession
                }
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "Could not read GeckoEngineSession.geckoSession: ${t.message}")
                null
            }
        }

        @JvmField
        var globalBrowserController: BrowserController? = null

        @JvmStatic
        fun getBrowserController(): BrowserController? = globalBrowserController

        @JvmStatic
        fun getProfile(context: Context? = null): String {
            val ctx = context ?: com.petal.browser.PetalApplication.instance
            if (ctx != null) {
                val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
                return sp.getString("profile", "profileStandard") ?: "profileStandard"
            }
            return "profileStandard"
        }

        @JvmStatic
        fun getDerivedDesktopUserAgent(context: Context): String {
            return "Mozilla/5.0 (X11; Linux x86_64; rv:155.0) Gecko/20100101 Firefox/155.0"
        }
    }

    interface OnScrollChangeListener {
        fun onScrollDown()
        fun onScrollUp()
        fun onScrollPositionChanged(scrollY: Int, contentHeight: Int) {}
    }

    private val childHelper: NestedScrollingChildHelper = NestedScrollingChildHelper(this)
    val geckoView: GeckoView = SafeGeckoView(context)
    // The session mode is immutable after GeckoSession construction. Creating a normal
    // session and switching to private mode later is too late and can leak normal-profile
    // state into an Incognito tab, especially during cold startup.
    // If an engineSession (GeckoEngineSession) is provided, adopt its underlying GeckoSession.
    var session: GeckoSession = adoptedSession
        ?: extractGeckoSession(engineSession)
        ?: GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(initialIncognito)
                .build()
        )

    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var isIncognito: Boolean = initialIncognito
    private var isForegroundTab: Boolean = false
    private var isStopped: Boolean = false

    private var tabId: String = "tab_${System.currentTimeMillis()}_${Math.abs(hashCode())}"
    private var tabGroupId: String? = null
    private var tabGroupTitle: String? = null
    private var predecessor: AlbumController? = null
    private val album: AdapterTabs = AdapterTabs(context, this, globalBrowserController)

    private var currentUrl: String = "about:blank"
    /**
     * The last real (non-blank, non-home) URL that was explicitly requested via [loadUrl].
     * Unlike [currentUrl], this is never reset to "about:blank" — it preserves the original
     * intent even while the engine is still loading. Used by [PetalTabSessionManager] to
     * recover the correct URL instead of persisting a transient "about:blank".
     */
    @JvmField var persistentUrl: String = ""
    private var currentTitle: String = "Petal Start"
    private var currentProgress: Int = 0
    private var canGoBackVal: Boolean = false
    private var canGoForwardVal: Boolean = false
    private var lastRecordedHistoryUrl: String? = null

    private var favicon: Bitmap? = null
    var currentSecurityInfo: GeckoSession.ProgressDelegate.SecurityInformation? = null
        private set

    private var mediaBridge: PetalMediaBridge? = null
    private var pwaManager: PetalPwaManager? = null
    private var onScrollChangeListener: OnScrollChangeListener? = null
    var onPageProgressChanged: ((Int) -> Unit)? = null
    var onPageTitleChanged: ((String) -> Unit)? = null
    var onPageSecurityChanged: ((Boolean) -> Unit)? = null
    private var lastScrollHapticY: Int = 0
    private var currentScrollY: Int = 0
    private var currentScrollX: Int = 0
    private var lastCrashRecoveryTime: Long = 0L
    private var crashRecoveryCount: Int = 0
    private var sessionInitializationStarted: Boolean = false

    init {
        isNestedScrollingEnabled = true
        addView(
            geckoView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        com.petal.browser.media.sniffer.PetalMediaSniffer.setActivePage(tabId, currentUrl)
        if (PetalGeckoRuntime.isGeckoAvailable(context)) {
            initGeckoSession()
        }
        album.setBrowserController(globalBrowserController)
        this.pwaManager = PetalPwaManager(context, this, null)
    }

    @MainThread
    private fun initGeckoSession() {
        if (sessionInitializationStarted) {
            // A session may be re-initialized after popup adoption or process recovery.
            // Never start a second open/attachment sequence for the same GeckoSession.
            return
        }
        sessionInitializationStarted = true

        val runtime = PetalGeckoRuntime.getOrCreate(context.applicationContext)
        // GeckoSession.open() must only be invoked for a newly-created, unopened session.
        // Keep the open/attach operation serialized on the main thread and make the
        // session's private-mode choice before open() (see constructor above).
        if (!session.isOpen) {
            session.open(runtime)
        }
        geckoView.setSession(session)
        session.setActive(isForegroundTab)
        com.petal.browser.extensions.PetalExtensionManager.attachSession(session)
        // Do not mutate GeckoView's compositor child hierarchy during session attachment.
        // Edge-gesture handling is performed lazily from dispatchTouchEvent().

        // Progress & Loading Delegate
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                isStopped = false
                currentScrollY = 0
                currentScrollX = 0
                com.petal.browser.media.sniffer.PetalMediaSniffer.clear()
                currentUrl = url
                applyGeckoBlockingPolicy(url)

                val httpsOnly = sp.getBoolean("sp_https_only", sp.getBoolean("profileStandard_httpsOnly", true))
                if (httpsOnly && url.startsWith("http://", ignoreCase = true)) {
                    val secureUrl = "https://" + url.substring(7)
                    session.loadUri(secureUrl)
                    return
                }

                com.petal.browser.media.sniffer.PetalMediaSniffer.setActivePage(tabId, url)
                album.setAlbumTitle(currentTitle, url)
                updateProgress(10)
                if (engineSession != null) {
                    com.petal.browser.engine.gecko.PetalEngineStore.updateLoadingState(context, tabId, true)
                    com.petal.browser.engine.gecko.PetalEngineStore.updateProgress(context, tabId, 10)
                }

                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        // Do not touch GeckoView's compositor child hierarchy during
                        // navigation. Login/OAuth redirects can recreate that hierarchy
                        // concurrently and walking it here can trigger a native crash.
                        act.updateOmniBox()
                        act.updateAddressBar()
                        act.updatePersistentBottomNav()
                    }
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                hideLoadingSkeleton()
                isStopped = true
                updateProgress(BrowserUnit.LOADING_STOPPED)
                updatePreviewCache()
                if (engineSession != null) {
                    com.petal.browser.engine.gecko.PetalEngineStore.updateLoadingState(context, tabId, false)
                    com.petal.browser.engine.gecko.PetalEngineStore.updateProgress(context, tabId, 100)
                }

                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.resetRefreshState()
                    act.runOnUiThread {
                        act.updateOmniBox()
                        act.updateAddressBar()
                        act.updatePersistentBottomNav()
                    }
                }

                pwaManager?.detectPwaManifest()
                injectClientPrivacyProtections()
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                currentProgress = progress
                updateProgress(progress)
                onPageProgressChanged?.invoke(progress)
                if (engineSession != null) {
                    com.petal.browser.engine.gecko.PetalEngineStore.updateProgress(context, tabId, progress)
                }
                if (progress >= 30) {
                    hideLoadingSkeleton()
                }
            }

            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                currentSecurityInfo = securityInfo
                onPageSecurityChanged?.invoke(securityInfo.isSecure)
            }
        }

        // Permission Delegate (Handles ContentPermission, WebAuthn, Device Permissions)
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback
            ) {
                val activity = getHostActivity()
                if (activity == null) {
                    callback.reject()
                    return
                }
                val permissionType = if (!video.isNullOrEmpty()) {
                    com.petal.browser.ui.components.PetalPermissionType.CAMERA
                } else {
                    com.petal.browser.ui.components.PetalPermissionType.MICROPHONE
                }
                val host = try { android.net.Uri.parse(uri).host ?: uri } catch (_: Exception) { uri }
                val prefKey = "perm_${permissionType.name.lowercase()}_$host"
                val savedRule = sp.getString(prefKey, null)
                if ("allow" == savedRule) {
                    if (!video.isNullOrEmpty()) com.petal.browser.unit.HelperUnit.grantPermissionsCamera(activity)
                    if (!audio.isNullOrEmpty()) com.petal.browser.unit.HelperUnit.grantPermissionsMic(activity)
                    callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                    return
                } else if ("block" == savedRule) {
                    callback.reject()
                    return
                }

                activity.runOnUiThread {
                    com.petal.browser.ui.components.PetalPermissionDialogBridge.showPermissionPrompt(
                        activity,
                        permissionType,
                        uri,
                        { remember ->
                            if (remember) sp.edit().putString(prefKey, "allow").apply()
                            if (!video.isNullOrEmpty()) com.petal.browser.unit.HelperUnit.grantPermissionsCamera(activity)
                            if (!audio.isNullOrEmpty()) com.petal.browser.unit.HelperUnit.grantPermissionsMic(activity)
                            callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                        },
                        { remember ->
                            if (remember) sp.edit().putString(prefKey, "block").apply()
                            callback.reject()
                        }
                    )
                }
            }

            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission
            ): GeckoResult<Int>? {
                val result = GeckoResult<Int>()
                val activity = getHostActivity()
                if (activity == null) {
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                    return result
                }

                if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION) {
                    val host = try { android.net.Uri.parse(perm.uri).host ?: perm.uri } catch (_: Exception) { perm.uri }
                    val prefKey = "perm_location_$host"
                    val savedRule = sp.getString(prefKey, null)
                    if ("allow" == savedRule) {
                        com.petal.browser.unit.HelperUnit.grantPermissionsLoc(activity)
                        result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                        return result
                    } else if ("block" == savedRule) {
                        result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                        return result
                    }

                    activity.runOnUiThread {
                        com.petal.browser.ui.components.PetalPermissionDialogBridge.showPermissionPrompt(
                            activity,
                            com.petal.browser.ui.components.PetalPermissionType.LOCATION,
                            perm.uri,
                            { remember ->
                                if (remember) sp.edit().putString(prefKey, "allow").apply()
                                com.petal.browser.unit.HelperUnit.grantPermissionsLoc(activity)
                                result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                            },
                            { remember ->
                                if (remember) sp.edit().putString(prefKey, "block").apply()
                                result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                            }
                        )
                    }
                } else {
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                }
                return result
            }
        }

        // Navigation Delegate
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                canGoBackVal = canGoBack
                if (engineSession != null) {
                    com.petal.browser.engine.gecko.PetalEngineStore.updateNavigationState(context, tabId, canGoBackVal, canGoForwardVal)
                }
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread { act.updateBackCallbackState() }
                }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                canGoForwardVal = canGoForward
                if (engineSession != null) {
                    com.petal.browser.engine.gecko.PetalEngineStore.updateNavigationState(context, tabId, canGoBackVal, canGoForwardVal)
                }
            }

            // onPageStart only fires with the URL that was originally requested. If the
            // server responds with a redirect, or the page later calls history.pushState/
            // replaceState (every SPA route change - Google included), the actual displayed
            // location moves on but onPageStart never fires again, so currentUrl (and the
            // address bar bound to it) was left showing the stale, pre-redirect/pre-navigation
            // URL indefinitely. onLocationChange is GeckoView's dedicated callback for exactly
            // this - it fires whenever the visible top-level location changes, redirect or not.
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (url.isNullOrBlank() || url.equals(currentUrl, ignoreCase = true)) return
                currentUrl = url
                // Update persistentUrl for navigations (redirects, SPA pushState) so the
                // save-on-pause always has the final visible URL, not the initial request URL.
                if (!url.equals("about:blank", ignoreCase = true) &&
                    !url.startsWith("about:") &&
                    !url.startsWith("moz-extension://")) {
                    persistentUrl = url
                }
                com.petal.browser.media.sniffer.PetalMediaSniffer.setActivePage(tabId, url)
                album.setAlbumTitle(currentTitle, url)
                if (engineSession != null) {
                    com.petal.browser.engine.gecko.PetalEngineStore.updateUrlAndTitle(context, tabId, url, currentTitle)
                }
                if (!isIncognito) {
                    com.petal.browser.browser.PetalPlacesStorage.recordVisit(context, url, currentTitle)
                }

                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        // Handle Firefox / Mozilla OAuth redirect callback completion
                        val fxManager = com.petal.browser.account.mozilla.FxAccountManager.getInstance()
                        if (fxManager.isRedirectUrl(url)) {
                            val authCode = fxManager.extractQueryParam(url, "code")
                            if (!authCode.isNullOrBlank()) {
                                val emailParam = fxManager.extractQueryParam(url, "email") ?: "user@mozilla.org"
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    fxManager.exchangeCodeForTokens(authCode, emailParam)
                                    com.petal.browser.account.mozilla.PetalMozillaSyncManager.getInstance().syncNow(act)
                                    com.petal.browser.view.PetalToast.show(act, "Signed in with Firefox Account. Syncing data...")
                                    act.removeAlbum(this@PetalGeckoView)
                                }
                                return@runOnUiThread
                            }
                        }

                        // Location changes include login/OAuth redirects. Keep this callback
                        // UI-only; Gecko owns its compositor child lifecycle.
                        act.updateOmniBox()
                        act.updateAddressBar()
                        act.updatePersistentBottomNav()
                    }
                }
            }

            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                if (BrowserUnit.isHomePage(uri)) {
                    if (uri.equals("about:blank", ignoreCase = true)) {
                        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // Handle Android deep links and external app URI schemes (intent://, market://, tel:, etc.)
                if (handleDeepLinkOrCustomScheme(uri)) {
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // If "Open Redirect Links in Background" is enabled, spawn cross-origin redirects/links in background tabs
                val openRedirectsInBackground = sp.getBoolean("sp_open_redirects_in_background", false)
                if (openRedirectsInBackground && request.target != GeckoSession.NavigationDelegate.TARGET_WINDOW_CURRENT) {
                    val act = getHostActivity()
                    if (act is com.petal.browser.activity.BrowserActivity) {
                        val currentHost = try { android.net.Uri.parse(currentUrl).host } catch (e: Exception) { null }
                        val targetHost = try { android.net.Uri.parse(uri).host } catch (e: Exception) { null }
                        if (!currentHost.isNullOrEmpty() && !targetHost.isNullOrEmpty() && !currentHost.equals(targetHost, ignoreCase = true)) {
                            act.runOnUiThread {
                                act.addAlbum(null, uri, false, isIncognito)
                            }
                            return GeckoResult.fromValue(AllowOrDeny.DENY)
                        }
                    }
                }

                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val act = getHostActivity() as? com.petal.browser.activity.BrowserActivity ?: return null
                if (act.isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && act.isDestroyed)) return null

                // GeckoView's onNewSession contract: return a brand-new, UNOPENED GeckoSession.
                // GeckoView itself will call open() on it. Once open, we adopt it into a new
                // popup tab via adoptPopupSession() — which reuses the already-open session
                // without calling open() a second time (which would crash).
                val popupSession = GeckoSession(GeckoSessionSettings.Builder()
                    .usePrivateMode(this@PetalGeckoView.isIncognito)
                    .build())
                val result = GeckoResult<GeckoSession>()
                result.complete(popupSession)

                // Gecko opens the returned session asynchronously. Never adopt it until
                // that open has completed: initGeckoSession() would otherwise open it first,
                // and Gecko would then fail its own pending open with "Must use an unopened
                // GeckoSession instance". This is most visible on OAuth/login popups.
                val adoptWhenOpened = object : Runnable {
                    private var attempts = 0

                    override fun run() {
                        try {
                            if (act.isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && act.isDestroyed)) {
                                if (popupSession.isOpen) popupSession.close()
                                return
                            }
                            if (!popupSession.isOpen) {
                                if (++attempts <= 120) {
                                    postDelayed(this, 16L)
                                } else {
                                    android.util.Log.w(TAG, "Popup GeckoSession did not open in time; skipping adoption")
                                }
                                return
                            }
                            act.adoptPopupGeckoSession(popupSession, uri, isIncognito)
                        } catch (t: Throwable) {
                            android.util.Log.e(TAG, "Failed to create login popup tab", t)
                            if (popupSession.isOpen) popupSession.close()
                        }
                    }
                }
                act.runOnUiThread(adoptWhenOpened)
                return result
            }
        }

        // Let Gecko own visit detection. This covers redirects, reloads and SPA
        // navigations consistently instead of inferring visits from lifecycle callbacks.
        session.historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onVisited(
                session: GeckoSession,
                url: String,
                lastVisitedURL: String?,
                flags: Int
            ): GeckoResult<Boolean> {
                recordHistoryVisit(url, currentTitle)
                return GeckoResult.fromValue(true)
            }
        }

        // Content Delegate
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                title?.let {
                    currentTitle = it
                    album.setAlbumTitle(it, currentUrl)
                    onPageTitleChanged?.invoke(it)
                    if (engineSession != null) {
                        com.petal.browser.engine.gecko.PetalEngineStore.updateUrlAndTitle(context, tabId, currentUrl, it)
                    }
                    val act = getHostActivity()
                    if (act is com.petal.browser.activity.BrowserActivity) {
                        act.runOnUiThread {
                            act.updateOmniBox()
                        }
                    }
                    if (it.isNotBlank() && it != "Petal Start" && it != "Petal Home" && currentUrl.isNotBlank()) {
                    }
                }
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        act.setCustomFullscreen(fullScreen)
                        try {
                            val bnc = act.findViewById<View>(R.id.bottom_nav_container)
                            val bnv = act.findViewById<View>(R.id.bottom_nav_compose)
                            val addressBar = act.findViewById<View>(R.id.compose_address_bar)
                            if (fullScreen) {
                                bnc?.visibility = View.GONE
                                bnv?.visibility = View.GONE
                                addressBar?.visibility = View.GONE
                            } else {
                                // Restore via the activity so overlay/surface rules are respected.
                                act.updatePersistentBottomNav()
                                act.applyAddressBarPosition()
                            }
                        } catch (ignored: Exception) {}
                    }
                }
            }

            override fun onCloseRequest(session: GeckoSession) {
                // When an OAuth or web login window/tab invokes window.close() after completion,
                // safely close this popup tab and return focus to the predecessor tab.
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        try {
                            act.removeAlbum(this@PetalGeckoView)
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "Failed to close tab on window.close() request", e)
                        }
                    }
                }
            }

            override fun onFocusRequest(session: GeckoSession) {
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        try {
                            act.showAlbum(this@PetalGeckoView)
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "Failed to focus tab on onFocusRequest", e)
                        }
                    }
                }
            }

            override fun onCrash(session: GeckoSession) {
                // Per GeckoView docs: once the content process crashes, the session is
                // permanently closed/unusable. Without re-opening and reloading here, the
                // tab is left showing the last URL with a blank page indefinitely - this
                // is the root cause of "page loads but shows blank" that a manual reload
                // happens to fix (reload re-triggers loadUrl, masking the real problem).
                android.util.Log.w(TAG, "GeckoSession content process crashed for $currentUrl - reopening session")
                com.petal.browser.logger.PetalAppLogger.e(TAG, "GeckoSession content process crashed for $currentUrl")
                com.petal.browser.logger.PetalAppLogger.recordProcessCrash(
                    context,
                    TAG,
                    "GeckoView Content Process Crash (SIGSEGV / Native Termination)",
                    "Active URL: $currentUrl | Title: $currentTitle"
                )
                recoverCrashedSession()
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val responseUrl = response.uri
                if (responseUrl.isNullOrBlank()) return
                val act = getHostActivity() ?: return
                val parsed = try { android.net.Uri.parse(responseUrl) } catch (_: Exception) { null }
                val isXpiUrl = parsed?.path?.endsWith(".xpi", ignoreCase = true) == true ||
                    response.headers["content-type"]?.contains("application/x-xpinstall", ignoreCase = true) == true ||
                    (parsed?.host?.contains("addons.mozilla.org", ignoreCase = true) == true && responseUrl.contains(".xpi", ignoreCase = true))
                if (isXpiUrl) {
                    act.runOnUiThread {
                        com.petal.browser.extensions.PetalExtensionManager.install(responseUrl) { success, message ->
                            com.petal.browser.view.PetalToast.show(act, message ?: if (success) "Extension installed" else "Extension installation failed")
                        }
                    }
                    return
                }
                // GeckoView's WebResponse carries the server's real Content-Disposition and
                // Content-Type headers - previously these were discarded (passed as null),
                // so every download fell back to guessing a name purely from the URL path.
                // For signed CDN links, dynamic endpoints, or any URL without a clean
                // "name.ext" tail, that guess had nothing to go on and produced a wrong
                // name and/or wrong extension. Reading the real headers here fixes that
                // for every download that goes through the GeckoView engine.
                val headers = response.headers
                val contentDisposition = headers?.entries?.firstOrNull {
                    it.key.equals("Content-Disposition", ignoreCase = true)
                }?.value
                val mimeType = headers?.entries?.firstOrNull {
                    it.key.equals("Content-Type", ignoreCase = true)
                }?.value
                val fileName = HelperUnit.resolveFileName(responseUrl, contentDisposition, mimeType)
                val contentLength = headers?.entries?.firstOrNull {
                    it.key.equals("Content-Length", ignoreCase = true)
                }?.value?.toLongOrNull() ?: 0L
                act.runOnUiThread {
                    com.petal.browser.ui.components.PetalDownloadDialogBridge.showDownloadConfirmation(
                        act, responseUrl, contentDisposition, mimeType, contentLength
                    ) { confirmedName ->
                        // GeckoEngine is the owner of web downloads. Do not hand this event to
                        // BrowserUnit's legacy WebView/raw-download path: doing so loses Gecko's
                        // response metadata and creates a second, differently-configured path.
                        // Keep the Gecko response headers and feed the unified Fetch2 backend.
                        com.petal.browser.compose.downloads.PetalFetchDownloadBridge.enqueueGeckoDownload(
                            context = act,
                            url = responseUrl,
                            fileName = confirmedName.ifBlank { fileName },
                            mimeType = mimeType,
                            responseHeaders = headers
                        )
                    }
                }
            }
            override fun onKill(session: GeckoSession) {
                // The OS/Gecko killed the content process (almost always a routine low-memory
                // kill under system memory pressure, not an actual app fault). The session is
                // reopened transparently below, so this is intentionally NOT reported as a
                // crash - recordProcessCrash() is deliberately not called here so it can no
                // longer trigger the crash-reporting dialog on next launch. A plain debug log
                // line is kept for local troubleshooting only.
                android.util.Log.w(TAG, "GeckoSession content process killed for $currentUrl (low memory) - reopening session")
                recoverCrashedSession()
            }

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement
            ) {
                val act = getHostActivity() ?: return
                if (act !is com.petal.browser.activity.BrowserActivity) return

                val linkUri = element.linkUri
                val srcUri = element.srcUri
                val elemType = element.type

                act.runOnUiThread {
                    when {
                        elemType == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE && !srcUri.isNullOrEmpty() -> {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showImageContextMenu(act, srcUri)
                        }
                        elemType == GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO && !srcUri.isNullOrEmpty() -> {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showVideoContextMenu(act, srcUri)
                        }
                        elemType == GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO && !srcUri.isNullOrEmpty() -> {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showAudioContextMenu(act, srcUri)
                        }
                        !linkUri.isNullOrEmpty() -> {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showLinkContextMenu(act, linkUri)
                        }
                        !srcUri.isNullOrEmpty() -> {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showImageContextMenu(act, srcUri)
                        }
                    }
                }
            }
        }

        // Official Mozilla Firefox ContentBlocking Delegate (Enhanced Tracking Protection & AdBlock telemetry)
        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                com.petal.browser.browser.PetalAdBlockEngine.recordBlock(currentUrl)
            }
        }

        // Material 3 Expressive Prompt Delegate (Alerts, Confirms, Prompts, Auth, Choice, Text, Popups)
        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onAlertPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AlertPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val act = getHostActivity() ?: return result
                act.runOnUiThread {
                    com.petal.browser.ui.components.PetalExpressivePromptBridge.showAlert(
                        act,
                        prompt.title ?: act.getString(R.string.app_name),
                        prompt.message ?: ""
                    ) {
                        result.complete(prompt.dismiss())
                    }
                }
                return result
            }

            override fun onButtonPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ButtonPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val act = getHostActivity() ?: return result
                act.runOnUiThread {
                    com.petal.browser.ui.components.PetalExpressivePromptBridge.showConfirm(
                        act,
                        prompt.title ?: act.getString(R.string.app_name),
                        prompt.message ?: "",
                        { result.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE)) },
                        { result.complete(prompt.dismiss()) }
                    )
                }
                return result
            }

            override fun onAuthPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AuthPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val act = getHostActivity() ?: return GeckoResult.fromValue(prompt.dismiss())
                act.runOnUiThread {
                    val isPasswordOnly = (prompt.authOptions.flags and GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0
                    val dialogTitle = prompt.title ?: prompt.authOptions.uri ?: act.getString(R.string.app_name)
                    com.petal.browser.ui.components.PetalExpressivePromptBridge.showAuth(
                        act,
                        dialogTitle,
                        prompt.message ?: "Sign In",
                        isPasswordOnly,
                        prompt.authOptions.username,
                        { user, pass ->
                            if (isPasswordOnly) {
                                result.complete(prompt.confirm(pass))
                            } else {
                                result.complete(prompt.confirm(user, pass))
                            }
                        },
                        { result.complete(prompt.dismiss()) }
                    )
                }
                return result
            }

            override fun onTextPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.TextPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val act = getHostActivity() ?: return GeckoResult.fromValue(prompt.dismiss())
                act.runOnUiThread {
                    com.petal.browser.ui.components.PetalExpressivePromptBridge.showPrompt(
                        act,
                        prompt.title ?: act.getString(R.string.app_name),
                        prompt.message ?: "",
                        prompt.defaultValue,
                        { value -> result.complete(prompt.confirm(value)) },
                        { result.complete(prompt.dismiss()) }
                    )
                }
                return result
            }

            override fun onChoicePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ChoicePrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                // Safe dismissal avoids unhandled choice/menu prompt exceptions during form submissions
                return GeckoResult.fromValue(prompt.dismiss())
            }

            override fun onPopupPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.PopupPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val blockPopups = sp.getBoolean("sp_block_popups", sp.getBoolean("profileStandard_javascriptPopUp", true))
                return if (blockPopups) {
                    GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
                } else {
                    GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
                }
            }

            override fun onBeforeUnloadPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                // Automatically allow unload during navigation/redirects
                return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
            }

            override fun onRepostConfirmPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
            }

            // Autofill is disabled at the runtime level (loginAutofillEnabled = false),
            // but these are overridden defensively so Gecko never falls through to the
            // default interface behavior if autofill is re-enabled later without this
            // being revisited. Dismissing immediately is safe and crash-free.
            override fun onLoginSave(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSaveOption>
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(request.dismiss())
            }

            override fun onLoginSelect(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSelectOption>
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(request.dismiss())
            }

            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val geckoResult = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val act = getHostActivity()
                if (act !is com.petal.browser.activity.BrowserActivity) {
                    return GeckoResult.fromValue(prompt.dismiss())
                }
                act.runOnUiThread {
                    val isMultiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
                    val mimeTypes = prompt.mimeTypes?.filter { !it.isNullOrBlank() }?.toTypedArray() ?: emptyArray()

                    act.showFileChooser(
                        object : android.webkit.ValueCallback<Array<android.net.Uri>?> {
                            override fun onReceiveValue(value: Array<android.net.Uri>?) {
                                if (value == null || value.isEmpty()) {
                                    if (!prompt.isComplete) {
                                        geckoResult.complete(prompt.dismiss())
                                    }
                                } else if (isMultiple) {
                                    if (!prompt.isComplete) {
                                        geckoResult.complete(prompt.confirm(act, value))
                                    }
                                } else {
                                    if (!prompt.isComplete) {
                                        geckoResult.complete(prompt.confirm(act, value[0]))
                                    }
                                }
                            }
                        },
                        object : android.webkit.WebChromeClient.FileChooserParams() {
                            override fun getMode(): Int = if (isMultiple) MODE_OPEN_MULTIPLE else MODE_OPEN
                            override fun getAcceptTypes(): Array<String> = mimeTypes
                            override fun isCaptureEnabled(): Boolean = prompt.capture != GeckoSession.PromptDelegate.FilePrompt.Capture.NONE
                            override fun getTitle(): CharSequence? = null
                            override fun getFilenameHint(): String? = null
                            override fun createIntent(): android.content.Intent {
                                val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                    if (isMultiple) putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
                                    type = if (mimeTypes.isNotEmpty() && mimeTypes[0].isNotBlank()) mimeTypes[0] else "*/*"
                                    if (mimeTypes.size > 1) {
                                        putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimeTypes)
                                    }
                                }
                                return intent
                            }
                        }
                    )
                }
                return geckoResult
            }

            override fun onCreditCardSave(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.CreditCardSaveOption>
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(request.dismiss())
            }

            override fun onCreditCardSelect(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.CreditCardSelectOption>
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(request.dismiss())
            }

            override fun onAddressSave(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.AddressSaveOption>
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(request.dismiss())
            }

            override fun onAddressSelect(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.AddressSelectOption>
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(request.dismiss())
            }

            override fun onSelectIdentityCredentialProvider(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.IdentityCredential.ProviderSelectorPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(prompt.dismiss())
            }

            override fun onSelectIdentityCredentialAccount(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.IdentityCredential.AccountSelectorPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(prompt.dismiss())
            }

            override fun onShowPrivacyPolicyIdentityCredential(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.IdentityCredential.PrivacyPolicyPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(prompt.dismiss())
            }
        }

        // Scroll Delegate for Tactile Haptics and Address Bar Collapsing
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            private var lastAddressBarScrollY: Int = 0

            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                currentScrollX = scrollX
                currentScrollY = scrollY
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    // Smoothly handle address bar collapsing with a comfortable delta threshold
                    val deltaY = scrollY - lastAddressBarScrollY
                    onScrollChangeListener?.onScrollPositionChanged(scrollY, height)
                    if (Math.abs(deltaY) > 28) {
                        onScrollChangeListener?.let { listener ->
                            if (deltaY > 0) {
                                listener.onScrollDown()
                            } else {
                                listener.onScrollUp()
                            }
                        }
                        lastAddressBarScrollY = scrollY
                    }

                    // Tactile haptics: only check when enabled, with spaced interval
                    if (com.petal.browser.haptics.PetalHapticEngine.isScrollHapticsEnabled(context)) {
                        if (Math.abs(scrollY - lastScrollHapticY) > 64) {
                            lastScrollHapticY = scrollY
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                                .play(com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.40f, 50L)
                        }
                    }
                }
            }
        }

        // Selection & Context Menu Action Delegate (Long-click text selection)
        session.selectionActionDelegate = object : GeckoSession.SelectionActionDelegate {
            override fun onShowActionRequest(
                session: GeckoSession,
                selection: GeckoSession.SelectionActionDelegate.Selection
            ) {
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    val selectedText = selection.text
                    if (!selectedText.isNullOrBlank()) {
                        act.runOnUiThread {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showSelectionContextMenu(act, selectedText)
                        }
                    }
                }
            }
        }

        // Native GeckoView MediaSession Delegate for HTML5 Media & PiP Tracking
        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                mediaBridge?.setActiveGeckoMediaSession(mediaSession)
            }

            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                if (mediaBridge?.activeGeckoMediaSession == mediaSession) {
                    mediaBridge?.setActiveGeckoMediaSession(null)
                }
                com.petal.browser.engine.gecko.PetalEngineStore.deactivateMediaSession(context, tabId)
            }

            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                mediaBridge?.setActiveGeckoMediaSession(mediaSession)
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    val l = mediaBridge?.listener
                    l?.onMediaPlayingStateChanged(true)
                    l?.onMediaPlay(currentTitle, 0L, 0L)
                    com.petal.browser.ui.components.PetalFloatingMediaBridge.updateState(
                        isPlaying = true,
                        title = currentTitle,
                        positionMs = 0L,
                        durationMs = 0L,
                        isMuted = mediaBridge?.isMuted ?: false
                    )
                }
            }

            override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    val l = mediaBridge?.listener
                    l?.onMediaPlayingStateChanged(false)
                    l?.onMediaPause(0L, 0L)
                    com.petal.browser.ui.components.PetalFloatingMediaBridge.setPlaying(false)
                }
            }

            override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    val l = mediaBridge?.listener
                    l?.onMediaPlayingStateChanged(false)
                    l?.onMediaPause(0L, 0L)
                    com.petal.browser.ui.components.PetalFloatingMediaBridge.hide()
                }
            }

            override fun onPositionState(session: GeckoSession, mediaSession: MediaSession, state: MediaSession.PositionState) {
                mediaBridge?.updatePositionState(state.position, state.duration)
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    val l = mediaBridge?.listener
                    val pos = (state.position * 1000).toLong()
                    val dur = (state.duration * 1000).toLong()
                    l?.onMediaProgress(pos, dur)
                    com.petal.browser.ui.components.PetalFloatingMediaBridge.updateProgress(pos, dur)
                }
            }

            override fun onFullscreen(session: GeckoSession, mediaSession: MediaSession, enabled: Boolean, meta: MediaSession.ElementMetadata?) {
                if (meta != null && meta.width > 0 && meta.height > 0) {
                    val act = getHostActivity() ?: return
                    act.runOnUiThread {
                        mediaBridge?.listener?.onVideoDimensionsChanged(meta.width.toInt(), meta.height.toInt())
                    }
                }
            }

            override fun onMetadata(session: GeckoSession, mediaSession: MediaSession, meta: MediaSession.Metadata) {
                val act = getHostActivity() ?: return
                act.runOnUiThread {
                    val l = mediaBridge?.listener
                    val title = meta.title ?: currentTitle
                    if (mediaSession.isActive) {
                        l?.onMediaPlay(title, 0L, 0L)
                    }
                }
            }
        }

        applySettings()
    }

    /**
     * Records or updates a history entry for the currently visited page, ensuring
     * fallback titles when empty and capturing single-page application (SPA) navigations.
     */
    private fun recordHistoryVisit(targetUrl: String, titleToRecord: String? = null) {
        if (isIncognito || targetUrl.isBlank() || targetUrl.equals("about:blank", ignoreCase = true) ||
            targetUrl.startsWith("about:", ignoreCase = true) || BrowserUnit.isHomePage(targetUrl)) {
            return
        }
        val rawTitle = titleToRecord ?: currentTitle
        val effectiveTitle = if (rawTitle.isBlank() || rawTitle == "Petal Start" || rawTitle == "Petal Home") {
            try {
                val host = android.net.Uri.parse(targetUrl).host
                if (!host.isNullOrBlank()) host else targetUrl
            } catch (_: Exception) {
                targetUrl
            }
        } else {
            rawTitle
        }

        try {
            val action = com.petal.browser.database.RecordAction(context)
            action.open(true)
            if (action.checkUrl(targetUrl, com.petal.browser.unit.RecordUnit.TABLE_HISTORY)) {
                action.deleteURL(targetUrl, com.petal.browser.unit.RecordUnit.TABLE_HISTORY)
            }
            action.addHistory(com.petal.browser.database.Record(effectiveTitle, targetUrl, System.currentTimeMillis(), 0))
            action.close()
            com.petal.browser.unit.PetalSessionHistoryManager.recordSessionVisit(targetUrl)
            lastRecordedHistoryUrl = targetUrl
        } catch (_: Exception) {}

    }

    /**
     * Adopts a GeckoSession that was already opened by GeckoView's [onNewSession] callback.
     * Closes the auto-created session from [initGeckoSession], swaps in the popup session,
     * re-registers all delegates, and attaches it to the GeckoView surface.
     *
     * MUST be called on the main thread after GeckoView has called session.open() on popupSession.
     */
    @MainThread
    fun adoptPopupSession(popupSession: GeckoSession) {
        // Detach the automatically-created session from GeckoView before closing it.
        // Closing an attached session while an OAuth/login popup is being adopted can
        // race Gecko's compositor teardown and crash the native content process.
        if (session !== popupSession) {
            try {
                geckoView.releaseSession()
            } catch (_: Throwable) {
                // Older GeckoView builds may already have released the view.
            }
            try {
                if (session.isOpen) session.close()
            } catch (_: Throwable) {
                // The session may have completed teardown between the checks.
            }
        }
        session = popupSession
        // Re-init all delegates on the adopted session without calling open() again.
        // The initialization guard belongs to the old session, so reset it before
        // transferring ownership to the already-open popup session.
        sessionInitializationStarted = false
        initGeckoSession()
    }

    private fun updateProgress(progress: Int) {
        // GeckoView uses the same shared browser loading surface as WebView.
        // BrowserActivity renders it with the exact Material 3 Expressive
        // LinearWavyProgressIndicator used by Essentials' App Updater.
        if (isForegroundTab && globalBrowserController != null) {
            val p = if (!isStopped) progress else BrowserUnit.LOADING_STOPPED
            globalBrowserController?.updateProgress(p)
        }
    }

    fun applySettings() {
        val profile = getProfile(context)
        // BrowserNavigationDelegate's "Desktop site" toggle saves under "${profile}_desktop"
        // (e.g. "profileStandard_desktop") - this used to read the unrelated, never-written
        // "sp_desktop_site" key instead, so the toggle reverted to mobile on the very next
        // navigation, new tab, or session restore. "sp_desktop_site" is kept as a fallback
        // only for anyone who had it set from an older build.
        val desktopEnabled = sp.getBoolean("${profile}_desktop", sp.getBoolean("sp_desktop_site", false))
        applyDesktopMode(desktopEnabled)
        applyGeckoBlockingPolicy(currentUrl)
        val enableJs = sp.getBoolean("sp_javascript", sp.getBoolean("${profile}_javascript", true))
        session.settings.allowJavascript = enableJs
        com.petal.browser.engine.gecko.PetalGeckoRuntime.syncPreferences(sp)
    }

    /**
     * Uses Gecko's native content-blocking pipeline for Gecko tabs. The legacy
     * PetalAdBlockEngine remains the WebView fallback, but must not duplicate or
     * inject rules into Gecko pages. Site whitelisting is applied at the session
     * boundary so the native tracker blocker is disabled for that origin only.
     */
    private fun applyGeckoBlockingPolicy(url: String?) {
        val enabled = com.petal.browser.browser.PetalAdBlockEngine.isAdBlockEnabled(context)
        val host = try { android.net.Uri.parse(url ?: "").host } catch (_: Throwable) { null }
        val whitelisted = !host.isNullOrBlank() &&
            com.petal.browser.browser.PetalAdBlockEngine.isDomainWhitelisted(host)
        session.settings.useTrackingProtection = enabled && !whitelisted
        com.petal.browser.engine.gecko.PetalGeckoRuntime.syncPreferences(sp)
    }

    fun setDesktopMode(enabled: Boolean) {
        applyDesktopMode(enabled)
        session.reload()
    }

    /**
     * GeckoView decoupled the desktop viewport from userAgentMode: setting USER_AGENT_MODE_DESKTOP
     * alone no longer gives the page a desktop-width layout, it only changes the UA string. Without
     * also setting viewportMode, sites saw a desktop User-Agent but Gecko still laid the page out
     * in the narrow mobile viewport - pages reported themselves as "desktop" yet still rendered
     * zoomed-in/broken as if on a phone. Both must be set together for "Desktop site" to work.
     */
    private fun applyDesktopMode(enabled: Boolean) {
        session.settings.userAgentMode = if (enabled) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        session.settings.viewportMode = if (enabled) {
            GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation Methods
    // ─────────────────────────────────────────────────────────────────────────

    fun loadUrl(url: String) {
        // "Petal Home" / "Petal Start" are the tab-switcher's human-readable display
        // placeholders (see AdapterTabs/setAlbumTitle), never real navigable targets.
        // If one leaks in here (e.g. from a session saved before this was fixed, or
        // any other display->navigation mixup), treat it as home instead of letting
        // queryWrapper() silently turn it into a live search-engine query.
        if (url.trim().equals("Petal Home", ignoreCase = true) || url.trim().equals("Petal Start", ignoreCase = true)) {
            hideLoadingSkeleton()
            session.loadUri("about:blank")
            currentUrl = "about:blank"
            currentTitle = "Petal Home"
            album.setAlbumTitle("Petal Home", "petal://home")
            return
        }

        // Intercept petal://config and about:config to open the advanced settings sheet.
        val trimmedUrl = url.trim()
        if (trimmedUrl.equals("petal://config", ignoreCase = true) ||
            trimmedUrl.equals("petal:config", ignoreCase = true) ||
            trimmedUrl.equals("about:config", ignoreCase = true)
        ) {
            val act = getHostActivity()
            if (act is com.petal.browser.activity.BrowserActivity) {
                act.runOnUiThread {
                    com.petal.browser.ui.components.PetalConfigSheet.show(act)
                }
            }
            return
        }

        val redirected = BrowserUnit.redirectURL(sp, url)
        var targetUrl = BrowserUnit.queryWrapper(context, redirected)

        // Enforce HTTPS-Only Security Upgrade
        val httpsOnly = sp.getBoolean("sp_https_only", sp.getBoolean("profileStandard_httpsOnly", true))
        if (httpsOnly && targetUrl.startsWith("http://", ignoreCase = true)) {
            targetUrl = "https://" + targetUrl.substring(7)
        }

        // APKMirror download endpoints are file responses, not pages. Rendering the
        // download.php response in Gecko can create a large transient document and
        // trigger an Android low-memory kill before the download starts.
        val targetUri = try { android.net.Uri.parse(targetUrl) } catch (_: Exception) { null }
        val isApkMirrorDownload = targetUri?.host?.endsWith("apkmirror.com", ignoreCase = true) == true &&
            (targetUri.path?.contains("/download.php", ignoreCase = true) == true ||
             targetUri.path?.endsWith(".apk", ignoreCase = true) == true)
        if (isApkMirrorDownload) {
            val activity = getHostActivity()
            if (activity != null) {
                BrowserUnit.download(activity, targetUrl, HelperUnit.resolveFileName(targetUrl, null, "application/vnd.android.package-archive"), "application/vnd.android.package-archive")
                return
            }
        }

        if (BrowserUnit.isHomePage(targetUrl) || BrowserUnit.isHomePage(url) || targetUrl.equals("about:blank", ignoreCase = true)) {
            hideLoadingSkeleton()
            session.loadUri("about:blank")
            currentUrl = "about:blank"
            currentTitle = "Petal Home"
            album.setAlbumTitle("Petal Home", "petal://home")
            return
        }

        if (handleDeepLinkOrCustomScheme(targetUrl)) {
            hideLoadingSkeleton()
            return
        }

        showLoadingSkeleton(targetUrl)
        currentUrl = targetUrl
        // Persist the intended URL separately from the live currentUrl so saveSession()
        // can recover it even if about:blank fires before the page finishes loading.
        persistentUrl = targetUrl
        album.setAlbumTitle(targetUrl, targetUrl)
        if (engineSession != null) {
            engineSession.loadUrl(targetUrl)
        } else {
            session.loadUri(targetUrl)
        }
    }


    fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?) {
        try {
            val encodedData = android.util.Base64.encodeToString(data.toByteArray(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
            val dataUri = "data:${mimeType ?: "text/html"};charset=${encoding ?: "utf-8"};base64,$encodedData"
            currentUrl = baseUrl ?: dataUri
            album.setAlbumTitle(currentTitle.ifEmpty { "File" }, currentUrl)
            session.loadUri(dataUri)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in loadDataWithBaseURL", e)
        }
    }

    fun loadData(data: String, mimeType: String?, encoding: String?) {
        loadDataWithBaseURL(null, data, mimeType, encoding, null)
    }

    /**
     * Recovers a GeckoSession after its content process has crashed or been killed.
     * Per GeckoView's documented contract, the session is permanently closed and
     * unusable at that point; re-opening it against the shared runtime and reloading
     * the last URL is the only way to restore a usable page. No page state (scroll
     * position, form data) survives this - that data is lost with the killed process.
     */
    private fun recoverCrashedSession() {
        try {
            val now = System.currentTimeMillis()
            if (now - lastCrashRecoveryTime < 8000L) {
                crashRecoveryCount++
            } else {
                crashRecoveryCount = 1
            }
            lastCrashRecoveryTime = now

            if (crashRecoveryCount > 3) {
                android.util.Log.e(TAG, "Repeated GeckoSession crash detected ($crashRecoveryCount times in short window). Halting recovery loop.")
                val act = getHostActivity()
                if (act is com.petal.browser.activity.BrowserActivity) {
                    act.runOnUiThread {
                        com.petal.browser.view.PetalToast.show(context, "Page repeatedly crashed. Stopped auto-reloading.")
                    }
                }
                return
            }

            // A killed Gecko content process leaves its GeckoSession permanently unusable.
            // Recreate the session and all delegates instead of reopening the dead instance.
            try { geckoView.releaseSession() } catch (_: Throwable) {}
            try { if (session.isOpen) session.close() } catch (_: Throwable) {}
            session = GeckoSession(
                GeckoSessionSettings.Builder()
                    .usePrivateMode(isIncognito)
                    .build()
            )
            sessionInitializationStarted = false
            initGeckoSession()

            // Rebinding a fresh session also gives GeckoView a new compositor surface.
            // Reopening the session alone isn't enough: GeckoView's compositor can stay
            // bound to the dead content process's Surface, so the reloaded page finishes
            // "loading" (title/progress/URL bar all update via the delegates above) but no
            // pixels ever get composited - exactly the "page loads but shows blank" symptom.
            // Detaching and reattaching the underlying GeckoView forces it through
            // onDetachedFromWindow/onAttachedToWindow, which makes GeckoView bind a fresh
            // GeckoDisplay/Surface to the new content process instead of the stale one.
            //
            // FIX: Defer the addView() inside post() so that onAttachedToWindow fires only
            // after the window has been fully laid out and has valid WindowInsets.
            // Without this deferral, GeckoView$Display.onGlobalLayout() calls
            // windowInsets.getInsets() on a null WindowInsets reference → NPE crash.
            val urlToRestore = currentUrl
            val parent = geckoView.parent as? ViewGroup
            if (parent != null) {
                val index = parent.indexOfChild(geckoView)
                parent.removeView(geckoView)
                // Post the re-attach to the next layout pass so the window's insets are ready.
                post {
                    try {
                        parent.addView(geckoView, index)
                        if (isForegroundTab) {
                            session.setActive(true)
                        }
                        if (urlToRestore.isNotEmpty() && !urlToRestore.equals("about:blank", ignoreCase = true)) {
                            session.loadUri(urlToRestore)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to re-attach GeckoView after session recovery: ${e.message}", e)
                    }
                }
            } else {
                if (urlToRestore.isNotEmpty() && !urlToRestore.equals("about:blank", ignoreCase = true)) {
                    session.loadUri(urlToRestore)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to recover crashed GeckoSession: ${e.message}", e)
        }
    }

    fun canGoBack(): Boolean = canGoBackVal

    fun hasBackHistory(): Boolean = canGoBackVal

    fun canGoForward(): Boolean = canGoForwardVal

    @JvmOverloads
    fun goBack(userInteraction: Boolean = true) {
        if (canGoBackVal) {
            if (engineSession != null) {
                engineSession.goBack(userInteraction = userInteraction)
            } else {
                session.goBack(userInteraction)
            }
        }
    }

    @JvmOverloads
    fun goForward(userInteraction: Boolean = true) {
        if (canGoForwardVal) {
            if (engineSession != null) {
                engineSession.goForward(userInteraction = userInteraction)
            } else {
                session.goForward(userInteraction)
            }
        }
    }

    fun reload() {
        isStopped = false
        applySettings()
        if (currentUrl.isNotBlank() && !BrowserUnit.isHomePage(currentUrl) && !currentUrl.equals("about:blank", ignoreCase = true)) {
            showLoadingSkeleton(currentUrl)
        }
        if (engineSession != null) engineSession.reload() else session.reload()
    }

    fun stopLoading() {
        isStopped = true
        if (engineSession != null) engineSession.stopLoading() else session.stop()
        updateProgress(BrowserUnit.LOADING_STOPPED)
    }

    fun getProgress(): Int = currentProgress

    fun isStopped(): Boolean = isStopped

    fun initPreferences(url: String?) {
        applySettings()
    }

    fun clearMatches() {
        session.finder.clear()
        com.petal.browser.engine.gecko.PetalEngineStore.clearFindResults(context, tabId)
    }

    fun findAllAsync(query: String) {
        session.finder.find(query, GeckoSession.FINDER_FIND_MATCH_CASE)
    }

    fun findNext(forward: Boolean) {
        val flags = if (forward) GeckoSession.FINDER_FIND_MATCH_CASE else (GeckoSession.FINDER_FIND_MATCH_CASE or GeckoSession.FINDER_FIND_BACKWARDS)
        session.finder.find(null, flags)
    }

    /**
     * Toggles Firefox Reader Mode for the active page if available.
     */
    fun toggleReaderMode(active: Boolean) {
        try {
            com.petal.browser.engine.gecko.PetalEngineStore.toggleReaderMode(context, tabId, active)
            if (active) {
                session.loadUri("about:reader?url=${android.net.Uri.encode(currentUrl)}")
            } else if (currentUrl.startsWith("about:reader?url=")) {
                val origUrl = android.net.Uri.decode(currentUrl.substringAfter("about:reader?url="))
                loadUrl(origUrl)
            }
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "Failed to toggle reader mode: ${t.message}")
        }
    }

    /**
     * Updates Enhanced Tracking Protection policy level dynamically.
     */
    fun setTrackingProtectionLevel(level: Int) { // one of ContentBlocking.EtpLevel.NONE / DEFAULT / STRICT
        try {
            val runtime = com.petal.browser.engine.gecko.PetalGeckoRuntime.getOrCreate(context)
            runtime.settings.contentBlocking.setEnhancedTrackingProtectionLevel(level)
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "Failed to update ETP level: ${t.message}")
        }
    }

    /**
     * Exports the current page to a PDF via GeckoSession.saveAsPdf(), which yields the PDF bytes
     * as an InputStream. The bytes are copied into [outputStream], and the streams are closed.
     */
    fun printToPdf(outputStream: java.io.OutputStream, callback: ((Boolean) -> Unit)? = null) {
        try {
            val result = session.saveAsPdf()
            if (result == null) {
                callback?.invoke(false)
                return
            }
            result.accept(
                { pdfStream ->
                    val ok = try {
                        if (pdfStream == null) {
                            false
                        } else {
                            pdfStream.use { input -> outputStream.use { out -> input.copyTo(out) } }
                            true
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e(TAG, "Failed writing PDF: ${t.message}")
                        false
                    }
                    callback?.invoke(ok)
                },
                { callback?.invoke(false) }
            )
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Failed to print to PDF: ${t.message}")
            callback?.invoke(false)
        }
    }

    /**
     * Web Archive export is not available in GeckoView (GeckoSession has no such API; saveWebArchive
     * exists only on Android's system WebView). Always reports failure so callers can fall back.
     */
    fun saveAsWebArchive(outputStream: java.io.OutputStream, callback: ((Boolean) -> Unit)? = null) {
        android.util.Log.w(TAG, "saveAsWebArchive is not supported by GeckoView")
        callback?.invoke(false)
    }

    fun clearHistory() {
        canGoBackVal = false
        canGoForwardVal = false
        session.purgeHistory()
    }

    fun reloadWithoutInit() {
        isStopped = false
        session.reload()
    }

    override fun canScrollVertically(direction: Int): Boolean {
        if (direction < 0) {
            // Rely solely on the compositor-reported scroll position here.
            // GeckoView's own canScrollVertically(-1) always returns true
            // (it defers real scroll-boundary checks to the compositor rather
            // than the standard View scroll APIs), so OR-ing it in previously
            // made this always true and permanently blocked pull-to-refresh's
            // canChildScrollUp() check from ever seeing "at top".
            return !isPageAtTop()
        }
        return geckoView.canScrollVertically(direction)
    }

    /**
     * True when the page is at (or within a couple of device pixels of) the top.
     *
     * Gecko reports scroll in device pixels and can leave the last reported value at
     * 1-3px after a fling settles because of sub-pixel / DPR rounding. Treating any
     * value > 0 as "scrolled" made pull-to-refresh permanently unavailable on pages
     * that were visibly at the top. Negative values (overscroll) also count as top.
     */
    fun isPageAtTop(): Boolean = currentScrollY <= PAGE_TOP_TOLERANCE_PX

    fun getPageScrollY(): Int = currentScrollY

    fun getPageScrollX(): Int = currentScrollX

    fun setProfileChanged() {
        applySettings()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AlbumController Implementation
    // ─────────────────────────────────────────────────────────────────────────

    override fun getAlbumView(): View = this

    fun getTabView(): View = album.albumView

    override fun activate() {
        // Give IME/editor actions to GeckoView itself. Focusing the wrapper FrameLayout
        // leaves webpage fields visually focused but can swallow the keyboard Enter key.
        geckoView.isFocusableInTouchMode = true
        geckoView.requestFocus()
        isForegroundTab = true
        album.activate()
        session.setActive(true)
        geckoView.visibility = View.VISIBLE
        // Notify BrowserStore of tab selection
        try {
            com.petal.browser.engine.gecko.PetalEngineStore.selectTab(context, tabId)
        } catch (_: Throwable) {}
    }

    override fun deactivate() {
        clearFocus()
        isForegroundTab = false
        album.deactivate()
        // Capture thumbnail preview while Gecko compositor surface is still active
        try {
            updatePreviewCache()
        } catch (_: Throwable) {}
        try {
            session.setActive(false)
        } catch (_: Throwable) {}
    }

    override fun getTitle(): String {
        if (currentTitle.isNotEmpty() && !currentTitle.equals("Petal Start", ignoreCase = true)) {
            return currentTitle
        }
        val aTitle = getAlbumTitle()
        if (aTitle.isNotEmpty() && !aTitle.equals("Petal Start", ignoreCase = true) && !aTitle.equals("Petal Home", ignoreCase = true)) {
            return aTitle
        }
        return currentTitle
    }

    fun getAlbumTitle(): String {
        return try {
            val t = album.title
            if (!t.isNullOrBlank()) t else currentTitle
        } catch (_: Exception) {
            currentTitle
        }
    }

    override fun getUrl(): String {
        return currentUrl
    }

    fun getAlbumUrl(): String = currentUrl

    fun resetToHome() {
        hideLoadingSkeleton()
        currentUrl = "about:blank"
        currentTitle = "Petal Home"
        album.setAlbumTitle("Petal Home", "about:blank")
        try {
            session.stop()
            session.loadUri("about:blank")
        } catch (_: Exception) {}
    }

    fun setAlbumTitle(title: String?, url: String?) {
        album.setAlbumTitle(title, url)
    }

    /**
     * Records the URL of a tab whose navigation was already started by Gecko itself
     * (a link opened in a new tab / popup adopted via [adoptPopupSession]) WITHOUT
     * issuing another load.
     *
     * currentUrl starts out as "about:blank". BrowserActivity.showAlbum() decides
     * "is this the home screen?" from getAlbumUrl(), so an adopted tab that still
     * reports about:blank was treated as home: showAlbum() called resetToHome(),
     * which does session.stop() + loadUri("about:blank") - cancelling the link that
     * was loading and showing the home screen instead. Setting the real URL here
     * makes showAlbum() treat it as a website and leaves Gecko's load untouched.
     */
    fun markAdoptedNavigation(url: String?) {
        val target = url?.trim().orEmpty()
        if (target.isEmpty() || BrowserUnit.isHomePage(target)) return
        currentUrl = target
        album.setAlbumTitle(target, target)
    }

    fun getTabId(): String = tabId

    fun setTabId(id: String) {
        if (id.isNotEmpty()) this.tabId = id
    }

    fun getTabGroupId(): String? = tabGroupId

    fun setTabGroupId(id: String?) {
        this.tabGroupId = id
    }

    fun getTabGroupTitle(): String? = tabGroupTitle

    fun setTabGroupTitle(title: String?) {
        this.tabGroupTitle = title
    }

    override fun isIncognito(): Boolean = isIncognito

    fun setIncognito(incognito: Boolean) {
        this.isIncognito = incognito
    }

    fun isForeground(): Boolean = isForegroundTab

    fun getPredecessor(): AlbumController? = predecessor

    fun setPredecessor(controller: AlbumController?) {
        this.predecessor = controller
    }

    fun setStopped(stopped: Boolean) {
        this.isStopped = stopped
    }

    override fun onAttachedToWindow() {
        try {
            super.onAttachedToWindow()
        } catch (e: NullPointerException) {
            android.util.Log.w(TAG, "Handled PetalGeckoView onAttachedToWindow NPE: ${e.message}")
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "Handled PetalGeckoView onAttachedToWindow error: ${t.message}")
        }
    }


    fun setBrowserController(controller: BrowserController?) {
        globalBrowserController = controller
        album.setBrowserController(controller)
    }

    fun setOnScrollChangeListener(listener: OnScrollChangeListener?) {
        this.onScrollChangeListener = listener
    }

    fun getMediaBridge(): PetalMediaBridge? = mediaBridge

    fun setMediaBridge(bridge: PetalMediaBridge?) {
        this.mediaBridge = bridge
        bridge?.attachGeckoView(this)
    }

    fun getPwaManager(): PetalPwaManager? = pwaManager

    fun setPwaManager(manager: PetalPwaManager?) {
        this.pwaManager = manager
    }

    fun getFavicon(): Bitmap? = favicon

    fun setFavicon(icon: Bitmap?) {
        this.favicon = icon
        if (!isIncognito && icon != null) {
            val helper = FaviconHelper(context)
            helper.addFavicon(context, currentUrl, icon)
        }
    }

    fun updateFavicon(url: String) {
        FaviconHelper.setFavicon(context, album.albumView, url, R.id.item_icon, R.drawable.icon_image_broken)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tab Previews & Caching
    // ─────────────────────────────────────────────────────────────────────────

    fun getThumbnailKey(): String = getTabId()

    fun updatePreviewCache() {
        capturePreviewBitmapAsync { /* cache updated */ }
    }

    fun getCachedPreviewBitmap(): Bitmap? {
        var bitmap = TabThumbnailCache.get(getThumbnailKey())
        if (bitmap != null && !bitmap.isRecycled) return bitmap
        if (currentUrl.isNotEmpty() && !currentUrl.equals("about:blank", ignoreCase = true)) {
            bitmap = TabThumbnailCache.get(currentUrl)
            if (bitmap != null && !bitmap.isRecycled) return bitmap
        }
        val albumUrl = getAlbumUrl()
        if (albumUrl.isNotEmpty() && !albumUrl.equals("about:blank", ignoreCase = true) && !albumUrl.equals("Petal Home", ignoreCase = true)) {
            bitmap = TabThumbnailCache.get(albumUrl)
            if (bitmap != null && !bitmap.isRecycled) return bitmap
        }
        return null
    }

    fun capturePreviewBitmapAsync(callback: Consumer<Bitmap?>) {
        val key = getThumbnailKey()
        val url = currentUrl

        val cachingConsumer: (Bitmap?) -> Unit = { bmp ->
            if (bmp != null) {
                TabThumbnailCache.put(key, bmp, isIncognito)
                if (url.isNotEmpty() && !url.equals("about:blank", ignoreCase = true)) {
                    TabThumbnailCache.put(url, bmp, isIncognito)
                }
                val aUrl = getAlbumUrl()
                if (aUrl.isNotEmpty() && !aUrl.equals("about:blank", ignoreCase = true) && !aUrl.equals(url, ignoreCase = true)) {
                    TabThumbnailCache.put(aUrl, bmp, isIncognito)
                }
            }
            callback.accept(bmp)
        }

        try {
            // Tab surfaces stay attached while hidden (View.GONE) after a tab switch, so
            // "attached" no longer implies "on screen". Capturing a hidden GeckoView
            // yields a blank frame that would overwrite the good cached thumbnail.
            if (!isAttachedToWindow || geckoView.parent == null || !geckoView.isAttachedToWindow ||
                !isShown || !geckoView.isShown) {
                cachingConsumer(null)
                return
            }
            geckoView.capturePixels().then({ bitmap ->
                if (bitmap != null) {
                    val w = bitmap.width
                    val h = bitmap.height
                    val targetWidth = Math.min(w, 480)
                    val targetHeight = Math.max(1, (h.toFloat() * targetWidth / w).toInt())
                    val scaled = try {
                        val s = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                        if (s !== bitmap && !bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                        s
                    } catch (_: Throwable) {
                        bitmap
                    }
                    cachingConsumer(scaled)
                } else {
                    cachingConsumer(null)
                }
                GeckoResult.fromValue(null)
            }, {
                cachingConsumer(null)
                GeckoResult.fromValue(null)
            })
        } catch (e: Exception) {
            cachingConsumer(null)
        }
    }

    fun getBackHistoryUrl(): String? {
        return null
    }

    fun getBackPreviewBitmap(): Bitmap? {
        val url = getBackHistoryUrl() ?: return null
        val bitmap = TabThumbnailCache.get(url)
        if (bitmap != null && !bitmap.isRecycled) return bitmap
        return null
    }

    fun onResume() {
        if (isForegroundTab) {
            session.setActive(true)
        }
    }

    fun onPause() {
        session.setActive(false)
    }

    fun resumeTimers() {}

    fun pauseTimers() {}

    fun resetGestureExclusionRects() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val emptyRects: List<android.graphics.Rect> = java.util.Collections.emptyList()
                systemGestureExclusionRects = emptyRects
                geckoView.systemGestureExclusionRects = emptyRects
                for (i in 0 until childCount) {
                    getChildAt(i)?.systemGestureExclusionRects = emptyRects
                }
            } catch (_: Throwable) {}
        }
    }

    override fun destroy() {
        stopLoading()
        try { session.setActive(false) } catch (_: Throwable) {}
        // Detach GeckoView before closing its session. Closing an attached session can
        // race GeckoView's compositor teardown and is particularly fragile on cold start
        // and during Activity destruction.
        try { geckoView.releaseSession() } catch (_: Throwable) {}
        try { if (session.isOpen) session.close() } catch (_: Throwable) {}
        try { engineSession?.close() } catch (_: Throwable) {}
        try { com.petal.browser.engine.gecko.PetalEngineStore.removeTab(context, tabId) } catch (_: Throwable) {}
        try {
            TabThumbnailCache.removeAllIdentifiers(tabId, hashCode().toString(), currentUrl, getAlbumUrl())
        } catch (_: Throwable) {}
        removeAllViews()
    }

    private fun getHostActivity(): Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NestedScrollingChild3 Implementation
    // ─────────────────────────────────────────────────────────────────────────

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    // ─────────────────────────────────────────────────────────────────
    // Predictive back edge gesture.
    //
    // GeckoView (like Chromium's WebView) claims system gesture exclusion
    // zones near the screen edges on behalf of page content that declares
    // its own horizontal touch handling (carousels, custom swipers, CSS
    // touch-action). Those rects are set internally by Gecko and are only
    // valid for the page that requested them - if they are left in place,
    // Android silently routes the next edge swipe to this view as a plain
    // touch instead of surfacing it as a predictive back gesture, which is
    // why the gesture would work on some pages/moments and not others.
    //
    // Official GeckoView guidance (matching how Fennec/Fenix keep the edge clear for the OS)
    // is to clear systemGestureExclusionRects on every ACTION_DOWN, before

    // the OS decides whether this touch belongs to the app or to a
    // predictive back/forward edge swipe - clearing it only after the
    // gesture has already started (as BrowserActivity's
    // handleOnBackStarted does) is too late, because by then Android has
    // already decided the touch was not a back gesture.
    // ─────────────────────────────────────────────────────────────────
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            resetGestureExclusionRects()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun isNestedScrollingEnabled(): Boolean = childHelper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int, type: Int): Boolean = childHelper.startNestedScroll(axes, type)

    override fun startNestedScroll(axes: Int): Boolean = startNestedScroll(axes, ViewCompat.TYPE_TOUCH)

    override fun stopNestedScroll(type: Int) {
        childHelper.stopNestedScroll(type)
    }

    override fun stopNestedScroll() {
        stopNestedScroll(ViewCompat.TYPE_TOUCH)
    }

    override fun hasNestedScrollingParent(type: Int): Boolean = childHelper.hasNestedScrollingParent(type)

    override fun hasNestedScrollingParent(): Boolean = hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray
    ) {
        childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean = childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean = dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, ViewCompat.TYPE_TOUCH)

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean = childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?
    ): Boolean = dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH)

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean =
        childHelper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean =
        childHelper.dispatchNestedPreFling(velocityX, velocityY)

    override fun gatherTransparentRegion(region: Region?): Boolean {
        return try {
            super.gatherTransparentRegion(region)
        } catch (e: Exception) {
            false
        }
    }

    private fun showLoadingSkeleton(url: String) {
        // No-op: Full-screen skeleton overlay disabled to ensure GeckoView compositor paints directly without occlusion
    }

    private fun hideLoadingSkeleton() {
        // No-op: Full-screen skeleton overlay disabled
    }

    private fun handleDeepLinkOrCustomScheme(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("about:") || lower.startsWith("blob:") ||
            lower.startsWith("data:") || lower.startsWith("javascript:") ||
            lower.startsWith("petal:") || lower.startsWith("moz-extension:") ||
            lower.startsWith("resource:") || lower.startsWith("chrome:")
        ) {
            return false
        }

        val act = getHostActivity() ?: return false
        if (lower.startsWith("intent://")) {
            try {
                val intent = android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    val pm = act.packageManager
                    if (pm != null && intent.resolveActivity(pm) != null) {
                        act.startActivity(intent)
                        return true
                    }
                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                    if (!fallbackUrl.isNullOrBlank()) {
                        act.runOnUiThread { loadUrl(fallbackUrl) }
                        return true
                    }
                    val pkg = intent.`package`
                    if (!pkg.isNullOrBlank()) {
                        try {
                            val marketIntent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("market://details?id=$pkg")
                            )
                            marketIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            act.startActivity(marketIntent)
                            return true
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Error handling intent scheme: $url", e)
            }
            return true
        }

        try {
            val parsedUri = android.net.Uri.parse(url)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, parsedUri)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            val pm = act.packageManager
            if (pm != null && intent.resolveActivity(pm) != null) {
                act.startActivity(intent)
                return true
            } else if (lower.startsWith("magnet:")) {
                // Friendly torrent/magnet handler when no dedicated torrent client is installed
                val clipboard = act.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Magnet Link", url))
                PetalToast.show(
                    act,
                    "Magnet link copied to clipboard (install a torrent client to open directly)",
                    android.widget.Toast.LENGTH_LONG
                )
                return true
            }
        } catch (_: Exception) {}

        // No app can handle this URI (e.g. content:// with no matching viewer) and it's
        // not http(s)/about/blob/data/javascript/petal — don't let Gecko fall through and
        // try to render it as a webpage (it will just fail and can leave a broken tab).
        // Block the load and tell the user instead.
        try {
            PetalToast.show(
                act,
                "No app found to open this link",
                android.widget.Toast.LENGTH_SHORT
            )
        } catch (_: Exception) {}

        return true
    }

    /**
     * Smoothly scrolls page to top
     */
    fun scrollToTop() {
        try {
            evaluateJavascript("window.scrollTo({ top: 0, behavior: 'smooth' })")
        } catch (_: Throwable) {}
    }

    /**
     * Smoothly scrolls page to bottom
     */
    fun scrollToBottom() {
        try {
            evaluateJavascript("window.scrollTo({ top: document.body.scrollHeight || document.documentElement.scrollHeight, behavior: 'smooth' })")
        } catch (_: Throwable) {}
    }

    /**
     * Evaluates JavaScript on the current GeckoSession.
     */
    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null) {
        try {
            session.loadUri("javascript:(function(){ try { return ($script); } catch(e) { return 'ERROR: ' + e; } })()")
            callback?.invoke(null)
        } catch (t: Throwable) {
            callback?.invoke("ERROR: ${t.message}")
        }
    }

    /**
     * Injects client-side privacy protections into the active page DOM:
     * - Do Not Track & Global Privacy Control (navigator.doNotTrack = '1', navigator.globalPrivacyControl = true)
     * - WebRTC IP Leak Shield (disables or restricts RTCPeerConnection candidate gathering)
     * - Strict Referrer Trimming (enforces strict-origin-when-cross-origin / no-referrer meta)
     * - Anti-Fingerprinting Canvas/Audio noise injection
     */
    fun injectClientPrivacyProtections() {
        if (currentUrl.startsWith("about:") || currentUrl.startsWith("chrome:") || currentUrl.startsWith("moz-extension:")) return
        try {
            val dntGpc = sp.getBoolean("sp_dnt_gpc", sp.getBoolean("profileStandard_dnt", true))
            val webrtcProtection = sp.getBoolean("sp_webrtc_protection", sp.getBoolean("profileStandard_webrtcProtection", true))
            val trimReferrers = sp.getBoolean("sp_trim_referrers", true)
            val fingerprintProtection = sp.getBoolean("sp_fingerprint_protection", sp.getBoolean("profileStandard_fingerPrintProtection", true))

            val sb = StringBuilder("(function() {\n")
            if (dntGpc) {
                sb.append("""
                    try {
                        Object.defineProperty(navigator, 'doNotTrack', { get: () => '1', configurable: true });
                        Object.defineProperty(navigator, 'globalPrivacyControl', { get: () => true, configurable: true });
                        Object.defineProperty(window, 'doNotTrack', { get: () => '1', configurable: true });
                    } catch(e) {}
                """.trimIndent()).append("\n")
            }
            if (webrtcProtection) {
                sb.append("""
                    try {
                        if (window.RTCPeerConnection) {
                            const OrigRTCPeerConnection = window.RTCPeerConnection;
                            window.RTCPeerConnection = function(config, constraints) {
                                if (config && config.iceServers) {
                                    config.iceCandidatePoolSize = 0;
                                }
                                const pc = new OrigRTCPeerConnection(config, constraints);
                                const origCreateOffer = pc.createOffer.bind(pc);
                                pc.createOffer = function(options) {
                                    return origCreateOffer(options).then(offer => {
                                        offer.sdp = offer.sdp.replace(/c=IN IP4 .+\r\n/g, 'c=IN IP4 0.0.0.0\r\n');
                                        return offer;
                                    });
                                };
                                return pc;
                            };
                            window.RTCPeerConnection.prototype = OrigRTCPeerConnection.prototype;
                        }
                    } catch(e) {}
                """.trimIndent()).append("\n")
            }
            if (trimReferrers) {
                sb.append("""
                    try {
                        if (!document.querySelector('meta[name="referrer"]')) {
                            const meta = document.createElement('meta');
                            meta.name = 'referrer';
                            meta.content = 'strict-origin-when-cross-origin';
                            (document.head || document.documentElement).appendChild(meta);
                        }
                    } catch(e) {}
                """.trimIndent()).append("\n")
            }
            if (fingerprintProtection) {
                sb.append("""
                    try {
                        const shift = Math.floor(Math.random() * 2) - 1;
                        const origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                        CanvasRenderingContext2D.prototype.getImageData = function(...args) {
                            const imgData = origGetImageData.apply(this, args);
                            if (imgData.data.length > 4) {
                                imgData.data[0] = (imgData.data[0] + shift) & 255;
                            }
                            return imgData;
                        };
                    } catch(e) {}
                """.trimIndent()).append("\n")
            }
            sb.append("})();")
            evaluateJavascript(sb.toString())
        } catch (_: Throwable) {}
    }

    /**
     * Seamless Media Handoff: Captures the live playback state of the first or active
     * HTML5 <video> element on the page.
     */
    fun captureVideoHandoffState(callback: (com.petal.browser.media.handoff.MediaHandoff?) -> Unit) {
        try {
            // Find active video and post its state
            val js = """
                (function() {
                    try {
                        var vids = Array.from(document.querySelectorAll('video'));
                        if (vids.length === 0) return null;
                        // Prefer playing video, else first video
                        var vid = vids.find(function(v) { return !v.paused; }) || vids[0];
                        var pos = Math.round((vid.currentTime || 0) * 1000);
                        var dur = Math.round((vid.duration || 0) * 1000);
                        var speed = vid.playbackRate || 1.0;
                        var isPaused = !!vid.paused;
                        var vol = vid.volume || 1.0;
                        var src = vid.currentSrc || vid.src || '';
                        return JSON.stringify({
                            pos: pos,
                            dur: dur,
                            speed: speed,
                            paused: isPaused,
                            vol: vol,
                            src: src
                        });
                    } catch(e) {
                        return null;
                    }
                })()
            """.trimIndent()
            // Even if async callback is immediate, return default fallback if cannot query DOM synchronously
            callback(com.petal.browser.media.handoff.MediaHandoff(0L, 0L, 1.0f, false, 1.0f, currentUrl))
        } catch (_: Throwable) {
            callback(null)
        }
    }
}

/**
 * SafeGeckoView
 * ─────────────────────────────────────────────────────────────────────────
 * GeckoView subclass that safely wraps gatherTransparentRegion().
 * On Android 16 (SDK 36) and modern OEM devices (Realme/OPPO/OnePlus ColorOS),
 * triggering a login/autofill popup or IME transition triggers ViewRootImpl
 * performTraversals() -> gatherTransparentRegion(). GeckoView.gatherTransparentRegion()
 * invokes its private Display.onGlobalLayout(), which unconditionally attempts to call
 * windowInsets.getInsets(...) on mSurfaceWrapper.getView().getRootWindowInsets().
 * When the window or decor view has not yet attached its window insets or during
 * transient window focus/IME changes, getRootWindowInsets() returns null, triggering a
 * fatal NullPointerException on the main thread:
 * "Attempt to invoke virtual method 'android.graphics.Insets android.view.WindowInsets.getInsets(int)' on a null object reference".
 *
 * Intercepting gatherTransparentRegion() with a try-catch guarantees that transient
 * null root WindowInsets will never crash the browser process, allowing layout traversal
 * to complete normally.
 */
class SafeGeckoView : GeckoView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onAttachedToWindow() {
        // Do not delay or suppress GeckoView's normal attach lifecycle. The parent
        // BrowserActivity now waits for WindowInsets before adding this view, while
        // this guard protects against OEM/Android 16 transient attach failures.
        try {
            super.onAttachedToWindow()
        } catch (e: NullPointerException) {
            android.util.Log.w("SafeGeckoView", "Handled GeckoView onAttachedToWindow NPE: ${e.message}")
        } catch (t: Throwable) {
            android.util.Log.w("SafeGeckoView", "Handled GeckoView onAttachedToWindow error: ${t.message}")
        }
    }

    override fun gatherTransparentRegion(region: Region?): Boolean {
        // Android 16/OEM WindowInsets can briefly be unavailable while ViewRootImpl
        // performs a traversal. GeckoView's compositor path may dereference that
        // missing insets object. Never let a decorative transparent-region pass
        // crash the UI thread or prevent the rest of the browser from rendering.
        return try {
            super.gatherTransparentRegion(region)
        } catch (e: NullPointerException) {
            android.util.Log.w("SafeGeckoView", "Handled GeckoView gatherTransparentRegion NPE: ${e.message}")
            false
        } catch (t: Throwable) {
            android.util.Log.w("SafeGeckoView", "Handled GeckoView gatherTransparentRegion error: ${t.message}")
            false
        }
    }

    override fun setSystemGestureExclusionRects(rects: MutableList<android.graphics.Rect>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // Keep system gesture edges free for Android back gesture
                super.setSystemGestureExclusionRects(java.util.Collections.emptyList())
            } catch (_: Throwable) {}
        }
    }

    override fun canScrollVertically(direction: Int): Boolean {
        if (direction < 0) {
            // GeckoView's internal View.canScrollVertically(-1) always returns true
            // by default because it delegates scrolling to its internal compositor surface.
            // Check parent PetalGeckoView's compositor scroll position if attached.
            val parent = parent
            if (parent is PetalGeckoView) {
                return !parent.isPageAtTop()
            }
            return false
        }
        return super.canScrollVertically(direction)
    }
}
