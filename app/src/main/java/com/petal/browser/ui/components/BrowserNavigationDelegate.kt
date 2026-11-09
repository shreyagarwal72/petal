package com.petal.browser.ui.components

import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.database.HistoryBookmarkRepository
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.unit.PetalBrowserUtils
import com.petal.browser.view.PetalToast

/**
 * Kotlin delegate handling navigation, overview tab switching, and
 * Material 3 Expressive overflow menu actions for MainActivity.
 */
object BrowserNavigationDelegate {

    @JvmStatic
    fun showOverflowMenu(activity: MainActivity) {
        val currentController = activity.currentAlbumController
        val tabSurface = currentController as? com.petal.browser.browser.PetalTabViewController ?: activity.getOrCreateTabViewController()

        val isHome = activity.isPetalHomeSurfaceShowing || activity.isCurrentTabHomeOrBlank || PetalBrowserUtils.isHomePage(tabSurface?.url ?: currentController?.url)
        val title = if (isHome) "Petal Home" else (tabSurface?.title ?: currentController?.title ?: "")
        val url = if (isHome) "petal://home" else (tabSurface?.url ?: currentController?.url ?: "")

        var isBookmarked = false
        if (!isHome && url.isNotEmpty() && !url.equals("about:blank", ignoreCase = true)) {
            val action = HistoryBookmarkRepository(activity)
            action.open(false)
            isBookmarked = action.checkBookmark(url)
            action.close()
        }

        val canGoBack = tabSurface?.canGoBack() ?: false
        val canGoForward = tabSurface?.canGoForward() ?: false
        val profile = com.petal.browser.engine.gecko.PetalEngineBridge.getProfile(activity)
        val prefs = activity.sp ?: PreferenceManager.getDefaultSharedPreferences(activity)
        val isDesktopSite = prefs.getBoolean("sp_desktop_site", false)
        val isAdBlock = prefs.getBoolean("sp_ad_block", prefs.getBoolean("${profile}_adBlock", true))
        val isMediaActive = activity.isMediaPlaying || (activity.customView != null || activity.fullscreenHolder != null || activity.videoView != null)

        PetalOverflowBridge.showOverflowMenu(
            activity,
            title,
            url,
            isBookmarked,
            canGoBack,
            canGoForward,
            isDesktopSite,
            isAdBlock,
            isMediaActive,
            object : PetalOverflowMenuActionHandler {
                override fun onGoBack() {
                    if (tabSurface != null && tabSurface.canGoBack()) {
                        tabSurface.goBack()
                    }
                }

                override fun onGoForward() {
                    if (tabSurface != null && tabSurface.canGoForward()) {
                        tabSurface.goForward()
                    }
                }

                override fun onToggleBookmark() {
                    if (url.isNotEmpty()) {
                        activity.toggleBookmark(title, url)
                    }
                }

                override fun onOpenDownloadsShortcut() {
                    activity.showDownloads()
                }

                override fun onOpenPageInfo() {
                    if (url.isNotEmpty() && activity.fab_menu != null) {
                        activity.showDialogFastToggle(PetalAndroidUtils.domain(url), url, activity.fab_menu)
                    }
                }

                override fun onReload() {
                    tabSurface?.reload()
                }

                override fun onToggleDesktopSite(enabled: Boolean) {
                    prefs.edit()
                        .putBoolean("sp_desktop_site", enabled)
                        .putBoolean("${profile}_desktop", enabled)
                        .putBoolean("profileStandard_desktop", enabled)
                        .apply()
                    tabSurface?.setDesktopMode(enabled)
                    PetalToast.show(activity, if (enabled) "Desktop site requested" else "Mobile site requested")
                }

                override fun onToggleAdBlock(enabled: Boolean) {
                    prefs.edit()
                        .putBoolean("sp_ad_block", enabled)
                        .putBoolean("${profile}_adBlock", enabled)
                        .putBoolean("profileStandard_adBlock", enabled)
                        .apply()
                    tabSurface?.reload()
                    PetalToast.show(activity, if (enabled) "AdBlocker Enabled" else "AdBlocker Disabled")
                }

                override fun onNewTab() {
                    activity.addAlbum(activity.getString(R.string.app_name), prefs.getString("favoriteURL", "about:blank") ?: "about:blank", true)
                }

                override fun onNewIncognitoTab() {
                    activity.addAlbum("Incognito Tab", prefs.getString("favoriteURL", "about:blank") ?: "about:blank", true, true)
                    PetalToast.show(activity, activity.getString(R.string.ui_opened_incognito_tab))
                }

                override fun onOpenHistory() {
                    activity.showHistoryScreen()
                }

                override fun onDeleteBrowsingData() {
                    activity.showDeleteScreen()
                }

                override fun onOpenDownloads() {
                    activity.showDownloads()
                }

                override fun onOpenBookmarks() {
                    activity.showBookmarksPage()
                }

                override fun onInstallPwa() {
                    activity.installPwaShortcut()
                }

                override fun onSearchOnSite() {
                    activity.searchOnSite()
                }

                override fun onShowReadingMode() {
                    activity.showReaderMode()
                }

                override fun onPrintPdf() {
                    try {
                        activity.createWebPrintJob(null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onSavePage() {
                    try {
                        activity.savePageOffline()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onShareLink() {
                    if (url.isNotEmpty()) {
                        activity.shareLink(title, url)
                    }
                }

                override fun onCopyCleanLink() {
                    if (url.isNotEmpty()) {
                        val cleanUrl = PetalBrowserUtils.cleanTrackingParams(url)
                        val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Clean URL", cleanUrl)
                        clipboard?.setPrimaryClip(clip)
                        com.petal.browser.haptics.PetalHapticEngine.getInstance(activity).play(com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.7f)
                        PetalToast.show(activity, activity.getString(R.string.ui_clean_url_copied))
                    }
                }

                override fun onOpenShieldHud() {
                    PetalPrivacyShieldSheet.show(activity, url) { enabled ->
                        onToggleAdBlock(enabled)
                    }
                }

                override fun onViewSource() {
                    if (url.isNotEmpty()) {
                        val sourceUrl = if (url.startsWith("view-source:")) url else "view-source:$url"
                        tabSurface?.loadUrl(sourceUrl)
                    }
                }

                override fun onOpenSettings() {
                    activity.openSettingsScreen()
                }

                override fun onOpenPetalAi() {
                    PetalAiSearchBridge.showAiSearchResult(activity, "")
                }

                override fun onOpenExtensions() {
                    activity.showExtensionsScreen()
                }

                override fun onOpenExtensionsMenu() {
                    com.petal.browser.compose.extensions.PetalExtensionsMenuSheet.show(activity, url) {
                        activity.showExtensionsScreen()
                    }
                }

                override fun onOpenExtensionAction(extensionId: String) {
                    // The overflow menu is a separate android.app.Dialog window. It must be
                    // fully gone before we trigger the popup, or the extension popup view
                    // gets added to the Activity's decor while the Dialog window still has
                    // focus above it and never becomes visible/touchable.
                    activity.window.decorView.post {
                        com.petal.browser.extensions.PetalExtensionManager.triggerBrowserAction(extensionId, activity)
                    }
                }

                override fun onOpenExtensionSettings(extensionId: String) {
                    activity.window.decorView.post {
                        com.petal.browser.extensions.PetalExtensionManager.openOptionsPage(extensionId, activity)
                    }
                }

                override fun onOpenDevConsole() {
                    val tab = currentController as? com.petal.browser.browser.PetalTabViewController ?: activity.getOrCreateTabViewController()
                    val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
                        setViewTreeLifecycleOwner(activity)
                        setViewTreeViewModelStoreOwner(activity)
                        setViewTreeSavedStateRegistryOwner(activity)
                        setContent {
                            com.petal.browser.ui.theme.PetalExpressiveTheme {
                                com.petal.browser.tools.PetalDevConsoleSheet(
                                    onExecuteScript = { script, callback ->
                                        tab?.evaluateJavascript(script, callback)
                                            ?: callback("Error: Web session not active")
                                    },
                                    onDismissRequest = {
                                        (parent as? android.view.ViewGroup)?.removeView(this)
                                    }
                                )
                            }
                        }
                    }
                    val decor = activity.window.decorView as? android.view.ViewGroup
                    decor?.addView(composeView)
                }

                override fun onOpenSafeLocker() {
                    activity.showSafeLocker()
                }

                override fun onOpenQuickTools() {
                    activity.showQuickTools()
                }

                override fun onTriggerMediaMode() {
                    val isPipSupported = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
                    val isBgPlayEnabled = prefs.getBoolean("sp_background_play", false)

                    // This is an explicit user action. Auto-PiP controls only
                    // automatic entry when minimizing the app and must not block
                    // the manual PiP command from the overflow menu.
                    if (isPipSupported) {
                        activity.triggerSystemPipMode()
                    } else if (isBgPlayEnabled) {
                        PetalToast.show(activity, activity.getString(R.string.ui_background_media_active))
                    } else {
                        PetalToast.show(activity, activity.getString(R.string.ui_enable_auto_pip_hint))
                    }
                }
            }
        )
    }
}
