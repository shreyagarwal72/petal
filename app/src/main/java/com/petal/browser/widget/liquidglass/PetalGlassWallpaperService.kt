package com.petal.browser.widget.liquidglass

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Interactive Live Wallpaper engine providing Dynamic 60/120 Hz real-time glass rendering
 * underneath Petal Liquid Glass widgets.
 *
 * When enabled, widgets switch to 100% transparent backgrounds while this engine renders
 * real-time glass panels and specular highlights directly on the wallpaper surface.
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

        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            loadWallpaper()
            if (visible) scheduleRedraw()
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            PetalBlurConfig.getPrefs(this@PetalGlassWallpaperService)
                .registerOnSharedPreferenceChangeListener(prefListener)
            PetalWallpaperBlurHelper.getPrefs(this@PetalGlassWallpaperService)
                .registerOnSharedPreferenceChangeListener(prefListener)
            loadWallpaper()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            loadWallpaper(width, height)
            if (visible) scheduleRedraw()
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
            this.xOffset = xOffset.coerceIn(0f, 1f)
            if (visible) scheduleRedraw()
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (event?.action == MotionEvent.ACTION_DOWN && visible) {
                scheduleRedraw()
            }
        }

        private fun loadWallpaper(reqWidth: Int = 0, reqHeight: Int = 0) {
            try {
                val (screenW, screenH) = if (reqWidth > 0 && reqHeight > 0) {
                    reqWidth to reqHeight
                } else {
                    PetalWallpaperBlurHelper.getScreenSize(this@PetalGlassWallpaperService)
                }
                val next = PetalWallpaperBlurHelper.renderWallpaperAtScreenSize(
                    this@PetalGlassWallpaperService,
                    screenW,
                    screenH
                )
                val old = wallpaperBitmap
                wallpaperBitmap = next
                if (old != null && old !== next && !old.isRecycled) {
                    old.recycle()
                }
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
                val maxShift = (bmp.width - canvas.width).coerceAtLeast(0)
                val dx = if (maxShift > 0) -(maxShift * xOffset) else 0f
                canvas.drawBitmap(bmp, dx, 0f, null)
            } else {
                val fallbackPaint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(),
                        0xFF232C44.toInt(), 0xFF42304F.toInt(), Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), fallbackPaint)
            }

            // Draw glass panels under all active placed Liquid Glass widgets
            val ctx = this@PetalGlassWallpaperService
            val mgr = AppWidgetManager.getInstance(ctx)
            val searchIds = mgr.getAppWidgetIds(ComponentName(ctx, PetalLiquidGlassSearchWidgetReceiver::class.java))
                ?.toSet() ?: emptySet()
            val pillIds = mgr.getAppWidgetIds(ComponentName(ctx, PetalLiquidGlassPillWidgetReceiver::class.java))
                ?.toSet() ?: emptySet()
            val activeIds = searchIds + pillIds
            if (activeIds.isEmpty()) return

            val density = resources.displayMetrics.density
            val config = PetalBlurConfig.load(ctx)

            val darkLightColor = config.getDarkLightColor()
            val customTintColor = config.getTintColor()
            val baseFrostColor = if (darkLightColor != 0) darkLightColor else 0x22FFFFFF

            val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseFrostColor }
            val customPaint = if (customTintColor != 0) Paint(Paint.ANTI_ALIAS_FLAG).apply { color = customTintColor } else null

            val edgePct = config.edgeAlpha.coerceIn(0, 100)
            val strokePaint = if (edgePct > 0) {
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = config.edgeWidthDp * density
                }
            } else null

            val scale = edgePct / 100f
            fun rim(alpha: Int): Int = (((alpha * scale).roundToInt().coerceIn(0, 255)) shl 24) or 0x00FFFFFF

            for (widgetId in activeIds) {
                val rect = PetalWallpaperBlurHelper.getWidgetRect(ctx, widgetId) ?: continue
                val rectF = RectF(rect)
                val cornerPx = min(config.cornerRadius * density, rectF.height() / 2f)

                canvas.drawRoundRect(rectF, cornerPx, cornerPx, glassPaint)
                if (customPaint != null) {
                    canvas.drawRoundRect(rectF, cornerPx, cornerPx, customPaint)
                }

                if (strokePaint != null) {
                    val colors = intArrayOf(
                        rim(0x55), rim(0xA8), rim(0x18), rim(0x55),
                        rim(0xA8), rim(0x55), rim(0x18), rim(0x55), rim(0x55)
                    )
                    val pos = floatArrayOf(0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f)
                    strokePaint.shader = SweepGradient(rectF.centerX(), rectF.centerY(), colors, pos)
                    canvas.drawRoundRect(rectF, cornerPx, cornerPx, strokePaint)
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            try {
                PetalBlurConfig.getPrefs(this@PetalGlassWallpaperService)
                    .unregisterOnSharedPreferenceChangeListener(prefListener)
                PetalWallpaperBlurHelper.getPrefs(this@PetalGlassWallpaperService)
                    .unregisterOnSharedPreferenceChangeListener(prefListener)
            } catch (_: Throwable) {}
            handler.removeCallbacksAndMessages(null)
            wallpaperBitmap?.recycle()
            wallpaperBitmap = null
        }
    }
}
