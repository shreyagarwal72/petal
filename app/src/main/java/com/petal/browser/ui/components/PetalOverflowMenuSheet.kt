package com.petal.browser.ui.components

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
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
import com.petal.browser.extensions.PetalBuiltInExtensionManager
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.glass.recordGlassWallpaper
import com.petal.browser.ui.glass.liveGlassWallpaper
import com.petal.browser.ui.theme.PetalExpressiveTheme
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

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
    fun onOpenQuickTools() {}
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
            val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
            com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
            val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
            val isLiquidGlass = sp.getBoolean("sp_liquid_glass_enabled", false)
            val useBlurBg = sp.getBoolean("sp_overflow_menu_blur_bg", true) && isLiquidGlass

            val dialog = android.app.Dialog(activity, com.google.android.material.R.style.Theme_Design_BottomSheetDialog).apply {
                setCanceledOnTouchOutside(true)
                setCancelable(true)
            }
            dialog.setOnDismissListener {
                com.petal.browser.predictive.PetalContentSnapshot.clear()
            }

            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
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
                            },
                            onOpenQuickTools = {
                                dialog.dismiss()
                                handler.onOpenQuickTools()
                            }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.setOnShowListener {
                dialog.window?.let { window ->
                    window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                    window.setGravity(android.view.Gravity.BOTTOM or android.view.Gravity.END)
                    window.setBackgroundDrawableResource(android.R.color.transparent)
                    if (useBlurBg && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window.setBackgroundBlurRadius(54)
                        window.setDimAmount(0.12f)
                    } else {
                        window.setDimAmount(0.35f)
                    }
                }
            }
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
    onOpenSafeLocker: () -> Unit = {},
    onOpenQuickTools: () -> Unit = {}
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

    BackHandler(enabled = !isDismissing) {
        handleDismiss()
    }

    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            kotlinx.coroutines.delay(180)
            onDismissRequest()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.12f,
        animationSpec = if (isVisible) {
            spring(
                dampingRatio = 0.74f,
                stiffness = 400f
            )
        } else {
            spring(
                dampingRatio = 0.88f,
                stiffness = 460f
            )
        },
        label = "MenuExpandScale"
    )

    val translationY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 32f,
        animationSpec = if (isVisible) {
            spring(
                dampingRatio = 0.74f,
                stiffness = 400f
            )
        } else {
            spring(
                dampingRatio = 0.88f,
                stiffness = 460f
            )
        },
        label = "MenuSlideY"
    )

    val translationX by animateFloatAsState(
        targetValue = if (isVisible) 0f else 8f,
        animationSpec = if (isVisible) {
            spring(
                dampingRatio = 0.74f,
                stiffness = 400f
            )
        } else {
            spring(
                dampingRatio = 0.88f,
                stiffness = 460f
            )
        },
        label = "MenuSlideX"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = if (isVisible) {
            spring(stiffness = Spring.StiffnessMediumLow)
        } else {
            tween(durationMillis = 150)
        },
        label = "MenuExpandAlpha"
    )

    val isLiquidGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
    val wallpaper = com.petal.browser.ui.glass.rememberPetalGlassWallpaper()
    val snapshotBitmap = remember { com.petal.browser.predictive.PetalContentSnapshot.current }
    val liveContext = androidx.compose.ui.platform.LocalContext.current
    val liveSource = remember(liveContext) {
        var c: android.content.Context? = liveContext
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        (c as? android.app.Activity)?.let {
            it.findViewById<android.view.View>(R.id.main) ?: it.findViewById<android.view.View>(android.R.id.content)
        }
    }
    val frameTick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(isLiquidGlass, liveSource) {
        if (isLiquidGlass && liveSource != null) {
            while (true) { withFrameNanos { frameTick.longValue = it } }
        }
    }

    CompositionLocalProvider(com.petal.browser.ui.glass.LocalPetalGlassWallpaper provides wallpaper) {
      Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = handleDismiss
            )
      ) {
        // Record-only source: feeds the frosted + refracted glass, never drawn itself,
        // so the live page stays visible behind the menu.
        if (isLiquidGlass && liveSource != null) {
            // Live: re-record the real app window behind the menu every frame.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .liveGlassWallpaper(wallpaper, liveSource) { frameTick.longValue }
            )
        } else if (isLiquidGlass) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .recordGlassWallpaper(wallpaper)
            ) {
                if (snapshotBitmap != null && !snapshotBitmap.isRecycled) {
                    Image(
                        bitmap = snapshotBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
                    )
                } else {
                    com.petal.browser.ui.glass.PetalGlassPattern(Modifier.fillMaxSize())
                }
            }
        }

        val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomOffset = navBarBottomInset + 76.dp
        val topOffset = statusBarTopInset + 16.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = topOffset,
                    end = 12.dp,
                    start = 12.dp,
                    bottom = bottomOffset
                ),
            contentAlignment = Alignment.BottomEnd
        ) {
        com.petal.browser.ui.containment.PetalHeroCard(
            shape = com.petal.browser.ui.containment.PetalContainmentShapes.HeroInner,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    this.translationX = translationX
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(0.92f, 1f)
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
                // Tier 1: Hero Quick Navigation & Action Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .entrance(index = 0),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularIconButton(
                        icon = Icons.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.ui_back),
                        enabled = canGoBack,
                        onClick = onGoBack
                    )
                    CircularIconButton(
                        icon = Icons.Rounded.ArrowForward,
                        contentDescription = stringResource(R.string.ui_forward),
                        enabled = canGoForward,
                        onClick = onGoForward
                    )
                    CircularIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.ui_reload),
                        enabled = true,
                        onClick = onReload
                    )
                    CircularIconButton(
                        icon = if (isBookmarked && !isHomePage) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(R.string.ui_toggle_bookmark),
                        enabled = !isHomePage,
                        tint = if (isBookmarked && !isHomePage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        onClick = onToggleBookmark
                    )
                    CircularIconButton(
                        icon = Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.ui_share_link),
                        enabled = !isHomePage,
                        onClick = onShareLink
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )

                // Tier 2: Tabs & Shield
                MenuRowItem(
                    icon = Icons.Rounded.Add,
                    title = stringResource(R.string.ui_new_tab_2),
                    onClick = onNewTab
                )
                MenuRowItem(
                    icon = Icons.Rounded.VisibilityOff,
                    title = stringResource(R.string.ui_new_private_incognito_tab),
                    subtitle = stringResource(R.string.ui_browse_without_saving_search_history),
                    onClick = onNewIncognitoTab
                )

                // Privacy Shield HUD
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
                                text = stringResource(R.string.ui_adblock_shield_hud),
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

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                // Tier 3: Current Page Controls (when active)
                if (!isHomePage) {
                    MenuRowSwitchItem(
                        icon = Icons.Rounded.DesktopWindows,
                        title = stringResource(R.string.ui_desktop_site),
                        subtitle = stringResource(R.string.ui_request_desktop_version),
                        checked = isDesktopSite,
                        onCheckedChange = onToggleDesktopSite
                    )
                    MenuRowItem(
                        icon = Icons.Rounded.FindInPage,
                        title = stringResource(R.string.ui_find_in_page),
                        onClick = onSearchOnSite
                    )
                    MenuRowItem(
                        icon = Icons.Rounded.MenuBook,
                        title = stringResource(R.string.ui_reading_mode),
                        onClick = onShowReadingMode
                    )
                    MenuRowItem(
                        icon = Icons.Rounded.InstallMobile,
                        title = stringResource(R.string.ui_install_as_app),
                        onClick = onInstallPwa
                    )
                }

                if (isMediaPlaying) {
                    MenuRowItem(
                        icon = Icons.Rounded.PictureInPicture,
                        title = stringResource(R.string.ui_play_in_picture_in_picture),
                        subtitle = stringResource(R.string.ui_floating_video_window),
                        onClick = onTriggerMediaMode
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                // Tier 4: Library & Navigation
                MenuRowItem(
                    icon = Icons.Rounded.History,
                    title = stringResource(R.string.ui_history),
                    onClick = onOpenHistory
                )
                MenuRowItem(
                    icon = Icons.Rounded.Bookmark,
                    title = stringResource(R.string.ui_bookmarks),
                    onClick = onOpenBookmarks
                )
                MenuRowItem(
                    icon = Icons.Rounded.Download,
                    title = stringResource(R.string.ui_downloads),
                    onClick = onOpenDownloads
                )
                MenuRowItem(
                    icon = Icons.Rounded.DeleteSweep,
                    title = stringResource(R.string.ui_delete_browsing_data),
                    onClick = onDeleteBrowsingData
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                // Tier 5: Extensions & Add-ons
                val extensions by PetalExtensionManager.extensions.collectAsState()
                val enabledExtensions = remember(extensions) {
                    extensions.filter { it.enabled && !PetalBuiltInExtensionManager.isBuiltIn(it.id) }
                }
                var isExtensionsExpanded by remember { mutableStateOf(false) }

                if (enabledExtensions.isEmpty()) {
                    MenuRowItem(
                        icon = Icons.Rounded.Extension,
                        title = stringResource(R.string.ui_extensions),
                        subtitle = stringResource(R.string.ui_install_firefox_add_ons),
                        onClick = onOpenExtensions
                    )
                } else {
                    val firstExtName = enabledExtensions.first().name
                    val subtitleText = if (enabledExtensions.size == 1) firstExtName else "$firstExtName..."
                    MenuRowItem(
                        icon = Icons.Rounded.Extension,
                        title = stringResource(R.string.ui_extensions),
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
                                title = stringResource(R.string.ui_manage_extensions),
                                subtitle = stringResource(R.string.ui_install_or_configure_add_ons),
                                isSubItem = true,
                                onClick = { isExtensionsExpanded = false; onOpenExtensions() }
                            )
                        }
                    }
                }

                // Tier 6: Advanced & Quick Tools (Quarantined power tools)
                MenuRowItem(
                    icon = Icons.Rounded.Build,
                    title = stringResource(R.string.ui_more_tools),
                    subtitle = "Quick tools, DevTools, Clean link & PDF",
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
                            icon = Icons.Rounded.Widgets,
                            title = stringResource(R.string.ui_quick_tools),
                            subtitle = stringResource(R.string.ui_qr_translator_pdf_devtools_inspector),
                            isSubItem = true,
                            onClick = onOpenQuickTools
                        )
                        MenuRowItem(
                            icon = Icons.Rounded.LinkOff,
                            title = stringResource(R.string.ui_copy_clean_link),
                            subtitle = stringResource(R.string.ui_strip_tracking_referral_tags),
                            isSubItem = true,
                            onClick = onCopyCleanLink
                        )
                        MenuRowItem(
                            icon = Icons.Rounded.Print,
                            title = stringResource(R.string.ui_print_to_pdf),
                            isSubItem = true,
                            onClick = onPrintPdf
                        )
                        MenuRowItem(
                            icon = Icons.Rounded.SaveAlt,
                            title = stringResource(R.string.ui_save_page),
                            isSubItem = true,
                            onClick = onSavePage
                        )
                        MenuRowItem(
                            icon = Icons.Rounded.Code,
                            title = "View Page Source",
                            isSubItem = true,
                            onClick = onViewSource
                        )
                        MenuRowItem(
                            icon = Icons.Rounded.Terminal,
                            title = stringResource(R.string.ui_developer_console),
                            isSubItem = true,
                            onClick = onOpenDevConsole
                        )
                        MenuRowItem(
                            icon = Icons.Rounded.Lock,
                            title = stringResource(R.string.ui_safe_locker),
                            isSubItem = true,
                            onClick = onOpenSafeLocker
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )

                MenuRowItem(
                    icon = Icons.Rounded.Settings,
                    title = stringResource(R.string.ui_settings),
                    onClick = onOpenSettings
                )

                Spacer(Modifier.height(8.dp))
            }
        }
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
    val context = LocalContext.current
    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
    IconButton(
        onClick = {
            com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                .playIfEnabled(context, com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.75f)
            onClick()
        },
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (isGlass) {
                    Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                } else Modifier
            )
    ) {
        val iconTint = if (enabled) {
            tint
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
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
                contentDescription = stringResource(R.string.ui_extension_settings),
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
