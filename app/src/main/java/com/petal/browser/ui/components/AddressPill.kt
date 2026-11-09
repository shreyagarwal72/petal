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
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var isSwipeTabsEnabled by remember { mutableStateOf(sp.getBoolean("sp_address_bar_swipe_tabs", true)) }
    var isQuickActionsEnabled by remember { mutableStateOf(sp.getBoolean("sp_address_bar_quick_actions", true)) }
    var addressBarHeight by remember { mutableStateOf(sp.getString("sp_address_bar_height", "COMPACT") ?: "COMPACT") }
    var addressBarAction by remember { mutableStateOf(sp.getString("sp_address_bar_action", if (sp.getBoolean("sp_ai_search_address_bar", true)) "AI" else "NONE") ?: "AI") }

    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "sp_address_bar_swipe_tabs" -> isSwipeTabsEnabled = sp.getBoolean("sp_address_bar_swipe_tabs", true)
                "sp_address_bar_quick_actions" -> isQuickActionsEnabled = sp.getBoolean("sp_address_bar_quick_actions", true)
                "sp_address_bar_height" -> addressBarHeight = sp.getString("sp_address_bar_height", "COMPACT") ?: "COMPACT"
                "sp_address_bar_action" -> addressBarAction = sp.getString("sp_address_bar_action", if (sp.getBoolean("sp_ai_search_address_bar", true)) "AI" else "NONE") ?: "AI"
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    var showQuickActionsMenu by remember { mutableStateOf(false) }

    // Media sniffer button pref & stream availability
    var isMediaButtonPrefEnabled by remember { mutableStateOf(sp.getBoolean("sp_media_button_address_bar", true)) }
    DisposableEffect(sp) {
        val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, k ->
            if (k == "sp_media_button_address_bar") {
                isMediaButtonPrefEnabled = prefs.getBoolean("sp_media_button_address_bar", true)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(l)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(l) }
    }
    val hasPlayableMedia by PetalMediaSniffer.interceptor.hasPlayableMedia.collectAsStateWithLifecycle()
    val nativeResources by PetalNativeSnifferEngine.currentTabResources.collectAsStateWithLifecycle()

    val isBlankOrSearch = url.isEmpty() || url == "about:blank" || url.startsWith("file:///android_asset/")
    val showMediaButton = isMediaButtonPrefEnabled && (hasPlayableMedia || nativeResources.isNotEmpty()) && !isBlankOrSearch

    val isHttps = url.startsWith("https://")
    val isHttp = url.startsWith("http://")

    val formattedUrl: AnnotatedString = if (isBlankOrSearch) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal)) {
                append("Search or type URL")
            }
        }
    } else {
        val cleanUrl = when {
            isHttps -> url.substring(8)
            isHttp -> url.substring(7)
            else -> url
        }
        val slashIndex = cleanUrl.indexOf('/')
        val domain = if (slashIndex != -1) cleanUrl.substring(0, slashIndex) else cleanUrl
        val path = if (slashIndex != -1) cleanUrl.substring(slashIndex) else ""

        buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)) {
                append(domain)
            }
            if (path.isNotEmpty()) {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontWeight = FontWeight.Normal)) {
                    append(path)
                }
            }
        }
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

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
    val glassBorderBrush = if (isLiquidGlass && (inMergedBar || isBottom)) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (isDarkLuma) 0.35f else 0.55f),
                Color.White.copy(alpha = if (isDarkLuma) 0.08f else 0.15f)
            )
        )
    } else null

    Box(
        modifier = modifier
            .onGloballyPositioned { pillBounds[0] = it.boundsInWindow() }
            .clip(urlBoxShape)
            .then(
                if (glassBorderBrush != null) Modifier.border(1.dp, glassBorderBrush, urlBoxShape)
                else Modifier
            )
            .background(urlBoxBackground)
            .pointerInput(isSwipeTabsEnabled) {
                if (!isSwipeTabsEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = {
                        val threshold = 90.dp.toPx()
                        if (dragAccumulator > threshold) {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.75f)
                            onSwipePrevTab()
                        } else if (dragAccumulator < -threshold) {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.75f)
                            onSwipeNextTab()
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
                onClick = { markOrigin(); onAddressClick() },
                onLongClick = {
                    if (isQuickActionsEnabled && !isBlankOrSearch) {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.8f)
                        showQuickActionsMenu = true
                    } else {
                        markOrigin()
                        onAddressClick()
                    }
                }
            )
            .padding(horizontal = if (isBottom || inMergedBar) 8.dp else 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Security / Privacy Shield Favicon Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(ExpressiveButtonShape)
                        .clickable(
                            indication = ripple(bounded = true),
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            val act = context as? ComponentActivity
                            if (!isBlankOrSearch && act != null) {
                                try {
                                    PetalHapticEngine.getInstance(context).play(
                                        PetalHapticEngine.Pattern.HEAVY_CLICK,
                                        0.65f
                                    )
                                } catch (_: Throwable) {}
                                PetalPrivacyShieldSheet.show(act, url) {}
                            } else {
                                markOrigin()
                                onAddressClick()
                            }
                        }
                ) {
                AnimatedContent(
                    targetState = (favicon != null && !isBlankOrSearch && !isIncognito) to favicon,
                    transitionSpec = {
                        fadeIn(PetalSpring.faviconFade()) togetherWith fadeOut(PetalSpring.faviconFade())
                    },
                    label = "FaviconCrossfade"
                ) { (hasFavicon, fav) ->
                    if (hasFavicon && fav != null) {
                        Image(
                            bitmap = fav.asImageBitmap(),
                            contentDescription = "Favicon",
                            modifier = Modifier.size(22.dp).clip(ExpressiveButtonShape)
                        )
                    } else {
                        Icon(
                            imageVector = if (isIncognito) {
                                Icons.Rounded.VisibilityOff
                            } else if (isHttps) {
                                Icons.Rounded.Lock
                            } else {
                                Icons.Rounded.Language
                            },
                            contentDescription = "Privacy Shield",
                            tint = if (isHttps && !isIncognito) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                }

                val hasActiveSensors = remember(url) {
                    try {
                        val host = Uri.parse(url).host ?: url
                        val origin = "https://$host"
                        PetalActiveMediaSensors.hasAnyActive(origin) || PetalActiveMediaSensors.hasAnyActive(url)
                    } catch (_: Exception) {
                        false
                    }
                }

                if (hasActiveSensors) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(ExpressiveButtonShape)
                            .background(Color(0xFF00E676))
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formattedUrl,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )

                // Optional contextual action button
                val isProperSite = remember(url) { PetalAiResearchEngine.isProperWebSite(url) }
                val showRightAction = !isBlankOrSearch && when (addressBarAction.uppercase()) {
                    "AI" -> isProperSite
                    "BOOKMARK" -> true
                    else -> false
                }

                if (showRightAction) {
                    var isBookmarked by remember(url) {
                        mutableStateOf(
                            try {
                                val action = HistoryBookmarkRepository(context)
                                action.open(false)
                                val result = action.checkBookmark(url)
                                action.close()
                                result
                            } catch (_: Exception) { false }
                        )
                    }
                    IconButton(
                        onClick = {
                            if (addressBarAction.equals("AI", true)) {
                                onAiResearchClick()
                            } else {
                                try {
                                    val action = HistoryBookmarkRepository(context)
                                    action.open(true)
                                    if (isBookmarked) {
                                        action.deleteURL(url, HistoryBookmarkContract.TABLE_BOOKMARK)
                                        isBookmarked = false
                                        PetalToast.show(context, context.getString(R.string.ui_bookmark_removed))
                                    } else {
                                        action.addBookmark(Record(if (title.isNotBlank()) title else url, url, 0L, 0))
                                        isBookmarked = true
                                        PetalToast.show(context, context.getString(R.string.ui_saved_to_bookmarks))
                                    }
                                    action.close()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (addressBarAction.equals("AI", true)) Icons.Rounded.AutoAwesome else if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (addressBarAction.equals("AI", true)) "Petal AI" else if (isBookmarked) stringResource(R.string.ui_remove_from_bookmarks) else stringResource(R.string.ui_bookmark_this_page),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Dynamic Reader Mode Button
                if (isReaderEligible) {
                    IconButton(
                        onClick = onReaderClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = "Reader View",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Media sniffer badge
                if (showMediaButton) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f)
                    ) {
                        val count = nativeResources.size
                        Surface(
                            shape = ExpressiveButtonShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable {
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                    onMediaClick()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.TravelExplore,
                                    contentDescription = "Sniffer — $count resources found",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                if (count > 0) {
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = if (count > 99) "99+" else count.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Material 3 Expressive integrated loading progress line
            val isStackedMerged = inMergedBar && BarPrefs.LAYOUT_STACKED.equals(
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("sp_merge_address_bar_layout", BarPrefs.LAYOUT_STACKED),
                ignoreCase = true
            )
            if (!isStackedMerged && isLoading && progress > 0f && progress < 1f) {
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
                                if (favicon != null && !isIncognito) {
                                    Image(
                                        bitmap = favicon.asImageBitmap(),
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
                                text = if (title.isNotBlank()) title else "Address Bar Quick Actions",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = url.ifBlank { "about:blank" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                var isBookmarked by remember {
                    mutableStateOf(
                        try {
                            val action = HistoryBookmarkRepository(context)
                            action.open(false)
                            val res = action.checkBookmark(url)
                            action.close()
                            res
                        } catch (e: Exception) { false }
                    )
                }

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
                            clipboard.setPrimaryClip(ClipData.newPlainText("Clean URL", cleanUrl))
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
                            try {
                                val action = HistoryBookmarkRepository(context)
                                action.open(true)
                                if (isBookmarked) {
                                    action.deleteURL(url, HistoryBookmarkContract.TABLE_BOOKMARK)
                                    isBookmarked = false
                                    PetalToast.show(context, context.getString(R.string.ui_bookmark_removed))
                                } else {
                                    val r = Record(if (title.isNotBlank()) title else url, url, 0L, 0)
                                    action.addBookmark(r)
                                    isBookmarked = true
                                    PetalToast.show(context, context.getString(R.string.ui_saved_to_bookmarks))
                                }
                                action.close()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                )

                actions.add(
                    QuickActionItem(
                        icon = Icons.Rounded.Refresh,
                        title = "Hard Refresh",
                        subtitle = "Reload page completely bypassing cached resources",
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
                        title = "Shield HUD & Whitelist",
                        subtitle = "Inspect blocked trackers, ads, and connection certificate",
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
                        title = "Petal AI Assist",
                        subtitle = "Summarize page, deep analysis, or ask questions with AI",
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
                                        haptics.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.75f)
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
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
