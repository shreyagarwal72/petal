package com.petal.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
    val focusManager = LocalFocusManager.current
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

    fun runResearch(mode: ResearchMode, prompt: String) {
        if (apiKey.isBlank() && selectedProvider != AiProvider.CUSTOM) {
            showApiKeyConfig = true
            com.petal.browser.view.NinjaToast.show(context, "Please configure an API Key first")
            return
        }
        focusManager.clearFocus()
        isLoading = true
        errorMessage = null
        responseResult = null

        PetalAiResearchEngine.performResearch(
            context = context,
            pageTitle = pageTitle,
            pageUrl = pageUrl,
            pageTextContent = pageContent,
            mode = mode,
            customPrompt = prompt
        ) { result ->
            isLoading = false
            result.onSuccess { text ->
                responseResult = text
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "AI Research request failed."
            }
        }
    }

    LaunchedEffect(Unit) {
        if (autoStart && apiKey.isNotBlank() && !isLoading && responseResult == null) {
            runResearch(selectedMode, customPromptText)
        }
    }

    var providerMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    val domainName = remember(pageUrl) {
        try {
            val uri = Uri.parse(pageUrl)
            val host = uri.host
            if (!host.isNullOrBlank()) host else pageUrl
        } catch (e: Exception) { pageUrl }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (showApiKeyConfig) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "keyChevron"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar: Expressive M3 Hero Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "AI Web Research",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                domainName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Surface(
                    onClick = { showApiKeyConfig = !showApiKeyConfig },
                    shape = RoundedCornerShape(12.dp),
                    color = if (apiKey.isBlank() && selectedProvider != AiProvider.CUSTOM)
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.VpnKey,
                            contentDescription = "API Keys",
                            modifier = Modifier.size(16.dp),
                            tint = if (apiKey.isBlank() && selectedProvider != AiProvider.CUSTOM)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(chevronRotation),
                            tint = if (apiKey.isBlank() && selectedProvider != AiProvider.CUSTOM)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // API Provider & Model Selector Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Provider Dropdown
                    Box {
                        AssistChip(
                            onClick = { providerMenuExpanded = true },
                            label = { Text(selectedProvider.displayName, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )

                        DropdownMenu(
                            expanded = providerMenuExpanded,
                            onDismissRequest = { providerMenuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            AiProvider.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                provider.displayName,
                                                fontWeight = if (provider == selectedProvider) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (provider == AiProvider.GEMINI) {
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    "(Recommended)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
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
                            placeholder = { Text("llama3, mistral...") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    } else {
                        Box {
                            AssistChip(
                                onClick = { modelMenuExpanded = true },
                                label = {
                                    val displayModel = selectedModel.substringAfterLast("/")
                                    Text(
                                        displayModel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Memory,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            )

                            DropdownMenu(
                                expanded = modelMenuExpanded,
                                onDismissRequest = { modelMenuExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
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

            // Inline API Key Setup Banner (Pure Material 3 Expressive Container)
            AnimatedVisibility(
                visible = showApiKeyConfig,
                enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(240))
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(
                        1.dp,
                        if (apiKey.isBlank() && selectedProvider != AiProvider.CUSTOM)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.VpnKey,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    if (selectedProvider == AiProvider.CUSTOM) "Custom AI Endpoint" else "${selectedProvider.displayName} API Key",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (selectedProvider == AiProvider.CUSTOM) "Connect local Ollama or custom OpenAI endpoint" else "Required to analyze webpages and generate insights",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                                shape = RoundedCornerShape(14.dp)
                            )
                        }

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { newKey ->
                                apiKey = newKey
                                PetalAiResearchEngine.setApiKey(context, selectedProvider, newKey)
                            },
                            label = { Text(if (selectedProvider == AiProvider.CUSTOM) "API Key (Optional)" else "${selectedProvider.displayName} Key") },
                            placeholder = { Text(if (selectedProvider == AiProvider.CUSTOM) "Paste key if required..." else "Paste your API key here...") },
                            singleLine = true,
                            trailingIcon = {
                                if (apiKey.isNotBlank()) {
                                    IconButton(onClick = {
                                        apiKey = ""
                                        PetalAiResearchEngine.setApiKey(context, selectedProvider, "")
                                    }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
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
                                    Spacer(Modifier.width(6.dp))
                                    Text("Get Free Key", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                Spacer(Modifier.width(4.dp))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
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
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Manage Keys", style = MaterialTheme.typography.labelMedium)
                                }

                                if (apiKey.isNotBlank() || selectedProvider == AiProvider.CUSTOM) {
                                    Button(
                                        onClick = { showApiKeyConfig = false },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Done", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Research Mode Selector: Pure Horizontal Scroll with Spaced Expressive FilterChips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Research Mode",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedMode == ResearchMode.SUMMARY,
                        onClick = { selectedMode = ResearchMode.SUMMARY },
                        label = { Text("Summary") },
                        leadingIcon = {
                            Icon(
                                if (selectedMode == ResearchMode.SUMMARY) Icons.Rounded.Check else Icons.Rounded.Subject,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = selectedMode == ResearchMode.DEEP_RESEARCH,
                        onClick = { selectedMode = ResearchMode.DEEP_RESEARCH },
                        label = { Text("Deep Research") },
                        leadingIcon = {
                            Icon(
                                if (selectedMode == ResearchMode.DEEP_RESEARCH) Icons.Rounded.Check else Icons.Rounded.Analytics,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = selectedMode == ResearchMode.KEY_QA,
                        onClick = { selectedMode = ResearchMode.KEY_QA },
                        label = { Text("Key Q&A") },
                        leadingIcon = {
                            Icon(
                                if (selectedMode == ResearchMode.KEY_QA) Icons.Rounded.Check else Icons.Rounded.QuestionAnswer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = selectedMode == ResearchMode.CRITIQUE,
                        onClick = { selectedMode = ResearchMode.CRITIQUE },
                        label = { Text("Critique & Fact-Check") },
                        leadingIcon = {
                            Icon(
                                if (selectedMode == ResearchMode.CRITIQUE) Icons.Rounded.Check else Icons.Rounded.FactCheck,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
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
                shape = RoundedCornerShape(18.dp),
                leadingIcon = {
                    Icon(
                        Icons.Rounded.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (customPromptText.isNotBlank()) {
                        IconButton(onClick = { customPromptText = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    runResearch(if (customPromptText.isNotBlank()) ResearchMode.CUSTOM else selectedMode, customPromptText)
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )

            // Primary Action Button with Material 3 Expressive Progress Feedback
            Button(
                onClick = {
                    runResearch(if (customPromptText.isNotBlank()) ResearchMode.CUSTOM else selectedMode, customPromptText)
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Analyzing with ${selectedProvider.displayName}...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                } else {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (customPromptText.isNotBlank()) "Ask ${selectedProvider.displayName}" else "Analyze with ${selectedProvider.displayName}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Error Display Card
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                errorMessage?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { errorMessage = null }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // AI Response Display Card
            AnimatedVisibility(
                visible = responseResult != null,
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(400)),
                exit = fadeOut() + shrinkVertically()
            ) {
                responseResult?.let { response ->
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.SmartToy,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "AI Insights",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("AI Research", response))
                                            com.petal.browser.view.NinjaToast.show(context, "Research copied to clipboard")
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "AI Research: $pageTitle")
                                                putExtra(Intent.EXTRA_TEXT, "$pageTitle\n$pageUrl\n\nAI Insights:\n$response")
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Research"))
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Rounded.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            PetalMarkdownText(markdown = response)
                        }
                    }
                }
            }
        }
    }
}
