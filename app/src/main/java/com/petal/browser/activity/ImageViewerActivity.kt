/*
 * ImageViewerActivity.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Standalone activity for Petal Image Viewer.
 * Declared with android:label="@string/petal_image_viewer_name" so Android's
 * system chooser shows "Petal Image Viewer" explicitly when opening images.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.activity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.EdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.petal.browser.compose.downloads.PetalImageViewerBridge
import com.petal.browser.unit.HelperUnit

class ImageViewerActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(HelperUnit.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.petal.browser.unit.PetalHighRefreshRateManager.applyHighRefreshRate(this)
        HelperUnit.initTheme(this)
        EdgeToEdge.enable(this)

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val targetUri = extractTargetUri(intent)
        if (targetUri == null) {
            finish()
            return
        }

        val displayName = extractDisplayName(targetUri)
        val composeView = PetalImageViewerBridge.createExternalViewerView(
            activity = this,
            contentUri = targetUri,
            displayName = displayName,
            onBackPress = { finish() },
        )
        setContentView(composeView)
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
        return name ?: uri.lastPathSegment ?: "Image"
    }
}
