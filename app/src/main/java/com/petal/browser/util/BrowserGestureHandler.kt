package com.petal.browser.util

import android.content.Intent
import android.util.Log
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.activity.Settings_Activity
import com.petal.browser.view.NinjaToast

/**
 * Kotlin utility handler encapsulating swipe & motion gesture actions for BrowserActivity.
 */
object BrowserGestureHandler {

    private const val TAG = "BrowserGestureHandler"

    @JvmStatic
    fun performGesture(activity: BrowserActivity, gestureKey: String, targetUrl: String?) {
        val prefs = activity.sp ?: return
        val gestureAction = prefs.getString(gestureKey, "0") ?: "0"
        val webView = activity.ninjaWebView

        when (gestureAction) {
            "01" -> {} // No-op
            "02" -> {
                if (webView != null && webView.canGoForward()) {
                    webView.stopLoading()
                    val backForwardList = webView.copyBackForwardList()
                    val historyUrl = backForwardList.getItemAtIndex(backForwardList.currentIndex + 1).url
                    webView.initPreferences(historyUrl)
                    webView.goForward()
                } else {
                    NinjaToast.show(activity, R.string.toast_webview_forward)
                }
            }
            "03" -> {
                if (activity.fullscreenHolder != null || activity.customView != null || activity.videoView != null) {
                    Log.v(TAG, "Petal in fullscreen mode")
                } else if (webView != null && webView.canGoBack()) {
                    prefs.edit().putBoolean("backPressed", true).apply()
                    webView.goBack()
                } else {
                    activity.removeAlbum(activity.currentAlbumController)
                }
            }
            "04" -> webView?.pageUp(true)
            "05" -> webView?.pageDown(true)
            "06" -> activity.showAlbum(activity.nextAlbumController(false))
            "07" -> activity.showAlbum(activity.nextAlbumController(true))
            "08" -> activity.showOverview()
            "09" -> {
                val favUrl = prefs.getString("favoriteURL", "about:blank") ?: "about:blank"
                activity.addAlbum(activity.getString(R.string.app_name), favUrl, true)
            }
            "10" -> activity.removeAlbum(activity.currentAlbumController)
            "11" -> {
                activity.overViewTab = activity.getString(R.string.album_title_tab)
                activity.setSelectedTab()
                activity.showOverview()
            }
            "12" -> {
                if (webView != null && webView.url != null) {
                    activity.shareLink(webView.title, webView.url)
                }
            }
            "13" -> activity.searchOnSite()
            "14" -> {
                if (webView != null && targetUrl != null) {
                    activity.saveBookmark(webView.title, targetUrl)
                }
            }
            "16" -> webView?.reload()
            "17" -> {
                val favUrl = prefs.getString("favoriteURL", "about:blank") ?: "about:blank"
                webView?.loadUrl(favUrl)
                activity.showAlbum(activity.currentAlbumController, favUrl)
            }
            "18" -> {
                activity.bottom_navigation?.selectedItemId = R.id.page_2
                activity.showOverview()
                activity.showDialogFilter()
            }
            "19" -> {
                if (webView != null && activity.fab_menu != null) {
                    activity.showDialogFastToggle(webView.title, webView.url, activity.fab_menu)
                }
            }
            "22" -> {
                prefs.edit().putBoolean("sp_screenOn", !prefs.getBoolean("sp_screenOn", false)).apply()
                activity.triggerRebirth(activity)
            }
            "24" -> {
                if (webView != null && webView.url != null) {
                    activity.copyLink(webView.url)
                }
            }
            "25" -> {
                activity.startActivity(Intent(activity, Settings_Activity::class.java))
            }
            "26" -> activity.doubleTapsQuit()
            "27" -> {
                prefs.edit().putString("profile", "profileStandard").apply()
                webView?.reload()
            }
            "29" -> activity.showDownloads()
            "30" -> {
                activity.overViewTab = activity.getString(R.string.album_title_bookmarks)
                activity.setSelectedTab()
                activity.showOverview()
            }
            "31" -> {
                activity.overViewTab = activity.getString(R.string.album_title_history)
                activity.setSelectedTab()
                activity.showOverview()
            }
        }
    }
}
