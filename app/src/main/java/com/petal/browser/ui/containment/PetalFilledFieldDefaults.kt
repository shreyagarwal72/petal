package com.petal.browser.ui.containment

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Petal's text field look: a tonal filled container with no outline and no underline
 * (design.md: no Outlined* components, no decorative borders). Colours come from the live scheme.
 */
@Composable
fun petalFilledTextFieldColors(
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest
): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = container,
    unfocusedContainerColor = container,
    disabledContainerColor = container.copy(alpha = 0.6f),
    errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent
)
