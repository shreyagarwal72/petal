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
    val voiceAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_VOICE))
    val incognitoAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO))
    val greetingText = remember { PetalGreetingManager.getRandomGreeting(context) }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.widgetBackground)
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

                ExpressiveSearchBarCapsule(
                    modifier = GlanceModifier.fillMaxWidth().height(56.dp),
                    searchAction = searchAction,
                    aiAction = aiAction,
                    incognitoAction = incognitoAction,
                    voiceAction = voiceAction
                )
            }
        } else {
            ExpressiveSearchBarCapsule(
                modifier = GlanceModifier.fillMaxSize(),
                searchAction = searchAction,
                aiAction = aiAction,
                incognitoAction = incognitoAction,
                voiceAction = voiceAction
            )
        }
    }
}

@Composable
private fun ExpressiveSearchBarCapsule(
    modifier: GlanceModifier,
    searchAction: androidx.glance.action.Action,
    aiAction: androidx.glance.action.Action,
    incognitoAction: androidx.glance.action.Action,
    voiceAction: androidx.glance.action.Action
) {
    val context = LocalContext.current
    val emblemColorInt = GlanceTheme.colors.primary.getColor(context).toArgb()
    val emblemBgColorInt = GlanceTheme.colors.primaryContainer.getColor(context).toArgb()

    val scallopedEmblemBitmap = remember(emblemBgColorInt, emblemColorInt) {
        createExpressiveEmblemBitmap(
            bgSizeDp = 44,
            bgColorInt = emblemBgColorInt,
            fgColorInt = emblemColorInt,
            context = context
        )
    }

    Box(
        modifier = modifier
            .cornerRadius(30.dp)
            .background(GlanceTheme.colors.surfaceContainerHigh)
            .clickable(searchAction)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Material 3 Expressive Scallop Monogram Badge (Left Side 'G' / Petal Emblem)
            Box(
                modifier = GlanceModifier
                    .size(44.dp)
                    .clickable(searchAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(scallopedEmblemBitmap),
                    contentDescription = "Search",
                    modifier = GlanceModifier.fillMaxSize()
                )
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            // Search hint text area
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .defaultWeight()
                    .clickable(searchAction),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = context.getString(R.string.widget_search_hint),
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // High-Contrast Expressive Action Island Pill (Incognito + AI Sparkle + Lens/Camera)
            Box(
                modifier = GlanceModifier
                    .height(42.dp)
                    .cornerRadius(21.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    // Incognito Shortcut
                    Box(
                        modifier = GlanceModifier
                            .size(34.dp)
                            .cornerRadius(17.dp)
                            .clickable(incognitoAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.icon_incognito),
                            contentDescription = "Incognito Tab",
                            modifier = GlanceModifier.size(18.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(2.dp))

                    // AI Search / Sparkles Shortcut
                    Box(
                        modifier = GlanceModifier
                            .size(34.dp)
                            .cornerRadius(17.dp)
                            .clickable(aiAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_auto_awesome),
                            contentDescription = "Ask Petal AI",
                            modifier = GlanceModifier.size(18.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(2.dp))

                    // Lens / Camera Search Shortcut
                    Box(
                        modifier = GlanceModifier
                            .size(34.dp)
                            .cornerRadius(17.dp)
                            .clickable(voiceAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_lens),
                            contentDescription = "Lens Search",
                            modifier = GlanceModifier.size(18.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
                        )
                    }
                }
            }
        }
    }
}

/** Expressive Scallop Monogram Badge Generator with centered Google 'G' styling. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun createExpressiveEmblemBitmap(
    bgSizeDp: Int = 44,
    bgColorInt: Int,
    fgColorInt: Int,
    context: Context
): Bitmap {
    val density = context.resources.displayMetrics.density
    val px = (bgSizeDp * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val centerX = px / 2f
    val centerY = px / 2f
    val radius = px / 2f

    // 12-vertex rounded scallop star shape
    val polygon = RoundedPolygon.star(
        numVerticesPerRadius = 12,
        radius = radius,
        innerRadius = radius * 0.84f,
        rounding = CornerRounding(radius * 0.30f),
        centerX = centerX,
        centerY = centerY
    )

    val path = polygon.toPath()
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColorInt
        style = Paint.Style.FILL
    }
    canvas.drawPath(path, bgPaint)

    // Draw centered crisp 'G' Monogram letter
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fgColorInt
        textSize = px * 0.52f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    val fontMetrics = textPaint.fontMetrics
    val baselineY = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f
    canvas.drawText("G", centerX, baselineY, textPaint)

    return bitmap
}

private fun widgetIntent(context: Context, action: String): Intent =
    Intent(context, BrowserActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
