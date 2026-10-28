package com.petal.browser.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.glass.LocalPetalGlassRevision
import com.petal.browser.ui.glass.LocalPetalGlassWallpaper

/**
 * Visual styling variants for [PetalLiquidButton].
 */
enum class PetalButtonVariant {
    Filled,
    Tonal,
    Outlined,
    Text,
    Destructive
}

/**
 * Universal interactive liquid-glass button for Petal.
 *
 * Implements Kyant's AndroidLiquidGlass button model:
 * - Real-time backdrop refraction and progressive blur.
 * - Single specular rim light (eliminates double-outline clashing).
 * - Interactive AGSL touch highlight bloom tracking the user's finger.
 * - Velocity squish and scale spring feedback on press/drag.
 * - Transparent / petalGroupSurfaceColor backgrounds so the glass is never masked.
 * - Graceful fallback to Material 3 Expressive buttons when liquid glass is off.
 */
@Composable
fun PetalLiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PetalButtonVariant = PetalButtonVariant.Tonal,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    pressFx: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    LocalPetalGlassRevision.current
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val masterOn = sp.getBoolean("sp_liquid_glass_enabled", false)
    val containmentsOn = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val wallpaper = LocalPetalGlassWallpaper.current
    val isGlass = (masterOn || containmentsOn) && (wallpaper?.ready == true || containmentsOn)

    val hapticEngine = remember(context) {
        try { PetalHapticEngine.getInstance(context) } catch (_: Exception) { null }
    }

    val handleClick = {
        if (enabled) {
            try { hapticEngine?.play(PetalHapticEngine.Pattern.TICK, 0.35f) } catch (_: Exception) {}
            onClick()
        }
    }

    val cs = MaterialTheme.colorScheme

    val (containerColor, contentColor, tintColor, sheen) = when (variant) {
        PetalButtonVariant.Filled -> Quadruple(
            if (isGlass) cs.primaryContainer.copy(alpha = 0.45f) else cs.primary,
            if (isGlass) cs.onPrimaryContainer else cs.onPrimary,
            cs.primaryContainer,
            0.75f
        )
        PetalButtonVariant.Tonal -> Quadruple(
            if (isGlass) petalGroupSurfaceColor() else cs.surfaceContainerHigh,
            cs.onSurface,
            Color.Unspecified,
            0.55f
        )
        PetalButtonVariant.Outlined -> Quadruple(
            if (isGlass) Color.Transparent else Color.Transparent,
            cs.primary,
            Color.Unspecified,
            0.45f
        )
        PetalButtonVariant.Text -> Quadruple(
            Color.Transparent,
            cs.primary,
            Color.Unspecified,
            0.25f
        )
        PetalButtonVariant.Destructive -> Quadruple(
            if (isGlass) cs.errorContainer.copy(alpha = 0.40f) else cs.error,
            if (isGlass) cs.onErrorContainer else cs.onError,
            cs.errorContainer,
            0.75f
        )
    }

    if (isGlass && variant != PetalButtonVariant.Text) {
        val glassModifier = modifier
            .liquidGlassChrome(
                shape = shape,
                enabled = true,
                sheenIntensity = sheen,
                tintColor = tintColor,
                pressFx = pressFx
            )
            .clip(shape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.3f))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = handleClick
            )
            .padding(contentPadding)
            .defaultMinSize(minHeight = 40.dp)

        Row(
            modifier = glassModifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(
                LocalContentColor provides (if (enabled) contentColor else contentColor.copy(alpha = 0.38f)),
                LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            ) {
                content()
            }
        }
    } else {
        // Material 3 fallback path
        when (variant) {
            PetalButtonVariant.Filled -> Button(
                onClick = handleClick,
                modifier = modifier.defaultMinSize(minHeight = 40.dp),
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
                contentPadding = contentPadding,
                content = content
            )
            PetalButtonVariant.Tonal -> FilledTonalButton(
                onClick = handleClick,
                modifier = modifier.defaultMinSize(minHeight = 40.dp),
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = containerColor, contentColor = contentColor),
                contentPadding = contentPadding,
                content = content
            )
            PetalButtonVariant.Outlined -> OutlinedButton(
                onClick = handleClick,
                modifier = modifier.defaultMinSize(minHeight = 40.dp),
                enabled = enabled,
                shape = shape,
                border = BorderStroke(1.dp, cs.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = contentColor),
                contentPadding = contentPadding,
                content = content
            )
            PetalButtonVariant.Text -> androidx.compose.material3.TextButton(
                onClick = handleClick,
                modifier = modifier.defaultMinSize(minHeight = 40.dp),
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                contentPadding = contentPadding,
                content = content
            )
            PetalButtonVariant.Destructive -> Button(
                onClick = handleClick,
                modifier = modifier.defaultMinSize(minHeight = 40.dp),
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
                contentPadding = contentPadding,
                content = content
            )
        }
    }
}

/**
 * Compact circular liquid-glass icon button for toolbars, floating navigation, and quick controls.
 */
@Composable
fun PetalLiquidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    shape: Shape = CircleShape,
    tint: Color = Color.Unspecified,
    pressFx: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    LocalPetalGlassRevision.current
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val masterOn = sp.getBoolean("sp_liquid_glass_enabled", false)
    val containmentsOn = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val wallpaper = LocalPetalGlassWallpaper.current
    val isGlass = (masterOn || containmentsOn) && (wallpaper?.ready == true || containmentsOn)

    val hapticEngine = remember(context) {
        try { PetalHapticEngine.getInstance(context) } catch (_: Exception) { null }
    }

    val handleClick = {
        if (enabled) {
            try { hapticEngine?.play(PetalHapticEngine.Pattern.TICK, 0.35f) } catch (_: Exception) {}
            onClick()
        }
    }

    val cs = MaterialTheme.colorScheme
    val effectiveTint = if (tint != Color.Unspecified) tint else cs.onSurface

    if (isGlass) {
        Box(
            modifier = modifier
                .size(size)
                .liquidGlassChrome(
                    shape = shape,
                    enabled = true,
                    sheenIntensity = 0.50f,
                    pressFx = pressFx
                )
                .clip(shape)
                .background(petalGroupSurfaceColor())
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = handleClick
                )
                .semantics {
                    role = Role.Button
                    if (contentDescription != null) {
                        this.contentDescription = contentDescription
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(
                LocalContentColor provides (if (enabled) effectiveTint else effectiveTint.copy(alpha = 0.38f))
            ) {
                content()
            }
        }
    } else {
        IconButton(
            onClick = handleClick,
            modifier = modifier.size(size),
            enabled = enabled
        ) {
            CompositionLocalProvider(
                LocalContentColor provides (if (enabled) effectiveTint else effectiveTint.copy(alpha = 0.38f))
            ) {
                content()
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
