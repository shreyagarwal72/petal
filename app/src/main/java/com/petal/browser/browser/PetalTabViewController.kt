package com.petal.browser.browser

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.PreferenceManager
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.petal.browser.engine.gecko.PetalEngineStore
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.session.SwipeRefreshFeature

/**
 * A browser tab surface backed by Android Components' engine abstractions.
 *
 * The view owns presentation and observation only. Tab lifetime and persistence remain with
 * [BrowserStore], so detaching this view never closes or recreates a tab session.
 */
class PetalTabViewController @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val engineView: EngineView = PetalEngineStore.createEngineView(context)
) : SwipeRefreshLayout(context, attrs, defStyleAttr), AlbumController, EngineView by engineView {

    private val appContext = context.applicationContext
    private val browserStore: BrowserStore = PetalEngineStore.getStore(appContext)
    private var tab: TabSessionState? = null
    private var boundTabId: String? = null
    private var observedSession: EngineSession? = null
    private var active = false
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
    private var attachedLifecycle: Lifecycle? = null
    private val engineLifecycleObserver = mozilla.components.concept.engine.LifecycleObserver(this)

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
            pageUrl = url
            applyPageSettings(url)
            tab?.let { browserStore.dispatch(ContentAction.UpdateUrlAction(it.id, url)) }
            publishState()
        }

        override fun onTitleChange(title: String) {
            pageTitle = title
            tab?.let { browserStore.dispatch(ContentAction.UpdateTitleAction(it.id, title)) }
            publishState()
        }

        override fun onProgress(progress: Int) {
            this@PetalTabViewController.progress = progress
            tab?.let { browserStore.dispatch(ContentAction.UpdateProgressAction(it.id, progress)) }
            publishState()
        }

        override fun onLoadingStateChange(loading: Boolean) {
            this@PetalTabViewController.loading = loading
            tab?.let { browserStore.dispatch(ContentAction.UpdateLoadingStateAction(it.id, loading)) }
            publishState()
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
                com.petal.browser.ui.components.PetalFloatingMediaBridge.hide()
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
                com.petal.browser.ui.components.PetalFloatingMediaBridge.updateState(
                    isPlaying = true,
                    title = mediaTitle,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isMuted = mediaBridge?.isMuted ?: false
                )
            } else {
                mediaBridge?.listener?.onMediaPause(positionMs, durationMs)
                com.petal.browser.ui.components.PetalFloatingMediaBridge.setPlaying(false)
                if (playbackState == mozilla.components.concept.engine.mediasession.MediaSession.PlaybackState.STOPPED) {
                    com.petal.browser.ui.components.PetalFloatingMediaBridge.hide()
                }
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
            com.petal.browser.ui.components.PetalFloatingMediaBridge.updateProgress(positionMs, durationMs)
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
        )
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
        com.petal.browser.extensions.PetalExtensionManager.attachSession(getGeckoSession())
        refreshFeature = SwipeRefreshFeature(
            store = browserStore,
            reloadUrlUseCase = SessionUseCases(browserStore).reload,
            swipeRefreshLayout = this,
            tabId = tab.id
        )
        if (active) {
            PetalEngineStore.selectTab(appContext, tab.id)
            refreshFeature?.start()
        }
        publishState()
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
        if (rawUrl.equals("petal://config", ignoreCase = true) ||
            rawUrl.equals("petal:config", ignoreCase = true) ||
            rawUrl.equals("about:config", ignoreCase = true)
        ) {
            (context as? com.petal.browser.activity.BrowserActivity)?.let { activity ->
                activity.runOnUiThread { com.petal.browser.ui.components.PetalConfigSheet.show(activity) }
            }
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
        session.settings.javascriptEnabled = javascriptEnabled

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
        session.processBackPressed { handled ->
            if (handled) return@processBackPressed
            if (backAvailable) goBack() else onUnhandled()
        }
    }

    fun canGoBack(): Boolean = backAvailable

    fun canGoForward(): Boolean = forwardAvailable

    fun getTabId(): String? = boundTabId

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

    fun currentState(): State? = tab?.let {
        State(it.id, pageUrl, pageTitle, progress, loading, backAvailable, forwardAvailable, isSecure, it.content.private)
    }

    override fun getAlbumView(): View = this

    @MainThread
    override fun activate() {
        active = true
        tab?.let { PetalEngineStore.selectTab(appContext, it.id) }
        refreshFeature?.start()
    }

    @MainThread
    override fun deactivate() {
        active = false
        refreshFeature?.stop()
        isRefreshing = false
    }

    override fun getTitle(): String = pageTitle

    override fun getUrl(): String = pageUrl

    override fun isIncognito(): Boolean = tab?.content?.private ?: false

    override fun destroy() {
        val removedTabId = tab?.id
        active = false
        refreshFeature?.stop()
        refreshFeature = null
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
