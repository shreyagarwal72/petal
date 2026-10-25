package com.petal.browser.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome

/**
 * Hosts ONE Material control (button, chip, toggle...) and, when [glass] is on, gives it a single
 * liquid-glass surface that is exactly the control's own container.
 *
 * Why this exists: Material controls pad themselves out to a 48dp touch target *outside* their
 * container. Putting `liquidGlassChrome` on the control's outer modifier painted the glass on that
 * padded box, so the control's own (smaller) container showed up as a second button inside the
 * glass one. Here the touch target is applied OUTSIDE the glass, the control's internal minimum
 * is switched off, and the glass modifier lands on the real container bounds.
 *
 * Give the control a transparent (or lightly tinted) container colour while glass is on, and pass
 * the supplied modifier as its `modifier`.
 */
@Composable
fun PetalGlassFit(
    glass: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    sheenIntensity: Float = 0.3f,
    content: @Composable (Modifier) -> Unit,
) {
    if (!glass) {
        Box(modifier, propagateMinConstraints = true) { content(Modifier) }
        return
    }
    Box(modifier.minimumInteractiveComponentSize(), propagateMinConstraints = true) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            content(Modifier.liquidGlassChrome(shape = shape, enabled = true, sheenIntensity = sheenIntensity))
        }
    }
}

/**
 * Material 3 Expressive selectable chip.
 * - Selected: tonal secondary container, morphs from a rounded rectangle into a pill (springy).
 * - Unselected: tonal surface, no outline.
 * - Liquid glass: one frosted surface (no nested container, no glow); selection is a soft tint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCheck: Boolean = true,
    labelStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge,
) {
    val glass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val cs = MaterialTheme.colorScheme
    val radius by animateDpAsState(
        targetValue = if (selected) 20.dp else 12.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "petalChipRadius"
    )
    val shape = RoundedCornerShape(radius)
    PetalGlassFit(glass = glass, shape = shape, modifier = modifier, sheenIntensity = 0.3f) { glassModifier ->
        FilterChip(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            modifier = glassModifier,
            shape = shape,
            border = null,
            label = { Text(label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = if (selected && showCheck) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            } else null,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = if (glass) Color.Transparent else cs.surfaceContainerHigh,
                selectedContainerColor = if (glass) cs.secondaryContainer.copy(alpha = 0.55f) else cs.secondaryContainer,
            )
        )
    }
}
