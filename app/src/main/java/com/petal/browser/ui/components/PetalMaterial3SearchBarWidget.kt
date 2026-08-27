/*
 * PetalMaterial3SearchBarWidget.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Collection of custom Material 3 search bar widgets for Petal Browser based on
 * reference specifications, featuring dynamic theme support, fully rounded pill
 * containers, scalloped badge shapes, and high-contrast violet accents.
 */

package com.petal.browser.ui.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.petal.browser.haptics.PetalHapticEngine
import kotlin.math.cos
import kotlin.math.sin

/**
 * 5 distinct Layout Variants for the Petal Search Bar Widget
 */
enum class SearchBarVariant(
    val key: String,
    val title: String,
    val subtitle: String
) {
    VARIANT_1("variant_1", "Center Scalloped Badge + Flanking Dots", "Centered flower badge with double indicator dots"),
    VARIANT_2("variant_2", "Left Logo + Right Action Pill Capsule", "Left logo with secondary capsule containing Incognito, AI, Lens"),
    VARIANT_3("variant_3", "Center Scalloped Badge + AI & Lens Flanks", "Center flower badge flanked by AI Sparkle and Lens scanner"),
    VARIANT_4("variant_4", "Search Capsule + Separate Circular Action Buttons", "Left search capsule with separate circular AI, Incognito, Lens buttons"),
    VARIANT_5("variant_5", "Minimal Circular Logo + Trailing AI Sparkle", "Circular logo on the left with single trailing AI sparkle");

    companion object {
        fun fromKey(key: String): SearchBarVariant {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: VARIANT_1
        }
    }
}

/**
 * Returns a 12-lobed scalloped flower shape for Material 3 Expressive badge logos.
 */
fun scallopedShape(lobes: Int = 12, innerRadiusRatio: Float = 0.88f): Shape {
    return GenericShape { size, _ ->
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val outerRadius = minOf(centerX, centerY)
        val innerRadius = outerRadius * innerRadiusRatio
        val totalPoints = lobes * 2
        val step = (2.0 * Math.PI / totalPoints).toFloat()

        for (i in 0 until totalPoints) {
            val angle = i * step - (Math.PI / 2.0).toFloat()
            val r = if (i % 2 == 0) outerRadius else innerRadius
            val x = centerX + r * cos(angle)
            val y = centerY + r * sin(angle)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * Draws the "G" / "P" monogram logo inside widgets.
 */
@Composable
fun WidgetMonogramLogo(
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSizeSp: Int = 22
) {
    Text(
        text = "G",
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Black,
            fontSize = fontSizeSp.sp
        ),
        color = textColor,
        modifier = modifier
    )
}

/**
 * Custom Incognito Hat & Glasses Mask Vector Icon.
 */
@Composable
fun IncognitoMaskIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height

        // Hat brim
        val brimY = h * 0.42f
        drawLine(
            color = tint,
            start = Offset(w * 0.15f, brimY),
            end = Offset(w * 0.85f, brimY),
            strokeWidth = w * 0.1f
        )

        // Hat crown
        val crownPath = Path().apply {
            moveTo(w * 0.32f, brimY)
            cubicTo(w * 0.32f, h * 0.15f, w * 0.40f, h * 0.15f, w * 0.50f, h * 0.15f)
            cubicTo(w * 0.60f, h * 0.15f, w * 0.68f, h * 0.15f, w * 0.68f, brimY)
            close()
        }
        drawPath(path = crownPath, color = tint)

        // Glasses lenses (two circles)
        val lensY = h * 0.68f
        val lensRadius = w * 0.14f
        drawCircle(
            color = tint,
            radius = lensRadius,
            center = Offset(w * 0.35f, lensY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.08f)
        )
        drawCircle(
            color = tint,
            radius = lensRadius,
            center = Offset(w * 0.65f, lensY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.08f)
        )
        // Glasses bridge
        drawLine(
            color = tint,
            start = Offset(w * 0.44f, lensY),
            end = Offset(w * 0.56f, lensY),
            strokeWidth = w * 0.08f
        )
    }
}

/**
 * Main Search Bar Widget container that renders the user-selected SearchBarVariant.
 */
@Composable
fun PetalMaterial3SearchBarWidget(
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenAi: (() -> Unit)? = null,
    onOpenIncognito: (() -> Unit)? = null,
    onOpenLens: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var selectedVariantKey by remember {
        mutableStateOf(sp.getString("sp_search_bar_widget_variant", SearchBarVariant.VARIANT_1.key) ?: SearchBarVariant.VARIANT_1.key)
    }

    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "sp_search_bar_widget_variant") {
                selectedVariantKey = sp.getString("sp_search_bar_widget_variant", SearchBarVariant.VARIANT_1.key) ?: SearchBarVariant.VARIANT_1.key
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val variant = SearchBarVariant.fromKey(selectedVariantKey)

    val effectiveOpenSearch = {
        PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.5f)
        onSearch("")
    }

    val effectiveOpenAi = {
        PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.6f)
        if (onOpenAi != null) {
            onOpenAi()
        } else if (activity != null) {
            com.petal.browser.compose.ai.PetalAiHubBridge.showAiHub(activity)
        }
    }

    val effectiveOpenIncognito = {
        PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.6f)
        if (onOpenIncognito != null) {
            onOpenIncognito()
        } else if (activity != null) {
            (activity as? com.petal.browser.activity.BrowserActivity)?.let { b ->
                b.addAlbum("Incognito Tab", "petal://incognito", true, true)
            }
        }
    }

    val effectiveOpenLens = {
        PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.6f)
        if (onOpenLens != null) {
            onOpenLens()
        } else if (activity != null) {
            com.petal.browser.compose.mlkit.PetalImageScannerBridge.show(activity, "")
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        when (variant) {
            SearchBarVariant.VARIANT_1 -> SearchBarVariant1(
                onSearch = effectiveOpenSearch
            )
            SearchBarVariant.VARIANT_2 -> SearchBarVariant2(
                onSearch = effectiveOpenSearch,
                onOpenIncognito = effectiveOpenIncognito,
                onOpenAi = effectiveOpenAi,
                onOpenLens = effectiveOpenLens
            )
            SearchBarVariant.VARIANT_3 -> SearchBarVariant3(
                onSearch = effectiveOpenSearch,
                onOpenAi = effectiveOpenAi,
                onOpenLens = effectiveOpenLens
            )
            SearchBarVariant.VARIANT_4 -> SearchBarVariant4(
                onSearch = effectiveOpenSearch,
                onOpenAi = effectiveOpenAi,
                onOpenIncognito = effectiveOpenIncognito,
                onOpenLens = effectiveOpenLens
            )
            SearchBarVariant.VARIANT_5 -> SearchBarVariant5(
                onSearch = effectiveOpenSearch,
                onOpenAi = effectiveOpenAi
            )
        }
    }
}

// ── Variant 1: Center Scalloped Badge + Flanking Dots ───────────────────────

@Composable
fun SearchBarVariant1(
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "v1_scale"
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onSearch
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
        ) {
            // High contrast violet pill track in center
            Surface(
                shape = CircleShape,
                color = Color(0xFF6750A4),
                modifier = Modifier
                    .width(220.dp)
                    .height(44.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                ) {
                    // Left 2 dots
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE8DEF8)))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE8DEF8)))
                    }

                    // Right 2 dots
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE8DEF8)))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE8DEF8)))
                    }
                }
            }

            // Scalloped Flower Badge Logo overlapping center
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(scallopedShape(lobes = 12))
                    .background(Color(0xFFE8DEF8))
            ) {
                WidgetMonogramLogo(textColor = Color(0xFF4A4458), fontSizeSp = 24)
            }
        }
    }
}

// ── Variant 2: Left Logo + Right Triple Action Pill Capsule ─────────────────

@Composable
fun SearchBarVariant2(
    onSearch: () -> Unit,
    onOpenIncognito: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenLens: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "v2_scale"
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            // Left Search Entry trigger
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = onSearch
                    )
            ) {
                Spacer(Modifier.width(10.dp))
                WidgetMonogramLogo(textColor = MaterialTheme.colorScheme.onSurface, fontSizeSp = 24)
            }

            // Right solid violet pill capsule containing 3 actions
            Surface(
                shape = CircleShape,
                color = Color(0xFFD0BCFF),
                modifier = Modifier.height(46.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    IconButton(onClick = onOpenIncognito, modifier = Modifier.size(40.dp)) {
                        IncognitoMaskIcon(tint = Color(0xFF381E72))
                    }
                    IconButton(onClick = onOpenAi, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI", tint = Color(0xFF381E72))
                    }
                    IconButton(onClick = onOpenLens, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.CenterFocusWeak, contentDescription = "Lens", tint = Color(0xFF381E72))
                    }
                }
            }
        }
    }
}

// ── Variant 3: Center Scalloped Badge + AI & Lens Flanks ────────────────────

@Composable
fun SearchBarVariant3(
    onSearch: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenLens: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "v3_scale"
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            // Left AI Sparkle
            IconButton(onClick = onOpenAi, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = "AI Sparkle",
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(26.dp)
                )
            }

            // Center Scalloped Flower Badge Logo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(scallopedShape(lobes = 12))
                    .background(Color(0xFFD0BCFF))
                    .clickable(onClick = onSearch)
            ) {
                WidgetMonogramLogo(textColor = Color(0xFF381E72), fontSizeSp = 24)
            }

            // Right Lens scanner
            IconButton(onClick = onOpenLens, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Rounded.CenterFocusWeak,
                    contentDescription = "Lens",
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

// ── Variant 4: Search Capsule + Separate Circular Action Buttons ────────────

@Composable
fun SearchBarVariant4(
    onSearch: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenIncognito: () -> Unit,
    onOpenLens: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "v4_scale"
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
        ) {
            // Compact Left Search Capsule
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .weight(1.1f)
                    .height(46.dp)
                    .clickable(onClick = onSearch)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Search",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Right: 3 separate distinct circular action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Violet AI Sparkle button
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFD0BCFF),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onOpenAi)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = "AI",
                            tint = Color(0xFF381E72),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // 2. Muted Mauve Incognito button
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE7E0EC),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onOpenIncognito)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        IncognitoMaskIcon(tint = Color(0xFF49454F))
                    }
                }

                // 3. Salmon Red Lens button
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFF6B6B),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onOpenLens)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Rounded.CenterFocusWeak,
                            contentDescription = "Lens",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Variant 5: Minimal Circular Logo + Trailing AI Sparkle ──────────────────

@Composable
fun SearchBarVariant5(
    onSearch: () -> Unit,
    onOpenAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "v5_scale"
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
        ) {
            // Circular Logo Badge on left
            Surface(
                shape = CircleShape,
                color = Color(0xFFE8DEF8),
                modifier = Modifier
                    .size(50.dp)
                    .clickable(onClick = onSearch)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    WidgetMonogramLogo(textColor = Color(0xFF4A4458), fontSizeSp = 22)
                }
            }

            // Middle Search Click Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onSearch)
            )

            // Trailing AI Sparkle on right
            IconButton(onClick = onOpenAi, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = "AI Sparkle",
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
