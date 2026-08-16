package com.petal.browser.ui.components

import android.content.Context
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.R
import com.petal.browser.ui.theme.PetalExpressiveTheme

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
                    painter = painterResource(id = R.mipmap.ic_launcher),
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

            Spacer(Modifier.height(32.dp))

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
                    .entrance(index = 3)
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

