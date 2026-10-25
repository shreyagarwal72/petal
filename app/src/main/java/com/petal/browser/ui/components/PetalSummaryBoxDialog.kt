package com.petal.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.petal.browser.compose.ai.AiProvider
import com.petal.browser.compose.ai.PetalAiResearchEngine
import com.petal.browser.compose.ai.ResearchMode
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import androidx.preference.PreferenceManager
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PetalSummaryBoxDialog(
    pageTitle: String,
    pageUrl: String,
    pageContent: String,
    onAskQuestion: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var selectedProvider by remember { mutableStateOf(PetalAiResearchEngine.getSelectedProvider(context)) }
    var selectedModel by remember { mutableStateOf(PetalAiResearchEngine.getSelectedModel(context, selectedProvider)) }
    var apiKey by remember(selectedProvider) { mutableStateOf(PetalAiResearchEngine.getApiKey(context, selectedProvider)) }

    var isLoading by remember { mutableStateOf(true) }
    var summaryResult by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun generateSummary() {
        if (apiKey.isBlank()) {
            isLoading = false
            errorMessage = "Please set an API key for ${selectedProvider.displayName} in Petal AI Settings."
            return
        }
        isLoading = true
        errorMessage = null
        summaryResult = null

        PetalAiResearchEngine.performResearch(
            context = context,
            pageTitle = pageTitle,
            pageUrl = pageUrl,
            pageTextContent = pageContent,
            mode = ResearchMode.SUMMARY,
            customPrompt = ""
        ) { result ->
            isLoading = false
            result.onSuccess { text ->
                summaryResult = text
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "Summary request failed."
            }
        }
    }

    LaunchedEffect(Unit) {
        generateSummary()
    }

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = if (isGlass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0.60f
    val dialogShape = RoundedCornerShape(28.dp)

    Dialog(onDismissRequest = onDismiss) {
        com.petal.browser.ui.glass.PetalGlassContainer(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            enabled = isGlass,
            live = true
        ) {
            Surface(
                shape = dialogShape,
                color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = if (isGlass) 0.dp else 8.dp,
                shadowElevation = if (isGlass) 0.dp else 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .liquidGlassChrome(shape = dialogShape, enabled = isGlass, sheenIntensity = sheen)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Expressive Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isGlass) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.ui_page_summary),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${selectedProvider.displayName} • $selectedModel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.ui_ai_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_close))
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Scrollable Summary Content Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 320.dp)
                ) {
                    if (isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            com.petal.browser.compose.composable.PetalContainedLoadingIndicator(
                                size = 40.dp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.ui_generating_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (errorMessage != null) {
                        val errorCardShape = RoundedCornerShape(16.dp)
                        Surface(
                            shape = errorCardShape,
                            color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isGlass) Modifier.liquidGlassChrome(
                                        errorCardShape, enabled = true, sheenIntensity = sheen,
                                        tintColor = MaterialTheme.colorScheme.errorContainer
                                    ) else Modifier
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text(stringResource(R.string.ui_summary_failed), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                                Text(errorMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { generateSummary() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.ui_retry), maxLines = 1)
                                    }
                                    val cfgShape = RoundedCornerShape(12.dp)
                                    OutlinedButton(
                                        onClick = onOpenSettings,
                                        shape = cfgShape,
                                        border = if (isGlass) null
                                            else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        modifier = if (isGlass) Modifier.liquidGlassChrome(cfgShape, enabled = true, sheenIntensity = 0.5f) else Modifier
                                    ) {
                                        Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            stringResource(R.string.ui_configure_key),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    } else if (summaryResult != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                        ) {
                            PetalMarkdownText(markdown = summaryResult!!)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Bottom Action Controls: Copy, Share & Ask Question Primary Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            enabled = summaryResult != null,
                            onClick = {
                                summaryResult?.let { text ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Page Summary", text))
                                    com.petal.browser.view.PetalToast.show(context, "Summary copied to clipboard")
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.ui_copy_summary), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        IconButton(
                            enabled = summaryResult != null,
                            onClick = {
                                summaryResult?.let { text ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Summary: $pageTitle")
                                        putExtra(Intent.EXTRA_TEXT, "$pageTitle\n$pageUrl\n\nSummary:\n$text")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Summary"))
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.ui_share_summary), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Primary Expressive "Ask Question" Button at last
                    val askButtonShape = RoundedCornerShape(16.dp)
                    Button(
                        onClick = onAskQuestion,
                        shape = askButtonShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_ask_question), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}
