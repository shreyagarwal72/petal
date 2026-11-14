package com.petal.browser.ui.containment

import com.petal.browser.ui.theme.ExpressiveButtonShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.preference.PreferenceManager
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.petal.browser.ui.theme.layout.cornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.components.PetalSpring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    actionColor: Color = MaterialTheme.colorScheme.primary,
) {
    SnackbarHost(hostState, modifier) { data ->
        val message = data.visuals.message
        val (accent, badgeContainer) = when {
            listOf("error", "failed", "crashed", "disabled").any { message.contains(it, true) } -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
            listOf("saved", "success", "installed", "enabled", "done").any { message.contains(it, true) } -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
        }
        val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
        SwipeToDismissBox(
            state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) { data.dismiss(); true } else false
            }),
            backgroundContent = {},
            modifier = Modifier.pointerInput(data) {
                detectVerticalDragGestures { _, dy -> if (dy > 12f) data.dismiss() }
            }
        ) {
            Surface(
                shape = ExpressiveButtonShape.shadowShape,
                color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = if (isGlass) 0.dp else 6.dp,
                shadowElevation = 6.dp,
                border = null,
                modifier = (if (isGlass) Modifier.liquidGlassChrome(ExpressiveButtonShape, enabled = true, sheenIntensity = 0.65f) else Modifier).clip(ExpressiveButtonShape)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PetalGroupIconBadge(Icons.Rounded.Info, container = badgeContainer, tint = accent, size = 36.dp, iconSize = 18.dp)
                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, lineHeight = 18.sp), maxLines = 2)
                    data.visuals.actionLabel?.let { label ->
                        TextButton(onClick = data::performAction, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), colors = ButtonDefaults.textButtonColors(contentColor = actionColor)) {
                            Text(label, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (data.visuals.withDismissAction) {
                        IconButton(onClick = data::dismiss, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_dismiss), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PetalPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(26.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 6.dp,
    border: BorderStroke? = null,
    transformOrigin: androidx.compose.ui.graphics.TransformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = androidx.compose.runtime.remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)

    val menuScale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.82f,
        animationSpec = if (expanded) PetalSpring.menuScale() else PetalSpring.menuCloseScale(),
        label = "menu_scale"
    )
    val itemAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (expanded) PetalSpring.menuItemFade() else PetalSpring.menuCloseFade(),
        label = "menu_item_alpha"
    )

    // The glass lives INSIDE the menu content. Putting liquidGlassChrome on DropdownMenu's own
    // modifier drew the panel outside the popup's real bounds (an offset ghost rectangle).
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .padding(vertical = 4.dp)
            .graphicsLayer {
                scaleX = menuScale
                scaleY = menuScale
                alpha = itemAlpha
                this.transformOrigin = transformOrigin
            },
        shape = shape,
        containerColor = if (isGlass) Color.Transparent else containerColor,
        tonalElevation = if (isGlass) 0.dp else tonalElevation,
        shadowElevation = if (isGlass) 0.dp else shadowElevation,
        border = if (isGlass) null else border,
    ) {
        if (isGlass) {
            // live = true: re-records the screen behind the popup every frame.
            com.petal.browser.ui.glass.PetalGlassContainer(enabled = true, live = true) {
                Column(Modifier.liquidGlassChrome(shape = shape, enabled = true, sheenIntensity = sheen)) {
                    content()
                }
            }
        } else {
            content()
        }
    }
}

@Composable
fun PetalMenuDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    thickness: Dp = 1.dp
) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        color = color,
        thickness = thickness
    )
}

@Composable
fun PetalPopupMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    iconContainerColor: Color? = null,
    iconContentColor: Color? = null,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
) {
    val glassMenu = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val defaultIconBg = when {
        destructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = if (glassMenu) 0.6f else 1f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (glassMenu) 0.55f else 1f)
    }
    val defaultIconFg = when {
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val wrappedLeadingIcon: (@Composable () -> Unit)? = leadingIcon?.let { icon ->
        {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconContainerColor ?: defaultIconBg,
                contentColor = iconContentColor ?: defaultIconFg,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    icon()
                }
            }
        }
    }

    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 52.dp)
            .bouncyClickable(scaleDown = 0.97f, onClick = onClick),
        leadingIcon = wrappedLeadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
    )
}

@Composable
fun PetalPopupMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    iconContainerColor: Color? = null,
    iconContentColor: Color? = null,
) {
    PetalPopupMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified
            )
        },
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        destructive = destructive,
        iconContainerColor = iconContainerColor,
        iconContentColor = iconContentColor,
    )
}

@Composable
fun PetalAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    confirmText: String = "OK",
    onConfirm: () -> Unit,
    dismissText: String? = "Cancel",
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Rounded.Info,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    PetalMaterialAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        icon = icon?.let { ic ->
            {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = null,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(ic, contentDescription = null, modifier = Modifier.size(26.dp))
                    }
                }
            }
        },
        title = {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = ExpressiveButtonShape
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = if (message != null || content != null) {
            {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (message != null) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    content?.invoke(this)
                }
            }
        } else null,
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                shape = if (dismissText != null) {
                    RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp)
                } else {
                    RoundedCornerShape(50.dp)
                },
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(confirmText, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = dismissText?.let { label ->
            {
                Button(
                    onClick = { onDismiss?.invoke() ?: onDismissRequest() },
                    shape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}

@Composable
fun PetalMaterialAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.extraLarge,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    iconContentColor: Color = MaterialTheme.colorScheme.secondary,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)

    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.SideEffect {
            dialogWindow?.let { win ->
                if (isGlass) {
                    win.setDimAmount(0.12f)
                }
            }
        }
        com.petal.browser.ui.glass.PetalGlassContainer(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            enabled = isGlass,
            live = true
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen),
                shape = shape,
                color = if (isGlass) petalGroupSurfaceColor() else containerColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = if (isGlass) 0.dp else 12.dp,
                border = null,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (icon != null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                                icon()
                            }
                        }
                    }
                    if (title != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides titleContentColor,
                            LocalTextStyle provides MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        ) {
                            title()
                        }
                    }
                    if (text != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides textContentColor,
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium
                        ) {
                            text()
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissButton != null) {
                            Box(modifier = Modifier.weight(1f)) {
                                dismissButton()
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PetalDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.extraLarge,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = androidx.compose.runtime.remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)

    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        var isEntering by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { isEntering = true }
        val dialogScale by animateFloatAsState(
            targetValue = if (isEntering) 1f else 0.85f,
            animationSpec = if (isEntering) PetalSpring.dialogScaleIn() else PetalSpring.dialogScaleOut(),
            label = "dialog_scale"
        )
        val dialogAlpha by animateFloatAsState(
            targetValue = if (isEntering) 1f else 0f,
            animationSpec = if (isEntering) PetalSpring.dialogFadeIn() else PetalSpring.dialogFadeOut(),
            label = "dialog_alpha"
        )

        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.SideEffect {
            dialogWindow?.let { win ->
                if (isGlass) {
                    win.setDimAmount(0.12f)
                }
            }
        }
        com.petal.browser.ui.glass.PetalGlassContainer(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .graphicsLayer {
                    scaleX = dialogScale
                    scaleY = dialogScale
                    alpha = dialogAlpha
                },
            enabled = isGlass,
            live = true
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen),
                shape = shape,
                color = if (isGlass) petalGroupSurfaceColor() else containerColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = if (isGlass) 0.dp else 12.dp,
                border = null,
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            }
        }
    }
}

/**
 * Shape calculation for stacked tonal content blocks within sheets and dialogs.
 * Following PixelPlayer specifications:
 * - Single item (count <= 1): 18.dp on all corners
 * - Top item (index == 0): 18.dp outer (top), 6.dp inner (bottom)
 * - Bottom item (index == count - 1): 6.dp inner (top), 18.dp outer (bottom)
 * - Middle item: 6.dp on all corners
 */
fun petalStackedBlockShape(index: Int, count: Int): RoundedCornerShape {
    return when {
        count <= 1 -> RoundedCornerShape(18.dp)
        index == 0 -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
        else -> RoundedCornerShape(6.dp)
    }
}

/**
 * Stacked tonal content block container conforming to PixelPlayer bottom sheet specifications.
 * Uses position-aware corners, tonal container background, zero border stroke, and 4dp spacing.
 */
@Composable
fun PetalStackedBlock(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = petalStackedBlockShape(index, count)
    val resolvedContainerColor = containerColor ?: if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val resolvedContentColor = contentColor ?: if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = shape,
        color = resolvedContainerColor,
        contentColor = resolvedContentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = null,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.bouncyClickable(scaleDown = 0.98f, onClick = onClick)
                } else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            content = content
        )
    }
}

/**
 * Prominent sheet header title badge styled as a pill in surfaceContainerLowest with primary text.
 * Matches PixelPlayer TimerOptionsBottomSheet / LibrarySortBottomSheet specifications.
 */
@Composable
fun PetalSheetTitleBadge(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = ExpressiveButtonShape
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Connected asymmetric action button row for bottom sheets and dialogs.
 * Matches PixelPlayer specifications:
 * - Dismiss: surfaceContainerHighest fill, 50dp outer / 6dp inner corners
 * - Confirm: primary fill, 6dp inner / 50dp outer corners
 * - Spacing: 6.dp gap
 * - Borders: null (zero outline borders)
 */
@Composable
fun PetalActionRow(
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dismissText != null && onDismiss != null) {
            Button(
                onClick = onDismiss,
                enabled = dismissEnabled,
                shape = RoundedCornerShape(
                    topStart = 50.dp,
                    bottomStart = 50.dp,
                    topEnd = 6.dp,
                    bottomEnd = 6.dp
                ),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text(dismissText, fontWeight = FontWeight.SemiBold)
            }
        }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            shape = if (dismissText != null && onDismiss != null) {
                RoundedCornerShape(
                    topStart = 6.dp,
                    bottomStart = 6.dp,
                    topEnd = 50.dp,
                    bottomEnd = 50.dp
                )
            } else {
                RoundedCornerShape(50.dp)
            },
            colors = if (destructive) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
        ) {
            Text(confirmText, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Shared Bottom Sheet conforming to PixelPlayer bottom sheet specifications:
 * - Container: surfaceContainerHigh (or transparent when liquid glass active)
 * - Shape: RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
 * - Drag handle: standard BottomSheetDefaults.DragHandle()
 * - Title: optional pill badge in surfaceContainerLowest with primary text
 * - Content: Column with 18.dp horizontal padding (when modern title/actionContent present)
 * - Action row: optional connected asymmetric action row with 6dp spacing
 * - Borders: 0 borders
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    title: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actionContent: (@Composable RowScope.() -> Unit)? = null,
    ownsGlassBackdrop: Boolean = true,
    containerColor: Color? = null,
    scrimColor: Color? = null,
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val isMiuix = isMiuixEnabled()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    val sheetShape = if (isMiuix) {
        com.petal.browser.ui.miuix.theme.MiuiXSquircleShape(
            smoothing = 0.68f,
            topStart = androidx.compose.foundation.shape.CornerSize(28.dp),
            topEnd = androidx.compose.foundation.shape.CornerSize(28.dp),
            bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
            bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp)
        )
    } else {
        com.petal.browser.ui.theme.layout.PetalLayoutState.settings.shapeType.cornerShape(
            topStart = 28.dp, topEnd = 28.dp, bottomEnd = 0.dp, bottomStart = 0.dp,
            multiplier = com.petal.browser.ui.theme.layout.PetalLayoutState.displayCorners
        )
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val fallbackColor = if (isMiuix) {
        if (dark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    } else {
        if (dark) Color(0xE61A1C1E) else Color(0xE6F2F4F7)
    }

    val ownWallpaper = com.petal.browser.ui.glass.rememberPetalGlassWallpaper()
    val activeWallpaper = if (isGlass && ownsGlassBackdrop) ownWallpaper else com.petal.browser.ui.glass.LocalPetalGlassWallpaper.current

    val resolvedContainerColor = containerColor ?: if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh
    val resolvedScrimColor = scrimColor ?: if (isGlass) Color.Black.copy(alpha = 0.14f) else BottomSheetDefaults.ScrimColor

    var sheetDragOffset by remember { mutableStateOf(0f) }
    val animatedDragOffset by animateFloatAsState(
        targetValue = sheetDragOffset,
        animationSpec = PetalSpring.sheetDragSettle(),
        label = "sheet_drag_settle"
    )

    val isMoving = sheetState.targetValue != sheetState.currentValue || sheetDragOffset > 0f
    androidx.compose.runtime.DisposableEffect(isMoving) {
        val handle = if (isMoving) com.petal.browser.motion.MotionGate.startTransition("sheet_settle") else null
        onDispose { handle?.release() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = sheetShape,
        containerColor = resolvedContainerColor,
        scrimColor = resolvedScrimColor,
        dragHandle = null,
        tonalElevation = 0.dp,
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0) },
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val view = androidx.compose.ui.platform.LocalView.current
        val fallbackStatusTop = WindowInsets.statusBars.getTop(density)
        val statusTopPx = androidx.compose.runtime.remember(view, fallbackStatusTop) {
            androidx.core.view.ViewCompat.getRootWindowInsets(view)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top
                ?: fallbackStatusTop
        }
        var statusOverlapPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
        CompositionLocalProvider(com.petal.browser.ui.glass.LocalPetalGlassWallpaper provides activeWallpaper) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = animatedDragOffset }
                    .onGloballyPositioned {
                        val top = it.positionInWindow().y
                        statusOverlapPx = (statusTopPx - top).toInt().coerceIn(0, statusTopPx)
                    }
            ) {
                if (isGlass && containerColor == null) {
                    if (ownsGlassBackdrop) PetalSheetGlassBackdrop(ownWallpaper, Modifier.matchParentSize())
                    val ready = activeWallpaper?.ready == true && activeWallpaper.hasRecorded
                    // Cross-fade the stand-in out instead of cutting it: the wallpaper becomes
                    // ready a frame or two after the sheet appears, and a hard swap read as a flash.
                    val standInAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (ready) 0f else 1f,
                        animationSpec = androidx.compose.animation.core.tween(if (ready) 180 else 0),
                        label = "sheet_glass_standin"
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .then(
                                if (standInAlpha > 0.01f)
                                    Modifier.background(fallbackColor.copy(alpha = fallbackColor.alpha * standInAlpha), sheetShape)
                                else Modifier
                            )
                            .liquidGlassChrome(shape = sheetShape, enabled = true, sheenIntensity = sheen)
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = with(density) { statusOverlapPx.toDp() })
                        .navigationBarsPadding()
                ) {
                    if (dragHandle != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            if (sheetDragOffset > 180f) {
                                                onDismissRequest()
                                            } else {
                                                sheetDragOffset = 0f
                                            }
                                        },
                                        onDragCancel = {
                                            sheetDragOffset = 0f
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            sheetDragOffset = (sheetDragOffset + dragAmount).coerceAtLeast(0f)
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) { dragHandle() }
                    }
                    if (titleContent != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            titleContent()
                        }
                    } else if (title != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            PetalSheetTitleBadge(title = title)
                        }
                    }
                    val hasModernHeaderOrFooter = title != null || titleContent != null || actionContent != null
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .then(if (hasModernHeaderOrFooter) Modifier.padding(horizontal = 18.dp) else Modifier),
                        content = content
                    )
                    if (actionContent != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = actionContent
                        )
                    }
                }
            }
        }
    }
}
