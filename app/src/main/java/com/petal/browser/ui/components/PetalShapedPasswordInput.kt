/*
 * PetalShapedPasswordInput.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Masked-Password & Passcode Input Component for Petal Browser.
 *
 * Replaces plain dot-masking with deterministic, stable polygon/blob shapes
 * (cookie, clover, pentagon, sunny, pill, arch, flower, gem, etc.) from the
 * Material 3 Expressive MaterialShapes API. Each typed character's shape is
 * seeded by combining its char code and index position to ensure zero re-randomization
 * on recomposition or typing.
 */

package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi

/**
 * List of Material 3 Expressive shapes used for password masking.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val ExpressivePasswordShapes = listOf(
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Pentagon,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Pill,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Arch,
    MaterialShapes.Flower,
    MaterialShapes.Gem,
    MaterialShapes.Circle,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Burst
)

/**
 * Deterministically picks a stable shape index for character [ch] at string index [index].
 *
 * Note: this intentionally returns an Int index rather than a Shape. Converting a
 * MaterialShapes entry to a Shape via `.toShape()` must happen in composable scope,
 * and `remember { ... }` calculation blocks disallow composable calls — so the actual
 * `.toShape()` call happens at the composable call site, not in this helper.
 */
private fun getStableShapeIndex(ch: Char, index: Int): Int {
    val seed = (ch.code * 31) + (index * 17) + 7
    return Math.abs(seed) % ExpressivePasswordShapes.size
}

/**
 * PetalShapedPasswordInput: Expressive M3 password and passcode input field with
 * MaterialShapes masked overlay, eye-icon visibility toggle, and unlock actions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PetalShapedPasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hintText: String = "Enter passcode or password",
    enabled: Boolean = true,
    isError: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    shapeSize: Dp = 22.dp,
    shapeSpacing: Dp = 8.dp,
    onUnlock: (() -> Unit)? = null,
    unlockButtonText: String = "Unlock"
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val currentBorderColor = when {
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, currentBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                focusRequester.requestFocus()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading lock icon
            Icon(
                imageVector = if (value.isNotEmpty()) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = null,
                tint = if (value.isNotEmpty()) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Main Input Area with Overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Hint Text (Visible when empty)
                if (value.isEmpty()) {
                    Text(
                        text = hintText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Shaped Mask Overlay (Visible when obscured and not empty)
                if (!isPasswordVisible && value.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(shapeSpacing),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        value.forEachIndexed { index, ch ->
                            val shapeIndex = remember(ch, index) {
                                getStableShapeIndex(ch, index)
                            }
                            val characterShape: Shape = ExpressivePasswordShapes[shapeIndex].toShape()

                            val scaleAnim = remember(index, value.length) { androidx.compose.animation.core.Animatable(0f) }
                            LaunchedEffect(index, value.length) {
                                scaleAnim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                    )
                                )
                            }

                            Canvas(
                                modifier = Modifier
                                    .size(shapeSize)
                                    .graphicsLayer {
                                        scaleX = scaleAnim.value
                                        scaleY = scaleAnim.value
                                    }
                            ) {
                                val outline = characterShape.createOutline(size, layoutDirection, density)
                                val path = when (outline) {
                                    is Outline.Generic -> outline.path
                                    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                                    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                                }
                                drawPath(
                                    path = path,
                                    color = accentColor
                                )
                            }
                        }
                    }
                }

                // Underlying Real BasicTextField (Invisible/zero-alpha when obscured for typing & keyboard)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPasswordVisible) KeyboardType.Text else KeyboardType.Password,
                        imeAction = if (onUnlock != null) ImeAction.Done else ImeAction.Default
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onUnlock?.invoke()
                        }
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(accentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .alpha(if (isPasswordVisible) 1f else 0.001f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Trailing Eye Icon Toggle
            IconButton(
                onClick = { isPasswordVisible = !isPasswordVisible },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                    tint = if (isPasswordVisible) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Optional Unlock Button
            if (onUnlock != null && unlockButtonText.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onUnlock,
                    enabled = enabled && value.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = unlockButtonText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
