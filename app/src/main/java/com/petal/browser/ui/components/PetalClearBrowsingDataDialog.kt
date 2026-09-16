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

    AlertDialog(
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
                text = "Clear Browsing Data",
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
                    text = "Select browsing data and storage to erase:",
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
                            label = "Cached images and files",
                            checked = clearCache,
                            onCheckedChange = { clearCache = it }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        ExpressiveClearOptionRow(
                            icon = Icons.Rounded.Cookie,
                            label = "Cookies and site data",
                            checked = clearCookies,
                            onCheckedChange = { clearCookies = it }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        ExpressiveClearOptionRow(
                            icon = Icons.Rounded.Storage,
                            label = "Site databases & WebStorage",
                            checked = clearStorage,
                            onCheckedChange = { clearStorage = it }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        ExpressiveClearOptionRow(
                            icon = Icons.Rounded.Password,
                            label = "Autofill passwords & logins",
                            checked = clearAutofill,
                            onCheckedChange = { clearAutofill = it }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        ExpressiveClearOptionRow(
                            icon = Icons.Rounded.Security,
                            label = "Site permissions (Location, etc.)",
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
                    label = "Clear",
                    onPrimaryClick = {
                        onPerformClear(clearCache, clearCookies, clearStorage, clearAutofill, clearPermissions)
                    },
                    onMenuClick = { splitMenuExpanded = !splitMenuExpanded },
                    icon = Icons.Rounded.DeleteSweep,
                    isMenuExpanded = splitMenuExpanded,
                    variant = SplitButtonVariant.FILLED,
                    height = 44.dp
                )

                DropdownMenu(
                    expanded = splitMenuExpanded,
                    onDismissRequest = { splitMenuExpanded = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    DropdownMenuItem(
                        text = { Text("Clear All Time", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            splitMenuExpanded = false
                            onPerformClear(true, true, true, true, true)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear Cache Only") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Cached, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        onClick = {
                            splitMenuExpanded = false
                            onPerformClear(true, false, false, false, false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear Cookies & Cache") },
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
                    text = "Cancel",
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
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
