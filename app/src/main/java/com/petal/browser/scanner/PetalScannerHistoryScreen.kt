package com.petal.browser.scanner

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.motion.MotionGate
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.PetalGlassHeaderLayout
import com.petal.browser.ui.components.PetalMorphBlob
import com.petal.browser.ui.components.PetalPageBackground
import com.petal.browser.ui.containment.PetalBadgeVariant
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.colors
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalContainmentShape
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.containment.rememberPetalGroupPressScale
import com.petal.browser.ui.containment.rememberPetalGroupShape
import com.petal.browser.ui.glass.rememberGlassSheen
import com.petal.browser.ui.theme.ExpressiveIconButton
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.ui.theme.PetalText

/**
 * Scan history view allowing user to search, pin, delete, and inspect past scans
 * using Petal M3E containment and Liquid Glass.
 */
@Composable
fun PetalScannerHistoryScreen(
    onBack: () -> Unit,
    onSelectEntry: (ScanHistoryEntry) -> Unit
) {
    val context = LocalContext.current
    val isLowEndDevice = remember(context) { MotionGate.isLowEndDevice(context) }
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments") && !isLowEndDevice
    val isAmoled = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean("sp_amoled", false)
    }

    var searchQuery by remember { mutableStateOf("") }
    var historyItems by remember { mutableStateOf(PetalScanHistoryStore.getHistory(context)) }

    fun reload() {
        historyItems = if (searchQuery.isBlank()) {
            PetalScanHistoryStore.getHistory(context)
        } else {
            PetalScanHistoryStore.search(context, searchQuery)
        }
    }

    LaunchedEffect(searchQuery) {
        reload()
    }

    PetalGlassHeaderLayout(
        modifier = Modifier.fillMaxSize(),
        enableLiquidGlass = isGlass,
        pageBackground = {
            if (isGlass) {
                PetalPageBackground(
                    pageKey = "scanner_history",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (isAmoled) Color.Black else MaterialTheme.colorScheme.background)
                )
            }
        },
        header = { headerModifier, headerBackdrop ->
            ExpressiveHeader(
                title = stringResource(R.string.scanner_history_title),
                subtitle = stringResource(R.string.scanner_history_subtitle),
                onBack = onBack,
                backContentDescription = stringResource(R.string.scanner_back),
                enableLiquidGlass = isGlass,
                backdrop = headerBackdrop,
                actions = {
                    if (historyItems.isNotEmpty()) {
                        ExpressiveIconButton(
                            icon = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.scanner_history_clear_all),
                            onClick = {
                                PetalScanHistoryStore.clearAll(context)
                                reload()
                            },
                            size = 44.dp,
                            containerColor = if (isGlass) {
                                petalGroupSurfaceColor(enableLiquidGlass = true)
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            contentColor = if (isGlass) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = if (isGlass) {
                                Modifier.liquidGlassChrome(petalContainmentShape(14.dp), enabled = true)
                            } else {
                                Modifier
                            },
                        )
                    }
                },
                modifier = headerModifier,
            )
        },
    ) { topInset ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = topInset + 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.scanner_history_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Rounded.Clear,
                                contentDescription = stringResource(R.string.scanner_history_clear_search),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = petalContainmentShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (historyItems.isEmpty()) {
                val emptyText = if (searchQuery.isNotEmpty()) {
                    stringResource(R.string.scanner_history_empty_search)
                } else {
                    stringResource(R.string.scanner_history_empty)
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PetalMorphBlob(
                            shapes = listOf(
                                PetalMaterialShapes.Cookie9Sided.polygon,
                                PetalMaterialShapes.Flower.polygon,
                            ),
                            index = if (searchQuery.isNotEmpty()) 1 else 0,
                            size = 88.dp,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = emptyText,
                            style = PetalText.emptyState,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    itemsIndexed(historyItems, key = { _, item -> item.id }) { index, item ->
                        HistoryItemRow(
                            item = item,
                            position = petalGroupPositionFor(index, historyItems.size),
                            enableLiquidGlass = isGlass,
                            onClick = { onSelectEntry(item) },
                            onTogglePin = {
                                PetalScanHistoryStore.togglePin(context, item.id)
                                reload()
                            },
                            onDelete = {
                                PetalScanHistoryStore.deleteHistory(context, item.id)
                                reload()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemRow(
    item: ScanHistoryEntry,
    position: PetalGroupPosition,
    enableLiquidGlass: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val relativeTime = remember(item.timestamp) {
        DateUtils.getRelativeTimeSpanString(
            item.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }
    val metaText = stringResource(R.string.scanner_history_meta_format, relativeTime, item.formatName)
    val pinnedLabel = stringResource(R.string.scanner_history_pinned)
    val pinLabel = stringResource(R.string.scanner_history_pin)
    val unpinLabel = stringResource(R.string.scanner_history_unpin)
    val deleteLabel = stringResource(R.string.scanner_history_delete)

    val source = remember { MutableInteractionSource() }
    val scale = rememberPetalGroupPressScale(source)
    val shape = rememberPetalGroupShape(position)
    val sheen = rememberGlassSheen(enableLiquidGlass)
    val surfaceColor = petalGroupSurfaceColor(enableLiquidGlass = enableLiquidGlass)
    val (badgeContainer, badgeTint) = if (item.isPinned) {
        PetalBadgeVariant.PRIMARY.colors()
    } else {
        PetalBadgeVariant.SECONDARY.colors()
    }

    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = source,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .liquidGlassChrome(
                shape = shape,
                enabled = enableLiquidGlass,
                sheenIntensity = sheen,
                pressFx = true,
            )
            .semantics {
                contentDescription = "${item.title}. ${item.subtitle}. $metaText"
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 70.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PetalGroupIconBadge(
                icon = Icons.Rounded.QrCode2,
                container = badgeContainer,
                tint = badgeTint,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isPinned) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = pinnedLabel,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp),
                        )
                    }
                    Text(
                        text = item.title,
                        style = PetalText.listItemTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = item.subtitle,
                    style = PetalText.listItemMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = metaText,
                    style = PetalText.caption,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = if (item.isPinned) unpinLabel else pinLabel,
                    tint = if (item.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = deleteLabel,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
