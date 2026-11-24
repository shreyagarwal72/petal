package com.petal.browser.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlDisplayTest {
    @Test fun stripsWww_andKeepsPath() {
        val p = UrlDisplay.split("https://www.example.com/a/b?q=1", "www.example.com")
        assertEquals("example.com", p.host); assertEquals("/a/b?q=1", p.rest); assertTrue(p.isHttps)
    }

    @Test fun textBeforeAtIsNeverTheDomain() {
        val p = UrlDisplay.split("https://google.com@evil.test/login", "evil.test")
        assertEquals("evil.test", p.host); assertEquals("google.com@", p.userInfo)
    }

    @Test fun flagsPunycode() {
        assertTrue(UrlDisplay.split("https://xn--pple-43d.com/", "xn--pple-43d.com").isPunycode)
        assertFalse(UrlDisplay.split("https://apple.com/", "apple.com").isPunycode)
    }

    @Test fun httpIsNotHttps() {
        val p = UrlDisplay.split("http://example.com:8080/x", "example.com")
        assertTrue(p.isHttp); assertFalse(p.isHttps); assertEquals(":8080/x", p.rest)
    }
}
