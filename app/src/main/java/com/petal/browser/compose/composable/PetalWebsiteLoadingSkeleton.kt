package com.petal.browser.compose.composable

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.unit.HelperUnit

/**
 * Creates an animated shimmering gradient brush for Material 3 Expressive skeletons.
 */
@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "websiteSkeletonShimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "websiteSkeletonShimmerOffset"
    )

    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.95f)

    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim - 450f, translateAnim - 450f),
        end = Offset(translateAnim + 450f, translateAnim + 450f)
    )
}

/**
 * Material 3 Expressive Website Loading Skeleton screen.
 * Displayed while connecting and loading websites instead of a blank frame or homepage.
 */
@Composable
fun PetalWebsiteLoadingSkeleton(
    url: String,
    modifier: Modifier = Modifier
) {
    val shimmerBrush = rememberShimmerBrush()
    val displayHost = remember(url) {
        try {
            val domain = HelperUnit.domain(url)
            if (!domain.isNullOrBlank() && !domain.equals("blank", ignoreCase = true)) domain else "Connecting..."
        } catch (_: Exception) {
            "Connecting..."
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState(), enabled = false),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header bar: Domain pill badge + Favicon placeholder
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(shimmerBrush)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = displayHost,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .height(10.dp)
                                .width(90.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(shimmerBrush)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush)
                )
            }

            // Hero banner skeleton card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(shimmerBrush)
            )

            // Headline skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(shimmerBrush)
            )

            // Sub-headline skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmerBrush)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Two card skeletons
            repeat(2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(shimmerBrush)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(shimmerBrush)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(shimmerBrush)
                            )
                        }
                    }
                }
            }

            // Bottom large card skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(shimmerBrush)
            )
        }
    }
}

object PetalWebsiteLoadingSkeletonBridge {
    @JvmStatic
    fun bindWebsiteLoadingSkeleton(
        composeView: ComposeView,
        lifecycleOwner: LifecycleOwner,
        urlProvider: () -> String
    ) {
        composeView.apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            if (lifecycleOwner is androidx.lifecycle.ViewModelStoreOwner) {
                setViewTreeViewModelStoreOwner(lifecycleOwner)
            }
            if (lifecycleOwner is androidx.savedstate.SavedStateRegistryOwner) {
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            }
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
                val fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                val appFont = remember(fontName) {
                    try { AppFont.valueOf(fontName) } catch (_: Exception) { AppFont.PETAL }
                }
                val colorStyle = remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    darkTheme = isSystemInDarkTheme(),
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalWebsiteLoadingSkeleton(url = urlProvider())
                }
            }
        }
    }
}
