// SPDX-License-Identifier: GPL-3.0-only
package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A collapsing Medium Top App Bar with Zenith-style animated title transitions.
 *
 * When [title] changes (e.g., switching between the Settings overview and a sub-page),
 * the outgoing title fades + scales out while the incoming title fades + scales in
 * (matching Zenith's `AnimatedContent` `HeaderTitleAnimation` pattern).
 *
 * Additional scroll-collapse effects:
 * - The title's start padding nudges in as the bar collapses.
 * - The back button morphs from a tonal to a fully-filled icon button.
 *
 * Use together with `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()` connected
 * to the scrolling content via `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalCollapsingTopAppBar(
    title: String,
    onNavigateBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    MediumTopAppBar(
        title = {
            val titleStartPadding = animateDpAsState(
                targetValue = if (scrollBehavior.state.collapsedFraction > 0.5f) 6.dp else 0.dp,
                animationSpec = tween(250),
                label = "petalHeaderTitleStartPadding",
            )
            // Zenith-style: fade + scale transition when the title text changes
            // (e.g., switching from "Settings" overview → a sub-page title).
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                            scaleIn(
                                initialScale = 0.92f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ))
                        .togetherWith(
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                    scaleOut(
                                        targetScale = 0.92f,
                                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                                    )
                        )
                },
                label = "petalHeaderTitleAnimation",
                modifier = Modifier.padding(start = titleStartPadding.value),
            ) { currentTitle ->
                Text(
                    text = currentTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            Crossfade(
                targetState = scrollBehavior.state.collapsedFraction > 0.5f,
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
