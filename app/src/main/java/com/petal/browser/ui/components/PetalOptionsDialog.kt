package com.petal.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

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
    com.petal.browser.ui.containment.PetalSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            com.petal.browser.ui.containment.PetalSectionLabel("Browser Options", Modifier.entrance(index = 0))

            // Top Quick Grid Action Tiles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(index = 1),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.petal.browser.ui.containment.PetalActionCard(onClick = { onNewTab(); onDismiss() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize().height(76.dp).padding(6.dp)) {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(Icons.Filled.Add)
                        Text(stringResource(R.string.ui_new_tab), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                com.petal.browser.ui.containment.PetalActionCard(onClick = { onBookmarks(); onDismiss() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize().height(76.dp).padding(6.dp)) {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(Icons.Filled.Bookmarks)
                        Text(stringResource(R.string.ui_bookmarks), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                com.petal.browser.ui.containment.PetalActionCard(onClick = { onHistory(); onDismiss() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize().height(76.dp).padding(6.dp)) {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(Icons.Filled.History)
                        Text(stringResource(R.string.ui_history), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                com.petal.browser.ui.containment.PetalActionCard(onClick = { onDownloads(); onDismiss() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize().height(76.dp).padding(6.dp)) {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(Icons.Filled.Download)
                        Text(stringResource(R.string.ui_downloads), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }

            // Toggles with RvSystemMonitor containment shape group
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(index = 2),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                com.petal.browser.ui.containment.PetalGroupToggleRow(Icons.Filled.DesktopWindows, "Desktop Mode", "Request desktop version of websites", isDesktopSite, onDesktopSiteChange, com.petal.browser.ui.containment.PetalGroupPosition.TOP)
                com.petal.browser.ui.containment.PetalGroupToggleRow(Icons.Filled.Security, "Private Browsing", "Don't save history or cookies", isIncognito, onIncognitoChange, com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM)
            }

            // Action Items Group with RvSystemMonitor variable corner shape
            val actionItems = listOf(
                Triple(Icons.Filled.Search, "Find in Page", onFindInPage),
                Triple(Icons.Filled.Share, "Share Web Page", onShare),
                Triple(Icons.Filled.Settings, "Browser Settings", onSettings)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(index = 3),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                actionItems.forEachIndexed { index, (icon, label, action) ->
                    com.petal.browser.ui.containment.PetalGroupRow(
                        icon = icon,
                        title = label,
                        position = com.petal.browser.ui.containment.petalGroupPositionFor(index, actionItems.size),
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
