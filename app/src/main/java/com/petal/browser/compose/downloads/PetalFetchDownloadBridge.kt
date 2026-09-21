/*
 * PetalFetchDownloadBridge.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Android's system android.app.DownloadManager has no public pause/resume
 * API - the "pauseDownload"/"resumeDownload" methods that PetalLiveAlertManager
 * used to reach via reflection don't exist on that class, so every pause/resume
 * tap silently no-opped. Fetch2 (com.petal.browser.download.PetalDownloadEngine)
 * was already wired in as a second, redundant download engine that DOES support
 * real pause/resume/cancel, but nothing ever read its state - the Download
 * Manager screen still polled the system DownloadManager, which is why nothing
 * ever visibly changed.
 *
 * This bridge makes Fetch2 the single source of truth: it listens to Fetch2's
 * download events live (no polling), exposes them as DownloadItem rows the
 * existing Compose UI already knows how to render, and forwards pause/resume/
 * cancel/delete straight to Fetch2 so they actually take effect.
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

            initialized = true
        }
    }

    /**
     * Kotlin-owned media download entry point. Media grabber callers must use this
     * instead of the legacy Java download engine API so every media download is
     * represented by the same Fetch2-backed Petal Download Manager state/UI.
     */
    /**
     * Enqueues a download originating from GeckoEngine/GeckoView.
     *
     * The Gecko engine is the source of truth for the response metadata. We keep that
     * metadata (especially Content-Disposition/Content-Type) instead of routing the event
     * through BrowserUnit, which was the old WebView/raw-download path. The actual bytes are
     * still handled by the single Fetch2 engine so the existing pause/resume/retry UI remains
     * consistent.
     */
    @JvmStatic
    fun enqueueGeckoDownload(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String? = null,
        responseHeaders: Map<String, String> = emptyMap(),
        onEnqueued: ((Long) -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        // Gecko browser responses now enter Mozilla's official download pipeline.
        // Media/sniffer downloads continue through Fetch2 below so the existing
        // media-specific behavior remains unchanged during migration.
        if (enqueueMozillaGeckoDownload(context, url, fileName, mimeType, onEnqueued, onFailed)) {
            return
        }
        val safeHeaders = responseHeaders.filter { (name, value) ->
            name.isNotBlank() && value.isNotBlank() &&
                !name.equals("Content-Length", true) &&
                !name.equals("Content-Encoding", true) &&
                !name.equals("Transfer-Encoding", true) &&
                !name.equals("Connection", true) &&
                !name.equals("Keep-Alive", true) &&
                !name.equals("Proxy-Authenticate", true) &&
                !name.equals("Proxy-Authorization", true) &&
                !name.equals("TE", true) &&
                !name.equals("Trailer", true) &&
                !name.equals("Upgrade", true) &&
                !name.equals("Content-Disposition", true) &&
                !name.equals("Content-Type", true) &&
                '\r' !in name && '\n' !in name &&
                '\r' !in value && '\n' !in value
        }
        enqueueMediaDownload(
            context = context,
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            userAgent = null,
            cookie = null,
            headers = safeHeaders,
            onEnqueued = onEnqueued,
            onFailed = onFailed
        )
    }

    private fun enqueueMozillaGeckoDownload(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String?,
        onEnqueued: ((Long) -> Unit)?,
        onFailed: (() -> Unit)?
    ): Boolean {
        return try {
            if (!SafeDownloadValues.isHttpUrl(url)) return false
            val safeName = SafeDownloadValues.fileName(url, null, mimeType, fileName).ifBlank { "download" }
            val directory = File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                safeName
            )
            val manager = FetchDownloadManager(
                applicationContext = context.applicationContext,
                store = com.petal.browser.engine.gecko.PetalEngineStore.getStore(context),
                service = com.petal.browser.download.PetalMozillaDownloadService::class,
                notificationsDelegate = NotificationsDelegate(
                    NotificationManagerCompat.from(context.applicationContext)
                )
            )
            val id = manager.download(
                DownloadState(
                    url = url,
                    fileName = safeName,
                    contentType = mimeType,
                    directoryPath = directory.parentFile?.absolutePath
                        ?: android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS
                        ).absolutePath
                )
            )
            if (id.isNullOrBlank()) return false
            onEnqueued?.invoke(id.hashCode().toLong())
            true
        } catch (_: Throwable) {
            onFailed?.invoke()
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
        // fileName is already the selected/server-provided name. Passing it as
        // Content-Disposition makes the resolver treat it as a URL fallback and
        // loses names for signed/dynamic CDN URLs.
        val safeName = SafeDownloadValues.fileName(
            url = url,
            contentDisposition = null,
            mimeType = mimeType,
            preferredFileName = fileName
        ).ifBlank { "download" }
        val downloadsDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            safeName
        )
        downloadsDir.parentFile?.mkdirs()
        var target = downloadsDir
        if (target.exists()) {
            val base = target.name.substringBeforeLast('.', target.name)
            val ext = target.name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".${it}" }
            var n = 1
            while (target.exists()) {
                target = File(target.parentFile, "$base($n)$ext")
                n++
            }
        }

        try {
            val request = Request(url, target.absolutePath).apply {
                priority = Priority.HIGH
                networkType = NetworkType.ALL
                enqueueAction = EnqueueAction.INCREMENT_FILE_NAME
                autoRetryMaxAttempts = 10
            }
            SafeDownloadValues.header(userAgent, 4096)?.let { request.addHeader("User-Agent", it) }
            SafeDownloadValues.header(cookie, 16384)?.let { request.addHeader("Cookie", it) }
            headers.forEach { (name, value) ->
                if (name.isBlank() || value.isBlank()) return@forEach
                if (name.equals("Host", true) || name.equals("Content-Length", true) ||
                    name.equals("Content-Encoding", true) || name.equals("Transfer-Encoding", true) ||
                    name.equals("Connection", true) || name.equals("Accept-Encoding", true) ||
                    name.equals("User-Agent", true) || name.equals("Cookie", true)) return@forEach
                if ('\r' in name || '\n' in name || '\r' in value || '\n' in value) return@forEach
                val safeKey = SafeDownloadValues.header(name, 1024) ?: return@forEach
                val safeValue = SafeDownloadValues.header(value, 4096) ?: return@forEach
                request.addHeader(safeKey, safeValue)
            }
            fetchInstance(context).enqueue(request, { updated ->
                // Start the foreground service + live progress notification, exactly like
                // BrowserUnit.download() does for normal downloads. Without this, media-sniffer
                // downloads showed in the list but had no notification and no foreground service,
                // so Android could kill them once the app went to the background.
                val trackedName = target.name
                PetalLiveAlertManager.trackDownload(
                    context.applicationContext,
                    updated.id.toLong(),
                    trackedName
                )
                onEnqueued?.invoke(updated.id.toLong())
            }, {
                onFailed?.invoke()
            })
        } catch (_: Throwable) {
            onFailed?.invoke()
        }
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
    fun deleteDownload(context: Context, item: DownloadItem) {
        ensureInitialized(context)
        try {
            fetchInstance(context).delete(item.id.toInt())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        removeEntry(item.id.toInt())
        try {
            val path = item.localUri?.removePrefix("file://")
            if (!path.isNullOrEmpty()) {
                val file = File(path)
                if (file.exists()) file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun deleteDownloads(context: Context, items: List<DownloadItem>) {
        items.forEach { deleteDownload(context, it) }
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
                    val id = download.id.hashCode().toLong()
                    val status = when (download.status) {
                        mozilla.components.browser.state.state.content.DownloadState.Status.DOWNLOADING -> DownloadManager.STATUS_RUNNING
                        mozilla.components.browser.state.state.content.DownloadState.Status.PAUSED -> DownloadManager.STATUS_PAUSED
                        mozilla.components.browser.state.state.content.DownloadState.Status.INITIATED -> DownloadManager.STATUS_PENDING
                        mozilla.components.browser.state.state.content.DownloadState.Status.COMPLETED -> DownloadManager.STATUS_SUCCESSFUL
                        else -> DownloadManager.STATUS_FAILED
                    }
                    val total = download.contentLength ?: 0L
                    mozillaDownloadsMap[download.id] = DownloadItem(
                        id = id,
                        fileName = download.fileName ?: "download",
                        fileUrl = download.url,
                        progress = download.progress,
                        status = status,
                        bytesDownloaded = download.currentBytesCopied,
                        totalSize = total,
                        localUri = download.filePath,
                        timestampMs = download.createdTime
                    )
                }
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
