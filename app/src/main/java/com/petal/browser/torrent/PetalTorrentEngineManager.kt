package com.petal.browser.torrent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.preference.PreferenceManager
import com.petal.browser.unit.Util1DM
import com.petal.browser.view.PetalToast
import java.io.File

/**
 * Petal Torrent Engine Manager
 * Supports:
 * 1. 1DM / 1DM+ High-Speed Torrent Engine (Option 1 - Official P2P SDK Intent)
 * 2. Embedded In-App Light Torrent Downloader (Option 2 - Native In-App WebSeed / Magnet Streamer)
 * 3. System External App Fallback
 *
 * Configured via Preference key "sp_torrent_engine" ("1DM", "EMBEDDED", "NATIVE").
 */
object PetalTorrentEngineManager {

    enum class TorrentEngineMode(val key: String, val title: String, val description: String) {
        ENGINE_1DM("1DM", "1DM Torrent Engine", "Integrates with 1DM/1DM+ for full BitTorrent P2P and DHT swarm downloads"),
        ENGINE_EMBEDDED("EMBEDDED", "WebSeed / Torrent Hand-off", "Fetches .torrent files and web seeds directly; hands magnet links to external torrent clients")
    }

    @JvmStatic
    fun getSelectedEngineMode(context: Context): TorrentEngineMode {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val modeStr = sp.getString("sp_torrent_engine", "1DM") ?: "1DM"
        return when (modeStr.uppercase()) {
            "EMBEDDED" -> TorrentEngineMode.ENGINE_EMBEDDED
            else -> TorrentEngineMode.ENGINE_1DM
        }
    }

    @JvmStatic
    fun isFirstTimeEnginePromptNeeded(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return !sp.getBoolean("sp_download_engine_prompt_shown", false)
    }

    @JvmStatic
    fun setEnginePromptCompleted(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean("sp_download_engine_prompt_shown", true).apply()
    }

    @JvmStatic
    fun setEngineMode(context: Context, mode: TorrentEngineMode) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit()
            .putString("sp_torrent_engine", mode.key)
            .putBoolean("sp_download_engine_prompt_shown", true)
            .apply()
    }

    @JvmStatic
    fun handleTorrentOrMagnet(
        activity: Activity,
        url: String,
        fileName: String? = null,
        mimeType: String? = null
    ): Boolean {
        val isMagnet = url.startsWith("magnet:", ignoreCase = true)
        val isTorrentFile = url.endsWith(".torrent", ignoreCase = true) || "application/x-bittorrent".equals(mimeType, ignoreCase = true)

        if (!isMagnet && !isTorrentFile) return false

        if (Util1DM.is1DMInstalled(activity)) {
            return try {
                if (isMagnet) {
                    Util1DM.downloadMagnet(activity, url, true)
                } else {
                    Util1DM.downloadTorrent(activity, url, true)
                }
                PetalToast.show(activity, "Opening 1DM Torrent Downloader...")
                true
            } catch (e: Exception) {
                e.printStackTrace()
                launchNativeInAppTorrent(activity, url, fileName)
            }
        } else {
            return launchNativeInAppTorrent(activity, url, fileName)
        }
    }

    /**
     * Option 2: Embedded In-App Magnet & Torrent Streamer.
     * Parses web seeds, magnet hashes, and launches direct stream download.
     */
    private fun launchNativeInAppTorrent(activity: Activity, url: String, fileName: String?): Boolean {
        try {
            if (url.startsWith("magnet:", ignoreCase = true)) {
                val uri = Uri.parse(url)
                val topic = uri.getQueryParameter("dn") ?: fileName ?: "Torrent_Download"
                val webSeed = uri.getQueryParameter("ws")
                
                if (!webSeed.isNullOrBlank()) {
                    com.petal.browser.download.PetalDownloadRouter.download(activity, webSeed, topic, null)
                    PetalToast.show(activity, "Downloading Torrent via WebSeed: $topic")
                } else {
                    // Open with system torrent handler intent as fallback
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(Intent.createChooser(intent, "Download Magnet Link"))
                }
            } else {
                val guessedName = fileName ?: "download.torrent"
                com.petal.browser.download.PetalDownloadRouter.download(activity, url, guessedName, "application/x-bittorrent")
                PetalToast.show(activity, "Downloading Torrent File: $guessedName")
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            PetalToast.show(activity, "Unable to handle Torrent link: ${e.message}")
            return false
        }
    }
}
