package com.petal.browser.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Wraps a horizontally-scrolling row of chips/buttons with soft fade edges that only
 * appear while there's more content to scroll to in that direction.
 *
 * Without this, content past the visible width is hard-clipped by the screen edge and
 * reads as a layout bug (e.g. a chip label cut off mid-word) rather than a scrollable
 * list. [edgeColor] should match the background the row sits on (card surface, page
 * background, etc.) so the fade blends in seamlessly.
 */
@Composable
fun ScrollFadeRow(
    scrollState: ScrollState,
    edgeColor: Color,
    modifier: Modifier = Modifier,
    edgeWidth: Dp = 28.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        content()

        if (scrollState.canScrollBackward) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(edgeWidth)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(edgeColor, edgeColor.copy(alpha = 0f))
                        )
                    )
            )
        }

        if (scrollState.canScrollForward) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(edgeWidth)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(edgeColor.copy(alpha = 0f), edgeColor)
                        )
                    )
            )
        }
    }
}
