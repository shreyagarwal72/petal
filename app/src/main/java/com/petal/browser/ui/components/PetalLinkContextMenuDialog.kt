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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Customizable, production-ready link context menu dialog component inspired by modern mobile browsers.
 * Features:
 * 1. Dark-themed floating card with heavily rounded corners (28dp).
 * 2. Header displaying website favicon / monogram avatar, page title, and clean truncated URL.
 * 3. Vertically scrollable list of actions:
 *    - Open in new tab
 *    - Open in new tab in group
 *    - Open in Incognito tab
 *    - Open in new window
 *    - Preview page
 *    - Copy link address
 *    - Copy link text
 *    - Download link
 *    - Add to reading list
 *    - Share link (with quick-share trailing app icon)
 * 4. Physics-based 60fps scale & fade opening and closing transitions.
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

    val cardBg = if (isIncognito) Color(0xFF1C1D24) else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
    val subtextColor = if (isIncognito) Color(0xFFA8C7FA) else MaterialTheme.colorScheme.primary
    val dividerColor = if (isIncognito) Color(0xFF2E2F3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

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
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = cardBg,
                    shadowElevation = 12.dp,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth(0.9f)
                        .padding(16.dp)
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        // ── Header Section ───────────────────────────────────
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                        ) {
                            if (favicon != null) {
                                Image(
                                    bitmap = favicon.asImageBitmap(),
                                    contentDescription = "Site Favicon",
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isIncognito) Color(0xFF2D2F3C) else MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = title.take(1).uppercase().ifBlank { "L" },
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isIncognito) Color(0xFFA8C7FA) else MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title.ifBlank { "Link Options" },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = url.ifBlank { "about:blank" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subtextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = dividerColor)
                        Spacer(Modifier.height(4.dp))

                        // ── Scrollable Menu Options ──────────────────────────
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            ContextMenuItem(
                                icon = Icons.Rounded.Tab,
                                label = "Open in new tab",
                                textColor = textColor,
                                onClick = {
                                    onOpenInNewTab()
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Rounded.TabUnselected,
                                label = "Open in new tab in group",
                                textColor = textColor,
                                onClick = {
                                    onOpenInNewTabGroup()
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Rounded.VisibilityOff,
                                label = "Open in Incognito tab",
                                textColor = textColor,
                                onClick = {
                                    onOpenInIncognito()
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Rounded.OpenInNew,
                                label = "Open in new window",
                                textColor = textColor,
                                onClick = {
                                    onOpenInNewWindow()
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Rounded.FindInPage,
                                label = "Preview page",
                                textColor = textColor,
                                onClick = {
                                    onPreviewPage()
                                    onDismiss()
                                }
                            )

                            HorizontalDivider(
                                color = dividerColor,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)
                            )

                            ContextMenuItem(
                                icon = Icons.Rounded.ContentCopy,
                                label = "Copy link address",
                                textColor = textColor,
                                onClick = {
                                    onCopyLinkAddress()
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Rounded.TextFields,
                                label = "Copy link text",
                                textColor = textColor,
                                onClick = {
                                    onCopyLinkText()
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Rounded.Download,
                                label = "Download link",
                                textColor = textColor,
                                onClick = {
                                    onDownloadLink()
                                    onDismiss()
                                }
                            )

                            ContextMenuItem(
                                icon = Icons.Rounded.BookmarkBorder,
                                label = "Add to reading list",
                                textColor = textColor,
                                onClick = {
                                    onAddToReadingList()
                                    onDismiss()
                                }
                            )

                            // Share link with quick trailing share app icon
                            ContextMenuItem(
                                icon = Icons.Rounded.Share,
                                label = "Share link",
                                textColor = textColor,
                                trailingIcon = Icons.Rounded.IosShare,
                                onClick = {
                                    onShareLink()
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    textColor: Color,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(14.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = textColor,
                modifier = Modifier.weight(1f)
            )

            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
