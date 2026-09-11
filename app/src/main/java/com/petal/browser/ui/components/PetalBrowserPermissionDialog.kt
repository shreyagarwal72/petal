package com.petal.browser.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalBrowserPermissionDialog {
    private const val PREF_LAST_PERMISSION_DIALOG_TIME = "sp_last_permission_dialog_time"
    private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L

    @JvmStatic
    fun hasUngrantedPermissions(context: Context): Boolean {
        fun granted(permission: String) =
            androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        val camera = granted(Manifest.permission.CAMERA)
        val mic = granted(Manifest.permission.RECORD_AUDIO)
        val location = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
        val media = if (Build.VERSION.SDK_INT >= 33) {
            granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        } else {
            granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        return !camera || !mic || !location || !notifications || !media
    }

    @JvmStatic
    fun shouldShow(activity: ComponentActivity): Boolean {
        if (!hasUngrantedPermissions(activity)) {
            return false
        }

        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
        val lastShownTime = sp.getLong(PREF_LAST_PERMISSION_DIALOG_TIME, 0L)
        val currentTime = System.currentTimeMillis()

        return (currentTime - lastShownTime) >= ONE_DAY_MILLIS
    }

    @JvmStatic
    fun markShown(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putLong(PREF_LAST_PERMISSION_DIALOG_TIME, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    fun show(activity: ComponentActivity) {
        if (activity.isFinishing || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) return

        // Record prompt timestamp to enforce at most 1 display every 24 hours
        markShown(activity)

        val dialog = BottomSheetDialog(activity)
        val view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PetalExpressiveTheme {
                    BrowserPermissionSheet(
                        onDone = {
                            try {
                                dialog.dismiss()
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
            dialog.behavior.apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
                isDraggable = true
            }
        }
        try {
            dialog.show()
        } catch (_: Exception) {}
    }
}

private data class PermissionEntry(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isGranted: Boolean,
    val onGrant: () -> Unit
)

@Composable
private fun BrowserPermissionSheet(onDone: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
    }

    fun granted(permission: String) =
        androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val permissionRefresh = refresh
    val camera = permissionRefresh.let { granted(Manifest.permission.CAMERA) }
    val microphone = permissionRefresh.let { granted(Manifest.permission.RECORD_AUDIO) }
    val location = permissionRefresh.let {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val notifications = Build.VERSION.SDK_INT < 33 || permissionRefresh.let { granted(Manifest.permission.POST_NOTIFICATIONS) }
    val media = if (Build.VERSION.SDK_INT >= 33) {
        permissionRefresh.let { granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) }
    } else {
        permissionRefresh.let { granted(Manifest.permission.READ_EXTERNAL_STORAGE) }
    }

    val missing = !camera || !microphone || !location || !notifications || !media
    val allGranted = !missing

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionItems = remember(camera, microphone, location, notifications, media) {
        val items = mutableListOf<PermissionEntry>()
        items.add(
            PermissionEntry(
                title = "Camera",
                description = "Video calls, QR scanning, and camera websites",
                icon = Icons.Outlined.Videocam,
                isGranted = camera,
                onGrant = { activity?.let { request.launch(arrayOf(Manifest.permission.CAMERA)) } }
            )
        )
        items.add(
            PermissionEntry(
                title = "Microphone",
                description = "Voice search, audio notes, and media calls",
                icon = Icons.Outlined.Mic,
                isGranted = microphone,
                onGrant = { activity?.let { request.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) } }
            )
        )
        items.add(
            PermissionEntry(
                title = "Location",
                description = "Maps and location-aware web experiences",
                icon = Icons.Outlined.LocationOn,
                isGranted = location,
                onGrant = {
                    activity?.let {
                        request.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }
            )
        )
        if (Build.VERSION.SDK_INT >= 33) {
            items.add(
                PermissionEntry(
                    title = "Notifications",
                    description = "Download progress, alerts, and web updates",
                    icon = Icons.Outlined.Notifications,
                    isGranted = notifications,
                    onGrant = { activity?.let { request.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) } }
                )
            )
            items.add(
                PermissionEntry(
                    title = "Photos and videos",
                    description = "Media picker, file uploads, and attachment selection",
                    icon = Icons.Outlined.PhotoLibrary,
                    isGranted = media,
                    onGrant = { activity?.let { request.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)) } }
                )
            )
        } else {
            items.add(
                PermissionEntry(
                    title = "Storage & Media",
                    description = "File uploads, image previews, and web downloads",
                    icon = Icons.Outlined.Folder,
                    isGranted = media,
                    onGrant = { activity?.let { request.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) } }
                )
            )
        }
        items
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zenith-style top drag handle
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )

            Spacer(Modifier.height(18.dp))

            // Zenith-style header shield icon
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Petal needs these permissions for websites to work properly, including video calls, voice search, maps, and file downloads.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(20.dp))

            // Zenith-style grouped permission list with connected shapes
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val totalCount = permissionItems.size
                permissionItems.forEachIndexed { index, item ->
                    val shape = getGroupItemShape(index, totalCount)
                    ZenithPermissionItemRow(
                        title = item.title,
                        description = item.description,
                        icon = item.icon,
                        isGranted = item.isGranted,
                        shape = shape,
                        onGrant = item.onGrant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (missing) {
                Text(
                    text = "You can change or grant any permission later in Android Settings.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Action Button: Zenith dynamic styling
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                AnimatedVisibility(
                    visible = allGranted,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Text(
                    text = if (allGranted) "Everything is Ready!" else "Continue",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ZenithPermissionItemRow(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onGrant: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isGranted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "bgColor"
    )

    Surface(
        onClick = if (!isGranted) onGrant else ({}),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
        shape = shape,
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isGranted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isGranted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }

            Spacer(Modifier.width(12.dp))

            if (isGranted) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.clickable(onClick = onGrant)
                ) {
                    Text(
                        text = "Grant",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
