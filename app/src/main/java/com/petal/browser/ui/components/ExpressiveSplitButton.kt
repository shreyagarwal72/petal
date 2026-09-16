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
import androidx.compose.foundation.BorderStroke
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
import com.petal.browser.ui.theme.petalTouchFeedback

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
    enabled: Boolean = true
) {
    val pillRadius = height / 2
    val innerRadius = 4.dp
    val pillGap = 3.dp

    // Morphing corner radius animation
    val animatedMenuInnerCorner by animateDpAsState(
        targetValue = if (isMenuExpanded) pillRadius else innerRadius,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "SplitButtonMenuCorner"
    )

    val animatedPrimaryInnerCorner by animateDpAsState(
        targetValue = if (isMenuExpanded) pillRadius else innerRadius,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "SplitButtonPrimaryCorner"
    )

    // Smooth drop-down arrow rotation
    val arrowRotation by animateFloatAsState(
        targetValue = if (isMenuExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "SplitButtonArrowRotation"
    )

    // Color resolution
    val containerColor = when (variant) {
        SplitButtonVariant.FILLED -> MaterialTheme.colorScheme.primary
        SplitButtonVariant.TONAL -> MaterialTheme.colorScheme.secondaryContainer
        SplitButtonVariant.ELEVATED -> MaterialTheme.colorScheme.surfaceContainerHigh
        SplitButtonVariant.OUTLINED -> MaterialTheme.colorScheme.surface
    }

    val contentColor = when (variant) {
        SplitButtonVariant.FILLED -> MaterialTheme.colorScheme.onPrimary
        SplitButtonVariant.TONAL -> MaterialTheme.colorScheme.onSecondaryContainer
        SplitButtonVariant.ELEVATED -> MaterialTheme.colorScheme.primary
        SplitButtonVariant.OUTLINED -> MaterialTheme.colorScheme.primary
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
            color = containerColor,
            tonalElevation = if (variant == SplitButtonVariant.ELEVATED) 4.dp else 0.dp,
            shadowElevation = if (variant == SplitButtonVariant.ELEVATED) 3.dp else 0.dp,
            border = if (variant == SplitButtonVariant.OUTLINED) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
            modifier = Modifier
                .height(height)
                .graphicsLayer {
                    scaleX = primaryScale
                    scaleY = primaryScale
                }
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
            color = containerColor,
            tonalElevation = if (variant == SplitButtonVariant.ELEVATED) 4.dp else 0.dp,
            shadowElevation = if (variant == SplitButtonVariant.ELEVATED) 3.dp else 0.dp,
            border = if (variant == SplitButtonVariant.OUTLINED) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
            modifier = Modifier
                .height(height)
                .width((height.value * 0.88f).dp)
                .graphicsLayer {
                    scaleX = menuScale
                    scaleY = menuScale
                }
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
                    contentDescription = "Show more options",
                    tint = contentColor,
                    modifier = Modifier
                        .size((height.value * 0.48f).dp)
                        .rotate(arrowRotation)
                )
            }
        }
    }
}
