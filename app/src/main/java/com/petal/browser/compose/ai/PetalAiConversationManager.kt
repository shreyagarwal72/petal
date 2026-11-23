package com.petal.browser.compose.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Single chat message within a Petal AI conversation.
 */
data class PetalChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: AiRole,
    val text: String,
    val imagePart: AiMessagePart.Image? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val error: String? = null
)

/**
 * Manages per-tab conversation histories for Petal AI chat.
 *
 * Responsibilities:
 * - Maintains 1 independent conversation history per tab ID.
 * - Caps history to [maxTurns] pairs (user + assistant turns).
 * - Manages streaming state and OkHttp job cancellation for the stop button.
 * - Allows clearing conversation ("New Chat") and regenerating the last response.
 * - Drops conversation state when tabs are closed.
 */
class PetalAiConversationManager(
    val maxTurns: Int = 20
) {
    private val _conversations = MutableStateFlow<Map<String, List<PetalChatMessage>>>(emptyMap())
    val conversations: StateFlow<Map<String, List<PetalChatMessage>>> = _conversations.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    private val _streamingTabs = MutableStateFlow<Set<String>>(emptySet())
    val streamingTabs: StateFlow<Set<String>> = _streamingTabs.asStateFlow()

    private val _tabErrors = MutableStateFlow<Map<String, Throwable?>>(emptyMap())
    val tabErrors: StateFlow<Map<String, Throwable?>> = _tabErrors.asStateFlow()

    fun getMessages(tabId: String): List<PetalChatMessage> {
        return _conversations.value[tabId].orEmpty()
    }

    fun isStreaming(tabId: String): Boolean {
        return _streamingTabs.value.contains(tabId)
    }

    fun getError(tabId: String): Throwable? {
        return _tabErrors.value[tabId]
    }

    fun setMessagesForTab(tabId: String, messages: List<PetalChatMessage>) {
        val maxHistorySize = maxTurns * 2
        val capped = if (messages.size > maxHistorySize) messages.takeLast(maxHistorySize) else messages
        _conversations.value = _conversations.value + (tabId to capped)
    }

    fun addMessage(tabId: String, message: PetalChatMessage) {
        val current = _conversations.value[tabId].orEmpty()
        val maxHistorySize = maxTurns * 2
        val updated = (current + message).let {
            if (it.size > maxHistorySize) it.takeLast(maxHistorySize) else it
        }
        _conversations.value = _conversations.value + (tabId to updated)
    }

    fun clearError(tabId: String) {
        _tabErrors.value = _tabErrors.value - tabId
    }

    /**
     * Clears conversation history for [tabId] (New chat).
     */
    fun clearConversation(tabId: String) {
        stopStreaming(tabId)
        _conversations.value = _conversations.value - tabId
        _tabErrors.value = _tabErrors.value - tabId
    }

    /**
     * Drops conversation history when a tab is closed.
     */
    fun dropConversation(tabId: String) {
        clearConversation(tabId)
    }

    fun dropTab(tabId: String) = dropConversation(tabId)

    /**
     * Prunes conversations for tabs that are no longer active.
     */
    fun pruneClosedTabs(activeTabIds: Set<String>) {
        val obsolete = _conversations.value.keys - activeTabIds
        for (id in obsolete) {
            dropConversation(id)
        }
    }

    /**
     * Cancels any active streaming generation on [tabId].
     */
    fun stopStreaming(tabId: String) {
        activeJobs[tabId]?.cancel()
        activeJobs.remove(tabId)
        _streamingTabs.value = _streamingTabs.value - tabId

        val currentList = _conversations.value[tabId] ?: return
        val updatedList = currentList.map {
            if (it.isStreaming) it.copy(isStreaming = false) else it
        }
        _conversations.value = _conversations.value + (tabId to updatedList)
    }

    /**
     * Sends a new user query and streams the assistant response using [backend].
     */
    fun sendMessage(
        tabId: String,
        userText: String,
        backend: AiBackend,
        model: String,
        scope: CoroutineScope,
        systemPrompt: String? = null,
        imagePart: AiMessagePart.Image? = null,
        onComplete: ((inputTokens: Int, outputTokens: Int) -> Unit)? = null
    ) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() && imagePart == null) return

        stopStreaming(tabId)
        clearError(tabId)

        val userMessage = PetalChatMessage(
            role = AiRole.USER,
            text = trimmed,
            imagePart = imagePart
        )
        val assistantMessageId = UUID.randomUUID().toString()
        val assistantPlaceholder = PetalChatMessage(
            id = assistantMessageId,
            role = AiRole.ASSISTANT,
            text = "",
            isStreaming = true
        )

        val previous = _conversations.value[tabId].orEmpty()
        // Cap conversation history: maxTurns * 2 (user + assistant turns)
        val maxHistorySize = maxTurns * 2
        val cappedPrevious = if (previous.size > maxHistorySize) {
            previous.takeLast(maxHistorySize)
        } else {
            previous
        }

        val updatedHistory = cappedPrevious + userMessage + assistantPlaceholder
        _conversations.value = _conversations.value + (tabId to updatedHistory)
        _streamingTabs.value = _streamingTabs.value + tabId

        // Build messages payload for backend with optional system prompt
        val historyMessages = updatedHistory
            .filter { it.id != assistantMessageId && (it.text.isNotBlank() || it.imagePart != null) }
            .map { msg ->
                val parts = mutableListOf<AiMessagePart>()
                if (msg.text.isNotBlank()) {
                    parts.add(AiMessagePart.Text(msg.text))
                }
                if (msg.imagePart != null) {
                    parts.add(msg.imagePart)
                }
                AiChatMessage(role = msg.role, parts = parts)
            }

        val messagesForBackend = if (!systemPrompt.isNullOrBlank()) {
            listOf(AiChatMessage.system(systemPrompt)) + historyMessages
        } else {
            historyMessages
        }

        val job = scope.launch {
            try {
                backend.stream(messagesForBackend, model).collect { chunk ->
                    val list = _conversations.value[tabId].orEmpty()
                    val modified = list.map { msg ->
                        if (msg.id == assistantMessageId) {
                            msg.copy(text = msg.text + chunk, isStreaming = true)
                        } else msg
                    }
                    _conversations.value = _conversations.value + (tabId to modified)
                }

                // Streaming finished cleanly
                val list = _conversations.value[tabId].orEmpty()
                val finalized = list.map { msg ->
                    if (msg.id == assistantMessageId) {
                        msg.copy(isStreaming = false)
                    } else msg
                }
                _conversations.value = _conversations.value + (tabId to finalized)

                val inChars = messagesForBackend.sumOf { it.textContent.length }
                val outChars = finalized.firstOrNull { it.id == assistantMessageId }?.text?.length ?: 0
                val inTok = (inChars / 4).coerceAtLeast(1)
                val outTok = (outChars / 4).coerceAtLeast(1)
                onComplete?.invoke(inTok, outTok)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _tabErrors.value = _tabErrors.value + (tabId to e)
                    val list = _conversations.value[tabId].orEmpty()
                    val withError = list.map { msg ->
                        if (msg.id == assistantMessageId) {
                            msg.copy(
                                isStreaming = false,
                                error = e.localizedMessage ?: e.message ?: "Request failed"
                            )
                        } else msg
                    }
                    _conversations.value = _conversations.value + (tabId to withError)
                }
            } finally {
                _streamingTabs.value = _streamingTabs.value - tabId
                activeJobs.remove(tabId)
            }
        }
        activeJobs[tabId] = job
    }

    /**
     * Regenerates the last assistant message in the conversation.
     */
    fun regenerateLastMessage(
        tabId: String,
        backend: AiBackend,
        model: String,
        scope: CoroutineScope,
        systemPrompt: String? = null,
        onComplete: ((inputTokens: Int, outputTokens: Int) -> Unit)? = null
    ) {
        val current = _conversations.value[tabId].orEmpty()
        if (current.isEmpty()) return

        val lastMsg = current.last()
        val userMsg = if (lastMsg.role == AiRole.ASSISTANT) {
            current.dropLast(1).lastOrNull { it.role == AiRole.USER }
        } else if (lastMsg.role == AiRole.USER) {
            lastMsg
        } else null

        if (userMsg == null) return

        // Drop assistant reply and user message so sendMessage can append them fresh
        val withoutAssistant = if (lastMsg.role == AiRole.ASSISTANT) {
            current.dropLast(1)
        } else {
            current
        }
        val cleanHistory = withoutAssistant.dropLast(1)
        _conversations.value = _conversations.value + (tabId to cleanHistory)

        sendMessage(
            tabId = tabId,
            userText = userMsg.text,
            backend = backend,
            model = model,
            scope = scope,
            systemPrompt = systemPrompt,
            imagePart = userMsg.imagePart,
            onComplete = onComplete
        )
    }
}
