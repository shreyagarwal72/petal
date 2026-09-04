package com.petal.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.compose.ai.AiProvider
import com.petal.browser.compose.ai.PetalAiResearchEngine
import com.petal.browser.compose.ai.PetalAiSearchManager
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalAiSearchBridge {
    @JvmStatic
    fun showAiSearchResult(
        activity: ComponentActivity,
        query: String
    ) {
        activity.runOnUiThread {
            val dialog = BottomSheetDialog(activity)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                    val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                    val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                    val isAmoled = sp.getBoolean("sp_amoled", false)

                    val appFont = AppFont.fromName(fontName)
                    val colorStyle = try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }

                    PetalExpressiveTheme(
                        dynamicColor = dynamicColor,
                        useAmoled = isAmoled,
                        appFont = appFont,
                        colorStyle = colorStyle,
                        paletteId = paletteId
                    ) {
                        PetalAiSearchResultSheet(
                            query = query,
                            onDismiss = { dialog.dismiss() }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalAiSearchResultSheet(
    query: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var searchQuery by remember { mutableStateOf(query) }
    var activeQuery by remember { mutableStateOf(query) }

    var selectedProvider by remember { mutableStateOf(PetalAiResearchEngine.getSelectedProvider(context)) }
    var selectedModel by remember { mutableStateOf(PetalAiResearchEngine.getSelectedModel(context, selectedProvider)) }
    var providerMenuExpanded by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(query.isNotBlank()) }
    var responseResult by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun executeSearch(targetQuery: String) {
        if (targetQuery.isBlank()) {
            isLoading = false
            return
        }
        activeQuery = targetQuery
        isLoading = true
        errorMessage = null
        responseResult = null
        PetalAiSearchManager.executeAiSearch(context, targetQuery) { result ->
            isLoading = false
            result.onSuccess { text ->
                responseResult = text
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "AI Web Search request failed."
            }
        }
    }

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            executeSearch(query)
        } else {
            isLoading = false
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 28.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Header with Petal AI Web Search Badge
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
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Petal AI",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${selectedProvider.displayName} • $selectedModel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // 🔍 Interactive AI Web Search Box UI Component
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(Modifier.width(10.dp))

                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search with Petal AI...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    executeSearch(searchQuery.trim())
                                }
                            }
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                executeSearch(searchQuery.trim())
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Icon(
                            Icons.Rounded.ArrowForward,
                            contentDescription = "Search AI",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // AI Provider & Grounding Info Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    AssistChip(
                        onClick = { providerMenuExpanded = true },
                        label = { Text(selectedProvider.displayName, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Rounded.Psychology, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    DropdownMenu(
                        expanded = providerMenuExpanded,
                        onDismissRequest = { providerMenuExpanded = false }
                    ) {
                        AiProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.displayName) },
                                onClick = {
                                    selectedProvider = provider
                                    PetalAiResearchEngine.setSelectedProvider(context, provider)
                                    selectedModel = PetalAiResearchEngine.getSelectedModel(context, provider)
                                    providerMenuExpanded = false
                                    if (activeQuery.isNotBlank()) {
                                        executeSearch(activeQuery)
                                    }
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isGrounding = PetalAiSearchManager.isGroundingEnabled(context)
                    if (isGrounding) {
                        SuggestionChip(
                            onClick = { },
                            label = { Text("Web Facts", style = MaterialTheme.typography.labelSmall) },
                            icon = { Icon(Icons.Rounded.FactCheck, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary) }
                        )
                    }

                    IconButton(
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
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "AI Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Loading / Result / Error content section
            if (isLoading) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Searching web & generating answer with ${selectedProvider.displayName}...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Query: \"$activeQuery\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            } else if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Petal AI Request Failed",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = errorMessage ?: "Unknown error occurred.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { executeSearch(activeQuery) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry")
                            }
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
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add API Key")
                            }
                        }
                    }
                }
            } else if (responseResult != null) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        PetalMarkdownText(markdown = responseResult ?: "")

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { executeSearch(activeQuery) }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("AI Web Search Answer", responseResult)
                                clipboard.setPrimaryClip(clip)
                                com.petal.browser.view.NinjaToast.show(context, "Answer copied to clipboard")
                            }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Query: $activeQuery\n\nAI Web Search Result:\n${responseResult}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share AI Search Answer"))
                            }) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Suggested Questions",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        val samplePrompts = listOf(
                            "What are the latest tech news headlines today?",
                            "Explain quantum computing in simple terms",
                            "Summarize current global weather trends",
                            "How to optimize Android performance?"
                        )
                        samplePrompts.forEach { prompt ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = prompt
                                        executeSearch(prompt)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = prompt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
