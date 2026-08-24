package com.petal.browser.widget.glance

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
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
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.ui.theme.paletteById
import com.petal.browser.widget.PetalSearchWidgetProvider

/**
 * Petal's home screen search widget, built on Jetpack Glance with full Material 3 Expressive UI & UX.
 * Dynamically responds to theme/palette changes and widget resizing (compact 1-row or expanded 2-row).
 */
class PetalSearchGlanceWidget : GlanceAppWidget() {

    companion object {
        private val SMALL_BOX = DpSize(180.dp, 56.dp)
        private val WIDE_BOX = DpSize(260.dp, 56.dp)
        private val TALL_BOX = DpSize(260.dp, 120.dp)

        /** Call after a theme/palette change if needed. */
        fun clearShapeCache() {
            // No-op for synced standard M3 widget UI
        }
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_BOX, WIDE_BOX, TALL_BOX)
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
                val isTall = size.height >= 100.dp
                PetalSearchWidgetContent(
                    isTall = isTall
                )
            }
        }
    }
}

class PetalSearchGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalSearchGlanceWidget()
}

@Composable
private fun PetalSearchWidgetContent(
    isTall: Boolean
) {
    val context = LocalContext.current
    val searchAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH))
    val aiAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_AI_SEARCH))
    val voiceAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_VOICE))

    val greetingText = remember { PetalGreetingManager.getRandomGreeting(context) }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp)
    ) {
        if (isTall) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Greeting tagline occupying the top row
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .clickable(searchAction)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = greetingText,
                        maxLines = 2,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // Search Bar taking the bottom position (Synced with Homepage PetalSearchBar)
                WidgetSearchBar(
                    modifier = GlanceModifier.fillMaxWidth().height(56.dp),
                    searchAction = searchAction,
                    aiAction = aiAction,
                    voiceAction = voiceAction
                )
            }
        } else {
            // Compact 1-row layout (Synced with Homepage PetalSearchBar)
            WidgetSearchBar(
                modifier = GlanceModifier.fillMaxSize(),
                searchAction = searchAction,
                aiAction = aiAction,
                voiceAction = voiceAction
            )
        }
    }
}

@Composable
private fun WidgetSearchBar(
    modifier: GlanceModifier,
    searchAction: androidx.glance.action.Action,
    aiAction: androidx.glance.action.Action,
    voiceAction: androidx.glance.action.Action
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .cornerRadius(32.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .clickable(searchAction)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.icon_search),
                contentDescription = null,
                modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
            )

            Spacer(modifier = GlanceModifier.width(10.dp))

            Box(
                modifier = GlanceModifier.fillMaxHeight().defaultWeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = context.getString(R.string.widget_search_hint),
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(4.dp))

            // AI Search Icon (Petal AI) - synced with homepage PetalSearchBar
            Box(
                modifier = GlanceModifier.size(40.dp).clickable(aiAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_auto_awesome),
                    contentDescription = context.getString(R.string.widget_ai_search),
                    modifier = GlanceModifier.size(22.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                )
            }

            Spacer(modifier = GlanceModifier.width(4.dp))

            // Voice Search Icon - synced with homepage PetalSearchBar
            Box(
                modifier = GlanceModifier.size(40.dp).clickable(voiceAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_mic),
                    contentDescription = context.getString(R.string.widget_voice_search),
                    modifier = GlanceModifier.size(22.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
                )
            }
        }
    }
}

private fun widgetIntent(context: Context, action: String): Intent =
    Intent(context, BrowserActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

