package com.petal.browser.ui.containment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
    highlightIds: List<String> = emptyList(),
    targetHighlightId: String? = null,
    collapsible: Boolean = false,
    expanded: Boolean = true,
    onToggleExpand: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val matched = remember(cardId, highlightIds, targetHighlightId) {
        val target = targetHighlightId?.trim()?.lowercase().orEmpty()
        if (target.isEmpty()) {
            false
        } else {
            val candidates = buildList {
                cardId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let(::add)
                highlightIds.mapNotNull { it.trim().lowercase().takeIf(String::isNotEmpty) }.forEach(::add)
            }
            candidates.any { candidate ->
                candidate == target ||
                    target.startsWith("${candidate}_") ||
                    candidate.startsWith("${target}_") ||
                    candidate.contains(target) ||
                    target.contains(candidate)
            }
        }
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
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "section_expand_chevron"
    )
    Column(modifier.fillMaxWidth().bringIntoViewRequester(requester)) {
        val headerModifier = if (collapsible && onToggleExpand != null) {
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggleExpand)
        } else {
            Modifier.fillMaxWidth()
        }
        Row(
            modifier = headerModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Section titles are plain labels (same as the Account page); `icon` / `iconRes` are kept
            // only so existing callers still compile.
            PetalSectionLabel(title, modifier = Modifier.weight(1f))
            if (collapsible) {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = if (onInfoClick != null) 0.dp else 4.dp)
                        .size(20.dp)
                        .rotate(chevronRotation)
                )
            }
            if (onInfoClick != null) {
                com.petal.browser.compose.settings.screens.PetalFeatureInfoButton(onClick = onInfoClick)
            }
        }
        CompositionLocalProvider(LocalPetalSectionHighlighted provides highlighted) {
            if (collapsible) {
                AnimatedVisibility(visible = expanded) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        content = content,
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    content = content,
                )
            }
        }
    }
}
