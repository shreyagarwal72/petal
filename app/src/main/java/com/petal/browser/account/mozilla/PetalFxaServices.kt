/*
 * PetalFxaServices.kt
 * ─────────────────────────────────────────────────────────────────────────
 * The ONLY file that talks to Mozilla's official android-components Firefox Account
 * stack (mozilla.components.service.fxa.*). Everything else in Petal goes through
 * [FxAccountManager] / [PetalMozillaSyncManager], which delegate here.
 *
 * Mirrors Fenix's BackgroundServices.kt:
 *   ServerConfig + DeviceConfig + SyncConfig  ->  FxaAccountManager
 *   GlobalSyncableStoreProvider.configureStore(engine to store)
 *   AccountObserver / SyncStatusObserver / AccountEventsObserver
 *
 * Real OAuth (PKCE, state, scoped keys), device registration, Send Tab and Sync15
 * crypto are done by Mozilla's Rust application-services - nothing is simulated here.
 *
 * NOTE FOR MAINTAINERS: if a future android-components bump changes a signature, this
 * is the single file to adjust. Cross-check against:
 *   mobile/android/fenix/.../components/BackgroundServices.kt
 *   mobile/android/android-components/components/service/firefox-accounts/
 */
package com.petal.browser.account.mozilla

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mozilla.components.browser.storage.sync.PlacesBookmarksStorage
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.browser.storage.sync.RemoteTabsStorage
import mozilla.components.concept.sync.AccountEvent
import mozilla.components.concept.sync.AccountEventsObserver
import mozilla.components.concept.sync.AccountObserver
import mozilla.components.concept.sync.AuthFlowError
import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.DeviceCapability
import mozilla.components.concept.sync.DeviceCommandIncoming
import mozilla.components.concept.sync.DeviceCommandOutgoing
import mozilla.components.concept.sync.DeviceType
import mozilla.components.concept.sync.FxAEntryPoint
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.concept.sync.Profile
import mozilla.components.service.fxa.DeviceConfig
import mozilla.components.service.fxa.FxaAuthData
import mozilla.components.service.fxa.PeriodicSyncConfig
import mozilla.components.service.fxa.ServerConfig
import mozilla.components.service.fxa.SyncConfig
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.service.fxa.sync.GlobalSyncableStoreProvider
import mozilla.components.service.fxa.sync.SyncReason
import mozilla.components.service.fxa.sync.SyncStatusObserver
import mozilla.components.service.fxa.FxaServer
import mozilla.components.service.fxa.SyncEngine as MozEngine

/** A device that can receive a tab (another Firefox / Petal signed in to the same account). */
data class FxaDevice(val id: String, val name: String, val type: String, val canReceiveTabs: Boolean)

/** Entry point name reported to Mozilla's account telemetry/server (shown as `entrypoint=` in the URL). */
object PetalFxaEntryPoint : FxAEntryPoint {
    override val entryName: String = "petal-settings"
}

internal class PetalFxaServices private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Stores that Firefox Sync reads/writes (Rust Places databases) ─────────────────────────
    val historyStorage: Lazy<PlacesHistoryStorage> = lazy { PlacesHistoryStorage(appContext) }
    val bookmarksStorage: Lazy<PlacesBookmarksStorage> = lazy { PlacesBookmarksStorage(appContext) }
    val tabsStorage: Lazy<RemoteTabsStorage> = lazy { RemoteTabsStorage(appContext) }

    private val supportedEngines: Set<MozEngine> =
        setOf(MozEngine.History, MozEngine.Bookmarks, MozEngine.Tabs)

    val accountManager: FxaAccountManager

    // ── Observable state for the UI ───────────────────────────────────────────────────────────
    private val _state = MutableStateFlow<FxaState>(FxaState.SignedOut)
    val state: StateFlow<FxaState> = _state.asStateFlow()

    private val _syncRunning = MutableStateFlow(false)
    val syncRunning: StateFlow<Boolean> = _syncRunning.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private val _devices = MutableStateFlow<List<FxaDevice>>(emptyList())
    val devices: StateFlow<List<FxaDevice>> = _devices.asStateFlow()

    /** Invoked after every successful sync so the app can pull Places -> Petal database. */
    @Volatile var onSyncFinished: ((Boolean) -> Unit)? = null

    /** Invoked when another device sends us a tab. */
    @Volatile var onTabReceived: ((fromName: String, title: String, url: String) -> Unit)? = null

    init {
        // Same endpoints Fenix uses for release builds. REDIRECT_URL lands on Mozilla's own
        // "oauth/success" page, which Petal's in-app sign-in sheet intercepts.
        val serverConfig = ServerConfig(FxaServer.Release, CLIENT_ID, REDIRECT_URL)

        val deviceConfig = DeviceConfig(
            name = deviceName(),
            type = DeviceType.MOBILE,
            capabilities = setOf(DeviceCapability.SEND_TAB),
            secureStateAtRest = true
        )

        val syncConfig = SyncConfig(
            supportedEngines = supportedEngines,
            periodicSyncConfig = PeriodicSyncConfig(periodMinutes = 240, initialDelayMinutes = 5)
        )

        // Engines -> stores. Must be configured BEFORE the manager starts (Fenix does the same).
        GlobalSyncableStoreProvider.configureStore(MozEngine.History to historyStorage)
        GlobalSyncableStoreProvider.configureStore(MozEngine.Bookmarks to bookmarksStorage)
        GlobalSyncableStoreProvider.configureStore(MozEngine.Tabs to tabsStorage)

        accountManager = FxaAccountManager(
            appContext,
            serverConfig,
            deviceConfig,
            syncConfig,
            setOf("https://identity.mozilla.com/apps/oldsync") // application scopes
        )

        accountManager.register(accountObserver)
        accountManager.registerForSyncEvents(syncObserver, ProcessLifecycleOwner.get(), false)
        accountManager.registerForAccountEvents(eventsObserver, ProcessLifecycleOwner.get(), false)
    }

    /** Restore a previously persisted session (equivalent of Fenix's `accountManager.start()`). */
    fun start() {
        scope.launch {
            try {
                accountManager.start()
                accountManager.authenticatedAccount()?.let { publishSignedIn(accountManager.accountProfile()) }
            } catch (t: Throwable) {
                Log.e(TAG, "FxaAccountManager.start failed", t)
            }
        }
    }

    // ── Sign in / out ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the Mozilla-hosted sign-in URL to load in the in-app browser, or null on failure.
     * [pairingUrl] (from the "Connect another device" QR on desktop Firefox) makes it a pairing flow.
     */
    suspend fun beginSignIn(pairingUrl: String? = null): String? {
        _state.value = FxaState.SigningIn
        return try {
            accountManager.beginAuthentication(pairingUrl, PetalFxaEntryPoint)
                ?: run { _state.value = FxaState.Error("Could not start sign-in. Check your connection."); null }
        } catch (t: Throwable) {
            Log.e(TAG, "beginAuthentication failed", t)
            _state.value = FxaState.Error(t.message ?: "Could not start sign-in")
            null
        }
    }

    /** Called with the final redirect URL (…/oauth/success/<id>?code=…&state=…&action=…). */
    suspend fun finishSignIn(code: String, state: String, action: String?): Boolean {
        return try {
            val type = when (action?.lowercase()) {
                "signup" -> AuthType.Signup
                "pairing" -> AuthType.Pairing
                else -> AuthType.Signin
            }
            accountManager.finishAuthentication(FxaAuthData(type, code, state))
            true
        } catch (t: Throwable) {
            Log.e(TAG, "finishAuthentication failed", t)
            _state.value = FxaState.Error(t.message ?: "Sign-in could not be completed")
            false
        }
    }

    fun cancelSignIn() {
        if (_state.value is FxaState.SigningIn || _state.value is FxaState.Error) {
            _state.value = if (accountManager.authenticatedAccount() != null)
                currentSignedIn() else FxaState.SignedOut
        }
    }

    fun signOut() {
        scope.launch {
            try { accountManager.logout() } catch (t: Throwable) { Log.e(TAG, "logout failed", t) }
            _state.value = FxaState.SignedOut
            _devices.value = emptyList()
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────────────────────

    fun isEngineEnabled(engine: SyncEngine): Boolean =
        appContext.getSharedPreferences(ENGINE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(engine.name, true)

    fun setEngineEnabled(engine: SyncEngine, enabled: Boolean) {
        appContext.getSharedPreferences(ENGINE_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(engine.name, enabled).apply()
    }

    fun syncNow(engines: Set<SyncEngine>) {
        if (accountManager.authenticatedAccount() == null) return
        val subset = engines.mapNotNull { it.toMoz() }
        if (subset.isEmpty()) return
        scope.launch {
            try {
                accountManager.syncNow(SyncReason.User, false, subset)
            } catch (t: Throwable) {
                _lastSyncError.value = t.message ?: "Sync failed"
                Log.e(TAG, "syncNow failed", t)
            }
        }
    }

    // ── Send Tab ──────────────────────────────────────────────────────────────────────────────

    fun refreshDevices() {
        val constellation = accountManager.authenticatedAccount()?.deviceConstellation() ?: return
        scope.launch {
            try {
                constellation.refreshDevices()
                publishDevices()
            } catch (t: Throwable) { Log.e(TAG, "refreshDevices failed", t) }
        }
    }

    /** [targetId] == "*" sends to every device that can receive tabs. */
    suspend fun sendTab(targetId: String, title: String, url: String): Boolean {
        val constellation = accountManager.authenticatedAccount()?.deviceConstellation() ?: return false
        return try {
            val targets = if (targetId == "*") {
                constellation.state()?.otherDevices
                    ?.filter { it.capabilities.contains(DeviceCapability.SEND_TAB) }
                    ?.map { it.id }.orEmpty()
            } else listOf(targetId)
            if (targets.isEmpty()) return false
            targets.map { constellation.sendCommandToDevice(it, DeviceCommandOutgoing.SendTab(title, url)) }
                .any { it }
        } catch (t: Throwable) {
            Log.e(TAG, "sendTab failed", t)
            false
        }
    }

    /** Fetch tabs other devices sent us. Called after sync and on app foreground. */
    fun pollIncomingTabs() {
        val constellation = accountManager.authenticatedAccount()?.deviceConstellation() ?: return
        scope.launch {
            try { constellation.pollForCommands() } catch (t: Throwable) { Log.e(TAG, "poll failed", t) }
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────────────────────

    private val accountObserver = object : AccountObserver {
        override fun onAuthenticated(account: OAuthAccount, authType: AuthType) {
            publishSignedIn(accountManager.accountProfile())
            refreshDevices()
        }

        override fun onProfileUpdated(profile: Profile) {
            publishSignedIn(profile)
        }

        override fun onLoggedOut() {
            _state.value = FxaState.SignedOut
        }

        override fun onAuthenticationProblems() {
            // Token revoked / password changed: Mozilla asks the user to sign in again.
            _state.value = FxaState.Error("Your Mozilla account needs you to sign in again.")
        }

        override fun onFlowError(error: AuthFlowError) {
            _state.value = FxaState.Error("Sign-in failed (${error.name.lowercase().replace('_', ' ')}).")
        }
    }

    private val syncObserver = object : SyncStatusObserver {
        override fun onStarted() {
            _lastSyncError.value = null
            _syncRunning.value = true
        }

        override fun onIdle() {
            _syncRunning.value = false
            onSyncFinished?.invoke(true)
            refreshDevices()
            pollIncomingTabs()
        }

        override fun onError(error: Exception?) {
            _syncRunning.value = false
            _lastSyncError.value = error?.message ?: "Sync failed"
            onSyncFinished?.invoke(false)
        }
    }

    private val eventsObserver = object : AccountEventsObserver {
        override fun onEvents(events: List<AccountEvent>) {
            events.filterIsInstance<AccountEvent.DeviceCommandIncoming>().forEach { ev ->
                val cmd = ev.command
                if (cmd is DeviceCommandIncoming.TabReceived) {
                    val from = cmd.from?.displayName ?: "another device"
                    cmd.entries.forEach { onTabReceived?.invoke(from, it.title, it.url) }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    private fun currentSignedIn(): FxaState {
        val p = accountManager.accountProfile()
        return FxaState.SignedIn(
            email = p?.email.orEmpty(),
            displayName = p?.displayName,
            avatarUrl = p?.avatar?.url,
            uid = p?.uid
        )
    }

    private fun publishSignedIn(profile: Profile?) {
        _state.value = FxaState.SignedIn(
            email = profile?.email.orEmpty(),
            displayName = profile?.displayName,
            avatarUrl = profile?.avatar?.url,
            uid = profile?.uid
        )
    }

    private fun publishDevices() {
        val list = accountManager.authenticatedAccount()?.deviceConstellation()?.state()?.otherDevices.orEmpty()
        _devices.value = list.map {
            FxaDevice(
                id = it.id,
                name = it.displayName,
                type = it.deviceType.name.lowercase(),
                canReceiveTabs = it.capabilities.contains(DeviceCapability.SEND_TAB)
            )
        }
    }

    private fun SyncEngine.toMoz(): MozEngine? = when (this) {
        SyncEngine.BOOKMARKS -> MozEngine.Bookmarks
        SyncEngine.HISTORY -> MozEngine.History
        SyncEngine.TABS -> MozEngine.Tabs
    }

    companion object {
        private const val TAG = "PetalFxa"
        private const val ENGINE_PREFS = "petal_fx_engine_prefs"

        /** Public OAuth client id used by Firefox for Android (same one Fenix ships). */
        const val CLIENT_ID = "a2270f727f45f648"
        const val REDIRECT_URL = "https://accounts.firefox.com/oauth/success/$CLIENT_ID"

        fun deviceName(): String = "Petal (${Build.MODEL ?: "Android"})"

        @Volatile private var instance: PetalFxaServices? = null

        fun get(context: Context): PetalFxaServices =
            instance ?: synchronized(this) {
                instance ?: PetalFxaServices(context.applicationContext).also { instance = it }
            }

        fun peek(): PetalFxaServices? = instance
    }
}
