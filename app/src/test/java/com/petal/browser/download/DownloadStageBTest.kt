package com.petal.browser.download

import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Headers
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.Response
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

/**
 * Unit tests for Download Consolidation Stage B:
 * Fallback Safety & Private Mode Isolation
 */
class DownloadStageBTest {

    @Test
    fun testFallbackFetchClient_PrimarySuccessNeverCallsFallback() {
        val primaryMock: Client = mock()
        val fallbackMock: Client = mock()
        val dummyResponse = Response(url = "https://example.com/file.zip", status = 200, headers = Headers.EMPTY, body = Response.Body.empty())

        whenever(primaryMock.fetch(any())).thenReturn(dummyResponse)

        val client = PetalFallbackFetchClient(primary = primaryMock, fallback = fallbackMock)
        val request = Request(url = "https://example.com/file.zip", private = false)

        val response = client.fetch(request)
        assertEquals(200, response.status)

        verify(primaryMock, times(1)).fetch(request)
        verify(fallbackMock, never()).fetch(any())
    }

    @Test
    fun testFallbackFetchClient_PrimaryMidStreamFailureNeverFallsBackToOkHttp() {
        val primaryMock: Client = mock()
        val fallbackMock: Client = mock()

        whenever(primaryMock.fetch(any())).thenThrow(IOException("Connection reset by peer"))

        val client = PetalFallbackFetchClient(primary = primaryMock, fallback = fallbackMock)
        val request = Request(url = "https://example.com/large.iso", private = false)

        try {
            client.fetch(request)
            fail("Expected IOException on mid-stream failure")
        } catch (e: IOException) {
            assertEquals("Connection reset by peer", e.message)
        }

        // Must NEVER call fallback on mid-stream disconnect
        verify(fallbackMock, never()).fetch(any())
    }

    @Test
    fun testFallbackFetchClient_PrimaryNullPrivateRequestThrowsWithoutCallingFallback() {
        val fallbackMock: Client = mock()

        val client = PetalFallbackFetchClient(primary = null, fallback = fallbackMock)
        val privateRequest = Request(url = "https://example.com/confidential.pdf", private = true)

        try {
            client.fetch(privateRequest)
            fail("Expected IOException for private request when primary Gecko client is null")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("private download aborted") == true)
        }

        verify(fallbackMock, never()).fetch(any())
    }

    @Test
    fun testFallbackFetchClient_PrimaryNullNonPrivateRequestUsesFallback() {
        val fallbackMock: Client = mock()
        val fallbackResponse = Response(url = "https://example.com/public.zip", status = 200, headers = Headers.EMPTY, body = Response.Body.empty())

        whenever(fallbackMock.fetch(any())).thenReturn(fallbackResponse)

        val client = PetalFallbackFetchClient(primary = null, fallback = fallbackMock)
        val publicRequest = Request(url = "https://example.com/public.zip", private = false)

        val response = client.fetch(publicRequest)
        assertEquals(200, response.status)
        verify(fallbackMock, times(1)).fetch(publicRequest)
    }

    @Test
    fun testOkHttpFetchClient_RejectsPrivateRequests() {
        val okHttpClient = PetalOkHttpFetchClient()
        val privateRequest = Request(url = "https://example.com/secret.pdf", private = true)

        try {
            okHttpClient.fetch(privateRequest)
            fail("Expected PetalOkHttpFetchClient to reject private requests")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("PetalOkHttpFetchClient cannot be used for private requests") == true)
        }
    }
}
