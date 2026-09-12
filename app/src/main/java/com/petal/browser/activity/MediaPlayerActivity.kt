/*
 * MediaPlayerActivity.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Standalone activity for Petal Media Player.
 * Declared with android:label="@string/petal_media_player_name" so Android's
 * system chooser shows "Petal Media Player" explicitly when opening media files.
 *
 * Automatically detects whether the opened URI is audio or video:
 *   - Video formats -> PetalVideoPlayerScreen (ExoPlayer + M3 Wavy HUD)
 *   - Audio formats -> PetalAudioPlayerScreen (Material 3 Expressive Audio Player)
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
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.petal.browser.media.PetalAudioPlayerScreen
import com.petal.browser.media.PetalVideoPlayerScreen
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.unit.HelperUnit

class MediaPlayerActivity : ComponentActivity() {

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
        val isAudio = isAudioMedia(targetUri, intent.type)

        setContent {
            PetalExpressiveTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    if (isAudio) {
                        PetalAudioPlayerScreen(
                            mediaUri = targetUri,
                            displayName = displayName,
                            onClose = { finish() }
                        )
                    } else {
                        PetalVideoPlayerScreen(
                            videoUri = targetUri,
                            displayName = displayName,
                            onClose = { finish() }
                        )
                    }
                }
            }
        }
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
        return name ?: uri.lastPathSegment ?: "Media"
    }

    private fun isAudioMedia(uri: Uri, mimeType: String?): Boolean {
        if (mimeType != null) {
            val lowerMime = mimeType.lowercase()
            if (lowerMime.startsWith("audio/")) return true
            if (lowerMime == "application/ogg" || lowerMime == "application/x-ogg") return true
        }
        val path = (uri.path ?: uri.toString()).lowercase()
        val audioExtensions = listOf(
            ".mp3", ".m4a", ".aac", ".flac", ".wav", ".ogg", ".oga", ".opus", ".mid", ".midi", ".wma"
        )
        return audioExtensions.any { path.endsWith(it) }
    }
}
