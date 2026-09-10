/*
 * PetalFeaturedCarousel.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Expressive Featured Banner Carousel adapted from Zenith for Petal Browser.
 * Features:
 * - Fluid weighted step card transitions with spring physics (Spring.DampingRatioLowBouncy)
 * - Auto-scrolling carousel with lifecycle pause/resume
 * - Animated progress indicators with animated width and fill
 * - Dynamic redirection to popular Petal browser features & highlight targets
 */

package com.petal.browser.compose.settings.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.petal.browser.compose.settings.SettingsCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Data model for an item displayed in the Petal feature carousel.
 */
data class PetalFeaturedItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isTertiary: Boolean = false,
    val onClick: () -> Unit = {}
)

/**
 * Featured Banner Carousel for Petal Browser Settings.
 * Matches Zenith's signature animated card morphing, auto-scrolling with progress bar indicators,
 * and redirects directly to famous browser features.
 */
@Composable
fun PetalFeaturedCarousel(
    onCategoryClick: (SettingsCategory, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Suggestions curated from Petal's most popular browser settings & features
    val suggestions = remember {
        listOf(
            PetalFeaturedItem(
                title = "Ad & Tracker Shield",
                description = "uBlock Origin-grade content filter engine",
                icon = Icons.Outlined.Shield,
                onClick = { onCategoryClick(SettingsCategory.PRIVACY, "privacy_adblock") }
            ),
            PetalFeaturedItem(
                title = "Pure Black AMOLED",
                description = "True pitch-black mode for OLED displays",
                icon = Icons.Outlined.DarkMode,
                onClick = { onCategoryClick(SettingsCategory.APPEARANCE, "appearance_theme") }
            ),
            PetalFeaturedItem(
                title = "Custom Fonts & Typography",
                description = "Load custom TTF/OTF fonts or Product Sans",
                icon = Icons.Outlined.FontDownload,
                onClick = { onCategoryClick(SettingsCategory.APPEARANCE, "appearance_font") }
            ),
            PetalFeaturedItem(
                title = "High Refresh Rate",
                description = "Lock 120Hz/144Hz peak display smoothness",
                icon = Icons.Outlined.Speed,
                onClick = { onCategoryClick(SettingsCategory.APPEARANCE, "appearance_refresh") }
            ),
            PetalFeaturedItem(
                title = "Deep Research AI",
                description = "Connect Gemini, GPT-4o, Claude or Ollama",
                icon = Icons.Outlined.AutoAwesome,
                onClick = { onCategoryClick(SettingsCategory.API_INTEGRATIONS, "ai") }
            ),
            PetalFeaturedItem(
                title = "Private DNS Protection",
                description = "Encrypt queries with Cloudflare & Google DoH",
                icon = Icons.Outlined.VpnLock,
                onClick = { onCategoryClick(SettingsCategory.PRIVACY, "privacy_private_dns") }
            ),
            PetalFeaturedItem(
                title = "Bottom Address Bar",
                description = "Ergonomic one-handed URL search navigation",
                icon = Icons.Outlined.ViewDay,
                onClick = { onCategoryClick(SettingsCategory.EXPERIMENTAL, "exp_address_bar") }
            ),
            PetalFeaturedItem(
                title = "M3 Expressive Shapes",
                description = "Fluid morphing ambient background shapes",
                icon = Icons.Outlined.Layers,
                onClick = { onCategoryClick(SettingsCategory.APPEARANCE, "appearance_layout") }
            ),
            PetalFeaturedItem(
                title = "Inactive Tabs Cleanup",
                description = "Declutter old tabs after 7, 14 or 21 days",
                icon = Icons.Outlined.Tab,
                onClick = { onCategoryClick(SettingsCategory.TABS, "tabs_inactive") }
            ),
            PetalFeaturedItem(
                title = "Predictive Back Gestures",
                description = "Fluid Android 14+ predictive back transitions",
                icon = Icons.Outlined.Animation,
                onClick = { onCategoryClick(SettingsCategory.DISPLAY_ZOOM, "display") }
            ),
            PetalFeaturedItem(
                title = "Backup & Restore",
                description = "Export all bookmarks, history & settings to JSON",
                icon = Icons.Outlined.CloudUpload,
                onClick = { onCategoryClick(SettingsCategory.DATA_STORAGE, "data_backup") }
            ),
            PetalFeaturedItem(
                title = "Material You Dynamic Color",
                description = "Harmonize browser palette with system wallpaper",
                icon = Icons.Outlined.Palette,
                onClick = { onCategoryClick(SettingsCategory.APPEARANCE, "appearance_theme") }
            )
        ).shuffled().take(3)
    }

    val items = remember(suggestions) {
        val list = mutableListOf<PetalFeaturedItem>()

        // 1. Support Development (Ko-fi / Developer Support)
        list.add(
            PetalFeaturedItem(
                title = "Support Development",
                description = "Fuel our mission with a tip on Ko-fi",
                icon = Icons.Outlined.Favorite,
                isTertiary = true,
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/shreyagarwal72"))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        onCategoryClick(SettingsCategory.ABOUT, "about_developer")
                    }
                }
            )
        )

        // 2. Star on GitHub
        list.add(
            PetalFeaturedItem(
                title = "Star on GitHub",
                description = "Love Petal? Give us a star on GitHub!",
                icon = Icons.Outlined.Star,
                isTertiary = true,
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/shreyagarwal72/petal"))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        onCategoryClick(SettingsCategory.ABOUT, "about_developer")
                    }
                }
            )
        )

        list + suggestions
    }

    val itemsCount = items.size
    val pagerState = rememberPagerState { itemsCount }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val spacing = 4.dp

    val carouselAnimationSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        )
    }

    val autoScrollProgress = remember { Animatable(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(pagerState.settledPage, itemsCount, lifecycleOwner) {
        if (itemsCount > 1) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                autoScrollProgress.snapTo(0f)
                val startTime = System.currentTimeMillis()
                while (true) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val p = (elapsed.toFloat() / 5000f).coerceIn(0f, 1f)
                    autoScrollProgress.snapTo(p)
                    if (p >= 1f) break
                    delay(16)
                }

                if (!pagerState.isScrollInProgress) {
                    val nextStep = (pagerState.currentPage + 1) % itemsCount
                    pagerState.animateScrollToPage(
                        page = nextStep,
                        animationSpec = carouselAnimationSpec
                    )
                }
            }
        } else {
            autoScrollProgress.snapTo(0f)
        }
    }

    val interactionSources = remember(itemsCount) { List(itemsCount) { MutableInteractionSource() } }

    val expressiveSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    val visualProgress by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val spacingPx = with(density) { spacing.toPx() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                for (i in 0 until itemsCount) {
                    val dist = (visualProgress - i).absoluteValue
                    val currentWeight = when {
                        dist < 1.0f -> {
                            val maxW = if (i == 0 || i == itemsCount - 1) 0.9f else 0.82f
                            lerp(maxW, 0.1f, dist)
                        }
                        dist < 2.0f -> lerp(0.1f, 0.0f, dist - 1.0f)
                        else -> 0.0f
                    }

                    if (currentWeight > 0.005f) {
                        val currentCornerRadius = if (dist < 1.0f) lerp(1000f, 24f, dist) else 24f
                        val currentAlpha = when {
                            dist < 1.0f -> lerp(1f, 0.4f, dist)
                            dist < 2.0f -> lerp(0.4f, 0f, dist - 1.0f)
                            else -> 0f
                        }

                        val baseColor = if (items[i].isTertiary) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }

                        FeaturedStepCard(
                            item = items[i],
                            dist = dist,
                            isToTheLeft = i < visualProgress,
                            interactionSource = interactionSources[i],
                            modifier = Modifier.weight(currentWeight),
                            containerColor = baseColor.copy(alpha = currentAlpha),
                            cornerRadius = currentCornerRadius.dp,
                            motionSpec = expressiveSpring
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .alpha(0f)
                    .pointerInput(itemsCount) {
                        detectTapGestures { offset ->
                            val tapX = offset.x
                            var currentX = 0f
                            val currentProgress = pagerState.currentPage + pagerState.currentPageOffsetFraction

                            val renderedWeights = (0 until itemsCount).map { i ->
                                val dist = (currentProgress - i).absoluteValue
                                when {
                                    dist < 1.0f -> lerp(if (i == 0 || i == itemsCount - 1) 0.9f else 0.82f, 0.1f, dist)
                                    dist < 2.0f -> lerp(0.1f, 0.0f, dist - 1.0f)
                                    else -> 0.0f
                                }
                            }

                            val visibleIndices = renderedWeights.indices.filter { renderedWeights[it] > 0.005f }
                            val totalGaps = (visibleIndices.size - 1).coerceAtLeast(0)
                            val availableWidthForCards = totalWidthPx - (spacingPx * totalGaps)

                            for (i in visibleIndices) {
                                val weight = renderedWeights[i]
                                val cardWidth = weight * availableWidthForCards

                                if (tapX >= currentX && tapX <= currentX + cardWidth) {
                                    coroutineScope.launch {
                                        val press = PressInteraction.Press(offset)
                                        interactionSources[i].emit(press)
                                        delay(150)
                                        interactionSources[i].emit(PressInteraction.Release(press))

                                        if (pagerState.currentPage == i) {
                                            items[i].onClick()
                                        } else {
                                            pagerState.animateScrollToPage(
                                                page = i,
                                                animationSpec = carouselAnimationSpec
                                            )
                                        }
                                    }
                                    break
                                }
                                currentX += cardWidth + spacingPx
                            }
                        }
                    }
            ) {
                Box(Modifier.fillMaxSize())
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(itemsCount) { index ->
                val isActive = pagerState.currentPage == index
                CarouselIndicator(
                    isActive = isActive,
                    progress = if (isActive && pagerState.settledPage == index && autoScrollProgress.value < 1f) autoScrollProgress.value else 0f,
                    motionSpec = expressiveSpring
                )
            }
        }
    }
}

@Composable
private fun CarouselIndicator(
    isActive: Boolean,
    progress: Float,
    motionSpec: SpringSpec<Float>
) {
    val width = if (isActive) 32.dp else 8.dp
    val animatedWidth by animateDpAsState(
        targetValue = width,
        animationSpec = spring(
            dampingRatio = motionSpec.dampingRatio,
            stiffness = motionSpec.stiffness
        ),
        label = "IndicatorWidth"
    )
    val color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    val animatedColor by animateColorAsState(
        targetValue = color,
        label = "IndicatorColor"
    )

    Box(
        modifier = Modifier
            .width(animatedWidth)
            .height(6.dp)
            .clip(CircleShape)
            .background(animatedColor)
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
private fun RowScope.FeaturedStepCard(
    item: PetalFeaturedItem,
    dist: Float,
    isToTheLeft: Boolean,
    interactionSource: MutableInteractionSource,
    containerColor: Color,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    motionSpec: SpringSpec<Float>
) {
    val isFocused = dist < 0.6f
    val contentColor = if (item.isTertiary) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {}
            ),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(cornerRadius)
    ) {
        AnimatedContent(
            targetState = isFocused,
            transitionSpec = {
                val springSpec = spring<IntOffset>(
                    stiffness = motionSpec.stiffness,
                    dampingRatio = motionSpec.dampingRatio
                )

                val slideIn = if (targetState) {
                    slideInHorizontally(animationSpec = springSpec) { if (isToTheLeft) -it else it }
                } else {
                    slideInHorizontally(animationSpec = springSpec) { if (isToTheLeft) it else -it }
                }

                val slideOut = if (targetState) {
                    slideOutHorizontally(animationSpec = springSpec) { if (isToTheLeft) it else -it }
                } else {
                    slideOutHorizontally(animationSpec = springSpec) { if (isToTheLeft) -it else it }
                }

                (fadeIn(animationSpec = spring(stiffness = motionSpec.stiffness, dampingRatio = motionSpec.dampingRatio)) + slideIn +
                 scaleIn(initialScale = 0.92f, animationSpec = spring(stiffness = motionSpec.stiffness, dampingRatio = motionSpec.dampingRatio)))
                    .togetherWith(
                        fadeOut(animationSpec = spring(stiffness = motionSpec.stiffness, dampingRatio = motionSpec.dampingRatio)) + slideOut +
                        scaleOut(targetScale = 0.92f, animationSpec = spring(stiffness = motionSpec.stiffness, dampingRatio = motionSpec.dampingRatio))
                    )
            },
            label = "CardContentTransition",
            modifier = Modifier.fillMaxSize()
        ) { focused ->
            if (focused) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = contentColor.copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = contentColor
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isToTheLeft) {
                            Icons.AutoMirrored.Outlined.KeyboardArrowLeft
                        } else {
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = contentColor.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
