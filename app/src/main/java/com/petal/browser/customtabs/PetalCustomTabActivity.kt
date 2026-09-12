package com.petal.browser.customtabs

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.view.PetalGeckoView

/**
 * PetalCustomTabActivity
 * ─────────────────────────────────────────────────────────────────────────
 * Lightweight standalone Activity handling incoming Custom Tabs [Intent.ACTION_VIEW] intents.
 * Hosts a single instance of [PetalGeckoView] directly, reusing all GeckoView rendering,
 * networking, and security logic.
 *
 * Minimal Toolbar UI features:
 *  - Close button: finishes the Activity.
 *  - URL / Domain display: clean, security-conscious representation of current page location.
 *  - "Open in Petal" action button: promotes the current page to [BrowserActivity] as a normal new tab
 *    and finishes this overlay session.
 */
class PetalCustomTabActivity : ComponentActivity() {

    private var geckoView: PetalGeckoView? = null
    private var currentUrlState by mutableStateOf("about:blank")
    private var currentTitleState by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetUrl = extractUrlFromIntent(intent)
        currentUrlState = targetUrl

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val gv = geckoView
                if (gv != null && gv.canGoBack()) {
                    gv.goBack()
                } else {
                    finish()
                }
            }
        })

        setContent {
            PetalExpressiveTheme {
                Scaffold(
                    topBar = {
                        CustomTabTopBar(
                            url = currentUrlState,
                            title = currentTitleState,
                            onClose = { finish() },
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
                                PetalGeckoView(ctx).also { gv ->
                                    gv.layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    geckoView = gv
                                    if (targetUrl.isNotBlank() && !targetUrl.equals("about:blank", ignoreCase = true)) {
                                        gv.loadUrl(targetUrl)
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
        val targetUrl = extractUrlFromIntent(intent)
        if (targetUrl.isNotBlank() && !targetUrl.equals("about:blank", ignoreCase = true)) {
            currentUrlState = targetUrl
            geckoView?.loadUrl(targetUrl)
        }
    }

    override fun onResume() {
        super.onResume()
        geckoView?.onResume()
        updateCurrentUrlAndTitle()
    }

    override fun onPause() {
        super.onPause()
        geckoView?.onPause()
    }

    override fun onDestroy() {
        geckoView?.destroy()
        geckoView = null
        super.onDestroy()
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
        val gv = geckoView ?: return
        val url = gv.url
        if (url.isNotBlank()) {
            currentUrlState = url
        }
        val title = gv.title
        if (title.isNotBlank()) {
            currentTitleState = title
        }
    }

    private fun openInPetalBrowser() {
        val urlToOpen = geckoView?.url?.takeIf { it.isNotBlank() && !it.equals("about:blank", ignoreCase = true) }
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
    onClose: () -> Unit,
    onOpenInPetal: () -> Unit
) {
    val displayHost = remember(url) {
        try {
            val parsed = Uri.parse(url)
            parsed.host ?: url
        } catch (_: Exception) {
            url
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close custom tab",
                    tint = MaterialTheme.colorScheme.onSurface
                )
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
                    if (url.startsWith("https://", ignoreCase = true)) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Secure connection",
                            tint = MaterialTheme.colorScheme.primary,
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

            Spacer(Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onOpenInPetal,
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Open in Petal",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    fontSize = 12.sp
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
