package com.petal.browser.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Collapsed/Scrolled Address Bar.
 * Single pill squircle container row matching modern Android browser design:
 * - Far left: Back/Navigation arrow icon button (min 48dp touch target, 24dp icon)
 * - Security Icon: Lock (HTTPS) / Warning (HTTP) / Search (blank)
 * - Center: Flexible width URL text (single line, end ellipsis)
 * - Far right: Share icon button (min 48dp touch target, 24dp icon)
 */
@Composable
fun PetalAddressBar(
    url: String,
    title: String,
    isIncognito: Boolean = false,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onAddressClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBlankOrSearch = url.isEmpty() || url == "about:blank" || url.startsWith("file:///android_asset/")
    val isHttps = url.startsWith("https://")
    val isHttp = url.startsWith("http://")

    val displayUrl = when {
        isBlankOrSearch -> "Search or type URL"
        isHttps -> url.substring(8)
        isHttp -> url.substring(7)
        else -> url
    }

    val securityIcon: ImageVector = when {
        isBlankOrSearch -> Icons.Rounded.Search
        isHttps -> Icons.Rounded.Lock
        isHttp -> Icons.Rounded.Warning
        else -> Icons.Rounded.Search
    }

    val securityIconTint = when {
        isBlankOrSearch -> MaterialTheme.colorScheme.onSurfaceVariant
        isHttps -> MaterialTheme.colorScheme.primary
        isHttp -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val containerColor = if (isIncognito) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f)
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
            // Far Left: Back Navigation Icon Button (min 48dp tap target)
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Center: Flexible Width URL Text & Security Icon
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
                        imageVector = securityIcon,
                        contentDescription = "Security Status",
                        tint = securityIconTint,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(18.dp)
                            .popIn()
                    )

                    Text(
                        text = displayUrl,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
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
