package com.petal.browser.widget.liquidglass

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.petal.browser.R
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Headless GPU-accelerated liquid glass renderer for Petal widgets.
 *
 * Implements the offscreen AGSL refraction pipeline adapted from Kyant0's AndroidLiquidGlass
 * and the offscreen HardwareRenderer baking engine used in Liquid Glass Widgets.
 */
object PetalLiquidGlassRenderer {

    private const val TAG = "PetalLiquidGlassRenderer"

    @Volatile
    private var shaderCode: String? = null

    @Volatile
    private var bakeShaderCode: String? = null

    fun loadShaderCode(context: Context): String? {
        shaderCode?.let { return it }
        return try {
            val res = context.resources.openRawResource(R.raw.liquidglass_kyant)
            val reader = BufferedReader(InputStreamReader(res))
            val sb = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                sb.append(line).append('\n')
                line = reader.readLine()
            }
            reader.close()
            res.close()
            sb.toString().also { shaderCode = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading AGSL shader code", e)
            null
        }
    }

    fun loadBakeShaderCode(context: Context): String? {
        bakeShaderCode?.let { return it }
        val raw = loadShaderCode(context) ?: return null
        return raw.replace(
            "cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0",
            "cornerCoord.x > 0.0 || cornerCoord.y > 0.0"
        ).replace(
            "normalize(",
            "safeNormalize("
        ).replace(
            "content.eval(refractedCoord)",
            "content.eval(clamp(refractedCoord, float2(0.5), size - float2(0.5)))"
        ).replace(
            "float radiusAt(float2 coord, float4 radii) {",
            "float2 safeNormalize(float2 v) {\n    return v / max(length(v), 0.0001);\n}\n\nfloat radiusAt(float2 coord, float4 radii) {"
        ).also { bakeShaderCode = it }
    }

    /**
     * Renders a wallpaper crop into a fully finished liquid glass widget background.
     */
    fun render(
        context: Context,
        wallpaperCrop: Bitmap?,
        widthPx: Int,
        heightPx: Int,
        config: PetalBlurConfig,
        density: Float,
        cornerRadiusDp: Float = -1f,
        highlightAlphaPct: Float = -1f,
        highlightWidthDp: Float = -1f
    ): Bitmap? {
        if (wallpaperCrop == null || widthPx <= 0 || heightPx <= 0) return null

        val effRadiusDp = if (cornerRadiusDp >= 0f) cornerRadiusDp else config.cornerRadius
        val maxRadiusDp = (min(widthPx, heightPx) / (2f * density))
        val clampedRadiusDp = min(effRadiusDp, maxRadiusDp)
        val cornerRadiusPx = clampedRadiusDp * density

        val effHighlightAlpha = if (highlightAlphaPct >= 0f) highlightAlphaPct.toInt() else config.edgeAlpha
        val effHighlightWidthDp = if (highlightWidthDp >= 0f) highlightWidthDp else config.edgeWidthDp

        // API 33+ (Android 13+): Offscreen HardwareRenderer with AGSL RuntimeShader
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val glassBitmap = renderWithShader(
                    context = context,
                    source = wallpaperCrop,
                    width = widthPx,
                    height = heightPx,
                    config = config,
                    density = density,
                    cornerRadiusDp = clampedRadiusDp
                )
                if (glassBitmap != null) {
                    var outBmp = glassBitmap
                    outBmp = applyOverlays(outBmp, config, cornerRadiusPx)
                    if (effHighlightAlpha > 0) {
                        outBmp = drawKyantHighlight(outBmp, cornerRadiusPx, density, effHighlightWidthDp, effHighlightAlpha)
                    }
                    outBmp = drawGlassEdges(outBmp, cornerRadiusPx, effHighlightWidthDp * density, true)
                    outBmp = applyRoundedCorners(outBmp, cornerRadiusPx)
                    return outBmp
                }
            } catch (t: Throwable) {
                Log.w(TAG, "HardwareRenderer shader render failed, falling back to CPU", t)
            }
        }

        // Fallback for pre-API 33 or when shader execution fails
        return try {
            val scaled = Bitmap.createScaledBitmap(wallpaperCrop, widthPx, heightPx, true)
            val blurRadiusInt = max(1, (config.blurRadius * density).roundToInt().coerceIn(1, 25))
            var outBmp = stackBlur(scaled, blurRadiusInt)
            outBmp = applyOverlays(outBmp, config, cornerRadiusPx)
            if (effHighlightAlpha > 0) {
                outBmp = drawKyantHighlight(outBmp, cornerRadiusPx, density, effHighlightWidthDp, effHighlightAlpha)
            }
            outBmp = drawGlassEdges(outBmp, cornerRadiusPx, effHighlightWidthDp * density, true)
            outBmp = applyRoundedCorners(outBmp, cornerRadiusPx)
            outBmp
        } catch (t: Throwable) {
            Log.e(TAG, "Fallback blur render failed", t)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun renderWithShader(
        context: Context,
        source: Bitmap,
        width: Int,
        height: Int,
        config: PetalBlurConfig,
        density: Float,
        cornerRadiusDp: Float
    ): Bitmap? {
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        val shaderSource = loadBakeShaderCode(context) ?: return null

        val runtimeShader = android.graphics.RuntimeShader(shaderSource)
        val cornerPx = min(cornerRadiusDp * density, min(width, height) / 2f)
        val refractionHeightPx = config.refractionHeight * density
        val refractionOffsetPx = -config.refractionOffset * density
        val blurRadiusPx = max(0.01f, config.blurRadius * density)

        // Set AGSL uniforms
        runtimeShader.setFloatUniform("size", width.toFloat(), height.toFloat())
        runtimeShader.setFloatUniform("offset", 0f, 0f)
        runtimeShader.setFloatUniform("cornerRadii", cornerPx, cornerPx, cornerPx, cornerPx)
        runtimeShader.setFloatUniform("refractionHeight", refractionHeightPx)
        runtimeShader.setFloatUniform("refractionAmount", refractionOffsetPx)
        runtimeShader.setFloatUniform("depthEffect", config.dispersion)

        var renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(runtimeShader, "content")
        if (blurRadiusPx > 0.01f) {
            val blurEffect = android.graphics.RenderEffect.createBlurEffect(
                blurRadiusPx,
                blurRadiusPx,
                android.graphics.Shader.TileMode.CLAMP
            )
            renderEffect = android.graphics.RenderEffect.createChainEffect(renderEffect, blurEffect)
        }

        val renderNode = android.graphics.RenderNode("PetalLiquidGlassWidget")
        renderNode.setPosition(0, 0, width, height)
        val canvas = renderNode.beginRecording(width, height)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        renderNode.endRecording()
        renderNode.setRenderEffect(renderEffect)

        val baked = renderNodeToBitmap(renderNode, width, height)
        renderNode.discardDisplayList()
        if (scaled != source) scaled.recycle()

        return baked
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun renderNodeToBitmap(renderNode: android.graphics.RenderNode, width: Int, height: Int): Bitmap? {
        var imageReader: ImageReader? = null
        var hardwareRenderer: android.graphics.HardwareRenderer? = null
        var image: android.media.Image? = null
        return try {
            imageReader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
            )
            val rootNode = android.graphics.RenderNode("RootNode")
            rootNode.setPosition(0, 0, width, height)
            val rootCanvas = rootNode.beginRecording(width, height)
            rootCanvas.drawRenderNode(renderNode)
            rootNode.endRecording()

            hardwareRenderer = android.graphics.HardwareRenderer().apply {
                setSurface(imageReader.surface)
                setContentRoot(rootNode)
            }
            hardwareRenderer.createRenderRequest().syncAndDraw()

            image = imageReader.acquireLatestImage() ?: return null
            val hardwareBuffer = image.hardwareBuffer ?: return null
            val hwBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, null) ?: return null
            val copy = hwBitmap.copy(Bitmap.Config.ARGB_8888, true)
            hwBitmap.recycle()
            hardwareBuffer.close()
            rootNode.discardDisplayList()
            copy
        } catch (t: Throwable) {
            Log.e(TAG, "renderNodeToBitmap failed", t)
            null
        } finally {
            image?.close()
            hardwareRenderer?.apply {
                setSurface(null)
                destroy()
            }
            imageReader?.close()
        }
    }

    private fun drawGlassEdges(bitmap: Bitmap, cornerRadiusPx: Float, strokeWidthPx: Float, enabled: Boolean): Bitmap {
        if (!enabled || bitmap.width <= 0 || bitmap.height <= 0) return bitmap
        val mutableBmp = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true).also { bitmap.recycle() }

        val canvas = Canvas(mutableBmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }

        val stroke = max(strokeWidthPx * 0.8f, 1f)
        val halfStroke = stroke / 2f
        val radius = max(0f, cornerRadiusPx - halfStroke)
        val rect = RectF(halfStroke, halfStroke, mutableBmp.width - halfStroke, mutableBmp.height - halfStroke)
        paint.strokeWidth = stroke

        val colors = intArrayOf(
            0x40FFFFFF,
            0x80FFFFFF.toInt(),
            0x40FFFFFF,
            0x20FFFFFF,
            0x40FFFFFF,
            0x80FFFFFF.toInt(),
            0x40FFFFFF,
            0x20FFFFFF,
            0x40FFFFFF
        )
        val positions = floatArrayOf(
            0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f
        )
        paint.shader = SweepGradient(mutableBmp.width / 2f, mutableBmp.height / 2f, colors, positions)
        canvas.drawRoundRect(rect, radius, radius, paint)
        return mutableBmp
    }

    private fun drawKyantHighlight(
        bitmap: Bitmap,
        cornerRadiusPx: Float,
        density: Float,
        strokeWidthDp: Float,
        alphaPct: Int
    ): Bitmap {
        if (alphaPct <= 0 || bitmap.width <= 0 || bitmap.height <= 0) return bitmap
        val mutableBmp = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true).also { bitmap.recycle() }

        val canvas = Canvas(mutableBmp)
        val maxR = min(mutableBmp.width, mutableBmp.height) / 2f
        val radius = cornerRadiusPx.coerceIn(0f, maxR)
        val stroke = max(1f, strokeWidthDp * density)

        val path = Path().apply {
            addRoundRect(
                RectF(0f, 0f, mutableBmp.width.toFloat(), mutableBmp.height.toFloat()),
                radius,
                radius,
                Path.Direction.CW
            )
        }
        canvas.save()
        canvas.clipPath(path)

        val baseAlpha = (alphaPct.coerceIn(0, 100) / 100f * 255f)
        val v15 = (baseAlpha.roundToInt().coerceIn(0, 255) shl 24) or 0x00FFFFFF
        val v14 = ((baseAlpha * 0.707f).roundToInt().coerceIn(0, 255) shl 24) or 0x00FFFFFF
        val transparent = 0x00FFFFFF

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ceil(stroke.toDouble()).toFloat() * 2f
            shader = SweepGradient(
                mutableBmp.width / 2f,
                mutableBmp.height / 2f,
                intArrayOf(v14, v15, transparent, v14, v15, v14, transparent, v14, v14),
                floatArrayOf(0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f)
            )
            maskFilter = BlurMaskFilter(max(1f, stroke / 2f), BlurMaskFilter.Blur.NORMAL)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }

        canvas.drawRoundRect(
            RectF(0f, 0f, mutableBmp.width.toFloat(), mutableBmp.height.toFloat()),
            radius,
            radius,
            paint
        )
        canvas.restore()
        return mutableBmp
    }

    private fun applyOverlays(bitmap: Bitmap, config: PetalBlurConfig, cornerRadiusPx: Float): Bitmap {
        val darkLightColor = config.getDarkLightColor()
        val tintColor = config.getTintColor()
        if (darkLightColor == 0 && tintColor == 0) return bitmap

        val mutableBmp = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true).also { bitmap.recycle() }
        val canvas = Canvas(mutableBmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, mutableBmp.width.toFloat(), mutableBmp.height.toFloat())

        if (darkLightColor != 0) {
            paint.color = darkLightColor
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
        }
        if (tintColor != 0) {
            paint.color = tintColor
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
        }
        return mutableBmp
    }

    private fun applyRoundedCorners(bitmap: Bitmap, cornerRadiusPx: Float): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())

        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        bitmap.recycle()
        return out
    }

    /** Fast StackBlur algorithm for software fallback. */
    fun stackBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        val bitmap = if (sentBitmap.isMutable) sentBitmap else sentBitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (radius < 1) return bitmap

        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) {
            dv[idx] = idx / divsum
        }

        yi = 0
        yw = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        for (yIdx in 0 until h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            for (j in -radius..radius) {
                p = pix[yi + min(wm, max(j, 0))]
                sir = stack[j + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - kotlin.math.abs(j)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (j > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackpointer = radius

            for (xIdx in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (yIdx == 0) {
                    vmin[xIdx] = min(xIdx + radius + 1, wm)
                }
                p = pix[yw + vmin[xIdx]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }

        for (xIdx in 0 until w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (j in -radius..radius) {
                yi = max(0, yp) + xIdx
                sir = stack[j + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - kotlin.math.abs(j)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (j > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (j < hm) {
                    yp += w
                }
            }
            yi = xIdx
            stackpointer = radius
            for (yIdx in 0 until h) {
                pix[yi] = (-0x1000000 and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (xIdx == 0) {
                    vmin[yIdx] = min(yIdx + r1, hm) * w
                }
                p = xIdx + vmin[yIdx]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }
}
