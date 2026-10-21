/*
 * PetalNewsCard.kt
 * ──────────────────────────────────────────────────────────────────────────
 * Brave News-style privacy RSS cards for the Petal home screen.
 * Full Liquid Glass + Material 3 Expressive support.
 */
package com.petal.browser.home.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import kotlinx.coroutines.launch

/**
 * Drop-in composable for the home screen with Liquid Glass + M3 Expressive styling.
 */
@Composable
fun PetalNewsSection(
    onArticleClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var articles by remember { mutableStateOf<List<NewsArticle>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                articles = PetalNewsManager.fetchTopArticles(maxPerFeed = 4)
            } catch (_: Exception) {
                /* silently swallow — news is non-critical */
            } finally {
                isLoading = false
            }
        }
    }

    if (isLoading || articles.isEmpty()) return

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Newspaper,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Top Stories",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(articles) { article ->
                PetalNewsArticleCard(
                    article = article,
                    onClick = { onArticleClick(article.url) }
                )
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PetalNewsArticleCard(
    article: NewsArticle,
    onClick: () -> Unit
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val shape = RoundedCornerShape(20.dp)

    Surface(
        modifier = Modifier
            .width(220.dp)
            .clip(shape)
            .liquidGlassChrome(shape = shape, enabled = isGlass)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = article.source.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = article.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (article.description.isNotBlank()) {
                Text(
                    text = article.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
