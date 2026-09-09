package com.petal.browser.compose.tabs

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.unit.HelperUnit
import com.petal.browser.predictive.PetalPredictiveBackSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen bottom sheet showing all Inactive / Archived tabs.
 * M3 Expressive redesign: headline header with live count, segmented action buttons,
 * FilledIconButton per-tab restore, animateItem() smooth list removal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalInactiveTabsSheet(
    inactiveTabs: List<PetalInactiveTab>,
    onRestoreTab: (PetalInactiveTab) -> Unit,
    onRestoreAllTabs: () -> Unit,
    onCloseTab: (PetalInactiveTab) -> Unit,
    onCloseAllInactive: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thresholdDays = remember { PetalInactiveTabManager.getThresholdDays(context) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTabs = remember(inactiveTabs, searchQuery) {
        if (searchQuery.isBlank()) inactiveTabs
        else {
            inactiveTabs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.url.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    PetalPredictiveBackSurface(
        enabled = true,
        onBack = onDismiss
    ) {
    // Inactive tabs is a full navigation page, not a bottom-sheet overlay.
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            M3ExpressiveVariableBackground(pageSeed = "inactive_tabs_sheet")

            Column(modifier = Modifier.fillMaxSize()) {

                // ── M3 Expressive Header (shared component, matches rest of app) ──
                val subtitleText = remember(thresholdDays, inactiveTabs.size) {
                    if (thresholdDays > 0) {
                        "Idle for $thresholdDays day${if (thresholdDays == 1) "" else "s"} · ${inactiveTabs.size} archived"
                    } else {
                        "${inactiveTabs.size} archived & duplicate tabs"
                    }
                }
                ExpressiveHeader(
                    title = "Inactive Tabs",
                    subtitle = subtitleText,
                    onBack = onDismiss,
                    actions = {
                        HeaderActionIcon(
                            icon = Icons.Rounded.Settings,
                            contentDescription = "Inactive Settings",
                            onClick = onOpenSettings
                        )
                    }
                )

                // ── Search Box ──
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search inactive tabs…") },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )

                // ── Bulk Actions ──
                if (inactiveTabs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onRestoreAllTabs,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Unarchive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Restore all (${inactiveTabs.size})")
                        }

                        OutlinedButton(
                            onClick = onCloseAllInactive,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Close all")
                        }
                    }
                }

                // ── Content ──
                if (filteredTabs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.TabUnselected,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Text(
                                text = "No inactive tabs",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tabs you haven't used in a while will appear here to reduce clutter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(0.85f)
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = onOpenSettings,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Adjust inactivity settings")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredTabs, key = { it.id }) { tab ->
                            InactiveTabItemCard(
                                tab = tab,
                                onRestore = { onRestoreTab(tab) },
                                onClose = { onCloseTab(tab) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun InactiveTabItemCard(
    tab: PetalInactiveTab,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val lastUsedStr = remember(tab.lastAccessedTimestamp) {
        if (tab.lastAccessedTimestamp > 0) {
            "Last active ${dateFormatter.format(Date(tab.lastAccessedTimestamp))}"
        } else "Archived"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onRestore() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (tab.isDuplicateArchive) Icons.Rounded.ContentCopy else Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = tab.title.ifBlank { HelperUnit.domain(tab.url).ifBlank { "Untitled" } },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = HelperUnit.domain(tab.url).ifBlank { tab.url },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = lastUsedStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledIconButton(
                    onClick = onRestore,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Unarchive,
                        contentDescription = "Restore tab",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close tab",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
