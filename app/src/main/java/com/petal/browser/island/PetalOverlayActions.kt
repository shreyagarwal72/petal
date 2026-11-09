package com.petal.browser.island

import android.content.Context
import android.content.Intent
import com.petal.browser.activity.MainActivity
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.live.LiveActionActionListener
import com.petal.browser.live.LiveActionItem
import com.petal.browser.live.PetalLiveActionBus
import com.petal.browser.media.PetalMediaSessionService

/**
 * Button handling for the over-other-apps island. Unlike the in-app listener it must work with
 * no Activity alive, so downloads go straight to the bridge and media goes through the
 * already-running media session service.
 */
class PetalOverlayActions(context: Context) : LiveActionActionListener {

    private val app = context.applicationContext

    override fun onDownloadTogglePause(downloadId: Long) =
        PetalFetchDownloadBridge.togglePauseResume(app, downloadId)

    override fun onDownloadCancel(downloadId: Long) =
        PetalFetchDownloadBridge.cancel(app, downloadId)

    override fun onMediaTogglePlayPause() {
        val s = PetalLiveActionBus.getCurrentState()
        val media = (s.primary as? LiveActionItem.Media) ?: (s.secondary as? LiveActionItem.Media)
        sendMedia(if (media?.isPlaying == true) PetalMediaSessionService.ACTION_PAUSE else PetalMediaSessionService.ACTION_PLAY)
    }

    override fun onMediaSkip(seconds: Int) =
        sendMedia(if (seconds < 0) PetalMediaSessionService.ACTION_SKIP_BACKWARD else PetalMediaSessionService.ACTION_SKIP_FORWARD)

    override fun onOpenDownloads() = launch { putExtra("open_downloads", true) }

    override fun onOpenBrowser() = launch { }

    override fun onOpenFileViewer() = launch { }

    override fun onOpenProfile() = launch { putExtra("open_profile", true) }

    override fun onOpenPasswords() = launch { putExtra("open_passwords", true) }

    override fun onOpenDownloadedFile(localUri: String, fileName: String) = onOpenDownloads()

    override fun onCopyText(text: String, label: String) {
        try {
            val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        } catch (_: Exception) {
        }
    }

    private fun sendMedia(action: String) {
        try {
            app.startService(Intent(app, PetalMediaSessionService::class.java).setAction(action))
        } catch (_: Exception) {
            // Service already gone: nothing to control.
        }
    }

    private inline fun launch(extras: Intent.() -> Unit) {
        try {
            app.startActivity(
                Intent(app, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .apply(extras)
            )
        } catch (_: Exception) {
        }
    }
}
