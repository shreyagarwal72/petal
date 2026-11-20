package com.petal.browser.ui.components.pullrefresh

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * Reusable scratch objects so drawing a Morph every frame allocates nothing.
 * Polygons are normalized to the 0..1 box (same convention as PetalMorphShape), then scaled.
 */
internal class MorphPathScratch {
    private val androidPath = android.graphics.Path()
    private val matrix = android.graphics.Matrix()
    private val composePath: Path = androidPath.asComposePath()

    /** Path of [morph] at [progress] filling a [sizePx] square whose top-left is (0, 0). */
    fun build(morph: Morph, progress: Float, sizePx: Float): Path {
        androidPath.rewind()
        morph.toPath(progress, androidPath)
        matrix.reset()
        matrix.setScale(sizePx, sizePx)
        androidPath.transform(matrix)
        return composePath
    }
}

/** Normalizes polygons to the unit box so Morph(a, b) and the scale-to-size step line up. */
internal fun RoundedPolygon.unit(): RoundedPolygon = normalized()
