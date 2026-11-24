package com.petal.browser.compose.ai

import android.net.Uri
import com.petal.browser.activity.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Isolated YouTube caption transcript extractor.
 *
 * Extracts closed caption tracks from YouTube watch pages via page data or timedtext endpoint.
 * Gracefully reports when transcripts are unavailable, disabled, or when YouTube layout changes.
 */
object PetalYouTubeTranscriptExtractor {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun isYouTubeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.trim().lowercase()
        return lower.contains("youtube.com/watch") || lower.contains("youtu.be/") || lower.contains("m.youtube.com/watch")
    }

    fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(url)
            if (uri.host?.contains("youtu.be") == true) {
                uri.pathSegments.firstOrNull()
            } else {
                uri.getQueryParameter("v")
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * JavaScript to extract caption tracks from the active YouTube page context.
     */
    val CAPTION_TRACK_JS = """
        (function() {
            try {
                var response = window.ytInitialPlayerResponse;
                if (!response && window.ytplayer && window.ytplayer.config && window.ytplayer.config.args) {
                    try {
                        response = JSON.parse(window.ytplayer.config.args.raw_player_response);
                    } catch(e) {}
                }
                if (!response) return JSON.stringify({ error: "No player response" });

                var tracks = response.captions &&
                             response.captions.playerCaptionsTracklistRenderer &&
                             response.captions.playerCaptionsTracklistRenderer.captionTracks;

                if (!tracks || tracks.length === 0) {
                    return JSON.stringify({ error: "No captions available" });
                }

                // Prefer English track or default to the first track
                var selected = tracks[0];
                for (var i = 0; i < tracks.length; i++) {
                    if (tracks[i].languageCode === 'en' || (tracks[i].vssId && tracks[i].vssId.indexOf('.en') !== -1)) {
                        selected = tracks[i];
                        break;
                    }
                }

                return JSON.stringify({
                    baseUrl: selected.baseUrl,
                    name: selected.name ? selected.name.simpleText : 'Transcript',
                    lang: selected.languageCode
                });
            } catch(err) {
                return JSON.stringify({ error: String(err) });
            }
        })();
    """.trimIndent()

    /**
     * Extracts transcript asynchronously from active tab or direct HTTP fallback.
     */
    suspend fun extractTranscript(
        activity: MainActivity,
        url: String
    ): String? = withContext(Dispatchers.IO) {
        if (!isYouTubeUrl(url)) return@withContext null

        var trackUrl: String? = null

        withContext(Dispatchers.Main) {
            activity.evaluateJavascriptInActiveTab(CAPTION_TRACK_JS) { rawResult ->
                if (!rawResult.isNullOrBlank() && rawResult != "null" && rawResult != "undefined") {
                    try {
                        val unquoted = if (rawResult.startsWith("\"") && rawResult.endsWith("\"")) {
                            org.json.JSONTokener(rawResult).nextValue().toString()
                        } else rawResult
                        val obj = JSONObject(unquoted)
                        if (obj.has("baseUrl")) {
                            trackUrl = obj.getString("baseUrl")
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // Wait up to 1.5 seconds for DOM evaluation
        var waited = 0
        while (trackUrl == null && waited < 15) {
            kotlinx.coroutines.delay(100L)
            waited++
        }

        val resolvedUrl = trackUrl ?: return@withContext null

        return@withContext try {
            val req = Request.Builder()
                .url(resolvedUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val resp = httpClient.newCall(req).execute()
            val xml = resp.body?.string().orEmpty()

            // Parse timedtext XML: <text start="X" dur="Y">Words</text>
            val matcher = Pattern.compile("<text[^>]*>([^<]+)</text>").matcher(xml)
            val segments = mutableListOf<String>()
            while (matcher.find()) {
                val txt = matcher.group(1)?.let {
                    android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
                }
                if (!txt.isNullOrBlank()) {
                    segments.add(txt)
                }
            }

            if (segments.isNotEmpty()) {
                segments.joinToString(" ")
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Standalone HTTP transcript extraction without requiring an active MainActivity tab.
     */
    suspend fun extractTranscriptFromUrl(url: String): String? = withContext(Dispatchers.IO) {
        if (!isYouTubeUrl(url)) return@withContext null
        val videoId = extractVideoId(url) ?: return@withContext null
        try {
            val timedTextUrl = "https://www.youtube.com/api/timedtext?v=$videoId&lang=en"
            val req = Request.Builder()
                .url(timedTextUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val resp = httpClient.newCall(req).execute()
            val xml = resp.body?.string().orEmpty()

            val matcher = Pattern.compile("<text[^>]*>([^<]+)</text>").matcher(xml)
            val segments = mutableListOf<String>()
            while (matcher.find()) {
                val txt = matcher.group(1)?.let {
                    android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
                }
                if (!txt.isNullOrBlank()) {
                    segments.add(txt)
                }
            }
            if (segments.isNotEmpty()) segments.joinToString(" ") else null
        } catch (_: Exception) {
            null
        }
    }
}
