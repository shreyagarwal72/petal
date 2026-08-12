/*
 * PetalOverflowMenuSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Options Menu Sheet positioned at the bottom right,
 * expanding outwards from the 3-dots bottom navigation icon with spring scale
 * animation, 25% background dimming, and zero blur on the menu container itself.
 */

package com.petal.browser.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.ui.theme.PetalExpressiveTheme

interface PetalOverflowMenuActionHandler {
    fun onGoBack()
    fun onGoForward()
    fun onToggleBookmark()
    fun onOpenDownloadsShortcut()
    fun onOpenPageInfo()
    fun onReload()
    fun onToggleDesktopSite(enabled: Boolean)
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
}

object PetalOverflowBridge {
    @JvmStatic
    fun showOverflowMenu(
        activity: ComponentActivity,
        title: String,
        url: String,
        isBookmarked: Boolean,
        canGoBack: Boolean,
        canGoForward: Boolean,
        isDesktopSite: Boolean,
        handler: PetalOverflowMenuActionHandler
    ) {
        try {
            val dialog = BottomSheetDialog(activity)
            dialog.setOnShowListener {
                val bottomSheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
                if (bottomSheet != null) {
                    val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
                    behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                    bottomSheet.background = null
                }
            }
            dialog.window?.let { window ->
                window.setDimAmount(0.25f) // 25% background backdrop dimming/blur
                window.setBackgroundDrawableResource(android.R.color.transparent)
                window.setWindowAnimations(com.google.android.material.R.style.Animation_Design_BottomSheetDialog)
            }

            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                    val fontName = sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", "tide") ?: "tide"
                    val isAmoled = sp.getBoolean("sp_amoled", false)
                    val dynamicColor = sp.getBoolean("useDynamicColor", false)

                    val appFont = remember(fontName) {
                        try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.SYSTEM }
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
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenDownloadsShortcut: () -> Unit,
    onOpenPageInfo: () -> Unit,
    onReload: () -> Unit,
    onToggleDesktopSite: (Boolean) -> Unit,
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
    onOpenSettings: () -> Unit
) {
    var isMoreToolsExpanded by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "MenuExpandScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "MenuExpandAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .widthIn(max = 350.dp)
                .padding(end = 12.dp, bottom = 12.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(1f, 1f)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Drag Handle Indicator
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        .align(Alignment.CenterHorizontally)
                )

                // Header Page Info Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (pageUrl.startsWith("https://")) Icons.Rounded.Lock else Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (pageTitle.isBlank()) "New Tab" else pageTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (pageUrl.isBlank()) "about:blank" else pageUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Top Icon Row: Back → Star/Bookmark → Download Site → Refresh
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularIconButton(
                        icon = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        enabled = canGoBack,
                        onClick = onGoBack
                    )
                    CircularIconButton(
                        icon = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Toggle Bookmark",
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        onClick = onToggleBookmark
                    )
                    CircularIconButton(
                        icon = Icons.Rounded.DownloadForOffline,
                        contentDescription = "Download Site",
                        onClick = onSavePage
                    )
                    CircularIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Reload",
                        onClick = onReload
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Section 1: New Tab, New Private Tab, Desktop Site, Install as App
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    MenuRowSwitchItem(
                        icon = Icons.Rounded.DesktopWindows,
                        title = "Desktop site",
                        subtitle = "Request desktop version of websites",
                        checked = isDesktopSite,
                        onCheckedChange = onToggleDesktopSite
                    )
                    MenuRowItem(
                        icon = Icons.Rounded.AppShortcut,
                        title = "Install as app",
                        subtitle = "Add Web App shortcut to Home screen",
                        onClick = onInstallPwa
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Section 2: History & Delete browsing data
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Section 3: Downloads, Bookmarks
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Section 4: Expandable More tools, View source, Settings
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        Column(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            MenuRowItem(
                                icon = Icons.Rounded.Search,
                                title = "Search on site",
                                onClick = onSearchOnSite
                            )
                            MenuRowItem(
                                icon = Icons.Rounded.Print,
                                title = "Print page to PDF",
                                onClick = onPrintPdf
                            )
                            MenuRowItem(
                                icon = Icons.Rounded.SaveAlt,
                                title = "Save page",
                                onClick = onSavePage
                            )
                            MenuRowItem(
                                icon = Icons.Rounded.Share,
                                title = "Share link",
                                onClick = onShareLink
                            )
                        }
                    }

                    MenuRowItem(
                        icon = Icons.Rounded.Code,
                        title = "View source",
                        onClick = onViewSource
                    )
                    MenuRowItem(
                        icon = Icons.Rounded.Settings,
                        title = "Settings",
                        onClick = onOpenSettings
                    )
                }

                Spacer(Modifier.height(16.dp))
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
            .size(48.dp)
            .bouncyClickable(scaleDown = 0.84f, enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun MenuRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(scaleDown = 0.96f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
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
            }

            if (trailingIcon != null) {
                Icon(
                    trailingIcon,
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
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
            }

            IconSwitch(
                checked = checked,
                icon = icon,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
