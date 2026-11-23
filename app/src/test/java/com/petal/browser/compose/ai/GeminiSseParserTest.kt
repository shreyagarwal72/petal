package com.petal.browser.compose.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GeminiSseParserTest {

    private lateinit var parser: GeminiSseParser

    @Before
    fun setUp() {
        parser = GeminiSseParser()
    }

    @Test
    fun testNormalChunks() {
        val chunk1 = "data: {\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"Hello from Gemini!\"}], \"role\": \"model\"}}]}\n\n"
        val chunk2 = "data: {\"candidates\": [{\"content\": {\"parts\": [{\"text\": \" Extra text.\"}], \"role\": \"model\"}}]}\n\n"

        val res1 = parser.processChunk(chunk1)
        val res2 = parser.processChunk(chunk2)
        val finalRes = parser.finish()

        assertEquals(listOf("Hello from Gemini!"), res1)
        assertEquals(listOf(" Extra text."), res2)
        assertTrue(finalRes.isEmpty())
        assertTrue(parser.hasProducedVisibleText)
    }

    @Test
    fun testSplitChunksAcrossReads() {
        val part1 = "data: {\"candidates\": [{\"content\": {\"parts\": [{\"te"
        val part2 = "xt\": \"Split Gemini"
        val part3 = " text\"}], \"role\": \"model\"}}]}\n\n"

        val res1 = parser.processChunk(part1)
        assertTrue(res1.isEmpty())

        val res2 = parser.processChunk(part2)
        assertTrue(res2.isEmpty())

        val res3 = parser.processChunk(part3)
        assertEquals(listOf("Split Gemini text"), res3)

        val finalRes = parser.finish()
        assertTrue(finalRes.isEmpty())
        assertTrue(parser.hasProducedVisibleText)
    }

    @Test
    fun testThoughtFlagDropped() {
        // Gemini 2.0 / 2.5 thinking part with thought: true
        val thoughtChunk = "data: {\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"Thinking steps here...\", \"thought\": true}], \"role\": \"model\"}}]}\n\n"
        val visibleChunk = "data: {\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"Actual answer.\"}], \"role\": \"model\"}}]}\n\n"

        val res1 = parser.processChunk(thoughtChunk)
        val res2 = parser.processChunk(visibleChunk)

        assertTrue(res1.isEmpty())
        assertEquals(listOf("Actual answer."), res2)
    }

    @Test
    fun testMultiplePartsInCandidate() {
        // One thinking part and one visible part in the same candidate
        val mixedChunk = "data: {\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"Thinking step\", \"thought\": true}, {\"text\": \"Clean output\"}], \"role\": \"model\"}}]}\n\n"

        val res = parser.processChunk(mixedChunk)
        assertEquals(listOf("Clean output"), res)
    }

    @Test
    fun testInlineThoughtTagsDropped() {
        val chunk = "data: {\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"<thought>internal logic</thought>Direct response\"}], \"role\": \"model\"}}]}\n\n"
        val res = parser.processChunk(chunk)

        assertEquals(listOf("Direct response"), res)
    }

    @Test
    fun testSseErrorBodyRateLimit() {
        val errorChunk = "data: {\"error\": {\"code\": 429, \"message\": \"Resource has been exhausted\", \"status\": \"RESOURCE_EXHAUSTED\"}}\n\n"
        try {
            parser.processChunk(errorChunk)
            fail("Expected RateLimitException")
        } catch (e: AiBackendException.RateLimitException) {
            assertEquals(429, e.code)
            assertTrue(e.message?.contains("exhausted") == true)
        }
    }

    @Test
    fun testSseErrorBodyServer() {
        val errorChunk = "data: {\"error\": {\"code\": 503, \"message\": \"Service Unavailable\", \"status\": \"UNAVAILABLE\"}}\n\n"
        try {
            parser.processChunk(errorChunk)
            fail("Expected ServerException")
        } catch (e: AiBackendException.ServerException) {
            assertEquals(503, e.code)
        }
    }

    @Test
    fun testSafetyBlockError() {
        val safetyChunk = "data: {\"candidates\": [{\"finishReason\": \"SAFETY\"}], \"promptFeedback\": {\"blockReason\": \"SAFETY\"}}\n\n"
        try {
            parser.processChunk(safetyChunk)
            fail("Expected ClientException for safety block")
        } catch (e: AiBackendException.ClientException) {
            assertTrue(e.message?.contains("safety", ignoreCase = true) == true)
        }
    }

    @Test
    fun testHttpErrorParsing() {
        val err429 = GeminiSseParser.parseHttpError(429, "{\"error\":{\"message\":\"Resource exhausted\"}}")
        assertTrue(err429 is AiBackendException.RateLimitException)

        val err500 = GeminiSseParser.parseHttpError(500, "{\"error\":{\"message\":\"Internal error\"}}")
        assertTrue(err500 is AiBackendException.ServerException)

        val errKey = GeminiSseParser.parseHttpError(400, "{\"error\":{\"message\":\"API_KEY_INVALID\"}}")
        assertTrue(errKey is AiBackendException.MissingApiKeyException)
    }

    @Test
    fun testEmptyReplyThrowsException() {
        val emptyChunk = "data: {\"candidates\": [{\"finishReason\": \"STOP\"}]}\n\n"
        parser.processChunk(emptyChunk)

        try {
            parser.finish()
            fail("Expected EmptyReplyException")
        } catch (e: AiBackendException.EmptyReplyException) {
            assertFalse(parser.hasProducedVisibleText)
        }
    }
}
