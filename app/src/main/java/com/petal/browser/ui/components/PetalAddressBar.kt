package com.petal.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.media.sniffer.PetalMediaSniffer
import com.petal.browser.view.PetalToast

/**
 * Material 3 Expressive Collapsed/Scrolled Address Bar.
 * Single pill squircle container row matching modern Android browser design:
 * - Far left: Back/Navigation arrow icon button or Stop button when loading (min 48dp touch target, 24dp icon)
 * - Security/Favicon Pill Chip: Favicon / Tune (HTTPS/HTTP) / Search (blank) / VisibilityOff (Incognito)
 * - Center: Flexible width URL text (root domain highlighted, path muted, single line, end ellipsis)
 * - Long-Press Quick Actions: Clean URL Copy, Paste & Go, Bookmark toggle, Hard Refresh
 * - Swipe left/right gesture across the bar to switch tabs (configurable via Accessibility)
 * - Far right: AI Research button (for proper sites) & Share icon button (min 48dp touch target, 24dp icon)
 * - Bottom edge: Integrated subtle animated loading progress indicator
 */
@OptIn(ExperimentalMaterial3Api::class, com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalAddressBar(
    url: String,
    title: String,
    favicon: Bitmap? = null,
    progress: Float = 0f,
    isIncognito: Boolean = false,
    isLoading: Boolean = false,
    canGoBack: Boolean = true,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onAddressClick: () -> Unit,
    onAiResearchClick: () -> Unit = {},
    onMediaClick: () -> Unit = {},
    onSwipeNextTab: () -> Unit = {},
    onSwipePrevTab: () -> Unit = {},
    onPasteAndGo: (String) -> Unit = {},
    onHardRefresh: () -> Unit = {},
    isBottom: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isBlankOrSearch = url.isEmpty() || url == "about:blank" || url.startsWith("file:///android_asset/")
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

    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var isSwipeTabsEnabled by remember { mutableStateOf(sp.getBoolean("sp_address_bar_swipe_tabs", true)) }
    var isQuickActionsEnabled by remember { mutableStateOf(sp.getBoolean("sp_address_bar_quick_actions", true)) }
    var addressBarHeight by remember { mutableStateOf(sp.getString("sp_address_bar_height", "COMPACT") ?: "COMPACT") }
    var addressBarAction by remember { mutableStateOf(sp.getString("sp_address_bar_action", if (sp.getBoolean("sp_ai_search_address_bar", true)) "AI" else "NONE") ?: "AI") }

    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "sp_address_bar_swipe_tabs") {
                isSwipeTabsEnabled = sp.getBoolean("sp_address_bar_swipe_tabs", true)
            } else if (key == "sp_address_bar_quick_actions") {
                isQuickActionsEnabled = sp.getBoolean("sp_address_bar_quick_actions", true)
            } else if (key == "sp_address_bar_height") {
                addressBarHeight = sp.getString("sp_address_bar_height", "COMPACT") ?: "COMPACT"
            } else if (key == "sp_address_bar_action") {
                addressBarAction = sp.getString("sp_address_bar_action", if (sp.getBoolean("sp_ai_search_address_bar", true)) "AI" else "NONE") ?: "AI"
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    var showQuickActionsMenu by remember { mutableStateOf(false) }

    // Media sniffer: observe hasPlayableMedia and the address-bar button pref
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
    val hasPlayableMedia by PetalMediaSniffer.interceptor.hasPlayableMedia.collectAsState()
    val showMediaButton = isMediaButtonPrefEnabled && hasPlayableMedia && !isBlankOrSearch

    val containerColor = if (isIncognito) {
        com.petal.browser.ui.theme.IncognitoSurfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val resolvedIsBottom = isBottom || "BOTTOM".equals(sp.getString("sp_address_bar_position", "TOP"), ignoreCase = true)

    val containerShape = if (resolvedIsBottom) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    } else {
        RoundedCornerShape(if (addressBarHeight.equals("COMPACT", true)) 24.dp else 28.dp)
    }

    val topBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Surface(
        shape = containerShape,
        color = containerColor,
        tonalElevation = if (resolvedIsBottom) 2.dp else 4.dp,
        shadowElevation = if (resolvedIsBottom) 4.dp else 4.dp,
        border = if (resolvedIsBottom) BorderStroke(0.65.dp, topBorderColor) else null,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (resolvedIsBottom) {
                    Modifier.padding(horizontal = 0.dp, vertical = 0.dp)
                } else {
                    Modifier.padding(horizontal = 12.dp, vertical = if (addressBarHeight.equals("COMPACT", true)) 3.dp else 5.dp)
                }
            )
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
            .entrance()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (addressBarHeight.equals("COMPACT", true)) 50.dp else 56.dp)
                    .padding(horizontal = if (resolvedIsBottom) 6.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Far Left: Back Navigation / Stop Loading Icon Button (min 48dp tap target)
                val leftIcon = if (isLoading) Icons.Rounded.Close else Icons.Rounded.ArrowBack
                val leftContentDesc = if (isLoading) "Stop Loading" else "Back"
                val isLeftEnabled = isLoading || canGoBack

                IconButton(
                    onClick = onBackClick,
                    enabled = isLeftEnabled,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = leftIcon,
                        contentDescription = leftContentDesc,
                        tint = if (isLeftEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(if (resolvedIsBottom) 4.dp else 2.dp))

                // Center: Flexible Width URL Text & Favicon / Security Chip (Firefox Android styled URL pill)
                val urlBoxShape = RoundedCornerShape(20.dp)
                val urlBoxBackground = if (resolvedIsBottom) {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(urlBoxShape)
                        .background(urlBoxBackground)
                        .combinedClickable(
                            onClick = { onAddressClick() },
                            onLongClick = {
                                if (isQuickActionsEnabled && !isBlankOrSearch) {
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.8f)
                                    showQuickActionsMenu = true
                                } else {
                                    onAddressClick()
                                }
                            }
                        )
                        .padding(horizontal = if (resolvedIsBottom) 8.dp else 6.dp, vertical = if (resolvedIsBottom) 6.dp else 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Security / Privacy Shield Favicon Button (Tapping opens Privacy & Tracker Shield HUD)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable(
                                    indication = ripple(bounded = true),
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                ) {
                                    val act = context as? androidx.activity.ComponentActivity
                                    if (!isBlankOrSearch && act != null) {
                                        try {
                                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).play(
                                                com.petal.browser.haptics.PetalHapticEngine.Pattern.HEAVY_CLICK,
                                                0.65f
                                            )
                                        } catch (_: Throwable) {}
                                        com.petal.browser.ui.components.PetalPrivacyShieldSheet.show(act, url) {}
                                    } else {
                                        onAddressClick()
                                    }
                                }
                        ) {
                            if (favicon != null && !isBlankOrSearch && !isIncognito) {
                                Image(
                                    bitmap = favicon.asImageBitmap(),
                                    contentDescription = "Favicon",
                                    modifier = Modifier.size(22.dp).clip(CircleShape)
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

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = formattedUrl,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

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
                                val action = RecordAction(context)
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
                                    val action = RecordAction(context)
                                    action.open(true)
                                    if (isBookmarked) {
                                        action.deleteURL(url, com.petal.browser.unit.RecordUnit.TABLE_BOOKMARK)
                                        isBookmarked = false
                                        PetalToast.show(context, "Bookmark removed")
                                    } else {
                                        action.addBookmark(Record(if (title.isNotBlank()) title else url, url, 0L, 0))
                                        isBookmarked = true
                                        PetalToast.show(context, "Saved to Bookmarks")
                                    }
                                    action.close()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (addressBarAction.equals("AI", true)) Icons.Rounded.AutoAwesome else if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (addressBarAction.equals("AI", true)) "Petal AI" else if (isBookmarked) "Remove bookmark" else "Bookmark",
                            tint = if (addressBarAction.equals("AI", true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Media sniffer badge: shown when playable streams are detected and button pref is on
                if (showMediaButton) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f)
                    ) {
                        Box(modifier = Modifier.padding(end = 2.dp)) {
                            IconButton(
                                onClick = {
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                    onMediaClick()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SmartDisplay,
                                    contentDescription = "Media found — tap to grab",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            // Red dot indicator
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                }

                // Far Right: Share Icon Button (min 48dp tap target)
                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    // Long-Press Quick Actions Bottom Sheet (Material 3 Expressive Containment)
    if (showQuickActionsMenu) {
        val haptics = com.petal.browser.haptics.PetalHapticEngine.getInstance(context)

        ModalBottomSheet(
            onDismissRequest = { showQuickActionsMenu = false },
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header site card
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
                                        modifier = Modifier.size(26.dp).clip(CircleShape)
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

                // Check bookmark state
                var isBookmarked by remember {
                    mutableStateOf(
                        try {
                            val action = RecordAction(context)
                            action.open(false)
                            val res = action.checkBookmark(url)
                            action.close()
                            res
                        } catch (e: Exception) { false }
                    )
                }

                // Clipboard for Paste & Go
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipText = clipboard.primaryClip?.let {
                    if (it.itemCount > 0) it.getItemAt(0)?.text?.toString()?.trim() else null
                }

                // Build list of actions for containment grouping
                data class QuickActionItem(
                    val icon: ImageVector,
                    val title: String,
                    val subtitle: String,
                    val iconContainerColor: Color,
                    val iconTint: Color,
                    val onClick: () -> Unit
                )

                val actions = mutableListOf<QuickActionItem>()

                // 1. Copy Clean URL
                actions.add(
                    QuickActionItem(
                        icon = Icons.Rounded.ContentCopy,
                        title = "Copy Clean URL",
                        subtitle = "Copies link with tracking parameters removed",
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {
                            showQuickActionsMenu = false
                            val cleanUrl = sanitizeTrackingParameters(url)
                            clipboard.setPrimaryClip(ClipData.newPlainText("Clean URL", cleanUrl))
                            PetalToast.show(context, "Clean URL copied to clipboard")
                        }
                    )
                )

                // 2. Paste & Go (if available)
                if (!clipText.isNullOrEmpty()) {
                    actions.add(
                        QuickActionItem(
                            icon = Icons.Rounded.ContentPasteGo,
                            title = "Paste & Go",
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

                // 3. Bookmark Toggle
                actions.add(
                    QuickActionItem(
                        icon = if (isBookmarked) Icons.Rounded.BookmarkRemove else Icons.Rounded.BookmarkAdd,
                        title = if (isBookmarked) "Remove from Bookmarks" else "Bookmark This Page",
                        subtitle = if (isBookmarked) "Tap to unbookmark this page" else "Save this page for quick access later",
                        iconContainerColor = if (isBookmarked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = if (isBookmarked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = {
                            try {
                                val action = RecordAction(context)
                                action.open(true)
                                if (isBookmarked) {
                                    action.deleteURL(url, com.petal.browser.unit.RecordUnit.TABLE_BOOKMARK)
                                    isBookmarked = false
                                    PetalToast.show(context, "Bookmark removed")
                                } else {
                                    val r = Record(if (title.isNotBlank()) title else url, url, 0L, 0)
                                    action.addBookmark(r)
                                    isBookmarked = true
                                    PetalToast.show(context, "Saved to Bookmarks")
                                }
                                action.close()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                )

                // 4. Hard Refresh (Bypasses Cache)
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

                // 5. Shield HUD
                actions.add(
                    QuickActionItem(
                        icon = Icons.Rounded.Shield,
                        title = "Shield HUD & Whitelist",
                        subtitle = "Inspect blocked trackers, ads, and connection certificate",
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = {
                            showQuickActionsMenu = false
                            val act = context as? androidx.activity.ComponentActivity
                            if (act != null) {
                                PetalPrivacyShieldSheet.show(act, url) {}
                            }
                        }
                    )
                )

                // 6. Petal AI Assist
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

                // Grouped Containment List
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    actions.forEachIndexed { index, item ->
                        val position = com.petal.browser.ui.containment.petalGroupPositionFor(index, actions.size)
                        val shape = com.petal.browser.ui.containment.petalGroupShape(position)

                        Surface(
                            shape = shape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .clickable {
                                    try {
                                        haptics.playIfEnabled(context, com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.75f)
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
