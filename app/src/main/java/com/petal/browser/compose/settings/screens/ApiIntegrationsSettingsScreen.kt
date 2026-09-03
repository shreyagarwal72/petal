package com.petal.browser.compose.settings.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.compose.ai.AiProvider
import com.petal.browser.compose.ai.PetalAiResearchEngine
import com.petal.browser.compose.ai.ResearchMode
import com.petal.browser.compose.settings.viewmodel.SearchHomeSettingsViewModel
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.ScrollFadeRow

@Composable
fun ApiIntegrationsSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchHomeSettingsViewModel = hiltViewModel()
) {
    val enableLiveSuggestions by viewModel.enableLiveSuggestions.collectAsStateWithLifecycle()

    ApiIntegrationsSettingsScreenContent(
        enableLiveSuggestions = enableLiveSuggestions,
        onEnableLiveSuggestionsChange = viewModel::setEnableLiveSuggestions,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
fun ApiIntegrationsSettingsScreenContent(
    enableLiveSuggestions: Boolean,
    onEnableLiveSuggestionsChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedProvider by remember { mutableStateOf(PetalAiResearchEngine.getSelectedProvider(context)) }
    var currentKey by remember(selectedProvider) { mutableStateOf(PetalAiResearchEngine.getApiKey(context, selectedProvider)) }
    var selectedModel by remember(selectedProvider) { mutableStateOf(PetalAiResearchEngine.getSelectedModel(context, selectedProvider)) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var testResultMsg by remember { mutableStateOf<String?>(null) }
    var isTestingKey by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "api_integrations_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "API & Integrations Hub",
                subtitle = "AndroidX WebKit, Google Credential Manager & Palette APIs",
                onBack = onNavigateBack
            )

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dedicated Petal AI & API Keys Hub Card
                SettingsCategoryCard(title = "Petal AI & API Keys Hub", iconRes = com.petal.browser.R.drawable.ic_ai_stars) {
                    Text(
                        "Configure AI providers, API keys, and model selections for Petal Deep Research, AI Search, and page summarizer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Active AI Provider:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val aiProviderScrollState = rememberScrollState()
                    ScrollFadeRow(
                        scrollState = aiProviderScrollState,
                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(aiProviderScrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AiProvider.values().forEach { provider ->
                                val isSelected = selectedProvider == provider
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedProvider = provider
                                        PetalAiResearchEngine.setSelectedProvider(context, provider)
                                        currentKey = PetalAiResearchEngine.getApiKey(context, provider)
                                        selectedModel = PetalAiResearchEngine.getSelectedModel(context, provider)
                                        testResultMsg = null
                                    },
                                    label = { Text(provider.displayName) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    OutlinedTextField(
                        value = currentKey,
                        onValueChange = { newKey ->
                            currentKey = newKey
                            PetalAiResearchEngine.setApiKey(context, selectedProvider, newKey)
                            testResultMsg = null
                        },
                        label = { Text("${selectedProvider.displayName} API Key") },
                        placeholder = { Text("Paste your ${selectedProvider.displayName} API Key...") },
                        singleLine = true,
                        visualTransformation = if (isKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        if (isKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Toggle Visibility"
                                    )
                                }
                                if (currentKey.isNotBlank()) {
                                    IconButton(onClick = {
                                        currentKey = ""
                                        PetalAiResearchEngine.setApiKey(context, selectedProvider, "")
                                        testResultMsg = null
                                    }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear Key")
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(selectedProvider.keyUrl))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Get Free ${selectedProvider.displayName} Key", style = MaterialTheme.typography.labelSmall)
                        }

                        TextButton(
                            enabled = currentKey.isNotBlank() && !isTestingKey,
                            onClick = {
                                isTestingKey = true
                                testResultMsg = "Testing connection..."
                                PetalAiResearchEngine.performResearch(
                                    context = context,
                                    pageTitle = "Test Page",
                                    pageUrl = "https://petal.browser/test",
                                    pageTextContent = "Petal Browser API key verification test",
                                    mode = ResearchMode.CUSTOM,
                                    customPrompt = "Respond with 'OK' if API key is working cleanly.",
                                    onResult = { res ->
                                        isTestingKey = false
                                        testResultMsg = if (res.isSuccess) "✓ API Key Verified & Connected!" else "✗ Connection Failed: ${res.exceptionOrNull()?.message ?: "Invalid Key"}"
                                    }
                                )
                            }
                        ) {
                            if (isTestingKey) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text("Test Key", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (testResultMsg != null) {
                        Text(
                            text = testResultMsg!!,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (testResultMsg!!.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "Preferred ${selectedProvider.displayName} Model:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val modelScrollState = rememberScrollState()
                    ScrollFadeRow(
                        scrollState = modelScrollState,
                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(modelScrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedProvider.availableModels.forEach { model ->
                                val isSelected = selectedModel == model
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedModel = model
                                        PetalAiResearchEngine.setSelectedModel(context, selectedProvider, model)
                                    },
                                    label = { Text(model) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ToggleRow(
                        title = "Live Search Recommendations",
                        subtitle = "Fetch live autocomplete suggestions from Google, DuckDuckGo, or Bing while typing",
                        icon = Icons.Rounded.Search,
                        checked = enableLiveSuggestions,
                        onCheckedChange = onEnableLiveSuggestionsChange
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
