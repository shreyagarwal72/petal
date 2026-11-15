/*
 * PdfViewerActivity.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Standalone activity for Petal PDF Viewer.
 * Declared with android:label="@string/petal_pdf_viewer_name" so Android's
 * system chooser shows "Petal PDF Viewer" explicitly when opening PDF documents.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.activity

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.petal.browser.compose.pdf.PetalPdfViewerScreen
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.unit.PetalAndroidUtils

class PdfViewerActivity : ComponentActivity() {

    private var currentUri by mutableStateOf<Uri?>(null)
    private var currentDisplayName by mutableStateOf("Document.pdf")

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(PetalAndroidUtils.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.petal.browser.unit.PetalHighRefreshRateManager.applyHighRefreshRate(this)
        PetalAndroidUtils.initTheme(this)
        enableEdgeToEdge()

        val targetUri = extractTargetUri(intent)
        if (targetUri == null) {
            finish()
            return
        }

        currentUri = targetUri
        currentDisplayName = extractDisplayName(targetUri)

        setContent {
            val uri = currentUri ?: return@setContent
            PetalExpressiveTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    PetalPdfViewerScreen(
                        pdfUri = uri,
                        displayName = currentDisplayName,
                        onBackPress = { finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val targetUri = extractTargetUri(intent) ?: return
        currentUri = targetUri
        currentDisplayName = extractDisplayName(targetUri)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        super.onDestroy()
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun extractTargetUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> intent.data
        }
    }

    private fun extractDisplayName(uri: Uri): String {
        var name: String? = null
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            try {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) {
                            name = cursor.getString(idx)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return name ?: uri.lastPathSegment ?: "Document.pdf"
    }
}
