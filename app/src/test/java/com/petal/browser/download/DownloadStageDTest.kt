package com.petal.browser.download

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Download Consolidation Stage D:
 * Plugins & Download Sources
 */
class DownloadStageDTest {

    @Test
    fun testDownloadSource_IdentifiersAndValues() {
        assertEquals(DownloadSource.HTTP_MOZILLA, DownloadSource.fromIdentifier("mozilla"))
        assertEquals(DownloadSource.YTDLP, DownloadSource.fromIdentifier("ytdlp"))
        assertEquals(DownloadSource.TORRENT, DownloadSource.fromIdentifier("torrent"))
        assertEquals(DownloadSource.EXTERNAL, DownloadSource.fromIdentifier("external"))

        // Case insensitivity
        assertEquals(DownloadSource.HTTP_MOZILLA, DownloadSource.fromIdentifier("MOZILLA"))
        assertEquals(DownloadSource.YTDLP, DownloadSource.fromIdentifier("YtDlp"))

        // Unknown fallback
        assertEquals(DownloadSource.HTTP_MOZILLA, DownloadSource.fromIdentifier("unknown_plugin"))
    }

    @Test
    fun testTorrentMode_AccurateDescriptors() {
        val embedded = com.petal.browser.torrent.PetalTorrentEngineManager.TorrentEngineMode.ENGINE_EMBEDDED
        assertTrue(embedded.description.contains("WebSeed") || embedded.description.contains("torrent"))

        val dm = com.petal.browser.torrent.PetalTorrentEngineManager.TorrentEngineMode.ENGINE_1DM
        assertTrue(dm.description.contains("1DM"))
    }
}
