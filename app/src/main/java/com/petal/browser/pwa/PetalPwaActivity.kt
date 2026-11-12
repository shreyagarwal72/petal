package com.petal.browser.pwa

import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.browser.PetalTabViewController
import com.petal.browser.compose.file.PetalUploadChooser
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import com.petal.browser.ui.components.PetalAlertDialogBuilder
import com.petal.browser.ui.components.PetalRuntimePermissionBridge
import com.petal.browser.unit.PetalAndroidUtils
import java.io.File

/**
 * PetalPwaActivity
 *
 * Dedicated standalone application shell for Progressive Web Apps installed from Petal
 * or launched via external Web App intents. Runs isolated in its own task window (documentLaunchMode="intoExisting"),
 * with full GeckoView rendering, service worker offline persistence, adaptive status/nav bar coloring,
 * and zero browser UI chrome for a true native-app experience.
 */
class PetalPwaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "pwa_url"
        const val EXTRA_TITLE = "pwa_title"
        const val EXTRA_THEME_COLOR = "pwa_theme_color"
        const val EXTRA_BG_COLOR = "pwa_background_color"
        const val EXTRA_DISPLAY = "pwa_display"
        const val EXTRA_OFFLINE_ARCHIVE = "offline_archive_path"
    }

    private var pwaRoot: View? = null
    private var contentFrame: FrameLayout? = null
    private var progressBarComposeView: View? = null
    private var fabBubbleView: View? = null
    private var fabBaseMarginPx = 0
    private var geckoView: PetalTabViewController? = null
    private var pwaUrl = "about:blank"
    private var pwaTitle = "Web App"
    private var themeColorHex = "#FFFFFF"
    private var displayMode = "standalone"
    private var offlineArchivePath: String? = null
    private var pwaTabId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PetalAndroidUtils.initTheme(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        setContentView(R.layout.activity_pwa)

        pwaRoot = findViewById(R.id.pwa_root)
        contentFrame = findViewById(R.id.pwa_content_frame)
        progressBarComposeView = findViewById(R.id.pwa_progress_bar_compose)
        fabBubbleView = findViewById(R.id.pwa_fab_bubble)
        fabBubbleView?.let { fab ->
            val lp = fab.layoutParams
            if (lp is ViewGroup.MarginLayoutParams) {
                fabBaseMarginPx = lp.bottomMargin
            }
        }
        handleIntent(intent)

        applyWindowConfiguration()
        setupWindowInsets()
        setupGeckoView()
        setupBackNavigation()
        setupFabMenu()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        applyWindowConfiguration()
        pwaRoot?.let { ViewCompat.requestApplyInsets(it) }
        if (geckoView != null && pwaUrl.isNotEmpty() && !"about:blank".equals(pwaUrl, ignoreCase = true)) {
            geckoView?.loadUrl(pwaUrl)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val dataUri: Uri? = intent.data
        if (dataUri != null) {
            pwaUrl = dataUri.toString()
        } else if (intent.hasExtra(EXTRA_URL)) {
            pwaUrl = intent.getStringExtra(EXTRA_URL) ?: "about:blank"
        } else if (intent.hasExtra("url")) {
            pwaUrl = intent.getStringExtra("url") ?: "about:blank"
        }

        if (intent.hasExtra(EXTRA_TITLE)) {
            pwaTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Web App"
            title = pwaTitle
        }
        if (intent.hasExtra(EXTRA_THEME_COLOR)) {
            themeColorHex = intent.getStringExtra(EXTRA_THEME_COLOR) ?: "#FFFFFF"
        }
        if (intent.hasExtra(EXTRA_DISPLAY)) {
            displayMode = intent.getStringExtra(EXTRA_DISPLAY) ?: "standalone"
        }
        if (intent.hasExtra(EXTRA_OFFLINE_ARCHIVE)) {
            offlineArchivePath = intent.getStringExtra(EXTRA_OFFLINE_ARCHIVE)
        }
    }

    private fun applyWindowConfiguration() {
        try {
            var parsedThemeColor = Color.WHITE
            if (themeColorHex.trim().isNotEmpty()) {
                parsedThemeColor = Color.parseColor(themeColorHex)
            }

            // Standalone / Minimal UI: Apply window theme color and adjust system bar icons
            window.statusBarColor = parsedThemeColor
            window.navigationBarColor = parsedThemeColor

            val darkness = 1 - (0.299 * Color.red(parsedThemeColor) + 0.587 * Color.green(parsedThemeColor) + 0.114 * Color.blue(parsedThemeColor)) / 255
            val lightStatusIcons = darkness >= 0.5

            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !lightStatusIcons
                isAppearanceLightNavigationBars = !lightStatusIcons
            }

            WindowCompat.setDecorFitsSystemWindows(window, false)

            val isFullscreen = isPwaFullscreenMode()
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (insetsController != null) {
                if (isFullscreen) {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            } else if (isFullscreen) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }

            fabBubbleView?.let { fab ->
                fab.visibility = if (isFullscreen) View.GONE else View.VISIBLE
                fab.alpha = if (isFullscreen) 0f else 0.4f
            }
        } catch (_: Exception) {}
    }

    private fun isPwaFullscreenMode(): Boolean {
        return "fullscreen".equals(displayMode, ignoreCase = true) ||
                PreferenceManager.getDefaultSharedPreferences(this).getBoolean("sp_pwa_default_fullscreen", true) ||
                PreferenceManager.getDefaultSharedPreferences(this).getBoolean("sp_immersive_fullscreen", false)
    }

    private fun setupWindowInsets() {
        val root = pwaRoot ?: return

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val systemBars: Insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val isFullscreen = isPwaFullscreenMode()
            val isBarsVisible = windowInsets.isVisible(WindowInsetsCompat.Type.systemBars())

            val topInset = if (isFullscreen) 0 else systemBars.top
            val bottomInset = if (isFullscreen) 0 else systemBars.bottom

            contentFrame?.let { frame ->
                frame.setPadding(
                    frame.paddingLeft, topInset,
                    frame.paddingRight, bottomInset
                )
            }
            progressBarComposeView?.let { bar ->
                bar.setPadding(
                    bar.paddingLeft, topInset,
                    bar.paddingRight, bar.paddingBottom
                )
            }
            fabBubbleView?.let { fab ->
                if (isFullscreen) {
                    if (isBarsVisible) {
                        fab.visibility = View.VISIBLE
                        fab.animate().alpha(0.85f).setDuration(220).start()
                    } else {
                        fab.animate().alpha(0f).setDuration(220).withEndAction {
                            if (isPwaFullscreenMode()) {
                                fab.visibility = View.GONE
                            }
                        }.start()
                    }
                } else {
                    fab.visibility = View.VISIBLE
                    fab.alpha = 0.4f
                }
                val lp = fab.layoutParams
                if (lp is ViewGroup.MarginLayoutParams) {
                    lp.bottomMargin = fabBaseMarginPx + bottomInset
                    fab.layoutParams = lp
                }
            }

            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun setupGeckoView() {
        val frame = contentFrame ?: return

        var targetUrl = pwaUrl
        if (offlineArchivePath != null && !PetalAndroidUtils.isNetworkAvailable(this)) {
            val archive = File(offlineArchivePath!!)
            if (archive.exists()) {
                targetUrl = "file://" + archive.absolutePath
            }
        }

        val effectiveUrl = if (targetUrl.isNotEmpty() && !"about:blank".equals(targetUrl, ignoreCase = true)) {
            targetUrl
        } else {
            "about:blank"
        }
        pwaTabId = "pwa_" + System.currentTimeMillis()
        val sessionPair = PetalEngineStore.createTabSession(
            this,
            pwaTabId!!,
            effectiveUrl,
            pwaTitle,
            false,
            true
        )

        val gv = PetalTabViewController(this).apply {
            bindTab(sessionPair.first)
            attachLifecycle(this@PetalPwaActivity)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        geckoView = gv
        frame.addView(gv)
        // The tab view's default spinner is never drawn; show Petal's Compose pull indicator instead.
        com.petal.browser.compose.composable.PetalRefreshBarBridge.attachTo(frame, this, gv)

        if (!"about:blank".equals(effectiveUrl, ignoreCase = true)) {
            gv.loadUrl(effectiveUrl)
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val gv = geckoView
                if (gv != null && gv.canGoBack()) {
                    gv.goBack(true)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupFabMenu() {
        val fabBubble = findViewById<View>(R.id.pwa_fab_bubble) ?: return
        fabBubble.setOnClickListener { showPwaQuickActions() }
    }

    private fun showPwaQuickActions() {
        val options = arrayOf(
            "Refresh",
            "Share App Link",
            "Open in Petal Browser",
            "Close App"
        )

        PetalAlertDialogBuilder(this)
            .setTitle(pwaTitle)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> geckoView?.reload()
                    1 -> {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, pwaUrl)
                        }
                        startActivity(Intent.createChooser(share, "Share Web App"))
                    }
                    2 -> {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(pwaUrl)).apply {
                            component = ComponentName(this@PetalPwaActivity, MainActivity::class.java)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(browserIntent)
                        finish()
                    }
                    3 -> finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        PetalGeckoRuntime.setForegroundActivity(this)
        super.onResume()
        geckoView?.activate()
    }

    override fun onPause() {
        if (PetalGeckoRuntime.getForegroundActivity() == this) {
            PetalGeckoRuntime.setForegroundActivity(null)
        }
        super.onPause()
        geckoView?.deactivate()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (PetalGeckoRuntime.handleActivityResult(requestCode, resultCode, data)) {
            return
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        @Suppress("UNCHECKED_CAST")
        PetalRuntimePermissionBridge.handlePermissionsResult(
            requestCode,
            permissions as Array<String>,
            grantResults
        )
    }

    fun showFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ) {
        PetalUploadChooser.show(
            this,
            filePathCallback,
            fileChooserParams
        ) {
            Log.e("PetalPwaActivity", "Petal pickers failed to open")
        }
    }

    override fun onDestroy() {
        geckoView?.let { gv ->
            try {
                contentFrame?.removeView(gv)
                gv.destroy()
            } catch (_: Exception) {}
            geckoView = null
        }
        pwaTabId?.let { tabId ->
            PetalEngineStore.removeTab(this, tabId)
            pwaTabId = null
        }
        super.onDestroy()
    }
}
