package com.petal.browser.engine.gecko

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.session.storage.AutoSave
import mozilla.components.browser.session.storage.SessionStorage
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.engine.middleware.SessionPrioritizationMiddleware
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.downloads.DownloadMiddleware
import mozilla.components.feature.prompts.PromptMiddleware
import mozilla.components.feature.prompts.file.FileUploadsDirCleaner
import mozilla.components.feature.prompts.file.FileUploadsDirCleanerMiddleware
import mozilla.components.feature.readerview.ReaderViewMiddleware
import mozilla.components.support.utils.DefaultDownloadFileUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.mozilla.geckoview.GeckoRuntime
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.request.RequestInterceptor
import mozilla.components.concept.engine.request.ErrorType
import androidx.preference.PreferenceManager
import mozilla.components.browser.thumbnails.ThumbnailsMiddleware
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.engine.gecko.fetch.GeckoViewFetchClient
import mozilla.components.support.base.android.NotificationsDelegate
import androidx.core.app.NotificationManagerCompat
import mozilla.components.feature.webnotifications.WebNotificationFeature

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
    private var thumbnailStorage: ThumbnailStorage? = null

    @Volatile
    private var browserIcons: BrowserIcons? = null

    @Volatile
    private var notificationsDelegate: NotificationsDelegate? = null

    @Volatile
    private var webNotificationFeature: WebNotificationFeature? = null

    @Volatile
    private var autoSave: AutoSave? = null

    @Volatile
    private var fileUploadsDirCleaner: FileUploadsDirCleaner? = null

    @Volatile
    private var searchUseCases: mozilla.components.feature.search.SearchUseCases? = null

    @Volatile
    private var pinnedSiteStorage: mozilla.components.feature.top.sites.PinnedSiteStorage? = null

    @Volatile
    private var defaultTopSitesStorage: mozilla.components.feature.top.sites.DefaultTopSitesStorage? = null

    @Volatile
    private var manifestStorage: mozilla.components.feature.pwa.ManifestStorage? = null

    @Volatile
    var pendingTabSelectionOnResume: String? = null

    @Volatile
    private var addonManager: mozilla.components.feature.addons.AddonManager? = null

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
            // Official Firefox history tracking: types visits (link/reload/redirect), skips error
            // pages and non-top-level loads, and records page titles, all into Places.
            historyTrackingDelegate = mozilla.components.feature.session.HistoryDelegate(
                lazy { com.petal.browser.database.PetalPlacesProvider.getHistoryStorage(appContext) }
            ),
            requestInterceptor = object : RequestInterceptor {
                override fun onErrorRequest(
                    session: EngineSession,
                    errorType: ErrorType,
                    uri: String?
                ): RequestInterceptor.ErrorResponse? {
                    try {
                        com.petal.browser.offlinegame.PetalOfflineGameRegistry.notifyError(session, uri, errorType.name)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Offline game hook failed", t)
                    }
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

        // Register official Mozilla concept-engine ServiceWorkerDelegate
        try {
            newEngine.registerServiceWorkerDelegate(object : mozilla.components.concept.engine.serviceworker.ServiceWorkerDelegate {
                override fun addNewTab(engineSession: EngineSession): Boolean {
                    val act = PetalGeckoRuntime.getForegroundActivity() as? com.petal.browser.activity.MainActivity
                    val tabId = java.util.UUID.randomUUID().toString()
                    PetalEngineStore.adoptPreparedSession(
                        context = appContext,
                        tabId = tabId,
                        url = "about:blank",
                        title = "",
                        isIncognito = false,
                        session = engineSession,
                        select = true
                    )
                    if (act != null) {
                        act.runOnUiThread {
                            act.viewModel.selectTab(tabId)
                        }
                    } else {
                        // Cold start / notification tap when activity is not foreground: queue tab selection for MainActivity.onResume
                        pendingTabSelectionOnResume = tabId
                    }
                    return true
                }
            })
            Log.i(TAG, "Registered Mozilla concept-engine ServiceWorkerDelegate on GeckoEngine")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not register ServiceWorkerDelegate on GeckoEngine: ${t.message}")
        }

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
            trimMemoryAutomatically = false
        )
        val downloadMiddleware = DownloadMiddleware(
            applicationContext = appContext,
            downloadServiceClass = com.petal.browser.download.PetalMozillaDownloadService::class.java,
            downloadFileUtils = DefaultDownloadFileUtils(appContext),
            deleteFileFromStorage = { true }
        )
        val uploadCleaner = getFileUploadsDirCleaner(appContext)
        // Closed tabs are recorded only by PetalRecentlyClosedManager (single store, owns the vault UI
        // and lock). Mozilla's RecentlyClosedMiddleware was a second, never-read copy and is removed.

        val thumbnailsMiddleware = PetalThumbnailsMiddleware()

        val newStore = BrowserStore(
            initialState = BrowserState(),
            middleware = listOf(
                // First: takes <input type="file"> requests before Mozilla's PromptFeature / reducer.
                PetalFileUploadMiddleware(),
                downloadMiddleware,
                PromptMiddleware(),
                FileUploadsDirCleanerMiddleware(uploadCleaner),
                SessionPrioritizationMiddleware(),
                ReaderViewMiddleware(),
                thumbnailsMiddleware
            ) + engineMiddleware
        )
        store = newStore
        try {
            mozilla.components.support.webextensions.WebExtensionSupport.initialize(
                runtime = currentEngine,
                store = newStore
            )
            Log.i(TAG, "Initialized Mozilla WebExtensionSupport")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize WebExtensionSupport: ${t.message}")
        }
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
    fun getThumbnailStorage(context: Context): ThumbnailStorage {
        thumbnailStorage?.let { return it }
        val storage = ThumbnailStorage(context.applicationContext)
        thumbnailStorage = storage
        Log.i(TAG, "Initialized Mozilla ThumbnailStorage")
        return storage
    }

    @JvmStatic
    @Synchronized
    fun getBrowserIcons(context: Context): BrowserIcons {
        browserIcons?.let { return it }
        val appContext = context.applicationContext
        val rt = PetalGeckoRuntime.getOrCreate(appContext)
        val client = GeckoViewFetchClient(context = appContext, runtime = rt)
        val icons = BrowserIcons(context = appContext, httpClient = client)
        browserIcons = icons
        Log.i(TAG, "Initialized Mozilla BrowserIcons")
        return icons
    }

    @JvmStatic
    @Synchronized
    fun getNotificationsDelegate(context: Context): NotificationsDelegate {
        notificationsDelegate?.let { return it }
        val delegate = NotificationsDelegate(NotificationManagerCompat.from(context.applicationContext))
        notificationsDelegate = delegate
        return delegate
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
    fun getSearchUseCases(context: Context): mozilla.components.feature.search.SearchUseCases {
        searchUseCases?.let { return it }
        val s = getStore(context)
        val useCases = mozilla.components.feature.search.SearchUseCases(
            s,
            mozilla.components.feature.tabs.TabsUseCases(s),
            mozilla.components.feature.session.SessionUseCases(s)
        )
        searchUseCases = useCases
        migrateSearchSettingsOnce(context.applicationContext, useCases)
        Log.i(TAG, "Initialized Mozilla SearchUseCases")
        return useCases
    }

    @JvmStatic
    @Synchronized
    fun getPinnedSiteStorage(context: Context): mozilla.components.feature.top.sites.PinnedSiteStorage {
        pinnedSiteStorage?.let { return it }
        val storage = mozilla.components.feature.top.sites.PinnedSiteStorage(context.applicationContext)
        pinnedSiteStorage = storage
        migrateTopSitesOnce(context.applicationContext, storage)
        Log.i(TAG, "Initialized Mozilla PinnedSiteStorage")
        return storage
    }

    @JvmStatic
    @Synchronized
    fun getDefaultTopSitesStorage(context: Context): mozilla.components.feature.top.sites.DefaultTopSitesStorage {
        defaultTopSitesStorage?.let { return it }
        val pinned = getPinnedSiteStorage(context)
        val history = com.petal.browser.database.PetalPlacesProvider.getHistoryStorage(context)
        val storage = mozilla.components.feature.top.sites.DefaultTopSitesStorage(pinned, history)
        defaultTopSitesStorage = storage
        Log.i(TAG, "Initialized Mozilla DefaultTopSitesStorage")
        return storage
    }

    private fun migrateSearchSettingsOnce(context: Context, useCases: mozilla.components.feature.search.SearchUseCases) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (sp.getBoolean("sp_search_migrated_v1", false)) return
        val customUrl = sp.getString("sp_search_engine_custom", "")?.trim()
        if (!customUrl.isNullOrEmpty()) {
            try {
                val customEngine = mozilla.components.browser.state.search.SearchEngine(
                    id = "custom_petal_engine",
                    name = "Custom",
                    icon = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888),
                    inputEncoding = "UTF-8",
                    type = mozilla.components.browser.state.search.SearchEngine.Type.CUSTOM,
                    resultUrls = listOf(customUrl),
                    suggestUrl = null,
                    trendingUrl = null,
                    isGeneral = true,
                    isOptional = false,
                    telemetrySuffix = null
                )
                useCases.addSearchEngine(customEngine)
            } catch (_: Throwable) {}
        }
        sp.edit().putBoolean("sp_search_migrated_v1", true).apply()
    }

    private fun migrateTopSitesOnce(context: Context, storage: mozilla.components.feature.top.sites.PinnedSiteStorage) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (sp.getBoolean("sp_top_sites_migrated_v1", false)) return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val existing = com.petal.browser.compose.home.loadHomeShortcuts(context)
                for (shortcut in existing) {
                    storage.addPinnedSite(title = shortcut.label, url = shortcut.url)
                }
                sp.edit().putBoolean("sp_top_sites_migrated_v1", true).apply()
            } catch (_: Throwable) {}
        }
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
            sessionStorage = RestoreGatedStorage(appContext, currentStorage),
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

    // TEMPORARY restore diagnostics (finding #3). Filter logcat with tag PetalRestoreDbg.
    const val RESTORE_DEBUG = true
    @JvmStatic
    fun dbg(msg: String) { if (RESTORE_DEBUG) Log.i("PetalRestoreDbg", msg) }

    // tabId -> "the state read from disk holds more than one history entry". Set when a saved tab is
    // put into the store, independent of which code path later creates its engine session, so Back
    // can trust restored history even if materializeTabSession was not the creator.
    private val diskBackHint: MutableMap<String, Boolean> = ConcurrentHashMap()

    /** Records, from the engine state restored from disk, whether Back should be available. */
    @JvmStatic
    fun noteDiskState(tabId: String, state: mozilla.components.concept.engine.EngineSessionState?) {
        if (state == null) { dbg("disk[$tabId] engineState=null"); return }
        val hasHistory = try {
            val jsonMethod = state.javaClass.methods.firstOrNull { it.name == "toJSON" && it.parameterCount == 0 }
            if (jsonMethod != null) {
                jsonMethod.isAccessible = true
                val json = jsonMethod.invoke(state) as? org.json.JSONObject
                if (json != null) historyHasBack(json) else true
            } else {
                true
            }
        } catch (t: Throwable) { true }
        diskBackHint[tabId] = hasHistory
        dbg("disk[$tabId] engineState present, backHint=$hasHistory")
    }

    /** True once for a tab whose disk state had back history; consumed by bindTab. */
    @JvmStatic
    fun consumeDiskBackHint(tabId: String): Boolean = diskBackHint.remove(tabId) == true

    @JvmStatic
    fun historyHasBack(json: org.json.JSONObject): Boolean = PetalSessionRules.historyHasBack(json)

    /**
     * Never persists before the saved session has been restored into the store. SessionStorage.save()
     * DELETES the session file when the store holds no tabs, and an empty store before restore means
     * "not restored yet", not "the user closed every tab".
     */
    private class RestoreGatedStorage(
        private val context: Context,
        private val delegate: SessionStorage
    ) : AutoSave.Storage {
        override fun save(state: BrowserState): Boolean = writeSession(context, delegate, state)
    }

    @JvmStatic
    fun isEphemeralTabId(id: String): Boolean = PetalSessionRules.isEphemeralTabId(id)

    @Volatile private var lastPersistedSelectedId: String? = null

    private const val SESSION_FILE = "mozilla_components_session_storage_gecko.json"
    private const val SESSION_BACKUP_FILE = "mozilla_components_session_storage_gecko.json.bak"
    private const val BACKUP_MIN_INTERVAL_MS = 60_000L
    private val sessionWriteLock = Any()

    /**
     * The single place session state is written to disk. AutoSave and the onStop flush both go
     * through here, serialized by [sessionWriteLock], so writes can never interleave. Before
     * overwriting, the previous file is kept as a rolling backup if (and only if) it parses.
     */
    @JvmStatic
    fun writeSession(context: Context, storage: SessionStorage, state: BrowserState): Boolean {
        if (!state.restoreComplete) return false
        val toWrite = PetalSessionRules.withoutEphemeralTabs(state, lastPersistedSelectedId)
        toWrite.selectedTabId?.let { lastPersistedSelectedId = it }
        synchronized(sessionWriteLock) {
            try { rollBackupLocked(context) } catch (t: Throwable) {
                Log.w(TAG, "Session backup skipped: ${t.message}")
            }
            return storage.save(toWrite)
        }
    }

    private fun rollBackupLocked(context: Context) {
        val dir = context.applicationContext.filesDir
        val main = java.io.File(dir, SESSION_FILE)
        val bak = java.io.File(dir, SESSION_BACKUP_FILE)
        if (!main.exists() || main.length() < 2L) return
        if (bak.exists() && System.currentTimeMillis() - bak.lastModified() < BACKUP_MIN_INTERVAL_MS) return
        // Only a file that still parses is a "last good" session.
        org.json.JSONObject(main.readText())
        main.copyTo(bak, overwrite = true)
    }

    /** Replaces the session file with [text] (must be a JSON object). Locked, atomic, resets the backup. */
    @JvmStatic
    fun replaceSessionFile(context: Context, text: String): Boolean = synchronized(sessionWriteLock) {
        try {
            org.json.JSONObject(text)
            val dir = context.applicationContext.filesDir
            val tmp = java.io.File(dir, "$SESSION_FILE.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(java.io.File(dir, SESSION_FILE))) {
                java.io.File(dir, SESSION_FILE).writeText(text, Charsets.UTF_8)
                tmp.delete()
            }
            java.io.File(dir, SESSION_BACKUP_FILE).delete() // stale after an import
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Refused to replace session file: ${t.message}")
            false
        }
    }

    /** Deletes the session file AND its rolling backup (privacy: a cleared session must not come back). */
    @JvmStatic
    fun deleteSessionFiles(context: Context) = synchronized(sessionWriteLock) {
        val dir = context.applicationContext.filesDir
        java.io.File(dir, SESSION_FILE).delete()
        java.io.File(dir, SESSION_BACKUP_FILE).delete()
        java.io.File(dir, "$SESSION_FILE.tmp").delete()
    }

    /**
     * If the main session file is missing/corrupt, swaps in the rolling backup so the next
     * restore can read it. Returns true if the backup was put in place.
     */
    @JvmStatic
    fun recoverSessionFromBackup(context: Context): Boolean = synchronized(sessionWriteLock) {
        val dir = context.applicationContext.filesDir
        val main = java.io.File(dir, SESSION_FILE)
        val bak = java.io.File(dir, SESSION_BACKUP_FILE)
        if (!bak.exists() || bak.length() < 2L) return false
        return try {
            org.json.JSONObject(bak.readText())
            bak.copyTo(main, overwrite = true)
            Log.w(TAG, "Session file unreadable; restored from rolling backup")
            true
        } catch (t: Throwable) { false }
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
    @Synchronized
    fun getManifestStorage(context: Context): mozilla.components.feature.pwa.ManifestStorage {
        manifestStorage?.let { return it }
        val appContext = context.applicationContext
        val storage = mozilla.components.feature.pwa.ManifestStorage(appContext)
        manifestStorage = storage
        return storage
    }

    @JvmStatic
    @Synchronized
    fun getAddonManager(context: Context): mozilla.components.feature.addons.AddonManager {
        addonManager?.let { return it }
        val appContext = context.applicationContext
        val currentStore = getStore(appContext)
        val currentEngine = getEngine(appContext)
        val client = com.petal.browser.download.PetalOkHttpFetchClient()
        val provider = mozilla.components.feature.addons.amo.AMOAddonsProvider(
            context = appContext,
            client = client
        )
        val updater = mozilla.components.feature.addons.update.DefaultAddonUpdater(
            applicationContext = appContext,
            store = currentStore
        )
        val manager = mozilla.components.feature.addons.AddonManager(
            store = currentStore,
            runtime = currentEngine,
            addonsProvider = provider,
            addonUpdater = updater,
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO
        )
        addonManager = manager
        return manager
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

    @JvmStatic
    @JvmOverloads
    fun createEngineSession(context: Context, isPrivate: Boolean = false): EngineSession {
        return getEngine(context).createSession(private = isPrivate)
    }

    @JvmStatic
    fun loadUrl(session: EngineSession, url: String) {
        session.loadUrl(url)
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
     * Adds a tab WITHOUT an engine session (lazy, like Fenix's background and restored tabs). The
     * session is created by [materializeTabSession] the first time the tab is selected, so a tab
     * opened in the background costs no Gecko process memory and does not start loading until
     * the user opens it.
     */
    @JvmStatic
    fun createLazyTab(
        context: Context,
        tabId: String,
        url: String,
        title: String = "",
        isIncognito: Boolean = false
    ) {
        val tabState = mozilla.components.browser.state.state.TabSessionState(
            id = tabId,
            content = mozilla.components.browser.state.state.ContentState(
                url = url,
                private = isIncognito,
                title = title
            )
        )
        getStore(context).dispatch(
            mozilla.components.browser.state.action.TabListAction.AddTabAction(tabState, select = false)
        )
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
                        dbg("materialize[$tabId] restoreState=$restored")
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
        // Single trim path (EngineMiddleware's trimMemoryAutomatically is off). UI_HIDDEN is
        // "app in background", not memory pressure, so it must not unlink background sessions.
        if (!PetalTabSuspender.isRealPressure(level)) return
        try {
            PetalTabSuspender.suspendUnderPressure(context, level)
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
