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
import androidx.glance.layout.defaultWeight
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

        private val shapeCache = mutableMapOf<String, Bitmap>()

        /** Call after a theme/palette change so cached shape bitmaps get regenerated. */
        fun clearShapeCache() {
            shapeCache.values.forEach { if (!it.isRecycled) it.recycle() }
            shapeCache.clear()
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
            val uiMode = context.resources.configuration.uiMode

            val sparkShape = remember(uiMode, paletteId, isDark) {
                shapeBitmap(context, "spark_$paletteId$isDark", 96, MaterialShapes.Sunny, scheme.primaryContainer.toArgb())
            }
            val micShape = remember(uiMode, paletteId, isDark) {
                shapeBitmap(context, "mic_$paletteId$isDark", 96, MaterialShapes.Pill, scheme.secondaryContainer.toArgb())
            }
            val incognitoShape = remember(uiMode, paletteId, isDark) {
                shapeBitmap(context, "incognito_$paletteId$isDark", 96, MaterialShapes.Clover, scheme.tertiaryContainer.toArgb())
            }
            val bookmarkShape = remember(uiMode, paletteId, isDark) {
                shapeBitmap(context, "bookmark_$paletteId$isDark", 96, MaterialShapes.Scallop, scheme.secondaryContainer.toArgb())
            }
            val downloadShape = remember(uiMode, paletteId, isDark) {
                shapeBitmap(context, "download_$paletteId$isDark", 96, MaterialShapes.Pentagon, scheme.primaryContainer.toArgb())
            }
            val newTabShape = remember(uiMode, paletteId, isDark) {
                shapeBitmap(context, "newtab_$paletteId$isDark", 96, MaterialShapes.Circle, scheme.surfaceContainerHigh.toArgb())
            }

            GlanceTheme(colors = ColorProviders(light = scheme, dark = scheme)) {
                val size = LocalSize.current
                val isTall = size.height >= 100.dp
                PetalSearchWidgetContent(
                    isTall = isTall,
                    sparkShape = sparkShape,
                    micShape = micShape,
                    incognitoShape = incognitoShape,
                    bookmarkShape = bookmarkShape,
                    downloadShape = downloadShape,
                    newTabShape = newTabShape
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @SuppressLint("RestrictedApi")
    private fun shapeBitmap(
        context: Context,
        cacheKey: String,
        sizeDp: Int,
        shape: RoundedPolygon,
        fillColorInt: Int
    ): Bitmap {
        val key = "${cacheKey}_$fillColorInt"
        shapeCache[key]?.let { if (!it.isRecycled) return it }

        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = shape.toPath()
        val matrix = Matrix()
        matrix.setScale(sizePx.toFloat(), sizePx.toFloat())
        path.transform(matrix)

        val paint = Paint().apply {
            color = fillColorInt
            isAntiAlias = true
            isFilterBitmap = true
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)

        shapeCache[key] = bitmap
        return bitmap
    }
}

class PetalSearchGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalSearchGlanceWidget()
}

@Composable
private fun PetalSearchWidgetContent(
    isTall: Boolean,
    sparkShape: Bitmap,
    micShape: Bitmap,
    incognitoShape: Bitmap,
    bookmarkShape: Bitmap,
    downloadShape: Bitmap,
    newTabShape: Bitmap
) {
    val context = LocalContext.current
    val searchAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH))
    val aiAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_AI_SEARCH))
    val voiceAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_VOICE))
    val incognitoAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO))
    val bookmarkAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_BOOKMARKS))
    val downloadAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_DOWNLOADS))
    val newTabAction = actionStartActivity(widgetIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_NEW_TAB))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(8.dp)
    ) {
        if (isTall) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Top Search Bar
                WidgetSearchBar(
                    modifier = GlanceModifier.fillMaxWidth().height(52.dp),
                    searchAction = searchAction,
                    aiAction = aiAction,
                    voiceAction = voiceAction,
                    sparkShape = sparkShape,
                    micShape = micShape
                )

                Spacer(modifier = GlanceModifier.height(6.dp))

                // M3 Expressive Quick Action Row
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    QuickActionTile(
                        modifier = GlanceModifier.defaultWeight(),
                        shapeBitmap = incognitoShape,
                        iconRes = R.drawable.icon_incognito,
                        iconTint = GlanceTheme.colors.onTertiaryContainer,
                        label = "Incognito",
                        action = incognitoAction
                    )
                    QuickActionTile(
                        modifier = GlanceModifier.defaultWeight(),
                        shapeBitmap = bookmarkShape,
                        iconRes = R.drawable.icon_bookmark,
                        iconTint = GlanceTheme.colors.onSecondaryContainer,
                        label = "Bookmarks",
                        action = bookmarkAction
                    )
                    QuickActionTile(
                        modifier = GlanceModifier.defaultWeight(),
                        shapeBitmap = downloadShape,
                        iconRes = R.drawable.icon_download,
                        iconTint = GlanceTheme.colors.onPrimaryContainer,
                        label = "Downloads",
                        action = downloadAction
                    )
                    QuickActionTile(
                        modifier = GlanceModifier.defaultWeight(),
                        shapeBitmap = newTabShape,
                        iconRes = R.drawable.icon_tab_plus,
                        iconTint = GlanceTheme.colors.onSurfaceVariant,
                        label = "New Tab",
                        action = newTabAction
                    )
                }
            }
        } else {
            // Compact 1-row layout
            WidgetSearchBar(
                modifier = GlanceModifier.fillMaxSize(),
                searchAction = searchAction,
                aiAction = aiAction,
                voiceAction = voiceAction,
                sparkShape = sparkShape,
                micShape = micShape
            )
        }
    }
}

@Composable
private fun WidgetSearchBar(
    modifier: GlanceModifier,
    searchAction: androidx.glance.action.Action,
    aiAction: androidx.glance.action.Action,
    voiceAction: androidx.glance.action.Action,
    sparkShape: Bitmap,
    micShape: Bitmap
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .cornerRadius(26.dp)
            .background(GlanceTheme.colors.surfaceContainerHighest)
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
                modifier = GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
            )

            Spacer(modifier = GlanceModifier.width(12.dp))

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

            // AI sparkle chip
            Box(
                modifier = GlanceModifier.size(40.dp).clickable(aiAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(sparkShape),
                    contentDescription = context.getString(R.string.widget_ai_search),
                    modifier = GlanceModifier.fillMaxSize()
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_auto_awesome),
                    contentDescription = null,
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
                )
            }

            Spacer(modifier = GlanceModifier.width(2.dp))

            // Voice mic chip
            Box(
                modifier = GlanceModifier.size(40.dp).clickable(voiceAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(micShape),
                    contentDescription = context.getString(R.string.widget_voice_search),
                    modifier = GlanceModifier.fillMaxSize()
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_mic),
                    contentDescription = null,
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer)
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    modifier: GlanceModifier,
    shapeBitmap: Bitmap,
    iconRes: Int,
    iconTint: ColorProvider,
    label: String,
    action: androidx.glance.action.Action
) {
    Column(
        modifier = modifier.clickable(action),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(shapeBitmap),
                contentDescription = label,
                modifier = GlanceModifier.fillMaxSize()
            )
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(iconTint)
            )
        }
        Spacer(modifier = GlanceModifier.height(3.dp))
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )
    }
}

private fun widgetIntent(context: Context, action: String): Intent =
    Intent(context, BrowserActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

