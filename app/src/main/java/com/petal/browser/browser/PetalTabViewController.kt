package com.petal.browser.browser

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.View
import androidx.preference.PreferenceManager
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.petal.browser.R
import com.petal.browser.unit.TabThumbnailCache
import com.petal.browser.view.PetalCrashOverlayHelper
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
import kotlinx.coroutines.launch

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
) : SwipeRefreshLayout(context, attrs), PetalTabSurface, PetalTabController, AlbumController, EngineView by engineView {

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) :
        this(context, attrs, PetalEngineStore.createEngineView(context))

    private val appContext = context.applicationContext
    private val browserStore: BrowserStore = PetalEngineStore.getStore(appContext)
    private var tab: TabSessionState? = null
    private var boundTabId: String? = null
    private var observedSession: EngineSession? = null
    private var active = false
    private var engineScrollDelegate: org.mozilla.geckoview.GeckoSession.ScrollDelegate? = null
    private var engineHistoryDelegate: org.mozilla.geckoview.GeckoSession.HistoryDelegate? = null
    private var engineContentBlockingDelegate: org.mozilla.geckoview.ContentBlocking.Delegate? = null
    private var previewRevision = 0L
    @Volatile
    private var capturedPreviewRevision = -1L
    private var previewCaptureSequence = 0L
    private var previewRetryCount = 0
    private var pageTitle = ""
    private var pageUrl = "about:blank"
    // URL of a real (non-home) page we just asked the engine to load. A brand-new engine session
    // first reports its own initial "about:blank" document; without this guard that report
    // overwrites pageUrl, the activity thinks the tab is Home, shows the Home screen and calls
    // stopLoading() - which kills the link that was opened from another app.
    private var pendingNavUrl: String? = null
    private var pendingNavSince = 0L

    // Most recent GeckoView session state (back/forward history, scroll, zoom, form data) reported by
    // Gecko through ProgressDelegate.onSessionStateChange. Kept as the raw object and only serialized
    // on demand (see captureEngineState) because Gecko emits these updates very frequently.
    @Volatile
    private var latestGeckoState: org.mozilla.geckoview.GeckoSession.SessionState? = null

    private fun isStaleInitialBlank(newUrl: String): Boolean {
        if (!newUrl.equals("about:blank", ignoreCase = true)) return false
        if (pendingNavUrl == null) return false
        return android.os.SystemClock.uptimeMillis() - pendingNavSince < 8000L
    }
    private var backAvailable = false
    private var forwardAvailable = false

    // True from the moment saved history is restored into a fresh session until the first real page has
    // finished loading. In that window Gecko has not reported its history yet, so backAvailable is still
    // false even though the restored tab can go back. While pending, Back is sent to Gecko anyway instead
    // of falling through to Home.
    @Volatile
    private var historyPending = false
    @Volatile
    private var historyPendingSince = 0L
    // Set once the restored session has reported a real (non-blank) location. Until then any
    // "loading finished" callback belongs to the session's own initial about:blank document.
    @Volatile
    private var restoreLocationSeen = false

    // Own URL trail of this tab (final URLs only), persisted next to the Gecko session state.
    // It is the safety net for Back after a restart: if Gecko cannot restore its history stack
    // (state missing/corrupt/rejected), Back still walks this trail instead of dropping to Home.
    private val navUrls = ArrayList<String>()
    private var navIndex = -1
    private var navFallbackEligible = false

    /** Pending restored history is only trusted briefly; afterwards the URL trail takes over. */
    private fun isHistoryPending(): Boolean =
        historyPending && android.os.SystemClock.uptimeMillis() - historyPendingSince < 4000L

    private fun trackNavigation(url: String) {
        if (isIncognito() || url.isBlank() || url.equals("about:blank", ignoreCase = true) ||
            com.petal.browser.unit.PetalBrowserUtils.isHomePage(url)) return
        if (navIndex in navUrls.indices && navUrls[navIndex] == url) return
        when {
            navIndex > 0 && navUrls[navIndex - 1] == url -> navIndex--
            navIndex >= 0 && navIndex + 1 < navUrls.size && navUrls[navIndex + 1] == url -> navIndex++
            else -> {
                while (navUrls.size > navIndex + 1) navUrls.removeAt(navUrls.size - 1)
                navUrls.add(url)
                navIndex = navUrls.size - 1
                while (navUrls.size > 40) { navUrls.removeAt(0); navIndex-- }
            }
        }
    }

    /** Snapshot of the URL trail (oldest first, current page last) for persistence. */
    fun captureNavUrls(): List<String>? {
        if (isIncognito() || navUrls.size < 2) return null
        return ArrayList(navUrls)
    }

    /** Re-installs a persisted URL trail. Call right after bindTab, before loading/restoring. */
    @MainThread
    fun restoreNavTrail(urls: List<String>, currentUrl: String) {
        navUrls.clear()
        navUrls.addAll(urls.filter { it.isNotBlank() }.takeLast(40))
        if (navUrls.isEmpty() || navUrls.last() != currentUrl) navUrls.add(currentUrl)
        navIndex = navUrls.size - 1
        navFallbackEligible = navUrls.size > 1
    }

    private fun canTrailBack(): Boolean = navFallbackEligible && navIndex > 0

    private fun trailBack() {
        val target = navUrls.getOrNull(navIndex - 1) ?: return
        val gs = getGeckoSession() ?: return
        pendingNavUrl = target
        pendingNavSince = android.os.SystemClock.uptimeMillis()
        try {
            // REPLACE_HISTORY keeps Gecko's own stack at a single entry, so the next Back walks
            // the trail again instead of bouncing between two Gecko entries.
            gs.load(
                org.mozilla.geckoview.GeckoSession.Loader()
                    .uri(target)
                    .flags(org.mozilla.geckoview.GeckoSession.LOAD_FLAGS_REPLACE_HISTORY)
            )
        } catch (_: Throwable) {
            observedSession?.loadUrl(target)
        }
    }

    private fun onGeckoStateChanged(state: org.mozilla.geckoview.GeckoSession.SessionState) {
        latestGeckoState = state
    }
    private var loading = false
    private var progress = 0
    private var isSecure = false
    private var tabGroupId: String? = null
    private var tabGroupTitle: String? = null
    private var predecessor: AlbumController? = null
    var isAdoptedPopup: Boolean = false
    private var mediaTitle = ""
    private var mediaPosition: mozilla.components.concept.engine.mediasession.MediaSession.PositionState? = null
    private var mediaBridge: com.petal.browser.media.PetalMediaBridge? = null
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(appContext) }
    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null) return@OnSharedPreferenceChangeListener
        when (key) {
            "sp_javascript", "profileStandard_javascript" -> {
                val profile = com.petal.browser.engine.gecko.PetalEngineBridge.getProfile(appContext)
                val jsEnabled = preferences.getBoolean(
                    "sp_javascript",
                    preferences.getBoolean("${profile}_javascript", preferences.getBoolean("profileStandard_javascript", true))
                )
                try {
                    getGeckoSession()?.settings?.allowJavascript = jsEnabled
                } catch (t: Throwable) {
                    android.util.Log.w("PetalTabViewController", "Failed to update allowJavascript: ${t.message}")
                }
            }
            "sp_ad_block", "sp_etp_strict", "profileStandard_adBlock" -> {
                val host = try { android.net.Uri.parse(pageUrl.orEmpty()).host } catch (_: Throwable) { null }
                val effectiveMode = com.petal.browser.browser.PetalAdBlockEngine.getEffectiveMode(appContext, host)
                applyTrackingProtectionMode(effectiveMode)
            }
            "sp_desktop_site" -> {
                val profile = com.petal.browser.engine.gecko.PetalEngineBridge.getProfile(appContext)
                val host = try { android.net.Uri.parse(pageUrl.orEmpty()).host } catch (_: Throwable) { null }
                val desktopEnabled = if (!host.isNullOrBlank() && preferences.contains("sp_desktop_site_$host")) {
                    preferences.getBoolean("sp_desktop_site_$host", false)
                } else {
                    preferences.getBoolean("${profile}_desktop", preferences.getBoolean("sp_desktop_site", false))
                }
                observedSession?.toggleDesktopMode(desktopEnabled, reload = false)
            }
            "sp_font_size_scale", "sp_zoom_level_scale", "sp_force_enable_zoom", "sp_caret_browsing" -> {
                com.petal.browser.accessibility.PetalAccessibilityEngine.applyAccessibilitySettings(this@PetalTabViewController)
            }
            "sp_liquid_glass_enabled", "sp_liquid_glass_omnibox", "sp_liquid_glass_bottom_nav" -> {
                if (isHostedByBrowserActivity() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    post {
                        findGeckoView(engineView.asView())?.let { gv ->
                            try {
                                com.petal.browser.ui.glass.GeckoGlassBackend.applyBackend(gv, context)
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
        }
    }

    @Volatile
    var currentScrollY: Int = 0
        private set
    @Volatile
    var currentScrollX: Int = 0
        private set

    private var lastLayoutChangeTime: Long = 0L
    private var lastBarStateChangeTime: Long = 0L
    private val pendingScrollUpdate = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastProcessedScrollY: Int = 0
    private var accumulatedScrollY: Int = 0
    private var consecutiveDirectionSteps: Int = 0
    private var lastDirection: Int = 0
    private val settleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val settleRunnable = Runnable {
        val act = resolveActivity(context) as? com.petal.browser.activity.BrowserActivity ?: return@Runnable
        val currentFrac = com.petal.browser.ui.components.BarCollapseState.collapsedFraction
        val target = if (currentFrac >= 0.5f) 1f else 0f
        act.settlePillFraction(target)
    }

    private val scrollDispatcher = Runnable {
        pendingScrollUpdate.set(false)
        android.os.Trace.beginSection("PetalTab:handleScrollChanged")
        try {
            val act = resolveActivity(context) as? com.petal.browser.activity.BrowserActivity ?: return@Runnable
            val now = android.os.SystemClock.uptimeMillis()

            // 1. Ignore scroll changes caused by viewport or layout resizes
            if (now - lastLayoutChangeTime < 350L) {
                lastProcessedScrollY = currentScrollY
                accumulatedScrollY = 0
                consecutiveDirectionSteps = 0
                return@Runnable
            }

            // 2. Ignore deltas while bars animate
            if (act.isBarAnimationRunning()) {
                lastProcessedScrollY = currentScrollY
                accumulatedScrollY = 0
                consecutiveDirectionSteps = 0
                return@Runnable
            }

            val isPill = com.petal.browser.ui.components.BarPrefs.isPillCollapse(androidx.preference.PreferenceManager.getDefaultSharedPreferences(context))

            // 3. Cooldown after each state change (only for non-pill hide mode)
            if (!isPill && now - lastBarStateChangeTime < 350L) {
                lastProcessedScrollY = currentScrollY
                accumulatedScrollY = 0
                consecutiveDirectionSteps = 0
                return@Runnable
            }

            val density = context.resources.displayMetrics.density
            val topThreshold = (2f * density).toInt()
            val hideThreshold = (56f * density).toInt()
            val showThreshold = (24f * density).toInt()

            val y = currentScrollY
            val deltaY = y - lastProcessedScrollY
            lastProcessedScrollY = y

            // At the top of the page: bars are always shown
            if (y <= topThreshold) {
                settleHandler.removeCallbacks(settleRunnable)
                if (isPill) {
                    act.settlePillFraction(0f)
                } else if (act.isAddressBarCollapsed()) {
                    accumulatedScrollY = 0
                    consecutiveDirectionSteps = 0
                    lastBarStateChangeTime = now
                    act.animateAddressBarCollapse(false)
                }
                return@Runnable
            }

            if (deltaY == 0) return@Runnable

            val currentDirection = if (deltaY > 0) 1 else -1

            if (isPill) {
                settleHandler.removeCallbacks(settleRunnable)
                if (currentDirection != lastDirection) {
                    lastDirection = currentDirection
                    // Settle with spring when reversing
                    val target = if (currentDirection > 0) 1f else 0f
                    act.settlePillFraction(target)
                } else {
                    // Driven directly by scroll offset (no timer)
                    act.onScrollOffsetDelta(deltaY.toFloat())
                    // Settle with spring when scrolling stops
                    settleHandler.postDelayed(settleRunnable, 75L)
                }
                return@Runnable
            }

            if (currentDirection != lastDirection) {
                accumulatedScrollY = 0
                consecutiveDirectionSteps = 0
                lastDirection = currentDirection
            }

            accumulatedScrollY += deltaY
            consecutiveDirectionSteps++

            // During fast flings or scroll, require sustained direction (at least 2 consecutive frames in same direction)
            if (consecutiveDirectionSteps >= 2) {
                if (accumulatedScrollY > hideThreshold) {
                    if (!act.isAddressBarCollapsed()) {
                        accumulatedScrollY = 0
                        consecutiveDirectionSteps = 0
                        lastBarStateChangeTime = now
                        act.animateAddressBarCollapse(true)
                    }
                } else if (accumulatedScrollY < -showThreshold) {
                    if (act.isAddressBarCollapsed()) {
                        accumulatedScrollY = 0
                        consecutiveDirectionSteps = 0
                        lastBarStateChangeTime = now
                        act.animateAddressBarCollapse(false)
                    }
                }
            }
        } finally {
            android.os.Trace.endSection()
        }
    }
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
            dismissCrashErrorState()
            android.util.Log.d("PetalTabNav", "observer.onLocationChange: url=$url gesture=$hasUserGesture oldPageUrl=$pageUrl")
            if (isStaleInitialBlank(url)) {
                android.util.Log.d("PetalTabNav", "ignoring initial about:blank while loading $pendingNavUrl")
                return
            }
            if (!url.equals("about:blank", ignoreCase = true)) {
                pendingNavUrl = null
                restoreLocationSeen = true
                trackNavigation(url)
            }
            if (pageUrl != url) {
                previewRevision++
                previewRetryCount = 0
                currentScrollX = 0
                currentScrollY = 0
                lastProcessedScrollY = 0
                accumulatedScrollY = 0
                consecutiveDirectionSteps = 0
            }
            pageUrl = url
            applyPageSettings(url)
            injectPagePrivacyAndAdBlock()
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
            if (progress in 10..30 || progress in 85..100) {
                injectPagePrivacyAndAdBlock()
            }
        }

        override fun onLoadingStateChange(loading: Boolean) {
            // pageUrl is already the restored URL at this point, so isStaleInitialBlank(pageUrl) can
            // never detect the session's own initial about:blank load finishing. Only a location
            // reported by the restored session counts as "Gecko has the real history now".
            if (!loading && historyPending && restoreLocationSeen) {
                historyPending = false
            }
            if (this@PetalTabViewController.loading != loading && loading) {
                previewRevision++
                previewRetryCount = 0
            }
            this@PetalTabViewController.loading = loading
            tab?.let { browserStore.dispatch(ContentAction.UpdateLoadingStateAction(it.id, loading)) }
            publishState()
            if (!loading) {
                isRefreshing = false
                if (active) {
                    (resolveActivity(context) as? com.petal.browser.activity.BrowserActivity)
                        ?.onActivePageLoadFinished()
                }
                injectPagePrivacyAndAdBlock()
                recordHistoryVisit(pageUrl, pageTitle)
                com.petal.browser.accessibility.PetalAccessibilityEngine.applyAccessibilitySettings(this@PetalTabViewController)
            }
        }

        override fun onNavigationStateChange(canGoBack: Boolean?, canGoForward: Boolean?) {
            canGoBack?.let { backAvailable = it }
            canGoForward?.let { forwardAvailable = it }
            // Gecko has now spoken about its history, so "pending" no longer applies.
            if (canGoBack != null && restoreLocationSeen) historyPending = false
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
        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                lastLayoutChangeTime = android.os.SystemClock.uptimeMillis()
            }
        }
        // Petal's own PullToRefreshFrameLayout owns the pull gesture inside BrowserActivity;
        // disable the Mozilla SwipeRefreshLayout so it never intercepts or draws its spinner.
        if (isHostedByBrowserActivity()) {
            isEnabled = false
        }
        // Tab pages are drawn by android-components' GeckoEngineView, whose inner GeckoView
        // defaults to a SurfaceView (official Mozilla GeckoView default). Switch to TextureView
        // only if Liquid Glass is active and requires page sampling.
        if (isHostedByBrowserActivity() && com.petal.browser.ui.glass.GeckoGlassBackend.shouldSamplePage(context)) {
            findGeckoView(engineView.asView())?.let {
                try { com.petal.browser.ui.glass.GeckoGlassBackend.applyBackend(it, context) } catch (_: Throwable) {}
            }
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
        preferences.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    /** Bind this view to a tab already owned by BrowserStore. */
    @MainThread
    fun bindTab(tab: TabSessionState) {
        dismissCrashErrorState()
        val session = requireNotNull(tab.engineState.engineSession) {
            "Tab ${tab.id} has no engine session"
        }
        if (this.tab?.id == tab.id && observedSession === session) return

        observedSession?.unregister(observer)
        refreshFeature?.stop()
        refreshFeature = null
        if (observedSession !== session) latestGeckoState = null
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
        // Seed from BrowserStore (kept current by Mozilla's EngineObserver even while no view is
        // attached). Zeroing these flags made Back fall through to Home whenever a tab that
        // already had history was re-bound, because Gecko only reports canGoBack on CHANGE.
        backAvailable = tab.content.canGoBack
        forwardAvailable = tab.content.canGoForward
        // A session rebuilt from saved state has not reported its history yet: trust Back briefly.
        historyPending = PetalEngineStore.consumeRestoredFlag(tab.id)
        historyPendingSince = android.os.SystemClock.uptimeMillis()
        restoreLocationSeen = false
        if (pageUrl.isNotBlank() && !com.petal.browser.unit.PetalBrowserUtils.isHomePage(pageUrl) && !pageUrl.equals("about:blank", ignoreCase = true)) {
            pendingNavUrl = pageUrl
            pendingNavSince = android.os.SystemClock.uptimeMillis()
        }
        navUrls.clear(); navIndex = -1; navFallbackEligible = false
        currentScrollX = 0; currentScrollY = 0; lastProcessedScrollY = 0; accumulatedScrollY = 0; consecutiveDirectionSteps = 0
        isSecure = pageUrl.startsWith("https://", ignoreCase = true)
        engineView.render(session)
        if (isHostedByBrowserActivity() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && com.petal.browser.ui.glass.GeckoGlassBackend.shouldSamplePage(context)) {
            post {
                findGeckoView(engineView.asView())?.let {
                    try { com.petal.browser.ui.glass.GeckoGlassBackend.applyBackend(it, context) } catch (_: Throwable) {}
                }
            }
        }
        session.register(observer)
        val geckoSession = getGeckoSession()
        com.petal.browser.extensions.PetalExtensionManager.attachSession(geckoSession)
        geckoSession?.let { gs ->
            // Prompt Delegate: Petal takes sole ownership (GeckoEngineSession never installs one).
            // Use PetalPromptDelegateWrapper so the activityProvider / incognitoProvider lambdas
            // point at THIS view after Activity recreation, without replacing the delegate object
            // on a live GeckoSession (which would race with any in-flight GeckoResult resolution).
            val existingPrompt = gs.promptDelegate
            if (existingPrompt is PetalPromptDelegateWrapper) {
                // Session outlived its previous view: re-point lambdas at the current context.
                existingPrompt.rebind(
                    newIncognitoProvider = { isIncognito() },
                    newActivityProvider = { resolveActivity(context) }
                )
            } else {
                gs.promptDelegate = PetalPromptDelegateWrapper(
                    engine = existingPrompt,
                    session = gs,
                    appContext = appContext,
                    sp = preferences,
                    incognitoProvider = { isIncognito() },
                    activityProvider = { resolveActivity(context) }
                )
            }

            android.util.Log.d("PetalTabNav", "bindTab: gs.navigationDelegate=${gs.navigationDelegate?.javaClass?.name}, gs.contentDelegate=${gs.contentDelegate?.javaClass?.name}")

            // Content Delegate: preserve Mozilla's engine contentDelegate so title, crash,
            // and fullscreen callbacks are NOT dropped, while handling onExternalResponse and onContextMenu.
            val engineContent = gs.contentDelegate
            val crashCallback: () -> Unit = {
                post {
                    recoverCrashedSession()
                }
            }
            if (engineContent !is PetalContentDelegateWrapper) {
                gs.contentDelegate = PetalContentDelegateWrapper(
                    engine = engineContent,
                    contextProvider = { context },
                    isPrivateProvider = { isIncognito() },
                    referrerProvider = { pageUrl.takeIf { it.startsWith("http") } },
                    onCrashCallback = crashCallback,
                    pageUrlProvider = { pageUrl },
                    pageTitleProvider = { pageTitle }
                )
            } else {
                // The session outlived the previous view (Activity recreation, suspension): point the
                // existing wrapper at THIS view instead of the destroyed one.
                engineContent.rebind(
                    contextProvider = { context },
                    isPrivateProvider = { isIncognito() },
                    referrerProvider = { pageUrl.takeIf { it.startsWith("http") } },
                    onCrashCallback = crashCallback,
                    pageUrlProvider = { pageUrl },
                    pageTitleProvider = { pageTitle }
                )
            }

            // Do NOT replace GeckoView's navigation delegate. Mozilla's GeckoEngineSession installs its
            // own delegate that reports onLocationChange / onCanGoBack / onLoadError to the observers
            // (this class's `observer`) and tracks the load request used by reload().
            // We explicitly forward all methods because Kotlin's `by` delegation drops Java default methods.
            val engineNavigation = gs.navigationDelegate
            val locationCallback: (String) -> Unit = { newUrl ->
                android.util.Log.d("PetalTabNav", "ExternalSchemeNavigationDelegate: onLocationChanged -> $newUrl (current pageUrl=$pageUrl)")
                val staleBlank = isStaleInitialBlank(newUrl)
                if (!staleBlank && !newUrl.equals("about:blank", ignoreCase = true)) {
                    pendingNavUrl = null
                    restoreLocationSeen = true
                }
                if (!staleBlank && newUrl.isNotBlank() && !newUrl.equals(pageUrl, ignoreCase = true)) {
                    previewRevision++
                    previewRetryCount = 0
                    pageUrl = newUrl
                    applyPageSettings(newUrl)
                    tab?.let { browserStore.dispatch(ContentAction.UpdateUrlAction(it.id, newUrl)) }
                    publishState()
                }
            }
            val canGoBackCallback: (Boolean) -> Unit = { canBack ->
                post {
                    backAvailable = canBack
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
                        act.updateBackCallbackState()
                        act.updateOmniBox()
                    }
                    publishState()
                }
            }
            val canGoForwardCallback: (Boolean) -> Unit = { canFwd ->
                post {
                    forwardAvailable = canFwd
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
                        act.updateOmniBox()
                    }
                    publishState()
                }
            }
            val externalSchemeCallback: (String) -> Boolean = { uri ->
                val act = resolveActivity(context)
                act != null && com.petal.browser.engine.gecko.PetalEngineBridge.handleExternalScheme(act, uri)
            }
            if (engineNavigation !is ExternalSchemeNavigationDelegate) {
                val inner = engineNavigation ?: object : org.mozilla.geckoview.GeckoSession.NavigationDelegate {}
                gs.navigationDelegate = ExternalSchemeNavigationDelegate(
                    engine = inner,
                    context = context,
                    currentUrlSupplier = { pageUrl },
                    isIncognitoSupplier = { isIncognito() },
                    onLocationChanged = locationCallback,
                    onCanGoBackChanged = canGoBackCallback,
                    onCanGoForwardChanged = canGoForwardCallback,
                    handleExternal = externalSchemeCallback
                )
            } else {
                // Re-bind: every callback must point at THIS view. Keeping the old view's lambdas
                // sends canGoBack/location updates (and external-app intents) to a destroyed view.
                engineNavigation.updateDependencies(
                    context,
                    { pageUrl },
                    { isIncognito() },
                    canGoBackCallback,
                    canGoForwardCallback,
                    locationCallback,
                    externalSchemeCallback
                )
            }

            // Progress Delegate: keep Mozilla's engine delegate (page start/stop, progress, security)
            // and additionally capture GeckoSession.SessionState so the tab's back/forward history is tracked.
            val engineProgress = gs.progressDelegate
            if (engineProgress is SessionStateCapturingProgressDelegate) {
                engineProgress.onSecurity = { sec -> currentSecurityInfo = sec }
                engineProgress.onState = { state -> onGeckoStateChanged(state) }
            } else {
                gs.progressDelegate = SessionStateCapturingProgressDelegate(
                    engine = engineProgress,
                    onSecurity = { sec -> currentSecurityInfo = sec },
                    onState = { state -> onGeckoStateChanged(state) }
                )
            }

            // Permission Delegate: Petal takes sole ownership (GeckoEngineSession never installs one).
            // Use PetalPermissionDelegateWrapper with rebind so activitySupplier always points at
            // the current Activity after a configuration change.
            val existingPerm = gs.permissionDelegate
            if (existingPerm is PetalPermissionDelegateWrapper) {
                existingPerm.rebind { resolveActivity(context) }
            } else {
                gs.permissionDelegate = PetalPermissionDelegateWrapper(
                    engine = existingPerm,
                    context = appContext,
                    activitySupplier = { resolveActivity(context) }
                )
            }

            gs.selectionActionDelegate = object : org.mozilla.geckoview.GeckoSession.SelectionActionDelegate {
                override fun onShowActionRequest(
                    session: org.mozilla.geckoview.GeckoSession,
                    selection: org.mozilla.geckoview.GeckoSession.SelectionActionDelegate.Selection
                ) {
                    this@PetalTabViewController.currentSelection = selection
                    val act = (context as? com.petal.browser.activity.BrowserActivity)
                        ?: (context as? android.content.ContextWrapper)?.baseContext as? com.petal.browser.activity.BrowserActivity
                        ?: return
                    val anchorView: View = findGeckoView(engineView.asView()) ?: this@PetalTabViewController
                    if (com.petal.browser.ui.selection.PetalPageSelectionToolbar.show(act, anchorView, selection)) {
                        return
                    }
                    val selectedText = selection.text
                    if (!selectedText.isNullOrBlank()) {
                        act.runOnUiThread {
                            com.petal.browser.compose.menu.BrowserContextMenuManager.showSelectionContextMenu(act, selectedText)
                        }
                    }
                }

                override fun onHideAction(session: org.mozilla.geckoview.GeckoSession, reason: Int) {
                    this@PetalTabViewController.currentSelection = null
                    val act = (context as? com.petal.browser.activity.BrowserActivity)
                        ?: (context as? android.content.ContextWrapper)?.baseContext as? com.petal.browser.activity.BrowserActivity
                    if (act != null) com.petal.browser.ui.selection.PetalPageSelectionToolbar.hide(act)
                }
            }

            // GeckoSession ScrollDelegate for Tactile Haptics and Address Bar Collapsing on scroll
            // Keep Mozilla's engine delegates alive (captured once, never our own wrapper) so the
            // BrowserStore still receives scroll / history / content-blocking events.
            gs.scrollDelegate?.takeIf { it !is PetalOwnedDelegate }?.let { engineScrollDelegate = it }
            gs.historyDelegate?.takeIf { it !is PetalOwnedDelegate }?.let { engineHistoryDelegate = it }
            gs.contentBlockingDelegate?.takeIf { it !is PetalOwnedDelegate }?.let { engineContentBlockingDelegate = it }
            gs.scrollDelegate = object : org.mozilla.geckoview.GeckoSession.ScrollDelegate, PetalOwnedDelegate {
                override fun onScrollChanged(session: org.mozilla.geckoview.GeckoSession, scrollX: Int, scrollY: Int) {
                    try { engineScrollDelegate?.onScrollChanged(session, scrollX, scrollY) } catch (_: Throwable) {}
                    currentScrollX = scrollX
                    currentScrollY = scrollY

                    if (pendingScrollUpdate.compareAndSet(false, true)) {
                        post(scrollDispatcher)
                    }
                }
            }

            // Official Mozilla Firefox ContentBlocking Delegate (Enhanced Tracking Protection & AdBlock telemetry)
            gs.contentBlockingDelegate = object : org.mozilla.geckoview.ContentBlocking.Delegate, PetalOwnedDelegate {
                override fun onContentBlocked(
                    session: org.mozilla.geckoview.GeckoSession,
                    event: org.mozilla.geckoview.ContentBlocking.BlockEvent
                ) {
                    try { engineContentBlockingDelegate?.onContentBlocked(session, event) } catch (_: Throwable) {}
                    val atCat = try { event.antiTrackingCategory } catch (_: Throwable) { 0 }
                    val cookieCat = try { event.cookieBehaviorCategory } catch (_: Throwable) { 0 }
                    com.petal.browser.browser.PetalAdBlockEngine.recordBlockWithCategories(pageUrl, atCat, cookieCat)
                }
            }

            // GeckoSession HistoryDelegate to record top-level page visits into Petal history
            gs.historyDelegate = object : org.mozilla.geckoview.GeckoSession.HistoryDelegate, PetalOwnedDelegate {
                override fun onHistoryStateChange(
                    session: org.mozilla.geckoview.GeckoSession,
                    historyList: org.mozilla.geckoview.GeckoSession.HistoryDelegate.HistoryList
                ) {
                    try { engineHistoryDelegate?.onHistoryStateChange(session, historyList) } catch (_: Throwable) {}
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

                override fun getVisited(
                    session: org.mozilla.geckoview.GeckoSession,
                    urls: Array<out String>
                ): org.mozilla.geckoview.GeckoResult<BooleanArray>? {
                    val result = org.mozilla.geckoview.GeckoResult<BooleanArray>()
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val action = com.petal.browser.database.HistoryBookmarkRepository(context)
                            action.open(false)
                            val visited = BooleanArray(urls.size) { i ->
                                val u = urls[i]
                                if (u.isBlank() || u.startsWith("about:") || u.startsWith("moz-extension://")) false
                                else action.checkUrl(u, com.petal.browser.database.HistoryBookmarkContract.TABLE_HISTORY)
                            }
                            action.close()
                            result.complete(visited)
                        } catch (_: Throwable) {
                            result.complete(BooleanArray(urls.size) { false })
                        }
                    }
                    return result
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
            if (ctx is com.petal.browser.customtabs.PetalCustomTabActivity) return true
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
        dismissCrashErrorState()
        val rawUrl = url.trim()
        if (rawUrl.isEmpty()) return
        if (rawUrl.equals("Petal Home", ignoreCase = true) || rawUrl.equals("Petal Start", ignoreCase = true)) {
            pageUrl = "about:blank"
            pageTitle = "Petal Home"
            observedSession?.loadUrl("about:blank")
            publishState()
            return
        }
        val redirected = com.petal.browser.unit.PetalBrowserUtils.redirectURL(preferences, rawUrl)
        var targetUrl = com.petal.browser.unit.PetalBrowserUtils.queryWrapper(appContext, redirected)
        val httpsOnly = preferences.getBoolean(
            "sp_https_only",
            preferences.getBoolean("profileStandard_httpsOnly", false)
        )
        val host = try { android.net.Uri.parse(targetUrl).host } catch (_: Throwable) { null }
        val isLocalHost = host != null && (
            host.equals("localhost", ignoreCase = true) ||
            host.endsWith(".local", ignoreCase = true) ||
            host == "127.0.0.1" || host == "::1" ||
            host.startsWith("192.168.") || host.startsWith("10.") ||
            (host.startsWith("172.") && try {
                val second = host.split(".")[1].toInt()
                second in 16..31
            } catch (_: Throwable) { false })
        )
        val isBypassed = host != null && com.petal.browser.privacy.PetalHttpsOnlyWhitelist.isBypassed(appContext, host)
        if (httpsOnly && !isLocalHost && !isBypassed && targetUrl.startsWith("http://", ignoreCase = true)) {
            targetUrl = "https://" + targetUrl.substring(7)
        }
        if (com.petal.browser.unit.PetalBrowserUtils.isHomePage(targetUrl) ||
            com.petal.browser.unit.PetalBrowserUtils.isHomePage(rawUrl) ||
            targetUrl.equals("about:blank", ignoreCase = true)
        ) {
            pendingNavUrl = null
            pageUrl = "about:blank"
            pageTitle = "Petal Home"
            observedSession?.loadUrl("about:blank")
        } else {
            applyPageSettings(targetUrl)
            pendingNavUrl = targetUrl
            pendingNavSince = android.os.SystemClock.uptimeMillis()
            pageUrl = targetUrl
            tab?.let {
                browserStore.dispatch(ContentAction.UpdateUrlAction(it.id, targetUrl))
            }
            observedSession?.loadUrl(targetUrl)
        }
        publishState()
    }

    /** @return true if a reload was actually issued (callers reset any refresh UI when false). */
    fun reload(): Boolean = reloadInternal(org.mozilla.geckoview.GeckoSession.LOAD_FLAGS_NONE)

    fun reloadBypassCache(): Boolean = reloadInternal(org.mozilla.geckoview.GeckoSession.LOAD_FLAGS_BYPASS_CACHE)

    private fun reloadInternal(flags: Int): Boolean {
        // Runs inside the touch-release callback of pull-to-refresh: nothing here may throw.
        // (Firefox's SwipeRefreshFeature just calls the reload use case.)
        try {
            applyPageSettings(pageUrl)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabViewController", "applyPageSettings failed before reload", t)
        }
        // Reload the live GeckoSession so we always refresh the page currently shown, not the
        // engine session's original (initial) load request.
        val live = getGeckoSession()
        if (live != null) {
            try {
                live.reload(flags)
                return true
            } catch (_: Throwable) {
            }
        }
        return try {
            val s = observedSession
            s?.reload()
            s != null
        } catch (_: Throwable) { false }
    }

    fun captureVideoHandoffState(callback: (com.petal.browser.media.handoff.MediaHandoff?) -> Unit) {
        try {
            val js = """
                (function() {
                    try {
                        var vids = Array.from(document.querySelectorAll('video'));
                        if (vids.length === 0) return null;
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
            evaluateJavascript(js) { rawResult ->
                if (rawResult.isNullOrBlank() || rawResult == "null" || rawResult.startsWith("ERROR:")) {
                    callback(null)
                    return@evaluateJavascript
                }
                try {
                    val json = org.json.JSONObject(rawResult)
                    val pos = json.optLong("pos", 0L)
                    val dur = json.optLong("dur", 0L)
                    val speed = json.optDouble("speed", 1.0).toFloat()
                    val paused = json.optBoolean("paused", false)
                    val vol = json.optDouble("vol", 1.0).toFloat()
                    val src = json.optString("src", "").takeIf { it.isNotBlank() } ?: url
                    callback(com.petal.browser.media.handoff.MediaHandoff(pos, dur, speed, paused, vol, src))
                } catch (_: Throwable) {
                    callback(null)
                }
            }
        } catch (_: Throwable) {
            callback(null)
        }
    }

    fun setProfileChanged() {
        applyPageSettings(url)
    }

    private var currentSelection: org.mozilla.geckoview.GeckoSession.SelectionActionDelegate.Selection? = null

    override fun canClearSelection(): Boolean = currentSelection != null

    fun clearWebSelection(): Boolean {
        return try {
            val sel = currentSelection
            if (sel != null) {
                currentSelection = null
                sel.collapseToStart()
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            currentSelection = null
            false
        }
    }

    var isReaderEligible: Boolean = false
    var currentSecurityInfo: org.mozilla.geckoview.GeckoSession.ProgressDelegate.SecurityInformation? = null

    fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?) {
        try {
            val encodedData = android.util.Base64.encodeToString(data.toByteArray(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
            val dataUri = "data:${mimeType ?: "text/html"};charset=${encoding ?: "utf-8"};base64,$encodedData"
            pageUrl = baseUrl ?: dataUri
            loadUrl(dataUri)
        } catch (e: Exception) {
            android.util.Log.e("PetalTabViewController", "Error in loadDataWithBaseURL", e)
        }
    }

    fun setBrowserController(controller: com.petal.browser.browser.BrowserController?) {
        com.petal.browser.engine.gecko.PetalEngineBridge.setBrowserController(controller)
    }

    fun loadData(data: String, mimeType: String, encoding: String) {
        observedSession?.loadData(data, mimeType, encoding)
    }

    fun findAll(query: String) {
        observedSession?.findAll(query)
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
        val profile = com.petal.browser.engine.gecko.PetalEngineBridge.getProfile(appContext)
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

        val effectiveMode = com.petal.browser.browser.PetalAdBlockEngine.getEffectiveMode(appContext, host)
        applyTrackingProtectionMode(effectiveMode)
        com.petal.browser.engine.gecko.PetalGeckoRuntime.syncPreferences(preferences)
        com.petal.browser.accessibility.PetalAccessibilityEngine.applyAccessibilitySettings(this)
    }

    fun applyTrackingProtectionMode(mode: com.petal.browser.browser.PetalAdBlockMode) {
        val policy = when (mode) {
            com.petal.browser.browser.PetalAdBlockMode.DISABLED -> EngineSession.TrackingProtectionPolicy.none()
            com.petal.browser.browser.PetalAdBlockMode.STANDARD -> EngineSession.TrackingProtectionPolicy.recommended()
        }
        observedSession?.updateTrackingProtection(policy)
    }

    fun setTrackingProtection(enabled: Boolean) {
        val mode = if (enabled) com.petal.browser.browser.PetalAdBlockMode.STANDARD else com.petal.browser.browser.PetalAdBlockMode.DISABLED
        applyTrackingProtectionMode(mode)
    }

    fun attachLifecycle(owner: LifecycleOwner) {
        if (attachedLifecycle === owner.lifecycle) return
        attachedLifecycle?.removeObserver(engineLifecycleObserver)
        attachedLifecycle = owner.lifecycle
        attachedLifecycle?.addObserver(engineLifecycleObserver)
    }

    /** Returns true while the session is actively restoring its history from saved engine state. */
    fun isHistoryRestoring(): Boolean = historyPending

    /** Same source Mozilla's SessionFeature.onBackPressed uses: the store's canGoBack, plus our live flag. */
    private fun hasBackHistory(): Boolean {
        if (backAvailable || historyPending) return true
        val id = boundTabId ?: return false
        return browserStore.state.tabs.firstOrNull { it.id == id }?.content?.canGoBack == true
    }

    @JvmOverloads
    fun goBack(userInteraction: Boolean = true) {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val useMozillaUseCase = sp.getBoolean("sp_mozilla_session_restore", false)
        val gs = getGeckoSession()
        if (hasBackHistory()) {
            if (useMozillaUseCase) {
                tab?.let { current ->
                    try {
                        mozilla.components.feature.session.SessionUseCases(browserStore).goBack.invoke(current.id, userInteraction)
                        return
                    } catch (_: Throwable) {}
                }
            }
            try {
                if (gs != null) {
                    gs.goBack(userInteraction)
                    return
                }
            } catch (_: Throwable) {}

            val current = tab
            if (current != null) {
                browserStore.dispatch(
                    mozilla.components.browser.state.action.EngineAction.GoBackAction(
                        current.id,
                        userInteraction
                    )
                )
                return
            }
        }
        if (canTrailBack()) {
            trailBack()
        }
    }

    @JvmOverloads
    fun goForward(userInteraction: Boolean = true) {
        val gs = getGeckoSession()
        if (forwardAvailable) {
            try {
                if (gs != null) {
                    gs.goForward(userInteraction)
                    return
                }
            } catch (_: Throwable) {}

            val current = tab ?: return
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
            // Engine session not yet bound (e.g. still restoring after process kill).
            // If a nav trail exists, navigate within it rather than triggering the
            // home-fallback. This matches Firefox's isHistoryPending() handling.
            if (canTrailBack()) goBack() else onUnhandled()
            return
        }
        if (fullScreenFeature?.onBackPressed() == true) return
        session.processBackPressed { handled ->
            if (handled) return@processBackPressed
            post {
                if (hasBackHistory() || canTrailBack()) goBack() else onUnhandled()
            }
        }
    }

    fun canGoBack(): Boolean = hasBackHistory() || canTrailBack()

    fun canGoForward(): Boolean = forwardAvailable

    fun markAdoptedNavigation(url: String?) {
        isAdoptedPopup = true
        val target = url?.trim().orEmpty()
        if (target.isNotEmpty() && !com.petal.browser.unit.PetalBrowserUtils.isHomePage(target)) {
            pageUrl = target
            tab?.let { browserStore.dispatch(ContentAction.UpdateUrlAction(it.id, target)) }
        }
    }

    override fun getCachedPreviewBitmap(): Bitmap? = TabThumbnailCache.getMemoryOnly(boundTabId, isIncognito())

    private var lastPreviewCaptureTime = 0L

    @JvmOverloads
    fun updatePreviewCache(force: Boolean = false) {
        val currentRev = previewRevision
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && capturedPreviewRevision == currentRev) {
            if (now - lastPreviewCaptureTime < 1500L) return
        }
        lastPreviewCaptureTime = now
        capturePreview(force) { }
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
                if (bitmap == null) {
                    callback(null)
                    return@then org.mozilla.geckoview.GeckoResult.fromValue<Void?>(null)
                }
                TabThumbnailCache.DISK.execute {
                    val current = key == boundTabId
                    var stored = false
                    var storedBitmap: Bitmap? = null
                    if (current) {
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
                        if (stored) {
                            capturedPreviewRevision = revision
                            storedBitmap = scaled
                        } else {
                            post { schedulePreviewRetry() }
                        }
                    }
                    post {
                        callback(if (current && stored) storedBitmap else null)
                    }
                }
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

    fun resetGestureExclusionRects() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val emptyRects: List<android.graphics.Rect> = java.util.Collections.emptyList()
                systemGestureExclusionRects = emptyRects
                findGeckoView(engineView.asView())?.systemGestureExclusionRects = emptyRects
                for (i in 0 until childCount) {
                    getChildAt(i)?.systemGestureExclusionRects = emptyRects
                }
            } catch (_: Throwable) {}
        }
    }

    private var touchListenerView: org.mozilla.geckoview.GeckoView? = null

    /**
     * Mozilla's NestedGeckoView already feeds every touch to Gecko exactly once and tracks the
     * input result (EngineView.getInputResultDetail). We must NOT send events to Gecko ourselves.
     * This only keeps the web surface from reserving edge areas that would block the back gesture.
     */
    private fun installGeckoTouchListener() {
        val gv = findGeckoView(engineView.asView()) ?: return
        if (touchListenerView === gv) return
        touchListenerView = gv
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            gv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                try {
                    if (gv.systemGestureExclusionRects.isNotEmpty()) {
                        gv.systemGestureExclusionRects = java.util.Collections.emptyList()
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            resetGestureExclusionRects()
        }
        installGeckoTouchListener()
        return super.dispatchTouchEvent(event)
    }

    private var lastToolbarMaxHeight = Int.MIN_VALUE
    private var lastVerticalClipping = Int.MIN_VALUE

    // GeckoEngineView already forwards these to its GeckoView; calling both doubled the
    // cross-process compositor messages on every toolbar animation frame. Dedupe identical values too.
    override fun setDynamicToolbarMaxHeight(height: Int) {
        if (height == lastToolbarMaxHeight) return
        lastToolbarMaxHeight = height
        try { engineView.setDynamicToolbarMaxHeight(height) } catch (_: Throwable) {}
    }

    override fun setVerticalClipping(clippingHeight: Int) {
        if (clippingHeight == lastVerticalClipping) return
        lastVerticalClipping = clippingHeight
        try { engineView.setVerticalClipping(clippingHeight) } catch (_: Throwable) {}
    }

    /** GeckoView-only integration point used for WebExtension delegates. */
    fun getGeckoSession(): org.mozilla.geckoview.GeckoSession? =
        com.petal.browser.engine.gecko.PetalEngineBridge.getEngineGeckoSession(observedSession)

    fun toggleReaderMode(active: Boolean) {
        val id = boundTabId ?: return
        PetalEngineStore.toggleReaderMode(context, id, active)
        val curUrl = url ?: return
        try {
            if (active) {
                loadUrl("about:reader?url=${android.net.Uri.encode(curUrl)}")
            } else if (curUrl.startsWith("about:reader?url=")) {
                val origUrl = android.net.Uri.decode(curUrl.substringAfter("about:reader?url="))
                loadUrl(origUrl)
            }
        } catch (_: Throwable) {}
    }

    fun printToPdf(outputStream: java.io.OutputStream, callback: ((Boolean) -> Unit)? = null) {
        val gs = getGeckoSession()
        if (gs == null) {
            callback?.invoke(false)
            return
        }
        try {
            val result = gs.saveAsPdf()
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
                        false
                    }
                    callback?.invoke(ok)
                },
                { _ -> callback?.invoke(false) }
            )
        } catch (e: Exception) {
            callback?.invoke(false)
        }
    }

    @JvmOverloads
    fun findAllAsync(query: String, callback: ((current: Int, total: Int) -> Unit)? = null) {
        val gs = getGeckoSession()
        if (gs != null) {
            gs.finder.find(query, 0).then({ result ->
                val cur = result?.current ?: 0
                val tot = result?.total ?: 0
                callback?.invoke(cur, tot)
                org.mozilla.geckoview.GeckoResult.fromValue(result)
            })
        } else {
            findAll(query)
            callback?.invoke(0, 0)
        }
    }

    @JvmOverloads
    fun findNext(forward: Boolean, callback: ((current: Int, total: Int) -> Unit)? = null) {
        val gs = getGeckoSession()
        if (gs != null) {
            val flags = if (forward) 0 else org.mozilla.geckoview.GeckoSession.FINDER_FIND_BACKWARDS
            gs.finder.find(null, flags).then({ result ->
                val cur = result?.current ?: 0
                val tot = result?.total ?: 0
                callback?.invoke(cur, tot)
                org.mozilla.geckoview.GeckoResult.fromValue(result)
            })
        } else {
            observedSession?.findNext(forward)
            callback?.invoke(0, 0)
        }
    }

    fun getSessionState(): org.mozilla.geckoview.GeckoSession.SessionState? = latestGeckoState

    /**
     * Serialized GeckoView session state (history stack, scroll, zoom, form data) for persistence,
     * or null when none is available yet or the tab is private (private tabs are never persisted).
     */
    fun captureEngineState(): String? {
        if (isIncognito()) return null
        val state = latestGeckoState ?: return null
        return try {
            state.toString().takeIf { it.length > 2 }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Restores a previously captured [captureEngineState] value into this tab's fresh engine session so
     * the back/forward history survives process death. Must be called on the main thread after
     * [bindTab] and INSTEAD of [loadUrl]. Returns false when the state is unusable; the caller should
     * then fall back to [loadUrl].
     */
    @MainThread
    fun restoreEngineState(serializedState: String, expectedUrl: String): Boolean {
        val gs = getGeckoSession() ?: return false
        return try {
            val state = org.mozilla.geckoview.GeckoSession.SessionState.fromString(serializedState)
                ?: return false
            // Same guard loadUrl() installs: a freshly created session first reports its own initial
            // about:blank document, which must not be mistaken for "this tab is Home".
            pendingNavUrl = expectedUrl
            pendingNavSince = android.os.SystemClock.uptimeMillis()
            gs.restoreState(state)
            latestGeckoState = state
            restoreLocationSeen = false
            historyPendingSince = android.os.SystemClock.uptimeMillis()
            historyPending = restoredStateHasHistory(serializedState)
            true
        } catch (t: Throwable) {
            pendingNavUrl = null
            android.util.Log.w("PetalTabNav", "restoreEngineState failed: ${t.message}")
            false
        }
    }

    /**
     * Returns true if this tab has a restored real URL but has not yet navigated to or loaded it.
     */
    fun needsReloadOrRecovery(): Boolean {
        return !restoreLocationSeen && pageUrl.isNotBlank() && !com.petal.browser.unit.PetalBrowserUtils.isHomePage(pageUrl) && !pageUrl.equals("about:blank", ignoreCase = true)
    }

    private var crashRecoveryCount = 0
    private var lastCrashRecoveryTime = 0L
    private var crashErrorView: android.view.View? = null

    private fun showCrashErrorState() {
        crashErrorView = PetalCrashOverlayHelper.showCrashErrorState(this, crashErrorView) {
            crashRecoveryCount = 0
            recoverCrashedSession()
        }
    }

    private fun dismissCrashErrorState() {
        crashErrorView = PetalCrashOverlayHelper.dismissCrashErrorState(this, crashErrorView)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dismissCrashErrorState()
    }

    fun recoverCrashedSession() {
        try {
            dismissCrashErrorState()
            val now = System.currentTimeMillis()
            if (now - lastCrashRecoveryTime < 8000L) {
                crashRecoveryCount++
            } else {
                crashRecoveryCount = 1
            }
            lastCrashRecoveryTime = now

            if (crashRecoveryCount > 3) {
                android.util.Log.e("PetalTabNav", "Repeated GeckoSession crash detected ($crashRecoveryCount times in short window). Halting recovery loop.")
                showCrashErrorState()
                return
            }

            val currentTab = tab
            val currentTabId = currentTab?.id ?: boundTabId
            val urlToRestore = if (pageUrl.isNotBlank() && !pageUrl.equals("about:blank", ignoreCase = true)) pageUrl else currentTab?.content?.url ?: ""
            val stateToRestore = latestGeckoState

            // 1. Release and close the dead GeckoSession
            val gs = getGeckoSession()
            try {
                com.petal.browser.extensions.PetalExtensionManager.detachSession(gs)
                observedSession?.unregister(observer)
                engineView.release()
            } catch (_: Throwable) {}
            try {
                if (gs?.isOpen == true) gs.close()
            } catch (_: Throwable) {}

            // 2. Recreate fresh EngineSession
            val eng = com.petal.browser.engine.gecko.PetalEngineStore.getEngine(context)
            val isPrivate = isIncognito()
            val newEngineSession = eng.createSession(private = isPrivate)

            if (currentTabId != null) {
                browserStore.dispatch(
                    mozilla.components.browser.state.action.EngineAction.LinkEngineSessionAction(
                        tabId = currentTabId,
                        engineSession = newEngineSession,
                        skipLoading = true
                    )
                )
            }

            // 3. Rebind the display: detach & re-attach GeckoView inside post to rebind compositor surface
            val gv = findGeckoView(engineView.asView())
            val parent = gv?.parent as? android.view.ViewGroup
            if (parent != null && gv != null) {
                val idx = parent.indexOfChild(gv)
                parent.removeView(gv)
                post {
                    try {
                        parent.addView(gv, idx)
                        val updatedTab = if (currentTabId != null) browserStore.state.tabs.firstOrNull { it.id == currentTabId } else null
                        if (updatedTab != null) {
                            bindTab(updatedTab)
                        }
                        if (stateToRestore != null) {
                            getGeckoSession()?.restoreState(stateToRestore)
                        } else if (urlToRestore.isNotBlank() && !com.petal.browser.unit.PetalBrowserUtils.isHomePage(urlToRestore)) {
                            loadUrl(urlToRestore)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PetalTabNav", "Failed to re-attach GeckoView after crash recovery: ${e.message}", e)
                    }
                }
            } else {
                val updatedTab = if (currentTabId != null) browserStore.state.tabs.firstOrNull { it.id == currentTabId } else null
                if (updatedTab != null) {
                    bindTab(updatedTab)
                }
                if (stateToRestore != null) {
                    getGeckoSession()?.restoreState(stateToRestore)
                } else if (urlToRestore.isNotBlank() && !com.petal.browser.unit.PetalBrowserUtils.isHomePage(urlToRestore)) {
                    loadUrl(urlToRestore)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PetalTabNav", "Failed to recover crashed GeckoSession: ${e.message}", e)
        }
    }

    /** False only when the saved state clearly holds a single entry; unknown formats count as "has history". */
    private fun restoredStateHasHistory(serialized: String): Boolean {
        return try {
            val root = org.json.JSONObject(serialized)
            val list = root.optJSONArray("history") ?: root.optJSONArray("entries") ?: return true
            list.length() > 1
        } catch (_: Throwable) {
            true
        }
    }

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
            val session = com.petal.browser.engine.gecko.PetalEngineBridge.getEngineGeckoSession(observedSession)
            com.petal.browser.extensions.PetalBuiltInExtensionManager.evaluateJavascript(session, script, callback)
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
                val desktopEnabled = preferences.getBoolean("sp_desktop_site", preferences.getBoolean("sp_desktop", false))
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

    override fun getFavicon(): Bitmap? = tab?.content?.icon

    fun currentState(): State? = tab?.let {
        State(it.id, pageUrl, pageTitle, progress, loading, backAvailable, forwardAvailable, isSecure, it.content.private)
    }

    /** Same rule as Mozilla's SwipeRefreshFeature.canChildScrollUp (inverted): page at top and not handling the touch. */
    fun canOverscrollTop(): Boolean {
        // Same as Firefox's SwipeRefreshFeature.canChildScrollUp: trust Gecko's input result only.
        // The old currentScrollY check went stale across navigations and blocked valid pulls.
        return try { engineView.getInputResultDetail().canOverscrollTop() } catch (_: Throwable) { true }
    }

    fun isPageAtTop(): Boolean {
        if (pageUrl.isBlank() || pageUrl.equals("about:blank", ignoreCase = true) || com.petal.browser.unit.PetalBrowserUtils.isHomePage(pageUrl)) {
            return true
        }
        if (currentScrollY <= 2) {
            return true
        }
        try {
            if (!engineView.asView().canScrollVertically(-1)) {
                return true
            }
        } catch (_: Throwable) {}
        return canOverscrollTop()
    }

    override fun canChildScrollUp(): Boolean {
        return !isPageAtTop()
    }

    override fun getTabView(): View = this
    override fun getAlbumView(): View = this
    override fun getTabId(): String = boundTabId ?: ""

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
                com.petal.browser.motion.MotionGate.postAfterTransition {
                    post { try { capturePreviewBitmapAsync { } } catch (_: Throwable) {} }
                }
            }
        }.also { it.resume() }
    }

    private fun stopThumbnails() {
        thumbnailSubscription?.unsubscribe()
        thumbnailSubscription = null
    }

    @MainThread
    override fun deactivate() {
        updatePreviewCache(true)
        stopThumbnails()
        active = false
        com.petal.browser.extensions.PetalExtensionManager.setActiveBrowserSession(getGeckoSession(), null)
        refreshFeature?.stop()
        fullScreenFeature?.stop()
        isRefreshing = false
        // A refresh belongs to the tab being left; don't leave the next tab blocked behind it.
        (resolveActivity(context) as? com.petal.browser.activity.BrowserActivity)?.resetRefreshState()
    }

    override fun getTitle(): String = pageTitle

    override fun getUrl(): String = pageUrl

    override fun isIncognito(): Boolean = tab?.content?.private ?: false

    /** Permanently closes the tab: removes it from BrowserStore and deletes its thumbnail. */
    override fun destroy() = destroy(keepThumbnail = false)

    /**
     * @param keepThumbnail true when the tab itself stays open (memory-saver suspension): the tab
     *        stays in BrowserStore (Mozilla's SuspendEngineSessionAction already stored its
     *        history in it) and its thumbnail stays on disk, so only the view is released. Only a
     *        real close (false) removes the tab and its thumbnail.
     */
    fun destroy(keepThumbnail: Boolean) {
        if (keepThumbnail) {
            release()
            return
        }
        val removedTabId = tab?.id
        if (removedTabId != null) {
            // BrowserStore is the single source of truth: removing the tab there also
            // deletes its thumbnail through PetalThumbnailsMiddleware. The direct calls below
            // cover a tab that is already gone from the store.
            if (isIncognito()) TabThumbnailCache.removePrivate(removedTabId)
            else TabThumbnailCache.remove(removedTabId)
        }
        release()
        if (removedTabId != null) PetalEngineStore.removeTab(appContext, removedTabId)
    }

    /**
     * Detaches this VIEW from its tab without closing the tab. The tab, its engine session
     * state, its metadata and its thumbnail all stay as they are (same rule as Fenix: views come
     * and go with the Activity, tabs are removed only when the user closes them).
     */
    @MainThread
    override fun release() {
        stopThumbnails()
        active = false
        val gs = getGeckoSession()
        com.petal.browser.extensions.PetalExtensionManager.setActiveBrowserSession(gs, null)
        com.petal.browser.extensions.PetalExtensionManager.detachSession(gs)
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
        try {
            preferences.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (_: Throwable) {}
        engineView.release()
    }

    override fun onResume() {
        try {
            engineView.onResume()
        } catch (_: Throwable) {}
        try {
            getGeckoSession()?.setActive(true)
        } catch (_: Throwable) {}
    }

    override fun onPause() {
        try {
            engineView.onPause()
        } catch (_: Throwable) {}
        try {
            getGeckoSession()?.setActive(false)
        } catch (_: Throwable) {}
    }

    private fun injectPagePrivacyAndAdBlock() {
        // Late gs.loadUri(javascript:) injection deleted per Stage 4.
        // Cosmetic filtering and scriptlets are handled early at document_start via WebExtension content scripts.
    }

    private fun recordHistoryVisit(targetUrl: String, titleToRecord: String? = null, force: Boolean = false) {
        if (isIncognito() || targetUrl.isBlank() || targetUrl.equals("about:blank", ignoreCase = true) ||
            targetUrl.startsWith("about:", ignoreCase = true) || com.petal.browser.unit.PetalBrowserUtils.isHomePage(targetUrl)) {
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
            val action = com.petal.browser.database.HistoryBookmarkRepository(appContext)
            action.open(true)
            if (action.checkUrl(targetUrl, com.petal.browser.database.HistoryBookmarkContract.TABLE_HISTORY)) {
                action.deleteURL(targetUrl, com.petal.browser.database.HistoryBookmarkContract.TABLE_HISTORY)
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
    private var contextProvider: () -> android.content.Context,
    private var isPrivateProvider: () -> Boolean = { false },
    private var referrerProvider: () -> String? = { null },
    private var onCrashCallback: (() -> Unit)? = null,
    private var pageUrlProvider: () -> String? = { null },
    private var pageTitleProvider: () -> String? = { null }
) : org.mozilla.geckoview.GeckoSession.ContentDelegate, PetalOwnedDelegate {

    /** Re-points every provider at a newly bound view (the session can outlive its view). */
    fun rebind(
        contextProvider: () -> android.content.Context,
        isPrivateProvider: () -> Boolean,
        referrerProvider: () -> String?,
        onCrashCallback: (() -> Unit)?,
        pageUrlProvider: () -> String?,
        pageTitleProvider: () -> String?
    ) {
        this.contextProvider = contextProvider
        this.isPrivateProvider = isPrivateProvider
        this.referrerProvider = referrerProvider
        this.onCrashCallback = onCrashCallback
        this.pageUrlProvider = pageUrlProvider
        this.pageTitleProvider = pageTitleProvider
    }

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
        onCrashCallback?.invoke()
    }

    override fun onKill(session: org.mozilla.geckoview.GeckoSession) {
        engine?.onKill(session)
        onCrashCallback?.invoke()
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
            val baseUri = runCatching { element.baseUri }.getOrNull()
            fun resolveUri(uri: String?): String? {
                if (uri.isNullOrBlank()) return null
                if (baseUri.isNullOrBlank() || uri.contains("://") || uri.startsWith("data:") || uri.startsWith("blob:") || uri.startsWith("javascript:")) {
                    return uri
                }
                return runCatching { java.net.URI(baseUri).resolve(uri).toString() }.getOrDefault(uri)
            }

            val linkUri = resolveUri(element.linkUri)
            val srcUri = resolveUri(element.srcUri)
            val elemType = element.type
            val linkText = runCatching { element.linkText }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: runCatching { element.title }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: runCatching { element.javaClass.getField("alt").get(element) as? String }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: runCatching { element.javaClass.getField("textContent").get(element) as? String }.getOrNull()
            val elementTitle = runCatching { element.title }.getOrNull()?.takeIf { it.isNotBlank() }
            val elementAlt = runCatching { element.javaClass.getField("alt").get(element) as? String }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: runCatching { element.javaClass.getField("altText").get(element) as? String }.getOrNull()?.takeIf { it.isNotBlank() }

            act.runOnUiThread {
                com.petal.browser.compose.menu.BrowserContextMenuManager.handleContextMenu(
                    activity = act,
                    elemType = elemType,
                    linkUri = linkUri,
                    srcUri = srcUri,
                    linkText = linkText,
                    title = elementTitle,
                    alt = elementAlt,
                    pageUrl = pageUrlProvider(),
                    pageTitle = pageTitleProvider()
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
            com.petal.browser.engine.gecko.PetalEngineBridge.handleExternalResponse(
                act, response, isPrivateProvider(), referrerProvider()
            )
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
    private var onLocationChanged: (String) -> Unit,
    private var onCanGoBackChanged: ((Boolean) -> Unit)? = null,
    private var onCanGoForwardChanged: ((Boolean) -> Unit)? = null,
    private var handleExternal: (String) -> Boolean
) : org.mozilla.geckoview.GeckoSession.NavigationDelegate, PetalOwnedDelegate {

    fun updateDependencies(
        newContext: android.content.Context,
        newUrlSupplier: () -> String,
        newIncognitoSupplier: () -> Boolean,
        newOnCanGoBack: ((Boolean) -> Unit)? = null,
        newOnCanGoForward: ((Boolean) -> Unit)? = null,
        newOnLocationChanged: ((String) -> Unit)? = null,
        newHandleExternal: ((String) -> Boolean)? = null
    ) {
        context = newContext
        currentUrlSupplier = newUrlSupplier
        isIncognitoSupplier = newIncognitoSupplier
        if (newOnCanGoBack != null) onCanGoBackChanged = newOnCanGoBack
        if (newOnCanGoForward != null) onCanGoForwardChanged = newOnCanGoForward
        if (newOnLocationChanged != null) onLocationChanged = newOnLocationChanged
        if (newHandleExternal != null) handleExternal = newHandleExternal
    }

    override fun onLocationChange(
        session: org.mozilla.geckoview.GeckoSession,
        url: String?,
        perms: MutableList<org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean
    ) {
        android.util.Log.d("PetalTabNav", "ExternalSchemeNavigationDelegate: onLocationChange (4-arg): url=$url gesture=$hasUserGesture")
        if (!url.isNullOrBlank()) {
            val bounceProtectionEnabled = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("sp_bounce_tracking", false)
            if (bounceProtectionEnabled) {
                val unbouncedUrl = com.petal.browser.privacy.PetalBounceTrackingProtection.detectAndUnwrapBounceUrl(url)
                if (unbouncedUrl != null && !unbouncedUrl.equals(url, ignoreCase = true)) {
                    android.util.Log.i("PetalTabNav", "Bounce Tracking detected: bypassing $url -> $unbouncedUrl")
                    loadReplacingHistory(session, unbouncedUrl)
                    return
                }
            }

            val deAmpUrl = com.petal.browser.privacy.PetalDeAmpEngine.unwrapAmpUrl(url)
            if (deAmpUrl != null && !deAmpUrl.equals(url, ignoreCase = true)) {
                android.util.Log.i("PetalTabNav", "De-AMP triggered: redirecting $url -> $deAmpUrl")
                loadReplacingHistory(session, deAmpUrl)
                return
            }

            onLocationChanged(url)
        }
        try {
            engine.onLocationChange(session, url, perms, hasUserGesture)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "Error forwarding onLocationChange(4-arg): ${t.message}")
        }
    }

    /**
     * Loads [target] in place of the current history entry. A plain loadUri() would ADD an entry, so
     * Back from the unwrapped page would return to the redirect/AMP wrapper, which is unwrapped
     * again immediately: Back looks stuck.
     */
    private fun loadReplacingHistory(session: org.mozilla.geckoview.GeckoSession, target: String) {
        try {
            session.load(
                org.mozilla.geckoview.GeckoSession.Loader()
                    .uri(target)
                    .flags(org.mozilla.geckoview.GeckoSession.LOAD_FLAGS_REPLACE_HISTORY)
            )
        } catch (_: Throwable) {
            session.loadUri(target)
        }
    }

    override fun onCanGoBack(session: org.mozilla.geckoview.GeckoSession, canGoBack: Boolean) {
        android.util.Log.d("PetalTabNav", "ExternalSchemeNavigationDelegate: onCanGoBack=$canGoBack")
        try {
            onCanGoBackChanged?.invoke(canGoBack)
        } catch (_: Throwable) {}
        try {
            engine.onCanGoBack(session, canGoBack)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "Error forwarding onCanGoBack: ${t.message}")
        }
    }

    override fun onCanGoForward(session: org.mozilla.geckoview.GeckoSession, canGoForward: Boolean) {
        android.util.Log.d("PetalTabNav", "ExternalSchemeNavigationDelegate: onCanGoForward=$canGoForward")
        try {
            onCanGoForwardChanged?.invoke(canGoForward)
        } catch (_: Throwable) {}
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
        if (uri != null && uri.startsWith("https://", ignoreCase = true)) {
            val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val httpsOnly = sp.getBoolean("sp_https_only", sp.getBoolean("profileStandard_httpsOnly", false))
            val host = try { android.net.Uri.parse(uri).host } catch (_: Throwable) { null }
            if (httpsOnly && !host.isNullOrBlank() && !com.petal.browser.privacy.PetalHttpsOnlyWhitelist.isBypassed(context, host)) {
                if (error.category == org.mozilla.geckoview.WebRequestError.ERROR_CATEGORY_SECURITY ||
                    error.category == org.mozilla.geckoview.WebRequestError.ERROR_CATEGORY_NETWORK) {
                    val act = resolveActivity() as? com.petal.browser.activity.BrowserActivity
                    act?.runOnUiThread {
                        com.petal.browser.view.PetalToast.show(
                            act,
                            "HTTPS connection failed for $host (HTTPS-Only Mode)",
                            com.petal.browser.view.PetalToast.LENGTH_LONG,
                            "Load via HTTP",
                            {
                                com.petal.browser.privacy.PetalHttpsOnlyWhitelist.addBypass(act, host)
                                val httpUrl = "http://" + uri.substring(8)
                                act.showAlbum(act.currentAlbumController, httpUrl)
                            }
                        )
                    }
                }
            }
        }
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

    private fun resolveActivity(): android.app.Activity? {
        var ctx: android.content.Context? = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? android.app.Activity
    }

    override fun onNewSession(
        session: org.mozilla.geckoview.GeckoSession,
        uri: String
    ): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.GeckoSession>? {
        android.util.Log.d("PetalTabNav", "ExternalSchemeNavigationDelegate: onNewSession uri=$uri")
        try {
            val engineResult = engine.onNewSession(session, uri)
            if (engineResult != null) {
                return engineResult
            }
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "engine.onNewSession threw or not handled: ${t.message}")
        }

        val act = resolveActivity() as? com.petal.browser.activity.BrowserActivity
        if (act == null || act.isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && act.isDestroyed)) {
            return try {
                engine.onNewSession(session, uri)
            } catch (t: Throwable) {
                null
            }
        }

        val isPrivate = isIncognitoSupplier()
        val popupSession = org.mozilla.geckoview.GeckoSession(
            org.mozilla.geckoview.GeckoSessionSettings.Builder()
                .usePrivateMode(isPrivate)
                .allowJavascript(androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).let { it.getBoolean("sp_javascript", it.getBoolean("profileStandard_javascript", true)) })
                .viewportMode(org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .build()
        )
        val result = org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.GeckoSession>()
        result.complete(popupSession)

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
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 16L)
                        } else {
                            android.util.Log.w("PetalTabNav", "Popup GeckoSession did not open in time; skipping adoption")
                        }
                        return
                    }
                    act.adoptPopupGeckoSession(popupSession, uri, isPrivate)
                } catch (t: Throwable) {
                    android.util.Log.e("PetalTabNav", "Failed to adopt popup GeckoSession", t)
                    if (popupSession.isOpen) popupSession.close()
                }
            }
        }
        act.runOnUiThread(adoptWhenOpened)
        return result
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

        // Interactive Element Zapper (petal://zap-element?domain=...&selector=...)
        if (uri.startsWith("petal://zap-element", ignoreCase = true)) {
            try {
                val parsedUri = android.net.Uri.parse(uri)
                val domain = parsedUri.getQueryParameter("domain") ?: ""
                val selector = parsedUri.getQueryParameter("selector") ?: ""
                if (domain.isNotBlank() && selector.isNotBlank()) {
                    com.petal.browser.privacy.PetalElementBlocker.addSelector(context, domain, selector)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "Element zapped & hidden on $domain", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Throwable) {}
            return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.DENY)
        }

        // Firefox Account OAuth Redirect Interception
        val fxaManager = com.petal.browser.account.mozilla.FxAccountManager.getInstance()
        if (fxaManager.isRedirectUrl(uri)) {
            val code = fxaManager.extractQueryParam(uri, "code")
            val state = fxaManager.extractQueryParam(uri, "state")
            if (!code.isNullOrBlank() && fxaManager.consumePendingAuth(state)) {
                val email = fxaManager.extractQueryParam(uri, "email") ?: ""
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val ok = fxaManager.exchangeCodeForTokens(code, email)
                    if (ok) {
                        com.petal.browser.account.mozilla.PetalMozillaSyncManager.getInstance().syncNow(context)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(context, "Firefox Account connected & sync active!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.DENY)
            }
            // If not a pending OAuth flow from this app or missing code, do not intercept or fake auth
        }

        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)

        // Open Redirect Links in Background
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

/**
 * Forwards every ProgressDelegate callback explicitly to Mozilla GeckoEngine's own delegate (no Kotlin
 * `by` delegation, which drops Java default methods per KT-18324) and additionally hands each
 * [org.mozilla.geckoview.GeckoSession.SessionState] to [onState]. The captured state carries the tab's
 * back/forward history, which is what lets Back keep working after the app is cleared from recents.
 */
private class SessionStateCapturingProgressDelegate(
    private val engine: org.mozilla.geckoview.GeckoSession.ProgressDelegate?,
    @Volatile var onSecurity: ((org.mozilla.geckoview.GeckoSession.ProgressDelegate.SecurityInformation) -> Unit)? = null,
    @Volatile var onState: (org.mozilla.geckoview.GeckoSession.SessionState) -> Unit
) : org.mozilla.geckoview.GeckoSession.ProgressDelegate, PetalOwnedDelegate {

    override fun onPageStart(session: org.mozilla.geckoview.GeckoSession, url: String) {
        try {
            engine?.onPageStart(session, url)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "Error forwarding onPageStart: ${t.message}")
        }
    }

    override fun onPageStop(session: org.mozilla.geckoview.GeckoSession, success: Boolean) {
        try {
            engine?.onPageStop(session, success)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "Error forwarding onPageStop: ${t.message}")
        }
    }

    override fun onProgressChange(session: org.mozilla.geckoview.GeckoSession, progress: Int) {
        try {
            engine?.onProgressChange(session, progress)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "Error forwarding onProgressChange: ${t.message}")
        }
    }

    override fun onSecurityChange(
        session: org.mozilla.geckoview.GeckoSession,
        securityInfo: org.mozilla.geckoview.GeckoSession.ProgressDelegate.SecurityInformation
    ) {
        onSecurity?.invoke(securityInfo)
        try {
            engine?.onSecurityChange(session, securityInfo)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "Error forwarding onSecurityChange: ${t.message}")
        }
    }

    override fun onSessionStateChange(
        session: org.mozilla.geckoview.GeckoSession,
        sessionState: org.mozilla.geckoview.GeckoSession.SessionState
    ) {
        try {
            engine?.onSessionStateChange(session, sessionState)
        } catch (t: Throwable) {
            android.util.Log.w("PetalTabNav", "Error forwarding onSessionStateChange: ${t.message}")
        }
        onState(sessionState)
    }
}
