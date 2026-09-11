/*
 * PetalBiometricExpressiveOverlay.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Biometric Authentication Dialog/Overlay for Petal Browser.
 * Enhances the system biometric unlock experience with:
 * - Material 3 Expressive dynamic container styling & variable background
 * - Expressive Sunny shape badge with breathing pulse animation
 * - Instant liquid displacement ripple effect upon successful verification
 * - High-precision tactile haptics via PetalHapticEngine
 */

package com.petal.browser.compose.security

import android.app.Activity
import android.os.Build
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.security.BiometricLockManager
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.layout.LiquidRippleEffect
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.PetalMaterialShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalBiometricExpressiveSheet(
    title: String = "Biometric Authentication",
    subtitle: String = "Confirm your fingerprint to access Petal Browser",
    allowPasswordFallback: Boolean = true,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    onUsePassword: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    fun authenticate() {
        val appCompatActivity = context as? AppCompatActivity
        if (appCompatActivity != null) {
            BiometricLockManager.authenticate(
                appCompatActivity,
                title,
                subtitle,
                onSuccess = {
                    isSuccess = true
                    errorMessage = null
                    PetalHapticEngine.getInstance(context).playIfEnabled(
                        context,
                        PetalHapticEngine.Pattern.DOUBLE_CLICK,
                        0.9f
                    )
                    val decor = appCompatActivity.window?.decorView
                    if (decor != null) {
                        LiquidRippleEffect.trigger(decor)
                    }
                    onSuccess()
                },
                onError = { err ->
                    errorMessage = err
                    PetalHapticEngine.getInstance(context).playIfEnabled(
                        context,
                        PetalHapticEngine.Pattern.HEAVY_CLICK,
                        1.0f
                    )
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        authenticate()
    }

    BackHandler {
        onCancel()
    }

    Surface(
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            M3ExpressiveVariableBackground(
                modifier = Modifier.matchParentSize(),
                pageSeed = "biometric_auth"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag Handle
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .padding(bottom = 8.dp)
                ) {}

                Spacer(modifier = Modifier.height(16.dp))

                // Expressive Shaped Badge (Sunny shape morphs on success)
                val badgeShape = remember(isSuccess) {
                    if (isSuccess) PetalMaterialShapes.Bun.toShape()
                    else PetalMaterialShapes.Sunny.toShape()
                }

                Surface(
                    shape = badgeShape,
                    modifier = Modifier
                        .size(92.dp)
                        .scale(if (isSuccess) 1.08f else pulseScale),
                    color = if (isSuccess) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else if (errorMessage != null) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    tonalElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isSuccess -> Icons.Rounded.CheckCircle
                                errorMessage != null -> Icons.Rounded.Warning
                                else -> Icons.Rounded.Fingerprint
                            },
                            contentDescription = "Biometric Icon",
                            tint = when {
                                isSuccess -> MaterialTheme.colorScheme.primary
                                errorMessage != null -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onPrimary
                            },
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = if (isSuccess) "Authenticated" else title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    if (errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (allowPasswordFallback && onUsePassword != null) {
                        OutlinedButton(
                            onClick = onUsePassword,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Use Password", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = {
                            errorMessage = null
                            authenticate()
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (errorMessage != null) "Retry" else "Authenticate", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Cancel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

object PetalBiometricOverlayBridge {

    /**
     * Shows the Material 3 Expressive Biometric overlay sheet over the provided Activity.
     */
    @JvmStatic
    @JvmOverloads
    fun show(
        activity: Activity,
        title: String = "Biometric Authentication",
        subtitle: String = "Touch the fingerprint sensor to continue",
        onSuccess: Runnable,
        onCancel: Runnable,
        onUsePassword: Runnable? = null
    ) {
        val appCompat = activity as? AppCompatActivity ?: return
        if (appCompat.isFinishing || (Build.VERSION.SDK_INT >= 17 && appCompat.isDestroyed)) return

        val dialog = BottomSheetDialog(appCompat)
        val composeView = ComposeView(appCompat).apply {
            setViewTreeLifecycleOwner(appCompat)
            setViewTreeViewModelStoreOwner(appCompat)
            setViewTreeSavedStateRegistryOwner(appCompat)
            if (appCompat is ComponentActivity) {
                setViewTreeOnBackPressedDispatcherOwner(appCompat)
            }
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = LocalContext.current
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
                val currentPaletteId = remember { sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId }
                val isAmoled = remember { sp.getBoolean("sp_amoled", false) }
                val isExpressiveColors = remember { sp.getBoolean("sp_expressive_colors", false) }
                val useDynamic = remember { sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported) }
                val fontName = remember { sp.getString("sp_app_font", "PETAL") ?: "PETAL" }
                val styleName = remember { sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT" }
                val fontWidthVal = remember { sp.getFloat("sp_font_width", 92f) }
                val fontWeightVal = remember { sp.getInt("sp_font_weight", 750) }
                val fontRoundnessVal = remember { sp.getFloat("sp_font_roundness", 100f) }

                val appFont = remember(fontName) { com.petal.browser.ui.theme.AppFont.fromName(fontName) }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (_: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    paletteId = currentPaletteId,
                    useAmoled = isAmoled,
                    dynamicColor = useDynamic,
                    expressiveColors = isExpressiveColors,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    fontWidth = fontWidthVal,
                    fontWeight = fontWeightVal,
                    fontRoundness = fontRoundnessVal
                ) {
                    PetalBiometricExpressiveSheet(
                        title = title,
                        subtitle = subtitle,
                        allowPasswordFallback = onUsePassword != null,
                        onSuccess = {
                            try { dialog.dismiss() } catch (_: Exception) {}
                            onSuccess.run()
                        },
                        onCancel = {
                            try { dialog.dismiss() } catch (_: Exception) {}
                            onCancel.run()
                        },
                        onUsePassword = onUsePassword?.let { cb ->
                            {
                                try { dialog.dismiss() } catch (_: Exception) {}
                                cb.run()
                            }
                        }
                    )
                }
            }
        }

        dialog.setContentView(composeView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
            dialog.behavior.apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
                isDraggable = true
            }
        }
        try {
            dialog.show()
        } catch (_: Exception) {}
    }
}
