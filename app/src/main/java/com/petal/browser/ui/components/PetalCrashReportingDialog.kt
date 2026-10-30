/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT/TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.petal.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.PetalExpressiveTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.preference.PreferenceManager
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.logger.PetalAppLogger
import com.petal.browser.view.PetalToast
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.PetalDialog
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

/**
 * Material 3 Expressive Crash Recovery Dialog for Petal Browser.
 * Appears seamlessly on startup when a crash from the previous session is detected.
 * Provides instant options to view the trace, export full diagnostic logs (.zip),
 * and report directly on GitHub.
 */
object PetalCrashReportingBridge {
    @JvmStatic
    fun showCrashRecoveryPromptIfNeeded(activity: ComponentActivity) {
        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
        val reportMode = sp.getString(PetalAppLogger.PREF_CRASH_REPORT_MODE, "off") ?: "off"
        if (reportMode == "off" || !PetalAppLogger.hasPendingCrashReport()) {
            return
        }

        activity.runOnUiThread {
            try {
                var composeView: ComposeView? = null
                composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setContent {
                        PetalExpressiveTheme {
                            PetalCrashRecoveryHost(
                                onDismiss = {
                                    val parent = composeView?.parent as? android.view.ViewGroup
                                    parent?.removeView(composeView)
                                }
                            )
                        }
                    }
                }
                val rootView = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                rootView?.addView(
                    composeView,
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun PetalCrashRecoveryHost(
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val reportMode = remember { sp.getString(PetalAppLogger.PREF_CRASH_REPORT_MODE, "off") ?: "off" }

    var showCrashDialog by remember {
        mutableStateOf(reportMode != "off" && PetalAppLogger.hasPendingCrashReport())
    }
    var showFullTraceDialog by remember { mutableStateOf(false) }

    val handleDismiss: () -> Unit = {
        showCrashDialog = false
        PetalAppLogger.clearPendingCrashReport(context)
        onDismiss?.invoke()
    }

    if (showCrashDialog) {
        val crashReport = remember { PetalAppLogger.getLastCrashReport() ?: "" }
        val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
        val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)

        PetalDialog(
            onDismissRequest = handleDismiss,
            shape = RoundedCornerShape(28.dp)
        ) {
            // Squircle Icon Badge + Centered Title
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .size(56.dp)
                        .then(
                            if (isGlass) Modifier.liquidGlassChrome(
                                shape = RoundedCornerShape(18.dp),
                                enabled = true,
                                sheenIntensity = 0.5f
                            ) else Modifier
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.ui_petal_browser_crashed),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Text(
                text = stringResource(R.string.ui_petal_recovered_from_an_unexpected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Contained crash snippet preview
            val previewShape = RoundedCornerShape(16.dp)
            Surface(
                shape = previewShape,
                color = if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isGlass) 0.25f else 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isGlass) Modifier.liquidGlassChrome(previewShape, enabled = true, sheenIntensity = 0.35f) else Modifier)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val firstLines = crashReport.lineSequence().take(4).joinToString("\n")
                    Text(
                        text = firstLines.ifBlank { "Uncaught Exception" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 4
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Expressive Button Layout: Never squishes or wraps vertically!
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary Action: Full Width Report on GitHub Button
                Button(
                    onClick = {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                        PetalAppLogger.openGitHubCrashIssue(context, crashReport)
                        handleDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .then(
                            if (isGlass) Modifier.liquidGlassChrome(
                                shape = RoundedCornerShape(16.dp),
                                enabled = true,
                                sheenIntensity = 0.65f,
                                pressFx = true
                            ) else Modifier
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ui_report_on_github),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Secondary Actions Row: Symmetrical View Log + Dismiss
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.TICK, 0.4f)
                            showFullTraceDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .then(
                                if (isGlass) Modifier.liquidGlassChrome(
                                    shape = RoundedCornerShape(14.dp),
                                    enabled = true,
                                    sheenIntensity = 0.35f,
                                    pressFx = true
                               ) else Modifier
                            ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.ui_view_log),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = handleDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .then(
                                if (isGlass) Modifier.liquidGlassChrome(
                                    shape = RoundedCornerShape(14.dp),
                                    enabled = true,
                                    sheenIntensity = 0.25f,
                                    pressFx = true
                                ) else Modifier
                            ),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isGlass) 0.3f else 0.7f))
                    ) {
                        Text(
                            text = stringResource(R.string.ui_dismiss),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    if (showFullTraceDialog) {
        val crashReport = remember { PetalAppLogger.getLastCrashReport() ?: "No log found" }
        val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
        val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)

        Dialog(onDismissRequest = { showFullTraceDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .then(
                        if (isGlass) Modifier.liquidGlassChrome(
                            shape = RoundedCornerShape(28.dp),
                            enabled = true,
                            sheenIntensity = sheen
                        ) else Modifier
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.ui_crash_diagnostics),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        IconButton(onClick = { showFullTraceDialog = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_close))
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isGlass) MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        val verticalScroll = rememberScrollState()
                        val horizontalScroll = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .verticalScroll(verticalScroll)
                                .horizontalScroll(horizontalScroll)
                        ) {
                            Text(
                                text = crashReport,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Petal Crash Log", crashReport))
                                    PetalToast.show(context, "Copied crash log to clipboard")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .then(
                                    if (isGlass) Modifier.liquidGlassChrome(
                                        shape = RoundedCornerShape(14.dp),
                                        enabled = true,
                                        sheenIntensity = 0.35f,
                                        pressFx = true
                                    ) else Modifier
                                ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ui_copy), fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                PetalAppLogger.shareLogsZip(context)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .then(
                                    if (isGlass) Modifier.liquidGlassChrome(
                                        shape = RoundedCornerShape(14.dp),
                                        enabled = true,
                                        sheenIntensity = 0.65f,
                                        pressFx = true
                                    ) else Modifier
                                ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ui_share_zip), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable M3 Expressive Crash Reporting Picker Component.
 * Inspired by Essentials' CrashReportingPicker.
 * Allows toggling between "Auto" (default) and "Off".
 */
@Composable
fun PetalCrashReportingPicker(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val options = listOf("auto", "off")
    val labels = listOf("Auto (Default)", "Off")

    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
    val pickerShape = com.petal.browser.ui.containment.petalGroupShape(com.petal.browser.ui.containment.PetalGroupPosition.TOP)

    Surface(
        shape = pickerShape,
        color = com.petal.browser.ui.containment.petalGroupSurfaceColor(),
        modifier = modifier
            .fillMaxWidth()
            .then(if (isGlass) Modifier.liquidGlassChrome(pickerShape, enabled = true) else Modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ui_crash_diagnostics_reporting),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (selectedMode == "auto") "Automatically captures crash logs and offers recovery reports" else "Crash logs and recovery prompts are disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEachIndexed { index, option ->
                    val isChecked = selectedMode == option
                    if (isChecked) {
                        Button(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                onModeSelected(option)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(labels[index], fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                onModeSelected(option)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(labels[index])
                        }
                    }
                }
            }
        }
    }
}
