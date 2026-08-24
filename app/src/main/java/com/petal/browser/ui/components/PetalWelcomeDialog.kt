package com.petal.browser.ui.components

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import com.petal.browser.account.ProfileAvatarDisplay
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.R
import com.petal.browser.account.GoogleAccountManager
import com.petal.browser.account.GoogleSignInResult
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlinx.coroutines.launch

object PetalWelcomeBridge {
    @JvmStatic
    fun showWelcomeDialog(activity: ComponentActivity, onGetStarted: () -> Unit) {
        try {
            val dialog = BottomSheetDialog(activity)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    PetalExpressiveTheme {
                        PetalWelcomeScreen(onGetStarted = {
                            try { dialog.dismiss() } catch (ignored: Exception) {}
                            onGetStarted()
                        })
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.behavior.apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
                isFitToContents = false
                expandedOffset = 0
            }
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun PetalWelcomeScreen(onGetStarted: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // App Logo Hero Section with prominent launcher icon & Lottie glow backdrop
            val iconContext = LocalContext.current
            val density = LocalDensity.current
            val appIconPainter = remember(iconContext) {
                val sizePx = with(density) { 80.dp.roundToPx() }.coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val drawable = ContextCompat.getDrawable(iconContext, R.mipmap.ic_launcher)
                if (drawable != null) {
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
                BitmapPainter(bitmap.asImageBitmap())
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(110.dp)
                    .entrance(index = 0)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxSize()
                ) {}

                Image(
                    painter = appIconPainter,
                    contentDescription = "Petal Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Title & Subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.entrance(index = 1)
            ) {
                Text(
                    text = "Welcome to Petal",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Experience rapid web browsing, multi-threaded fast downloads, and expressive Stride Material 3 customization.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(28.dp))

            // Expressive PetalFeatureTile Components
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(index = 2)
            ) {
                PetalFeatureTile(
                    title = "Built-in Privacy Shield",
                    subtitle = "Automated ad blocking, tracker protection, and HTTPS security enforcement",
                    icon = Icons.Rounded.Shield,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onContainer = MaterialTheme.colorScheme.onSurface,
                    pillLabel = "Protected"
                )

                PetalFeatureTile(
                    title = "High-Speed Multi-Thread Engine",
                    subtitle = "Integrated parallel chunk download manager for maximum download speeds",
                    icon = Icons.Rounded.Download,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onContainer = MaterialTheme.colorScheme.onSurface,
                    pillLabel = "Fast MDM"
                )

                PetalFeatureTile(
                    title = "Material You & Dynamic Themes",
                    subtitle = "Personalized Monet palette colors, Stride variable fonts, and OLED AMOLED black",
                    icon = Icons.Rounded.Palette,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onContainer = MaterialTheme.colorScheme.onSurface,
                    pillLabel = "Expressive"
                )
            }

            Spacer(Modifier.height(24.dp))

            // User Profile Customization Section (Name & Avatar Setup)
            val context = iconContext
            val currentProfile = GoogleAccountManager.currentProfile
            var nameInput by remember { mutableStateOf(currentProfile.displayName) }
            var pendingCropUri by remember { mutableStateOf<android.net.Uri?>(null) }
            val galleryLauncher = rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
            ) { uri: android.net.Uri? ->
                uri?.let {
                    pendingCropUri = it
                }
            }

            if (pendingCropUri != null) {
                com.petal.browser.account.PetalAvatarCropSheet(
                    imageUri = pendingCropUri!!,
                    onDismiss = { pendingCropUri = null },
                    onAvatarCropped = { pendingCropUri = null }
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(index = 3)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Customize Profile",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(Modifier.height(12.dp))

                    ProfileAvatarDisplay(profile = currentProfile, sizeDp = 72)

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { input ->
                            if (input.length <= 15) {
                                nameInput = input
                                GoogleAccountManager.updateDisplayName(context, input)
                            }
                        },
                        label = { Text("User Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Choose Profile Avatar",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(Modifier.height(8.dp))

                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            Surface(
                                onClick = { galleryLauncher.launch("image/*") },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountCircle,
                                        contentDescription = "Gallery",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        items(GoogleAccountManager.builtinAvatarPresets.size) { idx ->
                            val (presetId, label) = GoogleAccountManager.builtinAvatarPresets[idx]
                            val isSelected = currentProfile.avatarType == com.petal.browser.account.AvatarType.PRESET && currentProfile.avatarPresetId == presetId
                            Surface(
                                onClick = { GoogleAccountManager.updateAvatarPreset(context, presetId) },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val iconVec = com.petal.browser.account.getPresetMaterialIcon(presetId)
                                    if (iconVec != null) {
                                        Icon(
                                            imageVector = iconVec,
                                            contentDescription = label,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.AccountCircle,
                                            contentDescription = label,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Get Started Button
            Button(
                onClick = onGetStarted,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .bouncyClickable(scaleDown = 0.94f, onClick = onGetStarted)
                    .entrance(index = 4)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Explore Petal Browser",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}


