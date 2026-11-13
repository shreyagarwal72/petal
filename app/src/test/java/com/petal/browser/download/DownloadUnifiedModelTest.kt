package com.petal.browser.download

import com.petal.browser.compose.downloads.DownloadFailure
import com.petal.browser.compose.downloads.DownloadItem
import com.petal.browser.compose.downloads.DownloadStatus
import com.petal.browser.compose.downloads.PetalDownloadStatusMapper
import mozilla.components.browser.state.state.content.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unified download model: item mapping for every Mozilla status, the retry/resume decision inputs,
 * source tagging, URL validation in the router and failure reasons.
 */
class DownloadUnifiedModelTest {

    private fun map(status: DownloadState.Status, alive: Boolean = true, grace: Boolean = false) =
        PetalDownloadStatusMapper.toStatus(status, alive, grace)

    // ── Status mapping ────────────────────────────────────────────────────

    @Test
    fun downloading_isRunning_whileServiceAlive() {
        assertEquals(DownloadStatus.STATUS_RUNNING, map(DownloadState.Status.DOWNLOADING))
    }

    @Test
    fun initiated_isPending_whileServiceAlive() {
        assertEquals(DownloadStatus.STATUS_PENDING, map(DownloadState.Status.INITIATED))
    }

    @Test
    fun startGrace_countsAsDriven_evenBeforeServiceIsVisible() {
        assertEquals(DownloadStatus.STATUS_PENDING, map(DownloadState.Status.INITIATED, alive = false, grace = true))
        assertEquals(DownloadStatus.STATUS_RUNNING, map(DownloadState.Status.DOWNLOADING, alive = false, grace = true))
    }

    @Test
    fun orphanedDownloading_isShownPaused_soItCanBeResumed() {
        // Process death / killed service: nothing drives the transfer any more.
        assertEquals(DownloadStatus.STATUS_PAUSED, map(DownloadState.Status.DOWNLOADING, alive = false))
        assertEquals(DownloadStatus.STATUS_PAUSED, map(DownloadState.Status.INITIATED, alive = false))
    }

    @Test
    fun paused_completed_failed_cancelled() {
        assertEquals(DownloadStatus.STATUS_PAUSED, map(DownloadState.Status.PAUSED, alive = false))
        assertEquals(DownloadStatus.STATUS_SUCCESSFUL, map(DownloadState.Status.COMPLETED, alive = false))
        assertEquals(DownloadStatus.STATUS_FAILED, map(DownloadState.Status.FAILED, alive = false))
        // CANCELLED also offers Retry, so it shares the FAILED status.
        assertEquals(DownloadStatus.STATUS_FAILED, map(DownloadState.Status.CANCELLED, alive = false))
    }

    @Test
    fun everyMozillaStatus_mapsToAKnownPetalStatus() {
        val known = setOf(
            DownloadStatus.STATUS_PENDING, DownloadStatus.STATUS_RUNNING, DownloadStatus.STATUS_PAUSED,
            DownloadStatus.STATUS_SUCCESSFUL, DownloadStatus.STATUS_FAILED
        )
        DownloadState.Status.values().forEach { status ->
            listOf(true, false).forEach { alive ->
                assertTrue("$status alive=$alive", map(status, alive) in known)
            }
        }
    }

    // ── Retry path: which rows offer Retry vs Resume ──────────────────────

    @Test
    fun retryIsOfferedOnlyForFailedRows_resumeForPausedRows() {
        val failed = map(DownloadState.Status.FAILED)
        val cancelled = map(DownloadState.Status.CANCELLED)
        val paused = map(DownloadState.Status.PAUSED)
        assertEquals(DownloadStatus.STATUS_FAILED, failed)
        assertEquals(DownloadStatus.STATUS_FAILED, cancelled)
        assertEquals(DownloadStatus.STATUS_PAUSED, paused)
    }

    // ── Failure reasons ───────────────────────────────────────────────────

    @Test
    fun failureReason_isNullForNonFailedStatuses() {
        assertNull(PetalDownloadStatusMapper.failureReason(DownloadState.Status.DOWNLOADING, false, false, null))
        assertNull(PetalDownloadStatusMapper.failureReason(DownloadState.Status.COMPLETED, false, false, null))
        assertNull(PetalDownloadStatusMapper.failureReason(DownloadState.Status.PAUSED, false, false, null))
    }

    @Test
    fun failureReason_prefersLoggedReason_thenStorage_thenNetwork_thenGeneric() {
        val failed = DownloadState.Status.FAILED
        assertEquals("logged", PetalDownloadStatusMapper.failureReason(failed, offline = true, lowStorage = true, logged = "logged"))
        assertEquals(
            DownloadFailure.INSUFFICIENT_STORAGE.userMessage,
            PetalDownloadStatusMapper.failureReason(failed, offline = true, lowStorage = true, logged = null)
        )
        assertEquals(
            DownloadFailure.NO_NETWORK.userMessage,
            PetalDownloadStatusMapper.failureReason(failed, offline = true, lowStorage = false, logged = null)
        )
        assertNotNull(PetalDownloadStatusMapper.failureReason(failed, offline = false, lowStorage = false, logged = null))
    }

    @Test
    fun cancelled_reportsCancelled() {
        assertEquals("Cancelled", PetalDownloadStatusMapper.failureReason(DownloadState.Status.CANCELLED, false, false, null))
    }

    @Test
    fun everyStartFailure_hasAUserMessage() {
        DownloadFailure.values().forEach { assertTrue(it.userMessage.isNotBlank()) }
    }

    @Test
    fun failureLog_recordsAndClears() {
        val url = "https://example.org/private.zip"
        PetalDownloadFailureLog.record(url, "needs sign-in")
        assertEquals("needs sign-in", PetalDownloadFailureLog.reasonFor(url))
        PetalDownloadFailureLog.clear(url)
        assertNull(PetalDownloadFailureLog.reasonFor(url))
    }

    // ── Source tagging ────────────────────────────────────────────────────

    @Test
    fun source_defaultsToBrowserCore() {
        val item = DownloadItem(
            id = 1L, fileName = "a.zip", fileUrl = "https://example.org/a.zip", progress = null,
            status = DownloadStatus.STATUS_RUNNING, bytesDownloaded = 0L, totalSize = 0L, localUri = null
        )
        assertEquals(DownloadSource.HTTP_MOZILLA, item.source)
        assertNull(item.failureReason)
    }

    @Test
    fun source_canTagUpdateYtDlpAndBlobRows() {
        val base = DownloadItem(
            id = 1L, fileName = "x", fileUrl = "u", progress = null,
            status = DownloadStatus.STATUS_PENDING, bytesDownloaded = 0L, totalSize = 0L, localUri = null
        )
        assertEquals(DownloadSource.UPDATE, base.copy(source = DownloadSource.UPDATE).source)
        assertEquals(DownloadSource.YTDLP, base.copy(source = DownloadSource.YTDLP).source)
        assertEquals(DownloadSource.BLOB, base.copy(source = DownloadSource.BLOB).source)
    }

    @Test
    fun ownership_onlyMozillaBackedSourcesAreMozillaOwned() {
        assertTrue(DownloadSource.HTTP_MOZILLA.isMozillaOwned)
        assertTrue(DownloadSource.UPDATE.isMozillaOwned)
        assertFalse(DownloadSource.YTDLP.isMozillaOwned)
        assertFalse(DownloadSource.BLOB.isMozillaOwned)
        assertFalse(DownloadSource.TORRENT.isMozillaOwned)
        assertFalse(DownloadSource.EXTERNAL.isMozillaOwned)
    }

    @Test
    fun source_identifiersRoundTrip() {
        DownloadSource.values().forEach {
            assertEquals(it, DownloadSource.fromIdentifier(it.identifier))
        }
    }

    // ── Router URL validation ─────────────────────────────────────────────

    @Test
    fun router_keepsHttpAndHttpsUrls() {
        assertEquals("https://example.org/a.zip", PetalDownloadRouter.normalizeDownloadUrl("https://example.org/a.zip"))
        assertEquals("http://example.org/a.zip", PetalDownloadRouter.normalizeDownloadUrl("  http://example.org/a.zip "))
    }

    @Test
    fun router_addsHttpsToBareHosts() {
        assertEquals("https://example.org/a.zip", PetalDownloadRouter.normalizeDownloadUrl("example.org/a.zip"))
        assertEquals("https://localhost:8080/f.bin", PetalDownloadRouter.normalizeDownloadUrl("localhost:8080/f.bin"))
    }

    @Test
    fun router_blocksOtherSchemes() {
        listOf(
            "javascript:alert(1)",
            "file:///sdcard/secret.txt",
            "content://media/external/file/1",
            "intent://scan/#Intent;scheme=zxing;end",
            "magnet:?xt=urn:btih:abc",
            "ftp://example.org/a.zip",
            "data:text/plain;base64,AAAA"
        ).forEach { assertNull(it, PetalDownloadRouter.normalizeDownloadUrl(it)) }
    }

    @Test
    fun router_rejectsBlank() {
        assertNull(PetalDownloadRouter.normalizeDownloadUrl(null))
        assertNull(PetalDownloadRouter.normalizeDownloadUrl("   "))
    }
}
