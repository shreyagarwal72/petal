package com.petal.browser.widget.liquidglass

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.R

/**
 * Customization and setup activity for Petal's Liquid Glass widgets.
 * Allows live tuning of refraction, blur, corner highlights, and tint overlays.
 */
class PetalLiquidGlassSettingsActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            MaterialTheme {
                LiquidGlassSettingsScreen(
                    context = this,
                    initialConfig = PetalBlurConfig.load(this),
                    onSave = { updated ->
                        PetalBlurConfig.save(this, updated)
                        PetalWallpaperBlurHelper.rebakeAllFromWallpaper(this)
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            val resultValue = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(RESULT_OK, resultValue)
                        }
                        finish()
                    },
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiquidGlassSettingsScreen(
    context: Context,
    initialConfig: PetalBlurConfig,
    onSave: (PetalBlurConfig) -> Unit,
    onOpenAccessibility: () -> Unit
) {
    var blurRadius by remember { mutableFloatStateOf(initialConfig.blurRadius) }
    var refractionHeight by remember { mutableFloatStateOf(initialConfig.refractionHeight) }
    var refractionOffset by remember { mutableFloatStateOf(initialConfig.refractionOffset) }
    var cornerRadius by remember { mutableFloatStateOf(initialConfig.cornerRadius) }
    var edgeAlpha by remember { mutableIntStateOf(initialConfig.edgeAlpha) }
    var darkLightMode by remember { mutableIntStateOf(initialConfig.darkLightMode) }
    var darkLightIntensity by remember { mutableIntStateOf(initialConfig.darkLightIntensity) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_liquid_glass_settings_title)) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preview Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                shape = RoundedCornerShape(cornerRadius.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Petal Liquid Glass Preview",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Accessibility helper banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Automatic Positioning",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Petal uses an Accessibility Service to detect widget coordinates on your home screen automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(onClick = onOpenAccessibility) {
                        Text("Enable Widget Position Detection")
                    }
                }
            }

            // Tuning Sliders
            Text("Glass Optics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Refraction Height
            Text("Refraction Bevel Height: ${refractionHeight.toInt()} dp")
            Slider(
                value = refractionHeight,
                onValueChange = { refractionHeight = it },
                valueRange = 5f..50f
            )

            // Refraction Amount
            Text("Refraction Dispersion Amount: ${refractionOffset.toInt()} dp")
            Slider(
                value = refractionOffset,
                onValueChange = { refractionOffset = it },
                valueRange = 10f..120f
            )

            // Blur Radius
            Text("Frosted Blur: ${blurRadius.toInt()} px")
            Slider(
                value = blurRadius,
                onValueChange = { blurRadius = it },
                valueRange = 0.01f..35f
            )

            // Corner Radius
            Text("Corner Radius: ${cornerRadius.toInt()} dp")
            Slider(
                value = cornerRadius,
                onValueChange = { cornerRadius = it },
                valueRange = 12f..36f
            )

            // Specular Edge Alpha
            Text("Specular Rim Highlight: $edgeAlpha%")
            Slider(
                value = edgeAlpha.toFloat(),
                onValueChange = { edgeAlpha = it.toInt() },
                valueRange = 0f..100f
            )

            // Tint / Appearance Mode
            Text("Tinting & Tone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = darkLightMode == PetalBlurConfig.DARK_LIGHT_OFF,
                    onClick = { darkLightMode = PetalBlurConfig.DARK_LIGHT_OFF },
                    label = { Text("None") }
                )
                FilterChip(
                    selected = darkLightMode == PetalBlurConfig.DARK_LIGHT_DARK,
                    onClick = { darkLightMode = PetalBlurConfig.DARK_LIGHT_DARK },
                    label = { Text("Dark Tint") }
                )
                FilterChip(
                    selected = darkLightMode == PetalBlurConfig.DARK_LIGHT_LIGHT,
                    onClick = { darkLightMode = PetalBlurConfig.DARK_LIGHT_LIGHT },
                    label = { Text("Light Tint") }
                )
            }

            if (darkLightMode != PetalBlurConfig.DARK_LIGHT_OFF) {
                Text("Tint Intensity: $darkLightIntensity%")
                Slider(
                    value = darkLightIntensity.toFloat(),
                    onValueChange = { darkLightIntensity = it.toInt() },
                    valueRange = 10f..100f
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save / Apply Button
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                onClick = {
                    val config = initialConfig.copy(
                        blurRadius = blurRadius,
                        refractionHeight = refractionHeight,
                        refractionOffset = refractionOffset,
                        cornerRadius = cornerRadius,
                        edgeAlpha = edgeAlpha,
                        darkLightMode = darkLightMode,
                        darkLightIntensity = darkLightIntensity
                    )
                    onSave(config)
                }
            ) {
                Text("Apply & Update Widgets", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
