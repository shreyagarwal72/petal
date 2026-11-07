package com.petal.browser.download

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Download Consolidation Stage A:
 * Cookies, Headers & Registry Persistence
 */
class DownloadStageATest {

    @Before
    fun setUp() {
        PetalGeckoCookieBridge.clearAllCookies()
        PetalDownloadHeaderRegistry.clearPrivateHeaders()
    }

    @Test
    fun testGeckoCookieBridge_StandardCookieRecordingAndLookup() {
        PetalGeckoCookieBridge.recordCookies("https://example.com/file.zip", "session=abc12345; auth=token987", isPrivate = false)

        // Exact match
        val cookies = PetalGeckoCookieBridge.getCookies("https://example.com/download/asset", isPrivate = false)
        assertNotNull(cookies)
        assertTrue(cookies!!.contains("session=abc12345"))

        // Subdomain / www match
        val wwwCookies = PetalGeckoCookieBridge.getCookies("https://www.example.com/asset", isPrivate = false)
        assertNotNull(wwwCookies)
        assertTrue(wwwCookies!!.contains("auth=token987"))

        // Unrelated host should return null
        val otherCookies = PetalGeckoCookieBridge.getCookies("https://different-domain.org/asset", isPrivate = false)
        assertNull(otherCookies)
    }

    @Test
    fun testGeckoCookieBridge_PrivateCookieIsolation() {
        // Record cookie in private session
        PetalGeckoCookieBridge.recordCookies("https://secure.example.com/private", "private_token=secret123", isPrivate = true)

        // Should be found in private lookup
        val privateLookup = PetalGeckoCookieBridge.getCookies("https://secure.example.com/data", isPrivate = true)
        assertEquals("private_token=secret123", privateLookup)

        // MUST NEVER be visible to standard session lookup
        val standardLookup = PetalGeckoCookieBridge.getCookies("https://secure.example.com/data", isPrivate = false)
        assertNull(standardLookup)

        // Clear private cookies
        PetalGeckoCookieBridge.clearPrivateCookies()
        assertNull(PetalGeckoCookieBridge.getCookies("https://secure.example.com/data", isPrivate = true))
    }

    @Test
    fun testHeaderRegistry_PutAndRetrieveByUrlAndId() {
        val testUrl = "https://cdn.example.org/archive.tar.gz"
        val testHeaders = mapOf(
            "Referer" to "https://example.org/downloads",
            "Authorization" to "Bearer xyz-secret"
        )
        val downloadId = "petal-dl-uuid-1001"

        PetalDownloadHeaderRegistry.put(
            url = testUrl,
            headers = testHeaders,
            downloadId = downloadId,
            isPrivate = false
        )

        // Lookup by URL
        val fromUrl = PetalDownloadHeaderRegistry.headersFor(url = testUrl, isPrivate = false)
        assertEquals(2, fromUrl.size)
        assertEquals("Bearer xyz-secret", fromUrl["Authorization"])

        // Lookup by downloadId
        val fromId = PetalDownloadHeaderRegistry.headersFor(url = "", downloadId = downloadId, isPrivate = false)
        assertEquals("https://example.org/downloads", fromId["Referer"])
    }

    @Test
    fun testHeaderRegistry_PrivateIsolation() {
        val testUrl = "https://private-cdn.org/confidential.pdf"
        val secretHeaders = mapOf(
            "Authorization" to "Bearer super-private-token"
        )
        val dlId = "private-dl-001"

        PetalDownloadHeaderRegistry.put(
            url = testUrl,
            headers = secretHeaders,
            downloadId = dlId,
            isPrivate = true
        )

        // Private lookup should find it
        val privateHeaders = PetalDownloadHeaderRegistry.headersFor(url = testUrl, downloadId = dlId, isPrivate = true)
        assertEquals("Bearer super-private-token", privateHeaders["Authorization"])

        // Standard lookup MUST NOT find private headers
        val standardHeaders = PetalDownloadHeaderRegistry.headersFor(url = testUrl, downloadId = dlId, isPrivate = false)
        assertTrue(standardHeaders.isEmpty())

        // Clearing private headers removes them
        PetalDownloadHeaderRegistry.clearPrivateHeaders()
        val afterClear = PetalDownloadHeaderRegistry.headersFor(url = testUrl, downloadId = dlId, isPrivate = true)
        assertTrue(afterClear.isEmpty())
    }
}
