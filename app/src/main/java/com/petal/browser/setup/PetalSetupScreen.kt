package com.petal.browser.setup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.extensions.PetalBuiltInExtensionManager
import com.petal.browser.ui.components.*
import com.petal.browser.ui.containment.*
import com.petal.browser.unit.BackupUnit

private data class SetupPermission(val id: String, val title: Int, val reason: Int, val permissions: List<String>, val optional: Boolean = false)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalSetupScreen(
    activity: BrowserActivity,
    state: PetalSetupState,
    sp: android.content.SharedPreferences,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val language by state.language.collectAsStateWithLifecycle()
    val localizedContext = remember(language, context) { PetalSetupBridge.localizedContext(context, language) }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalContext provides localizedContext,
        androidx.compose.ui.platform.LocalConfiguration provides localizedContext.resources.configuration,
        androidx.activity.compose.LocalActivityResultRegistryOwner provides activity
    ) {
        PetalSetupContent(activity, state, sp, onFinished)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PetalSetupContent(
    activity: BrowserActivity,
    state: PetalSetupState,
    sp: android.content.SharedPreferences,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val stage by state.stage.collectAsStateWithLifecycle()
    val language by state.language.collectAsStateWithLifecycle()
    val skipConfirmation by state.skipConfirmation.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefresh by remember { mutableIntStateOf(0) }
    var showAiKey by remember { mutableStateOf(false) }
    var aiKey by remember { mutableStateOf(sp.getString("sp_gemini_api_key", "") ?: "") }
    var adBlock by remember { mutableStateOf(PetalAdBlockEngine.isAdBlockEnabled(context)) }
    var floatingTabs by remember { mutableStateOf(sp.getBoolean("sp_floating_tab_bar", true)) }
    var addressAtBottom by remember { mutableStateOf(sp.getString("sp_address_bar_position", "TOP") == "BOTTOM") }
    var darkWebpages by remember { mutableStateOf(sp.getBoolean("petal_builtin_dark_webpages", true)) }
    var selectedEngine by remember { mutableIntStateOf(state.selectedEngine.value) }
    var dynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", true)) }
    var amoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }

    val systemRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                BackupUnit.restoreFromUri(
                    context,
                    uri,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true
                )
                Toast.makeText(context, context.getString(R.string.petal_setup_restore_success), Toast.LENGTH_LONG).show()
                // Skip setup immediately after restoring only
                onFinished()
            }.onFailure {
                Toast.makeText(context, it.localizedMessage ?: "Restore failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissions = remember {
        val media = if (Build.VERSION.SDK_INT >= 33) listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        buildList {
            if (Build.VERSION.SDK_INT >= 33) add(SetupPermission("notifications", R.string.petal_setup_notifications, R.string.petal_setup_notifications_reason, listOf(Manifest.permission.POST_NOTIFICATIONS)))
            add(SetupPermission("camera", R.string.petal_setup_camera, R.string.petal_setup_camera_reason, listOf(Manifest.permission.CAMERA)))
            add(SetupPermission("media", R.string.petal_setup_media, R.string.petal_setup_media_reason, media))
            add(SetupPermission("microphone", R.string.petal_setup_microphone, R.string.petal_setup_microphone_reason, listOf(Manifest.permission.RECORD_AUDIO)))
            add(SetupPermission("location", R.string.petal_setup_location, R.string.petal_setup_location_reason, listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), true))
        }
    }
    var pendingPermission by remember { mutableStateOf<SetupPermission?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRefresh++
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val engines = remember(context) { allSearchEngines(context) }
    val chosenEngine = engines.firstOrNull { it.index == selectedEngine } ?: engines.firstOrNull()
    val isLastStage = stage == 3
    val nextLabel = if (isLastStage) stringResource(R.string.petal_setup_start_browsing) else stringResource(R.string.petal_setup_next)

    val stageTitles = listOf(
        stringResource(R.string.petal_setup_welcome_eyebrow),
        stringResource(R.string.petal_setup_access_title),
        stringResource(R.string.petal_setup_power_title),
        stringResource(R.string.petal_setup_you_title)
    )
    val stageIcons = listOf(
        Icons.Rounded.Favorite,
        Icons.Rounded.Security,
        Icons.Rounded.Shield,
        Icons.Rounded.Tune
    )
    val stageLabels = listOf(
        stringResource(R.string.petal_setup_tab_hello),
        stringResource(R.string.petal_setup_tab_access),
        stringResource(R.string.petal_setup_tab_power),
        stringResource(R.string.petal_setup_tab_you)
    )

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            M3ExpressiveVariableBackground(pageSeed = "petal_setup")

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = if (stage == 0) 16.dp else if (floatingTabs) 88.dp else 76.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Header: Segmented progress bars and explicit Skip button (matching reference design)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Segmented Expressive Progress Indicators
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0..3) {
                            val isCompletedOrActive = i <= stage
                            val segmentAlpha = if (i == stage) 1f else if (i < stage) 0.85f else 0.22f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (isCompletedOrActive) MaterialTheme.colorScheme.primary.copy(alpha = segmentAlpha)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = segmentAlpha)
                                    )
                            )
                        }
                    }

                    // Direct Skip button that skips entire setup immediately
                    TextButton(
                        onClick = onFinished,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            stringResource(R.string.petal_setup_skip),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (stage == 0) {
                    // ── NEW EXPRESSIVE FRONT PAGE ──────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Spacer(Modifier.height(8.dp))

                            // Animated M3 Expressive Shaped Hero Artwork
                            PetalExpressiveHeroArt(
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Eyebrow Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            ) {
                                Text(
                                    text = stringResource(R.string.petal_setup_welcome_eyebrow),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)
                                )
                            }

                            // Big Expressive Bold Headline
                            Text(
                                text = stringResource(R.string.petal_setup_hero_headline),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onBackground,
                                lineHeight = androidx.compose.ui.unit.TextUnit(42f, androidx.compose.ui.unit.TextUnitType.Sp)
                            )

                            // Subtitle description
                            Text(
                                text = stringResource(R.string.petal_setup_hero_subtitle),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp)
                            )
                        }

                        // Bottom Actions on Front Page: Get started pill button + Restore from backup
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Primary "Get started" Expressive Pill Button
                            Button(
                                onClick = { state.next() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.petal_setup_get_started),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Secondary "Restore from backup" action (Skips entire setup upon successful restore)
                            OutlinedButton(
                                onClick = { systemRestoreLauncher.launch(arrayOf("application/json", "*/*")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(26.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.petal_setup_restore_button),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Stages 1..3 with M3 containment and animated transitions
                    Surface(
                        Modifier.weight(1f).fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AnimatedContent(
                                targetState = stage,
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        (slideInHorizontally(tween(320)) { it / 2 } + fadeIn(tween(320)) + scaleIn(initialScale = 0.94f, animationSpec = tween(320)))
                                            .togetherWith(slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(250)) + scaleOut(targetScale = 0.94f, animationSpec = tween(250)))
                                    } else {
                                        (slideInHorizontally(tween(320)) { -it / 2 } + fadeIn(tween(320)) + scaleIn(initialScale = 0.94f, animationSpec = tween(320)))
                                            .togetherWith(slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(250)) + scaleOut(targetScale = 0.94f, animationSpec = tween(250)))
                                    }
                                },
                                label = "petalSetupStageContent"
                            ) { page ->
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    when (page) {
                                        1 -> AccessStage(
                                            activity = activity,
                                            sp = sp,
                                            items = permissions,
                                            refresh = permissionRefresh,
                                            pending = pendingPermission,
                                            request = { permission ->
                                                pendingPermission = permission
                                                val alreadyAsked = sp.getBoolean("setup_permission_asked_${permission.id}", false)
                                                val permanentlyDenied = alreadyAsked && permission.permissions.all { perm ->
                                                    ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED &&
                                                        !activity.shouldShowRequestPermissionRationale(perm)
                                                }
                                                if (permanentlyDenied) {
                                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                                } else {
                                                    sp.edit().putBoolean("setup_permission_asked_${permission.id}", true).apply()
                                                    try {
                                                        permissionLauncher.launch(permission.permissions.toTypedArray())
                                                    } catch (_: Exception) {
                                                        androidx.core.app.ActivityCompat.requestPermissions(activity, permission.permissions.toTypedArray(), 17322)
                                                    }
                                                }
                                            },
                                            openSettings = {
                                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                            }
                                        )
                                        2 -> PowerStage(
                                            sp = sp,
                                            adBlock = adBlock,
                                            setAdBlock = { adBlock = it; PetalAdBlockEngine.setAdBlockEnabled(context, it) },
                                            dark = darkWebpages,
                                            setDark = { darkWebpages = it; PetalBuiltInExtensionManager.setEnabled(context, "petal_builtin_dark_webpages", it) }
                                        )
                                        3 -> MakePetalYoursStage(
                                            engines = engines,
                                            selectedEngine = selectedEngine,
                                            onSelectEngine = {
                                                selectedEngine = it
                                                state.setSelectedEngine(it)
                                                sp.edit().putString("sp_search_engine", it.toString()).putBoolean("sp_search_engine_chosen", true).apply()
                                            },
                                            showAi = showAiKey,
                                            setShowAi = { showAiKey = it },
                                            aiKey = aiKey,
                                            setAiKey = {
                                                aiKey = it
                                                sp.edit().putString("sp_gemini_api_key", it).apply()
                                            }
                                        )
                                    }
                                }
                            }

                            // Bottom Actions: Back, Skip, Next
                            if (skipConfirmation) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            stringResource(R.string.petal_setup_skip_confirmation),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = { state.confirmSkip() },
                                                shape = RoundedCornerShape(18.dp)
                                            ) {
                                                Text(stringResource(R.string.petal_setup_continue), maxLines = 1)
                                            }
                                            Button(
                                                onClick = onFinished,
                                                shape = RoundedCornerShape(18.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                            ) {
                                                Text(stringResource(R.string.petal_setup_skip_anyway), color = MaterialTheme.colorScheme.onError, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { state.previous() },
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Icon(Icons.Rounded.ArrowBack, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.petal_setup_back), maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = onFinished,
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(stringResource(R.string.petal_setup_skip), maxLines = 1)
                                    }
                                    Button(
                                        onClick = {
                                            if (isLastStage) {
                                                sp.edit().putString("sp_search_engine", selectedEngine.toString()).putBoolean("sp_search_engine_chosen", true).apply()
                                                onFinished()
                                            } else {
                                                state.next()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Text(nextLabel, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            if (isLastStage) Icons.Rounded.Check else Icons.Rounded.ArrowForward,
                                            null,
                                            Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating vs Docked bottom navbar (only visible for stages > 0)
            if (stage > 0) {
                FloatingStageTabs(
                    stage = stage,
                    labels = stageLabels,
                    icons = stageIcons,
                    floating = floatingTabs,
                    onSelect = { state.goTo(it) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun HelloStage(
    language: String,
    setLanguage: (String) -> Unit,
    sp: android.content.SharedPreferences,
    floating: Boolean,
    setFloating: (Boolean) -> Unit,
    bottom: Boolean,
    setBottom: (Boolean) -> Unit,
    dynamic: Boolean,
    setDynamic: (Boolean) -> Unit,
    amoled: Boolean,
    setAmoled: (Boolean) -> Unit,
    onRestoreBackup: () -> Unit
) {
    PetalSettingsSection(
        title = stringResource(R.string.petal_setup_hello_title),
        icon = Icons.Rounded.Translate,
        cardId = "setup_language"
    ) {
        Text(
            stringResource(R.string.petal_setup_hello_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        var showLanguageSheet by remember { mutableStateOf(false) }
        var languageSearchQuery by remember { mutableStateOf("") }
        val currentLanguage = remember(language) { com.petal.browser.unit.PetalLanguages.findLanguage(language) }

        val quickLanguages = remember {
            listOf(
                com.petal.browser.unit.PetalLanguages.findLanguage("en"),
                com.petal.browser.unit.PetalLanguages.findLanguage("hi-Latn"),
                com.petal.browser.unit.PetalLanguages.findLanguage("hi"),
                com.petal.browser.unit.PetalLanguages.findLanguage("es")
            )
        }

        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            quickLanguages.forEach { lang ->
                val isSelected = language == lang.tag
                FilterChip(
                    selected = isSelected,
                    onClick = { setLanguage(lang.tag) },
                    label = { Text(lang.nativeName) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }

            FilledTonalButton(
                onClick = {
                    languageSearchQuery = ""
                    showLanguageSheet = true
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (quickLanguages.none { it.tag == language }) currentLanguage.displayLabel else "All (${com.petal.browser.unit.PetalLanguages.ALL_LANGUAGES.size})",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        if (showLanguageSheet) {
            PetalSheet(
                onDismissRequest = { showLanguageSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.ui_app_language),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.ui_choose_your_preferred_display_language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = languageSearchQuery,
                        onValueChange = { languageSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search language...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (languageSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { languageSearchQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    val filteredLanguages = remember(languageSearchQuery) {
                        if (languageSearchQuery.isBlank()) {
                            com.petal.browser.unit.PetalLanguages.ALL_LANGUAGES
                        } else {
                            val q = languageSearchQuery.trim().lowercase()
                            com.petal.browser.unit.PetalLanguages.ALL_LANGUAGES.filter {
                                it.nativeName.lowercase().contains(q) ||
                                it.englishName.lowercase().contains(q) ||
                                it.tag.lowercase().contains(q)
                            }
                        }
                    }

                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredLanguages.size) { idx ->
                            val lang = filteredLanguages[idx]
                            val isSelected = language == lang.tag
                            PetalSelectableOptionCard(
                                title = lang.displayLabel,
                                subtitle = if (lang.tag != "system") "BCP-47: ${lang.tag}" else null,
                                selected = isSelected,
                                onClick = {
                                    setLanguage(lang.tag)
                                    showLanguageSheet = false
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    // Appearance Toggles using PetalSettingsSection with M3 containment
    PetalSettingsSection(
        title = stringResource(R.string.title_theme),
        icon = Icons.Rounded.Palette,
        cardId = "setup_theme"
    ) {
        PetalGroup(rowCount = 4) { index, position ->
            when (index) {
                0 -> PetalSettingsToggleRow(
                    title = stringResource(R.string.petal_setup_floating_tabs),
                    subtitle = stringResource(R.string.pref_summary_floating_tab_bar),
                    icon = Icons.Rounded.Tab,
                    checked = floating,
                    onCheckedChange = { value ->
                        sp.edit().putBoolean("sp_floating_tab_bar", value).apply()
                        setFloating(value)
                    },
                    position = position
                )
                1 -> PetalSettingsToggleRow(
                    title = stringResource(R.string.petal_setup_address_bottom),
                    subtitle = stringResource(R.string.petal_setup_address_bottom),
                    icon = Icons.Rounded.VerticalAlignBottom,
                    checked = bottom,
                    onCheckedChange = { value ->
                        sp.edit().putString("sp_address_bar_position", if (value) "BOTTOM" else "TOP").apply()
                        setBottom(value)
                    },
                    position = position
                )
                2 -> PetalSettingsToggleRow(
                    title = stringResource(R.string.petal_setup_dynamic_color),
                    subtitle = stringResource(R.string.pref_summary_dynamic_colors),
                    icon = Icons.Rounded.Palette,
                    checked = dynamic,
                    onCheckedChange = { value ->
                        sp.edit().putBoolean("useDynamicColor", value).apply()
                        setDynamic(value)
                    },
                    position = position
                )
                3 -> PetalSettingsToggleRow(
                    title = stringResource(R.string.petal_setup_amoled),
                    subtitle = stringResource(R.string.pref_summary_amoled),
                    icon = Icons.Rounded.DarkMode,
                    checked = amoled,
                    onCheckedChange = { value ->
                        sp.edit().putBoolean("sp_amoled", value).apply()
                        setAmoled(value)
                    },
                    position = position
                )
            }
        }
    }

    // Restore Backup section
    PetalSettingsSection(
        title = stringResource(R.string.petal_setup_restore_title),
        icon = Icons.Rounded.Restore,
        cardId = "setup_backup"
    ) {
        PetalGroupRow(
            icon = Icons.Rounded.Restore,
            title = stringResource(R.string.petal_setup_restore_title),
            subtitle = stringResource(R.string.petal_setup_restore_desc),
            position = PetalGroupPosition.SINGLE,
            onClick = onRestoreBackup,
            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
            iconTint = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun AccessStage(
    activity: BrowserActivity,
    sp: android.content.SharedPreferences,
    items: List<SetupPermission>,
    refresh: Int,
    pending: SetupPermission?,
    request: (SetupPermission) -> Unit,
    openSettings: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            stringResource(R.string.petal_setup_access_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        PetalGroup(rowCount = items.size) { index, position ->
            val item = items[index]
            val granted = remember(refresh, item.id) {
                item.permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
            }
            val permanentlyDenied = pending?.id == item.id &&
                sp.getBoolean("setup_permission_asked_${item.id}", false) &&
                item.permissions.all { permission ->
                    ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED &&
                        !activity.shouldShowRequestPermissionRationale(permission)
                }

            val permIcon = when (item.id) {
                "notifications" -> Icons.Rounded.Notifications
                "camera" -> Icons.Rounded.CameraAlt
                "media" -> Icons.Rounded.PhotoLibrary
                "microphone" -> Icons.Rounded.Mic
                "location" -> Icons.Rounded.LocationOn
                else -> Icons.Rounded.Security
            }

            Card(
                onClick = {
                    if (permanentlyDenied) openSettings()
                    else if (!granted) request(item)
                },
                shape = petalGroupShape(position),
                colors = CardDefaults.cardColors(containerColor = petalGroupSurfaceColor()),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PetalGroupIconBadge(
                        icon = permIcon,
                        container = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        tint = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(item.title),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.optional) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        stringResource(R.string.petal_setup_optional),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            stringResource(item.reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (granted) {
                        FilledTonalButton(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.Check, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.petal_setup_granted), style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (permanentlyDenied) {
                        OutlinedButton(
                            onClick = openSettings,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.petal_setup_open_settings), style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Button(
                            onClick = { request(item) },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.ui_grant), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerStage(
    sp: android.content.SharedPreferences,
    adBlock: Boolean,
    setAdBlock: (Boolean) -> Unit,
    dark: Boolean,
    setDark: (Boolean) -> Unit
) {
    val context = LocalContext.current

    PetalSettingsSection(
        title = stringResource(R.string.petal_setup_power_title),
        icon = Icons.Rounded.Shield,
        cardId = "setup_power_features"
    ) {
        Text(
            stringResource(R.string.petal_setup_power_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val builtIns = PetalBuiltInExtensionManager.builtIns
        val totalRows = 1 + builtIns.size

        PetalGroup(rowCount = totalRows) { index, position ->
            if (index == 0) {
                PetalSettingsToggleRow(
                    title = stringResource(R.string.petal_setup_ad_block),
                    subtitle = stringResource(R.string.petal_setup_ad_block_reason),
                    icon = Icons.Rounded.Shield,
                    checked = adBlock,
                    onCheckedChange = setAdBlock,
                    position = position
                )
            } else {
                val spec = builtIns[index - 1]
                val defaultVal = true
                val enabled = remember(spec.prefKey) { mutableStateOf(sp.getBoolean(spec.prefKey, defaultVal)) }
                val extIcon = when (spec.prefKey) {
                    "petal_builtin_dark_webpages" -> Icons.Rounded.DarkMode
                    "petal_builtin_clean_link" -> Icons.Rounded.LinkOff
                    "petal_builtin_universal_copy" -> Icons.Rounded.ContentCopy
                    "petal_builtin_translate" -> Icons.Rounded.Translate
                    "petal_builtin_google_search_fixer" -> Icons.Rounded.Build
                    "petal_builtin_media_grabber" -> Icons.Rounded.Download
                    else -> Icons.Rounded.Extension
                }
                PetalSettingsToggleRow(
                    title = stringResource(extensionTitle(spec.prefKey)),
                    subtitle = stringResource(extensionReason(spec.prefKey)),
                    icon = extIcon,
                    checked = enabled.value,
                    onCheckedChange = { checked ->
                        enabled.value = checked
                        PetalBuiltInExtensionManager.setEnabled(context, spec.prefKey, checked)
                        if (spec.prefKey == "petal_builtin_dark_webpages") setDark(checked)
                    },
                    position = position
                )
            }
        }
    }
}

@Composable
private fun MakePetalYoursStage(
    engines: List<SearchEngineItem>,
    selectedEngine: Int,
    onSelectEngine: (Int) -> Unit,
    showAi: Boolean,
    setShowAi: (Boolean) -> Unit,
    aiKey: String,
    setAiKey: (String) -> Unit
) {
    PetalSettingsSection(
        title = stringResource(R.string.petal_setup_search),
        icon = Icons.Rounded.Search,
        cardId = "setup_search_engine"
    ) {
        Text(
            stringResource(R.string.petal_setup_you_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PetalGroup(rowCount = engines.size) { index, position ->
            val engine = engines[index]
            val isSelected = selectedEngine == engine.index
            Card(
                onClick = { onSelectEngine(engine.index) },
                shape = petalGroupShape(position),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else petalGroupSurfaceColor()
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PetalGroupIconBadge(
                        icon = Icons.Rounded.Search,
                        container = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(Modifier.weight(1f)) {
                        Text(engine.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            engine.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectEngine(engine.index) }
                    )
                }
            }
        }
    }

    PetalSettingsSection(
        title = stringResource(R.string.ui_ai_assistant),
        icon = Icons.Rounded.AutoAwesome,
        cardId = "setup_ai_assistant"
    ) {
        PetalSettingsToggleRow(
            title = stringResource(R.string.petal_setup_ai_optional),
            subtitle = stringResource(R.string.petal_setup_ai_reason),
            icon = Icons.Rounded.AutoAwesome,
            checked = showAi,
            onCheckedChange = setShowAi,
            position = PetalGroupPosition.SINGLE
        )

        AnimatedVisibility(visible = showAi) {
            OutlinedTextField(
                value = aiKey,
                onValueChange = setAiKey,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.petal_setup_ai_key)) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )
        }
    }
}

@Composable
private fun FloatingStageTabs(
    stage: Int,
    labels: List<String>,
    icons: List<androidx.compose.ui.graphics.vector.ImageVector>,
    floating: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (floating) {
        Surface(
            modifier = modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                labels.forEachIndexed { index, label ->
                    val active = index == stage
                    Surface(
                        onClick = { onSelect(index) },
                        shape = RoundedCornerShape(24.dp),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Row(
                            Modifier
                                .animateContentSize(spring())
                                .padding(horizontal = if (active) 14.dp else 10.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icons[index],
                                label,
                                modifier = Modifier.size(18.dp),
                                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (active) {
                                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            } else if (index < stage) {
                                Icon(Icons.Rounded.Check, stringResource(R.string.petal_setup_finished), Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                labels.forEachIndexed { index, label ->
                    val active = index == stage
                    Surface(
                        onClick = { onSelect(index) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Row(
                            Modifier
                                .animateContentSize(spring())
                                .padding(horizontal = if (active) 12.dp else 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icons[index],
                                label,
                                modifier = Modifier.size(18.dp),
                                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (active) {
                                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            } else if (index < stage) {
                                Icon(Icons.Rounded.Check, stringResource(R.string.petal_setup_finished), Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun extensionTitle(prefKey: String): Int = when (prefKey) {
    "petal_builtin_webcompat" -> R.string.petal_setup_ext_webcompat
    "petal_builtin_dark_webpages" -> R.string.petal_setup_ext_dark
    "petal_builtin_clean_link" -> R.string.petal_setup_ext_clean
    "petal_builtin_universal_copy" -> R.string.petal_setup_ext_copy
    "petal_builtin_translate" -> R.string.petal_setup_ext_translate
    "petal_builtin_google_search_fixer" -> R.string.petal_setup_ext_search_fixer
    "petal_builtin_media_grabber" -> R.string.petal_setup_ext_media
    else -> R.string.petal_setup_ext_password
}

private fun extensionReason(prefKey: String): Int = when (prefKey) {
    "petal_builtin_webcompat" -> R.string.petal_setup_ext_webcompat_reason
    "petal_builtin_dark_webpages" -> R.string.petal_setup_ext_dark_reason
    "petal_builtin_clean_link" -> R.string.petal_setup_ext_clean_reason
    "petal_builtin_universal_copy" -> R.string.petal_setup_ext_copy_reason
    "petal_builtin_translate" -> R.string.petal_setup_ext_translate_reason
    "petal_builtin_google_search_fixer" -> R.string.petal_setup_ext_search_fixer_reason
    "petal_builtin_media_grabber" -> R.string.petal_setup_ext_media_reason
    else -> R.string.petal_setup_ext_password_reason
}
