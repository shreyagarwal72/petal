/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT/TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.petal.browser.predictive

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.Window

/**
 * Shared holder for capturing and storing the current browser content view snapshot (Bitmap).
 * Used by Compose bridges before mounting top-level ComposeViews to feed predictive back depth blur.
 */
object PetalContentSnapshot {
    @Volatile
    private var _current: Bitmap? = null

    /** Shared background thread for non-blocking PixelCopy requests */
    private val copyThread by lazy {
        HandlerThread("PetalPixelCopyThread").apply { start() }
    }
    private val copyHandler by lazy { Handler(copyThread.looper) }

    /**
     * Guarded read: every caller across the 15+ screens that use this (Settings,
     * History, Downloads, Tab Switcher, Bookmarks, Account Sync, etc.) was reading
     * the raw field and handing it straight to Compose's Image() with no recycled
     * check - the same class of bug TabThumbnailCache already guards against on
     * its own bitmap reads. A crash log showed "Canvas: trying to use a recycled
     * bitmap" (FATAL, kills the whole app) with no app frame in the trace, which
     * is consistent with exactly this: some caller drawing a bitmap that became
     * invalid between capture and draw. Returning null instead of a recycled
     * bitmap means the Image() call sites just skip drawing that frame instead
     * of crashing the process.
     */
    val current: Bitmap?
        get() {
            val bmp = _current
            return if (bmp != null && !bmp.isRecycled()) bmp else null
        }

    /** A pre-hide screenshot taken by the host just before it hides the page behind an overlay. */
    @Volatile
    private var preview: Bitmap? = null

    @Volatile
    private var previewAt = 0L

    @Volatile
    private var currentAt = 0L

    private const val PREVIEW_MAX_AGE_MS = 1500L

    /** Max time the main thread waits for the pre-hide PixelCopy (typically a few ms). */
    private const val PREVIEW_WAIT_MS = 60L

    /** Cleanups that land right after a new capture belong to the screen that was just replaced. */
    private const val CLEAR_GRACE_MS = 600L

    /** Scale factor for the background blur snapshot: 0.5x resolution saves 75% RAM with identical visual blur */
    private const val SNAPSHOT_SCALE = 0.5f

    /**
     * Captures the visible page BEFORE the host hides it (app bar, tab surfaces, home...) to show an
     * overlay. Kicked off asynchronously on the background copy handler without blocking the main thread.
     */
    @JvmStatic
    fun capturePreview(rootView: View) {
        val width = (rootView.width * SNAPSHOT_SCALE).toInt().coerceAtLeast(1)
        val height = (rootView.height * SNAPSHOT_SCALE).toInt().coerceAtLeast(1)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val window = findWindow(rootView)
            if (window != null) {
                try {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                    // The host hides the page right after this call and the overlay's bridge calls
                    // capture() immediately. With a purely async copy, capture() found no preview and
                    // fell back to grab() on the ALREADY-HIDDEN view tree, producing a blank (white)
                    // snapshot that the tab-switcher hero and the overlay underlay then displayed.
                    // PixelCopy completes on its own thread without needing the main thread, so wait
                    // briefly for the real pre-hide frame.
                    val done = java.util.concurrent.CountDownLatch(1)
                    PixelCopy.request(window, bitmap, { result ->
                        if (result == PixelCopy.SUCCESS) {
                            preview = bitmap
                            previewAt = SystemClock.uptimeMillis()
                            _current = bitmap
                        } else {
                            bitmap.recycle()
                        }
                        done.countDown()
                    }, copyHandler)
                    try {
                        done.await(PREVIEW_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    return
                } catch (t: Throwable) {
                    // Fall back to immediate grab
                }
            }
        }
        preview = try { grab(rootView) } catch (e: Throwable) { null }
        previewAt = SystemClock.uptimeMillis()
    }

    @JvmStatic
    fun capture(rootView: View): Bitmap {
        val existing = com.petal.browser.motion.MotionGate.getTransitionSnapshot()
        if (existing != null && !existing.isRecycled) {
            _current = existing
            return existing
        }

        val now = SystemClock.uptimeMillis()
        val pre = preview
        preview = null
        val result = if (pre != null && !pre.isRecycled && now - previewAt <= PREVIEW_MAX_AGE_MS) pre else grab(rootView)
        _current = result
        currentAt = now

        if (com.petal.browser.motion.MotionGate.isRunning) {
            com.petal.browser.motion.MotionGate.acquireTransitionSnapshot { result }
        }
        return result
    }

    private fun grab(rootView: View): Bitmap {
        val width = (rootView.width * SNAPSHOT_SCALE).toInt().coerceAtLeast(1)
        val height = (rootView.height * SNAPSHOT_SCALE).toInt().coerceAtLeast(1)

        return try {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            // A software draw skips hardware surfaces and hidden views; start from the app's
            // background colour so a degraded snapshot is never a blank/white card.
            bmp.eraseColor(
                com.google.android.material.color.MaterialColors.getColor(
                    rootView, android.R.attr.colorBackground, android.graphics.Color.BLACK
                )
            )
            val canvas = Canvas(bmp)
            canvas.scale(SNAPSHOT_SCALE, SNAPSHOT_SCALE)
            rootView.draw(canvas)
            bmp
        } catch (e: Throwable) {
            try {
                Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
            } catch (e2: Throwable) {
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }
    }

    @JvmStatic
    fun clear() {
        if (com.petal.browser.motion.MotionGate.isRunning) return
        // The overlay being replaced is disposed after the new one has already captured; wiping
        // here would hand the new screen a null underlay (black predictive-back background).
        if (SystemClock.uptimeMillis() - currentAt < CLEAR_GRACE_MS) return
        _current = null
    }

    private fun findWindow(view: View): Window? {
        var context = view.context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context.window
            }
            context = context.baseContext
        }
        return null
    }
}
