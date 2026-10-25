package com.petal.browser.media.sniffer

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import com.petal.browser.engine.gecko.PetalGeckoRuntime

/** Installs the passive network-level sniffer and bridges its reports to native code. */
object PetalMediaGrabberInstaller {
    private const val TAG = "PetalMediaGrabber"
    private const val ID = "petal-media-grabber@petalbrowser.app"
    private const val NATIVE_APP = "petalApp"

    fun install(context: Context) {
        val runtime = PetalGeckoRuntime.getOrCreate(context.applicationContext)
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/web_extensions/media_grabber/",
            ID
        ).accept(
            { extension ->
                if (extension == null) return@accept
                try {
                    runtime.webExtensionController.setAllowedInPrivateBrowsing(extension, true)
                    runtime.webExtensionController.enable(extension, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
                    extension.setMessageDelegate(object : WebExtension.MessageDelegate {
                        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
                            if (nativeApp != NATIVE_APP) return null
                            val json = runCatching { if (message is JSONObject) message else JSONObject(message.toString()) }.getOrNull() ?: return null
                            val msgType = json.optString("type")
                            val pageUrl = json.optString("pageUrl").takeIf { it.startsWith("http") }
                            val cookies = json.optString("cookies").takeIf { it.isNotBlank() }
                            if (PetalMediaSniffer.interceptor.isSearchEngineOrInternalUrl(pageUrl)) {
                                return null
                            }
                            if (pageUrl != null && cookies != null) {
                                PetalMediaSniffer.recordCookiesForUrl(pageUrl, cookies)
                            }
                            if (msgType == "MEDIA_GRABBED") {
                                val url = json.optString("url").takeIf { it.startsWith("http") } ?: return null
                                if (PetalMediaSniffer.interceptor.isSearchEngineOrInternalUrl(url)) return null
                                val mime = json.optString("mimeType", "video/mp4")
                                val size = json.optLong("sizeBytes", -1L).takeIf { it > 0 }
                                PetalMediaSniffer.onAggressiveMedia(url, mime, cookies, size)
                            }
                            return null
                        }
                    }, NATIVE_APP)
                    Log.i(TAG, "Media grabber enabled")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to attach media grabber delegate", t)
                }
            },
            { error -> Log.e(TAG, "Failed to load media grabber", error) }
        )
    }

}
