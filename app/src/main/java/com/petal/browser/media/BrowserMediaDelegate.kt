package com.petal.browser.media

import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import android.view.View
import androidx.annotation.RequiresApi
import com.petal.browser.activity.BrowserActivity

/**
 * Kotlin delegate handling Picture-in-Picture (PiP) parameter building,
 * aspect ratio calculation, and system PiP triggers for BrowserActivity.
 */
object BrowserMediaDelegate {

    @JvmStatic
    fun updatePipParams(activity: BrowserActivity, enableAutoEnter: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val pipBuilder = PictureInPictureParams.Builder()
                val isAutoPipEnabled = activity.sp?.getBoolean("sp_auto_pip", true) ?: true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    pipBuilder.setAutoEnterEnabled(isAutoPipEnabled && enableAutoEnter)
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
                    var ratio = width.toFloat() / height.toFloat()
                    if (ratio > 2.39f) ratio = 2.39f
                    if (ratio < 0.418f) ratio = 0.418f
                    val aspectRatio = Rational((ratio * 1000).toInt(), 1000)
                    pipBuilder.setAspectRatio(aspectRatio)

                    if (targetView != null) {
                        val rect = Rect()
                        targetView.getGlobalVisibleRect(rect)
                        if (!rect.isEmpty) {
                            pipBuilder.setSourceRectHint(rect)
                        }
                    }
                }
                activity.setPictureInPictureParams(pipBuilder.build())
            } catch (ignored: Exception) {}
        }
    }

    @JvmStatic
    fun triggerSystemPipMode(activity: BrowserActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val pipBuilder = PictureInPictureParams.Builder()
                val isAutoPipEnabled = activity.sp?.getBoolean("sp_auto_pip", true) ?: true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    pipBuilder.setAutoEnterEnabled(isAutoPipEnabled)
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
                    var ratio = width.toFloat() / height.toFloat()
                    if (ratio > 2.39f) ratio = 2.39f
                    if (ratio < 0.418f) ratio = 0.418f
                    val aspectRatio = Rational((ratio * 1000).toInt(), 1000)
                    pipBuilder.setAspectRatio(aspectRatio)

                    if (targetView != null) {
                        val rect = Rect()
                        targetView.getGlobalVisibleRect(rect)
                        if (!rect.isEmpty) {
                            pipBuilder.setSourceRectHint(rect)
                        }
                    }
                }
                activity.enterPictureInPictureMode(pipBuilder.build())
            } catch (ignored: Exception) {}
        }
    }
}
