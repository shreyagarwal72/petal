package com.petal.browser.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petal.browser.R
import com.petal.browser.ui.components.SearchEngineItem

@Composable
fun MiniPetal(
    floatingTabs: Boolean,
    addressAtBottom: Boolean,
    darkWebpages: Boolean,
    adBlock: Boolean,
    engine: SearchEngineItem?,
    modifier: Modifier = Modifier
) {
    val pageColor = if (darkWebpages) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceContainerLowest
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(stringResource(R.string.petal_setup_mini_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (!addressAtBottom) MiniAddressBar(engine)
            Box(
                Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(18.dp)).background(pageColor).padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(stringResource(R.string.petal_setup_mini_page_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (darkWebpages) Color.White else MaterialTheme.colorScheme.onSurface)
                    Box(Modifier.fillMaxWidth(.82f).height(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .65f)))
                    Box(Modifier.fillMaxWidth(.58f).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .3f)))
                }
                if (adBlock) Icon(Icons.Rounded.Shield, stringResource(R.string.petal_setup_ad_shield), Modifier.align(Alignment.TopEnd).size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            if (addressAtBottom) MiniAddressBar(engine)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(if (floatingTabs) 30.dp else 14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Home, stringResource(R.string.petal_setup_home), tint = MaterialTheme.colorScheme.primary)
                Icon(Icons.Rounded.Bookmark, stringResource(R.string.petal_setup_bookmarks), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Rounded.Tab, stringResource(R.string.petal_setup_tabs), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MiniAddressBar(engine: SearchEngineItem?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.Search, stringResource(R.string.petal_setup_search), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(engine?.name ?: stringResource(R.string.petal_setup_search_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
