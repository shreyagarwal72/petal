package com.petal.browser.compose.ai

import kotlinx.coroutines.flow.Flow

/**
 * Roles for AI chat messages.
 */
enum class AiRole(val wireName: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    companion object {
        fun fromWireName(name: String): AiRole {
            return entries.find { it.wireName.equals(name, ignoreCase = true) } ?: USER
        }
    }
}

/**
 * Content part of an [AiChatMessage].
 * Supports text now and image parts for future multimodal capabilities.
 */
sealed class AiMessagePart {
    data class Text(val text: String) : AiMessagePart()

    data class Image(
        val mimeType: String = "image/jpeg",
        val base64Data: String? = null,
        val bytes: ByteArray? = null
    ) : AiMessagePart() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Image
            if (mimeType != other.mimeType) return false
            if (base64Data != other.base64Data) return false
            if (bytes != null) {
                if (other.bytes == null) return false
                if (!bytes.contentEquals(other.bytes)) return false
            } else if (other.bytes != null) return false
            return true
        }

        override fun hashCode(): Int {
            var result = mimeType.hashCode()
            result = 31 * result + (base64Data?.hashCode() ?: 0)
            result = 31 * result + (bytes?.contentHashCode() ?: 0)
            return result
        }
    }
}

/**
 * Chat message model holding a role and a list of parts.
 */
data class AiChatMessage(
    val role: AiRole,
    val parts: List<AiMessagePart> = emptyList()
) {
    constructor(role: AiRole, text: String) : this(
        role = role,
        parts = listOf(AiMessagePart.Text(text))
    )

    val textContent: String
        get() = parts.filterIsInstance<AiMessagePart.Text>().joinToString("\n") { it.text }

    val text: String
        get() = textContent

    companion object {
        fun system(text: String): AiChatMessage = AiChatMessage(AiRole.SYSTEM, text)
        fun user(text: String): AiChatMessage = AiChatMessage(AiRole.USER, text)
        fun assistant(text: String): AiChatMessage = AiChatMessage(AiRole.ASSISTANT, text)
    }
}

/**
 * Typed exceptions reported by [AiBackend] implementations.
 */
sealed class AiBackendException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    class MissingApiKeyException(
        message: String = "No API key configured for this AI provider. Please configure an API key in AI Settings."
    ) : AiBackendException(message)

    class BadEndpointException(
        val endpoint: String,
        message: String = "Invalid endpoint '$endpoint'. Endpoint must be https:// or an http:// private-LAN address (e.g. localhost, 192.168.x.x)."
    ) : AiBackendException(message)

    class RateLimitException(
        val code: Int = 429,
        val errorBody: String? = null,
        message: String = "Rate limit reached (HTTP 429). Please wait before retrying."
    ) : AiBackendException(message) {
        constructor(message: String) : this(429, null, message)
    }

    class ServerException(
        val code: Int = 500,
        val errorBody: String? = null,
        message: String = "AI server error (HTTP $code)."
    ) : AiBackendException(message) {
        constructor(message: String) : this(500, null, message)
    }

    class NetworkException(
        message: String = "Network error connecting to AI backend.",
        cause: Throwable? = null
    ) : AiBackendException(message, cause)

    class EmptyReplyException(
        message: String = "The model returned an empty response."
    ) : AiBackendException(message)

    class ClientException(
        val code: Int,
        val errorBody: String? = null,
        message: String = "AI client error (HTTP $code)."
    ) : AiBackendException(message)

    class VisionUnsupportedException(
        message: String = "This message includes an image, which needs a vision-capable model (Gemini, or a Custom endpoint model that supports images)."
    ) : AiBackendException(message)
}

/**
 * Common streaming backend interface for Petal AI.
 */
interface AiBackend {
    /**
     * Executes a streaming chat completion call.
     *
     * @param messages Conversation history (system, user, assistant).
     * @param model Model identifier to query.
     * @return A cold Kotlin [Flow] emitting text chunks as they arrive.
     *         Cancelling the collector cancels the underlying HTTP call.
     *         Reports failures via typed [AiBackendException] exceptions.
     */
    fun stream(messages: List<AiChatMessage>, model: String): Flow<String>
}
