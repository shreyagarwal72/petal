package com.petal.browser.compose.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Resumes unfinished downloads after device reboots or application updates,
 * matching Mozilla Firefox's download recovery architecture.
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
                        val serviceIntent = Intent(appContext, PetalDownloadService::class.java).apply {
                            setAction(PetalDownloadService.ACTION_RESTORE_DOWNLOADS)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            appContext.startForegroundService(serviceIntent)
                        } else {
                            appContext.startService(serviceIntent)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e("PetalDownloadBoot", "Failed to restore downloads on boot", t)
            }
        }
    }
}
