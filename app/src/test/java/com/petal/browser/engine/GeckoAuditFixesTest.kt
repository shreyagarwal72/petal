package com.petal.browser.engine

import android.content.Context
import android.content.SharedPreferences
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.browser.rules.PetalBackTarget
import com.petal.browser.browser.rules.PetalRootTabBackRules
import com.petal.browser.engine.gecko.PetalAppLinksBridge
import com.petal.browser.privacy.PetalPrivacyHardening
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GeckoAuditFixesTest {

    @Test
    fun `adblock whitelist rejects suffix spoofing while matching subdomains`() {
        val mockCtx = mock<Context>()
        val mockAppCtx = mock<Context>()
        val mockSp = mock<SharedPreferences>()
        whenever(mockCtx.applicationContext).thenReturn(mockAppCtx)
        whenever(mockAppCtx.getSharedPreferences(any(), any())).thenReturn(mockSp)
        whenever(mockSp.edit()).thenReturn(mock())
        PetalAdBlockEngine.clearPrivateSessionData()
        PetalAdBlockEngine.addDomainToWhitelist(mockCtx, "example.com", isPrivate = true)

        assertTrue(PetalAdBlockEngine.isDomainWhitelisted("example.com", isPrivate = true))
        assertTrue(PetalAdBlockEngine.isDomainWhitelisted("sub.example.com", isPrivate = true))
        assertFalse(PetalAdBlockEngine.isDomainWhitelisted("badexample.com", isPrivate = true))
        assertFalse(PetalAdBlockEngine.isDomainWhitelisted("phishing-example.com", isPrivate = true))

        PetalAdBlockEngine.clearPrivateSessionData()
    }

    @Test
    fun `fullscreen web content takes precedence in back rules`() {
        val target = PetalRootTabBackRules.decide(
            isOverlayShowing = false,
            isFullscreen = true,
            hasDialog = false,
            canGoBack = true,
            isHomePage = false,
            totalTabs = 3
        )
        assertEquals(PetalBackTarget.FULLSCREEN_VIDEO, target)
    }

    @Test
    fun `app links internal scheme classification handles view-source and external schemes`() {
        assertTrue(PetalAppLinksBridge.isInternalScheme("https://example.com"))
        assertTrue(PetalAppLinksBridge.isInternalScheme("view-source:https://example.com"))
        assertTrue(PetalAppLinksBridge.isInternalScheme("petal://bookmarks"))
        assertFalse(PetalAppLinksBridge.isInternalScheme("intent://scan/#Intent;end"))
        assertFalse(PetalAppLinksBridge.isInternalScheme("mailto:test@example.com"))
        assertTrue(PetalAppLinksBridge.isExternalScheme("intent://scan/#Intent;end"))
    }

    @Test
    fun `privacy hardening respects hardened and strict defaults when fingerprint toggle unset`() {
        val spHardened = mock<SharedPreferences>()
        whenever(spHardened.getString(eq(PetalPrivacyHardening.PREF_PRIVACY_PROFILE), any())).thenReturn("HARDENED")
        whenever(spHardened.getBoolean(eq("sp_fingerprint_protection"), any())).thenAnswer { it.arguments[1] as Boolean }
        whenever(spHardened.getBoolean(eq(PetalPrivacyHardening.PREF_WEBRTC_LEAK_PROTECT), any())).thenAnswer { it.arguments[1] as Boolean }
        whenever(spHardened.contains(PetalPrivacyHardening.PREF_WEBRTC_LEAK_PROTECT)).thenReturn(true)

        assertTrue(PetalPrivacyHardening.isFingerprintingProtectionEnabled(spHardened))
        assertTrue(PetalPrivacyHardening.isWebRtcNoHostEnabled(spHardened))

        val spStandard = mock<SharedPreferences>()
        whenever(spStandard.getString(eq(PetalPrivacyHardening.PREF_PRIVACY_PROFILE), any())).thenReturn("STANDARD")
        whenever(spStandard.getBoolean(eq("sp_fingerprint_protection"), any())).thenAnswer { it.arguments[1] as Boolean }
        whenever(spStandard.getBoolean(eq(PetalPrivacyHardening.PREF_WEBRTC_LEAK_PROTECT), any())).thenAnswer { it.arguments[1] as Boolean }
        whenever(spStandard.contains(PetalPrivacyHardening.PREF_WEBRTC_LEAK_PROTECT)).thenReturn(false)

        assertFalse(PetalPrivacyHardening.isFingerprintingProtectionEnabled(spStandard))
        assertFalse(PetalPrivacyHardening.isWebRtcNoHostEnabled(spStandard))
    }
}
