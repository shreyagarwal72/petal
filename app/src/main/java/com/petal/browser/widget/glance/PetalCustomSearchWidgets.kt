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
 * Generates an anti-aliased bitmap with an expressive radial gradient and bold letter "P".
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

    if (isExpanded) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(surfaceContainerLowProvider(scheme))
                .padding(10.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
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
        Petal1ExpressiveSearchRow(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(surfaceContainerHighProvider(scheme))
                .padding(horizontal = 6.dp),
            badgeBitmap = pBadgeBitmap,
            searchAction = searchAction,
            aiAction = aiAction,
            voiceAction = voiceAction,
            snapCameraAction = snapCameraAction
        )
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

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(surfaceContainerLowProvider(scheme))
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
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

    // Material 3 Expressive Pill Bar (height 56dp, corner radius 28dp)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(surfaceContainerHighProvider(scheme))
            .padding(start = 6.dp, end = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
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
