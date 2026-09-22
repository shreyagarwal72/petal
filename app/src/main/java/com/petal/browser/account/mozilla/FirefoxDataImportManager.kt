package com.petal.browser.account.mozilla

import android.content.Context
import android.net.Uri
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import org.json.JSONArray
import java.util.regex.Pattern

/** Imports Firefox bookmark HTML exports and simple history JSON exports into Petal. */
object FirefoxDataImportManager {
    fun importFile(context: Context, uri: Uri): Int {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return 0
        val action = RecordAction(context)
        action.open(true)
        return try {
            if (text.trimStart().startsWith("[")) importJson(text, action) else importBookmarksHtml(text, action)
        } finally { action.close() }
    }

    private fun importBookmarksHtml(text: String, action: RecordAction): Int {
        val pattern = Pattern.compile("<A[^>]*HREF=\\\"([^\\\"]+)\\\"[^>]*>(.*?)</A>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val matcher = pattern.matcher(text)
        var count = 0
        while (matcher.find()) {
            val url = matcher.group(1)?.trim().orEmpty()
            val title = matcher.group(2)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                action.addBookmark(Record(title.ifBlank { url }, url, 0L, 0L)); count++
            }
        }
        return count
    }

    private fun importJson(text: String, action: RecordAction): Int {
        val array = JSONArray(text)
        var count = 0
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val url = item.optString("url", item.optString("uri", "")).trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue
            val title = item.optString("title", item.optString("name", url)).ifBlank { url }
            val time = item.optLong("visitDate", item.optLong("lastVisited", System.currentTimeMillis()))
            action.addHistory(Record(title, url, if (time < 100000000000L) time * 1000L else time, 0L)); count++
        }
        return count
    }
}
