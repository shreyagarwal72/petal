package com.petal.browser.account.mozilla

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mozilla.components.browser.storage.sync.PlacesBookmarksStorage
import mozilla.components.browser.storage.sync.RemoteTabsStorage
import mozilla.components.feature.syncedtabs.storage.SyncedTabsStorage
import mozilla.components.lib.dataprotect.SecureAbove22Preferences
import mozilla.components.service.sync.logins.SyncableLoginsStorage
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.concept.sync.*
import mozilla.components.service.fxa.*
import mozilla.components.service.fxa.manager.*
import mozilla.components.service.fxa.sync.*
import java.util.concurrent.TimeUnit
import mozilla.appservices.fxaclient.FxaConfig
import mozilla.appservices.fxaclient.FxaServer
import mozilla.components.concept.base.crash.Breadcrumb
import mozilla.components.concept.base.crash.CrashReporting

private object NoopCrashReporter : CrashReporting {
    override fun submitCaughtException(throwable: Throwable) = kotlinx.coroutines.Job().apply { complete() }
    override fun recordCrashBreadcrumb(breadcrumb: Breadcrumb) {}
}

sealed interface FxaUi {
    data object SignedOut : FxaUi
    data object Connecting : FxaUi
    data object NeedsReauth : FxaUi
    data class SignedIn(val email: String, val displayName: String?, val avatarUrl: String?) : FxaUi
}

data class FxaDevice(val id: String, val name: String, val canReceiveTabs: Boolean)
data class RemoteTab(val device: String, val title: String, val url: String)
data class ReceivedTab(val from: String, val title: String, val url: String)

/**
 * Petal's Firefox Account layer. Everything account-related (OAuth, scoped keys, token refresh, device
 * records, Firefox Sync with its encryption) is done by Mozilla's own `service-firefox-accounts`
 * FxaAccountManager; this object only owns the app wiring: sign-in via a normal Petal tab, observing state,
 * and mirroring Petal's bookmark/history database into Places so the official sync engines can sync it.
 */
object PetalFxa {

    /**
     * OAuth client registered with Mozilla. This is the public Mozilla Reference Browser client. For a
     * released product, request your own client_id and redirect URL from the Firefox Accounts team and
     * change these two constants (https://mozilla.github.io/application-services/docs/accounts/welcome.html).
     */
    const val CLIENT_ID = "3c49430b43dfba77"
    const val REDIRECT_URL = "https://accounts.firefox.com/oauth/success/$CLIENT_ID"

    private object PetalEntryPoint : FxAEntryPoint { override val entryName = "petal" }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var manager: FxaAccountManager? = null
    private var bridge: PetalPlacesBridge? = null
    private var loginsBridge: PetalLoginsBridge? = null
    private var syncedTabs: SyncedTabsStorage? = null
    private var tabsStorage: RemoteTabsStorage? = null
    private var started = false
    private lateinit var appContext: Context

    private val _state = MutableStateFlow<FxaUi>(FxaUi.SignedOut)
    val state: StateFlow<FxaUi> = _state.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()
    private val _devices = MutableStateFlow<List<FxaDevice>>(emptyList())
    val devices: StateFlow<List<FxaDevice>> = _devices.asStateFlow()
    private val _received = MutableStateFlow<List<ReceivedTab>>(emptyList())
    val receivedTabs: StateFlow<List<ReceivedTab>> = _received.asStateFlow()
    private val _remoteTabs = MutableStateFlow<List<RemoteTab>>(emptyList())
    val remoteTabs: StateFlow<List<RemoteTab>> = _remoteTabs.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() { _error.value = null }
    fun dismissReceived(tab: ReceivedTab) { _received.value = _received.value - tab }

    /** Call from MainActivity. Safe to call on every activity creation. */
    @Synchronized
    fun init(activity: ComponentActivity) {
        appContext = activity.applicationContext
        _lastSync.value = PetalFxaSettings.lastSync(appContext)
        if (manager == null) {
            val history = PlacesHistoryStorage(appContext)
            val bookmarks = PlacesBookmarksStorage(appContext)
            bridge = PetalPlacesBridge(appContext, history, bookmarks)
            GlobalSyncableStoreProvider.configureStore(SyncEngine.History to lazy { history })
            GlobalSyncableStoreProvider.configureStore(SyncEngine.Bookmarks to lazy { bookmarks })
            val tabs = RemoteTabsStorage(appContext, NoopCrashReporter)
            tabsStorage = tabs
            GlobalSyncableStoreProvider.configureStore(SyncEngine.Tabs to lazy { tabs })
            val loginsStorage = SyncableLoginsStorage(appContext, lazy { SecureAbove22Preferences(appContext, "petal_fxa_logins", false) })
            loginsBridge = PetalLoginsBridge(appContext, loginsStorage)
            GlobalSyncableStoreProvider.configureStore(SyncEngine.Passwords to lazy { loginsStorage })
            applyEngineSettings()

            manager = FxaAccountManager(
                context = appContext,
                serverConfig = FxaConfig(
                    server = FxaServer.Release,
                    clientId = CLIENT_ID,
                    redirectUri = REDIRECT_URL
                ),
                deviceConfig = DeviceConfig(
                    name = PetalFxaSettings.deviceName(appContext),
                    type = DeviceType.MOBILE,
                    capabilities = setOf(DeviceCapability.SEND_TAB),
                    secureStateAtRest = true
                ),
                syncConfig = SyncConfig(
                    supportedEngines = setOf(SyncEngine.History, SyncEngine.Bookmarks, SyncEngine.Tabs, SyncEngine.Passwords),
                    periodicSyncConfig = PeriodicSyncConfig(periodMinutes = 240, initialDelayMinutes = 5)
                ),
                crashReporter = NoopCrashReporter
            )
        }
        val m = manager!!
        if (syncedTabs == null) {
            // Publishes this device's open tabs and reads other devices' tabs (official feature-syncedtabs).
            syncedTabs = SyncedTabsStorage(
                accountManager = m,
                store = com.petal.browser.engine.gecko.PetalEngineStore.getStore(appContext),
                tabsStorage = tabsStorage!!,
                maxActiveTime = TimeUnit.DAYS.toMillis(14)
            ).also { it.start() }
        }
        m.register(accountObserver, activity, false)
        m.registerForSyncEvents(syncObserver, activity, false)
        m.registerForAccountEvents(eventsObserver, activity, false)
        if (!started) {
            started = true
            scope.launch { try { m.start() } catch (e: Throwable) { _error.value = e.message } }
        }
    }

    private val accountObserver = object : AccountObserver {
        override fun onAuthenticated(account: OAuthAccount, authType: AuthType) {
            _state.value = FxaUi.SignedIn(m()?.accountProfile()?.email.orEmpty(), m()?.accountProfile()?.displayName, m()?.accountProfile()?.avatar?.url)
            refreshDevices()
            syncNow()
        }
        override fun onProfileUpdated(profile: Profile) {
            _state.value = FxaUi.SignedIn(profile.email.orEmpty(), profile.displayName, profile.avatar?.url)
        }
        override fun onLoggedOut() { _state.value = FxaUi.SignedOut; _devices.value = emptyList() }
        override fun onAuthenticationProblems() { _state.value = FxaUi.NeedsReauth }
        override fun onFlowError(error: AuthFlowError) {
            _state.value = FxaUi.SignedOut
            _error.value = "Sign-in failed (${error.javaClass.simpleName})."
        }
    }

    private val syncObserver = object : SyncStatusObserver {
        override fun onStarted() { _syncing.value = true }
        override fun onIdle() {
            _syncing.value = false
            val now = System.currentTimeMillis()
            PetalFxaSettings.setLastSync(appContext, now); _lastSync.value = now
            scope.launch {
                try { bridge?.pullHistory(); bridge?.reconcileBookmarks() } catch (_: Throwable) {}
                reconcilePasswords()
                refreshRemoteTabs()
            }
        }
        override fun onError(error: Exception?) { _syncing.value = false; _error.value = error?.message ?: "Sync failed." }
    }

    private val eventsObserver = object : AccountEventsObserver {
        override fun onEvents(events: List<AccountEvent>) {
            events.filterIsInstance<AccountEvent.DeviceCommandIncoming>()
                .map { it.command }
                .filterIsInstance<DeviceCommandIncoming.TabReceived>()
                .forEach { cmd ->
                    val from = cmd.from?.displayName ?: "another device"
                    _received.value = _received.value + cmd.entries.map { ReceivedTab(from, it.title, it.url) }
                }
        }
    }

    private fun m() = manager

    // ── sign in / out ──
    suspend fun beginSignIn(): String? {
        val mgr = manager ?: return null
        _state.value = FxaUi.Connecting
        val url = try { mgr.beginAuthentication(entrypoint = PetalEntryPoint) } catch (e: Throwable) { _error.value = e.message; null }
        if (url == null) _state.value = FxaUi.SignedOut
        return url
    }

    fun isRedirect(url: String) = url.startsWith(REDIRECT_URL)

    /** Handles the sign-in redirect seen by a browser tab. Returns true if the URL was consumed. */
    fun finishFromRedirect(url: String): Boolean {
        if (!isRedirect(url)) return false
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val st = uri.getQueryParameter("state")
        val mgr = manager
        if (code == null || st == null || mgr == null) return false
        scope.launch {
            try { mgr.finishAuthentication(FxaAuthData(AuthType.Signin, code, st)) }
            catch (e: Throwable) { _state.value = FxaUi.SignedOut; _error.value = e.message }
        }
        return true
    }

    fun cancelSignIn() { if (_state.value == FxaUi.Connecting) _state.value = FxaUi.SignedOut }

    fun signOut() { scope.launch { try { manager?.logout() } catch (e: Throwable) { _error.value = e.message } } }

    // ── sync ──
    fun syncNow(reason: SyncReason = SyncReason.User) {
        val mgr = manager ?: return
        scope.launch {
            try { bridge?.reconcileBookmarks() } catch (_: Throwable) {}
            reconcilePasswords()
            mgr.syncNow(reason)
            refreshDevices()
        }
    }

    /** Pushes the engine toggles into Mozilla's Sync engines and re-syncs. */
    fun applyEngineSettings() {
        val m = manager ?: return
        scope.launch {
            try {
                m.setEngineEnabled(SyncEngine.Bookmarks, PetalFxaSettings.bookmarks(appContext))
                m.setEngineEnabled(SyncEngine.History, PetalFxaSettings.history(appContext))
                m.setEngineEnabled(SyncEngine.Tabs, PetalFxaSettings.tabs(appContext))
                m.setEngineEnabled(SyncEngine.Passwords, PetalFxaSettings.passwords(appContext))
            } catch (_: Throwable) {}
        }
    }

    fun setEngine(engine: SyncEngine, enabled: Boolean) {
        when (engine) {
            SyncEngine.Bookmarks -> PetalFxaSettings.setBookmarks(appContext, enabled)
            SyncEngine.Tabs -> PetalFxaSettings.setTabs(appContext, enabled)
            SyncEngine.Passwords -> PetalFxaSettings.setPasswords(appContext, enabled)
            else -> PetalFxaSettings.setHistory(appContext, enabled)
        }
        manager?.let { m ->
            scope.launch {
                try { m.setEngineEnabled(engine, enabled) } catch (_: Throwable) {}
            }
        }
        syncNow(SyncReason.EngineChange)
    }

    /** Re-applies saved settings, used after a backup is restored. */
    fun onSettingsRestored(context: Context) {
        if (manager == null) return
        appContext = context.applicationContext
        applyEngineSettings()
        renameDevice(PetalFxaSettings.deviceName(appContext))
        if (_state.value is FxaUi.SignedIn) syncNow(SyncReason.EngineChange)
    }

    fun mirrorVisit(context: Context, url: String, title: String) {
        val b = bridge ?: return
        if (_state.value !is FxaUi.SignedIn || !PetalFxaSettings.history(context)) return
        scope.launch { try { b.mirrorVisit(url, title) } catch (_: Throwable) {} }
    }

    private suspend fun reconcilePasswords() {
        if (!PetalFxaSettings.passwords(appContext) || _state.value !is FxaUi.SignedIn) return
        try { loginsBridge?.reconcile() } catch (e: Throwable) { _error.value = "Password sync: ${e.message}" }
    }

    /** Tabs open on the account's other devices, from the official synced-tabs storage. */
    fun refreshRemoteTabs() {
        val st = syncedTabs ?: return
        scope.launch {
            try {
                _remoteTabs.value = st.getSyncedDeviceTabs().flatMap { dt ->
                    dt.tabs.mapNotNull { t -> t.active()?.let { RemoteTab(dt.device.displayName, it.title, it.url) } }
                }
            } catch (_: Throwable) {}
        }
    }

    // ── devices / send tab ──
    fun refreshDevices() {
        val account = manager?.authenticatedAccount() ?: return
        scope.launch {
            try {
                val c = account.deviceConstellation()
                c.refreshDevices()
                _devices.value = c.state()?.otherDevices.orEmpty().map {
                    FxaDevice(it.id, it.displayName, it.capabilities.contains(DeviceCapability.SEND_TAB))
                }
                c.pollForCommands()
                refreshRemoteTabs()
            } catch (_: Throwable) {}
        }
    }

    fun renameDevice(name: String) {
        PetalFxaSettings.setDeviceName(appContext, name)
        val account = manager?.authenticatedAccount() ?: return
        scope.launch { try { account.deviceConstellation().setDeviceName(name, appContext) } catch (_: Throwable) {} }
    }

    suspend fun sendTab(deviceId: String, title: String, url: String): Boolean {
        val account = manager?.authenticatedAccount() ?: return false
        return try {
            account.deviceConstellation().sendCommandToDevice(deviceId, DeviceCommandOutgoing.SendTab(title.ifBlank { url }, url, TabPrivacy.Normal))
        } catch (_: Throwable) { false }
    }
}
