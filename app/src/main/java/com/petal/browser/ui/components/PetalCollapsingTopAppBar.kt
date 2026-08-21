// SPDX-License-Identifier: GPL-3.0-only
package com.petal.browser.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
 * A collapsing Medium Top App Bar ported from RV System Monitor's
 * `ExitUntilCollapsedMediumTopAppBar`: the title's start padding nudges in and the back button
 * morphs from a tonal to a fully-filled icon button as the bar collapses on scroll.
 *
 * Use together with `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()` connected to the
 * scrolling content via `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`.
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
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = titleStartPadding.value),
            )
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



