package com.petal.browser.compose.settings.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.petal.browser.ui.components.PetalAboutDeveloperScreen

/**
 * Settings integration for the unified About & Developer experience.
 * Delegates to [PetalAboutDeveloperScreen] with [wrapPredictive] = false
 * since the host Settings container manages the root predictive-back surface.
 */
@Composable
fun AboutSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    onNavigateToUpdater: (() -> Unit)? = null
) {
    PetalAboutDeveloperScreen(
        onBack = onNavigateBack,
        modifier = modifier,
        wrapPredictive = false,
        targetHighlightItemId = targetHighlightItemId,
        onNavigateToUpdater = onNavigateToUpdater
    )
}
