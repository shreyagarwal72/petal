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
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.core.view.drawToBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Shared holder for capturing and providing live browser content view snapshots (Bitmap).
 * Used by Compose bridges before and during predictive back gestures to supply dynamic,
 * real-time underlays rather than static or stale screenshots.
 */
object PetalContentSnapshot {
    @Volatile
    private var _current: Bitmap? = null

    private val _liveSnapshotFlow = MutableStateFlow<Bitmap?>(null)
    val liveSnapshotFlow: StateFlow<Bitmap?> = _liveSnapshotFlow.asStateFlow()

    private var activeRootViewRef: WeakReference<View>? = null

    /**
     * Guarded read: returns valid non-recycled bitmap or null.
     */
    val current: Bitmap?
        get() {
            val bmp = _current
            return if (bmp != null && !bmp.isRecycled) bmp else null
        }

    /**
     * Registers active root view for real-time dynamic refresh during back gestures.
     */
    @JvmStatic
    fun registerActiveRootView(rootView: View) {
        activeRootViewRef = WeakReference(rootView)
    }

    /**
     * Captures a live snapshot synchronously or updates the current snapshot buffer.
     */
    @JvmStatic
    fun capture(rootView: View): Bitmap {
        registerActiveRootView(rootView)
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

                    val success = latch.await(150, TimeUnit.MILLISECONDS)
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
        _liveSnapshotFlow.value = captured
        return captured
    }

    /**
     * Triggers asynchronous live capture to refresh preview during dynamic web changes.
     */
    @JvmStatic
    fun refreshLiveSnapshotAsync(onComplete: ((Bitmap?) -> Unit)? = null) {
        val view = activeRootViewRef?.get() ?: return
        if (view.width <= 0 || view.height <= 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val window = findWindow(view)
            if (window != null) {
                try {
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    PixelCopy.request(window, bitmap, { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            _current = bitmap
                            _liveSnapshotFlow.value = bitmap
                            Handler(Looper.getMainLooper()).post { onComplete?.invoke(bitmap) }
                        } else {
                            bitmap.recycle()
                            Handler(Looper.getMainLooper()).post { onComplete?.invoke(current) }
                        }
                    }, Handler(Looper.getMainLooper()))
                    return
                } catch (ignored: Throwable) {}
            }
        }

        try {
            val bmp = view.drawToBitmap(Bitmap.Config.ARGB_8888)
            _current = bmp
            _liveSnapshotFlow.value = bmp
            onComplete?.invoke(bmp)
        } catch (ignored: Throwable) {
            onComplete?.invoke(current)
        }
    }

    @JvmStatic
    fun clear() {
        _current = null
        _liveSnapshotFlow.value = null
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
