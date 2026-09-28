package com.petal.browser.customtabs

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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.view.PetalToast
import com.petal.browser.browser.PetalTabViewController

/**
 * PetalCustomTabActivity
 * ─────────────────────────────────────────────────────────────────────────
 * Standalone Activity handling incoming Custom Tabs [Intent.ACTION_VIEW] intents,
 * implemented to Mozilla Firefox Fenix (`feature-customtabs`) specification.
 *
 * Capabilities:
 *  - CustomTabsIntent client extras (toolbar color tint, close button bitmap).
 *  - Real-time progress indicator using Material 3 Expressive linear wavy bar.
 *  - Dynamic security status (HTTPS lock vs unencrypted alert).
 *  - Strict Enhanced Tracking Protection (ETP) isolation honoring user preferences.
 *  - Overflow action menu: Share, Copy link, Desktop site toggle, and Open in Petal Browser.
 *  - Seamless session and tab handoff to [BrowserActivity].
 */
class PetalCustomTabActivity : ComponentActivity() {

    private var tabSurface: PetalTabViewController? = null
    private var customTabId: String? = null
    private var currentUrlState by mutableStateOf("about:blank")
    private var currentTitleState by mutableStateOf("")
    private var isSecureState by mutableStateOf(false)
    private var progressState by mutableStateOf(0)
    private var isDesktopModeState by mutableStateOf(false)

    // CustomTabsIntent customizations
    private var customToolbarColor: Color? = null
    private var customCloseButtonBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            PetalExpressiveTheme {
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
                        AndroidView(
                            factory = { ctx ->
                                val tabId = "tab_custom_${System.currentTimeMillis()}"
                                val sessionPair = com.petal.browser.engine.gecko.PetalEngineStore.createTabSession(
                                    context = ctx,
                                    tabId = tabId,
                                    url = targetUrl.ifBlank { "about:blank" },
                                    title = "",
                                    isIncognito = false,
                                    select = true
                                )
                                PetalTabViewController(ctx).also { surface ->
                                    customTabId = tabId
                                    surface.bindTab(sessionPair.first)
                                    surface.attachLifecycle(this@PetalCustomTabActivity)
                                    surface.onBrowserStateChanged = { state ->
                                        currentUrlState = state.url
                                        currentTitleState = state.title
                                        progressState = state.progress
                                        isSecureState = state.isSecure
                                    }
                                    val customTabEtpEnabled = PreferenceManager.getDefaultSharedPreferences(ctx)
                                        .getBoolean("sp_custom_tabs_etp", true)
                                    if (customTabEtpEnabled) surface.setTrackingProtection(true)
                                    surface.layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    tabSurface = surface
                                    if (targetUrl.isNotBlank() && !targetUrl.equals("about:blank", ignoreCase = true)) {
                                        surface.loadUrl(targetUrl)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
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
        super.onResume()
        updateCurrentUrlAndTitle()
    }

    override fun onPause() {
        super.onPause()
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
            ?: tabSurface?.getUrl() ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, urlToShare)
            putExtra(Intent.EXTRA_SUBJECT, currentTitleState.ifBlank { urlToShare })
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.pref_custom_tabs_share)))
    }

    private fun copyCurrentUrl() {
        val urlToCopy = currentUrlState.takeIf { it.isNotBlank() && !it.equals("about:blank", ignoreCase = true) }
            ?: tabSurface?.getUrl() ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("URL", urlToCopy)
        clipboard?.setPrimaryClip(clip)
        PetalToast.show(this, getString(R.string.pref_custom_tabs_copied))
    }

    private fun openInPetalBrowser() {
        val urlToOpen = tabSurface?.getUrl()?.takeIf { it.isNotBlank() && !it.equals("about:blank", ignoreCase = true) }
            ?: currentUrlState.takeIf { it.isNotBlank() && !it.equals("about:blank", ignoreCase = true) }
            ?: "about:blank"

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)).apply {
            component = ComponentName(this@PetalCustomTabActivity, BrowserActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(browserIntent)
        finish()
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

    val barColor = customBgColor ?: MaterialTheme.colorScheme.surfaceContainerHighest

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Surface(
            color = barColor,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    if (customCloseIcon != null) {
                        Image(
                            bitmap = customCloseIcon.asImageBitmap(),
                            contentDescription = "Close custom tab",
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close custom tab",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (title.isNotBlank() && !title.equals("about:blank", ignoreCase = true)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSecure) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Secure connection",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        } else if (!url.equals("about:blank", ignoreCase = true)) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = "Insecure connection",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = displayHost,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                // Overflow Actions Menu
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    com.petal.browser.ui.containment.PetalPopupMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        com.petal.browser.ui.containment.PetalPopupMenuItem(
                            text = { Text(text = "Share link", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        com.petal.browser.ui.containment.PetalPopupMenuItem(
                            text = { Text(text = "Copy link", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                menuExpanded = false
                                onCopyUrl()
                            }
                        )
                        com.petal.browser.ui.containment.PetalPopupMenuItem(
                            text = {
                                Text(
                                    text = if (isDesktopMode) "Mobile site" else "Desktop site",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
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
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        com.petal.browser.ui.containment.PetalPopupMenuItem(
                            text = {
                                Text(
                                    text = "Open in Petal Browser",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onOpenInPetal()
                            }
                        )
                    }
                }
            }
        }

        // Animated Material 3 Loading Progress Bar
        AnimatedVisibility(
            visible = progress in 1..99,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        }
    }
}
