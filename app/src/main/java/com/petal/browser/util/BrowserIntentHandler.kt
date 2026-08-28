package com.petal.browser.util

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Toast
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.compose.incognito.PetalIncognitoSessionManager
import com.petal.browser.compose.mlkit.PetalImageScannerBridge
import com.petal.browser.browser.BrowserContainer
import com.petal.browser.lens.PetalLensBridge
import com.petal.browser.ui.components.PetalAiSearchBridge
import com.petal.browser.ui.components.PetalVoiceSearchBridge
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.view.NinjaToast
import com.petal.browser.widget.PetalSearchWidgetProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.Arrays
import java.util.Objects

/**
 * Kotlin utility handler encapsulating Intent dispatching, file URI opening,
 * deep link routing, and widget actions for BrowserActivity.
 */
object BrowserIntentHandler {

    private const val TAG = "BrowserIntentHandler"

    @JvmStatic
    fun readTextFromUri(context: Context, uri: Uri): String {
        val stringBuilder = StringBuilder()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line).append("\n")
                        line = reader.readLine()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return stringBuilder.toString()
    }

    @JvmStatic
    fun copyHtmlToCache(context: Context, contentUri: Uri): File? {
        return try {
            val cacheDir = context.cacheDir
            val tempFile = File(cacheDir, "local_preview_" + System.currentTimeMillis() + ".html")
            context.contentResolver.openInputStream(contentUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
