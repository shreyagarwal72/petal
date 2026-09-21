package com.petal.browser.view;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.petal.browser.unit.HelperUnit;

/**
 * Petal's single transient-message entry point.
 * Activity-backed messages are rendered as expressive Material 3 snackbars;
 * application-only contexts retain a themed toast fallback.
 */
public class NinjaToast {

    public static void show(Context context, int stringResId) {
        if (context == null) return;
        show(context, context.getString(stringResId));
    }

    public static void show(Context context, String text) {
        show(context, text, Toast.LENGTH_SHORT);
    }

    public static void show(Context context, String text, int duration) {
        if (context == null || text == null || text.isEmpty()) return;

        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            Runnable render = () -> {
                if (activity.isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
                View root = activity.findViewById(android.R.id.content);
                if (root != null && root.isAttachedToWindow()) {
                    HelperUnit.makePetalSnackbar(
                            root,
                            text,
                            duration == Toast.LENGTH_LONG ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT
                    ).show();
                    return;
                }
                showFallback(context, text, duration);
            };
            if (Looper.myLooper() == Looper.getMainLooper()) render.run();
            else new Handler(Looper.getMainLooper()).post(render);
            return;
        }

        showFallback(context, text, duration);
    }

    private static void showFallback(Context context, String text, int duration) {
        try {
            Toast toast = new Toast(context);

            TypedValue surfaceValue = new TypedValue();
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHighest, surfaceValue, true);
            if (surfaceValue.data == 0) {
                context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHigh, surfaceValue, true);
            }
            int backgroundColor = surfaceValue.data != 0 ? surfaceValue.data : Color.parseColor("#2B2D30");

            TypedValue onSurfaceValue = new TypedValue();
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, onSurfaceValue, true);
            int textColor = onSurfaceValue.data != 0 ? onSurfaceValue.data : Color.WHITE;

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(Gravity.CENTER);
            int h = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, context.getResources().getDisplayMetrics());
            int v = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
            container.setPadding(h, v, h, v);

            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics()));
            background.setColor(backgroundColor);
            container.setBackground(background);
            container.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, context.getResources().getDisplayMetrics()));

            TextView textView = new TextView(context);
            textView.setText(text);
            textView.setTextColor(textColor);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            textView.setGravity(Gravity.CENTER);
            container.addView(textView);

            toast.setView(container);
            toast.setDuration(duration);
            toast.show();
        } catch (Exception e) {
            Toast.makeText(context.getApplicationContext(), text, duration).show();
        }
    }
}
