/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Legacy compatibility entry point for M3 Expressive Backgrounds.
 * Forwards directly to the unified [PetalPageBackground] resolver and Bloom renderer.
 */
@Deprecated("Use PetalPageBackground(pageKey, modifier) instead")
@Composable
fun M3ExpressiveVariableBackground(
    modifier: Modifier = Modifier,
    pageSeed: String = "expressive_page",
    showInSettings: Boolean = false
) {
    PetalPageBackground(pageKey = pageSeed, modifier = modifier)
}
