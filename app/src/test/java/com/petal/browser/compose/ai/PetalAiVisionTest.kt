package com.petal.browser.compose.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.spec.SecretKeySpec

@OptIn(ExperimentalCoroutinesApi::class)
class PetalAiVisionTest {

    private class FakePreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return map[key] as? MutableSet<String> ?: defValues
        }

        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            val toRemove = mutableSetOf<String>()
            var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) toRemove.add(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) map.clear()
                for (r in toRemove) map.remove(r)
                map.putAll(pending)
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private fun createMockContext(prefs: SharedPreferences): Context {
        lateinit var proxyContext: Context
        val handler = InvocationHandler { _, method, _ ->
            when (method.name) {
                "getApplicationContext" -> proxyContext
                "getSharedPreferences" -> prefs
                "getPackageName" -> "com.petal.browser"
                else -> null
            }
        }
        proxyContext = Proxy.newProxyInstance(
            Context::class.java.classLoader,
            arrayOf(Context::class.java),
            handler
        ) as Context
        return proxyContext
    }

    @Before
    fun setUp() {
        PetalAiKeyStorage.testSecretKey = SecretKeySpec(ByteArray(32) { 7.toByte() }, "AES")
    }

    @Test
    fun testExtendedVisionModelNameCheckAndGroqAllowlist() {
        val visionNames = listOf(
            "smolvlm-instruct",
            "gemma3:4b",
            "gemma-3-12b-it",
            "pixtral-12b",
            "moondream2",
            "qwen2.5-vl-7b",
            "llama3.2-vision:11b",
            "internvl2.5-8b",
            "minicpm-v-2.6"
        )
        for (model in visionNames) {
            assertTrue("Expected $model to support vision", PetalAiResearchEngine.isVisionSupported(model))
            assertTrue("Expected Custom $model to support vision", PetalAiResearchEngine.isVisionSupported(AiProvider.CUSTOM, model))
        }

        assertFalse(PetalAiResearchEngine.isVisionSupported("llama3.3:70b"))
        assertFalse(PetalAiResearchEngine.isVisionSupported(AiProvider.GROQ, "openai/gpt-oss-120b"))
        assertFalse(PetalAiResearchEngine.isVisionSupported(AiProvider.GROQ, "qwen/qwen3.6-27b"))
        assertFalse(PetalAiResearchEngine.isVisionSupported(AiProvider.LOCAL_ON_DEVICE, "gemma-2b-it"))
    }

    @Test
    fun testCustomModelVisionOverrideWinsOverGuess() {
        val prefs = FakePreferences()
        val context = createMockContext(prefs)

        assertFalse(PetalAiResearchEngine.isVisionSupported(context, AiProvider.CUSTOM, "my-local-model"))
        PetalAiResearchEngine.setCustomModelVisionOverride(context, "my-local-model", true)
        assertTrue(PetalAiResearchEngine.isVisionSupported(context, AiProvider.CUSTOM, "my-local-model"))

        assertTrue(PetalAiResearchEngine.isVisionSupported(context, AiProvider.CUSTOM, "llava:13b"))
        PetalAiResearchEngine.setCustomModelVisionOverride(context, "llava:13b", false)
        assertFalse(PetalAiResearchEngine.isVisionSupported(context, AiProvider.CUSTOM, "llava:13b"))
    }

    @Test
    fun testVisionAwareProviderFilteringAndConsent() = runTest {
        val prefs = FakePreferences().apply {
            putString("sp_ai_key_gemini", "gemini-key")
            putString("sp_ai_key_groq", "groq-key")
            putString("sp_ai_custom_endpoint", "http://localhost:11434/v1")
            putString("sp_ai_model_custom", "llama3.3:70b")
            putBoolean(PetalAiResearchEngine.KEY_CLOUD_CONSENT_ACCEPTED, false)
        }
        val context = createMockContext(prefs)

        // Cloud consent not accepted -> Gemini & Groq must NOT be candidates
        val noConsentCandidates = PetalAiProviderFallback.getCandidateProviders(
            context = context,
            primary = AiProvider.CUSTOM,
            requireVision = false
        )
        assertEquals(listOf(AiProvider.CUSTOM), noConsentCandidates)

        // With image and no consent, Custom text-only model is excluded -> throws VisionUnsupportedException
        val imageMessage = listOf(
            AiChatMessage(
                role = AiRole.USER,
                parts = listOf(
                    AiMessagePart.Text("What is this?"),
                    AiMessagePart.Image("image/jpeg", "AAAA")
                )
            )
        )
        try {
            PetalAiProviderFallback.streamWithFallback(
                context = context,
                messages = imageMessage,
                initialProvider = AiProvider.CUSTOM,
                initialModel = "llama3.3:70b",
                thinkHarder = false
            ).first()
            fail("Expected VisionUnsupportedException when no consented vision provider is available")
        } catch (e: AiBackendException.VisionUnsupportedException) {
            assertTrue(e.message?.contains("Gemini") == true)
        }

        // Accept cloud consent -> Gemini is included, Groq is still skipped for vision
        prefs.edit().putBoolean(PetalAiResearchEngine.KEY_CLOUD_CONSENT_ACCEPTED, true).apply()
        val visionCandidates = PetalAiProviderFallback.getCandidateProviders(
            context = context,
            primary = AiProvider.GROQ,
            requireVision = true,
            thinkHarder = true
        )
        assertEquals(listOf(AiProvider.GEMINI), visionCandidates)

        // Custom vision model with thinkHarder keeps the Custom vision model instead of text-only stronger model
        prefs.edit().putString("sp_ai_model_custom", "qwen2.5-vl-7b").apply()
        val customResolved = PetalAiResearchEngine.resolveModelForRequest(
            context = context,
            provider = AiProvider.CUSTOM,
            thinkHarder = true,
            requireVision = true
        )
        assertEquals("qwen2.5-vl-7b", customResolved)
    }

    @Test
    fun testOnlyNewestImageIsSentAndOlderImagesStayInDisplayHistory() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(testDispatcher)
        val manager = PetalAiConversationManager()

        var lastMessagesSent: List<AiChatMessage> = emptyList()
        val backend = object : AiBackend {
            override fun stream(messages: List<AiChatMessage>, model: String): Flow<String> {
                lastMessagesSent = messages
                return flow { emit("Reply") }
            }
        }

        val img1 = AiMessagePart.Image("image/jpeg", "IMG_ONE")
        val img2 = AiMessagePart.Image("image/jpeg", "IMG_TWO")

        manager.sendMessage("tab1", "First image", backend, "gemini-3.5-flash-lite", scope, imagePart = img1)
        testScheduler.advanceUntilIdle()

        val capturedInTok = AtomicInteger(0)
        manager.sendMessage(
            tabId = "tab1",
            userText = "Second image",
            backend = backend,
            model = "gemini-3.5-flash-lite",
            scope = scope,
            imagePart = img2,
            onComplete = { inTok, _ -> capturedInTok.set(inTok) }
        )
        testScheduler.advanceUntilIdle()

        // Visible history keeps both images
        val displayHistory = manager.getMessages("tab1")
        assertEquals(img1, displayHistory[0].imagePart)
        assertEquals(img2, displayHistory[2].imagePart)

        // Backend payload keeps ONLY the newest user image
        val userTurnsSent = lastMessagesSent.filter { it.role == AiRole.USER }
        assertEquals(2, userTurnsSent.size)
        assertFalse(userTurnsSent[0].parts.any { it is AiMessagePart.Image })
        assertEquals("First image", userTurnsSent[0].textContent)
        assertEquals(1, userTurnsSent[1].parts.count { it is AiMessagePart.Image })
        assertEquals(img2, userTurnsSent[1].parts.filterIsInstance<AiMessagePart.Image>().single())

        // Token estimate counts only the 1 sent image (300 tokens) + text tokens
        val expectedTextChars = lastMessagesSent.sumOf { it.textContent.length }
        assertEquals(PetalAiUsageTracker.estimateTokens(expectedTextChars) + 300, capturedInTok.get())
    }

    @Test
    fun testPerImageTokenEstimateInUsageTracker() {
        val prefs = FakePreferences()
        val context = createMockContext(prefs)

        assertEquals(300, PetalAiUsageTracker.TOKENS_PER_IMAGE)
        val msgWithImage = listOf(
            AiChatMessage(
                role = AiRole.USER,
                parts = listOf(
                    AiMessagePart.Text("12345678"), // 2 tokens
                    AiMessagePart.Image("image/jpeg", "AAAA")
                )
            )
        )
        assertEquals(302, PetalAiUsageTracker.estimateInputTokens(msgWithImage))

        PetalAiUsageTracker.recordUsage(
            context = context,
            providerId = "gemini",
            inputChars = 8,
            outputChars = 16,
            imageCount = 2
        )
        // 2 text input tokens + 600 image input tokens + 4 output tokens = 606
        assertEquals(606L, PetalAiUsageTracker.getTodayTokens(context))
    }
}
