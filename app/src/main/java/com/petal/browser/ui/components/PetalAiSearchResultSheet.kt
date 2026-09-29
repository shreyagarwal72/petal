package com.petal.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.petal.browser.ui.theme.PetalMaterialShapes
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.PetalSettingsSection

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
        val trimmed = targetQuery.trim()
        if (trimmed.isBlank()) {
            isLoading = false
            return
        }
        activeQuery = trimmed
        isLoading = true
        errorMessage = null
        responseResult = null
        PetalAiSearchManager.executeAiSearch(context, trimmed) { result ->
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

    var groundingOn by remember { mutableStateOf(PetalAiSearchManager.isGroundingEnabled(context)) }

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

    Surface(
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 28.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .width(42.dp)
                        .height(4.5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
            }

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
                            text = "${selectedProvider.displayName} • ${selectedModel.substringAfterLast("/")}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.ui_close),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── Search pill ──
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
                        Icons.Rounded.Search,
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
                                stringResource(R.string.ui_ask_anything_or_search_with),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    executeSearch(searchQuery)
                                }
                            }
                        ),
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
                        visible = searchQuery.isNotBlank(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.ui_clear_query),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    FilledIconButton(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                executeSearch(searchQuery)
                            }
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ArrowForward,
                            contentDescription = stringResource(R.string.ui_submit_query),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ── Search settings ──
            PetalSettingsSection(title = "Search settings", icon = Icons.Rounded.Tune) {
                PetalGroup(rowCount = 3) { index, position ->
                    when (index) {
                        0 -> PetalGroupListRow(
                            position = position,
                            onClick = { providerMenuExpanded = true },
                            leading = { PetalGroupIconBadge(Icons.Rounded.Psychology) },
                            content = {
                                Text("AI provider", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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
                                                    Text(
                                                        provider.displayName,
                                                        fontWeight = if (provider == selectedProvider) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        if (provider == selectedProvider) Icons.Rounded.CheckCircle else Icons.Rounded.SmartToy,
                                                        contentDescription = null,
                                                        tint = if (provider == selectedProvider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                },
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
                            }
                        )

                        1 -> com.petal.browser.ui.containment.PetalSettingsToggleRow(
                            title = "Web grounding",
                            subtitle = "Back answers with live web results",
                            icon = Icons.Rounded.Public,
                            checked = groundingOn,
                            onCheckedChange = {
                                groundingOn = it
                                PetalAiSearchManager.setGroundingEnabled(context, it)
                            },
                            position = position
                        )

                        else -> com.petal.browser.ui.containment.PetalGroupNavigationRow(
                            title = stringResource(R.string.ui_ai_settings),
                            subtitle = "API keys, models and providers",
                            position = position,
                            onClick = { openAiHub() },
                            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) }
                        )
                    }
                }
            }

            // ── State container: loading / error / result / suggestions ──
            AnimatedContent(
                targetState = Triple(isLoading, errorMessage, responseResult),
                transitionSpec = {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                },
                label = "AiContentState"
            ) { (loading, error, result) ->
                when {
                    loading -> {
                        PetalHeroCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                PetalGroupIconBadge(
                                    shape = PetalMaterialShapes.Burst.toShape(),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    size = 56.dp,
                                    iconSize = 28.dp
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.dp
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.ui_researching_synthesizing),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.ui_querying_with_web_grounding, selectedProvider.displayName),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    Text(
                                        text = "\"$activeQuery\"",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    error != null -> {
                        PetalHeroCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    PetalGroupIconBadge(
                                        shape = PetalMaterialShapes.SoftBoom.toShape(),
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                        size = 44.dp,
                                        iconSize = 22.dp
                                    ) {
                                        Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.ui_search_query_failed),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = error,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = { executeSearch(activeQuery) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.ui_retry))
                                    }

                                    FilledTonalButton(
                                        onClick = { openAiHub() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.ui_configure_key))
                                    }
                                }
                            }
                        }
                    }

                    result != null -> {
                        PetalHeroCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                        PetalGroupIconBadge(
                                            shape = PetalMaterialShapes.Sunny.toShape(),
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            size = 36.dp,
                                            iconSize = 18.dp
                                        ) {
                                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                        }
                                        Text(
                                            text = stringResource(R.string.ui_synthesized_answer),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Text(
                                            text = selectedProvider.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))

                                PetalMarkdownText(markdown = result)

                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                Spacer(Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalIconButton(
                                        onClick = { executeSearch(activeQuery) },
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                    ) {
                                        Icon(
                                            Icons.Rounded.Refresh,
                                            contentDescription = stringResource(R.string.ui_regenerate),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    FilledTonalIconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("AI Web Search Answer", result)
                                            clipboard.setPrimaryClip(clip)
                                            com.petal.browser.view.PetalToast.show(context, "Answer copied to clipboard")
                                        },
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                    ) {
                                        Icon(
                                            Icons.Rounded.ContentCopy,
                                            contentDescription = stringResource(R.string.ui_copy_answer),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    FilledTonalIconButton(
                                        onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "Query: $activeQuery\n\nAI Web Search Result:\n$result")
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share AI Search Answer"))
                                        },
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                    ) {
                                        Icon(
                                            Icons.Rounded.Share,
                                            contentDescription = stringResource(R.string.ui_share_answer),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        val samplePrompts = listOf(
                            "What are the latest tech news headlines today?" to PetalMaterialShapes.Sunny,
                            "Explain quantum computing in simple terms" to PetalMaterialShapes.Cookie4Sided,
                            "Summarize current global weather trends" to PetalMaterialShapes.SoftBoom,
                            "How to optimize Android app performance?" to PetalMaterialShapes.Burst
                        )

                        PetalSettingsSection(
                            title = stringResource(R.string.ui_suggested_questions),
                            icon = Icons.Rounded.Lightbulb
                        ) {
                            PetalGroup(rowCount = samplePrompts.size) { index, position ->
                                val (prompt, shapeType) = samplePrompts[index]
                                PetalGroupListRow(
                                    position = position,
                                    onClick = {
                                        searchQuery = prompt
                                        executeSearch(prompt)
                                    },
                                    leading = {
                                        PetalGroupIconBadge(
                                            shape = shapeType.toShape(),
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            size = 44.dp,
                                            iconSize = 22.dp
                                        ) {
                                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                        }
                                    },
                                    content = {
                                        Text(
                                            text = prompt,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailing = {
                                        Icon(
                                            Icons.Rounded.ArrowOutward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
