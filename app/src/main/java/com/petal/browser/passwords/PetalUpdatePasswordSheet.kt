package com.petal.browser.passwords

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.app.Dialog
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.petal.browser.ui.components.PetalSpring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.island.CutoutMetrics
import com.petal.browser.live.PetalLiveActionBus
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.glass.*
import kotlinx.coroutines.delay

/**
 * PetalUpdatePasswordSheet
 *
 * Material 3 Expressive bottom sheet shown when a user changes their password on a site,
 * featuring seamless morphing transitions directly into Petal NowBar with spring physics.
 */
object PetalUpdatePasswordSheet {

    fun show(
        activity: ComponentActivity,
        domain: String,
        username: String,
        newPassword: String,
        onUpdate: (newPassword: String) -> Unit,
        onDismiss: () -> Unit = {}
    ) {
        activity.runOnUiThread {
            try {
                val dialog = Dialog(activity, R.style.PetalContextMenuDialogTheme)
                val composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        com.petal.browser.ui.theme.PetalExpressiveTheme {
                            UpdatePasswordSheetContent(
                                domain = domain,
                                username = username,
                                newPassword = newPassword,
                                onUpdate = {
                                    onUpdate(newPassword)
                                },
                                onDismiss = {
                                    onDismiss()
                                },
                                onExitAnimationFinished = {
                                    dialog.dismiss()
                                }
                            )
                        }
                    }
                }
                dialog.setContentView(composeView)
                dialog.window?.let { win ->
                    win.setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    win.setWindowAnimations(0)
                }
                dialog.show()
            } catch (t: Throwable) {
                android.util.Log.w("PetalUpdatePasswordSheet", "Failed to show update password sheet", t)
            }
        }
    }
}

@Composable
private fun UpdatePasswordSheetContent(
    domain: String,
    username: String,
    newPassword: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onExitAnimationFinished: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = remember { PetalHapticEngine.getInstance(context) }

    val cleanDomain = remember(domain) {
        domain.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore(':')
    }

    var isMorphing by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val cutoutOffsetXDp = remember(view) {
        CutoutMetrics.cutoutHorizontalOffsetDp(view)
    }

    // Scrim fade animation
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isMorphing || isClosing) 0f else 0.52f,
        animationSpec = PetalSpring.fadeIn(),
        label = "scrimAlpha"
    )

    // Morph animations towards Petal NowBar coordinates
    val scale by animateFloatAsState(
        targetValue = if (isMorphing) 0.16f else if (isClosing) 0.92f else 1f,
        animationSpec = PetalSpring.containerMorph(),
        label = "scale"
    )

    val targetTranslationY = with(density) { (-screenHeightDp + 52.dp).toPx() }
    val translationY by animateFloatAsState(
        targetValue = if (isMorphing) targetTranslationY else if (isClosing) with(density) { 320.dp.toPx() } else 0f,
        animationSpec = PetalSpring.containerMorph(),
        label = "transY"
    )

    val targetTranslationX = with(density) { cutoutOffsetXDp.dp.toPx() }
    val translationX by animateFloatAsState(
        targetValue = if (isMorphing) targetTranslationX else 0f,
        animationSpec = PetalSpring.containerMorph(),
        label = "transX"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isMorphing) 0f else if (isClosing) 0f else 1f,
        animationSpec = PetalSpring.fadeOut(),
        label = "alpha"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (isMorphing) 50.dp else 32.dp,
        animationSpec = PetalSpring.containerMorph(),
        label = "corner"
    )

    LaunchedEffect(isMorphing) {
        if (isMorphing) {
            delay(320L)
            onUpdate()
            onExitAnimationFinished()
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            delay(280L)
            onExitAnimationFinished()
        }
    }

    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = rememberGlassSheen(isGlass)
    val sheetShape = RoundedCornerShape(
        topStart = cornerRadius,
        topEnd = cornerRadius,
        bottomStart = if (isMorphing) cornerRadius else 0.dp,
        bottomEnd = if (isMorphing) cornerRadius else 0.dp
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Scrim backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (!isMorphing && !isClosing) {
                        isClosing = true
                        onDismiss()
                    }
                }
        )

        // Morphing Sheet Container
        PetalGlassContainer(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    this.scaleX = scale
                    this.scaleY = scale
                    this.translationX = translationX
                    this.translationY = translationY
                    this.alpha = alpha
                },
            enabled = isGlass,
            live = true
        ) {
            Surface(
                shape = sheetShape,
                color = if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = if (isGlass) 0.dp else 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .liquidGlassChrome(shape = sheetShape, enabled = isGlass, sheenIntensity = sheen)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(ExpressiveButtonShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Header with update icon badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Update password?",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = cleanDomain,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                if (!isMorphing && !isClosing) {
                                    isClosing = true
                                    onDismiss()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Info card showing account being updated
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = username.ifBlank { "Saved Account" },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = "Petal detected a new password entered for this account. Update it in your encrypted vault?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Actions: Keep Existing vs Update
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (!isMorphing && !isClosing) {
                                    haptic.playClick(context)
                                    isClosing = true
                                    onDismiss()
                                }
                            },
                            enabled = !isMorphing && !isClosing,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Keep Existing",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                if (!isMorphing && !isClosing) {
                                    haptic.playHeavyClick(context)
                                    // Fluid spatial handoff: dispatch live event directly to Petal NowBar
                                    PetalLiveActionBus.postCredentialSaved(
                                        domain = cleanDomain,
                                        username = username,
                                        password = newPassword,
                                        isUpdate = true
                                    )
                                    isMorphing = true
                                }
                            },
                            enabled = !isMorphing && !isClosing,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Update",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
