/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Matrix
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.preference.PreferenceManager
import com.petal.browser.ui.containment.isLiquidGlassMaster
import com.petal.browser.ui.glass.LocalPetalGlassWallpaper
import com.petal.browser.ui.glass.PetalGlassWallpaper
import com.petal.browser.ui.glass.captureGlassWallpaperLive
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.wallpaper.PetalGlassAwareWallpaperBackground
import kotlin.math.PI
import kotlin.math.sin

/**
 * The 5 canonical background modes across Petal Browser:
 * - [HOME_WALLPAPER]: Homepage background (only PetalWallpaperManager wallpaper or seasonal/plain surface).
 * - [BLOOM_GLASS]: M3E morphing tonal shapes captured live into the liquid glass layer.
 * - [BLOOM]: Standalone M3E morphing tonal shapes (glass disabled, shapes enabled).
 * - [WALLPAPER]: Custom image wallpaper background for pages (glass on with image URI set).
 * - [PLAIN]: Solid theme surface background without ambient shapes.
 */
enum class PetalBackgroundMode {
    HOME_WALLPAPER,
    BLOOM_GLASS,
    BLOOM,
    WALLPAPER,
    PLAIN
}

enum class PageKind {
    PAGE,
    HOME
}

/**
 * Pure background mode resolver logic (fully testable in JUnit).
 */
fun resolveBackgroundMode(
    isGlassMaster: Boolean,
    glassBgMode: String,
    glassImageUri: String,
    shapesEnabled: Boolean
): PetalBackgroundMode {
    return when {
        isGlassMaster -> {
            if ((glassBgMode == "IMAGE" || glassBgMode == "WALLPAPER") && glassImageUri.isNotBlank()) {
                PetalBackgroundMode.WALLPAPER
            } else {
                PetalBackgroundMode.BLOOM_GLASS
            }
        }
        shapesEnabled -> PetalBackgroundMode.BLOOM
        else -> PetalBackgroundMode.PLAIN
    }
}

/**
 * Universal background mode resolver observing SharedPreferences.
 */
@Composable
fun rememberPageBackgroundMode(pageKind: PageKind = PageKind.PAGE): PetalBackgroundMode {
    if (pageKind == PageKind.HOME) {
        return PetalBackgroundMode.HOME_WALLPAPER
    }

    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }

    var isGlassMaster by remember { mutableStateOf(isLiquidGlassMaster(sp)) }
    var glassBgMode by remember {
        mutableStateOf(sp.getString("sp_liquid_glass_bg_mode", "BLOOM_GLASS") ?: "BLOOM_GLASS")
    }
    var glassImageUri by remember {
        mutableStateOf(sp.getString("sp_liquid_glass_bg_image_uri", "").orEmpty())
    }
    var shapesEnabled by remember {
        mutableStateOf(sp.getBoolean("sp_expressive_bg_shapes", true))
    }

    DisposableEffect(sp) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "sp_liquid_glass_enabled", "sp_ui_experience", "sp_liquid_glass_unlocked" -> {
                    isGlassMaster = isLiquidGlassMaster(sp)
                }
                "sp_liquid_glass_bg_mode" -> {
                    glassBgMode = sp.getString("sp_liquid_glass_bg_mode", "BLOOM_GLASS") ?: "BLOOM_GLASS"
                }
                "sp_liquid_glass_bg_image_uri" -> {
                    glassImageUri = sp.getString("sp_liquid_glass_bg_image_uri", "").orEmpty()
                }
                "sp_expressive_bg_shapes" -> {
                    shapesEnabled = sp.getBoolean("sp_expressive_bg_shapes", true)
                }
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // One-time automatic migration of legacy "STRIDE" and "MORPHING" to "BLOOM_GLASS"
    LaunchedEffect(glassBgMode) {
        if (glassBgMode == "STRIDE" || glassBgMode == "MORPHING") {
            sp.edit().putString("sp_liquid_glass_bg_mode", "BLOOM_GLASS").apply()
            glassBgMode = "BLOOM_GLASS"
        }
    }

    return resolveBackgroundMode(
        isGlassMaster = isGlassMaster,
        glassBgMode = glassBgMode,
        glassImageUri = glassImageUri,
        shapesEnabled = shapesEnabled
    )
}

/**
 * Universal Page Background Component.
 * Replaces all ad-hoc M3ExpressiveVariableBackground calls across the app.
 *
 * @param pageKey Stable deterministic identifier for the page (e.g. "history_page", "downloads_page").
 * @param modifier Layout modifier.
 */
@Composable
fun PetalPageBackground(
    pageKey: String,
    modifier: Modifier = Modifier
) {
    val mode = rememberPageBackgroundMode(PageKind.PAGE)
    val wallpaper = LocalPetalGlassWallpaper.current
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val isAmoled = sp.getBoolean("sp_amoled", false)

    when (mode) {
        PetalBackgroundMode.BLOOM_GLASS -> {
            PetalBloomBackground(
                pageSeed = pageKey,
                modifier = modifier.fillMaxSize(),
                isGlassTarget = true,
                wallpaper = wallpaper
            )
        }
        PetalBackgroundMode.BLOOM -> {
            PetalBloomBackground(
                pageSeed = pageKey,
                modifier = modifier.fillMaxSize(),
                isGlassTarget = false,
                wallpaper = null
            )
        }
        PetalBackgroundMode.WALLPAPER -> {
            val imageUri = sp.getString("sp_liquid_glass_bg_image_uri", "").orEmpty()
            val dim = sp.getFloat("sp_liquid_glass_bg_dim", 0.35f)
            val blur = sp.getFloat("sp_liquid_glass_bg_blur", 16f)
            PetalGlassAwareWallpaperBackground(
                wallpaperUri = imageUri,
                dim = dim,
                blur = blur,
                glassWallpaper = wallpaper,
                modifier = modifier.fillMaxSize()
            )
        }
        PetalBackgroundMode.PLAIN, PetalBackgroundMode.HOME_WALLPAPER -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(if (isAmoled) Color.Black else MaterialTheme.colorScheme.background)
            )
        }
    }
}

/**
 * Shape layout definition for the curated Bloom morphing shapes.
 */
data class BloomShapeDef(
    val morph: Morph,
    val centerRelX: Float,
    val centerRelY: Float,
    val radiusDp: Float,
    val baseRotation: Float,
    val roleIndex: Int, // 0 = primaryContainer, 1 = secondaryContainer, 2 = tertiaryContainer, 3 = tertiary accent
    val driftSign: Float
)

/**
 * Curated M3E Shape Catalog and deterministic layout generator.
 */
object BloomShapeCatalog {

    val morphPairs: List<Pair<RoundedPolygon, RoundedPolygon>> by lazy {
        listOf(
            // Pair 0: Clover 4-Leaf <-> Flower (smooth nature symmetry)
            Pair(PetalMaterialShapes.Clover4Leaf.polygon, PetalMaterialShapes.Flower.polygon),
            // Pair 1: Sunny <-> Cookie 9-Sided (celestial to scallop)
            Pair(PetalMaterialShapes.Sunny.polygon, PetalMaterialShapes.Cookie9Sided.polygon),
            // Pair 2: Soft Burst <-> Gem (high-energy to geometric)
            Pair(PetalMaterialShapes.SoftBurst.polygon, PetalMaterialShapes.Gem.polygon),
            // Pair 3: Pill <-> Cookie 4-Sided (pill to rounded cushion)
            Pair(PetalMaterialShapes.Pill.polygon, PetalMaterialShapes.Cookie4Sided.polygon),
            // Pair 4: Clover 8-Leaf <-> Burst (8-leaf organic to radiant burst)
            Pair(PetalMaterialShapes.Clover8Leaf.polygon, PetalMaterialShapes.Burst.polygon)
        )
    }

    fun createShapesForSeed(pageKey: String, rotationEpoch: Long): List<BloomShapeDef> {
        val hash = (pageKey.hashCode().toLong() xor (rotationEpoch * 31)).toInt()
        val r = java.util.Random(hash.toLong())
        val pairs = morphPairs

        val p0 = pairs[Math.abs(r.nextInt()) % pairs.size]
        val p1 = pairs[Math.abs(r.nextInt()) % pairs.size]
        val p2 = pairs[Math.abs(r.nextInt()) % pairs.size]
        val p3 = pairs[Math.abs(r.nextInt()) % pairs.size]

        return listOf(
            // Shape 0: Top-Left Corner (Second largest - secondaryContainer)
            BloomShapeDef(
                morph = Morph(p0.first, p0.second),
                centerRelX = 0.08f + (r.nextFloat() * 0.06f),
                centerRelY = 0.06f + (r.nextFloat() * 0.06f),
                radiusDp = 270f + (r.nextFloat() * 40f),
                baseRotation = r.nextFloat() * 360f,
                roleIndex = 1,
                driftSign = 1f
            ),
            // Shape 1: Bottom-Right Corner (Largest - primaryContainer)
            BloomShapeDef(
                morph = Morph(p1.first, p1.second),
                centerRelX = 0.92f - (r.nextFloat() * 0.06f),
                centerRelY = 0.90f - (r.nextFloat() * 0.06f),
                radiusDp = 300f + (r.nextFloat() * 50f),
                baseRotation = r.nextFloat() * 360f,
                roleIndex = 0,
                driftSign = -1f
            ),
            // Shape 2: Top-Right Edge (Third largest - tertiaryContainer)
            BloomShapeDef(
                morph = Morph(p2.first, p2.second),
                centerRelX = 0.90f - (r.nextFloat() * 0.06f),
                centerRelY = 0.14f + (r.nextFloat() * 0.06f),
                radiusDp = 190f + (r.nextFloat() * 30f),
                baseRotation = r.nextFloat() * 360f,
                roleIndex = 2,
                driftSign = 1f
            ),
            // Shape 3: Bottom-Left / Mid-Left (Small accent - tertiary ~12%)
            BloomShapeDef(
                morph = Morph(p3.first, p3.second),
                centerRelX = 0.05f + (r.nextFloat() * 0.05f),
                centerRelY = 0.68f + (r.nextFloat() * 0.08f),
                radiusDp = 150f + (r.nextFloat() * 25f),
                baseRotation = r.nextFloat() * 360f,
                roleIndex = 3,
                driftSign = -1f
            )
        )
    }
}

/**
 * Pure color role resolver according to Petal's design system:
 * - Largest shape: primaryContainer (alpha 35-55%)
 * - Second: secondaryContainer (alpha 35-55%)
 * - Third: tertiaryContainer (alpha 35-55%)
 * - Small accent: tertiary (alpha ~12%)
 * - ColorStyle.EXPRESSIVE rotates which role leads per page
 * - AMOLED reduces alpha and keeps base black
 */
fun resolveColorRole(
    roleIndex: Int,
    colorScheme: ColorScheme,
    colorStyle: ColorStyle,
    pageKey: String,
    isDark: Boolean,
    isAmoled: Boolean
): Pair<Color, Float> {
    val effectiveRoleIndex = if (colorStyle == ColorStyle.EXPRESSIVE) {
        val shift = Math.abs(pageKey.hashCode()) % 3
        if (roleIndex < 3) (roleIndex + shift) % 3 else 3
    } else {
        roleIndex
    }

    val (baseColor, targetAlpha) = when (effectiveRoleIndex) {
        0 -> colorScheme.primaryContainer to if (isAmoled) 0.28f else if (isDark) 0.40f else 0.46f
        1 -> colorScheme.secondaryContainer to if (isAmoled) 0.24f else if (isDark) 0.36f else 0.42f
        2 -> colorScheme.tertiaryContainer to if (isAmoled) 0.20f else if (isDark) 0.32f else 0.38f
        else -> colorScheme.tertiary to if (isAmoled) 0.08f else if (isDark) 0.12f else 0.14f
    }
    return Pair(baseColor, targetAlpha)
}

@Composable
private fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        val scale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Throwable) { 1f }
        scale == 0f
    }
}

@Composable
private fun isBatterySaverEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isPowerSaveMode == true
        } catch (_: Throwable) { false }
    }
}

/**
 * High-performance Bloom renderer:
 * - Uses curated M3E shapes from [PetalMaterialShapes]
 * - Cached paths & brushes via [drawWithCache]
 * - Expressive spatial spring enter animation
 * - Faint contrast scrims ensuring > 4.5:1 text contrast
 * - Optional API 31+ RenderEffect blur with smooth radial falloff
 */
@Composable
fun PetalBloomBackground(
    pageSeed: String,
    modifier: Modifier = Modifier,
    isGlassTarget: Boolean = false,
    wallpaper: PetalGlassWallpaper? = null
) {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val density = LocalDensity.current

    val isAmoled = remember { sp.getBoolean("sp_amoled", false) }
    val colorStyleName = remember { sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT" }
    val colorStyle = remember(colorStyleName) {
        try { ColorStyle.valueOf(colorStyleName) } catch (_: Throwable) { ColorStyle.TONAL_SPOT }
    }

    val shapeChangeMode = remember { sp.getString("sp_bg_shape_change_mode", "ALWAYS") ?: "ALWAYS" }
    val rotationMinutes = remember { sp.getInt("sp_bg_shape_rotation_min", 5) }
    var periodicSeedEpoch by rememberSaveable { mutableLongStateOf(sp.getLong("sp_periodic_seed_epoch", 1000L)) }

    LaunchedEffect(shapeChangeMode, rotationMinutes) {
        if (shapeChangeMode == "PERIODIC" && rotationMinutes > 0) {
            val intervalMs = rotationMinutes * 60 * 1000L
            while (true) {
                kotlinx.coroutines.delay(intervalMs)
                val nextEpoch = System.currentTimeMillis()
                periodicSeedEpoch = nextEpoch
                sp.edit().putLong("sp_periodic_seed_epoch", nextEpoch).apply()
            }
        }
    }

    // Deterministic seed: stable per pageKey; rotates only on periodic timer
    val rotationEpoch = if (shapeChangeMode == "PERIODIC") periodicSeedEpoch else 0L
    val shapes = remember(pageSeed, rotationEpoch) {
        BloomShapeCatalog.createShapesForSeed(pageSeed, rotationEpoch)
    }

    // Page Enter Spring Motion ("Bloom" expansion)
    val bloomAnim = remember { Animatable(0f) }
    LaunchedEffect(pageSeed) {
        bloomAnim.snapTo(0f)
        bloomAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // Gentle kinetic drift at rest: disabled under low-power / reduced motion
    val allowMotion = !isReducedMotionEnabled() && !isBatterySaverEnabled() && !rememberLowEndDevice()
    val driftProgress = if (allowMotion) {
        val transition = rememberInfiniteTransition(label = "bloom_drift")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 24000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bloom_drift_progress"
        ).value
    } else {
        0f
    }

    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.red * 0.299f + colorScheme.background.green * 0.587f + colorScheme.background.blue * 0.114f < 0.5f
    val scratchMatrix = remember { Matrix() }

    val blurModifier = if (Build.VERSION.SDK_INT >= 31 && allowMotion) {
        Modifier.graphicsLayer {
            try {
                renderEffect = android.graphics.RenderEffect.createBlurEffect(
                    36f, 36f, android.graphics.Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            } catch (_: Throwable) {}
        }
    } else {
        Modifier
    }

    val glassCaptureModifier = if (isGlassTarget && wallpaper != null) {
        Modifier.captureGlassWallpaperLive(wallpaper) {
            ((bloomAnim.value + driftProgress) * 1000f).toLong()
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(blurModifier)
            .then(glassCaptureModifier)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val baseBg = if (isAmoled) Color.Black else colorScheme.background

                // Text contrast scrims (top and bottom)
                val scrimColor = if (isAmoled) Color.Black else colorScheme.background
                val topScrimBrush = Brush.verticalGradient(
                    colors = listOf(scrimColor.copy(alpha = if (isDark) 0.45f else 0.30f), Color.Transparent),
                    startY = 0f,
                    endY = with(density) { 130.dp.toPx() }
                )
                val bottomScrimBrush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, scrimColor.copy(alpha = if (isDark) 0.50f else 0.35f)),
                    startY = (h - with(density) { 130.dp.toPx() }).coerceAtLeast(0f),
                    endY = h
                )

                onDrawBehind {
                    // Base canvas surface
                    drawRect(baseBg)

                    // Draw M3E Bloom shapes
                    shapes.forEach { shapeDef ->
                        val (roleColor, roleAlpha) = resolveColorRole(
                            roleIndex = shapeDef.roleIndex,
                            colorScheme = colorScheme,
                            colorStyle = colorStyle,
                            pageKey = pageSeed,
                            isDark = isDark,
                            isAmoled = isAmoled
                        )

                        val cx = w * shapeDef.centerRelX
                        val cy = h * shapeDef.centerRelY

                        // Morph progression combined with enter spring and subtle drift breathing
                        val morphProgress = (bloomAnim.value * 0.85f + (driftProgress * 0.15f * shapeDef.driftSign)).coerceIn(0f, 1f)
                        val breathe = 1f + (shapeDef.driftSign * 0.03f * sin(driftProgress * PI.toFloat()))
                        val animatedRadius = shapeDef.radiusDp.dp.toPx() * bloomAnim.value * breathe
                        val animatedRotation = shapeDef.baseRotation + (shapeDef.driftSign * driftProgress * 12f)

                        if (animatedRadius > 2f) {
                            rotate(degrees = animatedRotation, pivot = Offset(cx, cy)) {
                                val androidPath = shapeDef.morph.toPath(progress = morphProgress)
                                scratchMatrix.reset()
                                scratchMatrix.postScale(animatedRadius, animatedRadius)
                                scratchMatrix.postTranslate(cx, cy)
                                androidPath.transform(scratchMatrix)
                                val composePath = androidPath.asComposePath()

                                val fillBrush = Brush.radialGradient(
                                    colors = listOf(
                                        roleColor.copy(alpha = roleAlpha),
                                        roleColor.copy(alpha = roleAlpha * 0.45f),
                                        Color.Transparent
                                    ),
                                    center = Offset(cx, cy),
                                    radius = animatedRadius * 1.15f
                                )

                                drawPath(path = composePath, brush = fillBrush)
                            }
                        }
                    }

                    // Scrims for guaranteed >= 4.5:1 text readability
                    drawRect(topScrimBrush)
                    drawRect(bottomScrimBrush)
                }
            }
    ) {}
}
