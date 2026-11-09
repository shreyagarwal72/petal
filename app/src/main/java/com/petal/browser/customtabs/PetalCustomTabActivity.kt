package com.petal.browser.customtabs

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.browser.PetalTabViewController
import com.petal.browser.compose.composable.PetalRefreshBarState
import com.petal.browser.compose.composable.RefreshBarLoadingIndicatorLazy
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.view.PetalToast

/**
 * PetalCustomTabActivity
 * ─────────────────────────────────────────────────────────────────────────
 * Standalone Activity handling incoming Custom Tabs [Intent.ACTION_VIEW] intents,
 * implemented to Mozilla Firefox Fenix (`feature-customtabs`) specification with
 * full Material 3 Expressive UI and live Liquid Glass support.
 */
class PetalCustomTabActivity : ComponentActivity() {

    private var tabSurface: PetalTabViewController? = null
    private var customTabId: String? = null
    private var currentUrlState by mutableStateOf("about:blank")
    private var currentTitleState by mutableStateOf("")
    private var isSecureState by mutableStateOf(false)
    private var progressState by mutableStateOf(0)
    private var isDesktopModeState by mutableStateOf(false)

    // Pull-to-refresh state matching MainActivity's PetalRefreshBarState
    private val refreshState = PetalRefreshBarState()

    // CustomTabsIntent customizations
    private var customToolbarColor: Color? = null
    private var customCloseButtonBitmap: Bitmap? = null
    private var isIncognitoCustomTab: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.petal.browser.unit.PetalAndroidUtils.initTheme(this)

        parseCustomTabsIntentExtras(intent)

        val targetUrl = extractUrlFromIntent(intent)
        currentUrlState = targetUrl
        isSecureState = targetUrl.startsWith("https://", ignoreCase = true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val gv = tabSurface
                if (gv == null) finish() else gv.processBackPressed { finish() }
            }
        })

        setContent {
            val sp = remember { PreferenceManager.getDefaultSharedPreferences(this@PetalCustomTabActivity) }
            val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
            val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
            val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
            val isAmoled = sp.getBoolean("sp_amoled", false)
            val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

            val appFont = remember(fontName) { AppFont.fromName(fontName) }
            val colorStyle = remember(styleName) {
                try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT }
            }

            PetalExpressiveTheme(
                dynamicColor = dynamicColor,
                useAmoled = isAmoled,
                appFont = appFont,
                colorStyle = colorStyle,
                paletteId = paletteId
            ) {
                Scaffold(
                    topBar = {
                        CustomTabTopBar(
                            url = currentUrlState,
                            title = currentTitleState,
                            isSecure = isSecureState,
                            progress = progressState,
                            isDesktopMode = isDesktopModeState,
                            customBgColor = customToolbarColor,
                            customCloseIcon = customCloseButtonBitmap,
                            onClose = { finish() },
                            onToggleDesktop = {
                                val newState = !isDesktopModeState
                                isDesktopModeState = newState
                                tabSurface?.setDesktopMode(newState)
                            },
                            onReload = {
                                tabSurface?.reload()
                            },
                            onShare = { shareCurrentUrl() },
                            onCopyUrl = { copyCurrentUrl() },
                            onOpenInPetal = { openInPetalBrowser() }
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // PetalTabViewController is a SwipeRefreshLayout driven by Mozilla's
                        // SwipeRefreshFeature, so it supplies pull-to-refresh and its spinner itself.
                        AndroidView(
                            factory = { ctx ->
                                FrameLayout(ctx).also { p2r ->
                                    val tabId = "tab_custom_${System.currentTimeMillis()}"
                                    val sessionPair = com.petal.browser.engine.gecko.PetalEngineStore.createTabSession(
                                        context = ctx,
                                        tabId = tabId,
                                        url = targetUrl.ifBlank { "about:blank" },
                                        title = "",
                                        isIncognito = isIncognitoCustomTab,
                                        select = true
                                    )
                                    val surface = PetalTabViewController(ctx).also { sfc ->
                                        customTabId = tabId
                                        sfc.bindTab(sessionPair.first)
                                        sfc.attachLifecycle(this@PetalCustomTabActivity)
                                        sfc.activate()
                                        com.petal.browser.engine.gecko.PetalEngineStore.selectTab(ctx, tabId)
                                        sfc.onBrowserStateChanged = { state ->
                                            currentUrlState = state.url
                                            currentTitleState = state.title
                                            progressState = state.progress
                                            isSecureState = state.isSecure
                                            if (state.progress >= 100 || !state.loading) {
                                                refreshState.isRefreshing = false
                                                refreshState.pullProgress = 0f
                                            }
                                        }
                                        sfc.onPullProgress = { p ->
                                            refreshState.pullProgress = p
                                        }
                                        sfc.onRefreshStateChanged = { r ->
                                            refreshState.isRefreshing = r
                                            if (!r) refreshState.pullProgress = 0f
                                        }
                                        val customTabEtpEnabled = PreferenceManager.getDefaultSharedPreferences(ctx)
                                            .getBoolean("sp_custom_tabs_etp", true)
                                        if (customTabEtpEnabled) sfc.setTrackingProtection(true)
                                        sfc.layoutParams = FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        tabSurface = sfc
                                        if (targetUrl.isNotBlank() && !targetUrl.equals("about:blank", ignoreCase = true)) {
                                            sfc.loadUrl(targetUrl)
                                        }
                                    }
                                    p2r.addView(surface)
                                    p2r.layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        RefreshBarLoadingIndicatorLazy(
                            isRefreshing = refreshState.isRefreshing,
                            pullProgressProvider = { refreshState.pullProgress },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseCustomTabsIntentExtras(intent)
        val targetUrl = extractUrlFromIntent(intent)
        if (targetUrl.isNotBlank() && !targetUrl.equals("about:blank", ignoreCase = true)) {
            currentUrlState = targetUrl
            isSecureState = targetUrl.startsWith("https://", ignoreCase = true)
            tabSurface?.loadUrl(targetUrl)
        }
    }

    override fun onResume() {
        com.petal.browser.engine.gecko.PetalGeckoRuntime.setForegroundActivity(this)
        super.onResume()
        updateCurrentUrlAndTitle()
    }

    override fun onPause() {
        if (com.petal.browser.engine.gecko.PetalGeckoRuntime.getForegroundActivity() === this) {
            com.petal.browser.engine.gecko.PetalGeckoRuntime.setForegroundActivity(null)
        }
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (com.petal.browser.engine.gecko.PetalGeckoRuntime.handleActivityResult(requestCode, resultCode, data)) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        tabSurface?.destroy()
        tabSurface = null
        customTabId?.let { com.petal.browser.engine.gecko.PetalEngineStore.removeTab(this, it) }
        customTabId = null
        super.onDestroy()
    }

    private fun parseCustomTabsIntentExtras(intent: Intent?) {
        if (intent == null) return
        try {
            isIncognitoCustomTab = intent.getBooleanExtra("androidx.browser.customtabs.extra.INCOGNITO", false)
                || intent.getBooleanExtra("org.mozilla.fenix.EXTRA_IS_PRIVATE", false)
                || intent.getBooleanExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", false)
            if (intent.hasExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR)) {
                val colorInt = intent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0)
                if (colorInt != 0) {
                    customToolbarColor = Color(colorInt)
                }
            }
            if (intent.hasExtra(CustomTabsIntent.EXTRA_CLOSE_BUTTON_ICON)) {
                @Suppress("DEPRECATION")
                customCloseButtonBitmap = intent.getParcelableExtra<Bitmap>(CustomTabsIntent.EXTRA_CLOSE_BUTTON_ICON)
            }
        } catch (_: Throwable) {}
    }

    private fun extractUrlFromIntent(intent: Intent?): String {
        if (intent == null) return "about:blank"
        val dataUri = intent.data
        if (dataUri != null) {
            val uriStr = dataUri.toString()
            if (uriStr.isNotBlank()) return uriStr
        }
        val extraUrl = intent.getStringExtra(CustomTabsIntent.EXTRA_SESSION)
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra("url")
        return extraUrl?.takeIf { it.isNotBlank() } ?: "about:blank"
    }

    private fun updateCurrentUrlAndTitle() {
        val state = tabSurface?.currentState() ?: return
        val url = state.url
        if (url.isNotBlank()) {
            currentUrlState = url
            isSecureState = state.isSecure
        }
        val title = state.title
        if (title.isNotBlank()) {
            currentTitleState = title
        }
    }

    private fun shareCurrentUrl() {
        val urlToShare = currentUrlState.takeIf { it.isNotBlank() && !it.equals("about:blank", ignoreCase = true) }
            ?: tabSurface?.url ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, urlToShare)
            putExtra(Intent.EXTRA_SUBJECT, currentTitleState.ifBlank { urlToShare })
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.pref_custom_tabs_share)))
    }

    private fun copyCurrentUrl() {
        val urlToCopy = currentUrlState.takeIf { it.isNotBlank() && !it.equals("about:blank", ignoreCase = true) }
            ?: tabSurface?.url ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("URL", urlToCopy)
        clipboard?.setPrimaryClip(clip)
        PetalToast.show(this, getString(R.string.pref_custom_tabs_copied))
    }

    private fun openInPetalBrowser() {
        val urlToOpen = tabSurface?.url?.takeIf { it.isNotBlank() && !it.equals("about:blank", ignoreCase = true) }
            ?: currentUrlState.takeIf { it.isNotBlank() && !it.equals("about:blank", ignoreCase = true) }
            ?: "about:blank"

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)).apply {
            component = ComponentName(this@PetalCustomTabActivity, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(browserIntent)
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        com.petal.browser.ui.components.PetalRuntimePermissionBridge.handlePermissionsResult(
            requestCode,
            permissions,
            grantResults
        )
    }

    fun showFileChooser(
        filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>?,
        fileChooserParams: android.webkit.WebChromeClient.FileChooserParams?
    ) {
        com.petal.browser.compose.file.PetalUploadChooser.show(
            this,
            filePathCallback,
            fileChooserParams
        ) {
            android.util.Log.e("PetalCustomTabActivity", "Petal pickers failed to open")
        }
    }
}

@Composable
private fun CustomTabTopBar(
    url: String,
    title: String,
    isSecure: Boolean,
    progress: Int,
    isDesktopMode: Boolean,
    customBgColor: Color?,
    customCloseIcon: Bitmap?,
    onClose: () -> Unit,
    onToggleDesktop: () -> Unit,
    onReload: () -> Unit,
    onShare: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenInPetal: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val displayHost = remember(url) {
        try {
            val parsed = Uri.parse(url)
            parsed.host ?: url
        } catch (_: Exception) {
            url
        }
    }

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_toolbars")
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    val topBarShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

    val barColor = if (isGlass) {
        petalGroupSurfaceColor()
    } else {
        customBgColor ?: MaterialTheme.colorScheme.surfaceContainerLow
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = if (isGlass) 10.dp else 0.dp, vertical = if (isGlass) 6.dp else 0.dp)
    ) {
        Surface(
            color = barColor,
            shape = if (isGlass) topBarShape else RectangleShape,
            tonalElevation = if (isGlass) 0.dp else 2.dp,
            shadowElevation = if (isGlass) 0.dp else 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isGlass) {
                        Modifier.liquidGlassChrome(
                            shape = topBarShape,
                            enabled = true,
                            sheenIntensity = sheen,
                            pressFx = false
                        )
                    } else Modifier
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ── Left: Navigation Close Button ──────────────────────
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(42.dp)
                    ) {
                        if (customCloseIcon != null) {
                            Image(
                                bitmap = customCloseIcon.asImageBitmap(),
                                contentDescription = stringResource(R.string.ui_close_custom_tab),
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.ui_close_custom_tab),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    // ── Center: Title & Domain Column (Full flexible width) ──
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        val displayTitle = if (title.isNotBlank() && !title.equals("about:blank", ignoreCase = true)) {
                            title
                        } else {
                            displayHost
                        }

                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(2.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            when {
                                isSecure -> Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = stringResource(R.string.ui_secure_connection),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                !url.equals("about:blank", ignoreCase = true) -> Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = stringResource(R.string.ui_insecure_connection),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                text = displayHost,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    // ── Right: Compact "Open in Petal Browser" Action ─────────
                    FilledTonalIconButton(
                        onClick = onOpenInPetal,
                        shape = ExpressiveButtonShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = stringResource(R.string.ui_open_in_petal_browser),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    // ── Overflow Action Menu ────────────────────────────────
                    val menuRotation by animateFloatAsState(
                        targetValue = if (menuExpanded) 180f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "cct_menu_rot"
                    )

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(38.dp)
                                .graphicsLayer { rotationZ = menuRotation }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.ui_more_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        com.petal.browser.ui.containment.PetalPopupMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            com.petal.browser.ui.containment.PetalPopupMenuItem(
                                text = stringResource(R.string.ui_open_in_petal_browser),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onOpenInPetal()
                                }
                            )
                            com.petal.browser.ui.containment.PetalMenuDivider()
                            com.petal.browser.ui.containment.PetalPopupMenuItem(
                                text = "Reload page",
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onReload()
                                }
                            )
                            com.petal.browser.ui.containment.PetalPopupMenuItem(
                                text = stringResource(R.string.ui_share_link),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onShare()
                                }
                            )
                            com.petal.browser.ui.containment.PetalPopupMenuItem(
                                text = stringResource(R.string.ui_copy_link),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onCopyUrl()
                                }
                            )
                            com.petal.browser.ui.containment.PetalMenuDivider()
                            com.petal.browser.ui.containment.PetalPopupMenuItem(
                                text = if (isDesktopMode) "Mobile site" else "Desktop site",
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isDesktopMode) Icons.Rounded.PhoneAndroid else Icons.Rounded.DesktopWindows,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleDesktop()
                                }
                            )
                        }
                    }
                }

                // ── Integrated Browser-style Smooth Loading Bar ────────────
                val animatedProgress by animateFloatAsState(
                    targetValue = (progress.coerceIn(0, 100)) / 100f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "cctProgress"
                )

                AnimatedVisibility(
                    visible = progress in 1..99,
                    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    }
}
