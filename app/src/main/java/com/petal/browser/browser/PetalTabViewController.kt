package com.petal.browser.browser

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.View
import androidx.preference.PreferenceManager
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.petal.browser.unit.TabThumbnailCache
import com.petal.browser.engine.gecko.PetalEngineStore
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.session.FullScreenFeature
import mozilla.components.feature.session.SwipeRefreshFeature
import mozilla.components.feature.tabs.TabsUseCases

/**
 * A browser tab surface backed by Android Components' engine abstractions.
 *
 * The view owns presentation and observation only. Tab lifetime and persistence remain with
 * [BrowserStore], so detaching this view never closes or recreates a tab session.
 */
class PetalTabViewController private constructor(
    context: Context,
    attrs: AttributeSet?,
    private val engineView: EngineView
) : SwipeRefreshLayout(context, attrs), AlbumController, EngineView by engineView {

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) :
        this(context, attrs, PetalEngineStore.createEngineView(context))

    private val appContext = context.applicationContext
    private val browserStore: BrowserStore = PetalEngineStore.getStore(appContext)
    private var tab: TabSessionState? = null
    private var boundTabId: String? = null
    private var observedSession: EngineSession? = null
    private var active = false
    private var previewRevision = 0L
    private var capturedPreviewRevision = -1L
    private var previewCaptureSequence = 0L
    private var previewRetryCount = 0
    private var pageTitle = ""
    private var pageUrl = "about:blank"
    private var backAvailable = false
    private var forwardAvailable = false
    private var loading = false
    private var progress = 0
    private var isSecure = false
    private var tabGroupId: String? = null
    private var tabGroupTitle: String? = null
    private var predecessor: AlbumController? = null
    private var mediaTitle = ""
    private var mediaPosition: mozilla.components.concept.engine.mediasession.MediaSession.PositionState? = null
    private var mediaBridge: com.petal.browser.media.PetalMediaBridge? = null
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(appContext) }
    private var refreshFeature: SwipeRefreshFeature? = null
    private var fullScreenFeature: FullScreenFeature? = null
    private var attachedLifecycle: Lifecycle? = null
    private val engineLifecycleObserver = mozilla.components.concept.engine.LifecycleObserver(this)
    private var lastRecordedHistoryUrl: String? = null

    /** Receives browser-relevant session updates without coupling the activity to GeckoView. */
    var onBrowserStateChanged: ((State) -> Unit)? = null
        set(value) {
            field = value
            currentState()?.let { value?.invoke(it) }
        }

    data class State(
        val tabId: String,
        val url: String,
        val title: String,
        val progress: Int,
        val loading: Boolean,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
        val isSecure: Boolean,
        val isPrivate: Boolean
    )

    private val observer = object : EngineSession.Observer {
        override fun onLocationChange(url: String, hasUserGesture: Boolean) {
            android.util.Log.d("PetalTabNav", "observer.onLocationChange: url=$url gesture=$hasUserGesture oldPageUrl=$pageUrl")
            if (pageUrl != url) {
                previewRevision++
                previewRetryCount = 0
            }
            pageUrl = url
            applyPageSettings(url)
            tab?.let { browserStore.dispatch(ContentAction.UpdateUrlAction(it.id, url)) }
            publishState()
        }

        override fun onTitleChange(title: String) {
            pageTitle = title
            tab?.let { browserStore.dispatch(ContentAction.UpdateTitleAction(it.id, title)) }
            publishState()
            if (!loading && title.isNotBlank() && pageUrl == lastRecordedHistoryUrl) {
                recordHistoryVisit(pageUrl, title, force = true)
            }
        }

        override fun onProgress(progress: Int) {
            this@PetalTabViewController.progress = progress
            tab?.let { browserStore.dispatch(ContentAction.UpdateProgressAction(it.id, progress)) }
            publishState()
            if (progress in 85..100) {
                injectPagePrivacyAndAdBlock()
            }
        }

        override fun onLoadingStateChange(loading: Boolean) {
            if (this@PetalTabViewController.loading != loading && loading) {
                previewRevision++
                previewRetryCount = 0
            }
            this@PetalTabViewController.loading = loading
            tab?.let { browserStore.dispatch(ContentAction.UpdateLoadingStateAction(it.id, loading)) }
            publishState()
            if (!loading) {
                injectPagePrivacyAndAdBlock()
                recordHistoryVisit(pageUrl, pageTitle)
            }
        }

        override fun onNavigationStateChange(canGoBack: Boolean?, canGoForward: Boolean?) {
            canGoBack?.let { backAvailable = it }
            canGoForward?.let { forwardAvailable = it }
            tab?.let {
                PetalEngineStore.updateNavigationState(
                    appContext,
                    it.id,
                    backAvailable,
                    forwardAvailable
                )
            }
            publishState()
        }

        override fun onSecurityChange(
            secure: Boolean,
            host: String?,
            issuer: String?,
            certificate: java.security.cert.X509Certificate?
        ) {
            isSecure = secure
            publishState()
        }

        override fun onWindowRequest(
            windowRequest: mozilla.components.concept.engine.window.WindowRequest
        ) {
            val activity = context as? com.petal.browser.activity.BrowserActivity ?: return
            activity.runOnUiThread {
                if (windowRequest.type == mozilla.components.concept.engine.window.WindowRequest.Type.OPEN) {
                    activity.adoptPreparedWindow(this@PetalTabViewController, windowRequest)
                } else {
                    activity.closeContentWindow(this@PetalTabViewController)
                }
                boundTabId?.let {
                    PetalEngineStore.consumeWindowRequest(appContext, it)
                }
            }
        }

        override fun onMediaActivated(
            mediaSessionController: mozilla.components.concept.engine.mediasession.MediaSession.Controller
        ) {
            mediaBridge?.setActiveEngineMediaController(mediaSessionController)
            tab?.let { PetalEngineStore.updateMediaController(appContext, it.id, mediaSessionController) }
        }

        override fun onMediaDeactivated() {
            mediaBridge?.setActiveEngineMediaController(null)
            tab?.let { PetalEngineStore.updateMediaController(appContext, it.id, null) }
            (context as? com.petal.browser.activity.BrowserActivity)?.runOnUiThread {
                mediaBridge?.listener?.onMediaPlayingStateChanged(false)
            }
        }

        override fun onMediaMetadataChanged(
            metadata: mozilla.components.concept.engine.mediasession.MediaSession.Metadata
        ) {
            mediaTitle = metadata.title?.takeIf { it.isNotBlank() } ?: pageTitle
            tab?.let { PetalEngineStore.updateMediaMetadata(appContext, it.id, metadata) }
        }

        override fun onMediaPlaybackStateChanged(
            playbackState: mozilla.components.concept.engine.mediasession.MediaSession.PlaybackState
        ) {
            val playing = playbackState ==
                mozilla.components.concept.engine.mediasession.MediaSession.PlaybackState.PLAYING
            val position = mediaPosition
            val positionMs = ((position?.position ?: 0.0) * 1000).toLong()
            val durationMs = ((position?.duration ?: 0.0) * 1000).toLong()
            tab?.let { PetalEngineStore.updateMediaPlaybackState(appContext, it.id, playbackState) }
            if (playing) {
                mediaBridge?.listener?.onMediaPlay(mediaTitle, positionMs, durationMs)
            } else {
                mediaBridge?.listener?.onMediaPause(positionMs, durationMs)
            }
            mediaBridge?.listener?.onMediaPlayingStateChanged(playing)
        }

        override fun onMediaPositionStateChanged(
            positionState: mozilla.components.concept.engine.mediasession.MediaSession.PositionState
        ) {
            mediaPosition = positionState
            tab?.let { PetalEngineStore.updateMediaPosition(appContext, it.id, positionState) }
            mediaBridge?.updatePositionState(positionState.position, positionState.duration)
            val positionMs = (positionState.position * 1000).toLong()
            val durationMs = (positionState.duration * 1000).toLong()
            mediaBridge?.listener?.onMediaProgress(positionMs, durationMs)
        }

        override fun onMediaFullscreenChanged(
            fullscreen: Boolean,
            elementMetadata: mozilla.components.concept.engine.mediasession.MediaSession.ElementMetadata?
        ) {
            tab?.let { PetalEngineStore.updateMediaFullscreen(appContext, it.id, fullscreen, elementMetadata) }
            if (fullscreen && elementMetadata != null && elementMetadata.width > 0 && elementMetadata.height > 0) {
                mediaBridge?.listener?.onVideoDimensionsChanged(
                    elementMetadata.width.toInt(),
                    elementMetadata.height.toInt()
                )
            }
        }
    }

    init {
        isNestedScrollingEnabled = true
        // Petal's own PullToRefreshFrameLayout owns the pull gesture inside BrowserActivity;
        // disable the Mozilla SwipeRefreshLayout so it never intercepts or draws its spinner.
        if (isHostedByBrowserActivity()) {
            isEnabled = false
        }
        mediaBridge = com.petal.browser.media.PetalMediaBridge(
            context,
            object : com.petal.browser.media.PetalMediaBridge.MediaStateListener {
                override fun onMediaPlay(title: String?, positionMs: Long, durationMs: Long) {
                    val activity = context as? com.petal.browser.activity.BrowserActivity ?: return
                    activity.runOnUiThread {
                        activity.isMediaPlaying = true
                        activity.updatePipParams(true)
                        activity.mediaService?.updateMediaState(title, pageTitle, true, positionMs, durationMs)
                    }
                }

                override fun onMediaPause(positionMs: Long, durationMs: Long) {
                    val activity = context as? com.petal.browser.activity.BrowserActivity ?: return
                    activity.runOnUiThread {
                        activity.isMediaPlaying = false
                        activity.updatePipParams(false)
                        activity.mediaService?.updateMediaState(pageTitle, pageTitle, false, positionMs, durationMs)
                    }
                }

                override fun onMediaProgress(positionMs: Long, durationMs: Long) = Unit

                override fun onMediaPlayingStateChanged(isPlaying: Boolean) {
                    val activity = context as? com.petal.browser.activity.BrowserActivity ?: return
                    activity.runOnUiThread {
                        activity.isMediaPlaying = isPlaying
                        activity.updatePipParams(isPlaying)
                    }
                }

                override fun onVideoDimensionsChanged(width: Int, height: Int) {
                    (context as? com.petal.browser.activity.BrowserActivity)?.updateVideoDimensions(width, height)
                }
            }
        ).apply {
            attachTabViewController(this@PetalTabViewController)
        }
        addView(
            engineView.asView(),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        (context as? LifecycleOwner)?.let(::attachLifecycle)
    }

    /** Bind this view to a tab already owned by BrowserStore. */
    @MainThread
    fun bindTab(tab: TabSessionState) {
        val session = requireNotNull(tab.engineState.engineSession) {
            "Tab ${tab.id} has no engine session"
        }
        if (this.tab?.id == tab.id && observedSession === session) return

        observedSession?.unregister(observer)
        refreshFeature?.stop()
        refreshFeature = null
        observedSession = session
        this.tab = tab
        boundTabId = tab.id
        fullScreenFeature?.stop()
        fullScreenFeature = FullScreenFeature(
            store = browserStore,
            sessionUseCases = SessionUseCases(browserStore),
            tabId = tab.id,
            fullScreenChanged = { enabled ->
                (context as? com.petal.browser.activity.BrowserActivity)?.setCustomFullscreen(enabled)
            }
        )
        pageUrl = tab.content.url
        pageTitle = tab.content.title
        progress = tab.content.progress
        loading = tab.content.loading
        applyPageSettings(pageUrl)
        backAvailable = false
        forwardAvailable = false
        isSecure = pageUrl.startsWith("https://", ignoreCase = true)
        engineView.render(session)
        session.register(observer)
        val geckoSession = getGeckoSession()
        com.petal.browser.extensions.PetalExtensionManager.attachSession(geckoSession)
        geckoSession?.let { gs ->
            gs.promptDelegate = com.petal.browser.view.PetalGeckoView.createPromptDelegate(
                gs,
                context,
                preferences,
                { isIncognito() }
            ) { context as? android.app.Activity }

            android.util.Log.d("PetalTabNav", "bindTab: gs.navigationDelegate=${gs.navigationDelegate?.javaClass?.name}, gs.contentDelegate=${gs.contentDelegate?.javaClass?.name}")

            // Content Delegate: preserve Mozilla's engine contentDelegate so title, crash,
            // and fullscreen callbacks are NOT dropped, while handling onExternalResponse and onContextMenu.
            val engineContent = gs.contentDelegate
            if (engineContent !is PetalContentDelegateWrapper) {
                gs.contentDelegate = PetalContentDelegateWrapper(
                    engine = engineContent,
                    contextProvider = { context }
                )
            }

            // Do NOT replace GeckoView's navigation delegate. Mozilla's GeckoEngineSession installs its
            // own delegate that reports onLocationChange / onCanGoBack / onLoadError to the observers
            // (this class's `observer`) and tracks the load request used by reload().
            // We explicitly forward all methods because Kotlin's `by` delegation drops Java default methods.
            val engineNavigation = gs.navigationDelegate
            if (engineNavigation !is ExternalSchemeNavigationDelegate) {
                val inner = engineNavigation ?: object : org.mozilla.geckoview.GeckoSession.NavigationDelegate {}
                gs.navigationDelegate = ExternalSchemeNavigationDelegate(
                    engine = inner,
                    context = context,
                    currentUrlSupplier = { pageUrl },
                    isIncognitoSupplier = { isIncognito() },
                    onLocationChanged = { newUrl ->
                        android.util.Log.d("PetalTabNav", "ExternalSchemeNavigationDelegate: onLocationChanged -> $newUrl (current pageUrl=$pageUrl)")
                        if (newUrl.isNotBlank() && !newUrl.equals(pageUrl, ignoreCase = true)) {
                            previewRevision++
                            previewRetryCount = 0
                            pageUrl = newUrl
                            applyPageSettings(newUrl)
                            tab?.let { browserStore.dispatch(ContentAction.UpdateUrlAction(it.id, newUrl)) }
                            publishState()
                        }
                    }
                ) { uri ->
                    val act = resolveActivity(context)
                    act != null && com.petal.browser.view.PetalGeckoView.handleExternalScheme(act, uri)
                }
            } else {
                engineNavigation.updateDependencies(context, { pageUrl }, { isIncognito() })
            }

            gs.selectionActionDelegate = object : org.mozilla.geckoview.GeckoSession.SelectionActionDelegate {
                override fun onShowActionRequest(
                    session: org.mozilla.geckoview.GeckoSession,
                    selection: org.mozilla.geckoview.GeckoSession.SelectionActionDelegate.Selection
                ) {
                    val act = (context as? com.petal.browser.activity.BrowserActivity)
                        ?: (context as? android.content.ContextWrapper)?.baseContext as? com.petal.browser.activity.BrowserActivity
                        ?: return
                    val selectedText = selection.text
                    if (!selectedText.isNullOrBlank()) {
                        act.runOnUiThread {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showSelectionContextMenu(act, selectedText)
                        }
                    }
                }

                override fun onHideAction(session: org.mozilla.geckoview.GeckoSession, reason: Int) {}
            }

            // Official Mozilla Firefox ContentBlocking Delegate (Enhanced Tracking Protection & AdBlock telemetry)
            gs.contentBlockingDelegate = object : org.mozilla.geckoview.ContentBlocking.Delegate {
                override fun onContentBlocked(
                    session: org.mozilla.geckoview.GeckoSession,
                    event: org.mozilla.geckoview.ContentBlocking.BlockEvent
                ) {
                    com.petal.browser.browser.PetalAdBlockEngine.recordBlock(pageUrl)
                }
            }

            // GeckoSession HistoryDelegate to record top-level page visits into Petal history
            gs.historyDelegate = object : org.mozilla.geckoview.GeckoSession.HistoryDelegate {
                override fun onHistoryStateChange(
                    session: org.mozilla.geckoview.GeckoSession,
                    historyList: org.mozilla.geckoview.GeckoSession.HistoryDelegate.HistoryList
                ) {
                    val currentIndex = historyList.currentIndex
                    val size = historyList.size
                    backAvailable = currentIndex > 0
                    forwardAvailable = currentIndex < size - 1
                    tab?.let {
                        PetalEngineStore.updateNavigationState(
                            appContext,
                            it.id,
                            backAvailable,
                            forwardAvailable
                        )
                    }
                    val act = resolveActivity(context)
                    if (act is com.petal.browser.activity.BrowserActivity) {
                        act.runOnUiThread {
                            act.updateBackCallbackState()
                            act.updateOmniBox()
                        }
                    }
                }

                override fun onVisited(
                    session: org.mozilla.geckoview.GeckoSession,
                    url: String,
                    lastVisitedURL: String?,
                    flags: Int
                ): org.mozilla.geckoview.GeckoResult<Boolean> {
                    val isTopLevel = (flags and org.mozilla.geckoview.GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL) != 0
                    val isError = (flags and org.mozilla.geckoview.GeckoSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR) != 0
                    val isRedirect = (flags and org.mozilla.geckoview.GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE) != 0 ||
                                     (flags and org.mozilla.geckoview.GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT) != 0

                    if (isTopLevel && !isError && !isRedirect) {
                        recordHistoryVisit(url, pageTitle)
                    }
                    return org.mozilla.geckoview.GeckoResult.fromValue(true)
                }
            }
        }
        refreshFeature = if (isHostedByBrowserActivity()) null else SwipeRefreshFeature(
            store = browserStore,
            reloadUrlUseCase = SessionUseCases(browserStore).reload,
            swipeRefreshLayout = this,
            tabId = tab.id
        )
        if (active) {
            PetalEngineStore.selectTab(appContext, tab.id)
            refreshFeature?.start()
            fullScreenFeature?.start()
        }
        publishState()
    }

    private fun isHostedByBrowserActivity(): Boolean {
        var ctx: android.content.Context? = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is com.petal.browser.activity.BrowserActivity) return true
            ctx = ctx.baseContext
        }
        return false
    }

    private fun resolveActivity(c: android.content.Context): android.app.Activity? {
        var ctx: android.content.Context? = c
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? android.app.Activity
    }

    fun loadUrl(url: String) {
        val rawUrl = url.trim()
        if (rawUrl.isEmpty()) return
        if (rawUrl.equals("Petal Home", ignoreCase = true) || rawUrl.equals("Petal Start", ignoreCase = true)) {
            pageUrl = "about:blank"
            pageTitle = "Petal Home"
            observedSession?.loadUrl("about:blank")
            publishState()
            return
        }
        val redirected = com.petal.browser.unit.BrowserUnit.redirectURL(preferences, rawUrl)
        var targetUrl = com.petal.browser.unit.BrowserUnit.queryWrapper(appContext, redirected)
        val httpsOnly = preferences.getBoolean(
            "sp_https_only",
            preferences.getBoolean("profileStandard_httpsOnly", false)
        )
        if (httpsOnly && targetUrl.startsWith("http://", ignoreCase = true)) {
            targetUrl = "https://" + targetUrl.substring(7)
        }
        if (com.petal.browser.unit.BrowserUnit.isHomePage(targetUrl) ||
            com.petal.browser.unit.BrowserUnit.isHomePage(rawUrl) ||
            targetUrl.equals("about:blank", ignoreCase = true)
        ) {
            pageUrl = "about:blank"
            pageTitle = "Petal Home"
            observedSession?.loadUrl("about:blank")
        } else {
            applyPageSettings(targetUrl)
            pageUrl = targetUrl
            tab?.let {
                browserStore.dispatch(ContentAction.UpdateUrlAction(it.id, targetUrl))
            }
            observedSession?.loadUrl(targetUrl)
        }
        publishState()
    }

    fun reload() {
        applyPageSettings(pageUrl)
        // Reload the live GeckoSession so we always refresh the page currently shown, not the
        // engine session's original (initial) load request.
        val live = getGeckoSession()
        if (live != null) {
            try {
                live.reload(org.mozilla.geckoview.GeckoSession.LOAD_FLAGS_NONE)
                return
            } catch (_: Throwable) {
            }
        }
        observedSession?.reload()
    }

    fun loadData(data: String, mimeType: String, encoding: String) {
        observedSession?.loadData(data, mimeType, encoding)
    }

    fun findAll(query: String) {
        observedSession?.findAll(query)
    }

    fun findNext(forward: Boolean) {
        observedSession?.findNext(forward)
    }

    fun clearFindMatches() {
        observedSession?.clearFindMatches()
    }

    fun stopLoading() {
        observedSession?.stopLoading()
    }

    fun setDesktopMode(enabled: Boolean) {
        observedSession?.toggleDesktopMode(enabled, reload = true)
    }

    private fun applyPageSettings(url: String?) {
        val session = observedSession ?: return
        val profile = com.petal.browser.view.PetalGeckoView.getProfile(appContext)
        val javascriptEnabled = preferences.getBoolean(
            "sp_javascript",
            preferences.getBoolean("${profile}_javascript", preferences.getBoolean("profileStandard_javascript", true))
        )
        // Android Components' Settings.javascriptEnabled is an UnsupportedSetting on the
        // Gecko engine session and throws UnsupportedSettingException. Apply it on the raw
        // GeckoSession instead. The global runtime value is also synced below.
        try {
            getGeckoSession()?.settings?.allowJavascript = javascriptEnabled
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabViewController", "Could not apply per-session JavaScript setting", t)
        }

        val host = try { android.net.Uri.parse(url.orEmpty()).host } catch (_: Throwable) { null }
        val desktopEnabled = if (!host.isNullOrBlank() && preferences.contains("sp_desktop_site_$host")) {
            preferences.getBoolean("sp_desktop_site_$host", false)
        } else {
            preferences.getBoolean("${profile}_desktop", preferences.getBoolean("sp_desktop_site", false))
        }
        session.toggleDesktopMode(desktopEnabled, reload = false)

        val trackingProtectionEnabled = com.petal.browser.browser.PetalAdBlockEngine.isAdBlockEnabled(appContext) &&
            (host.isNullOrBlank() || !com.petal.browser.browser.PetalAdBlockEngine.isDomainWhitelisted(host))
        setTrackingProtection(trackingProtectionEnabled)
        com.petal.browser.engine.gecko.PetalGeckoRuntime.syncPreferences(preferences)
    }

    fun setTrackingProtection(enabled: Boolean) {
        observedSession?.updateTrackingProtection(
            if (enabled) {
                EngineSession.TrackingProtectionPolicy.strict()
            } else {
                EngineSession.TrackingProtectionPolicy.none()
            }
        )
    }

    fun attachLifecycle(owner: LifecycleOwner) {
        if (attachedLifecycle === owner.lifecycle) return
        attachedLifecycle?.removeObserver(engineLifecycleObserver)
        attachedLifecycle = owner.lifecycle
        attachedLifecycle?.addObserver(engineLifecycleObserver)
    }

    fun goBack(userInteraction: Boolean = true) {
        val current = tab ?: return
        if (backAvailable) {
            browserStore.dispatch(
                mozilla.components.browser.state.action.EngineAction.GoBackAction(
                    current.id,
                    userInteraction
                )
            )
        }
    }

    fun goForward(userInteraction: Boolean = true) {
        val current = tab ?: return
        if (forwardAvailable) {
            browserStore.dispatch(
                mozilla.components.browser.state.action.EngineAction.GoForwardAction(
                    current.id,
                    userInteraction
                )
            )
        }
    }

    /** Let page-owned modal UI consume Back before the browser traverses tab history. */
    fun processBackPressed(onUnhandled: () -> Unit) {
        val session = observedSession ?: run {
            onUnhandled()
            return
        }
        if (fullScreenFeature?.onBackPressed() == true) return
        session.processBackPressed { handled ->
            if (handled) return@processBackPressed
            if (backAvailable) goBack() else onUnhandled()
        }
    }

    fun canGoBack(): Boolean = backAvailable

    fun canGoForward(): Boolean = forwardAvailable

    fun getTabId(): String? = boundTabId

    fun getCachedPreviewBitmap(): Bitmap? = TabThumbnailCache.getMemoryOnly(boundTabId, isIncognito())

    @JvmOverloads
    fun updatePreviewCache(force: Boolean = false) {
        if (!force && capturedPreviewRevision == previewRevision) return
        capturePreviewBitmapAsync { }
    }

    /**
     * Same gate Firefox's BrowserThumbnails uses: a thumbnail is only requested once the engine
     * has reported a first contentful paint for the tab (BrowserStore tracks this, and resets it
     * when GeckoView releases its compositor resources).
     */
    private fun hasContentfulPaint(): Boolean {
        val id = boundTabId ?: return false
        return browserStore.state.tabs.firstOrNull { it.id == id }?.content?.firstContentfulPaint == true
    }

    /** Bounded retry used while the compositor has not painted yet or returned a blank frame. */
    private fun schedulePreviewRetry() {
        if (previewRetryCount >= 5) return
        previewRetryCount++
        postDelayed({
            try { updatePreviewCache() } catch (_: Throwable) {}
        }, 300L * previewRetryCount)
    }

    fun capturePreviewBitmapAsync(callback: (Bitmap?) -> Unit) = capturePreview(false, callback)

    /**
     * Explicit capture right before the tab manager opens (Chromium's CacheTab-before-switch
     * flow). The user is looking at this exact frame, so a plain dark/white page is a valid
     * thumbnail; only a fully transparent frame is rejected. Callable from Java.
     */
    fun captureForSwitcher(callback: java.util.function.Consumer<Bitmap?>) {
        capturePreview(true) { callback.accept(it) }
    }

    private fun capturePreview(forceAccept: Boolean, callback: (Bitmap?) -> Unit) {
        val key = boundTabId ?: run { callback(null); return }
        val revision = previewRevision
        val privateTab = isIncognito()
        // GeckoEngineView (android-components) is a FrameLayout that CONTAINS the real
        // GeckoView; asView() never returns the GeckoView itself, so a direct cast is always null.
        val geckoView = findGeckoView(engineView.asView())
        if (geckoView == null || !isAttachedToWindow || geckoView.width <= 0 || geckoView.height <= 0) {
            callback(null)
            return
        }
        try {
            geckoView.capturePixels().then({ bitmap: Bitmap? ->
                val current = key == boundTabId
                var stored = false
                var storedBitmap: Bitmap? = null
                if (bitmap != null && current) {
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
                    stored = TabThumbnailCache.put(key, scaled, privateTab, forceAccept || hasContentfulPaint())
                    if (stored) { capturedPreviewRevision = revision; storedBitmap = scaled } else schedulePreviewRetry()
                }
                callback(if (current && stored) storedBitmap else null)
                org.mozilla.geckoview.GeckoResult.fromValue<Void?>(null)
            }, {
                callback(null)
                org.mozilla.geckoview.GeckoResult.fromValue<Void?>(null)
            })
        } catch (_: Throwable) {
            callback(null)
        }
    }

    private fun findGeckoView(root: View?): org.mozilla.geckoview.GeckoView? {
        if (root is org.mozilla.geckoview.GeckoView) return root
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                findGeckoView(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    /** GeckoView-only integration point used for WebExtension delegates. */
    fun getGeckoSession(): org.mozilla.geckoview.GeckoSession? =
        com.petal.browser.view.PetalGeckoView.getEngineGeckoSession(observedSession)

    fun getMediaBridge(): com.petal.browser.media.PetalMediaBridge? = mediaBridge

    fun setTabGroupId(value: String?) { tabGroupId = value }

    fun setTabGroupTitle(value: String?) { tabGroupTitle = value }

    fun getTabGroupId(): String? = tabGroupId

    fun getTabGroupTitle(): String? = tabGroupTitle

    fun setPredecessor(value: AlbumController?) { predecessor = value }

    fun getPredecessor(): AlbumController? = predecessor

    private var pwaManager: com.petal.browser.pwa.PetalPwaManager? = null

    fun getPwaManager(): com.petal.browser.pwa.PetalPwaManager? = pwaManager

    fun setPwaManager(manager: com.petal.browser.pwa.PetalPwaManager?) {
        this.pwaManager = manager
    }

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null) {
        try {
            val session = observedSession
            if (session != null) {
                session.loadUrl("javascript:(function(){ try { return ($script); } catch(e) { return 'ERROR: ' + e; } })()")
                callback?.invoke(null)
            } else {
                callback?.invoke(null)
            }
        } catch (t: Throwable) {
            callback?.invoke("ERROR: ${t.message}")
        }
    }

    fun captureFullPageBitmap(callback: (Bitmap?) -> Unit) {
        val geckoView = findGeckoView(engineView.asView())
        if (geckoView == null || !isAttachedToWindow || geckoView.width <= 0 || geckoView.height <= 0) {
            callback(null)
            return
        }
        try {
            geckoView.capturePixels().then({ bitmap ->
                callback(bitmap)
                org.mozilla.geckoview.GeckoResult.fromValue<Void?>(null)
            }, {
                callback(null)
                org.mozilla.geckoview.GeckoResult.fromValue<Void?>(null)
            })
        } catch (_: Throwable) {
            callback(null)
        }
    }

    fun setUserAgent(customUserAgent: String?) {
        try {
            val gs = getGeckoSession() ?: return
            if (!customUserAgent.isNullOrBlank()) {
                gs.settings.userAgentMode = org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                gs.settings.userAgentOverride = customUserAgent
            } else {
                gs.settings.userAgentOverride = null
                val desktopEnabled = preferences.getBoolean("sp_desktop", false)
                gs.settings.userAgentMode = if (desktopEnabled) {
                    org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                } else {
                    org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("PetalTabViewController", "Failed to setUserAgent: ${e.message}")
        }
    }

    fun getFavicon(): Bitmap? = tab?.content?.icon

    fun currentState(): State? = tab?.let {
        State(it.id, pageUrl, pageTitle, progress, loading, backAvailable, forwardAvailable, isSecure, it.content.private)
    }

    fun isPageAtTop(): Boolean {
        if (pageUrl.isBlank() || pageUrl.equals("about:blank", ignoreCase = true) || com.petal.browser.unit.BrowserUnit.isHomePage(pageUrl)) {
            return true
        }
        val view = engineView.asView()
        return !view.canScrollVertically(-1)
    }

    override fun canChildScrollUp(): Boolean {
        return !isPageAtTop()
    }

    override fun getAlbumView(): View = this

    @MainThread
    override fun activate() {
        active = true
        tab?.let { PetalEngineStore.selectTab(appContext, it.id) }
        com.petal.browser.extensions.PetalExtensionManager.setActiveBrowserSession(null, getGeckoSession())
        refreshFeature?.start()
        fullScreenFeature?.start()
        startThumbnails()
    }

    /**
     * Port of Firefox's BrowserThumbnails: observe the store and, whenever the SELECTED tab is
     * not loading and has reported a first contentful paint, capture a thumbnail.
     */
    private var thumbnailSubscription: mozilla.components.lib.state.Store.Subscription<
        mozilla.components.browser.state.state.BrowserState,
        mozilla.components.browser.state.action.BrowserAction>? = null
    private var lastThumbnailSignal: String? = null

    private fun startThumbnails() {
        if (thumbnailSubscription != null) return
        lastThumbnailSignal = null
        thumbnailSubscription = browserStore.observeManually { state ->
            val id = boundTabId ?: return@observeManually
            if (state.selectedTabId != id) return@observeManually
            val content = state.tabs.firstOrNull { it.id == id }?.content ?: return@observeManually
            val signal = "${content.loading}|${content.firstContentfulPaint}|${content.url}"
            if (signal == lastThumbnailSignal) return@observeManually
            lastThumbnailSignal = signal
            if (!content.loading && content.firstContentfulPaint) {
                post { try { capturePreviewBitmapAsync { } } catch (_: Throwable) {} }
            }
        }.also { it.resume() }
    }

    private fun stopThumbnails() {
        thumbnailSubscription?.unsubscribe()
        thumbnailSubscription = null
    }

    @MainThread
    override fun deactivate() {
        updatePreviewCache()
        stopThumbnails()
        active = false
        com.petal.browser.extensions.PetalExtensionManager.setActiveBrowserSession(getGeckoSession(), null)
        refreshFeature?.stop()
        fullScreenFeature?.stop()
        isRefreshing = false
    }

    override fun getTitle(): String = pageTitle

    override fun getUrl(): String = pageUrl

    override fun isIncognito(): Boolean = tab?.content?.private ?: false

    override fun destroy() {
        stopThumbnails()
        val removedTabId = tab?.id
        active = false
        if (isIncognito()) {
            removedTabId?.let(TabThumbnailCache::removePrivate)
        } else {
            removedTabId?.let(TabThumbnailCache::remove)
        }
        com.petal.browser.extensions.PetalExtensionManager.setActiveBrowserSession(getGeckoSession(), null)
        refreshFeature?.stop()
        refreshFeature = null
        fullScreenFeature?.stop()
        fullScreenFeature = null
        observedSession?.unregister(observer)
        observedSession = null
        tab = null
        predecessor = null
        onBrowserStateChanged = null
        attachedLifecycle?.removeObserver(engineLifecycleObserver)
        attachedLifecycle = null
        engineView.release()
        if (removedTabId != null) PetalEngineStore.removeTab(appContext, removedTabId)
    }

    private fun injectPagePrivacyAndAdBlock() {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext)
        val adBlockEnabled = sp.getBoolean("sp_ad_block", sp.getBoolean("profileStandard_adBlock", true))
        val dntGpc = sp.getBoolean("sp_dnt_gpc", sp.getBoolean("profileStandard_dnt", true))
        val webrtcProtection = sp.getBoolean("sp_webrtc_protection", sp.getBoolean("profileStandard_webrtcProtection", true))
        val trimReferrers = sp.getBoolean("sp_trim_referrers", true)
        val fingerprintProtection = sp.getBoolean("sp_fingerprint_protection", sp.getBoolean("profileStandard_fingerPrintProtection", true))

        if (pageUrl.isBlank() || pageUrl.startsWith("about:") || pageUrl.startsWith("chrome:") || pageUrl.startsWith("moz-extension:")) return
        val gs = getGeckoSession() ?: return

        try {
            // 1. Inject uBlock cosmetic filtering and scriptlets
            if (adBlockEnabled) {
                val cosmeticScript = com.petal.browser.browser.PetalAdBlockEngine.getuBlockCosmeticAndScriptletPayload(pageUrl)
                if (cosmeticScript.isNotBlank()) {
                    gs.loadUri(cosmeticScript)
                }
            }

            // 2. Client-side privacy scripts: DNT/GPC, WebRTC IP masking, Referrer trimming, Canvas anti-fingerprinting
            val host = try { android.net.Uri.parse(pageUrl).host?.lowercase() ?: "" } catch (_: Throwable) { "" }
            val isSearchOrCaptchaDomain = host.contains("google.") || host.contains("gstatic.") ||
                host.contains("recaptcha") || host.contains("hcaptcha") || host.contains("cloudflare") ||
                host.contains("bing.com") || host.contains("duckduckgo.com")

            val sb = java.lang.StringBuilder("(function() {\n")
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
            if (fingerprintProtection && !isSearchOrCaptchaDomain) {
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
            val js = sb.toString()
            if (js.length > "(function() {\n})();".length) {
                gs.loadUri("javascript:$js")
            }
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabViewController", "Failed to inject privacy/adblock protections: ${t.message}")
        }
    }

    private fun recordHistoryVisit(targetUrl: String, titleToRecord: String? = null, force: Boolean = false) {
        if (isIncognito() || targetUrl.isBlank() || targetUrl.equals("about:blank", ignoreCase = true) ||
            targetUrl.startsWith("about:", ignoreCase = true) || com.petal.browser.unit.BrowserUnit.isHomePage(targetUrl)) {
            return
        }
        if (!force && targetUrl == lastRecordedHistoryUrl) {
            return
        }
        val rawTitle = titleToRecord ?: pageTitle
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
            val action = com.petal.browser.database.RecordAction(appContext)
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

    private fun publishState() {
        val current = tab ?: return
        onBrowserStateChanged?.invoke(
            State(
                tabId = current.id,
                url = pageUrl,
                title = pageTitle,
                progress = progress,
                loading = loading,
                canGoBack = backAvailable,
                canGoForward = forwardAvailable,
                isSecure = isSecure,
                isPrivate = current.content.private
            )
        )
    }
}

/**
 * Forwards navigation callbacks to the engine's delegate, adding:
 * 1. External-app scheme handling (intent://, tel:, market:// ...)
 * 2. HTTPS-Only upgrade interceptor (GeckoView official pattern)
 * 3. Open Redirect Links in Background handling
 */
/**
 * Preserves Mozilla engine's own ContentDelegate (for title, fullScreen, crash,
 * and firstContentfulPaint callbacks) while handling Petal's onExternalResponse and onContextMenu.
 */
private class PetalContentDelegateWrapper(
    val engine: org.mozilla.geckoview.GeckoSession.ContentDelegate?,
    private val contextProvider: () -> android.content.Context
) : org.mozilla.geckoview.GeckoSession.ContentDelegate {

    private fun resolveActivity(): android.app.Activity? {
        var ctx: android.content.Context? = contextProvider()
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? android.app.Activity
    }

    override fun onTitleChange(session: org.mozilla.geckoview.GeckoSession, title: String?) {
        engine?.onTitleChange(session, title)
    }

    override fun onFullScreen(session: org.mozilla.geckoview.GeckoSession, fullScreen: Boolean) {
        val act = resolveActivity()
        if (act is com.petal.browser.activity.BrowserActivity) {
            act.runOnUiThread {
                act.setCustomFullscreen(fullScreen)
            }
        }
        engine?.onFullScreen(session, fullScreen)
    }

    override fun onCloseRequest(session: org.mozilla.geckoview.GeckoSession) {
        engine?.onCloseRequest(session)
    }

    override fun onFocusRequest(session: org.mozilla.geckoview.GeckoSession) {
        engine?.onFocusRequest(session)
    }

    override fun onCrash(session: org.mozilla.geckoview.GeckoSession) {
        engine?.onCrash(session)
    }

    override fun onKill(session: org.mozilla.geckoview.GeckoSession) {
        engine?.onKill(session)
    }

    override fun onFirstContentfulPaint(session: org.mozilla.geckoview.GeckoSession) {
        engine?.onFirstContentfulPaint(session)
    }

    override fun onPaintStatusReset(session: org.mozilla.geckoview.GeckoSession) {
        engine?.onPaintStatusReset(session)
    }

    override fun onContextMenu(
        session: org.mozilla.geckoview.GeckoSession,
        screenX: Int,
        screenY: Int,
        element: org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement
    ) {
        val act = resolveActivity()
        if (act is com.petal.browser.activity.BrowserActivity) {
            val linkUri = element.linkUri
            val srcUri = element.srcUri
            val elemType = element.type
            val linkText = runCatching {
                element.javaClass.getField("textContent").get(element) as? String
            }.getOrNull()

            act.runOnUiThread {
                com.petal.browser.compose.menu.BrowserContextMenuManager.handleContextMenu(
                    activity = act,
                    elemType = elemType,
                    linkUri = linkUri,
                    srcUri = srcUri,
                    linkText = linkText
                )
            }
        }
        engine?.onContextMenu(session, screenX, screenY, element)
    }

    override fun onExternalResponse(
        session: org.mozilla.geckoview.GeckoSession,
        response: org.mozilla.geckoview.WebResponse
    ) {
        val act = resolveActivity()
        if (act != null) {
            com.petal.browser.view.PetalGeckoView.handleExternalResponse(act, response)
        }
        engine?.onExternalResponse(session, response)
    }

    override fun onShowDynamicToolbar(session: org.mozilla.geckoview.GeckoSession) {
        engine?.onShowDynamicToolbar(session)
    }

    override fun onWebAppManifest(session: org.mozilla.geckoview.GeckoSession, manifest: org.json.JSONObject) {
        engine?.onWebAppManifest(session, manifest)
    }

    override fun onMetaViewportFitChange(session: org.mozilla.geckoview.GeckoSession, viewportFit: String) {
        engine?.onMetaViewportFitChange(session, viewportFit)
    }
}

/**
 * Forwards every NavigationDelegate callback explicitly to Mozilla GeckoEngine's own delegate
 * without using Kotlin `by` interface delegation (which drops Java default methods per KT-18324).
 * Adds:
 * 1. External-app scheme handling (intent://, tel:, market:// ...)
 * 2. HTTPS-Only upgrade interceptor (GeckoView official pattern)
 * 3. Open Redirect Links in Background handling
 * 4. Immediate update of pageUrl and UI state on onLocationChange.
 */
private class ExternalSchemeNavigationDelegate(
    val engine: org.mozilla.geckoview.GeckoSession.NavigationDelegate,
    private var context: android.content.Context,
    private var currentUrlSupplier: () -> String,
    private var isIncognitoSupplier: () -> Boolean,
    private val onLocationChanged: (String) -> Unit,
    private val handleExternal: (String) -> Boolean
) : org.mozilla.geckoview.GeckoSession.NavigationDelegate {

    fun updateDependencies(
        newContext: android.content.Context,
        newUrlSupplier: () -> String,
        newIncognitoSupplier: () -> Boolean
    ) {
        context = newContext
        currentUrlSupplier = newUrlSupplier
        isIncognitoSupplier = newIncognitoSupplier
    }

    override fun onLocationChange(
        session: org.mozilla.geckoview.GeckoSession,
        url: String?,
        perms: MutableList<org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean
    ) {
        android.util.Log.d("PetalTabNav", "ExternalSchemeNavigationDelegate: onLocationChange (4-arg): url=$url gesture=$hasUserGesture")
        if (!url.isNullOrBlank()) {
            onLocationChanged(url)
        }
        try {
            engine.onLocationChange(session, url, perms, hasUserGesture)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "Error forwarding onLocationChange(4-arg): ${t.message}")
        }
    }

    override fun onCanGoBack(session: org.mozilla.geckoview.GeckoSession, canGoBack: Boolean) {
        android.util.Log.d("PetalTabNav", "ExternalSchemeNavigationDelegate: onCanGoBack=$canGoBack")
        try {
            engine.onCanGoBack(session, canGoBack)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "Error forwarding onCanGoBack: ${t.message}")
        }
    }

    override fun onCanGoForward(session: org.mozilla.geckoview.GeckoSession, canGoForward: Boolean) {
        android.util.Log.d("PetalTabNav", "ExternalSchemeNavigationDelegate: onCanGoForward=$canGoForward")
        try {
            engine.onCanGoForward(session, canGoForward)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "Error forwarding onCanGoForward: ${t.message}")
        }
    }

    override fun onLoadError(
        session: org.mozilla.geckoview.GeckoSession,
        uri: String?,
        error: org.mozilla.geckoview.WebRequestError
    ): org.mozilla.geckoview.GeckoResult<String>? {
        android.util.Log.d("PetalTabNav", "ExternalSchemeNavigationDelegate: onLoadError uri=$uri error=${error.code}")
        return try {
            engine.onLoadError(session, uri, error)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "Error forwarding onLoadError: ${t.message}")
            null
        }
    }

    override fun onSubframeLoadRequest(
        session: org.mozilla.geckoview.GeckoSession,
        request: org.mozilla.geckoview.GeckoSession.NavigationDelegate.LoadRequest
    ): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.AllowOrDeny>? {
        return try {
            engine.onSubframeLoadRequest(session, request)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "Error forwarding onSubframeLoadRequest: ${t.message}")
            null
        }
    }

    override fun onNewSession(
        session: org.mozilla.geckoview.GeckoSession,
        uri: String
    ): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.GeckoSession>? {
        android.util.Log.d("PetalTabNav", "ExternalSchemeNavigationDelegate: onNewSession uri=$uri")
        return try {
            engine.onNewSession(session, uri)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "Error forwarding onNewSession: ${t.message}")
            null
        }
    }

    override fun onLoadRequest(
        session: org.mozilla.geckoview.GeckoSession,
        request: org.mozilla.geckoview.GeckoSession.NavigationDelegate.LoadRequest
    ): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.AllowOrDeny>? {
        val uri = request.uri
        android.util.Log.d("PetalTabNav", "ExternalSchemeNavigationDelegate: onLoadRequest uri=$uri target=${request.target}")
        if (handleExternal(uri)) {
            return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.DENY)
        }

        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)

        // 1. HTTPS-Only Mode Upgrade
        val httpsOnly = sp.getBoolean("sp_https_only", sp.getBoolean("profileStandard_httpsOnly", false))
        if (httpsOnly && uri.startsWith("http://", ignoreCase = true) &&
            !uri.startsWith("http://localhost", ignoreCase = true) &&
            !uri.startsWith("http://127.0.0.1", ignoreCase = true) &&
            !uri.startsWith("http://10.", ignoreCase = true) &&
            !uri.startsWith("http://192.168.", ignoreCase = true)
        ) {
            val upgraded = "https://" + uri.substring(7)
            session.loadUri(upgraded)
            return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.DENY)
        }

        // 2. Open Redirect Links in Background
        val openRedirectsInBackground = sp.getBoolean("sp_open_redirects_in_background", false)
        if (openRedirectsInBackground && request.target != org.mozilla.geckoview.GeckoSession.NavigationDelegate.TARGET_WINDOW_CURRENT) {
            val currentUrl = currentUrlSupplier()
            val currentHost = try { android.net.Uri.parse(currentUrl).host?.lowercase() } catch (_: Throwable) { null }
            val reqHost = try { android.net.Uri.parse(uri).host?.lowercase() } catch (_: Throwable) { null }
            if (!currentHost.isNullOrBlank() && !reqHost.isNullOrBlank() && currentHost != reqHost) {
                var ctx: android.content.Context? = context
                while (ctx is android.content.ContextWrapper) {
                    if (ctx is com.petal.browser.activity.BrowserActivity) break
                    ctx = ctx.baseContext
                }
                val act = ctx as? com.petal.browser.activity.BrowserActivity
                if (act != null) {
                    act.runOnUiThread {
                        act.addAlbum(null, uri, false, isIncognitoSupplier())
                    }
                    return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.DENY)
                }
            }
        }

        return try {
            engine.onLoadRequest(session, request)
                ?: org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.ALLOW)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "Error forwarding onLoadRequest: ${t.message}")
            org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.ALLOW)
        }
    }
}
