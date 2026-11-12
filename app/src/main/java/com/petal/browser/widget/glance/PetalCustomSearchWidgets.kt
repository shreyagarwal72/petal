package com.petal.browser.widget.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.ImageProvider as GlanceUriImageProvider
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.MainActivity

/*
 * Petal's three home-screen widgets, in Material 3 Expressive.
 *
 * - Petal #1: search pill with a cookie "P" badge and cookie action buttons; grows a row of cookie shortcut tiles
 *   when resized taller.
 * - Petal #2: a nested search field next to an island of cookie action buttons.
 * - Petal #3: a pill with a large cookie badge, an open search prompt and cookie buttons.
 *
 * Every color, shape, shortcut and corner is user-customizable per widget (see PetalWidgetModel.kt and
 * PetalWidgetSettingsActivity), and every widget lays itself out from planLayout() for its exact size.
 *
 * Why the drawing looks the way it does (all of these were visibility bugs on real devices):
 * - Corners are shape drawables, not Glance's cornerRadius(): that modifier does nothing below Android 12, which
 *   left square, unclipped bars on those phones.
 * - Sizes are exact (SizeMode.Exact) and the layout drops buttons/badge when narrow instead of squeezing the
 *   search hint to zero width.
 * - The nested search field's color is derived from the bar, not read from a surface role, so it can't disappear
 *   when every surface role is the same white ("expressive colors").
 * - Shape bitmaps are small and cached so a widget never exceeds the RemoteViews memory budget.
 */

private fun widgetActionIntent(context: Context, action: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

private fun actionFor(context: Context, action: PetalWidgetAction): Action =
    actionStartActivity(widgetActionIntent(context, action.intentAction ?: PetalWidgetAction.SEARCH.intentAction!!))

private fun tint(color: Int): ColorFilter = ColorFilter.tint(ColorProvider(Color(color)))

private fun provider(color: Int): ColorProvider = ColorProvider(Color(color))

// ───────────────────────────── widgets ─────────────────────────────

abstract class PetalBaseWidget(private val kind: PetalWidgetKind) : GlanceAppWidget() {

    // Exact: the widget re-lays-out for the real size the launcher gives it, on every Android version.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (_: Throwable) {
            AppWidgetManager.INVALID_APPWIDGET_ID
        }
        // Resolved here, outside composition: a bad pref or palette must not take the whole widget down.
        val cfg = PetalWidgetConfigStore.load(context, kind)
        val palette = buildPalette(context, cfg)

        provideContent {
            PetalWidgetRoot(appWidgetId, cfg, palette)
        }
    }
}

class PetalSearchPetal1Widget : PetalBaseWidget(PetalWidgetKind.PETAL1)
class PetalSearchPetal2Widget : PetalBaseWidget(PetalWidgetKind.PETAL2)
class PetalSearchPetal3Widget : PetalBaseWidget(PetalWidgetKind.PETAL3)

/** Shared receiver base: forgets a widget's glass alignment and tiles when it is removed from the home screen. */
abstract class PetalBaseWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { id ->
            try {
                PetalGlassWidgets.purge(context.applicationContext, id)
            } catch (_: Throwable) {
            }
        }
    }
}

class PetalSearchPetal1WidgetReceiver : PetalBaseWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalSearchPetal1Widget()
}

class PetalSearchPetal2WidgetReceiver : PetalBaseWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalSearchPetal2Widget()
}

class PetalSearchPetal3WidgetReceiver : PetalBaseWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetalSearchPetal3Widget()
}

@Composable
private fun PetalWidgetRoot(appWidgetId: Int, cfg: PetalWidgetConfig, pal: PetalWidgetPalette) {
    val size = LocalSize.current
    val plan = planLayout(cfg, size.width.value, size.height.value)
    PetalWidgetCard(appWidgetId, cfg, pal, plan) {
        when (cfg.kind) {
            PetalWidgetKind.PETAL1 -> Petal1Content(cfg, pal, plan)
            PetalWidgetKind.PETAL2 -> Petal2Content(cfg, pal, plan)
            PetalWidgetKind.PETAL3 -> Petal3Content(cfg, pal, plan)
        }
    }
}

// ───────────────────────────── surfaces ─────────────────────────────

/** A tinted shape-drawable surface with [content] stacked on top. Works on every API level. */
@Composable
private fun Surface(
    @androidx.annotation.DrawableRes res: Int,
    color: Int,
    modifier: GlanceModifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        Image(
            provider = ImageProvider(res),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            colorFilter = tint(color)
        )
        content()
    }
}

/**
 * The widget's outer card. Normally a tinted rounded fill plus an optional outline ring. With Liquid Glass on it
 * is the blurred wallpaper slice instead (a FileProvider URI, because raw bitmaps blow the RemoteViews limit),
 * or an opaque fallback until the glass is aligned, because a transparent fallback looks broken.
 */
@Composable
private fun PetalWidgetCard(
    appWidgetId: Int,
    cfg: PetalWidgetConfig,
    pal: PetalWidgetPalette,
    plan: PetalLayoutPlan,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density

    val widthPx = (size.width.value * density).toInt()
    val heightPx = (size.height.value * density).toInt()
    val validId = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
    if (widthPx > 0 && heightPx > 0 && validId) {
        PetalGlassWidgets.noteSize(context, appWidgetId, widthPx, heightPx)
    }

    val isLiquidGlass = PetalGlassWidgets.isLiquidGlassEnabled(context)
    val storedRect = if (isLiquidGlass && validId) PetalGlassWidgets.getRect(context, appWidgetId) else null
    // If the widget was resized after it was aligned, keep the aligned top-left and take the live size so the
    // glass slice is never stretched or squashed.
    val rect = storedRect?.let { r ->
        if (widthPx > 0 && heightPx > 0 &&
            (kotlin.math.abs(r.width() - widthPx) > 2 || kotlin.math.abs(r.height() - heightPx) > 2)
        ) android.graphics.Rect(r.left, r.top, r.left + widthPx, r.top + heightPx) else r
    }
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val sheen = sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f)
    val refraction = sp.getFloat("sp_liquid_glass_refraction", 1.0f).coerceIn(0f, 2f)
    val refractionWidth = sp.getFloat("sp_liquid_glass_refraction_width", 1.0f).coerceIn(0f, 2f)

    // The glass renderer needs a number; "full" is half the widget's shorter side.
    val cornerDp = if (cfg.cornerRadius >= PETAL_RADIUS_FULL) minOf(size.width.value, size.height.value) / 2f else cfg.cornerRadius.toFloat()

    val glassUri = if (isLiquidGlass && rect != null) {
        PetalGlassWidgetRenderer.renderUri(
            context,
            PetalGlassWidgetRenderer.Spec(
                rect = rect,
                blurDp = 24f,
                cornerDp = cornerDp,
                dark = pal.isDark,
                edge = sheen,
                refraction = refraction,
                refractionWidth = refractionWidth
            ),
            appWidgetId
        )
    } else null

    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            glassUri != null -> Image(
                provider = GlanceUriImageProvider(glassUri),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            isLiquidGlass -> Image(
                provider = ImageProvider(PetalWidgetDrawables.fill(cfg.cornerRadius)),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = tint(if (pal.isDark) 0xFF1C1E27.toInt() else 0xFFF0F2FA.toInt())
            )
            else -> Image(
                provider = ImageProvider(PetalWidgetDrawables.fill(cfg.cornerRadius)),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = tint(pal.bar)
            )
        }

        if (cfg.outline > 0 && !isLiquidGlass) {
            Image(
                provider = ImageProvider(PetalWidgetDrawables.ring(cfg.cornerRadius, cfg.outline)),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = tint(pal.ring)
            )
        }

        if (isLiquidGlass && cfg.cornerRadius >= 16) {
            Image(
                provider = ImageProvider(
                    if (cfg.cornerRadius >= 24) R.drawable.widget_glass_sheen_pill else R.drawable.widget_glass_sheen_card
                ),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        content()

        // Also offered when the glass could not be drawn (no wallpaper access yet), so the user can fix it from here.
        if (isLiquidGlass && glassUri == null && validId) {
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(top = 4.dp, end = 8.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Surface(
                    res = PetalWidgetDrawables.fill(12),
                    color = 0xD910121A.toInt(),
                    modifier = GlanceModifier.clickable(actionStartActivity(PetalWidgetGlassActivity.intent(context, appWidgetId)))
                ) {
                    Row(
                        modifier = GlanceModifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.widget_ic_lens_camera),
                            contentDescription = context.getString(R.string.widget_glass_align_badge),
                            modifier = GlanceModifier.size(12.dp),
                            colorFilter = tint(0xFFFFFFFF.toInt())
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = context.getString(R.string.widget_glass_align_badge),
                            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = provider(0xFFFFFFFF.toInt()))
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────── building blocks ─────────────────────────────

@Composable
private fun Hint(cfg: PetalWidgetConfig, color: Int, modifier: GlanceModifier) {
    val context = LocalContext.current
    val text = cfg.hint.trim().ifEmpty { context.getString(cfg.hintRes()) }
    Text(
        text = text,
        maxLines = 1,
        modifier = modifier,
        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = provider(color))
    )
}

/** The cookie "P" badge. The whole [PetalLayoutPlan.badgeSize] box is the touch target. */
@Composable
private fun Badge(cfg: PetalWidgetConfig, pal: PetalWidgetPalette, plan: PetalLayoutPlan, action: Action) {
    val context = LocalContext.current
    val glass = PetalGlassWidgets.isLiquidGlassEnabled(context)
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val bmp = PetalWidgetShapes.bitmap(
        context = context,
        shape = cfg.badgeShape,
        sizeDp = plan.badgeSize,
        color = pal.badge.bg,
        glyph = cfg.badgeText.ifBlank { "P" },
        glyphColor = pal.badge.fg,
        rim = if (glass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0f
    )
    Box(
        modifier = GlanceModifier.size(plan.badgeSize.dp).clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(bmp),
            contentDescription = context.getString(R.string.ui_petal),
            modifier = GlanceModifier.size(plan.badgeSize.dp)
        )
    }
}

/** A cookie-shaped icon button; the touch target is [PetalLayoutPlan.buttonTouch], at least 36dp. */
@Composable
private fun CookieButton(
    cfg: PetalWidgetConfig,
    slot: PetalSlot,
    tone: PetalTone,
    plan: PetalLayoutPlan
) {
    val context = LocalContext.current
    val label = context.getString(slot.action.labelRes)
    val bmp = PetalWidgetShapes.bitmap(context, cfg.buttonShape, plan.buttonSize, tone.bg, rotationDeg = (slot.index * 15).toFloat())
    Box(
        modifier = GlanceModifier.size(plan.buttonTouch.dp).clickable(actionFor(context, slot.action)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(bmp),
            contentDescription = null,
            modifier = GlanceModifier.size(plan.buttonSize.dp)
        )
        Image(
            provider = ImageProvider(slot.action.iconRes),
            contentDescription = label,
            modifier = GlanceModifier.size((plan.buttonSize * 0.56f).dp),
            colorFilter = tint(tone.fg)
        )
    }
}

/** A shortcut tile: a big cookie with the icon, and the label underneath when there's room. */
@Composable
private fun CookieTile(
    cfg: PetalWidgetConfig,
    slot: PetalSlot,
    tone: PetalTone,
    plan: PetalLayoutPlan,
    labelColor: Int,
    modifier: GlanceModifier
) {
    val context = LocalContext.current
    val label = context.getString(slot.action.labelRes)
    val bmp = PetalWidgetShapes.bitmap(context, cfg.tileShape, plan.tileCookie, tone.bg, rotationDeg = (slot.index * 20).toFloat())
    Column(
        modifier = modifier.fillMaxHeight().clickable(actionFor(context, slot.action)),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Box(modifier = GlanceModifier.size(plan.tileCookie.dp), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(bmp),
                contentDescription = null,
                modifier = GlanceModifier.size(plan.tileCookie.dp)
            )
            Image(
                provider = ImageProvider(slot.action.iconRes),
                contentDescription = if (plan.showTileLabels) null else label,
                modifier = GlanceModifier.size((plan.tileCookie * 0.46f).dp),
                colorFilter = tint(tone.fg)
            )
        }
        if (plan.showTileLabels) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = provider(labelColor))
            )
        }
    }
}

// ───────────────────────────── Petal #1 ─────────────────────────────

@Composable
private fun Petal1Content(cfg: PetalWidgetConfig, pal: PetalWidgetPalette, plan: PetalLayoutPlan) {
    val context = LocalContext.current
    val searchAction = actionFor(context, PetalWidgetAction.SEARCH)

    if (plan.expanded) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(plan.padding.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Nested search field on top.
            Surface(
                res = PetalWidgetDrawables.fill(plan.fieldRadius),
                color = pal.field,
                modifier = GlanceModifier.fillMaxWidth().height((plan.barHeight - 2 * plan.padding).dp)
            ) {
                SearchRow(cfg, pal, plan, searchAction, hintColor = pal.onFieldMuted)
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                plan.tiles.forEach { slot ->
                    CookieTile(
                        cfg = cfg,
                        slot = slot,
                        tone = pal.tileTones[slot.index.coerceIn(0, 3)],
                        plan = plan,
                        labelColor = pal.onBar,
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
            }
        }
    } else {
        SearchRow(
            cfg, pal, plan, searchAction,
            hintColor = pal.onBarMuted,
            modifier = GlanceModifier.fillMaxSize().padding(plan.padding.dp)
        )
    }
}

/** Badge, hint and the cookie buttons that fit: the content of Petal #1's bar and of its nested field. */
@Composable
private fun SearchRow(
    cfg: PetalWidgetConfig,
    pal: PetalWidgetPalette,
    plan: PetalLayoutPlan,
    searchAction: Action,
    hintColor: Int,
    modifier: GlanceModifier = GlanceModifier.fillMaxSize().padding(horizontal = 2.dp)
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Vertical.CenterVertically) {
        if (plan.showBadge) {
            Badge(cfg, pal, plan, searchAction)
        }
        Box(
            modifier = GlanceModifier
                .fillMaxHeight()
                .defaultWeight()
                .clickable(searchAction)
                .padding(start = if (plan.showBadge) 10.dp else 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Hint(cfg, hintColor, GlanceModifier)
        }
        plan.buttons.forEach { slot ->
            CookieButton(cfg, slot, pal.barTones[slot.index.coerceIn(0, 3)], plan)
        }
    }
}

// ───────────────────────────── Petal #2 ─────────────────────────────

@Composable
private fun Petal2Content(cfg: PetalWidgetConfig, pal: PetalWidgetPalette, plan: PetalLayoutPlan) {
    val context = LocalContext.current
    val searchAction = actionFor(context, PetalWidgetAction.SEARCH)

    Row(
        modifier = GlanceModifier.fillMaxSize().padding(plan.padding.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // Nested search field.
        Surface(
            res = PetalWidgetDrawables.fill(plan.fieldRadius),
            color = pal.field,
            modifier = GlanceModifier.fillMaxHeight().defaultWeight().clickable(searchAction)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_ic_search),
                    contentDescription = context.getString(R.string.ui_search),
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = tint(pal.primary)
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
                Hint(cfg, pal.onFieldMuted, GlanceModifier)
            }
        }

        // Action island.
        plan.buttons.forEach { slot ->
            Spacer(modifier = GlanceModifier.width(4.dp))
            CookieButton(cfg, slot, pal.barTones[slot.index.coerceIn(0, 3)], plan)
        }
    }
}

// ───────────────────────────── Petal #3 ─────────────────────────────

@Composable
private fun Petal3Content(cfg: PetalWidgetConfig, pal: PetalWidgetPalette, plan: PetalLayoutPlan) {
    val context = LocalContext.current
    val searchAction = actionFor(context, PetalWidgetAction.SEARCH)

    Row(
        modifier = GlanceModifier.fillMaxSize().padding(start = plan.padding.dp, end = (plan.padding + 4).dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        if (plan.showBadge) {
            Badge(cfg, pal, plan, searchAction)
        }
        Box(
            modifier = GlanceModifier
                .fillMaxHeight()
                .defaultWeight()
                .clickable(searchAction)
                .padding(start = if (plan.showBadge) 14.dp else 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Hint(cfg, pal.onBarMuted, GlanceModifier)
        }
        plan.buttons.forEach { slot ->
            CookieButton(cfg, slot, pal.barTones[slot.index.coerceIn(0, 3)], plan)
        }
    }
}
