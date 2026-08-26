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
 * Material 3 Expressive Shortcuts & Bookmarks Widget.
 * Chrome-style home screen widget providing a quick search bar pill and a row/grid of top
 * shortcuts and bookmarks.
 */
class PetalShortcutsGlanceWidget : GlanceAppWidget() {

    companion object {
        private val GRID_BOX = DpSize(260.dp, 110.dp)
        private val LARGE_GRID_BOX = DpSize(320.dp, 160.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(GRID_BOX, LARGE_GRID_BOX)
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
                PetalShortcutsWidgetContent()
            }
        }
    }
}

class PetalShortcutsGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalShortcutsGlanceWidget()
}

@Composable
private fun PetalShortcutsWidgetContent() {
    val context = LocalContext.current
    val searchAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH))
    val voiceAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_VOICE))
    val incognitoAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO))
    val bookmarksAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_BOOKMARKS))
    val downloadsAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_DOWNLOADS))
    val newTabAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_NEW_TAB))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(10.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Top Row: Expressive Search Pill
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .cornerRadius(24.dp)
                    .background(GlanceTheme.colors.surfaceVariant)
                    .clickable(searchAction)
            ) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.icon_search),
                        contentDescription = null,
                        modifier = GlanceModifier.size(20.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
                    )

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    Box(
                        modifier = GlanceModifier.fillMaxHeight().defaultWeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = context.getString(R.string.widget_search_hint),
                            maxLines = 1,
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = GlanceTheme.colors.onSurfaceVariant
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(4.dp))

                    // Incognito Quick Pill Action
                    ExpressiveShortcutIconButton(
                        bgColor = GlanceTheme.colors.secondaryContainer,
                        iconRes = R.drawable.icon_incognito,
                        iconTint = GlanceTheme.colors.onSecondaryContainer,
                        contentDescription = "Incognito Tab",
                        action = incognitoAction
                    )

                    Spacer(modifier = GlanceModifier.width(4.dp))

                    // Voice Search Pill Action
                    ExpressiveShortcutIconButton(
                        bgColor = GlanceTheme.colors.tertiaryContainer,
                        iconRes = R.drawable.ic_mic,
                        iconTint = GlanceTheme.colors.onTertiaryContainer,
                        contentDescription = "Voice Search",
                        action = voiceAction
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Bottom Row: Chrome-Style Expressive Shortcuts Grid
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                ShortcutTileItem(
                    label = "New Tab",
                    iconRes = R.drawable.icon_tab_plus,
                    bgColor = GlanceTheme.colors.primaryContainer,
                    iconTint = GlanceTheme.colors.onPrimaryContainer,
                    action = newTabAction,
                    modifier = GlanceModifier.defaultWeight()
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                ShortcutTileItem(
                    label = "Bookmarks",
                    iconRes = R.drawable.ic_bookmark,
                    bgColor = GlanceTheme.colors.secondaryContainer,
                    iconTint = GlanceTheme.colors.onSecondaryContainer,
                    action = bookmarksAction,
                    modifier = GlanceModifier.defaultWeight()
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                ShortcutTileItem(
                    label = "Downloads",
                    iconRes = R.drawable.ic_download,
                    bgColor = GlanceTheme.colors.tertiaryContainer,
                    iconTint = GlanceTheme.colors.onTertiaryContainer,
                    action = downloadsAction,
                    modifier = GlanceModifier.defaultWeight()
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                ShortcutTileItem(
                    label = "Private",
                    iconRes = R.drawable.icon_incognito,
                    bgColor = GlanceTheme.colors.surfaceContainerHighest,
                    iconTint = GlanceTheme.colors.onSurface,
                    action = incognitoAction,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }
}

@Composable
private fun ExpressiveShortcutIconButton(
    bgColor: ColorProvider,
    iconRes: Int,
    iconTint: ColorProvider,
    contentDescription: String,
    action: androidx.glance.action.Action
) {
    Box(
        modifier = GlanceModifier
            .size(36.dp)
            .cornerRadius(18.dp)
            .background(bgColor)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(iconTint)
        )
    }
}

@Composable
private fun ShortcutTileItem(
    label: String,
    iconRes: Int,
    bgColor: ColorProvider,
    iconTint: ColorProvider,
    action: androidx.glance.action.Action,
    modifier: GlanceModifier = GlanceModifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .cornerRadius(18.dp)
            .background(bgColor)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically,
            modifier = GlanceModifier.padding(vertical = 4.dp, horizontal = 2.dp)
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = label,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(iconTint)
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = iconTint
                )
            )
        }
    }
}

private fun widgetIntent(context: Context, action: String): Intent =
    Intent(context, BrowserActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
