package com.petal.browser.compose.incognito

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.lens.PetalLensBridge
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalVoiceSearchBridge
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.containment.*
import com.petal.browser.ui.glass.LocalPetalGlassWallpaper
import com.petal.browser.ui.glass.rememberPetalGlassWallpaper
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.theme.PetalIncognitoTheme
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.ui.theme.toShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalIncognitoHomeScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    tabCount: Int = 0,
    onSearchClick: () -> Unit = {},
    onCloseIncognito: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
    var blockThirdPartyCookies by remember {
        mutableStateOf(sp.getBoolean("sp_incognito_block_3p_cookies", true))
    }
    var biometricLockEnabled by remember {
        mutableStateOf(sp.getBoolean("sp_biometric_incognito_tab_lock", false))
    }
    var httpsOnlyEnabled by remember {
        mutableStateOf(sp.getBoolean("sp_https_only", false))
    }

    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                "sp_incognito_block_3p_cookies" -> blockThirdPartyCookies = sp.getBoolean("sp_incognito_block_3p_cookies", true)
                "sp_biometric_incognito_tab_lock" -> biometricLockEnabled = sp.getBoolean("sp_biometric_incognito_tab_lock", false)
                "sp_https_only" -> httpsOnlyEnabled = sp.getBoolean("sp_https_only", false)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    var showLearnMoreDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = showLearnMoreDialog) {
        showLearnMoreDialog = false
    }

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val glassWallpaper = com.petal.browser.ui.glass.LocalPetalGlassWallpaper.current
        ?: rememberPetalGlassWallpaper()

    val liveIncognitoCount = remember(tabCount) {
        if (tabCount > 0) tabCount else PetalIncognitoSessionManager.getIncognitoCount(context)
    }

    PetalIncognitoTheme(useAmoled = isAmoled) {
        CompositionLocalProvider(LocalPetalGlassWallpaper provides glassWallpaper) {
            val animActive = com.petal.browser.ui.components.rememberAnimationActive()
            val infiniteTransition = rememberInfiniteTransition(label = "FirefoxMaskPulse")
            val rawAvatarScale by infiniteTransition.animateFloat(
                initialValue = 0.985f,
                targetValue = 1.022f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3200, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "avatarScale"
            )
            val avatarScale = if (animActive) rawAvatarScale else 1f
            val cookieShape = remember { PetalMaterialShapes.Cookie12Sided.toShape() }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Background morphing shapes & wallpaper recorder (sibling to content)
                M3ExpressiveVariableBackground(
                    modifier = Modifier.fillMaxSize(),
                    pageSeed = "incognito_firefox_stealth"
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    ExpressiveHeader(
                        title = stringResource(R.string.private_browsing_title),
                        subtitle = if (liveIncognitoCount > 1) {
                            "$liveIncognitoCount isolated tabs open • Zero traces"
                        } else {
                            "Isolated session • Zero traces"
                        },
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
                                    contentDescription = stringResource(R.string.private_browsing_close_all),
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
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = 640.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(18.dp))

                            // ── 1. Hero Firefox Private Browsing Mask Badge with M3 Cookie Shape ──
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(112.dp)
                                    .entrance(index = 0)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    tonalElevation = if (isGlass) 0.dp else 6.dp,
                                    shadowElevation = if (isGlass) 0.dp else 4.dp,
                                    modifier = Modifier
                                        .size(92.dp)
                                        .clip(cookieShape)
                                        .graphicsLayer {
                                            scaleX = avatarScale
                                            scaleY = avatarScale
                                        }
                                        .then(
                                            if (isGlass) Modifier.liquidGlassChrome(
                                                shape = cookieShape,
                                                enabled = true,
                                                sheenIntensity = 0.85f,
                                                tintColor = MaterialTheme.colorScheme.primary
                                            ) else Modifier
                                        )
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.icon_firefox_mask),
                                            contentDescription = stringResource(R.string.private_browsing_title),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(50.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // ── 2. Hero Headline & Subtitle ──
                            Text(
                                text = stringResource(R.string.private_browsing_title),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.entrance(index = 1)
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.private_browsing_subtitle),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 21.sp,
                                    letterSpacing = 0.1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .entrance(index = 2)
                            )

                            Spacer(Modifier.height(24.dp))

                            // ── 3. Stealth Decoy Search Bar in Pill Liquid Glass ──
                            IncognitoDecoySearchBar(
                                onSearch = onSearchClick,
                                activity = activity,
                                isGlass = isGlass,
                                modifier = Modifier.entrance(index = 3)
                            )

                            Spacer(Modifier.height(24.dp))

                            // ── 4. Containment Status Section: Firefox Tracking Protection Hero Card ──
                            PetalStatusHeroCard(
                                title = stringResource(R.string.private_browsing_tracking_protection),
                                subtitle = stringResource(R.string.private_browsing_tracking_protection_desc),
                                statusText = stringResource(R.string.private_browsing_protection_active),
                                icon = Icons.Rounded.Shield,
                                statusActive = true,
                                actionLabel = stringResource(R.string.ui_learn_more),
                                onActionClick = {
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                    showLearnMoreDialog = true
                                },
                                modifier = Modifier.entrance(index = 4)
                            )

                            Spacer(Modifier.height(20.dp))

                            // ── 5. Containment Settings Section: M3 Expressive Privacy Controls ──
                            PetalSettingsSection(
                                title = stringResource(R.string.ui_security_privacy),
                                icon = Icons.Rounded.Security,
                                modifier = Modifier.entrance(index = 5)
                            ) {
                                // Row 1 (TOP): 3rd-Party Cookie & Tracker Shield
                                PetalGroupListRow(
                                    position = PetalGroupPosition.TOP,
                                    onClick = {
                                        val nextVal = !blockThirdPartyCookies
                                        blockThirdPartyCookies = nextVal
                                        sp.edit().putBoolean("sp_incognito_block_3p_cookies", nextVal).apply()
                                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                    },
                                    leading = {
                                        PetalGroupIconBadge(
                                            shape = RoundedCornerShape(14.dp),
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Cookie,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    content = {
                                        Text(
                                            text = stringResource(R.string.ui_block_third_party_cookies),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(R.string.ui_when_on_sites_can_t),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailing = {
                                        IconSwitch(
                                            checked = blockThirdPartyCookies,
                                            icon = Icons.Rounded.Cookie,
                                            onCheckedChange = { checked ->
                                                blockThirdPartyCookies = checked
                                                sp.edit().putBoolean("sp_incognito_block_3p_cookies", checked).apply()
                                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                            }
                                        )
                                    }
                                )

                                // Row 2 (MIDDLE): Biometric Incognito Tab Lock
                                PetalGroupListRow(
                                    position = PetalGroupPosition.MIDDLE,
                                    onClick = {
                                        val nextVal = !biometricLockEnabled
                                        biometricLockEnabled = nextVal
                                        sp.edit().putBoolean("sp_biometric_incognito_tab_lock", nextVal).apply()
                                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                    },
                                    leading = {
                                        PetalGroupIconBadge(
                                            shape = RoundedCornerShape(14.dp),
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Fingerprint,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    content = {
                                        Text(
                                            text = "Biometric Tab Lock",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Require fingerprint or unlock to view private tabs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailing = {
                                        IconSwitch(
                                            checked = biometricLockEnabled,
                                            icon = Icons.Rounded.Fingerprint,
                                            onCheckedChange = { checked ->
                                                biometricLockEnabled = checked
                                                sp.edit().putBoolean("sp_biometric_incognito_tab_lock", checked).apply()
                                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                            }
                                        )
                                    }
                                )

                                // Row 3 (BOTTOM): HTTPS-Only Mode
                                PetalGroupListRow(
                                    position = PetalGroupPosition.BOTTOM,
                                    onClick = {
                                        val nextVal = !httpsOnlyEnabled
                                        httpsOnlyEnabled = nextVal
                                        sp.edit().putBoolean("sp_https_only", nextVal).apply()
                                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                    },
                                    leading = {
                                        PetalGroupIconBadge(
                                            shape = RoundedCornerShape(14.dp),
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Https,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    content = {
                                        Text(
                                            text = stringResource(R.string.ui_https_only_mode),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Automatically upgrade connections to secure HTTPS",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailing = {
                                        IconSwitch(
                                            checked = httpsOnlyEnabled,
                                            icon = Icons.Rounded.Https,
                                            onCheckedChange = { checked ->
                                                httpsOnlyEnabled = checked
                                                sp.edit().putBoolean("sp_https_only", checked).apply()
                                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                            }
                                        )
                                    }
                                )
                            }

                            Spacer(Modifier.height(20.dp))

                            // ── 6. Containment Section: Privacy Guarantees (Firefox Model) ──
                            PetalSettingsSection(
                                title = stringResource(R.string.private_browsing_privacy_guarantees),
                                icon = Icons.Rounded.Lock,
                                modifier = Modifier.entrance(index = 6)
                            ) {
                                // Row 1 (Top): What is never saved
                                PetalGroupListRow(
                                    position = PetalGroupPosition.TOP,
                                    onClick = {},
                                    leading = {
                                        PetalGroupIconBadge(
                                            icon = Icons.Rounded.CheckCircle,
                                            variant = PetalBadgeVariant.PRIMARY
                                        )
                                    },
                                    content = {
                                        Text(
                                            text = stringResource(R.string.private_browsing_not_saved),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "• " + stringResource(R.string.private_browsing_not_saved_history) +
                                                    "\n• " + stringResource(R.string.private_browsing_not_saved_cookies) +
                                                    "\n• " + stringResource(R.string.private_browsing_not_saved_forms) +
                                                    "\n• Screenshots & app switcher previews (FLAG_SECURE)",
                                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailing = null
                                )

                                // Row 2 (Bottom): Visible to external parties
                                PetalGroupListRow(
                                    position = PetalGroupPosition.BOTTOM,
                                    onClick = {},
                                    leading = {
                                        PetalGroupIconBadge(
                                            icon = Icons.Rounded.Visibility,
                                            variant = PetalBadgeVariant.SURFACE_TONAL
                                        )
                                    },
                                    content = {
                                        Text(
                                            text = stringResource(R.string.private_browsing_visible_to),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "• " + stringResource(R.string.private_browsing_visible_network) +
                                                    "\n• " + stringResource(R.string.private_browsing_visible_employer) +
                                                    "\n• " + stringResource(R.string.private_browsing_visible_sites),
                                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailing = null
                                )
                            }

                            Spacer(Modifier.height(20.dp))

                            // ── 7. Close All Private Tabs Action Card ──
                            PetalActionCard(
                                onClick = {
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.75f)
                                    onCloseIncognito()
                                },
                                shape = petalGroupShape(PetalGroupPosition.SINGLE),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .entrance(index = 7)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 60.dp)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PetalGroupIconBadge(
                                        icon = Icons.Rounded.DeleteSweep,
                                        variant = PetalBadgeVariant.ERROR
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.private_browsing_close_all),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(R.string.ui_automatically_purge_cache_history_and),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Bottom navigation bar clearance spacer matching regular home
                            Spacer(Modifier.height(96.dp))
                        }
                    }
                }

                // ── Learn More Expressive Containment Dialog ──
                if (showLearnMoreDialog) {
                    PetalAlertDialog(
                        onDismissRequest = { showLearnMoreDialog = false },
                        title = stringResource(R.string.private_browsing_title),
                        icon = Icons.Rounded.Security,
                        confirmText = stringResource(R.string.ui_got_it),
                        onConfirm = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                            showLearnMoreDialog = false
                        },
                        dismissText = null,
                        content = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_petal_won_t_remember_your),
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Text(
                                    text = stringResource(R.string.ui_important_safeguards),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                BulletItem("GeckoView private engine session isolates cookies, IndexedDB, and cache into an ephemeral sandbox.")
                                BulletItem("Closing private tabs immediately purges cookies, temporary cache files, and private thumbnails.")
                                BulletItem("Window security (FLAG_SECURE) locks screenshots and app switcher previews while viewing private tabs.")
                                BulletItem("Downloads and bookmarks you explicitly save are retained across sessions for your convenience.")
                                BulletItem("Visited sites, networks, and ISPs can still see your IP address and online requests.")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IncognitoDecoySearchBar(
    onSearch: () -> Unit,
    activity: ComponentActivity?,
    isGlass: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "incognito_search_scale"
    )
    val pillBounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }

    PetalFloatingToolbar(
        elevation = if (isGlass) 0.dp else 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .onGloballyPositioned { pillBounds[0] = it.boundsInWindow() }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(ExpressiveButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current
            ) {
                com.petal.browser.ui.components.PetalOmniboxOrigin.mark(pillBounds[0], -1f)
                onSearch()
            }
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.ui_search),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.ui_search_or_type_web_address),
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
                contentDescription = stringResource(R.string.ui_visual_search),
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
                contentDescription = stringResource(R.string.ui_voice_search_2),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
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
