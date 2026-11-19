package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import com.petal.browser.motion.MotionGate
import androidx.compose.animation.animateColorAsState
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor

enum class SplitButtonVariant {
    FILLED, TONAL, ELEVATED, OUTLINED
}

/**
 * PixelPlayer-inspired Animated Material 3 Expressive Dynamic-Sizing Split Button.
 * Features:
 * - Dynamic size scaling with responsive layout width & height
 * - Morphing inner & outer corners between compact connected mode and separated state
 * - Smooth spring-animated drop-down arrow rotation
 * - Micro-interaction press scale feedback
 * - Seamless icon visibility transitions
 */
@Composable
fun ExpressiveSplitButton(
    label: String,
    onPrimaryClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isMenuExpanded: Boolean = false,
    variant: SplitButtonVariant = SplitButtonVariant.FILLED,
    height: Dp = 48.dp,
    enabled: Boolean = true,
    containerColorOverride: Color? = null,
    contentColorOverride: Color? = null,
    /** Design-system role for the fill (FILLED = solid role color, TONAL = role container). */
    role: PetalGroupRole? = null,
    /** When set, the menu button switches to this role while the menu is open. */
    menuExpandedRole: PetalGroupRole? = null,
) {
    val pillRadius = height / 2
    val innerRadius = 6.dp
    val pillGap = 6.dp

    // Morphing corner radius animation
    val animatedMenuInnerCorner by animateDpAsState(
        targetValue = if (isMenuExpanded) pillRadius else innerRadius,
        animationSpec = PetalSpring.spatial(),
        label = "SplitButtonMenuCorner"
    )

    val animatedPrimaryInnerCorner by animateDpAsState(
        targetValue = if (isMenuExpanded) pillRadius else innerRadius,
        animationSpec = PetalSpring.spatial(),
        label = "SplitButtonPrimaryCorner"
    )

    // Smooth drop-down arrow rotation
    val arrowRotation by animateFloatAsState(
        targetValue = if (isMenuExpanded) 180f else 0f,
        animationSpec = PetalSpring.spatial(),
        label = "SplitButtonArrowRotation"
    )

    // Color resolution — explicit overrides win (used where the button must match
    // sibling controls, e.g. HeaderActionIcon's neutral surfaceContainerHighest in headers)
    val roleColors = role?.let { petalRoleColors(it) }
    val containerColor = containerColorOverride ?: roleColors?.let { if (variant == SplitButtonVariant.FILLED) it.solid else it.container } ?: when (variant) {
        SplitButtonVariant.FILLED -> MaterialTheme.colorScheme.primary
        SplitButtonVariant.TONAL -> MaterialTheme.colorScheme.secondaryContainer
        SplitButtonVariant.ELEVATED -> MaterialTheme.colorScheme.surfaceContainerHigh
        SplitButtonVariant.OUTLINED -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val contentColor = contentColorOverride ?: roleColors?.let { if (variant == SplitButtonVariant.FILLED) it.onSolid else it.onContainer } ?: when (variant) {
        SplitButtonVariant.FILLED -> MaterialTheme.colorScheme.onPrimary
        SplitButtonVariant.TONAL -> MaterialTheme.colorScheme.onSecondaryContainer
        SplitButtonVariant.ELEVATED -> MaterialTheme.colorScheme.primary
        SplitButtonVariant.OUTLINED -> MaterialTheme.colorScheme.onSurface
    }

    val primaryShape = RoundedCornerShape(
        topStart = pillRadius,
        bottomStart = pillRadius,
        topEnd = animatedPrimaryInnerCorner,
        bottomEnd = animatedPrimaryInnerCorner
    )

    val menuShape = RoundedCornerShape(
        topStart = animatedMenuInnerCorner,
        bottomStart = animatedMenuInnerCorner,
        topEnd = pillRadius,
        bottomEnd = pillRadius
    )

    val primaryInteractionSource = remember { MutableInteractionSource() }
    val menuInteractionSource = remember { MutableInteractionSource() }
    val isPrimaryPressed by primaryInteractionSource.collectIsPressedAsState()
    val isMenuPressed by menuInteractionSource.collectIsPressedAsState()

    val primaryScale by animateFloatAsState(
        targetValue = if (isPrimaryPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "SplitButtonPrimaryScale"
    )

    val menuScale by animateFloatAsState(
        targetValue = if (isMenuPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "SplitButtonMenuScale"
    )

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val simplifyFx = MotionGate.shouldSimplifyEffects()
    // Glass keeps filled / tonal buttons tinted with their role color so the label stays readable;
    // neutral variants (and explicit overrides) use the plain glass surface.
    val tintedInGlass = containerColorOverride == null &&
        (role != null || variant == SplitButtonVariant.FILLED || variant == SplitButtonVariant.TONAL)
    val effectiveContainer = when {
        !isGlass -> containerColor
        tintedInGlass -> containerColor.copy(alpha = 0.85f)
        else -> petalGroupSurfaceColor()
    }
    val expandedColors = menuExpandedRole?.let { petalRoleColors(it) }
    val menuExpandedActive = isMenuExpanded && expandedColors != null
    val menuContainer by animateColorAsState(
        targetValue = if (menuExpandedActive) expandedColors!!.solid else effectiveContainer,
        animationSpec = PetalSpring.crossfade(),
        label = "SplitButtonMenuContainer"
    )
    val menuContent by animateColorAsState(
        targetValue = if (menuExpandedActive) expandedColors!!.onSolid else contentColor,
        animationSpec = PetalSpring.crossfade(),
        label = "SplitButtonMenuContent"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(pillGap),
        modifier = modifier
            .height(height)
            .wrapContentWidth()
    ) {
        // --- PRIMARY ACTION SURFACE ---
        Surface(
            shape = primaryShape,
            color = effectiveContainer,
            tonalElevation = if (isGlass || variant != SplitButtonVariant.ELEVATED) 0.dp else 4.dp,
            shadowElevation = if (isGlass || variant != SplitButtonVariant.ELEVATED) 0.dp else 3.dp,
            modifier = Modifier
                .height(height)
                .graphicsLayer {
                    scaleX = primaryScale
                    scaleY = primaryScale
                }
                .then(
                    if (isGlass && !simplifyFx) Modifier.liquidGlassChrome(
                        shape = primaryShape,
                        enabled = true,
                        sheenIntensity = 0.55f,
                        pressFx = true
                    ) else Modifier
                )
                .clip(primaryShape)
                .clickable(
                    interactionSource = primaryInteractionSource,
                    indication = ripple(),
                    enabled = enabled,
                    onClick = onPrimaryClick
                )
                .petalTouchFeedback()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = (height.value * 0.35f).dp)
                    .animateContentSize()
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size((height.value * 0.44f).dp)
                    )
                    Spacer(Modifier.width((height.value * 0.16f).dp))
                }
                Text(
                    text = label,
                    style = if (height >= 48.dp) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }

        // --- CONNECTED MORPHING MENU SURFACE ---
        Surface(
            shape = menuShape,
            color = menuContainer,
            tonalElevation = if (isGlass || variant != SplitButtonVariant.ELEVATED) 0.dp else 4.dp,
            shadowElevation = if (isGlass || variant != SplitButtonVariant.ELEVATED) 0.dp else 3.dp,
            modifier = Modifier
                .height(height)
                .width((height.value * 0.88f).dp)
                .graphicsLayer {
                    scaleX = menuScale
                    scaleY = menuScale
                }
                .then(
                    if (isGlass && !simplifyFx) Modifier.liquidGlassChrome(
                        shape = menuShape,
                        enabled = true,
                        sheenIntensity = 0.55f,
                        pressFx = true
                    ) else Modifier
                )
                .clip(menuShape)
                .clickable(
                    interactionSource = menuInteractionSource,
                    indication = ripple(),
                    enabled = enabled,
                    onClick = onMenuClick
                )
                .petalTouchFeedback()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.ui_show_more_options),
                    tint = menuContent,
                    modifier = Modifier
                        .size((height.value * 0.48f).dp)
                        .rotate(arrowRotation)
                )
            }
        }
    }
}
