package com.petal.browser.compose.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Backend implementation for Google Gemini streaming using `streamGenerateContent?alt=sse`.
 */
class GeminiBackend(
    private val apiKey: String,
    private val httpClient: OkHttpClient = PetalAiResearchEngine.httpClient
) : AiBackend {

    override fun stream(messages: List<AiChatMessage>, model: String): Flow<String> = flow {
        if (apiKey.isBlank()) {
            throw AiBackendException.MissingApiKeyException(
                "No API key configured for Google Gemini. Please configure an API key in AI Settings."
            )
        }

        if (messages.isEmpty()) {
            throw IllegalArgumentException("Messages list cannot be empty.")
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
        val payloadJson = buildPayload(messages)

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(payloadJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = httpClient.newCall(request)
        val job = currentCoroutineContext().job
        val cancellationHandle = job.invokeOnCompletion {
            call.cancel()
        }

        val parser = GeminiSseParser()

        try {
            val response = call.execute()
            response.use {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw GeminiSseParser.parseHttpError(response.code, errorBody)
                }

                val source = response.body?.source()
                    ?: throw AiBackendException.EmptyReplyException("Empty response body from Gemini API.")

                val buffer = ByteArray(8192)
                val inputStream = source.inputStream()
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    val chunkText = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    val visibleChunks = parser.processChunk(chunkText)
                    for (chunk in visibleChunks) {
                        emit(chunk)
                    }
                }

                val finalChunks = parser.finish()
                for (chunk in finalChunks) {
                    emit(chunk)
                }
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: IOException) {
            if (job.isCancelled || call.isCanceled()) {
                throw CancellationException("Gemini streaming call was cancelled", e)
            }
            throw AiBackendException.NetworkException(
                message = "Network error connecting to Gemini API: ${e.message}",
                cause = e
            )
        } finally {
            cancellationHandle.dispose()
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildPayload(messages: List<AiChatMessage>): JSONObject {
        val payload = JSONObject()

        val systemMessages = messages.filter { it.role == AiRole.SYSTEM }
        val nonSystemMessages = messages.filter { it.role != AiRole.SYSTEM }

        if (systemMessages.isNotEmpty()) {
            val combinedSystemText = systemMessages.joinToString("\n\n") { it.textContent }
            if (combinedSystemText.isNotBlank()) {
                val systemParts = JSONArray().put(JSONObject().put("text", combinedSystemText))
                payload.put("systemInstruction", JSONObject().put("parts", systemParts))
            }
        }

        val effectiveMessages = if (nonSystemMessages.isEmpty() && systemMessages.isNotEmpty()) {
            listOf(AiChatMessage.user(systemMessages.joinToString("\n\n") { it.textContent }))
        } else {
            nonSystemMessages
        }

        val contentsArray = JSONArray()
        for (msg in effectiveMessages) {
            val msgObj = JSONObject()
            val role = if (msg.role == AiRole.ASSISTANT) "model" else "user"
            msgObj.put("role", role)

            val partsArray = JSONArray()
            for (part in msg.parts) {
                when (part) {
                    is AiMessagePart.Text -> {
                        partsArray.put(JSONObject().put("text", part.text))
                    }
                    is AiMessagePart.Image -> {
                        val inlineData = JSONObject().apply {
                            put("mimeType", part.mimeType)
                            put("data", part.base64Data ?: "")
                        }
                        partsArray.put(JSONObject().put("inlineData", inlineData))
                    }
                }
            }
            if (partsArray.length() == 0) {
                partsArray.put(JSONObject().put("text", ""))
            }
            msgObj.put("parts", partsArray)
            contentsArray.put(msgObj)
        }
        payload.put("contents", contentsArray)

        return payload
    }
}
