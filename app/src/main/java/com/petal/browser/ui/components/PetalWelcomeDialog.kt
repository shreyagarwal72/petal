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
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun PetalWelcomeScreen(onGetStarted: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(16.dp))

            // App Logo Hero Section with prominent launcher icon & Lottie glow backdrop
            val iconContext = LocalContext.current
            val density = LocalDensity.current
            val appIconPainter = remember(iconContext) {
                // R.mipmap.ic_launcher resolves to an AdaptiveIconDrawable on API 26+.
                // painterResource() only supports VectorDrawables and rasterized assets and
                // throws IllegalArgumentException for AdaptiveIconDrawable, which crashed this
                // dialog on virtually every modern device. Render the drawable into a bitmap
                // ourselves instead, which works for adaptive icons and plain bitmaps alike.
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

            // Google Sign-In & Data Privacy Section
            val context = iconContext
            val activity = remember(context) {
                var c = context
                while (c is android.content.ContextWrapper) {
                    if (c is Activity) break
                    c = c.baseContext
                }
                c as? Activity
            }
            val coroutineScope = rememberCoroutineScope()
            var isSigningIn by remember { mutableStateOf(false) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            val currentProfile = GoogleAccountManager.currentProfile

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (currentProfile.isSignedIn) "Signed in as ${currentProfile.displayName}" else "Google Account Sync",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (currentProfile.isSignedIn) currentProfile.email else "Sync bookmarks & preferences securely",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (!currentProfile.isSignedIn) {
                        Button(
                            onClick = {
                                if (!isSigningIn) {
                                    val targetContext = activity ?: context
                                    isSigningIn = true
                                    errorMessage = null
                                    coroutineScope.launch {
                                        try {
                                            when (val result = GoogleAccountManager.signIn(targetContext)) {
                                                is GoogleSignInResult.Success -> {
                                                    isSigningIn = false
                                                }
                                                is GoogleSignInResult.Failure -> {
                                                    isSigningIn = false
                                                    errorMessage = result.message
                                                }
                                            }
                                        } catch (e: Throwable) {
                                            isSigningIn = false
                                            errorMessage = e.message ?: "Sign-in error occurred"
                                        }
                                    }
                                }
                            },
                            enabled = !isSigningIn,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Connecting to Google...", style = MaterialTheme.typography.labelLarge)
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        "Sign in with Google",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        if (errorMessage != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    GoogleAccountManager.signOut(context)
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text("Sign Out", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Material Design Data Privacy & Safety Disclaimer
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = "Data Safety",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(top = 2.dp)
                            )
                            Column {
                                Text(
                                    text = "Data Privacy & Safety",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Google Sign-In is used exclusively to display your profile and sync your personal browser settings (bookmarks & history) locally. Petal Browser does not host a remote server or collect, sell, or share your data.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
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

