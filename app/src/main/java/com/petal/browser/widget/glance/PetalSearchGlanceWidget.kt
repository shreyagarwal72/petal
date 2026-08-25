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
 * Petal's home screen search widget reimagined with Material 3 Expressive morphing shapes.
 * Dynamically renders anti-aliased expressive morphing action buttons (Clover, Scallop, Burst, Cookie)
 * driven by Jetpack Glance & Material 3 color tokens.
 */
class PetalSearchGlanceWidget : GlanceAppWidget() {

    companion object {
        private val SMALL_BOX = DpSize(180.dp, 56.dp)
        private val WIDE_BOX = DpSize(260.dp, 56.dp)
        private val TALL_BOX = DpSize(260.dp, 120.dp)

        fun clearShapeCache() {
            // Synced via GlanceAppWidgetUpdater
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
    val incognitoAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO))
    val newTabAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_NEW_TAB))

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
                // Top Header Row with Greeting & Expressive Quick Action Shortcuts
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .defaultWeight()
                            .clickable(searchAction),
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

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    // Expressive Morphing Incognito Shortcut
                    ExpressiveMorphActionButton(
                        shapeType = ExpressiveShapeType.BURST,
                        bgColor = GlanceTheme.colors.secondaryContainer,
                        iconRes = R.drawable.icon_incognito,
                        iconTint = GlanceTheme.colors.onSecondaryContainer,
                        contentDescription = "New Incognito Tab",
                        action = incognitoAction
                    )

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    // Expressive Morphing New Tab Shortcut
                    ExpressiveMorphActionButton(
                        shapeType = ExpressiveShapeType.COOKIE,
                        bgColor = GlanceTheme.colors.primaryContainer,
                        iconRes = R.drawable.icon_tab_plus,
                        iconTint = GlanceTheme.colors.onPrimaryContainer,
                        contentDescription = "New Tab",
                        action = newTabAction
                    )
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // Expressive Search Bar
                WidgetSearchBar(
                    modifier = GlanceModifier.fillMaxWidth().height(54.dp),
                    searchAction = searchAction,
                    aiAction = aiAction,
                    voiceAction = voiceAction
                )
            }
        } else {
            // Compact 1-row layout with expressive search bar
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
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.icon_search),
                contentDescription = null,
                modifier = GlanceModifier.size(22.dp),
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(4.dp))

            // AI Search Icon with Expressive Clover Morph Shape
            ExpressiveMorphActionButton(
                shapeType = ExpressiveShapeType.CLOVER,
                bgColor = GlanceTheme.colors.primaryContainer,
                iconRes = R.drawable.ic_auto_awesome,
                iconTint = GlanceTheme.colors.onPrimaryContainer,
                contentDescription = context.getString(R.string.widget_ai_search),
                action = aiAction
            )

            Spacer(modifier = GlanceModifier.width(6.dp))

            // Voice Search Icon with Expressive Scallop Morph Shape
            ExpressiveMorphActionButton(
                shapeType = ExpressiveShapeType.SCALLOP,
                bgColor = GlanceTheme.colors.tertiaryContainer,
                iconRes = R.drawable.ic_mic,
                iconTint = GlanceTheme.colors.onTertiaryContainer,
                contentDescription = context.getString(R.string.widget_voice_search),
                action = voiceAction
            )
        }
    }
}

/**
 * Renders a Material 3 Expressive morphing shape container with a centered vector icon.
 */
@Composable
private fun ExpressiveMorphActionButton(
    shapeType: ExpressiveShapeType,
    bgColor: ColorProvider,
    iconRes: Int,
    iconTint: ColorProvider,
    contentDescription: String,
    action: androidx.glance.action.Action,
    modifier: GlanceModifier = GlanceModifier
) {
    val context = LocalContext.current
    val colorInt = bgColor.getColor(context).toArgb()
    val shapeBitmap = remember(shapeType, colorInt) {
        createExpressiveMorphShapeBitmap(shapeType = shapeType, sizeDp = 40, colorInt = colorInt, context = context)
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(shapeBitmap),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize()
        )
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = ColorFilter.tint(iconTint)
        )
    }
}

/** Expressive Morphing Shape Generator using AndroidX Graphics Shapes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun createExpressiveMorphShapeBitmap(
    shapeType: ExpressiveShapeType,
    sizeDp: Int = 40,
    colorInt: Int,
    context: Context
): Bitmap {
    val density = context.resources.displayMetrics.density
    val px = (sizeDp * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val centerX = px / 2f
    val centerY = px / 2f
    val radius = px / 2f

    val polygon = when (shapeType) {
        ExpressiveShapeType.CLOVER -> RoundedPolygon.star(
            numVerticesPerRadius = 4,
            radius = radius,
            innerRadius = radius * 0.72f,
            rounding = CornerRounding(radius * 0.35f),
            centerX = centerX,
            centerY = centerY
        )
        ExpressiveShapeType.SCALLOP -> RoundedPolygon.star(
            numVerticesPerRadius = 8,
            radius = radius,
            innerRadius = radius * 0.82f,
            rounding = CornerRounding(radius * 0.25f),
            centerX = centerX,
            centerY = centerY
        )
        ExpressiveShapeType.BURST -> RoundedPolygon.star(
            numVerticesPerRadius = 6,
            radius = radius,
            innerRadius = radius * 0.65f,
            rounding = CornerRounding(radius * 0.20f),
            centerX = centerX,
            centerY = centerY
        )
        ExpressiveShapeType.COOKIE -> RoundedPolygon.star(
            numVerticesPerRadius = 12,
            radius = radius,
            innerRadius = radius * 0.88f,
            rounding = CornerRounding(radius * 0.40f),
            centerX = centerX,
            centerY = centerY
        )
    }

    val path = polygon.toPath()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        style = Paint.Style.FILL
    }
    canvas.drawPath(path, paint)
    return bitmap
}

private enum class ExpressiveShapeType {
    CLOVER,
    SCALLOP,
    BURST,
    COOKIE
}

private fun widgetIntent(context: Context, action: String): Intent =
    Intent(context, BrowserActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
