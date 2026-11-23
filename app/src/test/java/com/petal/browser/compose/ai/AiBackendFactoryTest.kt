package com.petal.browser.compose.ai

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiBackendFactoryTest {

    @Test
    fun testCreateGeminiBackend() {
        val backend = AiBackendFactory.create(
            provider = AiProvider.GEMINI,
            apiKey = "test-gemini-key"
        )
        assertTrue(backend is GeminiBackend)
    }

    @Test
    fun testCreateGroqBackend() {
        val backend = AiBackendFactory.create(
            provider = AiProvider.GROQ,
            apiKey = "test-groq-key"
        )
        assertTrue(backend is OpenAiCompatibleBackend)
        val openAiBackend = backend as OpenAiCompatibleBackend
        assertEquals(AiProvider.GROQ, openAiBackend.provider)
        assertEquals(OpenAiCompatibleBackend.GROQ_ENDPOINT, openAiBackend.endpoint)
    }

    @Test
    fun testCreateCustomBackend() {
        val customUrl = "http://192.168.1.50:11434/v1"
        val backend = AiBackendFactory.create(
            provider = AiProvider.CUSTOM,
            apiKey = "",
            customEndpoint = customUrl
        )
        assertTrue(backend is OpenAiCompatibleBackend)
        val openAiBackend = backend as OpenAiCompatibleBackend
        assertEquals(AiProvider.CUSTOM, openAiBackend.provider)
        assertEquals(customUrl, openAiBackend.endpoint)
    }

    @Test
    fun testEndpointValidation() {
        // Valid HTTPS
        assertTrue(PetalAiResearchEngine.validateEndpoint("https://my-llm.example.com/v1"))
        assertTrue(PetalAiResearchEngine.validateEndpoint("https://api.groq.com/openai/v1/chat/completions"))

        // Valid Private-LAN HTTP
        assertTrue(PetalAiResearchEngine.validateEndpoint("http://localhost:11434/v1"))
        assertTrue(PetalAiResearchEngine.validateEndpoint("http://127.0.0.1:11434/v1"))
        assertTrue(PetalAiResearchEngine.validateEndpoint("http://192.168.1.100:8000/v1"))
        assertTrue(PetalAiResearchEngine.validateEndpoint("http://10.0.0.5:1234/v1"))
        assertTrue(PetalAiResearchEngine.validateEndpoint("http://my-pc.local:11434/v1"))

        // Invalid WAN HTTP
        assertFalse(PetalAiResearchEngine.validateEndpoint("http://insecure-public-server.com/v1"))
        assertFalse(PetalAiResearchEngine.validateEndpoint("http://8.8.8.8:8080"))
        assertFalse(PetalAiResearchEngine.validateEndpoint(""))
        assertFalse(PetalAiResearchEngine.validateEndpoint("ftp://localhost"))
    }

    @Test
    fun testNormalizeEndpoint() {
        assertEquals(
            "http://localhost:11434/v1/chat/completions",
            OpenAiCompatibleBackend.normalizeEndpoint("http://localhost:11434")
        )
        assertEquals(
            "http://localhost:11434/v1/chat/completions",
            OpenAiCompatibleBackend.normalizeEndpoint("http://localhost:11434/v1")
        )
        assertEquals(
            "http://localhost:11434/v1/chat/completions",
            OpenAiCompatibleBackend.normalizeEndpoint("http://localhost:11434/v1/chat/completions")
        )
    }

    @Test
    fun testMissingApiKeyThrowsForGemini() = runTest {
        val backend = AiBackendFactory.create(
            provider = AiProvider.GEMINI,
            apiKey = ""
        )
        try {
            backend.stream(listOf(AiChatMessage.user("Hi")), "gemini-3.5-flash-lite").first()
            fail("Expected MissingApiKeyException")
        } catch (e: AiBackendException.MissingApiKeyException) {
            assertTrue(e.message?.contains("API key") == true)
        }
    }

    @Test
    fun testMissingApiKeyThrowsForGroq() = runTest {
        val backend = AiBackendFactory.create(
            provider = AiProvider.GROQ,
            apiKey = ""
        )
        try {
            backend.stream(listOf(AiChatMessage.user("Hi")), "openai/gpt-oss-120b").first()
            fail("Expected MissingApiKeyException")
        } catch (e: AiBackendException.MissingApiKeyException) {
            assertTrue(e.message?.contains("API key") == true)
        }
    }

    @Test
    fun testBadEndpointThrowsForCustom() = runTest {
        val backend = AiBackendFactory.create(
            provider = AiProvider.CUSTOM,
            apiKey = "",
            customEndpoint = "http://insecure-public-server.com/v1"
        )
        try {
            backend.stream(listOf(AiChatMessage.user("Hi")), "custom-model").first()
            fail("Expected BadEndpointException")
        } catch (e: AiBackendException.BadEndpointException) {
            assertTrue(e.endpoint.contains("insecure-public-server.com"))
        }
    }
}
