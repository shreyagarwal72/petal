package com.petal.browser.media.sniffer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.view.PetalToast

/**
 * Soul Browser-Style Sniffer Bottom Sheet.
 * Displays categorized media & documents (All, Video, Audio, Image, Document)
 * with batch download, individual playback, link copying, and Liquid Glass chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalResourceSnifferSheet(
    onDismiss: () -> Unit,
    onPlayVideo: (String) -> Unit
) {
    val context = LocalContext.current
    val resources by PetalNativeSnifferEngine.currentTabResources.collectAsState()
    var selectedCategory by remember { mutableStateOf(PetalResourceCategory.ALL) }
    val selectedIds = remember { mutableStateListOf<String>() }

    val filteredResources = remember(resources, selectedCategory) {
        if (selectedCategory == PetalResourceCategory.ALL) {
            resources
        } else {
            resources.filter { it.category == selectedCategory }
        }
    }

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    com.petal.browser.ui.containment.PetalSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            // Header: Title & Resource count & Select All toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.TravelExplore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Resource Sniffer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${filteredResources.size} detected (${selectedIds.size} selected)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (filteredResources.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                if (selectedIds.size == filteredResources.size) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    selectedIds.addAll(filteredResources.map { it.id })
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedIds.size == filteredResources.size) "Deselect" else "Select All",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Category Filter Pills (Soul-style tabs: All, Video, Audio, Image, Doc)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PetalResourceCategory.values()) { category ->
                    val count = if (category == PetalResourceCategory.ALL) {
                        resources.size
                    } else {
                        resources.count { it.category == category }
                    }

                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                            selectedCategory = category
                        },
                        label = {
                            Text(
                                text = "${category.label} ($count)",
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Resource List
            if (filteredResources.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No ${selectedCategory.label.lowercase()} streams found yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 400.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredResources, key = { it.id }) { item ->
                        val isSelected = selectedIds.contains(item.id)
                        SniffedResourceRow(
                            resource = item,
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (isSelected) selectedIds.remove(item.id) else selectedIds.add(item.id)
                            },
                            onPlay = {
                                onDismiss()
                                onPlayVideo(item.url)
                            },
                            onDownload = {
                                PetalNativeSnifferEngine.downloadResource(context, item)
                                PetalToast.show(context, "Download enqueued")
                            },
                            onCopyUrl = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("URL", item.url))
                                PetalToast.show(context, "URL copied")
                            }
                        )
                    }
                }
            }

            // Bottom Batch Action Bar (Download selected / Copy selected)
            AnimatedVisibility(
                visible = selectedIds.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = petalGroupSurfaceColor(),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isGlass) 0.35f else 0.5f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedIds.size} selected",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val urls = filteredResources.filter { selectedIds.contains(it.id) }.joinToString("\n") { it.url }
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("URLs", urls))
                                    PetalToast.show(context, "Copied ${selectedIds.size} links")
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Copy Links", style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = {
                                    val toDownload = filteredResources.filter { selectedIds.contains(it.id) }
                                    PetalNativeSnifferEngine.downloadBatch(context, toDownload)
                                    PetalToast.show(context, "Downloading ${toDownload.size} items")
                                    selectedIds.clear()
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Download", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SniffedResourceRow(
    resource: SniffedResource,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCopyUrl: () -> Unit
) {
    val categoryIcon: ImageVector = when (resource.category) {
        PetalResourceCategory.VIDEO -> Icons.Rounded.PlayCircle
        PetalResourceCategory.AUDIO -> Icons.Rounded.Audiotrack
        PetalResourceCategory.IMAGE -> Icons.Rounded.Image
        PetalResourceCategory.DOCUMENT -> Icons.Rounded.Description
        PetalResourceCategory.ALL -> Icons.Rounded.Attachment
    }

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = petalGroupSurfaceColor(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect() }
            .border(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isGlass) 0.35f else 0.5f),
                RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() }
            )

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resource.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = resource.fileExtension,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    if (resource.displaySize.isNotBlank()) {
                        Text(
                            text = resource.displaySize,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Action Icons
            if (resource.category == PetalResourceCategory.VIDEO || resource.category == PetalResourceCategory.AUDIO) {
                IconButton(onClick = onPlay, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(onClick = onDownload, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(onClick = onCopyUrl, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy URL",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
