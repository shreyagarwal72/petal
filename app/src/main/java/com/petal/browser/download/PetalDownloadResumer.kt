package com.petal.browser.download

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.compose.downloads.LiveUpdateNotificationManager
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import mozilla.components.browser.state.state.content.DownloadState
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deferred resume of interrupted downloads.
 *
 * Android 15+ forbids starting a `dataSync` foreground service from BOOT_COMPLETED, and the
 * dataSync time limit (onTimeout) forces the service down. In both cases we only record which
 * downloads were interrupted and post a "tap to resume" notification. The actual resume
 * (ACTION_TRY_AGAIN + download id, the same call `restartDownload` makes) runs once an activity
 * of this app is started, i.e. when the app is in the foreground and FGS starts are allowed.
 *
 * Only non-private download ids are persisted; private ids stay in memory (private downloads
 * are not restored by the store after process death anyway).
 */
object PetalDownloadResumer {
    private const val TAG = "PetalDownloadResumer"
    private const val PREFS = "petal_download_resume"
    private const val KEY_IDS = "pending_ids"
    const val NOTIF_ID = 888130

    private val privatePending = CopyOnWriteArraySet<String>()
    private val started = AtomicInteger(0)
    @Volatile private var installed = false

    @JvmStatic
    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (started.getAndIncrement() == 0) {
                    Handler(Looper.getMainLooper()).post { resumePending(app) }
                }
            }
            override fun onActivityStopped(activity: Activity) {
                started.updateAndGet { (it - 1).coerceAtLeast(0) }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    @JvmStatic
    fun markPending(context: Context, ids: Collection<String>, isPrivate: Boolean = false) {
        if (ids.isEmpty()) return
        if (isPrivate) {
            privatePending.addAll(ids)
            return
        }
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val merged = (sp.getStringSet(KEY_IDS, emptySet()) ?: emptySet()) + ids
        sp.edit().putStringSet(KEY_IDS, merged).apply()
    }

    @JvmStatic
    fun pendingIds(context: Context): Set<String> {
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return (sp.getStringSet(KEY_IDS, emptySet()) ?: emptySet()) + privatePending
    }

    @JvmStatic
    fun clear(context: Context, id: String) {
        privatePending.remove(id)
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = sp.getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        if (id in cur) sp.edit().putStringSet(KEY_IDS, cur - id).apply()
    }

    /** True for statuses that still need work. */
    @JvmStatic
    fun isResumable(status: DownloadState.Status): Boolean = when (status) {
        DownloadState.Status.PAUSED, DownloadState.Status.FAILED,
        DownloadState.Status.DOWNLOADING, DownloadState.Status.INITIATED -> true
        else -> false
    }

    /** Resumes every recorded download that is still unfinished. Call only while in the foreground. */
    @JvmStatic
    fun resumePending(context: Context) {
        val app = context.applicationContext
        val ids = pendingIds(app)
        if (ids.isEmpty()) return
        try {
            PetalFetchDownloadBridge.ensureInitialized(app)
            val store = com.petal.browser.engine.gecko.PetalEngineStore.getStore(app)
            for (id in ids) {
                val dl = store.state.downloads[id]
                clear(app, id)
                if (dl == null || !isResumable(dl.status)) continue
                Log.i(TAG, "Resuming interrupted download $id")
                PetalFetchDownloadBridge.resumeByUuid(app, id)
            }
            (app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(NOTIF_ID)
        } catch (t: Throwable) {
            Log.w(TAG, "resumePending failed", t)
        }
    }

    @JvmStatic
    fun postResumeNotification(context: Context) {
        val app = context.applicationContext
        try {
            LiveUpdateNotificationManager.ensureChannelCreated(app)
            val open = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_downloads", true)
            }
            val pending = PendingIntent.getActivity(
                app, NOTIF_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(app, LiveUpdateNotificationManager.CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_download)
                .setContentTitle("Downloads paused")
                .setContentText("Tap to resume your unfinished downloads")
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            (app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(NOTIF_ID, n)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not post resume notification", t)
        }
    }
}
