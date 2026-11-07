package com.petal.browser.download

import com.petal.browser.compose.downloads.DownloadItem
import com.petal.browser.compose.downloads.DownloadStatus
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for Download Consolidation Stage F:
 * Mozilla Source Parity & Full Subsystem Verification
 */
class DownloadStageFParityTest {

    @Test
    fun testMozillaDownloadsServiceContract() {
        val serviceClass = PetalMozillaDownloadService::class.java
        assertTrue(
            "PetalMozillaDownloadService must extend AbstractFetchDownloadService",
            mozilla.components.feature.downloads.AbstractFetchDownloadService::class.java.isAssignableFrom(serviceClass)
        )
    }

    @Test
    fun testCompleteSubsystemPipelineIntegrity() {
        // 1. Authoritative Gecko Cookie Bridge
        PetalGeckoCookieBridge.clearAllCookies()
        val testUrl = "https://authenticated.cdn.mozilla.net/builds/firefox.apk"
        PetalGeckoCookieBridge.recordCookies(testUrl, "session_token=secret_abc123", isPrivate = false)
        assertEquals("session_token=secret_abc123", PetalGeckoCookieBridge.getCookies(testUrl, isPrivate = false))

        // 2. Header Registry Registration
        val downloadUuid = UUID.randomUUID().toString()
        val headers = mapOf(
            "Referer" to "https://authenticated.cdn.mozilla.net/releases",
            "Authorization" to "Bearer token_xyz"
        )
        PetalDownloadHeaderRegistry.put(testUrl, headers, downloadId = downloadUuid, isPrivate = false)

        val retrievedHeaders = PetalDownloadHeaderRegistry.headersFor(testUrl, downloadId = downloadUuid, isPrivate = false)
        assertEquals(2, retrievedHeaders.size)
        assertEquals("Bearer token_xyz", retrievedHeaders["Authorization"])

        // 3. Stable ID Generation for Unified Store
        val stableNumericId = PetalFetchDownloadBridge.stableId(downloadUuid)
        assertTrue(stableNumericId >= 0L)

        // 4. DownloadItem Unified Model
        val item = DownloadItem(
            id = stableNumericId,
            fileName = "firefox.apk",
            fileUrl = testUrl,
            progress = 0.75f,
            status = DownloadStatus.STATUS_RUNNING,
            bytesDownloaded = 75_000_000L,
            totalSize = 100_000_000L,
            speedBytesPerSec = 5_000_000L,
            etaSeconds = 5L,
            localUri = "file:///storage/emulated/0/Download/firefox.apk",
            connections = 4,
            downloadId = downloadUuid
        )

        assertEquals(downloadUuid, item.downloadId)
        assertEquals(stableNumericId, item.id)
        assertEquals(4, item.connections)

        // 5. DownloadSource Classification
        assertEquals(DownloadSource.HTTP_MOZILLA, DownloadSource.fromIdentifier("mozilla"))
        assertEquals(DownloadSource.YTDLP, DownloadSource.fromIdentifier("ytdlp"))
        assertEquals(DownloadSource.TORRENT, DownloadSource.fromIdentifier("torrent"))
        assertEquals(DownloadSource.EXTERNAL, DownloadSource.fromIdentifier("external"))

        // Cleanup
        PetalDownloadHeaderRegistry.remove(testUrl, downloadUuid)
        PetalGeckoCookieBridge.clearAllCookies()
    }

    @Test
    fun testPrivateDownloadStackIsolation() {
        val privateUrl = "https://secure-bank.example.com/statement.pdf"
        val privateToken = "session=top_secret_private_123"

        // Cookie isolation
        PetalGeckoCookieBridge.recordCookies(privateUrl, privateToken, isPrivate = true)
        assertNull("Private cookies must never leak to standard session", PetalGeckoCookieBridge.getCookies(privateUrl, isPrivate = false))
        assertEquals(privateToken, PetalGeckoCookieBridge.getCookies(privateUrl, isPrivate = true))

        // Header isolation
        val privateId = UUID.randomUUID().toString()
        PetalDownloadHeaderRegistry.put(privateUrl, mapOf("Cookie" to privateToken), downloadId = privateId, isPrivate = true)
        assertTrue("Private headers must never leak to standard session", PetalDownloadHeaderRegistry.headersFor(privateUrl, downloadId = privateId, isPrivate = false).isEmpty())
        assertFalse("Private headers must be available for private session", PetalDownloadHeaderRegistry.headersFor(privateUrl, downloadId = privateId, isPrivate = true).isEmpty())

        // Clear private data
        PetalGeckoCookieBridge.clearPrivateCookies()
        PetalDownloadHeaderRegistry.clearPrivateHeaders()

        assertNull(PetalGeckoCookieBridge.getCookies(privateUrl, isPrivate = true))
        assertTrue(PetalDownloadHeaderRegistry.headersFor(privateUrl, downloadId = privateId, isPrivate = true).isEmpty())
    }
}
