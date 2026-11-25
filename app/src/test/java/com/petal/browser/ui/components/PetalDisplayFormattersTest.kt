/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetalDisplayFormattersTest {

    @Test
    fun looksLikeFileName_distinguishesFilesFromHostnamesAndTitles() {
        assertTrue(looksLikeFileName("petal-browser-arm64-v8a-release(1).apk"))
        assertTrue(looksLikeFileName("archive-build.tar.gz"))
        assertFalse(looksLikeFileName("github.com"))
        assertFalse(looksLikeFileName("objects.githubusercontent.com"))
        assertFalse(looksLikeFileName("Download Petal Browser v4.9 Release Notes"))
    }

    @Test
    fun formatMiddleEllipsisFileName_keepsApkExtensionVisible() {
        val raw = "petal-browser-arm64-v8a-release(1).apk"
        val formatted = formatMiddleEllipsisFileName(raw, maxChars = 30)
        assertTrue(formatted.length <= 30)
        assertTrue(formatted.startsWith("petal-browser-"))
        assertTrue(formatted.contains("\u2026"))
        assertTrue(formatted.endsWith(".apk"))
    }

    @Test
    fun formatMiddleEllipsisFileName_keepsCompoundArchiveExtension() {
        val raw = "very-long-release-artifact-name-v4.9.0.tar.gz"
        val formatted = formatMiddleEllipsisFileName(raw, maxChars = 30)
        assertTrue(formatted.length <= 30)
        assertTrue(formatted.contains("\u2026"))
        assertTrue(formatted.endsWith(".tar.gz"))
    }

    @Test
    fun formatDisplayUrl_stripsQueryAndCapsPathForSignedGithubAssetUrl() {
        val signedUrl = "https://objects.githubusercontent.com/github-production-release-asset-2e65be/123456/petal-browser-arm64-v8a-release.apk?X-Amz-Algorithm=AWS4-HMAC-SHA256&sp=r&sv=2021-08-06&sig=" +
            "a".repeat(900)
        val display = formatDisplayUrl(signedUrl)
        assertFalse(display.contains("?"))
        assertFalse(display.contains("sig="))
        assertTrue(display.startsWith("objects.githubusercontent.com/"))
        assertTrue(display.endsWith("\u2026"))
        assertTrue(display.length <= 60)
    }

    @Test
    fun formatDisplayUrl_stripsGoogleRedirectQuery() {
        val url = "https://www.google.com/url?sa=t&source=web&rct=j&url=https%3A%2F%2Fexample.com&jwt=eyJhbGciOiJIUzI1NiJ9"
        assertEquals("google.com/url", formatDisplayUrl(url))
    }

    @Test
    fun formatDisplayUrl_handlesDataAndBareDomainsSafely() {
        assertEquals("github.com", formatDisplayUrl("https://www.github.com/"))
        assertTrue(formatDisplayUrl("data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==").startsWith("data:image/png;base64"))
    }
}
