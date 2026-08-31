package com.petal.browser.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Customizable link context menu dialog component styled with RvSystemMonitor containment principles.
 */
@Composable
fun PetalLinkContextMenuDialog(
    title: String,
    url: String,
    favicon: Bitmap? = null,
    isIncognito: Boolean = false,
    onOpenInNewTab: () -> Unit = {},
    onOpenInNewTabGroup: () -> Unit = {},
    onOpenInIncognito: () -> Unit = {},
    onOpenInNewWindow: () -> Unit = {},
    onPreviewPage: () -> Unit = {},
    onCopyLinkAddress: () -> Unit = {},
    onCopyLinkText: () -> Unit = {},
    onDownloadLink: () -> Unit = {},
    onAddToReadingList: () -> Unit = {},
    onShareLink: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = {
            isVisible = false
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable {
                    isVisible = false
                    onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessHigh)) +
                        scaleOut(targetScale = 0.9f, animationSpec = spring(stiffness = Spring.StiffnessHigh))
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isIncognito) Color(0xFF1C1D24) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth(0.92f)
                        .padding(16.dp)
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ── Header Section ───────────────────────────────────
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (favicon != null) {
                                        Image(
                                            bitmap = favicon.asImageBitmap(),
                                            contentDescription = "Site Favicon",
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Text(
                                            text = title.take(1).uppercase().ifBlank { "L" },
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title.ifBlank { "Link Options" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = url.ifBlank { "about:blank" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // ── Scrollable Menu Options ──────────────────────────
                        val actions = listOf(
                            Triple(Icons.Rounded.Tab, "Open in new tab") { onOpenInNewTab(); onDismiss() },
                            Triple(Icons.Rounded.TabUnselected, "Open in new tab in group") { onOpenInNewTabGroup(); onDismiss() },
                            Triple(Icons.Rounded.VisibilityOff, "Open in Incognito tab") { onOpenInIncognito(); onDismiss() },
                            Triple(Icons.Rounded.OpenInNew, "Open in new window") { onOpenInNewWindow(); onDismiss() },
                            Triple(Icons.Rounded.FindInPage, "Preview page") { onPreviewPage(); onDismiss() },
                            Triple(Icons.Rounded.ContentCopy, "Copy link address") { onCopyLinkAddress(); onDismiss() },
                            Triple(Icons.Rounded.TextFields, "Copy link text") { onCopyLinkText(); onDismiss() },
                            Triple(Icons.Rounded.Download, "Download link") { onDownloadLink(); onDismiss() },
                            Triple(Icons.Rounded.BookmarkBorder, "Add to reading list") { onAddToReadingList(); onDismiss() },
                            Triple(Icons.Rounded.Share, "Share link") { onShareLink(); onDismiss() }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            actions.forEachIndexed { index, (icon, label, onClick) ->
                                SettingsItem(
                                    title = label,
                                    subtitle = "",
                                    shape = getGroupItemShape(index, actions.size),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    },
                                    onClick = onClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
