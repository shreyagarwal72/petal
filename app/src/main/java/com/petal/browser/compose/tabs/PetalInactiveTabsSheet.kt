package com.petal.browser.compose.tabs

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.petal.browser.predictive.PetalPredictiveBackSurface
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.ExpressiveTabGroupPill
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.components.entrance
import com.petal.browser.unit.HelperUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen page showing all Inactive / Archived tabs.
 * Material 3 Expressive redesign matching PetalTabGridSwitcher:
 * - Headline header with live count & layout toggle (Grid / List).
 * - 2-column LazyVerticalGrid with 0.68f aspect-ratio cards matching Tab Manager.
 * - Guarantee zero thumbnail caching: domain/search expressive preview placeholder.
 * - Alternative LazyColumn list mode with smooth animateItem() transitions.
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
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val thresholdDays = remember { PetalInactiveTabManager.getThresholdDays(context) }
    var searchQuery by remember { mutableStateOf("") }
    var displayMode by remember {
        val savedMode = sp.getString("sp_inactive_tab_display_mode", "GRID") ?: "GRID"
        mutableStateOf(try { TabDisplayMode.valueOf(savedMode) } catch (e: Exception) { TabDisplayMode.GRID })
    }

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
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                M3ExpressiveVariableBackground(pageSeed = "inactive_tabs_sheet")

                Column(modifier = Modifier.fillMaxSize()) {

                    // ── M3 Expressive Header matching Tab Manager ──
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
                        enableLiquidGlass = true,
                        actions = {
                            HeaderActionIcon(
                                icon = if (displayMode == TabDisplayMode.GRID) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                                contentDescription = "Toggle layout",
                                onClick = {
                                    val nextMode = if (displayMode == TabDisplayMode.GRID) TabDisplayMode.LIST else TabDisplayMode.GRID
                                    displayMode = nextMode
                                    sp.edit().putString("sp_inactive_tab_display_mode", nextMode.name).apply()
                                }
                            )

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
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // ── Bulk Actions ──
                    if (inactiveTabs.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                                    textAlign = TextAlign.Center,
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
                    } else if (displayMode == TabDisplayMode.GRID) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(filteredTabs, key = { it.id }) { tab ->
                                InactiveTabGridCard(
                                    tab = tab,
                                    onRestore = { onRestoreTab(tab) },
                                    onClose = { onCloseTab(tab) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredTabs, key = { it.id }) { tab ->
                                InactiveTabListItem(
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

/**
 * Grid card matching the exact visual style, proportions, and shape of PetalTabGridCard in Tab Manager.
 * Uses zero thumbnail caching: displays domain/search expressive preview placeholder.
 */
@Composable
private fun InactiveTabGridCard(
    tab: PetalInactiveTab,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val cardBg = MaterialTheme.colorScheme.surfaceContainerLow
    val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface
    val cardShape = RoundedCornerShape(18.dp)

    val groupColor = tab.groupColorHex?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
    }

    val borderStroke = if (groupColor != null) {
        BorderStroke(1.5.dp, groupColor.copy(alpha = 0.6f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    val titleText = tab.title.ifBlank { HelperUnit.domain(tab.url).ifBlank { "Untitled" } }

    Surface(
        shape = cardShape,
        color = cardBg,
        border = borderStroke,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .bouncyClickable(onClick = onRestore)
            .entrance()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Icon + Title + Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (tab.isDuplicateArchive) Icons.Rounded.ContentCopy else Icons.Rounded.History,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onClose)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close tab",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Tab Group indicator pill if tab belonged to a group
            if (tab.groupTitle != null && groupColor != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg)
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    ExpressiveTabGroupPill(
                        groupName = tab.groupTitle,
                        containerColor = groupColor,
                        contentColor = Color.White
                    )
                }
            }

            // Expressive domain preview (No disk/memory thumbnail caching)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                InactiveTabPreviewCard(tab = tab, accentColor = accentColor, onRestore = onRestore)
            }
        }
    }
}

/**
 * Expressive preview placeholder matching PetalHomePreviewCard layout.
 * Ensures zero thumbnail caching for archived tabs while displaying domain and restore prompt.
 */
@Composable
private fun InactiveTabPreviewCard(
    tab: PetalInactiveTab,
    accentColor: Color,
    onRestore: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val lastUsedStr = remember(tab.lastAccessedTimestamp) {
        if (tab.lastAccessedTimestamp > 0) {
            "Active ${dateFormatter.format(Date(tab.lastAccessedTimestamp))}"
        } else "Archived"
    }
    val domain = remember(tab.url) { HelperUnit.domain(tab.url).ifBlank { tab.url } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (tab.isDuplicateArchive) Icons.Rounded.ContentCopy else Icons.Rounded.HistoryToggleOff,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            FilledTonalButton(
                onClick = onRestore,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Unarchive,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Restore", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
            }

            Text(
                text = lastUsedStr,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * List row item matching PetalTabListItem styling in Tab Manager.
 */
@Composable
private fun InactiveTabListItem(
    tab: PetalInactiveTab,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val cardBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface

    val groupColor = tab.groupColorHex?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
    }

    val borderStroke = if (groupColor != null) {
        BorderStroke(1.5.dp, groupColor.copy(alpha = 0.8f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }

    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val lastUsedStr = remember(tab.lastAccessedTimestamp) {
        if (tab.lastAccessedTimestamp > 0) {
            "Last active ${dateFormatter.format(Date(tab.lastAccessedTimestamp))}"
        } else "Archived"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = borderStroke,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .bouncyClickable(onClick = onRestore)
            .entrance()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(54.dp, 48.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (tab.isDuplicateArchive) Icons.Rounded.ContentCopy else Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tab.title.ifBlank { HelperUnit.domain(tab.url).ifBlank { "Untitled" } },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (tab.groupTitle != null && groupColor != null) {
                            ExpressiveTabGroupPill(
                                groupName = tab.groupTitle,
                                containerColor = groupColor,
                                contentColor = Color.White
                            )
                        }
                    }
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
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close tab",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

