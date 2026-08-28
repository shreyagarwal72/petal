package com.petal.browser.unit

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * WikipediaSummaryManager fetches instant article summaries, thumbnails, and descriptions
 * for terms typed into the search bar or long-pressed on web pages.
 */
object WikipediaSummaryManager {

    private const val TAG = "WikipediaSummaryManager"
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class WikiSummary(
        var title: String = "",
        var description: String = "",
        var extract: String = "",
        var thumbnailUrl: String = "",
        var articleUrl: String = ""
    )

    fun interface WikiSummaryCallback {
        fun onSummaryFetched(summary: WikiSummary?)
    }

    @JvmStatic
    fun fetchSummary(term: String?, callback: WikiSummaryCallback?) {
        if (term.isNullOrBlank()) {
            callback?.onSummaryFetched(null)
            return
        }

        executor.execute {
            var summary: WikiSummary? = null
            var connection: HttpURLConnection? = null
            try {
                val encodedTerm = URLEncoder.encode(term.trim().replace(" ", "_"), "UTF-8")
                val apiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTerm"
                val url = URL(apiUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 4000
                connection.readTimeout = 4000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    reader.close()

                    val json = JSONObject(sb.toString())
                    val title = json.optString("title", "")
                    val description = json.optString("description", "")
                    val extract = json.optString("extract", "")

                    var thumbUrl = ""
                    val thumbObj = json.optJSONObject("thumbnail")
                    if (thumbObj != null) {
                        thumbUrl = thumbObj.optString("source", "")
                    }

                    var pageUrl = ""
                    val contentUrls = json.optJSONObject("content_urls")
                    if (contentUrls != null) {
                        val desktopObj = contentUrls.optJSONObject("desktop")
                        if (desktopObj != null) {
                            pageUrl = desktopObj.optString("page", "")
                        }
                    }

                    summary = WikiSummary(title, description, extract, thumbUrl, pageUrl)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching Wiki summary: ${e.message}")
            } finally {
                connection?.disconnect()
            }
            callback?.onSummaryFetched(summary)
        }
    }
}
