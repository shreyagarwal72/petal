/*
 * PetalNewsManager.kt
 * ──────────────────────────────────────────────────────────────────────────
 * Brave News-style privacy-respecting RSS feed aggregator.
 * Fetches from curated public feeds with no tracking referrer headers.
 * Parses RSS 2.0 and Atom feeds using Android's built-in XmlPullParser.
 */
package com.petal.browser.home.news

import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

data class NewsArticle(
    val title: String,
    val url: String,
    val source: String,
    val publishedAt: String = "",
    val description: String = ""
)

object PetalNewsManager {

    private const val TAG = "PetalNewsManager"

    /** Privacy-respecting curated public RSS feeds — no tracking params */
    private val FEEDS = listOf(
        "https://feeds.bbci.co.uk/news/world/rss.xml"       to "BBC World",
        "https://feeds.reuters.com/reuters/topNews"          to "Reuters",
        "https://www.theverge.com/rss/index.xml"            to "The Verge",
        "https://feeds.arstechnica.com/arstechnica/index"   to "Ars Technica",
        "https://news.ycombinator.com/rss"                  to "Hacker News",
        "https://www.wired.com/feed/rss"                    to "WIRED",
        "https://techcrunch.com/feed/"                      to "TechCrunch",
        "https://www.androidauthority.com/feed/"            to "Android Authority",
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .removeHeader("Referer")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/109.0")
                .build()
            chain.proceed(req)
        }
        .build()

    suspend fun fetchTopArticles(maxPerFeed: Int = 5): List<NewsArticle> = withContext(Dispatchers.IO) {
        val articles = mutableListOf<NewsArticle>()
        for ((url, sourceName) in FEEDS) {
            try {
                val req = Request.Builder().url(url).build()
                val body = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                } ?: continue
                articles += parseRss(body, sourceName, maxPerFeed)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch $sourceName: ${e.message}")
            }
        }
        articles
    }

    private fun parseRss(xml: String, source: String, max: Int): List<NewsArticle> {
        val results = mutableListOf<NewsArticle>()
        return try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            var inItem = false
            var title = ""; var link = ""; var desc = ""; var pubDate = ""
            while (event != XmlPullParser.END_DOCUMENT && results.size < max) {
                val name = parser.name ?: ""
                when (event) {
                    XmlPullParser.START_TAG -> when (name) {
                        "item", "entry" -> {
                            inItem = true; title = ""; link = ""; desc = ""; pubDate = ""
                        }
                        "title"       -> if (inItem) title = parser.nextText().trim()
                        "link"        -> if (inItem) {
                            val href = parser.getAttributeValue(null, "href")
                            link = if (href != null) href else parser.nextText().trim()
                        }
                        "description", "summary", "content:encoded" -> if (inItem) {
                            desc = parser.nextText()
                                .replace(Regex("<[^>]*>"), "")   // strip HTML tags
                                .trim()
                                .take(180)
                        }
                        "pubDate", "published", "updated", "dc:date" -> if (inItem) pubDate = parser.nextText().trim()
                    }
                    XmlPullParser.END_TAG -> when (name) {
                        "item", "entry" -> {
                            if (inItem && title.isNotBlank() && link.isNotBlank()) {
                                results += NewsArticle(title, link, source, pubDate, desc)
                            }
                            inItem = false
                        }
                    }
                }
                event = parser.next()
            }
            results
        } catch (e: Exception) {
            Log.w(TAG, "RSS parse error for $source: ${e.message}")
            results
        }
    }
}
