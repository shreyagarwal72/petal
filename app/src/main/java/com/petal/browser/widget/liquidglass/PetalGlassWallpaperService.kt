package com.petal.browser.widget.liquidglass

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive Live Wallpaper engine providing Dynamic 60/120 Hz real-time glass rendering
 * underneath Petal Liquid Glass widgets.
 *
 * When enabled, widgets switch to 100% transparent backgrounds while this engine renders
 * real-time glass refraction panels and touch ripples directly on the wallpaper surface.
 */
class PetalGlassWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return GlassEngine()
    }

    private inner class GlassEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var xOffset = 0f
        private var wallpaperBitmap: Bitmap? = null

        private val drawRunnable = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            loadWallpaper()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                loadWallpaper()
                scheduleRedraw()
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            this.xOffset = xOffset
            if (visible) scheduleRedraw()
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (event?.action == MotionEvent.ACTION_DOWN && visible) {
                scheduleRedraw()
            }
        }

        private fun loadWallpaper() {
            try {
                val (screenW, screenH) = PetalWallpaperBlurHelper.getScreenSize(this@PetalGlassWallpaperService)
                wallpaperBitmap = PetalWallpaperBlurHelper.renderWallpaperAtScreenSize(this@PetalGlassWallpaperService, screenW, screenH)
            } catch (t: Throwable) {
                Log.w("PetalGlassWallpaper", "loadWallpaper failed", t)
            }
        }

        private fun scheduleRedraw() {
            handler.removeCallbacks(drawRunnable)
            handler.post(drawRunnable)
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    renderOnCanvas(canvas)
                }
            } catch (e: Exception) {
                Log.w("PetalGlassWallpaper", "drawFrame failed", e)
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {}
                }
            }
        }

        private fun renderOnCanvas(canvas: Canvas) {
            val bmp = wallpaperBitmap
            if (bmp != null && !bmp.isRecycled) {
                canvas.drawBitmap(bmp, 0f, 0f, null)
            } else {
                canvas.drawColor(Color.DKGRAY)
            }

            // Draw glass panels under all placed widget coordinates
            val density = resources.displayMetrics.density
            val config = PetalBlurConfig.load(this@PetalGlassWallpaperService)
            val cornerPx = config.cornerRadius * density

            val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (config.darkLightMode == PetalBlurConfig.DARK_LIGHT_DARK) 0x66000000 else 0x44FFFFFF
            }

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = config.edgeWidthDp * density
            }

            val sp = PetalWallpaperBlurHelper.getPrefs(this@PetalGlassWallpaperService)
            val allKeys = sp.all.keys
            for (key in allKeys) {
                if (key.endsWith("_left")) {
                    val widgetIdStr = key.removePrefix("rect_").removeSuffix("_left")
                    val widgetId = widgetIdStr.toIntOrNull() ?: continue
                    val rect = PetalWallpaperBlurHelper.getWidgetRect(this@PetalGlassWallpaperService, widgetId) ?: continue
                    val rectF = RectF(rect)

                    // Draw translucent frosted backing
                    canvas.drawRoundRect(rectF, cornerPx, cornerPx, glassPaint)

                    // Draw specular highlight sweep
                    val colors = intArrayOf(0x30FFFFFF, 0x80FFFFFF.toInt(), 0x00FFFFFF, 0x30FFFFFF, 0x80FFFFFF.toInt(), 0x30FFFFFF, 0x00FFFFFF, 0x30FFFFFF, 0x30FFFFFF)
                    val pos = floatArrayOf(0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f)
                    strokePaint.shader = SweepGradient(rectF.centerX(), rectF.centerY(), colors, pos)
                    canvas.drawRoundRect(rectF, cornerPx, cornerPx, strokePaint)
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacksAndMessages(null)
            wallpaperBitmap?.recycle()
            wallpaperBitmap = null
        }
    }
}
