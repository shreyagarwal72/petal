package com.petal.browser.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * Material 3 Expressive Contained Loading Indicator.
 * Features a circular background container (default 64.dp) housing an inner shape
 * that continuously morphs between rounded geometric shapes (pentagon and triangle)
 * using androidx.graphics.shapes.RoundedPolygon and Morph.
 */
@Composable
fun ContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerSize: Dp = 64.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    indicatorColor: Color = MaterialTheme.colorScheme.onPrimary,
    animationDurationMillis: Int = 1800
) {
    // Construct rounded pentagon and triangle polygons using androidx.graphics.shapes
    val pentagon = remember {
        RoundedPolygon(
            numVertices = 5,
            radius = 1f,
            rounding = androidx.graphics.shapes.CornerRounding(0.2f)
        )
    }

    val triangle = remember {
        RoundedPolygon(
            numVertices = 3,
            radius = 1f,
            rounding = androidx.graphics.shapes.CornerRounding(0.25f)
        )
    }

    // Create morph transition between pentagon and triangle
    val morph = remember { Morph(pentagon, triangle) }

    // Infinite transition loop (~1800ms) with reverse mode for seamless morphing
    val infiniteTransition = rememberInfiniteTransition(label = "ContainedLoadingMorphTransition")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = animationDurationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morphProgress"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = animationDurationMillis * 2, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    Box(
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val sizeRadius = minOf(size.width, size.height) * 0.28f

            // Convert Morph to Android Path and then Compose Path
            val androidPath = morph.toPath(progress = progress)
            val composePath = androidPath.asComposePath()

            // Scale and center the morph path onto the canvas
            val transformMatrix = Matrix().apply {
                translate(center.x, center.y)
                scale(sizeRadius, sizeRadius)
                rotateZ(rotation)
            }

            val finalPath = Path().apply {
                addPath(composePath)
                transform(transformMatrix)
            }

            drawPath(
                path = finalPath,
                color = indicatorColor
            )
        }
    }
}

@Preview(name = "Contained Loading Indicator - Dark Theme", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun ContainedLoadingIndicatorPreview() {
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Color(0xFF121212)),
            contentAlignment = Alignment.Center
        ) {
            ContainedLoadingIndicator()
        }
    }
}
