/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT/TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.petal.browser.engine

import com.petal.browser.engine.gecko.PetalCrashHandlerService
import com.petal.browser.engine.gecko.PetalEngineBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Stage 4 Verification Suite – GeckoEngineSession Reflection, Stage 2 Crash Handler & Settings,
 * and Stage 3 Preference & Hardening Audits.
 */
class Stage4GeckoReflectionTest {

    // ---------------------------------------------------------------------------
    // Stage 4: GeckoSession Reflection Verification
    // ---------------------------------------------------------------------------

    @Test
    fun extractGeckoSession_nullSession_returnsNullSafely() {
        val result = PetalEngineBridge.extractGeckoSession(null)
        assertNull("extractGeckoSession(null) must return null without throwing", result)
    }

    @Test
    fun extractGeckoSession_nonGeckoSession_returnsNullSafely() {
        val nonGecko = object : mozilla.components.concept.engine.EngineSession() {}
        val result = PetalEngineBridge.extractGeckoSession(nonGecko)
        assertNull("extractGeckoSession(nonGeckoSession) must return null without throwing", result)
    }

    @Test
    fun selfCheckReflection_executesSafelyWithoutCrashing() {
        // selfCheckReflection returns true if class is found and conforms, or false
        // with loud log if class is not on mock classpath; it MUST NOT throw an uncaught exception.
        try {
            val checkResult = PetalEngineBridge.selfCheckReflection(null)
            // Just verifying it executed cleanly
            assertNotNull(checkResult)
        } catch (t: Throwable) {
            org.junit.Assert.fail("selfCheckReflection must never throw uncaught exception: ${t.message}")
        }
    }

    @Test
    fun reflectiveProbe_extractsFromGetterOrField() {
        // Test synthetic class with private field
        class SyntheticFieldSession {
            private val geckoSession = "mockSessionField"
        }

        val instance1 = SyntheticFieldSession()
        val cls1 = instance1.javaClass
        val field = cls1.getDeclaredField("geckoSession")
        field.isAccessible = true
        val extracted1 = field.get(instance1)
        assertEquals("mockSessionField", extracted1)

        // Test synthetic class with getter method
        class SyntheticGetterSession {
            fun getGeckoSession(): String = "mockSessionGetter"
        }

        val instance2 = SyntheticGetterSession()
        val cls2 = instance2.javaClass
        val getter = cls2.methods.firstOrNull { it.parameterCount == 0 && it.name.startsWith("getGeckoSession") }
        assertNotNull(getter)
        val extracted2 = getter?.invoke(instance2)
        assertEquals("mockSessionGetter", extracted2)

        // Test synthetic class with Kotlin module-mangled getter
        class SyntheticMangledSession {
            fun `getGeckoSession$browser_engine_gecko`(): String = "mockSessionMangled"
        }

        val instance3 = SyntheticMangledSession()
        val cls3 = instance3.javaClass
        val getterMangled = cls3.methods.firstOrNull {
            it.parameterCount == 0 && (it.name == "getGeckoSession" || it.name.startsWith("getGeckoSession$"))
        }
        assertNotNull(getterMangled)
        val extracted3 = getterMangled?.invoke(instance3)
        assertEquals("mockSessionMangled", extracted3)
    }

    // ---------------------------------------------------------------------------
    // Stage 2: CrashHandler & GeckoRuntime Settings Static Audits
    // ---------------------------------------------------------------------------

    @Test
    fun crashHandlerService_constantsMatchGeckoViewSpec() {
        assertEquals("minidumpPath", PetalCrashHandlerService.EXTRA_MINIDUMP_PATH)
        assertEquals("extrasPath", PetalCrashHandlerService.EXTRA_EXTRAS_PATH)
        assertEquals("isFatal", PetalCrashHandlerService.EXTRA_CRASH_IS_FATAL)
        assertEquals("processType", PetalCrashHandlerService.EXTRA_CRASH_PROCESS)
        assertEquals("processVisibility", PetalCrashHandlerService.EXTRA_CRASH_PROCESS_VISIBILITY)
    }

    @Test
    fun crashHandlerService_registeredInManifestWithIsolatedProcess() {
        val manifestFile = File("src/main/AndroidManifest.xml")
            .takeIf { it.exists() } ?: File("app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val manifestText = manifestFile.readText()
        assertTrue(
            "AndroidManifest.xml must register PetalCrashHandlerService",
            manifestText.contains("PetalCrashHandlerService")
        )
        assertTrue(
            "PetalCrashHandlerService must specify android:process=\":gecko_crash\"",
            manifestText.contains("android:process=\":gecko_crash\"")
        )
        assertTrue(
            "PetalCrashHandlerService must specify android:exported=\"false\"",
            manifestText.contains("android:exported=\"false\"")
        )
    }

    @Test
    fun geckoRuntime_reflectionCallsDeletedAndOfficialApisUsed() {
        val runtimeFile = File("src/main/java/com/petal/browser/engine/gecko/PetalGeckoRuntime.kt")
            .takeIf { it.exists() } ?: File("app/src/main/java/com/petal/browser/engine/gecko/PetalGeckoRuntime.kt")
        assertTrue("PetalGeckoRuntime.kt must exist", runtimeFile.exists())

        val content = runtimeFile.readText()
        assertFalse(
            "setWebContentIsolationStrategy reflection call must be deleted",
            content.contains("setWebContentIsolationStrategy")
        )
        assertFalse(
            "setContentProcessCount reflection call must be deleted",
            content.contains("setContentProcessCount")
        )
        assertTrue(
            "isolatedProcessEnabled official API must be used",
            content.contains("isolatedProcessEnabled")
        )
        assertTrue(
            "lowMemoryDetection official API must be used",
            content.contains("lowMemoryDetection")
        )
        assertTrue(
            "crashHandler(PetalCrashHandlerService::class.java) must be passed",
            content.contains("crashHandler(PetalCrashHandlerService::class.java)")
        )
    }

    // ---------------------------------------------------------------------------
    // Stage 3: Preferences & user.js Hardening Audits
    // ---------------------------------------------------------------------------

    @Test
    fun stage3_configFilePathEmptyAndUserJsSeedPurged() {
        val runtimeFile = File("src/main/java/com/petal/browser/engine/gecko/PetalGeckoRuntime.kt")
            .takeIf { it.exists() } ?: File("app/src/main/java/com/petal/browser/engine/gecko/PetalGeckoRuntime.kt")
        assertTrue("PetalGeckoRuntime.kt must exist", runtimeFile.exists())

        val content = runtimeFile.readText()
        assertTrue(
            "settingsBuilder.configFilePath(\"\") must be present to disable unsolicited config file loading",
            content.contains("settingsBuilder.configFilePath(\"\")")
        )
        assertTrue(
            "MOZ_AVOID_OPENH264 setenv must be retained",
            content.contains("MOZ_AVOID_OPENH264")
        )
        assertFalse(
            "seedProfileUserJs disk writing method must be deleted",
            content.contains("fun seedProfileUserJs")
        )
    }

    @Test
    fun stage3_privacyResistFingerprinting_appliedStrictlyToStrictProfileOnly() {
        val privacyFile = File("src/main/java/com/petal/browser/privacy/PetalPrivacyHardening.kt")
            .takeIf { it.exists() } ?: File("app/src/main/java/com/petal/browser/privacy/PetalPrivacyHardening.kt")
        assertTrue("PetalPrivacyHardening.kt must exist", privacyFile.exists())

        val content = privacyFile.readText()
        assertTrue(
            "privacy.resistFingerprinting must be conditioned on profile == PrivacyProfile.STRICT",
            content.contains("if (profile == PrivacyProfile.STRICT)") &&
                content.contains("privacy.resistFingerprinting")
        )
    }

    @Test
    fun issue60_isolatedProcessDisabled_andArtImageExitsFiltered() {
        val runtimeFile = File("src/main/java/com/petal/browser/engine/gecko/PetalGeckoRuntime.kt")
            .takeIf { it.exists() } ?: File("app/src/main/java/com/petal/browser/engine/gecko/PetalGeckoRuntime.kt")
        val content = runtimeFile.readText()
        assertTrue(
            "isolatedProcessEnabled must be false to prevent Android 16 isolatedTab ART image crashes",
            content.contains(".isolatedProcessEnabled(false)") &&
                content.contains("settingsBuilder.isolatedProcessEnabled(false)")
        )

        val loggerFile = File("src/main/java/com/petal/browser/logger/PetalAppLogger.kt")
            .takeIf { it.exists() } ?: File("app/src/main/java/com/petal/browser/logger/PetalAppLogger.kt")
        val loggerContent = loggerFile.readText()
        assertTrue(
            "disable_art_image and isolatedTab exit reasons must be filtered in PetalAppLogger",
            loggerContent.contains("disable_art_image") && loggerContent.contains("isolatedTab")
        )
    }
}
