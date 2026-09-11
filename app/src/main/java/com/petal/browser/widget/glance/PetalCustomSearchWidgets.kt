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
    val isAmoled = sp.getBoolean("sp_amoled", false)
    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
    val colorStyle = try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }

    var scheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        paletteById(paletteId).let { if (isDark) it.dark else it.light }
    }

    scheme = scheme.applyStyle(colorStyle)

    if (isDark && isAmoled) {
        scheme = scheme.applyAmoled()
    }
    return scheme
}

private fun widgetActionIntent(context: Context, action: String): Intent =
    Intent(context, BrowserActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

/**
 * Generates an anti-aliased bitmap with a vibrant subtle radial gradient and bold letter "P".
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

    val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = px * 0.56f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
 * Petal Search Widget #1:
 * - At x x 1 (compact): Mirrors Google search widget from screenshot:
 *   Gradient "P" emblem badge on far left, clear search click surface in middle,
 *   and 3 right action icons: AI Search Sparkle, Voice Mic, and Snap Camera Lens.
 * - At x x 2 (expanded tall): Combines top search bar pill with bottom shortcuts grid
 *   (New Tab, Bookmarks, Downloads, Incognito).
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
                Petal1Content(isExpanded = isExpanded)
            }
        }
    }
}

class PetalSearchPetal1WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalSearchPetal1Widget()
}

@Composable
private fun Petal1Content(isExpanded: Boolean) {
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
            sizeDp = 40,
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
                .background(GlanceTheme.colors.surface)
                .padding(10.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Top Search Pill Row
                Petal1GoogleStyleSearchRow(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(48.dp),
                    badgeBitmap = pBadgeBitmap,
                    searchAction = searchAction,
                    aiAction = aiAction,
                    voiceAction = voiceAction,
                    snapCameraAction = snapCameraAction
                )

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Bottom Row: Chrome-Style Expressive Shortcuts Grid
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    WidgetShortcutTile(
                        label = "New Tab",
                        iconRes = R.drawable.icon_tab_plus,
                        bgColor = GlanceTheme.colors.primaryContainer,
                        iconTint = GlanceTheme.colors.onPrimaryContainer,
                        action = newTabAction,
                        modifier = GlanceModifier.defaultWeight()
                    )

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    WidgetShortcutTile(
                        label = "Bookmarks",
                        iconRes = R.drawable.icon_bookmark,
                        bgColor = GlanceTheme.colors.secondaryContainer,
                        iconTint = GlanceTheme.colors.onSecondaryContainer,
                        action = bookmarksAction,
                        modifier = GlanceModifier.defaultWeight()
                    )

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    WidgetShortcutTile(
                        label = "Downloads",
                        iconRes = R.drawable.ic_download,
                        bgColor = GlanceTheme.colors.tertiaryContainer,
                        iconTint = GlanceTheme.colors.onTertiaryContainer,
                        action = downloadsAction,
                        modifier = GlanceModifier.defaultWeight()
                    )

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    WidgetShortcutTile(
                        label = "Private",
                        iconRes = R.drawable.icon_incognito,
                        bgColor = GlanceTheme.colors.surfaceVariant,
                        iconTint = GlanceTheme.colors.onSurfaceVariant,
                        action = incognitoAction,
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
            }
        }
    } else {
        // x x 1 Compact Pill Mode
        Petal1GoogleStyleSearchRow(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.surfaceVariant)
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
private fun Petal1GoogleStyleSearchRow(
    modifier: GlanceModifier,
    badgeBitmap: Bitmap,
    searchAction: androidx.glance.action.Action,
    aiAction: androidx.glance.action.Action,
    voiceAction: androidx.glance.action.Action,
    snapCameraAction: androidx.glance.action.Action
) {
    Row(
        modifier = modifier
            .clickable(searchAction),
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
                contentDescription = "Petal",
                modifier = GlanceModifier.size(36.dp)
            )
        }

        // Middle: Open Search Click Area
        Box(
            modifier = GlanceModifier
                .fillMaxHeight()
                .defaultWeight()
                .clickable(searchAction)
        ) {}

        // Right 1: AI Search Sparkle Icon
        Box(
            modifier = GlanceModifier
                .size(38.dp)
                .cornerRadius(19.dp)
                .clickable(aiAction),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_search_sparkle),
                contentDescription = "Petal AI Search",
                modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
            )
        }

        Spacer(modifier = GlanceModifier.width(2.dp))

        // Right 2: Voice Search Mic Icon
        Box(
            modifier = GlanceModifier
                .size(38.dp)
                .cornerRadius(19.dp)
                .clickable(voiceAction),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_mic),
                contentDescription = "Voice Search",
                modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
            )
        }

        Spacer(modifier = GlanceModifier.width(2.dp))

        // Right 3: Google Lens Camera Scanner Icon (direct auto snap camera handoff)
        Box(
            modifier = GlanceModifier
                .size(38.dp)
                .cornerRadius(19.dp)
                .clickable(snapCameraAction),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_lens_camera_google),
                contentDescription = "Visual Camera Scanner",
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

/**
 * Petal Search Widget #2:
 * Material 3 Expressive Search Bar Widget:
 * - Pill search bar on left with elevated surface
 * - Expressive action island squircle buttons on right: AI Assistant, Incognito, and Lens/Camera
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
    val snapCameraAction = actionStartActivity(widgetActionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SNAP_CAMERA))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.surface)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Flexible-width Pill Search Trigger
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .defaultWeight()
                    .cornerRadius(24.dp)
                    .background(GlanceTheme.colors.surfaceVariant)
                    .clickable(searchAction)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Search",
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // AI Action Squircle Button - primaryContainer
            SquircleGlanceActionButton(
                iconRes = R.drawable.ic_auto_awesome,
                contentDescription = "AI Assistant",
                containerColor = GlanceTheme.colors.primaryContainer,
                contentColor = GlanceTheme.colors.onPrimaryContainer,
                action = aiAction
            )

            Spacer(modifier = GlanceModifier.width(6.dp))

            // Incognito Action Squircle Button - secondaryContainer
            SquircleGlanceActionButton(
                iconRes = R.drawable.icon_incognito,
                contentDescription = "Incognito Mode",
                containerColor = GlanceTheme.colors.secondaryContainer,
                contentColor = GlanceTheme.colors.onSecondaryContainer,
                action = incognitoAction
            )

            Spacer(modifier = GlanceModifier.width(6.dp))

            // Camera / Lens Action Squircle Button - tertiaryContainer (direct snap photo / Lens handoff)
            SquircleGlanceActionButton(
                iconRes = R.drawable.ic_lens_camera_google,
                contentDescription = "Visual Camera Scanner",
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
            .cornerRadius(16.dp)
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
