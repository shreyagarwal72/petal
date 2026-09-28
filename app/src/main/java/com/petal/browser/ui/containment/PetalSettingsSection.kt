package com.petal.browser.ui.containment

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PetalSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    cardId: String? = null,
    targetHighlightId: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val matched = remember(cardId, targetHighlightId) {
        val card = cardId?.trim()?.lowercase().orEmpty()
        val target = targetHighlightId?.trim()?.lowercase().orEmpty()
        card.isNotEmpty() && target.isNotEmpty() &&
            (card == target || target.startsWith("${card}_") || card.startsWith("${target}_") || card.contains(target) || target.contains(card))
    }
    var highlighted by remember { mutableStateOf(false) }
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(matched, cardId, targetHighlightId) {
        if (matched) {
            highlighted = true
            runCatching { requester.bringIntoView() }
            delay(1000L)
            highlighted = false
        }
    }
    Column(modifier.fillMaxWidth().bringIntoViewRequester(requester)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                iconRes != null -> PetalGroupIconBadge(painter = painterResource(iconRes))
                icon != null -> PetalGroupIconBadge(icon)
            }
            PetalSectionLabel(title, modifier = Modifier.weight(1f))
        }
        CompositionLocalProvider(LocalPetalSectionHighlighted provides highlighted) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                content = content,
            )
        }
    }
}
