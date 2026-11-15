package com.petal.browser.ui.miuix.containment

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * Adapter over the real Miuix rows: [ArrowPreference] when [showChevron] is set (the arrow is
 * drawn by the library), [BasicComponent] otherwise (trailing control as the end action). Rows
 * inside a Miuix Card carry no dividers, exactly like InstallerX, so [showDivider] is accepted for
 * source compatibility only.
 */
@Composable
fun MiuiXPreferenceRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContainerColor: Color? = null,
    iconTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    @Suppress("UNUSED_PARAMETER") showDivider: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val start: (@Composable () -> Unit)? = if (icon != null) {
        {
            MiuiXBadge(
                icon = icon,
                containerColor = iconContainerColor,
                iconTint = iconTint,
            )
        }
    } else {
        null
    }

    if (showChevron && trailing == null) {
        ArrowPreference(
            title = title,
            modifier = modifier,
            summary = subtitle,
            startAction = start,
            onClick = onClick,
            enabled = enabled,
        )
    } else if (trailing != null) {
        BasicComponent(
            modifier = modifier,
            title = title,
            summary = subtitle,
            startAction = start,
            endActions = { trailing() },
            onClick = onClick,
            enabled = enabled,
        )
    } else {
        BasicComponent(
            modifier = modifier,
            title = title,
            summary = subtitle,
            startAction = start,
            onClick = onClick,
            enabled = enabled,
        )
    }
}

/** Adapter over the real Miuix [SwitchPreference] (title, summary, switch and press feedback). */
@Composable
fun MiuiXSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContainerColor: Color? = null,
    iconTint: Color? = null,
    @Suppress("UNUSED_PARAMETER") showDivider: Boolean = true,
    enabled: Boolean = true,
) {
    val start: (@Composable () -> Unit)? = if (icon != null) {
        { MiuiXBadge(icon = icon, containerColor = iconContainerColor, iconTint = iconTint) }
    } else {
        null
    }
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        modifier = modifier,
        summary = subtitle,
        startAction = start,
        enabled = enabled,
    )
}
