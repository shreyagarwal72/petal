package com.petal.browser.compose.incognito

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.lens.PetalLensBridge
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalAiSearchBridge
import com.petal.browser.ui.components.PetalVoiceSearchBridge
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.theme.IncognitoDarkBackground
import com.petal.browser.ui.theme.IncognitoPrimary
import com.petal.browser.ui.theme.IncognitoSurfaceContainer
import com.petal.browser.ui.theme.IncognitoSurfaceContainerHigh
import com.petal.browser.ui.theme.PetalIncognitoTheme
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.ui.theme.toShape

@Composable
fun PetalIncognitoHomeScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onSearchClick: () -> Unit = {},
    onCloseIncognito: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }

    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "sp_amoled") {
                isAmoled = sp.getBoolean("sp_amoled", false)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    var blockThirdPartyCookies by remember { mutableStateOf(true) }

    PetalIncognitoTheme(useAmoled = isAmoled) {
        val incognitoSubtitles = remember {
            listOf(
                "Off the grid. No traces, no history.",
                "Stealth mode engaged. Browse like a shadow.",
                "Your secret is safe with this tab.",
                "Agent mode activated: look around, leave no footprints.",
                "Going dark. What happens here, stays here.",
                "Browse in absolute privacy.",
                "A clean slate with zero history saved.",
                "Zero cookies, zero tracks, 100% private.",
                "Explore freely—your sessions vanish when you close the tab.",
                "No history, no suggestions, just pure browsing.",
                "Searching for a gift? We won't spoil the surprise.",
                "You were never here, and neither were we.",
                "Don't worry, we won't tell your autofill.",
                "Your private detour begins now.",
                "Go ahead, ask the weird questions."
            )
        }
        val randomSubtitle = remember { incognitoSubtitles.random() }

        // Breathing & ambient animation for the stealth emblem
        val infiniteTransition = rememberInfiniteTransition(label = "IncognitoEmblemAnim")
        val emblemScale by infiniteTransition.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "emblemScale"
        )
        val emblemGlowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "emblemGlowAlpha"
        )

        val flowerShape = remember { PetalMaterialShapes.Flower.toShape() }
        val scallopShape = remember { PetalMaterialShapes.Cookie12Sided.toShape() }
        val cloverShape = remember { PetalMaterialShapes.Clover4Leaf.toShape() }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Subtle ambient dynamic M3 expressive background blobs
            M3ExpressiveVariableBackground(
                modifier = Modifier.fillMaxSize(),
                pageSeed = "incognito_stealth_page"
            )

            Column(modifier = Modifier.fillMaxSize()) {
                com.petal.browser.ui.components.ExpressiveHeader(
                    title = "Incognito Mode",
                    subtitle = randomSubtitle,
                    maxTitleLines = 1,
                    maxSubtitleLines = 3,
                    onBack = null,
                    actions = {
                        IconButton(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.75f)
                                onCloseIncognito()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close all Incognito tabs",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // --- 1. M3 Expressive Morphing Stealth Hero Emblem ---
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(112.dp)
                            .entrance(index = 0)
                    ) {
                        // Ambient outer glow pulsing in background
                        Box(
                            modifier = Modifier
                                .size(108.dp)
                                .graphicsLayer {
                                    scaleX = emblemScale * 1.05f
                                    scaleY = emblemScale * 1.05f
                                    alpha = emblemGlowAlpha
                                }
                                .clip(cloverShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Secondary scallop container
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .graphicsLayer {
                                    scaleX = emblemScale
                                    scaleY = emblemScale
                                }
                                .clip(scallopShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )

                        // Core flower container holding stealth badge
                        Surface(
                            shape = flowerShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 6.dp,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .size(80.dp)
                                .graphicsLayer {
                                    scaleX = emblemScale
                                    scaleY = emblemScale
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Rounded.VisibilityOff,
                                    contentDescription = "Incognito Mode",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // Hero Headline
                    Text(
                        text = "You've gone Incognito",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.entrance(index = 1)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Hero Description
                    Text(
                        text = "Browse completely off the record. Activity and downloads remain isolated from regular sessions and vanish when closed.",
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .entrance(index = 2)
                    )

                    Spacer(Modifier.height(24.dp))

                    // --- 2. Material 3 Expressive Search Decoy Bar with Integrated Voice & Lens Actions ---
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .entrance(index = 3)
                            .clip(RoundedCornerShape(28.dp))
                            .bouncyClickable(scaleDown = 0.97f) {
                                onSearchClick()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Search or type URL",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )

                            // Quick Google Lens scanner shortcut
                            IconButton(
                                onClick = {
                                    if (activity != null) {
                                        PetalLensBridge.showLensBottomSheet(activity)
                                    } else {
                                        onSearchClick()
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CenterFocusWeak,
                                    contentDescription = "Visual Search",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Quick Voice Search shortcut
                            IconButton(
                                onClick = {
                                    if (activity != null) {
                                        PetalVoiceSearchBridge.showVoiceSearchSheet(activity) { query ->
                                            if (query.isNotBlank()) {
                                                onSearchClick()
                                            }
                                        }
                                    } else {
                                        onSearchClick()
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Voice Search",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // --- 3. Material 3 Expressive Quick Action Shortcuts Island ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .entrance(index = 4),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IncognitoQuickActionButton(
                            icon = Icons.Rounded.AutoAwesome,
                            label = "AI Search",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (activity != null) {
                                    PetalAiSearchBridge.showAiSearchResult(activity, "")
                                } else {
                                    onSearchClick()
                                }
                            }
                        )

                        IncognitoQuickActionButton(
                            icon = Icons.Rounded.CloseFullscreen,
                            label = "Exit Private",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.8f)
                                onCloseIncognito()
                            }
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // --- 4. Privacy Guarantee Cards (What Petal Won't Save) ---
                    ExpressivePrivacyAssuranceCard(
                        title = "Petal won't save:",
                        icon = Icons.Rounded.Shield,
                        badgeColor = MaterialTheme.colorScheme.primary,
                        items = listOf(
                            PrivacyItem(Icons.Rounded.History, "Browsing history and search suggestions"),
                            PrivacyItem(Icons.Rounded.Cookie, "Cookies and site data (cleared on tab close)"),
                            PrivacyItem(Icons.Rounded.EditNote, "Credentials and information entered into forms")
                        ),
                        modifier = Modifier.entrance(index = 5)
                    )

                    Spacer(Modifier.height(14.dp))

                    // --- 5. Network Entity Transparency Card (What might be visible) ---
                    ExpressivePrivacyAssuranceCard(
                        title = "Your activity might still be visible to:",
                        icon = Icons.Rounded.Info,
                        badgeColor = MaterialTheme.colorScheme.tertiary,
                        items = listOf(
                            PrivacyItem(Icons.Rounded.Language, "Websites you visit and sign into"),
                            PrivacyItem(Icons.Rounded.Domain, "Your employer, school, or network administrator"),
                            PrivacyItem(Icons.Rounded.Router, "Your internet service provider (ISP)")
                        ),
                        modifier = Modifier.entrance(index = 6)
                    )

                    Spacer(Modifier.height(18.dp))

                    // --- 6. Expressive Third-Party Cookie Blocking Toggle Card ---
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .entrance(index = 7)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable {
                                blockThirdPartyCookies = !blockThirdPartyCookies
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Cookie,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Block third-party cookies",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "When active, cross-site trackers and advertisers cannot monitor you across the web.",
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = blockThirdPartyCookies,
                                onCheckedChange = {
                                    blockThirdPartyCookies = it
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                },
                                thumbContent = {
                                    AnimatedContent(
                                        targetState = blockThirdPartyCookies,
                                        transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                                        label = "switch_thumb"
                                    ) { isChecked ->
                                        Icon(
                                            imageVector = if (isChecked) Icons.Rounded.Check else Icons.Rounded.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize)
                                        )
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedIconColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

private data class PrivacyItem(
    val icon: ImageVector,
    val text: String
)

@Composable
private fun IncognitoQuickActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        tonalElevation = 2.dp,
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .bouncyClickable(scaleDown = 0.94f) { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExpressivePrivacyAssuranceCard(
    title: String,
    icon: ImageVector,
    badgeColor: Color,
    items: List<PrivacyItem>,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(14.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

object PetalIncognitoBridge {
    @JvmStatic
    fun createIncognitoHomeView(
        activity: ComponentActivity,
        onSearchClick: Runnable,
        onCloseIncognito: Runnable
    ): ComposeView {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }
                DisposableEffect(Unit) {
                    onDispose {
                        com.petal.browser.predictive.PetalContentSnapshot.clear()
                    }
                }
                PetalIncognitoHomeScreen(
                    backgroundSnapshot = snapshotBitmap,
                    onSearchClick = { onSearchClick.run() },
                    onCloseIncognito = { onCloseIncognito.run() }
                )
            }
        }
    }
}

@Preview(name = "Incognito Home Screen Preview", showBackground = true)
@Composable
private fun PetalIncognitoHomeScreenPreview() {
    PetalIncognitoHomeScreen()
}

