package com.petal.browser.compose.tabs

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
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

@Composable
fun PetalTabGridSwitcher(
    tabs: List<PetalTabItem>,
    onTabSelect: (PetalTabItem) -> Unit,
    onTabClose: (PetalTabItem) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onCloseAllTabs: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var displayMode by remember { mutableStateOf(TabDisplayMode.GRID) }
    var isIncognitoMode by remember { mutableStateOf(tabs.any { it.isSelected && it.isIncognito }) }
    var isOverflowMenuExpanded by remember { mutableStateOf(false) }

    val standardCount = remember(tabs) { tabs.count { !it.isIncognito } }
    val incognitoCount = remember(tabs) { tabs.count { it.isIncognito } }

    val currentModeTabs = remember(tabs, isIncognitoMode) {
        tabs.filter { it.isIncognito == isIncognitoMode }
    }

    val filteredTabs = remember(currentModeTabs, searchQuery) {
        currentModeTabs.filter { tab ->
            searchQuery.isBlank() ||
            tab.title.contains(searchQuery, ignoreCase = true) ||
            tab.url.contains(searchQuery, ignoreCase = true)
        }
    }

    val backgroundColor = if (isIncognitoMode) Color(0xFF121318) else MaterialTheme.colorScheme.background
    val topBarColor = if (isIncognitoMode) Color(0xFF1C1D24) else MaterialTheme.colorScheme.surfaceContainerHigh
    val accentColor = if (isIncognitoMode) Color(0xFFA8C7FA) else MaterialTheme.colorScheme.primary
    val textColor = if (isIncognitoMode) Color(0xFFE2E2E9) else MaterialTheme.colorScheme.onSurface

    Surface(
        color = backgroundColor,
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Action Bar with Stealth Theme for Incognito Mode
            Surface(
                color = topBarColor,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Standard vs Incognito Dual-Mode Switcher
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.height(38.dp)
                        ) {
                            SegmentedButton(
                                selected = !isIncognitoMode,
                                onClick = { isIncognitoMode = false },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = if (isIncognitoMode) Color(0xFF2B2C36) else MaterialTheme.colorScheme.primaryContainer,
                                    activeContentColor = if (isIncognitoMode) Color(0xFFE2E2E9) else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.Tab, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("$standardCount", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            SegmentedButton(
                                selected = isIncognitoMode,
                                onClick = { isIncognitoMode = true },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = Color(0xFF333542),
                                    activeContentColor = Color(0xFFA8C7FA)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("$incognitoCount", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        // Right: Primary Add Button (+), View Switcher, 3-Dots Menu
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Primary (+) Add Tab Button
                            IconButton(
                                onClick = { onNewTab(isIncognitoMode) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "New Tab",
                                    tint = if (isIncognitoMode) Color(0xFF121318) else MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // View Switcher (Grid vs List)
                            IconButton(
                                onClick = {
                                    displayMode = if (displayMode == TabDisplayMode.GRID) TabDisplayMode.LIST else TabDisplayMode.GRID
                                }
                            ) {
                                Icon(
                                    imageVector = if (displayMode == TabDisplayMode.GRID) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                                    contentDescription = "Toggle Grid/List",
                                    tint = textColor
                                )
                            }

                            // 3-Dots Overflow Menu
                            Box {
                                IconButton(onClick = { isOverflowMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "Options",
                                        tint = textColor
                                    )
                                }

                                DropdownMenu(
                                    expanded = isOverflowMenuExpanded,
                                    onDismissRequest = { isOverflowMenuExpanded = false },
                                    shape = RoundedCornerShape(16.dp),
                                    containerColor = if (isIncognitoMode) Color(0xFF24252E) else MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("New Tab", color = if (isIncognitoMode) Color.White else MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, tint = accentColor) },
                                        onClick = {
                                            isOverflowMenuExpanded = false
                                            onNewTab(false)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("New Incognito Tab", color = if (isIncognitoMode) Color.White else MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = accentColor) },
                                        onClick = {
                                            isOverflowMenuExpanded = false
                                            onNewTab(true)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings", color = if (isIncognitoMode) Color.White else MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null, tint = accentColor) },
                                        onClick = {
                                            isOverflowMenuExpanded = false
                                            onOpenSettings()
                                        }
                                    )
                                    HorizontalDivider(color = if (isIncognitoMode) Color(0xFF383944) else MaterialTheme.colorScheme.outlineVariant)
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

                    Spacer(Modifier.height(10.dp))

                    // Real-Time Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = if (isIncognitoMode) "Search incognito tabs..." else "Search open tabs...",
                                color = if (isIncognitoMode) Color(0xFF8E909F) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = if (isIncognitoMode) Color(0xFF8E909F) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Clear",
                                        tint = if (isIncognitoMode) Color(0xFF8E909F) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isIncognitoMode) Color(0xFF24252E) else MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = if (isIncognitoMode) Color(0xFF1E1F26) else MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 2. Responsive Two-Column LazyVerticalGrid with Empty State Handling
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (filteredTabs.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isIncognitoMode) Color(0xFF24252E) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isIncognitoMode) Icons.Rounded.VisibilityOff else Icons.Rounded.TabUnselected,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = when {
                                searchQuery.isNotEmpty() -> "No matching tabs"
                                isIncognitoMode -> "No Incognito tabs open"
                                else -> "No Standard tabs open"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (isIncognitoMode) "Pages you view in incognito won't leave a local trace" else "Tap '+' to open a new tab",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isIncognitoMode) Color(0xFF8E909F) else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { onNewTab(isIncognitoMode) },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = if (isIncognitoMode) Color(0xFF121318) else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open New Tab", fontWeight = FontWeight.Bold)
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
                            val dismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart ||
                                    dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd
                                ) {
                                    onTabClose(tab)
                                }
                            }

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(MaterialTheme.colorScheme.errorContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Close",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                },
                                modifier = Modifier.animateItem()
                            ) {
                                PetalTabCard(
                                    tab = tab,
                                    isIncognitoMode = isIncognitoMode,
                                    accentColor = accentColor,
                                    onTabSelect = { onTabSelect(tab) },
                                    onTabClose = { onTabClose(tab) }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredTabs, key = { it.id }) { tab ->
                            val dismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart ||
                                    dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd
                                ) {
                                    onTabClose(tab)
                                }
                            }

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.errorContainer),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Close",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(end = 16.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.animateItem()
                            ) {
                                PetalTabListItem(
                                    tab = tab,
                                    isIncognitoMode = isIncognitoMode,
                                    accentColor = accentColor,
                                    onTabSelect = { onTabSelect(tab) },
                                    onTabClose = { onTabClose(tab) }
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
private fun PetalTabCard(
    tab: PetalTabItem,
    isIncognitoMode: Boolean,
    accentColor: Color,
    onTabSelect: () -> Unit,
    onTabClose: () -> Unit
) {
    val cardBg = if (isIncognitoMode) Color(0xFF1E1F26) else MaterialTheme.colorScheme.surfaceContainerHigh
    val headerBg = if (isIncognitoMode) Color(0xFF2B2C36) else MaterialTheme.colorScheme.surfaceContainer
    val textColor = if (isIncognitoMode) Color(0xFFE2E2E9) else MaterialTheme.colorScheme.onSurface

    val borderStroke = if (tab.isSelected) {
        BorderStroke(2.dp, accentColor)
    } else {
        BorderStroke(1.dp, if (isIncognitoMode) Color(0xFF383944) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
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
                            color = accentColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = tab.title.take(1).uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = accentColor
                                )
                            }
                        }
                    }

                    Text(
                        text = if (tab.title.isBlank()) "New Tab" else tab.title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = textColor,
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
                        tint = if (isIncognitoMode) Color(0xFF8E909F) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (isIncognitoMode) {
                                listOf(Color(0xFF1E1F26), Color(0xFF16171D))
                            } else {
                                listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainer)
                            }
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
                        tint = accentColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = tab.url.ifBlank { "about:blank" },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (isIncognitoMode) Color(0xFF8E909F) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
    isIncognitoMode: Boolean,
    accentColor: Color,
    onTabSelect: () -> Unit,
    onTabClose: () -> Unit
) {
    val cardBg = if (isIncognitoMode) Color(0xFF1E1F26) else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (isIncognitoMode) Color(0xFFE2E2E9) else MaterialTheme.colorScheme.onSurface

    val borderStroke = if (tab.isSelected) {
        BorderStroke(2.dp, accentColor)
    } else {
        BorderStroke(1.dp, if (isIncognitoMode) Color(0xFF383944) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
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
                        color = accentColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tab.title.take(1).uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = accentColor
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (tab.title.isBlank()) "New Tab" else tab.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = tab.url.ifBlank { "about:blank" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isIncognitoMode) Color(0xFF8E909F) else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    tint = if (isIncognitoMode) Color(0xFF8E909F) else MaterialTheme.colorScheme.onSurfaceVariant,
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
        onCloseAllTabsListener: () -> Unit,
        onOpenSettingsListener: () -> Unit
    ): ComposeView {
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                val appFont = remember(fontName) {
                    try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.GS_FLEX }
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
                        onCloseAllTabs = onCloseAllTabsListener,
                        onOpenSettings = onOpenSettingsListener
                    )
                }
            }
        }
    }
}
