package com.petal.browser.compose.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Composable to display an indeterminate loading indicator that fills all available screen
 * @param modifier The modifier to be applied to the composable
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContainedLoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        val description = "Loading..."
        ContainedLoadingIndicator(
            modifier = Modifier
                .requiredSize(48.dp)
                .semantics { stateDescription = description }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ContainedLoadingIndicatorPreview() {
    ContainedLoadingIndicator()
}
