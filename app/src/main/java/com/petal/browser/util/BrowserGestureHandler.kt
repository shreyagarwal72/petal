package com.petal.browser.util

import android.content.Intent
import android.util.Log
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.activity.Settings_Activity
import com.petal.browser.view.PetalToast

/**
 * Kotlin utility handler encapsulating swipe & motion gesture actions for BrowserActivity.
 */
object BrowserGestureHandler {

    private const val TAG = "BrowserGestureHandler"

    @JvmStatic
    fun performGesture(activity: BrowserActivity, gestureKey: String, targetUrl: String?) {
        val prefs = activity.sp ?: return
        val gestureAction = prefs.getString(gestureKey, "0") ?: "0"
        val ctrl = activity.currentAlbumController
        val geckoView = ctrl as? com.petal.browser.view.PetalGeckoView

        when (gestureAction) {
            "01" -> {} // No-op
            "02" -> {
                if (geckoView != null && geckoView.canGoForward()) {
                    geckoView.goForward()
                } else {
                    PetalToast.show(activity, R.string.toast_webview_forward)
                }
            }
            "03" -> {
                if (activity.fullscreenHolder != null || activity.customView != null || activity.videoView != null) {
                    Log.v(TAG, "Petal in fullscreen mode")
                } else if (geckoView != null && geckoView.canGoBack()) {
                    prefs.edit().putBoolean("backPressed", true).apply()
                    geckoView.goBack()
                } else {
                    activity.removeAlbum(activity.currentAlbumController)
                }
            }
            "04" -> {}
            "05" -> {}
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
                val url = ctrl?.url
                if (!url.isNullOrBlank()) {
                    activity.shareLink(ctrl.title ?: "", url)
                }
            }
            "13" -> activity.searchOnSite()
            "14" -> {
                val url = targetUrl ?: ctrl?.url
                if (!url.isNullOrBlank()) {
                    activity.saveBookmark(ctrl?.title ?: "", url)
                }
            }
            "16" -> geckoView?.reload()
            "17" -> {
                val favUrl = prefs.getString("favoriteURL", "about:blank") ?: "about:blank"
                if (geckoView != null) {
                    geckoView.loadUrl(favUrl)
                    activity.showAlbum(geckoView, favUrl)
                } else {
                    activity.addAlbum(activity.getString(R.string.app_name), favUrl, true)
                }
            }
            "18" -> {
                activity.bottom_navigation?.selectedItemId = R.id.page_2
                activity.showOverview()
                activity.showDialogFilter()
            }
            "19" -> {
                val url = ctrl?.url
                if (!url.isNullOrBlank() && activity.fab_menu != null) {
                    activity.showDialogFastToggle(ctrl.title ?: "", url, activity.fab_menu)
                }
            }
            "22" -> {
                prefs.edit().putBoolean("sp_screenOn", !prefs.getBoolean("sp_screenOn", false)).apply()
                activity.triggerRebirth(activity)
            }
            "24" -> {
                val url = ctrl?.url
                if (!url.isNullOrBlank()) {
                    activity.copyLink(url)
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
