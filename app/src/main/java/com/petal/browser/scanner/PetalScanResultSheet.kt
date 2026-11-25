package com.petal.browser.scanner

import androidx.compose.animation.AnimatedVisibility
import com.petal.browser.ui.theme.PetalText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.R
import com.petal.browser.ui.components.PetalQrGeneratorDialog
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.theme.ExpressiveButtonShape

/**
 * Expandable Material 3 Expressive bottom sheet presenting scanned code details,
 * safety analysis, multi-code selection chips, and per-type context actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalScanResultSheet(
    results: List<PetalScanResult>,
    selectedIndex: Int = 0,
    onSelectIndex: (Int) -> Unit = {},
    onDismissRequest: () -> Unit,
    onOpenInBrowser: (url: String, inNewTab: Boolean) -> Unit
) {
    if (results.isEmpty() || selectedIndex !in results.indices) return

    val currentResult = results[selectedIndex]
    val context = LocalContext.current
    val isLiquidGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    var showQrGenerator by remember { mutableStateOf(false) }

    if (showQrGenerator) {
        PetalQrGeneratorDialog(
            url = currentResult.rawValue,
            title = currentResult.rawValue.take(40),
            onDismissRequest = { showQrGenerator = false }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = if (isLiquidGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = if (isLiquidGlass) Modifier.liquidGlassChrome(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), enabled = true) else Modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Multi-code selector chips if more than 1 code detected ──
            if (results.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Codes detected (${results.size}):",
                        style = PetalText.sectionHeader,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    results.forEachIndexed { idx, _ ->
                        val isSelected = idx == selectedIndex
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(32.dp)
                                .bouncyClickable { onSelectIndex(idx) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${idx + 1}",
                                    style = PetalText.badge,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // ── Payload Type Header & Icon ──
            val payload = currentResult.payload
            val (icon, typeLabel) = getPayloadMeta(payload)

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(56.dp)
                    .padding(bottom = 8.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = typeLabel,
                style = PetalText.sectionHeader,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // ── Main Content Presentation ──
            when (payload) {
                is ScanPayload.Url -> {
                    Text(
                        text = payload.host,
                        style = PetalText.sheetTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = payload.displayUrl,
                        style = PetalText.cardBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                }
                is ScanPayload.Email -> {
                    Text(
                        text = payload.address,
                        style = PetalText.sheetTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    if (!payload.subject.isNullOrBlank()) {
                        Text(
                            text = "Subject: ${payload.subject}",
                            style = PetalText.cardBody,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                is ScanPayload.Phone -> {
                    Text(
                        text = payload.number,
                        style = PetalText.sheetTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
                is ScanPayload.Sms -> {
                    Text(
                        text = payload.number,
                        style = PetalText.sheetTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!payload.message.isNullOrBlank()) {
                        Text(
                            text = payload.message,
                            style = PetalText.cardBody,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                is ScanPayload.Wifi -> {
                    Text(
                        text = payload.ssid,
                        style = PetalText.sheetTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Security: ${payload.authType}${if (payload.hidden) " (Hidden)" else ""}",
                        style = PetalText.cardBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ScanPayload.Contact -> {
                    Text(
                        text = payload.name,
                        style = PetalText.sheetTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    payload.phone?.let {
                        Text(text = it, style = PetalText.cardBody, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    payload.email?.let {
                        Text(text = it, style = PetalText.cardBody, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is ScanPayload.Calendar -> {
                    Text(
                        text = payload.title,
                        style = PetalText.sheetTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    payload.location?.let {
                        Text(text = "Location: $it", style = PetalText.cardBody, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is ScanPayload.Otp -> {
                    Text(
                        text = payload.account ?: "Authentication Code",
                        style = PetalText.sheetTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Issuer: ${payload.issuer ?: "Unknown"} (${payload.type.uppercase()})",
                        style = PetalText.cardBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ScanPayload.Product -> {
                    Text(
                        text = payload.code,
                        style = PetalText.sheetTitle.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Format: ${payload.format}",
                        style = PetalText.cardBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ScanPayload.Geo -> {
                    Text(
                        text = "${payload.latitude}, ${payload.longitude}",
                        style = PetalText.sheetTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    payload.query?.let {
                        Text(text = it, style = PetalText.cardBody, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is ScanPayload.Text -> {
                    Text(
                        text = payload.content,
                        style = PetalText.cardBody,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    )
                }
            }

            // ── Link Safety Banner (for URLs) ──
            val safetyReport = currentResult.safetyReport
            if (safetyReport != null && safetyReport.isSuspicious && PetalScannerSettings.isSafetyWarningsEnabled(context)) {
                Spacer(Modifier.height(12.dp))
                val isHighRisk = safetyReport.riskLevel == RiskLevel.HIGH
                val bannerColor = if (isHighRisk) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
                val bannerOnColor = if (isHighRisk) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = bannerColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isHighRisk) Icons.Rounded.Warning else Icons.Rounded.Info,
                                contentDescription = null,
                                tint = bannerOnColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isHighRisk) "Security Alert: Suspicious Link" else "Link Notice",
                                style = PetalText.cardTitle,
                                color = bannerOnColor
                            )
                        }
                        safetyReport.warnings.forEach { warning ->
                            Text(
                                text = "• ${warning.title}: ${warning.description}",
                                style = PetalText.cardBody,
                                color = bannerOnColor.copy(alpha = 0.9f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Action Buttons ──
            ActionButtonsLayout(
                payload = payload,
                rawValue = currentResult.rawValue,
                isLiquidGlass = isLiquidGlass,
                onOpenInBrowser = { inNewTab ->
                    if (payload is ScanPayload.Url) {
                        onOpenInBrowser(payload.url, inNewTab)
                        onDismissRequest()
                    }
                },
                onShowQr = { showQrGenerator = true },
                onDismiss = onDismissRequest
            )
        }
    }
}

@Composable
private fun ActionButtonsLayout(
    payload: ScanPayload,
    rawValue: String,
    isLiquidGlass: Boolean,
    onOpenInBrowser: (inNewTab: Boolean) -> Unit,
    onShowQr: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (payload) {
            is ScanPayload.Url -> {
                Button(
                    onClick = { onOpenInBrowser(false) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ExpressiveButtonShape
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_open))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onOpenInBrowser(true) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = ExpressiveButtonShape
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Tab")
                    }

                    FilledTonalButton(
                        onClick = {
                            PetalScanActionHandler.copyToClipboard(context, payload.url)
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = ExpressiveButtonShape
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                }
            }
            is ScanPayload.Wifi -> {
                Button(
                    onClick = {
                        PetalScanActionHandler.connectWifi(context, payload)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ExpressiveButtonShape
                ) {
                    Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connect to Network")
                }

                if (!payload.password.isNullOrEmpty()) {
                    FilledTonalButton(
                        onClick = {
                            PetalScanActionHandler.copyToClipboard(context, payload.password, isSensitive = true)
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = ExpressiveButtonShape
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy Password")
                    }
                }
            }
            is ScanPayload.Contact -> {
                Button(
                    onClick = {
                        PetalScanActionHandler.addContact(context, payload)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ExpressiveButtonShape
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add to Contacts")
                }

                if (!payload.phone.isNullOrEmpty()) {
                    FilledTonalButton(
                        onClick = {
                            PetalScanActionHandler.dialPhone(context, payload.phone)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = ExpressiveButtonShape
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Call ${payload.phone}")
                    }
                }
            }
            is ScanPayload.Email -> {
                Button(
                    onClick = {
                        PetalScanActionHandler.composeEmail(context, payload)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ExpressiveButtonShape
                ) {
                    Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send Email")
                }
            }
            is ScanPayload.Phone -> {
                Button(
                    onClick = {
                        PetalScanActionHandler.dialPhone(context, payload.number)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ExpressiveButtonShape
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Call Number")
                }
            }
            is ScanPayload.Sms -> {
                Button(
                    onClick = {
                        PetalScanActionHandler.sendSms(context, payload)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ExpressiveButtonShape
                ) {
                    Icon(Icons.Rounded.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send SMS")
                }
            }
            is ScanPayload.Geo -> {
                Button(
                    onClick = {
                        PetalScanActionHandler.openMap(context, payload)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ExpressiveButtonShape
                ) {
                    Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open in Maps")
                }
            }
            is ScanPayload.Calendar -> {
                Button(
                    onClick = {
                        PetalScanActionHandler.addCalendarEvent(context, payload)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ExpressiveButtonShape
                ) {
                    Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add to Calendar")
                }
            }
            is ScanPayload.Otp -> {
                Button(
                    onClick = {
                        PetalScanActionHandler.copyToClipboard(context, payload.secret, isSensitive = true)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ExpressiveButtonShape
                ) {
                    Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Secret Key")
                }
            }
            is ScanPayload.Product -> {
                Button(
                    onClick = {
                        PetalScanActionHandler.searchWeb(context, payload.code)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ExpressiveButtonShape
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Search Product")
                }
            }
            is ScanPayload.Text -> {
                Button(
                    onClick = {
                        PetalScanActionHandler.copyToClipboard(context, payload.content)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ExpressiveButtonShape
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Text")
                }
            }
        }

        // Secondary Utility Row: Share & QR Generator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    PetalScanActionHandler.shareText(context, rawValue)
                },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = ExpressiveButtonShape
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }

            FilledTonalButton(
                onClick = onShowQr,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = ExpressiveButtonShape
            ) {
                Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("QR Code")
            }
        }
    }
}

private fun getPayloadMeta(payload: ScanPayload): Pair<ImageVector, String> {
    return when (payload) {
        is ScanPayload.Url -> Pair(Icons.Rounded.Language, "Website Link")
        is ScanPayload.Email -> Pair(Icons.Rounded.Email, "Email Address")
        is ScanPayload.Phone -> Pair(Icons.Rounded.Phone, "Phone Number")
        is ScanPayload.Sms -> Pair(Icons.Rounded.Sms, "SMS Message")
        is ScanPayload.Wifi -> Pair(Icons.Rounded.Wifi, "Wi-Fi Network")
        is ScanPayload.Contact -> Pair(Icons.Rounded.Contacts, "Contact Card")
        is ScanPayload.Calendar -> Pair(Icons.Rounded.Event, "Calendar Event")
        is ScanPayload.Otp -> Pair(Icons.Rounded.VpnKey, "Two-Factor Auth")
        is ScanPayload.Product -> Pair(Icons.Rounded.ShoppingBag, "Product Barcode")
        is ScanPayload.Geo -> Pair(Icons.Rounded.LocationOn, "Geographic Coordinates")
        is ScanPayload.Text -> Pair(Icons.Rounded.Article, "Plain Text")
    }
}
