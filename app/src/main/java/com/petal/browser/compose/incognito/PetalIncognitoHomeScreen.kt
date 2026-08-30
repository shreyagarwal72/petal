package com.petal.browser.compose.incognito

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.theme.IncognitoDarkBackground
import com.petal.browser.ui.theme.IncognitoPrimary
import com.petal.browser.ui.theme.IncognitoSurfaceContainer
import com.petal.browser.ui.theme.PetalIncognitoTheme

@Composable
fun PetalIncognitoHomeScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onSearchClick: () -> Unit = {},
    onCloseIncognito: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }

    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "sp_amoled") {
                isAmoled = sp.getBoolean("sp_amoled", false)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    var blockThirdPartyCookies by remember { mutableStateOf(true) }

    PetalIncognitoTheme(useAmoled = isAmoled) {
        val incognitoSubtitles = remember {
            listOf(
                "Off the grid. No traces, no history.",
                "Stealth mode engaged. Browse like a shadow.",
                "Your secret is safe with this tab.",
                "Agent mode activated: look around, leave no footprints.",
                "Going dark. What happens here, stays here.",
                "Browse in absolute private.",
                "A clean slate with zero history saved.",
                "Zero cookies, zero tracks, 100% private.",
                "Explore freely—your sessions vanish when you close the tab.",
                "No history, no suggestions, just pure browsing.",
                "Searching for a gift? We won't spoil the surprise.",
                "You were never here, and neither were we.",
                "Don't worry, we won't tell your autofill.",
                "Your private detour begins now.",
                "Go ahead, ask the weird questions."
            )
        }
        val randomSubtitle = remember { incognitoSubtitles.random() }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                com.petal.browser.ui.components.ExpressiveHeader(
                    title = "Incognito Mode",
                    subtitle = randomSubtitle,
                    maxTitleLines = 1,
                    maxSubtitleLines = 3,
                    onBack = null,
                    actions = {}
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Spy Hat & Glasses Circular Badge
                    Surface(
                        shape = CircleShape,
                        color = IncognitoSurfaceContainer,
                        tonalElevation = 4.dp,
                        modifier = Modifier
                            .size(88.dp)
                            .entrance(index = 0)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.VisibilityOff,
                                contentDescription = "Incognito Mode",
                                tint = IncognitoPrimary,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Hero Headline
                    Text(
                        text = "You've gone Incognito",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.entrance(index = 1)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Hero Description
                    Text(
                        text = "Now you can browse privately, and other people who use this device won't see your activity.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .entrance(index = 2)
                    )

                    Spacer(Modifier.height(28.dp))

                    // Chrome-style Quick Search Decoy Bar
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = IncognitoSurfaceContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .entrance(index = 3)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.foundation.LocalIndication.current
                            ) { onSearchClick() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = IncognitoPrimary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Search or type URL",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    // What Petal Won't Save Card
                    IncognitoInfoCard(
                        title = "Petal won't save:",
                        icon = Icons.Rounded.Block,
                        items = listOf(
                            "Your browsing history and search terms",
                            "Cookies and site data (cleared on tab close)",
                            "Information entered in web forms"
                        ),
                        modifier = Modifier.entrance(index = 4)
                    )

                    Spacer(Modifier.height(16.dp))

                    // Activity Still Visible Card
                    IncognitoInfoCard(
                        title = "Your activity might still be visible to:",
                        icon = Icons.Rounded.Info,
                        items = listOf(
                            "Websites you visit and sign in to",
                            "Your employer, school, or network admin",
                            "Your internet service provider (ISP)"
                        ),
                        modifier = Modifier.entrance(index = 5)
                    )

                    Spacer(Modifier.height(24.dp))

                    // Block 3rd Party Cookies Toggle Card
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = IncognitoSurfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .entrance(index = 6)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Cookie,
                                contentDescription = null,
                                tint = IncognitoPrimary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(end = 4.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Block third-party cookies",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "When turned on, sites can't use cookies that track you across the web.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            com.petal.browser.ui.components.IconSwitch(
                                checked = blockThirdPartyCookies,
                                icon = Icons.Rounded.Cookie,
                                onCheckedChange = { blockThirdPartyCookies = it }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun IncognitoInfoCard(
    title: String,
    icon: ImageVector,
    items: List<String>,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = IncognitoSurfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = IncognitoPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(14.dp))

            items.forEach { item ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = IncognitoPrimary
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

object PetalIncognitoBridge {
    @JvmStatic
    fun createIncognitoHomeView(
        activity: ComponentActivity,
        onSearchClick: Runnable,
        onCloseIncognito: Runnable
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
                PetalIncognitoHomeScreen(
                    backgroundSnapshot = snapshotBitmap,
                    onSearchClick = { onSearchClick.run() },
                    onCloseIncognito = { onCloseIncognito.run() }
                )
            }
        }
    }
}

@Preview(name = "Incognito Home Screen Preview", showBackground = true)
@Composable
private fun PetalIncognitoHomeScreenPreview() {
    PetalIncognitoHomeScreen()
}
