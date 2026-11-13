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
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.Button
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * PetalSavePasswordSheet
 *
 * Material 3 Expressive interactive bottom sheet for in-browser password saving,
 * featuring seamless morphing transitions directly into Petal NowBar with spring physics.
 */
object PetalSavePasswordSheet {

    fun show(
        activity: ComponentActivity,
        domain: String,
        initialUsername: String,
        initialPassword: String,
        onSave: (username: String, password: String) -> Unit,
        onNeverSave: () -> Unit = {},
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
                        val hostSource = activity.findViewById<android.view.View>(com.petal.browser.R.id.main)
                            ?: activity.findViewById(android.R.id.content)
                        CompositionLocalProvider(LocalPetalGlassSource provides hostSource) {
                            com.petal.browser.ui.theme.PetalExpressiveTheme {
                                SavePasswordSheetContent(
                                    domain = domain,
                                    initialUsername = initialUsername,
                                    initialPassword = initialPassword,
                                    onSave = onSave,
                                    onNever = {
                                        onNeverSave()
                                        dialog.dismiss()
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
                android.util.Log.w("PetalSavePasswordSheet", "Failed to show save password sheet", t)
            }
        }
    }
}

@Composable
private fun SavePasswordSheetContent(
    domain: String,
    initialUsername: String,
    initialPassword: String,
    onSave: (String, String) -> Unit,
    onNever: () -> Unit,
    onDismiss: () -> Unit,
    onExitAnimationFinished: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = remember { PetalHapticEngine.getInstance(context) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }
    var passwordVisible by remember { mutableStateOf(false) }

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
            onSave(username, password)
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
                    .imePadding()
                    .liquidGlassChrome(shape = sheetShape, enabled = isGlass, sheenIntensity = sheen)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Drag Handle
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(ExpressiveButtonShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Header with expressive key icon badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Save password?",
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

                    // Editable Fields Container (PixelMusic / M3 Containment)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Username field
                            com.petal.browser.ui.kit.TextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Username or Email") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = com.petal.browser.ui.containment.petalFilledTextFieldColors()
                            )

                            // Password field
                            com.petal.browser.ui.kit.TextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = com.petal.browser.ui.containment.petalFilledTextFieldColors()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Buttons (PixelMusic inspired tactile expressive layout)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Secondary action: "Never for this site"
                        com.petal.browser.ui.kit.FilledTonalButton(
                            onClick = {
                                if (!isMorphing && !isClosing) {
                                    haptic.playClick(context)
                                    onNever()
                                }
                            },
                            enabled = !isMorphing && !isClosing,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Never",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }

                        // Primary action: "Save" with seamless morph handoff to Petal NowBar
                        Button(
                            onClick = {
                                if (!isMorphing && !isClosing) {
                                    haptic.playHeavyClick(context)
                                    // Fluid spatial handoff: dispatch live event directly to Petal NowBar
                                    PetalLiveActionBus.postCredentialSaved(
                                        domain = cleanDomain,
                                        username = username,
                                        password = password,
                                        isUpdate = false
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
                                text = "Save",
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
