package com.petal.browser.unit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class PetalOmniboxUrlAndSearchTest {

    // Mirror of PetalBrowserUtils DOMAIN_OR_IP_PATTERN
    private val domainOrIpPattern = Pattern.compile(
        "^((https?|ftp|file|content|about|petal|javascript)://)?" +
                "((([a-zA-Z0-9_-]+\\.)+[a-zA-Z]{2,})|" + // Standard FQDN (e.g. example.com)
                "(localhost)|" + // Localhost
                "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}))" + // IPv4 address
                "(:\\d{1,5})?" + // Optional port number (e.g. :8080)
                "(/.*)?$", // Optional path & query
        Pattern.CASE_INSENSITIVE
    )

    private fun isUrlCandidate(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true) ||
            trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true) ||
            trimmed.startsWith("petal://", ignoreCase = true) ||
            trimmed.startsWith("javascript:", ignoreCase = true)
        ) return true
        if (trimmed.contains(" ") || trimmed.contains("\n") || trimmed.contains("\t")) return false
        return domainOrIpPattern.matcher(trimmed).matches()
    }

    private fun wrapQuery(raw: String, searchEngineUrl: String = "https://www.google.com/search?q=%s"): String {
        val trimmed = raw.trim()
        if (isUrlCandidate(trimmed)) {
            return when {
                trimmed.contains("://") -> trimmed
                trimmed.startsWith("about:", ignoreCase = true) || trimmed.startsWith("javascript:", ignoreCase = true) -> trimmed
                trimmed.startsWith("localhost", ignoreCase = true) || trimmed.startsWith("127.0.0.1") -> "http://$trimmed"
                else -> "https://$trimmed"
            }
        }
        return searchEngineUrl.replace("%s", trimmed)
    }

    @Test
    fun testIsUrlDetection() {
        // Standard FQDNs
        assertTrue(isUrlCandidate("google.com"))
        assertTrue(isUrlCandidate("www.google.com"))
        assertTrue(isUrlCandidate("sub.domain.co.uk"))
        assertTrue(isUrlCandidate("https://github.com"))
        assertTrue(isUrlCandidate("http://news.ycombinator.com/item?id=123"))

        // Ports on domains
        assertTrue(isUrlCandidate("example.com:8080"))
        assertTrue(isUrlCandidate("http://example.com:8080/api/v1"))

        // Localhost and Ports
        assertTrue(isUrlCandidate("localhost"))
        assertTrue(isUrlCandidate("localhost:3000"))
        assertTrue(isUrlCandidate("localhost:8080/dashboard"))
        assertTrue(isUrlCandidate("http://localhost:5173"))

        // IPv4 Addresses and Ports
        assertTrue(isUrlCandidate("127.0.0.1"))
        assertTrue(isUrlCandidate("127.0.0.1:8000"))
        assertTrue(isUrlCandidate("192.168.1.1"))
        assertTrue(isUrlCandidate("192.168.1.1:8080"))
        assertTrue(isUrlCandidate("10.0.0.1/admin"))

        // Custom Petal, about and internal schemes
        assertTrue(isUrlCandidate("petal://bookmarks"))
        assertTrue(isUrlCandidate("petal://history"))
        assertTrue(isUrlCandidate("about:blank"))
        assertTrue(isUrlCandidate("about:home"))
        assertTrue(isUrlCandidate("javascript:void(0)"))
        assertTrue(isUrlCandidate("file:///sdcard/Download/test.html"))
        assertTrue(isUrlCandidate("content://media/external/images/media/1"))
    }

    @Test
    fun testNonUrlDetection() {
        assertFalse(isUrlCandidate(""))
        assertFalse(isUrlCandidate("   "))
        assertFalse(isUrlCandidate("how to make pasta"))
        assertFalse(isUrlCandidate("kotlin coroutines vs rxjava"))
        assertFalse(isUrlCandidate("justaword"))
        assertFalse(isUrlCandidate("word1 word2"))
        assertFalse(isUrlCandidate("something.with.spaces in it"))
        assertFalse(isUrlCandidate("123456"))
    }

    @Test
    fun testQueryWrapper() {
        // Localhost should default to http://
        assertEquals("http://localhost", wrapQuery("localhost"))
        assertEquals("http://localhost:3000", wrapQuery("localhost:3000"))
        assertEquals("http://127.0.0.1:8080", wrapQuery("127.0.0.1:8080"))

        // Standard domains should default to https://
        assertEquals("https://example.com", wrapQuery("example.com"))
        assertEquals("https://example.com:8080", wrapQuery("example.com:8080"))
        assertEquals("https://192.168.1.1", wrapQuery("192.168.1.1"))

        // Explicit schemes should be preserved
        assertEquals("https://github.com", wrapQuery("https://github.com"))
        assertEquals("http://insecure.site", wrapQuery("http://insecure.site"))
        assertEquals("petal://bookmarks", wrapQuery("petal://bookmarks"))
        assertEquals("about:blank", wrapQuery("about:blank"))

        // Search queries should be mapped to the search engine
        assertEquals("https://www.google.com/search?q=kotlin+android", wrapQuery("kotlin+android"))
    }

    @Test
    fun testMultiWordFallbackCondition() {
        val query1 = "single"
        val words1 = query1.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        assertFalse("Single word should not trigger multi-word fallback", words1.size >= 2)

        val query2 = "hello world"
        val words2 = query2.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        assertTrue("Two-word query should trigger multi-word fallback", words2.size >= 2)

        val query3 = "fast modern web browser"
        val words3 = query3.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        assertTrue("Multi-word query should trigger multi-word fallback", words3.size >= 2)
    }

    @Test
    fun testCustomSearchEngineUrlQueryAppend() {
        val query = "kotlin"
        val encodedQuery = "kotlin"

        // Template with %s
        val template1 = "https://example.com/search?q=%s"
        val url1 = if (template1.contains("%s")) template1.replace("%s", encodedQuery) else "$template1$encodedQuery"
        assertEquals("https://example.com/search?q=kotlin", url1)

        // Template with {searchTerms}
        val template2 = "https://example.com/search?q={searchTerms}"
        val url2 = if (template2.contains("{searchTerms}")) template2.replace("{searchTerms}", encodedQuery) else "$template2$encodedQuery"
        assertEquals("https://example.com/search?q=kotlin", url2)

        // Template missing placeholder with trailing query param
        val template3 = "https://example.com/search?q="
        val url3 = if (template3.contains("%s")) template3.replace("%s", encodedQuery)
        else if (template3.endsWith("=") || template3.endsWith("/")) "$template3$encodedQuery"
        else if (template3.contains("?")) "$template3&$encodedQuery"
        else "$template3?q=$encodedQuery"
        assertEquals("https://example.com/search?q=kotlin", url3)

        // Template missing placeholder without query param
        val template4 = "https://example.com/search"
        val url4 = if (template4.contains("%s")) template4.replace("%s", encodedQuery)
        else if (template4.endsWith("=") || template4.endsWith("/")) "$template4$encodedQuery"
        else if (template4.contains("?")) "$template4&$encodedQuery"
        else "$template4?q=$encodedQuery"
        assertEquals("https://example.com/search?q=kotlin", url4)
    }
}
