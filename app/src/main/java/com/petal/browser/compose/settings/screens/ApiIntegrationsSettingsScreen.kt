package com.petal.browser.compose.settings.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.R
import com.petal.browser.compose.ai.AiProvider
import com.petal.browser.compose.ai.PetalAiResearchEngine
import com.petal.browser.compose.ai.PetalAiUsageTracker
import com.petal.browser.compose.ai.ResearchMode
import com.petal.browser.compose.ai.ondevice.PetalOnDeviceModelManager
import com.petal.browser.compose.settings.viewmodel.SearchHomeSettingsViewModel
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.scrollMotionBlur
import com.petal.browser.ui.containment.PetalBadgeVariant
import com.petal.browser.ui.containment.PetalFilterChip
import androidx.compose.material.icons.rounded.Check
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalGroupRow
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSettingsToggleRow
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalContainmentShape
import com.petal.browser.ui.containment.petalFilledTextFieldColors
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.containment.rememberPetalGroupShape
import com.petal.browser.ui.glass.rememberGlassSheen
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.kit.TextField

private const val ENDPOINT_OLLAMA = "http://localhost:11434/v1"
private const val ENDPOINT_LM_STUDIO = "http://localhost:1234/v1"
private const val ENDPOINT_LLAMA_CPP = "http://localhost:8080/v1"

@Composable
fun ApiIntegrationsSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: SearchHomeSettingsViewModel = hiltViewModel()
) {
    val enableLiveSuggestions by viewModel.enableLiveSuggestions.collectAsStateWithLifecycle()

    ApiIntegrationsSettingsScreenContent(
        enableLiveSuggestions = enableLiveSuggestions,
        onEnableLiveSuggestionsChange = viewModel::setEnableLiveSuggestions,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        targetHighlightItemId = targetHighlightItemId
    )
}

/**
 * One Petal containment block: the same grouped surface the rest of Settings uses (group position
 * corners, live liquid glass, press-free), with a Petal icon badge + title header and free content below.
 * Stack several of them inside a [PetalGroup] and the corners join up like a single connected card.
 */
@Composable
private fun HubBlock(
    position: PetalGroupPosition,
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    variant: PetalBadgeVariant = PetalBadgeVariant.PRIMARY,
    content: @Composable () -> Unit,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = rememberGlassSheen(isGlass)
    val shape = rememberPetalGroupShape(position)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = petalGroupSurfaceColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PetalGroupIconBadge(icon = icon, variant = variant)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            content()
        }
    }
}

/** Tonal inline note inside a block (warnings, success, help text). Always readable on glass or solid. */
@Composable
private fun HubNote(
    icon: ImageVector,
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = petalContainmentShape(16.dp),
        color = container,
        contentColor = content,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = content)
        }
    }
}

/** Selectable chip with the Petal check mark on the selected one. */
@Composable
private fun HubChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    PetalFilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null,
    )
}

@Composable
private fun hubFieldColors() = petalFilledTextFieldColors(
    container = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
        alpha = if (isLiquidGlassEnabled("sp_liquid_glass_containments")) 0.6f else 1f
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiIntegrationsSettingsScreenContent(
    enableLiveSuggestions: Boolean,
    onEnableLiveSuggestionsChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null
) {
    val context = LocalContext.current
    var selectedProvider by remember { mutableStateOf(PetalAiResearchEngine.getSelectedProvider(context)) }
    var currentKey by remember(selectedProvider) { mutableStateOf(PetalAiResearchEngine.getApiKey(context, selectedProvider)) }
    var selectedModel by remember(selectedProvider) { mutableStateOf(PetalAiResearchEngine.getSelectedModel(context, selectedProvider)) }
    var customEndpoint by remember { mutableStateOf(PetalAiResearchEngine.getCustomEndpoint(context)) }
    var customModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var testResultMsg by remember { mutableStateOf<String?>(null) }
    var showCustomAiGuide by remember { mutableStateOf(false) }
    var isTestingKey by remember { mutableStateOf(false) }
    var autoPageContext by remember { mutableStateOf(PetalAiResearchEngine.isAutoPageContextEnabled(context)) }
    var generateSuggestions by remember { mutableStateOf(PetalAiResearchEngine.isGeneratePageSuggestionsEnabled(context)) }
    var todayTokens by remember { mutableLongStateOf(PetalAiUsageTracker.getTodayTokens(context)) }
    var monthTokens by remember { mutableLongStateOf(PetalAiUsageTracker.getMonthTokens(context)) }

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val actionShape = petalContainmentShape(14.dp)
    val isCustom = selectedProvider == AiProvider.CUSTOM
    val isOnDevice = selectedProvider == AiProvider.LOCAL_ON_DEVICE

    Box(modifier = modifier.fillMaxSize()) {
        com.petal.browser.ui.components.PetalGlassHeaderLayout(
            modifier = Modifier.fillMaxSize(),
            pageBackground = { com.petal.browser.ui.components.M3ExpressiveVariableBackground(modifier = Modifier.fillMaxSize(), pageSeed = "api_integrations_settings") },
            header = { headerModifier, headerBackdrop ->
                ExpressiveHeader(
                    modifier = headerModifier,
                    backdrop = headerBackdrop,
                    title = "API & Integrations Hub",
                    subtitle = "AI Research Engines, API Keys & Local LLM Endpoints",
                    onBack = onNavigateBack
                )
            }
        ) { topInset ->

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollMotionBlur(scrollState)
                    .verticalScroll(scrollState, flingBehavior = com.petal.browser.ui.theme.layout.petalFlingBehavior())
                    .padding(start = 16.dp, end = 16.dp, top = topInset + 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── 1. Providers, keys, endpoints, models ─────────────────────────────────────────
                PetalSettingsSection(
                    title = stringResource(R.string.ui_petal_ai_api_keys_hub),
                    cardId = "ai_provider",
                    targetHighlightId = targetHighlightItemId,
                    onInfoClick = if (isCustom) ({ showCustomAiGuide = true }) else null,
                ) {
                    // Each block is one connected containment row; the list drives group corner positions.
                    val blocks = buildList<@Composable (PetalGroupPosition) -> Unit> {
                        // Provider picker
                        add { pos ->
                            HubBlock(
                                position = pos,
                                icon = Icons.Rounded.AutoAwesome,
                                title = stringResource(R.string.ui_active_ai_provider).trimEnd(':'),
                                subtitle = stringResource(R.string.ui_configure_ai_providers_api_keys),
                            ) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    AiProvider.entries.forEach { provider ->
                                        HubChip(
                                            selected = selectedProvider == provider,
                                            onClick = {
                                                selectedProvider = provider
                                                PetalAiResearchEngine.setSelectedProvider(context, provider)
                                                currentKey = PetalAiResearchEngine.getApiKey(context, provider)
                                                selectedModel = PetalAiResearchEngine.getSelectedModel(context, provider)
                                                testResultMsg = null
                                            },
                                            label = { Text(provider.displayName, maxLines = 1) },
                                        )
                                    }
                                }
                            }
                        }

                        // Custom / LAN endpoint
                        if (isCustom) {
                            add { pos ->
                                HubBlock(
                                    position = pos,
                                    icon = Icons.Rounded.CloudQueue,
                                    title = "Local & LAN Endpoint",
                                    subtitle = "OpenAI-compatible server on this phone or your network",
                                    variant = PetalBadgeVariant.SECONDARY,
                                ) {
                                    Text(
                                        "Local Server Presets",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        listOf(
                                            ENDPOINT_OLLAMA to stringResource(R.string.ui_petal_ai_preset_ollama),
                                            ENDPOINT_LM_STUDIO to stringResource(R.string.ui_petal_ai_preset_lmstudio),
                                            ENDPOINT_LLAMA_CPP to stringResource(R.string.ui_petal_ai_preset_llamacpp),
                                        ).forEach { (url, name) ->
                                            HubChip(
                                                selected = customEndpoint == url,
                                                onClick = {
                                                    customEndpoint = url
                                                    PetalAiResearchEngine.setCustomEndpoint(context, url)
                                                    testResultMsg = null
                                                },
                                                label = { Text(name, maxLines = 1) },
                                            )
                                        }
                                    }
                                    TextField(
                                        value = customEndpoint,
                                        onValueChange = { newEndpoint ->
                                            customEndpoint = newEndpoint
                                            PetalAiResearchEngine.setCustomEndpoint(context, newEndpoint)
                                            testResultMsg = null
                                        },
                                        label = { Text("Endpoint URL", maxLines = 1) },
                                        placeholder = { Text("http://192.168.1.50:11434/v1", maxLines = 1) },
                                        singleLine = true,
                                        leadingIcon = { Icon(Icons.Rounded.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        colors = hubFieldColors(),
                                        glass = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (PetalAiResearchEngine.isLocalEndpoint(context)) {
                                        HubNote(
                                            icon = Icons.Rounded.CheckCircle,
                                            text = stringResource(R.string.ui_petal_ai_local_no_key_needed),
                                            container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                            // Termux + Ollama help
                            add { pos ->
                                HubBlock(
                                    position = pos,
                                    icon = Icons.Rounded.Terminal,
                                    title = stringResource(R.string.ui_petal_ai_termux_help_title),
                                    variant = PetalBadgeVariant.TERTIARY,
                                ) {
                                    Text(
                                        stringResource(R.string.ui_petal_ai_termux_help_body),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // On-device Gemma model manager
                        if (isOnDevice) {
                            add { pos ->
                                val memStats = remember { PetalOnDeviceModelManager.getMemoryStats(context) }
                                val freeMb = memStats.availMemBytes / (1024 * 1024)
                                val hasModel = remember { PetalOnDeviceModelManager.getModelFile(context) != null }
                                HubBlock(
                                    position = pos,
                                    icon = Icons.Rounded.Memory,
                                    title = stringResource(R.string.ui_petal_ai_ondevice_model_manager),
                                    subtitle = "Runs fully offline on this phone",
                                    variant = PetalBadgeVariant.TERTIARY,
                                ) {
                                    if (!memStats.hasSufficientRam) {
                                        HubNote(
                                            icon = Icons.Rounded.WarningAmber,
                                            text = stringResource(R.string.ui_petal_ai_ondevice_ram_warning, "$freeMb MB"),
                                            container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                            content = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    } else {
                                        HubNote(
                                            icon = Icons.Rounded.CheckCircle,
                                            text = stringResource(R.string.ui_petal_ai_ondevice_ram_ok, "$freeMb MB"),
                                            container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    HubNote(
                                        icon = if (hasModel) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                        text = if (hasModel) "Model loaded: Ready for offline inference" else "No model file found on device.",
                                        container = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // API key (not needed for on-device inference)
                        if (!isOnDevice) {
                            add { pos ->
                                HubBlock(
                                    position = pos,
                                    icon = Icons.Rounded.VpnKey,
                                    title = if (isCustom) "API Key" else "${selectedProvider.displayName} API Key",
                                    subtitle = if (isCustom) "Optional for local AI" else "Stored on this device only",
                                    variant = PetalBadgeVariant.SECONDARY,
                                ) {
                                    TextField(
                                        value = currentKey,
                                        onValueChange = { newKey ->
                                            currentKey = newKey
                                            PetalAiResearchEngine.setApiKey(context, selectedProvider, newKey)
                                            testResultMsg = null
                                        },
                                        label = { Text(if (isCustom) "API Key (optional)" else "${selectedProvider.displayName} API Key", maxLines = 1) },
                                        placeholder = { Text(if (isCustom) "Leave blank if not required" else "Paste your key", maxLines = 1) },
                                        singleLine = true,
                                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        leadingIcon = { Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        trailingIcon = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                                    Icon(
                                                        if (isKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                        contentDescription = stringResource(R.string.ui_toggle_visibility)
                                                    )
                                                }
                                                if (currentKey.isNotBlank()) {
                                                    IconButton(onClick = {
                                                        currentKey = ""
                                                        PetalAiResearchEngine.setApiKey(context, selectedProvider, "")
                                                        testResultMsg = null
                                                    }) {
                                                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_clear_key))
                                                    }
                                                }
                                            }
                                        },
                                        colors = hubFieldColors(),
                                        glass = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (selectedProvider.keyUrl.isNotBlank()) {
                                        FilledTonalButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(selectedProvider.keyUrl)))
                                                }
                                            },
                                            shape = actionShape,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isGlass) 0.65f else 1f)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                            glass = false,
                                        ) {
                                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Get API Key", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }

                        // Connection test + model discovery
                        add { pos ->
                            HubBlock(
                                position = pos,
                                icon = Icons.Rounded.NetworkCheck,
                                title = stringResource(R.string.ui_test_connection),
                                subtitle = "Check that ${selectedProvider.displayName} responds",
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isCustom) {
                                        FilledTonalButton(
                                            enabled = customEndpoint.isNotBlank() && !isFetchingModels,
                                            onClick = {
                                                isFetchingModels = true
                                                testResultMsg = "Fetching models from endpoint..."
                                                PetalAiResearchEngine.fetchCustomModels(context) { res ->
                                                    isFetchingModels = false
                                                    res.onSuccess { fetched ->
                                                        if (fetched.isNotEmpty()) {
                                                            customModels = fetched
                                                            testResultMsg = "✓ Found ${fetched.size} model(s)"
                                                        } else {
                                                            testResultMsg = "Reachable, but no models found. Enter model name manually."
                                                        }
                                                    }.onFailure { err ->
                                                        testResultMsg = "✗ Fetch failed: ${err.message}"
                                                    }
                                                }
                                            },
                                            shape = actionShape,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isGlass) 0.65f else 1f)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                            glass = false,
                                        ) {
                                            if (isFetchingModels) {
                                                com.petal.browser.compose.composable.PetalContainedLoadingIndicator(size = 16.dp)
                                            } else {
                                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                stringResource(R.string.ui_fetch_models),
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    FilledTonalButton(
                                        // On-device and local endpoints don't need a key, so they're always testable.
                                        enabled = (currentKey.isNotBlank() || isCustom || isOnDevice) && !isTestingKey,
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
                                                    testResultMsg = if (res.isSuccess) "✓ AI Verified & Connected!" else "✗ Connection Failed: ${res.exceptionOrNull()?.message ?: "Connection Error"}"
                                                }
                                            )
                                        },
                                        shape = actionShape,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isGlass) 0.75f else 1f),
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                        glass = false,
                                    ) {
                                        if (isTestingKey) {
                                            com.petal.browser.compose.composable.PetalContainedLoadingIndicator(size = 16.dp)
                                        } else {
                                            Icon(Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            stringResource(R.string.ui_test_connection),
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                testResultMsg?.let { msg ->
                                    val ok = msg.startsWith("✓")
                                    val failed = msg.startsWith("✗")
                                    HubNote(
                                        icon = when {
                                            ok -> Icons.Rounded.CheckCircle
                                            failed -> Icons.Rounded.ErrorOutline
                                            else -> Icons.Rounded.NetworkCheck
                                        },
                                        text = msg.removePrefix("✓").removePrefix("✗").trim(),
                                        container = when {
                                            ok -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                            failed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                            else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                                        },
                                        content = when {
                                            ok -> MaterialTheme.colorScheme.onPrimaryContainer
                                            failed -> MaterialTheme.colorScheme.onErrorContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }

                        // Preferred model
                        add { pos ->
                            HubBlock(
                                position = pos,
                                icon = Icons.Rounded.Memory,
                                title = stringResource(R.string.ui_preferred_model, selectedProvider.displayName).trimEnd(':'),
                                variant = PetalBadgeVariant.TERTIARY,
                            ) {
                                if (isCustom) {
                                    TextField(
                                        value = selectedModel,
                                        onValueChange = { newModel ->
                                            selectedModel = newModel
                                            PetalAiResearchEngine.setSelectedModel(context, selectedProvider, newModel)
                                        },
                                        label = { Text(stringResource(R.string.ui_custom_model_id_name), maxLines = 1) },
                                        placeholder = { Text(stringResource(R.string.ui_e_g_llama3_latest_deepseek), maxLines = 1) },
                                        singleLine = true,
                                        leadingIcon = { Icon(Icons.Rounded.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        colors = hubFieldColors(),
                                        glass = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                val modelChoices = if (isCustom) customModels else selectedProvider.availableModels
                                if (modelChoices.isNotEmpty()) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        modelChoices.forEach { model ->
                                            HubChip(
                                                selected = selectedModel == model,
                                                onClick = {
                                                    selectedModel = model
                                                    PetalAiResearchEngine.setSelectedModel(context, selectedProvider, model)
                                                },
                                                label = { Text(model, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    PetalGroup(rowCount = blocks.size) { index, position -> blocks[index](position) }
                }

                // ── 2. Page context toggles ───────────────────────────────────────────────────────
                PetalSettingsSection(title = "Page Context & Intelligence") {
                    PetalGroup(rowCount = 2) { index, position ->
                        when (index) {
                            0 -> PetalSettingsToggleRow(
                                title = stringResource(R.string.ui_petal_ai_setting_auto_page_context),
                                subtitle = stringResource(R.string.ui_petal_ai_setting_auto_page_context_desc),
                                icon = Icons.Rounded.Description,
                                checked = autoPageContext,
                                position = position,
                                onCheckedChange = {
                                    autoPageContext = it
                                    PetalAiResearchEngine.setAutoPageContextEnabled(context, it)
                                }
                            )
                            else -> PetalSettingsToggleRow(
                                title = stringResource(R.string.ui_petal_ai_setting_generate_suggestions),
                                subtitle = stringResource(R.string.ui_petal_ai_setting_generate_suggestions_desc),
                                icon = Icons.Rounded.Lightbulb,
                                checked = generateSuggestions,
                                position = position,
                                onCheckedChange = {
                                    generateSuggestions = it
                                    PetalAiResearchEngine.setGeneratePageSuggestionsEnabled(context, it)
                                }
                            )
                        }
                    }
                }

                // ── 3. Token usage ────────────────────────────────────────────────────────────────
                PetalSettingsSection(title = stringResource(R.string.ui_petal_ai_usage_title)) {
                    PetalGroup(rowCount = 2) { index, position ->
                        when (index) {
                            0 -> HubBlock(
                                position = position,
                                icon = Icons.Rounded.DataUsage,
                                title = stringResource(R.string.ui_petal_ai_usage_today, todayTokens.toString()),
                                subtitle = stringResource(R.string.ui_petal_ai_usage_month, monthTokens.toString()),
                                variant = PetalBadgeVariant.SECONDARY,
                            ) {}
                            else -> PetalGroupRow(
                                icon = Icons.Rounded.DeleteSweep,
                                title = stringResource(R.string.ui_petal_ai_usage_reset),
                                position = position,
                                onClick = {
                                    PetalAiUsageTracker.resetUsage(context)
                                    todayTokens = 0L
                                    monthTokens = 0L
                                },
                                iconContainer = MaterialTheme.colorScheme.errorContainer,
                                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                                trailing = {},
                            )
                        }
                    }
                }

                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
        }
    }

    if (showCustomAiGuide) {
        com.petal.browser.ui.containment.PetalMaterialAlertDialog(
            onDismissRequest = { showCustomAiGuide = false },
            title = { Text(stringResource(R.string.ui_connect_any_openai_compatible_ai)) },
            text = { Text(stringResource(R.string.ui_1_choose_custom_ai_2)) },
            confirmButton = { TextButton(onClick = { showCustomAiGuide = false }) { Text(stringResource(R.string.ui_got_it)) } }
        )
    }
}
