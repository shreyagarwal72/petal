package com.petal.browser.widget.glance

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.ui.theme.paletteById
import com.petal.browser.widget.PetalSearchWidgetProvider

/**
 * Petal's home screen search widget, rebuilt on Jetpack Glance so it can use real
 * Material 3 Expressive building blocks — [GlanceTheme] driven by Petal's own
 * [com.petal.browser.ui.theme.PetalPalettes] color schemes (so it matches whatever
 * palette/light-dark mode the user picked in-app, on every API level, not just
 * Android 12+ dynamic color), plus [MaterialShapes] for the colorful "tonal chip"
 * shapes behind the AI and mic actions.
 *
 * Every tap (the search field itself, the AI sparkle, or the mic) always opens a
 * brand-new tab in [BrowserActivity] — see the ACTION_OPEN_* handling in
 * `dispatchIntent()` — rather than reusing/mutating whatever tab was already open.
 */
class PetalSearchGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    companion object {
        private val shapeCache = mutableMapOf<String, Bitmap>()

        /** Call after a theme/palette change so cached shape bitmaps get regenerated. */
        fun clearShapeCache() {
            shapeCache.values.forEach { if (!it.isRecycled) it.recycle() }
            shapeCache.clear()
        }
    }

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
            // Reuse the exact same ColorScheme the rest of the app renders with — the
            // widget always matches the in-app palette/theme choice, on every API level.
            val scheme = paletteById(paletteId).let { if (isDark) it.dark else it.light }
            val uiMode = context.resources.configuration.uiMode

            val sparkShape = remember(uiMode, paletteId, isDark) {
                shapeBitmap(context, "spark_$paletteId$isDark", 96, MaterialShapes.Sunny)
            }
            val micShape = remember(uiMode, paletteId, isDark) {
                shapeBitmap(context, "mic_$paletteId$isDark", 96, MaterialShapes.Circle)
            }

            GlanceTheme(colors = ColorProviders(light = scheme, dark = scheme)) {
                PetalSearchWidgetContent(sparkShape, micShape)
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @SuppressLint("RestrictedApi")
    private fun shapeBitmap(context: Context, cacheKey: String, sizeDp: Int, shape: RoundedPolygon): Bitmap {
        shapeCache[cacheKey]?.let { if (!it.isRecycled) return it }

        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = shape.toPath()
        val matrix = Matrix()
        matrix.setScale(sizePx.toFloat(), sizePx.toFloat())
        path.transform(matrix)

        val paint = Paint().apply {
            color = AndroidColor.WHITE
            isAntiAlias = true
            isFilterBitmap = true
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)

        shapeCache[cacheKey] = bitmap
        return bitmap
    }
}

class PetalSearchGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalSearchGlanceWidget()
}

@Composable
private fun PetalSearchWidgetContent(sparkShape: Bitmap, micShape: Bitmap) {
    val context = LocalContext.current
    val searchAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH))
    val aiAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_AI_SEARCH))
    val voiceAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_VOICE))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(32.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(searchAction)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.icon_search),
                contentDescription = null,
                modifier = GlanceModifier.size(22.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
            )

            Spacer(modifier = GlanceModifier.width(14.dp))

            Box(modifier = GlanceModifier.fillMaxHeight().defaultWeight(), contentAlignment = Alignment.CenterStart) {
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

            // AI sparkle — an "expressive" Sunny-shaped tonal chip so it reads as a
            // distinct, inviting action rather than a plain monochrome icon.
            Box(
                modifier = GlanceModifier.size(44.dp).clickable(aiAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(sparkShape),
                    contentDescription = context.getString(R.string.widget_ai_search),
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primaryContainer)
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_auto_awesome),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
                )
            }

            Spacer(modifier = GlanceModifier.width(2.dp))

            // Mic — a calmer circular tonal chip so the two actions stay visually
            // distinct from each other while sharing the same shape language.
            Box(
                modifier = GlanceModifier.size(44.dp).clickable(voiceAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(micShape),
                    contentDescription = context.getString(R.string.widget_voice_search),
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.secondaryContainer)
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_mic),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer)
                )
            }
        }
    }
}

/**
 * Builds the launch [Intent] for a widget tap. `BrowserActivity.dispatchIntent()`
 * always opens a fresh new tab for every ACTION_OPEN_* widget action (see the
 * `PetalSearchWidgetProvider.ACTION_OPEN_*` branches there) — nothing extra needs to
 * be passed in to request that, it's unconditional for widget taps.
 */
private fun widgetIntent(context: Context, action: String): Intent =
    Intent(context, BrowserActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
