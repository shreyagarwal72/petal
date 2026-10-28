package com.petal.browser.account.progress

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.theme.MaterialShapes
import com.petal.browser.ui.theme.toShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private val LOADING_MESSAGES = listOf(
    "Brewing fresh coffee for the dev...",
    "Compiling GeckoView engine...",
    "Watering the code greenhouse...",
    "Polishing the liquid glass shaders...",
    "Pruning background memory leaks...",
    "Connecting to sponsor network..."
)

private suspend fun checkAdIntegrity(context: Context): String? {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val net = cm?.activeNetwork ?: return "No Internet Connection"
    val caps = cm.getNetworkCapabilities(net) ?: return "No Internet Connection"
    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        return "No Internet Connection"
    }
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
        return "VPN Detected"
    }
    return withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://config.unityads.unity3d.com")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "HEAD"
            }
            conn.connect()
            conn.disconnect()
            null
        } catch (_: java.net.UnknownHostException) {
            "Ad Blocker / DNS Filter Detected"
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Interactive 'Fuel the Code' Developer Support Sheet inspired by Orion Store's AdDonation approach.
 * Transforms voluntary ad viewing into an indie developer support quest with milestones, story cards,
 * and transparent integrity feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalFuelTheCodeSheet(
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val haptic = remember { PetalHapticEngine.getInstance(ctx) }

    val provider = PetalAds.provider
    val adsWatched = PetalProgressStore.counter(PetalCounter.ADS_WATCHED)
    val currentMilestone = PetalSupporterQuest.currentMilestone(adsWatched)
    val nextMilestone = PetalSupporterQuest.nextMilestone(adsWatched)

    var viewState by remember { mutableStateOf("checking") }
    var integrityReason by remember { mutableStateOf("") }
    var loadingIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val blockReason = PetalProgressStore.adBlockReason(ctx)
        if (blockReason != null) {
            integrityReason = blockReason
            viewState = "noad"
            return@LaunchedEffect
        }

        val securityViolation = checkAdIntegrity(ctx)
        if (securityViolation != null) {
            integrityReason = securityViolation
            viewState = "violation"
            return@LaunchedEffect
        }

        // Integrity passed - warm up provider
        provider.preload(ctx)
        viewState = if (adsWatched == 0L) "intro" else "camp"
    }

    // Cycle through whimsical loading messages while ad buffers
    LaunchedEffect(viewState) {
        if (viewState == "loading") {
            loadingIndex = 0
            while (true) {
                delay(1400)
                loadingIndex = (loadingIndex + 1) % LOADING_MESSAGES.size
            }
        }
    }

    fun handleAdComplete() {
        val success = PetalProgressStore.onAdCompleted(ctx)
        if (success) {
            haptic?.playHeavyClick(ctx)
            viewState = "success"
        } else {
            integrityReason = PetalProgressStore.adBlockReason(ctx) ?: "Please try again later."
            viewState = "noad"
        }
    }

    fun handleAdFailed(reason: String) {
        integrityReason = reason
        haptic?.playHeavyClick(ctx)
        viewState = "noad"
    }

    fun startSupportSequence() {
        val act = ctx.findActivity()
        if (act == null) {
            handleAdFailed("Unable to present quest here.")
            return
        }

        viewState = "loading"
        haptic?.playClick(ctx)

        provider.show(
            activity = act,
            onMessage = { msg ->
                // Surfaced from watchdog or Unity error callback
                handleAdFailed(msg)
                onMessage(msg)
            },
            onResult = { watched ->
                if (watched) {
                    handleAdComplete()
                } else if (provider.lastError == null) {
                    handleAdFailed("The sponsor video was skipped. Watch the full video to fuel the code.")
                }
            }
        )
    }

    PetalSheet(
        onDismissRequest = onDismiss
    ) {
        AnimatedContent(
            targetState = viewState,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            label = "quest_content"
        ) { state ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (state) {
                    "checking" -> {
                        Spacer(Modifier.height(32.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            color = cs.primary,
                            strokeWidth = 4.dp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Verifying Integrity...",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = cs.onSurface
                        )
                        Text(
                            text = "Connecting to the developer support bridge",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                        Spacer(Modifier.height(32.dp))
                    }

                    "violation" -> {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(MaterialShapes.Cookie7Sided.toShape())
                                .background(cs.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Shield, null, Modifier.size(44.dp), tint = cs.onErrorContainer)
                        }
                        Text(
                            text = "Signal Jammed",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                            color = cs.error
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cs.errorContainer.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, cs.error.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = integrityReason,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = cs.error
                            )
                        }
                        Text(
                            text = "To protect developer support and ensure sponsor validation, VPNs and DNS ad blockers pause this quest. Please pause them temporarily to fuel the code.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = cs.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("Understand & Return", fontWeight = FontWeight.Bold)
                        }
                    }

                    "intro" -> {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(MaterialShapes.Sunny.toShape())
                                .background(cs.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.VolunteerActivism, null, Modifier.size(48.dp), tint = cs.primary)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Fuel the Code",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                textAlign = TextAlign.Center,
                                color = cs.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = cs.tertiaryContainer
                            ) {
                                Text(
                                    text = "BETA",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = cs.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Code name: Beta (Work in Progress). Petal Browser is built as an independent open-source web browser. Voluntary ad viewing directly supports development, and all browser features remain 100% unlocked for everyone.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = cs.onSurfaceVariant
                        )

                        // Dev note invitation
                        val cardShape = RoundedCornerShape(20.dp)
                        Surface(
                            shape = cardShape,
                            color = if (isGlass) petalGroupSurfaceColor() else cs.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable { viewState = "urgent" }
                                .then(if (isGlass) Modifier.liquidGlassChrome(cardShape, enabled = true) else Modifier)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(cs.tertiaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Favorite, null, Modifier.size(24.dp), tint = cs.tertiary)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("A Note from the Developer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Read Shrey's message to backers", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = cs.onSurfaceVariant)
                            }
                        }

                        Button(
                            onClick = { startSupportSequence() },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Rounded.Bolt, null)
                            Spacer(Modifier.width(8.dp))
                            Text("I'm a Hero · Fuel the Code (Beta) (+40 XP)", fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Not right now", color = cs.onSurfaceVariant)
                        }
                    }

                    "urgent" -> {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(MaterialShapes.Cookie12Sided.toShape())
                                .background(cs.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "P",
                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                                color = cs.onTertiaryContainer
                            )
                        }
                        Text(
                            text = "“Building an independent browser is tough.”",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic),
                            textAlign = TextAlign.Center,
                            color = cs.onSurface
                        )
                        Text(
                            text = "“Major tech giants dominate the web with Chromium. Petal is an effort to keep GeckoView alive, privacy honored, and design beautiful with liquid glass. Watching an ad costs you 30 seconds, but gives me the resources to keep this project thriving and 100% free for everyone.”",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = cs.onSurfaceVariant
                        )
                        Text(
                            text = "— Shrey Agarwal, Lead Developer",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = cs.primary
                        )

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = { startSupportSequence() },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Rounded.Favorite, null)
                            Spacer(Modifier.width(8.dp))
                            Text("ACCEPT QUEST & SUPPORT", fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = { viewState = if (adsWatched == 0L) "intro" else "camp" },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to Quest Hub", color = cs.onSurfaceVariant)
                        }
                    }

                    "camp" -> {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(MaterialShapes.Flower.toShape())
                                .background(cs.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.LocalFlorist, null, Modifier.size(52.dp), tint = cs.primary)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Fuel the Code",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                color = cs.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = cs.tertiaryContainer
                            ) {
                                Text(
                                    text = "BETA",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = cs.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = cs.secondaryContainer,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "Current Rank: ${currentMilestone.rank} · $adsWatched Fueled (Beta)",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = cs.onSecondaryContainer
                            )
                        }

                        // Next milestone card
                        val cardShape = RoundedCornerShape(22.dp)
                        Surface(
                            shape = cardShape,
                            color = if (isGlass) petalGroupSurfaceColor() else cs.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isGlass) Modifier.liquidGlassChrome(cardShape, enabled = true) else Modifier)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.EmojiEvents, null, tint = cs.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = nextMilestone?.let { "Next Milestone: ${it.title}" } ?: "All Milestones Unlocked!",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = nextMilestone?.story ?: "You are an eternal patron of Petal Browser.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.onSurfaceVariant
                                )
                                if (nextMilestone != null) {
                                    Spacer(Modifier.height(12.dp))
                                    val progress = (adsWatched.toFloat() / nextMilestone.level).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                        color = cs.primary,
                                        trackColor = cs.surfaceVariant
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "$adsWatched / ${nextMilestone.level} ads watched (${nextMilestone.level - adsWatched} more needed)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { startSupportSequence() },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Continue Journey (+40 XP)", fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Rest at Camp (Close)", color = cs.onSurfaceVariant)
                        }
                    }

                    "loading" -> {
                        Spacer(Modifier.height(32.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = cs.primary,
                            strokeWidth = 5.dp
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "Channeling Support...",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = cs.onSurface
                        )
                        AnimatedContent(
                            targetState = loadingIndex,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(180)) },
                            label = "loading_step"
                        ) { idx ->
                            Text(
                                text = LOADING_MESSAGES[idx],
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            text = "(Connecting to Unity Network)",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(32.dp))
                    }

                    "success" -> {
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(MaterialShapes.Burst.toShape())
                                .background(cs.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Star, null, Modifier.size(56.dp), tint = cs.onTertiaryContainer)
                        }
                        Text(
                            text = "Code Fueled!",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                            color = cs.onSurface
                        )
                        Surface(
                            shape = CircleShape,
                            color = cs.primaryContainer,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "+${PetalXp.AD_XP} XP Earned · Developer Supported",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = cs.onPrimaryContainer
                            )
                        }
                        Text(
                            text = currentMilestone.story,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = cs.onSurfaceVariant
                        )

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = { startSupportSequence() },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Rounded.VolunteerActivism, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Fuel Again (+40 XP)", fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Rest at Greenhouse (Close)", color = cs.onSurfaceVariant)
                        }
                    }

                    "noad" -> {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(MaterialShapes.Cookie6Sided.toShape())
                                .background(cs.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Info, null, Modifier.size(44.dp), tint = cs.onSurfaceVariant)
                        }
                        Text(
                            text = "Mission Paused",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                            color = cs.onSurface
                        )
                        Text(
                            text = if (integrityReason.isNotBlank()) integrityReason else "No sponsor video is available at this moment.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = cs.onSurfaceVariant
                        )
                        Text(
                            text = "Your intention to support Petal means the world! You can check back in a few minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = cs.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("Back to Camp", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
