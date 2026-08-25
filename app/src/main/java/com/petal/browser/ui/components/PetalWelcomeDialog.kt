package com.petal.browser.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.petal.browser.R
import com.petal.browser.account.GoogleAccountManager
import com.petal.browser.account.ProfileAvatarDisplay
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.unit.HelperUnit
import kotlinx.coroutines.launch

object PetalWelcomeBridge {
    @JvmStatic
    fun showWelcomeDialog(activity: ComponentActivity, onGetStarted: () -> Unit) {
        try {
            var dialog: AlertDialog? = null
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    PetalExpressiveTheme {
                        PetalWelcomeScreen(onGetStarted = {
                            try { dialog?.dismiss() } catch (ignored: Exception) {}
                            onGetStarted()
                        })
                    }
                }
            }
            val builder = MaterialAlertDialogBuilder(activity)
            builder.setView(composeView)
            builder.setCancelable(false)
            dialog = builder.create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.setCanceledOnTouchOutside(false)
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalWelcomeScreen(onGetStarted: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 10 })
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Page Indicators (Pills)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(10) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 22.dp else 6.dp,
                            animationSpec = spring(),
                            label = "indicatorWidth"
                        )
                        val color by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            label = "indicatorColor"
                        )
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                Text(
                    text = if (pagerState.currentPage == 0) "Welcome" else "${pagerState.currentPage} / 9",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Unswipeable Horizontal Pager
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val autoAdvance: () -> Unit = {
                    scope.launch {
                        if (pagerState.currentPage < 9) {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (page) {
                        0 -> WelcomeStepPage()
                        1 -> EssentialPermissionsStepPage(activity, context, onAutoAdvance = autoAdvance)
                        2 -> NotificationPermissionStepPage(activity, context, onAutoAdvance = autoAdvance)
                        3 -> BackupFeatureStepPage(context)
                        4 -> ThemeAndLanguageStepPage(sp, activity)
                        5 -> BottomNavbarStyleStepPage(sp)
                        6 -> SetupPetalAiKeyStepPage(sp)
                        7 -> SearchEngineStepPage(sp)
                        8 -> DefaultFontStepPage(sp)
                        9 -> AdBlockerStepPage(sp)
                    }
                }
            }

            // Bottom Navigation Action Bar
            SetupBottomBar(
                pagerState = pagerState,
                onNextClicked = {
                    scope.launch {
                        if (pagerState.currentPage < 9) {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                onFinishClicked = {
                    sp.edit().putBoolean("sp_welcome_shown", true).apply()
                    onGetStarted()
                }
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun SetupBottomBar(
    pagerState: androidx.compose.foundation.pager.PagerState,
    onNextClicked: () -> Unit,
    onFinishClicked: () -> Unit
) {
    val isLastPage = pagerState.currentPage == 9

    // Corner Morphing Animation
    val targetShapeValues = when (pagerState.currentPage % 3) {
        0 -> listOf(50f, 50f, 50f, 50f)
        1 -> listOf(26f, 26f, 26f, 26f)
        else -> listOf(18f, 50f, 18f, 50f)
    }

    val animTopStart by animateFloatAsState(targetShapeValues[0], tween(500, easing = FastOutSlowInEasing), label = "TopStart")
    val animTopEnd by animateFloatAsState(targetShapeValues[1], tween(500, easing = FastOutSlowInEasing), label = "TopEnd")
    val animBottomStart by animateFloatAsState(targetShapeValues[2], tween(500, easing = FastOutSlowInEasing), label = "BottomStart")
    val animBottomEnd by animateFloatAsState(targetShapeValues[3], tween(500, easing = FastOutSlowInEasing), label = "BottomEnd")

    // Rotation Animation
    val animatedRotation by animateFloatAsState(
        targetValue = pagerState.currentPage * 360f,
        animationSpec = tween(750, easing = FastOutSlowInEasing),
        label = "FabRotation"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated Step Count Text
            AnimatedContent(
                targetState = pagerState.currentPage,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInVertically { height -> height } + fadeIn()).togetherWith(slideOutVertically { height -> -height } + fadeOut())
                    } else {
                        (slideInVertically { height -> -height } + fadeIn()).togetherWith(slideOutVertically { height -> height } + fadeOut())
                    }.using(SizeTransform(clip = false))
                },
                label = "StepTextAnim"
            ) { targetPage ->
                if (targetPage == 0) {
                    Text(
                        text = "Welcome to Petal",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "Step $targetPage of 9",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Morphing Rotating Action FAB Button
            Surface(
                onClick = if (isLastPage) onFinishClicked else onNextClicked,
                shape = RoundedCornerShape(
                    topStart = animTopStart.dp,
                    topEnd = animTopEnd.dp,
                    bottomStart = animBottomStart.dp,
                    bottomEnd = animBottomEnd.dp
                ),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .rotate(animatedRotation)
                    .size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.rotate(-animatedRotation)) {
                    AnimatedContent(
                        targetState = isLastPage,
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut(tween(150)) + scaleOut(targetScale = 0.8f))
                        },
                        label = "FabIconAnim"
                    ) { lastPage ->
                        if (lastPage) {
                            Icon(Icons.Rounded.Check, contentDescription = "Finish", modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next", modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. WELCOME PAGE
// ==========================================
@Composable
private fun WelcomeStepPage() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val appIconPainter = remember(context) {
        val sizePx = with(density) { 80.dp.roundToPx() }.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        if (drawable != null) {
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
        BitmapPainter(bitmap.asImageBitmap())
    }

    Spacer(Modifier.height(8.dp))

    // PixelPlayer Style Welcome Title Header
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 38.sp,
                fontWeight = FontWeight.Normal
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Petal",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "v1.7.7",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "• Official Release",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    Spacer(Modifier.height(18.dp))

    // App Hero Icon & Badge Card
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 6.dp,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = appIconPainter,
                        contentDescription = "Petal Logo",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Fast, private & customizable web browser designed for modern Android with Material 3 Expressive UI.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                SuggestionChip(onClick = {}, label = { Text("Privacy First") }, icon = { Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(16.dp)) })
                SuggestionChip(onClick = {}, label = { Text("Material 3 Expressive") }, icon = { Icon(Icons.Rounded.Palette, null, modifier = Modifier.size(16.dp)) })
                SuggestionChip(onClick = {}, label = { Text("Petal AI Hub") }, icon = { Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp)) })
                SuggestionChip(onClick = {}, label = { Text("Fast MDM Downloads") }, icon = { Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp)) })
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // Profile Customization Card
    val currentProfile = GoogleAccountManager.currentProfile
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Active Profile",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatarDisplay(profile = currentProfile, size = 46.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = currentProfile.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (currentProfile.isGuest) "Incognito Guest Mode" else "Synced Account Profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. ALL ESSENTIAL PERMISSIONS PAGE
// ==========================================
@Composable
private fun EssentialPermissionsStepPage(activity: Activity?, context: Context, onAutoAdvance: () -> Unit) {
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var hasLoc by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }

    val allGranted = hasCamera && hasMic && hasLoc
    LaunchedEffect(allGranted) {
        if (allGranted) {
            kotlinx.coroutines.delay(350)
            onAutoAdvance()
        }
    }

    Spacer(Modifier.height(12.dp))

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(80.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(38.dp))
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Essential Permissions",
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = "Grant camera, microphone, and location permissions to allow interactive websites, video calls, and maps to function properly.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(20.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Camera
            PermissionStatusRow(
                title = "Camera Access",
                description = "For QR scanning, video chats & WebRTC",
                icon = Icons.Rounded.Videocam,
                isGranted = hasCamera,
                onGrant = {
                    if (activity != null) {
                        HelperUnit.grantPermissionsCamera(activity)
                        hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    }
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Microphone
            PermissionStatusRow(
                title = "Microphone Access",
                description = "For voice search & audio calls",
                icon = Icons.Rounded.Mic,
                isGranted = hasMic,
                onGrant = {
                    if (activity != null) {
                        HelperUnit.grantPermissionsMic(activity)
                        hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    }
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Location
            PermissionStatusRow(
                title = "Location Access",
                description = "For web maps & local search results",
                icon = Icons.Rounded.MyLocation,
                isGranted = hasLoc,
                onGrant = {
                    if (activity != null) {
                        HelperUnit.grantPermissionsLoc(activity)
                        hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    }
                }
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(imageVector = icon, contentDescription = title, tint = if (isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.width(8.dp))

        if (isGranted) {
            FilledTonalButton(onClick = {}, enabled = false, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Granted", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Button(onClick = onGrant, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text("Grant", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ==========================================
// 3. NOTIFICATION PERMISSION PAGE
// ==========================================
@Composable
private fun NotificationPermissionStepPage(activity: Activity?, context: Context, onAutoAdvance: () -> Unit) {
    var hasNotification by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    LaunchedEffect(hasNotification) {
        if (hasNotification) {
            kotlinx.coroutines.delay(350)
            onAutoAdvance()
        }
    }

    Spacer(Modifier.height(12.dp))

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.size(80.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(38.dp))
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Downloads & Media Notifications",
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = "Allow notifications to receive real-time download progress updates, completion alerts, and media playback controls.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(24.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (hasNotification) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                contentDescription = null,
                tint = if (hasNotification) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = if (hasNotification) "Notification Permission Enabled" else "Notification Permission Disabled",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null) {
                        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                        hasNotification = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    }
                },
                enabled = !hasNotification,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text(if (hasNotification) "Notifications Allowed" else "Allow Notifications")
            }
        }
    }
}

// ==========================================
// 4. BACKUP FEATURE PAGE
// ==========================================
@Composable
private fun BackupFeatureStepPage(context: Context) {
    Spacer(Modifier.height(12.dp))

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(80.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(38.dp))
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Backup & Restore Feature",
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = "Do you have a backup? Petal Browser lets you export or restore your bookmarks, history, web settings, and search engines at any time.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(24.dp))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Card(
            onClick = {
                try {
                    context.startActivity(Intent(context, com.petal.browser.activity.Settings_Backup::class.java))
                } catch (e: Exception) {
                    Toast.makeText(context, "Open Settings -> Data & Backup to manage backups", Toast.LENGTH_SHORT).show()
                }
            },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Export New Backup", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                    Text("Save bookmarks, history & search engines to file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }

        Card(
            onClick = {
                try {
                    context.startActivity(Intent(context, com.petal.browser.activity.Settings_Backup::class.java))
                } catch (e: Exception) {
                    Toast.makeText(context, "Open Settings -> Data & Backup to restore", Toast.LENGTH_SHORT).show()
                }
            },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Restore Existing Backup", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                    Text("Import bookmarks and data from a .json file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ==========================================
// 5. THEME & APP LANGUAGE PAGE
// ==========================================
@Composable
private fun ThemeAndLanguageStepPage(sp: SharedPreferences, activity: Activity?) {
    val context = LocalContext.current
    var selectedTheme by remember { mutableStateOf(sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM") }
    var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
    var appLanguage by remember { mutableStateOf(sp.getString("sp_app_language", "system") ?: "system") }

    Spacer(Modifier.height(12.dp))

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(80.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(38.dp))
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Theme & Language",
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = "Customize your visual style with light/dark theme modes, OLED pure black, and select your preferred display language.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(20.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("App Theme Mode", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = selectedTheme == "LIGHT",
                    onClick = {
                        selectedTheme = "LIGHT"
                        sp.edit().putString("sp_theme_config", "LIGHT").apply()
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    },
                    label = { Text("Light") },
                    leadingIcon = { Icon(Icons.Outlined.LightMode, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = selectedTheme == "DARK",
                    onClick = {
                        selectedTheme = "DARK"
                        sp.edit().putString("sp_theme_config", "DARK").apply()
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    },
                    label = { Text("Dark") },
                    leadingIcon = { Icon(Icons.Rounded.DarkMode, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = selectedTheme == "FOLLOW_SYSTEM",
                    onClick = {
                        selectedTheme = "FOLLOW_SYSTEM"
                        sp.edit().putString("sp_theme_config", "FOLLOW_SYSTEM").apply()
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    },
                    label = { Text("System") },
                    leadingIcon = { Icon(Icons.Rounded.Android, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AMOLED Pure Black", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                    Text("Deep OLED pitch black background", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isAmoled,
                    onCheckedChange = { checked ->
                        isAmoled = checked
                        sp.edit().putBoolean("sp_amoled", checked).apply()
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Text("Display Language", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))

            val languages = listOf(
                Pair("system", "System Default"),
                Pair("en", "English"),
                Pair("hi-Latn", "Hinglish (Hindi in English)"),
                Pair("hi", "हिन्दी (Hindi)"),
                Pair("es", "Español (Spanish)"),
                Pair("fr", "Français (French)"),
                Pair("de", "Deutsch (German)"),
                Pair("zh", "中文 (Chinese)"),
                Pair("ar", "العربية (Arabic)"),
                Pair("pt", "Português (Portuguese)"),
                Pair("ru", "Русский (Russian)"),
                Pair("ja", "日本語 (Japanese)")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                languages.forEach { (tag, label) ->
                    FilterChip(
                        selected = appLanguage == tag,
                        onClick = {
                            if (appLanguage != tag) {
                                appLanguage = tag
                                HelperUnit.setAppLanguage(context, tag)
                            }
                        },
                        label = { Text(label) },
                        leadingIcon = if (appLanguage == tag) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }
    }
}

// ==========================================
// 6. BOTTOM NAVBAR STYLE PAGE
// ==========================================
@Composable
private fun BottomNavbarStyleStepPage(sp: SharedPreferences) {
    var navStyle by remember { mutableStateOf(sp.getString("sp_bottom_navbar_style", "FLOATING") ?: "FLOATING") }
    var isFloatingBar by remember { mutableStateOf(sp.getBoolean("sp_floating_tab_bar", true)) }

    Spacer(Modifier.height(12.dp))

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(80.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ViewDay, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(38.dp))
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Navigation Bar Style",
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = "Choose between a modern floating pill navigation bar or a classic docked bottom layout for web navigation.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(20.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                onClick = {
                    navStyle = "FLOATING"
                    isFloatingBar = true
                    sp.edit().putString("sp_bottom_navbar_style", "FLOATING").putBoolean("sp_floating_tab_bar", true).apply()
                },
                border = BorderStroke(2.dp, if (navStyle == "FLOATING") MaterialTheme.colorScheme.primary else Color.Transparent),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SmartButton, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Floating Pill Bar", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        Text("Detached rounded floating navigation bar with fluid gesture morphing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (navStyle == "FLOATING") Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Card(
                onClick = {
                    navStyle = "CLASSIC"
                    isFloatingBar = false
                    sp.edit().putString("sp_bottom_navbar_style", "CLASSIC").putBoolean("sp_floating_tab_bar", false).apply()
                },
                border = BorderStroke(2.dp, if (navStyle == "CLASSIC") MaterialTheme.colorScheme.primary else Color.Transparent),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ViewAgenda, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Classic Docked Bar", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        Text("Edge-to-edge docked bottom navigation bar with integrated tab counter", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (navStyle == "CLASSIC") Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ==========================================
// 7. SETUP PETAL AI KEY PAGE
// ==========================================
@Composable
private fun SetupPetalAiKeyStepPage(sp: SharedPreferences) {
    var apiKey by remember { mutableStateOf(sp.getString("sp_gemini_api_key", "") ?: "") }
    var selectedProvider by remember { mutableStateOf(sp.getString("sp_ai_provider", "Gemini 2.5 Flash") ?: "Gemini 2.5 Flash") }

    Spacer(Modifier.height(12.dp))

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.size(80.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(38.dp))
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Petal AI & Deep Research",
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = "Configure your preferred AI API key for webpage summaries, instant search answers, and live site translation.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(20.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Select AI Engine Provider", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))

            val providers = listOf("Gemini 2.5 Flash", "OpenAI GPT-4o", "DeepSeek R1", "Groq Llama 3")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                providers.forEach { provider ->
                    FilterChip(
                        selected = selectedProvider == provider,
                        onClick = {
                            selectedProvider = provider
                            sp.edit().putString("sp_ai_provider", provider).apply()
                        },
                        label = { Text(provider) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { input ->
                    apiKey = input
                    sp.edit().putString("sp_gemini_api_key", input).apply()
                },
                label = { Text("API Key (Optional)") },
                placeholder = { Text("Paste your API key here...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Keys are securely stored in your local encrypted SharedPreferences.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==========================================
// 8. SEARCH ENGINE STEP PAGE
// ==========================================
@Composable
private fun SearchEngineStepPage(sp: SharedPreferences) {
    var searchEngineIndex by remember { mutableStateOf(sp.getString("sp_search_engine", "0") ?: "0") }

    val engines = listOf(
        Pair("0", "Google"),
        Pair("1", "DuckDuckGo"),
        Pair("2", "Brave Search"),
        Pair("3", "Bing"),
        Pair("4", "Startpage"),
        Pair("5", "Ecosia")
    )

    Spacer(Modifier.height(12.dp))

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(80.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(38.dp))
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Default Search Engine",
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = "Select your default search provider for omnibox address bar queries and homepage searches.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(20.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            engines.forEach { (indexStr, name) ->
                Card(
                    onClick = {
                        searchEngineIndex = indexStr
                        sp.edit().putString("sp_search_engine", indexStr).apply()
                    },
                    border = BorderStroke(1.5.dp, if (searchEngineIndex == indexStr) MaterialTheme.colorScheme.primary else Color.Transparent),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                        RadioButton(
                            selected = searchEngineIndex == indexStr,
                            onClick = {
                                searchEngineIndex = indexStr
                                sp.edit().putString("sp_search_engine", indexStr).apply()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 9. DEFAULT FONT STEP PAGE
// ==========================================
@Composable
private fun DefaultFontStepPage(sp: SharedPreferences) {
    var fontSelection by remember { mutableStateOf(sp.getString("sp_font_family_option", "GS_FLEX_PERMANENT") ?: "GS_FLEX_PERMANENT") }

    Spacer(Modifier.height(12.dp))

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(80.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(38.dp))
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Typography & Font Family",
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = "Choose between Petal Signature (Google Sans Flex) or import your custom TTF/OTF font file.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(20.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                onClick = {
                    fontSelection = "GS_FLEX_PERMANENT"
                    sp.edit().putString("sp_font_family_option", "GS_FLEX_PERMANENT").apply()
                },
                border = BorderStroke(2.dp, if (fontSelection == "GS_FLEX_PERMANENT") MaterialTheme.colorScheme.primary else Color.Transparent),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FontDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Petal Signature", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        Text("Google Sans Flex variable font with dynamic optical sizing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (fontSelection == "GS_FLEX_PERMANENT") Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Card(
                onClick = {
                    fontSelection = "CUSTOM_STORAGE"
                    sp.edit().putString("sp_font_family_option", "CUSTOM_STORAGE").apply()
                },
                border = BorderStroke(2.dp, if (fontSelection == "CUSTOM_STORAGE") MaterialTheme.colorScheme.primary else Color.Transparent),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FolderZip, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Custom Font File", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        Text("Import custom TTF/OTF variable font from device storage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (fontSelection == "CUSTOM_STORAGE") Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ==========================================
// 10. AD BLOCKER STEP PAGE
// ==========================================
@Composable
private fun AdBlockerStepPage(sp: SharedPreferences) {
    var isAdBlockEnabled by remember { mutableStateOf(sp.getBoolean("sp_ad_block", true)) }

    Spacer(Modifier.height(12.dp))

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.size(80.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(38.dp))
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Ad & Tracker Shield",
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = "Block intrusive web ads, popups, and tracking scripts automatically for faster page load speeds.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(24.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Enable Petal Shield", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                Text("Block ads, trackers & popups across all websites", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = isAdBlockEnabled,
                onCheckedChange = { checked ->
                    isAdBlockEnabled = checked
                    sp.edit().putBoolean("sp_ad_block", checked).apply()
                }
            )
        }
    }
}
