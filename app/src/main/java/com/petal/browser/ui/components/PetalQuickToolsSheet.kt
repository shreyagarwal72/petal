/*
 * PetalQuickToolsSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Quick Tools Bottom Sheet matching Omni Browser
 * architecture and Firefox GeckoView engine capabilities.
 *
 * Provides a 4-column reorderable grid of 15 quick web tools. Tools that
 * duplicate an item already in the overflow menu (Safe Locker, Save/Print
 * PDF, Pin Web App, Console Log/Developer Console) were removed from here
 * on 2026-09-27 — those actions now live only in the overflow menu.
 * 1. QR Scanner (Camera barcode/QR reader)
 * 2. Translator (Page & selection translation)
 * 3. Edit Page (Live contentEditable / designMode)
 * 4. Network (Connection, TLS & IP inspector)
 * 5. Auto-Scroll (Hands-free page reader with speed HUD)
 * 6. QR Scan Page (In-page barcode & QR code extractor)
 * 7. QR Generator (ZXing QR code generator)
 * 8. Dev Notes (Per-site encrypted scratchpad)
 * 9. Site Style (Invert, Sepia, High Contrast & Custom Fonts)
 * 10. Image Grabber (Extract & batch download page images)
 * 11. Inspector & DOM (Tap-to-inspect element inspector)
 * 12. Block Area (Element zapper & cosmetic filter rule builder)
 * 13. Spoof Identity (Switch Desktop/Mobile/iOS/macOS User-Agent)
 * 14. Force Zoom (Bypass viewport pinch-to-zoom restrictions)
 * 15. Torrent & Magnet (Magnet link & torrent catcher)
 *
 * Enhanced with:
 * - Material 3 Expressive shapes & dynamic tonal container coloring
 * - Working long-press drag-to-reorder & edit mode with tactile haptics
 * - Persistent ordering via SharedPreferences (sp_quick_tools_order_v1)
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import com.petal.browser.ui.theme.ExpressiveButtonShape
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.petal.browser.haptics.PetalHapticEngine
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor

private const val PREF_QUICK_TOOLS_ORDER = "sp_quick_tools_order_v1"

enum class QuickToolId(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val subtitle: String,
    val shapeIndex: Int
) {
    QR_SCANNER("qr_scanner", "QR Scanner", Icons.Rounded.QrCodeScanner, "Scan QR with camera", 0),
    TRANSLATOR("translator", "Translator", Icons.Rounded.Translate, "Translate page", 1),
    EDIT_PAGE("edit_page", "Edit Page", Icons.Rounded.Edit, "Modify text on page", 2),
    NETWORK("network", "Network", Icons.Rounded.Language, "Connection & DNS inspector", 3),
    AUTO_SCROLL("auto_scroll", "Auto-Scroll", Icons.Rounded.ArrowDownward, "Hands-free reader", 4),
    QR_SCAN_PAGE("qr_scan_page", "QR Scan Page", Icons.Rounded.CropFree, "Find QR codes on page", 5),
    QR_GENERATOR("qr_generator", "QR Generator", Icons.Rounded.QrCode, "Share link as QR", 6),
    DEV_NOTES("dev_notes", "Dev Notes", Icons.Rounded.Description, "Notes for this site", 7),
    SITE_STYLE("site_style", "Site Style", Icons.Rounded.Palette, "Custom color & font themes", 8),
    IMAGE_GRABBER("image_grabber", "Image Grabber", Icons.Rounded.PhotoLibrary, "Extract all images", 9),
    INSPECTOR("inspector", "Inspector", Icons.Rounded.Code, "DOM element inspector", 10),
    BLOCK_AREA("block_area", "Block Area", Icons.Rounded.VisibilityOff, "Element zapper", 11),
    SPOOF_IDENTITY("spoof_identity", "Spoof Identity", Icons.Rounded.Devices, "Change User-Agent", 12),
    FORCE_ZOOM("force_zoom", "Force Zoom", Icons.Rounded.ZoomIn, "Bypass zoom locks", 13),
    SCREENSHOT("screenshot", "Screenshot", Icons.Rounded.Screenshot, "Capture page screenshot", 14),
    TORRENT_MAGNET("torrent_magnet", "Torrents", Icons.Rounded.Download, "Magnet & torrent links", 15);

    val shape: androidx.compose.ui.graphics.Shape
        get() = RoundedCornerShape(12.dp)
}

interface PetalQuickToolsActionHandler {
    fun onToolClicked(tool: QuickToolId)
    fun onDismiss()
}

@Composable
fun PetalQuickToolsSheet(
    currentPageUrl: String,
    currentPageTitle: String,
    onToolClicked: (QuickToolId) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    fun loadOrder(): List<QuickToolId> {
        val saved = sp.getString(PREF_QUICK_TOOLS_ORDER, null)
        val defaultList = QuickToolId.values().toList()
        if (saved.isNullOrBlank()) return defaultList
        val map = defaultList.associateBy { it.id }
        val parsed = saved.split(",").mapNotNull { map[it.trim()] }
        val remaining = defaultList.filter { it !in parsed }
        return parsed + remaining
    }

    fun saveOrder(tools: List<QuickToolId>) {
        sp.edit().putString(PREF_QUICK_TOOLS_ORDER, tools.joinToString(",") { it.id }).apply()
    }

    var toolsList by remember { mutableStateOf(loadOrder()) }
    var isReorderMode by remember { mutableStateOf(false) }

    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    val sheetShape = BottomSheetDefaults.ExpandedShape

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isGlass) Modifier.liquidGlassChrome(shape = sheetShape, enabled = true, sheenIntensity = sheen) else Modifier),
        shape = sheetShape,
        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerLow,
        border = null,
        tonalElevation = if (isGlass) 0.dp else 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 32.dp)
        ) {
            // Drag Handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                BottomSheetDefaults.DragHandle()
            }

            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.ui_quick_tools),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isReorderMode) "Tap arrows to rearrange or reset" else "Long-press any tool to rearrange",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isReorderMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                if (isReorderMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                toolsList = QuickToolId.values().toList()
                                saveOrder(toolsList)
                            },
                            shape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.ui_reset), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                        }

                        Button(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                isReorderMode = false
                                saveOrder(toolsList)
                            },
                            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.ui_done), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // 4-Column Tool Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(toolsList, key = { _, tool -> tool.id }) { index, tool ->
                    Box(modifier = Modifier.animateItem().entrance(index = index, playKey = tool.id)) {
                        ExpressiveQuickToolItem(
                            tool = tool,
                            isReorderMode = isReorderMode,
                            canMoveLeft = index > 0,
                            canMoveRight = index < toolsList.size - 1,
                        onMoveLeft = {
                            if (index > 0) {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                val updated = toolsList.toMutableList()
                                val temp = updated[index]
                                updated[index] = updated[index - 1]
                                updated[index - 1] = temp
                                toolsList = updated
                                saveOrder(updated)
                            }
                        },
                        onMoveRight = {
                            if (index < toolsList.size - 1) {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                val updated = toolsList.toMutableList()
                                val temp = updated[index]
                                updated[index] = updated[index + 1]
                                updated[index + 1] = temp
                                toolsList = updated
                                saveOrder(updated)
                            }
                        },
                        onLongClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.8f)
                            isReorderMode = true
                        },
                        onClick = {
                            if (!isReorderMode) {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                onToolClicked(tool)
                            }
                        }
                    )
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpressiveQuickToolItem(
    tool: QuickToolId,
    isReorderMode: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val scaleAnim by animateFloatAsState(
        targetValue = if (isReorderMode) 0.95f else 1f,
        animationSpec = PetalSpring.listReorder(),
        label = "toolScale"
    )

    // Standard 12.dp rounded squircle for ordinary item rows
    val shape: Shape = RoundedCornerShape(12.dp)

    // Adaptive color pairs based on index
    val (containerColor, iconColor) = when (tool.shapeIndex % 4) {
        0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        2 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scaleAnim)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
        // Standard Icon Container (0 borders)
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
                .background(if (isGlass) petalGroupSurfaceColor() else containerColor)
                .then(if (isGlass) Modifier.liquidGlassChrome(shape, enabled = true, sheenIntensity = 0.55f, pressFx = true) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.title,
                tint = if (isGlass) MaterialTheme.colorScheme.primary else iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Tool Title
        Text(
            text = tool.title,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // Reorder Arrows Row
        AnimatedVisibility(
            visible = isReorderMode,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMoveLeft,
                    enabled = canMoveLeft,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        Icons.Rounded.ChevronLeft,
                        contentDescription = stringResource(R.string.ui_move_left),
                        tint = if (canMoveLeft) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onMoveRight,
                    enabled = canMoveRight,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = stringResource(R.string.ui_move_right),
                        tint = if (canMoveRight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
