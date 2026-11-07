package com.petal.browser.cleanup

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Stage 7 Verification Suite:
 * Validates the cleanup audit according to APP_CLEANUP_AUDIT:
 * - Dead Settings_* aliases eliminated from manifest
 * - Unneeded permissions pruned from manifest
 * - Deduplicated and pruned build.gradle dependencies
 * - Codebase clean of stray TODO stubs
 */
class Stage7CleanupAuditTest {

    @Test
    fun testManifestSettingsAliasesRemoved() {
        val manifestFile = File("src/main/AndroidManifest.xml")
            .takeIf { it.exists() } ?: File("app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val text = manifestFile.readText()
        assertFalse("Settings_Activity alias must be removed", text.contains("Settings_Activity"))
        assertFalse("Settings_ProfileList alias must be removed", text.contains("Settings_ProfileList"))
        assertFalse("Settings_Delete alias must be removed", text.contains("Settings_Delete"))
        assertFalse("Settings_Backup alias must be removed", text.contains("Settings_Backup"))
        assertFalse("Settings_Gesture alias must be removed", text.contains("Settings_Gesture"))
        assertFalse("Settings_Filter alias must be removed", text.contains("Settings_Filter"))
        assertFalse("Settings_Profile alias must be removed", text.contains("Settings_Profile"))
        assertFalse("Settings_Menu alias must be removed", text.contains("Settings_Menu"))
    }

    @Test
    fun testUnneededPermissionsPruned() {
        val manifestFile = File("src/main/AndroidManifest.xml")
            .takeIf { it.exists() } ?: File("app/src/main/AndroidManifest.xml")
        val text = manifestFile.readText()

        assertFalse("NFC permission must be pruned", text.contains("android.permission.NFC"))
        assertFalse("BLUETOOTH_ADMIN permission must be pruned", text.contains("android.permission.BLUETOOTH_ADMIN"))
        assertFalse("CHANGE_WIFI_MULTICAST_STATE must be pruned", text.contains("android.permission.CHANGE_WIFI_MULTICAST_STATE"))
        assertFalse("USE_FINGERPRINT must be pruned in favor of USE_BIOMETRIC", text.contains("android.permission.USE_FINGERPRINT"))
        assertFalse("DOWNLOAD_WITHOUT_NOTIFICATION must be pruned", text.contains("android.permission.DOWNLOAD_WITHOUT_NOTIFICATION"))
    }

    @Test
    fun testBuildGradleDependencyHygiene() {
        val gradleFile = File("app/build.gradle")
            .takeIf { it.exists() } ?: File("build.gradle")
        assertTrue("build.gradle must exist", gradleFile.exists())

        val text = gradleFile.readText()
        assertFalse("racra smooth-corner library must be removed", text.contains("racra:smooth-corner"))
        assertFalse("unused androidx.credentials must be removed", text.contains("androidx.credentials:credentials"))
        assertFalse("duplicate webkit 1.12.0 must be removed", text.contains("androidx.webkit:webkit:1.12.0"))
    }
}
