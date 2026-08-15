/*
 * File: PetalBackdropBlur.kt
 * Description: Applies real backdrop (behind-the-bars) progressive blur
 * directly to the web content container using a plain Android View
 * RenderEffect (API 33+) -- no Compose involved. This is what makes the
 * Header Blur / Navigation Bar Blur settings show genuinely blurred page
 * content through the translucent address bar and bottom nav bar, since
 * both currently live outside the Compose tree (the WebView is a plain
 * android.webkit.WebView hosted in a native FrameLayout).
 *
 * The shader is a single dual-edge variant of the AGSL progressive blur
 * ported from sameerasw/essentials (MIT License): it blurs a strip near the
 * TOP of [target] (behind the address bar) and a strip near the BOTTOM
 * (behind the floating nav bar) in one pass, fading to zero blur toward the
 * middle of the screen.
 */

package com.petal.browser.ui.blur

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import com.petal.browser.utils.DeviceUtils

object PetalBackdropBlur {

    private const val DUAL_EDGE_BLUR_SKSL = """
        uniform shader content;
        uniform float blurRadius;
        uniform float topHeight;
        uniform float bottomHeight;
        uniform float contentHeight;

        half4 main(float2 fragCoord) {
            float topProgress = topHeight > 0.0
                ? 1.0 - clamp(fragCoord.y / topHeight, 0.0, 1.0)
                : 0.0;
            float bottomProgress = bottomHeight > 0.0
                ? 1.0 - clamp((contentHeight - fragCoord.y) / bottomHeight, 0.0, 1.0)
                : 0.0;

            float progress = max(topProgress, bottomProgress);
            progress = pow(progress, 1.5);

            float radius = progress * blurRadius;
            if (radius <= 0.0) {
                return content.eval(fragCoord);
            }

            half4 accum = half4(0.0);
            float weightSum = 0.0;

            float dither = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
            float2 jitter = float2(dither - 0.5, fract(dither * 1.618) - 0.5);

            const int SAMPLES = 4;
            float offsetScale = radius / float(SAMPLES);

            for (int x = -SAMPLES; x <= SAMPLES; x++) {
                for (int y = -SAMPLES; y <= SAMPLES; y++) {
                    float2 offset = (float2(float(x), float(y)) + jitter) * offsetScale;
                    float distSq = dot(offset, offset);
                    float radiusSq = radius * radius;
                    if (distSq <= radiusSq) {
                        float weight = exp(-3.0 * distSq / radiusSq);
                        accum += content.eval(fragCoord + offset) * weight;
                        weightSum += weight;
                    }
                }
            }

            return accum / weightSum;
        }
    """

    /** Whether this device/state can safely run the RuntimeShader blur. */
    @JvmStatic
    fun isSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !DeviceUtils.isBlurProblematicDevice() &&
                !DeviceUtils.isPowerSaveMode(context)
    }

    /**
     * Applies the dual-edge progressive blur to [target] (the web content
     * container). Call this again whenever [target]'s size or the bar
     * heights change (e.g. from a layout listener), and whenever the
     * Header Blur preference is toggled.
     *
     * @param blurRadius max blur radius in px; pass 0f (or call [clear]) to disable.
     * @param topHeightPx how far down from the top the blur fades out (address bar height).
     * @param bottomHeightPx how far up from the bottom the blur fades out (nav bar height).
     */
    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun apply(target: View, blurRadius: Float, topHeightPx: Float, bottomHeightPx: Float) {
        if (blurRadius <= 0f || target.height <= 0) {
            clear(target)
            return
        }
        val shader = RuntimeShader(DUAL_EDGE_BLUR_SKSL)
        shader.setFloatUniform("blurRadius", blurRadius)
        shader.setFloatUniform("topHeight", topHeightPx)
        shader.setFloatUniform("bottomHeight", bottomHeightPx)
        shader.setFloatUniform("contentHeight", target.height.toFloat())
        target.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
    }

    /** Clears any backdrop blur previously applied via [apply]. */
    @JvmStatic
    fun clear(target: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            target.setRenderEffect(null)
        }
    }
}
