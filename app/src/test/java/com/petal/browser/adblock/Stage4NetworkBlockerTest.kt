package com.petal.browser.adblock

import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.extensions.PetalBuiltInExtensionManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Stage 4 Verification Suite:
 * Validates the ad-block network blocker, early cosmetic injection, and clean engine integration:
 * - BuiltIn WebExtension registration for petal-network-blocker
 * - WebExtension manifest and script assets validity
 * - Safe retirement of gs.loadUri cosmetic script injection
 * - Whitelist management and block telemetry recording
 */
class Stage4NetworkBlockerTest {

    @Test
    fun testBuiltInExtensionRegistration() {
        val extensionId = "petal-network-blocker@petalbrowser.app"
        assertTrue("Extension should be recognized as built-in", PetalBuiltInExtensionManager.isBuiltIn(extensionId))

        val spec = PetalBuiltInExtensionManager.builtIns.find { it.extensionId == extensionId }
        assertNotNull("Network blocker spec must exist in builtIns list", spec)
        assertEquals("web_extensions/petal_network_blocker/", spec?.assetPath)
        assertEquals("sp_ad_block", spec?.prefKey)
        assertTrue("Network blocker must be enabled by default", spec?.defaultOn == true)
    }

    @Test
    fun testExtensionManifestIntegrity() {
        val manifestFile = File("src/main/assets/web_extensions/petal_network_blocker/manifest.json")
        val altFile = File("app/src/main/assets/web_extensions/petal_network_blocker/manifest.json")
        val file = if (manifestFile.exists()) manifestFile else altFile
        assertTrue("manifest.json must exist on disk", file.exists())

        val jsonStr = file.readText()
        val json = JSONObject(jsonStr)
        assertEquals(2, json.getInt("manifest_version"))
        assertEquals("petal-network-blocker@petalbrowser.app", json.getJSONObject("browser_specific_settings").getJSONObject("gecko").getString("id"))

        val perms = json.getJSONArray("permissions")
        val permList = (0 until perms.length()).map { perms.getString(it) }
        assertTrue("Must include webRequest permission", permList.contains("webRequest"))
        assertTrue("Must include webRequestBlocking permission", permList.contains("webRequestBlocking"))
        assertTrue("Must include nativeMessaging permission", permList.contains("nativeMessaging"))

        val bgScripts = json.getJSONObject("background").getJSONArray("scripts")
        assertEquals("blocker_background.js", bgScripts.getString(0))

        val contentScripts = json.getJSONArray("content_scripts")
        val firstScript = contentScripts.getJSONObject(0)
        assertEquals("document_start", firstScript.getString("run_at"))
        assertTrue("Must run in all frames", firstScript.getBoolean("all_frames"))
        assertEquals("early_cosmetics.js", firstScript.getJSONArray("js").getString(0))
    }

    @Test
    fun testBlockerScriptsExistAndNotEmpty() {
        val bgFile = File("app/src/main/assets/web_extensions/petal_network_blocker/blocker_background.js")
            .takeIf { it.exists() } ?: File("src/main/assets/web_extensions/petal_network_blocker/blocker_background.js")
        val csFile = File("app/src/main/assets/web_extensions/petal_network_blocker/early_cosmetics.js")
            .takeIf { it.exists() } ?: File("src/main/assets/web_extensions/petal_network_blocker/early_cosmetics.js")

        assertTrue("blocker_background.js must exist", bgFile.exists())
        assertTrue("early_cosmetics.js must exist", csFile.exists())

        val bgText = bgFile.readText()
        assertTrue("blocker_background.js must listen to onBeforeRequest", bgText.contains("webRequest.onBeforeRequest.addListener"))
        assertTrue("blocker_background.js must handle RECORD_BLOCK", bgText.contains("RECORD_BLOCK"))

        val csText = csFile.readText()
        assertTrue("early_cosmetics.js must inject generic cosmetic CSS", csText.contains("GENERIC_COSMETIC_CSS"))
        assertTrue("early_cosmetics.js must handle YouTube ad skipping", csText.contains("handleYouTubeAds"))
    }

    @Test
    fun testAdBlockEngineWhitelistAndRecording() {
        val testDomain = "test-whitelist-stage4.example.com"
        assertFalse("Domain should not be whitelisted initially", PetalAdBlockEngine.isDomainWhitelisted(testDomain))

        // Record a block
        val initialTotal = PetalAdBlockEngine.getTotalBlockedCount()
        PetalAdBlockEngine.recordBlock("https://$testDomain/page")
        val newTotal = PetalAdBlockEngine.getTotalBlockedCount()
        assertEquals("Total blocked count must increment", initialTotal + 1, newTotal)

        // Test deprecated payload method returns safe empty string without running javascript: navigation
        val payload = PetalAdBlockEngine.getuBlockCosmeticAndScriptletPayload("https://$testDomain")
        assertEquals("Deprecated cosmetic payload must return empty string", "", payload)
    }
}
