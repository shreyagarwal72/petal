package com.petal.browser.compose.bookmarks

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import com.petal.browser.database.Record
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PetalBookmarkHtmlExporter {

    fun generateNetscapeHtml(bookmarks: List<Record>): String {
        val sb = StringBuilder()
        val now = System.currentTimeMillis() / 1000
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<!-- This is an automatically generated file.\n")
        sb.append("     It will be read and overwritten.\n")
        sb.append("     DO NOT EDIT! -->\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Bookmarks</TITLE>\n")
        sb.append("<H1>Bookmarks</H1>\n")
        sb.append("<DL><p>\n")
        sb.append("    <DT><H3 ADD_DATE=\"$now\" LAST_MODIFIED=\"$now\">Petal Bookmarks</H3>\n")
        sb.append("    <DL><p>\n")
        for (b in bookmarks) {
            val url = b.url?.trim() ?: continue
            if (url.isEmpty()) continue
            val safeUrl = url.replace("\"", "%22")
            val title = (b.title?.takeIf { it.isNotBlank() } ?: url)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            val addDate = if (b.iconColor > 0) b.iconColor / 1000 else now
            sb.append("        <DT><A HREF=\"$safeUrl\" ADD_DATE=\"$addDate\">$title</A>\n")
        }
        sb.append("    </DL><p>\n")
        sb.append("</DL><p>\n")
        return sb.toString()
    }

    fun exportToDownloads(context: Context, bookmarks: List<Record>): File? {
        return try {
            val html = generateNetscapeHtml(bookmarks)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "petal_bookmarks_$timeStamp.html"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { it.write(html.toByteArray(Charsets.UTF_8)) }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
