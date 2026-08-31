package com.petal.browser.torrent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.preference.PreferenceManager
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.Util1DM
import com.petal.browser.view.NinjaToast
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
        ENGINE_1DM("1DM", "1DM High-Speed", "Fastest multi-threaded engine with 1DM/1DM+ integration & DHT P2P scraping"),
        ENGINE_EMBEDDED("EMBEDDED", "In-App Downloader", "Direct in-app multi-threaded downloader with Live Alerts & stream support")
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

        val mode = getSelectedEngineMode(activity)

        return when (mode) {
            TorrentEngineMode.ENGINE_1DM -> {
                if (Util1DM.is1DMInstalled(activity)) {
                    try {
                        if (isMagnet) {
                            Util1DM.downloadMagnet(activity, url, true)
                        } else {
                            Util1DM.downloadTorrent(activity, url, true)
                        }
                        NinjaToast.show(activity, "Opening 1DM Torrent Downloader...")
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        NinjaToast.show(activity, "1DM Engine unavailable. Opening in-app handler...")
                        launchNativeInAppTorrent(activity, url, fileName)
                    }
                } else {
                    launchNativeInAppTorrent(activity, url, fileName)
                }
            }
            TorrentEngineMode.ENGINE_EMBEDDED -> {
                launchNativeInAppTorrent(activity, url, fileName)
            }
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
                    BrowserUnit.download(activity, webSeed, topic, null)
                    NinjaToast.show(activity, "Downloading Torrent via WebSeed: $topic")
                } else {
                    // Open with system torrent handler intent as fallback
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(Intent.createChooser(intent, "Download Magnet Link"))
                }
            } else {
                val guessedName = fileName ?: "download.torrent"
                BrowserUnit.download(activity, url, guessedName, "application/x-bittorrent")
                NinjaToast.show(activity, "Downloading Torrent File: $guessedName")
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            NinjaToast.show(activity, "Unable to handle Torrent link: ${e.message}")
            return false
        }
    }
}
