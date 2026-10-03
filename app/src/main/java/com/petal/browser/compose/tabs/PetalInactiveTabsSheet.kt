package com.petal.browser.compose.tabs

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.petalGroupPositionFor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

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
                                contentDescription = stringResource(R.string.ui_inactive_settings),
                                onClick = onOpenSettings
                            )
                        }
                    )

                    // ── Search Box ──
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.ui_search_inactive_tabs)) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.ui_clear),
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
                                    Icons.Filled.Unarchive,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.ui_restore_all, inactiveTabs.size))
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
                                    Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.ui_close_all))
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
                                PetalGroupIconBadge(
                                    icon = Icons.Filled.TabUnselected,
                                    size = 72.dp,
                                    iconSize = 32.dp,
                                )
                                Text(
                                    text = stringResource(R.string.ui_no_inactive_tabs),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.ui_tabs_you_haven_t_used),
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
                                        Icons.Filled.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.ui_adjust_inactivity_settings))
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
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            itemsIndexed(filteredTabs, key = { _, tab -> tab.id }) { index, tab ->
                                InactiveTabListItem(
                                    tab = tab,
                                    position = petalGroupPositionFor(index, filteredTabs.size),
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

    val groupColor = tab.groupColorHex?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
    }

    val titleText = tab.title.ifBlank { HelperUnit.domain(tab.url).ifBlank { "Untitled" } }

    PetalHeroCard(
        shape = RoundedCornerShape(32.dp),
        containerColor = cardBg,
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
                                imageVector = if (tab.isDuplicateArchive) Icons.Filled.ContentCopy else Icons.Filled.History,
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
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.ui_close_tab_2),
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
                    imageVector = if (tab.isDuplicateArchive) Icons.Filled.ContentCopy else Icons.Filled.HistoryToggleOff,
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
                        imageVector = Icons.Filled.Language,
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
                imageVector = Icons.Filled.Unarchive,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ui_restore), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
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
    position: PetalGroupPosition = PetalGroupPosition.SINGLE,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface

    val groupColor = tab.groupColorHex?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
    }

    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val lastUsedStr = remember(tab.lastAccessedTimestamp) {
        if (tab.lastAccessedTimestamp > 0) {
            "Last active ${dateFormatter.format(Date(tab.lastAccessedTimestamp))}"
        } else "Archived"
    }

    PetalGroupListRow(
        position = position,
        onClick = onRestore,
        modifier = modifier
            .fillMaxWidth()
            .entrance(),
        leading = {
            PetalGroupIconBadge(
                icon = if (tab.isDuplicateArchive) Icons.Filled.ContentCopy else Icons.Filled.History,
                container = if (tab.isDuplicateArchive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                tint = if (tab.isDuplicateArchive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                size = 44.dp,
                iconSize = 22.dp,
            )
        },
        content = {
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
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRestore, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Unarchive, contentDescription = stringResource(R.string.ui_restore_tab), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ui_close_tab_2), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        },
    )
}
