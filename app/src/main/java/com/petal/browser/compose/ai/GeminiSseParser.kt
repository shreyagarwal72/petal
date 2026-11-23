package com.petal.browser.compose.ai

import org.json.JSONObject

/**
 * Server-Sent Events (SSE) parser for Google Gemini `streamGenerateContent?alt=sse`.
 *
 * Responsibilities:
 * - Buffers incoming data streams to handle split lines across read chunks.
 * - Extracts `candidates[0].content.parts[].text` chunks.
 * - Drops thinking parts (e.g. `thought: true` or `thought` fields).
 * - Filters inline reasoning tags (`<think>...</think>`, `<thought>...</thought>`).
 * - Parses SSE-level error objects and safety block notifications.
 * - Enforces empty-reply detection on stream finish.
 */
class GeminiSseParser {

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
            val chunks = processLine(line)
            visibleChunks.addAll(chunks)
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
            finalChunks.addAll(processLine(remainingLine))
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

    private fun processLine(rawLine: String): List<String> {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith(":")) {
            // Keep-alive or comment line in SSE
            return emptyList()
        }

        if (line.startsWith("data:", ignoreCase = true)) {
            val payload = line.substring(5).trim()
            if (payload == "[DONE]") {
                isDone = true
                return emptyList()
            }
            return parsePayloadJson(payload)
        }

        // Handle raw JSON error bodies (e.g. sent without "data:" prefix)
        if (line.startsWith("{") && line.contains("\"error\"")) {
            throw parseHttpError(0, line)
        }

        return emptyList()
    }

    private fun parsePayloadJson(payload: String): List<String> {
        if (payload.isBlank()) return emptyList()

        val json = try {
            JSONObject(payload)
        } catch (_: Exception) {
            return emptyList()
        }

        // Check for Gemini API error object
        if (json.has("error")) {
            val errObj = json.optJSONObject("error")
            val code = errObj?.optInt("code", 400) ?: 400
            val msg = errObj?.optString("message") ?: json.optString("error")
            throw parseHttpError(code, json.toString(), msg)
        }

        // Check for promptFeedback block
        val promptFeedback = json.optJSONObject("promptFeedback")
        val blockReason = promptFeedback?.optString("blockReason")
        if (!blockReason.isNullOrBlank()) {
            throw AiBackendException.ClientException(400, json.toString(), "Prompt blocked by Gemini safety filters: $blockReason")
        }

        val candidates = json.optJSONArray("candidates") ?: return emptyList()
        if (candidates.length() == 0) return emptyList()

        val candidate = candidates.optJSONObject(0) ?: return emptyList()
        val finishReason = candidate.optString("finishReason")
        if (finishReason.equals("SAFETY", ignoreCase = true)) {
            throw AiBackendException.ClientException(400, json.toString(), "Response blocked by Gemini safety filters.")
        }

        val content = candidate.optJSONObject("content") ?: return emptyList()
        val parts = content.optJSONArray("parts") ?: return emptyList()

        val emitted = mutableListOf<String>()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue

            // Drop thinking / thought parts
            if (isThoughtPart(part)) {
                continue
            }

            val text = part.optString("text", "")
            if (text.isNotEmpty()) {
                val visible = thinkingFilter.filter(text)
                if (visible.isNotEmpty()) {
                    hasProducedVisibleText = true
                    emitted.add(visible)
                }
            }
        }

        return emitted
    }

    private fun isThoughtPart(part: JSONObject): Boolean {
        if (part.optBoolean("thought", false)) return true
        if (part.has("thought") && part.opt("thought") != false && part.optString("thought") != "false") return true
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
                code == 429 || finalMessage.contains("resource exhausted", ignoreCase = true) ||
                        finalMessage.contains("rate limit", ignoreCase = true) ->
                    AiBackendException.RateLimitException(429, body, finalMessage)
                code in 500..599 ->
                    AiBackendException.ServerException(code, body, finalMessage)
                code == 400 && (finalMessage.contains("api key", ignoreCase = true) ||
                        finalMessage.contains("API_KEY_INVALID", ignoreCase = true)) ->
                    AiBackendException.MissingApiKeyException(finalMessage)
                code == 401 || code == 403 ->
                    AiBackendException.MissingApiKeyException(finalMessage)
                else ->
                    AiBackendException.ClientException(code, body, finalMessage)
            }
        }
    }
}
