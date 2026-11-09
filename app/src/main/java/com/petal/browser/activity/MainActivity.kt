package com.petal.browser.activity

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.petal.browser.account.mozilla.FxAccountManager
import com.petal.browser.account.mozilla.PetalMozillaSyncManager
import com.petal.browser.browser.PetalBrowserFeatures
import com.petal.browser.browser.viewmodel.PetalBrowserViewModel
import com.petal.browser.compose.PetalRootApp
import com.petal.browser.extensions.PetalBuiltInExtensionManager
import com.petal.browser.extensions.PetalExtensionManager
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

        // Initialize Mozilla features: Extensions & Firefox Account Sync
        PetalExtensionManager.attach(applicationContext)
        PetalBuiltInExtensionManager.installAll(applicationContext)
        FxAccountManager.getInstance().initialize(this)
        PetalMozillaSyncManager.getInstance()

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
        val dataUri = intent.data

        // Handle WebExtension .xpi installation
        if (dataUri != null && (intent.type == "application/x-xpinstall" || dataUri.path?.endsWith(".xpi") == true)) {
            PetalExtensionManager.installFromContentUri(this, dataUri) { success, _ ->
                if (success) {
                    viewModel.openExtensions()
                }
            }
            return
        }

        if (Intent.ACTION_PROCESS_TEXT == action) {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            if (!text.isNullOrBlank()) {
                viewModel.loadUrl(text)
            }
            return
        }

        when (action) {
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_NEW_TAB -> {
                viewModel.openNewTab("about:home", selectTab = true, private = false)
            }
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO -> {
                viewModel.openNewTab("about:home", selectTab = true, private = true)
            }
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_BOOKMARKS -> {
                viewModel.openBookmarks()
            }
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_DOWNLOADS -> {
                viewModel.openDownloads()
            }
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_AI_SEARCH -> {
                viewModel.openAiSheet()
            }
            com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_SEARCH -> {
                viewModel.openNewTab("about:home", selectTab = true, private = false)
            }
            com.petal.browser.compose.incognito.PetalIncognitoSessionManager.ACTION_CLOSE_INCOGNITO -> {
                viewModel.closeAllTabs(isIncognitoOnly = true)
            }
            Intent.ACTION_VIEW -> {
                if (!data.isNullOrBlank()) {
                    viewModel.loadUrl(data)
                }
            }
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrBlank()) {
                        viewModel.loadUrl(text)
                    }
                }
            }
        }
    }
}
