package com.petal.browser.compose.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.SettingsMenuItem
import com.petal.browser.ui.components.getGroupItemShape

/**
 * Main Settings Hub Screen matching RvSystem-Monitor's SettingsScreen.kt visual structure:
 * - ExpressiveHeader with title, subtitle, and back button
 * - Settings search input
 * - LazyColumn of grouped cards with variable corner radii (getGroupItemShape)
 * - 48dp icon inside primary-tinted rounded box, title, subtitle, trailing chevron
 */
@Composable
fun SettingsHubScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCategoryClick: (SettingsCategory) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        SettingsCategory.API_INTEGRATIONS,
        SettingsCategory.APPEARANCE,
        SettingsCategory.PRIVACY,
        SettingsCategory.SEARCH_HOMEPAGE,
        SettingsCategory.DISPLAY_ZOOM,
        SettingsCategory.EXPERIMENTAL,
        SettingsCategory.MISCELLANEOUS,
        SettingsCategory.DATA_STORAGE,
        SettingsCategory.UPDATER,
        SettingsCategory.ABOUT
    )

    val filteredCategories = if (searchQuery.isBlank()) {
        categories
    } else {
        categories.filter { cat ->
            cat.title.contains(searchQuery, ignoreCase = true) ||
                    cat.subtitle.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "settings_hub")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Settings",
                subtitle = "Browser Preferences & Customization",
                onBack = onNavigateBack
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "Categories" else "Matching Categories",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }

                itemsIndexed(filteredCategories) { index, category ->
                    val shape = getGroupItemShape(index, filteredCategories.size)
                    SettingsMenuItem(
                        title = category.title,
                        subtitle = category.subtitle,
                        icon = painterResource(category.iconRes),
                        shape = shape,
                        onClick = { onCategoryClick(category) }
                    )
                }
            }
        }
    }
}
