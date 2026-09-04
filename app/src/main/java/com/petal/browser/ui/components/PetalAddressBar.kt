package com.petal.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
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
import com.petal.browser.view.NinjaToast

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
@OptIn(ExperimentalMaterial3Api::class)
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
    onSiteControlsClick: () -> Unit = {},
    onAiResearchClick: () -> Unit = {},
    onSwipeNextTab: () -> Unit = {},
    onSwipePrevTab: () -> Unit = {},
    onPasteAndGo: (String) -> Unit = {},
    onHardRefresh: () -> Unit = {},
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

    val securityIcon: ImageVector = when {
        isIncognito -> Icons.Rounded.VisibilityOff
        isBlankOrSearch -> Icons.Rounded.Search
        isHttps || isHttp -> Icons.Rounded.Tune
        else -> Icons.Rounded.Search
    }

    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val isSwipeTabsEnabled = remember(sp) { sp.getBoolean("sp_address_bar_swipe_tabs", true) }
    val isQuickActionsEnabled = remember(sp) { sp.getBoolean("sp_address_bar_quick_actions", true) }

    var showQuickActionsMenu by remember { mutableStateOf(false) }

    val securityIconTint = when {
        isIncognito -> com.petal.browser.ui.theme.IncognitoPrimary
        isBlankOrSearch -> MaterialTheme.colorScheme.onSurfaceVariant
        isHttps || isHttp -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val containerColor = if (isIncognito) {
        com.petal.browser.ui.theme.IncognitoSurfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
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
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
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

                Spacer(modifier = Modifier.width(2.dp))

                // Center: Flexible Width URL Text & Favicon / Security Chip
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
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
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Favicon / Security capsule chip
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (isHttps || isHttp) {
                                        onSiteControlsClick()
                                    } else {
                                        onAddressClick()
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                if (favicon != null && !isBlankOrSearch && !isIncognito) {
                                    Image(
                                        bitmap = favicon.asImageBitmap(),
                                        contentDescription = "Favicon",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = securityIcon,
                                        contentDescription = "Site Controls and Security",
                                        tint = securityIconTint,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .popIn()
                                    )
                                }
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

                if (isProperSite) {
                    IconButton(
                        onClick = onAiResearchClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Petal AI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(2.dp))

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

            // Integrated Inline Loading Progress Indicator at base of pill
            val animatedProgress by animateFloatAsState(
                targetValue = if (isLoading) progress.coerceIn(0.05f, 1f) else 0f,
                label = "addressBarProgress"
            )
            if (isLoading && animatedProgress > 0f) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }
    }

    // Long-Press Quick Actions Bottom Sheet
    if (showQuickActionsMenu) {
        ModalBottomSheet(
            onDismissRequest = { showQuickActionsMenu = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header site info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (favicon != null && !isIncognito) {
                                Image(
                                    bitmap = favicon.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
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
                            text = url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Action 1: Copy Clean URL (strips tracking query params)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showQuickActionsMenu = false
                            val cleanUrl = sanitizeTrackingParameters(url)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Clean URL", cleanUrl))
                            NinjaToast.show(context, "Clean URL copied to clipboard")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Copy Clean URL", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Copies link with tracking parameters removed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Action 2: Paste & Go (if clipboard has content)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipText = clipboard.primaryClip?.let {
                    if (it.itemCount > 0) it.getItemAt(0)?.text?.toString()?.trim() else null
                }
                if (!clipText.isNullOrEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showQuickActionsMenu = false
                                onPasteAndGo(clipText)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(Icons.Rounded.ContentPasteGo, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Paste & Go", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(clipText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                // Action 3: Quick Bookmark Toggle
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

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val action = RecordAction(context)
                                action.open(true)
                                if (isBookmarked) {
                                    action.deleteURL(url, com.petal.browser.unit.RecordUnit.TABLE_BOOKMARK)
                                    isBookmarked = false
                                    NinjaToast.show(context, "Bookmark removed")
                                } else {
                                    val r = Record(if (title.isNotBlank()) title else url, url, 0L, 0)
                                    action.addBookmark(r)
                                    isBookmarked = true
                                    NinjaToast.show(context, "Saved to Bookmarks")
                                }
                                action.close()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Rounded.BookmarkRemove else Icons.Rounded.BookmarkAdd,
                            contentDescription = null,
                            tint = if (isBookmarked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isBookmarked) "Remove from Bookmarks" else "Bookmark This Page",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (isBookmarked) "Tap to unbookmark this page" else "Save this page for quick access later",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Action 4: Hard Refresh
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showQuickActionsMenu = false
                            onHardRefresh()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hard Refresh", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Reload page clearing cached resources", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
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
