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
 *   • BackHandler                — delegates Android back to session.goBack()
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.gecko

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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

// ── Default home URL ──────────────────────────────────────────────────────

private const val DEFAULT_HOME_URL = "https://www.google.com"

// ── Main Screen ───────────────────────────────────────────────────────────

/**
 * Full-screen browser powered by GeckoView.
 *
 * @param initialUrl    The first URL to load. Defaults to [DEFAULT_HOME_URL].
 * @param onOpenTabs    Invoked when the user taps the tab switcher button.
 * @param onClose       Invoked when the back stack is exhausted and the
 *                      screen should be dismissed.
 */
@Composable
fun GeckoBrowserScreen(
    initialUrl: String = DEFAULT_HOME_URL,
    onOpenTabs: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    // Guard — show a fallback if the user hasn't enabled Gecko in Settings yet
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    // ── State ─────────────────────────────────────────────────────────────
    val browserState = remember { GeckoBrowserState() }

    // ── GeckoSession lifecycle ────────────────────────────────────────────
    val session = remember {
        GeckoSession().apply {
            progressDelegate = GeckoProgressDelegate(browserState)
            navigationDelegate = GeckoNavigationDelegate(browserState)
            contentDelegate = GeckoContentDelegate(browserState)
        }
    }

    // Open the session against the shared runtime on first composition;
    // close it when this composable leaves the tree.
    DisposableEffect(session) {
        val runtime = GeckoRuntimeHolder.runtime
        session.open(runtime)
        session.loadUri(initialUrl)
        onDispose {
            session.close()
        }
    }

    // ── Back handler ──────────────────────────────────────────────────────
    BackHandler(enabled = browserState.canGoBack) {
        session.goBack()
    }

    // ── UI ────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            GeckoAddressBar(
                url = browserState.currentUrl,
                isSecure = browserState.isSecure,
                isLoading = browserState.isLoading,
                progress = browserState.progress,
                onNavigate = { url -> session.loadUri(url) },
                onRefresh = { session.reload() }
            )
        },
        bottomBar = {
            GeckoBottomNav(
                canGoBack = browserState.canGoBack,
                canGoForward = browserState.canGoForward,
                onBack = { session.goBack() },
                onForward = { session.goForward() },
                onHome = { session.loadUri(DEFAULT_HOME_URL) },
                onTabs = onOpenTabs
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── GeckoView native view ────────────────────────────────────
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    GeckoView(ctx).apply {
                        setSession(session)
                    }
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
                                val target = normalizeUrl(inputText)
                                onNavigate(target)
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
                            contentDescription = if (isSecure) "Secure connection" else "Insecure connection",
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

        // Animated progress bar — visible only while loading
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
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
            icon = { Icon(Icons.Rounded.Tab, contentDescription = "Tabs") },
            label = { Text("Tabs") }
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────

/**
 * If the user typed a bare domain or search query, convert it to a full URL.
 * Queries without a recognizable domain are sent to DuckDuckGo.
 */
private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    // Looks like a domain?
    return if (trimmed.contains(".") && !trimmed.contains(" ")) {
        "https://$trimmed"
    } else {
        "https://duckduckgo.com/?q=${trimmed.replace(" ", "+")}"
    }
}

/** Strips scheme and trailing slash for a compact address-bar display. */
private fun prettyUrl(url: String): String {
    return url
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
        .ifEmpty { url }
}

/** Modifier extension — click without ripple (for the address bar row). */
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.then(
        androidx.compose.foundation.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    )
