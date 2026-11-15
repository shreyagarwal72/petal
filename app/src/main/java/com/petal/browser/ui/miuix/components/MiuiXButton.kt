package com.petal.browser.ui.miuix.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * Adapters over the real Miuix Button / IconButton. The library owns corner radius, colors,
 * press feedback and animation; legacy [shape], [borderStroke] and [interactionSource]
 * parameters are accepted for source compatibility only.
 */

/** Primary (accent) button. */
@Composable
fun MiuiXButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    contentPadding: PaddingValues = ButtonDefaults.InsideMargin,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColorsPrimary(),
        insideMargin = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** Secondary (tonal) button. */
@Composable
fun MiuiXTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    contentPadding: PaddingValues = ButtonDefaults.InsideMargin,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(),
        insideMargin = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** Miuix has no outlined button; the secondary style is the closest real component. */
@Composable
fun MiuiXOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") borderStroke: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.InsideMargin,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(),
        insideMargin = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun MiuiXIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") size: Dp = 40.dp,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    tint: Color = MiuixTheme.colorScheme.onSurface,
    @Suppress("UNUSED_PARAMETER") interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        CompositionLocalProvider(LocalContentColor provides tint) { content() }
    }
}
