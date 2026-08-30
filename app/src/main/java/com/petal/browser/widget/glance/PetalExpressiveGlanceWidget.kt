package com.petal.browser.widget.glance

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.ui.theme.paletteById
import com.petal.browser.widget.PetalSearchWidgetProvider

/**
 * Material 3 Expressive Search Bar Widget.
 * Inspired directly by Google Search 8 (4x2) Material 3 Expressive launcher widget:
 * - Floating Scallop/Cookie Monogram Emblem Badge with Petal 'G'/Emblem at center
 * - High-Contrast Expressive Action Island pill containing Incognito, AI Sparkles, and Lens/Camera
 * - Dynamic Material 3 color tokens & responsive Glance layout wiring
 */
class PetalExpressiveGlanceWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT_BAR = DpSize(240.dp, 56.dp)
        private val TALL_BAR = DpSize(240.dp, 110.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_BAR, TALL_BAR)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val themeConfig = sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
            val isDark = when (themeConfig) {
                "DARK" -> true
                "LIGHT" -> false
                else -> {
                    val flags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    flags == Configuration.UI_MODE_NIGHT_YES
                }
            }
            val paletteId = sp.getString("sp_palette_id", "tide") ?: "tide"
            val useDynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

            val scheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                paletteById(paletteId).let { if (isDark) it.dark else it.light }
            }

            GlanceTheme(colors = ColorProviders(light = scheme, dark = scheme)) {
                val size = LocalSize.current
                val isTall = size.height >= 90.dp
                PetalExpressiveWidgetContent(isTall = isTall)
            }
        }
    }
}

class PetalExpressiveGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalExpressiveGlanceWidget()
}

@Composable
private fun PetalExpressiveWidgetContent(isTall: Boolean) {
    val context = LocalContext.current
    val searchAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH))
    val aiAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_AI_SEARCH))
    val incognitoAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO))
    val lensAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_LENS))
    val greetingText = remember { PetalGreetingManager.getRandomGreeting(context) }

    // Outer Surface Container styled with extraLargeIncreased (28dp) corners & elevated surface coloring
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.surface)
            .padding(10.dp)
    ) {
        if (isTall) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Top Greeting Header
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .clickable(searchAction)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = greetingText,
                        maxLines = 1,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                ExpressiveSearchBarRow(
                    modifier = GlanceModifier.fillMaxWidth().height(56.dp),
                    searchAction = searchAction,
                    aiAction = aiAction,
                    incognitoAction = incognitoAction,
                    lensAction = lensAction
                )
            }
        } else {
            ExpressiveSearchBarRow(
                modifier = GlanceModifier.fillMaxSize(),
                searchAction = searchAction,
                aiAction = aiAction,
                incognitoAction = incognitoAction,
                lensAction = lensAction
            )
        }
    }
}

@Composable
private fun ExpressiveSearchBarRow(
    modifier: GlanceModifier,
    searchAction: androidx.glance.action.Action,
    aiAction: androidx.glance.action.Action,
    incognitoAction: androidx.glance.action.Action,
    lensAction: androidx.glance.action.Action
) {
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // Flexible-width Pill Search Trigger (largeIncreased shape / 24dp)
        Box(
            modifier = GlanceModifier
                .fillMaxHeight()
                .defaultWeight()
                .cornerRadius(24.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .clickable(searchAction)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "Search",
                maxLines = 1,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        // AI Action Squircle Button - primaryContainer
        SquircleGlanceActionButton(
            iconRes = R.drawable.ic_auto_awesome,
            contentDescription = "AI Assistant",
            containerColor = GlanceTheme.colors.primaryContainer,
            contentColor = GlanceTheme.colors.onPrimaryContainer,
            action = aiAction
        )

        Spacer(modifier = GlanceModifier.width(8.dp))

        // Incognito Action Squircle Button - secondaryContainer
        SquircleGlanceActionButton(
            iconRes = R.drawable.icon_incognito,
            contentDescription = "Incognito Mode",
            containerColor = GlanceTheme.colors.secondaryContainer,
            contentColor = GlanceTheme.colors.onSecondaryContainer,
            action = incognitoAction
        )

        Spacer(modifier = GlanceModifier.width(8.dp))

        // Lens Action Squircle Button - tertiaryContainer
        SquircleGlanceActionButton(
            iconRes = R.drawable.ic_lens,
            contentDescription = "Visual Lens",
            containerColor = GlanceTheme.colors.tertiaryContainer,
            contentColor = GlanceTheme.colors.onTertiaryContainer,
            action = lensAction
        )
    }
}

@Composable
private fun SquircleGlanceActionButton(
    iconRes: Int,
    contentDescription: String,
    containerColor: androidx.glance.unit.ColorProvider,
    contentColor: androidx.glance.unit.ColorProvider,
    action: androidx.glance.action.Action
) {
    Box(
        modifier = GlanceModifier
            .size(46.dp)
            .cornerRadius(16.dp)
            .background(containerColor)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(22.dp),
            colorFilter = ColorFilter.tint(contentColor)
        )
    }
}

private fun widgetIntent(context: Context, action: String): Intent =
    Intent(context, BrowserActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
