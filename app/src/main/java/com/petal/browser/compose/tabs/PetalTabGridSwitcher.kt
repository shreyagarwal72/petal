package com.petal.browser.compose.tabs

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported

enum class TabDisplayMode {
    GRID,
    LIST
}

data class PetalTabItem(
    val id: String,
    val title: String,
    val url: String,
    val faviconBitmap: Bitmap? = null,
    val isIncognito: Boolean = false,
    val isSelected: Boolean = false
)

/**
 * Customizable Jetpack Compose Tab Grid Switcher component inspired by modern mobile browsers.
 */
@Composable
fun PetalTabGridSwitcher(
    tabs: List<PetalTabItem>,
    onTabSelect: (PetalTabItem) -> Unit,
    onTabClose: (PetalTabItem) -> Unit,
    onNewTab: (Boolean) -> Unit, // isIncognito
    onCloseAllTabs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var displayMode by remember { mutableStateOf(TabDisplayMode.GRID) }
    var isIncognitoFilter by remember { mutableStateOf(false) }
    var isOverflowMenuExpanded by remember { mutableStateOf(false) }

    val filteredTabs = remember(tabs, searchQuery, isIncognitoFilter) {
        tabs.filter { tab ->
            (tab.isIncognito == isIncognitoFilter) &&
            (searchQuery.isBlank() ||
             tab.title.contains(searchQuery, ignoreCase = true) ||
             tab.url.contains(searchQuery, ignoreCase = true))
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 1. Top Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Mode Selector (Normal vs Incognito Tabs)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !isIncognitoFilter,
                        onClick = { isIncognitoFilter = false },
                        label = {
                            val normalCount = tabs.count { !it.isIncognito }
                            Text("Tabs ($normalCount)", fontWeight = FontWeight.SemiBold)
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Tab, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        shape = RoundedCornerShape(20.dp)
                    )

                    FilterChip(
                        selected = isIncognitoFilter,
                        onClick = { isIncognitoFilter = true },
                        label = {
                            val incognitoCount = tabs.count { it.isIncognito }
                            Text("Incognito ($incognitoCount)", fontWeight = FontWeight.SemiBold)
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                // Right: Action Buttons (New Tab, Grid/List toggle, Overflow Menu)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // New Tab Button
                    IconButton(
                        onClick = { onNewTab(isIncognitoFilter) },
                        modifier = Modifier
                            .size(38.dp)
                            .bouncyClickable { onNewTab(isIncognitoFilter) }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New Tab",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Display Mode Switcher (Grid vs List)
                    IconButton(
                        onClick = {
                            displayMode = if (displayMode == TabDisplayMode.GRID) TabDisplayMode.LIST else TabDisplayMode.GRID
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (displayMode == TabDisplayMode.GRID) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = "Switch View Mode",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Overflow Menu
                    Box {
                        IconButton(
                            onClick = { isOverflowMenuExpanded = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Menu Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = isOverflowMenuExpanded,
                            onDismissRequest = { isOverflowMenuExpanded = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Tab") },
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                onClick = {
                                    isOverflowMenuExpanded = false
                                    onNewTab(false)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("New Incognito Tab") },
                                leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
                                onClick = {
                                    isOverflowMenuExpanded = false
                                    onNewTab(true)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Close All Tabs", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    isOverflowMenuExpanded = false
                                    onCloseAllTabs()
                                }
                            )
                        }
                    }
                }
            }

            // 2. Search & Filter Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                placeholder = { Text("Search open tabs...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            // 3. Responsive Tab Layout (Two-Column Grid or List View)
            if (filteredTabs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.TabUnselected,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching tabs found" else "No open tabs",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (displayMode == TabDisplayMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTabs, key = { it.id }) { tab ->
                        PetalTabCard(
                            tab = tab,
                            onTabSelect = { onTabSelect(tab) },
                            onTabClose = { onTabClose(tab) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTabs, key = { it.id }) { tab ->
                        PetalTabListItem(
                            tab = tab,
                            onTabSelect = { onTabSelect(tab) },
                            onTabClose = { onTabClose(tab) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PetalTabCard(
    tab: PetalTabItem,
    onTabSelect: () -> Unit,
    onTabClose: () -> Unit
) {
    val borderStroke = if (tab.isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = borderStroke,
        tonalElevation = if (tab.isSelected) 8.dp else 2.dp,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .bouncyClickable(onClick = onTabSelect)
            .entrance()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Card Header: Site Icon, Dynamic Title, Close Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tab.faviconBitmap != null) {
                        Image(
                            bitmap = tab.faviconBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = tab.title.take(1).uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Text(
                        text = if (tab.title.isBlank()) "New Tab" else tab.title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onTabClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close tab",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Page Preview Viewport
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (tab.isIncognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = tab.url.ifBlank { "about:blank" },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PetalTabListItem(
    tab: PetalTabItem,
    onTabSelect: () -> Unit,
    onTabClose: () -> Unit
) {
    val borderStroke = if (tab.isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .bouncyClickable(onClick = onTabSelect)
            .entrance()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (tab.faviconBitmap != null) {
                    Image(
                        bitmap = tab.faviconBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tab.title.take(1).uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (tab.title.isBlank()) "New Tab" else tab.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = tab.url.ifBlank { "about:blank" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onTabClose,
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

object PetalTabGridBridge {
    @JvmStatic
    fun createTabGridSwitcher(
        activity: ComponentActivity,
        tabs: List<PetalTabItem>,
        onTabSelectListener: (PetalTabItem) -> Unit,
        onTabCloseListener: (PetalTabItem) -> Unit,
        onNewTabListener: (Boolean) -> Unit,
        onCloseAllTabsListener: () -> Unit
    ): ComposeView {
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                val appFont = remember(fontName) {
                    try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.SYSTEM }
                }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalTabGridSwitcher(
                        tabs = tabs,
                        onTabSelect = onTabSelectListener,
                        onTabClose = onTabCloseListener,
                        onNewTab = onNewTabListener,
                        onCloseAllTabs = onCloseAllTabsListener
                    )
                }
            }
        }
    }
}

@Preview(name = "Tab Grid Switcher Preview", showBackground = true)
@Composable
private fun PetalTabGridSwitcherPreview() {
    val mockTabs = listOf(
        PetalTabItem("1", "Google Search", "https://google.com", isSelected = true),
        PetalTabItem("2", "GitHub Repository", "https://github.com"),
        PetalTabItem("3", "Wikipedia - Material 3", "https://wikipedia.org"),
        PetalTabItem("4", "Private Search", "https://duckduckgo.com", isIncognito = true)
    )
    PetalExpressiveTheme(darkTheme = true) {
        PetalTabGridSwitcher(
            tabs = mockTabs,
            onTabSelect = {},
            onTabClose = {},
            onNewTab = {},
            onCloseAllTabs = {}
        )
    }
}
