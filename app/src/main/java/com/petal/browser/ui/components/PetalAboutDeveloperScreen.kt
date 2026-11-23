package com.petal.browser.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.predictive.PetalContentSnapshot
import com.petal.browser.predictive.PetalPredictiveShell
import com.petal.browser.ui.containment.PetalBadgeVariant
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalMaterialAlertDialog
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSnackbarHost
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.ui.theme.layout.petalFlingBehavior
import com.petal.browser.unit.PetalBrowserUtils
import com.petal.browser.util.PetalWebLinks
import kotlinx.coroutines.launch

/**
 * Java Interop Bridge to present the Material 3 Expressive "About Developer" page.
 * Keeps existing callers compatible while delegating to [PetalAboutDeveloperScreen].
 */
object PetalAboutDeveloperBridge {
    private var activeComposeView: ComposeView? = null

    @JvmStatic
    @JvmOverloads
    fun show(activity: ComponentActivity, onDismiss: Runnable? = null) {
        activity.runOnUiThread {
            try {
                activeComposeView?.let {
                    (it.parent as? ViewGroup)?.removeView(it)
                }
                activeComposeView = null

                val rootView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@runOnUiThread
                PetalContentSnapshot.capture(rootView)

                var composeView: ComposeView? = null
                fun cleanup() {
                    PetalContentSnapshot.clear()
                    composeView?.let { cv ->
                        (cv.parent as? ViewGroup)?.removeView(cv)
                        if (activeComposeView == cv) {
                            activeComposeView = null
                        }
                    }
                    onDismiss?.run()
                }

                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val isExpressiveColors = sp.getBoolean("sp_expressive_colors", false)
                val useDynamic = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                val fontWidthVal = sp.getFloat("sp_font_width", 92f)
                val fontWeightVal = sp.getInt("sp_font_weight", 750)
                val fontRoundnessVal = sp.getFloat("sp_font_roundness", 100f)

                val appFont = try { AppFont.valueOf(fontName) } catch (_: Exception) { AppFont.PETAL }
                val colorStyle = try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT }

                composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewTreeOnBackPressedDispatcherOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                    setContent {
                        val snapshotBitmap = remember { PetalContentSnapshot.current?.asImageBitmap() }

                        PetalExpressiveTheme(
                            dynamicColor = useDynamic,
                            useAmoled = isAmoled,
                            expressiveColors = isExpressiveColors,
                            appFont = appFont,
                            colorStyle = colorStyle,
                            paletteId = paletteId,
                            fontWidth = fontWidthVal,
                            fontWeight = fontWeightVal,
                            fontRoundness = fontRoundnessVal
                        ) {
                            PetalAboutDeveloperScreen(
                                backgroundSnapshot = snapshotBitmap,
                                onBack = { cleanup() },
                                wrapPredictive = true
                            )
                        }
                    }
                }
                activeComposeView = composeView
                rootView.addView(
                    composeView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

/**
 * Unified native About & Developer Screen for Petal Browser.
 * Integrates predictive-back transitions, liquid glass header scrolling,
 * interactive metadata rows, build/engine diagnostics, and developer support.
 */
@Composable
fun PetalAboutDeveloperScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** False when hosted inside Settings, whose container owns the single predictive-back surface. */
    wrapPredictive: Boolean = true,
    onNavigateToUpdater: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = remember { PetalHapticEngine.getInstance(context) }

    var showFuelSheet by remember { mutableStateOf(false) }
    var showBuildInfoDialog by remember { mutableStateOf(false) }
    var showNoticesDialog by remember { mutableStateOf(false) }

    val pInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Throwable) {
            null
        }
    }
    val verName = pInfo?.versionName ?: "4.5"
    val verCode = @Suppress("DEPRECATION") (pInfo?.versionCode ?: 450)

    val buildInfoText = remember(verName, verCode) {
        """
        Petal Browser Build & System Diagnostics
        ────────────────────────────────────────
        App Name: ${context.getString(R.string.app_name)}
        Package: ${context.packageName}
        Version: $verName
        Build Code: $verCode
        Build Type: ${com.petal.browser.BuildConfig.BUILD_TYPE}
        Web Engine: Mozilla GeckoView 161.0 Beta Quantum
        UI Framework: Jetpack Compose 1.8 / Material 3 Expressive
        Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        Device: ${Build.MANUFACTURER} ${Build.MODEL}
        Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}
        ────────────────────────────────────────
        """.trimIndent()
    }

    val noticesText = remember {
        try {
            context.assets.open("THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Third-party notices could not be loaded: ${e.message}"
        }
    }

    fun copyToClipboard(label: String, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            haptics.play(PetalHapticEngine.Pattern.CLICK)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.about_copied_to_clipboard, label),
                    duration = SnackbarDuration.Short
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openUrl(url: String) {
        try {
            PetalBrowserUtils.intentURL(context, Uri.parse(url))
        } catch (_: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
            putExtra(
                Intent.EXTRA_TEXT,
                "${context.getString(R.string.app_name)} — Fast, private, and customizable browser for Android: ${PetalWebLinks.WEBSITE}"
            )
        }
        try {
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.about_share_app)))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun rateApp() {
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            openUrl("https://github.com/shreyagarwal72/petal/releases")
        }
    }

    fun sendFeedback() {
        val subject = "Petal Browser Feedback [v$verName]"
        val body = """
            Hi Vanshu,

            [Please describe your feedback or issue here]

            ────────────────────────────────────────
            Diagnostic Info:
            App Version: $verName ($verCode)
            Build Type: ${com.petal.browser.BuildConfig.BUILD_TYPE}
            Web Engine: Mozilla GeckoView 161.0 Beta
            Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            ────────────────────────────────────────
        """.trimIndent()
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:hereyourchampion@gmail.com")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            context.startActivity(Intent.createChooser(emailIntent, context.getString(R.string.about_feedback_bug_report)))
        } catch (_: ActivityNotFoundException) {
            val issueUrl = "https://github.com/shreyagarwal72/petal/issues/new?title=" +
                Uri.encode("Feedback: v$verName") +
                "&body=" + Uri.encode(body)
            openUrl(issueUrl)
        }
    }

    val hasSubOverlay = showFuelSheet || showBuildInfoDialog || showNoticesDialog
    if (hasSubOverlay) {
        BackHandler(enabled = true) {
            showFuelSheet = false
            showBuildInfoDialog = false
            showNoticesDialog = false
        }
    }

    PetalPredictiveShell(
        wrap = wrapPredictive,
        enabled = !hasSubOverlay,
        onBack = onBack,
        backgroundSnapshot = backgroundSnapshot
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                PetalSnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(16.dp)
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                PetalGlassHeaderLayout(
                    modifier = Modifier.fillMaxSize(),
                    pageBackground = {
                        M3ExpressiveVariableBackground(
                            modifier = Modifier.fillMaxSize(),
                            pageSeed = "about_developer_page"
                        )
                    },
                    header = { headerModifier, headerBackdrop ->
                        ExpressiveHeader(
                            modifier = headerModifier,
                            backdrop = headerBackdrop,
                            title = stringResource(R.string.about_developer_title),
                            subtitle = stringResource(R.string.about_developer_subtitle),
                            onBack = onBack,
                            actions = {
                                HeaderActionIcon(
                                    icon = Icons.Rounded.Share,
                                    contentDescription = stringResource(R.string.about_share_app),
                                    onClick = { shareApp() }
                                )
                                HeaderActionIcon(
                                    icon = Icons.Rounded.BugReport,
                                    contentDescription = stringResource(R.string.about_feedback_bug_report),
                                    onClick = { sendFeedback() }
                                )
                            }
                        )
                    }
                ) { topInset ->
                    val listState = rememberLazyListState()
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .scrollMotionBlur(listState),
                        flingBehavior = petalFlingBehavior(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = topInset + 12.dp,
                            bottom = 32.dp + navBarBottom
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Section 1: Developer Hero
                        item {
                            PetalDeveloperHeroSection(
                                onOpenUrl = { openUrl(it) }
                            )
                        }

                        // Section 2: Developer Profile Group
                        item {
                            PetalSettingsSection(
                                title = "Developer & Architect",
                                icon = Icons.Rounded.Person,
                                cardId = "about_developer_profile"
                            ) {
                                DeveloperProfileGroup(
                                    onCopyGithub = {
                                        copyToClipboard("GitHub Handle", "@shreyagarwal72")
                                    },
                                    onOpenUrl = { openUrl(it) },
                                    onCopyFocus = {
                                        copyToClipboard(
                                            "Specialization",
                                            "Kotlin • Jetpack Compose • Material 3 Expressive • Android • GeckoView"
                                        )
                                    }
                                )
                            }
                        }

                        // Section 3: Application & Engine
                        item {
                            PetalSettingsSection(
                                title = "Application & Engine",
                                icon = Icons.Rounded.Info,
                                cardId = "about_app_info"
                            ) {
                                AppInfoGroup(
                                    versionName = verName,
                                    versionCode = verCode,
                                    onCopyVersion = { copyToClipboard("Version", "$verName ($verCode)") },
                                    onCheckUpdates = {
                                        if (onNavigateToUpdater != null) {
                                            onNavigateToUpdater()
                                        } else {
                                            try {
                                                val act = context as? ComponentActivity
                                                if (act != null) {
                                                    com.petal.browser.compose.settings.PetalSettingsBridge.createSettingsView(
                                                        activity = act,
                                                        initialCategory = com.petal.browser.compose.settings.SettingsCategory.UPDATER,
                                                        onBackPress = {}
                                                    ).let { cv ->
                                                        act.findViewById<ViewGroup>(android.R.id.content)?.addView(cv)
                                                    }
                                                } else {
                                                    openUrl("https://github.com/shreyagarwal72/petal/releases")
                                                }
                                            } catch (_: Exception) {
                                                openUrl("https://github.com/shreyagarwal72/petal/releases")
                                            }
                                        }
                                    },
                                    onBuildInfoClick = { showBuildInfoDialog = true },
                                    onEngineClick = {
                                        copyToClipboard("Rendering Engine", "Mozilla GeckoView 161.0 Beta Quantum")
                                    },
                                    onUiStackClick = {
                                        copyToClipboard("UI Stack", "Jetpack Compose 1.8 • Material 3 Expressive • Liquid Glass UI")
                                    },
                                    onPrivacyClick = {
                                        openUrl(PetalWebLinks.PRIVACY_POLICY)
                                    }
                                )
                            }
                        }

                        // Section 4: Petal Website
                        item {
                            PetalSettingsSection(
                                title = "Petal Website",
                                icon = Icons.Rounded.Language,
                                cardId = "about_website"
                            ) {
                                PetalWebsiteGroup(onOpenUrl = { openUrl(it) })
                            }
                        }

                        // Section 5: Project & Source Code
                        item {
                            PetalSettingsSection(
                                title = "Project & Source",
                                icon = Icons.Rounded.Code,
                                cardId = "about_repo"
                            ) {
                                ProjectSourceGroup(
                                    onOpenUrl = { openUrl(it) },
                                    onFeedbackClick = { sendFeedback() }
                                )
                            }
                        }

                        // Section 6: Legal & Attributions
                        item {
                            PetalSettingsSection(
                                title = "Legal & Attributions",
                                icon = Icons.Rounded.Gavel,
                                cardId = "about_legal"
                            ) {
                                HelpLegalGroup(
                                    onOpenUrl = { openUrl(it) },
                                    onViewNotices = { showNoticesDialog = true }
                                )
                            }
                        }

                        // Section 7: Ecosystem & Community
                        item {
                            PetalSettingsSection(
                                title = "Ecosystem & Community",
                                icon = Icons.Rounded.Favorite,
                                cardId = "about_community"
                            ) {
                                CommunityEcosystemGroup(
                                    onOpenUrl = { openUrl(it) },
                                    onFuelTheCode = { showFuelSheet = true },
                                    onShareApp = { shareApp() },
                                    onRateApp = { rateApp() }
                                )
                            }
                        }

                        // Section 8: Technologies & Frameworks
                        item {
                            PetalSettingsSection(
                                title = "Technologies & Frameworks",
                                icon = Icons.Rounded.Layers,
                                cardId = "about_tech"
                            ) {
                                CoreTechStackGroup()
                            }
                        }

                        // Section 9: Mission & Philosophy
                        item {
                            PetalSettingsSection(
                                title = "Mission & Philosophy",
                                icon = Icons.Rounded.RocketLaunch,
                                cardId = "about_mission"
                            ) {
                                MissionMetricsGroup(
                                    onOpenUrl = { openUrl(it) },
                                    onCopyMission = {
                                        copyToClipboard(
                                            "Mission Statement",
                                            context.getString(R.string.ui_petal_browser_was_built_to)
                                        )
                                    }
                                )
                            }
                        }

                        // Footer Copyright
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_petal_browser_open_source_project),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.ui_made_with_jetpack_compose_material),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Sub-overlays and dialogs
    if (showFuelSheet) {
        com.petal.browser.account.progress.PetalFuelTheCodeSheet(
            onDismiss = { showFuelSheet = false },
            onMessage = { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    if (showBuildInfoDialog) {
        PetalMaterialAlertDialog(
            onDismissRequest = { showBuildInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.PermDeviceInformation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.about_build_info),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                SelectionContainer {
                    Text(
                        text = buildInfoText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBuildInfoDialog = false }) {
                    Text(stringResource(R.string.about_close))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        copyToClipboard("Build Diagnostics", buildInfoText)
                    }
                ) {
                    Text(stringResource(R.string.about_copy_all))
                }
            }
        )
    }

    if (showNoticesDialog) {
        PetalMaterialAlertDialog(
            onDismissRequest = { showNoticesDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Article,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.about_third_party_notices),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                SelectionContainer {
                    Text(
                        text = noticesText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showNoticesDialog = false }) {
                    Text(stringResource(R.string.about_close))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        copyToClipboard("Third-Party Notices", noticesText)
                    }
                ) {
                    Text(stringResource(R.string.about_copy_all))
                }
            }
        )
    }
}

/**
 * Clean Material 3 Expressive Developer Profile containment group.
 */
@Composable
fun DeveloperProfileGroup(
    onCopyGithub: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyFocus: () -> Unit = {}
) {
    PetalGroup(rowCount = 3, modifier = Modifier.fillMaxWidth()) { index, position ->
        when (index) {
            0 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = onCopyGithub,
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(ExpressiveButtonShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.avatar_developer),
                                contentDescription = "Developer Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.ui_vanshu_agarwal),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "@shreyagarwal72 • Lead Architect & Developer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.about_share_profile),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            1 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = { onOpenUrl("https://github.com/shreyagarwal72") },
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Code,
                            variant = PetalBadgeVariant.PRIMARY
                        )
                    },
                    content = {
                        Text(
                            text = "GitHub Profile",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Explore open source repositories, projects & activity",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            2 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = onCopyFocus,
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.AutoAwesome,
                            variant = PetalBadgeVariant.SECONDARY
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_developer_focus_title),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_developer_focus_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "Copy Specialization",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

/**
 * Application & Engine metadata containment group with version, build info, and update actions.
 */
@Composable
fun AppInfoGroup(
    versionName: String = "4.5",
    versionCode: Int = 450,
    onCopyVersion: () -> Unit,
    onCheckUpdates: () -> Unit = {},
    onBuildInfoClick: () -> Unit = {},
    onEngineClick: () -> Unit = {},
    onUiStackClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {}
) {
    PetalGroup(rowCount = 6, modifier = Modifier.fillMaxWidth()) { index, position ->
        when (index) {
            0 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = onCopyVersion,
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Explore,
                            variant = PetalBadgeVariant.PRIMARY
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Version $versionName (Build $versionCode)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (com.petal.browser.BuildConfig.DEBUG) "Debug" else "Release",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                )
            }
            1 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = onCheckUpdates,
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Update,
                            variant = PetalBadgeVariant.TERTIARY
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_check_updates),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_check_updates_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            2 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = onBuildInfoClick,
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.PermDeviceInformation,
                            variant = PetalBadgeVariant.SECONDARY
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_build_info),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_build_info_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            3 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = onEngineClick,
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Speed,
                            variant = PetalBadgeVariant.TERTIARY
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_gecko_engine_title),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_gecko_engine_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "Copy engine info",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            4 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = onUiStackClick,
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Palette,
                            variant = PetalBadgeVariant.SURFACE_TONAL
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_ui_framework_title),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_ui_framework_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "Copy UI stack info",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            5 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = onPrivacyClick,
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Security,
                            variant = PetalBadgeVariant.SURFACE_TONAL
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_privacy_title),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_privacy_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

/**
 * Clean Project & Source containment group with repository, tracker, feedback, and license links.
 */
@Composable
fun ProjectSourceGroup(
    onOpenUrl: (String) -> Unit,
    onFeedbackClick: () -> Unit = {}
) {
    PetalGroup(rowCount = 5, modifier = Modifier.fillMaxWidth()) { index, position ->
        when (index) {
            0 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = { onOpenUrl("https://github.com/shreyagarwal72/petal") },
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Source,
                            variant = PetalBadgeVariant.PRIMARY
                        )
                    },
                    content = {
                        Text(
                            text = "Source Repository",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "shreyagarwal72/petal • Branch main",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            1 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = { onOpenUrl("https://github.com/shreyagarwal72/petal/issues") },
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.BugReport,
                            variant = PetalBadgeVariant.SECONDARY
                        )
                    },
                    content = {
                        Text(
                            text = "Issue Tracker",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Report bugs, request features & suggest enhancements",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            2 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = onFeedbackClick,
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Mail,
                            variant = PetalBadgeVariant.TERTIARY
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_feedback_bug_report),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_feedback_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            3 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = { onOpenUrl("https://github.com/shreyagarwal72/petal/releases") },
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Download,
                            variant = PetalBadgeVariant.PRIMARY
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_fdroid_releases),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_fdroid_releases_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            4 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = { onOpenUrl("https://github.com/shreyagarwal72/petal/blob/main/LICENSE") },
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Gavel,
                            variant = PetalBadgeVariant.SURFACE_TONAL
                        )
                    },
                    content = {
                        Text(
                            text = "License",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "GNU General Public License v3.0 (GPL-3.0)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

/**
 * Help & Legal containment items.
 */
private data class HelpLegalItem(
    val title: String,
    val subtitle: String,
    val url: String?,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val variant: PetalBadgeVariant,
    val isCustomAction: Boolean = false
)

/** Pages of the Petal website (my-petal-browser.vercel.app) that open inside the app browser. */
@Composable
fun PetalWebsiteGroup(
    onOpenUrl: (String) -> Unit
) {
    val items = remember {
        listOf(
            HelpLegalItem(
                "Petal Website", "Features, screenshots and release news",
                PetalWebLinks.WEBSITE,
                Icons.Rounded.Language, PetalBadgeVariant.PRIMARY
            ),
            HelpLegalItem(
                "Download", "Latest APK and all release channels",
                PetalWebLinks.DOWNLOAD,
                Icons.Rounded.Download, PetalBadgeVariant.PRIMARY
            ),
            HelpLegalItem(
                "Changelog", "Every release, searchable, straight from the site",
                PetalWebLinks.CHANGELOG,
                Icons.Rounded.History, PetalBadgeVariant.SECONDARY
            ),
            HelpLegalItem(
                "FAQ", "Answers to common questions",
                PetalWebLinks.FAQ,
                Icons.Rounded.Help, PetalBadgeVariant.SECONDARY
            ),
            HelpLegalItem(
                "Support & Contact", "Report a bug, request a feature, get help",
                PetalWebLinks.SUPPORT,
                Icons.Rounded.SupportAgent, PetalBadgeVariant.SECONDARY
            )
        )
    }
    PetalGroup(rowCount = items.size, modifier = Modifier.fillMaxWidth()) { index, position ->
        val item = items[index]
        PetalGroupListRow(
            position = position,
            onClick = { item.url?.let { onOpenUrl(it) } },
            leading = {
                PetalGroupIconBadge(
                    icon = item.icon,
                    variant = item.variant
                )
            },
            content = {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailing = {
                Icon(
                    Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

/**
 * Help, Legal & Attributions group: licenses, terms, third-party notices, design & architecture docs.
 */
@Composable
fun HelpLegalGroup(
    onOpenUrl: (String) -> Unit,
    onViewNotices: () -> Unit = {}
) {
    val items = remember {
        listOf(
            HelpLegalItem(
                "Third-Party Notices", "Read open-source licenses and attribution from asset",
                null,
                Icons.Rounded.Article, PetalBadgeVariant.PRIMARY,
                isCustomAction = true
            ),
            HelpLegalItem(
                "Privacy Policy", "How Petal handles and protects your data",
                PetalWebLinks.PRIVACY_POLICY,
                Icons.Rounded.PrivacyTip, PetalBadgeVariant.SURFACE_TONAL
            ),
            HelpLegalItem(
                "Terms of Use", "The rules for using Petal",
                PetalWebLinks.TERMS,
                Icons.Rounded.Description, PetalBadgeVariant.SURFACE_TONAL
            ),
            HelpLegalItem(
                "Open-Source Licenses", "GPL-3.0, GeckoView and third-party credits",
                PetalWebLinks.LICENSES,
                Icons.Rounded.Gavel, PetalBadgeVariant.SURFACE_TONAL
            ),
            HelpLegalItem(
                "GNU GPL v3.0", "Full license text",
                PetalWebLinks.GPL,
                Icons.Rounded.Balance, PetalBadgeVariant.SURFACE_TONAL
            ),
            HelpLegalItem(
                "Code of Conduct", "How the Petal community works together",
                PetalWebLinks.CODE_OF_CONDUCT,
                Icons.Rounded.Handshake, PetalBadgeVariant.SURFACE_TONAL
            ),
            HelpLegalItem(
                "Design System", "Material 3 Expressive and Liquid Glass spec",
                PetalWebLinks.DESIGN,
                Icons.Rounded.Palette, PetalBadgeVariant.SECONDARY
            ),
            HelpLegalItem(
                "Architecture", "How Petal is built on GeckoView",
                PetalWebLinks.ARCHITECTURE,
                Icons.Rounded.Layers, PetalBadgeVariant.SECONDARY
            ),
            HelpLegalItem(
                "Product Requirements", "Roadmap and product goals",
                PetalWebLinks.PRD,
                Icons.Rounded.Article, PetalBadgeVariant.SECONDARY
            )
        )
    }
    PetalGroup(rowCount = items.size, modifier = Modifier.fillMaxWidth()) { index, position ->
        val item = items[index]
        PetalGroupListRow(
            position = position,
            onClick = {
                if (item.isCustomAction) {
                    onViewNotices()
                } else {
                    item.url?.let { onOpenUrl(it) }
                }
            },
            leading = {
                PetalGroupIconBadge(
                    icon = item.icon,
                    variant = item.variant
                )
            },
            content = {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailing = {
                Icon(
                    if (item.isCustomAction) Icons.Rounded.ChevronRight else Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

/**
 * Community & Ecosystem containment group with developer support, community links, sharing, and rating.
 */
@Composable
fun CommunityEcosystemGroup(
    onOpenUrl: (String) -> Unit,
    onFuelTheCode: () -> Unit = {},
    onShareApp: () -> Unit = {},
    onRateApp: () -> Unit = {}
) {
    PetalGroup(rowCount = 5, modifier = Modifier.fillMaxWidth()) { index, position ->
        when (index) {
            0 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = onFuelTheCode,
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.VolunteerActivism,
                            variant = PetalBadgeVariant.TERTIARY
                        )
                    },
                    content = {
                        Text(
                            text = "Support Development",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Send direct payment support via Telegram to @hereyourchampion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            1 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = { onOpenUrl("https://t.me/championworkspace") },
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Send,
                            variant = PetalBadgeVariant.PRIMARY
                        )
                    },
                    content = {
                        Text(
                            text = "Champion Workspace",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Official Telegram community for updates & testing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            2 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = { onOpenUrl("https://github.com/shreyagarwal72?tab=repositories") },
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Apps,
                            variant = PetalBadgeVariant.TERTIARY
                        )
                    },
                    content = {
                        Text(
                            text = "Developer Ecosystem",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "15+ active open-source tools, apps and libraries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            3 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = onShareApp,
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Share,
                            variant = PetalBadgeVariant.SECONDARY
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_share_app),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_share_app_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            4 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = onRateApp,
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Star,
                            variant = PetalBadgeVariant.PRIMARY
                        )
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.about_rate_app),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.about_rate_app_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

/**
 * Technologies & Frameworks containment group.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoreTechStackGroup() {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isGlass) 0.dp else 1.dp,
        border = null,
        modifier = Modifier.fillMaxWidth().liquidGlassChrome(shape, enabled = isGlass)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                M3TechTag(stringResource(R.string.ui_jetpack_compose), isGlass)
                M3TechTag(stringResource(R.string.ui_kotlin_coroutines_flow), isGlass)
                M3TechTag(stringResource(R.string.ui_material_3_expressive), isGlass)
                M3TechTag("Liquid Glass UI", isGlass)
                M3TechTag("GeckoView Quantum", isGlass)
                M3TechTag(stringResource(R.string.ui_native_webview_bridges), isGlass)
                M3TechTag(stringResource(R.string.ui_pixelcopy_gpu_snapshots), isGlass)
                M3TechTag(stringResource(R.string.ui_adblock_rule_engine), isGlass)
                M3TechTag(stringResource(R.string.ui_termux_integration), isGlass)
            }
        }
    }
}

@Composable
private fun M3TechTag(label: String, isGlass: Boolean = false) {
    val tagShape = RoundedCornerShape(12.dp)
    Surface(
        shape = tagShape,
        color = if (isGlass) MaterialTheme.colorScheme.surface.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = null,
        modifier = if (isGlass) Modifier.liquidGlassChrome(tagShape, enabled = true, sheenIntensity = 0.40f) else Modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * Mission & Philosophy containment group with active links and copy actions.
 */
@Composable
fun MissionMetricsGroup(
    onOpenUrl: (String) -> Unit = {},
    onCopyMission: () -> Unit = {}
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val shape = RoundedCornerShape(20.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = shape,
            color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = if (isGlass) 0.dp else 1.dp,
            border = null,
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassChrome(shape, enabled = isGlass)
                .clickable { onCopyMission() }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.ui_petal_browser_was_built_to),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy mission statement",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Tap card to copy mission",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        val metrics = remember {
            listOf(
                Triple(Icons.Rounded.FolderCopy, "15+ Repositories", R.string.ui_active_open_source_repositories_librar),
                Triple(Icons.Rounded.Gavel, "GPL-3.0 License", R.string.ui_free_open_source_redistribute_and),
                Triple(Icons.Rounded.Security, "Zero Telemetry", R.string.ui_100_private_no_trackers_telemetry),
                Triple(Icons.Rounded.DesignServices, "Material 3 + Liquid Glass", R.string.ui_material_3_expressive_design_system)
            )
        }
        val variants = listOf(
            PetalBadgeVariant.PRIMARY,
            PetalBadgeVariant.SECONDARY,
            PetalBadgeVariant.TERTIARY,
            PetalBadgeVariant.SURFACE_TONAL
        )
        PetalGroup(rowCount = metrics.size, modifier = Modifier.fillMaxWidth()) { index, position ->
            val (icon, value, labelRes) = metrics[index]
            val variant = variants[index % variants.size]
            PetalGroupListRow(
                position = position,
                onClick = {
                    when (index) {
                        0 -> onOpenUrl("https://github.com/shreyagarwal72?tab=repositories")
                        1 -> onOpenUrl(PetalWebLinks.GPL)
                        2 -> onOpenUrl(PetalWebLinks.PRIVACY_POLICY)
                        3 -> onOpenUrl(PetalWebLinks.DESIGN)
                    }
                },
                leading = {
                    PetalGroupIconBadge(
                        icon = icon,
                        variant = variant
                    )
                },
                content = {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailing = {
                    Icon(
                        Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}
