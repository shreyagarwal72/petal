package com.petal.browser.unit

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.petal.browser.ui.components.AddressBarPosition
import com.petal.browser.ui.components.BarCollapseState
import com.petal.browser.ui.components.BarMetrics
import com.petal.browser.ui.components.BarPrefs
import kotlin.math.roundToInt

/**
 * Single entry point for capturing a content-area tab thumbnail at the exact tile aspect ratio
 * (0.68, matching [com.petal.browser.compose.tabs.PetalTabGridSwitcher]) using hardware [PixelCopy]
 * into [Bitmap.Config.ARGB_8888] and scaling off the main thread.
 */
object TabThumbnailCapture {

    const val TILE_ASPECT_RATIO = 0.68f
    private const val TARGET_WIDTH = 480
    private val TARGET_HEIGHT = (TARGET_WIDTH / TILE_ASPECT_RATIO).roundToInt()

    private val copyThread by lazy {
        HandlerThread("PetalTabThumbCopy").apply { start() }
    }
    private val copyHandler by lazy { Handler(copyThread.looper) }

    @JvmStatic
    @JvmOverloads
    fun capture(
        rootView: View?,
        tabId: String?,
        isPrivate: Boolean,
        isHome: Boolean = false,
        onComplete: ((Bitmap?) -> Unit)? = null
    ) {
        if (rootView == null || tabId.isNullOrBlank()) {
            onComplete?.invoke(null)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !rootView.isAttachedToWindow) {
            onComplete?.invoke(null)
            return
        }
        val window = findWindow(rootView.context) ?: run {
            onComplete?.invoke(null)
            return
        }
        val cropRect = computeContentCropRect(rootView, isHome) ?: run {
            onComplete?.invoke(null)
            return
        }
        val copyWidth = cropRect.width().coerceAtMost(TARGET_WIDTH * 2).coerceAtLeast(1)
        val copyHeight = (copyWidth / TILE_ASPECT_RATIO).roundToInt().coerceAtLeast(1)
        val rawBitmap = try {
            Bitmap.createBitmap(copyWidth, copyHeight, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            onComplete?.invoke(null)
            return
        }

        try {
            PixelCopy.request(window, cropRect, rawBitmap, { result ->
                if (result != PixelCopy.SUCCESS || rawBitmap.isRecycled) {
                    if (!rawBitmap.isRecycled) rawBitmap.recycle()
                    onComplete?.invoke(null)
                    return@request
                }
                scaleAndStoreAsync(
                    source = rawBitmap,
                    recycleSource = true,
                    tabId = tabId,
                    isPrivate = isPrivate,
                    allowUniform = isHome,
                    onComplete = onComplete
                )
            }, copyHandler)
        } catch (_: Throwable) {
            if (!rawBitmap.isRecycled) rawBitmap.recycle()
            onComplete?.invoke(null)
        }
    }

    /**
     * Scales an already-captured bitmap (such as a recorded Compose GraphicsLayer snapshot of Home)
     * off the main thread to the exact 0.68 tile aspect ratio and writes it to [TabThumbnailCache].
     */
    @JvmStatic
    fun storeCapturedBitmapAsync(
        source: Bitmap,
        tabId: String,
        isPrivate: Boolean,
        isHome: Boolean,
        onComplete: ((Bitmap?) -> Unit)? = null
    ) {
        if (source.isRecycled || source.width <= 1 || source.height <= 1) {
            onComplete?.invoke(null)
            return
        }
        scaleAndStoreAsync(
            source = source,
            recycleSource = false,
            tabId = tabId,
            isPrivate = isPrivate,
            allowUniform = isHome,
            onComplete = onComplete
        )
    }

    private fun scaleAndStoreAsync(
        source: Bitmap,
        recycleSource: Boolean,
        tabId: String,
        isPrivate: Boolean,
        allowUniform: Boolean,
        onComplete: ((Bitmap?) -> Unit)?
    ) {
        TabThumbnailCache.DISK.execute {
            var softwareSrc: Bitmap? = null
            var scaled: Bitmap? = null
            try {
                var from = source
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && from.config == Bitmap.Config.HARDWARE) {
                    softwareSrc = from.copy(Bitmap.Config.ARGB_8888, false)
                    from = softwareSrc ?: return@execute
                }
                val sw = from.width
                val sh = from.height
                if (sw <= 1 || sh <= 1 || from.isRecycled) return@execute

                // Top-aligned center crop at 0.68 aspect ratio.
                val cropW: Int
                val cropH: Int
                if (sw.toFloat() / sh > TILE_ASPECT_RATIO) {
                    cropH = sh
                    cropW = (sh * TILE_ASPECT_RATIO).roundToInt().coerceIn(1, sw)
                } else {
                    cropW = sw
                    cropH = (sw / TILE_ASPECT_RATIO).roundToInt().coerceIn(1, sh)
                }
                val cropLeft = ((sw - cropW) / 2).coerceAtLeast(0)

                val outW = cropW.coerceAtMost(TARGET_WIDTH).coerceAtLeast(1)
                val outH = (outW / TILE_ASPECT_RATIO).roundToInt().coerceAtLeast(1)
                scaled = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(scaled)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(
                    from,
                    Rect(cropLeft, 0, cropLeft + cropW, cropH),
                    Rect(0, 0, outW, outH),
                    paint
                )

                val stored = TabThumbnailCache.put(tabId, scaled, isPrivate, allowUniform = allowUniform)
                onComplete?.invoke(if (stored) TabThumbnailCache.getMemoryOnly(tabId, isPrivate) ?: scaled else null)
            } catch (_: Throwable) {
                onComplete?.invoke(null)
            } finally {
                if (softwareSrc != null && !softwareSrc.isRecycled) softwareSrc.recycle()
                if (recycleSource && !source.isRecycled) source.recycle()
            }
        }
    }

    /** Computes the content-area rect (excluding status bar, address bar, and bottom nav) cropped to 0.68. */
    private fun computeContentCropRect(rootView: View, isHome: Boolean): Rect? {
        val w = rootView.width
        val h = rootView.height
        if (w <= 1 || h <= 1) return null

        val loc = IntArray(2)
        rootView.getLocationInWindow(loc)
        val rootLeft = loc[0]
        val rootTop = loc[1]

        val insets = ViewCompat.getRootWindowInsets(rootView)
        val statusTop = insets?.getInsets(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        )?.top ?: 0
        val navBottom = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0

        val sp = PreferenceManager.getDefaultSharedPreferences(rootView.context)
        val isBottomAddress = AddressBarPosition.fromPref(
            sp.getString(BarPrefs.KEY_ADDRESS_BAR_POSITION, null)
        ) == AddressBarPosition.BOTTOM
        val collapsed = BarCollapseState.isCollapsed

        val topExclude = if (isHome || isBottomAddress || collapsed) {
            statusTop
        } else {
            maxOf(statusTop, BarMetrics.topBarPx)
        }.coerceIn(0, (h - 2).coerceAtLeast(0))

        val bottomBarReserve = if (collapsed) {
            0
        } else {
            val addrBottom = if (!isHome && isBottomAddress) BarMetrics.bottomAddressPx else 0
            addrBottom + BarMetrics.bottomNavPx
        }
        val bottomExclude = maxOf(navBottom, bottomBarReserve)
            .coerceIn(0, (h - topExclude - 1).coerceAtLeast(0))

        val contentLeft = rootLeft
        val contentTop = rootTop + topExclude
        val contentW = w
        val contentH = (h - topExclude - bottomExclude).coerceAtLeast(1)

        val cropW: Int
        val cropH: Int
        if (contentW.toFloat() / contentH > TILE_ASPECT_RATIO) {
            cropH = contentH
            cropW = (contentH * TILE_ASPECT_RATIO).roundToInt().coerceIn(1, contentW)
        } else {
            cropW = contentW
            cropH = (contentW / TILE_ASPECT_RATIO).roundToInt().coerceIn(1, contentH)
        }
        val cropLeft = contentLeft + (contentW - cropW) / 2
        val cropTop = contentTop
        return Rect(cropLeft, cropTop, cropLeft + cropW, cropTop + cropH)
    }

    private fun findWindow(context: Context): Window? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx.window
            ctx = ctx.baseContext
        }
        return (ctx as? Activity)?.window
    }
}
