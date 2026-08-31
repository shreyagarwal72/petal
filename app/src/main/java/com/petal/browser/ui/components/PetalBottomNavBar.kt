package com.petal.browser.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi

enum class PetalNavTab {
    HOME, NEW_TAB, TABS, MENU
}

// -------------------------------------------------------------------------------------------------
// AGSL Progressive Blur Shader & Modifier (from Remember and FilePipe)
// -------------------------------------------------------------------------------------------------

private val dualEdgeBlurAgsl =
    """
    uniform shader content;
    uniform float blurRadius;
    uniform float topHeight;
    uniform float bottomHeight;
    uniform float contentHeight;
    uniform float topBlurProgressPower;

    half4 main(float2 fragCoord) {
        float topProgress = topHeight > 0.0
            ? 1.0 - clamp(fragCoord.y / topHeight, 0.0, 1.0)
            : 0.0;
        float bottomProgress = bottomHeight > 0.0
            ? 1.0 - clamp((contentHeight - fragCoord.y) / bottomHeight, 0.0, 1.0)
            : 0.0;

        float topBlur = pow(topProgress, topBlurProgressPower);
        float bottomBlur = pow(bottomProgress, 2.5);
        float progress = max(topBlur, bottomBlur);
        float radius = progress * blurRadius;

        if (radius <= 0.0) {
            return content.eval(fragCoord);
        }

        half4 accum = half4(0.0);
        float weightSum = 0.0;

        float dither = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
        float2 jitter = float2(dither - 0.5, fract(dither * 1.618) - 0.5);

        const int SAMPLES = 5;
        float offsetScale = radius / float(SAMPLES);

        for (int x = -SAMPLES; x <= SAMPLES; x++) {
            for (int y = -SAMPLES; y <= SAMPLES; y++) {
                float2 offset = (float2(float(x), float(y)) + jitter) * offsetScale;
                float distSq = dot(offset, offset);
                float radiusSq = radius * radius;

                if (distSq <= radiusSq) {
                    float weight = exp(-3.0 * distSq / radiusSq);
                    accum += content.eval(fragCoord + offset) * weight;
                    weightSum += weight;
                }
            }
        }

        return accum / weightSum;
    }
    """.trimIndent()

/**
 * Real AGSL progressive blur modifier on Android 13+ (API 33+), with vertical gradient overlay
 * and fallback rendering on earlier Android versions (matching Remember and FilePipe).
 */
fun Modifier.progressiveBlur(
    blurRadius: Float = 80f,
    topHeight: Float = 0f,
    bottomHeight: Float = 0f,
    showGradientOverlay: Boolean = true,
    overlayAlpha: Float = 0.28f,
    overlayAlphaBottom: Float = 0.45f,
    topBlurProgressPower: Float = 2.5f,
    topAlphaMultiplier: Float = 1f,
    bottomAlphaMultiplier: Float = 1f,
): Modifier =
    composed {
        val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
        val finalAlphaTop = (if (blurRadius <= 0f) 1.0f else overlayAlpha) * topAlphaMultiplier
        val finalAlphaBottom = (if (blurRadius <= 0f) 1.0f else overlayAlphaBottom) * bottomAlphaMultiplier

        val overlayColorTop =
            remember(surfaceContainer, finalAlphaTop) {
                surfaceContainer.copy(alpha = finalAlphaTop)
            }
        val overlayColorBottom =
            remember(surfaceContainer, finalAlphaBottom) {
                surfaceContainer.copy(alpha = finalAlphaBottom)
            }

        val blurModifier =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && blurRadius > 0f) {
                val shader = remember { RuntimeShader(dualEdgeBlurAgsl) }
                Modifier.graphicsLayer {
                    shader.setFloatUniform("blurRadius", blurRadius)
                    shader.setFloatUniform("topHeight", topHeight * topAlphaMultiplier)
                    shader.setFloatUniform("bottomHeight", bottomHeight * bottomAlphaMultiplier)
                    shader.setFloatUniform("contentHeight", size.height)
                    shader.setFloatUniform("topBlurProgressPower", topBlurProgressPower)
                    renderEffect =
                        RenderEffect
                            .createRuntimeShaderEffect(shader, "content")
                            .asComposeRenderEffect()
                }
            } else {
                Modifier
            }

        val gradientModifier =
            if (showGradientOverlay) {
                Modifier.drawWithContent {
                    drawContent()
                    val activeTopHeight = topHeight * topAlphaMultiplier
                    if (activeTopHeight > 0f) {
                        val brush =
                            if (blurRadius <= 0f) {
                                Brush.verticalGradient(
                                    colorStops =
                                        arrayOf(
                                            0.0f to overlayColorTop,
                                            0.75f to overlayColorTop.copy(alpha = overlayColorTop.alpha * 0.95f),
                                            1.0f to Color.Transparent,
                                        ),
                                    endY = activeTopHeight,
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(overlayColorTop, Color.Transparent),
                                    endY = activeTopHeight,
                                )
                            }
                        drawRect(brush = brush)
                    }
                    val activeBottomHeight = bottomHeight * bottomAlphaMultiplier
                    if (activeBottomHeight > 0f) {
                        val brush =
                            if (blurRadius <= 0f) {
                                Brush.verticalGradient(
                                    colorStops =
                                        arrayOf(
                                            0.0f to Color.Transparent,
                                            0.65f to overlayColorBottom.copy(alpha = overlayColorBottom.alpha * 0.9f),
                                            1.0f to overlayColorBottom,
                                        ),
                                    startY = size.height - activeBottomHeight,
                                    endY = size.height,
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, overlayColorBottom),
                                    startY = size.height - activeBottomHeight,
                                )
                            }
                        drawRect(brush = brush)
                    }
                }
            } else {
                Modifier
            }

        this.then(blurModifier).then(gradientModifier)
    }

/**
 * Progressive Frosted-Glass Blur Layer (matching FilePipe and Remember).
 * Leverages AGSL RuntimeShader on Android 13+ and graded opacity gradient fallback.
 */
@Composable
fun ProgressiveBlurBar(
    modifier: Modifier = Modifier,
    blurRadius: Float = 80f
) {
    val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Modifier.progressiveBlur(
                        blurRadius = blurRadius,
                        bottomHeight = 300f,
                        overlayAlphaBottom = 0.55f
                    )
                } else if (isBlurSupported) {
                    Modifier.blur(20.dp)
                } else Modifier
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        backgroundColor.copy(alpha = 0.35f),
                        backgroundColor.copy(alpha = 0.75f),
                        backgroundColor.copy(alpha = 0.95f)
                    )
                )
            )
    )
}

// -------------------------------------------------------------------------------------------------
// Modern Floating Bottom Navigation Bar (Remember & FilePipe style HorizontalFloatingToolbar)
// -------------------------------------------------------------------------------------------------

/**
 * Modern Floating Navigation Bar with Material 3 Expressive HorizontalFloatingToolbar
 * and Progressive Frosted-Glass Blur (inspired by bikram-agarwal/Remember and bikram-agarwal/FilePipe).
 * Features:
 * - Proper HorizontalFloatingToolbar floating pill architecture
 * - Spring-animated tab expansion (72.dp label expand on active selection)
 * - Tactile active container pill morphing
 * - Edge-to-edge progressive blur effect behind navigation bar
 * - Live Tab Count badge with animated scale bounce
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalBottomNavBar(
    selectedTab: PetalNavTab,
    tabCount: Int,
    isIncognito: Boolean = false,
    isFloatingStyle: Boolean = true,
    isProgressiveBlurEnabled: Boolean = true,
    onHomeClick: () -> Unit,
    onNewTabClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedCount by animateIntAsState(
        targetValue = tabCount,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tabCountAnimation"
    )

    val badgeScale = remember { Animatable(1f) }
    LaunchedEffect(tabCount) {
        badgeScale.snapTo(1.35f)
        badgeScale.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    val tabsLabel = if (isIncognito) "Incognito ($animatedCount)" else "Tabs ($animatedCount)"
    val newTabLabel = if (isIncognito) "Incognito" else "New"

    if (isFloatingStyle) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Progressive blur underlay (FilePipe / Remember style)
            if (isProgressiveBlurEnabled) {
                ProgressiveBlurBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .align(Alignment.BottomCenter)
                )
            }

            // Material 3 Expressive Floating Toolbar (matching Remember & FilePipe)
            val containerColor = if (isIncognito) {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (isProgressiveBlurEnabled) 0.90f else 0.98f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isProgressiveBlurEnabled) 0.90f else 0.98f)
            }

            val contentColor = if (isIncognito) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .wrapContentWidth()
                    .height(64.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape),
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                    toolbarContainerColor = containerColor,
                    toolbarContentColor = contentColor
                )
            ) {
                FloatingNavTabItem(
                    selected = selectedTab == PetalNavTab.HOME,
                    label = "Home",
                    index = 0,
                    icon = { isSelected, tint ->
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = "Home",
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    onClick = onHomeClick
                )

                FloatingNavTabItem(
                    selected = selectedTab == PetalNavTab.NEW_TAB,
                    label = newTabLabel,
                    index = 1,
                    icon = { isSelected, tint ->
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New Tab",
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    onClick = onNewTabClick
                )

                FloatingNavTabItem(
                    selected = selectedTab == PetalNavTab.TABS,
                    label = tabsLabel,
                    index = 2,
                    icon = { isSelected, tint ->
                        TabCountBadge(
                            color = tint,
                            count = animatedCount,
                            scale = badgeScale.value
                        )
                    },
                    onClick = onTabsClick
                )

                FloatingNavTabItem(
                    selected = selectedTab == PetalNavTab.MENU,
                    label = "Menu",
                    index = 3,
                    icon = { isSelected, tint ->
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Menu",
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    onClick = onMenuClick
                )
            }
        }
    } else {
        // Flat Bottom Navigation Bar with Optional Progressive Blur
        Box(modifier = modifier.fillMaxWidth()) {
            if (isProgressiveBlurEnabled) {
                ProgressiveBlurBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                )
            }

            Surface(
                color = if (isIncognito) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onHomeClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = "Home",
                                tint = if (selectedTab == PetalNavTab.HOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = onNewTabClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "New Tab",
                                tint = if (selectedTab == PetalNavTab.NEW_TAB) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = onTabsClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            TabCountBadge(
                                color = if (selectedTab == PetalNavTab.TABS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                count = animatedCount,
                                scale = badgeScale.value
                            )
                        }

                        IconButton(
                            onClick = onMenuClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Menu",
                                tint = if (selectedTab == PetalNavTab.MENU) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating Nav Tab Item (Remember & FilePipe style).
 * Features spring-animated label width expansion (`72.dp`), active background morphing,
 * and filled/tonal icon button colors.
 */
@Composable
private fun FloatingNavTabItem(
    selected: Boolean,
    label: String,
    index: Int,
    icon: @Composable (isSelected: Boolean, tint: Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val labelWidth by animateDpAsState(
        targetValue = if (selected) 72.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "nav_label_$index"
    )

    val activeContentColor = MaterialTheme.colorScheme.primary
    val activeContainerColor = MaterialTheme.colorScheme.background
    val inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant

    val currentContentColor = if (selected) activeContentColor else inactiveContentColor

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) activeContainerColor else Color.Transparent,
        modifier = modifier
            .height(48.dp)
            .width(48.dp + labelWidth)
            .clip(CircleShape)
            .semantics { contentDescription = label }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = if (selected) 8.dp else 0.dp)
                .fillMaxHeight()
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon(selected, currentContentColor)
            }

            if (labelWidth > 4.dp) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    color = currentContentColor,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Chrome Android style live tab counter badge (bordered box with the current tab
 * count), reused as the icon slot for the Tabs item in both bar styles.
 */
@Composable
private fun TabCountBadge(color: Color, count: Int, scale: Float) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = 2.dp,
                color = color,
                shape = RoundedCornerShape(7.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            color = color,
            textAlign = TextAlign.Center
        )
    }
}
