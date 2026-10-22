/*
 * PetalNewsManager.kt
 * ──────────────────────────────────────────────────────────────────────────
 * Brave News-style privacy-respecting RSS feed aggregator.
 * Fetches from curated public feeds with no tracking referrer headers.
 * Extracts rich article thumbnail images from media:content, media:thumbnail,
 * enclosures, and HTML img tags with Liquid Glass + Material 3 styling.
 */
package com.petal.browser.home.news

import android.content.Context
import android.util.Log
import android.util.Xml
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

data class NewsSource(
    val id: String,
    val name: String,
    val url: String,
    val category: String,
    val enabledByDefault: Boolean = true
)

data class NewsArticle(
    val title: String,
    val url: String,
    val source: String,
    val publishedAt: String = "",
    val description: String = "",
    val imageUrl: String? = null
)

object PetalNewsManager {

    private const val TAG = "PetalNewsManager"
    const val PREF_BRAVE_NEWS_ENABLED = "petal_builtin_brave_news"
    private const val PREF_SELECTED_SOURCES = "petal_news_selected_sources"

    val AVAILABLE_SOURCES = listOf(
        NewsSource("bbc", "BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", "Global News"),
        NewsSource("reuters", "Reuters", "https://feeds.reuters.com/reuters/topNews", "Global News"),
        NewsSource("verge", "The Verge", "https://www.theverge.com/rss/index.xml", "Tech & Culture"),
        NewsSource("arstechnica", "Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "Tech & Science"),
        NewsSource("wired", "WIRED", "https://www.wired.com/feed/rss", "Tech & Culture"),
        NewsSource("androidauthority", "Android Authority", "https://www.androidauthority.com/feed/", "Mobile & Android"),
        NewsSource("hackernews", "Hacker News", "https://news.ycombinator.com/rss", "Developer"),
        NewsSource("techcrunch", "TechCrunch", "https://techcrunch.com/feed/", "Startups & Tech")
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

    fun isNewsEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return sp.getBoolean(PREF_BRAVE_NEWS_ENABLED, true)
    }

    fun setNewsEnabled(context: Context, enabled: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        sp.edit().putBoolean(PREF_BRAVE_NEWS_ENABLED, enabled).apply()
    }

    fun getSelectedSourceIds(context: Context): Set<String> {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val saved = sp.getStringSet(PREF_SELECTED_SOURCES, null)
        return saved ?: AVAILABLE_SOURCES.filter { it.enabledByDefault }.map { it.id }.toSet()
    }

    fun setSelectedSourceIds(context: Context, ids: Set<String>) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        sp.edit().putStringSet(PREF_SELECTED_SOURCES, ids).apply()
    }

    fun toggleSource(context: Context, sourceId: String, enabled: Boolean) {
        val current = getSelectedSourceIds(context).toMutableSet()
        if (enabled) current.add(sourceId) else current.remove(sourceId)
        setSelectedSourceIds(context, current)
    }

    suspend fun fetchTopArticles(context: Context, maxPerFeed: Int = 4): List<NewsArticle> = withContext(Dispatchers.IO) {
        if (!isNewsEnabled(context)) return@withContext emptyList()

        val activeIds = getSelectedSourceIds(context)
        val activeSources = AVAILABLE_SOURCES.filter { it.id in activeIds }
        val articles = mutableListOf<NewsArticle>()

        for (source in activeSources) {
            try {
                val req = Request.Builder().url(source.url).build()
                val body = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                } ?: continue
                articles += parseRss(body, source.name, maxPerFeed)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch ${source.name}: ${e.message}")
            }
        }
        articles.shuffled()
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
            var imageUrl: String? = null

            while (event != XmlPullParser.END_DOCUMENT && results.size < max) {
                val name = parser.name?.lowercase() ?: ""
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (name) {
                            "item", "entry" -> {
                                inItem = true
                                title = ""
                                link = ""
                                desc = ""
                                pubDate = ""
                                imageUrl = null
                            }
                            "title" -> if (inItem) title = parser.nextText().trim()
                            "link" -> if (inItem) {
                                val href = parser.getAttributeValue(null, "href")
                                link = if (!href.isNullOrBlank()) href else parser.nextText().trim()
                            }
                            "media:content", "content" -> if (inItem && imageUrl == null) {
                                val url = parser.getAttributeValue(null, "url")
                                if (isLikelyImageUrl(url)) imageUrl = sanitizeUrl(url)
                            }
                            "media:thumbnail" -> if (inItem && imageUrl == null) {
                                val url = parser.getAttributeValue(null, "url")
                                if (isLikelyImageUrl(url)) imageUrl = sanitizeUrl(url)
                            }
                            "enclosure" -> if (inItem && imageUrl == null) {
                                val type = parser.getAttributeValue(null, "type") ?: ""
                                val url = parser.getAttributeValue(null, "url")
                                if (type.startsWith("image/") || isLikelyImageUrl(url)) {
                                    imageUrl = sanitizeUrl(url)
                                }
                            }
                            "description", "summary", "content:encoded" -> if (inItem) {
                                val rawText = parser.nextText()
                                if (imageUrl == null) {
                                    imageUrl = extractFirstImageUrl(rawText)
                                }
                                desc = rawText
                                    .replace(Regex("<[^>]*>"), "") // strip HTML tags
                                    .replace("&amp;", "&")
                                    .replace("&quot;", "\"")
                                    .replace("&#038;", "&")
                                    .replace("&nbsp;", " ")
                                    .trim()
                                    .take(180)
                            }
                            "pubdate", "published", "updated", "dc:date" -> if (inItem) {
                                pubDate = parser.nextText().trim()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "item" || name == "entry") {
                            if (inItem && title.isNotBlank() && link.isNotBlank()) {
                                results += NewsArticle(
                                    title = title,
                                    url = link,
                                    source = source,
                                    publishedAt = pubDate,
                                    description = desc,
                                    imageUrl = imageUrl
                                )
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

    private fun isLikelyImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.startsWith("http") && (
            lower.contains(".jpg") || lower.contains(".jpeg") ||
            lower.contains(".png") || lower.contains(".webp") ||
            lower.contains(".gif") || lower.contains("image")
        )
    }

    private fun sanitizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.trim()
            .replace("&amp;", "&")
            .replace("&#038;", "&")
    }

    private fun extractFirstImageUrl(html: String): String? {
        val match = Regex("""<img[^>]+src=["'](https?://[^"'\s>]+)["']""", RegexOption.IGNORE_CASE).find(html)
        return match?.groupValues?.get(1)?.let { sanitizeUrl(it) }
    }
}
