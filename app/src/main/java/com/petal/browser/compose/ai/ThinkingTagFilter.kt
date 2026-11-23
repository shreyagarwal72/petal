package com.petal.browser.compose.ai

import java.util.Locale

/**
 * Stateful filter that removes reasoning/thinking tags (`<think>...</think>` and `<thought>...</thought>`)
 * from streaming text chunks so reasoning tokens are dropped from the visible text.
 *
 * Handles tags that split across chunk boundaries and buffers potential tag prefixes
 * until confirmed or disconfirmed.
 */
class ThinkingTagFilter {

    private var activeTagName: String? = null // "think" or "thought"
    private val tagBuffer = StringBuilder()

    private val supportedTags = listOf("think", "thought")

    /**
     * Filters thinking tags out of [chunk] and returns only visible text.
     */
    fun filter(chunk: String): String {
        if (chunk.isEmpty()) return ""

        val output = StringBuilder()
        var i = 0
        while (i < chunk.length) {
            val c = chunk[i]
            if (activeTagName != null) {
                // Inside an active thinking tag block. Looking for closing tag: </activeTagName>
                val closingTag = "</$activeTagName>"
                tagBuffer.append(c)
                val current = tagBuffer.toString().lowercase(Locale.ROOT)
                if (closingTag.startsWith(current)) {
                    if (current == closingTag) {
                        // Closing tag matched! Exit thinking mode.
                        activeTagName = null
                        tagBuffer.clear()
                    }
                    // Still potentially matching closing tag; continue buffering.
                } else {
                    // Mismatch while searching for closing tag. Discard buffer and stay in thinking mode.
                    tagBuffer.clear()
                    if (c == '<') {
                        tagBuffer.append(c)
                    }
                }
            } else {
                // Outside thinking mode. Look for opening tag <think> or <thought>
                if (tagBuffer.isNotEmpty() || c == '<') {
                    tagBuffer.append(c)
                    val current = tagBuffer.toString().lowercase(Locale.ROOT)
                    val matchedTag = supportedTags.find { "<$it>" == current }
                    if (matchedTag != null) {
                        // Opening tag matched! Enter thinking mode and drop the tag.
                        activeTagName = matchedTag
                        tagBuffer.clear()
                    } else {
                        val isPrefixOfAny = supportedTags.any { "<$it>".startsWith(current) }
                        if (!isPrefixOfAny) {
                            // Diverged from any known opening tag prefix: flush buffered text as visible.
                            output.append(tagBuffer.toString())
                            tagBuffer.clear()
                        }
                    }
                } else {
                    output.append(c)
                }
            }
            i++
        }
        return output.toString()
    }

    /**
     * Flushes any remaining characters buffered at the end of stream if not inside a thinking tag.
     */
    fun finish(): String {
        val remaining = if (activeTagName == null && tagBuffer.isNotEmpty()) {
            tagBuffer.toString()
        } else {
            ""
        }
        tagBuffer.clear()
        return remaining
    }
}
