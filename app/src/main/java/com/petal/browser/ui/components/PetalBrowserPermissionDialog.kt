package com.petal.browser.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalBrowserPermissionDialog {
    @JvmStatic
    fun shouldShow(activity: ComponentActivity): Boolean {
        fun granted(permission: String) = androidx.core.content.ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        return !granted(Manifest.permission.CAMERA) ||
            !granted(Manifest.permission.RECORD_AUDIO) ||
            !granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) ||
            (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.READ_MEDIA_IMAGES) && !granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) ||
            (Build.VERSION.SDK_INT < 33 && !granted(Manifest.permission.READ_EXTERNAL_STORAGE))
    }

    @JvmStatic
    fun show(activity: ComponentActivity) {
        if (activity.isFinishing || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) return
        val dialog = BottomSheetDialog(activity)
        val view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { PetalExpressiveTheme { BrowserPermissionSheet { dialog.dismiss() } } }
        }
        dialog.setContentView(view)
        dialog.setOnShowListener {
            // Give Compose a real, bounded viewport. Without an expanded sheet,
            // BottomSheetBehavior can consume vertical drags while the content is
            // still wrap-content, making the permission list feel stuck.
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true
        }
        dialog.show()
    }
}

@Composable
private fun BrowserPermissionSheet(onDone: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    fun granted(permission: String) = androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val permissionRefresh = refresh
    val camera = permissionRefresh.let { granted(Manifest.permission.CAMERA) }
    val microphone = permissionRefresh.let { granted(Manifest.permission.RECORD_AUDIO) }
    val location = permissionRefresh.let { granted(Manifest.permission.ACCESS_FINE_LOCATION) }
    val notifications = Build.VERSION.SDK_INT < 33 || permissionRefresh.let { granted(Manifest.permission.POST_NOTIFICATIONS) }
    val media = Build.VERSION.SDK_INT < 33 || permissionRefresh.let { granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) }
    val storage = Build.VERSION.SDK_INT >= 33 || permissionRefresh.let { granted(Manifest.permission.READ_EXTERNAL_STORAGE) }
    val missing = !camera || !microphone || !location || !notifications || !media
    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.Security, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Permissions Required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Petal needs these permissions for websites to work properly, including video calls, maps, notifications, and file uploads.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            Spacer(Modifier.height(12.dp))
            PermissionRow("Camera", "Video calls, QR scanning, and camera websites", Icons.Outlined.Videocam, camera) { activity?.let { request.launch(arrayOf(Manifest.permission.CAMERA)) } }
            PermissionRow("Microphone", "Voice search, calls, and media recording", Icons.Outlined.Mic, microphone) { activity?.let { request.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) } }
            PermissionRow("Location", "Maps and location-aware websites", Icons.Outlined.LocationOn, location) { activity?.let { request.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) } }
            if (Build.VERSION.SDK_INT >= 33) PermissionRow("Notifications", "Download progress and browser alerts", Icons.Outlined.Notifications, notifications) { request.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
            if (Build.VERSION.SDK_INT >= 33) PermissionRow("Photos and videos", "File uploads and media selection", Icons.Outlined.PhotoLibrary, media) { request.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)) }
            if (Build.VERSION.SDK_INT < 33) PermissionRow("Files and media", "File uploads and downloads", Icons.Outlined.Folder, storage) { request.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) }
            Spacer(Modifier.height(16.dp))
            if (missing) Text("You can grant any permission later from Android Settings.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(if (missing) "Continue" else "Everything is ready") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, icon: ImageVector, granted: Boolean, onGrant: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (granted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.surfaceContainerHigh)) {
        ListItem(headlineContent = { Text(title, fontWeight = FontWeight.Bold) }, supportingContent = { Text(description) }, leadingContent = { Icon(icon, null, tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }, trailingContent = { if (granted) Icon(Icons.Outlined.CheckCircle, "Granted", tint = MaterialTheme.colorScheme.primary) else OutlinedButton(onClick = onGrant, shape = RoundedCornerShape(12.dp)) { Text("Grant") } }, colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
    }
}
