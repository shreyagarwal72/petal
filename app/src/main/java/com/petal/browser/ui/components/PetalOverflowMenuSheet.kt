package com.petal.browser.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.extensions.PetalExtensionManager
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
    fun onShowReadingMode() {}
    fun onPrintPdf()
    fun onSavePage()
    fun onShareLink()
    fun onCopyCleanLink() {}
    fun onOpenShieldHud() {}
    fun onViewSource()
    fun onOpenSettings()
    fun onTriggerMediaMode() {}
    fun onOpenPetalAi() {}
    fun onOpenExtensions() {}
    fun onOpenExtensionAction(extensionId: String) {}
    fun onOpenExtensionSettings(extensionId: String) {}
    fun onOpenDevConsole() {}
    fun onOpenSafeLocker() {}
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
                window.setDimAmount(0.35f)
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
                            onShowReadingMode = {
                                dialog.dismiss()
                                handler.onShowReadingMode()
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
                            onCopyCleanLink = {
                                dialog.dismiss()
                                handler.onCopyCleanLink()
                            },
                            onOpenShieldHud = {
                                dialog.dismiss()
                                handler.onOpenShieldHud()
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
                            },
                            onOpenExtensions = {
                                dialog.dismiss()
                                handler.onOpenExtensions()
                            },
                            onOpenExtensionAction = { extensionId ->
                                dialog.dismiss()
                                handler.onOpenExtensionAction(extensionId)
                            },
                            onOpenExtensionSettings = { extensionId ->
                                dialog.dismiss()
                                handler.onOpenExtensionSettings(extensionId)
                            },
                            onOpenDevConsole = {
                                dialog.dismiss()
                                handler.onOpenDevConsole()
                            },
                            onOpenSafeLocker = {
                                dialog.dismiss()
                                handler.onOpenSafeLocker()
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
    onShowReadingMode: () -> Unit = {},
    onPrintPdf: () -> Unit,
    onSavePage: () -> Unit,
    onShareLink: () -> Unit,
    onCopyCleanLink: () -> Unit = {},
    onOpenShieldHud: () -> Unit = {},
    onViewSource: () -> Unit,
    onOpenSettings: () -> Unit,
    onTriggerMediaMode: () -> Unit = {},
    onOpenPetalAi: () -> Unit = {},
    onOpenExtensions: () -> Unit = {},
    onOpenExtensionAction: (String) -> Unit = {},
    onOpenExtensionSettings: (String) -> Unit = {},
    onOpenDevConsole: () -> Unit = {},
    onOpenSafeLocker: () -> Unit = {}
) {
    val context = LocalContext.current
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
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 350.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.translationY = translationY
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(1f, 1f)
                }
        ) {
            val isHomePage = remember(pageUrl, pageTitle) {
                pageUrl.isBlank() ||
                pageUrl.equals("about:blank", ignoreCase = true) ||
                pageUrl.startsWith("petal://") ||
                pageUrl.contains("petal_home.html") ||
                pageUrl.startsWith("file:///android_asset/") ||
                pageTitle.equals("Petal", ignoreCase = true) ||
                pageTitle.equals("Petal Home", ignoreCase = true) ||
                pageTitle.equals("Petal Start", ignoreCase = true) ||
                com.petal.browser.unit.BrowserUnit.isHomePage(pageUrl)
            }

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
                    CircularIconButton(
                        icon = if (isBookmarked && !isHomePage) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Toggle Bookmark",
                        enabled = !isHomePage,
                        tint = if (isBookmarked && !isHomePage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        onClick = onToggleBookmark
                    )
                    CircularIconButton(
                        icon = Icons.Rounded.OfflinePin,
                        contentDescription = "Install site offline",
                        enabled = !isHomePage,
                        onClick = onSavePage
                    )
                    CircularIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Reload",
                        onClick = onReload
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                // 4-Column Quick Action Matrix
                ActionMatrixGrid(
                    actions = listOf(
                        ActionMatrixItem(
                            icon = Icons.Rounded.DesktopWindows,
                            label = "Desktop",
                            isActive = isDesktopSite,
                            shape = com.petal.browser.ui.theme.PetalMaterialShapes.Cookie6Sided.toShape(),
                            onClick = { onToggleDesktopSite(!isDesktopSite) }
                        ),
                        ActionMatrixItem(
                            icon = Icons.Rounded.Shield,
                            label = "AdBlock",
                            isActive = isAdBlockEnabled,
                            shape = com.petal.browser.ui.theme.PetalMaterialShapes.Burst.toShape(),
                            onClick = { onToggleAdBlock(!isAdBlockEnabled) }
                        ),
                        ActionMatrixItem(
                            icon = Icons.Rounded.Share,
                            label = "Share",
                            shape = com.petal.browser.ui.theme.PetalMaterialShapes.SoftBoom.toShape(),
                            onClick = onShareLink
                        ),
                        ActionMatrixItem(
                            icon = Icons.Rounded.FindInPage,
                            label = "Find",
                            shape = com.petal.browser.ui.theme.PetalMaterialShapes.Sunny.toShape(),
                            onClick = onSearchOnSite
                        )
                    )
                )

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

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                // Section 2: Quick Toggles (AdBlocker & Desktop site)
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenShieldHud() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.width(28.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AdBlock & Shield HUD",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isAdBlockEnabled) "Tap for live shield stats & whitelist" else "AdBlocker disabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconSwitch(
                            checked = isAdBlockEnabled,
                            icon = Icons.Rounded.Shield,
                            onCheckedChange = onToggleAdBlock
                        )
                    }
                }

                if (!isHomePage) {
                    MenuRowSwitchItem(
                        icon = Icons.Rounded.DesktopWindows,
                        title = "Desktop site",
                        subtitle = "Request desktop version",
                        checked = isDesktopSite,
                        onCheckedChange = onToggleDesktopSite
                    )
                }

                if (isMediaPlaying) {
                    MenuRowItem(
                        icon = Icons.Rounded.PictureInPicture,
                        title = "Play in Picture-in-Picture",
                        subtitle = "Floating video window",
                        onClick = onTriggerMediaMode
                    )
                }

                if (!isHomePage) {
                    MenuRowItem(
                        icon = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        title = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                        onClick = onToggleBookmark
                    )
                }

                // Section 2.5: Extensions
                val extensions by PetalExtensionManager.extensions.collectAsState()
                val enabledExtensions = remember(extensions) { extensions.filter { it.enabled } }
                // Keep the Extensions section collapsed every time the overflow menu opens.
                // This also prevents the submenu from being left visually expanded when the
                // overflow dialog is dismissed and opened again.
                var isExtensionsExpanded by remember { mutableStateOf(false) }

                if (enabledExtensions.isEmpty()) {
                    MenuRowItem(
                        icon = Icons.Rounded.Extension,
                        title = "Extensions",
                        subtitle = "Install Firefox add-ons",
                        onClick = onOpenExtensions
                    )
                } else {
                    val firstExtName = enabledExtensions.first().name
                    val subtitleText = if (enabledExtensions.size == 1) firstExtName else "$firstExtName..."
                    MenuRowItem(
                        icon = Icons.Rounded.Extension,
                        title = "Extensions",
                        subtitle = subtitleText,
                        badgeCount = enabledExtensions.size,
                        trailingIcon = if (isExtensionsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        onClick = { isExtensionsExpanded = !isExtensionsExpanded }
                    )

                    AnimatedVisibility(
                        visible = isExtensionsExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            enabledExtensions.forEach { ext ->
                                ExtensionMenuRowItem(
                                    extension = ext,
                                    onClick = { isExtensionsExpanded = false; onOpenExtensionAction(ext.id) },
                                    onSettingsClick = { onOpenExtensionSettings(ext.id) }
                                )
                            }
                            MenuRowItem(
                                icon = Icons.Rounded.Tune,
                                title = "Manage extensions",
                                subtitle = "Install or configure add-ons",
                                isSubItem = true,
                                onClick = { isExtensionsExpanded = false; onOpenExtensions() }
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                // Section 3: Navigation & Data
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

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                // Section 4: Tools & Settings
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
                            icon = Icons.Rounded.MenuBook,
                            title = "Reading mode",
                            isSubItem = true,
                            onClick = onShowReadingMode
                        )
                        MenuRowItem(
                            icon = Icons.Rounded.Print,
                            title = "Print to PDF",
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
                        MenuRowItem(
                            icon = Icons.Rounded.LinkOff,
                            title = "Copy Clean Link",
                            subtitle = "Strip tracking & referral tags",
                            isSubItem = true,
                            onClick = onCopyCleanLink
                        )
                        MenuRowItem(
                            icon = Icons.Rounded.Terminal,
                            title = "Developer Console",
                            isSubItem = true,
                            onClick = onOpenDevConsole
                        )
                        MenuRowItem(
                            icon = Icons.Rounded.Lock,
                            title = "Safe Locker",
                            isSubItem = true,
                            onClick = onOpenSafeLocker
                        )

                        val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
                        var universalCopyEnabled by remember {
                            mutableStateOf(sp.getBoolean("petal_builtin_universal_copy", true))
                        }
                        var aiBlockerEnabled by remember {
                            mutableStateOf(sp.getBoolean("petal_builtin_ai_blocker", true))
                        }

                        MenuRowItem(
                            icon = Icons.Rounded.ContentCopy,
                            title = "Universal Copy",
                            subtitle = "Force text copy on blocked pages",
                            isSubItem = true,
                            trailingContent = {
                                Switch(
                                    checked = universalCopyEnabled,
                                    onCheckedChange = { isChecked ->
                                        universalCopyEnabled = isChecked
                                        sp.edit().putBoolean("petal_builtin_universal_copy", isChecked).apply()
                                        com.petal.browser.extensions.PetalBuiltInExtensionManager.setEnabled(
                                            context,
                                            "petal_builtin_universal_copy",
                                            isChecked
                                        )
                                    },
                                    modifier = Modifier.scale(0.8f)
                                )
                            },
                            onClick = {
                                val newState = !universalCopyEnabled
                                universalCopyEnabled = newState
                                sp.edit().putBoolean("petal_builtin_universal_copy", newState).apply()
                                com.petal.browser.extensions.PetalBuiltInExtensionManager.setEnabled(
                                    context,
                                    "petal_builtin_universal_copy",
                                    newState
                                )
                            }
                        )

                        MenuRowItem(
                            icon = Icons.Rounded.SmartToy,
                            title = "AI Overview Blocker",
                            subtitle = "Collapse AI summary search cards",
                            isSubItem = true,
                            trailingContent = {
                                Switch(
                                    checked = aiBlockerEnabled,
                                    onCheckedChange = { isChecked ->
                                        aiBlockerEnabled = isChecked
                                        sp.edit().putBoolean("petal_builtin_ai_blocker", isChecked).apply()
                                        com.petal.browser.extensions.PetalBuiltInExtensionManager.setEnabled(
                                            context,
                                            "petal_builtin_ai_blocker",
                                            isChecked
                                        )
                                    },
                                    modifier = Modifier.scale(0.8f)
                                )
                            },
                            onClick = {
                                val newState = !aiBlockerEnabled
                                aiBlockerEnabled = newState
                                sp.edit().putBoolean("petal_builtin_ai_blocker", newState).apply()
                                com.petal.browser.extensions.PetalBuiltInExtensionManager.setEnabled(
                                    context,
                                    "petal_builtin_ai_blocker",
                                    newState
                                )
                            }
                        )
                    }
                }

                if (!isHomePage) {
                    MenuRowItem(
                        icon = Icons.Rounded.InstallMobile,
                        title = "Install as App",
                        onClick = onInstallPwa
                    )
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
    Card(
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            contentColor = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .size(44.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
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
    badgeCount: Int? = null,
    isSubItem: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailingContent != null) {
                trailingContent()
            } else if (badgeCount != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeCount.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (trailingIcon != null) {
                        Icon(
                            imageVector = trailingIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else if (trailingIcon != null) {
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
private fun ExtensionMenuRowItem(
    extension: PetalExtensionManager.InstalledExtension,
    onClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {
                com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                    .playIfEnabled(context, com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.75f)
                onClick()
            })
            .padding(start = 28.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val bmp = extension.icon
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = extension.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = {
                com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                    .playIfEnabled(context, com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.75f)
                onSettingsClick()
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Extension Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
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
