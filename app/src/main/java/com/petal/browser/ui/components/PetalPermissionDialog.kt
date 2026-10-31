package com.petal.browser.ui.components

import android.content.Context
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.glass.PetalGlassContainer
import com.petal.browser.ui.glass.rememberGlassSheen
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class PetalPermissionType(
    val title: String,
    val description: String,
    val icon: ImageVector,
    /** Kept for other callers (recording indicators). The dialog itself uses theme colours. */
    val accentColor: Color
) {
    LOCATION(
        title = "Location",
        description = "Shows nearby results, local weather and accurate directions.",
        icon = Icons.Rounded.LocationOn,
        accentColor = Color(0xFF00B4D8)
    ),
    CAMERA(
        title = "Camera",
        description = "Lets the site take photos, record video or scan QR codes.",
        icon = Icons.Rounded.PhotoCamera,
        accentColor = Color(0xFFFF9400)
    ),
    MICROPHONE(
        title = "Microphone",
        description = "Lets the site record audio for voice search and calls.",
        icon = Icons.Rounded.Mic,
        accentColor = Color(0xFFAB47BC)
    ),
    NOTIFICATION(
        title = "Web Notifications",
        description = "Lets the site send you alerts and updates directly to your device.",
        icon = Icons.Rounded.NotificationsActive,
        accentColor = Color(0xFF2979FF)
    ),
    DRM(
        title = "Protected Media",
        description = "Lets the site play protected, encrypted video such as streaming shows.",
        icon = Icons.Rounded.Key,
        accentColor = Color(0xFF00C853)
    ),
    STORAGE(
        title = "Site Data",
        description = "Lets the site save offline data, settings and cache on this device.",
        icon = Icons.Rounded.Cookie,
        accentColor = Color(0xFFE91E63)
    )
}

/** Theme-driven colours for each permission type, so the dialog follows the app palette. */
private data class PermissionAccent(val container: Color, val onContainer: Color, val strong: Color)

@Composable
private fun PetalPermissionType.accent(): PermissionAccent {
    val c = MaterialTheme.colorScheme
    return when (this) {
        PetalPermissionType.LOCATION, PetalPermissionType.NOTIFICATION ->
            PermissionAccent(c.primaryContainer, c.onPrimaryContainer, c.primary)
        PetalPermissionType.CAMERA, PetalPermissionType.DRM ->
            PermissionAccent(c.tertiaryContainer, c.onTertiaryContainer, c.tertiary)
        PetalPermissionType.MICROPHONE, PetalPermissionType.STORAGE ->
            PermissionAccent(c.secondaryContainer, c.onSecondaryContainer, c.secondary)
    }
}

/** "Allow <host> ..." split around the host so the host can be highlighted. */
private fun PetalPermissionType.titleSuffix(): String = when (this) {
    PetalPermissionType.LOCATION -> " to use your location?"
    PetalPermissionType.CAMERA -> " to use your camera?"
    PetalPermissionType.MICROPHONE -> " to use your microphone?"
    PetalPermissionType.NOTIFICATION -> " to send you notifications?"
    PetalPermissionType.DRM -> " to play protected media?"
    PetalPermissionType.STORAGE -> " to save site data?"
}

/** A softly scalloped circle: the expressive "cookie" badge shape. */
internal class ScallopShape(private val bumps: Int = 9, private val amplitude: Float = 0.055f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val base = min(size.width, size.height) / 2f / (1f + amplitude)
        val steps = 144
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * 2f * PI.toFloat()
            val r = base * (1f + amplitude * cos(bumps * t))
            val x = cx + r * cos(t)
            val y = cy + r * sin(t)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Material 3 Expressive permission prompt with live Liquid Glass.
 *
 * - The panel uses the app's shared glass pipeline ([PetalGlassContainer] with `live = true`), which
 *   re-records the real app window (the page) every frame, so the blur and rim refraction follow the
 *   site behind the dialog even though the dialog is its own window.
 * - Theme-coloured scalloped badge with a type-specific icon, origin chip, highlighted host in the title.
 * - "Remember" is a toggle row with a check thumb; the buttons are pills that squish while pressed.
 *
 * [glassSource] is kept for source compatibility; the glass now samples the app window itself.
 */
@Composable
fun PetalPermissionDialog(
    type: PetalPermissionType,
    origin: String,
    @Suppress("UNUSED_PARAMETER") glassSource: View? = null,
    onAllow: (remember: Boolean) -> Unit,
    onDeny: (remember: Boolean) -> Unit
) {
    val context = LocalContext.current
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = rememberGlassSheen(isGlass)
    val shape = RoundedCornerShape(36.dp)
    val accent = type.accent()
    val scheme = MaterialTheme.colorScheme

    var rememberDecision by remember { mutableStateOf(true) }

    val displayHost = remember(origin) {
        try {
            val h = Uri.parse(origin).host
            if (!h.isNullOrBlank()) h else origin.ifBlank { "This website" }
        } catch (_: Exception) {
            origin.ifBlank { "This website" }
        }
    }
    val isHttps = origin.startsWith("https://", ignoreCase = true)

    val enter = remember { MutableTransitionState(false).apply { targetState = true } }

    PetalGlassContainer(
        modifier = Modifier.fillMaxWidth(),
        enabled = isGlass,
        live = true
    ) {
        AnimatedVisibility(
            visibleState = enter,
            enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.72f, stiffness = 420f), initialScale = 0.88f)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen),
                shape = shape,
                color = if (isGlass) petalGroupSurfaceColor() else scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = if (isGlass) 0.dp else 12.dp,
                border = if (isGlass) BorderStroke(0.75.dp, Color.White.copy(alpha = 0.20f))
                else BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Expressive badge: a slowly turning scalloped shape behind a steady icon.
                    val spin = rememberInfiniteTransition(label = "perm_badge_spin")
                    val angle by spin.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Restart),
                        label = "perm_badge_angle"
                    )
                    Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = angle }
                                .clip(ScallopShape())
                                .background(accent.container)
                        )
                        Icon(
                            imageVector = type.icon,
                            contentDescription = type.title,
                            tint = accent.onContainer,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Origin chip
                    Surface(
                        shape = CircleShape,
                        color = scheme.surfaceContainerHighest.copy(alpha = if (isGlass) 0.55f else 0.9f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 7.dp, bottom = 7.dp)
                        ) {
                            Icon(
                                imageVector = if (isHttps) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                contentDescription = if (isHttps) "Secure connection" else "Not secure",
                                tint = if (isHttps) accent.strong else scheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = displayHost,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 240.dp)
                            )
                        }
                    }

                    // Title with the host highlighted
                    Text(
                        text = buildAnnotatedString {
                            append("Allow ")
                            withStyle(SpanStyle(color = accent.strong)) { append(displayHost) }
                            append(type.titleSuffix())
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            lineHeight = 30.sp
                        ),
                        color = scheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = type.description,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(Modifier.height(2.dp))

                    // Remember decision
                    val rowShape = RoundedCornerShape(26.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .background(scheme.surfaceContainerHighest.copy(alpha = if (isGlass) 0.50f else 0.85f))
                            .toggleable(
                                value = rememberDecision,
                                role = Role.Switch,
                                onValueChange = {
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                    rememberDecision = it
                                }
                            )
                            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(scheme.surfaceContainerHigh.copy(alpha = if (isGlass) 0.7f else 1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Remember for this site",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = scheme.onSurface
                            )
                            Text(
                                text = "Don't ask again on future visits",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        val checkThumb: @Composable () -> Unit = {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize))
                        }
                        Switch(
                            checked = rememberDecision,
                            onCheckedChange = null,
                            thumbContent = if (rememberDecision) checkThumb else null
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PermissionActionButton(
                            text = "Don't allow",
                            icon = Icons.Rounded.Close,
                            filled = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                                onDeny(rememberDecision)
                            }
                        )
                        PermissionActionButton(
                            text = "Allow",
                            icon = Icons.Rounded.Check,
                            filled = true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.8f)
                                onAllow(rememberDecision)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Pill button whose corners tighten while pressed (Material 3 Expressive press morph). */
@Composable
internal fun PermissionActionButton(
    text: String,
    icon: ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 16.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 520f),
        label = "perm_btn_corner"
    )
    val shape = RoundedCornerShape(corner)
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), maxLines = 1)
    }
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            shape = shape,
            interactionSource = interaction,
            contentPadding = PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
            content = content
        )
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            shape = shape,
            interactionSource = interaction,
            contentPadding = PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer
            ),
            content = content
        )
    }
}

/**
 * Java interop: shows the permission prompt in its own transparent, centred window.
 */
object PetalPermissionDialogBridge {

    @JvmStatic
    fun showPermissionPrompt(
        context: Context,
        type: PetalPermissionType,
        origin: String,
        onAllow: Runnable,
        onDeny: Runnable
    ) {
        showPermissionPrompt(context, type, origin, { onAllow.run() }, { onDeny.run() })
    }

    @JvmStatic
    fun showPermissionPrompt(
        context: Context,
        type: PetalPermissionType,
        origin: String,
        onAllow: (remember: Boolean) -> Unit,
        onDeny: (remember: Boolean) -> Unit
    ) {
        var currContext = context
        while (currContext is android.content.ContextWrapper) {
            if (currContext is androidx.activity.ComponentActivity) break
            currContext = currContext.baseContext
        }

        val activity = currContext as? androidx.activity.ComponentActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = android.app.Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PetalExpressiveTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PetalPermissionDialog(
                            type = type,
                            origin = origin,
                            onAllow = { remember ->
                                try { dialog.dismiss() } catch (_: Exception) {}
                                onAllow(remember)
                            },
                            onDeny = { remember ->
                                try { dialog.dismiss() } catch (_: Exception) {}
                                onDeny(remember)
                            }
                        )
                    }
                }
            }
        }

        dialog.setContentView(composeView)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener { onDeny(false) }
        dialog.window?.let { win ->
            win.setBackgroundDrawableResource(android.R.color.transparent)
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setGravity(Gravity.CENTER)
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // Light dim so the live page stays vivid through the glass.
            win.setDimAmount(0.12f)
        }
        try {
            dialog.show()
        } catch (_: Exception) {}
    }
}
