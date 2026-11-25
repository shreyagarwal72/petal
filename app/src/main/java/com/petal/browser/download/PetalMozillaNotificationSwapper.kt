package com.petal.browser.download

import android.app.Notification
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.petal.browser.compose.downloads.DownloadItem
import com.petal.browser.compose.downloads.DownloadStatus
import com.petal.browser.compose.downloads.LiveUpdateNotificationManager
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.compose.downloads.PetalLiveAlertManager
import mozilla.components.browser.state.state.content.DownloadState
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Makes Mozilla's download service show Petal's live notification instead of its own plain one,
 * so each download has exactly ONE notification (the "hide the original" rule).
 *
 * How: [PetalNotificationChannelOptimizer] already sits in front of the system notification
 * service for this process. For every `enqueueNotificationWithTag` call it asks [intercept]:
 *  - the notification belongs to a running / pending / paused Mozilla download -> its Notification
 *    is replaced by [PetalLiveAlertManager.buildActiveNotification] (same id, so the foreground
 *    service stays valid and the system just updates the existing card);
 *  - anything else (completed, failed, the service's group summary, other features) -> untouched.
 *
 * Safety: every failure path returns the original arguments, so the worst case is Mozilla's own
 * notification (never zero notifications, never two). A download's first post is never dropped.
 */
object PetalMozillaNotificationSwapper {
    private const val TAG = "PetalNotifSwapper"
    private const val MIN_GAP_MS = 600L

    /** Returned by [intercept] when this update should be skipped (too soon after the last one). */
    @JvmField
    val DROP: Array<Any?> = arrayOfNulls<Any>(0)

    @Volatile
    private var appContext: Context? = null

    private val notifIdToUuid = ConcurrentHashMap<Int, String>()
    private val lastPostAt = ConcurrentHashMap<Int, Long>()
    private val lastStatus = ConcurrentHashMap<Int, Int>()
    private var notificationIdGetter: Method? = null

    @JvmStatic
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @JvmStatic
    fun intercept(args: Array<out Any?>?): Array<out Any?>? {
        if (args == null) return args
        return try {
            doIntercept(args)
        } catch (t: Throwable) {
            Log.w(TAG, "swap skipped: ${t.message}")
            args
        }
    }

    private fun doIntercept(args: Array<out Any?>): Array<out Any?> {
        val ctx = appContext ?: return args
        val idx = args.indexOfFirst { it is Notification }
        if (idx < 2) return args
        val notifId = args[idx - 1] as? Int ?: return args
        // Mozilla posts with a null tag; a tagged notification is somebody else's (including ours).
        if (args[idx - 2] != null) return args
        if (!LiveUpdateNotificationManager.isLiveUpdatesEnabled(ctx)) return args

        val row = resolveItem(ctx, notifId) ?: return args
        // Decide from the BrowserStore, which Mozilla's service updates BEFORE it posts a notification.
        // The bridge row is debounced (about 250 ms behind): going by it, a "completed" post could be
        // swapped for a stale "Downloading" card (or dropped), and nothing would ever replace that card.
        val live = com.petal.browser.engine.gecko.PetalEngineStore.getStore(ctx).state.downloads[row.downloadId]
        val liveStatus = when (live?.status) {
            DownloadState.Status.DOWNLOADING, DownloadState.Status.INITIATED -> DownloadStatus.STATUS_RUNNING
            DownloadState.Status.PAUSED -> DownloadStatus.STATUS_PAUSED
            else -> null
        }
        if (live == null || liveStatus == null) {
            clear(notifId)
            return args
        }
        val item = row.copy(
            status = liveStatus,
            bytesDownloaded = live.currentBytesCopied,
            totalSize = live.contentLength ?: row.totalSize
        )

        val now = SystemClock.elapsedRealtime()
        val prevAt = lastPostAt[notifId]
        val prevStatus = lastStatus[notifId]
        if (prevAt != null && prevStatus == item.status && now - prevAt < MIN_GAP_MS) return DROP
        lastPostAt[notifId] = now
        lastStatus[notifId] = item.status

        val copy: Array<Any?> = Array(args.size) { args[it] }
        copy[idx] = PetalLiveAlertManager.buildActiveNotification(ctx, item, (args[idx] as Notification).group)
        return copy
    }

    private fun clear(notifId: Int) {
        lastPostAt.remove(notifId)
        lastStatus.remove(notifId)
    }

    private fun resolveItem(ctx: Context, notifId: Int): DownloadItem? {
        var uuid = notifIdToUuid[notifId]
        if (uuid == null) {
            val downloads = com.petal.browser.engine.gecko.PetalEngineStore.getStore(ctx).state.downloads
            for ((key, dl) in downloads) {
                val getter = notificationIdGetter
                    ?: dl.javaClass.getMethod("getNotificationId").also { notificationIdGetter = it }
                val nid = getter.invoke(dl) as? Int ?: continue
                notifIdToUuid[nid] = key
            }
            uuid = notifIdToUuid[notifId] ?: return null
        }
        return PetalFetchDownloadBridge.downloadItems.value.firstOrNull { it.downloadId == uuid && it.source.isMozillaOwned }
    }
}
