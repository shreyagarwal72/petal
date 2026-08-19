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

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_petal_search);

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

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
