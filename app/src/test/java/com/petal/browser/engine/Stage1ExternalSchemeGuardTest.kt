package com.petal.browser.engine

import com.petal.browser.engine.gecko.PetalEngineBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 1 Verification Suite – External Scheme Guard
 *
 * Directly tests [PetalEngineBridge.INTERNAL_SCHEMES], [PetalEngineBridge.isInternalScheme],
 * and [PetalEngineBridge.isExternalScheme] against all 15 internal schemes, external schemes,
 * casing variations, and edge cases (null, empty, whitespace).
 *
 * Ensures web navigations (http, https, about, petal, data, blob, file, javascript, view-source,
 * resource, moz-extension, content, ws, wss, ftp) remain inside Petal Browser and never fire
 * an external ACTION_VIEW intent to another browser.
 */
class Stage1ExternalSchemeGuardTest {

    // ---------------------------------------------------------------------------
    // Internal Schemes List Verification (Contract Parity)
    // ---------------------------------------------------------------------------

    @Test
    fun internalSchemes_containsExactly15RequiredSchemes() {
        val expectedSchemes = listOf(
            "http:", "https:", "about:", "petal:", "data:", "blob:", "file:",
            "javascript:", "view-source:", "resource:", "moz-extension:",
            "content:", "ws:", "wss:", "ftp:"
        )
        assertEquals(15, PetalEngineBridge.INTERNAL_SCHEMES.size)
        assertEquals(expectedSchemes, PetalEngineBridge.INTERNAL_SCHEMES)
    }

    @Test
    fun internalSchemes_doesNotContainExternalSchemes() {
        assertFalse(PetalEngineBridge.INTERNAL_SCHEMES.contains("mailto:"))
        assertFalse(PetalEngineBridge.INTERNAL_SCHEMES.contains("tel:"))
        assertFalse(PetalEngineBridge.INTERNAL_SCHEMES.contains("market:"))
        assertFalse(PetalEngineBridge.INTERNAL_SCHEMES.contains("intent:"))
        assertFalse(PetalEngineBridge.INTERNAL_SCHEMES.contains("sms:"))
        assertFalse(PetalEngineBridge.INTERNAL_SCHEMES.contains("geo:"))
    }

    // ---------------------------------------------------------------------------
    // All 15 Internal / Web Schemes — Must return true for isInternalScheme
    // and false for isExternalScheme (Browser handles these internally)
    // ---------------------------------------------------------------------------

    @Test
    fun http_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("http://example.com"))
        assertFalse(PetalEngineBridge.isExternalScheme("http://example.com"))
    }

    @Test
    fun https_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("https://example.com/path?q=1"))
        assertFalse(PetalEngineBridge.isExternalScheme("https://example.com/path?q=1"))
    }

    @Test
    fun about_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("about:blank"))
        assertFalse(PetalEngineBridge.isExternalScheme("about:blank"))
    }

    @Test
    fun about_home_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("about:home"))
        assertFalse(PetalEngineBridge.isExternalScheme("about:home"))
    }

    @Test
    fun petal_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("petal://newtab"))
        assertFalse(PetalEngineBridge.isExternalScheme("petal://newtab"))
    }

    @Test
    fun data_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("data:text/html,<h1>Hi</h1>"))
        assertFalse(PetalEngineBridge.isExternalScheme("data:text/html,<h1>Hi</h1>"))
    }

    @Test
    fun blob_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("blob:https://example.com/uuid-1234"))
        assertFalse(PetalEngineBridge.isExternalScheme("blob:https://example.com/uuid-1234"))
    }

    @Test
    fun file_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("file:///sdcard/index.html"))
        assertFalse(PetalEngineBridge.isExternalScheme("file:///sdcard/index.html"))
    }

    @Test
    fun javascript_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("javascript:void(0)"))
        assertFalse(PetalEngineBridge.isExternalScheme("javascript:void(0)"))
    }

    @Test
    fun view_source_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("view-source:https://example.com"))
        assertFalse(PetalEngineBridge.isExternalScheme("view-source:https://example.com"))
    }

    @Test
    fun resource_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("resource://gre/res/EditorOverride.css"))
        assertFalse(PetalEngineBridge.isExternalScheme("resource://gre/res/EditorOverride.css"))
    }

    @Test
    fun moz_extension_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("moz-extension://abc123/background.html"))
        assertFalse(PetalEngineBridge.isExternalScheme("moz-extension://abc123/background.html"))
    }

    @Test
    fun content_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("content://com.android.contacts/contacts"))
        assertFalse(PetalEngineBridge.isExternalScheme("content://com.android.contacts/contacts"))
    }

    @Test
    fun ws_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("ws://localhost:8080/socket"))
        assertFalse(PetalEngineBridge.isExternalScheme("ws://localhost:8080/socket"))
    }

    @Test
    fun wss_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("wss://echo.websocket.org"))
        assertFalse(PetalEngineBridge.isExternalScheme("wss://echo.websocket.org"))
    }

    @Test
    fun ftp_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("ftp://ftp.example.com/file.txt"))
        assertFalse(PetalEngineBridge.isExternalScheme("ftp://ftp.example.com/file.txt"))
    }

    // ---------------------------------------------------------------------------
    // Case Insensitivity — Upper, lower, and mixed case
    // ---------------------------------------------------------------------------

    @Test
    fun http_uppercase_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("HTTP://EXAMPLE.COM"))
        assertFalse(PetalEngineBridge.isExternalScheme("HTTP://EXAMPLE.COM"))
    }

    @Test
    fun https_mixedCase_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("Https://secure.example.com"))
        assertFalse(PetalEngineBridge.isExternalScheme("Https://secure.example.com"))
    }

    @Test
    fun about_mixedCase_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("ABOUT:blank"))
        assertFalse(PetalEngineBridge.isExternalScheme("ABOUT:blank"))
        assertTrue(PetalEngineBridge.isInternalScheme("About:Home"))
        assertFalse(PetalEngineBridge.isExternalScheme("About:Home"))
    }

    @Test
    fun petal_uppercase_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("PETAL://BOOKMARKS"))
        assertFalse(PetalEngineBridge.isExternalScheme("PETAL://BOOKMARKS"))
    }

    @Test
    fun data_uppercase_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("DATA:text/plain;charset=utf-8,Hello"))
        assertFalse(PetalEngineBridge.isExternalScheme("DATA:text/plain;charset=utf-8,Hello"))
    }

    @Test
    fun blob_uppercase_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("BLOB:https://example.com/xyz"))
        assertFalse(PetalEngineBridge.isExternalScheme("BLOB:https://example.com/xyz"))
    }

    @Test
    fun file_mixedCase_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("File:///sdcard/document.pdf"))
        assertFalse(PetalEngineBridge.isExternalScheme("File:///sdcard/document.pdf"))
    }

    @Test
    fun javascript_mixedCase_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("JavaScript:window.close()"))
        assertFalse(PetalEngineBridge.isExternalScheme("JavaScript:window.close()"))
    }

    @Test
    fun view_source_mixedCase_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("VIEW-SOURCE:https://example.com"))
        assertFalse(PetalEngineBridge.isExternalScheme("VIEW-SOURCE:https://example.com"))
    }

    @Test
    fun resource_uppercase_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("RESOURCE://gre/res/test.css"))
        assertFalse(PetalEngineBridge.isExternalScheme("RESOURCE://gre/res/test.css"))
    }

    @Test
    fun moz_extension_mixedCase_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("Moz-Extension://uuid/popup.html"))
        assertFalse(PetalEngineBridge.isExternalScheme("Moz-Extension://uuid/popup.html"))
    }

    @Test
    fun content_uppercase_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("CONTENT://media/external/images"))
        assertFalse(PetalEngineBridge.isExternalScheme("CONTENT://media/external/images"))
    }

    @Test
    fun ws_wss_mixedCase_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("Ws://echo.websocket.events"))
        assertFalse(PetalEngineBridge.isExternalScheme("Ws://echo.websocket.events"))
        assertTrue(PetalEngineBridge.isInternalScheme("WSS://secure.websocket.events"))
        assertFalse(PetalEngineBridge.isExternalScheme("WSS://secure.websocket.events"))
    }

    @Test
    fun ftp_uppercase_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("FTP://FTP.EXAMPLE.COM/PUB"))
        assertFalse(PetalEngineBridge.isExternalScheme("FTP://FTP.EXAMPLE.COM/PUB"))
    }

    // ---------------------------------------------------------------------------
    // Null, Empty String, and Whitespace Handling
    // ---------------------------------------------------------------------------

    @Test
    fun null_url_isNeitherInternalNorExternal() {
        assertFalse(PetalEngineBridge.isInternalScheme(null))
        assertFalse(PetalEngineBridge.isExternalScheme(null))
    }

    @Test
    fun emptyString_url_isNeitherInternalNorExternal() {
        assertFalse(PetalEngineBridge.isInternalScheme(""))
        assertFalse(PetalEngineBridge.isExternalScheme(""))
    }

    @Test
    fun whitespaceOnly_url_isNeitherInternalNorExternal() {
        assertFalse(PetalEngineBridge.isInternalScheme("   "))
        assertFalse(PetalEngineBridge.isExternalScheme("   "))
        assertFalse(PetalEngineBridge.isInternalScheme("\t\n\r  "))
        assertFalse(PetalEngineBridge.isExternalScheme("\t\n\r  "))
    }

    @Test
    fun paddedWhitespace_internalUrl_isInternal() {
        assertTrue(PetalEngineBridge.isInternalScheme("   https://example.com   "))
        assertFalse(PetalEngineBridge.isExternalScheme("   https://example.com   "))
        assertTrue(PetalEngineBridge.isInternalScheme("\t http://localhost:3000\n"))
        assertFalse(PetalEngineBridge.isExternalScheme("\t http://localhost:3000\n"))
    }

    @Test
    fun paddedWhitespace_externalUrl_isExternal() {
        assertFalse(PetalEngineBridge.isInternalScheme("   mailto:test@example.com   "))
        assertTrue(PetalEngineBridge.isExternalScheme("   mailto:test@example.com   "))
        assertFalse(PetalEngineBridge.isInternalScheme("\t tel:+1234567890 \n"))
        assertTrue(PetalEngineBridge.isExternalScheme("\t tel:+1234567890 \n"))
    }

    // ---------------------------------------------------------------------------
    // External Schemes — Must return false for isInternalScheme and true for isExternalScheme
    // (Android dispatches these to external applications)
    // ---------------------------------------------------------------------------

    @Test
    fun mailto_isExternal() {
        assertFalse(PetalEngineBridge.isInternalScheme("mailto:user@example.com"))
        assertTrue(PetalEngineBridge.isExternalScheme("mailto:user@example.com"))
    }

    @Test
    fun tel_isExternal() {
        assertFalse(PetalEngineBridge.isInternalScheme("tel:+15551234567"))
        assertTrue(PetalEngineBridge.isExternalScheme("tel:+15551234567"))
    }

    @Test
    fun market_isExternal() {
        assertFalse(PetalEngineBridge.isInternalScheme("market://details?id=com.example.app"))
        assertTrue(PetalEngineBridge.isExternalScheme("market://details?id=com.example.app"))
    }

    @Test
    fun intent_isExternal() {
        assertFalse(PetalEngineBridge.isInternalScheme("intent://scan/#Intent;scheme=zxing;end"))
        assertTrue(PetalEngineBridge.isExternalScheme("intent://scan/#Intent;scheme=zxing;end"))
    }

    @Test
    fun sms_isExternal() {
        assertFalse(PetalEngineBridge.isInternalScheme("sms:+15551234567"))
        assertTrue(PetalEngineBridge.isExternalScheme("sms:+15551234567"))
    }

    @Test
    fun geo_isExternal() {
        assertFalse(PetalEngineBridge.isInternalScheme("geo:37.786971,-122.399677"))
        assertTrue(PetalEngineBridge.isExternalScheme("geo:37.786971,-122.399677"))
    }

    @Test
    fun facetime_isExternal() {
        assertFalse(PetalEngineBridge.isInternalScheme("facetime:user@example.com"))
        assertTrue(PetalEngineBridge.isExternalScheme("facetime:user@example.com"))
    }

    @Test
    fun thirdPartyApps_areExternal() {
        assertFalse(PetalEngineBridge.isInternalScheme("whatsapp://send?text=Hello"))
        assertTrue(PetalEngineBridge.isExternalScheme("whatsapp://send?text=Hello"))

        assertFalse(PetalEngineBridge.isInternalScheme("tg://resolve?domain=petal"))
        assertTrue(PetalEngineBridge.isExternalScheme("tg://resolve?domain=petal"))

        assertFalse(PetalEngineBridge.isInternalScheme("alipays://platformapi/startapp"))
        assertTrue(PetalEngineBridge.isExternalScheme("alipays://platformapi/startapp"))

        assertFalse(PetalEngineBridge.isInternalScheme("weixin://dl/chat"))
        assertTrue(PetalEngineBridge.isExternalScheme("weixin://dl/chat"))
    }

    @Test
    fun externalSchemes_caseInsensitivity() {
        assertFalse(PetalEngineBridge.isInternalScheme("MAILTO:user@example.com"))
        assertTrue(PetalEngineBridge.isExternalScheme("MAILTO:user@example.com"))

        assertFalse(PetalEngineBridge.isInternalScheme("Tel:+15551234567"))
        assertTrue(PetalEngineBridge.isExternalScheme("Tel:+15551234567"))

        assertFalse(PetalEngineBridge.isInternalScheme("INTENT://scan/#Intent;scheme=zxing;end"))
        assertTrue(PetalEngineBridge.isExternalScheme("INTENT://scan/#Intent;scheme=zxing;end"))

        assertFalse(PetalEngineBridge.isInternalScheme("Market://details?id=com.example.app"))
        assertTrue(PetalEngineBridge.isExternalScheme("Market://details?id=com.example.app"))
    }
}
