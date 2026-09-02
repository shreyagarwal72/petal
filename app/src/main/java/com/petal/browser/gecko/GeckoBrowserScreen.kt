/*
 * GeckoBrowserScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Production-ready Jetpack Compose screen wrapping Mozilla GeckoView with
 * full multi-tab session management, Material 3 Tab Manager grid integration,
 * dynamic tab switching, and crash-proof lifecycle management.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.gecko

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import com.petal.browser.compose.tabs.PetalTabGridSwitcher
import com.petal.browser.ui.components.AnimatedCounterBadge
import org.mozilla.geckoview.GeckoView

// ── Constants ─────────────────────────────────────────────────────────────

private const val DEFAULT_HOME_URL = "https://www.google.com"

/** URLs considered "home" — back from these exits or shows the double-back toast. */
private val HOME_URL_PATTERNS = setOf(
    "about:blank", "about:home", "petal://home", "petal://start"
)

private fun isGeckoHomePage(url: String): Boolean {
    val clean = url.trim().lowercase()
    if (clean.isEmpty() || clean in HOME_URL_PATTERNS) return true
    if (clean.contains("petal_home.html")) return true
    if (clean.startsWith("file:///android_asset/")) return true
    return false
}

// ── Main Screen ───────────────────────────────────────────────────────────

/**
 * Full-screen Gecko-powered browser with robust multi-tab support.
 *
 * @param initialUrl  First URL to load. Defaults to [DEFAULT_HOME_URL].
 * @param onOpenTabs  Optional external callback for opening tabs overview.
 * @param onClose     Invoked when the app should exit (double-back confirmed).
 */
@Composable
fun GeckoBrowserScreen(
    initialUrl: String = DEFAULT_HOME_URL,
    onOpenTabs: (() -> Unit)? = null,
    onClose: () -> Unit = {}
) {
    // ── Runtime guard ─────────────────────────────────────────────────────
    if (!GeckoRuntimeHolder.isInitialized) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    "Gecko Engine not active",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Enable the Gecko Engine toggle in\nSettings → Experimental to use this browser.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    // Initialize or retrieve active tab session
    LaunchedEffect(Unit) {
        GeckoTabManager.ensureInitialTab(initialUrl)
    }

    val activeTab = GeckoTabManager.activeTab ?: remember {
        GeckoTabManager.ensureInitialTab(initialUrl)
    }

    val browserState = activeTab.state
    var showTabSwitcher by remember { mutableStateOf(false) }

    // Ensure active tab session is opened with runtime
    DisposableEffect(activeTab.id) {
        if (GeckoRuntimeHolder.isInitialized) {
            activeTab.openSafely(GeckoRuntimeHolder.runtime)
        }
        onDispose {}
    }

    // Tracks when the user last pressed back — for double-back-to-exit.
    var lastBackPressMs by remember { mutableLongStateOf(0L) }

    // ── Petal-parity back handler ─────────────────────────────────────────
    BackHandler(enabled = true) {
        when {
            showTabSwitcher -> {
                showTabSwitcher = false
            }
            browserState.canGoBack -> {
                activeTab.goBack()
            }
            !isGeckoHomePage(browserState.currentUrl) -> {
                activeTab.stop()
                activeTab.loadUri(DEFAULT_HOME_URL)
            }
            else -> {
                val requireDouble = sp.getBoolean("sp_double_back_exit", true)
                if (!requireDouble) {
                    onClose()
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressMs < 2_000L) {
                        onClose()
                    } else {
                        lastBackPressMs = now
                        Toast.makeText(
                            context,
                            "Press back again to exit Petal",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // ── Tab Switcher Overlay ──────────────────────────────────────────────
    if (showTabSwitcher) {
        PetalTabGridSwitcher(
            tabs = GeckoTabManager.getPetalTabItems(),
            onTabSelect = { selectedItem ->
                GeckoTabManager.selectTab(selectedItem.id)
                showTabSwitcher = false
            },
            onTabClose = { closedItem ->
                GeckoTabManager.closeTab(closedItem.id)
            },
            onNewTab = { isIncognito ->
                GeckoTabManager.createTab(
                    initialUrl = DEFAULT_HOME_URL,
                    isIncognito = isIncognito,
                    selectImmediately = true
                )
                showTabSwitcher = false
            },
            onCloseAllTabs = {
                GeckoTabManager.closeAllTabs(context)
                showTabSwitcher = false
            },
            onBack = {
                showTabSwitcher = false
            }
        )
        return
    }

    // ── UI ────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            GeckoAddressBar(
                url = browserState.currentUrl,
                isSecure = browserState.isSecure,
                isLoading = browserState.isLoading,
                progress = browserState.progress,
                onNavigate = { url -> activeTab.loadUri(url) },
                onRefresh = { activeTab.reload() }
            )
        },
        bottomBar = {
            GeckoBottomNav(
                canGoBack = browserState.canGoBack,
                canGoForward = browserState.canGoForward,
                tabCount = GeckoTabManager.tabs.size,
                onBack = {
                    if (browserState.canGoBack) {
                        activeTab.goBack()
                    }
                },
                onForward = { activeTab.goForward() },
                onHome = { activeTab.loadUri(DEFAULT_HOME_URL) },
                onTabs = {
                    if (onOpenTabs != null) {
                        onOpenTabs()
                    } else {
                        showTabSwitcher = true
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            key(activeTab.id) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        GeckoView(ctx).apply {
                            setSession(activeTab.session)
                        }
                    },
                    update = { view ->
                        try {
                            view.setSession(activeTab.session)
                        } catch (e: Exception) {
                            android.util.Log.e("GeckoBrowserScreen", "Error updating GeckoView session", e)
                        }
                    }
                )
            }
        }
    }
}

// ── Address Bar ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeckoAddressBar(
    url: String,
    isSecure: Boolean,
    isLoading: Boolean,
    progress: Float,
    onNavigate: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var editingUrl by remember { mutableStateOf(false) }
    var inputText by remember(url) { mutableStateOf(url) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column {
        TopAppBar(
            title = {
                if (editingUrl) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                keyboardController?.hide()
                                editingUrl = false
                                onNavigate(normalizeUrl(inputText))
                            }
                        ),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .noRippleClickable { editingUrl = true }
                    ) {
                        Icon(
                            imageVector = if (isSecure) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = if (isSecure) "Secure" else "Not secure",
                            tint = if (isSecure)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = prettyUrl(url),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            actions = {
                if (editingUrl) {
                    IconButton(onClick = {
                        editingUrl = false
                        inputText = url
                        keyboardController?.hide()
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                    }
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = if (isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                            contentDescription = if (isLoading) "Stop" else "Refresh"
                        )
                    }
                }
            }
        )

        AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
        }
    }
}

// ── Bottom Navigation ─────────────────────────────────────────────────────

@Composable
private fun GeckoBottomNav(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = false,
            onClick = onBack,
            enabled = canGoBack,
            icon = { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") },
            label = { Text("Back") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onForward,
            enabled = canGoForward,
            icon = { Icon(Icons.Rounded.ArrowForward, contentDescription = "Forward") },
            label = { Text("Forward") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onHome,
            icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onTabs,
            icon = {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tab, contentDescription = "Tabs")
                    AnimatedCounterBadge(count = tabCount, modifier = Modifier.padding(start = 14.dp, bottom = 14.dp))
                }
            },
            label = { Text("Tabs") }
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────

/** Prefixes bare domains with https://, sends plain queries to DuckDuckGo. */
private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    return if (trimmed.contains(".") && !trimmed.contains(" ")) {
        "https://$trimmed"
    } else {
        "https://duckduckgo.com/?q=${trimmed.replace(" ", "+")}"
    }
}

/** Strips scheme and trailing slash for a compact address-bar display. */
private fun prettyUrl(url: String): String =
    url.removePrefix("https://").removePrefix("http://").trimEnd('/').ifEmpty { url }

/** Click without ripple — used for the tappable address-bar display row. */
@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}
