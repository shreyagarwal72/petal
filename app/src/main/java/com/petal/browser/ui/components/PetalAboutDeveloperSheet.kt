package com.petal.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
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
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val snapshotBitmap = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }
                    DisposableEffect(Unit) {
                        onDispose {
                            com.petal.browser.predictive.PetalContentSnapshot.clear()
                        }
                    }
                    val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                    val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                    val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                    val isAmoled = sp.getBoolean("sp_amoled", false)

                    val appFont = remember(fontName) {
                        com.petal.browser.ui.theme.AppFont.fromName(fontName)
                    }
                    val colorStyle = remember(styleName) {
                        try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
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
    val secondaryColor = MaterialTheme.colorScheme.tertiary

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

    androidx.activity.compose.BackHandler(enabled = true, onBack = onClose)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            M3ExpressiveVariableBackground(pageSeed = "about_developer_page")

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                ExpressiveHeader(
                    title = "About Developer",
                    subtitle = "Crafted with ❤ for Android & Termux",
                    onBack = onClose,
                    enableLiquidGlass = true,
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
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // ── Developer Hero Profile Card ─────────────────────────
                            DeveloperHeroCard(
                                onCopyGithub = { copyToClipboard("GitHub URL", "https://github.com/shreyagarwal72") }
                            )

                        // ── Petal Browser Philosophy & Mission Card ─────────────
                        DeveloperMissionCard()

                        // ── Expressive Metric Badges Grid ───────────────────────
                        DeveloperMetricsGrid()

                        // ── Developer Tech Stack Chips ──────────────────────────
                        DeveloperTechStackCard()

                        // ── Community Links & Action Group ──────────────────────
                        DeveloperActionsCard(
                            onOpenUrl = { url ->
                                try {
                                    if (url == "petal://credits") {
                                        (context as? ComponentActivity)?.let { act ->
                                            onClose()
                                            PetalCreditsBridge.show(act) {
                                                PetalAboutDeveloperBridge.show(act)
                                            }
                                        }
                                    } else {
                                        val activity = context as? com.petal.browser.activity.BrowserActivity
                                        if (activity != null && activity.ninjaWebView != null) {
                                            onClose()
                                            activity.ninjaWebView.loadUrl(url)
                                            activity.showAlbum(activity.currentAlbumController, url)
                                        } else {
                                            BrowserUnit.intentURL(context, Uri.parse(url))
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        )

                        // ── Footer Copyright & Build Hash ────────────────────────
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Petal Browser • Open Source Project",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Made with Jetpack Compose & Material 3 Expressive UI",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Floating Material 3 Toast / Snackbar Host
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

/** Developer Hero Profile Card with glowing radial avatar ring and bio chips. */
@Composable
fun DeveloperHeroCard(
    onCopyGithub: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val containerBg = MaterialTheme.colorScheme.surfaceContainerLow

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerBg,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onCopyGithub)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Glowing Avatar Badge Container
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.40f),
                                    tertiaryColor.copy(alpha = 0.15f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.width * 0.75f
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(2.5.dp, primaryColor.copy(alpha = 0.8f)),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "VA",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 28.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Developer Name & Handle
            Text(
                text = "Vanshu Agarwal",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "@shreyagarwal72",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = primaryColor,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            // Short Executive Bio
            Text(
                text = "Lead Android & Systems Developer crafting high-performance browsers, native tools, and expressive UI experiences for Android & Termux.",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // Expressive Specialty Chips Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ExpressivePillChip(icon = Icons.Rounded.Code, label = "Kotlin")
                ExpressivePillChip(icon = Icons.Rounded.AutoAwesome, label = "M3 Expressive")
                ExpressivePillChip(icon = Icons.Rounded.Terminal, label = "Termux")
            }
        }
    }
}

/** Expressive Project Mission Card describing Petal Browser's architectural vision. */
@Composable
fun DeveloperMissionCard() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.RocketLaunch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "The Petal Mission",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Petal Browser was built to prove that an Android web browser can combine uncompromising speed, complete user privacy, and fluid Material 3 Expressive motion physics without corporate telemetry or heavy bloat.",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Horizontal Metric Highlights below Petal Mission. */
@Composable
fun DeveloperMetricsGrid() {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        MetricBadgeCard(
            icon = Icons.Rounded.FolderCopy,
            value = "15+ Repositories",
            label = "Active Open Source Repositories & Libraries"
        )
        MetricBadgeCard(
            icon = Icons.Rounded.Gavel,
            value = "GPL-3.0 License",
            label = "Free & Open Source — Redistribute and Modify Freely"
        )
        MetricBadgeCard(
            icon = Icons.Rounded.Security,
            value = "Zero Telemetry",
            label = "100% Private — No Trackers, Telemetry, or Analytics"
        )
        MetricBadgeCard(
            icon = Icons.Rounded.DesignServices,
            value = "100% Material 3",
            label = "Material 3 Expressive Design System & Dynamic Palettes"
        )
    }
}

@Composable
private fun MetricBadgeCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Developer Tech Stack Chip Grid. */
@Composable
fun DeveloperTechStackCard() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "Core Tech Stack",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TechChip(label = "Jetpack Compose")
                TechChip(label = "Kotlin Coroutines & Flow")
                TechChip(label = "Material 3 Expressive")
                TechChip(label = "Native WebView Bridges")
                TechChip(label = "PixelCopy GPU Snapshots")
                TechChip(label = "AdBlock Rule Engine")
                TechChip(label = "Termux Integration")
            }
        }
    }
}

@Composable
private fun TechChip(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** Action buttons for GitHub, Source Code, Telegram, and Bug Reports. */
@Composable
fun DeveloperActionsCard(
    onOpenUrl: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Community & Connect",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onOpenUrl("https://github.com/shreyagarwal72/") },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("GitHub", fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = { onOpenUrl("https://github.com/shreyagarwal72/petal/") },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Source", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { onOpenUrl("https://t.me/championworkspace") },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Telegram", maxLines = 1)
                }

                OutlinedButton(
                    onClick = { onOpenUrl("https://github.com/shreyagarwal72/petal/issues") },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Issues", maxLines = 1)
                }
            }

            // ── Dedicated Credits Button ────────────────────────────────────
            Button(
                onClick = { onOpenUrl("petal://credits") },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Source Credits & Developers", fontWeight = FontWeight.Bold, maxLines = 1)
            }

            // ── Diagnostic Logs Export Button ──────────────────────────────
            val context = LocalContext.current
            FilledTonalButton(
                onClick = { com.petal.browser.logger.PetalAppLogger.shareLogsZip(context) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export Diagnostic Logs (.zip)", fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ExpressivePillChip(
    icon: ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ── Dedicated Open Source Credits & Developers Architecture ──────────────────
// ─────────────────────────────────────────────────────────────────────────────

data class AppCreditItem(
    val title: String,
    val developer: String,
    val role: String,
    val description: String,
    val url: String,
    val icon: ImageVector,
    val containerColor: Color,
    val tags: List<String>
)

val petalAppCredits = listOf(
    AppCreditItem(
        title = "FOSS Browser",
        developer = "scoute-dich",
        role = "Core Browser Engine & Android Architecture",
        description = "Fully open source, lightweight web browser built on Android WebKit with clean navigation controls.",
        url = "https://github.com/scoute-dich/browser",
        icon = Icons.Rounded.Public,
        containerColor = Color(0xFF4285F4),
        tags = listOf("Browser Base", "GPL-3.0", "Core Engine")
    ),
    AppCreditItem(
        title = "Zenith",
        developer = "1372Slash",
        role = "Material 3 Expressive Design & Digital Wellbeing",
        description = "Smart digital wellbeing assistant built with Material Design 3 Expressive, proactive intervention, and fluid motion.",
        url = "https://github.com/1372Slash/Zenith",
        icon = Icons.Rounded.AutoAwesome,
        containerColor = Color(0xFF6750A4),
        tags = listOf("Material 3 Expressive", "Wellbeing", "Motion Rich")
    ),
    AppCreditItem(
        title = "LastWave",
        developer = "duxtami",
        role = "Hi-Res Audio Streaming & Dynamic Discovery",
        description = "High-resolution lossless music streaming and player with real-time synced lyrics and smart discovery.",
        url = "https://github.com/duxtami/LastWave-native",
        icon = Icons.Rounded.PlayArrow,
        containerColor = Color(0xFF00E5FF),
        tags = listOf("Lossless Audio", "Streaming", "Material 3")
    ),
    AppCreditItem(
        title = "Aurora Store",
        developer = "whyorean (Rahul Patel)",
        role = "Material Design Patterns & Architecture",
        description = "Privacy-respecting Google Play client demonstrating exquisite Material 3 design and robust app architecture.",
        url = "https://github.com/whyorean/AuroraStore",
        icon = Icons.Rounded.Android,
        containerColor = Color(0xFF00C853),
        tags = listOf("Material 3", "App Architecture", "Open Source")
    ),
    AppCreditItem(
        title = "RvSystem-Monitor",
        developer = "Rve27",
        role = "System Monitor & Hardware Insights",
        description = "High-performance system monitoring solution for Android merging Jetpack Compose with raw efficiency.",
        url = "https://github.com/Rve27/RvSystem-Monitor",
        icon = Icons.Rounded.AutoAwesome,
        containerColor = Color(0xFF9C27B0),
        tags = listOf("System Monitor", "Hardware Insights", "Compose UI")
    ),
    AppCreditItem(
        title = "Ever-Haptics",
        developer = "hari161008",
        role = "Tactile Scroll & Interaction Haptics",
        description = "Ultra-responsive high-fidelity waveform vibration synthesis for page scrolling, switches, and tactile feedback.",
        url = "https://github.com/hari161008/Ever-Haptics",
        icon = Icons.Rounded.Vibration,
        containerColor = Color(0xFF00BCD4),
        tags = listOf("Haptic Engine", "Waveforms", "Feedback")
    ),
    AppCreditItem(
        title = "PixelPlayer",
        developer = "PixelPlayerHQ",
        role = "Dynamic Palette & Squircle Motion Framework",
        description = "Dynamic multi-style color palette system, 4-quadrant morphing squircle swatches, and expressive media controls.",
        url = "https://github.com/PixelPlayerHQ/PixelPlayer",
        icon = Icons.Rounded.Palette,
        containerColor = Color(0xFFFF6D00),
        tags = listOf("Dynamic Color", "Squircle Motion", "Expressive Theme")
    ),
    AppCreditItem(
        title = "Fetch / Android Fetch2",
        developer = "tonyofrancis",
        role = "Multi-threaded Download Engine",
        description = "High-performance background download orchestration with pause, resume, progress streaming, and retry policies.",
        url = "https://github.com/tonyofrancis/Fetch",
        icon = Icons.Rounded.Download,
        containerColor = Color(0xFFFBBC05),
        tags = listOf("Downloads", "Multi-thread", "Resumable")
    ),
    AppCreditItem(
        title = "Coil Image Loader",
        developer = "coil-kt",
        role = "Asynchronous Favicon & Image Rendering",
        description = "Coroutines-first image loading pipeline for instant favicon caching, site icons, and fluid thumbnail displays.",
        url = "https://github.com/coil-kt/coil",
        icon = Icons.Rounded.Image,
        containerColor = Color(0xFFEA4335),
        tags = listOf("Image Loader", "Kotlin Coroutines", "Memory Cache")
    ),
    AppCreditItem(
        title = "Material 3 Expressive",
        developer = "Google Android Jetpack Team",
        role = "Design Language & Dynamic Shape Morphing",
        description = "Next-generation Material 3 Expressive components, 35 dynamic polygon shapes, and ColorStyle palette generators.",
        url = "https://m3.material.io",
        icon = Icons.Rounded.ColorLens,
        containerColor = Color(0xFF6750A4),
        tags = listOf("Material 3", "Compose UI", "Expressive Shapes")
    )
)

object PetalCreditsBridge {
    @JvmStatic
    @JvmOverloads
    fun show(activity: ComponentActivity, onDismiss: Runnable? = null) {
        val browserActivity = activity as? com.petal.browser.activity.BrowserActivity
        if (browserActivity != null) {
            browserActivity.showCreditsScreen(onDismiss)
            return
        }
        try {
            val creditsView = createCreditsView(activity) {
                onDismiss?.run()
            }
            activity.setContentView(creditsView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun createCreditsView(
        activity: ComponentActivity,
        onBackPress: () -> Unit
    ): ComposeView {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }
                DisposableEffect(Unit) {
                    onDispose {
                        com.petal.browser.predictive.PetalContentSnapshot.clear()
                    }
                }
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                var currentPaletteId by remember { mutableStateOf(sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId) }
                var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                var isExpressiveColors by remember { mutableStateOf(sp.getBoolean("sp_expressive_colors", false)) }
                var useDynamic by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }
                var fontName by remember { mutableStateOf(sp.getString("sp_app_font", "PETAL") ?: "PETAL") }
                var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }
                var fontWidthVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 92f)) }
                var fontWeightVal by remember { mutableIntStateOf(sp.getInt("sp_font_weight", 750)) }
                var fontRoundnessVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 100f)) }

                DisposableEffect(sp) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            "sp_palette_id" -> currentPaletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                            "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                            "sp_expressive_colors" -> isExpressiveColors = sp.getBoolean("sp_expressive_colors", false)
                            "useDynamicColor" -> useDynamic = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                            "sp_app_font" -> fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                            "sp_color_style" -> styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                            "sp_font_width" -> fontWidthVal = sp.getFloat("sp_font_width", 92f)
                            "sp_font_weight" -> fontWeightVal = sp.getInt("sp_font_weight", 750)
                            "sp_font_roundness" -> fontRoundnessVal = sp.getFloat("sp_font_roundness", 100f)
                        }
                    }
                    sp.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                val appFont = remember(fontName) {
                    com.petal.browser.ui.theme.AppFont.fromName(fontName)
                }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    paletteId = currentPaletteId,
                    useAmoled = isAmoled,
                    dynamicColor = useDynamic,
                    expressiveColors = isExpressiveColors,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    fontWidth = fontWidthVal,
                    fontWeight = fontWeightVal,
                    fontRoundness = fontRoundnessVal
                ) {
                    PetalCreditsSheetContent(
                        backgroundSnapshot = snapshotBitmap,
                        onClose = onBackPress
                    )
                }
            }
        }
    }
}

@Composable
fun PetalCreditsSheetContent(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = onClose
    ) {
        com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                Box(
                    modifier = modifier.fillMaxSize()
                ) {
                    M3ExpressiveVariableBackground(pageSeed = "credits_page")

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        ExpressiveHeader(
                            title = "Open Source Credits",
                            subtitle = "Standing on the Shoulders of Giants",
                            onBack = onClose,
                            enableLiquidGlass = true
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Intro Mission Card
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                tonalElevation = 2.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Favorite,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Gratitude & Attribution",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "Petal Browser is built upon phenomenal open source projects, libraries, and design frameworks created by visionary developers across the globe. We gratefully acknowledge and credit their outstanding work.",
                                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Credit List Cards
                            petalAppCredits.forEachIndexed { index, credit ->
                                CreditCardItem(
                                    credit = credit,
                                    onClick = {
                                        try {
                                            val activity = context as? com.petal.browser.activity.BrowserActivity
                                            if (activity != null) {
                                                onClose()
                                                activity.ninjaWebView?.let { wv ->
                                                    wv.loadUrl(credit.url)
                                                    activity.showAlbum(activity.currentAlbumController, credit.url)
                                                } ?: run {
                                                    BrowserUnit.intentURL(context, Uri.parse(credit.url))
                                                }
                                            } else {
                                                BrowserUnit.intentURL(context, Uri.parse(credit.url))
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                )
                            }

                            // Footer
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "All trademarks and open-source licenses belong to their respective owners.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreditCardItem(
    credit: AppCreditItem,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = credit.containerColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = credit.icon,
                            contentDescription = null,
                            tint = credit.containerColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = credit.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "by ${credit.developer}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = "Open Project",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = credit.role,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            Text(
                text = credit.description,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                credit.tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

