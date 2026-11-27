package com.petal.browser.unit

import android.content.SharedPreferences
import com.petal.browser.privacy.ForgetfulBrowsingManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class TabSessionAndCacheAuditTest {

    // ── Clear-on-exit & quick-clear preference flag mapping ──────────────────

    private fun mockClearPrefs(
        cache: Boolean = false,
        cookie: Boolean = false,
        indexedDb: Boolean = false,
        permissions: Boolean = false,
        autofill: Boolean = false,
    ): SharedPreferences {
        val sp = mock(SharedPreferences::class.java)
        `when`(sp.getBoolean("sp_clear_cache", false)).thenReturn(cache)
        `when`(sp.getBoolean("sp_clear_cookie", false)).thenReturn(cookie)
        `when`(sp.getBoolean("sp_clearIndexedDB", false)).thenReturn(indexedDb)
        `when`(sp.getBoolean("sp_clear_permissions", false)).thenReturn(permissions)
        `when`(sp.getBoolean("sp_clear_autofill", false)).thenReturn(autofill)
        return sp
    }

    @Test
    fun `clearCacheFlagsForPrefs returns zero when no website or cache categories enabled`() {
        val sp = mockClearPrefs()
        assertEquals(0L, PetalBrowserUtils.clearCacheFlagsForPrefs(sp))
    }

    @Test
    fun `clearCacheFlagsForPrefs maps each selected checkbox to matching PetalCacheManager flags`() {
        val sp = mockClearPrefs(
            cache = true,
            cookie = true,
            indexedDb = true,
            permissions = true,
            autofill = true,
        )
        val expected = PetalCacheManager.Flags.ALL_CACHES or
            PetalCacheManager.Flags.COOKIES or
            PetalCacheManager.Flags.DOM_WEB_STORAGE or
            PetalCacheManager.Flags.PERMISSIONS or
            PetalCacheManager.Flags.AUTH_SESSIONS
        assertEquals(expected, PetalBrowserUtils.clearCacheFlagsForPrefs(sp))
    }

    @Test
    fun `clearCacheFlagsForPrefs includes DOM storage and cookies when indexedDb flag is enabled`() {
        val sp = mockClearPrefs(indexedDb = true)
        val expected = PetalCacheManager.Flags.DOM_WEB_STORAGE or PetalCacheManager.Flags.COOKIES
        assertEquals(expected, PetalBrowserUtils.clearCacheFlagsForPrefs(sp))
    }

    // ── Forgetful Browsing domain close detection ────────────────────────────

    @Test
    fun `hostOf extracts normalized host and ignores internal schemes`() {
        assertEquals("example.com", ForgetfulBrowsingManager.hostOf("https://Example.COM/path?q=1"))
        assertEquals("sub.site.org", ForgetfulBrowsingManager.hostOf("http://sub.site.org:8080/x"))
        assertNull(ForgetfulBrowsingManager.hostOf("about:home"))
        assertNull(ForgetfulBrowsingManager.hostOf("   "))
        assertNull(ForgetfulBrowsingManager.hostOf(null))
    }

    @Test
    fun `domainsNoLongerOpen emits forgetful host only after last open tab leaves domain`() {
        val closed = listOf("https://a.example.com/1", "https://b.example.com/2", "https://c.example.com/3")
        val remaining = listOf("https://b.example.com/other", "https://d.example.com/4")
        val forgetful = setOf("a.example.com", "b.example.com")

        val toPurge = ForgetfulBrowsingManager.domainsNoLongerOpen(
            closedUrls = closed,
            remainingUrls = remaining,
            isForgetful = { it in forgetful }
        )
        assertEquals(listOf("a.example.com"), toPurge)
    }

    @Test
    fun `domainsNoLongerOpen deduplicates repeated closed URLs for same host`() {
        val closed = listOf("https://a.example.com/1", "https://A.example.com/2")
        val toPurge = ForgetfulBrowsingManager.domainsNoLongerOpen(
            closedUrls = closed,
            remainingUrls = emptyList(),
            isForgetful = { true }
        )
        assertEquals(listOf("a.example.com"), toPurge)
    }
}
