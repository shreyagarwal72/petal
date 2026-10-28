package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.petal.browser.ui.containment.liquidGlassChrome

/** Only the bottom corners are rounded (24dp) for a clean Material 3 Expressive header look */
private val HeaderShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

/**
 * Material 3 Expressive Header component ported from LastWave-native (duxtami).
 * Features full-bleed edge-to-edge width, dynamic radial glow animations,
 * optional leading back button, title/subtitle layout, and trailing actions.
 */
@Composable
fun ExpressiveHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    enableLiquidGlass: Boolean = com.petal.browser.ui.containment.isLiquidGlassEnabled(),
    // Optional real-glass backdrop (must be a sibling of the screen background, see PetalLiquidGlass.kt)
    backdrop: com.petal.browser.ui.containment.PetalBackdrop? = null,
    maxTitleLines: Int = 2,
    maxSubtitleLines: Int = 2,
    isRootHeader: Boolean = false,
    targetTitle: String? = null,
    targetSubtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val isHoisted = com.petal.browser.predictive.LocalZenithHeaderHoisted.current
    if (isHoisted && !isRootHeader) {
        // Child screen yields to the hoisted top-level Zenith header
        return
    }

    val isZenithTransition = com.petal.browser.predictive.LocalZenithHeaderActive.current
    // Motion comes from the theme's scheme (instant when reduce-motion is on), not hardcoded springs.
    val motion = MaterialTheme.motionScheme
    val glow = MaterialTheme.colorScheme.primary
    val secondaryGlow = MaterialTheme.colorScheme.tertiary

    Box(modifier.fillMaxWidth().zIndex(1f)) {
        val sharedWallpaper = com.petal.browser.ui.glass.LocalPetalGlassWallpaper.current?.ready == true
        val realGlass = enableLiquidGlass && ((backdrop != null &&
            com.petal.browser.ui.containment.petalRealGlassSupported) ||
            sharedWallpaper)
        val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val headerColor = when {
            realGlass -> Color.Transparent
            enableLiquidGlass -> com.petal.browser.ui.containment.petalGroupSurfaceColor()
            else -> MaterialTheme.colorScheme.surfaceContainer
        }
        Surface(
            shape = HeaderShape,
            // Real glass draws its own blurred backdrop, so the container must be see-through.
            color = headerColor,
            tonalElevation = if (enableLiquidGlass) 0.dp else 2.dp,
            modifier = Modifier.fillMaxWidth().liquidGlassChrome(
                shape = HeaderShape,
                enabled = enableLiquidGlass,
                backdrop = if (realGlass && !sharedWallpaper) backdrop else null,
                isDark = isDarkSurface
            ),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (!enableLiquidGlass) Modifier.drawGlowBackground(glow, secondaryGlow) else Modifier)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 12.dp),
            ) {
                val backState = com.petal.browser.predictive.LocalPredictiveBackState.current
                val effectiveTargetTitle = targetTitle ?: if (onBack != null) "Settings" else null
                val effectiveTargetSubtitle = targetSubtitle ?: if (onBack != null) "Browser Preferences & Customization" else null
                val isGestureActive = backState.isActive && effectiveTargetTitle != null && onBack != null
                val progress = if (isGestureActive) backState.progress.coerceIn(0f, 1f) else 0f
                val swipeDir = if (backState.swipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT) 1f else -1f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = onBack != null,
                        enter = if (isZenithTransition) {
                            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) +
                                slideInHorizontally(initialOffsetX = { -it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessLow))
                        } else {
                            fadeIn(animationSpec = motion.defaultEffectsSpec()) +
                                scaleIn(initialScale = 0.92f, animationSpec = motion.defaultSpatialSpec()) +
                                slideInHorizontally(initialOffsetX = { -it / 2 }, animationSpec = motion.defaultSpatialSpec())
                        },
                        exit = if (isZenithTransition) {
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                scaleOut(targetScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                slideOutHorizontally(targetOffsetX = { -it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessLow))
                        } else {
                            fadeOut(animationSpec = motion.fastEffectsSpec()) +
                                scaleOut(targetScale = 0.92f) +
                                slideOutHorizontally(targetOffsetX = { -it / 2 }, animationSpec = motion.defaultSpatialSpec())
                        }
                    ) {
                        val backButtonAlpha = if (isGestureActive) (1f - progress * 2.0f).coerceIn(0f, 1f) else 1f
                        val backButtonScale = if (isGestureActive) (1f - 0.20f * progress).coerceIn(0.8f, 1f) else 1f
                        val backButtonOffset = if (isGestureActive) -16f * progress * swipeDir else 0f
                        Row(
                            modifier = Modifier.graphicsLayer {
                                alpha = backButtonAlpha
                                scaleX = backButtonScale
                                scaleY = backButtonScale
                                translationX = backButtonOffset
                            }
                        ) {
                            FilledTonalIconButton(
                                onClick = { onBack?.invoke() },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (enableLiquidGlass) {
                                        com.petal.browser.ui.containment.petalGroupSurfaceColor()
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = if (enableLiquidGlass) {
                                    Modifier.liquidGlassChrome(CircleShape, enabled = true)
                                } else Modifier
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                    }

                    if (isGestureActive && effectiveTargetTitle != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clipToBounds()
                        ) {
                            // Outgoing active title
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = (1f - progress * 1.8f).coerceIn(0f, 1f)
                                        translationX = progress * 48f * swipeDir
                                        scaleX = 1f - 0.06f * progress
                                        scaleY = 1f - 0.06f * progress
                                    }
                            ) {
                                HeaderTitleBlock(
                                    title = title,
                                    subtitle = subtitle,
                                    hasBack = onBack != null,
                                    maxSubtitleLines = maxSubtitleLines
                                )
                            }

                            // Incoming target title
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = ((progress - 0.2f) / 0.8f).coerceIn(0f, 1f)
                                        translationX = -28f * (1f - progress) * swipeDir
                                        scaleX = 0.94f + 0.06f * progress
                                        scaleY = 0.94f + 0.06f * progress
                                    }
                            ) {
                                HeaderTitleBlock(
                                    title = effectiveTargetTitle,
                                    subtitle = effectiveTargetSubtitle,
                                    hasBack = false,
                                    maxSubtitleLines = maxSubtitleLines
                                )
                            }
                        }
                    } else {
                        AnimatedContent(
                            targetState = title to (subtitle ?: ""),
                            transitionSpec = {
                                if (isZenithTransition) {
                                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                     slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { it / 4 } +
                                     scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)))
                                        .togetherWith(
                                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                            slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { -it / 4 } +
                                            scaleOut(targetScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessLow))
                                        )
                                } else {
                                    (fadeIn(animationSpec = motion.defaultEffectsSpec()) +
                                     slideInVertically(animationSpec = motion.defaultSpatialSpec()) { it / 3 } +
                                     scaleIn(initialScale = 0.92f, animationSpec = motion.defaultSpatialSpec()))
                                        .togetherWith(
                                            fadeOut(animationSpec = motion.fastEffectsSpec()) +
                                            slideOutVertically(animationSpec = motion.defaultSpatialSpec()) { -it / 3 } +
                                            scaleOut(targetScale = 0.92f)
                                        )
                                }
                            },
                            label = "ExpressiveHeaderTransition",
                            modifier = Modifier.weight(1f)
                        ) { (currentTitle, currentSubtitle) ->
                            HeaderTitleBlock(
                                title = currentTitle,
                                subtitle = currentSubtitle.takeIf { it.isNotBlank() },
                                hasBack = onBack != null,
                                maxSubtitleLines = maxSubtitleLines
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = if (isZenithTransition) {
                            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleIn(initialScale = 0.7f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) +
                                slideInHorizontally(initialOffsetX = { it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                expandHorizontally(expandFrom = Alignment.End, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        } else {
                            fadeIn(animationSpec = motion.defaultEffectsSpec()) +
                                scaleIn(initialScale = 0.92f, animationSpec = motion.defaultSpatialSpec())
                        },
                        exit = if (isZenithTransition) {
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleOut(targetScale = 0.7f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                slideOutHorizontally(targetOffsetX = { it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        } else {
                            fadeOut(animationSpec = motion.fastEffectsSpec()) +
                                scaleOut(targetScale = 0.92f)
                        }
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.graphicsLayer {
                                if (isGestureActive) {
                                    alpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                                    scaleX = 0.8f + 0.2f * progress
                                    scaleY = 0.8f + 0.2f * progress
                                }
                            }
                        ) {
                            actions()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderTitleBlock(
    title: String,
    subtitle: String?,
    hasBack: Boolean,
    maxSubtitleLines: Int = 2
) {
    Column(modifier = Modifier.fillMaxWidth().clipToBounds()) {
        val titleFontSize = remember(title, hasBack) {
            when {
                hasBack && title.length > 22 -> 14.sp
                hasBack && title.length > 18 -> 15.sp
                hasBack && title.length > 14 -> 16.5.sp
                hasBack -> 18.sp
                title.length > 22 -> 16.5.sp
                title.length > 18 -> 17.5.sp
                title.length > 14 -> 19.5.sp
                else -> 22.sp
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = titleFontSize,
                lineHeight = (titleFontSize.value + 4).sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        if (!subtitle.isNullOrBlank()) {
            val subtitleFontSize = remember(subtitle) {
                when {
                    subtitle.length > 32 -> 11.5.sp
                    subtitle.length > 24 -> 12.sp
                    else -> 13.sp
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = subtitleFontSize,
                    lineHeight = (subtitleFontSize.value + 4).sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = maxSubtitleLines,
                softWrap = maxSubtitleLines > 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Paints two soft radial glows whose centers drift side-to-side when foregrounded.
 */
@Composable
private fun Modifier.drawGlowBackground(color: Color, secondaryColor: Color): Modifier {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val drift: Float = if (resumed) {
        val transition = rememberInfiniteTransition(label = "headerGlowDrift")
        val animated by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 7000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "headerGlowDriftX",
        )
        animated
    } else {
        0f
    }
    return this.drawBehind {
        val cx = size.width / 2f + drift * size.width * 0.30f
        val cy = size.height * 0.46f
        val radius = (size.width.coerceAtLeast(size.height) * 1.08f).coerceAtLeast(1f)
        val secondaryCx = size.width / 2f - drift * size.width * 0.24f
        val secondaryRadius = (size.width.coerceAtLeast(size.height) * 0.72f).coerceAtLeast(1f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryColor.copy(alpha = 0.13f),
                    secondaryColor.copy(alpha = 0.045f),
                    secondaryColor.copy(alpha = 0f),
                ),
                center = Offset(secondaryCx, size.height * 0.78f),
                radius = secondaryRadius,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.23f),
                    color.copy(alpha = 0.07f),
                    color.copy(alpha = 0f),
                ),
                center = Offset(cx, cy),
                radius = radius,
            ),
        )
    }
}

/**
 * Trailing action icon for [ExpressiveHeader].
 */
@Composable
fun HeaderActionIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    enableLiquidGlass: Boolean = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments"),
) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (enableLiquidGlass) {
                com.petal.browser.ui.containment.petalGroupSurfaceColor()
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = if (enableLiquidGlass) {
            Modifier.liquidGlassChrome(CircleShape, enabled = true)
        } else Modifier,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

