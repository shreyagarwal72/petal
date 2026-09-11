package com.petal.browser.widget.glance

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
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

private fun getWidgetColorScheme(context: Context): androidx.compose.material3.ColorScheme {
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

    return if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        paletteById(paletteId).let { if (isDark) it.dark else it.light }
    }
}

private fun widgetActionIntent(context: Context, action: String): Intent =
    Intent(context, BrowserActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

/**
 * Generates an anti-aliased bitmap for a shape (Scallop, Circle, Pill) with an embedded
 * centered monogram letter or brand icon.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun createMonogramBadgeBitmap(
    isScallop: Boolean,
    sizeDp: Int = 46,
    bgColor: Int,
    textColor: Int,
    text: String = "P",
    context: Context
): Bitmap {
    val density = context.resources.displayMetrics.density
    val px = (sizeDp * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val centerX = px / 2f
    val centerY = px / 2f
    val radius = px / 2f

    val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }

    if (isScallop) {
        val polygon = RoundedPolygon.star(
            numVerticesPerRadius = 10,
            radius = radius * 0.98f,
            innerRadius = radius * 0.82f,
            rounding = CornerRounding(radius * 0.22f),
            centerX = centerX,
            centerY = centerY
        )
        canvas.drawPath(polygon.toPath(), paintBg)
    } else {
        canvas.drawCircle(centerX, centerY, radius * 0.95f, paintBg)
    }

    // Centered bold lettermark "P"
    val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = px * 0.54f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val fontMetrics = paintText.fontMetrics
    val textBaseY = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f
    canvas.drawText(text, centerX, textBaseY, paintText)

    return bitmap
}

/**
 * Petal Search Widget #1 (Image 1):
 * Fully rounded pill search bar featuring an eye-catching scalloped flower-petal badge centered in the bar
 * holding the app brand lettermark "P", flanked by an AI sparkle action on the far left and a
 * camera/lens scanner action on the far right.
 */
class PetalSearchPetal1Widget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val scheme = getWidgetColorScheme(context)
            GlanceTheme(colors = ColorProviders(light = scheme, dark = scheme)) {
                Petal1Content()
            }
        }
    }
}

class PetalSearchPetal1WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalSearchPetal1Widget()
}

@Composable
private fun Petal1Content() {
    val context = LocalContext.current
    val searchAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH))
    val aiAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_AI_SEARCH))
    val lensAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_LENS))

    val badgeBgInt = GlanceTheme.colors.primaryContainer.getColor(context).toArgb()
    val badgeFgInt = GlanceTheme.colors.onPrimaryContainer.getColor(context).toArgb()
    val badgeBitmap = remember(badgeBgInt, badgeFgInt) {
        createMonogramBadgeBitmap(
            isScallop = true,
            sizeDp = 48,
            bgColor = badgeBgInt,
            textColor = badgeFgInt,
            text = "P",
            context = context
        )
    }

    // Pill search bar container (height 56dp, corner radius 28dp)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .clickable(searchAction)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Far Left: AI Sparkle Action
            Box(
                modifier = GlanceModifier
                    .size(44.dp)
                    .cornerRadius(22.dp)
                    .clickable(aiAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_sparkle_clean),
                    contentDescription = "Petal AI Search",
                    modifier = GlanceModifier.size(24.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                )
            }

            // Left Search Click Area
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .defaultWeight()
                    .clickable(searchAction)
            ) {}

            // Centered: Scalloped Flower-Petal Badge holding "P"
            Box(
                modifier = GlanceModifier
                    .size(48.dp)
                    .clickable(searchAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(badgeBitmap),
                    contentDescription = "Petal Home",
                    modifier = GlanceModifier.fillMaxSize()
                )
            }

            // Right Search Click Area
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .defaultWeight()
                    .clickable(searchAction)
            ) {}

            // Far Right: Camera / Lens Scanner Action
            Box(
                modifier = GlanceModifier
                    .size(44.dp)
                    .cornerRadius(22.dp)
                    .clickable(lensAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_lens_scanner),
                    contentDescription = "Visual Lens Scanner",
                    modifier = GlanceModifier.size(24.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }
}

/**
 * Petal Search Widget #2 (Image 2):
 * Pill container containing an embedded "Search" pill button on the left and a trio of distinct circular
 * quick-action buttons on the right—an AI sparkle button, an Incognito private browsing button, and a
 * vibrant scanner shortcut button—each opening its own dedicated intent.
 */
class PetalSearchPetal2Widget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val scheme = getWidgetColorScheme(context)
            GlanceTheme(colors = ColorProviders(light = scheme, dark = scheme)) {
                Petal2Content()
            }
        }
    }
}

class PetalSearchPetal2WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalSearchPetal2Widget()
}

@Composable
private fun Petal2Content() {
    val context = LocalContext.current
    val searchAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH))
    val aiAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_AI_SEARCH))
    val incognitoAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO))
    val lensAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_LENS))

    // Outer Pill Container
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Embedded "Search" Pill Button on the left
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .defaultWeight()
                    .cornerRadius(22.dp)
                    .background(GlanceTheme.colors.surface)
                    .clickable(searchAction)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Search",
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Trio of distinct circular quick-action buttons on the right:
            // 1. AI Sparkle Button (primaryContainer)
            Box(
                modifier = GlanceModifier
                    .size(42.dp)
                    .cornerRadius(21.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .clickable(aiAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_sparkle_clean),
                    contentDescription = "Petal AI Search",
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
                )
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // 2. Incognito Private Browsing Button (secondaryContainer)
            Box(
                modifier = GlanceModifier
                    .size(42.dp)
                    .cornerRadius(21.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .clickable(incognitoAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.icon_incognito),
                    contentDescription = "Incognito Mode",
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer)
                )
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // 3. Vibrant Scanner Shortcut Button (tertiaryContainer)
            Box(
                modifier = GlanceModifier
                    .size(42.dp)
                    .cornerRadius(21.dp)
                    .background(GlanceTheme.colors.tertiaryContainer)
                    .clickable(lensAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_lens_scanner),
                    contentDescription = "Visual Scanner",
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onTertiaryContainer)
                )
            }
        }
    }
}

/**
 * Petal Search Widget #3 (Image 3):
 * Minimalist horizontal pill bar with a clean circular brand badge anchored to the far left
 * holding the app logo / lettermark "P", leading into an open search area with a single AI sparkle
 * shortcut icon aligned on the right.
 */
class PetalSearchPetal3Widget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val scheme = getWidgetColorScheme(context)
            GlanceTheme(colors = ColorProviders(light = scheme, dark = scheme)) {
                Petal3Content()
            }
        }
    }
}

class PetalSearchPetal3WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalSearchPetal3Widget()
}

@Composable
private fun Petal3Content() {
    val context = LocalContext.current
    val searchAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH))
    val aiAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_AI_SEARCH))

    val badgeBgInt = GlanceTheme.colors.primaryContainer.getColor(context).toArgb()
    val badgeFgInt = GlanceTheme.colors.onPrimaryContainer.getColor(context).toArgb()
    val badgeBitmap = remember(badgeBgInt, badgeFgInt) {
        createMonogramBadgeBitmap(
            isScallop = false,
            sizeDp = 44,
            bgColor = badgeBgInt,
            textColor = badgeFgInt,
            text = "P",
            context = context
        )
    }

    // Minimalist Horizontal Pill Bar (height 56dp, corner radius 28dp)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .clickable(searchAction)
            .padding(start = 6.dp, end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Anchored Far Left: Clean Circular Brand Badge holding "P"
            Box(
                modifier = GlanceModifier
                    .size(44.dp)
                    .clickable(searchAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(badgeBitmap),
                    contentDescription = "Petal Home",
                    modifier = GlanceModifier.fillMaxSize()
                )
            }

            // Open Search Area
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .defaultWeight()
                    .clickable(searchAction)
                    .padding(start = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Search or type URL",
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }

            // Right: Single AI Sparkle Shortcut Icon
            Box(
                modifier = GlanceModifier
                    .size(40.dp)
                    .cornerRadius(20.dp)
                    .clickable(aiAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_sparkle_clean),
                    contentDescription = "Ask Petal AI",
                    modifier = GlanceModifier.size(24.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }
}
