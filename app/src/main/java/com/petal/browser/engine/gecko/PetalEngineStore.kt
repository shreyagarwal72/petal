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
import mozilla.components.feature.prompts.PromptMiddleware
import mozilla.components.feature.prompts.file.FileUploadsDirCleaner
import mozilla.components.feature.prompts.file.FileUploadsDirCleanerMiddleware
import mozilla.components.support.utils.DefaultDownloadFileUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.mozilla.geckoview.GeckoRuntime
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.error.ErrorType
import mozilla.components.concept.engine.request.RequestInterceptor

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

    @Volatile
    private var fileUploadsDirCleaner: FileUploadsDirCleaner? = null

    @Volatile
    private var autoSaveStarted = false

    /**
     * Tabs whose engine session was just rebuilt from saved state. Gecko has not reported their
     * history yet, so the view treats Back as available for a short time (see bindTab).
     */
    private val restoredTabIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val mainScope = MainScope()

    @JvmStatic
    @Synchronized
    fun getEngine(context: Context): GeckoEngine {
        engine?.let { return it }

        val appContext = context.applicationContext
        val runtime = PetalGeckoRuntime.getOrCreate(appContext)

        val defaultSettings = DefaultSettings(
            requestInterceptor = object : RequestInterceptor {
                override fun onErrorRequest(
                    session: EngineSession,
                    errorType: ErrorType,
                    uri: String?
                ): RequestInterceptor.ErrorResponse? {
                    return try {
                        val errorPageUri = mozilla.components.browser.errorpages.ErrorPages.createUrlEncodedErrorPage(
                            appContext,
                            errorType,
                            uri
                        )
                        RequestInterceptor.ErrorResponse(errorPageUri)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to create url-encoded error page", t)
                        null
                    }
                }
            }
        )

        val newEngine = GeckoEngine(
            context = appContext,
            defaultSettings = defaultSettings,
            runtime = runtime
        )
        engine = newEngine
        Log.i(TAG, "Initialized Mozilla GeckoEngine wrapping PetalGeckoRuntime")
        try {
            com.petal.browser.extensions.PetalBuiltInExtensions.installAll(newEngine)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize built-in extensions: ${t.message}")
        }
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
            deleteFileFromStorage = { true }
        )
        val uploadCleaner = getFileUploadsDirCleaner(appContext)

        val newStore = BrowserStore(
            initialState = BrowserState(),
            middleware = listOf(
                downloadMiddleware,
                PromptMiddleware(),
                FileUploadsDirCleanerMiddleware(uploadCleaner),
                PetalSessionPrioritizationMiddleware(),
                PetalThumbnailsMiddleware()
            ) + engineMiddleware
        )
        store = newStore
        // Reload persisted (non-private) downloads so history and unfinished downloads survive
        // process death, the same way Fenix does at startup.
        try {
            newStore.dispatch(mozilla.components.browser.state.action.DownloadAction.RestoreDownloadsStateAction)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to restore downloads state: ${t.message}")
        }
        Log.i(TAG, "Initialized Mozilla BrowserStore with Prioritization, EngineMiddleware and DownloadMiddleware")
        return newStore
    }

    @JvmStatic
    @Synchronized
    fun getSessionStorage(context: Context): SessionStorage {
        sessionStorage?.let { return it }

        val appContext = context.applicationContext
        val currentEngine = getEngine(appContext)
        val storage = SessionStorage(
            context = appContext,
            engine = currentEngine,
            applicationScope = mainScope
        )
        sessionStorage = storage
        Log.i(TAG, "Initialized Mozilla SessionStorage")
        return storage
    }

    @JvmStatic
    @Synchronized
    fun getAutoSave(context: Context): AutoSave {
        autoSave?.let { return it }

        val appContext = context.applicationContext
        val currentStore = getStore(appContext)
        val currentStorage = getSessionStorage(appContext)
        val saver = AutoSave(
            store = currentStore,
            sessionStorage = RestoreGatedStorage(currentStorage),
            minimumIntervalMs = 2000L,
            applicationScope = mainScope
        )
        autoSave = saver
        Log.i(TAG, "Initialized Mozilla AutoSave")
        return saver
    }

    /**
     * Starts the same three save triggers Fenix uses: every 30 s while in the foreground, when the
     * app goes to the background, and when tabs / the selected tab / a page load change.
     * Safe to call more than once.
     */
    @JvmStatic
    @Synchronized
    fun startAutoSave(context: Context) {
        if (autoSaveStarted) return
        autoSaveStarted = true
        getAutoSave(context)
            .periodicallyInForeground(interval = 30, unit = TimeUnit.SECONDS)
            .whenGoingToBackground()
            .whenSessionsChange()
        Log.i(TAG, "Started Mozilla AutoSave triggers")
    }

    /** Returns true exactly once for a tab whose session was just restored from saved state. */
    @JvmStatic
    fun consumeRestoredFlag(tabId: String): Boolean = restoredTabIds.remove(tabId)

    /**
     * Never persists before the saved session has been restored into the store. SessionStorage.save()
     * DELETES the session file when the store holds no tabs, and an empty store before restore means
     * "not restored yet", not "the user closed every tab".
     */
    private class RestoreGatedStorage(private val delegate: SessionStorage) : AutoSave.Storage {
        override fun save(state: BrowserState): Boolean {
            if (!state.restoreComplete) return false
            return delegate.save(state)
        }
    }

    @JvmStatic
    @Synchronized
    fun getFileUploadsDirCleaner(context: Context): FileUploadsDirCleaner {
        fileUploadsDirCleaner?.let { return it }
        return FileUploadsDirCleaner(
            scope = mainScope,
            cacheDirectory = { context.applicationContext.cacheDir }
        ).also {
            fileUploadsDirCleaner = it
        }
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
     * Creates an [mozilla.components.concept.engine.EngineSession] and registers it as a [mozilla.components.browser.state.state.TabSessionState] in [BrowserStore],
     * linking the session so [mozilla.components.browser.state.engine.EngineMiddleware] actively tracks its state.
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
        st.dispatch(
            mozilla.components.browser.state.action.EngineAction.LinkEngineSessionAction(
                tabId = tabId,
                engineSession = session,
                skipLoading = false
            )
        )
        return Pair(tabState, session)
    }

    /**
     * Prepares or retrieves an [EngineSession] for the specified tab, linking it to [BrowserStore].
     * If the tab was restored lazily and has a serialized engineSessionState, it is restored
     * into the engine session with back/forward history intact.
     */
    @JvmStatic
    fun materializeTabSession(
        context: Context,
        tabId: String,
        fallbackUrl: String = "about:blank",
        fallbackTitle: String = "",
        isIncognito: Boolean = false,
        select: Boolean = true
    ): Pair<mozilla.components.browser.state.state.TabSessionState, mozilla.components.concept.engine.EngineSession> {
        val st = getStore(context)
        val eng = getEngine(context)

        val existingTab = st.state.tabs.firstOrNull { it.id == tabId }
        if (existingTab != null) {
            val existingSession = existingTab.engineState.engineSession
            val session = if (existingSession != null) {
                existingSession
            } else {
                val newSession = eng.createSession(private = existingTab.content.private)
                val state = existingTab.engineState.engineSessionState
                // Same rule as Mozilla's CreateEngineSessionMiddleware: skip the initial load ONLY when
                // the engine accepted the saved state. An empty/rejected state must load the URL,
                // otherwise the tab stays blank.
                val skipLoading = if (state != null) {
                    try {
                        val restored = newSession.restoreState(state)
                        if (restored) restoredTabIds.add(tabId)
                        restored
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to restore engine session state: ${t.message}")
                        false
                    }
                } else {
                    false
                }
                st.dispatch(
                    mozilla.components.browser.state.action.EngineAction.LinkEngineSessionAction(
                        tabId = tabId,
                        engineSession = newSession,
                        skipLoading = skipLoading
                    )
                )
                newSession
            }
            if (select) {
                st.dispatch(mozilla.components.browser.state.action.TabListAction.SelectTabAction(tabId))
            }
            val updatedTab = st.state.tabs.firstOrNull { it.id == tabId } ?: existingTab
            return Pair(updatedTab, session)
        }

        // Tab is not in store: create brand new tab
        return createTabSession(
            context = context,
            tabId = tabId,
            url = fallbackUrl,
            title = fallbackTitle,
            isIncognito = isIncognito,
            select = select
        )
    }

    /** Registers the prepared EngineSession supplied by an Android Components window request. */
    @JvmStatic
    fun adoptPreparedSession(
        context: Context,
        tabId: String,
        url: String,
        title: String,
        isIncognito: Boolean,
        session: mozilla.components.concept.engine.EngineSession,
        select: Boolean = true
    ): mozilla.components.browser.state.state.TabSessionState {
        val tabState = mozilla.components.browser.state.state.TabSessionState(
            id = tabId,
            content = mozilla.components.browser.state.state.ContentState(
                url = url,
                private = isIncognito,
                title = title
            ),
            engineState = mozilla.components.browser.state.state.EngineState(engineSession = session)
        )
        val st = getStore(context)
        st.dispatch(
            mozilla.components.browser.state.action.TabListAction.AddTabAction(tabState, select)
        )
        st.dispatch(
            mozilla.components.browser.state.action.EngineAction.LinkEngineSessionAction(
                tabId = tabId,
                engineSession = session,
                skipLoading = true
            )
        )
        return tabState
    }

    /**
     * Removes a tab from [BrowserStore].
     */
    @JvmStatic
    fun removeTab(context: Context, tabId: String) {
        getStore(context).dispatch(
            mozilla.components.browser.state.action.TabListAction.RemoveTabAction(tabId)
        )
        com.petal.browser.browser.PetalTabMetadataStore.remove(context, tabId)
    }

    @JvmStatic
    fun consumeWindowRequest(context: Context, tabId: String) {
        getStore(context).dispatch(
            mozilla.components.browser.state.action.ContentAction.ConsumeWindowRequestAction(tabId)
        )
    }

    /** Removes the given tabs from [BrowserStore] (ids that are not in the store are ignored). */
    @JvmStatic
    fun removeTabs(context: Context, tabIds: Collection<String>) {
        val st = getStore(context)
        for (id in tabIds) {
            if (st.state.tabs.any { it.id == id }) {
                st.dispatch(mozilla.components.browser.state.action.TabListAction.RemoveTabAction(id))
            }
        }
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
        if (url.startsWith("moz-extension://") && (url.contains("bootstrap.html") || url.contains("token="))) {
            return
        }
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
    fun updateMediaController(
        context: Context,
        tabId: String,
        controller: mozilla.components.concept.engine.mediasession.MediaSession.Controller?
    ) {
        if (controller == null) {
            deactivateMediaSession(context, tabId)
        } else {
            activateMediaSession(context, tabId, controller)
        }
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

    /**
     * Propagates OS Low Memory / Trim Memory events to Mozilla Components [TrimMemoryMiddleware].
     */
    @JvmStatic
    fun onLowMemory(context: Context, level: Int) {
        try {
            getStore(context).dispatch(
                mozilla.components.browser.state.action.SystemAction.LowMemoryAction(level)
            )
        } catch (_: Throwable) {}
    }

    /**
     * Propagates application pause lifecycle event to BrowserStore for background form data capture.
     */
    @JvmStatic
    fun onAppPause(context: Context) {
        try {
            getStore(context).dispatch(
                mozilla.components.browser.state.action.AppLifecycleAction.PauseAction
            )
        } catch (_: Throwable) {}
    }

    /**
     * Propagates application resume lifecycle event to BrowserStore.
     */
    @JvmStatic
    fun onAppResume(context: Context) {
        try {
            getStore(context).dispatch(
                mozilla.components.browser.state.action.AppLifecycleAction.ResumeAction
            )
        } catch (_: Throwable) {}
    }
}
