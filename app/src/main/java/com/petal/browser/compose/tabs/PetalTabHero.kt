package com.petal.browser.compose.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
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
        /** Spring tuned to feel like Chrome/Brave: quick start, soft settle, no visible bounce. */
        fun spec() = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
    }
}

/** Full-screen overlay that draws the morphing page snapshot between full screen and [tileBounds]. */
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
    ) {
        val img = hero.image
        if (hero.visible && img != null) {
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .layout { measurable, c ->
                        val p = hero.progress.value
                        val full = Rect(Offset.Zero, Size(c.maxWidth.toFloat(), c.maxHeight.toFloat()))
                        val tile = hero.tabId?.let(tileBounds)?.translate(-origin)
                        // No tile on screen (scrolled away / filtered): shrink towards the centre instead.
                        val target = tile ?: Rect.fromCenter(full.center, full.width * 0.5f, full.height * 0.5f)
                        val r = lerp(full, target, p)
                        val placeable = measurable.measure(
                            Constraints.fixed(
                                r.width.roundToInt().coerceAtLeast(1),
                                r.height.roundToInt().coerceAtLeast(1)
                            )
                        )
                        layout(c.maxWidth, c.maxHeight) {
                            placeable.place(r.left.roundToInt(), r.top.roundToInt())
                        }
                    }
                    .graphicsLayer {
                        val p = hero.progress.value
                        alpha = hero.heroAlpha()
                        shape = RoundedCornerShape(CornerSize(deviceRadius + (tileRadius - deviceRadius) * p))
                        clip = true
                    }
            )
        }
    }
}
