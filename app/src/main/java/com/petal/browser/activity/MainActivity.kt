package com.petal.browser.activity

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.petal.browser.browser.PetalBrowserFeatures
import com.petal.browser.browser.viewmodel.PetalBrowserViewModel
import com.petal.browser.compose.PetalRootApp
import com.petal.browser.ui.theme.PetalExpressiveTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity
 * ─────────────────────────────────────────────────────────────────────────────
 * The modern, lightweight 100% Jetpack Compose Entry Point for Petal Browser.
 *
 * Replaces the legacy 450 KB BrowserActivity. All UI rendering, window insets,
 * Material 3 Expressive theme tokens, and Liquid Glass composition run entirely
 * in Jetpack Compose, while Mozilla's [BrowserStore], [GeckoView], and
 * [PetalBrowserFeatures] power engine features, prompts, and site permissions.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: PetalBrowserViewModel by viewModels()
    private var browserFeatures: PetalBrowserFeatures? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        browserFeatures = PetalBrowserFeatures(this).also { it.start() }

        handleIncomingIntent(intent)

        setContent {
            PetalExpressiveTheme {
                PetalRootApp(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        browserFeatures?.stop()
        browserFeatures = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        browserFeatures?.onPermissionsResult(requestCode, permissions, grantResults)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (browserFeatures?.onActivityResult(requestCode, resultCode, data) == true) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        browserFeatures?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val data = intent.dataString

        if (Intent.ACTION_VIEW == action && !data.isNullOrBlank()) {
            viewModel.loadUrl(data)
        } else if (Intent.ACTION_SEND == action && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                viewModel.loadUrl(text)
            }
        }
    }
}
