package com.petal.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.R
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.unit.BrowserUnit
import kotlinx.coroutines.launch

/**
 * Java Interop Bridge to present the Material 3 Expressive "About Developer" sheet.
 */
object PetalAboutDeveloperBridge {
    @JvmStatic
    @JvmOverloads
    fun show(activity: ComponentActivity, onDismiss: Runnable? = null) {
        try {
            val dialog = BottomSheetDialog(activity)
            dialog.behavior.isDraggable = false
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            dialog.window?.let { window ->
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.setWindowAnimations(0)
            }
            dialog.setOnShowListener {
                try {
                    val container = dialog.findViewById<android.view.View>(com.google.android.material.R.id.container)
                    container?.let { root ->
                        root.fitsSystemWindows = false
                        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets -> insets }
                    }

                    val coordinator = dialog.findViewById<android.view.View>(com.google.android.material.R.id.coordinator)
                    coordinator?.let { root ->
                        root.fitsSystemWindows = false
                        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets -> insets }
                    }

                    val bottomSheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
                    bottomSheet?.let { sheet ->
                        sheet.fitsSystemWindows = false
                        sheet.background = null
                        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sheet) { _, insets -> insets }

                        val behavior = BottomSheetBehavior.from(sheet)
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                        behavior.skipCollapsed = true
                        behavior.isDraggable = false
                        sheet.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
            com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewTreeOnBackPressedDispatcherOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent {
                    val snapshotBitmap = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }
                    val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                    val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                    val isAmoled = sp.getBoolean("sp_amoled", false)
                    val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                    val appFont = remember(fontName) { com.petal.browser.ui.theme.AppFont.fromName(fontName) }
                    val colorStyle = remember(styleName) {
                        try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (_: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                    }

                    PetalExpressiveTheme(
                        dynamicColor = dynamicColor,
                        useAmoled = isAmoled,
                        appFont = appFont,
                        colorStyle = colorStyle,
                        paletteId = paletteId
                    ) {
                        PetalAboutDeveloperSheetContent(
                            backgroundSnapshot = snapshotBitmap,
                            onClose = {
                                try { dialog.dismiss() } catch (_: Exception) {}
                                onDismiss?.run()
                            }
                        )
                    }
                }
            }
            dialog.setOnDismissListener {
                com.petal.browser.predictive.PetalContentSnapshot.clear()
            }
            dialog.setContentView(composeView)
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Full-screen Material 3 Expressive About Developer UI layout.
 */
@Composable
fun PetalAboutDeveloperSheetContent(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val primaryColor = MaterialTheme.colorScheme.primary

    fun copyToClipboard(label: String, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Copied $label to clipboard", duration = SnackbarDuration.Short)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openUrl(url: String) {
        try {
            val activity = context as? com.petal.browser.activity.BrowserActivity
            if (activity != null) {
                onClose()
                val ctrl = activity.currentAlbumController
                if (ctrl is com.petal.browser.view.PetalGeckoView) {
                    ctrl.loadUrl(url)
                    activity.showAlbum(ctrl, url)
                } else {
                    activity.addAlbum(null, url, true)
                }
            } else {
                BrowserUnit.intentURL(context, Uri.parse(url))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = onClose
    ) {
        com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                Box(
                    modifier = modifier.fillMaxSize().padding(innerPadding)
                ) {
                    M3ExpressiveVariableBackground(pageSeed = "about_developer_page")

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        ExpressiveHeader(
                            title = stringResource(R.string.title_about),
                            subtitle = "App version, licenses, GitHub & developer",
                            onBack = onClose,
                            actions = {
                                HeaderActionIcon(
                                    icon = Icons.Rounded.Share,
                                    contentDescription = "Share Profile",
                                    onClick = {
                                        copyToClipboard("Developer Profile Link", "https://github.com/shreyagarwal72")
                                    }
                                )
                            }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Section 1: Developer Profile & Identification
                            PetalSettingsSection(
                                title = "Developer",
                                icon = Icons.Rounded.Person,
                                cardId = "about_developer_spotlight"
                            ) {
                                DeveloperProfileGroup(
                                    onCopyGithub = { copyToClipboard("GitHub URL", "https://github.com/shreyagarwal72") },
                                    onOpenUrl = { openUrl(it) }
                                )
                            }

                            // Section 2: Application Details & Environment
                            val pInfo = remember {
                                try {
                                    context.packageManager.getPackageInfo(context.packageName, 0)
                                } catch (_: Throwable) {
                                    null
                                }
                            }
                            val verName = pInfo?.versionName ?: "3.9"
                            val verCode = @Suppress("DEPRECATION") (pInfo?.versionCode ?: 390)
                            PetalSettingsSection(
                                title = "Application & Engine",
                                icon = Icons.Rounded.Info,
                                cardId = "about_app_info"
                            ) {
                                AppInfoGroup(
                                    versionName = verName,
                                    versionCode = verCode,
                                    onCopyVersion = { copyToClipboard("Version", "$verName ($verCode)") }
                                )
                            }

                            // Section 3: Project & Source Code
                            PetalSettingsSection(
                                title = "Project & Source",
                                icon = Icons.Rounded.Code,
                                cardId = "about_repo"
                            ) {
                                ProjectSourceGroup(
                                    onOpenUrl = { openUrl(it) }
                                )
                            }

                            // Section 4: Ecosystem & Community
                            PetalSettingsSection(
                                title = "Ecosystem & Community",
                                icon = Icons.Rounded.Favorite,
                                cardId = "about_community"
                            ) {
                                CommunityEcosystemGroup(
                                    onOpenUrl = { openUrl(it) }
                                )
                            }

                            // Section 5: Technologies & Frameworks
                            PetalSettingsSection(
                                title = "Technologies & Frameworks",
                                icon = Icons.Rounded.Layers,
                                cardId = "about_tech"
                            ) {
                                CoreTechStackGroup()
                            }

                            // Section 6: Mission & Philosophy
                            PetalSettingsSection(
                                title = "Mission & Philosophy",
                                icon = Icons.Rounded.RocketLaunch,
                                cardId = "about_mission"
                            ) {
                                MissionMetricsGroup()
                            }

                            // Footer Copyright
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            Spacer(Modifier.height(32.dp))
                        }
                    }

                    PetalThemedSnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        actionColor = primaryColor
                    )
                }
            }
        }
    }
}

/**
 * Clean Material 3 Expressive Developer Profile containment group.
 */
@Composable
fun DeveloperProfileGroup(
    onCopyGithub: () -> Unit,
    onOpenUrl: (String) -> Unit
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
                                .clip(CircleShape)
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
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "Copy GitHub handle",
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
                            variant = com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                    onClick = {},
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.AutoAwesome,
                            variant = com.petal.browser.ui.containment.PetalBadgeVariant.SECONDARY
                        )
                    },
                    content = {
                        Text(
                            text = "Focus & Specialization",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Kotlin • Jetpack Compose • Material 3 Expressive • Termux",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

/**
 * Clean Application metadata containment group.
 */
@Composable
fun AppInfoGroup(
    versionName: String = "3.9",
    versionCode: Int = 390,
    onCopyVersion: () -> Unit
) {
    PetalGroup(rowCount = 3, modifier = Modifier.fillMaxWidth()) { index, position ->
        when (index) {
            0 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = onCopyVersion,
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Explore,
                            variant = com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY
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
                                text = "Release",
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
                    onClick = {},
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Speed,
                            variant = com.petal.browser.ui.containment.PetalBadgeVariant.TERTIARY
                        )
                    },
                    content = {
                        Text(
                            text = "Web Rendering Engine",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Mozilla GeckoView Quantum (Full Web Standards & Add-ons)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
            2 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = {},
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Security,
                            variant = com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
                        )
                    },
                    content = {
                        Text(
                            text = "Privacy Architecture",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Zero Telemetry • Isolated Profiles • AdBlock & Tracking Protection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    }
}

/**
 * Clean Project & Source containment group.
 */
@Composable
fun ProjectSourceGroup(
    onOpenUrl: (String) -> Unit
) {
    PetalGroup(rowCount = 3, modifier = Modifier.fillMaxWidth()) { index, position ->
        when (index) {
            0 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = { onOpenUrl("https://github.com/shreyagarwal72/petal") },
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Source,
                            variant = com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY
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
                            variant = com.petal.browser.ui.containment.PetalBadgeVariant.SECONDARY
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
                    onClick = { onOpenUrl("https://github.com/shreyagarwal72/petal/blob/main/LICENSE") },
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Gavel,
                            variant = com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
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
 * Clean Community & Ecosystem containment group.
 */
@Composable
fun CommunityEcosystemGroup(
    onOpenUrl: (String) -> Unit
) {
    PetalGroup(rowCount = 2, modifier = Modifier.fillMaxWidth()) { index, position ->
        when (index) {
            0 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = { onOpenUrl("https://t.me/championworkspace") },
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Send,
                            variant = com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY
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
            1 -> {
                PetalGroupListRow(
                    position = position,
                    onClick = { onOpenUrl("https://github.com/shreyagarwal72?tab=repositories") },
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.Apps,
                            variant = com.petal.browser.ui.containment.PetalBadgeVariant.TERTIARY
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
        }
    }
}

/**
 * Technologies & Frameworks containment group.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoreTechStackGroup() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
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
                M3TechTag(stringResource(R.string.ui_jetpack_compose))
                M3TechTag(stringResource(R.string.ui_kotlin_coroutines_flow))
                M3TechTag(stringResource(R.string.ui_material_3_expressive))
                M3TechTag("GeckoView Quantum")
                M3TechTag(stringResource(R.string.ui_native_webview_bridges))
                M3TechTag(stringResource(R.string.ui_pixelcopy_gpu_snapshots))
                M3TechTag(stringResource(R.string.ui_adblock_rule_engine))
                M3TechTag(stringResource(R.string.ui_termux_integration))
            }
        }
    }
}

@Composable
private fun M3TechTag(label: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
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
 * Mission & Philosophy containment group.
 */
@Composable
fun MissionMetricsGroup() {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.ui_petal_browser_was_built_to),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }

        val metrics = remember {
            listOf(
                Triple(Icons.Rounded.FolderCopy, "15+ Repositories", R.string.ui_active_open_source_repositories_librar),
                Triple(Icons.Rounded.Gavel, "GPL-3.0 License", R.string.ui_free_open_source_redistribute_and),
                Triple(Icons.Rounded.Security, "Zero Telemetry", R.string.ui_100_private_no_trackers_telemetry),
                Triple(Icons.Rounded.DesignServices, "100% Material 3", R.string.ui_material_3_expressive_design_system)
            )
        }
        val variants = listOf(
            com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY,
            com.petal.browser.ui.containment.PetalBadgeVariant.SECONDARY,
            com.petal.browser.ui.containment.PetalBadgeVariant.TERTIARY,
            com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
        )
        PetalGroup(rowCount = metrics.size, modifier = Modifier.fillMaxWidth()) { index, position ->
            val (icon, value, labelRes) = metrics[index]
            val variant = variants[index % variants.size]
            PetalGroupListRow(
                position = position,
                onClick = {},
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}
