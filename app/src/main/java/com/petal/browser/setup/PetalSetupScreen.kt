package com.petal.browser.setup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.account.AvatarType
import com.petal.browser.account.GoogleAccountManager
import com.petal.browser.account.ProfileAvatarDisplay
import com.petal.browser.account.mozilla.FxAccountManager
import com.petal.browser.account.mozilla.FxaState
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.extensions.PetalBuiltInExtensionManager
import com.petal.browser.ui.components.*
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.theme.PetalPalettes
import kotlinx.coroutines.launch

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
        androidx.compose.ui.platform.LocalConfiguration provides localizedContext.resources.configuration
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
    val avatarUri by state.avatarUri.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefresh by remember { mutableIntStateOf(0) }
    var showMediaPicker by remember { mutableStateOf(false) }
    var showAiKey by remember { mutableStateOf(false) }
    var aiKey by remember { mutableStateOf(sp.getString("sp_gemini_api_key", "") ?: "") }
    var adBlock by remember { mutableStateOf(PetalAdBlockEngine.isAdBlockEnabled(context)) }
    var floatingTabs by remember { mutableStateOf(sp.getBoolean("sp_floating_tab_bar", true)) }
    var addressAtBottom by remember { mutableStateOf(sp.getString("sp_address_bar_position", "TOP") == "BOTTOM") }
    var darkWebpages by remember { mutableStateOf(sp.getBoolean("petal_builtin_dark_webpages", true)) }
    var selectedEngine by remember { mutableIntStateOf(state.selectedEngine.value) }
    var selectedPalette by remember { mutableStateOf(sp.getString("sp_palette_id", "petal") ?: "petal") }
    var dynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", true)) }
    var amoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
    val fxa = remember { FxAccountManager.getInstance() }
    val fxaState by fxa.accountState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

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
    val nextLabel = if (stage == 4) stringResource(R.string.petal_setup_start_browsing) else stringResource(R.string.petal_setup_next)
    val stageTitles = listOf(
        stringResource(R.string.petal_setup_hello_title), stringResource(R.string.petal_setup_access_title),
        stringResource(R.string.petal_setup_power_title), stringResource(R.string.petal_setup_you_title),
        stringResource(R.string.petal_setup_finish_title)
    )
    val stageIcons = listOf(Icons.Rounded.Favorite, Icons.Rounded.Security, Icons.Rounded.Build, Icons.Rounded.Person, Icons.Rounded.CheckCircle)
    val stageLabels = listOf(
        stringResource(R.string.petal_setup_tab_hello), stringResource(R.string.petal_setup_tab_access),
        stringResource(R.string.petal_setup_tab_power), stringResource(R.string.petal_setup_tab_you),
        stringResource(R.string.petal_setup_tab_finish)
    )

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp).padding(top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinearWavyProgressIndicator(
                    progress = { (stage + 1) / 5f },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Text(stringResource(R.string.petal_setup_brand), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(stageTitles[stage], style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                MiniPetal(
                    floatingTabs, addressAtBottom, darkWebpages, adBlock, chosenEngine,
                    Modifier.animateContentSize(spring())
                )
                Surface(
                    Modifier.weight(1f).fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnimatedContent(stage, label = "petalSetupStage") { page ->
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                when (page) {
                                    0 -> HelloStage(
                                        language, { state.setLanguage(it) }, sp, floatingTabs, { floatingTabs = it },
                                        addressAtBottom, { addressAtBottom = it }, dynamicColor, { dynamicColor = it },
                                        amoled, { amoled = it }, selectedPalette, { selectedPalette = it }
                                    )
                                    1 -> AccessStage(activity, sp, permissions, permissionRefresh, pendingPermission, { permission ->
                                        pendingPermission = permission
                                        val alreadyAsked = sp.getBoolean("setup_permission_asked_${permission.id}", false)
                                        val permanentlyDenied = alreadyAsked && permission.permissions.all { permissionName ->
                                            ContextCompat.checkSelfPermission(context, permissionName) != PackageManager.PERMISSION_GRANTED && !activity.shouldShowRequestPermissionRationale(permissionName)
                                        }
                                        if (permanentlyDenied) {
                                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                        } else {
                                            sp.edit().putBoolean("setup_permission_asked_${permission.id}", true).apply()
                                            permissionLauncher.launch(permission.permissions.toTypedArray())
                                        }
                                    }, { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) })
                                    2 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        PowerStage(sp, adBlock, { adBlock = it; PetalAdBlockEngine.setAdBlockEnabled(context, it) }, darkWebpages, { darkWebpages = it; PetalBuiltInExtensionManager.setEnabled(context, "petal_builtin_dark_webpages", it) })
                                        AppearanceLiveToggles(sp, floatingTabs, { floatingTabs = it }, addressAtBottom, { addressAtBottom = it }, dynamicColor, { dynamicColor = it }, amoled, { amoled = it }, selectedPalette, { selectedPalette = it })
                                    }
                                    3 -> YouStage(
                                        activity, avatarUri, { showMediaPicker = true }, fxaState,
                                        { activity.addAlbum(activity.getString(R.string.petal_setup_mozilla_tab), fxa.beginLogin(), true) },
                                        showAiKey, { showAiKey = it }, aiKey,
                                        { aiKey = it; sp.edit().putString("sp_gemini_api_key", it).apply() }
                                    )
                                    else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FinishStage(engines, selectedEngine, { selectedEngine = it; state.setSelectedEngine(it); sp.edit().putString("sp_search_engine", it.toString()).putBoolean("sp_search_engine_chosen", true).apply() })
                                        AppearanceLiveToggles(sp, floatingTabs, { floatingTabs = it }, addressAtBottom, { addressAtBottom = it }, dynamicColor, { dynamicColor = it }, amoled, { amoled = it }, selectedPalette, { selectedPalette = it })
                                    }
                                }
                            }
                        }
                        if (skipConfirmation) {
                            Text(stringResource(R.string.petal_setup_skip_confirmation), color = MaterialTheme.colorScheme.error)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { state.confirmSkip() }) { Text(stringResource(R.string.petal_setup_continue)) }
                                Button(onClick = onFinished) { Text(stringResource(R.string.petal_setup_skip_anyway)) }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (stage > 0) OutlinedButton(onClick = { state.previous() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.petal_setup_back)) }
                                OutlinedButton(onClick = {
                                    if (stage < 4) state.next() else onFinished()
                                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.petal_setup_skip)) }
                                Button(onClick = {
                                    if (stage == 4) {
                                        sp.edit().putString("sp_search_engine", selectedEngine.toString()).putBoolean("sp_search_engine_chosen", true).apply()
                                        onFinished()
                                    } else state.next()
                                }, modifier = Modifier.weight(1.3f)) { Text(nextLabel) }
                            }
                        }
                    }
                }
            }
            FloatingStageTabs(stage, stageLabels, stageIcons, { state.goTo(it) }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp))
        }
    }

    if (showMediaPicker) {
        PetalSheet(onDismissRequest = { showMediaPicker = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = null) {
            com.petal.browser.media.PetalMediaPickerBottomSheet(
                allowMultiple = false, acceptTypes = arrayOf("image/*"),
                onMediaSelected = { uris ->
                    showMediaPicker = false
                    uris.firstOrNull()?.let { uri ->
                        runCatching {
                            val file = java.io.File(context.filesDir, "petal_user_avatar.png")
                            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                            GoogleAccountManager.updateAvatarGalleryUri(context, Uri.fromFile(file).toString())
                            state.setAvatarUri(Uri.fromFile(file).toString())
                        }.onFailure {
                            GoogleAccountManager.updateAvatarGalleryUri(context, uri.toString())
                            state.setAvatarUri(uri.toString())
                        }
                    }
                },
                onDismissRequest = { showMediaPicker = false },
                onBrowseSystemFiles = { showMediaPicker = false }
            )
        }
    }
}

@Composable
private fun HelloStage(language: String, setLanguage: (String) -> Unit, sp: android.content.SharedPreferences, floating: Boolean, setFloating: (Boolean) -> Unit, bottom: Boolean, setBottom: (Boolean) -> Unit, dynamic: Boolean, setDynamic: (Boolean) -> Unit, amoled: Boolean, setAmoled: (Boolean) -> Unit, palette: String, setPalette: (String) -> Unit) {
    Text(stringResource(R.string.petal_setup_hello_body), style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("en" to R.string.petal_setup_english, "hi-Latn" to R.string.petal_setup_hinglish).forEach { (tag, label) ->
            Surface(Modifier.weight(1f).clickable { setLanguage(tag) }, shape = RoundedCornerShape(22.dp), color = if (language == tag) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.petal_setup_language_live), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    AppearanceLiveToggles(sp, floating, setFloating, bottom, setBottom, dynamic, setDynamic, amoled, setAmoled, palette, setPalette)
}

@Composable
private fun AccessStage(activity: BrowserActivity, sp: android.content.SharedPreferences, items: List<SetupPermission>, refresh: Int, pending: SetupPermission?, request: (SetupPermission) -> Unit, openSettings: () -> Unit) {
    val context = LocalContext.current
    Text(stringResource(R.string.petal_setup_access_body), style = MaterialTheme.typography.bodyMedium)
    items.forEach { item ->
        val granted = remember(refresh, item.id) {
            item.permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        }
        val permanentlyDenied = pending?.id == item.id && sp.getBoolean("setup_permission_asked_${item.id}", false) && item.permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED && !activity.shouldShowRequestPermissionRationale(permission)
        }
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().clickable { if (!granted) request(item) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(item.title), fontWeight = FontWeight.SemiBold)
                        if (item.optional) Text(stringResource(R.string.petal_setup_optional), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(stringResource(item.reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text(if (granted) stringResource(R.string.petal_setup_granted) else stringResource(R.string.petal_setup_not_now), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (permanentlyDenied) TextButton(onClick = openSettings) { Text(stringResource(R.string.petal_setup_open_settings)) }
    }
}

@Composable
private fun PowerStage(sp: android.content.SharedPreferences, adBlock: Boolean, setAdBlock: (Boolean) -> Unit, dark: Boolean, setDark: (Boolean) -> Unit) {
    val context = LocalContext.current
    Text(stringResource(R.string.petal_setup_power_body), style = MaterialTheme.typography.bodyMedium)
    SetupToggle(stringResource(R.string.petal_setup_ad_block), adBlock, setAdBlock, stringResource(R.string.petal_setup_ad_block_reason))
    PetalBuiltInExtensionManager.builtIns.forEach { spec ->
        val enabled = remember(spec.prefKey) { mutableStateOf(sp.getBoolean(spec.prefKey, true)) }
        SetupToggle(
            stringResource(extensionTitle(spec.prefKey)), enabled.value,
            { checked -> enabled.value = checked; PetalBuiltInExtensionManager.setEnabled(context, spec.prefKey, checked); if (spec.prefKey == "petal_builtin_dark_webpages") setDark(checked) },
            stringResource(extensionReason(spec.prefKey))
        )
    }
}

@Composable
private fun YouStage(activity: BrowserActivity, avatarUri: String, chooseAvatar: () -> Unit, fxaState: FxaState, signIn: () -> Unit, showAi: Boolean, setShowAi: (Boolean) -> Unit, aiKey: String, setAiKey: (String) -> Unit) {
    Text(stringResource(R.string.petal_setup_you_body), style = MaterialTheme.typography.bodyMedium)
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().clickable(onClick = chooseAvatar).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (avatarUri.isNotBlank()) coil.compose.AsyncImage(avatarUri, null, Modifier.size(52.dp).clip(CircleShape))
            else ProfileAvatarDisplay(profile = GoogleAccountManager.currentProfile.copy(avatarType = AvatarType.GALLERY_URI, customAvatarUri = avatarUri.ifBlank { GoogleAccountManager.currentProfile.customAvatarUri }), sizeDp = 52)
            Column(Modifier.weight(1f)) { Text(stringResource(R.string.petal_setup_profile_photo), fontWeight = FontWeight.SemiBold); Text(stringResource(R.string.petal_setup_choose_gallery), style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.Rounded.PhotoLibrary, null)
        }
    }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().clickable { if (fxaState !is FxaState.SignedIn) signIn() }.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Sync, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) { Text(stringResource(R.string.petal_setup_mozilla), fontWeight = FontWeight.SemiBold); Text(if (fxaState is FxaState.SignedIn) (fxaState as FxaState.SignedIn).email else stringResource(R.string.petal_setup_mozilla_reason), style = MaterialTheme.typography.bodySmall) }
            Text(if (fxaState is FxaState.SignedIn) stringResource(R.string.petal_setup_connected) else stringResource(R.string.petal_setup_sign_in))
        }
    }
    SetupToggle(stringResource(R.string.petal_setup_ai_optional), showAi, { setShowAi(it) }, stringResource(R.string.petal_setup_ai_reason))
    if (showAi) OutlinedTextField(aiKey, setAiKey, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.petal_setup_ai_key)) }, singleLine = true)
}

@Composable
private fun FinishStage(engines: List<SearchEngineItem>, selected: Int, onSelect: (Int) -> Unit) {
    Text(stringResource(R.string.petal_setup_finish_body), style = MaterialTheme.typography.bodyLarge)
    engines.forEach { engine ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable { onSelect(engine.index) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(engine.name, fontWeight = FontWeight.SemiBold); Text(engine.description, style = MaterialTheme.typography.bodySmall) }
            RadioButton(selected == engine.index, onClick = { onSelect(engine.index) })
        }
    }
}

@Composable
private fun AppearanceLiveToggles(sp: android.content.SharedPreferences, floating: Boolean, setFloating: (Boolean) -> Unit, bottom: Boolean, setBottom: (Boolean) -> Unit, dynamic: Boolean, setDynamic: (Boolean) -> Unit, amoled: Boolean, setAmoled: (Boolean) -> Unit, palette: String, setPalette: (String) -> Unit) {
    SetupToggle(stringResource(R.string.petal_setup_floating_tabs), floating, { value -> sp.edit().putBoolean("sp_floating_tab_bar", value).apply(); setFloating(value) })
    SetupToggle(stringResource(R.string.petal_setup_address_bottom), bottom, { value -> sp.edit().putString("sp_address_bar_position", if (value) "BOTTOM" else "TOP").apply(); setBottom(value) })
    SetupToggle(stringResource(R.string.petal_setup_dynamic_color), dynamic, { value -> sp.edit().putBoolean("useDynamicColor", value).apply(); setDynamic(value) })
    SetupToggle(stringResource(R.string.petal_setup_amoled), amoled, { value -> sp.edit().putBoolean("sp_amoled", value).apply(); setAmoled(value) })
    Text(stringResource(R.string.petal_setup_palette), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PetalPalettes.forEachIndexed { index, item ->
            FilterChip(selected = palette == item.id, onClick = { sp.edit().putString("sp_palette_id", item.id).apply(); setPalette(item.id) }, label = { Text(stringResource(paletteLabel(index))) })
        }
    }
}

@Composable
private fun SetupToggle(title: String, checked: Boolean, onChecked: (Boolean) -> Unit, reason: String? = null) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); if (reason != null) Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun FloatingStageTabs(stage: Int, labels: List<String>, icons: List<androidx.compose.ui.graphics.vector.ImageVector>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, tonalElevation = 6.dp, shadowElevation = 7.dp) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                val active = index == stage
                Surface(onClick = { onSelect(index) }, shape = RoundedCornerShape(24.dp), color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Row(Modifier.animateContentSize(spring()).padding(horizontal = if (active) 14.dp else 11.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icons[index], label, modifier = Modifier.size(20.dp), tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (active) Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        else if (index < stage) Icon(Icons.Rounded.Check, stringResource(R.string.petal_setup_finished), Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
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
    "petal_builtin_ai_blocker" -> R.string.petal_setup_ext_ai_blocker
    "petal_builtin_translate" -> R.string.petal_setup_ext_translate
    "petal_builtin_google_search_fixer" -> R.string.petal_setup_ext_search_fixer
    "petal_builtin_media_grabber" -> R.string.petal_setup_ext_media
    else -> R.string.petal_setup_ext_password
}

private fun paletteLabel(index: Int): Int = when (index) {
    1 -> R.string.petal_setup_palette_indigo
    2 -> R.string.petal_setup_palette_tide
    3 -> R.string.petal_setup_palette_zen
    4 -> R.string.petal_setup_palette_ember
    5 -> R.string.petal_setup_palette_forest
    else -> R.string.petal_setup_palette_petal
}

private fun extensionReason(prefKey: String): Int = when (prefKey) {
    "petal_builtin_webcompat" -> R.string.petal_setup_ext_webcompat_reason
    "petal_builtin_dark_webpages" -> R.string.petal_setup_ext_dark_reason
    "petal_builtin_clean_link" -> R.string.petal_setup_ext_clean_reason
    "petal_builtin_universal_copy" -> R.string.petal_setup_ext_copy_reason
    "petal_builtin_ai_blocker" -> R.string.petal_setup_ext_ai_blocker_reason
    "petal_builtin_translate" -> R.string.petal_setup_ext_translate_reason
    "petal_builtin_google_search_fixer" -> R.string.petal_setup_ext_search_fixer_reason
    "petal_builtin_media_grabber" -> R.string.petal_setup_ext_media_reason
    else -> R.string.petal_setup_ext_password_reason
}
