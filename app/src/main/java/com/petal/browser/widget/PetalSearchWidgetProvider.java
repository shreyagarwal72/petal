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

        // Apply theme colors dynamically
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

        int textColor = isDark ? 0xFFE2E2E6 : 0xFF5F6368;
        int iconColor = isDark ? 0xFFC4C6D0 : 0xFF5F6368;

        views.setTextColor(R.id.widget_search_text, textColor);
        views.setInt(R.id.widget_icon_search, "setColorFilter", iconColor);
        views.setInt(R.id.widget_icon_mic, "setColorFilter", iconColor);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        // Search Bar Intent
        Intent searchIntent = new Intent(context, BrowserActivity.class);
        searchIntent.setAction(ACTION_OPEN_SEARCH);
        searchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent searchPendingIntent = PendingIntent.getActivity(context, 0, searchIntent, flags);
        views.setOnClickPendingIntent(R.id.widget_search_bar, searchPendingIntent);

        // Voice Search Intent
        Intent voiceIntent = new Intent(context, BrowserActivity.class);
        voiceIntent.setAction(ACTION_OPEN_VOICE);
        voiceIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent voicePendingIntent = PendingIntent.getActivity(context, 2, voiceIntent, flags);
        views.setOnClickPendingIntent(R.id.widget_icon_mic, voicePendingIntent);

        // AI Search Intent
        Intent aiIntent = new Intent(context, BrowserActivity.class);
        aiIntent.setAction(ACTION_OPEN_AI_SEARCH);
        aiIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent aiPendingIntent = PendingIntent.getActivity(context, 3, aiIntent, flags);
        views.setOnClickPendingIntent(R.id.widget_icon_ai, aiPendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
