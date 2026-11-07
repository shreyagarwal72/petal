package com.petal.browser.download

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Stage 1 Verification Suite:
 * Ensures all residual android.webkit.CookieManager call sites are completely
 * replaced with PetalGeckoCookieBridge, strictly adhering to Gecko session isolation,
 * private session data protection, and cross-component cookie retrieval.
 */
class Stage1CookieBridgeTest {

    @Before
    fun setUp() {
        PetalGeckoCookieBridge.clearAllCookies()
    }

    @Test
    fun testPrivateTabCookieIsolation_neverLeaksToStandardContext() {
        val targetUrl = "https://accounts.google.com/ServiceLogin"
        val privateCookie = "SSID=Ap8PrivateSecretToken; HSID=Hp7PrivateToken"
        val standardCookie = "SSID=StandardUserToken; HSID=StandardToken"

        // Store private session cookie
        PetalGeckoCookieBridge.recordCookies(targetUrl, privateCookie, isPrivate = true)
        // Store standard session cookie for same host
        PetalGeckoCookieBridge.recordCookies(targetUrl, standardCookie, isPrivate = false)

        // Private lookup returns private cookie
        val privateResult = PetalGeckoCookieBridge.getCookies(targetUrl, isPrivate = true)
        assertEquals(privateCookie, privateResult)

        // Standard lookup returns standard cookie
        val standardResult = PetalGeckoCookieBridge.getCookies(targetUrl, isPrivate = false)
        assertEquals(standardCookie, standardResult)

        // Purging private cookies removes ONLY private cookies
        PetalGeckoCookieBridge.clearPrivateCookies()
        assertNull(PetalGeckoCookieBridge.getCookies(targetUrl, isPrivate = true))
        assertEquals(standardCookie, PetalGeckoCookieBridge.getCookies(targetUrl, isPrivate = false))
    }

    @Test
    fun testHostNormalizationAndSubdomainMatching() {
        val baseSite = "https://example.org/path/resource"
        PetalGeckoCookieBridge.recordCookies(baseSite, "cf_clearance=abcdef123456", isPrivate = false)

        // Subdomain lookup
        val subResult = PetalGeckoCookieBridge.getCookies("https://static.example.org/image.png", isPrivate = false)
        assertNotNull(subResult)
        assertTrue(subResult!!.contains("cf_clearance=abcdef123456"))

        // Count
        assertEquals(1, PetalGeckoCookieBridge.getCookieCount(baseSite, isPrivate = false))
        assertTrue(PetalGeckoCookieBridge.hasCookies(baseSite, isPrivate = false))
    }

    @Test
    fun testClearCookiesForHost() {
        PetalGeckoCookieBridge.recordCookies("https://site-a.com/page", "auth=tokenA", isPrivate = false)
        PetalGeckoCookieBridge.recordCookies("https://site-b.com/page", "auth=tokenB", isPrivate = false)

        PetalGeckoCookieBridge.clearCookiesForHost("site-a.com")

        assertNull(PetalGeckoCookieBridge.getCookies("https://site-a.com/test", isPrivate = false))
        assertEquals("auth=tokenB", PetalGeckoCookieBridge.getCookies("https://site-b.com/test", isPrivate = false))
    }

    @Test
    fun testCookieCountParsingForSiteInfo() {
        val url = "https://portal.github.com/login"
        val cookieHeader = "logged_in=yes; dotcom_user=octocat; _gh_sess=xyz987"
        PetalGeckoCookieBridge.recordCookies(url, cookieHeader, isPrivate = false)

        val count = PetalGeckoCookieBridge.getCookieCount(url, isPrivate = false)
        assertEquals(3, count)
    }

    @Test
    fun testYtDlpCookieExtractionContract() {
        val videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val ytCookies = "LOGIN_INFO=AFmmF2kwRQ...; SID=AbCdEfGhIjKl; HSID=ZyXwVuTsRq"
        PetalGeckoCookieBridge.recordCookies(videoUrl, ytCookies, isPrivate = false)

        val retrieved = PetalGeckoCookieBridge.getCookies(videoUrl, isPrivate = false)
        assertNotNull(retrieved)

        val pairs = retrieved!!.split(";").map { it.trim() }
        assertEquals(3, pairs.size)
        assertTrue(pairs.any { it.startsWith("LOGIN_INFO=") })
        assertTrue(pairs.any { it.startsWith("SID=") })
        assertTrue(pairs.any { it.startsWith("HSID=") })
    }
}
