package com.petal.browser.engine.gecko

import android.content.Context
import android.util.Log
import kotlinx.coroutines.MainScope
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.session.storage.AutoSave
import mozilla.components.browser.session.storage.SessionStorage
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.downloads.DownloadMiddleware
import mozilla.components.support.utils.DefaultDownloadFileUtils
import org.mozilla.geckoview.GeckoRuntime

/**
 * PetalEngineStore
 * ─────────────────────────────────────────────────────────────────────────
 * Thread-safe singleton managing Mozilla Android Components [GeckoEngine],
 * [BrowserStore], and [SessionStorage].
 *
 * It bridges Petal's shared [GeckoRuntime] into the Android Components
 * architecture, allowing incremental migration of tab management and web
 * browsing to [BrowserStore] without disturbing existing raw GeckoView workflows.
 */
object PetalEngineStore {
    private const val TAG = "PetalEngineStore"

    @Volatile
    private var engine: GeckoEngine? = null

    @Volatile
    private var store: BrowserStore? = null

    @Volatile
    private var sessionStorage: SessionStorage? = null

    @Volatile
    private var autoSave: AutoSave? = null

    private val mainScope = MainScope()

    @JvmStatic
    @Synchronized
    fun getEngine(context: Context): GeckoEngine {
        engine?.let { return it }

        val appContext = context.applicationContext
        val runtime = PetalGeckoRuntime.getOrCreate(appContext)

        val newEngine = GeckoEngine(
            context = appContext,
            runtime = runtime
        )
        engine = newEngine
        Log.i(TAG, "Initialized Mozilla GeckoEngine wrapping PetalGeckoRuntime")
        return newEngine
    }

    @JvmStatic
    @Synchronized
    fun getStore(context: Context): BrowserStore {
        store?.let { return it }

        val appContext = context.applicationContext
        val currentEngine = getEngine(appContext)

        val engineMiddleware = EngineMiddleware.create(
            engine = currentEngine,
            scope = mainScope,
            trimMemoryAutomatically = true
        )
        val downloadMiddleware = DownloadMiddleware(
            applicationContext = appContext,
            downloadServiceClass = com.petal.browser.download.PetalMozillaDownloadService::class.java,
            downloadFileUtils = DefaultDownloadFileUtils(appContext),
            deleteFileFromStorage = true
        )

        val newStore = BrowserStore(
            initialState = BrowserState(),
            middleware = engineMiddleware + listOf(downloadMiddleware)
        )
        store = newStore
        Log.i(TAG, "Initialized Mozilla BrowserStore with EngineMiddleware and DownloadMiddleware")
        return newStore
    }

    @JvmStatic
    @Synchronized
    fun getSessionStorage(context: Context): SessionStorage {
        sessionStorage?.let { return it }

        val appContext = context.applicationContext
        val currentEngine = getEngine(appContext)
        val storage = SessionStorage(appContext, currentEngine)
        sessionStorage = storage

        val currentStore = getStore(appContext)
        autoSave = storage.autoSave(currentStore)
        Log.i(TAG, "Initialized Mozilla SessionStorage with AutoSave")
        return storage
    }

    @JvmStatic
    fun dispatch(action: BrowserAction) {
        store?.dispatch(action)
    }

    /** Marks the BrowserStore restore phase complete after Petal's tab metadata has been restored. */
    @JvmStatic
    fun markRestoreComplete(context: Context) {
        getStore(context).dispatch(mozilla.components.browser.state.action.RestoreCompleteAction)
    }

    /**
     * Creates an [mozilla.components.concept.engine.EngineView] using [GeckoEngine].
     */
    @JvmStatic
    fun createEngineView(context: Context): mozilla.components.concept.engine.EngineView {
        return getEngine(context).createView(context)
    }

    /**
     * Creates an [mozilla.components.concept.engine.EngineSession] and registers it as a [mozilla.components.browser.state.state.TabSessionState] in [BrowserStore].
     */
    @JvmStatic
    fun createTabSession(
        context: Context,
        tabId: String,
        url: String,
        title: String = "",
        isIncognito: Boolean = false,
        select: Boolean = false
    ): Pair<mozilla.components.browser.state.state.TabSessionState, mozilla.components.concept.engine.EngineSession> {
        val eng = getEngine(context)
        val st = getStore(context)
        val session = eng.createSession(private = isIncognito)

        val tabState = mozilla.components.browser.state.state.TabSessionState(
            id = tabId,
            content = mozilla.components.browser.state.state.ContentState(
                url = url,
                private = isIncognito,
                title = title
            ),
            engineState = mozilla.components.browser.state.state.EngineState(
                engineSession = session
            )
        )

        st.dispatch(mozilla.components.browser.state.action.TabListAction.AddTabAction(tabState, select = select))
        return Pair(tabState, session)
    }

    /**
     * Removes a tab from [BrowserStore].
     */
    @JvmStatic
    fun removeTab(context: Context, tabId: String) {
        getStore(context).dispatch(
            mozilla.components.browser.state.action.TabListAction.RemoveTabAction(tabId)
        )
    }

    /**
     * Selects a tab in [BrowserStore].
     */
    @JvmStatic
    fun selectTab(context: Context, tabId: String) {
        getStore(context).dispatch(
            mozilla.components.browser.state.action.TabListAction.SelectTabAction(tabId)
        )
    }

    /**
     * Updates back/forward navigation state in [BrowserStore].
     */
    @JvmStatic
    fun updateNavigationState(context: Context, tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        val st = getStore(context)
        st.dispatch(
            mozilla.components.browser.state.action.ContentAction.UpdateBackNavigationStateAction(tabId, canGoBack)
        )
        st.dispatch(
            mozilla.components.browser.state.action.ContentAction.UpdateForwardNavigationStateAction(tabId, canGoForward)
        )
    }

    /**
     * Updates URL and title in [BrowserStore].
     */
    @JvmStatic
    fun updateUrlAndTitle(context: Context, tabId: String, url: String, title: String) {
        val st = getStore(context)
        st.dispatch(
            mozilla.components.browser.state.action.ContentAction.UpdateUrlAction(tabId, url)
        )
        st.dispatch(
            mozilla.components.browser.state.action.ContentAction.UpdateTitleAction(tabId, title)
        )
    }

    /**
     * Updates progress in [BrowserStore].
     */
    @JvmStatic
    fun updateProgress(context: Context, tabId: String, progress: Int) {
        val st = getStore(context)
        st.dispatch(
            mozilla.components.browser.state.action.ContentAction.UpdateProgressAction(tabId, progress)
        )
    }

    /**
     * Updates loading state in [BrowserStore].
     */
    @JvmStatic
    fun updateLoadingState(context: Context, tabId: String, loading: Boolean) {
        val st = getStore(context)
        st.dispatch(
            mozilla.components.browser.state.action.ContentAction.UpdateLoadingStateAction(tabId, loading)
        )
    }

    /**
     * Toggles Reader Mode for the tab in [BrowserStore].
     */
    @JvmStatic
    fun toggleReaderMode(context: Context, tabId: String, active: Boolean) {
        val st = getStore(context)
        st.dispatch(
            mozilla.components.browser.state.action.ReaderAction.UpdateReaderActiveAction(tabId, active)
        )
    }

    /** Clears the official BrowserStore find-in-page result state for a tab. */
    @JvmStatic
    fun clearFindResults(context: Context, tabId: String) {
        getStore(context).dispatch(
            mozilla.components.browser.state.action.ContentAction.ClearFindResultsAction(tabId)
        )
    }

    /** Updates find-in-page match results in BrowserStore for the active tab. */
    @JvmStatic
    fun updateFindResults(context: Context, tabId: String, current: Int, total: Int) {
        getStore(context).dispatch(
            mozilla.components.browser.state.action.ContentAction.UpdateFindResultsAction(
                tabId,
                mozilla.components.browser.state.state.content.FindResultState(current = current, total = total)
            )
        )
    }

    @JvmStatic
    fun activateMediaSession(
        context: Context,
        tabId: String,
        controller: mozilla.components.concept.engine.mediasession.MediaSession.Controller
    ) {
        getStore(context).dispatch(
            mozilla.components.browser.state.action.MediaSessionAction.ActivatedMediaSessionAction(tabId, controller)
        )
    }

    @JvmStatic
    fun deactivateMediaSession(context: Context, tabId: String) {
        getStore(context).dispatch(
            mozilla.components.browser.state.action.MediaSessionAction.DeactivatedMediaSessionAction(tabId)
        )
    }

    @JvmStatic
    fun updateMediaPlaybackState(
        context: Context,
        tabId: String,
        state: mozilla.components.concept.engine.mediasession.MediaSession.PlaybackState
    ) {
        getStore(context).dispatch(
            mozilla.components.browser.state.action.MediaSessionAction.UpdateMediaPlaybackStateAction(tabId, state)
        )
    }

    @JvmStatic
    fun updateMediaPosition(
        context: Context,
        tabId: String,
        state: mozilla.components.concept.engine.mediasession.MediaSession.PositionState
    ) {
        getStore(context).dispatch(
            mozilla.components.browser.state.action.MediaSessionAction.UpdateMediaPositionStateAction(tabId, state)
        )
    }

    @JvmStatic
    fun updateMediaMetadata(
        context: Context,
        tabId: String,
        metadata: mozilla.components.concept.engine.mediasession.MediaSession.Metadata
    ) {
        getStore(context).dispatch(
            mozilla.components.browser.state.action.MediaSessionAction.UpdateMediaMetadataAction(tabId, metadata)
        )
    }

    @JvmStatic
    fun updateMediaMuted(context: Context, tabId: String, muted: Boolean) {
        getStore(context).dispatch(
            mozilla.components.browser.state.action.MediaSessionAction.UpdateMediaMutedAction(tabId, muted)
        )
    }

    @JvmStatic
    fun updateMediaFullscreen(
        context: Context,
        tabId: String,
        fullscreen: Boolean,
        metadata: mozilla.components.concept.engine.mediasession.MediaSession.ElementMetadata?
    ) {
        getStore(context).dispatch(
            mozilla.components.browser.state.action.MediaSessionAction.UpdateMediaFullscreenAction(tabId, fullscreen, metadata)
        )
    }

    /** Registers a completed offline archive in the official BrowserStore download history. */
    @JvmStatic
    fun addOfflineArchive(context: Context, url: String, filePath: String, title: String?) {
        val file = java.io.File(filePath)
        getStore(context).dispatch(
            mozilla.components.browser.state.action.DownloadAction.AddDownloadAction(
                mozilla.components.browser.state.state.content.DownloadState(
                    url = url,
                    fileName = file.name,
                    contentType = "text/html",
                    contentLength = if (file.exists()) file.length() else null,
                    currentBytesCopied = if (file.exists()) file.length() else 0L,
                    status = mozilla.components.browser.state.state.content.DownloadState.Status.COMPLETED,
                    directoryPath = file.parentFile?.absolutePath ?: filePath,
                    id = "offline-${filePath.hashCode()}"
                )
            )
        )
    }
}
