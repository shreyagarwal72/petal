package com.petal.browser.compose.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PetalDownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        when (intent.action) {
            ACTION_CANCEL_DOWNLOAD -> {
                try {
                    PetalFetchDownloadBridge.cancel(context, downloadId)
                    PetalLiveAlertManager.stopTracking(context, downloadId)
                    Log.d("PetalLiveAlert", "Download $downloadId cancelled by user action")
                } catch (e: Exception) {
                    Log.e("PetalLiveAlert", "Error cancelling download $downloadId", e)
                }
            }
            ACTION_PAUSE_DOWNLOAD -> {
                PetalLiveAlertManager.pauseDownload(context, downloadId)
                Log.d("PetalLiveAlert", "Download $downloadId paused by user action")
            }
            ACTION_RESUME_DOWNLOAD -> {
                PetalLiveAlertManager.resumeDownload(context, downloadId)
                Log.d("PetalLiveAlert", "Download $downloadId resumed by user action")
            }
            ACTION_RETRY_DOWNLOAD -> {
                PetalLiveAlertManager.retryDownload(context, downloadId)
                Log.d("PetalLiveAlert", "Download $downloadId retry initiated by user action")
            }
        }
    }

    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "com.petal.browser.action.CANCEL_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.petal.browser.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.petal.browser.action.RESUME_DOWNLOAD"
        const val ACTION_RETRY_DOWNLOAD = "com.petal.browser.action.RETRY_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
    }
}
