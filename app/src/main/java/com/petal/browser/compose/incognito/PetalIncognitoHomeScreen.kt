package com.petal.browser.compose.incognito

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.lens.PetalLensBridge
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalAiSearchBridge
import com.petal.browser.ui.components.PetalVoiceSearchBridge
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.theme.PetalIncognitoTheme
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.ui.theme.toShape

@OptIn(ExperimentalMaterial3Api::class)
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

    var blockThirdPartyCookies by remember {
        mutableStateOf(sp.getBoolean("sp_incognito_block_3p_cookies", true))
    }
    var showLearnMoreDialog by remember { mutableStateOf(false) }

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
                "No history, no suggestions, just pure browsing."
            )
        }
        val randomSubtitle = remember { incognitoSubtitles.random() }

        // Subtle pulsing ambient animation for the hero avatar
        val infiniteTransition = rememberInfiniteTransition(label = "IncognitoHeroPulse")
        val avatarScale by infiniteTransition.animateFloat(
            initialValue = 0.985f,
            targetValue = 1.015f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "avatarScale"
        )
        val auraAlpha by infiniteTransition.animateFloat(
            initialValue = 0.20f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "auraAlpha"
        )

        val cookieShape = remember { PetalMaterialShapes.Cookie12Sided.toShape() }

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
                // Persistent header matching the home/browser system header
                ExpressiveHeader(
                    title = "Incognito",
                    subtitle = randomSubtitle,
                    maxTitleLines = 1,
                    maxSubtitleLines = 2,
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

                // Scrollable main content column
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 600.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(28.dp))

                        // ── 1. Chrome-Inspired Expressive Stealth Hero Avatar ──
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(96.dp)
                                .entrance(index = 0)
                        ) {
                            // Pulsing ambient aura
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .graphicsLayer {
                                        scaleX = avatarScale * 1.08f
                                        scaleY = avatarScale * 1.08f
                                        alpha = auraAlpha
                                    }
                                    .clip(cookieShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            // Expressive circular avatar badge
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                tonalElevation = 6.dp,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .size(80.dp)
                                    .graphicsLayer {
                                        scaleX = avatarScale
                                        scaleY = avatarScale
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.icon_incognito),
                                        contentDescription = "Incognito Fedora and Glasses",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // ── 2. Hero Headline ──
                        Text(
                            text = "You've gone Incognito",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.entrance(index = 1)
                        )

                        Spacer(Modifier.height(10.dp))

                        // ── 3. Chrome-Exact Primary Narrative ──
                        Text(
                            text = "Now you can browse privately, and other people who use this device won't see your activity. However, downloads, bookmarks and reading list items will be saved.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 21.sp,
                                letterSpacing = 0.1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .entrance(index = 2)
                        )

                        Spacer(Modifier.height(20.dp))

                        // ── 4. Decoy Omnibox Search Bar ──
                        IncognitoDecoySearchBar(
                            onSearch = onSearchClick,
                            activity = activity,
                            modifier = Modifier.entrance(index = 3)
                        )

                        Spacer(Modifier.height(24.dp))

                        // ── 5. Privacy Information Sections ──
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .entrance(index = 4),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Section A: Petal won't save
                            PrivacySectionGroup(
                                header = "Petal won't save the following information:",
                                items = listOf(
                                    "Your browsing history",
                                    "Cookies and site data",
                                    "Information entered in forms"
                                )
                            )

                            // Section B: Activity might still be visible
                            PrivacySectionGroup(
                                header = "Your activity might still be visible to:",
                                items = listOf(
                                    "Websites you visit",
                                    "Your employer or school",
                                    "Your internet service provider"
                                )
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // "Learn more" action link
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .entrance(index = 5),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            TextButton(
                                onClick = {
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                    showLearnMoreDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Learn more",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        // ── 6. Third-Party Cookies Expressive Control Card ──
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .entrance(index = 6)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    val next = !blockThirdPartyCookies
                                    blockThirdPartyCookies = next
                                    sp.edit().putBoolean("sp_incognito_block_3p_cookies", next).apply()
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Block third-party cookies",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "When on, sites can't use cookies that track you across the web. Features on some sites may break.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            lineHeight = 17.sp,
                                            letterSpacing = 0.1.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                Switch(
                                    checked = blockThirdPartyCookies,
                                    onCheckedChange = { checked ->
                                        blockThirdPartyCookies = checked
                                        sp.edit().putBoolean("sp_incognito_block_3p_cookies", checked).apply()
                                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                    },
                                    thumbContent = {
                                        AnimatedContent(
                                            targetState = blockThirdPartyCookies,
                                            transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                                            label = "incognito_cookie_switch_thumb"
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
                                        checkedIconColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                )
                            }
                        }

                        // Bottom navigation bar clearance spacer matching regular home
                        Spacer(Modifier.height(96.dp))
                    }
                }
            }

            // ── Learn More Expressive Dialog ──
            if (showLearnMoreDialog) {
                AlertDialog(
                    onDismissRequest = { showLearnMoreDialog = false },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.icon_incognito),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "About Incognito Browsing",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Petal won't remember your browsing history, cookies, site data, or form inputs once you close your private tabs.",
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Text(
                                text = "Important Safeguards:",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            BulletItem("Files you download and bookmarks you create will be kept.")
                            BulletItem("Your IP address and traffic remain visible to visited servers and your network provider.")
                            BulletItem("If you sign into websites while Incognito, those sites can recognize your session.")
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                showLearnMoreDialog = false
                            }
                        ) {
                            Text("Got it", fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun IncognitoDecoySearchBar(
    onSearch: () -> Unit,
    activity: ComponentActivity?,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "incognito_search_scale"
    )

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(32.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current
            ) { onSearch() }
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
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Search or type web address",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Visual search shortcut
            IconButton(
                onClick = {
                    if (activity != null) {
                        PetalLensBridge.showLensBottomSheet(activity)
                    } else {
                        onSearch()
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CenterFocusWeak,
                    contentDescription = "Visual Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Voice search shortcut
            IconButton(
                onClick = {
                    if (activity != null) {
                        PetalVoiceSearchBridge.showVoiceSearchSheet(activity) { query ->
                            if (query.isNotBlank()) {
                                onSearch()
                            }
                        }
                    } else {
                        onSearch()
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Voice Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun PrivacySectionGroup(
    header: String,
    items: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = header,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                BulletItem(text = item)
            }
        }
    }
}

@Composable
private fun BulletItem(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
