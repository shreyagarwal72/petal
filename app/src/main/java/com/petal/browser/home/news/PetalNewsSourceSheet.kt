/*
 * PetalNewsSourceSheet.kt
 * ──────────────────────────────────────────────────────────────────────────
 * Liquid Glass & Material 3 bottom sheet for configuring Brave-style news feeds:
 * - Master feed toggle (turn feeds completely on/off).
 * - Multi-source feed selector (BBC, Reuters, The Verge, Ars Technica, WIRED, etc.).
 * - Instant live refresh when preferences change.
 */
package com.petal.browser.home.news

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Feed
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.petalGroupPositionFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalNewsSourceSheet(
    onDismissRequest: () -> Unit,
    onSourcesChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = remember { PetalHapticEngine.getInstance(context) }

    var isEnabled by remember { mutableStateOf(PetalNewsManager.isNewsEnabled(context)) }
    var selectedIds by remember { mutableStateOf(PetalNewsManager.getSelectedSourceIds(context)) }

    PetalSheet(
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PetalGroupIconBadge(
                        icon = Icons.Rounded.Newspaper,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        size = 42.dp,
                        iconSize = 22.dp
                    )
                    Column {
                        Text(
                            text = "Petal Feed & News",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Privacy RSS aggregator without tracking",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Master Feed Toggle Row
            PetalGroupListRow(
                position = PetalGroupPosition.SINGLE,
                onClick = {
                    haptic.playClick(context)
                    val next = !isEnabled
                    isEnabled = next
                    PetalNewsManager.setNewsEnabled(context, next)
                    onSourcesChanged()
                },
                leading = {
                    PetalGroupIconBadge(
                        icon = Icons.Rounded.Feed,
                        container = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        tint = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 38.dp,
                        iconSize = 20.dp
                    )
                },
                content = {
                    Text(
                        text = "Show Feed on Homepage",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isEnabled) "Active · Displays top articles below shortcuts" else "Disabled · Homepage is minimal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailing = {
                    com.petal.browser.ui.components.IconSwitch(
                        checked = isEnabled,
                        icon = Icons.Rounded.Check,
                        onCheckedChange = { checked ->
                            haptic.playClick(context)
                            isEnabled = checked
                            PetalNewsManager.setNewsEnabled(context, checked)
                            onSourcesChanged()
                        }
                    )
                }
            )

            if (isEnabled) {
                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Select News Sources",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${selectedIds.size} of ${PetalNewsManager.AVAILABLE_SOURCES.size} active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Sources List
                val sources = PetalNewsManager.AVAILABLE_SOURCES
                sources.forEachIndexed { index, source ->
                    val isChecked = source.id in selectedIds
                    PetalGroupListRow(
                        position = petalGroupPositionFor(index, sources.size),
                        onClick = {
                            haptic.playTick(context)
                            val nextChecked = !isChecked
                            val nextSet = selectedIds.toMutableSet()
                            if (nextChecked) nextSet.add(source.id) else nextSet.remove(source.id)
                            selectedIds = nextSet
                            PetalNewsManager.setSelectedSourceIds(context, nextSet)
                            onSourcesChanged()
                        },
                        leading = {
                            PetalGroupIconBadge(
                                icon = Icons.Rounded.RssFeed,
                                container = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                tint = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                size = 36.dp,
                                iconSize = 18.dp
                            )
                        },
                        content = {
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = source.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailing = {
                            com.petal.browser.ui.components.IconSwitch(
                                checked = isChecked,
                                icon = Icons.Rounded.Check,
                                onCheckedChange = { checked ->
                                    haptic.playTick(context)
                                    val nextSet = selectedIds.toMutableSet()
                                    if (checked) nextSet.add(source.id) else nextSet.remove(source.id)
                                    selectedIds = nextSet
                                    PetalNewsManager.setSelectedSourceIds(context, nextSet)
                                    onSourcesChanged()
                                }
                            )
                        }
                    )
                    if (index < sources.size - 1) {
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            FilledTonalButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Done")
            }
        }
    }
}
