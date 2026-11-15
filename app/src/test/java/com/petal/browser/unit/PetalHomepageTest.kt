package com.petal.browser.unit

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class PetalHomepageTest {

    @Test
    fun isHomePageIdentifiesBuiltInHomePages() {
        assertTrue(PetalBrowserUtils.isHomePage(null))
        assertTrue(PetalBrowserUtils.isHomePage(""))
        assertTrue(PetalBrowserUtils.isHomePage("   "))
        assertTrue(PetalBrowserUtils.isHomePage("about:blank"))
        assertTrue(PetalBrowserUtils.isHomePage("about:home"))
        assertTrue(PetalBrowserUtils.isHomePage("petal://home"))
        assertTrue(PetalBrowserUtils.isHomePage("petal://start"))
        assertFalse(PetalBrowserUtils.isHomePage("https://google.com"))
        assertFalse(PetalBrowserUtils.isHomePage("https://duckduckgo.com"))
    }

    @Test
    fun defaultHomepageReturnsAboutHome() {
        val sp = mock(SharedPreferences::class.java)
        `when`(sp.getString("sp_home_type", "0")).thenReturn("0")
        val effective = PetalBrowserUtils.getEffectiveHomepageUrl(context = null, sp = sp)
        assertEquals("about:home", effective)
    }

    @Test
    fun customHomepageReturnsCustomUrl() {
        val sp = mock(SharedPreferences::class.java)
        `when`(sp.getString("sp_home_type", "0")).thenReturn("1")
        `when`(sp.getString("sp_custom_homepage_url", "https://google.com")).thenReturn("https://duckduckgo.com")
        val effective = PetalBrowserUtils.getEffectiveHomepageUrl(context = null, sp = sp)
        assertEquals("https://duckduckgo.com", effective)
    }

    @Test
    fun customHomepageAddsHttpsPrefixWhenMissing() {
        val sp = mock(SharedPreferences::class.java)
        `when`(sp.getString("sp_home_type", "0")).thenReturn("1")
        `when`(sp.getString("sp_custom_homepage_url", "https://google.com")).thenReturn("duckduckgo.com")
        val effective = PetalBrowserUtils.getEffectiveHomepageUrl(context = null, sp = sp)
        assertEquals("https://duckduckgo.com", effective)
    }

    @Test
    fun blankCustomHomepageFallsBackToAboutHome() {
        val sp = mock(SharedPreferences::class.java)
        `when`(sp.getString("sp_home_type", "0")).thenReturn("1")
        `when`(sp.getString("sp_custom_homepage_url", "https://google.com")).thenReturn("   ")
        val effective = PetalBrowserUtils.getEffectiveHomepageUrl(context = null, sp = sp)
        assertEquals("about:home", effective)
    }
}
