package com.petal.browser.download

import android.content.Context

/**
 * DownloadSource
 * ─────────────────────────────────────────────────────────────────────────────
 * Authoritative classification for all download origins and plugins in Petal.
 * Every download source coordinates with or delegates to the unified download subsystem.
 */
enum class DownloadSource(val identifier: String, val displayName: String) {
    /**
     * Primary browser core: Mozilla AbstractFetchDownloadService + GeckoViewFetchClient
     * + PetalParallelFetchClient.
     */
    HTTP_MOZILLA("mozilla", "Browser Core"),

    /**
     * Video / Social extraction plugin powered by yt-dlp + aria2c + ffmpeg.
     */
    YTDLP("ytdlp", "Media / yt-dlp"),

    /**
     * Torrent / Magnet routing (1DM P2P, web seeds, or external torrent client).
     */
    TORRENT("torrent", "Torrent / Magnet"),

    /**
     * External third-party download managers (1DM, ADM, Download Navi).
     */
    EXTERNAL("external", "External Manager");

    companion object {
        @JvmStatic
        fun fromIdentifier(id: String): DownloadSource {
            return values().firstOrNull { it.identifier.equals(id, ignoreCase = true) } ?: HTTP_MOZILLA
        }
    }
}

/**
 * PetalDownloadPlugin
 * ─────────────────────────────────────────────────────────────────────────────
 * Interface for secondary download sources plugging into Petal's unified core.
 */
interface PetalDownloadPlugin {
    val source: DownloadSource
    fun canHandle(url: String, mimeType: String?): Boolean
    fun handle(
        context: Context,
        url: String,
        fileName: String? = null,
        mimeType: String? = null,
        headers: Map<String, String> = emptyMap()
    ): Boolean
}
