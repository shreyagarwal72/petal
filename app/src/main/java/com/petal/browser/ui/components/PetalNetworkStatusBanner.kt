/*
 * PetalNetworkStatusBanner.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Floating horizontal (left-to-right) short-height dialogue for network status alerts:
 * 1. Shows "You are Offline" when internet connectivity is lost.
 * 2. Shows "You are Back Online" when connectivity is restored.
 * 3. Short-height floating pill (~40dp) with M3 Expressive container colors.
 * 4. Animated with fluid left-to-right spring enter and exit transitions.
 * 5. Auto-dismiss after 3.5 seconds or swipe/tap to dismiss.
 * 6. Only visible on website pages; suspended on Home and overlay screens.
 */

package com.petal.browser.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlinx.coroutines.delay

enum class NetworkAlertState {
    IDLE,
    OFFLINE,
    ONLINE
}

object PetalNetworkStatusBridge {
    private val _networkState = mutableStateOf(NetworkAlertState.IDLE)
    val networkState: State<NetworkAlertState> get() = _networkState

    private var hasInitialized = false
    private var wasPreviouslyOffline = false
    private var isWebsiteActive = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun setWebsiteActive(active: Boolean) {
        isWebsiteActive = active
        if (!active) {
            _networkState.value = NetworkAlertState.IDLE
        }
    }

    fun dismiss() {
        _networkState.value = NetworkAlertState.IDLE
    }

    fun init(context: Context) {
        if (networkCallback != null) return
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val isConnectedInitially = checkCurrentConnection(cm)
        wasPreviouslyOffline = !isConnectedInitially
        hasInitialized = true

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!hasInitialized) return
                if (wasPreviouslyOffline && isWebsiteActive) {
                    _networkState.value = NetworkAlertState.ONLINE
                }
                wasPreviouslyOffline = false
            }

            override fun onLost(network: Network) {
                if (!hasInitialized) return
                val isStillConnected = checkCurrentConnection(cm)
                if (!isStillConnected) {
                    wasPreviouslyOffline = true
                    if (isWebsiteActive) {
                        _networkState.value = NetworkAlertState.OFFLINE
                    }
                }
            }
        }

        try {
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            android.util.Log.w("PetalNetworkStatus", "Failed to register network callback", e)
        }
    }

    private fun checkCurrentConnection(cm: ConnectivityManager): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val net = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(net) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val info = cm.activeNetworkInfo
                info != null && info.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    fun attachComposeView(activity: ComponentActivity, composeView: ComposeView) {
        composeView.setViewTreeLifecycleOwner(activity)
        composeView.setViewTreeViewModelStoreOwner(activity)
        composeView.setViewTreeSavedStateRegistryOwner(activity)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        init(activity)

        composeView.setContent {
            PetalExpressiveTheme {
                val state by networkState
                PetalNetworkStatusBanner(
                    state = state,
                    onDismiss = { dismiss() }
                )
            }
        }
    }
}

@Composable
fun PetalNetworkStatusBanner(
    state: NetworkAlertState,
    onDismiss: () -> Unit
) {
    val visible = state != NetworkAlertState.IDLE

    LaunchedEffect(state) {
        if (state == NetworkAlertState.ONLINE) {
            delay(3000L)
            onDismiss()
        } else if (state == NetworkAlertState.OFFLINE) {
            delay(4500L)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioLowBouncy
                )
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(250))
        ) {
            val isOffline = state == NetworkAlertState.OFFLINE
            val containerColor = if (isOffline) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
            val contentColor = if (isOffline) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }

            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .height(42.dp)
                    .shadow(8.dp, RoundedCornerShape(21.dp))
                    .clip(RoundedCornerShape(21.dp))
                    .pointerInput(state) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (Math.abs(dragAmount) > 12f) {
                                onDismiss()
                            }
                        }
                    },
                shape = RoundedCornerShape(21.dp),
                color = containerColor,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(contentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isOffline) Icons.Rounded.WifiOff else Icons.Rounded.Wifi,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = if (isOffline) "You are Offline" else "You are Back Online",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = contentColor
                    )

                    Spacer(Modifier.width(2.dp))

                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() }
                    )
                }
            }
        }
    }
}
