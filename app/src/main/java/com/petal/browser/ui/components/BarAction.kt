package com.petal.browser.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Registry of user-customizable toolbar button actions for the Petal Merge Bar.
 *
 * Each action defines its stable persistence ID, user-facing label, primary icon,
 * and capability flags (badges, long-press, availability).
 */
enum class BarAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val isAvailable: Boolean = true,
    val supportsBadge: Boolean = false,
    val supportsLongPress: Boolean = false
) {
    TABS(
        id = "TABS",
        label = "Tabs",
        icon = Icons.Rounded.FilterNone,
        isAvailable = true,
        supportsBadge = true,
        supportsLongPress = true
    ),
    NEW_TAB(
        id = "NEW_TAB",
        label = "New tab",
        icon = Icons.Rounded.Add,
        isAvailable = true,
        supportsBadge = false,
        supportsLongPress = false
    ),
    HOME(
        id = "HOME",
        label = "Home",
        icon = Icons.Rounded.Home,
        isAvailable = true,
        supportsBadge = false,
        supportsLongPress = false
    ),
    SEARCH(
        id = "SEARCH",
        label = "Search",
        icon = Icons.Rounded.Search,
        isAvailable = true,
        supportsBadge = false,
        supportsLongPress = false
    ),
    BACK(
        id = "BACK",
        label = "Back",
        icon = Icons.Rounded.ArrowBack,
        isAvailable = true,
        supportsBadge = false,
        supportsLongPress = false
    ),
    FORWARD(
        id = "FORWARD",
        label = "Forward",
        icon = Icons.Rounded.ArrowForward,
        isAvailable = true,
        supportsBadge = false,
        supportsLongPress = false
    ),
    RELOAD(
        id = "RELOAD",
        label = "Reload",
        icon = Icons.Rounded.Refresh,
        isAvailable = true,
        supportsBadge = false,
        supportsLongPress = true
    ),
    SHARE(
        id = "SHARE",
        label = "Share",
        icon = Icons.Rounded.Share,
        isAvailable = true,
        supportsBadge = false,
        supportsLongPress = false
    ),
    BOOKMARK(
        id = "BOOKMARK",
        label = "Bookmark",
        icon = Icons.Rounded.BookmarkBorder,
        isAvailable = true,
        supportsBadge = false,
        supportsLongPress = false
    ),
    AI_RESEARCH(
        id = "AI_RESEARCH",
        label = "AI Research",
        icon = Icons.Rounded.AutoAwesome,
        isAvailable = true,
        supportsBadge = false,
        supportsLongPress = false
    ),
    READER(
        id = "READER",
        label = "Reader View",
        icon = Icons.Rounded.MenuBook,
        isAvailable = true,
        supportsBadge = false,
        supportsLongPress = false
    ),
    DOWNLOADS(
        id = "DOWNLOADS",
        label = "Downloads",
        icon = Icons.Rounded.Download,
        isAvailable = true,
        supportsBadge = false,
        supportsLongPress = false
    ),
    FIND_IN_PAGE(
        id = "FIND_IN_PAGE",
        label = "Find in page",
        icon = Icons.Rounded.FindInPage,
        isAvailable = true,
        supportsBadge = false,
        supportsLongPress = false
    ),
    MENU(
        id = "MENU",
        label = "Menu",
        icon = Icons.Rounded.MoreVert,
        isAvailable = true,
        supportsBadge = false,
        supportsLongPress = true
    );

    companion object {
        fun fromId(rawId: String?): BarAction? {
            if (rawId.isNullOrBlank()) return null
            val clean = rawId.trim().uppercase()
            return entries.firstOrNull { it.id.equals(clean, ignoreCase = true) }
        }

        /**
         * Parses a comma-separated ID string into a list of [BarAction].
         * Drops unknown IDs, duplicate items (keeps first), and filters by [isAvailable].
         */
        fun parseActionList(csv: String?): List<BarAction> {
            if (csv.isNullOrBlank()) return emptyList()
            val seen = mutableSetOf<BarAction>()
            val result = mutableListOf<BarAction>()
            for (token in csv.split(",")) {
                val action = fromId(token)
                if (action != null && action.isAvailable && seen.add(action)) {
                    result.add(action)
                }
            }
            return result
        }

        /**
         * Serializes a list of [BarAction] into a comma-separated string.
         */
        fun serializeActionList(actions: List<BarAction>): String {
            return actions.joinToString(",") { it.id }
        }
    }
}
