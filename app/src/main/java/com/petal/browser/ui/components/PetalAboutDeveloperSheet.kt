package com.petal.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalStatusHeroCard
import com.petal.browser.ui.containment.petalGroupShape
import com.petal.browser.ui.containment.rememberPetalGroupPressScale

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

                        // ── Petal Repository Overview Card ─────────────────────
                        PetalRepoDetailsCard(
                            onOpenUrl = { url ->
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
                        )

                        // ── Developer Ecosystem & Projects Showcase ─────────────
                        DeveloperEcosystemCard(
                            onOpenUrl = { url ->
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
                        )

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
    } // PetalScreenWrapper
    } // PetalPredictiveBackSurface

/**
 * High-performance animated profile container featuring:
 * - Fluid rotating dual-orbital aura with radial gradient illumination
 * - 12-sided squircle / cookie morphing perimeter with subtle breathing scale
 * - Counter-rotation so the inner developer avatar remains perfectly upright
 * - Interactive tap to toggle between Developer Avatar and Petal Logo with spring rotation
 * - Long-click haptic pulse with custom feedback
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PetalAnimatedProfileContainer(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 100.dp,
    onLongClick: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var showPetalLogo by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "petal_avatar_transition")
    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val flipRotation by animateFloatAsState(
        targetValue = if (showPetalLogo) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "flip_rotation"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val containerColor = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = modifier
            .size(size + 24.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.35f),
                            tertiaryColor.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = Offset(this.size.width / 2f, this.size.height / 2f),
                        radius = this.size.width * 0.72f
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer rotating orbital aura ring
        val cookie12Shape = remember { com.petal.browser.ui.theme.PetalMaterialShapes.Cookie12Sided.toShape() }
        Box(
            modifier = Modifier
                .size(size + 14.dp)
                .graphicsLayer {
                    rotationZ = orbitRotation
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .clip(cookie12Shape)
                .background(
                    Brush.sweepGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.65f),
                            tertiaryColor.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f),
                            primaryColor.copy(alpha = 0.65f)
                        )
                    )
                )
        )

        // Core avatar container with counter-rotation and flip interaction
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    rotationZ = flipRotation
                }
                .clip(cookie12Shape)
                .background(containerColor)
                .border(
                    BorderStroke(2.5.dp, MaterialTheme.colorScheme.surface),
                    cookie12Shape
                )
                .combinedClickable(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showPetalLogo = !showPetalLogo
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (flipRotation <= 90f) {
                // Front: Clean Developer Avatar Image
                Image(
                    painter = painterResource(id = R.drawable.avatar_developer),
                    contentDescription = "Developer Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Back: Petal Browser Brand Identity Icon
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Explore,
                        contentDescription = "Petal Browser",
                        tint = primaryColor,
                        modifier = Modifier.size(size * 0.55f)
                    )
                }
            }
        }
    }
}

/** Developer Hero Profile Card with animated profile container, bio, and stats. */
@Composable
fun DeveloperHeroCard(
    onCopyGithub: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerBg = MaterialTheme.colorScheme.surfaceContainerLow

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerBg,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Avatar Container with flip and orbital aura
            PetalAnimatedProfileContainer(
                size = 96.dp,
                onLongClick = onCopyGithub
            )

            Spacer(Modifier.height(14.dp))

            // Developer Name & Handle
            Text(
                text = stringResource(R.string.ui_vanshu_agarwal),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(3.dp))
            Surface(
                onClick = onCopyGithub,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.ui_shreyagarwal72),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = primaryColor
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Bio
            Text(
                text = stringResource(R.string.ui_developer_bio),
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
                ExpressivePillChip(icon = Icons.Rounded.Code, label = stringResource(R.string.ui_kotlin))
                ExpressivePillChip(icon = Icons.Rounded.AutoAwesome, label = stringResource(R.string.ui_m3_expressive))
                ExpressivePillChip(icon = Icons.Rounded.Terminal, label = stringResource(R.string.ui_termux))
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
                PetalGroupIconBadge(
                    icon = Icons.Rounded.RocketLaunch,
                    variant = com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY,
                    size = 40.dp,
                    iconSize = 22.dp
                )

                Text(
                    text = stringResource(R.string.ui_the_petal_mission),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = stringResource(R.string.ui_petal_browser_was_built_to),
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
            com.petal.browser.ui.containment.PetalGroupListRow(
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
                PetalGroupIconBadge(
                    icon = Icons.Rounded.Layers,
                    variant = com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY,
                    size = 40.dp,
                    iconSize = 22.dp
                )

                Text(
                    text = stringResource(R.string.ui_core_tech_stack),
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
                TechChip(label = stringResource(R.string.ui_jetpack_compose))
                TechChip(label = stringResource(R.string.ui_kotlin_coroutines_flow))
                TechChip(label = stringResource(R.string.ui_material_3_expressive))
                TechChip(label = stringResource(R.string.ui_native_webview_bridges))
                TechChip(label = stringResource(R.string.ui_pixelcopy_gpu_snapshots))
                TechChip(label = stringResource(R.string.ui_adblock_rule_engine))
                TechChip(label = stringResource(R.string.ui_termux_integration))
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
                text = stringResource(R.string.ui_community_connect),
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
                    Text(stringResource(R.string.ui_github), fontWeight = FontWeight.Bold, maxLines = 1)
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
                    Text(stringResource(R.string.ui_source), fontWeight = FontWeight.Bold, maxLines = 1)
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
                    Text(stringResource(R.string.ui_telegram), maxLines = 1)
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
                    Text(stringResource(R.string.ui_issues), maxLines = 1)
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
                Text(stringResource(R.string.ui_open_source_credits_developers), fontWeight = FontWeight.Bold, maxLines = 1)
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
                Text(stringResource(R.string.ui_export_diagnostic_logs_zip), fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/**
 * Interactive Ecosystem Showcase displaying other open-source apps & tools by the developer.
 */
@Composable
fun DeveloperEcosystemCard(
    onOpenUrl: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PetalGroupIconBadge(
                        icon = Icons.Rounded.Apps,
                        variant = com.petal.browser.ui.containment.PetalBadgeVariant.TERTIARY,
                        size = 40.dp,
                        iconSize = 22.dp
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.ui_developer_ecosystem),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ui_developer_ecosystem_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Toggle ecosystem"
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EcosystemItemRow(
                        name = "Petal Browser",
                        description = "Ultra-fast private web browser with GeckoView Quantum & Material 3 Expressive UI",
                        badge = "Flagship",
                        icon = Icons.Rounded.Public,
                        position = com.petal.browser.ui.containment.PetalGroupPosition.TOP,
                        onClick = { onOpenUrl("https://github.com/shreyagarwal72/petal") }
                    )
                    EcosystemItemRow(
                        name = "Champion Workspace",
                        description = "Official Telegram community for announcements, builds, feedback & testing",
                        badge = "Community",
                        icon = Icons.Rounded.Send,
                        position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                        onClick = { onOpenUrl("https://t.me/championworkspace") }
                    )
                    EcosystemItemRow(
                        name = "Developer GitHub Hub",
                        description = "Explore all repositories, libraries, scripts, and open source projects",
                        badge = "15+ Repos",
                        icon = Icons.Rounded.Code,
                        position = com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM,
                        onClick = { onOpenUrl("https://github.com/shreyagarwal72") }
                    )
                }
            }
        }
    }
}

@Composable
private fun EcosystemItemRow(
    name: String,
    description: String,
    badge: String,
    icon: ImageVector,
    position: com.petal.browser.ui.containment.PetalGroupPosition,
    onClick: () -> Unit
) {
    com.petal.browser.ui.containment.PetalGroupListRow(
        position = position,
        onClick = onClick,
        leading = {
            PetalGroupIconBadge(
                icon = icon,
                variant = com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY,
                size = 40.dp,
                iconSize = 20.dp
            )
        },
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailing = {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    )
}

/**
 * Interactive GitHub Repository card with live stats, branch, license & link.
 */
@Composable
fun PetalRepoDetailsCard(
    onOpenUrl: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PetalGroupIconBadge(
                        icon = Icons.Rounded.Source,
                        variant = com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY,
                        size = 40.dp,
                        iconSize = 22.dp
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.ui_repository_overview),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "shreyagarwal72/petal",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "main",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Repo stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = { onOpenUrl("https://github.com/shreyagarwal72/petal/stargazers") },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                        Column {
                            Text("Star Repo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("GitHub", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Surface(
                    onClick = { onOpenUrl("https://github.com/shreyagarwal72/petal/network/members") },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.ForkRight, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Column {
                            Text("Fork & Build", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Open Source", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // Direct GitHub action button
            OutlinedButton(
                onClick = { onOpenUrl("https://github.com/shreyagarwal72/petal") },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ui_view_on_github), fontWeight = FontWeight.SemiBold)
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
        title = "FilePipe & Remember",
        developer = "Bikram Agarwal (bikram-agarwal)",
        role = "Floating Navigation Bar & Progressive Blur Architecture",
        description = "Fluid pill-shaped floating navigation bar, haptic spring interaction animations, and progressive frosted-glass blur underlay.",
        url = "https://github.com/bikram-agarwal/FilePipe",
        icon = Icons.Rounded.BlurOn,
        containerColor = Color(0xFF00897B),
        tags = listOf("Floating Navbar", "Progressive Blur", "Frosted Glass")
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
    ),
    AppCreditItem(
        title = "mpvEx",
        developer = "marlboro-advance",
        role = "Video Player UI/UX & Wavy Seekbar Architecture",
        description = "Full-featured modern media player interface inspiration, squiggly wavy seekbar mechanics, and dynamic PiP layout geometry.",
        url = "https://github.com/marlboro-advance/mpvEx",
        icon = Icons.Rounded.PlayCircle,
        containerColor = Color(0xFFE91E63),
        tags = listOf("Video Player", "Wavy Seekbar", "Compose UI", "PiP Mode")
    ),
    AppCreditItem(
        title = "Duo-animation (Apple Duo)",
        developer = "Atomicx7",
        role = "iPhone Duo Fold 3D Gyro Motion & Frosted Glass Shader",
        description = "Physics-based device tilt motion model, AGSL runtime shader with Vogel-disk blur and 3D folding perspective transformation.",
        url = "https://github.com/Atomicx7/Duo-animation",
        icon = Icons.Rounded.Animation,
        containerColor = Color(0xFF3F51B5),
        tags = listOf("Apple Duo", "AGSL Shader", "Motion Model", "Gyroscope")
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
            setViewTreeOnBackPressedDispatcherOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }
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
            addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: android.view.View) {}
                override fun onViewDetachedFromWindow(v: android.view.View) {
                    removeOnAttachStateChangeListener(this)
                    com.petal.browser.predictive.PetalContentSnapshot.clear()
                }
            })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PetalCreditsSheetContent(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var expandedTitle by remember { mutableStateOf<String?>(null) }

    val filteredCredits = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            petalAppCredits
        } else {
            val q = searchQuery.trim().lowercase()
            petalAppCredits.filter {
                it.title.lowercase().contains(q) ||
                it.developer.lowercase().contains(q) ||
                it.role.lowercase().contains(q) ||
                it.tags.any { tag -> tag.lowercase().contains(q) }
            }
        }
    }

    fun openCredit(url: String) {
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
                    modifier = modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    M3ExpressiveVariableBackground(pageSeed = "credits_page")

                    Column(modifier = Modifier.fillMaxSize()) {
                        ExpressiveHeader(
                            title = "Open Source Credits",
                            subtitle = "Standing on the shoulders of giants",
                            onBack = onClose,
                            enableLiquidGlass = true
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            PetalStatusHeroCard(
                                title = stringResource(R.string.ui_gratitude_attribution),
                                subtitle = stringResource(R.string.ui_petal_browser_is_crafted_upon),
                                statusText = "${petalAppCredits.size} projects credited",
                                icon = Icons.Rounded.Favorite
                            )

                            CreditsSearchField(
                                query = searchQuery,
                                onQueryChange = {
                                    searchQuery = it
                                    expandedTitle = null
                                }
                            )

                            PetalSettingsSection(
                                title = if (searchQuery.isBlank()) "Projects & contributors" else "${filteredCredits.size} matching",
                                icon = Icons.Rounded.Code
                            ) {
                                if (filteredCredits.isEmpty()) {
                                    PetalHeroCard {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            PetalGroupIconBadge(
                                                Icons.Rounded.SearchOff,
                                                container = MaterialTheme.colorScheme.secondaryContainer,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                size = 56.dp,
                                                iconSize = 28.dp
                                            )
                                            Text(
                                                text = stringResource(R.string.ui_no_matching_credits_found),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    PetalGroup(
                                        rowCount = filteredCredits.size,
                                        modifier = Modifier.animateContentSize(
                                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f)
                                        )
                                    ) { index, position ->
                                        val credit = filteredCredits[index]
                                        CreditGroupCard(
                                            credit = credit,
                                            position = position,
                                            expanded = expandedTitle == credit.title,
                                            onToggle = {
                                                expandedTitle = if (expandedTitle == credit.title) null else credit.title
                                            },
                                            onOpen = { openCredit(credit.url) }
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_all_project_names_trademarks_and),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Stadium-shaped search container matching the omnibox / AI search styling. */
@Composable
private fun CreditsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
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
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        stringResource(R.string.ui_search_contributors_or_technologies),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = true,
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
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.ui_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * One credited project as a containment group row. Collapsed it shows the project and its
 * author; tapping expands the role, description, tags and an "open project" action.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreditGroupCard(
    credit: AppCreditItem,
    position: PetalGroupPosition,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressScale = rememberPetalGroupPressScale(source)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "creditChevron"
    )

    Card(
        onClick = onToggle,
        shape = petalGroupShape(position),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = source,
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PetalGroupIconBadge(
                    credit.icon,
                    container = credit.containerColor.copy(alpha = 0.16f),
                    tint = credit.containerColor
                )

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = credit.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.ui_by, credit.developer),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(spring(stiffness = 500f)) + expandVertically(),
                exit = fadeOut(spring(stiffness = 700f)) + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = credit.role,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Text(
                        text = credit.description,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        credit.tags.forEach { tag ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = onOpen,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_open_project), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
