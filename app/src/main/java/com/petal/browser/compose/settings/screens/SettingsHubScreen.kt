package com.petal.browser.compose.settings.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petal.browser.compose.settings.PetalSettingsSearchIndex
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalSectionLabel
import com.petal.browser.ui.containment.petalGroupPositionFor

/**
 * Main Settings Hub Screen matching RvSystem-Monitor's SettingsScreen.kt visual structure:
 * - ExpressiveHeader with title, subtitle, and back button
 * - Settings search input with clear button
 * - Typo tolerance / "Did you mean?" suggestion chip banner
 * - Search results across all settings category contents & preferences
 * - LazyColumn of grouped rows using Petal containment surfaces
 * - 48dp icon inside primary-tinted rounded box, title, subtitle, trailing chevron
 */
@Composable
fun SettingsHubScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCategoryClick: (SettingsCategory, String?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchResult = remember(searchQuery) {
        PetalSettingsSearchIndex.search(searchQuery)
    }

    val isSearching = searchQuery.isNotBlank()
    val filteredCategories = searchResult.matchingCategories
    val matchingItems = searchResult.matchingItems
    val didYouMean = searchResult.didYouMean

    var isSearchOpen by rememberSaveable { mutableStateOf(searchQuery.isNotBlank()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen && searchQuery.isEmpty()) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        M3ExpressiveVariableBackground(pageSeed = "settings_hub")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Settings",
                subtitle = "Browser Preferences & Customization",
                onBack = onNavigateBack,
                actions = {
                    HeaderActionIcon(
                        icon = if (isSearchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = if (isSearchOpen) "Close search" else "Search settings",
                        onClick = {
                            if (isSearchOpen) {
                                isSearchOpen = false
                                onSearchQueryChange("")
                            } else {
                                isSearchOpen = true
                            }
                        }
                    )
                }
            )

            AnimatedVisibility(
                visible = isSearchOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search settings...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }

            // Typo / Misspelling Suggestion Banner
            if (isSearching && didYouMean != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSearchQueryChange(didYouMean) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Did you mean:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = didYouMean,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Text(
                            text = "Apply",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = 24.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Feature Banner Carousel from Zenith (displayed when not actively searching)
                if (!isSearching) {
                    item {
                        PetalFeaturedCarousel(
                            onCategoryClick = onCategoryClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                    }
                }

                // Matching Detailed Settings Items (when searching)
                if (isSearching && matchingItems.isNotEmpty()) {
                    item {
                        PetalSectionLabel("Matching Settings (${matchingItems.size})", Modifier.padding(top = 4.dp))
                    }

                    itemsIndexed(matchingItems) { index, item ->
                        PetalGroupListRow(
                            position = petalGroupPositionFor(index, matchingItems.size),
                            onClick = { onCategoryClick(item.category, item.id) },
                            leading = { PetalGroupIconBadge(Icons.Rounded.Search) },
                            content = {
                                Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                                    Text(item.category.title, style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            trailing = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Categories Header
                if (filteredCategories.isNotEmpty()) {
                    item {
                        PetalSectionLabel(
                            if (isSearching) "Matching Categories (${filteredCategories.size})" else "Categories"
                        )
                    }

                    itemsIndexed(filteredCategories) { index, category ->
                        PetalGroupListRow(
                            position = petalGroupPositionFor(index, filteredCategories.size),
                            onClick = { onCategoryClick(category, null) },
                            leading = {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(painterResource(category.iconRes), contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            },
                            content = {
                                Text(category.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(category.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailing = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                    }
                } else if (isSearching && matchingItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "No settings found for \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
