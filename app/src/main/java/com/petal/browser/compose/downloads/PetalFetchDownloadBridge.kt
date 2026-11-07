/*
 * PetalFetchDownloadBridge.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Single entry point and state bridge for Petal downloads.
 *
 * Browser and media downloads run on Mozilla's official download pipeline
 * (Android Components feature-downloads / AbstractFetchDownloadService over
 * Gecko's network stack, as in Firefox/Fenix). Their state lives in the
 * BrowserStore and is mirrored here into DownloadItem rows (with sampled
 * speed/ETA) for the existing Compose UI. Mozilla's service owns their
 * notifications; PetalLiveAlertManager skips them via isMozillaOwned().
 *
 * Fetch2 remains only for legacy/persisted rows and the self-updater.
 */

package com.petal.browser.compose.downloads

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationManagerCompat
import com.petal.browser.download.SafeDownloadValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.feature.downloads.manager.FetchDownloadManager
import mozilla.components.support.base.android.NotificationsDelegate
import kotlin.reflect.KClass

object PetalFetchDownloadBridge {

    // Unified single download repository keyed by authoritative downloadId
    private val downloadRepository = LinkedHashMap<String, DownloadItem>()
    private val createdAtMap = LinkedHashMap<Int, Long>()
    private val speedMap = LinkedHashMap<Int, Long>()
    private val etaMap = LinkedHashMap<Int, Long>()
    // Throughput for Mozilla downloads (the store exposes bytes, not speed/ETA). One sliding-window
    // meter per download gives a steady readout that also decays when the transfer stalls.
    private val mozillaMeters = HashMap<String, com.petal.browser.download.PetalSpeedMeter>()
    private val tickerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null
    @Volatile
    private var applicationContext: Context? = null

    private val _downloadItems = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloadItems: StateFlow<List<DownloadItem>> = _downloadItems.asStateFlow()

    @Volatile
    private var initialized = false

    @JvmStatic
    fun stableId(downloadId: String): Long {
        if (downloadId.isBlank()) return 0L
        return try {
            val uuid = java.util.UUID.fromString(downloadId)
            uuid.mostSignificantBits and Long.MAX_VALUE
        } catch (_: Throwable) {
            downloadId.hashCode().toLong() and 0x7FFFFFFF_FFFFFFFFL
        }
    }

    @JvmStatic
    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            applicationContext = appContext

            try {
                // BrowserStore emits on every tab/title/progress change; only react to downloads.
                var lastDownloads: Any? = null
                com.petal.browser.engine.gecko.PetalEngineStore.getStore(appContext).observeManually { state ->
                    val downloads = state.downloads
                    if (downloads !== lastDownloads) {
                        lastDownloads = downloads
                        publish()
                    }
                }.resume()
                publish()
            } catch (_: Throwable) {
            }

            try {
                PetalLiveAlertManager.startGlobalDownloadObserver(appContext)
            } catch (_: Throwable) {
            }

            startTicker()
            initialized = true
        }
    }

    /**
     * Kotlin-owned media download entry point. Media grabber callers must use this
     * instead of the legacy Java download engine API so every media download is
     * represented by the same Fetch2-backed Petal Download Manager state/UI.
     */
    /**
     * Enqueues a download originating from GeckoEngine/GeckoView through Mozilla's official
     * download pipeline (Android Components feature-downloads, as in Firefox/Fenix).
     *
     * Bytes are fetched by [com.petal.browser.download.PetalMozillaDownloadService] over Gecko's
     * own network stack, so Gecko's cookies, private mode and proxy settings apply. The service
     * owns the foreground/progress/completion notifications for these downloads.
     */
    @JvmStatic
    @JvmOverloads
    fun enqueueGeckoDownload(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String? = null,
        responseHeaders: Map<String, String> = emptyMap(),
        onEnqueued: ((Long) -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
        referrerUrl: String? = null,
        isPrivate: Boolean = false
    ) {
        ensureInitialized(context)
        // ResponseHeaders are the server's reply headers; they are only useful for naming (already
        // resolved by the caller) and must never be copied onto the outgoing request.
        // GeckoViewFetchClient automatically applies Gecko's internal cookie jar, so we do not
        // manually inject cookies here.
        val outgoingHeaders = mutableMapOf<String, String>()
        val started = enqueueMozillaDownload(
            context = context,
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            referrerUrl = referrerUrl,
            isPrivate = isPrivate,
            extraHeaders = outgoingHeaders,
            onEnqueued = onEnqueued
        )
        if (!started) onFailed?.invoke()
    }

    /** True when [id] belongs to a download owned by Mozilla's service (and its notifications). */
    @JvmStatic
    fun isMozillaOwned(id: Long): Boolean = synchronized(downloadRepository) {
        downloadRepository.values.any { it.id == id && it.downloadId.isNotBlank() && !it.downloadId.startsWith("fetch_") }
    }

    private fun enqueueMozillaDownload(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String?,
        referrerUrl: String?,
        isPrivate: Boolean,
        extraHeaders: Map<String, String>,
        onEnqueued: ((Long) -> Unit)?
    ): Boolean {
        if (!SafeDownloadValues.isHttpUrl(url)) return false
        return try {
            val appContext = context.applicationContext
            val safeName = SafeDownloadValues.fileName(
                url = url,
                contentDisposition = null,
                mimeType = mimeType,
                preferredFileName = fileName
            ).ifBlank { "download" }
            val downloadsDir = com.petal.browser.download.PetalSmartDownloadRouter
                .resolveDestinationDirectory(appContext, safeName, mimeType)
            // Pre-flight storage check: ensure device has sufficient free space
            val freeSpace = downloadsDir.usableSpace
            if (freeSpace < 20L * 1024 * 1024) { // Minimum safety threshold: 20MB
                android.util.Log.e("PetalDownloadBridge", "Insufficient storage space ($freeSpace bytes free) to start download")
                return false
            }
            com.petal.browser.download.PetalDownloadHeaderRegistry.put(
                url = url,
                headers = extraHeaders,
                isPrivate = isPrivate
            )
            val manager = FetchDownloadManager(
                applicationContext = appContext,
                store = com.petal.browser.engine.gecko.PetalEngineStore.getStore(appContext),
                service = com.petal.browser.download.PetalMozillaDownloadService::class,
                notificationsDelegate = NotificationsDelegate(NotificationManagerCompat.from(appContext))
            )
            val id = manager.download(
                DownloadState(
                    url = url,
                    fileName = safeName,
                    contentType = mimeType,
                    directoryPath = downloadsDir.absolutePath,
                    referrerUrl = referrerUrl,
                    private = isPrivate
                )
            )
            if (id.isNullOrBlank()) return false
            com.petal.browser.download.PetalDownloadHeaderRegistry.put(
                url = url,
                headers = extraHeaders,
                downloadId = id,
                isPrivate = isPrivate
            )
            onEnqueued?.invoke(stableId(id))
            true
        } catch (t: Throwable) {
            android.util.Log.e("PetalDownloadBridge", "Mozilla download start failed: ${t.message}", t)
            false
        }
    }

    @JvmStatic
    @JvmOverloads
    fun enqueueUnifiedDownload(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String? = null,
        referrerUrl: String? = null,
        isPrivate: Boolean = false,
        userAgent: String? = null,
        cookie: String? = null,
        headers: Map<String, String> = emptyMap(),
        onEnqueued: ((Long) -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        ensureInitialized(context)
        if (!SafeDownloadValues.isHttpUrl(url)) {
            onFailed?.invoke()
            return
        }
        val extra = LinkedHashMap<String, String>()
        SafeDownloadValues.header(userAgent, 4096)?.let { extra["User-Agent"] = it }
        val isS3OrCdn = url.contains(".s3.amazonaws.com", ignoreCase = true) ||
            url.contains(".githubusercontent.com", ignoreCase = true) ||
            url.contains("github-production-release-asset", ignoreCase = true)
        if (!isS3OrCdn) {
            SafeDownloadValues.header(cookie, 16384)?.let { extra["Cookie"] = it }
        }
        var referrer = referrerUrl
        headers.forEach { (name, value) ->
            if (name.isBlank() || value.isBlank()) return@forEach
            if (name.equals("Referer", true)) { referrer = value }
            if (name.equals("Host", true) || name.equals("Content-Length", true) ||
                name.equals("Content-Encoding", true) || name.equals("Transfer-Encoding", true) ||
                name.equals("Connection", true) || name.equals("Accept-Encoding", true) ||
                name.equals("User-Agent", true) || name.equals("Cookie", true)) return@forEach
            if (isS3OrCdn && (name.startsWith("x-amz", true) || name.equals("Authorization", true))) return@forEach
            if ('\r' in name || '\n' in name || '\r' in value || '\n' in value) return@forEach
            val safeKey = SafeDownloadValues.header(name, 1024) ?: return@forEach
            val safeValue = SafeDownloadValues.header(value, 4096) ?: return@forEach
            extra[safeKey] = safeValue
        }
        val started = enqueueMozillaDownload(
            context = context,
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            referrerUrl = referrer,
            isPrivate = isPrivate,
            extraHeaders = extra,
            onEnqueued = onEnqueued
        )
        if (!started) onFailed?.invoke()
    }

    @JvmStatic
    fun enqueueMediaDownload(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String? = null,
        userAgent: String? = null,
        cookie: String? = null,
        headers: Map<String, String> = emptyMap(),
        onEnqueued: ((Long) -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        enqueueUnifiedDownload(
            context = context,
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            referrerUrl = headers["Referer"],
            isPrivate = false,
            userAgent = userAgent,
            cookie = cookie,
            headers = headers,
            onEnqueued = onEnqueued,
            onFailed = onFailed
        )
    }

    @JvmStatic
    fun refresh(context: Context) {
        ensureInitialized(context)
        publish()
    }

    @JvmStatic
    fun pause(context: Context, id: Long) {
        ensureInitialized(context)
        sendMozillaDownloadAction(context, id, mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_PAUSE)
    }

    @JvmStatic
    fun togglePauseResume(context: Context, id: Long) {
        val item = _downloadItems.value.firstOrNull { it.id == id } ?: return
        if (item.status == DownloadStatus.STATUS_PAUSED) {
            resume(context, id)
        } else {
            pause(context, id)
        }
    }

    @JvmStatic
    fun resume(context: Context, id: Long) {
        ensureInitialized(context)
        sendMozillaDownloadAction(context, id, mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_RESUME)
    }

    @JvmStatic
    fun retry(context: Context, id: Long) {
        ensureInitialized(context)
        sendMozillaDownloadAction(context, id, mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_TRY_AGAIN)
        val item = _downloadItems.value.firstOrNull { it.id == id }
        val fileName = item?.fileName ?: "File"
        PetalLiveAlertManager.trackDownload(context, id, fileName)
    }

    @JvmStatic
    fun cancel(context: Context, id: Long) {
        ensureInitialized(context)
        sendMozillaDownloadAction(context, id, mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_CANCEL)
        synchronized(downloadRepository) {
            val key = downloadRepository.entries.firstOrNull { it.value.id == id }?.key
            if (key != null) downloadRepository.remove(key)
        }
        publish()
    }

    private fun sendMozillaDownloadAction(context: Context, id: Long, action: String): Boolean {
        val uuid = synchronized(downloadRepository) {
            val item = downloadRepository.values.firstOrNull { it.id == id || it.downloadId == id.toString() }
            item?.downloadId?.takeIf { it.isNotBlank() && !it.startsWith("fetch_") }
        } ?: return false
        return sendMozillaDownloadAction(context, uuid, action)
    }

    @JvmStatic
    fun sendMozillaDownloadAction(context: Context, downloadId: String, action: String): Boolean {
        if (downloadId.isBlank() || downloadId.startsWith("fetch_")) return false
        return try {
            val intent = Intent(action).apply {
                setPackage(context.applicationContext.packageName)
                putExtra(mozilla.components.feature.downloads.INTENT_EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.applicationContext.sendBroadcast(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Cancels (if active) and permanently deletes the download + its partial/complete file. */
    @JvmStatic
    fun deleteDownload(context: Context, item: DownloadItem, deleteFile: Boolean = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean("sp_delete_download_file", false)) {
        ensureInitialized(context)
        val appContext = context.applicationContext
        // 1. Immediately terminate active notification tracking and live alert service
        try {
            PetalLiveAlertManager.stopTracking(appContext, item.id)
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            nm?.cancel(item.id.toInt())
        } catch (_: Throwable) {}

        // 2. Check and remove from Mozilla Download Manager / BrowserStore
        val mozillaId = item.downloadId.takeIf { it.isNotBlank() }
            ?: synchronized(downloadRepository) {
                downloadRepository.values.firstOrNull { it.id == item.id }?.downloadId
            }
        if (mozillaId != null) {
            try {
                sendMozillaDownloadAction(appContext, mozillaId, mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_CANCEL)
            } catch (_: Throwable) {}
            try {
                com.petal.browser.engine.gecko.PetalEngineStore.getStore(appContext).dispatch(
                    mozilla.components.browser.state.action.DownloadAction.RemoveDownloadAction(mozillaId)
                )
            } catch (_: Throwable) {}
            synchronized(downloadRepository) { downloadRepository.remove(mozillaId) }
        }

        // 3. Delete local file and any temporary/partial download file
        try {
            val path = item.localUri?.removePrefix("file://")
            if (!path.isNullOrEmpty()) {
                val file = File(path)
                if (file.exists() && (deleteFile || item.status == DownloadStatus.STATUS_RUNNING || item.status == DownloadStatus.STATUS_PENDING)) {
                    file.delete()
                }
                val partFile = File(path + ".part")
                if (partFile.exists()) partFile.delete()
                val tempFile = File(path + ".download")
                if (tempFile.exists()) tempFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        publish()
    }

    @JvmStatic
    fun deleteDownloads(
        context: Context,
        items: List<DownloadItem>,
        deleteFile: Boolean = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean("sp_delete_download_file", false)
    ) {
        items.forEach { deleteDownload(context, it, deleteFile) }
    }

    private val seenActiveIds = java.util.Collections.synchronizedSet(HashSet<Long>())
    private val announcedDoneIds = java.util.Collections.synchronizedSet(HashSet<Long>())

    private fun publish() {
        refreshMozillaDownloads()
        val items = synchronized(downloadRepository) { downloadRepository.values.toList() }
            .sortedWith(
                compareByDescending<DownloadItem> { item ->
                    item.status == DownloadStatus.STATUS_RUNNING ||
                    item.status == DownloadStatus.STATUS_PAUSED ||
                    item.status == DownloadStatus.STATUS_PENDING
                }.thenByDescending { it.timestampMs }
            )
        _downloadItems.value = items

        // Forward to Petal Live Actions & Petal NowBar Hub
        try {
            val bus = com.petal.browser.live.PetalLiveActionBus
            val active = items.filter {
                it.status == DownloadStatus.STATUS_RUNNING || it.status == DownloadStatus.STATUS_PAUSED
            }
            active.forEach { seenActiveIds.add(it.id) }
            // Cancelled/removed downloads must not stay stuck on the island.
            bus.pruneDownloads(active.take(2).map { it.id }.toSet())
            active.take(2).forEach { bus.updateDownload(it) }
            items.forEach {
                if (it.id !in seenActiveIds) return@forEach
                if (it.status == DownloadStatus.STATUS_SUCCESSFUL && announcedDoneIds.add(it.id)) {
                    bus.updateDownload(it) // shows the "complete" card, auto-dismisses itself
                } else if (it.status == DownloadStatus.STATUS_FAILED) {
                    bus.dismiss("dl_${it.id}")
                }
            }
        } catch (ignored: Throwable) {}

        // Hold the CPU/Wi-Fi awake for every engine's transfers, so nothing stalls on screen-off.
        applicationContext?.let { ctx ->
            // Only bytes-in-flight count. A queued/offline (PENDING) item must not pin the CPU awake.
            val moving = items.count { it.status == DownloadStatus.STATUS_RUNNING }
            com.petal.browser.download.PetalDownloadKeepAlive.update(ctx, moving)
        }
    }

    /**
     * Re-publishes once a second while something is downloading. The store only emits when bytes
     * change, so without this a stalled download would keep showing its last speed forever and the
     * keep-alive wake lock would never be renewed. Publishing an unchanged list is a no-op for the
     * StateFlow, so this costs no recomposition when nothing moved.
     */
    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = tickerScope.launch {
            while (isActive) {
                delay(1000L)
                if (_downloadItems.value.any { it.status == DownloadStatus.STATUS_RUNNING }) publish()
            }
        }
    }

    /** Mirrors Android Components' global BrowserStore download map into Petal's existing UI model. */
    private fun refreshMozillaDownloads() {
        val context = applicationContext ?: return
        try {
            val downloads = com.petal.browser.engine.gecko.PetalEngineStore
                .getStore(context).state.downloads.values
            val nowMs = System.currentTimeMillis()
            val currentMozillaIds = HashSet<String>()
            synchronized(downloadRepository) {
                downloads.forEach { download ->
                    // Filter out stale unhandled INITIATED downloads (0 B stuck items older than 30s) from Mozilla pipeline
                    val isStaleInitiated = download.status == mozilla.components.browser.state.state.content.DownloadState.Status.INITIATED &&
                        (download.contentLength == null || download.contentLength == 0L) &&
                        download.currentBytesCopied == 0L &&
                        (nowMs - download.createdTime > 30_000L)
                    if (isStaleInitiated) {
                        return@forEach
                    }
                    currentMozillaIds.add(download.id)
                    val id = stableId(download.id)
                    val status = when (download.status) {
                        mozilla.components.browser.state.state.content.DownloadState.Status.DOWNLOADING -> DownloadStatus.STATUS_RUNNING
                        mozilla.components.browser.state.state.content.DownloadState.Status.PAUSED -> DownloadStatus.STATUS_PAUSED
                        mozilla.components.browser.state.state.content.DownloadState.Status.INITIATED -> DownloadStatus.STATUS_PENDING
                        mozilla.components.browser.state.state.content.DownloadState.Status.COMPLETED -> DownloadStatus.STATUS_SUCCESSFUL
                        else -> DownloadStatus.STATUS_FAILED
                    }
                    val total = download.contentLength ?: 0L
                    val copied = download.currentBytesCopied
                    var speed = 0L
                    if (status == DownloadStatus.STATUS_RUNNING) {
                        val meter = mozillaMeters.getOrPut(download.id) { com.petal.browser.download.PetalSpeedMeter() }
                        speed = meter.update(nowMs, copied)
                    } else {
                        mozillaMeters.remove(download.id)
                    }
                    val etaSec = com.petal.browser.download.PetalSpeedMeter.etaSeconds(total - copied, speed)
                    val connections = if (status == DownloadStatus.STATUS_RUNNING) {
                        com.petal.browser.download.PetalParallelStats.connectionsFor(download.url)
                    } else 1
                    val rawProgress = download.progress
                    val safeProgress = rawProgress?.let { p ->
                        val pf = p.toFloat()
                        (if (pf > 1f) pf / 100f else pf).coerceIn(0f, 1f)
                    }
                    downloadRepository[download.id] = DownloadItem(
                        id = id,
                        fileName = download.fileName ?: "download",
                        fileUrl = download.url,
                        progress = safeProgress,
                        status = status,
                        bytesDownloaded = download.currentBytesCopied,
                        totalSize = total,
                        speedBytesPerSec = speed,
                        etaSeconds = etaSec,
                        localUri = download.filePath?.let { path -> if (path.startsWith("/")) "file://$path" else path },
                        timestampMs = download.createdTime,
                        connections = connections,
                        downloadId = download.id
                    )
                }
                // Drop meters and repository rows for Mozilla downloads that no longer exist
                val toRemove = downloadRepository.keys.filter { key ->
                    val item = downloadRepository[key]
                    item != null && item.downloadId.isNotBlank() &&
                        !item.downloadId.startsWith("fetch_") &&
                        !currentMozillaIds.contains(key)
                }
                toRemove.forEach { downloadRepository.remove(it) }
                mozillaMeters.keys.retainAll(currentMozillaIds)
            }
        } catch (_: Throwable) {
            // BrowserStore may not be available during helper-process startup.
        }
    }
}
