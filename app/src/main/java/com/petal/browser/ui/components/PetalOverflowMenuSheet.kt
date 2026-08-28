/*
 * PetalOverflowMenuSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Chrome for Android style compact floating options menu panel anchored to the top-right,
 * with continuous panel layout, circular header actions, fixed-width icon column alignment,
 * 1px inset dividers between logical groups, and dimmed backdrop overlay.
 */

package com.petal.browser.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.PetalExpressiveTheme

interface PetalOverflowMenuActionHandler {
    fun onGoBack()
    fun onGoForward()
    fun onToggleBookmark()
    fun onOpenDownloadsShortcut()
    fun onOpenPageInfo()
    fun onReload()
    fun onToggleDesktopSite(enabled: Boolean)
    fun onToggleAdBlock(enabled: Boolean)
    fun onNewTab()
    fun onNewIncognitoTab()
    fun onOpenHistory()
    fun onDeleteBrowsingData()
    fun onOpenDownloads()
    fun onOpenBookmarks()
    fun onInstallPwa()
    fun onSearchOnSite()
    fun onPrintPdf()
    fun onSavePage()
    fun onShareLink()
    fun onViewSource()
    fun onOpenSettings()
    fun onTriggerMediaMode() {}
    fun onOpenPetalAi() {}
}

object PetalOverflowBridge {
    @JvmStatic
    @JvmOverloads
    fun showOverflowMenu(
        activity: ComponentActivity,
        title: String,
        url: String,
        isBookmarked: Boolean,
        canGoBack: Boolean,
        canGoForward: Boolean,
        isDesktopSite: Boolean,
        isAdBlockEnabled: Boolean = true,
        isMediaPlaying: Boolean = false,
        handler: PetalOverflowMenuActionHandler
    ) {
        try {
            val dialog = android.app.Dialog(activity, com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
            dialog.window?.let { window ->
                window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                window.setGravity(android.view.Gravity.TOP or android.view.Gravity.END)
                window.setDimAmount(0.35f) // 35% background backdrop dimming
                window.setBackgroundDrawableResource(android.R.color.transparent)
            }

            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                    val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                    val isAmoled = sp.getBoolean("sp_amoled", false)
                    val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)

                    val appFont = remember(fontName) {
                        com.petal.browser.ui.theme.AppFont.fromName(fontName)
                    }
                    val colorStyle = remember(styleName) {
                        try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                    }

                    PetalExpressiveTheme(
                        dynamicColor = dynamicColor,
                        useAmoled = isAmoled,
                        appFont = appFont,
                        colorStyle = colorStyle,
                        paletteId = paletteId
                    ) {
                        PetalOverflowMenuSheet(
                            pageTitle = title,
                            pageUrl = url,
                            isBookmarked = isBookmarked,
                            canGoBack = canGoBack,
                            canGoForward = canGoForward,
                            isDesktopSite = isDesktopSite,
                            isAdBlockEnabled = isAdBlockEnabled,
                            isMediaPlaying = isMediaPlaying,
                            onDismissRequest = { dialog.dismiss() },
                            onGoBack = {
                                dialog.dismiss()
                                handler.onGoBack()
                            },
                            onGoForward = {
                                dialog.dismiss()
                                handler.onGoForward()
                            },
                            onToggleBookmark = {
                                dialog.dismiss()
                                handler.onToggleBookmark()
                            },
                            onOpenDownloadsShortcut = {
                                dialog.dismiss()
                                handler.onOpenDownloadsShortcut()
                            },
                            onOpenPageInfo = {
                                dialog.dismiss()
                                handler.onOpenPageInfo()
                            },
                            onReload = {
                                dialog.dismiss()
                                handler.onReload()
                            },
                            onToggleDesktopSite = { enabled ->
                                dialog.dismiss()
                                handler.onToggleDesktopSite(enabled)
                            },
                            onToggleAdBlock = { enabled ->
                                dialog.dismiss()
                                handler.onToggleAdBlock(enabled)
                            },
                            onNewTab = {
                                dialog.dismiss()
                                handler.onNewTab()
                            },
                            onNewIncognitoTab = {
                                dialog.dismiss()
                                handler.onNewIncognitoTab()
                            },
                            onOpenHistory = {
                                dialog.dismiss()
                                handler.onOpenHistory()
                            },
                            onDeleteBrowsingData = {
                                dialog.dismiss()
                                handler.onDeleteBrowsingData()
                            },
                            onOpenDownloads = {
                                dialog.dismiss()
                                handler.onOpenDownloads()
                            },
                            onOpenBookmarks = {
                                dialog.dismiss()
                                handler.onOpenBookmarks()
                            },
                            onInstallPwa = {
                                dialog.dismiss()
                                handler.onInstallPwa()
                            },
                            onSearchOnSite = {
                                dialog.dismiss()
                                handler.onSearchOnSite()
                            },
                            onPrintPdf = {
                                dialog.dismiss()
                                handler.onPrintPdf()
                            },
                            onSavePage = {
                                dialog.dismiss()
                                handler.onSavePage()
                            },
                            onShareLink = {
                                dialog.dismiss()
                                handler.onShareLink()
                            },
                            onViewSource = {
                                dialog.dismiss()
                                handler.onViewSource()
                            },
                            onOpenSettings = {
                                dialog.dismiss()
                                handler.onOpenSettings()
                            },
                            onTriggerMediaMode = {
                                dialog.dismiss()
                                handler.onTriggerMediaMode()
                            },
                            onOpenPetalAi = {
                                dialog.dismiss()
                                handler.onOpenPetalAi()
                            }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun PetalOverflowMenuSheet(
    pageTitle: String,
    pageUrl: String,
    isBookmarked: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isDesktopSite: Boolean,
    isAdBlockEnabled: Boolean = true,
    isMediaPlaying: Boolean = false,
    onDismissRequest: () -> Unit = {},
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenDownloadsShortcut: () -> Unit,
    onOpenPageInfo: () -> Unit,
    onReload: () -> Unit,
    onToggleDesktopSite: (Boolean) -> Unit,
    onToggleAdBlock: (Boolean) -> Unit = {},
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onOpenHistory: () -> Unit,
    onDeleteBrowsingData: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onInstallPwa: () -> Unit,
    onSearchOnSite: () -> Unit,
    onPrintPdf: () -> Unit,
    onSavePage: () -> Unit,
    onShareLink: () -> Unit,
    onViewSource: () -> Unit,
    onOpenSettings: () -> Unit,
    onTriggerMediaMode: () -> Unit = {},
    onOpenPetalAi: () -> Unit = {}
) {
    var isMoreToolsExpanded by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { isVisible = true }

    val handleDismiss = {
        if (!isDismissing) {
            isDismissing = true
            isVisible = false
        }
    }

    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            kotlinx.coroutines.delay(200)
            onDismissRequest()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "MenuExpandScale"
    )

    val translationY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 120f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "MenuSlideUp"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "MenuExpandAlpha"
    )

    // Full screen outer box with dim backdrop overlay over rest of screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = handleDismiss
            )
            .padding(top = 52.dp, end = 12.dp, start = 12.dp, bottom = 72.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        // Compact floating panel anchored to bottom-right expanding out of bottom nav bar
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 350.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume clicks inside panel
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.translationY = translationY
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(1f, 1f)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {


                // Top Header Circular Icon Action Buttons Row (evenly spaced)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .entrance(index = 0),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularIconButton(
                        icon = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        enabled = canGoBack,
                        onClick = onGoBack
                    )
                    val isHomePageUrl = pageUrl.isBlank() || pageUrl == "about:blank" || pageUrl.startsWith("file:///android_asset/")
                    CircularIconButton(
                        icon = if (isBookmarked && !isHomePageUrl) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Toggle Bookmark",
                        enabled = !isHomePageUrl,
                        tint = if (isBookmarked && !isHomePageUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        onClick = onToggleBookmark
                    )
                    CircularIconButton(
                        icon = Icons.Rounded.OfflinePin,
                        contentDescription = "Install site offline",
                        enabled = !isHomePageUrl,
                        onClick = onInstallPwa
                    )
                    CircularIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Reload",
                        onClick = onReload
                    )
                }

                // Thin 1px horizontal divider line spanning row width minus panel horizontal padding
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                // Section 1: Tab actions
                MenuRowItem(
                    icon = Icons.Rounded.Add,
                    title = "New tab",
                    onClick = onNewTab
                )
                MenuRowItem(
                    icon = Icons.Rounded.VisibilityOff,
                    title = "New Private / Incognito tab",
                    subtitle = "Browse without saving search history",
                    onClick = onNewIncognitoTab
                )

                // Thin 1px horizontal divider line
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                val isHomePage = remember(pageUrl, pageTitle) {
                    pageUrl.isBlank() || pageUrl == "about:blank" || pageUrl.startsWith("petal://") || pageUrl == "file:///android_asset/petal_home.html" || pageTitle == "Petal"
                }

                // Section 2: Quick Toggles (AdBlocker & Desktop site)
                MenuRowSwitchItem(
                    icon = Icons.Rounded.Shield,
                    title = "AdBlocker",
                    subtitle = if (isAdBlockEnabled) "Ad & tracker shield active" else "AdBlocker disabled",
                    checked = isAdBlockEnabled,
                    onCheckedChange = onToggleAdBlock
                )
                if (isMediaPlaying) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
                    val autoPip = sp.getBoolean("sp_auto_pip", true)
                    val backgroundPlay = sp.getBoolean("sp_background_play", false)
                    val isPipSupported = remember {
                        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
                    }

                    if (autoPip && isPipSupported) {
                        MenuRowItem(
                            icon = Icons.Rounded.PictureInPicture,
                            title = "Play in Picture-in-Picture",
                            subtitle = "Open floating video window (per PiP settings)",
                            onClick = onTriggerMediaMode
                        )
                    } else if (backgroundPlay) {
                        MenuRowItem(
                            icon = Icons.Rounded.PlayCircle,
                            title = "Play in Background",
                            subtitle = "Continue video/audio when switching apps (per settings)",
                            onClick = onTriggerMediaMode
                        )
                    } else {
                        MenuRowItem(
                            icon = Icons.Rounded.PictureInPicture,
                            title = "Play Video in Window",
                            subtitle = "Open video playback mode",
                            onClick = onTriggerMediaMode
                        )
                    }
                }

                if (!isHomePage) {
                    MenuRowSwitchItem(
                        icon = Icons.Rounded.DesktopWindows,
                        title = "Desktop site",
                        subtitle = "Request desktop version of websites",
                        checked = isDesktopSite,
                        onCheckedChange = onToggleDesktopSite
                    )
                    MenuRowItem(
                        icon = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        title = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                        subtitle = if (isBookmarked) "Remove current page from bookmarks" else "Save page to your bookmarks",
                        onClick = onToggleBookmark
                    )
                    MenuRowItem(
                        icon = Icons.Rounded.OfflinePin,
                        title = "Install site offline",
                        subtitle = "Save website to view completely offline",
                        onClick = onInstallPwa
                    )

                    // Thin 1px horizontal divider line
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }

                // Section 3: History & Delete browsing data
                MenuRowItem(
                    icon = Icons.Rounded.History,
                    title = "History",
                    onClick = onOpenHistory
                )
                MenuRowItem(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Delete browsing data",
                    onClick = onDeleteBrowsingData
                )

                // Thin 1px horizontal divider line
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                // Section 4: Downloads & Bookmarks
                MenuRowItem(
                    icon = Icons.Rounded.Download,
                    title = "Downloads",
                    onClick = onOpenDownloads
                )
                MenuRowItem(
                    icon = Icons.Rounded.Bookmark,
                    title = "Bookmarks",
                    onClick = onOpenBookmarks
                )

                // Thin 1px horizontal divider line
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                // Section 5: Tools & Settings
                MenuRowItem(
                    icon = Icons.Rounded.Build,
                    title = "More tools",
                    trailingIcon = if (isMoreToolsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    onClick = { isMoreToolsExpanded = !isMoreToolsExpanded }
                )

                AnimatedVisibility(
                    visible = isMoreToolsExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        MenuRowItem(
                            icon = Icons.Rounded.Search,
                            title = "Search on site",
                            isSubItem = true,
                            onClick = onSearchOnSite
                        )
                        MenuRowItem(
                            icon = Icons.Rounded.Print,
                            title = "Print page to PDF",
                            isSubItem = true,
                            onClick = onPrintPdf
                        )
                        MenuRowItem(
                            icon = Icons.Rounded.SaveAlt,
                            title = "Save page",
                            isSubItem = true,
                            onClick = onSavePage
                        )
                        MenuRowItem(
                            icon = Icons.Rounded.Share,
                            title = "Share link",
                            isSubItem = true,
                            onClick = onShareLink
                        )
                    }
                }

                MenuRowItem(
                    icon = Icons.Rounded.Settings,
                    title = "Settings",
                    onClick = onOpenSettings
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CircularIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .size(44.dp)
            .bouncyClickable(scaleDown = 0.84f, enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MenuRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingIcon: ImageVector? = null,
    isSubItem: Boolean = false,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {
                com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                    .playIfEnabled(context, com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.75f)
                onClick()
            })
            .padding(
                start = if (isSubItem) 28.dp else 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 10.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fixed-width icon column so all icons align vertically across rows
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MenuRowSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fixed-width icon column so all icons align vertically across rows
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconSwitch(
                checked = checked,
                icon = icon,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
