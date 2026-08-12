package com.petal.browser.compose.downloads

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PetalDownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId != -1L) {
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                dm?.remove(downloadId)
                PetalLiveAlertManager.stopTracking(context, downloadId)
                Log.d("PetalLiveAlert", "Download $downloadId cancelled by user action")
            } catch (e: Exception) {
                Log.e("PetalLiveAlert", "Error cancelling download $downloadId", e)
            }
        }
    }

    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "com.petal.browser.action.CANCEL_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
    }
}
