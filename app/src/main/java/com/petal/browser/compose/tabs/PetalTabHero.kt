package com.petal.browser.compose.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Drives the Chrome-style "page <-> tile" hero transition of the tab grid.
 *
 * [progress] 0f = hero fills the screen (the page), 1f = hero sits exactly on its tile.
 */
@Stable
class TabHeroState(startVisible: Boolean) {
    val progress = Animatable(if (startVisible) 0f else 1f)
    var visible by mutableStateOf(startVisible)
    var busy by mutableStateOf(false)
    var tabId by mutableStateOf<String?>(null)
    var image by mutableStateOf<ImageBitmap?>(null)

    /** Hero opacity: invisible while docked on the tile (the real tile shows), opaque once it has grown. */
    fun heroAlpha(): Float = ((1f - progress.value) / 0.3f).coerceIn(0f, 1f)

    /** Reset to the idle, docked state. */
    suspend fun dock() {
        visible = false
        busy = false
        progress.snapTo(1f)
    }

    companion object {
        /**
         * Fixed-length emphasized curve (~Chrome's 300-400ms page<->tile morph). The old spring
         * (damping 0.86, stiffness 400) needed ~0.7s to come to rest, and the tab was only shown
         * once it had, so opening a tab felt like it hung before the page appeared.
         */
        fun spec(): AnimationSpec<Float> =
            tween(durationMillis = 360, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    }
}

/**
 * Full-screen overlay that draws the morphing page snapshot between full screen and [tileBounds].
 *
 * Everything runs in the DRAW phase. The previous version re-measured and re-laid-out the bitmap
 * with fixed constraints on every animation frame (and re-read the tile bounds during layout),
 * which is what made the morph stutter on busy grids. Here the rect, corner radius and crop are
 * computed per frame inside drawBehind and the bitmap is just blitted, so a frame costs one clip
 * and one image draw and nothing is invalidated except the draw.
 */
@Composable
fun TabHeroOverlay(
    hero: TabHeroState,
    tileBounds: (String) -> Rect?,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val deviceRadius = with(density) { 28.dp.toPx() }
    val tileRadius = with(density) { 20.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .drawBehind {
                val img = hero.image
                if (!hero.visible || img == null) return@drawBehind
                val heroAlpha = hero.heroAlpha()
                if (heroAlpha <= 0f) return@drawBehind

                val p = hero.progress.value
                val full = Rect(Offset.Zero, size)
                val tile = hero.tabId?.let(tileBounds)?.translate(-origin)
                // No tile on screen (scrolled away / filtered): shrink towards the centre instead.
                val target = tile ?: run {
                    val w = full.width * 0.5f
                    val h = full.height * 0.5f
                    Rect(full.center.x - w * 0.5f, full.center.y - h * 0.5f, full.center.x + w * 0.5f, full.center.y + h * 0.5f)
                }
                val r = lerp(full, target, p)
                if (r.width < 1f || r.height < 1f) return@drawBehind

                // Crop-to-fill, top aligned: take the largest source window with the target's aspect ratio.
                val srcW = img.width.toFloat()
                val srcH = img.height.toFloat()
                val dstAspect = r.width / r.height
                val cropW: Float
                val cropH: Float
                if (srcW / srcH > dstAspect) {
                    cropH = srcH
                    cropW = srcH * dstAspect
                } else {
                    cropW = srcW
                    cropH = srcW / dstAspect
                }
                val cropX = ((srcW - cropW) / 2f).roundToInt()

                val radius = deviceRadius + (tileRadius - deviceRadius) * p
                val clip = Path().apply { addRoundRect(RoundRect(r, CornerRadius(radius))) }
                clipPath(clip) {
                    drawImage(
                        image = img,
                        srcOffset = IntOffset(cropX, 0),
                        srcSize = IntSize(cropW.roundToInt().coerceAtLeast(1), cropH.roundToInt().coerceAtLeast(1)),
                        dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
                        dstSize = IntSize(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)),
                        alpha = heroAlpha,
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
    )
}
