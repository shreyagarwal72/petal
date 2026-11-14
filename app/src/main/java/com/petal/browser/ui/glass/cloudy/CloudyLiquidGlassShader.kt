package com.petal.browser.ui.glass.cloudy

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AGSL Liquid Glass 2.0 Shader Engine (Inspired by skydoves/Cloudy).
 *
 * Implements:
 * 1. 2D Signed Distance Field (SDF) continuous corner normal vector field.
 * 2. Spectral Chromatic Dispersion (Cauchy RGB wavelength separation along curved edges).
 * 3. Directional specular highlight rim glint.
 */
object CloudyLiquidGlassShader {

    private const val SHADER_SRC = """
        uniform shader image;
        uniform float2 resolution;
        uniform float cornerRadius;
        uniform float rimWidth;
        uniform float refractionStrength;
        uniform float chromaticDispersion;
        uniform float specularIntensity;
        uniform float isDark;

        float sdRoundRect(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + float2(r);
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 fragCoord) {
            float2 halfRes = resolution * 0.5;
            float2 p = fragCoord - halfRes;
            float dist = sdRoundRect(p, halfRes, cornerRadius);

            if (dist > 0.0) {
                return half4(0.0);
            }

            float safeRim = max(rimWidth, 1.0);
            float rimProgress = clamp(-dist / safeRim, 0.0, 1.0);

            // Compute 2D surface normal from SDF gradient
            float eps = 1.0;
            float dx = sdRoundRect(p + float2(eps, 0.0), halfRes, cornerRadius) - sdRoundRect(p - float2(eps, 0.0), halfRes, cornerRadius);
            float dy = sdRoundRect(p + float2(0.0, eps), halfRes, cornerRadius) - sdRoundRect(p - float2(0.0, eps), halfRes, cornerRadius);
            float2 norm = float2(dx, dy);
            float normLen = length(norm);
            float2 normal = (normLen > 0.0001) ? (norm / normLen) : float2(0.0);

            // Optical curvature refraction displacement
            float bend = pow(1.0 - rimProgress, 1.8) * refractionStrength;
            float2 refractOffset = normal * bend;

            // Chromatic dispersion (wavelength offset)
            float disp = chromaticDispersion * bend * 0.65;
            float4 rCol = image.eval(fragCoord + refractOffset + normal * disp);
            float4 gCol = image.eval(fragCoord + refractOffset);
            float4 bCol = image.eval(fragCoord + refractOffset - normal * disp);
            float4 baseColor = float4(rCol.r, gCol.g, bCol.b, (rCol.a + gCol.a + bCol.a) / 3.0);

            // Directional specular glint from top-left light angle
            float2 lightDir = normalize(float2(-0.5, -0.8));
            float specFactor = pow(max(dot(normal, lightDir), 0.0), 12.0) * specularIntensity * (1.0 - rimProgress);
            float4 specLight = (isDark > 0.5) ? float4(1.0, 1.0, 1.0, 0.9) : float4(1.0, 1.0, 1.0, 0.65);

            return half4(baseColor.rgb + specLight.rgb * specFactor, baseColor.a);
        }
    """

    private var cachedShader: RuntimeShader? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getOrCreateShader(): RuntimeShader {
        return cachedShader ?: RuntimeShader(SHADER_SRC).also { cachedShader = it }
    }
}

/**
 * Applies the Cloudy Liquid Glass 2.0 optical chromatic dispersion and 2D SDF refraction
 * to this composable.
 *
 * Safe on all Android versions: strictly no-op on API < 33 or when [tuning.enabled] is false.
 */
fun Modifier.cloudyOpticalRefraction(
    cornerRadius: Dp,
    rimWidth: Dp = 24.dp,
    refractionStrength: Float = 16f,
    tuning: CloudyGlassTuning = CloudyGlassTuning(),
    isDark: Boolean = true,
): Modifier {
    if (!tuning.enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this
    }

    val dispersionMultiplier = tuning.dispersion * tuning.dispersionMode.factor

    return this.graphicsLayer {
        clip = true
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@graphicsLayer

        val shader = CloudyLiquidGlassShader.getOrCreateShader()
        shader.setFloatUniform("resolution", w, h)
        shader.setFloatUniform("cornerRadius", cornerRadius.toPx())
        shader.setFloatUniform("rimWidth", rimWidth.toPx())
        shader.setFloatUniform("refractionStrength", refractionStrength)
        shader.setFloatUniform("chromaticDispersion", dispersionMultiplier * 12f)
        shader.setFloatUniform("specularIntensity", tuning.specularGlint)
        shader.setFloatUniform("isDark", if (isDark) 1f else 0f)

        val effect = RenderEffect.createRuntimeShaderEffect(shader, "image")
        renderEffect = effect.asComposeRenderEffect()
    }
}
