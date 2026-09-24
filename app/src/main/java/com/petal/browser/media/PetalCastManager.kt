package com.petal.browser.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.petal.browser.view.PetalToast

/**
 * PetalCastManager
 * Dispatches video streams to external cast receivers, Smart TVs, Chromecast apps,
 * DLNA/UPnP players (VLC, Web Video Caster, BubbleUPnP, LocalCast, etc.).
 */
object PetalCastManager {

    @JvmStatic
    fun castMedia(context: Context, videoUrl: String?, title: String?) {
        if (videoUrl.isNullOrBlank()) {
            PetalToast.show(context, "No stream URL found to cast")
            return
        }

        try {
            val uri = Uri.parse(videoUrl)
            val mimeType = when {
                videoUrl.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
                videoUrl.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
                videoUrl.contains(".webm", ignoreCase = true) -> "video/webm"
                videoUrl.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
                else -> "video/*"
            }

            // Standard Android Cast / View Intent targeting media players and casting apps
            val castIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_TITLE, title ?: "Web Video")
                putExtra("title", title ?: "Web Video")
            }

            val chooser = Intent.createChooser(castIntent, "Cast to device / TV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            PetalToast.show(context, "No casting or video player apps found")
        }
    }
}
