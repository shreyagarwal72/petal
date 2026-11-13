/*
 * PetalMozillaSyncManager.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Orchestrates Firefox Sync for Petal on top of Mozilla's official FxaAccountManager
 * ([PetalFxaServices]) and [PetalPlacesSyncBridge]:
 *
 *   syncNow():  push Petal -> Places  ->  Mozilla sync  ->  pull Places -> Petal
 *
 * Sign-in, encryption keys, tokens and the Sync protocol are handled by Mozilla's code.
 */
package com.petal.browser.account.mozilla

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class MozSyncState {
    object Idle : MozSyncState()
    data class Syncing(val engine: SyncEngine, val message: String = "Syncing ${engine.name.lowercase()}...") : MozSyncState()
    data class Done(val lastSyncTime: Long) : MozSyncState()
    data class Error(val message: String) : MozSyncState()
}

class PetalMozillaSyncManager private constructor() {

    val accountManager: FxAccountManager = FxAccountManager.getInstance()
    val tabBridge: PetalTabSyncBridge = PetalTabSyncBridge()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _syncState = MutableStateFlow<MozSyncState>(MozSyncState.Idle)
    val syncState: StateFlow<MozSyncState> = _syncState.asStateFlow()

    @Volatile private var pendingCallbacks = mutableListOf<(Boolean) -> Unit>()
    @Volatile private var wired = false
    @Volatile private var appContext: Context? = null

    /** Hook Mozilla's sync events into Petal. Safe to call repeatedly. */
    @Synchronized
    fun attach(context: Context) {
        if (wired) return
        val ctx = context.applicationContext
        val svc = accountManager.services(ctx)
        appContext = ctx
        wired = true

        svc.onSyncFinished = { ok -> scope.launch { afterSync(ctx, svc, ok) } }
        svc.onTabReceived = { from, title, url -> dispatchReceivedTabNotification(ctx, from, title, url) }
        scope.launch {
            svc.syncRunning.collect { running ->
                if (running) _syncState.value = MozSyncState.Syncing(SyncEngine.BOOKMARKS, "Syncing with Mozilla...")
            }
        }
    }

    private suspend fun afterSync(ctx: Context, svc: PetalFxaServices, ok: Boolean) {
        if (!ok) {
            val msg = svc.lastSyncError.value ?: "Sync failed"
            _syncState.value = MozSyncState.Error(msg)
            finishCallbacks(false)
            return
        }
        val bridge = PetalPlacesSyncBridge(ctx, svc)
        if (accountManager.isEngineEnabled(SyncEngine.BOOKMARKS)) bridge.pullBookmarks()
        if (accountManager.isEngineEnabled(SyncEngine.HISTORY)) bridge.pullHistory()
        if (accountManager.isEngineEnabled(SyncEngine.TABS)) {
            tabBridge.publish(bridge.pullRemoteTabs(svc.devices.value.associateBy { it.id }))
        }
        val now = System.currentTimeMillis()
        accountManager.setLastSyncTime(now)
        _syncState.value = MozSyncState.Done(now)
        finishCallbacks(true)
    }

    @Synchronized
    private fun finishCallbacks(ok: Boolean) {
        val cbs = pendingCallbacks
        pendingCallbacks = mutableListOf()
        cbs.forEach { try { it(ok) } catch (_: Throwable) {} }
    }

    /**
     * @param forceRestore kept for API compatibility; a sync always downloads remote data too,
     *        so "Restore" and "Sync now" run the same (non-destructive, merge-by-URL) pipeline.
     */
    @JvmOverloads
    fun syncNow(
        context: Context,
        openTabs: List<MozTabInfo> = emptyList(),
        engines: Set<SyncEngine>? = null,
        forceRestore: Boolean = false,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val ctx = context.applicationContext
        attach(ctx)
        val svc = accountManager.services(ctx)
        if (accountManager.accountState.value !is FxaState.SignedIn) {
            onComplete?.invoke(false)
            return
        }
        val target = engines ?: SyncEngine.values().filter { accountManager.isEngineEnabled(it) }.toSet()
        if (target.isEmpty()) { onComplete?.invoke(true); return }
        if (onComplete != null) synchronized(this) { pendingCallbacks.add(onComplete) }

        scope.launch {
            _syncState.value = MozSyncState.Syncing(target.first(), "Preparing your data...")
            val bridge = PetalPlacesSyncBridge(ctx, svc)
            if (SyncEngine.BOOKMARKS in target) bridge.pushBookmarks()
            if (SyncEngine.TABS in target) {
                val tabs = if (openTabs.isNotEmpty()) openTabs else currentOpenTabs(ctx)
                bridge.pushTabs(tabs)
            }
            svc.syncNow(target)
        }
    }

    private suspend fun currentOpenTabs(context: Context): List<MozTabInfo> = withContext(Dispatchers.Main) {
        try {
            val store = com.petal.browser.engine.gecko.PetalEngineStore.getStore(context)
            store.state.tabs
                .filter { !it.content.private && it.content.url.isNotBlank() && !it.content.url.startsWith("about:") }
                .map { tab ->
                    MozTabInfo(
                        title = tab.content.title.ifBlank { tab.content.url },
                        url = tab.content.url
                    )
                }
        } catch (_: Throwable) { emptyList() }
    }

    /** Mirror each page visit into Places so it uploads on the next sync. */
    fun onPageVisited(context: Context, url: String, title: String) {
        if (accountManager.accountState.value !is FxaState.SignedIn) return
        if (!accountManager.isEngineEnabled(SyncEngine.HISTORY)) return
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("moz-extension://")) return
        val ctx = context.applicationContext
        val svc = accountManager.services(ctx)
        scope.launch { PetalPlacesSyncBridge(ctx, svc).mirrorVisit(url, title) }
    }

    /**
     * Send a tab through Mozilla's Send Tab (device commands). [targetDeviceId] == "*" sends to all.
     */
    fun sendTabToDevice(
        context: Context,
        targetDeviceId: String?,
        targetDeviceName: String,
        url: String,
        title: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val svc = accountManager.services(context.applicationContext)
        scope.launch {
            val ok = svc.sendTab(targetDeviceId ?: "*", title, url)
            withContext(Dispatchers.Main) { onComplete(ok) }
        }
    }

    /** Devices that can receive a tab (refreshes in background). */
    fun devices(context: Context): StateFlow<List<FxaDevice>> {
        val svc = accountManager.services(context.applicationContext)
        svc.refreshDevices()
        return svc.devices
    }

    private fun dispatchReceivedTabNotification(context: Context, senderName: String, title: String, url: String) {
        try {
            val intent = android.content.Intent(context, com.petal.browser.activity.MainActivity::class.java).apply {
                action = android.content.Intent.ACTION_VIEW
                data = android.net.Uri.parse(url)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                url.hashCode(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                ?: return

            val channelId = "petal_synced_tabs"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Synced Tabs",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Tabs sent from your connected Firefox and Petal devices"
                    enableVibration(true)
                }
                notifManager.createNotificationChannel(channel)
            }

            val notif = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(com.petal.browser.R.drawable.ic_launcher_foreground)
                .setContentTitle("Tab received from $senderName")
                .setContentText(title.ifBlank { url })
                .setSubText("Firefox Sync")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .addAction(
                    android.R.drawable.ic_menu_view,
                    "Open Tab",
                    pendingIntent
                )
                .build()

            notifManager.notify(url.hashCode(), notif)
        } catch (_: Exception) {}
    }

    companion object {
        @Volatile
        private var instance: PetalMozillaSyncManager? = null

        @JvmStatic
        fun getInstance(): PetalMozillaSyncManager {
            return instance ?: synchronized(this) {
                instance ?: PetalMozillaSyncManager().also { instance = it }
            }
        }
    }
}
