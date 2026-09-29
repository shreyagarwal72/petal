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
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.PetalSectionLabel
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalStatusHeroCard
import com.petal.browser.ui.containment.petalGroupShape
import com.petal.browser.ui.theme.PetalMaterialShapes

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
            com.petal.browser.view.PetalToast.show(context, "Please configure an API Key first")
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

    val isKeyMissing = apiKey.isBlank() && selectedProvider != AiProvider.CUSTOM
    val isCustomProvider = selectedProvider == AiProvider.CUSTOM

    fun openAiHub() {
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

    val modeOptions = listOf(
        AiModeOption(ResearchMode.SUMMARY, Icons.Rounded.Summarize, stringResource(R.string.ui_summary), "Key points and conclusions at a glance"),
        AiModeOption(ResearchMode.DEEP_RESEARCH, Icons.Rounded.Analytics, stringResource(R.string.ui_deep_research), "Arguments, data points and core insights"),
        AiModeOption(ResearchMode.KEY_QA, Icons.Rounded.QuestionAnswer, stringResource(R.string.ui_key_q_a), "The questions this page actually answers"),
        AiModeOption(ResearchMode.CRITIQUE, Icons.Rounded.FactCheck, stringResource(R.string.ui_critique_fact_check), "Check claims, sources and bias"),
    )

    com.petal.browser.ui.containment.PetalSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PetalGroupIconBadge(
                        shape = PetalMaterialShapes.Cookie6Sided.toShape(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        size = 48.dp,
                        iconSize = 24.dp
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.ui_petal_ai),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ui_ai_web_research),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                FilledTonalIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_close), modifier = Modifier.size(20.dp))
                }
            }

            // ── Page context + provider status ──
            PetalStatusHeroCard(
                title = pageTitle.ifBlank { domainName }.take(90),
                subtitle = domainName,
                statusText = "${selectedProvider.displayName} • ${selectedModel.substringAfterLast("/")}",
                icon = Icons.Rounded.Language,
                statusActive = !isKeyMissing,
                actionLabel = when {
                    showApiKeyConfig -> "Hide key"
                    isKeyMissing -> "Add key"
                    else -> "API key"
                },
                onActionClick = { showApiKeyConfig = !showApiKeyConfig }
            )

            // ── Model selection ──
            PetalSettingsSection(title = "Model", icon = Icons.Rounded.Psychology) {
                PetalGroup(rowCount = 2) { index, position ->
                    when {
                        index == 0 -> PetalGroupListRow(
                            position = position,
                            onClick = { providerMenuExpanded = true },
                            leading = { PetalGroupIconBadge(Icons.Rounded.Psychology) },
                            content = {
                                Text("Provider", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    selectedProvider.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailing = {
                                Box {
                                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    com.petal.browser.ui.containment.PetalPopupMenu(
                                        expanded = providerMenuExpanded,
                                        onDismissRequest = { providerMenuExpanded = false }
                                    ) {
                                        AiProvider.entries.forEach { provider ->
                                            com.petal.browser.ui.containment.PetalPopupMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            provider.displayName,
                                                            fontWeight = if (provider == selectedProvider) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                        if (provider == AiProvider.GEMINI) {
                                                            Spacer(Modifier.width(6.dp))
                                                            Text(
                                                                stringResource(R.string.ui_recommended),
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
                            }
                        )

                        isCustomProvider -> AiFieldRow(position) {
                            OutlinedTextField(
                                value = selectedModel,
                                onValueChange = { newModel ->
                                    selectedModel = newModel
                                    PetalAiResearchEngine.setSelectedModel(context, selectedProvider, newModel)
                                },
                                label = { Text(stringResource(R.string.ui_model_id)) },
                                placeholder = { Text(stringResource(R.string.ui_llama3_mistral)) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                shape = RoundedCornerShape(16.dp),
                                colors = aiFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        else -> PetalGroupListRow(
                            position = position,
                            onClick = { modelMenuExpanded = true },
                            leading = {
                                PetalGroupIconBadge(
                                    Icons.Rounded.Memory,
                                    container = MaterialTheme.colorScheme.secondaryContainer,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            },
                            content = {
                                Text("Model", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    selectedModel.substringAfterLast("/"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailing = {
                                Box {
                                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    com.petal.browser.ui.containment.PetalPopupMenu(
                                        expanded = modelMenuExpanded,
                                        onDismissRequest = { modelMenuExpanded = false }
                                    ) {
                                        selectedProvider.availableModels.forEach { model ->
                                            com.petal.browser.ui.containment.PetalPopupMenuItem(
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
                        )
                    }
                }
            }

            // ── Inline API key setup ──
            AnimatedVisibility(
                visible = showApiKeyConfig,
                enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(240))
            ) {
                val keyRows = buildList {
                    if (isCustomProvider) add(AiKeyRow.ENDPOINT)
                    add(AiKeyRow.KEY)
                    if (selectedProvider.keyUrl.isNotBlank()) add(AiKeyRow.GET_KEY)
                    add(AiKeyRow.MANAGE)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PetalSettingsSection(
                        title = if (isCustomProvider) "Custom AI endpoint" else "${selectedProvider.displayName} API key",
                        icon = Icons.Rounded.VpnKey
                    ) {
                        PetalGroup(rowCount = keyRows.size) { index, position ->
                            when (keyRows[index]) {
                                AiKeyRow.ENDPOINT -> AiFieldRow(position) {
                                    OutlinedTextField(
                                        value = customEndpoint,
                                        onValueChange = { newEp ->
                                            customEndpoint = newEp
                                            PetalAiResearchEngine.setCustomEndpoint(context, newEp)
                                        },
                                        label = { Text(stringResource(R.string.ui_endpoint_url)) },
                                        placeholder = { Text(stringResource(R.string.ui_https_api_openai_com_v1)) },
                                        supportingText = { Text("Connect local Ollama or any OpenAI-compatible endpoint") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = aiFieldColors(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                AiKeyRow.KEY -> AiFieldRow(position) {
                                    OutlinedTextField(
                                        value = apiKey,
                                        onValueChange = { newKey ->
                                            apiKey = newKey
                                            PetalAiResearchEngine.setApiKey(context, selectedProvider, newKey)
                                        },
                                        label = { Text(if (isCustomProvider) "API Key (Optional)" else "${selectedProvider.displayName} Key") },
                                        placeholder = { Text(if (isCustomProvider) "Paste key if required..." else "Paste your API key here...") },
                                        supportingText = { Text("Required to analyze webpages and generate insights") },
                                        singleLine = true,
                                        trailingIcon = {
                                            if (apiKey.isNotBlank()) {
                                                IconButton(onClick = {
                                                    apiKey = ""
                                                    PetalAiResearchEngine.setApiKey(context, selectedProvider, "")
                                                }) {
                                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_clear), modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = aiFieldColors(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                AiKeyRow.GET_KEY -> com.petal.browser.ui.containment.PetalGroupNavigationRow(
                                    title = stringResource(R.string.ui_get_free_key),
                                    subtitle = "Open the ${selectedProvider.displayName} key page",
                                    position = position,
                                    onClick = {
                                        try {
                                            BrowserUnit.intentURL(context, Uri.parse(selectedProvider.keyUrl))
                                        } catch (e: Exception) { e.printStackTrace() }
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) }
                                )

                                AiKeyRow.MANAGE -> com.petal.browser.ui.containment.PetalGroupNavigationRow(
                                    title = stringResource(R.string.ui_manage_keys),
                                    subtitle = "Providers, models and grounding in Settings",
                                    position = position,
                                    onClick = { openAiHub() },
                                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) }
                                )
                            }
                        }
                    }

                    if (apiKey.isNotBlank() || isCustomProvider) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = { showApiKeyConfig = false }) {
                                Text(stringResource(R.string.ui_done), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Research mode ──
            PetalSettingsSection(title = stringResource(R.string.ui_research_mode), icon = Icons.Rounded.Tune) {
                PetalGroup(rowCount = modeOptions.size) { index, position ->
                    val option = modeOptions[index]
                    val selected = selectedMode == option.mode
                    PetalGroupListRow(
                        position = position,
                        selected = selected,
                        onClick = { selectedMode = option.mode },
                        leading = {
                            PetalGroupIconBadge(
                                option.icon,
                                container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        },
                        content = {
                            Text(option.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                option.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailing = { RadioButton(selected = selected, onClick = null) }
                    )
                }
            }

            // ── Custom question ──
            Column {
                PetalSectionLabel("Ask your own question")
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        TextField(
                            value = customPromptText,
                            onValueChange = {
                                customPromptText = it
                                if (it.isNotBlank()) selectedMode = ResearchMode.CUSTOM
                            },
                            placeholder = {
                                Text(
                                    stringResource(R.string.ui_ask_custom_question_about_this),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                runResearch(if (customPromptText.isNotBlank()) ResearchMode.CUSTOM else selectedMode, customPromptText)
                            }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        AnimatedVisibility(
                            visible = customPromptText.isNotBlank(),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            IconButton(onClick = { customPromptText = "" }) {
                                Icon(
                                    Icons.Rounded.Clear,
                                    contentDescription = stringResource(R.string.ui_clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Primary action ──
            var modeSplitMenuExpanded by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSplitButton(
                    label = if (isLoading) "Analyzing..." else (if (customPromptText.isNotBlank()) "Ask ${selectedProvider.displayName}" else "Analyze Page"),
                    onPrimaryClick = {
                        if (!isLoading) {
                            runResearch(if (customPromptText.isNotBlank()) ResearchMode.CUSTOM else selectedMode, customPromptText)
                        }
                    },
                    onMenuClick = {
                        if (!isLoading) {
                            modeSplitMenuExpanded = !modeSplitMenuExpanded
                        }
                    },
                    icon = Icons.Rounded.AutoAwesome,
                    isMenuExpanded = modeSplitMenuExpanded,
                    variant = SplitButtonVariant.FILLED,
                    height = 54.dp,
                    modifier = Modifier.fillMaxWidth()
                )

                com.petal.browser.ui.containment.PetalPopupMenu(
                    expanded = modeSplitMenuExpanded,
                    onDismissRequest = { modeSplitMenuExpanded = false }
                ) {
                    ResearchMode.values().forEach { mode ->
                        com.petal.browser.ui.containment.PetalPopupMenuItem(
                            text = { Text(mode.title.ifBlank { "Custom Query" }) },
                            onClick = {
                                modeSplitMenuExpanded = false
                                selectedMode = mode
                                runResearch(mode, customPromptText)
                            },
                            leadingIcon = {
                                Icon(
                                    when (mode) {
                                        ResearchMode.SUMMARY -> Icons.Rounded.Summarize
                                        ResearchMode.DEEP_RESEARCH -> Icons.Rounded.Psychology
                                        ResearchMode.KEY_QA -> Icons.Rounded.FormatListBulleted
                                        ResearchMode.CRITIQUE -> Icons.Rounded.Verified
                                        ResearchMode.CUSTOM -> Icons.Rounded.Edit
                                    },
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }

            // ── Loading ──
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(220))
            ) {
                PetalHeroCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PetalGroupIconBadge(
                            shape = PetalMaterialShapes.Burst.toShape(),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            size = 52.dp,
                            iconSize = 26.dp
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(R.string.ui_researching_synthesizing),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                selectedProvider.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Error ──
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                errorMessage?.let { err ->
                    PetalHeroCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PetalGroupIconBadge(
                                shape = PetalMaterialShapes.SoftBoom.toShape(),
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                                size = 40.dp,
                                iconSize = 20.dp
                            ) {
                                Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                            }
                            Text(
                                err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { errorMessage = null }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.ui_dismiss),
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Result ──
            AnimatedVisibility(
                visible = responseResult != null,
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(400)),
                exit = fadeOut() + shrinkVertically()
            ) {
                responseResult?.let { response ->
                    PetalHeroCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PetalGroupIconBadge(
                                        shape = PetalMaterialShapes.Sunny.toShape(),
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        size = 36.dp,
                                        iconSize = 18.dp
                                    ) {
                                        Icon(Icons.Rounded.SmartToy, contentDescription = null)
                                    }
                                    Text(
                                        stringResource(R.string.ui_ai_insights),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("AI Research", response))
                                            com.petal.browser.view.PetalToast.show(context, "Research copied to clipboard")
                                        },
                                        modifier = Modifier.size(38.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.ui_copy), modifier = Modifier.size(18.dp))
                                    }

                                    FilledTonalIconButton(
                                        onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "AI Research: $pageTitle")
                                                putExtra(Intent.EXTRA_TEXT, "$pageTitle\n$pageUrl\n\nAI Insights:\n$response")
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Research"))
                                        },
                                        modifier = Modifier.size(38.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                    ) {
                                        Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.ui_share), modifier = Modifier.size(18.dp))
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

private data class AiModeOption(
    val mode: ResearchMode,
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

private enum class AiKeyRow { ENDPOINT, KEY, GET_KEY, MANAGE }

@Composable
private fun aiFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
)

/** Grouped-container host for form fields so they line up with the other containment rows. */
@Composable
private fun AiFieldRow(
    position: PetalGroupPosition,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = petalGroupShape(position),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            content = content
        )
    }
}
