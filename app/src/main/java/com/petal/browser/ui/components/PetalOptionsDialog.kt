package com.petal.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Refined Material 3 Expressive Options Sheet / Dialog for Petal Browser featuring
 * RvSystemMonitor position-aware containment, 48dp rounded badge icons, 0dp elevation, and smooth haptic styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalOptionsSheet(
    isDesktopSite: Boolean,
    onDesktopSiteChange: (Boolean) -> Unit,
    isIncognito: Boolean,
    onIncognitoChange: (Boolean) -> Unit,
    onNewTab: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onFindInPage: () -> Unit,
    onShare: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Browser Options",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp).entrance(index = 0)
            )

            // Top Quick Grid Action Tiles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(index = 1),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OptionTile(Icons.Rounded.Add, "New Tab", Modifier.weight(1f)) {
                    onNewTab()
                    onDismiss()
                }
                OptionTile(Icons.Rounded.Bookmarks, "Bookmarks", Modifier.weight(1f)) {
                    onBookmarks()
                    onDismiss()
                }
                OptionTile(Icons.Rounded.History, "History", Modifier.weight(1f)) {
                    onHistory()
                    onDismiss()
                }
                OptionTile(Icons.Rounded.Downloading, "Downloads", Modifier.weight(1f)) {
                    onDownloads()
                    onDismiss()
                }
            }

            // Toggles with RvSystemMonitor containment shape group
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(index = 2),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SwitchSettingItem(
                    title = "Desktop Mode",
                    subtitle = "Request desktop version of websites",
                    checked = isDesktopSite,
                    onCheckedChange = onDesktopSiteChange,
                    shape = getGroupItemShape(0, 2),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.DesktopWindows,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                )

                SwitchSettingItem(
                    title = "Private Browsing",
                    subtitle = "Don't save history or cookies",
                    checked = isIncognito,
                    onCheckedChange = onIncognitoChange,
                    shape = getGroupItemShape(1, 2),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                )
            }

            // Action Items Group with RvSystemMonitor variable corner shape
            val actionItems = listOf(
                Triple(Icons.Rounded.Search, "Find in Page", onFindInPage),
                Triple(Icons.Rounded.Share, "Share Web Page", onShare),
                Triple(Icons.Rounded.Settings, "Browser Settings", onSettings)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(index = 3),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                actionItems.forEachIndexed { index, (icon, label, action) ->
                    SettingsItem(
                        title = label,
                        subtitle = "",
                        shape = getGroupItemShape(index, actionItems.size),
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = {
                            action()
                            onDismiss()
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OptionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .height(76.dp)
            .bouncyClickable(scaleDown = 0.92f, onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
