package com.petal.browser.media.subtitle

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Data model for a single subtitle caption entry.
 */
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * Petal Subtitle Model & Parser
 * Supports WebVTT (.vtt) and SubRip (.srt) formats with time offset shift.
 */
object PetalSubtitleEngine {
    private const val TAG = "PetalSubtitleEngine"

    suspend fun parseFromUri(context: Context, uri: Uri): List<SubtitleCue> = withContext(Dispatchers.IO) {
        val cues = mutableListOf<SubtitleCue>()
        try {
            val inputStream: InputStream? = if (uri.scheme == "http" || uri.scheme == "https") {
                val url = java.net.URL(uri.toString())
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.inputStream
            } else {
                context.contentResolver.openInputStream(uri)
            }

            if (inputStream == null) return@withContext emptyList()

            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                val lines = reader.readLines()
                if (lines.any { it.trim().startsWith("WEBVTT") }) {
                    cues.addAll(parseWebVtt(lines))
                } else {
                    cues.addAll(parseSrt(lines))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse subtitle from $uri", e)
        }
        return@withContext cues.sortedBy { it.startMs }
    }

    suspend fun parseFromString(content: String): List<SubtitleCue> = withContext(Dispatchers.Default) {
        val lines = content.lines()
        val cues = if (lines.any { it.trim().startsWith("WEBVTT") }) {
            parseWebVtt(lines)
        } else {
            parseSrt(lines)
        }
        cues.sortedBy { it.startMs }
    }

    private fun parseSrt(lines: List<String>): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val timePattern = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.all { it.isDigit() }) {
                i++
                continue
            }
            val matcher = timePattern.matcher(line)
            if (matcher.find()) {
                val startMs = parseTime(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4))
                val endMs = parseTime(matcher.group(5), matcher.group(6), matcher.group(7), matcher.group(8))
                i++
                val textBuilder = StringBuilder()
                while (i < lines.size && lines[i].trim().isNotEmpty()) {
                    if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                    textBuilder.append(stripHtmlTags(lines[i].trim()))
                    i++
                }
                cues.add(SubtitleCue(startMs, endMs, textBuilder.toString()))
            } else {
                i++
            }
        }
        return cues
    }

    private fun parseWebVtt(lines: List<String>): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val timePattern = Pattern.compile("(?:(\\d{2}):)?(\\d{2}):(\\d{2})\\.(\\d{3})\\s*-->\\s*(?:(\\d{2}):)?(\\d{2}):(\\d{2})\\.(\\d{3})")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("WEBVTT") || line.startsWith("NOTE")) {
                i++
                continue
            }
            val matcher = timePattern.matcher(line)
            if (matcher.find()) {
                val sH = matcher.group(1) ?: "00"
                val sM = matcher.group(2) ?: "00"
                val sS = matcher.group(3) ?: "00"
                val sMs = matcher.group(4) ?: "000"

                val eH = matcher.group(5) ?: "00"
                val eM = matcher.group(6) ?: "00"
                val eS = matcher.group(7) ?: "00"
                val eMs = matcher.group(8) ?: "000"

                val startMs = parseTime(sH, sM, sS, sMs)
                val endMs = parseTime(eH, eM, eS, eMs)
                i++
                val textBuilder = StringBuilder()
                while (i < lines.size && lines[i].trim().isNotEmpty()) {
                    if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                    textBuilder.append(stripHtmlTags(lines[i].trim()))
                    i++
                }
                cues.add(SubtitleCue(startMs, endMs, textBuilder.toString()))
            } else {
                i++
            }
        }
        return cues
    }

    private fun parseTime(h: String, m: String, s: String, ms: String): Long {
        return h.toLong() * 3600000L + m.toLong() * 60000L + s.toLong() * 1000L + ms.toLong()
    }

    private fun stripHtmlTags(input: String): String {
        return input.replace(Regex("<[^>]*>"), "")
    }

    /**
     * Find active cue for current playback timestamp taking offsetMs into account.
     */
    fun getActiveCue(cues: List<SubtitleCue>, currentMs: Long, offsetMs: Long): SubtitleCue? {
        val shiftedMs = currentMs - offsetMs
        return cues.firstOrNull { shiftedMs in it.startMs..it.endMs }
    }
}
