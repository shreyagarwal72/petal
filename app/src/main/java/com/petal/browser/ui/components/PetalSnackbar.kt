package com.petal.browser.ui.components

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.theme.PetalBrowserShapes
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Message types for PetalSnackbar specifying visual accent and leading icon.
 */
enum class PetalSnackbarType {
    INFO,
    SUCCESS,
    WARNING
}

/**
 * Visual payload for [PetalSnackbar].
 */
data class PetalSnackbarData(
    val message: String,
    val type: PetalSnackbarType = PetalSnackbarType.INFO,
    val actionLabel: String? = null,
    val durationMs: Long = 3200L,
    val onAction: (() -> Unit)? = null
)

/**
 * Global singleton dispatcher managing transient [PetalSnackbar] notifications across the app.
 */
object PetalSnackbarDispatcher {
    private val _currentSnackbar = MutableStateFlow<PetalSnackbarData?>(null)
    val currentSnackbar = _currentSnackbar.asStateFlow()

    private var dismissJob: Job? = null

    /**
     * Posts a transient message to be rendered as an Expressive [PetalSnackbar].
     * Can be invoked from any thread safely.
     */
    fun show(
        message: String,
        type: PetalSnackbarType = PetalSnackbarType.INFO,
        actionLabel: String? = null,
        durationMs: Long = 3200L,
        onAction: (() -> Unit)? = null
    ) {
        if (message.isBlank()) return

        val runPost = {
            _currentSnackbar.value = PetalSnackbarData(
                message = message,
                type = type,
                actionLabel = actionLabel,
                durationMs = durationMs,
                onAction = onAction
            )
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            runPost()
        } else {
            Handler(Looper.getMainLooper()).post(runPost)
        }
    }

    fun show(
        context: Context,
        @StringRes stringResId: Int,
        type: PetalSnackbarType = PetalSnackbarType.INFO,
        actionLabel: String? = null,
        durationMs: Long = 3200L,
        onAction: (() -> Unit)? = null
    ) {
        try {
            show(context.getString(stringResId), type, actionLabel, durationMs, onAction)
        } catch (_: Exception) {}
    }

    fun dismiss() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _currentSnackbar.value = null
        } else {
            Handler(Looper.getMainLooper()).post {
                _currentSnackbar.value = null
            }
        }
    }

    /**
     * Attaches and binds a dedicated ComposeView in activity_main or any layout.
     */
    fun attachComposeView(activity: androidx.activity.ComponentActivity, composeView: ComposeView) {
        composeView.setViewTreeLifecycleOwner(activity)
        composeView.setViewTreeViewModelStoreOwner(activity)
        composeView.setViewTreeSavedStateRegistryOwner(activity)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            PetalExpressiveTheme {
                PetalSnackbarHost()
            }
        }
    }

    /**
     * Attaches the top-level persistent PetalSnackbarHost ComposeView into the Activity's decor or root view.
     */
    fun attachToActivity(activity: Activity) {
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val existing = decor.findViewWithTag<ComposeView>("petal_snackbar_decor_host")
        if (existing != null) return

        val composeView = ComposeView(activity).apply {
            tag = "petal_snackbar_decor_host"
            setViewTreeLifecycleOwner(activity as? androidx.lifecycle.LifecycleOwner)
            setViewTreeViewModelStoreOwner(activity as? androidx.lifecycle.ViewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(activity as? androidx.savedstate.SavedStateRegistryOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PetalExpressiveTheme {
                    PetalSnackbarHost()
                }
            }
        }

        decor.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }
}

/**
 * Top-level Material 3 Expressive [PetalSnackbarHost] composable.
 * Observes [PetalSnackbarDispatcher.currentSnackbar] and displays animated snackbar overlays.
 * Layout adapts automatically between portrait phones, landscape, and tablets.
 */
@Composable
fun PetalSnackbarHost(
    modifier: Modifier = Modifier
) {
    val snackbarData by PetalSnackbarDispatcher.currentSnackbar.collectAsState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(snackbarData) {
        val current = snackbarData
        if (current != null && current.durationMs > 0) {
            delay(current.durationMs)
            PetalSnackbarDispatcher.dismiss()
        }
    }

    val config = LocalConfiguration.current
    val isTabletOrExpanded = config.screenWidthDp >= 600
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                bottom = if (isLandscape) 16.dp else 72.dp,
                start = if (isTabletOrExpanded) 24.dp else 16.dp,
                end = if (isTabletOrExpanded) 24.dp else 16.dp
            ),
        contentAlignment = if (isTabletOrExpanded) Alignment.BottomStart else Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = snackbarData != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium
                )
            )
        ) {
            snackbarData?.let { data ->
                val adaptiveModifier = if (isTabletOrExpanded) {
                    Modifier.widthIn(min = 280.dp, max = 460.dp)
                } else {
                    Modifier.fillMaxWidth().widthIn(max = 420.dp)
                }

                PetalSnackbar(
                    data = data,
                    onDismiss = {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.TICK, 0.3f)
                        PetalSnackbarDispatcher.dismiss()
                    },
                    modifier = adaptiveModifier
                )
            }
        }
    }
}

/**
 * Single Material 3 Expressive [PetalSnackbar] component.
 * Features:
 * - Rounded large corner radius pill shape
 * - Subtle leading icon changing with message type (INFO, SUCCESS, WARNING)
 * - Styled with PetalTheme container and content colors
 * - Optional styled action button (e.g. "Undo")
 * - Spring-based motion and swipe-to-dismiss gesture
 */
@Composable
fun PetalSnackbar(
    data: PetalSnackbarData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(percent = 50)
) {
    val (iconRes, iconTint) = when (data.type) {
        PetalSnackbarType.INFO -> Pair(R.drawable.icon_info, MaterialTheme.colorScheme.primary)
        PetalSnackbarType.SUCCESS -> Pair(R.drawable.icon_check, MaterialTheme.colorScheme.tertiary)
        PetalSnackbarType.WARNING -> Pair(R.drawable.icon_alert, MaterialTheme.colorScheme.error)
    }

    Surface(
        modifier = modifier
            .pointerInput(data) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 10f) {
                        onDismiss()
                    }
                }
            },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = data.type.name,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = data.message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )

            data.actionLabel?.let { label ->
                TextButton(
                    onClick = {
                        data.onAction?.invoke()
                        onDismiss()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
