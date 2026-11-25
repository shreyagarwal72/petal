package com.petal.browser.setup

import com.petal.browser.ui.components.PetalSpring
import com.petal.browser.ui.theme.PetalText

import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.account.progress.PetalProgressStore
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.ui.components.PetalPageBackground
import com.petal.browser.ui.components.allSearchEngines
import com.petal.browser.ui.kit.Button
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.ThemeConfig
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.ui.theme.paletteById
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.unit.PetalBackupManager
import com.petal.browser.unit.PetalLanguages

/**
 * First-run setup, redesigned. Six pages: Hello · Look · Privacy · Access · Search · Ready.
 *
 * It is a Compose overlay (mounted by `PetalRootApp` while [PetalSetupGate.visible]) that owns its own
 * theme so palette / light-dark / AMOLED choices preview live before they are applied app-wide.
 */
@Composable
fun PetalSetupFlow(activity: ComponentActivity, onFinished: () -> Unit) {
    val state: PetalSetupState = viewModel(viewModelStoreOwner = activity)
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }

    // ── Live theme state (pages write SharedPreferences immediately; this drives the local preview) ──
    val initialTheme = remember {
        SetupTheme(
            paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId,
            dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported),
            amoled = sp.getBoolean("sp_amoled", false),
            mode = runCatching { ThemeConfig.valueOf(sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "") }
                .getOrDefault(ThemeConfig.FOLLOW_SYSTEM)
        )
    }
    var theme by remember { mutableStateOf(initialTheme) }
    val fontState = com.petal.browser.ui.theme.rememberFontState()
    val colorStyle = remember {
        runCatching { ColorStyle.valueOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "") }.getOrDefault(ColorStyle.TONAL_SPOT)
    }
    val dark = when (theme.mode) {
        ThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeConfig.LIGHT -> false
        ThemeConfig.DARK -> true
    }

    val language by state.language.collectAsStateWithLifecycle()
    val baseContext = LocalContext.current
    val localizedContext = remember(language, baseContext) {
        if (language == "system") baseContext
        else {
            val config = android.content.res.Configuration(baseContext.resources.configuration)
            val locale = java.util.Locale.forLanguageTag(language)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            baseContext.createConfigurationContext(config)
        }
    }

    PetalExpressiveTheme(
        darkTheme = dark,
        dynamicColor = theme.dynamicColor,
        useAmoled = theme.amoled,
        fontState = fontState,
        colorStyle = colorStyle,
        paletteId = theme.paletteId
    ) {
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedContext.resources.configuration
        ) {
            SetupScaffold(
                activity = activity,
                state = state,
                sp = sp,
                theme = theme,
                onTheme = { theme = it },
                onFinish = { completed, forceRecreate ->
                    val themeChanged = forceRecreate || theme != initialTheme
                    if (completed && !sp.getBoolean(PetalSetupGate.KEY_XP_AWARDED, false)) {
                        // Reuse Petal's existing XP system; guarded so a replayed setup never pays twice.
                        sp.edit().putBoolean(PetalSetupGate.KEY_XP_AWARDED, true).apply()
                        runCatching { PetalProgressStore.addXp(activity, PetalSetupGate.WELCOME_XP) }
                    }
                    val chosenLanguage = state.language.value
                    val priorLanguage = sp.getString("sp_app_language", "system") ?: "system"
                    onFinished()
                    if (chosenLanguage != priorLanguage) {
                        // Applies the locale (and recreates the activity, which also applies the theme).
                        PetalAndroidUtils.setAppLanguage(activity, chosenLanguage)
                    } else if (themeChanged) {
                        // The root theme was composed before setup ran; recreate so it picks up the new look.
                        activity.window.decorView.post { if (!activity.isFinishing) activity.recreate() }
                    }
                }
            )
        }
    }
}

@Composable
private fun SetupScaffold(
    activity: ComponentActivity,
    state: PetalSetupState,
    sp: android.content.SharedPreferences,
    theme: SetupTheme,
    onTheme: (SetupTheme) -> Unit,
    onFinish: (completed: Boolean, forceRecreate: Boolean) -> Unit
) {
    val context = LocalContext.current
    val stage by state.stage.collectAsStateWithLifecycle()
    val language by state.language.collectAsStateWithLifecycle()
    val skipConfirmation by state.skipConfirmation.collectAsStateWithLifecycle()
    val engines = remember(context) { allSearchEngines(context) }
    var adBlock by remember { mutableStateOf(PetalAdBlockEngine.isAdBlockEnabled(context)) }
    var engineIndex by remember {
        mutableStateOf(sp.getString("sp_search_engine", "0")?.toIntOrNull() ?: state.selectedEngine.value)
    }
    val permissions = remember { setupPermissions() }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            // Restore runs in the background and shows its own progress, result and restart prompt.
            PetalBackupManager.restoreFromUri(context, uri, true, true, true, true, true, true)
        }.onSuccess {
            // A restore replaces every preference, so there is nothing left to configure here.
            onFinish(false, false)
        }.onFailure {
            Toast.makeText(context, it.localizedMessage ?: "Restore failed", Toast.LENGTH_SHORT).show()
        }
    }

    val titles = listOf(
        stringResource(R.string.petal_setup_tab_hello),
        stringResource(R.string.petal_setup_tab_look),
        stringResource(R.string.petal_setup_tab_privacy),
        stringResource(R.string.petal_setup_tab_access),
        stringResource(R.string.petal_setup_search),
        stringResource(R.string.petal_setup_tab_finish)
    )
    val isLast = stage == PetalSetupState.STAGE_READY

    // Registered last in composition order, so it wins over the root's own Back handling while setup is open.
    BackHandler(enabled = true) { state.previous() }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            // The browser surface underneath must not receive touches while setup is showing.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
        color = MaterialTheme.colorScheme.background
    ) {
        Box(Modifier.fillMaxSize()) {
            com.petal.browser.ui.components.PetalPageBackground(pageKey = "petal_setup")

            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                // ── Top: step label, skip, progress ──
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.petal_setup_step_of, stage + 1, titles.size) + "  ·  " + titles[stage],
                            style = PetalText.sectionHeader,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isLast) {
                            TextButton(onClick = { state.requestSkip() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                Text(stringResource(R.string.petal_setup_skip), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                    SetupProgressBar(stage = stage, count = PetalSetupState.STAGE_COUNT)
                }

                // ── Page ──
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = stage,
                        transitionSpec = {
                            val forward = targetState > initialState
                            val dir = if (forward) 1 else -1
                            (slideInHorizontally(PetalSpring.calm()) { dir * it / 3 } +
                                fadeIn(PetalSpring.calm()) + scaleIn(initialScale = 0.96f, animationSpec = PetalSpring.calm()))
                                .togetherWith(
                                    slideOutHorizontally(PetalSpring.calm()) { -dir * it / 4 } +
                                        fadeOut(PetalSpring.calm()) + scaleOut(targetScale = 0.96f, animationSpec = PetalSpring.calm())
                                )
                        },
                        label = "petalSetupPage"
                    ) { page ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp)
                                .padding(top = 18.dp, bottom = 16.dp)
                        ) {
                            when (page) {
                                PetalSetupState.STAGE_WELCOME -> WelcomePage(language, state::setLanguage)
                                PetalSetupState.STAGE_LOOK -> LookPage(sp, theme, onTheme, adBlock)
                                PetalSetupState.STAGE_PRIVACY -> PrivacyPage(sp, adBlock) {
                                    adBlock = it
                                    PetalAdBlockEngine.setAdBlockEnabled(context, it)
                                }
                                PetalSetupState.STAGE_ACCESS -> AccessPage(activity, sp, permissions)
                                PetalSetupState.STAGE_SEARCH -> SearchPage(sp, engines, engineIndex) {
                                    engineIndex = it
                                    state.setSelectedEngine(it)
                                    sp.edit().putString("sp_search_engine", it.toString()).putBoolean("sp_search_engine_chosen", true).apply()
                                }
                                else -> ReadyPage(
                                    activity,
                                    SetupSummary(
                                        language = PetalLanguages.findLanguage(language).nativeName,
                                        colors = if (theme.dynamicColor) stringResource(R.string.petal_setup_dynamic_color) else paletteById(theme.paletteId).label,
                                        engine = engines.firstOrNull { it.index == engineIndex }?.name ?: "",
                                        adBlock = adBlock,
                                        permissionsGranted = permissions.count { it.isGranted(context) },
                                        xpAvailable = !sp.getBoolean(PetalSetupGate.KEY_XP_AWARDED, false)
                                    )
                                )
                            }
                        }
                    }
                }

                // ── Bottom actions ──
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (stage == PetalSetupState.STAGE_WELCOME) {
                        PrimaryAction(stringResource(R.string.petal_setup_get_started), onClick = { state.next() })
                        FilledTonalButton(
                            onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.petal_setup_restore_button), fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = { state.previous() },
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.petal_setup_back))
                            }
                            PrimaryAction(
                                label = stringResource(if (isLast) R.string.petal_setup_start_browsing else R.string.petal_setup_next),
                                modifier = Modifier.weight(1f),
                                onClick = { if (isLast) onFinish(true, false) else state.next() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (skipConfirmation) {
        AlertDialog(
            onDismissRequest = { state.dismissSkip() },
            title = { Text(stringResource(R.string.petal_setup_skip_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.petal_setup_skip_confirmation)) },
            confirmButton = {
                Button(
                    onClick = { state.dismissSkip(); onFinish(false, false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.petal_setup_skip_anyway), maxLines = 1) }
            },
            dismissButton = {
                TextButton(onClick = { state.dismissSkip() }) { Text(stringResource(R.string.petal_setup_continue), maxLines = 1) }
            }
        )
    }
}

@Composable
private fun PrimaryAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(label, style = PetalText.button, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
    }
}
