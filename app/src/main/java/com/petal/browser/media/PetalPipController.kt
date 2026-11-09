/*
 * PetalPipController.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Universal Picture-in-Picture (PiP) Controller for Petal Browser.
 * Unified architecture supporting:
 *   - Standalone Video Player (PetalVideoPlayerScreen / MediaPlayerActivity)
 *   - Standalone Audio Player (PetalAudioPlayerScreen)
 *   - Website In-Page and Fullscreen HTML5 Video Players (MainActivity)
 *
 * Features:
 *   - Clamped aspect ratio boundaries [0.418f..2.39f] as enforced by Android OS
 *   - Dynamic RemoteActions: Replay 10s, Play/Pause toggle with dynamic icons, Forward 10s
 *   - Seamless Auto-Enter on Android 12+ (API 31+)
 *   - SourceRectHint letterbox/pillarbox geometry for flicker-free window morphing
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.media

import android.app.Activity
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
import androidx.preference.PreferenceManager
import com.petal.browser.R

object PetalPipController {

    const val ACTION_PIP_CONTROL = "com.petal.browser.media.ACTION_PIP_CONTROL"
    const val EXTRA_CONTROL_TYPE = "control_type"
    const val CONTROL_PLAY = 1
    const val CONTROL_PAUSE = 2
    const val CONTROL_REPLAY_10 = 3
    const val CONTROL_FORWARD_10 = 4

    private var pipReceiver: BroadcastReceiver? = null
    private var activeCallback: PipActionCallback? = null

    interface PipActionCallback {
        fun onPlay()
        fun onPause()
        fun onSkip(deltaSeconds: Int)
    }

    /**
     * Registers a broadcast receiver to handle PiP window remote action taps.
     */
    fun registerPipReceiver(activity: Activity, callback: PipActionCallback) {
        activeCallback = callback
        if (pipReceiver == null) {
            pipReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null || intent.action != ACTION_PIP_CONTROL) return
                    when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                        CONTROL_PLAY -> activeCallback?.onPlay()
                        CONTROL_PAUSE -> activeCallback?.onPause()
                        CONTROL_REPLAY_10 -> activeCallback?.onSkip(-10)
                        CONTROL_FORWARD_10 -> activeCallback?.onSkip(10)
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

    /**
     * Unregisters the PiP remote action receiver.
     */
    fun unregisterPipReceiver(activity: Activity) {
        pipReceiver?.let {
            try {
                activity.unregisterReceiver(it)
            } catch (_: Exception) {}
            pipReceiver = null
        }
        activeCallback = null
    }

    /**
     * Checks if Picture-in-Picture is supported by the device hardware.
     */
    fun isPipSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    /**
     * Checks if Auto-PiP is enabled in user settings.
     */
    fun isAutoPipEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean("sp_auto_pip", true)
    }

    /**
     * Clamps aspect ratio within Android PiP permissible limits [0.418f..2.39f].
     */
    fun clampAspectRatio(ratio: Rational): Rational {
        val f = ratio.toFloat()
        return when {
            f < 0.418f -> Rational(418, 1000)
            f > 2.39f -> Rational(2390, 1000)
            else -> ratio
        }
    }

    /**
     * Calculates letterboxed / pillarboxed source rect hint for smooth morphing animation.
     */
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
            val height = (viewWidth / videoAspect).coerceAtMost(viewHeight)
            val top = ((viewHeight - height) / 2f).coerceAtLeast(0f).toInt()
            Rect(0, top, viewWidth.toInt(), (height + top).toInt().coerceAtMost(viewHeight.toInt()))
        } else {
            val width = (viewHeight * videoAspect).coerceAtMost(viewWidth)
            val left = ((viewWidth - width) / 2f).coerceAtLeast(0f).toInt()
            Rect(left, 0, (width + left).toInt().coerceAtMost(viewWidth.toInt()), viewHeight.toInt())
        }

        val location = IntArray(2)
        view.getLocationInWindow(location)
        localRect.offset(location[0], location[1])
        return localRect
    }

    /**
     * Builds PictureInPictureParams with remote actions, aspect ratio, and optional auto-enter.
     */
    fun buildPipParams(
        activity: Activity,
        isPlaying: Boolean,
        aspectRatio: Rational? = null,
        sourceView: View? = null,
        enableAutoEnter: Boolean = true
    ): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        val pipBuilder = PictureInPictureParams.Builder()
        val autoPipPref = isAutoPipEnabled(activity)

        // Android 12+ (API 31+) seamless gesture auto-enter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pipBuilder.setAutoEnterEnabled(autoPipPref && enableAutoEnter)
        }

        // Clamped Aspect Ratio
        val finalRatio = clampAspectRatio(aspectRatio ?: Rational(16, 9))
        pipBuilder.setAspectRatio(finalRatio)

        // Source Rect Hint for seamless transform
        if (sourceView != null && sourceView.width > 0 && sourceView.height > 0) {
            pipBuilder.setSourceRectHint(calculateSourceRect(sourceView, finalRatio))
        }

        // Remote Actions (Replay 10s, Play/Pause, Forward 10s)
        val actions = buildPipActions(activity, isPlaying)
        if (actions.isNotEmpty()) {
            pipBuilder.setActions(actions)
        }

        return pipBuilder.build()
    }

    /**
     * Updates PiP parameters on the activity dynamically.
     */
    fun updatePipParams(
        activity: Activity,
        isPlaying: Boolean,
        aspectRatio: Rational? = null,
        sourceView: View? = null,
        enableAutoEnter: Boolean = true
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = buildPipParams(activity, isPlaying, aspectRatio, sourceView, enableAutoEnter)
            if (params != null) {
                try {
                    activity.setPictureInPictureParams(params)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Triggers Picture-in-Picture mode on the activity.
     */
    fun enterPip(
        activity: Activity,
        isPlaying: Boolean,
        aspectRatio: Rational? = null,
        sourceView: View? = null
    ): Boolean {
        if (!isPipSupported(activity)) return false
        val params = buildPipParams(activity, isPlaying, aspectRatio, sourceView, enableAutoEnter = true)
        return try {
            if (params != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.enterPictureInPictureMode(params)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                @Suppress("DEPRECATION")
                activity.enterPictureInPictureMode()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipActions(context: Context, isPlaying: Boolean): List<RemoteAction> {
        val actions = mutableListOf<RemoteAction>()

        // 1. Rewind 10s
        val replayIntent = Intent(ACTION_PIP_CONTROL).putExtra(EXTRA_CONTROL_TYPE, CONTROL_REPLAY_10)
        val replayPending = PendingIntent.getBroadcast(
            context, CONTROL_REPLAY_10, replayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        actions.add(
            RemoteAction(
                Icon.createWithResource(context, R.drawable.icon_pip_replay),
                "-10s",
                "-10s",
                replayPending
            )
        )

        // 2. Play / Pause Toggle
        val playPauseType = if (isPlaying) CONTROL_PAUSE else CONTROL_PLAY
        val playPauseIntent = Intent(ACTION_PIP_CONTROL).putExtra(EXTRA_CONTROL_TYPE, playPauseType)
        val playPausePending = PendingIntent.getBroadcast(
            context, playPauseType, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val iconRes = if (isPlaying) R.drawable.icon_pause else R.drawable.icon_play
        val label = if (isPlaying) "Pause" else "Play"
        actions.add(
            RemoteAction(
                Icon.createWithResource(context, iconRes),
                label,
                label,
                playPausePending
            )
        )

        // 3. Forward 10s
        val forwardIntent = Intent(ACTION_PIP_CONTROL).putExtra(EXTRA_CONTROL_TYPE, CONTROL_FORWARD_10)
        val forwardPending = PendingIntent.getBroadcast(
            context, CONTROL_FORWARD_10, forwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        actions.add(
            RemoteAction(
                Icon.createWithResource(context, R.drawable.icon_pip_forward),
                "+10s",
                "+10s",
                forwardPending
            )
        )

        return actions
    }
}
