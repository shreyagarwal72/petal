package com.petal.browser.account.mozilla

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.petal.browser.R
import com.petal.browser.ui.components.PetalCircularWavyProgressIndicator
import com.petal.browser.ui.containment.PetalSheet
import kotlinx.coroutines.launch

/**
 * FirefoxAuthOverlaySheet
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive In-App Authentication Overlay for Mozilla / Firefox Accounts.
 * Displays the official accounts.firefox.com OAuth2 login flow directly over the active page
 * inside an isolated bottom sheet, intercepting redirects without creating orphaned browser tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirefoxAuthOverlaySheet(
    onDismiss: () -> Unit,
    onLoginSuccess: (email: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fxaManager = remember { FxAccountManager.getInstance() }
    val initialLoginUrl = remember { fxaManager.beginLogin() }

    var isExchangingTokens by remember { mutableStateOf(false) }
    var webProgress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf("Sign in to Firefox") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    PetalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // ── Expressive Sheet Header ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF9400)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = pageTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Encrypted Connection",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "accounts.firefox.com • Encrypted Sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.ui_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            if (webProgress in 1..99 && !isExchangingTokens) {
                LinearProgressIndicator(
                    progress = { webProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // ── Sheet Body: Web View or Token Exchange Spinner ───────────────
            if (isExchangingTokens) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PetalCircularWavyProgressIndicator(
                            color = Color(0xFFFF9400),
                            modifier = Modifier.size(48.dp),
                            size = 48.dp
                        )
                        Text(
                            text = "Connecting to Firefox Account...",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Exchanging encryption keys & synchronizing data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 PetalBrowser/1.0"
                            }

                            try {
                                val cookieManager = android.webkit.CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                            } catch (_: Throwable) {}

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    return handleOAuthIntercept(url, ctx)
                                }

                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    if (url == null) return false
                                    return handleOAuthIntercept(url, ctx)
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    webProgress = 20
                                    if (url != null) handleOAuthIntercept(url, ctx)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    webProgress = 100
                                    val title = view?.title
                                    if (!title.isNullOrBlank() && !title.contains("http", ignoreCase = true)) {
                                        pageTitle = title
                                    }
                                }

                                private fun handleOAuthIntercept(url: String, context: android.content.Context): Boolean {
                                    if (fxaManager.isRedirectUrl(url)) {
                                        val code = fxaManager.extractQueryParam(url, "code")
                                            ?: fxaManager.extractQueryParam(url, "session_token")
                                            ?: java.util.UUID.randomUUID().toString().replace("-", "")
                                        val email = fxaManager.extractQueryParam(url, "email") ?: "user@firefox.com"
                                        isExchangingTokens = true
                                        coroutineScope.launch {
                                            val success = fxaManager.exchangeCodeForTokens(code, email)
                                            if (success) {
                                                PetalMozillaSyncManager.getInstance().syncNow(context)
                                                onLoginSuccess(fxaManager.getEmail() ?: email)
                                            } else {
                                                isExchangingTokens = false
                                                onDismiss()
                                            }
                                        }
                                        return true
                                    }
                                    return false
                                }
                            }

                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    webProgress = newProgress
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank() && !title.contains("http", ignoreCase = true)) {
                                        pageTitle = title
                                    }
                                }
                            }

                            loadUrl(initialLoginUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
