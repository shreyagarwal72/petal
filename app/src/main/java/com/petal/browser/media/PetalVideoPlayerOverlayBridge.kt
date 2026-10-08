package com.petal.browser.media

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.preference.PreferenceManager
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.PetalTabViewController
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.view.PetalGeckoView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * PetalVideoPlayerOverlayBridge
 * Attaches PetalVideoPlayerOverlay directly over any video view (such as WebChromeClient customView)
 * and synchronizes state bidirectionally with PetalMediaBridge.
 *
 * NOTE: Does NOT attach on YouTube or any embedded YouTube video player.
 */
class PetalVideoPlayerOverlayBridge(
    private val activity: Activity,
    private val controller: AlbumController?,
    private val onClose: () -> Unit,
) : PetalMediaBridge.MediaStateListener {

    private val mediaBridge: PetalMediaBridge?
        get() = (controller as? PetalGeckoView)?.getMediaBridge()
            ?: (controller as? PetalTabViewController)?.getMediaBridge()
            ?: (activity as? com.petal.browser.activity.BrowserActivity)?.activeMediaBridge

    private var composeView: ComposeView? = null
    private var previousListener: PetalMediaBridge.MediaStateListener? = null

    // Reactive states observed by PetalVideoPlayerOverlay
    var isPlaying by mutableStateOf(true)
    var title by mutableStateOf(controller?.title ?: "Web Video")
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var playbackSpeed by mutableFloatStateOf(1.0f)

    companion object {
        @JvmStatic
        fun isYouTubeVideo(controller: AlbumController?, targetView: View?): Boolean {
            val webUrl = controller?.url?.lowercase() ?: ""
            if (BrowserMediaDelegate.isYouTubeUrl(webUrl)) {
                return true
            }
            // Check view class / hierarchy for YouTube embedded players
            if (targetView != null) {
                val className = targetView.javaClass.name.lowercase()
                if (className.contains("youtube") || className.contains("ytp")) {
                    return true
                }
            }
            return false
        }
    }

    fun attachOverlay(container: ViewGroup, targetView: View?): View? {
        detachOverlay()

        // Respect Native Video Player setting (sp_native_video_player).
        // If disabled, user prefers the website's own player/controls seamlessly.
        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
        val nativePlayerEnabled = sp.getBoolean("sp_native_video_player", false)
        if (!nativePlayerEnabled) {
            return null
        }

        // If the video is on YouTube or is an embedded YouTube video, bypass overlay
        if (isYouTubeVideo(controller, targetView)) {
            return null
        }

        // Hook into mediaBridge
        mediaBridge?.let { bridge ->
            previousListener = bridge.listener
            bridge.listener = this
            bridge.injectMediaHooks()
        }

        val cv = ComposeView(activity).apply {
            setContent {
                val fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                val appFont = androidx.compose.runtime.remember(fontName) {
                    AppFont.fromName(fontName)
                }
                val colorStyle = androidx.compose.runtime.remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId,
                ) {
                    // Continuous position ticker loop while playing so timeline & timers never freeze
                    LaunchedEffect(isPlaying, playbackSpeed) {
                        var lastTick = android.os.SystemClock.elapsedRealtime()
                        while (isActive && isPlaying) {
                            delay(200)
                            val now = android.os.SystemClock.elapsedRealtime()
                            val deltaMs = ((now - lastTick) * playbackSpeed).toLong()
                            lastTick = now
                            if (durationMs > 0L) {
                                positionMs = (positionMs + deltaMs).coerceIn(0L, durationMs)
                            } else {
                                positionMs += deltaMs
                            }
                        }
                    }

                    PetalVideoPlayerOverlay(
                        title = title,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        playbackSpeed = playbackSpeed,
                        onPlayPauseToggle = {
                            val mb = mediaBridge
                            if (isPlaying) {
                                mb?.pauseMedia()
                                isPlaying = false
                            } else {
                                mb?.playMedia()
                                isPlaying = true
                            }
                        },
                        onSeek = { targetMs ->
                            positionMs = targetMs
                            mediaBridge?.seekMediaTo(targetMs)
                        },
                        onFastForward = {
                            mediaBridge?.skip(10)
                            positionMs = (positionMs + 10000L).coerceAtMost(if (durationMs > 0) durationMs else Long.MAX_VALUE)
                        },
                        onRewind = {
                            mediaBridge?.skip(-10)
                            positionMs = (positionMs - 10000L).coerceAtLeast(0L)
                        },
                        onSpeedChange = { speed ->
                            playbackSpeed = speed
                            mediaBridge?.changeSpeed(speed)
                        },
                        onAspectRatioToggle = { mode ->
                            mediaBridge?.setVideoAspectRatio(mode)
                        },
                        onPipClick = {
                            BrowserMediaDelegate.triggerSystemPipMode(activity as com.petal.browser.activity.BrowserActivity)
                        },
                        onCloseFullscreen = {
                            onClose()
                        },
                        videoUrl = controller?.url,
                        onCastClick = {
                            val act = activity as? com.petal.browser.activity.BrowserActivity
                            val currentTab = controller ?: act?.currentAlbumController
                            val pageUrl = currentTab?.url ?: ""
                            // 1. Try to find sniffed media stream for current page (HLS, MP4, WebM)
                            val sniffedMedia = com.petal.browser.media.sniffer.PetalMediaSniffer.interceptor.playableMedia.value
                                .firstOrNull { it.type != com.petal.browser.media.sniffer.MediaInterceptor.MediaType.AUDIO }
                                ?: com.petal.browser.media.sniffer.PetalMediaSniffer.interceptor.detectedMedia.value
                                    .lastOrNull { it.type != com.petal.browser.media.sniffer.MediaInterceptor.MediaType.AUDIO }

                            val streamCandidate = sniffedMedia?.url

                            // 2. Query DOM for HTML5 <video> src or currentSrc
                            val queryScript = "(function() { var v = document.querySelector('video'); return v ? (v.currentSrc || v.src || '') : ''; })()"
                            when (currentTab) {
                                is PetalGeckoView -> {
                                    currentTab.evaluateJavascript(queryScript) { domSrc ->
                                        val finalUrl = when {
                                            !domSrc.isNullOrBlank() && !domSrc.startsWith("blob:") && !domSrc.startsWith("ERROR:") -> domSrc
                                            !streamCandidate.isNullOrBlank() -> streamCandidate
                                            else -> pageUrl
                                        }
                                        PetalCastManager.castMedia(activity, finalUrl, title)
                                    }
                                }
                                is PetalTabViewController -> {
                                    currentTab.evaluateJavascript(queryScript) { domSrc ->
                                        val finalUrl = when {
                                            !domSrc.isNullOrBlank() && !domSrc.startsWith("blob:") && !domSrc.startsWith("ERROR:") -> domSrc
                                            !streamCandidate.isNullOrBlank() -> streamCandidate
                                            else -> pageUrl
                                        }
                                        PetalCastManager.castMedia(activity, finalUrl, title)
                                    }
                                }
                                else -> {
                                    val finalUrl = streamCandidate ?: pageUrl
                                    PetalCastManager.castMedia(activity, finalUrl, title)
                                }
                            }
                        },
                    )
                }
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

    fun setOverlayVisible(visible: Boolean) {
        composeView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun detachOverlay() {
        // Restore previous listener if any
        mediaBridge?.let { bridge ->
            if (bridge.listener == this) {
                bridge.listener = previousListener
            }
        }
        previousListener = null

        composeView?.let { cv ->
            (cv.parent as? ViewGroup)?.removeView(cv)
        }
        composeView = null

        // Ensure activity window screenBrightness is restored to system default
        try {
            val lp = activity.window.attributes
            lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = lp
        } catch (ignored: Exception) {}
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
