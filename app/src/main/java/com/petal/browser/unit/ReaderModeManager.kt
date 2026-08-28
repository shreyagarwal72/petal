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
 * ReaderModeManager uses Readability / Mercury Parser API to extract clean, clutter-free
 * article content (title, lead image, text body) from web pages.
 */
object ReaderModeManager {

    private const val TAG = "ReaderModeManager"
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class ReaderArticle(
        var title: String = "",
        var author: String = "",
        var contentHtml: String = "",
        var leadImageUrl: String = "",
        var domain: String = ""
    )

    fun interface ReaderCallback {
        fun onArticleParsed(article: ReaderArticle?)
    }

    @JvmStatic
    fun parseArticle(targetUrl: String?, callback: ReaderCallback?) {
        if (targetUrl.isNullOrBlank()) {
            callback?.onArticleParsed(null)
            return
        }

        executor.execute {
            var article: ReaderArticle? = null
            var connection: HttpURLConnection? = null
            try {
                val apiUrl = "https://mercury.postlight.com/parser?url=" + URLEncoder.encode(targetUrl, "UTF-8")
                val url = URL(apiUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    reader.close()

                    val json = JSONObject(sb.toString())
                    article = ReaderArticle(
                        title = json.optString("title", ""),
                        author = json.optString("author", ""),
                        contentHtml = json.optString("content", ""),
                        leadImageUrl = json.optString("lead_image_url", ""),
                        domain = json.optString("domain", "")
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching reader article: ${e.message}")
            } finally {
                connection?.disconnect()
            }
            callback?.onArticleParsed(article)
        }
    }
}
