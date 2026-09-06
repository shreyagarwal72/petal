package com.petal.browser.ui.components

import android.content.Intent
import android.content.pm.PackageManager
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.activity.Settings_Delete
import com.petal.browser.database.RecordAction
import com.petal.browser.unit.HelperUnit
import com.petal.browser.view.NinjaToast
import com.petal.browser.view.NinjaWebView
import com.petal.browser.view.PetalGeckoView

/**
 * Kotlin delegate handling navigation, overview tab switching, and
 * Material 3 Expressive overflow menu actions for BrowserActivity.
 */
object BrowserNavigationDelegate {

    @JvmStatic
    fun showOverflowMenu(activity: BrowserActivity) {
        val currentController = activity.currentAlbumController
        val geckoView = currentController as? com.petal.browser.view.PetalGeckoView
        val webView = currentController as? NinjaWebView ?: activity.ninjaWebView

        val title = geckoView?.title ?: webView?.title ?: ""
        val url = geckoView?.url ?: webView?.url ?: ""

        var isBookmarked = false
        if (url.isNotEmpty() && !url.equals("about:blank", ignoreCase = true)) {
            val action = RecordAction(activity)
            action.open(false)
            isBookmarked = action.checkBookmark(url)
            action.close()
        }

        val canGoBack = geckoView?.canGoBack() ?: webView?.canGoBack() ?: false
        val canGoForward = geckoView?.canGoForward() ?: webView?.canGoForward() ?: false
        val profile = PetalGeckoView.getProfile(activity)
        val prefs = activity.sp ?: PreferenceManager.getDefaultSharedPreferences(activity)
        val isDesktopSite = prefs.getBoolean("${profile}_desktop", false)
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
                    if (geckoView != null && geckoView.canGoBack()) {
                        geckoView.goBack()
                    } else if (webView != null && webView.canGoBack()) {
                        webView.goBack()
                    }
                }

                override fun onGoForward() {
                    if (geckoView != null && geckoView.canGoForward()) {
                        geckoView.goForward()
                    } else if (webView != null && webView.canGoForward()) {
                        webView.goForward()
                    }
                }

                override fun onToggleBookmark() {
                    if (url.isNotEmpty()) {
                        activity.saveBookmark(title, url)
                    }
                }

                override fun onOpenDownloadsShortcut() {
                    activity.showDownloads()
                }

                override fun onOpenPageInfo() {
                    if (url.isNotEmpty() && activity.fab_menu != null) {
                        activity.showDialogFastToggle(HelperUnit.domain(url), url, activity.fab_menu)
                    }
                }

                override fun onReload() {
                    geckoView?.reload() ?: webView?.reload()
                }

                override fun onToggleDesktopSite(enabled: Boolean) {
                    prefs.edit()
                        .putBoolean("${profile}_desktop", enabled)
                        .putBoolean("profileStandard_desktop", enabled)
                        .apply()
                    geckoView?.setDesktopMode(enabled)
                    webView?.setDesktopMode(enabled)
                    NinjaToast.show(activity, if (enabled) "Desktop site requested" else "Mobile site requested")
                }

                override fun onToggleAdBlock(enabled: Boolean) {
                    prefs.edit()
                        .putBoolean("sp_ad_block", enabled)
                        .putBoolean("${profile}_adBlock", enabled)
                        .putBoolean("profileStandard_adBlock", enabled)
                        .apply()
                    geckoView?.initPreferences(url)
                    geckoView?.reload()
                    if (webView != null) {
                        webView.initPreferences(url)
                        webView.reload()
                    }
                    NinjaToast.show(activity, if (enabled) "AdBlocker Enabled" else "AdBlocker Disabled")
                }

                override fun onNewTab() {
                    activity.addAlbum(activity.getString(R.string.app_name), prefs.getString("favoriteURL", "about:blank"), true)
                }

                override fun onNewIncognitoTab() {
                    activity.addAlbum("Incognito Tab", prefs.getString("favoriteURL", "about:blank"), true, true)
                    NinjaToast.show(activity, "Opened Incognito Tab")
                }

                override fun onOpenHistory() {
                    activity.showHistoryScreen()
                }

                override fun onDeleteBrowsingData() {
                    val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
                    com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
                    activity.startActivity(Intent(activity, Settings_Delete::class.java))
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
                        if (webView != null) {
                            activity.createWebPrintJob(webView)
                        }
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

                override fun onViewSource() {
                    if (url.isNotEmpty()) {
                        val sourceUrl = if (url.startsWith("view-source:")) url else "view-source:$url"
                        geckoView?.loadUrl(sourceUrl) ?: webView?.loadUrl(sourceUrl)
                    }
                }

                override fun onOpenSettings() {
                    activity.openSettingsScreen()
                }

                override fun onOpenPetalAi() {
                    PetalAiSearchBridge.showAiSearchResult(activity, "")
                }

                override fun onTriggerMediaMode() {
                    val isPipSupported = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
                    val isAutoPipEnabled = prefs.getBoolean("sp_auto_pip", true)
                    val isBgPlayEnabled = prefs.getBoolean("sp_background_play", false)

                    if (isPipSupported && isAutoPipEnabled) {
                        activity.triggerSystemPipMode()
                    } else if (isBgPlayEnabled) {
                        NinjaToast.show(activity, "Background media playback active")
                    } else {
                        NinjaToast.show(activity, "Enable Auto PiP or Background Play in Settings")
                    }
                }
            }
        )
    }
}
