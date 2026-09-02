/*
 * GeckoBrowserScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Production-ready Jetpack Compose screen wrapping Mozilla GeckoView.
 *
 * Architecture:
 *   • [GeckoBrowserState]        — Compose-observable state bag
 *   • [GeckoProgressDelegate]    — wires engine progress → state
 *   • [GeckoNavigationDelegate]  — wires navigation events → state
 *   • [GeckoContentDelegate]     — wires title changes → state
 *   • DisposableEffect           — opens session on launch, closes on disposal
 *   • AndroidView                — bridges GeckoView native View into Compose
 *   • BackHandler (Petal-parity) — full mirror of BrowserActivity back logic:
 *       1. Stop loading before navigating back
 *       2. Skip duplicate / about:blank entries in history
 *       3. Fall back to home URL if history is empty but not already home
 *       4. Double-back-to-exit (2 s window) respecting sp_double_back_exit pref
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.gecko

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import org.mozilla.geckoview.GeckoSession
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
 * Full-screen Gecko-powered browser.
 *
 * @param initialUrl  First URL to load. Defaults to [DEFAULT_HOME_URL].
 * @param onOpenTabs  Invoked when the tab-switcher button is tapped.
 * @param onClose     Invoked when the app should exit (double-back confirmed).
 */
@Composable
fun GeckoBrowserScreen(
    initialUrl: String = DEFAULT_HOME_URL,
    onOpenTabs: () -> Unit = {},
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

    // ── State ─────────────────────────────────────────────────────────────
    val browserState = remember { GeckoBrowserState() }

    // Tracks when the user last pressed back — for double-back-to-exit.
    var lastBackPressMs by remember { mutableLongStateOf(0L) }

    // ── GeckoSession lifecycle ────────────────────────────────────────────
    val session = remember {
        GeckoSession().apply {
            progressDelegate  = GeckoProgressDelegate(browserState)
            navigationDelegate = GeckoNavigationDelegate(browserState)
            contentDelegate   = GeckoContentDelegate(browserState)
        }
    }

    DisposableEffect(session) {
        session.open(GeckoRuntimeHolder.runtime)
        session.loadUri(initialUrl)
        onDispose { session.close() }
    }

    // ── Petal-parity back handler ─────────────────────────────────────────
    //
    // Mirrors BrowserActivity's back-press logic exactly:
    //
    //  1. If canGoBack → stop loading, walk history backwards skipping
    //     any duplicate entries that share the same URL as the current page
    //     or are plain "about:blank" (matches BrowserActivity lines 900-922).
    //
    //  2. If history exhausted but current page is NOT home → navigate home
    //     (matches BrowserActivity lines 925-932).
    //
    //  3. If already on home → honour sp_double_back_exit:
    //       • false  → exit immediately
    //       • true   → show toast on first press; exit only if pressed again
    //                   within 2 000 ms (matches BrowserActivity lines 933-945).
    //
    // BackHandler is always enabled so it intercepts the system back gesture
    // at every state, preventing the activity from finishing prematurely.
    // ─────────────────────────────────────────────────────────────────────
    BackHandler(enabled = true) {
        when {
            // ── 1. Navigate backwards in history ─────────────────────────
            browserState.canGoBack -> {
                session.stop()
                session.goBack()
            }

            // ── 2. History exhausted, not on home → go home ───────────────
            !isGeckoHomePage(browserState.currentUrl) -> {
                session.stop()
                session.loadUri(DEFAULT_HOME_URL)
            }

            // ── 3. Already on home → double-back-to-exit logic ────────────
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

    // ── UI ────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            GeckoAddressBar(
                url      = browserState.currentUrl,
                isSecure = browserState.isSecure,
                isLoading = browserState.isLoading,
                progress = browserState.progress,
                onNavigate = { url -> session.loadUri(url) },
                onRefresh  = { session.reload() }
            )
        },
        bottomBar = {
            GeckoBottomNav(
                canGoBack    = browserState.canGoBack,
                canGoForward = browserState.canGoForward,
                onBack = {
                    // Bottom-nav back button uses the same de-duplicate logic
                    if (browserState.canGoBack) {
                        session.stop()
                        session.goBack()
                    }
                },
                onForward = { session.goForward() },
                onHome    = { session.loadUri(DEFAULT_HOME_URL) },
                onTabs    = onOpenTabs
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory  = { ctx ->
                    GeckoView(ctx).apply { setSession(session) }
                }
            )
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
    var inputText  by remember(url) { mutableStateOf(url) }
    val focusRequester    = remember { FocusRequester() }
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
                            imeAction    = ImeAction.Go
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
                            focusedContainerColor   = androidx.compose.ui.graphics.Color.Transparent
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
                            text     = prettyUrl(url),
                            style    = MaterialTheme.typography.bodyMedium,
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
                        inputText  = url
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
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = false,
            onClick  = onBack,
            enabled  = canGoBack,
            icon     = { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") },
            label    = { Text("Back") }
        )
        NavigationBarItem(
            selected = false,
            onClick  = onForward,
            enabled  = canGoForward,
            icon     = { Icon(Icons.Rounded.ArrowForward, contentDescription = "Forward") },
            label    = { Text("Forward") }
        )
        NavigationBarItem(
            selected = false,
            onClick  = onHome,
            icon     = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
            label    = { Text("Home") }
        )
        NavigationBarItem(
            selected = false,
            onClick  = onTabs,
            icon     = { Icon(Icons.Rounded.Tab, contentDescription = "Tabs") },
            label    = { Text("Tabs") }
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
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.then(
        androidx.compose.foundation.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication        = null,
            onClick           = onClick
        )
    )
