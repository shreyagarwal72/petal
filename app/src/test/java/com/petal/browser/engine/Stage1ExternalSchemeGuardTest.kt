package com.petal.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 1 Verification Suite – External Scheme Guard
 *
 * Verifies the logic of [com.petal.browser.engine.gecko.PetalEngineBridge.handleExternalScheme]
 * WITHOUT Android framework dependencies (Activity, PackageManager).
 *
 * Because [handleExternalScheme] requires a real [Activity] to call [startActivity], we extract
 * and unit-test the pure "is this an internal scheme?" predicate inline here. This keeps the test
 * hermetic (no Robolectric / instrumented APK needed) while still exercising the exact guard logic
 * copied verbatim from PetalEngineBridge.kt lines 112-117.
 */
class Stage1ExternalSchemeGuardTest {

    // ---------------------------------------------------------------------------
    // Mirror of the guard from PetalEngineBridge.handleExternalScheme (lines 112–117)
    // ---------------------------------------------------------------------------
    private val internalSchemes = listOf(
        "http:", "https:", "about:", "petal:", "data:", "blob:", "file:",
        "javascript:", "view-source:", "resource:", "moz-extension:",
        "content:", "ws:", "wss:", "ftp:"
    )

    /**
     * Returns false if the url starts with an internal scheme (Petal handles it),
     * true if the url should be dispatched externally.
     * Mirrors the predicate in PetalEngineBridge exactly.
     */
    private fun isExternal(url: String?): Boolean {
        if (url == null) return false
        val lower = url.lowercase()
        if (internalSchemes.any { lower.startsWith(it) }) return false
        // All remaining schemes are external (mailto, tel, market, intent, …)
        return true
    }

    // ---------------------------------------------------------------------------
    // Internal / web schemes — must return false (browser handles these itself)
    // ---------------------------------------------------------------------------

    @Test fun http_isInternal() = assertFalse(isExternal("http://example.com"))
    @Test fun https_isInternal() = assertFalse(isExternal("https://example.com/path?q=1"))
    @Test fun about_isInternal() = assertFalse(isExternal("about:blank"))
    @Test fun about_home_isInternal() = assertFalse(isExternal("about:home"))
    @Test fun petal_isInternal() = assertFalse(isExternal("petal://newtab"))
    @Test fun data_isInternal() = assertFalse(isExternal("data:text/html,<h1>Hi</h1>"))
    @Test fun blob_isInternal() = assertFalse(isExternal("blob:https://example.com/uuid"))
    @Test fun file_isInternal() = assertFalse(isExternal("file:///sdcard/index.html"))
    @Test fun javascript_isInternal() = assertFalse(isExternal("javascript:void(0)"))
    @Test fun view_source_isInternal() = assertFalse(isExternal("view-source:https://example.com"))
    @Test fun resource_isInternal() = assertFalse(isExternal("resource://gre/res/EditorOverride.css"))
    @Test fun moz_extension_isInternal() = assertFalse(isExternal("moz-extension://abc123/background.html"))
    @Test fun content_isInternal() = assertFalse(isExternal("content://com.android.contacts/contacts"))
    @Test fun ws_isInternal() = assertFalse(isExternal("ws://localhost:8080/socket"))
    @Test fun wss_isInternal() = assertFalse(isExternal("wss://echo.websocket.org"))
    @Test fun ftp_isInternal() = assertFalse(isExternal("ftp://ftp.example.com/file.txt"))

    // Null safety
    @Test fun null_url_isNotExternal() = assertFalse(isExternal(null))

    // Case-insensitivity: guard uses lowercase() so HTTP:// must still be blocked
    @Test fun http_uppercase_isInternal() = assertFalse(isExternal("HTTP://EXAMPLE.COM"))
    @Test fun https_mixed_case_isInternal() = assertFalse(isExternal("HTTPS://secure.example.com"))

    // ---------------------------------------------------------------------------
    // External schemes — must return true (Android dispatches these to other apps)
    // ---------------------------------------------------------------------------

    @Test fun mailto_isExternal() = assertTrue(isExternal("mailto:user@example.com"))
    @Test fun tel_isExternal() = assertTrue(isExternal("tel:+15551234567"))
    @Test fun market_isExternal() = assertTrue(isExternal("market://details?id=com.example.app"))
    @Test fun intent_isExternal() = assertTrue(isExternal("intent://scan/#Intent;scheme=zxing;end"))

    // Additional external schemes that should open other apps
    @Test fun sms_isExternal() = assertTrue(isExternal("sms:+15551234567"))
    @Test fun geo_isExternal() = assertTrue(isExternal("geo:37.786971,-122.399677"))
    @Test fun facetime_isExternal() = assertTrue(isExternal("facetime:user@example.com"))
}
