package com.petal.browser.controller

import com.petal.browser.activity.BrowserActivity
import com.petal.browser.media.PetalMediaBridge
import com.petal.browser.pwa.PetalPwaManager
import com.petal.browser.view.NinjaWebView
import com.petal.browser.view.PetalGeckoView

/**
 * Kotlin controller handling NinjaWebView and PetalGeckoView standalone initialization,
 * Media Bridge callbacks, and PWA Manager binding for BrowserActivity.
 */
object BrowserWebViewController {

    @JvmStatic
    fun createAndConfigureWebView(
        activity: BrowserActivity,
        title: String?,
        url: String?,
        foreground: Boolean,
        isIncognito: Boolean
    ): NinjaWebView {
        val webView = NinjaWebView(activity)
        if (isIncognito) {
            webView.isIncognito = true
        }

        // Configure Media Bridge
        val bridge = PetalMediaBridge(
            activity,
            webView,
            object : PetalMediaBridge.MediaStateListener {
                override fun onMediaPlay(title: String?, positionMs: Long, durationMs: Long) {
                    activity.isMediaPlaying = true
                    activity.updatePipParams(true)
                    activity.mediaService?.updateMediaState(title, webView.title, true, positionMs, durationMs)
                }

                override fun onMediaPause(positionMs: Long, durationMs: Long) {
                    activity.isMediaPlaying = false
                    activity.updatePipParams(false)
                    activity.mediaService?.updateMediaState(webView.title, webView.title, false, positionMs, durationMs)
                }

                override fun onMediaProgress(positionMs: Long, durationMs: Long) {}

                override fun onMediaPlayingStateChanged(playing: Boolean) {
                    activity.isMediaPlaying = playing
                    activity.updatePipParams(playing)
                }

                override fun onVideoDimensionsChanged(width: Int, height: Int) {
                    activity.updateVideoDimensions(width, height)
                }
            }
        )
        webView.setMediaBridge(bridge)

        // Configure PWA Manager
        val pwaManager = PetalPwaManager(activity, webView) { _ ->
            activity.runOnUiThread {
                // PWA notification handling callback
            }
        }
        webView.setPwaManager(pwaManager)

        // Scroll listener for address bar collapse
        webView.setOnScrollChangeListener(object : NinjaWebView.OnScrollChangeListener {
            override fun onScrollDown() {
                activity.runOnUiThread { activity.animateAddressBarCollapse(true) }
            }

            override fun onScrollUp() {
                activity.runOnUiThread { activity.animateAddressBarCollapse(false) }
            }
        })

        return webView
    }

    @JvmStatic
    fun createAndConfigureGeckoView(
        activity: BrowserActivity,
        title: String?,
        url: String?,
        foreground: Boolean,
        isIncognito: Boolean
    ): PetalGeckoView {
        val geckoView = PetalGeckoView(activity)
        if (isIncognito) {
            geckoView.setIncognito(true)
        }

        // Configure Media Bridge
        val bridge = PetalMediaBridge(
            activity,
            null,
            object : PetalMediaBridge.MediaStateListener {
                override fun onMediaPlay(title: String?, positionMs: Long, durationMs: Long) {
                    activity.isMediaPlaying = true
                    activity.updatePipParams(true)
                    activity.mediaService?.updateMediaState(title, geckoView.title, true, positionMs, durationMs)
                }

                override fun onMediaPause(positionMs: Long, durationMs: Long) {
                    activity.isMediaPlaying = false
                    activity.updatePipParams(false)
                    activity.mediaService?.updateMediaState(geckoView.title, geckoView.title, false, positionMs, durationMs)
                }

                override fun onMediaProgress(positionMs: Long, durationMs: Long) {}

                override fun onMediaPlayingStateChanged(playing: Boolean) {
                    activity.isMediaPlaying = playing
                    activity.updatePipParams(playing)
                }

                override fun onVideoDimensionsChanged(width: Int, height: Int) {
                    activity.updateVideoDimensions(width, height)
                }
            }
        )
        geckoView.setMediaBridge(bridge)

        // Configure PWA Manager
        val pwaManager = PetalPwaManager(activity, null) { _ ->
            activity.runOnUiThread {
                // PWA notification handling callback
            }
        }
        geckoView.setPwaManager(pwaManager)

        // Scroll listener for address bar collapse
        geckoView.setOnScrollChangeListener(object : PetalGeckoView.OnScrollChangeListener {
            override fun onScrollDown() {
                activity.runOnUiThread { activity.animateAddressBarCollapse(true) }
            }

            override fun onScrollUp() {
                activity.runOnUiThread { activity.animateAddressBarCollapse(false) }
            }
        })

        return geckoView
    }
}
