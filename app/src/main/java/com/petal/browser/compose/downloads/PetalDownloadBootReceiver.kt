package com.petal.browser.compose.downloads

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity

/**
 * Resumes unfinished downloads after device reboots or application updates,
 * matching Mozilla Firefox's download recovery architecture.
 *
 * Android 15+ forbids starting a `dataSync` foreground service from BOOT_COMPLETED
 * (ForegroundServiceStartNotAllowedException). This receiver used to call startForegroundService
 * from inside the Fetch callback with no guard, which on a target-36 build crashed the app
 * process at every boot that had an unfinished download. The start is now guarded, and when the
 * system refuses it the user gets a tap-to-resume notification instead of a crash.
 */
class PetalDownloadBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i("PetalDownloadBoot", "Received $action: restoring active background downloads")
            try {
                val appContext = context.applicationContext
                PetalFetchDownloadBridge.ensureInitialized(appContext)
                PetalFetchDownloadBridge.fetchInstance(appContext).getDownloads { downloads ->
                    val hasActive = downloads.any {
                        it.status == com.tonyodev.fetch2.Status.DOWNLOADING ||
                        it.status == com.tonyodev.fetch2.Status.QUEUED
                    }
                    if (hasActive) {
                        Log.i("PetalDownloadBoot", "Unfinished downloads found. Starting PetalDownloadService...")
                        startServiceOrNotify(appContext)
                    }
                }
            } catch (t: Throwable) {
                Log.e("PetalDownloadBoot", "Failed to restore downloads on boot", t)
            }
        }
    }

    private fun startServiceOrNotify(appContext: Context) {
        try {
            val serviceIntent = Intent(appContext, PetalDownloadService::class.java).apply {
                setAction(PetalDownloadService.ACTION_RESTORE_DOWNLOADS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(serviceIntent)
            } else {
                appContext.startService(serviceIntent)
            }
        } catch (t: Throwable) {
            Log.w("PetalDownloadBoot", "System refused to start the download service from the background", t)
            postResumeNotification(appContext)
        }
    }

    private fun postResumeNotification(appContext: Context) {
        try {
            LiveUpdateNotificationManager.ensureChannelCreated(appContext)
            val open = Intent(appContext, BrowserActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_downloads", true)
            }
            val pending = PendingIntent.getActivity(
                appContext, NOTIF_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(appContext, LiveUpdateNotificationManager.CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_download)
                .setContentTitle("Downloads paused")
                .setContentText("Tap to resume your unfinished downloads")
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.notify(NOTIF_ID, notification)
        } catch (t: Throwable) {
            Log.w("PetalDownloadBoot", "Could not post resume notification", t)
        }
    }

    private companion object {
        const val NOTIF_ID = 888130
    }
}
