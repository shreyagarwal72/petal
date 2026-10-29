package com.petal.browser.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Comprehensive tuning parameters for the high-fidelity AGSL Liquid Glass Shader.
 * Reverse-engineered and calibrated from Liquid Glass v17.2.
 */
@Immutable
data class PetalLiquidGlassTuning(
    val blurRadiusDp: Float = 20.0f,
    val refractionStrength: Float = 0.085f,
    val edgeWidth: Float = 0.040f,
    val chromaticAberration: Float = 0.015f,
    val rimAngleDeg: Float = 45.0f,
    val rimStrength: Float = 1.20f,
    val softRimStrength: Float = 0.80f,
    val softRimWidth: Float = 1.00f,
    val softRimTint: Float = 0.50f,
    val borderStrength: Float = 0.35f,
    val contrastBoost: Float = 1.08f,
    val squircleN: Float = 3.0f,
    val perfDisableChromatic: Boolean = false,
    val perfDisableRimLight: Boolean = false
) {
    val refractionEnabled: Boolean
        get() = refractionStrength > 0.001f && edgeWidth > 0.001f
}

/**
 * High-performance AGSL Liquid Glass RuntimeShader source.
 * Reverse-engineered from Liquid Glass v17.2 (Pau).
 *
 * Features:
 *  1. Squircle SDF (superellipse exponent support up to n=4.0).
 *  2. Analytical 2D gradient surface normals.
 *  3. Ground optical lens convex profile (sin(bulgeT^2 * 2.57)).
 *  4. True 3-point prismatic chromatic dispersion (Red & Blue wavelength separation).
 *  5. Directional specular rim highlight with light source angle vector.
 *  6. Ambient soft luminous glow reflecting background dominant color.
 *  7. Light-opposing bevel occlusion shadow for 3D tactile depth.
 *  8. Subpixel unit normalization (unitPx = height * 0.5) for resolution-independent bevel sharpness.
 */
const val PETAL_LIQUID_GLASS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float radius;
uniform float cornerN;
uniform float unitPx;
uniform float edgeWidth;
uniform float refraction;
uniform float chromatic;
uniform float4 tint;
uniform float contrast;
uniform float rimStrength;
uniform float2 rimTrig;
uniform float3 softRim;
uniform float borderStrength;

float sdRoundedRect(float2 p, float2 h, float r) {
    float2 q = abs(p) - (h - float2(r));
    return length(max(q, 0.0)) - r + min(max(q.x, q.y), 0.0);
}

float sdSquircle(float2 p, float2 b, float r, float n) {
    if (r < 0.5 || abs(n - 2.0) < 0.01) return sdRoundedRect(p, b, r);
    float2 q = abs(p) - b + r;
    if (q.x <= 0.0 || q.y <= 0.0) return max(q.x, q.y) - r;
    float2 pn = max(q, 0.0) / r;
    float ln = pow(pow(pn.x, n) + pow(pn.y, n), 1.0 / n);
    return (ln - 1.0) * r;
}

float2 gradRoundedRect(float2 p, float2 b, float r) {
    float gradR = min(r * 1.5, min(b.x, b.y));
    float2 cornerCoord = abs(p) - (b - float2(gradR));
    float2 cClamped = max(cornerCoord, 0.0);
    float inCorner = step(0.0, cornerCoord.x) * step(0.0, cornerCoord.y);
    float cLen = length(cClamped) + (1.0 - inCorner);
    float2 cornerN = cClamped / cLen;
    float gradX = step(cornerCoord.y, cornerCoord.x);
    float2 flatN = float2(gradX, 1.0 - gradX);
    return sign(p) * mix(flatN, cornerN, inCorner);
}

half4 sampleAt(float2 c) {
    float2 layerSize = size - 2.0 * offset;
    return content.eval(clamp(c, float2(0.5), layerSize - float2(0.5)));
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = (coord + offset) - halfSize;
    float r = min(radius, min(halfSize.x, halfSize.y));
    float distPx = sdSquircle(p, halfSize, r, cornerN);
    if (distPx > 0.5) return content.eval(coord);

    float dist = distPx / unitPx;
    float2 hs = halfSize / unitPx;
    float2 g = gradRoundedRect(p, halfSize, r);
    float2 nDown = dot(g, g) > 1e-6 ? normalize(g) : float2(0.0, 1.0);
    float2 nUp = float2(nDown.x, -nDown.y);

    float shapeMinDim = min(hs.x, hs.y) * 2.0;
    float sizeFactor = mix(0.1, 1.0, smoothstep(0.0, 0.20, shapeMinDim));
    float effEdge = edgeWidth * sizeFactor;
    float effRefr = refraction * sizeFactor;
    float distanceFactor = abs(dist) / max(0.0001, effEdge);
    float edgeFactor = exp(-distanceFactor * 3.0);
    float bulgeT = clamp(1.0 - dist * inversesqrt(dot(hs, hs)), 0.0, 1.0);
    float bulge = sin(bulgeT * bulgeT * 2.57);
    float screenH = 2.0 * unitPx;
    float2 refPx = float2(0.0);
    if (refraction > 0.0001) refPx = nDown * bulge * effRefr * edgeFactor * screenH;

    half4 col;
    if (tint.a >= 0.99) {
        col = half4(half3(tint.rgb), 1.0);
    } else if (chromatic > 0.0001) {
        float2 disp = nDown * (chromatic * edgeFactor * 0.5) * screenH;
        half4 center = sampleAt(coord - refPx);
        half rC = sampleAt(coord - refPx + disp).r;
        half bC = sampleAt(coord - refPx - disp).b;
        col = half4(rC, center.g, bC, center.a);
    } else {
        col = sampleAt(coord - refPx);
    }
    float3 rgb = mix(float3(col.rgb), tint.rgb, tint.a);
    rgb = clamp((rgb - 0.5) * contrast + 0.5, 0.0, 1.0);

    float3 rim = float3(0.0);
    float borderDarken = 0.0;
    if (rimStrength > 0.001 || borderStrength > 0.0) {
        float2 rn = float2(nUp.x * rimTrig.x - nUp.y * rimTrig.y, nUp.x * rimTrig.y + nUp.y * rimTrig.x);
        float qLight = sqrt(max(0.0, -rn.x * rn.y));
        float qDark = sqrt(max(0.0, rn.x * rn.y));
        float diagonal = smoothstep(0.0, 2.5, qLight);
        float rimWidth = 0.002 / 1.0;
        float rimInner = smoothstep(-0.5, -0.003, dist);
        float rimOuter = 1.0 - smoothstep(0.0, rimWidth, abs(dist));
        float edgeMask = rimInner * rimOuter;
        if (rimStrength > 0.001) {
            float glowWidth = rimWidth * 45.0 * softRim.y;
            float glowFall = 1.0 - smoothstep(0.0, glowWidth, abs(dist));
            float glowMask = rimInner * glowFall * glowFall * glowFall;
            float glowDiag = smoothstep(0.3, 1.6, qLight);
            float sharpRim = edgeMask * diagonal;
            float softR = glowMask * glowDiag * 0.125 * softRim.x;
            float3 surround = rgb / max(max(rgb.r, max(rgb.g, rgb.b)), 0.05);
            float3 softCol = mix(float3(1.0), clamp(surround, 0.0, 1.0), softRim.z);
            rim = (float3(sharpRim) + softCol * softR * (1.0 - sharpRim)) * rimStrength;
        }
        if (borderStrength > 0.0) {
            borderDarken = edgeMask * smoothstep(0.15, 0.7, qDark) * borderStrength;
        }
    }
    rgb = mix(rgb, float3(0.0), borderDarken);
    return half4(half3(rgb + rim), 1.0);
}
"""

/**
 * Cache pool for AGSL RuntimeShader instances.
 * Compiles the shader once and reuses per thread / scope to prevent garbage collection churn.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object PetalLiquidShaderPool {
    private val shaders = WeakHashMap<Any, RuntimeShader>()
    private var singletonShader: RuntimeShader? = null

    fun get(scope: Any? = null): RuntimeShader {
        if (scope != null) {
            synchronized(shaders) {
                return shaders.getOrPut(scope) { RuntimeShader(PETAL_LIQUID_GLASS_AGSL) }
            }
        }
        var s = singletonShader
        if (s == null) {
            s = RuntimeShader(PETAL_LIQUID_GLASS_AGSL)
            singletonShader = s
        }
        return s
    }
}

/**
 * Extracts the corner radius from a Compose Shape for the shader.
 */
fun superellipseRadius(shape: Shape, size: Size, density: Density): Float {
    if (shape !is CornerBasedShape) return (min(size.width, size.height) * 0.15f).coerceAtLeast(4f)
    val maxRadius = min(size.width, size.height) / 2f
    val r = shape.topStart.toPx(size, density)
    return r.coerceIn(0f, maxRadius)
}

/**
 * Determines the squircle exponent (n) for continuous smooth corner curves.
 */
fun superellipseExponent(shape: Shape, requestedN: Float = 3.0f): Float {
    return requestedN.coerceIn(2.0f, 4.0f)
}

/**
 * Binds all uniforms to the AGSL RuntimeShader with subpixel unit normalization.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun bindLiquidShaderUniforms(
    shader: RuntimeShader,
    size: Size,
    offset: Offset,
    shape: Shape,
    tuning: PetalLiquidGlassTuning,
    tintColor: Color = Color.Unspecified,
    tintAlpha: Float = 0.20f,
    density: Density = Density(1f)
) {
    val width = size.width.coerceAtLeast(1f)
    val height = size.height.coerceAtLeast(1f)
    shader.setFloatUniform("size", width, height)
    shader.setFloatUniform("offset", offset.x, offset.y)

    val radius = superellipseRadius(shape, size, density)
    shader.setFloatUniform("radius", radius)

    val cornerN = superellipseExponent(shape, tuning.squircleN)
    shader.setFloatUniform("cornerN", cornerN)

    // Subpixel unit normalization factor (half height, minimum 16px)
    val unitPx = (height * 0.5f).coerceAtLeast(16f)
    shader.setFloatUniform("unitPx", unitPx)

    shader.setFloatUniform("edgeWidth", tuning.edgeWidth.coerceIn(0.001f, 0.5f))
    shader.setFloatUniform("refraction", tuning.refractionStrength.coerceIn(0f, 1f))

    val effChromatic = if (tuning.perfDisableChromatic) 0f else tuning.chromaticAberration.coerceIn(0f, 0.1f)
    shader.setFloatUniform("chromatic", effChromatic)

    val r = if (tintColor != Color.Unspecified) tintColor.red else 1f
    val g = if (tintColor != Color.Unspecified) tintColor.green else 1f
    val b = if (tintColor != Color.Unspecified) tintColor.blue else 1f
    val a = tintAlpha.coerceIn(0f, 1f)
    shader.setFloatUniform("tint", r, g, b, a)

    shader.setFloatUniform("contrast", tuning.contrastBoost.coerceIn(0.8f, 2.0f))

    val effRim = if (tuning.perfDisableRimLight) 0f else tuning.rimStrength.coerceIn(0f, 3.0f)
    shader.setFloatUniform("rimStrength", effRim)

    val angleRad = (tuning.rimAngleDeg * (PI / 180.0)).toFloat()
    shader.setFloatUniform("rimTrig", cos(angleRad), sin(angleRad))

    shader.setFloatUniform(
        "softRim",
        tuning.softRimStrength.coerceIn(0f, 3f),
        tuning.softRimWidth.coerceIn(0.1f, 5f),
        tuning.softRimTint.coerceIn(0f, 1f)
    )

    shader.setFloatUniform("borderStrength", tuning.borderStrength.coerceIn(0f, 2f))
}

/**
 * Creates an Android RenderEffect backed by the AGSL Liquid Glass RuntimeShader.
 * Returns null if below Android 13 (API 33).
 */
fun createPetalLiquidRenderEffect(
    size: Size,
    offset: Offset,
    shape: Shape,
    tuning: PetalLiquidGlassTuning,
    tintColor: Color = Color.Unspecified,
    tintAlpha: Float = 0.20f,
    density: Density = Density(1f),
    scope: Any? = null
): RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    if (size.width <= 0f || size.height <= 0f) return null

    val shader = PetalLiquidShaderPool.get(scope)
    bindLiquidShaderUniforms(
        shader = shader,
        size = size,
        offset = offset,
        shape = shape,
        tuning = tuning,
        tintColor = tintColor,
        tintAlpha = tintAlpha,
        density = density
    )
    return try {
        RenderEffect.createRuntimeShaderEffect(shader, "content")
    } catch (_: Throwable) {
        null
    }
}
