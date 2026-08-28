package com.petal.browser.unit

import kotlin.math.ceil
import kotlin.math.max

/**
 * ReadingTimeEstimator calculates estimated reading duration and total word count for articles.
 */
object ReadingTimeEstimator {

    class ReadingStats(var wordCount: Int, var minutesRead: Int)

    /**
     * Estimates reading time assuming average reading speed of 200 words per minute.
     */
    @JvmStatic
    fun calculateReadingStats(textBody: String?): ReadingStats {
        if (textBody.isNullOrBlank()) return ReadingStats(0, 0)

        val cleanText = textBody.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        if (cleanText.isEmpty()) return ReadingStats(0, 0)

        val words = cleanText.split(" ").filter { it.isNotEmpty() }
        val wordCount = words.size
        val minutes = ceil(wordCount.toDouble() / 200.0).toInt()

        return ReadingStats(wordCount, max(1, minutes))
    }
}
