// SPDX-License-Identifier: GPL-3.0-only
package com.petal.browser.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * A collapsing Medium Top App Bar leveraging PixelPlayer's ExpressiveTopBarContent
 * logic for expressive variable font rendering and smooth collapsedFraction title interpolation,
 * while morphing the back button between tonal and filled states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalCollapsingTopAppBar(
    title: String,
    onNavigateBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val collapseFraction = scrollBehavior.state.collapsedFraction
    MediumTopAppBar(
        title = {
            ExpressiveTopBarTitle(
                title = title,
                collapseFraction = collapseFraction
            )
        },
        navigationIcon = {
            Crossfade(
                targetState = collapseFraction > 0.5f,
                animationSpec = tween(500),
                label = "petalHeaderBackButtonMorph",
            ) { scrolled ->
                if (scrolled) {
                    FilledIconButton(onClick = onNavigateBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    FilledTonalIconButton(onClick = onNavigateBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun ExpressiveTopBarTitle(
    title: String,
    collapseFraction: Float
) {
    val clampedFraction = collapseFraction.coerceIn(0f, 1f)
    val startPadding = androidx.compose.ui.unit.lerp(0.dp, 6.dp, clampedFraction)
    androidx.compose.material3.Text(
        text = title,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = androidx.compose.ui.Modifier.padding(start = startPadding),
        style = androidx.compose.material3.MaterialTheme.typography.titleLarge
    )
}

