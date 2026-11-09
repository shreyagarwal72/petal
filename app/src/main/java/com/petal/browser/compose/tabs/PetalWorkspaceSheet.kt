package com.petal.browser.compose.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import com.petal.browser.ui.kit.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.petal.browser.ui.kit.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.petal.browser.ui.kit.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.containment.PetalSheet

/**
 * Zen Browser-style Workspace Sheet.
 * Bottom sheet interface for creating and customizing workspaces (name, accent color, and vector icon).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PetalWorkspaceSheet(
    targetWorkspace: PetalWorkspace? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, iconName: String, colorHex: String) -> Unit,
    onDelete: ((PetalWorkspace) -> Unit)? = null
) {
    val isEditMode = targetWorkspace != null
    var name by remember(targetWorkspace) {
        mutableStateOf(targetWorkspace?.name ?: "")
    }
    var selectedColorHex by remember(targetWorkspace) {
        mutableStateOf(targetWorkspace?.colorHex ?: WorkspaceColorPresets.first())
    }
    var selectedIconName by remember(targetWorkspace) {
        mutableStateOf(targetWorkspace?.iconName ?: WorkspaceIconPresets.first())
    }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    PetalSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isEditMode) "Edit Workspace" else "New Workspace",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                val activeColor = remember(selectedColorHex) {
                    try {
                        Color(android.graphics.Color.parseColor(selectedColorHex))
                    } catch (_: Exception) {
                        Color(0xFF3B82F6)
                    }
                }

                // Mini preview pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = activeColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, activeColor.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = getWorkspaceIconVector(selectedIconName),
                            contentDescription = null,
                            tint = activeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = name.ifBlank { "Workspace" },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = activeColor
                        )
                    }
                }
            }

            // Name field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Workspace Name") },
                placeholder = { Text("e.g. Work, Research, Shopping") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Color Palette Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Accent Color",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WorkspaceColorPresets.forEach { hex ->
                        val isSelected = (hex == selectedColorHex)
                        val swatchColor = remember(hex) {
                            try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (_: Exception) {
                                Color(0xFF3B82F6)
                            }
                        }

                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            animationSpec = spring(),
                            label = "color_scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(48.dp) // Minimum 48dp touch target
                                .scale(scale)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .clickable(
                                    role = Role.RadioButton,
                                    onClick = { selectedColorHex = hex }
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Icon Grid Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val activeColor = remember(selectedColorHex) {
                    try {
                        Color(android.graphics.Color.parseColor(selectedColorHex))
                    } catch (_: Exception) {
                        Color(0xFF3B82F6)
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WorkspaceIconPresets.forEach { iconName ->
                        val isSelected = (iconName == selectedIconName)
                        val iconShape = RoundedCornerShape(12.dp)

                        val iconBgColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                activeColor.copy(alpha = 0.20f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                            },
                            animationSpec = spring(),
                            label = "icon_bg"
                        )

                        val iconBorderColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                activeColor
                            } else {
                                Color.Transparent
                            },
                            animationSpec = spring(),
                            label = "icon_border"
                        )

                        Surface(
                            modifier = Modifier
                                .size(48.dp) // Minimum 48dp touch target
                                .clip(iconShape)
                                .clickable(
                                    role = Role.RadioButton,
                                    onClick = { selectedIconName = iconName }
                                ),
                            shape = iconShape,
                            color = iconBgColor,
                            border = if (isSelected) BorderStroke(1.5.dp, iconBorderColor) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = getWorkspaceIconVector(iconName),
                                    contentDescription = iconName,
                                    tint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Delete option for existing custom workspace
            if (isEditMode && targetWorkspace != null && !targetWorkspace.isDefault) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Workspace", fontWeight = FontWeight.SemiBold)
                }
            }

            // Actions row: Cancel & Save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        onSave(name.trim(), selectedIconName, selectedColorHex)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = name.isNotBlank()
                ) {
                    Text(if (isEditMode) "Save Changes" else "Create Workspace")
                }
            }
        }
    }

    if (showDeleteConfirmDialog && targetWorkspace != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Workspace?") },
            text = {
                Text("Tabs inside '${targetWorkspace.name}' will automatically be moved to your default 'General' workspace.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete?.invoke(targetWorkspace)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
