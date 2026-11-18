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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
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
import com.petal.browser.ui.containment.liquidGlassChrome
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.petal.browser.ui.containment.PetalBackdrop
import com.petal.browser.ui.containment.petalBackdropSource
import com.petal.browser.ui.containment.petalRealGlassSupported
import com.petal.browser.ui.containment.petalHeaderGlass

/** Large continuous-curvature bottom corners (squircle) for the expressive header silhouette. */
private val HeaderShape = com.petal.browser.ui.theme.SmoothRoundedShape(
    topStart = 0.dp,
    topEnd = 0.dp,
    bottomEnd = 36.dp,
    bottomStart = 36.dp,
)

/**
 * Glass-only silhouette: Kyant's lens (edge refraction) accepts corner-based shapes only, and the
 * squircle above is not one. Same radii, so the header outline stays put when glass turns on.
 */
private val GlassHeaderShape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)

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

    val scheme = MaterialTheme.colorScheme
    val isDarkSurface = scheme.background.luminance() < 0.5f
    val sharedWallpaper = com.petal.browser.ui.glass.LocalPetalGlassWallpaper.current?.ready == true
    val backgroundColor = scheme.background
    val washBrush = petalHeaderGlassWash()

    // Liquid glass (all wired through the existing prefs: master toggle, opacity, blur, sheen,
    // refraction and the low-end fallback live inside liquidGlassChrome). Ported from LastWave:
    // a LOCAL sibling backdrop (page colour + themed wash) so the glass always has something to
    // blur and refract, even on a flat page. A screen can pass its own backdrop instead (see
    // PetalGlassHeaderLayout) so real content scrolls under the glass.
    val localBackdrop: PetalBackdrop? =
        if (enableLiquidGlass && petalRealGlassSupported && backdrop == null) {
            rememberLayerBackdrop {
                drawRect(backgroundColor)
                drawContent()
            }
        } else null
    val effectiveBackdrop: PetalBackdrop? = if (enableLiquidGlass && petalRealGlassSupported) backdrop ?: localBackdrop else null
    val realGlass = effectiveBackdrop != null
    val wallpaperGlass = enableLiquidGlass && !realGlass && sharedWallpaper
    val glass = realGlass || wallpaperGlass

    // LastWave header glass (blur + vibrancy + lens + sheen + rim) whenever real backdrop glass is on.
    // Low-end devices keep the cheap frosted fallback inside liquidGlassChrome.
    val glassContext = androidx.compose.ui.platform.LocalContext.current
    com.petal.browser.ui.glass.LocalPetalGlassRevision.current // live-update on glass slider changes
    val glassPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(glassContext)
    val useLastWaveGlass = realGlass && !com.petal.browser.motion.MotionGate.isLowEndDevice(glassContext) &&
        com.petal.browser.ui.containment.isLiquidGlassMaster(glassPrefs)
    val glassOpacity = glassPrefs.getFloat("sp_liquid_glass_alpha", 0.70f).coerceIn(0.10f, 0.95f)
    val glassBlur = com.petal.browser.ui.glass.readGlassBlurDp(glassPrefs)

    val shape: androidx.compose.ui.graphics.Shape = if (useLastWaveGlass) GlassHeaderShape else HeaderShape

    val headerColor = when {
        glass -> Color.Transparent
        enableLiquidGlass -> com.petal.browser.ui.containment.petalGroupSurfaceColor()
        // Material 3 Expressive (glass off): a surface tinted toward the primary container.
        else -> lerp(scheme.surfaceContainer, scheme.primaryContainer, if (isDarkSurface) 0.30f else 0.42f)
    }

    Box(modifier.fillMaxWidth().zIndex(1f)) {
        if (localBackdrop != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .petalBackdropSource(localBackdrop)
            ) {
                Box(Modifier.fillMaxSize().background(washBrush))
            }
        }
        Surface(
            shape = shape,
            // Real glass draws its own blurred backdrop, so the container must be see-through.
            color = headerColor,
            tonalElevation = 0.dp,
            shadowElevation = if (!enableLiquidGlass) 3.dp else 0.dp,
            modifier = Modifier.fillMaxWidth().then(
                if (useLastWaveGlass && effectiveBackdrop != null) {
                    Modifier.petalHeaderGlass(
                        backdrop = effectiveBackdrop,
                        shape = shape,
                        isDark = isDarkSurface,
                        opacity = glassOpacity,
                        blurDp = glassBlur,
                    )
                } else {
                    Modifier.liquidGlassChrome(
                        shape = shape,
                        enabled = enableLiquidGlass,
                        backdrop = effectiveBackdrop,
                        isDark = isDarkSurface
                    )
                }
            ),
        ) {
            val decorColor = MaterialTheme.colorScheme.primary
            val decorAccent = MaterialTheme.colorScheme.tertiary
            Column(
                Modifier
                    .fillMaxWidth()
                    .expressiveHeaderDecor(decorColor, decorAccent, strong = !enableLiquidGlass)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
            ) {
                val backState = com.petal.browser.predictive.LocalPredictiveBackState.current
                val effectiveTargetTitle = targetTitle
                val effectiveTargetSubtitle = targetSubtitle
                val isGestureActive = backState.isActive && !effectiveTargetTitle.isNullOrBlank() && onBack != null
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
                            com.petal.browser.ui.theme.ExpressiveIconButton(
                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                onClick = { onBack?.invoke() },
                                size = 44.dp,
                                containerColor = if (enableLiquidGlass) {
                                    com.petal.browser.ui.containment.petalGroupSurfaceColor()
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                contentColor = if (enableLiquidGlass) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = if (enableLiquidGlass) {
                                    Modifier.liquidGlassChrome(com.petal.browser.ui.theme.ExpressiveAccentShape, enabled = true)
                                } else Modifier,
                            )
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                title.length > 22 -> 17.5.sp
                title.length > 18 -> 19.5.sp
                title.length > 14 -> 22.sp
                else -> 26.sp
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = titleFontSize,
                lineHeight = (titleFontSize.value + 4).sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
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
 * Trailing action icon for [ExpressiveHeader].
 */
@Composable
fun HeaderActionIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    enableLiquidGlass: Boolean = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments"),
    /** Pick a different expressive shape per neighbour, e.g. ExpressiveButtonPreset.variety[index]. */
    preset: com.petal.browser.ui.theme.ExpressiveButtonPreset? = null,
    emphasized: Boolean = false,
) {
    val container = when {
        enableLiquidGlass -> com.petal.browser.ui.containment.petalGroupSurfaceColor()
        emphasized -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when {
        enableLiquidGlass -> MaterialTheme.colorScheme.onSurface
        emphasized -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    com.petal.browser.ui.theme.ExpressiveIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        size = 44.dp,
        containerColor = container,
        contentColor = content,
        preset = preset,
        modifier = if (enableLiquidGlass) {
            Modifier.liquidGlassChrome(com.petal.browser.ui.theme.ExpressiveAccentShape, enabled = true)
        } else Modifier,
    )
}

/**
 * Expressive accent shapes peeking in from the header corners. Glass keeps one faint shape (the glass
 * itself is the statement); the Material 3 Expressive look (glass off) gets two clearly visible ones.
 */
private fun Modifier.expressiveHeaderDecor(color: Color, accent: Color, strong: Boolean): Modifier = this.drawWithCache {
    val preset = com.petal.browser.ui.theme.PetalShapeState.effectivePreset
    val show = preset != com.petal.browser.ui.theme.ExpressiveButtonPreset.CIRCLE &&
        preset != com.petal.browser.ui.theme.ExpressiveButtonPreset.PILL
    val path = if (show) {
        com.petal.browser.ui.theme.buildExpressivePath(
            com.petal.browser.ui.theme.ExpressiveButtonPreset.SUNNY,
            cx = size.width * 0.98f,
            cy = size.height * 0.02f,
            radius = size.height * (if (strong) 0.62f else 0.55f),
        )
    } else null
    val path2 = if (show && strong) {
        com.petal.browser.ui.theme.buildExpressivePath(
            com.petal.browser.ui.theme.ExpressiveButtonPreset.SUNNY,
            cx = size.width * 0.04f,
            cy = size.height * 1.02f,
            radius = size.height * 0.42f,
        )
    } else null
    onDrawBehind {
        if (path != null) drawPath(path, color.copy(alpha = if (strong) 0.16f else 0.05f))
        if (path2 != null) drawPath(path2, accent.copy(alpha = 0.14f))
    }
}

/** Theme-tinted wash the glass blurs when no content sits under the header (from LastWave). */
@Composable
internal fun petalHeaderGlassWash(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.horizontalGradient(
        listOf(
            scheme.primary.copy(alpha = 0.20f),
            scheme.tertiary.copy(alpha = 0.08f),
            scheme.secondary.copy(alpha = 0.16f),
        ),
    )
}

/**
 * Puts [header] over [content] so content scrolls UNDER the glass (ported from LastWave's
 * GlassHeaderLayout). [header] receives the modifier + backdrop to hand to [ExpressiveHeader].
 * [content] receives the header height to add to its scroll container's top padding; without real
 * glass it gets 0 and starts below the header as before. The content is the backdrop source and the
 * header a sibling, never inside it (a feedback loop would crash).
 */
@Composable
fun PetalGlassHeaderLayout(
    modifier: Modifier = Modifier,
    enableLiquidGlass: Boolean = com.petal.browser.ui.containment.isLiquidGlassEnabled(),
    header: @Composable (modifier: Modifier, backdrop: PetalBackdrop?) -> Unit,
    content: @Composable (topInset: Dp) -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val wash = petalHeaderGlassWash()
    var headerHeightPx by androidx.compose.runtime.remember { mutableIntStateOf(0) }
    val glass = enableLiquidGlass && petalRealGlassSupported
    val backdrop: PetalBackdrop? = if (glass) {
        rememberLayerBackdrop {
            drawRect(background)
            // Screens whose content starts below the header would otherwise give the glass only flat colour.
            drawRect(wash, size = Size(size.width, headerHeightPx.toFloat()))
            drawContent()
        }
    } else null
    // Header is measured first, so with glass off the content sits exactly below it on the first
    // frame (the old stacked Column layout) and with glass on it fills the screen under the header.
    // One tree for both modes: toggling glass never resets the content's scroll state.
    androidx.compose.ui.layout.SubcomposeLayout(modifier) { constraints ->
        val w = constraints.maxWidth
        val headerPlaceable = subcompose("header") { header(Modifier, backdrop) }
            .firstOrNull()?.measure(androidx.compose.ui.unit.Constraints(w, w, 0, constraints.maxHeight))
        // A hoisted (yielding) header composes nothing: height 0, content simply fills the screen.
        val h = headerPlaceable?.height ?: 0
        if (h != headerHeightPx) headerHeightPx = h
        val top = if (backdrop != null) 0 else h
        val contentHeight = (constraints.maxHeight - top).coerceAtLeast(0)
        val inset = if (backdrop != null) h.toDp() else 0.dp
        val contentPlaceable = subcompose("content") {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.petalBackdropSource(backdrop) else Modifier)
            ) { content(inset) }
        }.first().measure(androidx.compose.ui.unit.Constraints(w, w, contentHeight, contentHeight))
        layout(w, constraints.maxHeight) {
            contentPlaceable.place(0, top)
            headerPlaceable?.place(0, 0)
        }
    }
}
