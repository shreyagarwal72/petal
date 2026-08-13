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
 * Single pill squircle container row matching modern Android browser design:
 * - Far left: Back/Navigation arrow icon button or Stop button when loading (min 48dp touch target, 24dp icon)
 * - Security/Page Controls Icon: Tune (HTTPS/HTTP) / Search (blank) / VisibilityOff (Incognito)
 * - Center: Flexible width URL text (root domain highlighted, path muted, single line, end ellipsis)
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
    onShareClick: () -> Unit,
    onAddressClick: () -> Unit,
    onSiteControlsClick: () -> Unit = {},
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

    val securityIconTint = when {
        isIncognito -> com.petal.browser.ui.theme.IncognitoPrimary
        isBlankOrSearch -> MaterialTheme.colorScheme.onSurfaceVariant
        isHttps || isHttp -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val containerColor = if (isIncognito) {
        com.petal.browser.ui.theme.IncognitoSurfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)
    }

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

            // Center: Flexible Width URL Text & Site Controls / Security Icon
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
                    IconButton(
                        onClick = {
                            if (isHttps || isHttp) {
                                onSiteControlsClick()
                            } else {
                                onAddressClick()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = securityIcon,
                            contentDescription = "Site Controls and Security",
                            tint = securityIconTint,
                            modifier = Modifier
                                .size(18.dp)
                                .popIn()
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = formattedUrl,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
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
