package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.petal.browser.ui.theme.PetalText
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.R
import com.petal.browser.extensions.PetalBuiltInExtensionManager
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalGroupRow
import com.petal.browser.ui.containment.petalContainmentShape
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.containment.petalSheetSurface

/** Preference key of the "New main menu (Beta)" setting (default off). */
const val PREF_NEW_MAIN_MENU_BETA = "sp_new_main_menu_beta"

/**
 * Set to true just before the beta menu is shown and cleared once it drew two frames. If the process dies
 * in between, the next menu open sees it still set and falls back to the stable menu.
 */
const val PREF_NEW_MAIN_MENU_BETA_PENDING = "sp_new_main_menu_beta_pending"

/**
 * Decides, once per menu open and outside composition, whether the beta menu may be used.
 * Returns false (stable menu) when the setting is off, when a previous beta render never finished
 * (the process died while it was drawing: the setting is switched off so it cannot crash-loop), or when
 * a pre-flight resource check throws. On success it arms the "render pending" flag, which the host
 * clears again once the menu rendered or closed.
 */
fun shouldUsePetalBetaMenu(context: android.content.Context, sp: android.content.SharedPreferences): Boolean {
    return try {
        if (!sp.getBoolean(PREF_NEW_MAIN_MENU_BETA, false)) return false
        if (sp.getBoolean(PREF_NEW_MAIN_MENU_BETA_PENDING, false)) {
            sp.edit()
                .putBoolean(PREF_NEW_MAIN_MENU_BETA_PENDING, false)
                .putBoolean(PREF_NEW_MAIN_MENU_BETA, false)
                .commit()
            return false
        }
        // Pre-flight: every string the beta menu needs must resolve.
        context.getString(R.string.menu_tile_private)
        context.getString(R.string.menu_tile_addons)
        context.getString(R.string.menu_tile_shield)
        context.getString(R.string.menu_state_on)
        context.getString(R.string.menu_state_off)
        context.getString(R.string.menu_more_tools_subtitle)
        context.getString(R.string.menu_view_page_source)
        sp.edit().putBoolean(PREF_NEW_MAIN_MENU_BETA_PENDING, true).commit()
        true
    } catch (e: Throwable) {
        false
    }
}

/**
 * Experimental redesigned main menu. Same parameters and callbacks as [PetalOverflowMenuSheet] (minus the
 * removed Home/Forward entries) and drawn through the same [PetalOverflowMenuShell], so popup size,
 * position, entrance animation, blur, glass and fallbacks are identical to the stable menu.
 *
 * [onRenderConfirmed] fires after two frames drew with a non-empty size; [onRenderFailed] fires when the
 * menu ended up with no size, so the host can switch to the stable menu.
 */
@Composable
fun PetalOverflowMenuSheetBeta(
    pageTitle: String,
    pageUrl: String,
    isBookmarked: Boolean,
    canGoBack: Boolean,
    isDesktopSite: Boolean,
    isAdBlockEnabled: Boolean = true,
    isMediaPlaying: Boolean = false,
    onDismissRequest: () -> Unit = {},
    onGoBack: () -> Unit,
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
    onOpenExtensionsMenu: () -> Unit = onOpenExtensions,
    onOpenExtensionAction: (String) -> Unit = {},
    onOpenExtensionSettings: (String) -> Unit = {},
    onOpenDevConsole: () -> Unit = {},
    onOpenSafeLocker: () -> Unit = {},
    onOpenQuickTools: () -> Unit = {},
    handleBackInternally: Boolean = false,
    onRenderConfirmed: () -> Unit = {},
    onRenderFailed: () -> Unit = {}
) {
    val context = LocalContext.current
    var isMoreToolsExpanded by remember { mutableStateOf(false) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        if (cardSize.width > 0 && cardSize.height > 0) onRenderConfirmed() else onRenderFailed()
    }

    PetalOverflowMenuShell(
        onDismissRequest = onDismissRequest,
        handleBackInternally = handleBackInternally
    ) {
        // Same rule as the stable menu: page-only entries are hidden/disabled on the home surface.
        val isHomePage = remember(pageUrl, pageTitle) {
            pageUrl.isBlank() ||
                pageUrl.equals("about:blank", ignoreCase = true) ||
                pageUrl.startsWith("petal://") ||
                pageUrl.contains("petal_home.html") ||
                pageUrl.startsWith("file:///android_asset/") ||
                pageTitle.equals("Petal", ignoreCase = true) ||
                pageTitle.equals("Petal Home", ignoreCase = true) ||
                pageTitle.equals("Petal Start", ignoreCase = true) ||
                com.petal.browser.unit.PetalBrowserUtils.isHomePage(pageUrl)
        }
        // On short screens (landscape phones) only the navigation group stays pinned; the footer scrolls.
        val compact = LocalConfiguration.current.screenHeightDp < 480

        val extensions by PetalExtensionManager.extensions.collectAsStateWithLifecycle()
        val enabledExtensions = remember(extensions) {
            extensions.filter { it.enabled && !PetalBuiltInExtensionManager.isBuiltIn(it.id) }
        }

        val footer: @Composable () -> Unit = {
            BetaFooter(
                onDeleteBrowsingData = onDeleteBrowsingData,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.entrance(index = 4)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { cardSize = it }
        ) {
            // Pinned: connected navigation group.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .entrance(index = 0),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BetaNavButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.ui_back),
                    enabled = canGoBack,
                    index = 0,
                    count = 4,
                    onClick = onGoBack
                )
                BetaNavButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.ui_reload),
                    enabled = true,
                    index = 1,
                    count = 4,
                    onClick = onReload
                )
                BetaNavButton(
                    modifier = Modifier.weight(1f),
                    icon = if (isBookmarked && !isHomePage) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = stringResource(R.string.ui_toggle_bookmark),
                    enabled = !isHomePage,
                    active = isBookmarked && !isHomePage,
                    index = 2,
                    count = 4,
                    onClick = onToggleBookmark
                )
                BetaNavButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Share,
                    contentDescription = stringResource(R.string.ui_share_link),
                    enabled = !isHomePage,
                    index = 3,
                    count = 4,
                    onClick = onShareLink
                )
            }

            // Scrolling middle.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                // 3x2 quick tiles.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .entrance(index = 1),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BetaTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Add,
                            label = stringResource(R.string.ui_new_tab_2),
                            role = BetaRole.Primary,
                            shape = petalContainmentShape(20.dp),
                            onClick = onNewTab
                        )
                        BetaTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.VisibilityOff,
                            label = stringResource(R.string.menu_tile_private),
                            role = BetaRole.Tertiary,
                            // Different corner shape: leaf-like alternating corners.
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomEnd = 28.dp, bottomStart = 8.dp),
                            onClick = onNewIncognitoTab
                        )
                        BetaTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Download,
                            label = stringResource(R.string.ui_downloads),
                            role = BetaRole.Secondary,
                            shape = petalContainmentShape(20.dp),
                            onClick = onOpenDownloads
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BetaTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.History,
                            label = stringResource(R.string.ui_history),
                            role = BetaRole.Neutral,
                            shape = petalContainmentShape(20.dp),
                            onClick = onOpenHistory
                        )
                        BetaTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Bookmark,
                            label = stringResource(R.string.ui_bookmarks),
                            role = BetaRole.Neutral,
                            shape = petalContainmentShape(20.dp),
                            onClick = onOpenBookmarks
                        )
                        BetaTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Extension,
                            label = stringResource(R.string.menu_tile_addons),
                            role = BetaRole.Neutral,
                            shape = petalContainmentShape(20.dp),
                            badgeCount = if (enabledExtensions.isNotEmpty()) enabledExtensions.size else null,
                            badgeDescription = if (enabledExtensions.isNotEmpty()) {
                                stringResource(R.string.menu_addons_count_desc, enabledExtensions.size)
                            } else null,
                            onClick = onOpenExtensionsMenu
                        )
                    }
                }

                // Toggle tiles.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .entrance(index = 2),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BetaToggleTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Shield,
                        iconOff = Icons.Rounded.GppMaybe,
                        label = stringResource(R.string.menu_tile_shield),
                        checked = isAdBlockEnabled,
                        role = BetaRole.Primary,
                        longClickLabel = stringResource(R.string.menu_shield_open_stats),
                        onLongClick = onOpenShieldHud,
                        onToggle = onToggleAdBlock
                    )
                    // Desktop site is a page control: hidden on the home surface, like the stable menu.
                    if (!isHomePage) {
                        BetaToggleTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.DesktopWindows,
                            iconOff = Icons.Rounded.PhoneAndroid,
                            label = stringResource(R.string.ui_desktop_site),
                            checked = isDesktopSite,
                            role = BetaRole.Secondary,
                            onToggle = onToggleDesktopSite
                        )
                    }
                }

                // One rounded group of the remaining actions: 2dp gaps, no dividers.
                BetaActionsGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .entrance(index = 3),
                    isHomePage = isHomePage,
                    isMediaPlaying = isMediaPlaying,
                    isMoreToolsExpanded = isMoreToolsExpanded,
                    onToggleMoreTools = { isMoreToolsExpanded = !isMoreToolsExpanded },
                    onSearchOnSite = onSearchOnSite,
                    onShowReadingMode = onShowReadingMode,
                    onInstallPwa = onInstallPwa,
                    onTriggerMediaMode = onTriggerMediaMode,
                    onOpenQuickTools = onOpenQuickTools,
                    onCopyCleanLink = onCopyCleanLink,
                    onPrintPdf = onPrintPdf,
                    onSavePage = onSavePage,
                    onViewSource = onViewSource,
                    onOpenDevConsole = onOpenDevConsole,
                    onOpenSafeLocker = onOpenSafeLocker
                )

                if (compact) footer()
            }

            // Pinned footer.
            if (!compact) footer()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

private enum class BetaRole { Primary, Secondary, Tertiary, Neutral }

@Composable
private fun BetaRole.container(): Color = when (this) {
    BetaRole.Primary -> MaterialTheme.colorScheme.primaryContainer
    BetaRole.Secondary -> MaterialTheme.colorScheme.secondaryContainer
    BetaRole.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer
    BetaRole.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun BetaRole.content(): Color = when (this) {
    BetaRole.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
    BetaRole.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
    BetaRole.Tertiary -> MaterialTheme.colorScheme.onTertiaryContainer
    BetaRole.Neutral -> MaterialTheme.colorScheme.onSurface
}

/** Tracks finger-down without consuming, so it can sit next to any clickable. */
private fun Modifier.betaPressTracker(onPressedChange: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onPressedChange(true)
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
            onPressedChange(false)
        }
    }

/** Corner radius the end/start of a connected group uses for a given slot. */
private val BetaOuterCorner = 28.dp
private val BetaInnerCorner = 6.dp

@Composable
private fun BetaNavButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    var pressed by remember { mutableStateOf(false) }

    val first = index == 0
    val last = index == count - 1
    val startTarget = if (first) BetaOuterCorner else BetaInnerCorner
    val endTarget = if (last) BetaOuterCorner else BetaInnerCorner
    // Pressed: every corner rounds up. Saved bookmark: settles into a softer, rounder shape.
    val cornerSpec = PetalSpring.calm<Dp>().orSnap(reduceMotion)
    val startCorner by animateDpAsState(
        targetValue = when {
            pressed && enabled -> BetaOuterCorner
            active -> 20.dp
            else -> startTarget
        },
        animationSpec = cornerSpec,
        label = "BetaNavStartCorner"
    )
    val endCorner by animateDpAsState(
        targetValue = when {
            pressed && enabled -> BetaOuterCorner
            active -> 20.dp
            else -> endTarget
        },
        animationSpec = cornerSpec,
        label = "BetaNavEndCorner"
    )
    val shape = RoundedCornerShape(
        topStart = startCorner.coerceAtLeast(0.dp),
        topEnd = endCorner.coerceAtLeast(0.dp),
        bottomEnd = endCorner.coerceAtLeast(0.dp),
        bottomStart = startCorner.coerceAtLeast(0.dp)
    )

    val container by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = PetalSpring.calm(),
        label = "BetaNavContainer"
    )
    val baseTint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val tint = if (enabled) baseTint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) PetalSpring.PRESS_SCALE_CARD else 1f,
        animationSpec = PetalSpring.quick<Float>().orSnap(reduceMotion),
        label = "BetaNavPress"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (active) 1.12f else 1f,
        animationSpec = PetalSpring.calm<Float>().orSnap(reduceMotion),
        label = "BetaNavIconScale"
    )

    Box(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .betaPressTracker { pressed = it }
            .petalSheetSurface(
                shape = shape,
                color = container,
                onClick = if (enabled) {
                    {
                        onClick()
                    }
                } else null,
                sheen = 0.35f
            ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = icon,
            animationSpec = PetalSpring.calm(),
            label = "BetaNavIcon"
        ) { shown ->
            Icon(
                imageVector = shown,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        }
    }
}

@Composable
private fun BetaTile(
    icon: ImageVector,
    label: String,
    role: BetaRole,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int? = null,
    badgeDescription: String? = null
) {
    val context = LocalContext.current
    val content = role.content()
    Box(
        modifier = modifier
            .heightIn(min = 72.dp)
            .petalSheetSurface(
                shape = shape,
                color = role.container(),
                onClick = {
                    onClick()
                },
                sheen = 0.35f
            )
            .semantics(mergeDescendants = true) {
                if (badgeDescription != null) contentDescription = badgeDescription
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = PetalText.chip,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        if (badgeCount != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeCount.toString(),
                    style = PetalText.badge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BetaToggleTile(
    icon: ImageVector,
    iconOff: ImageVector,
    label: String,
    checked: Boolean,
    role: BetaRole,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()

    val container by animateColorAsState(
        targetValue = if (checked) role.container() else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = PetalSpring.calm(),
        label = "BetaToggleContainer"
    )
    val content by animateColorAsState(
        targetValue = if (checked) role.content() else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = PetalSpring.calm(),
        label = "BetaToggleContent"
    )
    val corner by animateDpAsState(
        targetValue = if (checked) 28.dp else 16.dp,
        animationSpec = PetalSpring.calm<Dp>().orSnap(reduceMotion),
        label = "BetaToggleCorner"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (checked) 1.1f else 1f,
        animationSpec = PetalSpring.calm<Float>().orSnap(reduceMotion),
        label = "BetaToggleIconScale"
    )
    val shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))
    val stateText = stringResource(if (checked) R.string.menu_state_on else R.string.menu_state_off)

    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .petalSheetSurface(shape = shape, color = container, onClick = null, sheen = 0.35f)
            .combinedClickable(
                role = Role.Switch,
                onClickLabel = label,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick?.let {
                    {
                        it()
                    }
                },
                onClick = {
                    com.petal.browser.haptics.PetalHapticEngine.getInstance(context).selectionChanged(context)
                    onToggle(!checked)
                }
            )
            .semantics(mergeDescendants = true) { stateDescription = stateText }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(
            targetState = checked,
            animationSpec = PetalSpring.calm(),
            label = "BetaToggleIcon"
        ) { on ->
            Icon(
                imageVector = if (on) icon else iconOff,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = PetalText.chip,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stateText,
                style = PetalText.badge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private class BetaActionRow(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit,
    val trailing: (@Composable () -> Unit)? = null,
    val isSubItem: Boolean = false
)

@Composable
private fun BetaActionsGroup(
    isHomePage: Boolean,
    isMediaPlaying: Boolean,
    isMoreToolsExpanded: Boolean,
    onToggleMoreTools: () -> Unit,
    onSearchOnSite: () -> Unit,
    onShowReadingMode: () -> Unit,
    onInstallPwa: () -> Unit,
    onTriggerMediaMode: () -> Unit,
    onOpenQuickTools: () -> Unit,
    onCopyCleanLink: () -> Unit,
    onPrintPdf: () -> Unit,
    onSavePage: () -> Unit,
    onViewSource: () -> Unit,
    onOpenDevConsole: () -> Unit,
    onOpenSafeLocker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val mainRows = buildList {
        if (!isHomePage) {
            add(BetaActionRow(Icons.Rounded.FindInPage, stringResource(R.string.ui_find_in_page), onClick = onSearchOnSite))
            add(BetaActionRow(Icons.Rounded.MenuBook, stringResource(R.string.ui_reading_mode), onClick = onShowReadingMode))
            add(BetaActionRow(Icons.Rounded.InstallMobile, stringResource(R.string.ui_install_as_app), onClick = onInstallPwa))
        }
        if (isMediaPlaying) {
            add(
                BetaActionRow(
                    Icons.Rounded.PictureInPicture,
                    stringResource(R.string.ui_play_in_picture_in_picture),
                    subtitle = stringResource(R.string.ui_floating_video_window),
                    onClick = onTriggerMediaMode
                )
            )
        }
    }
    val moreToolsRow = BetaActionRow(
        icon = Icons.Rounded.Build,
        title = stringResource(R.string.ui_more_tools),
        subtitle = stringResource(R.string.menu_more_tools_subtitle),
        onClick = onToggleMoreTools,
        trailing = {
            Icon(
                imageVector = if (isMoreToolsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
    val subRows = listOf(
        BetaActionRow(Icons.Rounded.Widgets, stringResource(R.string.ui_quick_tools), stringResource(R.string.ui_qr_translator_pdf_devtools_inspector), onOpenQuickTools, isSubItem = true),
        BetaActionRow(Icons.Rounded.LinkOff, stringResource(R.string.ui_copy_clean_link), stringResource(R.string.ui_strip_tracking_referral_tags), onCopyCleanLink, isSubItem = true),
        BetaActionRow(Icons.Rounded.Print, stringResource(R.string.ui_print_to_pdf), onClick = onPrintPdf, isSubItem = true),
        BetaActionRow(Icons.Rounded.SaveAlt, stringResource(R.string.ui_save_page), onClick = onSavePage, isSubItem = true),
        BetaActionRow(Icons.Rounded.Code, stringResource(R.string.menu_view_page_source), onClick = onViewSource, isSubItem = true),
        BetaActionRow(Icons.Rounded.Terminal, stringResource(R.string.ui_developer_console), onClick = onOpenDevConsole, isSubItem = true),
        BetaActionRow(Icons.Rounded.Lock, stringResource(R.string.ui_safe_locker), onClick = onOpenSafeLocker, isSubItem = true)
    )

    // Positions follow the target state so the corners spring while the section expands/collapses.
    val total = mainRows.size + 1 + if (isMoreToolsExpanded) subRows.size else 0

    @Composable
    fun ActionRow(row: BetaActionRow, index: Int) {
        PetalGroupRow(
            icon = row.icon,
            title = row.title,
            subtitle = row.subtitle,
            position = petalGroupPositionFor(index, total),
            onClick = {
                row.onClick()
            },
            trailing = row.trailing,
            modifier = Modifier.padding(top = if (index > 0) 2.dp else 0.dp)
        )
    }

    Column(modifier = modifier) {
        mainRows.forEachIndexed { i, row -> ActionRow(row, i) }
        ActionRow(moreToolsRow, mainRows.size)
        AnimatedVisibility(
            visible = isMoreToolsExpanded,
            enter = expandVertically(animationSpec = PetalSpring.calm()),
            exit = shrinkVertically(animationSpec = PetalSpring.calm())
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                subRows.forEachIndexed { i, row -> ActionRow(row, mainRows.size + 1 + i) }
            }
        }
    }
}

@Composable
private fun BetaFooter(
    onDeleteBrowsingData: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            onClick = {
                onDeleteBrowsingData()
            },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.ui_delete_browsing_data),
                style = PetalText.button,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        FilledTonalButton(
            onClick = {
                onOpenSettings()
            },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.ui_settings),
                style = PetalText.button,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
