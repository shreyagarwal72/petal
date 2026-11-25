package com.petal.browser.compose.tabs

import com.petal.browser.ui.components.PetalSpring
import com.petal.browser.ui.theme.PetalText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.VolumeMute
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.unit.PetalAndroidUtils
import androidx.compose.material3.Icon
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalConfiguration
import com.petal.browser.ui.theme.SmoothRoundedShape
import com.petal.browser.ui.containment.PetalSheetPill
import com.petal.browser.ui.containment.PetalSheetIconPill
import com.petal.browser.ui.containment.PetalSheetHeader
import com.petal.browser.ui.containment.PetalSheetActionRow
import com.petal.browser.ui.containment.PetalSheetSegment
import com.petal.browser.ui.containment.PetalSheetSegmentedBar
import com.petal.browser.ui.containment.PetalSheetTone
import com.petal.browser.ui.containment.PetalSheetCard
import com.petal.browser.ui.containment.PetalSheetSectionLabel
import com.petal.browser.ui.containment.petalSheetSurface

/**
 * Zen Browser-style Tab Glance (Peek Sheet).
 * Shows high-resolution tab preview and 8-action contextual quick-dispatch dock.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PetalTabGlanceSheet(
    tab: PetalTabItem,
    availableWorkspaces: List<PetalWorkspace> = emptyList(),
    availableGroups: List<PetalTabGroup> = emptyList(),
    onDismiss: () -> Unit,
    onSelectTab: (PetalTabItem) -> Unit,
    onSplitTab: (PetalTabItem) -> Unit,
    onTogglePin: (PetalTabItem) -> Unit,
    onMoveToWorkspace: (PetalTabItem, String) -> Unit,
    onMoveToGroup: (PetalTabItem, String?) -> Unit,
    onToggleMute: (PetalTabItem) -> Unit,
    onDuplicateTab: (PetalTabItem) -> Unit,
    onCloseTab: (PetalTabItem) -> Unit
) {
    var showWorkspacePicker by remember { mutableStateOf(false) }
    var showGroupPicker by remember { mutableStateOf(false) }

    var page by remember { mutableIntStateOf(0) }
    val maxBody = (LocalConfiguration.current.screenHeightDp * 0.62f).dp

    PetalSheet(
        onDismissRequest = onDismiss,
        blurSurface = com.petal.browser.ui.blur.PetalBlurSurface.TAB_MANAGER,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PetalSheetHeader(
                title = tab.title.ifBlank { domain(tab) },
                subtitle = domain(tab),
                modifier = Modifier.padding(horizontal = 16.dp),
                leading = { GlanceLeading(tab) }
            )

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxBody)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (page == 0) {
                    PetalSheetPill(
                        text = "Open Tab",
                        icon = Icons.Rounded.OpenInNew,
                        tone = PetalSheetTone.Primary,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onDismiss(); onSelectTab(tab) }
                    )

                    PetalSheetActionRow {
                        PetalSheetPill(
                            text = "Split View",
                            icon = Icons.Rounded.VerticalSplit,
                            tone = PetalSheetTone.Primary,
                            modifier = Modifier.weight(0.5f).heightIn(min = 80.dp),
                            onClick = { onDismiss(); onSplitTab(tab) }
                        )
                        PetalSheetIconPill(
                            icon = Icons.Rounded.PushPin,
                            contentDescription = if (tab.isPinned) "Unpin tab" else "Pin to Essentials",
                            selected = tab.isPinned,
                            modifier = Modifier.weight(0.25f).heightIn(min = 80.dp),
                            onClick = { onTogglePin(tab) }
                        )
                        PetalSheetIconPill(
                            icon = if (tab.isMuted) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeMute,
                            contentDescription = if (tab.isMuted) "Unmute audio" else "Mute audio",
                            tone = PetalSheetTone.Secondary,
                            modifier = Modifier.weight(0.25f).heightIn(min = 80.dp),
                            onClick = { onToggleMute(tab) }
                        )
                    }

                    PetalSheetActionRow {
                        PetalSheetPill(
                            text = "Workspace",
                            icon = Icons.Rounded.Workspaces,
                            tone = PetalSheetTone.Tertiary,
                            modifier = Modifier.weight(0.55f),
                            onClick = {
                                showWorkspacePicker = !showWorkspacePicker
                                showGroupPicker = false
                            }
                        )
                        PetalSheetPill(
                            text = if (tab.groupId != null) (tab.groupTitle ?: "Group") else "Group",
                            icon = Icons.Rounded.Folder,
                            tone = PetalSheetTone.TertiaryStrong,
                            modifier = Modifier.weight(0.45f),
                            onClick = {
                                showGroupPicker = !showGroupPicker
                                showWorkspacePicker = false
                            }
                        )
                    }

                    AnimatedVisibility(visible = showWorkspacePicker, enter = androidx.compose.animation.fadeIn(PetalSpring.calm()) + androidx.compose.animation.expandIn(PetalSpring.calm()), exit = androidx.compose.animation.fadeOut(PetalSpring.calm()) + androidx.compose.animation.shrinkOut(PetalSpring.calm())) {
                        GlancePickerCard(title = "Move to workspace") {
                            availableWorkspaces.forEach { ws ->
                                val isCurrent = (tab.workspaceId == ws.id) || (tab.workspaceId == null && ws.isDefault)
                                GlancePickerRow(
                                    selected = isCurrent,
                                    onClick = {
                                        onMoveToWorkspace(tab, ws.id)
                                        showWorkspacePicker = false
                                        onDismiss()
                                    }
                                ) {
                                    Icon(
                                        imageVector = getWorkspaceIconVector(ws.iconName),
                                        contentDescription = null,
                                        tint = ws.parseColor(),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = ws.name,
                                        style = PetalText.rowTitle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isCurrent) Text("Current", style = PetalText.badge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = showGroupPicker, enter = androidx.compose.animation.fadeIn(PetalSpring.calm()) + androidx.compose.animation.expandIn(PetalSpring.calm()), exit = androidx.compose.animation.fadeOut(PetalSpring.calm()) + androidx.compose.animation.shrinkOut(PetalSpring.calm())) {
                        GlancePickerCard(title = "Assign tab group") {
                            if (tab.groupId != null) {
                                GlancePickerRow(selected = false, onClick = {
                                    onMoveToGroup(tab, null)
                                    showGroupPicker = false
                                }) {
                                    Text("Remove from group", style = PetalText.button, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            availableGroups.forEach { grp ->
                                val isCurrent = tab.groupId == grp.id
                                GlancePickerRow(selected = isCurrent, onClick = {
                                    onMoveToGroup(tab, grp.id)
                                    showGroupPicker = false
                                    onDismiss()
                                }) {
                                    Box(Modifier.size(12.dp).clip(CircleShape).background(grp.parseColor()))
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = grp.title,
                                        style = PetalText.rowTitle,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    PetalSheetActionRow {
                        PetalSheetPill(
                            text = "Duplicate",
                            icon = Icons.Rounded.ContentCopy,
                            tone = PetalSheetTone.Secondary,
                            modifier = Modifier.weight(0.5f),
                            onClick = { onDismiss(); onDuplicateTab(tab) }
                        )
                        PetalSheetPill(
                            text = "Close Tab",
                            icon = Icons.Rounded.Close,
                            tone = PetalSheetTone.Danger,
                            modifier = Modifier.weight(0.5f),
                            onClick = { onDismiss(); onCloseTab(tab) }
                        )
                    }
                } else {
                    GlanceInfoCard(
                        rows = buildList {
                            add("Address" to tab.url)
                            add("Site" to domain(tab))
                            add("Pinned" to if (tab.isPinned) "Yes" else "No")
                            add("Audio" to if (tab.isMuted) "Muted" else "On")
                            add("Group" to (tab.groupTitle ?: "None"))
                            val ws = availableWorkspaces.firstOrNull { (tab.workspaceId == it.id) || (tab.workspaceId == null && it.isDefault) }
                            add("Workspace" to (ws?.name ?: "Default"))
                            if (tab.isIncognito) add("Mode" to "Incognito")
                        }
                    )
                }
                Spacer(Modifier.height(2.dp))
            }

            PetalSheetSegmentedBar(
                segments = listOf(
                    PetalSheetSegment("Options", Icons.Rounded.Menu),
                    PetalSheetSegment("Info", Icons.Rounded.Info),
                ),
                selectedIndex = page,
                onSelect = { page = it },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .padding(bottom = 6.dp)
            )
        }
    }
}

private fun domain(tab: PetalTabItem): String = PetalAndroidUtils.domain(tab.url) ?: tab.url

@Composable
private fun GlanceLeading(tab: PetalTabItem) {
    val shape = SmoothRoundedShape(24.dp)
    val bmp = tab.previewBitmap ?: tab.faviconBitmap
    Box(
        modifier = Modifier.size(80.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = if (tab.previewBitmap != null) ContentScale.Crop else ContentScale.Fit,
                modifier = if (tab.previewBitmap != null) Modifier.size(80.dp) else Modifier.size(40.dp)
            )
        } else {
            Text(
                text = tab.title.take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun GlancePickerCard(title: String, content: @Composable () -> Unit) {
    PetalSheetCard {
        PetalSheetSectionLabel(title)
        content()
    }
}

@Composable
private fun GlancePickerRow(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .petalSheetSurface(
                CircleShape,
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun GlanceInfoCard(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { i, (label, value) ->
            val shape = when {
                rows.size == 1 -> RoundedCornerShape(24.dp)
                i == 0 -> RoundedCornerShape(24.dp, 24.dp, 8.dp, 8.dp)
                i == rows.lastIndex -> RoundedCornerShape(8.dp, 8.dp, 24.dp, 24.dp)
                else -> RoundedCornerShape(8.dp)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .petalSheetSurface(shape, MaterialTheme.colorScheme.surfaceContainerHighest, sheen = 0.25f)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(label, style = PetalText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = PetalText.listItemTitle, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

