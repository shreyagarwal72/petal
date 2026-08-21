package com.petal.browser.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import com.petal.browser.R;
import com.petal.browser.activity.BrowserActivity;

public class PetalSearchWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_OPEN_SEARCH = "com.petal.browser.action.OPEN_SEARCH";
    public static final String ACTION_OPEN_VOICE = "com.petal.browser.action.OPEN_VOICE";
    public static final String ACTION_OPEN_AI_SEARCH = "com.petal.browser.action.OPEN_AI_SEARCH";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, android.os.Bundle newOptions) {
        updateAppWidget(context, appWidgetManager, appWidgetId);
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_petal_search);

        // Apply theme colors dynamically matching Petal's active theme palette and dark mode
        android.content.SharedPreferences sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String themeConfig = sp.getString("sp_theme_config", "FOLLOW_SYSTEM");
        boolean isDark = false;
        if ("DARK".equals(themeConfig)) {
            isDark = true;
        } else if ("LIGHT".equals(themeConfig)) {
            isDark = false;
        } else {
            int nightModeFlags = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            isDark = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
        boolean isAmoled = isDark && sp.getBoolean("sp_amoled", false);

        // Apply background shape resource without calling setBackgroundColor (which destroys 28dp pill corners)
        int bgResId = isAmoled ? R.drawable.bg_petal_widget_amoled : (isDark ? R.drawable.bg_petal_widget_dark : R.drawable.bg_petal_widget_light);
        views.setInt(R.id.widget_search_bar, "setBackgroundResource", bgResId);

        // Material You / Theme Palette colors for text and icons
        String paletteId = sp.getString("sp_palette_id", "tide");
        com.petal.browser.ui.theme.PetalPalette palette = com.petal.browser.ui.theme.ColorStylesKt.paletteById(paletteId);
        androidx.compose.material3.ColorScheme scheme = isDark ? palette.getDark() : palette.getLight();

        int primaryIconColor = androidx.compose.ui.graphics.ColorKt.toArgb(scheme.getPrimary());
        int aiIconColor = androidx.compose.ui.graphics.ColorKt.toArgb(scheme.getSecondary());
        int micIconColor = androidx.compose.ui.graphics.ColorKt.toArgb(scheme.getTertiary());
        int hintColor = androidx.compose.ui.graphics.ColorKt.toArgb(scheme.getOnSurfaceVariant());

        views.setTextColor(R.id.widget_search_text, hintColor);
        views.setInt(R.id.widget_icon_search, "setColorFilter", primaryIconColor);
        views.setInt(R.id.widget_icon_ai, "setColorFilter", aiIconColor);
        views.setInt(R.id.widget_icon_mic, "setColorFilter", micIconColor);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        // Search Bar Intent (opens main browser search)
        Intent searchIntent = new Intent(context, BrowserActivity.class);
        searchIntent.setAction(ACTION_OPEN_SEARCH);
        searchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent searchPendingIntent = PendingIntent.getActivity(context, 0, searchIntent, flags);
        views.setOnClickPendingIntent(R.id.widget_search_bar, searchPendingIntent);

        // AI Search Intent (opens AI Search sheet directly)
        Intent aiSearchIntent = new Intent(context, BrowserActivity.class);
        aiSearchIntent.setAction(ACTION_OPEN_AI_SEARCH);
        aiSearchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent aiSearchPendingIntent = PendingIntent.getActivity(context, 1, aiSearchIntent, flags);
        views.setOnClickPendingIntent(R.id.widget_icon_ai, aiSearchPendingIntent);

        // Voice Search Intent
        Intent voiceIntent = new Intent(context, BrowserActivity.class);
        voiceIntent.setAction(ACTION_OPEN_VOICE);
        voiceIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent voicePendingIntent = PendingIntent.getActivity(context, 2, voiceIntent, flags);
        views.setOnClickPendingIntent(R.id.widget_icon_mic, voicePendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    public static void updateAllWidgets(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            if (manager != null) {
                android.content.ComponentName componentName = new android.content.ComponentName(context, PetalSearchWidgetProvider.class);
                int[] appWidgetIds = manager.getAppWidgetIds(componentName);
                if (appWidgetIds != null) {
                    for (int id : appWidgetIds) {
                        updateAppWidget(context, manager, id);
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("PetalSearchWidget", "Error updating search widgets", e);
        }
    }
}
