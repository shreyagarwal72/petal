package com.petal.browser.compose.settings.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.petal.browser.R
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.liquidGlassChrome

enum class ExplainerFeatureType {
    APPLE_DUO,
    EXPRESSIVE_MORPHING_SHAPES,
    MOTION_BLUR,
    ADDRESS_BAR_POSITION,
    ADBLOCK_TRIE_SHIELD
}

data class PetalExplainerFeature(
    val type: ExplainerFeatureType,
    val title: String,
    val badge: String? = null,
    val subtitle: String,
    val bullets: List<String>,
    val icon: ImageVector,
    val iconContainerColor: Color? = null,
    val iconTint: Color? = null
)

@Composable
fun PetalFeatureInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    iconSize: Dp = 16.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String = "Feature Information"
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .size(size)
            .bouncyClickable(scaleDown = 0.88f, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = contentColor
            )
        }
    }
}

@Composable
fun PetalFeatureExplainerDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    feature: PetalExplainerFeature,
    onActionClick: (() -> Unit)? = null,
    actionLabel: String = "Got it"
) {
    if (!visible) return

    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
    val dialogShape = RoundedCornerShape(32.dp)
    val canvasShape = RoundedCornerShape(22.dp)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.petal.browser.ui.glass.PetalGlassContainer(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            enabled = isGlass
        ) {
            Surface(
                shape = dialogShape,
                color = if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = if (isGlass) 0.dp else 6.dp,
                shadowElevation = if (isGlass) 0.dp else 12.dp,
                border = if (!isGlass) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .liquidGlassChrome(dialogShape, enabled = isGlass)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── 1. Top Header with Badges ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = feature.iconContainerColor ?: MaterialTheme.colorScheme.primaryContainer,
                            contentColor = feature.iconTint ?: MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = feature.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            feature.badge?.let { b ->
                                val badgeColor = feature.iconTint ?: MaterialTheme.colorScheme.primary
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = badgeColor.copy(alpha = 0.16f),
                                    border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = b,
                                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(
                                text = feature.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = feature.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.ui_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── 2. Live Interactive Animated Preview Canvas ──
                Surface(
                    shape = canvasShape,
                    color = if (isGlass) MaterialTheme.colorScheme.surface.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = if (!isGlass) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .then(if (isGlass) Modifier.liquidGlassChrome(canvasShape, enabled = true) else Modifier)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (feature.type) {
                            ExplainerFeatureType.APPLE_DUO -> AppleDuoLivePreview()
                            ExplainerFeatureType.EXPRESSIVE_MORPHING_SHAPES -> MorphingShapesLivePreview()
                            ExplainerFeatureType.MOTION_BLUR -> MotionBlurLivePreview()
                            ExplainerFeatureType.ADDRESS_BAR_POSITION -> AddressBarPositionLivePreview()
                            ExplainerFeatureType.ADBLOCK_TRIE_SHIELD -> AdBlockTrieLivePreview()
                        }
                    }
                }

                // ── 3. Feature Breakdown Bullet Points ──
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    feature.bullets.forEach { bullet ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(16.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                            Text(
                                text = bullet,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                // ── 4. Action Button ──
                Button(
                    onClick = {
                        onActionClick?.invoke()
                        onDismissRequest()
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGlass) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .then(if (isGlass) Modifier.liquidGlassChrome(RoundedCornerShape(18.dp), enabled = true, sheenIntensity = 0.5f) else Modifier)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
}

// ─────────────────────────────────────────────────────────────────────────────
// Specialized Animated Micro-Interactive Previews
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 1. Apple Duo Live Animated Preview:
 * A 3D dual-pane card perspective-tilting with animated frosted glass blur and fold crease.
 */
@Composable
private fun AppleDuoLivePreview() {
    val infiniteTransition = rememberInfiniteTransition(label = "appleDuoFold")
    val tiltAngle by infiniteTransition.animateFloat(
        initialValue = -16f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .width(220.dp)
                .height(115.dp)
                .graphicsLayer {
                    rotationY = tiltAngle
                    cameraDistance = 18f * density
                },
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Left Pane
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                        Text("Left Page", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Glass Fold Hinge Line
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.4f))
            )

            // Right Pane
            Surface(
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 4.dp, bottomStart = 4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.20f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Layers, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
                        Text("Right Page", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 2. M3 Expressive Morphing Shapes Preview:
 * Smoothly morphing shape with dynamic scale, rotation, and particle pulses.
 */
@Composable
private fun MorphingShapesLivePreview() {
    val transition = rememberInfiniteTransition(label = "m3Shapes")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "rot"
    )
    val cornerRadius by transition.animateFloat(
        initialValue = 12f,
        targetValue = 48f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corner"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(RoundedCornerShape(cornerRadius.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.BubbleChart,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/**
 * 3. Motion Blur Velocity Preview:
 * Content oscillating vertically with speed-dependent dynamic blur effect.
 */
@Composable
private fun MotionBlurLivePreview() {
    val transition = rememberInfiniteTransition(label = "motionBlur")
    val offsetY by transition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scrollOffset"
    )
    val simulatedBlur = (kotlin.math.abs(offsetY) / 6f).coerceIn(0f, 6f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier
                .width(200.dp)
                .graphicsLayer { translationY = offsetY }
                .blur(simulatedBlur.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
                Column {
                    Text("Smooth 120Hz Blur", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Velocity Shader Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * 4. Address Bar Position Live Preview:
 * Animated phone frame shifting address bar smoothly between top and bottom.
 */
@Composable
private fun AddressBarPositionLivePreview() {
    val transition = rememberInfiniteTransition(label = "addrPos")
    val isBottom by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "posSwitch"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Mini Phone Screen Frame
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .width(130.dp)
                .height(140.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                // Mock web content
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.width(80.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))
                    Box(Modifier.width(60.dp).height(6.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
                    Box(Modifier.width(70.dp).height(6.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
                }

                // Moving Floating Address Bar Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(if (isBottom > 0.5f) Alignment.BottomCenter else Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(10.dp))
                        Text("petal.browser", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Rounded.MoreVert, null, modifier = Modifier.size(10.dp))
                    }
                }
            }
        }
    }
}

/**
 * 5. AdBlock Trie Shield Live Preview:
 * Packet nodes traveling toward the Shield. Safe packets pass through; tracker packets are blocked.
 */
@Composable
private fun AdBlockTrieLivePreview() {
    val transition = rememberInfiniteTransition(label = "adBlockShield")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "packetProgress"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Central Shield
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Clean Packet (Passing Through to Right)
        val cleanOffset = (progress * 130f) - 65f
        Box(
            modifier = Modifier
                .offset(x = cleanOffset.dp, y = (-12).dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )

        // Blocked Ad Packet (Stopped at Shield Center)
        val trackerOffset = ((progress.coerceAtMost(0.48f)) * 130f) - 65f
        val trackerAlpha = if (progress > 0.5f) 0f else 1f
        Box(
            modifier = Modifier
                .offset(x = trackerOffset.dp, y = 14.dp)
                .size(10.dp)
                .graphicsLayer { alpha = trackerAlpha }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
        )
    }
}
