package com.petal.browser.ui.glass.cloudy

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance O(1) StackBlur software fallback engine.
 *
 * Used for legacy Android devices (API < 31) where hardware `RenderEffect` is unavailable,
 * ensuring authentic frosted glass blur across all supported Android versions.
 */
object CloudyStackBlur {

    fun fastBlur(original: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return original

        val width = original.width
        val height = original.height
        val bitmap = original.copy(Bitmap.Config.ARGB_8888, true)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val wm = width - 1
        val hm = height - 1
        val wh = width * height
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        val a = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var asum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(width, height))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = i / divsum
            i++
        }

        yw = 0
        yi = 0

        val stack = Array(div) { IntArray(4) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var aoutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int
        var ainsum: Int

        y = 0
        while (y < height) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            ainsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            aoutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            asum = 0
            i = -radius
            while (i <= radius) {
                p = pixels[yi + min(wm, max(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                sir[3] = (p ushr 24)

                rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                asum += sir[3] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                    ainsum += sir[3]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                    aoutsum += sir[3]
                }
                i++
            }
            stackpointer = radius

            x = 0
            while (x < width) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                a[yi] = dv[asum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                asum -= aoutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                aoutsum -= sir[3]

                if (y == 0) {
                    vmin[x] = min(x + radius + 1, wm)
                }
                p = pixels[yw + vmin[x]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                sir[3] = (p ushr 24)

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                ainsum += sir[3]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                asum += ainsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                aoutsum += sir[3]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                ainsum -= sir[3]

                yi++
                x++
            }
            yw += width
            y++
        }

        x = 0
        while (x < width) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            ainsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            aoutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            asum = 0
            yp = -radius * width
            i = -radius
            while (i <= radius) {
                yi = max(0, yp) + x

                sir = stack[i + radius]

                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                sir[3] = a[yi]

                rbs = r1 - kotlin.math.abs(i)

                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                asum += a[yi] * rbs

                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                    ainsum += sir[3]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                    aoutsum += sir[3]
                }

                if (i < hm) {
                    yp += width
                }
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < height) {
                pixels[yi] = (dv[asum] shl 24) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                asum -= aoutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                aoutsum -= sir[3]

                if (x == 0) {
                    vmin[y] = min(y + r1, hm) * width
                }
                p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                sir[3] = a[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                ainsum += sir[3]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                asum += ainsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                aoutsum += sir[3]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                ainsum -= sir[3]

                yi += width
                y++
            }
            x++
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
