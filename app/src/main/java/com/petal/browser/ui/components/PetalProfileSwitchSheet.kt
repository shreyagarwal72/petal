/*
 * PetalProfileSwitchSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive profile switcher bottom sheet (Issue #23).
 * Allows users to easily switch between profiles (Personal, Work, School, etc.),
 * view avatar status, edit names/colors, or add new profiles.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.petal.browser.account.GoogleAccountManager
import com.petal.browser.account.GoogleUserProfile
import com.petal.browser.account.PetalAvatarCropSheet
import com.petal.browser.account.ProfileAvatarDisplay
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.profile.PetalProfile
import com.petal.browser.profile.PetalProfileManager
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalProfileSwitchSheet(
    onDismissRequest: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onSelectProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val active = PetalProfileManager.activeProfile
    val profiles = PetalProfileManager.profiles

    var isCreatingNew by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#4285F4") }
    var selectedAvatarPreset by remember { mutableStateOf("petal_flower") }

    // Custom profile picture state for the create form (Issue #23 avatar wiring).
    var pendingCropImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCustomAvatarUri by remember { mutableStateOf<String?>(null) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingCropImageUri = uri
        }
    }

    val presetColors = listOf(
        "#4285F4" to "Google Blue",
        "#34A853" to "Emerald Green",
        "#FBBC05" to "Amber Gold",
        "#EA4335" to "Crimson Red",
        "#9C27B0" to "Violet Purple",
        "#00BCD4" to "Oceanic Cyan",
        "#24292E" to "Dark Slate",
        "#FF5722" to "Vibrant Orange"
    )

    com.petal.browser.ui.containment.PetalSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.ui_profiles),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.ui_switch_or_create_browsing_profiles),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        onDismissRequest()
                        onOpenAccountSettings()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ManageAccounts,
                        contentDescription = stringResource(R.string.ui_manage_account),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            if (!isCreatingNew) {
                // Profile List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        val isSelected = profile.id == active.id
                        val profileColor = profile.getComposeColor()

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                    onDismissRequest()
                                    onSelectProfile(profile.id)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Profile color ring & indicator
                                com.petal.browser.ui.containment.PetalGroupIconBadge(
                                    shape = RoundedCornerShape(12.dp),
                                    containerColor = profileColor.copy(alpha = 0.22f),
                                    contentColor = profileColor,
                                    size = 42.dp,
                                    iconSize = 22.dp
                                ) {
                                    if (profile.customAvatarUri != null) {
                                        AsyncImage(
                                            model = profile.customAvatarUri,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (profile.isDefault) Icons.Rounded.Person else Icons.Rounded.FolderShared,
                                            contentDescription = null,
                                            tint = profileColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (profile.isDefault) "Default Profile" else "Custom Profile",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.ui_active),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else if (!profile.isDefault) {
                                    IconButton(
                                        onClick = {
                                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                            PetalProfileManager.deleteProfile(context, profile.id)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = stringResource(R.string.ui_delete),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Add Profile Button
                FilledTonalButton(
                    onClick = {
                        isCreatingNew = true
                        newProfileName = "Profile ${profiles.size + 1}"
                        pendingCropImageUri = null
                        pendingCustomAvatarUri = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_add_new_profile))
                }
            } else {
                // Create New Profile Form
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Tappable circular avatar area — opens the system photo picker.
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(selectedColorHex)).copy(alpha = 0.2f))
                                .clickable {
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                    avatarPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (pendingCustomAvatarUri != null) {
                                AsyncImage(
                                    model = pendingCustomAvatarUri,
                                    contentDescription = stringResource(R.string.ui_profile_picture),
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = stringResource(R.string.ui_profile_picture),
                                    tint = Color(android.graphics.Color.parseColor(selectedColorHex)),
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            // Camera badge to signal the avatar is tappable.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoCamera,
                                    contentDescription = stringResource(R.string.ui_change_profile_picture),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text(stringResource(R.string.ui_profile_name)) },
                        placeholder = { Text(stringResource(R.string.ui_e_g_work_school_research)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Text(
                        text = stringResource(R.string.ui_accent_color),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        presetColors.take(6).forEach { (hex, _) ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            val isColorSelected = selectedColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColorHex = hex }
                                    .then(
                                        if (isColorSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isColorSelected) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isCreatingNew = false
                                pendingCropImageUri = null
                                pendingCustomAvatarUri = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.ui_cancel))
                        }

                        Button(
                            onClick = {
                                if (newProfileName.isNotBlank()) {
                                    val newProfile = PetalProfileManager.createProfile(
                                        context = context,
                                        name = newProfileName,
                                        colorHex = selectedColorHex,
                                        avatarPresetId = selectedAvatarPreset
                                    )
                                    val avatarUri = pendingCustomAvatarUri
                                    if (avatarUri != null) {
                                        PetalProfileManager.updateProfile(
                                            context,
                                            newProfile.copy(customAvatarUri = avatarUri)
                                        )
                                    }
                                    isCreatingNew = false
                                    pendingCropImageUri = null
                                    pendingCustomAvatarUri = null
                                    onDismissRequest()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.ui_create))
                        }
                    }
                }
            }
        }
    }

    // Avatar crop sheet — opened after a photo is picked from the create-profile form.
    val cropImageUri = pendingCropImageUri
    if (cropImageUri != null) {
        PetalAvatarCropSheet(
            imageUri = cropImageUri,
            onDismiss = { pendingCropImageUri = null },
            onAvatarCropped = { pendingCropImageUri = null },
            onSaveBitmap = { bitmap ->
                pendingCustomAvatarUri = savePetalProfileAvatarBitmap(context, bitmap)
            }
        )
    }
}

/**
 * Persists a cropped profile-picture bitmap to local app storage and returns its file URI
 * string, suitable for [PetalProfile.customAvatarUri]. Each save gets a unique filename so
 * picking a new photo for one profile never clobbers another profile's saved avatar.
 */
private fun savePetalProfileAvatarBitmap(context: android.content.Context, bitmap: android.graphics.Bitmap): String {
    val file = java.io.File(context.filesDir, "petal_profile_avatar_${System.currentTimeMillis()}.png")
    java.io.FileOutputStream(file).use { out ->
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    }
    return Uri.fromFile(file).toString()
}
