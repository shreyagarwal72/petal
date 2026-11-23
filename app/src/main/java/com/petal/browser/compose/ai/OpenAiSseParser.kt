package com.petal.browser.compose.ai

import org.json.JSONObject

/**
 * Server-Sent Events (SSE) parser for OpenAI-compatible streaming endpoints (Groq, Custom / Ollama / LM Studio).
 *
 * Responsibilities:
 * - Buffers incoming data streams to handle split lines across read chunks.
 * - Extracts `choices[0].delta.content` chunks.
 * - Drops reasoning chunks (`reasoning_content`, `reasoning`, `thought`).
 * - Filters inline reasoning tags (`<think>...</think>`, `<thought>...</thought>`).
 * - Handles `data: [DONE]`.
 * - Parses SSE-level and HTTP-level error responses.
 * - Enforces empty-reply detection on stream finish.
 */
class OpenAiSseParser {

    private val lineBuffer = StringBuilder()
    private val thinkingFilter = ThinkingTagFilter()

    var hasProducedVisibleText: Boolean = false
        private set

    var isDone: Boolean = false
        private set

    /**
     * Ingests a raw text chunk received from the network.
     *
     * @return List of visible text chunks emitted by complete SSE events.
     */
    fun processChunk(chunk: String): List<String> {
        val extractedLines = appendAndExtractLines(chunk)
        val visibleChunks = mutableListOf<String>()

        for (line in extractedLines) {
            val chunkText = processLine(line)
            if (!chunkText.isNullOrEmpty()) {
                visibleChunks.add(chunkText)
            }
        }
        return visibleChunks
    }

    /**
     * Called when the network response stream ends.
     * Flushes buffered lines and tags, and throws [AiBackendException.EmptyReplyException]
     * if no visible text was received.
     */
    fun finish(): List<String> {
        val finalChunks = mutableListOf<String>()

        val remainingLine = flushLineBuffer()
        if (!remainingLine.isNullOrBlank()) {
            val chunkText = processLine(remainingLine)
            if (!chunkText.isNullOrEmpty()) {
                finalChunks.add(chunkText)
            }
        }

        val tagFlushed = thinkingFilter.finish()
        if (tagFlushed.isNotEmpty()) {
            hasProducedVisibleText = true
            finalChunks.add(tagFlushed)
        }

        if (!hasProducedVisibleText) {
            throw AiBackendException.EmptyReplyException("The model returned an empty response.")
        }

        return finalChunks
    }

    private fun processLine(rawLine: String): String? {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith(":")) {
            // Keep-alive or comment line in SSE
            return null
        }

        if (line.startsWith("data:", ignoreCase = true)) {
            val payload = line.substring(5).trim()
            if (payload == "[DONE]") {
                isDone = true
                return null
            }
            return parsePayloadJson(payload)
        }

        // Handle raw JSON error bodies (e.g. sent without "data:" prefix)
        if (line.startsWith("{") && line.contains("\"error\"")) {
            throw parseHttpError(0, line)
        }

        return null
    }

    private fun parsePayloadJson(payload: String): String? {
        if (payload.isBlank()) return null

        val json = try {
            JSONObject(payload)
        } catch (_: Exception) {
            return null
        }

        // Check for error payload inside SSE
        if (json.has("error")) {
            val errObj = json.optJSONObject("error")
            val code = errObj?.optInt("code", 0) ?: 0
            val msg = errObj?.optString("message") ?: json.optString("error")
            throw parseHttpError(code, json.toString(), msg)
        }

        val choices = json.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null

        val choice = choices.optJSONObject(0) ?: return null
        val delta = choice.optJSONObject("delta") ?: return null

        // Drop reasoning / thinking delta fields
        if (isReasoningDelta(delta)) {
            return null
        }

        val content = delta.optString("content", "")
        if (content.isEmpty()) return null

        val visible = thinkingFilter.filter(content)
        if (visible.isNotEmpty()) {
            hasProducedVisibleText = true
            return visible
        }

        return null
    }

    private fun isReasoningDelta(delta: JSONObject): Boolean {
        // Groq, DeepSeek, Ollama, and llama.cpp delta reasoning attributes
        if (delta.has("reasoning_content") && delta.optString("reasoning_content").isNotEmpty()) return true
        if (delta.has("reasoning") && delta.optString("reasoning").isNotEmpty()) return true
        if (delta.has("thought") && delta.optString("thought").isNotEmpty()) return true
        return false
    }

    private fun appendAndExtractLines(chunk: String): List<String> {
        lineBuffer.append(chunk)
        val lines = mutableListOf<String>()
        var start = 0
        var i = 0
        while (i < lineBuffer.length) {
            val c = lineBuffer[i]
            if (c == '\n') {
                val end = if (i > start && lineBuffer[i - 1] == '\r') i - 1 else i
                lines.add(lineBuffer.substring(start, end))
                start = i + 1
            }
            i++
        }
        if (start > 0) {
            lineBuffer.delete(0, start)
        }
        return lines
    }

    private fun flushLineBuffer(): String? {
        if (lineBuffer.isEmpty()) return null
        var str = lineBuffer.toString()
        if (str.endsWith("\r")) {
            str = str.substring(0, str.length - 1)
        }
        lineBuffer.clear()
        return str.ifEmpty { null }
    }

    companion object {
        /**
         * Maps HTTP error status codes and error response bodies to typed [AiBackendException]s.
         */
        fun parseHttpError(code: Int, body: String, fallbackMessage: String? = null): AiBackendException {
            val parsedMessage = try {
                val json = JSONObject(body)
                val errObj = json.optJSONObject("error")
                errObj?.optString("message") ?: json.optString("message")
            } catch (_: Exception) {
                null
            }

            val finalMessage = parsedMessage?.takeIf { it.isNotBlank() }
                ?: fallbackMessage?.takeIf { it.isNotBlank() }
                ?: body.takeIf { it.isNotBlank() }
                ?: "HTTP $code"

            return when {
                code == 429 || finalMessage.contains("rate limit", ignoreCase = true) ->
                    AiBackendException.RateLimitException(429, body, finalMessage)
                code in 500..599 ->
                    AiBackendException.ServerException(code, body, finalMessage)
                code == 401 || code == 403 || finalMessage.contains("api key", ignoreCase = true) ->
                    AiBackendException.MissingApiKeyException(finalMessage)
                else ->
                    AiBackendException.ClientException(code, body, finalMessage)
            }
        }
    }
}
