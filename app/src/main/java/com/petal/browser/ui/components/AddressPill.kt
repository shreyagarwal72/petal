package com.petal.browser.ui.components

import com.petal.browser.R
import androidx.compose.ui.res.stringResource

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.minimumInteractiveComponentSize
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.petal.browser.ui.theme.PetalText
import com.petal.browser.compose.ai.PetalAiResearchEngine
import com.petal.browser.database.HistoryBookmarkContract
import com.petal.browser.database.HistoryBookmarkRepository
import com.petal.browser.database.Record
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.media.sniffer.PetalMediaSniffer
import com.petal.browser.media.sniffer.PetalNativeSnifferEngine
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.view.PetalToast

/**
 * Reusable URL Pill for Petal Browser address bars.
 *
 * Encapsulates:
 * - URL formatting (highlighted root domain, muted path, placeholder)
 * - Security & Privacy Shield / Favicon indicator
 * - Tap to open omnibox & Site-Info Shield HUD
 * - Long-press Quick Actions sheet (Clean URL Copy, Paste & Go, Bookmark toggle, Hard Refresh)
 * - Swipe left/right gesture to switch adjacent tabs
 * - Subtle loading progress indicator
 * - Reader Mode button, Contextual Action (AI / Bookmark / None), Media Sniffer badge
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressPill(
    url: String,
    title: String,
    favicon: Bitmap? = null,
    progress: Float = 0f,
    isIncognito: Boolean = false,
    isLoading: Boolean = false,
    onAddressClick: () -> Unit,
    onAiResearchClick: () -> Unit = {},
    onMediaClick: () -> Unit = {},
    onSwipeNextTab: () -> Unit = {},
    onSwipePrevTab: () -> Unit = {},
    /** Swipe up on the bar opens the tab switcher. */
    onSwipeUp: () -> Unit = {},
    onPasteAndGo: (String) -> Unit = {},
    onHardRefresh: () -> Unit = {},
    isReaderEligible: Boolean = false,
    onReaderClick: () -> Unit = {},
    isBottom: Boolean = false,
    inMergedBar: Boolean = false,
    isLiquidGlass: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = rememberBarSettings()
    val isSwipeTabsEnabled = settings.swipeTabs
    val isQuickActionsEnabled = settings.quickActions
    val addressBarAction = settings.contextAction

    var showQuickActionsMenu by remember { mutableStateOf(false) }

    // Media sniffer button pref & stream availability
    val isMediaButtonPrefEnabled = settings.mediaButton
    val hasPlayableMedia by PetalMediaSniffer.interceptor.hasPlayableMedia.collectAsStateWithLifecycle()
    val nativeResources by PetalNativeSnifferEngine.currentTabResources.collectAsStateWithLifecycle()
    val capturedMedia by com.petal.browser.media.sniffer.PetalMediaCaptureStore.currentTabMedia.collectAsStateWithLifecycle()

    val totalMediaCount = maxOf(capturedMedia.size, nativeResources.size + (if (hasPlayableMedia) 1 else 0))
    val isBlankOrSearch = url.isEmpty() || url == "about:blank" || url.startsWith("file:///android_asset/")
    val showMediaButton = isMediaButtonPrefEnabled && (totalMediaCount > 0 || hasPlayableMedia || com.petal.browser.media.ytdlp.YtDlpSites.isLikelySupported(url)) && !isBlankOrSearch

    val urlParts = remember(url) { if (isBlankOrSearch) null else UrlDisplay.parse(url) }
    val isHttps = urlParts?.isHttps == true
    val isHttp = urlParts?.isHttp == true
    val isPunycode = urlParts?.isPunycode == true
    val resolvedFavicon by rememberSiteFavicon(
        url = url,
        tabIcon = favicon,
        isPrivate = isIncognito
    )

    val searchHint = stringResource(R.string.bar_search_or_type_url)
    val urlDisplayMode = settings.urlDisplayMode
    val colors = MaterialTheme.colorScheme
    val formattedUrl: AnnotatedString = if (urlParts == null) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.onSurfaceVariant, fontWeight = FontWeight.Normal)) {
                append(searchHint)
            }
        }
    } else {
        val muted = SpanStyle(color = colors.onSurfaceVariant.copy(alpha = 0.7f), fontWeight = FontWeight.Normal)
        buildAnnotatedString {
            // Text before an '@' is login info, never the domain: keep it muted (shown in FULL mode only).
            if (urlDisplayMode == UrlDisplayMode.FULL && urlParts.userInfo.isNotEmpty()) {
                withStyle(muted) { append(urlParts.userInfo) }
            }
            // Only the real host is bold. Punycode hosts stay in their raw xn-- form and are tinted.
            withStyle(
                SpanStyle(
                    color = if (isPunycode) colors.error else colors.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            ) { append(urlParts.host) }
            if (urlDisplayMode == UrlDisplayMode.FULL && urlParts.rest.isNotEmpty()) {
                withStyle(muted) { append(urlParts.rest) }
            }
        }
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    // Gesture lambdas are read through updated state so pointerInput never captures a stale one.
    val currentSwipePrev by rememberUpdatedState(onSwipePrevTab)
    val currentSwipeNext by rememberUpdatedState(onSwipeNextTab)
    val currentSwipeUp by rememberUpdatedState(onSwipeUp)
    var verticalAccumulator by remember { mutableFloatStateOf(0f) }
    val currentAddressClick by rememberUpdatedState(onAddressClick)

    // Where this pill is on screen, so the omnibox page can grow out of it / collapse back into it.
    // Plain array (not state) so layout changes while the bar collapses never trigger recomposition.
    val pillBounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    val pillRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 20.dp.toPx() }
    val markOrigin = { PetalOmniboxOrigin.mark(pillBounds[0], pillRadiusPx) }

    val urlBoxShape = RoundedCornerShape(20.dp)
    val urlBoxBackground = when {
        inMergedBar -> {
            if (isLiquidGlass) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
        }
        isBottom -> {
            if (isLiquidGlass) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
        }
        else -> Color.Transparent
    }

    val isDarkLuma = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Box(
        modifier = modifier
            .onGloballyPositioned { pillBounds[0] = it.boundsInWindow() }
            .clip(urlBoxShape)
            .background(urlBoxBackground)
            .pointerInput(Unit) {
                // Swipe up (at least 48dp, mostly vertical) opens the tab switcher.
                detectVerticalDragGestures(
                    onDragStart = { verticalAccumulator = 0f },
                    onDragEnd = {
                        if (verticalAccumulator < -48.dp.toPx()) {
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).thresholdCrossed(context)
                            currentSwipeUp()
                        }
                        verticalAccumulator = 0f
                    },
                    onDragCancel = { verticalAccumulator = 0f },
                    onVerticalDrag = { _, dragAmount -> verticalAccumulator += dragAmount }
                )
            }
            .pointerInput(isSwipeTabsEnabled) {
                if (!isSwipeTabsEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = {
                        val threshold = 90.dp.toPx()
                        if (dragAccumulator > threshold) {
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).thresholdCrossed(context)
                            currentSwipePrev()
                        } else if (dragAccumulator < -threshold) {
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).thresholdCrossed(context)
                            currentSwipeNext()
                        }
                        dragAccumulator = 0f
                    },
                    onDragCancel = { dragAccumulator = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                    }
                )
            }
            .combinedClickable(
                onClick = { markOrigin(); currentAddressClick() },
                onLongClick = {
                    if (isQuickActionsEnabled && !isBlankOrSearch) {
                        com.petal.browser.haptics.PetalHapticEngine.getInstance(context).thresholdCrossed(context)
                        showQuickActionsMenu = true
                    } else {
                        markOrigin()
                        currentAddressClick()
                    }
                }
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val translateController = rememberTranslateChipController()
            val extensionActions = rememberExtensionChipActions(url)
            val isProperSite = remember(url) { PetalAiResearchEngine.isProperWebSite(url) }
            val isBookmarked by rememberIsBookmarked(url)
            val bookmarkScope = rememberCoroutineScope()
            var showPillOverflowMenu by remember { mutableStateOf(false) }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isTightPill = maxWidth < 260.dp
                val faviconSlotWidth = if (isTightPill) 40.dp else 48.dp
                val chipSlotWidth = if (maxWidth < 300.dp) 40.dp else 44.dp
                val moreSlotWidth = 40.dp

                val hasActiveSensors = try {
                    val host = Uri.parse(url).host ?: url
                    PetalActiveMediaSensors.hasAnyActive("https://$host") || PetalActiveMediaSensors.hasAnyActive(url)
                } catch (_: Exception) {
                    false
                }

                val securityWidth = when {
                    urlDisplayMode == UrlDisplayMode.DOMAIN_ONLY -> when {
                        isHttp || isPunycode -> 20.dp
                        else -> 0.dp
                    }
                    isHttp -> if (isPunycode) 88.dp else 72.dp
                    isHttps -> if (isPunycode) 34.dp else 18.dp
                    else -> 0.dp
                }
                val sensorWidth = if (hasActiveSensors) 10.dp else 0.dp
                val leftFixedWidth = faviconSlotWidth + securityWidth + sensorWidth + 8.dp
                val minUrlWidth = minOf(120.dp, (maxWidth - leftFixedWidth).coerceAtLeast(48.dp))
                val chipBudget = (maxWidth - leftFixedWidth - minUrlWidth).coerceAtLeast(0.dp)

                // Classic right-side action is exclusively for Classic split mode; Merge Bar configures AI/Bookmark directly.
                val showRightAction = !inMergedBar && !isBlankOrSearch && when (addressBarAction.uppercase()) {
                    "AI" -> isProperSite
                    "BOOKMARK" -> true
                    else -> false
                }

                val aiLabel = stringResource(R.string.bar_petal_ai)
                val bookmarkLabel = if (isBookmarked) stringResource(R.string.ui_remove_from_bookmarks) else stringResource(R.string.ui_bookmark_this_page)
                val readerLabel = stringResource(R.string.bar_reader_view)
                val mediaLabel = stringResource(R.string.bar_media_found, totalMediaCount)

                // Priority order (highest first; lowest priority dropped first into More chip):
                // 1. Contextual AI/Bookmark  2. Reader  3. Media  4. Translate  5. Extension chips
                val pillItems = buildList<PillChipEntry> {
                    if (showRightAction) {
                        val isAi = addressBarAction.equals("AI", true)
                        add(
                            PillChipEntry(
                                id = "right_action",
                                label = if (isAi) aiLabel else bookmarkLabel,
                                icon = if (isAi) Icons.Rounded.AutoAwesome else if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                onClick = {
                                    if (isAi) {
                                        onAiResearchClick()
                                    } else {
                                        bookmarkScope.launch {
                                            val was = isBookmarked
                                            val now = BookmarkChanges.toggle(context, url, title, was)
                                            if (now != null) {
                                                PetalToast.show(
                                                    context,
                                                    context.getString(if (now) R.string.ui_saved_to_bookmarks else R.string.ui_bookmark_removed)
                                                )
                                            }
                                        }
                                    }
                                },
                                renderVisual = { slotW ->
                                    IconButton(
                                        onClick = {
                                            if (isAi) {
                                                onAiResearchClick()
                                            } else {
                                                bookmarkScope.launch {
                                                    val was = isBookmarked
                                                    val now = BookmarkChanges.toggle(context, url, title, was)
                                                    if (now != null) {
                                                        PetalToast.show(
                                                            context,
                                                            context.getString(if (now) R.string.ui_saved_to_bookmarks else R.string.ui_bookmark_removed)
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.width(slotW).heightIn(min = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isAi) Icons.Rounded.AutoAwesome else if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                            contentDescription = if (isAi) aiLabel else bookmarkLabel,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            )
                        )
                    }
                    if (isReaderEligible) {
                        add(
                            PillChipEntry(
                                id = "reader",
                                label = readerLabel,
                                icon = Icons.Rounded.MenuBook,
                                onClick = onReaderClick,
                                renderVisual = { slotW ->
                                    IconButton(
                                        onClick = onReaderClick,
                                        modifier = Modifier.width(slotW).heightIn(min = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MenuBook,
                                            contentDescription = readerLabel,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            )
                        )
                    }
                    if (showMediaButton) {
                        val hasVideo = capturedMedia.any { it.kind == com.petal.browser.media.sniffer.capture.MediaKind.VIDEO } || hasPlayableMedia
                        val hasAudio = capturedMedia.any { it.kind == com.petal.browser.media.sniffer.capture.MediaKind.AUDIO }
                        val mediaIcon = when {
                            hasVideo -> Icons.Rounded.VideoLibrary
                            hasAudio -> Icons.Rounded.Audiotrack
                            else -> Icons.Rounded.TravelExplore
                        }
                        add(
                            PillChipEntry(
                                id = "media",
                                label = mediaLabel,
                                icon = mediaIcon,
                                onClick = onMediaClick,
                                renderVisual = { _ ->
                                    Surface(
                                        shape = ExpressiveButtonShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .minimumInteractiveComponentSize()
                                            .clickable { onMediaClick() }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = mediaIcon,
                                                contentDescription = mediaLabel,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            if (totalMediaCount > 0) {
                                                Spacer(Modifier.width(3.5.dp))
                                                Text(
                                                    text = if (totalMediaCount > 99) "99+" else totalMediaCount.toString(),
                                                    style = PetalText.badge,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        )
                    }
                    if (translateController.isTranslatable) {
                        add(
                            PillChipEntry(
                                id = "translate",
                                label = "Translate page",
                                icon = Icons.Rounded.Translate,
                                onClick = translateController.openSheet,
                                renderVisual = { slotW ->
                                    TranslateChipButton(
                                        isTranslated = translateController.isTranslated,
                                        onClick = translateController.openSheet,
                                        slotWidth = slotW
                                    )
                                }
                            )
                        )
                    }
                    extensionActions.forEach { ext ->
                        add(
                            PillChipEntry(
                                id = "ext_${ext.id}",
                                label = ext.label,
                                icon = Icons.Rounded.Extension,
                                onClick = ext.onClick,
                                renderVisual = { slotW ->
                                    ExtensionChipItem(action = ext, slotWidth = slotW)
                                }
                            )
                        )
                    }
                }

                // Cap visible pill chips at 1 primary chip + 1 More chip (or More only if tight).
                val primaryChip: PillChipEntry?
                val overflowChips: List<PillChipEntry>
                when {
                    pillItems.isEmpty() -> {
                        primaryChip = null
                        overflowChips = emptyList()
                    }
                    pillItems.size == 1 -> {
                        val needed = if (pillItems[0].id == "media") 56.dp else chipSlotWidth
                        if (chipBudget >= needed) {
                            primaryChip = pillItems[0]
                            overflowChips = emptyList()
                        } else if (chipBudget >= moreSlotWidth) {
                            primaryChip = null
                            overflowChips = pillItems
                        } else {
                            primaryChip = null
                            overflowChips = emptyList()
                        }
                    }
                    else -> {
                        val neededWithPrimary = (if (pillItems[0].id == "media") 56.dp else chipSlotWidth) + moreSlotWidth
                        if (chipBudget >= neededWithPrimary) {
                            primaryChip = pillItems[0]
                            overflowChips = pillItems.drop(1)
                        } else if (chipBudget >= moreSlotWidth) {
                            primaryChip = null
                            overflowChips = pillItems
                        } else {
                            primaryChip = null
                            overflowChips = emptyList()
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Favicon button: tap opens the privacy shield.
                    val faviconDesc = stringResource(R.string.bar_privacy_shield)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(faviconSlotWidth)
                            .heightIn(min = 48.dp)
                            .clip(ExpressiveButtonShape)
                            .clickable(
                                indication = ripple(bounded = true),
                                interactionSource = remember { MutableInteractionSource() },
                                onClickLabel = faviconDesc
                            ) {
                                val act = context as? ComponentActivity
                                if (!isBlankOrSearch && act != null) {
                                    PetalPrivacyShieldSheet.show(act, url) {}
                                } else {
                                    markOrigin()
                                    currentAddressClick()
                                }
                            }
                    ) {
                        AnimatedContent(
                            targetState = (resolvedFavicon != null && !isBlankOrSearch && !isIncognito) to resolvedFavicon,
                            transitionSpec = {
                                fadeIn(PetalSpring.calm()) togetherWith fadeOut(PetalSpring.calm())
                            },
                            label = "FaviconCrossfade"
                        ) { (hasFavicon, fav) ->
                            if (hasFavicon && fav != null) {
                                Image(
                                    bitmap = fav.asImageBitmap(),
                                    contentDescription = stringResource(R.string.bar_site_icon),
                                    modifier = Modifier.size(22.dp).clip(ExpressiveButtonShape)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isIncognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Language,
                                    contentDescription = faviconDesc,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // Security indicator: hidden on clean HTTPS when Domain-Only mode is selected, always warns on HTTP or punycode.
                    if ((isHttp || isPunycode) || (isHttps && urlDisplayMode != UrlDisplayMode.DOMAIN_ONLY)) {
                        Spacer(modifier = Modifier.width(2.dp))
                        SecurityIndicator(
                            isHttps = isHttps,
                            isPunycode = isPunycode,
                            compact = urlDisplayMode == UrlDisplayMode.DOMAIN_ONLY
                        )
                    }

                    if (hasActiveSensors) {
                        Spacer(modifier = Modifier.width(3.dp))
                        val sensorDesc = stringResource(R.string.bar_sensor_active)
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(ExpressiveButtonShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                                .semantics { contentDescription = sensorDesc }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = formattedUrl,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = minUrlWidth)
                    )

                    primaryChip?.renderVisual?.invoke(chipSlotWidth)

                    if (overflowChips.isNotEmpty()) {
                        Box {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .width(moreSlotWidth)
                                    .heightIn(min = 48.dp)
                                    .clip(CircleShape)
                                    .clickable { showPillOverflowMenu = true }
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreHoriz,
                                            contentDescription = "More page actions",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            DropdownMenu(
                                expanded = showPillOverflowMenu,
                                onDismissRequest = { showPillOverflowMenu = false }
                            ) {
                                overflowChips.forEach { entry ->
                                    DropdownMenuItem(
                                        text = { Text(entry.label) },
                                        leadingIcon = {
                                            Icon(entry.icon, contentDescription = null)
                                        },
                                        onClick = {
                                            showPillOverflowMenu = false
                                            entry.onClick()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Material 3 Expressive integrated loading progress line
            val isStackedMerged = inMergedBar && (settings.mergeLayout == MergeLayout.STACKED)
            if (settings.progressStyle == BarProgressStyle.LINE && !isStackedMerged && isLoading && progress > 0f && progress < 1f) {
                Material3ExpressiveProgressIndicator(
                    progress = progress,
                    visible = isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }

    // Quick Actions Sheet
    if (showQuickActionsMenu) {
        val haptics = PetalHapticEngine.getInstance(context)

        com.petal.browser.ui.containment.PetalSheet(
            onDismissRequest = { showQuickActionsMenu = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                com.petal.browser.ui.containment.PetalHeroCard(
                    shape = RoundedCornerShape(22.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val quickIcon = resolvedFavicon
                                if (quickIcon != null && !isIncognito) {
                                    Image(
                                        bitmap = quickIcon.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp).clip(ExpressiveButtonShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (title.isNotBlank()) title else stringResource(R.string.bar_quick_actions_title),
                                style = PetalText.sheetTitle
                            )
                            Text(
                                text = url.ifBlank { "about:blank" },
                                style = PetalText.listItemMeta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                val isBookmarked by rememberIsBookmarked(url)
                val sheetScope = rememberCoroutineScope()

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipText = clipboard.primaryClip?.let {
                    if (it.itemCount > 0) it.getItemAt(0)?.text?.toString()?.trim() else null
                }

                data class QuickActionItem(
                    val icon: ImageVector,
                    val title: String,
                    val subtitle: String,
                    val iconContainerColor: Color,
                    val iconTint: Color,
                    val onClick: () -> Unit
                )

                val actions = mutableListOf<QuickActionItem>()

                actions.add(
                    QuickActionItem(
                        icon = Icons.Rounded.ContentCopy,
                        title = context.getString(R.string.ui_copy_clean_url),
                        subtitle = context.getString(R.string.ui_copy_clean_url_desc),
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {
                            showQuickActionsMenu = false
                            val cleanUrl = sanitizeTrackingParameters(url)
                            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.bar_search_hint_clean_url), cleanUrl))
                            PetalToast.show(context, context.getString(R.string.ui_clean_url_copied))
                        }
                    )
                )

                if (!clipText.isNullOrEmpty()) {
                    actions.add(
                        QuickActionItem(
                            icon = Icons.Rounded.ContentPasteGo,
                            title = context.getString(R.string.ui_paste_and_go),
                            subtitle = clipText,
                            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = {
                                showQuickActionsMenu = false
                                onPasteAndGo(clipText)
                            }
                        )
                    )
                }

                actions.add(
                    QuickActionItem(
                        icon = if (isBookmarked) Icons.Rounded.BookmarkRemove else Icons.Rounded.BookmarkAdd,
                        title = if (isBookmarked) context.getString(R.string.ui_remove_from_bookmarks) else context.getString(R.string.ui_bookmark_this_page),
                        subtitle = if (isBookmarked) context.getString(R.string.ui_unbookmark_desc) else context.getString(R.string.ui_bookmark_desc),
                        iconContainerColor = if (isBookmarked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = if (isBookmarked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = {
                            sheetScope.launch {
                                val now = BookmarkChanges.toggle(context, url, title, isBookmarked)
                                if (now != null) {
                                    PetalToast.show(
                                        context,
                                        context.getString(if (now) R.string.ui_saved_to_bookmarks else R.string.ui_bookmark_removed)
                                    )
                                }
                            }
                        }
                    )
                )

                actions.add(
                    QuickActionItem(
                        icon = Icons.Rounded.Refresh,
                        title = context.getString(R.string.bar_hard_refresh),
                        subtitle = context.getString(R.string.bar_hard_refresh_desc),
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {
                            showQuickActionsMenu = false
                            onHardRefresh()
                        }
                    )
                )

                actions.add(
                    QuickActionItem(
                        icon = Icons.Rounded.Shield,
                        title = context.getString(R.string.bar_shield_hud),
                        subtitle = context.getString(R.string.bar_shield_hud_desc),
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = {
                            showQuickActionsMenu = false
                            val act = context as? ComponentActivity
                            if (act != null) {
                                PetalPrivacyShieldSheet.show(act, url) {}
                            }
                        }
                    )
                )

                actions.add(
                    QuickActionItem(
                        icon = Icons.Rounded.AutoAwesome,
                        title = context.getString(R.string.bar_ai_assist),
                        subtitle = context.getString(R.string.bar_ai_assist_desc),
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = {
                            showQuickActionsMenu = false
                            onAiResearchClick()
                        }
                    )
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    actions.forEachIndexed { index, item ->
                        val position = com.petal.browser.ui.containment.petalGroupPositionFor(index, actions.size)
                        val shape = com.petal.browser.ui.containment.petalGroupShape(position)

                        val rowGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
                        Surface(
                            shape = shape,
                            color = if (rowGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (rowGlass) Modifier.liquidGlassChrome(shape, enabled = true, sheenIntensity = 0.5f) else Modifier)
                                .clip(shape)
                                .clickable {
                                    try {
                                    } catch (_: Exception) {}
                                    item.onClick()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(item.iconContainerColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = item.iconTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = PetalText.rowTitle,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = item.subtitle,
                                        style = PetalText.rowSubtitle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private class PillChipEntry(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val renderVisual: @Composable (androidx.compose.ui.unit.Dp) -> Unit
)

/** Small lock (HTTPS) or "Not secure" label (HTTP). Punycode lookalike hosts add a warning glyph. */
@Composable
private fun SecurityIndicator(isHttps: Boolean, isPunycode: Boolean, compact: Boolean = false) {
    val secureDesc = stringResource(R.string.bar_secure_connection)
    val notSecure = stringResource(R.string.bar_not_secure)
    val lookalike = stringResource(R.string.bar_lookalike_warning)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isHttps) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = secureDesc,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.LockOpen,
                contentDescription = if (compact) notSecure else null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp)
            )
            if (!compact) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = notSecure,
                    style = PetalText.badge,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1
                )
            }
        }
        if (isPunycode) {
            Spacer(Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = lookalike,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Strips common tracking / telemetry parameters from query string.
 */
private fun sanitizeTrackingParameters(rawUrl: String): String {
    try {
        val uri = Uri.parse(rawUrl)
        if (uri.isOpaque || uri.query.isNullOrEmpty()) return rawUrl

        val trackingParams = setOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "fbclid", "gclid", "gbraid", "wbraid", "mc_eid", "msclkid",
            "igshid", "_hsenc", "_hsmi", "yclid", "zanpid"
        )

        val newUriBuilder = uri.buildUpon().clearQuery()
        var hasCleanedParams = false
        for (paramName in uri.queryParameterNames) {
            if (trackingParams.contains(paramName.lowercase(java.util.Locale.ROOT))) {
                hasCleanedParams = true
                continue
            }
            val values = uri.getQueryParameters(paramName)
            for (value in values) {
                newUriBuilder.appendQueryParameter(paramName, value)
            }
        }
        return if (hasCleanedParams) newUriBuilder.build().toString() else rawUrl
    } catch (e: Exception) {
        return rawUrl
    }
}
