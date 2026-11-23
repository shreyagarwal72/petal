package com.petal.browser.compose.ai.ondevice

import android.content.Context
import com.petal.browser.compose.ai.AiBackend
import com.petal.browser.compose.ai.AiChatMessage
import com.petal.browser.compose.ai.ThinkingTagFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * AiBackend implementation executing on-device Gemma inference.
 */
class OnDeviceGemmaBackend(
    private val context: Context
) : AiBackend {

    override fun stream(messages: List<AiChatMessage>, model: String): Flow<String> = flow {
        val filter = ThinkingTagFilter()
        PetalOnDeviceLlmEngine.streamChat(context, messages, model).collect { rawChunk ->
            val filtered = filter.filter(rawChunk)
            if (filtered.isNotEmpty()) {
                emit(filtered)
            }
        }
        val remaining = filter.finish()
        if (remaining.isNotEmpty()) {
            emit(remaining)
        }
    }
}
