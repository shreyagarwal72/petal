package com.petal.browser.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Collapsed/Scrolled Address Bar.
 * Single pill squircle container row matching modern Chrome-style browser design:
 * - Far left: Back/Navigation icon button that morphs into a Stop action while loading,
 *   and reflects `canGoBack` as its enabled state (min 48dp touch target, 24dp icon)
 * - Site controls icon: Tune icon on HTTP/HTTPS pages (opens permissions/SSL details),
 *   Search icon on the blank/new-tab state, VisibilityOff while incognito
 * - Center: Flexible width URL text, root domain emphasized, subdomain/path muted
 * - Far right: Share icon button (min 48dp touch target, 24dp icon)
 */
@Composable
fun PetalAddressBar(
    url: String,
    title: String,
    isIncognito: Boolean = false,
    isLoading: Boolean = false,
    canGoBack: Boolean = true,
    onBackClick: () -> Unit,
    onStopClick: () -> Unit = onBackClick,
    onShareClick: () -> Unit,
    onAddressClick: () -> Unit,
    onSiteControlsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isBlankOrSearch = url.isEmpty() || url == "about:blank" || url.startsWith("file:///android_asset/")
    val isHttps = url.startsWith("https://")
    val isHttp = url.startsWith("http://")
    val isRealSite = isHttps || isHttp

    val displayUrl = when {
        isBlankOrSearch -> "Search or type URL"
        isHttps -> url.substring(8)
        isHttp -> url.substring(7)
        else -> url
    }

    // Site controls icon: Tune for actual HTTP/HTTPS pages, preserving the
    // incognito / blank-state affordances the bar already had.
    val siteControlsIcon: ImageVector = when {
        isIncognito -> Icons.Rounded.VisibilityOff
        isBlankOrSearch -> Icons.Rounded.Search
        isRealSite -> Icons.Rounded.Tune
        else -> Icons.Rounded.Search
    }

    val siteControlsTint = when {
        isIncognito -> com.petal.browser.ui.theme.IncognitoPrimary
        isBlankOrSearch -> MaterialTheme.colorScheme.onSurfaceVariant
        isHttps -> MaterialTheme.colorScheme.primary
        isHttp -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val containerColor = if (isIncognito) {
        com.petal.browser.ui.theme.IncognitoSurfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)
    }

    // Back button morphs into a Stop action while the page is loading; otherwise
    // its enabled/disabled state reflects whether back navigation is possible.
    val backIcon = if (isLoading) Icons.Rounded.Close else Icons.Rounded.ArrowBack
    val backContentDescription = if (isLoading) "Stop" else "Back"
    val backEnabled = isLoading || canGoBack
    val backTint = if (backEnabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    val mutedUrlColor = MaterialTheme.colorScheme.onSurfaceVariant
    val emphasisUrlColor = MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .entrance()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Far Left: Back / Stop Navigation Icon Button (min 48dp tap target)
            IconButton(
                onClick = if (isLoading) onStopClick else onBackClick,
                enabled = backEnabled,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = backIcon,
                    contentDescription = backContentDescription,
                    tint = backTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Center: Flexible Width URL Text & Site Controls Icon
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onAddressClick() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = siteControlsIcon,
                        contentDescription = if (isRealSite) "Site controls" else "Security Status",
                        tint = siteControlsTint,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(18.dp)
                            .popIn()
                            .let { iconModifier ->
                                if (isRealSite) {
                                    iconModifier.clickable(onClick = onSiteControlsClick)
                                } else {
                                    iconModifier
                                }
                            }
                    )

                    Text(
                        text = formatDisplayUrl(displayUrl, isBlankOrSearch, mutedUrlColor, emphasisUrlColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
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
    }
}

/**
 * Builds the address bar's text with the root domain emphasized and the
 * subdomain/path muted, Chrome-style. `displayUrl` is expected to already
 * have the scheme stripped (see [PetalAddressBar]'s `displayUrl`).
 *
 * Root domain is approximated as the last two dot-separated labels of the
 * host (e.g. "example.com" in "www.example.com/path"). This is a simple
 * heuristic, not a full public-suffix-list lookup, so multi-part public
 * suffixes (e.g. "co.uk") will still highlight only the last two labels.
 */
private fun formatDisplayUrl(
    displayUrl: String,
    isBlankOrSearch: Boolean,
    mutedColor: Color,
    emphasisColor: Color
): AnnotatedString {
    if (isBlankOrSearch) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = mutedColor)) {
                append(displayUrl)
            }
        }
    }

    val slashIndex = displayUrl.indexOf('/')
    val host = if (slashIndex == -1) displayUrl else displayUrl.substring(0, slashIndex)
    val path = if (slashIndex == -1) "" else displayUrl.substring(slashIndex)

    val labels = host.split(".")
    val rootDomain = if (labels.size > 2) labels.takeLast(2).joinToString(".") else host
    val subdomainPrefixLength = host.length - rootDomain.length

    return buildAnnotatedString {
        if (subdomainPrefixLength > 0) {
            withStyle(SpanStyle(color = mutedColor)) {
                append(host.substring(0, subdomainPrefixLength))
            }
        }
        withStyle(SpanStyle(color = emphasisColor, fontWeight = FontWeight.SemiBold)) {
            append(rootDomain)
        }
        if (path.isNotEmpty()) {
            withStyle(SpanStyle(color = mutedColor)) {
                append(path)
            }
        }
    }
}
