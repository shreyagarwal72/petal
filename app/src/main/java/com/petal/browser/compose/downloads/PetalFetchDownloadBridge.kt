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

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationManagerCompat
import com.tonyodev.fetch2.AbstractFetchListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.Status
import com.tonyodev.fetch2.EnqueueAction
import com.tonyodev.fetch2.NetworkType
import com.tonyodev.fetch2.Priority
import com.tonyodev.fetch2.Request
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

    private val downloadsMap = LinkedHashMap<Int, Download>()
    private val createdAtMap = LinkedHashMap<Int, Long>()
    private val speedMap = LinkedHashMap<Int, Long>()
    private val etaMap = LinkedHashMap<Int, Long>()
    private val mozillaDownloadsMap = LinkedHashMap<String, DownloadItem>()
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
    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            applicationContext = appContext
            val fetch = fetchInstance(appContext)

            fetch.addListener(object : AbstractFetchListener() {
                override fun onQueued(download: Download, waitingOnNetwork: Boolean) {
                    upsert(download)
                }

                override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                    synchronized(downloadsMap) {
                        speedMap[download.id] = downloadedBytesPerSecond.coerceAtLeast(0L)
                        etaMap[download.id] = etaInMilliSeconds.coerceAtLeast(0L)
                    }
                    upsert(download)
                }

                override fun onPaused(download: Download) {
                    synchronized(downloadsMap) { speedMap[download.id] = 0L }
                    upsert(download)
                }

                override fun onResumed(download: Download) {
                    upsert(download)
                }

                override fun onCompleted(download: Download) {
                    synchronized(downloadsMap) {
                        speedMap[download.id] = 0L
                        etaMap[download.id] = 0L
                    }
                    upsert(download)
                    registerWithSystemDownloads(appContext, download)
                }

                override fun onError(download: Download, error: com.tonyodev.fetch2.Error, throwable: Throwable?) {
                    synchronized(downloadsMap) { speedMap[download.id] = 0L }
                    upsert(download)
                }

                override fun onCancelled(download: Download) {
                    removeEntry(download.id)
                }

                override fun onRemoved(download: Download) {
                    removeEntry(download.id)
                }

                override fun onDeleted(download: Download) {
                    removeEntry(download.id)
                }
            })

            fetch.getDownloads { list ->
                synchronized(downloadsMap) {
                    list.forEach { d ->
                        downloadsMap[d.id] = d
                        if (!createdAtMap.containsKey(d.id)) {
                            val f = File(d.file)
                            val t = when {
                                d.created > 0L -> d.created
                                f.exists() && f.lastModified() > 0L -> f.lastModified()
                                else -> System.currentTimeMillis()
                            }
                            createdAtMap[d.id] = t
                        }
                    }
                }
                publish()
            }

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
        // responseHeaders are the SERVER'S reply headers; they are only useful for naming (already
        // resolved by the caller) and must never be copied onto the outgoing request.
        val started = enqueueMozillaDownload(
            context = context,
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            referrerUrl = referrerUrl,
            isPrivate = isPrivate,
            extraHeaders = emptyMap(),
            onEnqueued = onEnqueued
        )
        if (!started) onFailed?.invoke()
    }

    /** True when [id] belongs to a download owned by Mozilla's service (and its notifications). */
    @JvmStatic
    fun isMozillaOwned(id: Long): Boolean = synchronized(mozillaDownloadsMap) {
        mozillaDownloadsMap.values.any { it.id == id }
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
            val downloadsDir = android.os.Environment
                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            com.petal.browser.download.PetalDownloadHeaderRegistry.put(url, extraHeaders)
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
            onEnqueued?.invoke(id.hashCode().toLong())
            true
        } catch (t: Throwable) {
            android.util.Log.e("PetalDownloadBridge", "Mozilla download start failed: ${t.message}", t)
            false
        }
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
        ensureInitialized(context)
        if (!SafeDownloadValues.isHttpUrl(url)) {
            onFailed?.invoke()
            return
        }
        // Collect the per-download request headers; PetalMozillaFetchClient applies them on top of
        // Gecko's own request when Mozilla's service fetches this URL.
        val extra = LinkedHashMap<String, String>()
        SafeDownloadValues.header(userAgent, 4096)?.let { extra["User-Agent"] = it }
        // Do not send cookies or custom auth headers to presigned CDN / S3 URLs (e.g. github-production-release-asset)
        // as AWS S3 rejects requests with SignatureDoesNotMatch if extra headers are present.
        val isS3OrCdn = url.contains(".s3.amazonaws.com", ignoreCase = true) ||
            url.contains(".githubusercontent.com", ignoreCase = true) ||
            url.contains("github-production-release-asset", ignoreCase = true)
        if (!isS3OrCdn) {
            SafeDownloadValues.header(cookie, 16384)?.let { extra["Cookie"] = it }
        }
        var referrer: String? = null
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
            isPrivate = false,
            extraHeaders = extra,
            onEnqueued = onEnqueued
        )
        if (!started) onFailed?.invoke()
    }

    @JvmStatic
    fun fetchInstance(context: Context): Fetch =
        com.petal.browser.download.PetalDownloadEngine.getInstance(context.applicationContext).fetch

    @JvmStatic
    fun refresh(context: Context) {
        ensureInitialized(context)
        fetchInstance(context).getDownloads { list ->
            synchronized(downloadsMap) {
                list.forEach { d ->
                    downloadsMap[d.id] = d
                    if (!createdAtMap.containsKey(d.id)) {
                        val f = File(d.file)
                        val t = when {
                            d.created > 0L -> d.created
                            f.exists() && f.lastModified() > 0L -> f.lastModified()
                            else -> System.currentTimeMillis()
                        }
                        createdAtMap[d.id] = t
                    }
                }
            }
            publish()
        }
    }

    @JvmStatic
    fun pause(context: Context, id: Long) {
        ensureInitialized(context)
        if (sendMozillaDownloadAction(context, id, mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_PAUSE)) return
        fetchInstance(context).pause(id.toInt())
    }

    @JvmStatic
    fun resume(context: Context, id: Long) {
        ensureInitialized(context)
        if (sendMozillaDownloadAction(context, id, mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_RESUME)) return
        fetchInstance(context).resume(id.toInt())
    }

    @JvmStatic
    fun retry(context: Context, id: Long) {
        ensureInitialized(context)
        if (sendMozillaDownloadAction(context, id, mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_TRY_AGAIN)) return
        fetchInstance(context).retry(id.toInt())
        val item = _downloadItems.value.firstOrNull { it.id == id }
        val fileName = item?.fileName ?: "File"
        PetalLiveAlertManager.trackDownload(context, id, fileName)
    }

    @JvmStatic
    fun cancel(context: Context, id: Long) {
        ensureInitialized(context)
        if (sendMozillaDownloadAction(context, id, mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_CANCEL)) return
        fetchInstance(context).cancel(id.toInt())
        removeEntry(id.toInt())
    }

    private fun sendMozillaDownloadAction(context: Context, id: Long, action: String): Boolean {
        val uuid = synchronized(mozillaDownloadsMap) {
            mozillaDownloadsMap.keys.firstOrNull { it.hashCode().toLong() == id }
        } ?: return false
        return try {
            val intent = Intent(action).apply {
                setPackage(context.applicationContext.packageName)
                putExtra(mozilla.components.feature.downloads.INTENT_EXTRA_DOWNLOAD_ID, uuid)
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
        val mozillaId = synchronized(mozillaDownloadsMap) {
            mozillaDownloadsMap.keys.firstOrNull { it.hashCode().toLong() == item.id || it == item.id.toString() }
        }
        if (mozillaId != null) {
            try {
                sendMozillaDownloadAction(appContext, item.id, mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_CANCEL)
            } catch (_: Throwable) {}
            try {
                com.petal.browser.engine.gecko.PetalEngineStore.getStore(appContext).dispatch(
                    mozilla.components.browser.state.action.DownloadAction.RemoveDownloadAction(mozillaId)
                )
            } catch (_: Throwable) {}
            synchronized(mozillaDownloadsMap) { mozillaDownloadsMap.remove(mozillaId) }
        }

        // 3. Cancel and delete from Fetch2
        val fetchId = item.id.toInt()
        try {
            val fetch = fetchInstance(appContext)
            fetch.cancel(fetchId)
            fetch.delete(fetchId)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Remove immediately from memory state maps
        removeEntry(fetchId)

        // 5. Delete local file and any temporary/partial download file
        try {
            val path = item.localUri?.removePrefix("file://")
            if (!path.isNullOrEmpty()) {
                val file = File(path)
                if (file.exists() && (deleteFile || item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING)) {
                    file.delete()
                }
                // Also clean up Fetch temporary .download or .tmp files
                val tempFile = File(path + ".download")
                if (tempFile.exists()) tempFile.delete()
                val partFile = File(path + ".part")
                if (partFile.exists()) partFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun deleteDownloads(
        context: Context,
        items: List<DownloadItem>,
        deleteFile: Boolean = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean("sp_delete_download_file", false)
    ) {
        items.forEach { deleteDownload(context, it, deleteFile) }
    }

    private fun upsert(download: Download) {
        synchronized(downloadsMap) {
            downloadsMap[download.id] = download
            if (!createdAtMap.containsKey(download.id)) createdAtMap[download.id] = System.currentTimeMillis()
        }
        publish()
    }

    private fun removeEntry(id: Int) {
        synchronized(downloadsMap) {
            downloadsMap.remove(id)
            createdAtMap.remove(id)
            speedMap.remove(id)
            etaMap.remove(id)
        }
        publish()
    }

    private fun publish() {
        refreshMozillaDownloads()
        val fetchItems = synchronized(downloadsMap) { downloadsMap.values.toList() }
            .map { toDownloadItem(it) }
        val items = fetchItems + synchronized(mozillaDownloadsMap) { mozillaDownloadsMap.values.toList() }
            .sortedWith(
                compareByDescending<DownloadItem> { item ->
                    item.status == DownloadManager.STATUS_RUNNING ||
                    item.status == DownloadManager.STATUS_PAUSED ||
                    item.status == DownloadManager.STATUS_PENDING
                }.thenByDescending { it.timestampMs }
            )
        _downloadItems.value = items

        // Hold the CPU/Wi-Fi awake for every engine's transfers, so nothing stalls on screen-off.
        applicationContext?.let { ctx ->
            // Only bytes-in-flight count. A queued/offline (PENDING) item must not pin the CPU awake.
            val moving = items.count { it.status == DownloadManager.STATUS_RUNNING }
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
                if (_downloadItems.value.any { it.status == DownloadManager.STATUS_RUNNING }) publish()
            }
        }
    }

    /** Mirrors Android Components' global BrowserStore download map into Petal's existing UI model. */
    private fun refreshMozillaDownloads() {
        val context = applicationContext ?: return
        try {
            val downloads = com.petal.browser.engine.gecko.PetalEngineStore
                .getStore(context).state.downloads.values
            synchronized(mozillaDownloadsMap) {
                mozillaDownloadsMap.clear()
                downloads.forEach { download ->
                    // Filter out stale unhandled INITIATED downloads (0 B stuck items) from Mozilla pipeline
                    if (download.status == mozilla.components.browser.state.state.content.DownloadState.Status.INITIATED &&
                        (download.contentLength == null || download.contentLength == 0L) &&
                        download.currentBytesCopied == 0L
                    ) {
                        return@forEach
                    }
                    val id = download.id.hashCode().toLong()
                    val status = when (download.status) {
                        mozilla.components.browser.state.state.content.DownloadState.Status.DOWNLOADING -> DownloadManager.STATUS_RUNNING
                        mozilla.components.browser.state.state.content.DownloadState.Status.PAUSED -> DownloadManager.STATUS_PAUSED
                        mozilla.components.browser.state.state.content.DownloadState.Status.INITIATED -> DownloadManager.STATUS_PENDING
                        mozilla.components.browser.state.state.content.DownloadState.Status.COMPLETED -> DownloadManager.STATUS_SUCCESSFUL
                        else -> DownloadManager.STATUS_FAILED
                    }
                    val total = download.contentLength ?: 0L
                    val nowMs = System.currentTimeMillis()
                    val copied = download.currentBytesCopied
                    var speed = 0L
                    if (status == DownloadManager.STATUS_RUNNING) {
                        val meter = mozillaMeters.getOrPut(download.id) { com.petal.browser.download.PetalSpeedMeter() }
                        speed = meter.update(nowMs, copied)
                    } else {
                        mozillaMeters.remove(download.id)
                    }
                    val etaSec = com.petal.browser.download.PetalSpeedMeter.etaSeconds(total - copied, speed)
                    val connections = if (status == DownloadManager.STATUS_RUNNING) {
                        com.petal.browser.download.PetalParallelStats.connectionsFor(download.url)
                    } else 1
                    mozillaDownloadsMap[download.id] = DownloadItem(
                        id = id,
                        fileName = download.fileName ?: "download",
                        fileUrl = download.url,
                        progress = download.progress,
                        status = status,
                        bytesDownloaded = download.currentBytesCopied,
                        totalSize = total,
                        speedBytesPerSec = speed,
                        etaSeconds = etaSec,
                        localUri = download.filePath?.let { path -> if (path.startsWith("/")) "file://$path" else path },
                        timestampMs = download.createdTime,
                        connections = connections
                    )
                }
                // Drop meters for downloads that no longer exist (previously leaked until process death).
                mozillaMeters.keys.retainAll(mozillaDownloadsMap.keys)
            }
        } catch (_: Throwable) {
            // BrowserStore may not be available during helper-process startup.
        }
    }

    private fun toDownloadItem(d: Download): DownloadItem {
        val total = d.total.coerceAtLeast(0L)
        val soFar = d.downloaded.coerceAtLeast(0L)
        val progress = if (total > 0) (soFar.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
        val speed = speedMap[d.id] ?: 0L
        val etaMsValue = etaMap[d.id] ?: 0L
        val etaSec = if (etaMsValue > 0) etaMsValue / 1000L else 0L
        val file = File(d.file)
        val displayName = if (file.name.isNotEmpty()) file.name else d.url

        val timestamp = when {
            d.created > 0L -> d.created
            createdAtMap.containsKey(d.id) -> createdAtMap[d.id]!!
            file.exists() && file.lastModified() > 0L -> file.lastModified()
            else -> System.currentTimeMillis()
        }

        return DownloadItem(
            id = d.id.toLong(),
            fileName = displayName,
            fileUrl = d.url,
            progress = progress,
            status = mapStatus(d.status),
            bytesDownloaded = soFar,
            totalSize = total,
            speedBytesPerSec = speed,
            etaSeconds = etaSec,
            localUri = "file://" + d.file,
            timestampMs = timestamp
        )
    }

    private fun mapStatus(status: Status): Int = when (status) {
        Status.DOWNLOADING -> DownloadManager.STATUS_RUNNING
        Status.PAUSED -> DownloadManager.STATUS_PAUSED
        Status.COMPLETED -> DownloadManager.STATUS_SUCCESSFUL
        Status.QUEUED, Status.ADDED, Status.NONE -> DownloadManager.STATUS_PENDING
        else -> DownloadManager.STATUS_FAILED // FAILED, CANCELLED, REMOVED, DELETED
    }

    /** Makes the finished file visible to the system Downloads app / other apps, same as before. */
    private fun registerWithSystemDownloads(context: Context, download: Download) {
        try {
            val file = File(download.file)
            if (!file.exists()) return
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
                val ext = MimeTypeMap.getFileExtensionFromUrl(file.name)?.lowercase(Locale.US)
                val mime = if (!ext.isNullOrEmpty()) {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                } else "*/*"
                dm.addCompletedDownload(file.name, file.name, true, mime, file.absolutePath, file.length(), true)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
