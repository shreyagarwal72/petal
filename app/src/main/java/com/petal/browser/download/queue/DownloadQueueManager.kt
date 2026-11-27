package com.petal.browser.download.queue

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.compose.downloads.DownloadStatus
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.download.DownloadPrefs
import com.petal.browser.download.PetalDownloadHeaderRegistry
import com.petal.browser.download.PetalDownloadRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Persistent download queue.
 *
 *  - Every in-app HTTP download goes through [submit]; it starts immediately when a slot is free and
 *    the network rules allow it, otherwise it waits (QUEUED) and survives process death (Room).
 *  - Private downloads are queued in memory only and never written to disk.
 *  - A [DownloadNetworkMonitor] callback pauses running downloads when the network is lost (or when
 *    Wi-Fi-only is on and the link is metered) and resumes them when it is back, with exponential
 *    backoff, so no manual Retry is needed.
 */
object DownloadQueueManager {
    private const val TAG = "DownloadQueue"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val privateEntries = CopyOnWriteArrayList<QueuedDownload>()
    private val privateIds = AtomicLong(-1L)
    private val callbacks = ConcurrentHashMap<Long, Pair<((Long) -> Unit)?, (() -> Unit)?>>()
    private val autoPaused: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val cancelledWhileStarting: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    @Volatile private var started = false
    @Volatile private var appContext: Context? = null
    @Volatile private var lastNetworkLossAt = 0L
    @Volatile private var flapCount = 0

    private val _queued = MutableStateFlow<List<QueuedDownload>>(emptyList())
    /** Entries that have not finished (queued, starting, running, paused or failed), for the UI. */
    val entries: StateFlow<List<QueuedDownload>> = _queued.asStateFlow()

    @JvmStatic
    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            appContext = context.applicationContext
        }
        val app = appContext ?: return
        DownloadNetworkMonitor.start(app)
        scope.launch {
            // Entries that were STARTING/RUNNING when the process died go back in line; the store
            // restore (and the resumer) decide whether the bytes are still resumable.
            mutex.withLock {
                val dao = DownloadQueueDatabase.get(app).dao()
                dao.all().filter { it.state == QueuedDownload.STATE_STARTING }
                    .forEach { dao.upsert(it.copy(state = QueuedDownload.STATE_QUEUED)) }
            }
            refreshEntries()
            // React to: bridge changes (finished / failed / freed slot) and network changes.
            var lastNet = DownloadNetworkMonitor.state.value
            combine(PetalFetchDownloadBridge.downloadItems, DownloadNetworkMonitor.state) { _, net -> net }
                .collect { net ->
                    if (net != lastNet) { onNetworkChanged(app, lastNet, net); lastNet = net }
                    pump(app)
                }
        }
    }

    // ── Public API ──────────────────────────────────────────────────────────

    @JvmStatic
    @JvmOverloads
    fun submit(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String?,
        referrerUrl: String?,
        isPrivate: Boolean,
        userAgent: String?,
        headers: Map<String, String>,
        priority: Int = QueuedDownload.PRIORITY_NORMAL,
        scheduledAt: Long = 0L,
        wifiOnly: Boolean = false,
        onEnqueued: ((Long) -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        val app = context.applicationContext
        start(app)
        val safeHeaders = if (isPrivate) headers else PetalDownloadHeaderRegistry.persistable(headers)
        val entry = QueuedDownload(
            id = if (isPrivate) privateIds.getAndDecrement() else 0L,
            url = url, fileName = fileName, mimeType = mimeType, referrerUrl = referrerUrl,
            userAgent = userAgent, headersJson = JSONObject(safeHeaders as Map<*, *>).toString(),
            priority = priority, scheduledAt = scheduledAt, wifiOnly = wifiOnly, isPrivate = isPrivate
        )
        scope.launch {
            val id = mutex.withLock {
                if (isPrivate) { privateEntries.add(entry); entry.id }
                else DownloadQueueDatabase.get(app).dao().upsert(entry)
            }
            if (onEnqueued != null || onFailed != null) callbacks[id] = onEnqueued to onFailed
            // Secrets (Cookie/Authorization) live only in the in-memory registry, keyed by URL.
            if (headers.isNotEmpty()) PetalDownloadHeaderRegistry.put(url, headers, isPrivate = isPrivate)
            refreshEntries()
            pump(app)
        }
    }

    @JvmStatic
    fun pauseAll(context: Context) {
        scope.launch {
            mutex.withLock {
                allEntries(context).filter { it.state == QueuedDownload.STATE_QUEUED }
                    .forEach { save(context, it.copy(state = QueuedDownload.STATE_PAUSED)) }
            }
            PetalFetchDownloadBridge.pauseAllRunning(context)
            refreshEntries()
        }
    }

    @JvmStatic
    fun resumeAll(context: Context) {
        scope.launch {
            mutex.withLock {
                allEntries(context).filter { it.state == QueuedDownload.STATE_PAUSED }
                    .forEach { save(context, it.copy(state = QueuedDownload.STATE_QUEUED)) }
            }
            PetalFetchDownloadBridge.resumeAllPaused(context)
            refreshEntries()
            pump(context.applicationContext)
        }
    }

    @JvmStatic
    fun setPriority(context: Context, id: Long, priority: Int) {
        scope.launch {
            mutex.withLock { allEntries(context).firstOrNull { it.id == id }?.let { save(context, it.copy(priority = priority)) } }
            refreshEntries()
            pump(context.applicationContext)
        }
    }

    @JvmStatic
    fun remove(context: Context, id: Long) {
        val app = context.applicationContext
        cancelledWhileStarting.add(id)
        scope.launch {
            val sid = mutex.withLock {
                val target = allEntries(app).firstOrNull { it.id == id }
                delete(app, id)
                target?.storeId
            }
            if (!sid.isNullOrBlank()) {
                PetalFetchDownloadBridge.cancelAndRemoveByUuid(app, sid)
            }
            refreshEntries()
        }
    }

    /** Clears auto-paused tracking when the user manually pauses a download. */
    @JvmStatic
    fun clearAutoPaused(uuid: String) {
        if (uuid.isNotBlank()) autoPaused.remove(uuid)
    }

    /** Called when the user changes the concurrency / Wi-Fi settings so waiting items can start. */
    @JvmStatic
    fun settingsChanged(context: Context) {
        scope.launch { pump(context.applicationContext) }
    }

    // ── Core loop ───────────────────────────────────────────────────────────

    private suspend fun pump(app: Context) {
        val toStart = mutex.withLock {
            reconcile(app)
            val sp = PreferenceManager.getDefaultSharedPreferences(app)
            val limits = QueueScheduler.Limits(
                maxConcurrent = runCatching { sp.getInt(DownloadPrefs.KEY_MAX_CONCURRENT, DownloadPrefs.DEFAULT_MAX_CONCURRENT) }
                    .getOrDefault(DownloadPrefs.DEFAULT_MAX_CONCURRENT).coerceIn(1, DownloadPrefs.MAX_CONCURRENT_LIMIT),
                wifiOnlyGlobal = sp.getBoolean(DownloadPrefs.KEY_WIFI_ONLY, false)
            )
            val items = PetalFetchDownloadBridge.currentItemsSnapshot(app)
            val running = items.filter { !it.queued && (it.status == DownloadStatus.STATUS_RUNNING || it.status == DownloadStatus.STATUS_PENDING) }
            val entries = allEntries(app)
            val startingCount = entries.count { it.state == QueuedDownload.STATE_STARTING }
            val picked = QueueScheduler.pickToStart(
                entries, running.size + startingCount, limits, DownloadNetworkMonitor.state.value,
                System.currentTimeMillis(), running.map { it.fileUrl }.toSet()
            )
            picked.map {
                cancelledWhileStarting.remove(it.id)
                save(app, it.copy(state = QueuedDownload.STATE_STARTING))
            }
        }
        if (toStart.isEmpty()) { refreshEntries(); return }
        for (e in toStart) startNow(app, e)
        refreshEntries()
    }

    /** Syncs queue rows with what the download engine reports; drops finished rows, schedules retries. */
    private suspend fun reconcile(app: Context) {
        val items = PetalFetchDownloadBridge.currentItemsSnapshot(app).associateBy { it.downloadId }
        for (e in allEntries(app)) {
            if (e.state != QueuedDownload.STATE_RUNNING && e.state != QueuedDownload.STATE_STARTING) continue
            val sid = e.storeId ?: continue
            val item = items[sid]
            when {
                item == null -> delete(app, e.id) // removed by the user
                item.status == DownloadStatus.STATUS_SUCCESSFUL -> delete(app, e.id)
                item.status == DownloadStatus.STATUS_FAILED -> {
                    if (item.failureReason == "Cancelled") {
                        delete(app, e.id)
                        continue
                    }
                    val attempts = e.attempts + 1
                    if (QueueScheduler.canRetry(attempts) && DownloadNetworkMonitor.state.value.online) {
                        save(app, e.copy(
                            state = QueuedDownload.STATE_QUEUED, attempts = attempts,
                            nextAttemptAt = System.currentTimeMillis() + QueueScheduler.backoffMs(attempts),
                            lastError = item.failureReason
                        ))
                        scheduleWake(app, QueueScheduler.backoffMs(attempts))
                    } else {
                        save(app, e.copy(state = QueuedDownload.STATE_FAILED, attempts = attempts, lastError = item.failureReason))
                    }
                }
            }
        }
    }

    private fun scheduleWake(app: Context, afterMs: Long) {
        scope.launch { delay(afterMs + 50); pump(app) }
    }

    private fun startNow(app: Context, e: QueuedDownload) {
        if (cancelledWhileStarting.remove(e.id)) {
            scope.launch {
                mutex.withLock { delete(app, e.id) }
                refreshEntries()
            }
            return
        }
        if (e.storeId != null) {
            // A retry of a download that already exists in the store: resume it, never duplicate it.
            PetalFetchDownloadBridge.resumeByUuid(app, e.storeId)
            scope.launch { mutex.withLock { save(app, e.copy(state = QueuedDownload.STATE_RUNNING)) }; refreshEntries() }
            return
        }
        val headers = runCatching {
            val o = JSONObject(e.headersJson)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        PetalDownloadRouter.downloadImmediate(
            context = app, url = e.url, fileName = e.fileName, mimeType = e.mimeType,
            referrerUrl = e.referrerUrl, isPrivate = e.isPrivate, userAgent = e.userAgent, cookie = null,
            headers = headers,
            onEnqueued = { stableId ->
                val sid = PetalFetchDownloadBridge.storeIdForStableId(stableId)
                if (cancelledWhileStarting.remove(e.id)) {
                    if (!sid.isNullOrBlank()) PetalFetchDownloadBridge.cancelAndRemoveByUuid(app, sid)
                    scope.launch {
                        mutex.withLock { delete(app, e.id) }
                        refreshEntries()
                    }
                } else {
                    scope.launch {
                        mutex.withLock { save(app, e.copy(state = QueuedDownload.STATE_RUNNING, storeId = sid)) }
                        refreshEntries()
                    }
                    callbacks.remove(e.id)?.first?.invoke(stableId)
                }
            },
            onFailed = {
                if (cancelledWhileStarting.remove(e.id)) {
                    scope.launch {
                        mutex.withLock { delete(app, e.id) }
                        refreshEntries()
                    }
                } else {
                    scope.launch {
                        mutex.withLock {
                            val attempts = e.attempts + 1
                            if (QueueScheduler.canRetry(attempts) && DownloadNetworkMonitor.state.value.online) {
                                save(app, e.copy(state = QueuedDownload.STATE_QUEUED, attempts = attempts,
                                    nextAttemptAt = System.currentTimeMillis() + QueueScheduler.backoffMs(attempts)))
                                scheduleWake(app, QueueScheduler.backoffMs(attempts))
                            } else {
                                save(app, e.copy(state = QueuedDownload.STATE_FAILED, attempts = attempts))
                                callbacks.remove(e.id)?.second?.invoke()
                            }
                        }
                        refreshEntries()
                    }
                }
            }
        )
    }

    // ── Network rules ───────────────────────────────────────────────────────

    private fun onNetworkChanged(app: Context, old: QueueScheduler.Network, new: QueueScheduler.Network) {
        val wifiOnly = PreferenceManager.getDefaultSharedPreferences(app).getBoolean(DownloadPrefs.KEY_WIFI_ONLY, false)
        val blocked = !new.online || (wifiOnly && new.metered)
        val wasBlocked = !old.online || (wifiOnly && old.metered)
        if (blocked && !wasBlocked) {
            // Pause what is moving so it neither fails noisily nor burns metered data.
            val ids = PetalFetchDownloadBridge.runningMozillaIds()
            autoPaused.addAll(ids)
            ids.forEach { PetalFetchDownloadBridge.pauseByUuid(app, it) }
            val now = System.currentTimeMillis()
            flapCount = if (now - lastNetworkLossAt < 30_000L) flapCount + 1 else 1
            lastNetworkLossAt = now
        } else if (!blocked && wasBlocked) {
            val wait = QueueScheduler.backoffMs(flapCount.coerceAtLeast(1)).coerceAtMost(8_000L)
            scope.launch {
                delay(wait)
                val ids = autoPaused.toList()
                autoPaused.clear()
                ids.forEach { PetalFetchDownloadBridge.resumeByUuid(app, it) }
                // Downloads that already flipped to FAILED while offline are retried too.
                PetalFetchDownloadBridge.failedNetworkMozillaIds().forEach { PetalFetchDownloadBridge.resumeByUuid(app, it) }
                pump(app)
            }
        }
    }

    // ── Storage helpers ─────────────────────────────────────────────────────

    private suspend fun allEntries(app: Context): List<QueuedDownload> =
        DownloadQueueDatabase.get(app).dao().all() + privateEntries

    private suspend fun save(app: Context, e: QueuedDownload): QueuedDownload {
        if (e.isPrivate) {
            val i = privateEntries.indexOfFirst { it.id == e.id }
            if (i >= 0) privateEntries[i] = e else privateEntries.add(e)
            return e
        }
        DownloadQueueDatabase.get(app).dao().upsert(e)
        return e
    }

    private suspend fun delete(app: Context, id: Long) {
        if (id < 0) privateEntries.removeAll { it.id == id } else DownloadQueueDatabase.get(app).dao().delete(id)
        callbacks.remove(id)
    }

    private suspend fun refreshEntries() {
        val app = appContext ?: return
        try {
            val list = allEntries(app)
            if (list != _queued.value) { // only touch the UI state when something actually changed
                _queued.value = list
                PetalFetchDownloadBridge.refresh(app)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "refresh failed: ${t.message}")
        }
    }
}
