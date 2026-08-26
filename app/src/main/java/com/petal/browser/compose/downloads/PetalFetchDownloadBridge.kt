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
import android.media.MediaScannerConnection
import android.webkit.MimeTypeMap
import com.tonyodev.fetch2.AbstractFetchListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.Status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

object PetalFetchDownloadBridge {

    private val downloadsMap = LinkedHashMap<Int, Download>()
    private val createdAtMap = LinkedHashMap<Int, Long>()
    private val speedMap = LinkedHashMap<Int, Long>()
    private val etaMap = LinkedHashMap<Int, Long>()

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
        fetchInstance(context).pause(id.toInt())
    }

    @JvmStatic
    fun resume(context: Context, id: Long) {
        ensureInitialized(context)
        fetchInstance(context).resume(id.toInt())
    }

    @JvmStatic
    fun cancel(context: Context, id: Long) {
        ensureInitialized(context)
        fetchInstance(context).cancel(id.toInt())
        removeEntry(id.toInt())
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
        val items = synchronized(downloadsMap) { downloadsMap.values.toList() }
            .map { toDownloadItem(it) }
            .sortedWith(
                compareByDescending<DownloadItem> { item ->
                    item.status == DownloadManager.STATUS_RUNNING ||
                    item.status == DownloadManager.STATUS_PAUSED ||
                    item.status == DownloadManager.STATUS_PENDING
                }.thenByDescending { it.timestampMs }
            )
        _downloadItems.value = items
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
