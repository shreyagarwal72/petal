package com.petal.browser.ui.containment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                shape = CircleShape,
                color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = if (isGlass) 0.dp else 6.dp,
                shadowElevation = 6.dp,
                border = if (isGlass) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = if (isGlass) Modifier.liquidGlassChrome(CircleShape, enabled = true, sheenIntensity = 0.65f) else Modifier
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PetalGroupIconBadge(Icons.Filled.Info, container = badgeContainer, tint = accent, size = 36.dp, iconSize = 18.dp)
                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, lineHeight = 18.sp), maxLines = 2)
                    data.visuals.actionLabel?.let { label ->
                        TextButton(onClick = data::performAction, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), colors = ButtonDefaults.textButtonColors(contentColor = actionColor)) {
                            Text(label, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (data.visuals.withDismissAction) {
                        IconButton(onClick = data::dismiss, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ui_dismiss), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    tonalElevation: Dp = 6.dp,
    shadowElevation: Dp = 8.dp,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = androidx.compose.runtime.remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = if (isGlass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0.60f

    // The glass lives INSIDE the menu content. Putting liquidGlassChrome on DropdownMenu's own
    // modifier drew the panel outside the popup's real bounds (an offset ghost rectangle).
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.padding(vertical = 4.dp),
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
    message: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Info,
    destructive: Boolean = false,
    confirmText: String = "OK",
    dismissText: String? = "Cancel",
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = androidx.compose.runtime.remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = if (isGlass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0.60f
    val dialogShape = RoundedCornerShape(32.dp)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.liquidGlassChrome(shape = dialogShape, enabled = isGlass, sheenIntensity = sheen),
        icon = {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp)) }
            }
        },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(confirmText, fontWeight = FontWeight.Bold) }
        },
        dismissButton = dismissText?.let { label ->
            {
                TextButton(onClick = onDismissRequest, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
            }
        },
        shape = dialogShape,
        containerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
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
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(32.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    iconContentColor: Color = MaterialTheme.colorScheme.secondary,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = androidx.compose.runtime.remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = if (isGlass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0.60f

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = if (isGlass) petalGroupSurfaceColor() else containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

@Composable
fun PetalDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(32.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = androidx.compose.runtime.remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = if (isGlass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0.60f

    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
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
                border = if (isGlass) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            }
        }
    }
}
