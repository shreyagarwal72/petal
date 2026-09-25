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
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

enum class QuickToolId(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val subtitle: String
) {
    QR_SCANNER("qr_scanner", "QR Scanner", Icons.Rounded.QrCodeScanner, "Scan QR with camera"),
    SAFE_LOCKER("safe_locker", "Safe Locker", Icons.Rounded.Lock, "Encrypted vault"),
    TRANSLATOR("translator", "Translator", Icons.Rounded.Translate, "Translate page"),
    EDIT_PAGE("edit_page", "Edit Page", Icons.Rounded.Edit, "Modify text on page"),
    SAVE_PDF("save_pdf", "Save PDF", Icons.Rounded.Print, "Export page to PDF"),
    NETWORK("network", "Network", Icons.Rounded.Language, "Connection & DNS inspector"),
    PIN_WEB_APP("pin_web_app", "Pin Web App", Icons.Rounded.OpenInNew, "Add to Home screen"),
    AUTO_SCROLL("auto_scroll", "Auto-Scroll", Icons.Rounded.ArrowDownward, "Hands-free reader"),
    QR_SCAN_PAGE("qr_scan_page", "QR Scan Page", Icons.Rounded.CropFree, "Find QR codes on page"),
    QR_GENERATOR("qr_generator", "QR Generator", Icons.Rounded.QrCode, "Share link as QR"),
    CONSOLE_LOG("console_log", "Console Log", Icons.Rounded.Terminal, "JavaScript REPL"),
    DEV_NOTES("dev_notes", "Dev Notes", Icons.Rounded.Description, "Notes for this site"),
    SITE_STYLE("site_style", "Site Style", Icons.Rounded.Palette, "Custom color & font themes"),
    IMAGE_GRABBER("image_grabber", "Image Grabber", Icons.Rounded.PhotoLibrary, "Extract all images"),
    INSPECTOR("inspector", "Inspector & ...", Icons.Rounded.Code, "DOM element inspector"),
    BLOCK_AREA("block_area", "Block Area", Icons.Rounded.VisibilityOff, "Element zapper"),
    SPOOF_IDENTITY("spoof_identity", "Spoof Identity", Icons.Rounded.Devices, "Change User-Agent"),
    FORCE_ZOOM("force_zoom", "Force Zoom", Icons.Rounded.ZoomIn, "Bypass zoom locks"),
    TORRENT_MAGNET("torrent_magnet", "Torrent & Ma...", Icons.Rounded.Download, "Magnet & torrent links"),
    PETAL_CONFIG("petal_config", "petal:config", Icons.Rounded.Tune, "Advanced engine flags")
}

interface PetalQuickToolsActionHandler {
    fun onToolClicked(tool: QuickToolId)
    fun onDismiss()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalQuickToolsSheet(
    currentPageUrl: String,
    currentPageTitle: String,
    onToolClicked: (QuickToolId) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val toolsList = remember {
        QuickToolId.values().toList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Quick Tools",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Long-press & drag to reorder",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            // 4-Column Tool Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(toolsList, key = { it.id }) { tool ->
                    QuickToolItem(
                        tool = tool,
                        onClick = {
                            onDismissRequest()
                            onToolClicked(tool)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickToolItem(
    tool: QuickToolId,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true)
            ) { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon Circle
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.title,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tool Title
        Text(
            text = tool.title,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
