package com.petal.browser.media

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity

/**
 * Petal PiP Engine & Delegate
 * Rebuilt using mpvEx architecture:
 * - Precise aspect-ratio computation clamped strictly to Android PIP boundaries [0.418f..2.39f]
 * - Dynamic SourceRectHint letterbox/pillarbox calculation for seamless, flicker-free window morphing
 * - Broadcast-driven remote actions (Rewind 10s, Play/Pause toggle, Forward 10s) with auto-updating icons
 * - Android 12+ (API 31+) seamless AutoEnter enabled state
 */
object BrowserMediaDelegate {

    const val ACTION_PIP_CONTROL = "com.petal.browser.media.ACTION_PIP_CONTROL"
    const val EXTRA_CONTROL_TYPE = "control_type"
    const val CONTROL_TYPE_PLAY = 1
    const val CONTROL_TYPE_PAUSE = 2
    const val CONTROL_TYPE_REPLAY_10 = 3
    const val CONTROL_TYPE_FORWARD_10 = 4

    private var pipReceiver: BroadcastReceiver? = null

    @JvmStatic
    fun registerPipReceiver(activity: BrowserActivity) {
        if (pipReceiver == null) {
            pipReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null || intent.action != ACTION_PIP_CONTROL) return
                    val controlType = intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)
                    val activeWebView = activity.ninjaWebView
                    val mediaBridge = activeWebView?.mediaBridge
                    when (controlType) {
                        CONTROL_TYPE_PLAY -> {
                            mediaBridge?.playMedia()
                            activity.isMediaPlaying = true
                            updatePipParams(activity, true)
                        }
                        CONTROL_TYPE_PAUSE -> {
                            mediaBridge?.pauseMedia()
                            activity.isMediaPlaying = false
                            updatePipParams(activity, false)
                        }
                        CONTROL_TYPE_REPLAY_10 -> {
                            mediaBridge?.skip(-10)
                        }
                        CONTROL_TYPE_FORWARD_10 -> {
                            mediaBridge?.skip(10)
                        }
                    }
                }
            }
            val filter = IntentFilter(ACTION_PIP_CONTROL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(activity, pipReceiver!!, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            } else {
                activity.registerReceiver(pipReceiver, filter)
            }
        }
    }

    @JvmStatic
    fun unregisterPipReceiver(activity: BrowserActivity) {
        pipReceiver?.let {
            try {
                activity.unregisterReceiver(it)
            } catch (ignored: Exception) {}
            pipReceiver = null
        }
    }

    @JvmStatic
    fun isYouTubeUrl(url: String?): Boolean {
        if (url == null || url.isEmpty()) return false
        val lower = url.lowercase()
        return lower.contains("youtube.com") ||
            lower.contains("youtu.be") ||
            lower.contains("youtube-nocookie.com") ||
            lower.contains("/embed/") ||
            lower.contains("ytimg.com")
    }

    @JvmStatic
    fun calculateSourceRect(view: View, aspectRatio: Rational): Rect {
        val viewWidth = view.width.toFloat()
        val viewHeight = view.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) {
            val globalRect = Rect()
            view.getGlobalVisibleRect(globalRect)
            return globalRect
        }

        val videoAspect = aspectRatio.toFloat()
        val viewAspect = viewWidth / viewHeight

        val localRect = if (viewAspect < videoAspect) {
            // Letterboxed: black bars top and bottom
            val height = viewWidth / videoAspect
            val top = ((viewHeight - height) / 2f).toInt()
            Rect(0, top, viewWidth.toInt(), (height + top).toInt())
        } else {
            // Pillarboxed: black bars left and right
            val width = viewHeight * videoAspect
            val left = ((viewWidth - width) / 2f).toInt()
            Rect(left, 0, (width + left).toInt(), viewHeight.toInt())
        }

        val location = IntArray(2)
        view.getLocationInWindow(location)
        localRect.offset(location[0], location[1])
        return localRect
    }

    @JvmStatic
    fun buildPipParams(activity: BrowserActivity, enableAutoEnter: Boolean): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        val pipBuilder = PictureInPictureParams.Builder()
        val isAutoPipEnabled = activity.sp?.getBoolean("sp_auto_pip", true) ?: true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pipBuilder.setAutoEnterEnabled(isAutoPipEnabled && enableAutoEnter)
        }

        // Add Remote Actions (Replay 10s, Play/Pause, Forward 10s)
        val actions = buildPipActions(activity, activity.isMediaPlaying)
        if (actions.isNotEmpty()) {
            pipBuilder.setActions(actions)
        }

        val targetView: View? = activity.customView
            ?: (activity.videoView ?: (activity.ninjaWebView ?: activity.findViewById(android.R.id.content)))

        var width = 0
        var height = 0
        if (activity.currentVideoWidth > 0 && activity.currentVideoHeight > 0) {
            width = activity.currentVideoWidth
            height = activity.currentVideoHeight
        } else if (targetView != null && targetView.width > 0 && targetView.height > 0) {
            width = targetView.width
            height = targetView.height
        }

        if (width > 0 && height > 0) {
            val ratio = (width.toFloat() / height.toFloat()).coerceIn(0.418f, 2.39f)
            val aspectRatio = Rational((ratio * 1000).toInt(), 1000)
            pipBuilder.setAspectRatio(aspectRatio)

            if (targetView != null && targetView.width > 0 && targetView.height > 0) {
                val sourceRect = calculateSourceRect(targetView, aspectRatio)
                if (!sourceRect.isEmpty) {
                    pipBuilder.setSourceRectHint(sourceRect)
                }
            }
        }

        return pipBuilder.build()
    }

    @JvmStatic
    fun updatePipParams(activity: BrowserActivity, enableAutoEnter: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                registerPipReceiver(activity)
                val params = buildPipParams(activity, enableAutoEnter)
                if (params != null) {
                    activity.setPictureInPictureParams(params)
                }
            } catch (ignored: Exception) {}
        }
    }

    @JvmStatic
    fun triggerSystemPipMode(activity: BrowserActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                registerPipReceiver(activity)
                val params = buildPipParams(activity, true)
                if (params != null) {
                    activity.enterPictureInPictureMode(params)
                }
            } catch (ignored: Exception) {}
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipActions(context: Context, isPlaying: Boolean): List<RemoteAction> {
        val actions = mutableListOf<RemoteAction>()

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // 1. Rewind / Replay 10s
        val replayIntent = Intent(ACTION_PIP_CONTROL).putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_REPLAY_10)
        val replayPendingIntent = PendingIntent.getBroadcast(context, CONTROL_TYPE_REPLAY_10, replayIntent, flag)
        val replayIcon = Icon.createWithResource(context, R.drawable.icon_pip_replay)
        actions.add(RemoteAction(replayIcon, "Rewind 10s", "Rewind 10s", replayPendingIntent))

        // 2. Play / Pause Action
        if (isPlaying) {
            val pauseIntent = Intent(ACTION_PIP_CONTROL).putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_PAUSE)
            val pausePendingIntent = PendingIntent.getBroadcast(context, CONTROL_TYPE_PAUSE, pauseIntent, flag)
            val pauseIcon = Icon.createWithResource(context, R.drawable.icon_pause)
            actions.add(RemoteAction(pauseIcon, "Pause", "Pause", pausePendingIntent))
        } else {
            val playIntent = Intent(ACTION_PIP_CONTROL).putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_PLAY)
            val playPendingIntent = PendingIntent.getBroadcast(context, CONTROL_TYPE_PLAY, playIntent, flag)
            val playIcon = Icon.createWithResource(context, R.drawable.icon_play)
            actions.add(RemoteAction(playIcon, "Play", "Play", playPendingIntent))
        }

        // 3. Fast Forward 10s
        val forwardIntent = Intent(ACTION_PIP_CONTROL).putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_FORWARD_10)
        val forwardPendingIntent = PendingIntent.getBroadcast(context, CONTROL_TYPE_FORWARD_10, forwardIntent, flag)
        val forwardIcon = Icon.createWithResource(context, R.drawable.icon_pip_forward)
        actions.add(RemoteAction(forwardIcon, "Forward 10s", "Forward 10s", forwardPendingIntent))

        return actions
    }
}
