package com.petal.browser.ui.components

import android.content.Intent
import android.content.pm.PackageManager
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.activity.Settings_Delete
import com.petal.browser.database.RecordAction
import com.petal.browser.unit.HelperUnit
import com.petal.browser.view.NinjaToast
import com.petal.browser.view.NinjaWebView

/**
 * Kotlin delegate handling navigation, overview tab switching, and
 * Material 3 Expressive overflow menu actions for BrowserActivity.
 */
object BrowserNavigationDelegate {

    @JvmStatic
    fun showOverflowMenu(activity: BrowserActivity) {
        val webView = activity.ninjaWebView
        var isBookmarked = false
        if (webView != null && webView.url != null) {
            val action = RecordAction(activity)
            action.open(false)
            isBookmarked = action.checkBookmark(webView.url)
            action.close()
        }

        val canGoBack = webView != null && webView.canGoBack()
        val canGoForward = webView != null && webView.canGoForward()
        val profile = NinjaWebView.getProfile()
        val prefs = activity.sp
        val isDesktopSite = prefs.getBoolean("${profile}_desktop", false)
        val isAdBlock = prefs.getBoolean("sp_ad_block", prefs.getBoolean("${profile}_adBlock", true))
        val isMediaActive = activity.isMediaPlaying || (activity.customView != null || activity.fullscreenHolder != null || activity.videoView != null)

        PetalOverflowBridge.showOverflowMenu(
            activity,
            webView?.title ?: "",
            webView?.url ?: "",
            isBookmarked,
            canGoBack,
            canGoForward,
            isDesktopSite,
            isAdBlock,
            isMediaActive,
            object : PetalOverflowMenuActionHandler {
                override fun onGoBack() {
                    if (webView != null && webView.canGoBack()) {
                        webView.goBack()
                    }
                }

                override fun onGoForward() {
                    if (webView != null && webView.canGoForward()) {
                        webView.goForward()
                    }
                }

                override fun onToggleBookmark() {
                    if (webView != null && webView.url != null) {
                        activity.saveBookmark(webView.title, webView.url)
                    }
                }

                override fun onOpenDownloadsShortcut() {
                    activity.showDownloads()
                }

                override fun onOpenPageInfo() {
                    if (webView != null && activity.fab_menu != null) {
                        activity.showDialogFastToggle(HelperUnit.domain(webView.url), webView.url, activity.fab_menu)
                    }
                }

                override fun onReload() {
                    webView?.reload()
                }

                override fun onToggleDesktopSite(enabled: Boolean) {
                    prefs.edit()
                        .putBoolean("${profile}_desktop", enabled)
                        .putBoolean("profileStandard_desktop", enabled)
                        .apply()
                    webView?.setDesktopMode(enabled)
                    NinjaToast.show(activity, if (enabled) "Desktop site requested" else "Mobile site requested")
                }

                override fun onToggleAdBlock(enabled: Boolean) {
                    prefs.edit()
                        .putBoolean("sp_ad_block", enabled)
                        .putBoolean("${profile}_adBlock", enabled)
                        .putBoolean("profileStandard_adBlock", enabled)
                        .apply()
                    if (webView != null) {
                        webView.initPreferences(webView.url)
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
                    activity.startActivity(Intent(activity, Settings_Delete::class.java))
                }

                override fun onOpenDownloads() {
                    activity.showDownloads()
                }

                override fun onOpenBookmarks() {
                    activity.showBookmarksPage()
                }

                override fun onInstallPwa() {
                    activity.savePageOffline()
                }

                override fun onSearchOnSite() {
                    activity.searchOnSite()
                }

                override fun onPrintPdf() {
                    try {
                        activity.createWebPrintJob(webView)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onSavePage() {
                    try {
                        activity.saveBookmark(webView?.title ?: "", webView?.url ?: "")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onShareLink() {
                    if (webView != null) {
                        activity.shareLink(webView.title, webView.url)
                    }
                }

                override fun onViewSource() {
                    if (webView != null && webView.url != null) {
                        webView.loadUrl("view-source:" + webView.url)
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
