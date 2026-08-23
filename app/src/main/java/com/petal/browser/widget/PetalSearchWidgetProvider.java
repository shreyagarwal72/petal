package com.petal.browser.widget;

/**
 * Action constants for Petal's home screen search widget.
 *
 * The widget itself is now implemented with Jetpack Glance (Material 3 Expressive
 * components) — see {@link com.petal.browser.widget.glance.PetalSearchGlanceWidget} and
 * {@link com.petal.browser.widget.glance.PetalSearchGlanceWidgetReceiver}, which is what's
 * registered as the actual AppWidgetProvider in AndroidManifest.xml.
 *
 * This class survives only as the single shared source of truth for the widget's
 * action strings and the {@link #updateAllWidgets(android.content.Context)} entry
 * point, since both are referenced from Java call sites
 * ({@link com.petal.browser.activity.BrowserActivity#dispatchIntent} and the palette
 * pickers in PetalSettingsScreen.kt) that shouldn't need to know it's Glance under the
 * hood.
 */
public final class PetalSearchWidgetProvider {

    private PetalSearchWidgetProvider() {
    }

    public static final String ACTION_OPEN_SEARCH = "com.petal.browser.action.OPEN_SEARCH";
    public static final String ACTION_OPEN_VOICE = "com.petal.browser.action.OPEN_VOICE";
    public static final String ACTION_OPEN_AI_SEARCH = "com.petal.browser.action.OPEN_AI_SEARCH";

    /** Refreshes every placed instance of the widget, e.g. after a theme/palette change. */
    public static void updateAllWidgets(android.content.Context context) {
        com.petal.browser.widget.glance.PetalSearchGlanceWidgetUpdater.refresh(context);
    }
}
