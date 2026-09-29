package com.petal.browser.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

@Composable
fun PetalClearBrowsingDataDialog(
    onDismiss: () -> Unit,
    onPerformClear: (cache: Boolean, cookies: Boolean, storage: Boolean, autofill: Boolean, permissions: Boolean) -> Unit
) {
    var clearCache by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearStorage by remember { mutableStateOf(true) }
    var clearAutofill by remember { mutableStateOf(false) }
    var clearPermissions by remember { mutableStateOf(false) }
    var splitMenuExpanded by remember { mutableStateOf(false) }

    com.petal.browser.ui.containment.PetalMaterialAlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        icon = {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = stringResource(R.string.ui_clear_browsing_data),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.ui_select_browsing_data_and_storage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        ExpressiveClearOptionRow(
                            icon = Icons.Rounded.Image,
                            label = stringResource(R.string.ui_cached_images_and_files),
                            checked = clearCache,
                            onCheckedChange = { clearCache = it }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        ExpressiveClearOptionRow(
                            icon = Icons.Rounded.Cookie,
                            label = stringResource(R.string.ui_cookies_and_site_data),
                            checked = clearCookies,
                            onCheckedChange = { clearCookies = it }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        ExpressiveClearOptionRow(
                            icon = Icons.Rounded.Storage,
                            label = stringResource(R.string.ui_site_databases_webstorage),
                            checked = clearStorage,
                            onCheckedChange = { clearStorage = it }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        ExpressiveClearOptionRow(
                            icon = Icons.Rounded.Password,
                            label = stringResource(R.string.ui_autofill_passwords_logins),
                            checked = clearAutofill,
                            onCheckedChange = { clearAutofill = it }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        ExpressiveClearOptionRow(
                            icon = Icons.Rounded.Security,
                            label = stringResource(R.string.ui_site_permissions_location_etc),
                            checked = clearPermissions,
                            onCheckedChange = { clearPermissions = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Box {
                ExpressiveSplitButton(
                    label = stringResource(R.string.ui_clear),
                    onPrimaryClick = {
                        onPerformClear(clearCache, clearCookies, clearStorage, clearAutofill, clearPermissions)
                    },
                    onMenuClick = { splitMenuExpanded = !splitMenuExpanded },
                    icon = Icons.Rounded.DeleteSweep,
                    isMenuExpanded = splitMenuExpanded,
                    variant = SplitButtonVariant.FILLED,
                    height = 44.dp
                )

                com.petal.browser.ui.containment.PetalPopupMenu(
                    expanded = splitMenuExpanded,
                    onDismissRequest = { splitMenuExpanded = false }
                ) {
                    PetalExpressiveMenuItem(
                        text = stringResource(R.string.ui_clear_all_time),
                        leadingIcon = {
                            Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            splitMenuExpanded = false
                            onPerformClear(true, true, true, true, true)
                        }
                    )
                    PetalExpressiveMenuItem(
                        text = stringResource(R.string.ui_clear_cache_only),
                        leadingIcon = {
                            Icon(Icons.Rounded.Cached, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        onClick = {
                            splitMenuExpanded = false
                            onPerformClear(true, false, false, false, false)
                        }
                    )
                    PetalExpressiveMenuItem(
                        text = stringResource(R.string.ui_clear_cookies_cache),
                        leadingIcon = {
                            Icon(Icons.Rounded.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        onClick = {
                            splitMenuExpanded = false
                            onPerformClear(true, true, false, false, false)
                        }
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = stringResource(R.string.ui_cancel),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    )
}

@Composable
private fun ExpressiveClearOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "ClearOptionRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = { onCheckedChange(!checked) }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .petalTouchFeedback(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            com.petal.browser.ui.containment.PetalGroupIconBadge(
                icon = icon,
                variant = if (checked) com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY else com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL,
                size = 36.dp,
                iconSize = 18.dp,
                shape = RoundedCornerShape(10.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }
}
