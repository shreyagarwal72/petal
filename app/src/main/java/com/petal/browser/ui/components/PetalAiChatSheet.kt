package com.petal.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.R
import com.petal.browser.account.PetalUserProfileManager
import com.petal.browser.browser.viewmodel.PetalBrowserViewModel
import com.petal.browser.compose.ai.AiMessagePart
import com.petal.browser.compose.ai.AiRole
import com.petal.browser.compose.ai.PageContextData
import com.petal.browser.compose.ai.PetalAiPageContextManager
import com.petal.browser.compose.ai.PetalAiProviderFallback
import com.petal.browser.compose.ai.PetalAiResearchEngine
import com.petal.browser.compose.ai.PetalAiUsageTracker
import com.petal.browser.compose.ai.PetalChatMessage
import com.petal.browser.compose.ai.PetalYouTubeTranscriptExtractor
import com.petal.browser.compose.ai.history.PetalAiChatHistoryStore
import com.petal.browser.compose.ai.history.SavedChatSession
import com.petal.browser.compose.ai.ondevice.PetalOnDeviceLlmEngine
import com.petal.browser.compose.reader.PetalTtsReaderEngine
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalMaterialAlertDialog
import com.petal.browser.view.PetalToast
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch

/**
 * Non-modal, draggable bottom panel for Petal AI chat.
 *
 * Characteristics:
 * - No dark scrim; webpage above remains visible and touchable.
 * - Default height is ~65% of screen height, expandable to full screen.
 * - Rising with software keyboard using [imePadding].
 * - Closed via header close X, by dragging down, or system back.
 * - Chrome Gemini panel layout: header, empty state greeting, suggestion chips,
 *   streaming Markdown replies, and pill input with send/stop.
 * - Multi-tab context attachment, quoted text selection chip, image attachment (vision),
 *   YouTube transcript extraction, voice input mic, TTS read-aloud, and Think Harder toggle.
 * - Reachable "Page research" sheet and persistent local chat history.
 */
@Composable
fun PetalAiChatSheet(
    viewModel: PetalBrowserViewModel,
    activeTabId: String,
    activeTitle: String = "",
    activeUrl: String = "",
    isPrivate: Boolean = false,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var showPageResearchSheet by remember { mutableStateOf(false) }

    val targetHeight = if (isExpanded) screenHeight * 0.94f else screenHeight * 0.65f
    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = PetalSpring.calm(),
        label = "sheet_height"
    )

    val conversations by viewModel.aiConversationManager.conversations.collectAsStateWithLifecycle()
    val tabMessages = conversations[activeTabId].orEmpty()

    val streamingTabs by viewModel.aiConversationManager.streamingTabs.collectAsStateWithLifecycle()
    val isStreaming = streamingTabs.contains(activeTabId)

    val tabErrors by viewModel.aiConversationManager.tabErrors.collectAsStateWithLifecycle()
    val activeError = tabErrors[activeTabId]

    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // ── Context & Attachment States ──
    var pageContext by remember { mutableStateOf<PageContextData?>(null) }
    var isPageChipAttached by rememberSaveable { mutableStateOf(true) }
    var previousUrl by rememberSaveable { mutableStateOf(activeUrl) }
    var pageNavigatedNote by remember { mutableStateOf(false) }

    val attachedQuote by viewModel.aiSelectedQuote.collectAsStateWithLifecycle()
    var attachedTabs by remember { mutableStateOf<List<PageContextData>>(emptyList()) }
    var showTabPickerDialog by remember { mutableStateOf(false) }

    var attachedImageBase64 by remember { mutableStateOf<String?>(null) }
    var attachedImageMime by remember { mutableStateOf("image/jpeg") }
    var attachedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var videoTranscript by remember { mutableStateOf<String?>(null) }
    var isExtractingTranscript by remember { mutableStateOf(false) }

    var thinkHarder by rememberSaveable { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showChatHistoryDialog by remember { mutableStateOf(false) }
    var showDailyLimitWarning by remember { mutableStateOf(false) }

    // TTS playback state
    var ttsPlayingMessageId by remember { mutableStateOf<String?>(null) }
    val ttsEngine = remember {
        PetalTtsReaderEngine(
            context = context,
            onPlaybackStateChanged = { speaking ->
                if (!speaking) ttsPlayingMessageId = null
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsEngine.shutdown()
            PetalOnDeviceLlmEngine.unload()
        }
    }

    var showCloudConsentDialog by remember { mutableStateOf(false) }
    var pendingSendAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val original = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (original != null) {
                    val maxDim = 1024
                    val width = original.width
                    val height = original.height
                    val scaled = if (width > maxDim || height > maxDim) {
                        val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
                        Bitmap.createScaledBitmap(original, (width * ratio).toInt(), (height * ratio).toInt(), true)
                    } else {
                        original
                    }
                    attachedImageBitmap = scaled
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                    val bytes = baos.toByteArray()
                    attachedImageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    attachedImageMime = "image/jpeg"
                }
            } catch (_: Exception) {
                PetalToast.show(context, "Failed to load image")
            }
        }
    }

    // Speech-to-Text Recognizer Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
            }
        }
    }

    fun triggerExtraction() {
        if (isPrivate || !PetalAiPageContextManager.isEligibleUrl(activeUrl)) {
            pageContext = null
            return
        }
        if (!PetalAiResearchEngine.isAutoPageContextEnabled(context)) {
            return
        }
        val act = context as? com.petal.browser.activity.MainActivity ?: return
        PetalAiPageContextManager.extractPageContext(act, activeTabId, activeUrl, activeTitle, isPrivate) { result ->
            if (result != null) {
                pageContext = result
                if (!result.hasPasswordInput) {
                    isPageChipAttached = true
                }
            }
        }
    }

    // Extract page context on sheet open or URL change
    LaunchedEffect(activeTabId, activeUrl, isPrivate) {
        if (activeUrl != previousUrl) {
            if (pageContext != null && isPageChipAttached) {
                pageNavigatedNote = true
            }
            previousUrl = activeUrl
        }
        triggerExtraction()
    }

    fun getSystemPrompt(): String? {
        val hasPageChip = isPageChipAttached && pageContext != null && !pageContext!!.hasPasswordInput && !isPrivate
        val hasQuote = !attachedQuote.isNullOrBlank()
        val hasTabs = attachedTabs.isNotEmpty()
        val hasTranscript = !videoTranscript.isNullOrBlank()

        if (!hasPageChip && !hasQuote && !hasTabs && !hasTranscript) {
            return null
        }

        val isLocal = PetalAiResearchEngine.isLocalEndpoint(context)
        return PetalAiPageContextManager.formatSystemPrompt(
            pageContext = if (hasPageChip) pageContext else null,
            isLocalModel = isLocal,
            quoteText = if (hasQuote) attachedQuote else null,
            additionalTabs = attachedTabs,
            videoTranscript = videoTranscript,
            activeTabIsPrivate = isPrivate
        )
    }

    fun executeWithConsentCheck(action: () -> Unit) {
        val isLocal = PetalAiResearchEngine.isLocalEndpoint(context)
        val consentAccepted = PetalAiResearchEngine.isCloudConsentAccepted(context)
        val dailyExceeded = PetalAiUsageTracker.isDailyLimitExceeded(context)

        val runAction = {
            if (dailyExceeded) {
                pendingSendAction = action
                showDailyLimitWarning = true
            } else {
                action()
            }
        }

        if (isLocal || consentAccepted) {
            runAction()
        } else {
            pendingSendAction = runAction
            showCloudConsentDialog = true
        }
    }

    fun doSendMessage(query: String) {
        val imagePart = if (!attachedImageBase64.isNullOrBlank()) {
            AiMessagePart.Image(
                mimeType = attachedImageMime,
                base64Data = attachedImageBase64
            )
        } else null

        val sysPrompt = getSystemPrompt()
        executeWithConsentCheck {
            viewModel.sendAiMessage(
                tabId = activeTabId,
                query = query,
                systemPrompt = sysPrompt,
                imagePart = imagePart,
                thinkHarder = thinkHarder,
                isPrivate = isPrivate,
                pageTitle = activeTitle,
                pageUrl = activeUrl
            )
            attachedImageBase64 = null
            attachedImageBitmap = null
        }
    }

    // Auto-scroll when assistant produces new content
    LaunchedEffect(tabMessages.size, tabMessages.lastOrNull()?.text) {
        if (tabMessages.isNotEmpty()) {
            listState.animateScrollToItem(tabMessages.size - 1)
        }
    }

    fun openAiSettings() {
        val browserActivity = context as? com.petal.browser.activity.MainActivity
        if (browserActivity != null) {
            browserActivity.openApiIntegrationsHub()
        } else {
            val intent = Intent(context, com.petal.browser.activity.SettingsActivity::class.java).apply {
                putExtra(
                    com.petal.browser.activity.SettingsActivity.EXTRA_SETTINGS_CATEGORY,
                    SettingsCategory.API_INTEGRATIONS.name
                )
            }
            context.startActivity(intent)
        }
    }

    fun handleDismiss() {
        viewModel.stopAiStreaming(activeTabId)
        PetalOnDeviceLlmEngine.unload()
        ttsEngine.stop()
        onDismiss()
    }

    val selectedProvider = PetalAiResearchEngine.getSelectedProvider(context)
    val activeModel = if (thinkHarder) {
        PetalAiResearchEngine.getStrongerModel(selectedProvider)
    } else {
        PetalAiResearchEngine.getSelectedModel(context, selectedProvider)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Non-modal: NO dark scrim background. Webpage above is touchable.
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 3.dp,
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedHeight)
                .imePadding()
                .pointerInput(Unit) {
                    detectTapGestures { /* consume touches inside panel */ }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 8.dp)
            ) {
                // ── Drag Handle & Header Row ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            var totalDrag = 0f
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDrag += dragAmount.y
                                },
                                onDragEnd = {
                                    if (totalDrag > 80f) {
                                        if (isExpanded) {
                                            isExpanded = false
                                        } else {
                                            handleDismiss()
                                        }
                                    } else if (totalDrag < -60f) {
                                        isExpanded = true
                                    }
                                    totalDrag = 0f
                                }
                            )
                        }
                ) {
                    // Drag pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                                .clickable { isExpanded = !isExpanded }
                        )
                    }

                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Sparkle icon & Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PetalGroupIconBadge(
                                shape = RoundedCornerShape(10.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                size = 34.dp,
                                iconSize = 20.dp
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.ui_petal_ai),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val subtitle = if (thinkHarder) {
                                    "$activeModel • ${stringResource(R.string.ui_petal_ai_think_harder)}"
                                } else {
                                    activeModel
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (thinkHarder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Right: Think Harder, New Chat, Menu, Close X
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Think Harder Toggle Button
                            IconButton(
                                onClick = { thinkHarder = !thinkHarder },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Psychology,
                                    contentDescription = stringResource(R.string.ui_petal_ai_think_harder),
                                    tint = if (thinkHarder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // New Chat Button
                            IconButton(
                                onClick = { viewModel.clearAiConversation(activeTabId) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.ui_petal_ai_new_chat),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Overflow Menu
                            Box {
                                var menuExpanded by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.MoreVert,
                                        contentDescription = "Menu",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ui_petal_ai_page_research)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            showPageResearchSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ui_petal_ai_translate_page)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Translate,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            showTranslateDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ui_petal_ai_chat_history)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.History,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            showChatHistoryDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ui_petal_ai_clear_chat)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Refresh,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.clearAiConversation(activeTabId)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ui_petal_ai_ai_settings)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Settings,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            openAiSettings()
                                        }
                                    )
                                }
                            }

                            // Close X Button
                            IconButton(
                                onClick = { handleDismiss() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.about_close),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // ── Body: Empty State or Messages ──
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    if (tabMessages.isEmpty()) {
                        // Empty State (Chrome Gemini style)
                        val firstName = remember {
                            val name = PetalUserProfileManager.currentProfile.displayName.trim()
                            if (name.isNotBlank() && !name.equals("Petal Explorer", ignoreCase = true)) {
                                name.split(" ").firstOrNull()?.takeIf { it.isNotBlank() }
                            } else null
                        }

                        val greeting = if (firstName != null) {
                            stringResource(R.string.ui_petal_ai_greeting_name, firstName)
                        } else {
                            stringResource(R.string.ui_petal_ai_greeting)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 20.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.ui_petal_ai_help_prompt),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(28.dp))

                            // Three Tappable Suggestion Chips
                            val hasPageChip = isPageChipAttached && pageContext != null && !pageContext!!.hasPasswordInput && !isPrivate
                            val chip1Text = if (hasPageChip) stringResource(R.string.ui_petal_ai_chip_summarize) else stringResource(R.string.ui_petal_ai_chip_brainstorm)
                            val chip2Text = if (hasPageChip) stringResource(R.string.ui_petal_ai_chip_list_key_points) else stringResource(R.string.ui_petal_ai_chip_help_write)
                            val chip3Text = if (hasPageChip) stringResource(R.string.ui_petal_ai_chip_explain_simply) else stringResource(R.string.ui_petal_ai_chip_explain_concept)

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SuggestionPromptRow(
                                    icon = if (hasPageChip) Icons.Rounded.Summarize else Icons.Rounded.Lightbulb,
                                    label = chip1Text
                                ) {
                                    val prompt = if (hasPageChip) "Summarize this page concisely." else "Brainstorm creative ideas for a new project."
                                    doSendMessage(prompt)
                                }

                                SuggestionPromptRow(
                                    icon = if (hasPageChip) Icons.Rounded.FactCheck else Icons.Rounded.Edit,
                                    label = chip2Text
                                ) {
                                    val prompt = if (hasPageChip) "List the key points and conclusions." else "Help me write a concise and polite message."
                                    doSendMessage(prompt)
                                }

                                SuggestionPromptRow(
                                    icon = if (hasPageChip) Icons.Rounded.HelpOutline else Icons.Rounded.Psychology,
                                    label = chip3Text
                                ) {
                                    val prompt = if (hasPageChip) "Explain this simply, as if to a beginner." else "Explain a complex concept in everyday terms."
                                    doSendMessage(prompt)
                                }
                            }
                        }
                    } else {
                        // Messages List
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            items(tabMessages, key = { it.id }) { message ->
                                when (message.role) {
                                    AiRole.USER -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.widthIn(max = 280.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                                    if (message.imagePart != null && message.imagePart.base64Data != null) {
                                                        val bitmap = remember(message.imagePart.base64Data) {
                                                            try {
                                                                val bytes = Base64.decode(message.imagePart.base64Data, Base64.DEFAULT)
                                                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                                            } catch (_: Exception) {
                                                                null
                                                            }
                                                        }
                                                        if (bitmap != null) {
                                                            Image(
                                                                bitmap = bitmap.asImageBitmap(),
                                                                contentDescription = "User uploaded image",
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .heightIn(max = 160.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .padding(bottom = 6.dp)
                                                            )
                                                        }
                                                    }
                                                    if (message.text.isNotBlank()) {
                                                        Text(
                                                            text = message.text,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    AiRole.ASSISTANT -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (message.text.isNotBlank()) {
                                                PetalMarkdownText(
                                                    markdown = message.text,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            } else if (message.isStreaming) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(vertical = 8.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = "Petal AI is generating...",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontStyle = FontStyle.Italic,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            // Action row under each assistant reply
                                            if (!message.isStreaming && message.text.isNotBlank()) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 6.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Copy Plain Text
                                                    IconButton(
                                                        onClick = {
                                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            cm.setPrimaryClip(ClipData.newPlainText("Petal AI", message.text))
                                                            PetalToast.show(context, context.getString(R.string.ui_petal_ai_copied))
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.ContentCopy,
                                                            contentDescription = stringResource(R.string.ui_copy),
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    // Copy as Markdown
                                                    IconButton(
                                                        onClick = {
                                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            cm.setPrimaryClip(ClipData.newPlainText("Petal AI Markdown", message.text))
                                                            PetalToast.show(context, context.getString(R.string.ui_petal_ai_copied))
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.Description,
                                                            contentDescription = stringResource(R.string.ui_petal_ai_copy_markdown),
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    // Read Aloud TTS Play/Stop
                                                    val isThisPlaying = ttsPlayingMessageId == message.id && ttsEngine.isSpeaking
                                                    IconButton(
                                                        onClick = {
                                                            if (isThisPlaying) {
                                                                ttsEngine.stop()
                                                                ttsPlayingMessageId = null
                                                            } else {
                                                                ttsEngine.loadText(message.text)
                                                                ttsEngine.play()
                                                                ttsPlayingMessageId = message.id
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            if (isThisPlaying) Icons.Rounded.Stop else Icons.Rounded.VolumeUp,
                                                            contentDescription = stringResource(R.string.ui_petal_ai_read_aloud),
                                                            modifier = Modifier.size(16.dp),
                                                            tint = if (isThisPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    // Save to Collection
                                                    IconButton(
                                                        onClick = {
                                                            if (isPrivate) {
                                                                PetalToast.show(context, context.getString(R.string.ui_petal_ai_incognito_no_history))
                                                            } else {
                                                                val title = pageContext?.title?.ifBlank { activeTitle } ?: activeTitle.ifBlank { "AI Conversation" }
                                                                val saved = PetalAiChatHistoryStore.saveSession(
                                                                    context = context,
                                                                    title = title,
                                                                    pageUrl = activeUrl,
                                                                    messages = tabMessages,
                                                                    isPrivate = isPrivate
                                                                )
                                                                if (saved) {
                                                                    PetalToast.show(context, context.getString(R.string.ui_petal_ai_saved_to_collection))
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.BookmarkAdd,
                                                            contentDescription = stringResource(R.string.ui_petal_ai_save_to_collection),
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    // Regenerate
                                                    IconButton(
                                                        onClick = {
                                                            executeWithConsentCheck {
                                                                viewModel.regenerateLastAiMessage(
                                                                    tabId = activeTabId,
                                                                    systemPrompt = getSystemPrompt(),
                                                                    thinkHarder = thinkHarder
                                                                )
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.Refresh,
                                                            contentDescription = stringResource(R.string.ui_regenerate),
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    // Share
                                                    IconButton(
                                                        onClick = {
                                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                type = "text/plain"
                                                                putExtra(Intent.EXTRA_TEXT, message.text)
                                                            }
                                                            context.startActivity(Intent.createChooser(shareIntent, "Share AI response"))
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.Share,
                                                            contentDescription = "Share",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    AiRole.SYSTEM -> { /* Internal prompt, not displayed */ }
                                }
                            }

                            // Error card if current tab encountered an error
                            if (activeError != null) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.ErrorOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = activeError.localizedMessage
                                                        ?: activeError.message
                                                        ?: stringResource(R.string.ui_petal_ai_no_key_warning),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                TextButton(
                                                    onClick = { openAiSettings() },
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.ui_petal_ai_open_settings),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.error
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

                // ── Privacy / Password Notices ──
                if (isPrivate) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.ui_petal_ai_private_tab_notice),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (pageContext?.hasPasswordInput == true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.ui_petal_ai_password_page_notice),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // ── Pill-Shaped Input Container with Chips ──
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        // ── Chips Row: Page Context, Quoted Selection, Extra Tabs, Images, Transcripts ──
                        val hasAnyChip = (isPageChipAttached && pageContext != null && !pageContext!!.hasPasswordInput && !isPrivate) ||
                                !attachedQuote.isNullOrBlank() ||
                                attachedTabs.isNotEmpty() ||
                                attachedImageBitmap != null ||
                                !videoTranscript.isNullOrBlank()

                        if (hasAnyChip) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Page Context Chip
                                if (isPageChipAttached && pageContext != null && !pageContext!!.hasPasswordInput && !isPrivate) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f))
                                            .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Language,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = pageContext?.title?.ifBlank { activeTitle.ifBlank { activeUrl } } ?: activeUrl,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (pageNavigatedNote) {
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "(${stringResource(R.string.ui_petal_ai_page_changed_note)})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { isPageChipAttached = false },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.ui_petal_ai_remove_page_chip),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }

                                // 2. Quoted Text Selection Chip
                                if (!attachedQuote.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f))
                                            .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.FormatQuote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = attachedQuote ?: "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { viewModel.clearSelectedQuote() },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.ui_petal_ai_quote_chip_remove),
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }

                                // 3. Additional Attached Open Tabs Chips
                                for (tab in attachedTabs) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Tab,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = tab.title.ifBlank { tab.url },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                attachedTabs = attachedTabs.filterNot { it.tabId == tab.tabId }
                                            },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Remove tab chip",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }

                                // 4. Image Attachment Thumbnail Chip
                                if (attachedImageBitmap != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                            .padding(start = 6.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
                                    ) {
                                        Image(
                                            bitmap = attachedImageBitmap!!.asImageBitmap(),
                                            contentDescription = "Attached thumbnail",
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.ui_petal_ai_attach_image),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                attachedImageBitmap = null
                                                attachedImageBase64 = null
                                            },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Remove image",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }

                                // 5. YouTube Transcript Chip
                                if (!videoTranscript.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Subtitles,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.ui_petal_ai_transcript_chip),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { videoTranscript = null },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Remove transcript",
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Vision unsupported warning notice
                            if (attachedImageBitmap != null && !PetalAiResearchEngine.isVisionSupported(activeModel)) {
                                Text(
                                    text = stringResource(R.string.ui_petal_ai_vision_unsupported_model),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // ── Input Row: [+] Attach Menu, TextField, Mic, Send/Stop ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Attach Action Menu Button
                            Box {
                                IconButton(
                                    onClick = { showAttachMenu = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.AddCircleOutline,
                                        contentDescription = stringResource(R.string.ui_petal_ai_attach_menu),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                DropdownMenu(
                                    expanded = showAttachMenu,
                                    onDismissRequest = { showAttachMenu = false }
                                ) {
                                    // Attach Open Tab
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ui_petal_ai_attach_open_tab)) },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Tab, contentDescription = null)
                                        },
                                        onClick = {
                                            showAttachMenu = false
                                            showTabPickerDialog = true
                                        }
                                    )

                                    // Attach Image
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ui_petal_ai_attach_image)) },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Image, contentDescription = null)
                                        },
                                        onClick = {
                                            showAttachMenu = false
                                            imagePickerLauncher.launch("image/*")
                                        }
                                    )

                                    // YouTube Video Transcript (if on YouTube)
                                    if (PetalYouTubeTranscriptExtractor.isYouTubeUrl(activeUrl)) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.ui_petal_ai_attach_transcript)) },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Subtitles, contentDescription = null)
                                            },
                                            onClick = {
                                                showAttachMenu = false
                                                coroutineScope.launch {
                                                    isExtractingTranscript = true
                                                    val transcript = PetalYouTubeTranscriptExtractor.extractTranscriptFromUrl(activeUrl)
                                                    isExtractingTranscript = false
                                                    if (!transcript.isNullOrBlank()) {
                                                        videoTranscript = transcript
                                                    } else {
                                                        PetalToast.show(context, context.getString(R.string.ui_petal_ai_transcript_unavailable))
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Text Input Field
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 10.dp)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            triggerExtraction()
                                        }
                                    },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (inputText.isNotBlank() && !isStreaming) {
                                            val query = inputText
                                            inputText = ""
                                            doSendMessage(query)
                                        }
                                    }
                                ),
                                decorationBox = { innerTextField ->
                                    if (inputText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.ui_petal_ai_ask_hint),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            // Mic Speech-to-Text Button (shown when text is empty and not streaming)
                            if (inputText.isEmpty() && !isStreaming) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.ui_petal_ai_speech_mic))
                                        }
                                        try {
                                            speechLauncher.launch(intent)
                                        } catch (_: Exception) {
                                            PetalToast.show(context, "Voice input not available")
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Mic,
                                        contentDescription = stringResource(R.string.ui_petal_ai_speech_mic),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Send / Stop button
                            if (isStreaming) {
                                FilledIconButton(
                                    onClick = { viewModel.stopAiStreaming(activeTabId) },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Stop,
                                        contentDescription = stringResource(R.string.ui_petal_ai_stop),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                val canSend = inputText.isNotBlank() || attachedImageBase64 != null
                                FilledIconButton(
                                    onClick = {
                                        if (canSend) {
                                            val query = inputText.ifBlank { "Describe this image." }
                                            inputText = ""
                                            doSendMessage(query)
                                        }
                                    },
                                    enabled = canSend,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.ArrowUpward,
                                        contentDescription = stringResource(R.string.ui_petal_ai_send),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── First-Run Cloud Consent Dialog ──
        if (showCloudConsentDialog) {
            PetalMaterialAlertDialog(
                onDismissRequest = {
                    showCloudConsentDialog = false
                    pendingSendAction = null
                },
                title = { Text(stringResource(R.string.ui_petal_ai_cloud_consent_title)) },
                text = { Text(stringResource(R.string.ui_petal_ai_cloud_consent_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        PetalAiResearchEngine.setCloudConsentAccepted(context, true)
                        showCloudConsentDialog = false
                        pendingSendAction?.invoke()
                        pendingSendAction = null
                    }) {
                        Text(stringResource(R.string.ui_petal_ai_cloud_consent_start))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCloudConsentDialog = false
                        pendingSendAction = null
                    }) {
                        Text(stringResource(R.string.ui_petal_ai_cloud_consent_cancel))
                    }
                }
            )
        }

        // ── Daily Token Limit Soft Warning Dialog ──
        if (showDailyLimitWarning) {
            PetalMaterialAlertDialog(
                onDismissRequest = {
                    showDailyLimitWarning = false
                    pendingSendAction = null
                },
                title = { Text(stringResource(R.string.ui_petal_ai_token_limit_warning_title)) },
                text = { Text(stringResource(R.string.ui_petal_ai_token_limit_warning_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDailyLimitWarning = false
                        pendingSendAction?.invoke()
                        pendingSendAction = null
                    }) {
                        Text(stringResource(R.string.ui_petal_ai_token_limit_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDailyLimitWarning = false
                        pendingSendAction = null
                    }) {
                        Text(stringResource(R.string.ui_petal_ai_cloud_consent_cancel))
                    }
                }
            )
        }

        // ── Attach Open Tab Dialog ──
        if (showTabPickerDialog) {
            val allTabs by viewModel.tabs.collectAsStateWithLifecycle()
            val candidateTabs = allTabs.filter { tab ->
                tab.id != activeTabId && (isPrivate || !tab.isPrivate) && attachedTabs.none { it.tabId == tab.id }
            }

            PetalMaterialAlertDialog(
                onDismissRequest = { showTabPickerDialog = false },
                title = { Text(stringResource(R.string.ui_petal_ai_attach_open_tab)) },
                text = {
                    if (candidateTabs.isEmpty()) {
                        Text(
                            text = "No other eligible open tabs found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(candidateTabs, key = { it.id }) { t ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (attachedTabs.size < 3) {
                                                val tabContext = PageContextData(
                                                    tabId = t.id,
                                                    url = t.url,
                                                    title = t.title,
                                                    readableText = t.title
                                                )
                                                attachedTabs = attachedTabs + tabContext
                                            } else {
                                                PetalToast.show(context, "Maximum 3 tabs attached")
                                            }
                                            showTabPickerDialog = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Tab,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = t.title.ifBlank { t.url },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = t.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTabPickerDialog = false }) {
                        Text(stringResource(R.string.about_close))
                    }
                }
            )
        }

        // ── Translate Page Language Dialog ──
        if (showTranslateDialog) {
            val languages = listOf(
                "English", "Spanish", "French", "German", "Italian",
                "Portuguese", "Japanese", "Chinese", "Hindi", "Arabic", "Korean"
            )
            PetalMaterialAlertDialog(
                onDismissRequest = { showTranslateDialog = false },
                title = { Text(stringResource(R.string.ui_petal_ai_translate_dialog_title)) },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(languages) { lang ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showTranslateDialog = false
                                        val query = "Translate this page into $lang accurately and fluently."
                                        doSendMessage(query)
                                    }
                            ) {
                                Text(
                                    text = lang,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTranslateDialog = false }) {
                        Text(stringResource(R.string.about_close))
                    }
                }
            )
        }

        // ── Chat History Viewer Dialog ──
        if (showChatHistoryDialog) {
            var historySearchQuery by remember { mutableStateOf("") }
            val sessions = remember(historySearchQuery) {
                if (historySearchQuery.isBlank()) {
                    PetalAiChatHistoryStore.getSessions(context)
                } else {
                    PetalAiChatHistoryStore.searchSessions(context, historySearchQuery)
                }
            }

            PetalMaterialAlertDialog(
                onDismissRequest = { showChatHistoryDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.ui_petal_ai_chat_history))
                        if (sessions.isNotEmpty()) {
                            TextButton(onClick = {
                                PetalAiChatHistoryStore.clearAll(context)
                                showChatHistoryDialog = false
                                PetalToast.show(context, "History cleared")
                            }) {
                                Text(
                                    text = stringResource(R.string.ui_petal_ai_history_clear_all),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Search bar
                        OutlinedTextField(
                            value = historySearchQuery,
                            onValueChange = { historySearchQuery = it },
                            placeholder = { Text(stringResource(R.string.ui_petal_ai_history_search_hint)) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (sessions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_petal_ai_history_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(sessions, key = { it.id }) { item ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val preview = item.messages.lastOrNull()?.text ?: ""
                                                Text(
                                                    text = preview,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    PetalAiChatHistoryStore.deleteSession(context, item.id)
                                                    // trigger recomposition by resetting query string
                                                    historySearchQuery = historySearchQuery + ""
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.DeleteOutline,
                                                    contentDescription = stringResource(R.string.ui_petal_ai_history_delete_one),
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showChatHistoryDialog = false }) {
                        Text(stringResource(R.string.about_close))
                    }
                }
            )
        }
    }

    // Page research bottom sheet when requested from menu
    if (showPageResearchSheet) {
        PetalAiResearchSheet(
            pageTitle = activeTitle,
            pageUrl = activeUrl,
            pageContent = activeTitle,
            onDismiss = { showPageResearchSheet = false }
        )
    }
}

@Composable
private fun SuggestionPromptRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
