package com.petal.browser.download

import mozilla.components.concept.fetch.Request
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Download Consolidation Stage E:
 * Quality, Reliability & Hardening
 */
class DownloadStageETest {

    @Test
    fun testSmartDownloadRouter_Categorization() {
        assertEquals(PetalSmartDownloadRouter.Category.IMAGES, PetalSmartDownloadRouter.getCategory("photo.jpg", null))
        assertEquals(PetalSmartDownloadRouter.Category.VIDEO, PetalSmartDownloadRouter.getCategory("movie.mp4", null))
        assertEquals(PetalSmartDownloadRouter.Category.AUDIO, PetalSmartDownloadRouter.getCategory("song.mp3", null))
        assertEquals(PetalSmartDownloadRouter.Category.DOCUMENTS, PetalSmartDownloadRouter.getCategory("manual.pdf", null))
        assertEquals(PetalSmartDownloadRouter.Category.PACKAGES, PetalSmartDownloadRouter.getCategory("app.apk", null))
        assertEquals(PetalSmartDownloadRouter.Category.GENERAL, PetalSmartDownloadRouter.getCategory("archive.dat", null))
    }

    @Test
    fun testSmartDownloadRouter_RelativePathGeneration() {
        val relPath = PetalSmartDownloadRouter.getRelativePath("avatar.png", "image/png")
        assertEquals("Download/Petal/Images/", relPath)

        val docPath = PetalSmartDownloadRouter.getRelativePath("report.pdf", "application/pdf")
        assertEquals("Download/Petal/Documents/", docPath)
    }

    @Test
    fun testRangeHeaders_InjectsRangeAndIfRange() {
        val request = Request(url = "https://example.com/asset.bin")
        val headers = PetalParallelFetchClient.rangeHeadersFor(
            request = request,
            from = 0L,
            to = 1048575L,
            etag = "\"etag-val-123\""
        )

        assertEquals("bytes=0-1048575", headers.get("Range"))
        assertEquals("\"etag-val-123\"", headers.get("If-Range"))
    }
}
