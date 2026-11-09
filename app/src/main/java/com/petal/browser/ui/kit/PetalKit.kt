package com.petal.browser.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.components.PetalSpring
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalChipShape
import com.petal.browser.ui.containment.petalContainmentShape
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.theme.layout.PetalLayoutState
import com.petal.browser.ui.theme.layout.PetalShapeType
import com.petal.browser.ui.theme.layout.cornerShape
import com.petal.browser.ui.theme.layout.pillShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.FilledTonalButton as M3FilledTonalButton
import androidx.compose.material3.Card as M3Card
import androidx.compose.material3.FilterChip as M3FilterChip
import androidx.compose.material3.TextField as M3TextField
import androidx.compose.material3.OutlinedButton as M3OutlinedButton
import androidx.compose.material3.TextButton as M3TextButton

/*
 * Petal Material 3 Expressive kit.
 *
 * Drop-in replacements for the raw Material 3 controls with IDENTICAL names and parameters (plus an
 * optional `glass` flag), so a screen adopts them by changing one import
 * (androidx.compose.material3.X -> com.petal.browser.ui.kit.X). They add:
 *   - live Appearance settings: Shapes Type and Corners Size (PetalLayoutState) drive every default
 *     shape; buttons are pills that morph their corners on press (PetalSpring.press)
 *   - live Liquid Glass, same switches containments use: glass surface + liquidGlassChrome when on
 *   - filter chips with the containment chip shape and an automatic check mark
 * A caller that passes an explicit `shape`/`colors` keeps it. Calls whose caller already draws glass
 * itself are marked `glass = false` so the effect is never doubled.
 */

/**
 * Set to false around a subtree whose parent already draws liquid glass for these controls, so the
 * effect is never applied twice. Individual calls can also pass `glass = false`.
 */
val LocalPetalKitGlass = compositionLocalOf { true }

/** Live liquid-glass state for kit controls (same switch containments use). */
@Composable
private fun kitGlass(requested: Boolean): Boolean =
    requested && LocalPetalKitGlass.current && isLiquidGlassEnabled("sp_liquid_glass_containments")

/**
 * Shape that follows the live Appearance settings (Shapes Type + Corners Size) and morphs on press.
 * ROUNDED animates a pill -> squarer corner; other styles (cut, squircle, wavy, scoop...) swap between a
 * pill and a tighter corner in their own style, since those paths cannot interpolate.
 */
@Composable
private fun morphingShape(
    source: MutableInteractionSource,
    explicit: Shape?,
    pressedFactor: Float = 0.56f,
): Shape {
    if (explicit != null) return explicit
    val type = PetalLayoutState.settings.shapeType
    val scale = PetalLayoutState.displayCorners
    val pressed by source.collectIsPressedAsState()
    val restPercent = (50f * scale).toInt().coerceIn(8, 50)
    val pressedPercent = (restPercent * pressedFactor).toInt().coerceIn(6, restPercent)
    val percent by animateIntAsState(
        targetValue = if (pressed) pressedPercent else restPercent,
        animationSpec = PetalSpring.press(),
        label = "PetalKitShapeMorph",
    )
    return if (type == PetalShapeType.ROUNDED) {
        RoundedCornerShape(percent = percent.coerceIn(0, 50))
    } else if (pressed) {
        type.cornerShape(14.dp, multiplier = scale)
    } else {
        type.pillShape()
    }
}

/** Container for a filled kit control: glass surface when liquid glass is on, otherwise the given colour. */
@Composable
private fun glassContainer(glass: Boolean, normal: Color): Color =
    if (glass) petalGroupSurfaceColor() else normal

@Composable
private fun Modifier.kitGlassChrome(glass: Boolean, shape: Shape, sheen: Float = 0.45f): Modifier =
    if (glass) this.liquidGlassChrome(shape = shape, enabled = true, sheenIntensity = sheen, pressFx = true) else this

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    glass: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val resolved = morphingShape(source, shape)
    val useGlass = kitGlass(glass)
    M3Button(
        onClick = onClick,
        modifier = modifier.kitGlassChrome(useGlass, resolved),
        enabled = enabled,
        shape = resolved,
        colors = if (useGlass) colors.copy(containerColor = glassContainer(true, colors.containerColor)) else colors,
        elevation = if (useGlass) null else elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    glass: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val resolved = morphingShape(source, shape)
    val useGlass = kitGlass(glass)
    M3FilledTonalButton(
        onClick = onClick,
        modifier = modifier.kitGlassChrome(useGlass, resolved),
        enabled = enabled,
        shape = resolved,
        colors = if (useGlass) colors.copy(containerColor = glassContainer(true, colors.containerColor)) else colors,
        elevation = if (useGlass) null else elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    glass: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val resolved = morphingShape(source, shape)
    val useGlass = kitGlass(glass)
    M3OutlinedButton(
        onClick = onClick,
        modifier = modifier.kitGlassChrome(useGlass, resolved),
        enabled = enabled,
        shape = resolved,
        colors = if (useGlass) colors.copy(containerColor = glassContainer(true, colors.containerColor)) else colors,
        elevation = if (useGlass) null else elevation,
        border = if (useGlass) null else border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    glass: Boolean = true, // accepted for API symmetry; text buttons have no container to glass
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    M3TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = morphingShape(source, shape),
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

/** Circular icon button that squircles on press (M3 Expressive icon-button feel). */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    glass: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val shape = morphingShape(source, null, pressedFactor = 0.6f)
    // Only filled icon buttons (non-transparent container) become glass; plain ones stay transparent.
    val useGlass = kitGlass(glass) && colors.containerColor != Color.Transparent
    val container = if (useGlass) Color.Transparent else if (enabled) colors.containerColor else colors.disabledContainerColor
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .kitGlassChrome(useGlass, shape, 0.35f)
            .clip(shape)
            .background(container, shape)
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = source,
                indication = ripple(),
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

/** Filter chip with the expressive 12dp shape and an automatic check mark when selected. */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = petalChipShape(),
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
    glass: Boolean = true,
) {
    val useGlass = kitGlass(glass)
    val glassColors = if (useGlass) colors.copy(
        containerColor = petalGroupSurfaceColor(),
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) else colors
    val check: @Composable (() -> Unit)? = leadingIcon ?: if (selected) {
        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
    } else null
    M3FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.kitGlassChrome(useGlass, shape, 0.3f),
        enabled = enabled,
        leadingIcon = check,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = glassColors,
        elevation = if (useGlass) null else elevation,
        border = if (useGlass) null else border,
        interactionSource = interactionSource,
    )
}

// ── Text fields ─────────────────────────────────────────────────────────────────────────────

/** Expressive filled field: 16dp shape, no indicator line, tonal container. Callers may override. */
@Composable
fun petalTextFieldColors(glass: Boolean = true): TextFieldColors {
    val useGlass = kitGlass(glass)
    val glassFill = if (useGlass) petalGroupSurfaceColor() else null
    return TextFieldDefaults.colors(
    focusedContainerColor = glassFill ?: MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = glassFill ?: MaterialTheme.colorScheme.surfaceContainerHigh,
    disabledContainerColor = glassFill ?: MaterialTheme.colorScheme.surfaceContainerLow,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
    )
}

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = petalContainmentShape(16.dp),
    colors: TextFieldColors = petalTextFieldColors(),
    glass: Boolean = true,
) {
    M3TextField(
        value = value, onValueChange = onValueChange, modifier = modifier.kitGlassChrome(kitGlass(glass), shape, 0.3f), enabled = enabled,
        readOnly = readOnly, textStyle = textStyle, label = label, placeholder = placeholder,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
        supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
        maxLines = maxLines, minLines = minLines, interactionSource = interactionSource,
        shape = shape, colors = colors,
    )
}

@Composable
fun TextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = petalContainmentShape(16.dp),
    colors: TextFieldColors = petalTextFieldColors(),
    glass: Boolean = true,
) {
    M3TextField(
        value = value, onValueChange = onValueChange, modifier = modifier.kitGlassChrome(kitGlass(glass), shape, 0.3f), enabled = enabled,
        readOnly = readOnly, textStyle = textStyle, label = label, placeholder = placeholder,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
        supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
        maxLines = maxLines, minLines = minLines, interactionSource = interactionSource,
        shape = shape, colors = colors,
    )
}

// ── Cards ───────────────────────────────────────────────────────────────────────────────────

@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = petalContainmentShape(24.dp),
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    glass: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val useGlass = kitGlass(glass)
    M3Card(
        modifier = modifier.kitGlassChrome(useGlass, shape, 0.5f),
        shape = shape,
        colors = if (useGlass) colors.copy(containerColor = petalGroupSurfaceColor()) else colors,
        elevation = if (useGlass) CardDefaults.cardElevation(defaultElevation = 0.dp) else elevation,
        border = if (useGlass) null else border,
        content = content,
    )
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = petalContainmentShape(24.dp),
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    glass: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val useGlass = kitGlass(glass)
    M3Card(
        onClick = onClick,
        modifier = modifier.kitGlassChrome(useGlass, shape, 0.5f),
        enabled = enabled,
        shape = shape,
        colors = if (useGlass) colors.copy(containerColor = petalGroupSurfaceColor()) else colors,
        elevation = if (useGlass) CardDefaults.cardElevation(defaultElevation = 0.dp) else elevation,
        border = if (useGlass) null else border,
        interactionSource = interactionSource,
        content = content,
    )
}
