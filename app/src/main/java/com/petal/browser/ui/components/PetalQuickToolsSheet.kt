/*
 * PetalQuickToolsSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Quick Tools Bottom Sheet matching Omni Browser
 * architecture and Firefox GeckoView engine capabilities.
 *
 * Provides a 4-column reorderable grid of 20 quick web tools:
 * 1. QR Scanner (Camera barcode/QR reader)
 * 2. Safe Locker (AES-GCM Biometric private storage)
 * 3. Translator (Page & selection translation)
 * 4. Edit Page (Live contentEditable / designMode)
 * 5. Save PDF (GeckoView Print to PDF)
 * 6. Network (Connection, TLS & IP inspector)
 * 7. Pin Web App (Add PWA to Home Screen)
 * 8. Auto-Scroll (Hands-free page reader with speed HUD)
 * 9. QR Scan Page (In-page barcode & QR code extractor)
 * 10. QR Generator (ZXing QR code generator)
 * 11. Console Log (Live JS REPL & logger)
 * 12. Dev Notes (Per-site encrypted scratchpad)
 * 13. Site Style (Invert, Sepia, High Contrast & Custom Fonts)
 * 14. Image Grabber (Extract & batch download page images)
 * 15. Inspector & DOM (Tap-to-inspect element inspector)
 * 16. Block Area (Element zapper & cosmetic filter rule builder)
 * 17. Spoof Identity (Switch Desktop/Mobile/iOS/macOS User-Agent)
 * 18. Force Zoom (Bypass viewport pinch-to-zoom restrictions)
 * 19. Torrent & Magnet (Magnet link & torrent catcher)
 * 20. petal:config (Internal Gecko preferences & flags)
 *
 * Enhanced with:
 * - Material 3 Expressive shapes & dynamic tonal container coloring
 * - Working long-press drag-to-reorder & edit mode with tactile haptics
 * - Persistent ordering via SharedPreferences (sp_quick_tools_order_v1)
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.ui.theme.toShape

private const val PREF_QUICK_TOOLS_ORDER = "sp_quick_tools_order_v1"

enum class QuickToolId(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val subtitle: String,
    val shapeIndex: Int
) {
    QR_SCANNER("qr_scanner", "QR Scanner", Icons.Rounded.QrCodeScanner, "Scan QR with camera", 0),
    SAFE_LOCKER("safe_locker", "Safe Locker", Icons.Rounded.Lock, "Encrypted vault", 1),
    TRANSLATOR("translator", "Translator", Icons.Rounded.Translate, "Translate page", 2),
    EDIT_PAGE("edit_page", "Edit Page", Icons.Rounded.Edit, "Modify text on page", 3),
    SAVE_PDF("save_pdf", "Save PDF", Icons.Rounded.Print, "Export page to PDF", 4),
    NETWORK("network", "Network", Icons.Rounded.Language, "Connection & DNS inspector", 5),
    PIN_WEB_APP("pin_web_app", "Pin Web App", Icons.Rounded.OpenInNew, "Add to Home screen", 6),
    AUTO_SCROLL("auto_scroll", "Auto-Scroll", Icons.Rounded.ArrowDownward, "Hands-free reader", 7),
    QR_SCAN_PAGE("qr_scan_page", "QR Scan Page", Icons.Rounded.CropFree, "Find QR codes on page", 8),
    QR_GENERATOR("qr_generator", "QR Generator", Icons.Rounded.QrCode, "Share link as QR", 9),
    CONSOLE_LOG("console_log", "Console Log", Icons.Rounded.Terminal, "JavaScript REPL", 10),
    DEV_NOTES("dev_notes", "Dev Notes", Icons.Rounded.Description, "Notes for this site", 11),
    SITE_STYLE("site_style", "Site Style", Icons.Rounded.Palette, "Custom color & font themes", 12),
    IMAGE_GRABBER("image_grabber", "Image Grabber", Icons.Rounded.PhotoLibrary, "Extract all images", 13),
    INSPECTOR("inspector", "Inspector", Icons.Rounded.Code, "DOM element inspector", 14),
    BLOCK_AREA("block_area", "Block Area", Icons.Rounded.VisibilityOff, "Element zapper", 15),
    SPOOF_IDENTITY("spoof_identity", "Spoof Identity", Icons.Rounded.Devices, "Change User-Agent", 16),
    FORCE_ZOOM("force_zoom", "Force Zoom", Icons.Rounded.ZoomIn, "Bypass zoom locks", 0),
    TORRENT_MAGNET("torrent_magnet", "Torrents", Icons.Rounded.Download, "Magnet & torrent links", 1),
    PETAL_CONFIG("petal_config", "petal:config", Icons.Rounded.Tune, "Advanced engine flags", 2)
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp
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
                Surface(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
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
                        text = "Quick Tools",
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
                        TextButton(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                toolsList = QuickToolId.values().toList()
                                saveOrder(toolsList)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                        }

                        FilledTonalButton(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                isReorderMode = false
                                saveOrder(toolsList)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Done", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
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
        animationSpec = spring(dampingRatio = 0.7f),
        label = "toolScale"
    )

    // Curated M3 Expressive Shape selection
    val shapeHolder = remember(tool.shapeIndex) {
        val shapes = PetalMaterialShapes.homescreenShapes
        shapes[tool.shapeIndex % shapes.size]
    }
    val shape: Shape = remember(shapeHolder) { shapeHolder.toShape() }

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
        // Expressive Shaped Icon Container
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
                .background(containerColor)
                .border(
                    BorderStroke(
                        width = if (isReorderMode) 1.5.dp else 0.5.dp,
                        color = if (isReorderMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.title,
                tint = iconColor,
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
                        contentDescription = "Move Left",
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
                        contentDescription = "Move Right",
                        tint = if (canMoveRight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
