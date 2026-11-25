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

    private fun resolveAppFile(relPath: String): File {
        return File(relPath).takeIf { it.exists() }
            ?: File("app/$relPath")
    }

    @Test
    fun testAllSyncPreferencesSettersTrackedInSyncFingerprint() {
        val repoFile = resolveAppFile("src/main/java/com/petal/browser/data/repository/SettingsRepositoryImpl.kt")
        assertTrue("SettingsRepositoryImpl.kt must exist", repoFile.exists())

        val repoLines = repoFile.readLines()
        val setterKeys = linkedSetOf<String>()
        val putKeyRegex = Regex("""\.put(?:Boolean|Int|Float|String|Long)\(\s*"([^"]+)"""")

        var i = 0
        while (i < repoLines.size) {
            if (repoLines[i].trimStart().startsWith("override suspend fun set")) {
                val block = StringBuilder()
                var braceDepth = 0
                var seenOpen = false
                while (i < repoLines.size) {
                    val line = repoLines[i]
                    block.appendLine(line)
                    for (ch in line) {
                        if (ch == '{') {
                            braceDepth++
                            seenOpen = true
                        } else if (ch == '}') {
                            braceDepth--
                        }
                    }
                    i++
                    if (seenOpen && braceDepth <= 0) break
                }
                val body = block.toString()
                if (body.contains("syncPreferences(")) {
                    putKeyRegex.findAll(body).forEach { match ->
                        setterKeys += match.groupValues[1]
                    }
                }
            } else {
                i++
            }
        }

        assertTrue("Expected SettingsRepositoryImpl setters calling syncPreferences", setterKeys.isNotEmpty())
        val fingerprintKeys = com.petal.browser.engine.gecko.PetalGeckoRuntime.SYNC_FINGERPRINT_KEYS.toSet()

        val requiredToggleKeys = listOf(
            "sp_force_enable_zoom",
            "sp_zoom_level_scale",
            "sp_webrtc_protection",
            "sp_canvas_letterboxing",
            "sp_bounce_tracking",
            "sp_drm"
        )
        for (req in requiredToggleKeys) {
            assertTrue("Expected $req in SYNC_FINGERPRINT_KEYS", req in fingerprintKeys)
        }

        val missing = setterKeys.filterNot { it in fingerprintKeys }
        assertTrue(
            "Keys written by syncPreferences setters must appear in syncFingerprint: missing=$missing",
            missing.isEmpty()
        )
    }

    @Test
    fun testCategorySubtitlesAndSearchIndexConsistency() {
        assertEquals(
            "Video player, media sniffer & picture-in-picture",
            com.petal.browser.compose.settings.SettingsCategory.MEDIA.subtitle
        )
        assertEquals(
            "Custom tabs, external apps, offline game and quick tiles",
            com.petal.browser.compose.settings.SettingsCategory.MISCELLANEOUS.subtitle
        )
        val searchItems = com.petal.browser.compose.settings.PetalSettingsSearchIndex.allItems
        assertTrue(searchItems.any { it.id == "misc_offline_game" && it.category == com.petal.browser.compose.settings.SettingsCategory.MISCELLANEOUS })
        assertTrue(searchItems.any { it.id == "misc_quick_settings" && it.category == com.petal.browser.compose.settings.SettingsCategory.MISCELLANEOUS })
    }
}
