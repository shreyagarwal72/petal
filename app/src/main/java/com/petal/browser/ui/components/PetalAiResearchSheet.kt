package com.petal.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.compose.ai.AiProvider
import com.petal.browser.compose.ai.PetalAiResearchEngine
import com.petal.browser.compose.ai.ResearchMode
import com.petal.browser.unit.BrowserUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalAiResearchSheet(
    pageTitle: String,
    pageUrl: String,
    pageContent: String,
    initialMode: ResearchMode = ResearchMode.SUMMARY,
    autoStart: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var selectedProvider by remember { mutableStateOf(PetalAiResearchEngine.getSelectedProvider(context)) }
    var selectedModel by remember { mutableStateOf(PetalAiResearchEngine.getSelectedModel(context, selectedProvider)) }
    var apiKey by remember(selectedProvider) { mutableStateOf(PetalAiResearchEngine.getApiKey(context, selectedProvider)) }
    var customEndpoint by remember { mutableStateOf(PetalAiResearchEngine.getCustomEndpoint(context)) }
    var showApiKeyConfig by remember { mutableStateOf(apiKey.isBlank() && selectedProvider != AiProvider.CUSTOM) }

    var selectedMode by remember { mutableStateOf(initialMode) }
    var customPromptText by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var responseResult by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (autoStart && apiKey.isNotBlank() && !isLoading && responseResult == null) {
            isLoading = true
            PetalAiResearchEngine.performResearch(
                context = context,
                pageTitle = pageTitle,
                pageUrl = pageUrl,
                pageTextContent = pageContent,
                mode = selectedMode,
                customPrompt = customPromptText
            ) { result ->
                isLoading = false
                result.onSuccess { text ->
                    responseResult = text
                }.onFailure { err ->
                    errorMessage = err.localizedMessage ?: "AI Research request failed."
                }
            }
        }
    }

    var providerMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    val domainName = remember(pageUrl) {
        try {
            val uri = Uri.parse(pageUrl)
            uri.host ?: pageUrl
        } catch (e: Exception) { pageUrl }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            "AI Web Research",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            domainName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = { showApiKeyConfig = !showApiKeyConfig }) {
                    Icon(
                        Icons.Rounded.Key,
                        contentDescription = "API Keys",
                        tint = if (apiKey.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // API Provider & Model Selector Row
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Provider Dropdown
                    Box {
                        AssistChip(
                            onClick = { providerMenuExpanded = true },
                            label = { Text(selectedProvider.displayName, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Rounded.Psychology, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) }
                        )

                        DropdownMenu(
                            expanded = providerMenuExpanded,
                            onDismissRequest = { providerMenuExpanded = false }
                        ) {
                            AiProvider.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(provider.displayName, fontWeight = if (provider == selectedProvider) FontWeight.Bold else FontWeight.Normal)
                                            if (provider == AiProvider.GEMINI) {
                                                Spacer(Modifier.width(6.dp))
                                                Text("(Recommended)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedProvider = provider
                                        PetalAiResearchEngine.setSelectedProvider(context, provider)
                                        selectedModel = PetalAiResearchEngine.getSelectedModel(context, provider)
                                        apiKey = PetalAiResearchEngine.getApiKey(context, provider)
                                        showApiKeyConfig = apiKey.isBlank() && provider != AiProvider.CUSTOM
                                        providerMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Model Dropdown or Custom Model Input
                    if (selectedProvider == AiProvider.CUSTOM) {
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = { newModel ->
                                selectedModel = newModel
                                PetalAiResearchEngine.setSelectedModel(context, selectedProvider, newModel)
                            },
                            label = { Text("Model ID") },
                            placeholder = { Text("e.g. llama3, deepseek-r1") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    } else {
                        Box {
                            AssistChip(
                                onClick = { modelMenuExpanded = true },
                                label = {
                                    val displayModel = selectedModel.substringAfterLast("/")
                                    Text(displayModel, maxLines = 1)
                                },
                                leadingIcon = { Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) }
                            )

                            DropdownMenu(
                                expanded = modelMenuExpanded,
                                onDismissRequest = { modelMenuExpanded = false }
                            ) {
                                selectedProvider.availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model) },
                                        onClick = {
                                            selectedModel = model
                                            PetalAiResearchEngine.setSelectedModel(context, selectedProvider, model)
                                            modelMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Inline API Key Setup Banner if missing or toggled
            AnimatedVisibility(visible = showApiKeyConfig) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                if (selectedProvider == AiProvider.CUSTOM) "Configure Custom AI Endpoint" else "Configure ${selectedProvider.displayName} API Key",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (selectedProvider == AiProvider.CUSTOM) {
                            OutlinedTextField(
                                value = customEndpoint,
                                onValueChange = { newEp ->
                                    customEndpoint = newEp
                                    PetalAiResearchEngine.setCustomEndpoint(context, newEp)
                                },
                                label = { Text("Endpoint URL") },
                                placeholder = { Text("https://api.openai.com/v1 or http://localhost:11434/v1") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { newKey ->
                                apiKey = newKey
                                PetalAiResearchEngine.setApiKey(context, selectedProvider, newKey)
                            },
                            label = { Text(if (selectedProvider == AiProvider.CUSTOM) "API Key (Optional)" else "${selectedProvider.displayName} API Key") },
                            placeholder = { Text(if (selectedProvider == AiProvider.CUSTOM) "Paste API key (leave blank if none)..." else "Paste API key here...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedProvider.keyUrl.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        try {
                                            BrowserUnit.intentURL(context, Uri.parse(selectedProvider.keyUrl))
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                ) {
                                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Get API Key")
                                }
                            } else {
                                Spacer(Modifier.width(4.dp))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        onDismiss()
                                        val browserActivity = context as? com.petal.browser.activity.BrowserActivity
                                        if (browserActivity != null) {
                                            browserActivity.openApiIntegrationsHub()
                                        } else {
                                            val intent = Intent(context, com.petal.browser.activity.Settings_Activity::class.java).apply {
                                                putExtra(
                                                    com.petal.browser.activity.Settings_Activity.EXTRA_SETTINGS_CATEGORY,
                                                    com.petal.browser.compose.settings.SettingsCategory.API_INTEGRATIONS.name
                                                )
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("All AI Keys")
                                }

                                if (apiKey.isNotBlank()) {
                                    Button(onClick = { showApiKeyConfig = false }) {
                                        Text("Save")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Research Mode Selector Chips
            Text(
                "Research Mode",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedMode == ResearchMode.SUMMARY,
                    onClick = { selectedMode = ResearchMode.SUMMARY },
                    label = { Text("Summary") },
                    leadingIcon = { Icon(Icons.Rounded.Subject, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = selectedMode == ResearchMode.DEEP_RESEARCH,
                    onClick = { selectedMode = ResearchMode.DEEP_RESEARCH },
                    label = { Text("Deep") },
                    leadingIcon = { Icon(Icons.Rounded.Analytics, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = selectedMode == ResearchMode.KEY_QA,
                    onClick = { selectedMode = ResearchMode.KEY_QA },
                    label = { Text("Q&A") },
                    leadingIcon = { Icon(Icons.Rounded.QuestionAnswer, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = selectedMode == ResearchMode.CRITIQUE,
                    onClick = { selectedMode = ResearchMode.CRITIQUE },
                    label = { Text("Critique") },
                    leadingIcon = { Icon(Icons.Rounded.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            // Custom Prompt Input Field
            OutlinedTextField(
                value = customPromptText,
                onValueChange = {
                    customPromptText = it
                    if (it.isNotBlank()) selectedMode = ResearchMode.CUSTOM
                },
                placeholder = { Text("Ask custom question about this webpage...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    if (customPromptText.isNotBlank()) {
                        IconButton(onClick = { customPromptText = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )

            // Start Research Action Button
            Button(
                onClick = {
                    if (apiKey.isBlank() && selectedProvider != AiProvider.CUSTOM) {
                        showApiKeyConfig = true
                        com.petal.browser.view.NinjaToast.show(context, "Please enter an API Key first")
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    responseResult = null

                    PetalAiResearchEngine.performResearch(
                        context = context,
                        pageTitle = pageTitle,
                        pageUrl = pageUrl,
                        pageTextContent = pageContent,
                        mode = selectedMode,
                        customPrompt = customPromptText
                    ) { result ->
                        isLoading = false
                        result.onSuccess { text ->
                            responseResult = text
                        }.onFailure { err ->
                            errorMessage = err.localizedMessage ?: "AI Research request failed."
                        }
                    }
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Researching Webpage with ${selectedProvider.displayName}...")
                } else {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Analyze with ${selectedProvider.displayName}", fontWeight = FontWeight.Bold)
                }
            }

            // Error Display
            errorMessage?.let { err ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // AI Response Display Card
            responseResult?.let { response ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text("AI Insights", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("AI Research", response))
                                        com.petal.browser.view.NinjaToast.show(context, "Research copied to clipboard")
                                    }
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
                                }

                                IconButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "AI Research: $pageTitle")
                                            putExtra(Intent.EXTRA_TEXT, "$pageTitle\n$pageUrl\n\nAI Insights:\n$response")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Research"))
                                    }
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = "Share", modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        PetalMarkdownText(markdown = response)
                    }
                }
            }
        }
    }
}
