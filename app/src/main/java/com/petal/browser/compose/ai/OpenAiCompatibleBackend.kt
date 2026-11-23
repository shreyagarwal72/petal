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
 * Backend implementation for OpenAI-compatible streaming endpoints (Groq and Custom / Ollama / LM Studio / llama.cpp).
 * Uses standard `stream=true` Server-Sent Events (SSE).
 */
class OpenAiCompatibleBackend(
    val provider: AiProvider,
    val endpoint: String,
    private val apiKey: String = "",
    private val httpClient: OkHttpClient = PetalAiResearchEngine.httpClient
) : AiBackend {

    override fun stream(messages: List<AiChatMessage>, model: String): Flow<String> = flow {
        if (apiKey.isBlank() && provider != AiProvider.CUSTOM) {
            throw AiBackendException.MissingApiKeyException(
                "No API key configured for ${provider.displayName}. Please configure an API key in AI Settings."
            )
        }

        if (messages.isEmpty()) {
            throw IllegalArgumentException("Messages list cannot be empty.")
        }

        val targetUrl = when (provider) {
            AiProvider.GROQ -> GROQ_ENDPOINT
            AiProvider.CUSTOM -> normalizeEndpoint(endpoint)
            else -> normalizeEndpoint(endpoint)
        }

        if (!PetalAiResearchEngine.validateEndpoint(targetUrl)) {
            throw AiBackendException.BadEndpointException(targetUrl)
        }

        val payloadJson = buildPayload(messages, model)

        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        val request = requestBuilder
            .post(payloadJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = httpClient.newCall(request)
        val job = currentCoroutineContext().job
        val cancellationHandle = job.invokeOnCompletion {
            call.cancel()
        }

        val parser = OpenAiSseParser()

        try {
            val response = call.execute()
            response.use {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw OpenAiSseParser.parseHttpError(response.code, errorBody)
                }

                val source = response.body?.source()
                    ?: throw AiBackendException.EmptyReplyException("Empty response body from AI endpoint.")

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
                throw CancellationException("AI streaming call was cancelled", e)
            }
            throw AiBackendException.NetworkException(
                message = "Network error connecting to AI endpoint: ${e.message}",
                cause = e
            )
        } finally {
            cancellationHandle.dispose()
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildPayload(messages: List<AiChatMessage>, model: String): JSONObject {
        val payload = JSONObject()
        payload.put("model", model)
        payload.put("stream", true)

        if (provider == AiProvider.GROQ) {
            when (model) {
                "openai/gpt-oss-120b" -> {
                    payload.put("reasoning_effort", "medium")
                    payload.put("include_reasoning", false)
                }
                "qwen/qwen3.6-27b" -> {
                    payload.put("reasoning_effort", "none")
                }
            }
        }

        val messagesArray = JSONArray()
        for (msg in messages) {
            val msgObj = JSONObject()
            msgObj.put("role", msg.role.wireName)

            val imageParts = msg.parts.filterIsInstance<AiMessagePart.Image>()
            if (imageParts.isEmpty()) {
                msgObj.put("content", msg.textContent)
            } else {
                val contentArray = JSONArray()
                for (part in msg.parts) {
                    when (part) {
                        is AiMessagePart.Text -> {
                            contentArray.put(JSONObject().apply {
                                put("type", "text")
                                put("text", part.text)
                            })
                        }
                        is AiMessagePart.Image -> {
                            val imgUrl = JSONObject().apply {
                                put("url", "data:${part.mimeType};base64,${part.base64Data ?: ""}")
                            }
                            contentArray.put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", imgUrl)
                            })
                        }
                    }
                }
                msgObj.put("content", contentArray)
            }
            messagesArray.put(msgObj)
        }
        payload.put("messages", messagesArray)

        return payload
    }

    companion object {
        const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val DEFAULT_CUSTOM_ENDPOINT = "http://localhost:11434/v1"

        /**
         * Normalizes a base URL into an OpenAI `/chat/completions` endpoint.
         */
        fun normalizeEndpoint(endpoint: String): String {
            val base = endpoint.trim().trimEnd('/')
            return when {
                base.endsWith("/chat/completions") -> base
                base.endsWith("/v1") -> "$base/chat/completions"
                else -> "$base/v1/chat/completions"
            }
        }
    }
}
