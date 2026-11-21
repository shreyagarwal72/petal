package com.petal.browser.compose.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.petal.browser.download.PetalDownloadResumer
import mozilla.components.browser.state.state.content.DownloadState

/**
 * Recovers unfinished downloads after a reboot or an app update.
 *
 * Android 15+ forbids starting a `dataSync` foreground service from BOOT_COMPLETED, so this
 * receiver NEVER starts the download service. It only restores the persisted store state, records
 * which downloads were interrupted and posts a "tap to resume" notification. The resume itself
 * (ACTION_TRY_AGAIN + download id) happens in [PetalDownloadResumer] once the app is in the
 * foreground.
 */
class PetalDownloadBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        Log.i(TAG, "Received $action: restoring download state (no service start)")
        val pending = goAsync()
        Thread {
            try {
                val app = context.applicationContext
                val store = com.petal.browser.engine.gecko.PetalEngineStore.getStore(app)
                store.dispatch(mozilla.components.browser.state.action.DownloadAction.RestoreDownloadsStateAction)
                val interrupted = store.state.downloads.values.filter {
                    !it.private && (it.status == DownloadState.Status.DOWNLOADING ||
                        it.status == DownloadState.Status.INITIATED ||
                        it.status == DownloadState.Status.PAUSED)
                }.map { it.id }
                if (interrupted.isNotEmpty()) {
                    PetalDownloadResumer.markPending(app, interrupted)
                    PetalDownloadResumer.postResumeNotification(app)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to restore downloads on boot", t)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "PetalDownloadBoot"
    }
}
