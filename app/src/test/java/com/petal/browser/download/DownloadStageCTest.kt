package com.petal.browser.download

import com.petal.browser.compose.downloads.DownloadItem
import com.petal.browser.compose.downloads.DownloadStatus
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for Download Consolidation Stage C:
 * Single State Store & Stable IDs
 */
class DownloadStageCTest {

    @Test
    fun testStableId_ProducesConsistentNonNegativeId() {
        val uuid1 = UUID.randomUUID().toString()
        val id1_first = PetalFetchDownloadBridge.stableId(uuid1)
        val id1_second = PetalFetchDownloadBridge.stableId(uuid1)

        assertEquals(id1_first, id1_second)
        assertTrue(id1_first >= 0L)

        // Empty string safety
        assertEquals(0L, PetalFetchDownloadBridge.stableId(""))
    }

    @Test
    fun testStableId_DistinctUuidsProduceDistinctIds() {
        val uuids = (1..100).map { UUID.randomUUID().toString() }
        val ids = uuids.map { PetalFetchDownloadBridge.stableId(it) }.toSet()

        // 100 random UUIDs must produce 100 distinct 64-bit IDs
        assertEquals(100, ids.size)
    }

    @Test
    fun testDownloadItem_WithDownloadIdPreservesContract() {
        val uuid = UUID.randomUUID().toString()
        val stableId = PetalFetchDownloadBridge.stableId(uuid)

        val item = DownloadItem(
            id = stableId,
            fileName = "linux_distro.iso",
            fileUrl = "https://example.org/linux.iso",
            progress = 0.5f,
            status = DownloadStatus.STATUS_RUNNING,
            bytesDownloaded = 500_000_000L,
            totalSize = 1_000_000_000L,
            speedBytesPerSec = 10_000_000L,
            etaSeconds = 50L,
            localUri = "file:///storage/emulated/0/Download/linux.iso",
            timestampMs = 1700000000000L,
            connections = 4,
            downloadId = uuid
        )

        assertEquals(stableId, item.id)
        assertEquals(uuid, item.downloadId)
        assertEquals(4, item.connections)
        assertEquals(DownloadStatus.STATUS_RUNNING, item.status)
        assertEquals(DownloadSource.HTTP_MOZILLA, item.source)
    }

    @Test
    fun testLocalRowKey_UsesSameStableIdScheme() {
        // yt-dlp / page-data rows use their own task key but the same id scheme as Mozilla rows.
        val key = "petal_social_ab12cd34"
        assertEquals(PetalFetchDownloadBridge.stableId(key), PetalFetchDownloadBridge.stableId(key))
        assertTrue(PetalFetchDownloadBridge.stableId(key) >= 0L)
    }
}
