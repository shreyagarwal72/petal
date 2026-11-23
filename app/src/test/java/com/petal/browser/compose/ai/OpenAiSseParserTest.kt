package com.petal.browser.compose.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OpenAiSseParserTest {

    private lateinit var parser: OpenAiSseParser

    @Before
    fun setUp() {
        parser = OpenAiSseParser()
    }

    @Test
    fun testNormalChunks() {
        val chunk1 = "data: {\"choices\": [{\"delta\": {\"content\": \"Hello \"}}]}\n\n"
        val chunk2 = "data: {\"choices\": [{\"delta\": {\"content\": \"world!\"}}]}\n\n"
        val chunk3 = "data: [DONE]\n\n"

        val res1 = parser.processChunk(chunk1)
        val res2 = parser.processChunk(chunk2)
        val res3 = parser.processChunk(chunk3)
        val finalRes = parser.finish()

        assertEquals(listOf("Hello "), res1)
        assertEquals(listOf("world!"), res2)
        assertTrue(res3.isEmpty())
        assertTrue(finalRes.isEmpty())
        assertTrue(parser.hasProducedVisibleText)
        assertTrue(parser.isDone)
    }

    @Test
    fun testSplitChunksAcrossReads() {
        // Line split right in the middle of data line
        val part1 = "data: {\"choices\": [{\"del"
        val part2 = "ta\": {\"content\": \"Spl"
        val part3 = "it chunk\"}}]}\n\n"

        val res1 = parser.processChunk(part1)
        assertTrue(res1.isEmpty())

        val res2 = parser.processChunk(part2)
        assertTrue(res2.isEmpty())

        val res3 = parser.processChunk(part3)
        assertEquals(listOf("Split chunk"), res3)

        val finalRes = parser.finish()
        assertTrue(finalRes.isEmpty())
        assertTrue(parser.hasProducedVisibleText)
    }

    @Test
    fun testReasoningDeltasDropped() {
        // Various reasoning fields used by Groq, DeepSeek, and llama.cpp
        val chunkReasoning1 = "data: {\"choices\": [{\"delta\": {\"reasoning_content\": \"Thinking step 1...\"}}]}\n\n"
        val chunkReasoning2 = "data: {\"choices\": [{\"delta\": {\"reasoning\": \"Thinking step 2...\"}}]}\n\n"
        val chunkReasoning3 = "data: {\"choices\": [{\"delta\": {\"thought\": \"Thinking step 3...\"}}]}\n\n"
        val chunkVisible = "data: {\"choices\": [{\"delta\": {\"content\": \"Final answer.\"}}]}\n\n"

        val res1 = parser.processChunk(chunkReasoning1)
        val res2 = parser.processChunk(chunkReasoning2)
        val res3 = parser.processChunk(chunkReasoning3)
        val res4 = parser.processChunk(chunkVisible)

        assertTrue(res1.isEmpty())
        assertTrue(res2.isEmpty())
        assertTrue(res3.isEmpty())
        assertEquals(listOf("Final answer."), res4)
    }

    @Test
    fun testInlineThinkingTagsDropped() {
        // Models streaming <think> tags directly inside content
        val chunk1 = "data: {\"choices\": [{\"delta\": {\"content\": \"<think>pondering problem</think>Result: \"}}]}\n\n"
        val chunk2 = "data: {\"choices\": [{\"delta\": {\"content\": \"42\"}}]}\n\n"

        val res1 = parser.processChunk(chunk1)
        val res2 = parser.processChunk(chunk2)

        assertEquals(listOf("Result: "), res1)
        assertEquals(listOf("42"), res2)
    }

    @Test
    fun testInlineThinkingTagsSplitAcrossChunks() {
        // <think> tag split across chunk boundaries
        val chunk1 = "data: {\"choices\": [{\"delta\": {\"content\": \"<thi\"}}]}\n\n"
        val chunk2 = "data: {\"choices\": [{\"delta\": {\"content\": \"nk>internal thought</thi\"}}]}\n\n"
        val chunk3 = "data: {\"choices\": [{\"delta\": {\"content\": \"nk>Visible text\"}}]}\n\n"

        val res1 = parser.processChunk(chunk1)
        val res2 = parser.processChunk(chunk2)
        val res3 = parser.processChunk(chunk3)

        assertTrue(res1.isEmpty())
        assertTrue(res2.isEmpty())
        assertEquals(listOf("Visible text"), res3)
    }

    @Test
    fun testDoneHandling() {
        val chunk = "data: [DONE]\n\n"
        val res = parser.processChunk(chunk)

        assertTrue(res.isEmpty())
        assertTrue(parser.isDone)
    }

    @Test
    fun testSseErrorBodyRateLimit() {
        val errorChunk = "data: {\"error\": {\"message\": \"Rate limit exceeded\", \"code\": 429}}\n\n"
        try {
            parser.processChunk(errorChunk)
            fail("Expected RateLimitException")
        } catch (e: AiBackendException.RateLimitException) {
            assertEquals(429, e.code)
            assertTrue(e.message?.contains("Rate limit") == true)
        }
    }

    @Test
    fun testSseErrorBodyServer() {
        val errorChunk = "data: {\"error\": {\"message\": \"Internal Server Error\", \"code\": 500}}\n\n"
        try {
            parser.processChunk(errorChunk)
            fail("Expected ServerException")
        } catch (e: AiBackendException.ServerException) {
            assertEquals(500, e.code)
        }
    }

    @Test
    fun testRawJsonErrorBodyWithoutDataPrefix() {
        val errorBody = "{\"error\": {\"message\": \"Invalid API key provided\", \"code\": 401}}\n"
        try {
            parser.processChunk(errorBody)
            fail("Expected MissingApiKeyException or ClientException")
        } catch (e: AiBackendException.MissingApiKeyException) {
            assertTrue(e.message?.contains("API key") == true)
        } catch (e: AiBackendException.ClientException) {
            assertTrue(e.message?.contains("API key") == true)
        }
    }

    @Test
    fun testHttpErrorParsing() {
        val err429 = OpenAiSseParser.parseHttpError(429, "{\"error\":{\"message\":\"Too many requests\"}}")
        assertTrue(err429 is AiBackendException.RateLimitException)

        val err503 = OpenAiSseParser.parseHttpError(503, "{\"error\":{\"message\":\"Service unavailable\"}}")
        assertTrue(err503 is AiBackendException.ServerException)

        val err401 = OpenAiSseParser.parseHttpError(401, "{\"error\":{\"message\":\"Incorrect API key\"}}")
        assertTrue(err401 is AiBackendException.MissingApiKeyException)
    }

    @Test
    fun testEmptyReplyThrowsException() {
        // Stream completed with only [DONE] and no content chunks
        parser.processChunk("data: [DONE]\n\n")
        try {
            parser.finish()
            fail("Expected EmptyReplyException")
        } catch (e: AiBackendException.EmptyReplyException) {
            assertFalse(parser.hasProducedVisibleText)
        }
    }

    @Test
    fun testOnlyReasoningChunksThrowsEmptyReplyException() {
        // Stream completed with only reasoning content and no visible text
        parser.processChunk("data: {\"choices\": [{\"delta\": {\"reasoning_content\": \"Thinking...\"}}]}\n\n")
        parser.processChunk("data: [DONE]\n\n")
        try {
            parser.finish()
            fail("Expected EmptyReplyException")
        } catch (e: AiBackendException.EmptyReplyException) {
            assertFalse(parser.hasProducedVisibleText)
        }
    }

    @Test
    fun testCommentLinesIgnored() {
        val pingChunk = ": ping\n\ndata: {\"choices\": [{\"delta\": {\"content\": \"Live\"}}]}\n\n"
        val res = parser.processChunk(pingChunk)
        assertEquals(listOf("Live"), res)
    }
}
