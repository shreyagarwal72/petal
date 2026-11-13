/*
 * PetalFetchDownloadBridge.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Single entry point and state bridge for Petal downloads.
 *
 * HTTP downloads (browser, media sniffer, in-app updater) run on Mozilla's official download
 * pipeline (Android Components feature-downloads / AbstractFetchDownloadService over Gecko's
 * network stack, as in Firefox/Fenix). Their state lives in the BrowserStore and is mirrored here
 * into DownloadItem rows (with sampled speed/ETA) for the Compose UI. Mozilla's service owns their
 * notifications; PetalLiveAlertManager skips them via isMozillaOwned().
 *
 * Work that Mozilla's service cannot do (yt-dlp extraction, blob:/data: payloads written by Petal)
 * is tracked as "local rows" with the same DownloadItem model, tagged by [DownloadSource], so the
 * Downloads screen and the live notifications treat every source uniformly.
 */

package com.petal.browser.compose.downloads

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import com.petal.browser.download.DownloadSource
import com.petal.browser.download.PetalDownloadFailureLog
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
import java.util.concurrent.ConcurrentHashMap
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.feature.downloads.manager.FetchDownloadManager
import mozilla.components.support.base.android.NotificationsDelegate

/** Why a download could not be started. Each case has a message that is shown to the user. */
enum class DownloadFailure(val userMessage: String) {
    INVALID_URL("This link can't be downloaded (blocked or unsupported URL)."),
    NO_NETWORK("No network connection. Check your connection and try again."),
    INSUFFICIENT_STORAGE("Not enough free storage to start this download."),
    START_FAILED("The download could not be started. Please try again.")
}

/**
 * Pure mapping from Mozilla's [DownloadState.Status] to Petal's [DownloadStatus] ints.
 *
 * DOWNLOADING/INITIATED are only trusted while something is actually driving the transfer (the
 * Mozilla service is alive, or a start was just requested). Otherwise the row is an orphan left by
 * process death or a killed service; it is shown as PAUSED so the user can resume it, instead of
 * being hidden or shown as an eternal spinner.
 */
internal object PetalDownloadStatusMapper {
    @JvmStatic
    fun toStatus(status: DownloadState.Status, serviceAlive: Boolean, startGraceActive: Boolean): Int {
        val driven = serviceAlive || startGraceActive
        return when (status) {
            DownloadState.Status.DOWNLOADING -> if (driven) DownloadStatus.STATUS_RUNNING else DownloadStatus.STATUS_PAUSED
            DownloadState.Status.INITIATED -> if (driven) DownloadStatus.STATUS_PENDING else DownloadStatus.STATUS_PAUSED
            DownloadState.Status.PAUSED -> DownloadStatus.STATUS_PAUSED
            DownloadState.Status.COMPLETED -> DownloadStatus.STATUS_SUCCESSFUL
            else -> DownloadStatus.STATUS_FAILED // FAILED and CANCELLED: both offer Retry
        }
    }

    @JvmStatic
    fun failureReason(
        status: DownloadState.Status,
        offline: Boolean,
        lowStorage: Boolean,
        logged: String?
    ): String? = when {
        status == DownloadState.Status.CANCELLED -> "Cancelled"
        status != DownloadState.Status.FAILED -> null
        !logged.isNullOrBlank() -> logged
        lowStorage -> DownloadFailure.INSUFFICIENT_STORAGE.userMessage
        offline -> DownloadFailure.NO_NETWORK.userMessage
        else -> "Download failed. Tap Retry to try again."
    }
}

object PetalFetchDownloadBridge {

    private const val TAG = "PetalDownloadBridge"
    private const val MIN_FREE_BYTES = 20L * 1024 * 1024
    private const val START_GRACE_MS = 8_000L

    // Rows derived from Mozilla's BrowserStore, keyed by Mozilla's download id (UUID).
    private val downloadRepository = LinkedHashMap<String, DownloadItem>()
    // Rows owned by Petal itself (yt-dlp, blob:/data:), keyed by their own task key.
    private val localRows = LinkedHashMap<String, DownloadItem>()
    private val localCancel = ConcurrentHashMap<String, () -> Unit>()
    private val localRetry = ConcurrentHashMap<String, () -> Unit>()
    private val sourceById = ConcurrentHashMap<String, DownloadSource>()

    // Throughput for Mozilla downloads (the store exposes bytes, not speed/ETA). One sliding-window
    // meter per download gives a steady readout that also decays when the transfer stalls.
    private val mozillaMeters = HashMap<String, com.petal.browser.download.PetalSpeedMeter>()
    private val tickerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null
    @Volatile
    private var applicationContext: Context? = null
    @Volatile
    private var startGraceUntil = 0L

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
                // Link taps arrive as ContentState.download on the selected tab; this shows the
                // confirmation sheet and hands the download to the router.
                com.petal.browser.download.PetalDownloadPromptObserver.start(appContext)
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "Download prompt observer failed to start", t)
            }

            try {
                PetalLiveAlertManager.startGlobalDownloadObserver(appContext)
            } catch (_: Throwable) {
            }

            startTicker()
            initialized = true
        }
    }

    // ── Starting downloads ────────────────────────────────────────────────

    private class StartResult(val id: String?, val failure: DownloadFailure?)

    private fun mozillaManager(appContext: Context): FetchDownloadManager<com.petal.browser.download.PetalMozillaDownloadService> = FetchDownloadManager(
        applicationContext = appContext,
        store = com.petal.browser.engine.gecko.PetalEngineStore.getStore(appContext),
        service = com.petal.browser.download.PetalMozillaDownloadService::class,
        notificationsDelegate = NotificationsDelegate(NotificationManagerCompat.from(appContext))
    )

    private fun armStartGrace() {
        startGraceUntil = SystemClock.elapsedRealtime() + START_GRACE_MS
        com.petal.browser.download.PetalDownloadServiceState.invalidate()
    }

    private fun isOnline(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Throwable) {
        true // never block a download because the connectivity probe itself failed
    }

    /** Shows [failure] to the user (toast on the main thread). */
    @JvmStatic
    fun notifyFailure(context: Context, failure: DownloadFailure) = notifyMessage(context, failure.userMessage)

    @JvmStatic
    fun notifyMessage(context: Context, message: String) {
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            try {
                val host: Context = com.petal.browser.engine.gecko.PetalGeckoRuntime.getForegroundActivity() ?: appContext
                com.petal.browser.view.PetalToast.show(host, message)
            } catch (_: Throwable) {
            }
        }
    }

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
        val result = startMozillaDownload(
            context = context,
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            referrerUrl = referrerUrl,
            isPrivate = isPrivate,
            extraHeaders = emptyMap(),
            source = DownloadSource.HTTP_MOZILLA
        )
        finishStart(context, result, onEnqueued, onFailed)
    }

    private fun finishStart(
        context: Context,
        result: StartResult,
        onEnqueued: ((Long) -> Unit)?,
        onFailed: (() -> Unit)?
    ) {
        val id = result.id
        if (id != null) {
            onEnqueued?.invoke(stableId(id))
        } else {
            result.failure?.let { notifyFailure(context, it) }
            onFailed?.invoke()
        }
    }

    /**
     * Starts the self-updater's APK download through the shared pipeline, tagged [DownloadSource.UPDATE].
     * [onEnqueued] receives Mozilla's download id; [onFailed] receives a user-facing reason.
     */
    @JvmStatic
    fun enqueueUpdateDownload(
        context: Context,
        url: String,
        fileName: String,
        referrerUrl: String?,
        onEnqueued: (String) -> Unit,
        onFailed: (String) -> Unit
    ) {
        ensureInitialized(context)
        val result = startMozillaDownload(
            context = context,
            url = url,
            fileName = fileName,
            mimeType = "application/vnd.android.package-archive",
            referrerUrl = referrerUrl,
            isPrivate = false,
            extraHeaders = emptyMap(),
            source = DownloadSource.UPDATE
        )
        val id = result.id
        if (id != null) onEnqueued(id) else onFailed((result.failure ?: DownloadFailure.START_FAILED).userMessage)
    }

    /**
     * Hands a [DownloadState] reported by a tab (ContentState.download) straight to Mozilla's
     * manager. Used for blob:/data:/moz-extension: payloads: their bytes live in the original
     * state (Fenix passes it through unchanged), so it must not be rebuilt from the URL.
     */
    @JvmStatic
    fun enqueueStoreDownload(context: Context, download: DownloadState, fileName: String): Boolean {
        ensureInitialized(context)
        val appContext = context.applicationContext
        return try {
            val safeName = SafeDownloadValues.fileName(
                url = download.url,
                contentDisposition = null,
                mimeType = download.contentType,
                preferredFileName = fileName
            ).ifBlank { "download" }
            val dir = com.petal.browser.download.PetalSmartDownloadRouter
                .resolveDestinationDirectory(appContext, safeName, download.contentType)
            if (dir.usableSpace < MIN_FREE_BYTES) {
                notifyFailure(appContext, DownloadFailure.INSUFFICIENT_STORAGE)
                return false
            }
            armStartGrace()
            val id = mozillaManager(appContext).download(
                download.copy(fileName = safeName, directoryPath = dir.absolutePath)
            )
            if (id.isNullOrBlank()) {
                notifyFailure(appContext, DownloadFailure.START_FAILED)
                false
            } else {
                sourceById[id] = DownloadSource.HTTP_MOZILLA
                publish()
                true
            }
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Store download start failed: ${t.message}", t)
            notifyFailure(appContext, DownloadFailure.START_FAILED)
            false
        }
    }

    /** True when [id] belongs to a download owned by Mozilla's service (and its notifications). */
    @JvmStatic
    fun isMozillaOwned(id: Long): Boolean = synchronized(downloadRepository) {
        downloadRepository.values.any { it.id == id && it.source.isMozillaOwned }
    }

    private fun startMozillaDownload(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String?,
        referrerUrl: String?,
        isPrivate: Boolean,
        extraHeaders: Map<String, String>,
        source: DownloadSource
    ): StartResult {
        if (!SafeDownloadValues.isHttpUrl(url)) return StartResult(null, DownloadFailure.INVALID_URL)
        val appContext = context.applicationContext
        if (!isOnline(appContext)) return StartResult(null, DownloadFailure.NO_NETWORK)
        return try {
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
            if (freeSpace < MIN_FREE_BYTES) {
                android.util.Log.e(TAG, "Insufficient storage space ($freeSpace bytes free) to start download")
                return StartResult(null, DownloadFailure.INSUFFICIENT_STORAGE)
            }
            PetalDownloadFailureLog.clear(url)
            com.petal.browser.download.PetalDownloadHeaderRegistry.put(
                url = url,
                headers = extraHeaders,
                isPrivate = isPrivate
            )
            armStartGrace()
            val id = mozillaManager(appContext).download(
                DownloadState(
                    url = url,
                    fileName = safeName,
                    contentType = mimeType,
                    directoryPath = downloadsDir.absolutePath,
                    referrerUrl = referrerUrl,
                    private = isPrivate
                )
            )
            if (id.isNullOrBlank()) return StartResult(null, DownloadFailure.START_FAILED)
            sourceById[id] = source
            com.petal.browser.download.PetalDownloadHeaderRegistry.put(
                url = url,
                headers = extraHeaders,
                downloadId = id,
                isPrivate = isPrivate
            )
            publish()
            StartResult(id, null)
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Mozilla download start failed: ${t.message}", t)
            StartResult(null, DownloadFailure.START_FAILED)
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
            notifyFailure(context, DownloadFailure.INVALID_URL)
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
        val result = startMozillaDownload(
            context = context,
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            referrerUrl = referrer,
            isPrivate = isPrivate,
            extraHeaders = extra,
            source = DownloadSource.HTTP_MOZILLA
        )
        finishStart(context, result, onEnqueued, onFailed)
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

    // ── Local rows (yt-dlp, blob:/data:) ──────────────────────────────────

    /** Creates or updates a Petal-owned row. [key] is the caller's stable task key. */
    @JvmStatic
    @JvmOverloads
    fun updateLocalRow(
        context: Context,
        key: String,
        source: DownloadSource,
        fileName: String,
        fileUrl: String,
        status: Int,
        progress: Float? = null,
        bytes: Long = 0L,
        total: Long = 0L,
        speed: Long = 0L,
        eta: Long = 0L,
        localUri: String? = null,
        failureReason: String? = null
    ) {
        ensureInitialized(context)
        synchronized(localRows) {
            val prev = localRows[key]
            localRows[key] = DownloadItem(
                id = stableId(key),
                fileName = fileName,
                fileUrl = fileUrl,
                progress = progress,
                status = status,
                bytesDownloaded = bytes,
                totalSize = total,
                speedBytesPerSec = speed,
                etaSeconds = eta,
                localUri = localUri ?: prev?.localUri,
                timestampMs = prev?.timestampMs ?: System.currentTimeMillis(),
                connections = 1,
                downloadId = key,
                source = source,
                failureReason = failureReason
            )
        }
        publish()
    }

    /** Registers what the Cancel/Retry buttons of a local row do. Either may be null. */
    @JvmStatic
    fun setLocalHandlers(key: String, onCancel: (() -> Unit)?, onRetry: (() -> Unit)?) {
        if (onCancel != null) localCancel[key] = onCancel else localCancel.remove(key)
        if (onRetry != null) localRetry[key] = onRetry else localRetry.remove(key)
    }

    @JvmStatic
    fun removeLocalRow(key: String) {
        synchronized(localRows) { localRows.remove(key) }
        localCancel.remove(key)
        localRetry.remove(key)
        publish()
    }

    private fun findItem(id: Long): DownloadItem? {
        _downloadItems.value.firstOrNull { it.id == id }?.let { return it }
        synchronized(downloadRepository) { downloadRepository.values.firstOrNull { it.id == id } }?.let { return it }
        return synchronized(localRows) { localRows.values.firstOrNull { it.id == id } }
    }

    // ── Controls ──────────────────────────────────────────────────────────

    @JvmStatic
    fun refresh(context: Context) {
        ensureInitialized(context)
        publish()
    }

    @JvmStatic
    fun pause(context: Context, id: Long) {
        ensureInitialized(context)
        val item = findItem(id) ?: return
        if (!item.source.isMozillaOwned) return // yt-dlp / page-data rows cannot be paused
        val appContext = context.applicationContext
        if (com.petal.browser.download.PetalDownloadServiceState.isRunning(appContext)) {
            sendMozillaDownloadAction(appContext, item.downloadId, mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_PAUSE)
        } else {
            // Nothing is driving the transfer; just record the paused state so Resume can restart it.
            try {
                val store = com.petal.browser.engine.gecko.PetalEngineStore.getStore(appContext)
                store.state.downloads[item.downloadId]?.let { dl ->
                    store.dispatch(DownloadAction.UpdateDownloadAction(dl.copy(status = DownloadState.Status.PAUSED)))
                }
            } catch (_: Throwable) {
            }
        }
    }

    @JvmStatic
    fun togglePauseResume(context: Context, id: Long) {
        val item = findItem(id) ?: return
        if (item.status == DownloadStatus.STATUS_PAUSED) {
            resume(context, id)
        } else {
            pause(context, id)
        }
    }

    @JvmStatic
    fun resume(context: Context, id: Long) {
        ensureInitialized(context)
        val item = findItem(id) ?: return
        if (!item.source.isMozillaOwned) return
        val appContext = context.applicationContext
        val uuid = item.downloadId
        if (uuid.isBlank()) return
        if (com.petal.browser.download.PetalDownloadServiceState.isRunning(appContext)) {
            sendMozillaDownloadAction(appContext, uuid, mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_RESUME)
            // The running service may no longer hold a job for this download (it was paused long ago
            // or restored after a restart). If it is still PAUSED shortly after, restart it directly.
            tickerScope.launch {
                delay(1500L)
                val stillPaused = try {
                    com.petal.browser.engine.gecko.PetalEngineStore.getStore(appContext)
                        .state.downloads[uuid]?.status == DownloadState.Status.PAUSED
                } catch (_: Throwable) {
                    false
                }
                if (stillPaused) restartDownload(appContext, uuid)
            }
        } else {
            restartDownload(appContext, uuid)
        }
        publish()
    }

    @JvmStatic
    fun retry(context: Context, id: Long) {
        ensureInitialized(context)
        val appContext = context.applicationContext
        val item = findItem(id) ?: return
        if (!item.source.isMozillaOwned) {
            localRetry[item.downloadId]?.invoke() ?: notifyMessage(appContext, "This download can't be retried. Start it again from the page.")
            return
        }
        if (item.downloadId.isBlank()) return
        if (!isOnline(appContext)) {
            notifyFailure(appContext, DownloadFailure.NO_NETWORK)
            return
        }
        PetalDownloadFailureLog.clear(item.fileUrl)
        restartDownload(appContext, item.downloadId)
        PetalLiveAlertManager.trackDownload(appContext, id, item.fileName)
        publish()
    }

    /**
     * (Re)starts Mozilla's foreground service for an existing download id, as Fenix's retry does
     * (FetchDownloadManager.tryAgain). A broadcast alone is not enough: the service registers its
     * action receiver only while it is alive and stops itself when nothing is active, so after a
     * failure or process death the broadcast would go nowhere.
     */
    private fun restartDownload(appContext: Context, uuid: String) {
        try {
            armStartGrace()
            val alive = com.petal.browser.download.PetalDownloadServiceState.isRunning(appContext)
            if (alive) {
                sendMozillaDownloadAction(
                    appContext,
                    uuid,
                    mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_TRY_AGAIN
                )
            } else {
                val intent = Intent(appContext, com.petal.browser.download.PetalMozillaDownloadService::class.java).apply {
                    action = mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_TRY_AGAIN
                    putExtra(mozilla.components.feature.downloads.INTENT_EXTRA_DOWNLOAD_ID, uuid)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "restartDownload failed for $uuid: ${t.message}", t)
            notifyFailure(appContext, DownloadFailure.START_FAILED)
        }
    }

    @JvmStatic
    fun cancel(context: Context, id: Long) {
        ensureInitialized(context)
        val item = findItem(id) ?: return
        if (!item.source.isMozillaOwned) {
            localCancel[item.downloadId]?.invoke()
            removeLocalRow(item.downloadId)
            return
        }
        removeMozillaDownload(context.applicationContext, item.downloadId)
        publish()
    }

    /** Cancels the service job, dismisses Mozilla's notification and drops the download from the store. */
    private fun removeMozillaDownload(appContext: Context, uuid: String) {
        if (uuid.isBlank()) return
        // Read Mozilla's real notification id while the download still exists in the store.
        cancelMozillaNotification(appContext, uuid)
        val alive = com.petal.browser.download.PetalDownloadServiceState.isRunning(appContext)
        if (alive) {
            sendMozillaDownloadAction(appContext, uuid, mozilla.components.feature.downloads.AbstractFetchDownloadService.ACTION_CANCEL)
        }
        val removeFromStore = {
            try {
                com.petal.browser.engine.gecko.PetalEngineStore.getStore(appContext)
                    .dispatch(DownloadAction.RemoveDownloadAction(uuid))
            } catch (_: Throwable) {
            }
            synchronized(downloadRepository) { downloadRepository.remove(uuid) }
            mozillaMeters.remove(uuid)
            sourceById.remove(uuid)
            publish()
        }
        if (alive) {
            // Give the service a moment to process the cancel so it cannot write the row back.
            tickerScope.launch {
                delay(400L)
                removeFromStore()
            }
        } else {
            removeFromStore()
        }
    }

    private fun cancelMozillaNotification(appContext: Context, uuid: String) {
        try {
            val dl = com.petal.browser.engine.gecko.PetalEngineStore.getStore(appContext).state.downloads[uuid]
            // DownloadState.notificationId is the id Mozilla's service actually posted under.
            val realId = dl?.let {
                runCatching { it.javaClass.getMethod("getNotificationId").invoke(it) as? Int }.getOrNull()
            }
            val nm = NotificationManagerCompat.from(appContext)
            nm.cancel(realId ?: uuid.hashCode())
        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    fun sendMozillaDownloadAction(context: Context, downloadId: String, action: String): Boolean {
        if (downloadId.isBlank()) return false
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
        val row = findItem(item.id) ?: item

        // 1. Stop live-alert tracking for this row.
        try {
            PetalLiveAlertManager.stopTracking(appContext, row.id)
        } catch (_: Throwable) {}

        // 2. Cancel the transfer and remove the row from whichever owner holds it.
        if (row.source.isMozillaOwned) {
            removeMozillaDownload(appContext, row.downloadId)
            PetalDownloadFailureLog.clear(row.fileUrl)
        } else {
            try { localCancel.remove(row.downloadId)?.invoke() } catch (_: Throwable) {}
            localRetry.remove(row.downloadId)
            synchronized(localRows) { localRows.remove(row.downloadId) }
            try {
                NotificationManagerCompat.from(appContext).cancel(row.id.toInt())
            } catch (_: Throwable) {}
        }

        // 3. Delete local file and any temporary/partial download file
        val inFlight = row.status == DownloadStatus.STATUS_RUNNING || row.status == DownloadStatus.STATUS_PENDING
        try {
            val uriString = row.localUri
            if (!uriString.isNullOrEmpty()) {
                if (uriString.startsWith("content://")) {
                    if (deleteFile || inFlight) {
                        appContext.contentResolver.delete(android.net.Uri.parse(uriString), null, null)
                    }
                } else {
                    val path = uriString.removePrefix("file://")
                    val file = File(path)
                    if (file.exists() && (deleteFile || inFlight)) {
                        file.delete()
                    }
                    val partFile = File("$path.part")
                    if (partFile.exists()) partFile.delete()
                    val tempFile = File("$path.download")
                    if (tempFile.exists()) tempFile.delete()
                }
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

    /** Removes every finished row from the list (files stay on disk unless [deleteFile]). */
    @JvmStatic
    fun clearCompleted(context: Context, deleteFile: Boolean = false) {
        ensureInitialized(context)
        val done = _downloadItems.value.filter { it.status == DownloadStatus.STATUS_SUCCESSFUL }
        deleteDownloads(context, done, deleteFile)
    }

    // ── Publishing ────────────────────────────────────────────────────────

    private val seenActiveIds = java.util.Collections.synchronizedSet(HashSet<Long>())
    private val announcedDoneIds = java.util.Collections.synchronizedSet(HashSet<Long>())

    private fun publish() {
        refreshMozillaDownloads()
        val items = (synchronized(downloadRepository) { downloadRepository.values.toList() } +
            synchronized(localRows) { localRows.values.toList() })
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
     * Re-publishes once a second while something is downloading or starting. The store only emits
     * when bytes change, so without this a stalled download would keep showing its last speed forever,
     * a start that never reached the service would never flip to PAUSED, and the keep-alive wake lock
     * would never be renewed. Publishing an unchanged list is a no-op for the StateFlow.
     */
    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = tickerScope.launch {
            while (isActive) {
                delay(1000L)
                if (_downloadItems.value.any {
                        it.status == DownloadStatus.STATUS_RUNNING || it.status == DownloadStatus.STATUS_PENDING
                    }) publish()
            }
        }
    }

    private fun sourceFor(context: Context, id: String): DownloadSource {
        sourceById[id]?.let { return it }
        if (com.petal.browser.unit.PetalUpdateManager.isUpdateDownload(context, id)) {
            sourceById[id] = DownloadSource.UPDATE
            return DownloadSource.UPDATE
        }
        return DownloadSource.HTTP_MOZILLA
    }

    /** Mirrors Android Components' global BrowserStore download map into Petal's existing UI model. */
    private fun refreshMozillaDownloads() {
        val context = applicationContext ?: return
        try {
            val downloads = com.petal.browser.engine.gecko.PetalEngineStore
                .getStore(context).state.downloads.values
            val nowMs = System.currentTimeMillis()
            val serviceAlive = com.petal.browser.download.PetalDownloadServiceState.isRunning(context)
            val graceActive = SystemClock.elapsedRealtime() < startGraceUntil
            val offline = !isOnline(context)
            val currentMozillaIds = HashSet<String>()
            synchronized(downloadRepository) {
                downloads.forEach { download ->
                    currentMozillaIds.add(download.id)
                    val id = stableId(download.id)
                    val status = PetalDownloadStatusMapper.toStatus(download.status, serviceAlive, graceActive)
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
                    val reason = if (status == DownloadStatus.STATUS_FAILED) {
                        val lowStorage = total > 0 && runCatching {
                            File(download.directoryPath).usableSpace < (total - copied).coerceAtLeast(0L)
                        }.getOrDefault(false)
                        PetalDownloadStatusMapper.failureReason(
                            download.status,
                            offline,
                            lowStorage,
                            PetalDownloadFailureLog.reasonFor(download.url)
                        )
                    } else null
                    downloadRepository[download.id] = DownloadItem(
                        id = id,
                        fileName = download.fileName ?: "download",
                        fileUrl = download.url,
                        progress = safeProgress,
                        status = status,
                        bytesDownloaded = copied,
                        totalSize = total,
                        speedBytesPerSec = speed,
                        etaSeconds = etaSec,
                        localUri = download.filePath?.let { path -> if (path.startsWith("/")) "file://$path" else path },
                        timestampMs = download.createdTime,
                        connections = connections,
                        downloadId = download.id,
                        source = sourceFor(context, download.id),
                        failureReason = reason
                    )
                }
                // Drop meters and repository rows for Mozilla downloads that no longer exist
                val toRemove = downloadRepository.keys.filter { key -> !currentMozillaIds.contains(key) }
                toRemove.forEach { downloadRepository.remove(it) }
                mozillaMeters.keys.retainAll(currentMozillaIds)
                sourceById.keys.retainAll(currentMozillaIds)
            }
        } catch (_: Throwable) {
            // BrowserStore may not be available during helper-process startup.
        }
    }
}
