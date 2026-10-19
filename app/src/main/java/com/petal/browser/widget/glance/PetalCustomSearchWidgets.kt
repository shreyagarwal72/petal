package com.petal.browser.widget.glance

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
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
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.applyAmoled
import com.petal.browser.ui.theme.applyStyle
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.ui.theme.paletteById
import com.petal.browser.widget.PetalSearchWidgetProvider

import androidx.compose.ui.graphics.Color
import androidx.core.content.res.ResourcesCompat
import com.petal.browser.ui.theme.AppFont


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
    val paletteId = sp.getString("sp_palette_id", "petal") ?: "petal"
    val useDynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
    val isAmoled = sp.getBoolean("sp_amoled", false)
    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
    val colorStyle = try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
    val expressiveColors = sp.getBoolean("sp_expressive_colors", false)

    var scheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        paletteById(paletteId).let { if (isDark) it.dark else it.light }
    }

    scheme = scheme.applyStyle(colorStyle)

    if (expressiveColors) {
        scheme = if (isDark) {
            if (isAmoled) {
                scheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color(0xFF0B0B0B),
                    surfaceContainer = Color(0xFF181818),
                    surfaceContainerHigh = Color(0xFF1E1E1E),
                    surfaceContainerHighest = Color(0xFF262626),
                    surfaceVariant = Color(0xFF1C1C1C)
                )
            } else {
                scheme.copy(
                    background = scheme.surfaceContainerLow,
                    surface = scheme.surfaceContainerLow,
                    surfaceContainer = scheme.surfaceContainerHigh,
                    surfaceContainerLow = scheme.surfaceContainerHigh,
                    surfaceContainerHigh = scheme.surfaceContainerHigh,
                    surfaceContainerHighest = scheme.surfaceContainerHigh,
                    surfaceContainerLowest = scheme.surfaceContainerHigh
                )
            }
        } else {
            scheme.copy(
                background = scheme.surfaceContainerLow,
                surface = scheme.surfaceContainerLow,
                surfaceContainer = Color.White,
                surfaceContainerLow = Color.White,
                surfaceContainerHigh = Color.White,
                surfaceContainerHighest = Color.White,
                surfaceContainerLowest = Color.White
            )
        }
    } else if (isDark && isAmoled) {
        scheme = scheme.applyAmoled()
    }

    val liquidGlassEnabled = sp.getBoolean("sp_liquid_glass_enabled", false)
    if (liquidGlassEnabled) {
        val glassAlpha = sp.getFloat("sp_liquid_glass_alpha", 0.70f).coerceIn(0.20f, 0.95f)
        val outerAlpha = (glassAlpha * 0.45f).coerceIn(0.12f, 0.75f)
        val innerAlpha = (glassAlpha * 0.38f).coerceIn(0.10f, 0.65f)
        val pillAlpha = (glassAlpha * 0.35f).coerceIn(0.10f, 0.60f)

        scheme = scheme.copy(
            surfaceContainerLow = scheme.surfaceContainerLow.copy(alpha = outerAlpha),
            surfaceContainer = scheme.surfaceContainer.copy(alpha = innerAlpha),
            surfaceContainerHigh = scheme.surfaceContainerHigh.copy(alpha = innerAlpha),
            surfaceContainerHighest = scheme.surfaceContainerHighest.copy(alpha = innerAlpha),
            surfaceContainerLowest = scheme.surfaceContainerLowest.copy(alpha = outerAlpha),
            primaryContainer = scheme.primaryContainer.copy(alpha = pillAlpha),
            secondaryContainer = scheme.secondaryContainer.copy(alpha = pillAlpha),
            tertiaryContainer = scheme.tertiaryContainer.copy(alpha = pillAlpha)
        )
    }

    return scheme
}

private fun surfaceContainerLowProvider(scheme: androidx.compose.material3.ColorScheme): ColorProvider =
    ColorProvider(scheme.surfaceContainerLow)

private fun surfaceContainerHighProvider(scheme: androidx.compose.material3.ColorScheme): ColorProvider =
    ColorProvider(scheme.surfaceContainerHigh)

private fun surfaceContainerHighestProvider(scheme: androidx.compose.material3.ColorScheme): ColorProvider =
    ColorProvider(scheme.surfaceContainerHighest)

private fun getWidgetTypeface(context: Context): Typeface {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val appFont = AppFont.fromName(sp.getString("sp_app_font", "PETAL"))
    if (appFont == AppFont.CUSTOM) {
        val path = sp.getString("sp_custom_font_path", null)
        if (!path.isNullOrBlank()) {
            val file = java.io.File(path)
            if (file.exists() && file.canRead()) {
                try {
                    return Typeface.createFromFile(file)
                } catch (_: Exception) {}
            }
        }
    }
    return try {
        ResourcesCompat.getFont(context, R.font.google_sans_flex_variable) ?: Typeface.DEFAULT_BOLD
    } catch (_: Exception) {
        Typeface.DEFAULT_BOLD
    }
}

private fun widgetActionIntent(context: Context, action: String): Intent =
    Intent(context, BrowserActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

/**
 * Fast two-pass box blur on ARGB_8888 bitmap.
 * Runs quickly on downsampled bitmaps to produce a soft frosted glass effect.
 */
private fun fastBoxBlur(src: Bitmap, radius: Int): Bitmap {
    if (radius < 1) return src
    val w = src.width
    val h = src.height
    val pix = IntArray(w * h)
    src.getPixels(pix, 0, w, 0, 0, w, h)

    val wm = w - 1
    val hm = h - 1
    val wh = w * h
    val div = radius + radius + 1

    val r = IntArray(wh)
    val g = IntArray(wh)
    val b = IntArray(wh)
    var rsum: Int
    var gsum: Int
    var bsum: Int
    var x: Int
    var y: Int
    var i: Int
    var p: Int
    var yp: Int
    var yi: Int
    var yw: Int
    val vmin = IntArray(maxOf(w, h))

    var divsum = (div + 1) shr 1
    divsum *= divsum
    val dv = IntArray(256 * divsum)
    for (idx in 0 until 256 * divsum) {
        dv[idx] = idx / divsum
    }

    yw = 0
    yi = 0

    for (row in 0 until h) {
        rsum = 0
        gsum = 0
        bsum = 0
        for (col in -radius..radius) {
            p = pix[yi + minOf(wm, maxOf(col, 0))]
            rsum += (p shr 16) and 0xff
            gsum += (p shr 8) and 0xff
            bsum += p and 0xff
        }
        for (col in 0 until w) {
            r[yi] = dv[rsum]
            g[yi] = dv[gsum]
            b[yi] = dv[bsum]

            if (row == 0) {
                vmin[col] = minOf(col + radius + 1, wm)
            }
            val p1 = pix[yw + vmin[col]]
            val p2 = pix[yw + maxOf(col - radius, 0)]

            rsum += ((p1 shr 16) and 0xff) - ((p2 shr 16) and 0xff)
            gsum += ((p1 shr 8) and 0xff) - ((p2 shr 8) and 0xff)
            bsum += (p1 and 0xff) - (p2 and 0xff)
            yi++
        }
        yw += w
    }

    for (col in 0 until w) {
        rsum = 0
        gsum = 0
        bsum = 0
        yp = -radius * w
        for (row in -radius..radius) {
            yi = maxOf(0, yp) + col
            rsum += r[yi]
            gsum += g[yi]
            bsum += b[yi]
            yp += w
        }
        yi = col
        for (row in 0 until h) {
            pix[yi] = (0xff000000.toInt()) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
            if (col == 0) {
                vmin[row] = minOf(row + radius + 1, hm) * w
            }
            val p1 = col + vmin[row]
            val p2 = col + maxOf(row - radius, 0) * w

            rsum += r[p1] - r[p2]
            gsum += g[p1] - g[p2]
            bsum += b[p1] - b[p2]

            yi += w
        }
    }

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(pix, 0, w, 0, 0, w, h)
    return out
}

/**
 * Samples the current wallpaper (Petal custom wallpaper or system wallpaper)
 * and generates a frosted liquid glass card bitmap with dual specular highlight rim.
 */
private fun createLiquidGlassCardBitmap(
    widthDp: Int,
    heightDp: Int,
    cornerDp: Int,
    scheme: androidx.compose.material3.ColorScheme,
    context: Context
): Bitmap? {
    val density = context.resources.displayMetrics.density
    val w = (widthDp * density).toInt().coerceAtLeast(10)
    val h = (heightDp * density).toInt().coerceAtLeast(10)

    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val isGlass = sp.getBoolean("sp_liquid_glass_enabled", false)
    val sheen = sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f)
    val glassAlpha = sp.getFloat("sp_liquid_glass_alpha", 0.70f).coerceIn(0.20f, 0.95f)

    var baseWallpaper: Bitmap? = null
    try {
        val profileUri = com.petal.browser.wallpaper.PetalWallpaperManager.wallpaperUri
            ?: sp.getString("sp_home_wallpaper_uri", null)
        if (!profileUri.isNullOrBlank()) {
            val uri = android.net.Uri.parse(profileUri)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                baseWallpaper = android.graphics.BitmapFactory.decodeStream(stream)
            }
        }
    } catch (_: Throwable) {}

    if (baseWallpaper == null) {
        try {
            val wm = android.app.WallpaperManager.getInstance(context)
            val d = wm.drawable
            if (d is android.graphics.drawable.BitmapDrawable) {
                baseWallpaper = d.bitmap
            } else if (d != null) {
                val bmp = Bitmap.createBitmap(w.coerceAtLeast(100), h.coerceAtLeast(100), Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                d.setBounds(0, 0, c.width, c.height)
                d.draw(c)
                baseWallpaper = bmp
            }
        } catch (_: Throwable) {}
    }

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val rect = android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat())
    val cornerPx = cornerDp * density

    val path = android.graphics.Path().apply {
        addRoundRect(rect, cornerPx, cornerPx, android.graphics.Path.Direction.CW)
    }

    canvas.save()
    canvas.clipPath(path)

    if (baseWallpaper != null && !baseWallpaper!!.isRecycled) {
        // Downscale wallpaper to card resolution then apply fast box blur for genuine frosted glass
        try {
            val downscaleW = (w / 3).coerceAtLeast(40)
            val downscaleH = (h / 3).coerceAtLeast(40)
            val scaledDown = Bitmap.createScaledBitmap(baseWallpaper!!, downscaleW, downscaleH, true)
            val blurred = fastBoxBlur(scaledDown, radius = 6)
            val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val dstRect = android.graphics.Rect(0, 0, w, h)
            canvas.drawBitmap(blurred, null, dstRect, filterPaint)
            if (blurred !== scaledDown && !blurred.isRecycled) blurred.recycle()
            if (!scaledDown.isRecycled) scaledDown.recycle()
        } catch (_: Throwable) {
            // Fallback: draw directly if downscale/blur fails
            val srcW = baseWallpaper!!.width
            val srcH = baseWallpaper!!.height
            val srcRect = android.graphics.Rect(0, (srcH * 0.15f).toInt(), srcW, (srcH * 0.85f).toInt())
            val dstRect = android.graphics.Rect(0, 0, w, h)
            val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(baseWallpaper!!, srcRect, dstRect, filterPaint)
        }
    }

    // Frosted tint overlay matching the scheme surfaceContainer
    val surfaceInt = scheme.surfaceContainer.toArgb()
    val tintAlpha = if (isGlass) (glassAlpha * 0.55f).coerceIn(0.20f, 0.80f) else 1.0f
    val alphaInt = (tintAlpha * 255).toInt().coerceIn(0, 255)
    val tintColor = android.graphics.Color.argb(
        alphaInt,
        android.graphics.Color.red(surfaceInt),
        android.graphics.Color.green(surfaceInt),
        android.graphics.Color.blue(surfaceInt)
    )
    val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintColor
        style = Paint.Style.FILL
    }
    canvas.drawRect(rect, tintPaint)

    // Liquid Glass Specular Rim Border & Sheen Highlight
    if (isGlass) {
        val rimStrokeWidth = 1.5f * density
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = rimStrokeWidth
            shader = android.graphics.LinearGradient(
                0f, 0f, w.toFloat() * 0.6f, h.toFloat(),
                android.graphics.Color.argb((220 * sheen).toInt().coerceIn(40, 255), 255, 255, 255),
                android.graphics.Color.argb((35 * sheen).toInt().coerceIn(10, 100), 255, 255, 255),
                Shader.TileMode.CLAMP
            )
        }
        val insetRect = android.graphics.RectF(
            rimStrokeWidth / 2f,
            rimStrokeWidth / 2f,
            w.toFloat() - rimStrokeWidth / 2f,
            h.toFloat() - rimStrokeWidth / 2f
        )
        val rimCorner = (cornerPx - rimStrokeWidth / 2f).coerceAtLeast(0f)
        canvas.drawRoundRect(insetRect, rimCorner, rimCorner, rimPaint)
    }

    canvas.restore()
    return bitmap
}

/**
 * Generates an anti-aliased bitmap with an expressive radial gradient and bold letter "P".
 * When Liquid Glass is enabled, renders specular reflective rim stroke and translucent gradient.
 */
private fun createGoogleStylePBadgeBitmap(
    sizeDp: Int = 42,
    primaryColor: Int,
    containerColor: Int,
    textColor: Int,
    context: Context
): Bitmap {
    val density = context.resources.displayMetrics.density
    val px = (sizeDp * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val centerX = px / 2f
    val centerY = px / 2f
    val radius = px / 2f

    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val isGlass = sp.getBoolean("sp_liquid_glass_enabled", false)

    val gradient = RadialGradient(
        centerX,
        centerY * 0.7f,
        radius,
        primaryColor,
        containerColor,
        Shader.TileMode.CLAMP
    )
    val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = gradient
        style = Paint.Style.FILL
    }
    canvas.drawCircle(centerX, centerY, radius * 0.94f, paintBg)

    if (isGlass) {
        val sheen = sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f)
        val rimStrokeWidth = 1.75f * density
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = rimStrokeWidth
            shader = android.graphics.LinearGradient(
                0f, 0f, px.toFloat(), px.toFloat(),
                android.graphics.Color.argb((180 * sheen).toInt().coerceIn(40, 240), 255, 255, 255),
                android.graphics.Color.argb((40 * sheen).toInt().coerceIn(10, 100), 255, 255, 255),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(centerX, centerY, (radius * 0.94f) - (rimStrokeWidth / 2f), rimPaint)
    }

    val customTypeface = getWidgetTypeface(context)
    val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = px * 0.56f
        typeface = customTypeface
        textAlign = Paint.Align.CENTER
    }
    val fontMetrics = paintText.fontMetrics
    val textBaseY = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f
    canvas.drawText("P", centerX, textBaseY, paintText)

    return bitmap
}

/**
 * Generates an anti-aliased bitmap for a shape (Scallop, Circle, Pill) with an embedded
 * centered monogram letter or brand icon.
 * When Liquid Glass is enabled, renders specular reflective rim stroke.
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

    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val isGlass = sp.getBoolean("sp_liquid_glass_enabled", false)

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
        val path = polygon.toPath()
        canvas.drawPath(path, paintBg)

        if (isGlass) {
            val sheen = sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f)
            val rimStrokeWidth = 1.75f * density
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = rimStrokeWidth
                shader = android.graphics.LinearGradient(
                    0f, 0f, px.toFloat(), px.toFloat(),
                    android.graphics.Color.argb((180 * sheen).toInt().coerceIn(40, 240), 255, 255, 255),
                    android.graphics.Color.argb((40 * sheen).toInt().coerceIn(10, 100), 255, 255, 255),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(path, rimPaint)
        }
    } else {
        canvas.drawCircle(centerX, centerY, radius * 0.95f, paintBg)
        if (isGlass) {
            val sheen = sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f)
            val rimStrokeWidth = 1.75f * density
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = rimStrokeWidth
                shader = android.graphics.LinearGradient(
                    0f, 0f, px.toFloat(), px.toFloat(),
                    android.graphics.Color.argb((180 * sheen).toInt().coerceIn(40, 240), 255, 255, 255),
                    android.graphics.Color.argb((40 * sheen).toInt().coerceIn(10, 100), 255, 255, 255),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(centerX, centerY, (radius * 0.95f) - (rimStrokeWidth / 2f), rimPaint)
        }
    }

    val customTypeface = getWidgetTypeface(context)
    val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = px * 0.54f
        typeface = customTypeface
        textAlign = Paint.Align.CENTER
    }
    val fontMetrics = paintText.fontMetrics
    val textBaseY = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f
    canvas.drawText(text, centerX, textBaseY, paintText)

    return bitmap
}

/**
 * Petal Search Widget #1:
 * Material 3 Expressive Pill & Expandable Grid
 * - Compact (height < 85dp): Material 3 Expressive Pill with gradient "P" brand badge,
 *   subtle search trigger area, and interactive expressive action circles (AI, Voice, Lens).
 * - Expanded (height >= 85dp): Combines the search pill header with expressive squircle
 *   shortcut tiles with icons and labels (New Tab, Bookmarks, Downloads, Incognito).
 */
class PetalSearchPetal1Widget : GlanceAppWidget() {

    companion object {
        private val COMPACT_1X1 = DpSize(200.dp, 48.dp)
        private val EXPANDED_1X2 = DpSize(240.dp, 90.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_1X1, EXPANDED_1X2)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val scheme = getWidgetColorScheme(context)
            GlanceTheme(colors = ColorProviders(light = scheme, dark = scheme)) {
                val size = LocalSize.current
                val isExpanded = size.height >= 85.dp
                Petal1Content(scheme = scheme, isExpanded = isExpanded)
            }
        }
    }
}

class PetalSearchPetal1WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalSearchPetal1Widget()
}

@Composable
private fun Petal1Content(scheme: androidx.compose.material3.ColorScheme, isExpanded: Boolean) {
    val context = LocalContext.current
    val searchAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH))
    val aiAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_AI_SEARCH))
    val voiceAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_VOICE))
    val snapCameraAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SNAP_CAMERA))
    val bookmarksAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_BOOKMARKS))
    val downloadsAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_DOWNLOADS))
    val newTabAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_NEW_TAB))
    val incognitoAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO))

    val pPrimary = GlanceTheme.colors.primary.getColor(context).toArgb()
    val pContainer = GlanceTheme.colors.primaryContainer.getColor(context).toArgb()
    val pOnPrimary = GlanceTheme.colors.onPrimary.getColor(context).toArgb()

    val pBadgeBitmap = remember(pPrimary, pContainer, pOnPrimary) {
        createGoogleStylePBadgeBitmap(
            sizeDp = 42,
            primaryColor = pPrimary,
            containerColor = pContainer,
            textColor = pOnPrimary,
            context = context
        )
    }

    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val isLiquidGlass = sp.getBoolean("sp_liquid_glass_enabled", false)

    if (isExpanded) {
        val glassCardBitmap = remember(scheme, isLiquidGlass) {
            if (isLiquidGlass) {
                createLiquidGlassCardBitmap(widthDp = 280, heightDp = 110, cornerDp = 28, scheme = scheme, context = context)
            } else null
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .then(
                    if (glassCardBitmap != null) GlanceModifier else GlanceModifier.background(surfaceContainerLowProvider(scheme))
                )
        ) {
            if (glassCardBitmap != null) {
                Image(
                    provider = ImageProvider(glassCardBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize()
                )
            }
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(10.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Top Search Pill Row
                Petal1ExpressiveSearchRow(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .cornerRadius(24.dp)
                        .background(surfaceContainerHighProvider(scheme))
                        .padding(horizontal = 6.dp),
                    badgeBitmap = pBadgeBitmap,
                    searchAction = searchAction,
                    aiAction = aiAction,
                    voiceAction = voiceAction,
                    snapCameraAction = snapCameraAction
                )

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Bottom Row: Expressive Squircle Shortcuts Grid
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    WidgetShortcutTile(
                        label = context.getString(R.string.ui_new_tab),
                        iconRes = R.drawable.widget_ic_tab_plus,
                        bgColor = GlanceTheme.colors.primaryContainer,
                        iconTint = GlanceTheme.colors.onPrimaryContainer,
                        action = newTabAction,
                        modifier = GlanceModifier.defaultWeight()
                    )

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    WidgetShortcutTile(
                        label = context.getString(R.string.ui_bookmarks),
                        iconRes = R.drawable.widget_ic_bookmark,
                        bgColor = GlanceTheme.colors.secondaryContainer,
                        iconTint = GlanceTheme.colors.onSecondaryContainer,
                        action = bookmarksAction,
                        modifier = GlanceModifier.defaultWeight()
                    )

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    WidgetShortcutTile(
                        label = context.getString(R.string.ui_downloads),
                        iconRes = R.drawable.ic_download,
                        bgColor = GlanceTheme.colors.tertiaryContainer,
                        iconTint = GlanceTheme.colors.onTertiaryContainer,
                        action = downloadsAction,
                        modifier = GlanceModifier.defaultWeight()
                    )

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    WidgetShortcutTile(
                        label = context.getString(R.string.ui_private),
                        iconRes = R.drawable.widget_ic_incognito,
                        bgColor = surfaceContainerHighestProvider(scheme),
                        iconTint = GlanceTheme.colors.onSurface,
                        action = incognitoAction,
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
            }
        }
    } else {
        // Compact Pill Mode
        val glassPillBitmap = remember(scheme, isLiquidGlass) {
            if (isLiquidGlass) {
                createLiquidGlassCardBitmap(widthDp = 240, heightDp = 52, cornerDp = 26, scheme = scheme, context = context)
            } else null
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .then(
                    if (glassPillBitmap != null) GlanceModifier else GlanceModifier.background(surfaceContainerHighProvider(scheme))
                )
        ) {
            if (glassPillBitmap != null) {
                Image(
                    provider = ImageProvider(glassPillBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize()
                )
            }
            Petal1ExpressiveSearchRow(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                badgeBitmap = pBadgeBitmap,
                searchAction = searchAction,
                aiAction = aiAction,
                voiceAction = voiceAction,
                snapCameraAction = snapCameraAction
            )
        }
    }
}

@Composable
private fun Petal1ExpressiveSearchRow(
    modifier: GlanceModifier,
    badgeBitmap: Bitmap,
    searchAction: androidx.glance.action.Action,
    aiAction: androidx.glance.action.Action,
    voiceAction: androidx.glance.action.Action,
    snapCameraAction: androidx.glance.action.Action
) {
    val context = LocalContext.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // Left: Google-style Gradient "P" Brand Badge
        Box(
            modifier = GlanceModifier
                .size(42.dp)
                .clickable(searchAction),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(badgeBitmap),
                contentDescription = context.getString(R.string.ui_petal),
                modifier = GlanceModifier.size(38.dp)
            )
        }

        // Middle: Search Hint / Area
        Box(
            modifier = GlanceModifier
                .fillMaxHeight()
                .defaultWeight()
                .clickable(searchAction)
                .padding(start = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = context.getString(R.string.ui_search),
                maxLines = 1,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }

        // Right 1: AI Search Sparkle Icon
        Box(
            modifier = GlanceModifier
                .size(38.dp)
                .clickable(aiAction),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_ic_search_sparkle),
                contentDescription = context.getString(R.string.ui_petal_ai_search),
                modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
            )
        }

        Spacer(modifier = GlanceModifier.width(2.dp))

        // Right 2: Voice Search Mic Icon
        Box(
            modifier = GlanceModifier
                .size(38.dp)
                .clickable(voiceAction),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_ic_mic),
                contentDescription = context.getString(R.string.ui_voice_search_2),
                modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
            )
        }

        Spacer(modifier = GlanceModifier.width(2.dp))

        // Right 3: Lens Camera Scanner Icon
        Box(
            modifier = GlanceModifier
                .size(38.dp)
                .clickable(snapCameraAction),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_ic_lens_camera),
                contentDescription = context.getString(R.string.ui_visual_camera_scanner),
                modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
            )
        }
    }
}

@Composable
private fun WidgetShortcutTile(
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
            .cornerRadius(20.dp)
            .background(bgColor)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically,
            modifier = GlanceModifier.padding(vertical = 6.dp, horizontal = 2.dp)
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = label,
                modifier = GlanceModifier.size(22.dp),
                colorFilter = ColorFilter.tint(iconTint)
            )
            Spacer(modifier = GlanceModifier.height(3.dp))
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

/**
 * Petal Search Widget #2:
 * Material 3 Expressive Search Bar & Action Island
 * - Left: Dedicated nested pill search field inside the container
 * - Right: Expressive squircle action island buttons (AI, Incognito, Visual Camera)
 */
class PetalSearchPetal2Widget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val scheme = getWidgetColorScheme(context)
            GlanceTheme(colors = ColorProviders(light = scheme, dark = scheme)) {
                Petal2Content(scheme = scheme)
            }
        }
    }
}

class PetalSearchPetal2WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalSearchPetal2Widget()
}

@Composable
private fun Petal2Content(scheme: androidx.compose.material3.ColorScheme) {
    val context = LocalContext.current
    val searchAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH))
    val aiAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_AI_SEARCH))
    val incognitoAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO))
    val snapCameraAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SNAP_CAMERA))

    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val isLiquidGlass = sp.getBoolean("sp_liquid_glass_enabled", false)

    val glassCardBitmap = remember(scheme, isLiquidGlass) {
        if (isLiquidGlass) {
            createLiquidGlassCardBitmap(widthDp = 280, heightDp = 56, cornerDp = 28, scheme = scheme, context = context)
        } else null
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .then(
                if (glassCardBitmap != null) GlanceModifier else GlanceModifier.background(surfaceContainerLowProvider(scheme))
            ),
        contentAlignment = Alignment.Center
    ) {
        if (glassCardBitmap != null) {
            Image(
                provider = ImageProvider(glassCardBitmap),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize()
            )
        }
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(6.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Left: Nested Search Input Pill
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .defaultWeight()
                    .cornerRadius(22.dp)
                    .background(surfaceContainerHighProvider(scheme))
                    .clickable(searchAction)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.widget_ic_search),
                        contentDescription = context.getString(R.string.ui_search),
                        modifier = GlanceModifier.size(20.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
                    )
                    Spacer(modifier = GlanceModifier.width(10.dp))
                    Text(
                        text = context.getString(R.string.ui_search),
                        maxLines = 1,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // AI Action Squircle Button - primaryContainer
            SquircleGlanceActionButton(
                iconRes = R.drawable.widget_ic_auto_awesome,
                contentDescription = context.getString(R.string.ui_ai_assistant),
                containerColor = GlanceTheme.colors.primaryContainer,
                contentColor = GlanceTheme.colors.onPrimaryContainer,
                action = aiAction
            )

            Spacer(modifier = GlanceModifier.width(6.dp))

            // Incognito Action Squircle Button - secondaryContainer
            SquircleGlanceActionButton(
                iconRes = R.drawable.widget_ic_incognito,
                contentDescription = context.getString(R.string.ui_incognito_mode),
                containerColor = GlanceTheme.colors.secondaryContainer,
                contentColor = GlanceTheme.colors.onSecondaryContainer,
                action = incognitoAction
            )

            Spacer(modifier = GlanceModifier.width(6.dp))

            // Camera / Lens Action Squircle Button - tertiaryContainer
            SquircleGlanceActionButton(
                iconRes = R.drawable.widget_ic_lens_camera,
                contentDescription = context.getString(R.string.ui_visual_camera_scanner),
                containerColor = GlanceTheme.colors.tertiaryContainer,
                contentColor = GlanceTheme.colors.onTertiaryContainer,
                action = snapCameraAction
            )
        }
    }
}

@Composable
private fun SquircleGlanceActionButton(
    iconRes: Int,
    contentDescription: String,
    containerColor: ColorProvider,
    contentColor: ColorProvider,
    action: androidx.glance.action.Action
) {
    Box(
        modifier = GlanceModifier
            .size(44.dp)
            .cornerRadius(18.dp)
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

/**
 * Petal Search Widget #3:
 * Expressive Scalloped Brand Pill Bar
 * - Anchored left: 10-point rounded scallop monogram badge holding "P" in primaryContainer
 * - Open center: Fluid search prompt in onSurfaceVariant
 * - Right: Expressive elevated AI sparkle badge
 */
class PetalSearchPetal3Widget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val scheme = getWidgetColorScheme(context)
            GlanceTheme(colors = ColorProviders(light = scheme, dark = scheme)) {
                Petal3Content(scheme = scheme)
            }
        }
    }
}

class PetalSearchPetal3WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalSearchPetal3Widget()
}

@Composable
private fun Petal3Content(scheme: androidx.compose.material3.ColorScheme) {
    val context = LocalContext.current
    val searchAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH))
    val aiAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_AI_SEARCH))

    val badgeBgInt = GlanceTheme.colors.primaryContainer.getColor(context).toArgb()
    val badgeFgInt = GlanceTheme.colors.onPrimaryContainer.getColor(context).toArgb()
    val badgeBitmap = remember(badgeBgInt, badgeFgInt) {
        createMonogramBadgeBitmap(
            isScallop = true,
            sizeDp = 44,
            bgColor = badgeBgInt,
            textColor = badgeFgInt,
            text = "P",
            context = context
        )
    }

    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val isLiquidGlass = sp.getBoolean("sp_liquid_glass_enabled", false)

    val glassPillBitmap = remember(scheme, isLiquidGlass) {
        if (isLiquidGlass) {
            createLiquidGlassCardBitmap(widthDp = 280, heightDp = 56, cornerDp = 28, scheme = scheme, context = context)
        } else null
    }

    // Material 3 Expressive Pill Bar (height 56dp, corner radius 28dp)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .then(
                if (glassPillBitmap != null) GlanceModifier else GlanceModifier.background(surfaceContainerHighProvider(scheme))
            ),
        contentAlignment = Alignment.Center
    ) {
        if (glassPillBitmap != null) {
            Image(
                provider = ImageProvider(glassPillBitmap),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize()
            )
        }
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Anchored Far Left: Expressive Scallop Brand Badge holding "P"
            Box(
                modifier = GlanceModifier
                    .size(44.dp)
                    .clickable(searchAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(badgeBitmap),
                    contentDescription = context.getString(R.string.ui_petal_home),
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
                    text = context.getString(R.string.ui_search_or_type_url_2),
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }

            // Right: Elevated AI Sparkle Shortcut Button
            Box(
                modifier = GlanceModifier
                    .size(40.dp)
                    .cornerRadius(20.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                .clickable(aiAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_ic_sparkle),
                    contentDescription = context.getString(R.string.ui_ask_petal_ai),
                    modifier = GlanceModifier.size(22.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
                )
            }
        }
    }
}
