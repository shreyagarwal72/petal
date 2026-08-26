package com.petal.browser.widget.glance

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.ui.theme.paletteById
import com.petal.browser.widget.PetalSearchWidgetProvider

/**
 * Material 3 Expressive Mode Switcher & Compact Quick Pill Widget.
 * Compact pill widget allowing instant mode switching (Regular / Incognito) and quick search launch.
 */
class PetalModeSwitchGlanceWidget : GlanceAppWidget() {

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
                PetalModeSwitchWidgetContent()
            }
        }
    }
}

class PetalModeSwitchGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalModeSwitchGlanceWidget()
}

@Composable
private fun PetalModeSwitchWidgetContent() {
    val context = LocalContext.current
    val searchAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH))
    val incognitoAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(6.dp)
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .clickable(searchAction),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Petal Emblem
                Box(
                    modifier = GlanceModifier
                        .size(32.dp)
                        .cornerRadius(16.dp)
                        .background(GlanceTheme.colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.icon_search),
                        contentDescription = null,
                        modifier = GlanceModifier.size(16.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
                    )
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                Text(
                    text = "Petal Mode",
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurfaceVariant
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                Spacer(modifier = GlanceModifier.width(4.dp))

                // Incognito Switch Button
                Box(
                    modifier = GlanceModifier
                        .size(32.dp)
                        .cornerRadius(16.dp)
                        .background(GlanceTheme.colors.secondaryContainer)
                        .clickable(incognitoAction),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.icon_incognito),
                        contentDescription = "Switch to Incognito",
                        modifier = GlanceModifier.size(16.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer)
                    )
                }
            }
        }
    }
}

private fun widgetIntent(context: Context, action: String): Intent =
    Intent(context, BrowserActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
