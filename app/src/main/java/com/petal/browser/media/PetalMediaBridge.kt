package com.petal.browser.media

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.util.Rational
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.preference.PreferenceManager

/**
 * PetalMediaBridge
 * Connects WebView HTML5 media elements (audio/video) with MediaSessionService and PiP mode.
 * Provides JavaScript injection to monitor play, pause, timeupdate, and fullscreen video state.
 */
class PetalMediaBridge(private val context: Context, private val webView: WebView) {

    private var isMediaPlaying = false
    private var mediaTitle: String = "Web Media"
    private var mediaPosition: Long = 0
    private var mediaDuration: Long = 0
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var mediaSessionListener: MediaSessionUpdateListener? = null

    interface MediaSessionUpdateListener {
        fun onMediaPlayStateChanged(isPlaying: Boolean, title: String, positionMs: Long, durationMs: Long)
        fun onMediaPositionUpdated(positionMs: Long, durationMs: Long)
        fun onVideoSizeChanged(width: Int, height: Int)
    }

    fun setMediaSessionListener(listener: MediaSessionUpdateListener?) {
        this.mediaSessionListener = listener
    }

    init {
        setupBridge()
    }

    private fun setupBridge() {
        webView.addJavascriptInterface(MediaJsInterface(), JS_INTERFACE_NAME)
    }

    fun injectMediaScript() {
        webView.evaluateJavascript(MEDIA_JS_INJECTION, null)
    }

    fun playMedia() {
        webView.evaluateJavascript(
            "(function() {" +
            "   var media = document.querySelector('video, audio');" +
            "   if (media) media.play();" +
            "})();", null
        )
    }

    fun pauseMedia() {
        webView.evaluateJavascript(
            "(function() {" +
            "   var media = document.querySelector('video, audio');" +
            "   if (media) media.pause();" +
            "})();", null
        )
    }

    fun seekMediaTo(positionMs: Long) {
        val seconds = positionMs / 1000.0
        webView.evaluateJavascript(
            "(function() {" +
            "   var media = document.querySelector('video, audio');" +
            "   if (media) media.currentTime = " + seconds + ";" +
            "})();", null
        )
    }

    fun skipMediaForward(seconds: Int) {
        webView.evaluateJavascript(
            "(function() {" +
            "   var media = document.querySelector('video, audio');" +
            "   if (media) media.currentTime += " + seconds + ";" +
            "})();", null
        )
    }

    fun skipMediaBackward(seconds: Int) {
        webView.evaluateJavascript(
            "(function() {" +
            "   var media = document.querySelector('video, audio');" +
            "   if (media) media.currentTime -= " + seconds + ";" +
            "})();", null
        )
    }

    fun setPlaybackRate(rate: Float) {
        webView.evaluateJavascript(
            "(function() {" +
            "   var media = document.querySelector('video, audio');" +
            "   if (media) media.playbackRate = " + rate + ";" +
            "})();", null
        )
    }

    fun setMuted(muted: Boolean) {
        webView.evaluateJavascript(
            "(function() {" +
            "   var media = document.querySelector('video, audio');" +
            "   if (media) media.muted = " + muted + ";" +
            "})();", null
        )
    }

    fun enterPipMode(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder()
            if (videoWidth > 0 && videoHeight > 0) {
                val clampedW = videoWidth.coerceIn(1, 10000)
                val clampedH = videoHeight.coerceIn(1, 10000)
                var ratio = Rational(clampedW, clampedH)
                if (ratio.toFloat() < 0.418410f) {
                    ratio = Rational(418, 1000)
                } else if (ratio.toFloat() > 2.390000f) {
                    ratio = Rational(239, 100)
                }
                builder.setAspectRatio(ratio)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true)
                builder.setSeamlessResizeEnabled(true)
            }
            try {
                activity.enterPictureInPictureMode(builder.build())
            } catch (ignored: Exception) {
            }
        }
    }

    fun isMediaPlaying(): Boolean = isMediaPlaying
    fun getMediaTitle(): String = mediaTitle
    fun getMediaPosition(): Long = mediaPosition
    fun getMediaDuration(): Long = mediaDuration
    fun getVideoWidth(): Int = videoWidth
    fun getVideoHeight(): Int = videoHeight

    private inner class MediaJsInterface {
        @JavascriptInterface
        fun onMediaStateChanged(playing: Boolean, title: String?, positionMs: Long, durationMs: Long) {
            isMediaPlaying = playing
            mediaTitle = if (!title.isNullOrBlank()) title else "Web Media"
            mediaPosition = positionMs
            mediaDuration = durationMs

            mediaSessionListener?.onMediaPlayStateChanged(playing, mediaTitle, positionMs, durationMs)

            // Auto Background Video Playback handling
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val autoBgPlay = sp.getBoolean("sp_background_media_play", true)
            if (autoBgPlay && playing && context is Activity) {
                // Background playback keepalive
            }
        }

        @JavascriptInterface
        fun onMediaProgress(positionMs: Long, durationMs: Long) {
            mediaPosition = positionMs
            mediaDuration = durationMs
            mediaSessionListener?.onMediaPositionUpdated(positionMs, durationMs)
        }

        @JavascriptInterface
        fun onVideoDimensions(width: Int, height: Int) {
            videoWidth = width
            videoHeight = height
            mediaSessionListener?.onVideoSizeChanged(width, height)
        }
    }

    companion object {
        private const val JS_INTERFACE_NAME = "PetalMediaInterface"

        const val MEDIA_JS_INJECTION =
            "(function() {" +
            "   try {" +
            "       Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });" +
            "       Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });" +
            "       window.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);" +
            "       document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);" +
            "   } catch(e) {}" +
            "   if (window.petalMediaInjected) return;" +
            "   window.petalMediaInjected = true;" +
            "   function hookMediaElements() {" +
            "       var mediaEls = document.querySelectorAll('video, audio');" +
            "       for (var i = 0; i < mediaEls.length; i++) {" +
            "           var el = mediaEls[i];" +
            "           if (el.dataset.petalHooked) continue;" +
            "           el.dataset.petalHooked = 'true';" +
            "           el.addEventListener('play', function() {" +
            "               if (window." + JS_INTERFACE_NAME + ") {" +
            "                   window." + JS_INTERFACE_NAME + ".onMediaStateChanged(true, this.title || document.title, this.currentTime * 1000, (this.duration || 0) * 1000);" +
            "               }" +
            "           });" +
            "           el.addEventListener('pause', function() {" +
            "               if (window." + JS_INTERFACE_NAME + ") {" +
            "                   window." + JS_INTERFACE_NAME + ".onMediaStateChanged(false, this.title || document.title, this.currentTime * 1000, (this.duration || 0) * 1000);" +
            "               }" +
            "           });" +
            "           el.addEventListener('timeupdate', function() {" +
            "               if (!this.paused && window." + JS_INTERFACE_NAME + ") {" +
            "                   window." + JS_INTERFACE_NAME + ".onMediaProgress(this.currentTime * 1000, (this.duration || 0) * 1000);" +
            "               }" +
            "           });" +
            "           el.addEventListener('loadedmetadata', function() {" +
            "               if (window." + JS_INTERFACE_NAME + ") {" +
            "                   window." + JS_INTERFACE_NAME + ".onVideoDimensions(this.videoWidth || 0, this.videoHeight || 0);" +
            "               }" +
            "           });" +
            "           el.addEventListener('resize', function() {" +
            "               if (window." + JS_INTERFACE_NAME + ") {" +
            "                   window." + JS_INTERFACE_NAME + ".onVideoDimensions(this.videoWidth || 0, this.videoHeight || 0);" +
            "               }" +
            "           });" +
            "       }" +
            "   }" +
            "   hookMediaElements();" +
            "   setInterval(hookMediaElements, 1000);" +
            "})();"
    }
}
