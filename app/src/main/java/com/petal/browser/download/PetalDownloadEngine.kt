package com.petal.browser.download

import android.content.Context
import android.os.Environment
import android.util.Log
import com.tonyodev.fetch2.AbstractFetchListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import com.tonyodev.fetch2.NetworkType
import com.tonyodev.fetch2.Priority
import com.tonyodev.fetch2.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer

/**
 * Petal Fast Download Engine (MDM - Multi-threaded Download Manager)
 * Uses Fetch2 under the hood for parallel multi-chunk downloading, resume support,
 * and high-speed downloads.
 */
class PetalDownloadEngine private constructor(context: Context) {

    val fetch: Fetch
    private val recentEnqueues: MutableMap<String, Long> = ConcurrentHashMap()

    init {
        val appContext = context.applicationContext
        val fetchConfiguration = FetchConfiguration.Builder(appContext)
            .setDownloadConcurrentLimit(12)
            .setProgressReportingInterval(100L)
            .enableAutoStart(true)
            .enableRetryOnNetworkGain(true)
            .enableLogging(false)
            .build()
        fetch = Fetch.Impl.getInstance(fetchConfiguration)

        fetch.addListener(object : AbstractFetchListener() {
            override fun onCompleted(download: Download) {
                Log.d(TAG, "Download completed: " + download.file)
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                Log.e(TAG, "Download error: $error, url=" + download.url, throwable)
            }

            override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                Log.d(TAG, "Download speed: $downloadedBytesPerSecond B/s, progress: " + download.progress + "%")
            }
        })
    }

    /**
     * Enqueues a high-speed multi-threaded download request.
     */
    fun enqueueDownload(
        context: Context,
        url: String?,
        fileName: String,
        userAgent: String?,
        cookie: String?,
        extraHeaders: Map<String, String>?
    ) {
        enqueueDownload(context, url, fileName, userAgent, cookie, extraHeaders, null)
    }

    /**
     * Enqueues a download request and reports the Fetch2-assigned download ID back once
     * queued, so callers (e.g. BrowserUnit) can start live-tracking/notifications for the
     * exact download that was created instead of guessing an ID.
     */
    fun enqueueDownload(
        context: Context,
        url: String?,
        fileName: String,
        userAgent: String?,
        cookie: String?,
        extraHeaders: Map<String, String>?,
        onEnqueued: BiConsumer<Int, String>?
    ) {
        if (url.isNullOrEmpty()) return

        val now = System.currentTimeMillis()
        val lastTime = recentEnqueues[url]
        if (lastTime != null && (now - lastTime) < 2000L) {
            Log.d(TAG, "Bypassing duplicate download enqueue for URL: $url")
            return
        }
        recentEnqueues[url] = now
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        var targetFile = File(downloadsDir, fileName)
        if (targetFile.exists()) {
            var name = fileName
            var extension = ""
            val dotIndex = fileName.lastIndexOf('.')
            if (dotIndex > 0 && dotIndex < fileName.length - 1) {
                name = fileName.substring(0, dotIndex)
                extension = fileName.substring(dotIndex)
            }
            var counter = 1
            while (targetFile.exists()) {
                targetFile = File(downloadsDir, "$name($counter)$extension")
                counter++
            }
        }
        val filePath = targetFile.absolutePath

        val request = Request(url, filePath).apply {
            priority = Priority.HIGH
            networkType = NetworkType.ALL
        }

        if (!userAgent.isNullOrEmpty()) {
            request.addHeader("User-Agent", userAgent)
        }
        if (!cookie.isNullOrEmpty()) {
            request.addHeader("Cookie", cookie)
        }
        if (extraHeaders != null) {
            for ((key, value) in extraHeaders) {
                request.addHeader(key, value)
            }
        }

        val finalResolvedFileName = targetFile.name
        fetch.enqueue(request, { updatedRequest ->
            Log.d(TAG, "Download enqueued successfully with ID: " + updatedRequest.id + ", file: " + filePath)
            onEnqueued?.accept(updatedRequest.id, finalResolvedFileName)
        }, { error ->
            Log.e(TAG, "Failed to enqueue download: $error")
        })
    }

    companion object {
        private const val TAG = "PetalDownloadEngine"
        private var sInstance: PetalDownloadEngine? = null

        @Synchronized
        @JvmStatic
        fun getInstance(context: Context): PetalDownloadEngine {
            if (sInstance == null) {
                sInstance = PetalDownloadEngine(context)
            }
            return sInstance!!
        }
    }
}
