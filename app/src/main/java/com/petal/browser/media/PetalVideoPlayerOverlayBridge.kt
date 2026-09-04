package com.petal.browser.media

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.petal.browser.view.NinjaWebView

/**
 * PetalVideoPlayerOverlayBridge
 * Attaches PetalVideoPlayerOverlay directly over any video view (such as WebChromeClient customView)
 * and synchronizes state bidirectionally with PetalMediaBridge.
 */
class PetalVideoPlayerOverlayBridge(
    private val activity: Activity,
    private val webView: NinjaWebView?,
    private val onClose: () -> Unit,
) : PetalMediaBridge.MediaStateListener {

    private var composeView: ComposeView? = null
    private var previousListener: PetalMediaBridge.MediaStateListener? = null

    // Reactive states observed by PetalVideoPlayerOverlay
    var isPlaying by mutableStateOf(true)
    var title by mutableStateOf(webView?.title ?: "Web Video")
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var playbackSpeed by mutableFloatStateOf(1.0f)

    fun attachOverlay(container: ViewGroup): View {
        detachOverlay()

        // Hook into mediaBridge
        webView?.mediaBridge?.let { bridge ->
            previousListener = bridge.listener
            bridge.listener = this
            bridge.injectMediaHooks()
        }

        val cv = ComposeView(activity).apply {
            setContent {
                PetalVideoPlayerOverlay(
                    title = title,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    playbackSpeed = playbackSpeed,
                    onPlayPauseToggle = {
                        val mediaBridge = webView?.mediaBridge
                        if (isPlaying) {
                            mediaBridge?.pauseMedia()
                            isPlaying = false
                        } else {
                            mediaBridge?.playMedia()
                            isPlaying = true
                        }
                    },
                    onSeek = { targetMs ->
                        positionMs = targetMs
                        webView?.mediaBridge?.seekMediaTo(targetMs)
                    },
                    onFastForward = {
                        webView?.mediaBridge?.skip(10)
                        positionMs = (positionMs + 10000L).coerceAtMost(if (durationMs > 0) durationMs else Long.MAX_VALUE)
                    },
                    onRewind = {
                        webView?.mediaBridge?.skip(-10)
                        positionMs = (positionMs - 10000L).coerceAtLeast(0L)
                    },
                    onSpeedChange = { speed ->
                        playbackSpeed = speed
                        webView?.mediaBridge?.changeSpeed(speed)
                    },
                    onPipClick = {
                        val mediaBridge = webView?.mediaBridge
                        PetalMediaBridge.enterPipIfSupported(activity, container)
                    },
                    onCloseFullscreen = {
                        onClose()
                    },
                )
            }
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        container.addView(cv, lp)
        composeView = cv
        return cv
    }

    fun detachOverlay() {
        // Restore previous listener if any
        webView?.mediaBridge?.let { bridge ->
            if (bridge.listener == this) {
                bridge.listener = previousListener
            }
        }
        previousListener = null

        composeView?.let { cv ->
            (cv.parent as? ViewGroup)?.removeView(cv)
        }
        composeView = null
    }

    // MediaStateListener callbacks
    override fun onMediaPlay(mediaTitle: String?, posMs: Long, durMs: Long) {
        previousListener?.onMediaPlay(mediaTitle, posMs, durMs)
        activity.runOnUiThread {
            isPlaying = true
            if (!mediaTitle.isNullOrEmpty()) title = mediaTitle
            if (posMs >= 0) positionMs = posMs
            if (durMs > 0) durationMs = durMs
        }
    }

    override fun onMediaPause(posMs: Long, durMs: Long) {
        previousListener?.onMediaPause(posMs, durMs)
        activity.runOnUiThread {
            isPlaying = false
            if (posMs >= 0) positionMs = posMs
            if (durMs > 0) durationMs = durMs
        }
    }

    override fun onMediaProgress(posMs: Long, durMs: Long) {
        previousListener?.onMediaProgress(posMs, durMs)
        activity.runOnUiThread {
            if (posMs >= 0) positionMs = posMs
            if (durMs > 0) durationMs = durMs
        }
    }

    override fun onMediaPlayingStateChanged(playing: Boolean) {
        previousListener?.onMediaPlayingStateChanged(playing)
        activity.runOnUiThread {
            isPlaying = playing
        }
    }

    override fun onSpeedChanged(speed: Float) {
        previousListener?.onSpeedChanged(speed)
        activity.runOnUiThread {
            playbackSpeed = speed
        }
    }

    override fun onVideoDimensionsChanged(width: Int, height: Int) {
        previousListener?.onVideoDimensionsChanged(width, height)
    }

    override fun onMuteChanged(muted: Boolean) {
        previousListener?.onMuteChanged(muted)
    }
}
