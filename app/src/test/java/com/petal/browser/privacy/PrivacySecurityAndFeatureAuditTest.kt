package com.petal.browser.privacy

import android.content.SharedPreferences
import com.petal.browser.security.BiometricLockManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

class PrivacySecurityAndFeatureAuditTest {

    // ── App Lock passcode hashing & legacy migration compatibility ───────────

    @Test
    fun `hashPasscode prefixes sha256 digest and ignores blank inputs`() {
        assertEquals("", BiometricLockManager.hashPasscode(""))
        assertEquals("", BiometricLockManager.hashPasscode("   "))

        val hashed = BiometricLockManager.hashPasscode("2580")
        assertTrue(BiometricLockManager.isHashedPasscode(hashed))
        assertNotEquals("2580", hashed)
        assertEquals(hashed, BiometricLockManager.hashPasscode(" 2580 "))
    }

    @Test
    fun `matchesPasscode verifies hashed and legacy plaintext values accurately`() {
        val hashed = BiometricLockManager.hashPasscode("13579")
        assertTrue(BiometricLockManager.matchesPasscode("13579", hashed))
        assertTrue(BiometricLockManager.matchesPasscode(" 13579 ", hashed))
        assertFalse(BiometricLockManager.matchesPasscode("00000", hashed))
        assertFalse(BiometricLockManager.matchesPasscode("", hashed))

        // Legacy plaintext migration support before upgrade on unlock
        assertTrue(BiometricLockManager.matchesPasscode("4321", "4321"))
        assertFalse(BiometricLockManager.matchesPasscode("4320", "4321"))
        assertFalse(BiometricLockManager.matchesPasscode("", ""))
    }

    // ── Privacy profile helpers ──────────────────────────────────────────────

    @Test
    fun `getPrivacyProfile resolves known modes and falls back to STANDARD`() {
        val sp = mock(SharedPreferences::class.java)
        `when`(sp.getString(PetalPrivacyHardening.PREF_PRIVACY_PROFILE, "STANDARD")).thenReturn("strict")
        assertEquals(PetalPrivacyHardening.PrivacyProfile.STRICT, PetalPrivacyHardening.getPrivacyProfile(sp))

        `when`(sp.getString(PetalPrivacyHardening.PREF_PRIVACY_PROFILE, "STANDARD")).thenReturn("invalid_mode")
        assertEquals(PetalPrivacyHardening.PrivacyProfile.STANDARD, PetalPrivacyHardening.getPrivacyProfile(sp))
    }

    // ── Unusable / superseded dead modules stay removed ──────────────────────

    private fun resolveAppFile(relPath: String): File =
        File(relPath).takeIf { it.exists() } ?: File("app/$relPath")

    @Test
    fun `dead and unusable modules are removed from source tree`() {
        val removed = listOf(
            "src/main/java/com/petal/browser/privacy/PetalTorManager.kt",
            "src/main/java/com/petal/browser/privacy/PetalUrlCleaner.kt",
            "src/main/java/com/petal/browser/privacy/SafeLockerSheet.kt",
            "src/main/java/com/petal/browser/ui/components/PetalGeckoPermissionDelegate.kt",
            "src/main/java/com/petal/browser/compose/settings/screens/SettingsComponents.kt",
        )
        for (path in removed) {
            assertFalse("Expected dead file $path to remain deleted", resolveAppFile(path).exists())
        }
    }
}
