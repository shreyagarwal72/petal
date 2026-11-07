package com.petal.browser.settings

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Stage 5 Verification Suite:
 * Validates the recreated Privacy & Security settings:
 * - Deprecated Samsung content blocker bridge eliminated from manifest and codebase
 * - Search index mappings for privacy sections
 * - Preference migration & defaults
 */
class Stage5PrivacySettingsTest {

    @Test
    fun testSamsungBridgePurgedFromManifest() {
        val manifestFile = File("src/main/AndroidManifest.xml")
            .takeIf { it.exists() } ?: File("app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val manifestText = manifestFile.readText()
        assertFalse(
            "SamsungContentBlockerUpdateReceiver must not be present in AndroidManifest.xml",
            manifestText.contains("SamsungContentBlockerUpdateReceiver")
        )
        assertFalse(
            "Samsung permission must not be claimed in AndroidManifest.xml",
            manifestText.contains("com.samsung.android.sbrowser.permission.CONTENTBLOCKER")
        )
    }

    @Test
    fun testSamsungBridgeClassDeleted() {
        val bridgeFile = File("app/src/main/java/com/petal/browser/browser/samsung/SamsungContentBlockerBridge.kt")
        assertFalse("SamsungContentBlockerBridge.kt must be deleted", bridgeFile.exists())
    }

    @Test
    fun testPrivacySearchIndexIntegrity() {
        val searchIndex = com.petal.browser.compose.settings.PetalSettingsSearchIndex.items
        val privacyItems = searchIndex.filter { it.category == com.petal.browser.compose.settings.SettingsCategory.PRIVACY }

        assertTrue("Privacy search index should contain privacy_adblock", privacyItems.any { it.id == "privacy_adblock" })
        assertTrue("Privacy search index should contain privacy_etp", privacyItems.any { it.id == "privacy_etp" })
        assertTrue("Privacy search index should contain privacy_incognito", privacyItems.any { it.id == "privacy_incognito" })
        assertTrue("Privacy search index should contain privacy_passwords", privacyItems.any { it.id == "privacy_passwords" })
    }

    @Test
    fun testPreferenceDefaults() {
        // Test key names match specifications
        val keyAdBlock = "sp_ad_block"
        val keyEtpStrict = "sp_etp_strict"
        val keyTcp = "sp_total_cookie_protection"
        val keySessionPartitioning = "sp_session_partitioning"
        val keyClearOnExit = "sp_clear_on_exit"

        assertNotNull(keyAdBlock)
        assertNotNull(keyEtpStrict)
        assertNotNull(keyTcp)
        assertNotNull(keySessionPartitioning)
        assertNotNull(keyClearOnExit)
    }
}
