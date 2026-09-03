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
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.core.view.drawToBitmap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Shared holder for capturing and storing the current browser content view snapshot (Bitmap).
 * Used by Compose bridges before mounting top-level ComposeViews to feed predictive back depth blur.
 */
object PetalContentSnapshot {
    @Volatile
    private var _current: Bitmap? = null

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

    @JvmStatic
    fun capture(rootView: View): Bitmap {
        val width = rootView.width.coerceAtLeast(1)
        val height = rootView.height.coerceAtLeast(1)

        var captured: Bitmap? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val window = findWindow(rootView)
            if (window != null) {
                try {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val latch = CountDownLatch(1)
                    var copyResult = PixelCopy.ERROR_UNKNOWN

                    val thread = HandlerThread("PetalPixelCopyThread")
                    thread.start()
                    val handler = Handler(thread.looper)

                    PixelCopy.request(window, bitmap, { result ->
                        copyResult = result
                        latch.countDown()
                    }, handler)

                    val success = latch.await(200, TimeUnit.MILLISECONDS)
                    thread.quitSafely()

                    if (success && copyResult == PixelCopy.SUCCESS) {
                        captured = bitmap
                    } else {
                        bitmap.recycle()
                    }
                } catch (e: Throwable) {
                    captured = null
                }
            }
        }

        if (captured == null) {
            captured = try {
                rootView.drawToBitmap(Bitmap.Config.ARGB_8888)
            } catch (e: Throwable) {
                try {
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    rootView.draw(canvas)
                    bmp
                } catch (e2: Throwable) {
                    Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                }
            }
        }

        _current = captured
        return captured
    }

    @JvmStatic
    fun clear() {
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
