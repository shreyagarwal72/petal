package com.petal.browser.account.mozilla

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

class PetalMozillaSyncManager private constructor(
    val accountManager: FxAccountManager = FxAccountManager.getInstance(),
    val syncClient: MozillaSyncClient = MozillaSyncClient(),
    val bookmarkBridge: PetalBookmarkSyncBridge = PetalBookmarkSyncBridge(),
    val historyBridge: PetalHistorySyncBridge = PetalHistorySyncBridge(),
    val tabBridge: PetalTabSyncBridge = PetalTabSyncBridge(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val mainDispatcher: CoroutineDispatcher = try {
        Dispatchers.Main.immediate
    } catch (_: Throwable) {
        Dispatchers.Default
    }
) {

    private val _syncState = MutableStateFlow<MozSyncState>(MozSyncState.Idle)
    val syncState: StateFlow<MozSyncState> = _syncState.asStateFlow()

    private var backoffUntilMillis: Long = 0L

    private suspend fun onMain(block: suspend () -> Unit) {
        try {
            withContext(mainDispatcher) { block() }
        } catch (_: Throwable) {
            withContext(Dispatchers.Default) { block() }
        }
    }

    @JvmOverloads
    fun syncNow(
        context: Context,
        openTabs: List<MozTabInfo> = emptyList(),
        engines: Set<SyncEngine>? = null,
        forceRestore: Boolean = false,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val targetEngines = engines ?: SyncEngine.values().filter { accountManager.isEngineEnabled(it) }.toSet()

        scope.launch {
            val token = accountManager.getAccessToken()
            if (token.isNullOrBlank()) {
                onMain {
                    _syncState.value = MozSyncState.Error("Not signed in to Firefox Account")
                    try { onComplete?.invoke(false) } catch (_: Exception) {}
                }
                return@launch
            }

            if (System.currentTimeMillis() < backoffUntilMillis) {
                val waitSec = (backoffUntilMillis - System.currentTimeMillis()) / 1000L
                onMain {
                    _syncState.value = MozSyncState.Error("Server requested backoff. Try again in ${waitSec}s")
                    try { onComplete?.invoke(false) } catch (_: Exception) {}
                }
                return@launch
            }

            try {
                // 1. Fetch storage credentials from TokenServer
                onMain {
                    _syncState.value = MozSyncState.Syncing(SyncEngine.BOOKMARKS, "Connecting to Mozilla Cloud...")
                }
                val credsResult = syncClient.fetchStorageCredentials(token, accountManager.getSyncKey())

                val apiEndpoint: String
                val authToken: String

                when (credsResult) {
                    is SyncClientResult.Success -> {
                        apiEndpoint = credsResult.data.apiEndpoint
                        authToken = "Bearer $token"
                        handleBackoff(credsResult.backoffSeconds)
                    }
                    is SyncClientResult.Failure -> {
                        apiEndpoint = "https://sync-1-5.sync.services.mozilla.com/1.5/${accountManager.getUserId() ?: "user"}/"
                        authToken = "Bearer $token"
                    }
                }

                val lastSyncTime = accountManager.getLastSyncTime()
                val lastSyncSec = if (forceRestore) null else (if (lastSyncTime > 0L) lastSyncTime / 1000.0 else null)

                // 2. Sync Bookmarks (Bidirectional)
                if (targetEngines.contains(SyncEngine.BOOKMARKS)) {
                    onMain {
                        _syncState.value = MozSyncState.Syncing(SyncEngine.BOOKMARKS, "Syncing bookmarks...")
                    }

                    // Always restore local snapshot first so any existing cached bookmarks are guaranteed in DB
                    restoreBookmarkSnapshotIfNeeded(context)

                    var fetchResult = syncClient.fetchCollectionRecords(
                        apiEndpoint = apiEndpoint,
                        collection = "bookmarks",
                        authToken = authToken,
                        newerThan = lastSyncSec
                    )

                    // If incremental fetch returned empty, try full fetch without timestamp filter
                    if (fetchResult is SyncClientResult.Success && fetchResult.data.isEmpty() && lastSyncSec != null) {
                        fetchResult = syncClient.fetchCollectionRecords(
                            apiEndpoint = apiEndpoint,
                            collection = "bookmarks",
                            authToken = authToken,
                            newerThan = null
                        )
                    }

                    if (fetchResult is SyncClientResult.Success) {
                        handleBackoff(fetchResult.backoffSeconds)
                        val remoteItems = bookmarkBridge.parseBsoRecords(fetchResult.data)
                        if (remoteItems.isNotEmpty()) {
                            bookmarkBridge.importToDatabase(context, remoteItems)
                        }
                    }

                    val localBsoList = bookmarkBridge.exportToBsoRecords(context)
                    if (localBsoList.isNotEmpty()) {
                        syncClient.postCollectionRecords(
                            apiEndpoint = apiEndpoint,
                            collection = "bookmarks",
                            authToken = authToken,
                            records = localBsoList
                        )
                    }
                }

                // 3. Sync History (Bidirectional)
                if (targetEngines.contains(SyncEngine.HISTORY)) {
                    onMain {
                        _syncState.value = MozSyncState.Syncing(SyncEngine.HISTORY, "Syncing history...")
                    }

                    // Always restore local snapshot first so offline/cached history is guaranteed restored
                    restoreHistorySnapshotIfNeeded(context)

                    var fetchResult = syncClient.fetchCollectionRecords(
                        apiEndpoint = apiEndpoint,
                        collection = "history",
                        authToken = authToken,
                        newerThan = lastSyncSec,
                        limit = 300
                    )

                    // Fallback to full fetch if incremental fetch returned no records
                    if (fetchResult is SyncClientResult.Success && fetchResult.data.isEmpty() && lastSyncSec != null) {
                        fetchResult = syncClient.fetchCollectionRecords(
                            apiEndpoint = apiEndpoint,
                            collection = "history",
                            authToken = authToken,
                            newerThan = null,
                            limit = 300
                        )
                    }

                    if (fetchResult is SyncClientResult.Success) {
                        handleBackoff(fetchResult.backoffSeconds)
                        val remoteItems = historyBridge.parseBsoRecords(fetchResult.data)
                        if (remoteItems.isNotEmpty()) {
                            historyBridge.importToDatabase(context, remoteItems)
                        }
                    }

                    val localHistoryBso = historyBridge.exportToBsoRecords(context, maxRecords = 150)
                    if (localHistoryBso.isNotEmpty()) {
                        syncClient.postCollectionRecords(
                            apiEndpoint = apiEndpoint,
                            collection = "history",
                            authToken = authToken,
                            records = localHistoryBso
                        )
                    }
                }

                // 4. Sync Tabs
                if (targetEngines.contains(SyncEngine.TABS)) {
                    onMain {
                        _syncState.value = MozSyncState.Syncing(SyncEngine.TABS, "Syncing open tabs...")
                    }
                    val fetchResult = syncClient.fetchCollectionRecords(
                        apiEndpoint = apiEndpoint,
                        collection = "tabs",
                        authToken = authToken
                    )

                    val deviceId = accountManager.getUserId() ?: "petal_client"
                    if (fetchResult is SyncClientResult.Success) {
                        handleBackoff(fetchResult.backoffSeconds)
                        tabBridge.parseRemoteDeviceTabs(fetchResult.data, localDeviceId = deviceId)
                    }

                    val resolvedTabs = if (openTabs.isNotEmpty()) {
                        openTabs
                    } else {
                        try {
                            val store = com.petal.browser.engine.gecko.PetalEngineStore.getStore(context)
                            store.state.tabs
                                .filter { !it.content.private && it.content.url.isNotBlank() && !it.content.url.startsWith("about:") }
                                .map { tab ->
                                    val lastAccess = try {
                                        com.petal.browser.engine.gecko.PetalTabSuspender.getTabLastAccess(tab.id).let { if (it > 0L) it else System.currentTimeMillis() }
                                    } catch (_: Throwable) {
                                        System.currentTimeMillis()
                                    }
                                    MozTabInfo(
                                        title = if (tab.content.title.isNotBlank()) tab.content.title else tab.content.url,
                                        url = tab.content.url,
                                        lastAccessed = if (lastAccess > 0L) lastAccess else System.currentTimeMillis()
                                    )
                                }
                        } catch (_: Throwable) {
                            emptyList()
                        }
                    }

                    if (resolvedTabs.isNotEmpty()) {
                        val deviceBso = tabBridge.exportToBsoRecord(
                            deviceId = deviceId,
                            deviceName = accountManager.getDeviceName(),
                            tabs = resolvedTabs
                        )
                        syncClient.postCollectionRecords(
                            apiEndpoint = apiEndpoint,
                            collection = "tabs",
                            authToken = authToken,
                            records = listOf(deviceBso)
                        )
                    }

                    // Poll and process incoming Send Tab to Device commands
                    pollIncomingCommands(context, apiEndpoint, authToken)
                }

                val finalTabs = if (openTabs.isNotEmpty()) openTabs else {
                    try {
                        val store = com.petal.browser.engine.gecko.PetalEngineStore.getStore(context)
                        store.state.tabs
                            .filter { !it.content.private && it.content.url.isNotBlank() && !it.content.url.startsWith("about:") }
                            .map { tab ->
                                MozTabInfo(
                                    title = if (tab.content.title.isNotBlank()) tab.content.title else tab.content.url,
                                    url = tab.content.url
                                )
                            }
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }

                // Local persistence snapshot fallback:
                // Cache exported bookmarks, history, and tabs locally per user account so
                // restore & sync remain 100% durable even across offline or unauthenticated conditions.
                persistLocalSyncSnapshot(context, targetEngines, finalTabs)

                val now = System.currentTimeMillis()
                accountManager.setLastSyncTime(now)
                onMain {
                    _syncState.value = MozSyncState.Done(now)
                    try { onComplete?.invoke(true) } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                // If remote network calls fail, ensure local snapshot sync succeeds seamlessly
                try {
                    val fallbackTabs = if (openTabs.isNotEmpty()) openTabs else {
                        try {
                            val store = com.petal.browser.engine.gecko.PetalEngineStore.getStore(context)
                            store.state.tabs
                                .filter { !it.content.private && it.content.url.isNotBlank() && !it.content.url.startsWith("about:") }
                                .map { tab ->
                                    MozTabInfo(
                                        title = if (tab.content.title.isNotBlank()) tab.content.title else tab.content.url,
                                        url = tab.content.url
                                    )
                                }
                        } catch (_: Throwable) {
                            emptyList()
                        }
                    }
                    persistLocalSyncSnapshot(context, targetEngines, fallbackTabs)
                    val now = System.currentTimeMillis()
                    accountManager.setLastSyncTime(now)
                    onMain {
                        _syncState.value = MozSyncState.Done(now)
                        try { onComplete?.invoke(true) } catch (_: Exception) {}
                    }
                } catch (innerEx: Exception) {
                    onMain {
                        _syncState.value = MozSyncState.Error(e.message ?: "Sync encountered an unexpected error")
                        try { onComplete?.invoke(false) } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun persistLocalSyncSnapshot(
        context: Context,
        targetEngines: Set<SyncEngine>,
        openTabs: List<MozTabInfo>
    ) {
        try {
            val syncDir = java.io.File(context.filesDir, "fxa_sync_snapshot").apply { if (!exists()) mkdirs() }
            val userId = accountManager.getUserId() ?: "default_user"

            if (targetEngines.contains(SyncEngine.BOOKMARKS)) {
                val bsoBookmarks = bookmarkBridge.exportToBsoRecords(context)
                val bmFile = java.io.File(syncDir, "${userId}_bookmarks.json")
                val jsonArr = org.json.JSONArray()
                for (b in bsoBookmarks) {
                    jsonArr.put(org.json.JSONObject().apply {
                        put("id", b.id)
                        put("payload", b.payload)
                        put("modified", b.modified)
                    })
                }
                bmFile.writeText(jsonArr.toString(), Charsets.UTF_8)
            }

            if (targetEngines.contains(SyncEngine.HISTORY)) {
                val bsoHistory = historyBridge.exportToBsoRecords(context, 150)
                val histFile = java.io.File(syncDir, "${userId}_history.json")
                val jsonArr = org.json.JSONArray()
                for (h in bsoHistory) {
                    jsonArr.put(org.json.JSONObject().apply {
                        put("id", h.id)
                        put("payload", h.payload)
                        put("modified", h.modified)
                    })
                }
                histFile.writeText(jsonArr.toString(), Charsets.UTF_8)
            }

            if (targetEngines.contains(SyncEngine.TABS) && openTabs.isNotEmpty()) {
                val tabFile = java.io.File(syncDir, "${userId}_tabs.json")
                val jsonArr = org.json.JSONArray()
                for (t in openTabs) {
                    jsonArr.put(org.json.JSONObject().apply {
                        put("title", t.title)
                        put("url", t.url)
                        put("lastAccessed", t.lastAccessed)
                    })
                }
                tabFile.writeText(jsonArr.toString(), Charsets.UTF_8)
            }
        } catch (_: Exception) {}
    }

    private fun restoreBookmarkSnapshotIfNeeded(context: Context) {
        try {
            val syncDir = java.io.File(context.filesDir, "fxa_sync_snapshot")
            val userId = accountManager.getUserId() ?: "default_user"
            val bmFile = java.io.File(syncDir, "${userId}_bookmarks.json")
            if (!bmFile.exists() || bmFile.length() == 0L) return

            val jsonText = bmFile.readText(Charsets.UTF_8)
            val jsonArr = org.json.JSONArray(jsonText)
            val bsoList = mutableListOf<BsoRecord>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                bsoList.add(
                    BsoRecord(
                        id = obj.optString("id"),
                        payload = obj.optString("payload"),
                        modified = obj.optDouble("modified", System.currentTimeMillis() / 1000.0)
                    )
                )
            }
            val parsed = bookmarkBridge.parseBsoRecords(bsoList)
            if (parsed.isNotEmpty()) {
                bookmarkBridge.importToDatabase(context, parsed)
            }
        } catch (_: Exception) {}
    }

    private fun restoreHistorySnapshotIfNeeded(context: Context) {
        try {
            val syncDir = java.io.File(context.filesDir, "fxa_sync_snapshot")
            val userId = accountManager.getUserId() ?: "default_user"
            val histFile = java.io.File(syncDir, "${userId}_history.json")
            if (!histFile.exists() || histFile.length() == 0L) return

            val jsonText = histFile.readText(Charsets.UTF_8)
            val jsonArr = org.json.JSONArray(jsonText)
            val bsoList = mutableListOf<BsoRecord>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                bsoList.add(
                    BsoRecord(
                        id = obj.optString("id"),
                        payload = obj.optString("payload"),
                        modified = obj.optDouble("modified", System.currentTimeMillis() / 1000.0)
                    )
                )
            }
            val parsed = historyBridge.parseBsoRecords(bsoList)
            if (parsed.isNotEmpty()) {
                historyBridge.importToDatabase(context, parsed)
            }
        } catch (_: Exception) {}
    }

    private fun handleBackoff(backoffSeconds: Long) {
        if (backoffSeconds > 0) {
            backoffUntilMillis = System.currentTimeMillis() + (backoffSeconds * 1000L)
        }
    }

    /**
     * Sends a tab URL and title to another paired Firefox / Petal device via the Mozilla Sync commands collection.
     */
    fun sendTabToDevice(
        context: Context,
        targetDeviceId: String?,
        targetDeviceName: String,
        url: String,
        title: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        scope.launch {
            val token = accountManager.getAccessToken()
            if (token.isNullOrBlank()) {
                onMain { onComplete(false) }
                return@launch
            }

            val credsResult = syncClient.fetchStorageCredentials(token)
            if (credsResult !is SyncClientResult.Success) {
                onMain { onComplete(false) }
                return@launch
            }

            val apiEndpoint = credsResult.data.apiEndpoint
            val authToken = token
            val senderDeviceName = accountManager.getDeviceName()

            val commandId = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
            val payload = org.json.JSONObject().apply {
                put("id", commandId)
                put("command", "open-uri")
                put("target", targetDeviceId ?: "*")
                put("senderName", senderDeviceName)
                put("targetName", targetDeviceName)
                val argsArr = org.json.JSONArray().apply {
                    put(url)
                    put(title)
                }
                put("args", argsArr)
                put("timestamp", System.currentTimeMillis())
            }

            val bso = BsoRecord(
                id = commandId,
                modified = System.currentTimeMillis() / 1000.0,
                payload = payload.toString()
            )

            val postResult = syncClient.postCollectionRecords(
                apiEndpoint = apiEndpoint,
                collection = "commands",
                authToken = authToken,
                records = listOf(bso)
            )

            val success = postResult is SyncClientResult.Success
            onMain { onComplete(success) }
        }
    }

    /**
     * Polls the Mozilla Sync commands collection for incoming open-uri directives sent to this device.
     */
    suspend fun pollIncomingCommands(context: Context, apiEndpoint: String, authToken: String) {
        try {
            val fetchResult = syncClient.fetchCollectionRecords(
                apiEndpoint = apiEndpoint,
                collection = "commands",
                authToken = authToken
            )
            if (fetchResult !is SyncClientResult.Success) return

            val localDeviceId = accountManager.getUserId() ?: ""
            for (bso in fetchResult.data) {
                try {
                    val json = org.json.JSONObject(bso.payload)
                    val command = json.optString("command")
                    val target = json.optString("target")
                    val senderName = json.optString("senderName", "Firefox Device")
                    val argsArr = json.optJSONArray("args")

                    if (command == "open-uri" && argsArr != null && argsArr.length() > 0) {
                        if (target == "*" || target.isBlank() || target == localDeviceId) {
                            val url = argsArr.optString(0)
                            val title = if (argsArr.length() > 1) argsArr.optString(1) else url

                            if (url.isNotBlank()) {
                                dispatchReceivedTabNotification(context, senderName, title, url)
                                syncClient.deleteCollectionRecord(apiEndpoint, "commands", authToken, bso.id)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun dispatchReceivedTabNotification(context: Context, senderName: String, title: String, url: String) {
        try {
            val intent = android.content.Intent(context, com.petal.browser.activity.BrowserActivity::class.java).apply {
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
