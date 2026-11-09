package com.petal.browser.media

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import android.webkit.JavascriptInterface
import androidx.preference.PreferenceManager
import com.petal.browser.activity.MainActivity
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.PetalTabViewController
import org.mozilla.geckoview.MediaSession

/**
 * PetalMediaBridge
 * Connects WebView HTML5 media elements (audio/video) with MediaSessionService and PiP mode.
 * Provides JavaScript injection to monitor play, pause, timeupdate, and fullscreen video state.
 */
class PetalMediaBridge(private val context: Context, private var listener: MediaStateListener?) {

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
            "           el.addEventListener('playing', function() {" +
            "               if (window." + JS_INTERFACE_NAME + ") {" +
            "                   window." + JS_INTERFACE_NAME + ".onMediaStateChanged(true, this.title || document.title, this.currentTime * 1000, (this.duration || 0) * 1000);" +
            "               }" +
            "           });" +
            "           el.addEventListener('pause', function() {" +
            "               if (window." + JS_INTERFACE_NAME + ") {" +
            "                   window." + JS_INTERFACE_NAME + ".onMediaStateChanged(false, this.title || document.title, this.currentTime * 1000, (this.duration || 0) * 1000);" +
            "               }" +
            "           });" +
            "           el.addEventListener('ended', function() {" +
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
            "           if (el.videoWidth && el.videoHeight && window." + JS_INTERFACE_NAME + ") {" +
            "               window." + JS_INTERFACE_NAME + ".onVideoDimensions(el.videoWidth, el.videoHeight);" +
            "           }" +
            "           if (!el.paused && el.currentTime > 0 && !el.ended && window." + JS_INTERFACE_NAME + ") {" +
            "               window." + JS_INTERFACE_NAME + ".onMediaStateChanged(true, el.title || document.title, el.currentTime * 1000, (el.duration || 0) * 1000);" +
            "           }" +
            "       }" +
            "   }" +
            "   var observer = new MutationObserver(function() { hookMediaElements(); });" +
            "   observer.observe(document.documentElement || document.body, { childList: true, subtree: true });" +
            "   window.addEventListener('yt-navigate-finish', hookMediaElements);" +
            "   window.addEventListener('load', hookMediaElements);" +
            "   setInterval(function() {" +
            "       var vids = document.querySelectorAll('video');" +
            "       var anyPlaying = false;" +
            "       for (var j = 0; j < vids.length; j++) {" +
            "           if (!vids[j].paused && vids[j].currentTime > 0 && !vids[j].ended) {" +
            "               anyPlaying = true;" +
            "               if (vids[j].videoWidth && vids[j].videoHeight && window." + JS_INTERFACE_NAME + ") {" +
            "                   window." + JS_INTERFACE_NAME + ".onVideoDimensions(vids[j].videoWidth, vids[j].videoHeight);" +
            "               }" +
            "               break;" +
            "           }" +
            "       }" +
            "   }, 1500);" +
            "   hookMediaElements();" +
            "})();"

        /**
         * Checks if Auto-PiP on home gesture is enabled and attempts entering PiP mode.
         */
        @JvmStatic
        fun enterPipIfSupported(activity: Activity?, customView: View?): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity == null) {
                return false
            }
            val sp = PreferenceManager.getDefaultSharedPreferences(activity)
            val autoPip = sp.getBoolean("sp_auto_pip", true)
            if (!autoPip) return false

            return try {
                if (activity is MainActivity) {
                    BrowserMediaDelegate.triggerSystemPipMode(activity)
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    interface MediaStateListener {
        fun onMediaPlay(title: String?, positionMs: Long, durationMs: Long)
        fun onMediaPause(positionMs: Long, durationMs: Long)
        fun onMediaProgress(positionMs: Long, durationMs: Long)
        fun onMediaPlayingStateChanged(isPlaying: Boolean) {}
        fun onVideoDimensionsChanged(width: Int, height: Int) {}
        fun onSpeedChanged(speed: Float) {}
        fun onMuteChanged(muted: Boolean) {}
    }

    private var activeGeckoMediaSession: MediaSession? = null
    private var activeEngineMediaController: mozilla.components.concept.engine.mediasession.MediaSession.Controller? = null
    private var tabViewController: PetalTabViewController? = null
    private var lastKnownPositionSec = 0.0
    private var lastKnownDurationSec = 0.0
    private var isMutedState = false

    fun setListener(listener: MediaStateListener?) {
        this.listener = listener
    }

    fun getListener(): MediaStateListener? = listener

    fun attachTabViewController(tabViewController: PetalTabViewController?) {
        this.tabViewController = tabViewController
    }

    fun evaluateJavascriptOnTab(js: String) {
        if (tabViewController != null) {
            tabViewController?.evaluateJavascript(js, null)
        } else if (context is MainActivity) {
            val controller: AlbumController? = context.getCurrentAlbumController()
            if (controller is PetalTabViewController) {
                controller.evaluateJavascript(js, null)
            }
        }
    }

    fun setActiveGeckoMediaSession(session: MediaSession?) {
        this.activeGeckoMediaSession = session
    }

    fun getActiveGeckoMediaSession(): MediaSession? = activeGeckoMediaSession

    fun setActiveEngineMediaController(
        controller: mozilla.components.concept.engine.mediasession.MediaSession.Controller?
    ) {
        this.activeEngineMediaController = controller
    }

    fun getActiveEngineMediaController(): mozilla.components.concept.engine.mediasession.MediaSession.Controller? =
        activeEngineMediaController

    fun hasActiveMedia(): Boolean =
        activeGeckoMediaSession != null || activeEngineMediaController != null

    fun injectMediaHooks() {
        evaluateJavascriptOnTab(MEDIA_JS_INJECTION)
    }

    fun updatePositionState(positionSec: double, durationSec: double) {
        this.lastKnownPositionSec = positionSec
        this.lastKnownDurationSec = durationSec
    }

    fun playMedia() {
        if (activeGeckoMediaSession != null) {
            try {
                activeGeckoMediaSession?.play()
                return
            } catch (_: Exception) {}
        }
        if (activeEngineMediaController != null) {
            try {
                activeEngineMediaController?.play()
                return
            } catch (_: Exception) {}
        }
        evaluateJavascriptOnTab(
            "(function() {" +
            "   var vids = document.querySelectorAll('video');" +
            "   if (vids.length === 0) vids = document.querySelectorAll('audio');" +
            "   for (var i = 0; i < vids.length; i++) {" +
            "       try { vids[i].play(); } catch(e) {}" +
            "   }" +
            "})();"
        )
    }

    fun pauseMedia() {
        if (activeGeckoMediaSession != null) {
            try {
                activeGeckoMediaSession?.pause()
                return
            } catch (_: Exception) {}
        }
        if (activeEngineMediaController != null) {
            try {
                activeEngineMediaController?.pause()
                return
            } catch (_: Exception) {}
        }
        evaluateJavascriptOnTab(
            "(function() {" +
            "   var vids = document.querySelectorAll('video, audio');" +
            "   for (var i = 0; i < vids.length; i++) {" +
            "       try { vids[i].pause(); } catch(e) {}" +
            "   }" +
            "})();"
        )
    }

    fun changeSpeed(speed: Float) {
        val js = "(function() {" +
                "   var vids = document.querySelectorAll('video, audio');" +
                "   for (var i = 0; i < vids.length; i++) {" +
                "       try { vids[i].playbackRate = $speed; } catch(e) {}" +
                "   }" +
                "})();"
        evaluateJavascriptOnTab(js)
    }

    fun setVideoAspectRatio(mode: String) {
        val js = "(function() {" +
                "   var vids = document.querySelectorAll('video');" +
                "   var mode = '$mode';" +
                "   for (var i = 0; i < vids.length; i++) {" +
                "       var v = vids[i];" +
                "       try {" +
                "           if (mode === 'FIT') {" +
                "               v.style.objectFit = 'contain';" +
                "               v.style.width = '100%';" +
                "               v.style.height = '100%';" +
                "               v.style.aspectRatio = '';" +
                "           } else if (mode === 'ZOOM') {" +
                "               v.style.objectFit = 'cover';" +
                "               v.style.width = '100%';" +
                "               v.style.height = '100%';" +
                "               v.style.aspectRatio = '';" +
                "           } else if (mode === 'STRETCH') {" +
                "               v.style.objectFit = 'fill';" +
                "               v.style.width = '100%';" +
                "               v.style.height = '100%';" +
                "               v.style.aspectRatio = '';" +
                "           } else if (mode === '16:9' || mode === 'WIDE_16_9') {" +
                "               v.style.objectFit = 'contain';" +
                "               v.style.aspectRatio = '16/9';" +
                "           } else if (mode === '4:3' || mode === 'CLASSIC_4_3') {" +
                "               v.style.objectFit = 'contain';" +
                "               v.style.aspectRatio = '4/3';" +
                "           } else if (mode === '1:1' || mode === 'SQUARE_1_1') {" +
                "               v.style.objectFit = 'contain';" +
                "               v.style.aspectRatio = '1/1';" +
                "           } else if (mode === '9:16' || mode === 'VERTICAL_9_16') {" +
                "               v.style.objectFit = 'contain';" +
                "               v.style.aspectRatio = '9/16';" +
                "           } else if (mode === '21:9' || mode === 'CINEMA_21_9') {" +
                "               v.style.objectFit = 'contain';" +
                "               v.style.aspectRatio = '21/9';" +
                "           }" +
                "       } catch(e) {}" +
                "   }" +
                "})();"
        evaluateJavascriptOnTab(js)
    }

    fun isMuted(): Boolean = isMutedState

    fun setMuted(muted: Boolean) {
        if (isMutedState == muted) return
        toggleMute()
    }

    fun toggleMute() {
        isMutedState = !isMutedState
        if (activeGeckoMediaSession != null) {
            try {
                activeGeckoMediaSession?.muteAudio(isMutedState)
                return
            } catch (_: Exception) {}
        }
        if (activeEngineMediaController != null) {
            try {
                activeEngineMediaController?.muteAudio(isMutedState)
                return
            } catch (_: Exception) {}
        }
        evaluateJavascriptOnTab(
            "(function() {" +
            "   var vids = document.querySelectorAll('video, audio');" +
            "   for (var i = 0; i < vids.length; i++) {" +
            "       try { vids[i].muted = !vids[i].muted; } catch(e) {}" +
            "   }" +
            "})();"
        )
    }

    fun skip(deltaSeconds: Int) {
        if (activeGeckoMediaSession != null) {
            try {
                var target = Math.max(0.0, lastKnownPositionSec + deltaSeconds)
                if (lastKnownDurationSec > 0.0) {
                    target = Math.min(lastKnownDurationSec, target)
                }
                activeGeckoMediaSession?.seekTo(target, false)
                lastKnownPositionSec = target
                return
            } catch (_: Exception) {}
        }
        if (activeEngineMediaController != null) {
            try {
                var target = Math.max(0.0, lastKnownPositionSec + deltaSeconds)
                if (lastKnownDurationSec > 0.0) target = Math.min(lastKnownDurationSec, target)
                activeEngineMediaController?.seekTo(target, false)
                lastKnownPositionSec = target
                return
            } catch (_: Exception) {}
        }
        val skipJs = "(function() {" +
                "   var vids = document.querySelectorAll('video');" +
                "   if (vids.length === 0) vids = document.querySelectorAll('audio');" +
                "   var target = null;" +
                "   for (var i = 0; i < vids.length; i++) {" +
                "       if (!vids[i].paused && vids[i].currentTime > 0) { target = vids[i]; break; }" +
                "   }" +
                "   if (!target && vids.length > 0) target = vids[0];" +
                "   if (target) {" +
                "       try {" +
                "           target.currentTime = Math.max(0, Math.min(target.duration || 1e9, target.currentTime + ($deltaSeconds)));" +
                "       } catch(e) {}" +
                "   }" +
                "})();"
        evaluateJavascriptOnTab(skipJs)
    }

    fun seekMediaTo(positionMs: Long) {
        if (activeGeckoMediaSession != null) {
            try {
                val target = positionMs / 1000.0
                activeGeckoMediaSession?.seekTo(target, false)
                lastKnownPositionSec = target
                return
            } catch (_: Exception) {}
        }
        if (activeEngineMediaController != null) {
            try {
                val target = positionMs / 1000.0
                activeEngineMediaController?.seekTo(target, false)
                lastKnownPositionSec = target
                return
            } catch (_: Exception) {}
        }
        val seconds = positionMs / 1000.0
        val seekJs = "(function() {" +
                "   var vids = document.querySelectorAll('video');" +
                "   if (vids.length === 0) vids = document.querySelectorAll('audio');" +
                "   var target = null;" +
                "   for (var i = 0; i < vids.length; i++) {" +
                "       if (!vids[i].paused && vids[i].currentTime > 0) { target = vids[i]; break; }" +
                "   }" +
                "   if (!target && vids.length > 0) target = vids[0];" +
                "   if (target) {" +
                "       try {" +
                "           target.currentTime = $seconds;" +
                "       } catch(e) {}" +
                "   }" +
                "})();"
        evaluateJavascriptOnTab(seekJs)
    }

    private inner class MediaJsInterface {
        @JavascriptInterface
        fun triggerPip() {
            if (context is Activity) {
                context.runOnUiThread {
                    enterPipIfSupported(context, tabViewController?.albumView)
                }
            }
        }

        @JavascriptInterface
        fun onMediaStateChanged(isPlaying: Boolean, title: String?, positionMs: Double, durationMs: Double) {
            listener?.let { l ->
                l.onMediaPlayingStateChanged(isPlaying)
                if (isPlaying) {
                    l.onMediaPlay(title, positionMs.toLong(), durationMs.toLong())
                } else {
                    l.onMediaPause(positionMs.toLong(), durationMs.toLong())
                }
            }
        }

        @JavascriptInterface
        fun onMediaProgress(positionMs: Double, durationMs: Double) {
            listener?.onMediaProgress(positionMs.toLong(), durationMs.toLong())
        }

        @JavascriptInterface
        fun onVideoDimensions(width: Int, height: Int) {
            if (context is Activity) {
                context.runOnUiThread {
                    listener?.onVideoDimensionsChanged(width, height)
                }
            }
        }
    }
}
