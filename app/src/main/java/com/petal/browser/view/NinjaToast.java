package com.petal.browser.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class NinjaToast {

    public static void show(Context context, int stringResId) {
        if (context == null) return;
        show(context, context.getString(stringResId));
    }

    public static void show(Context context, String text) {
        if (context == null || text == null || text.isEmpty()) return;

        try {
            Toast toast = new Toast(context);

            // Resolve M3 Theme Colors dynamically
            TypedValue surfaceValue = new TypedValue();
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHighest, surfaceValue, true);
            if (surfaceValue.data == 0) {
                context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHigh, surfaceValue, true);
            }
            int backgroundColor = surfaceValue.data != 0 ? surfaceValue.data : Color.parseColor("#2B2D30");

            TypedValue onSurfaceValue = new TypedValue();
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, onSurfaceValue, true);
            int textColor = onSurfaceValue.data != 0 ? onSurfaceValue.data : Color.WHITE;

            // Container layout
            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(Gravity.CENTER);

            int paddingHorizontal = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, context.getResources().getDisplayMetrics());
            int paddingVertical = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
            container.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);

            // M3 Expressive Pill shape background with subtle elevation shadow
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, context.getResources().getDisplayMetrics()));
            background.setColor(backgroundColor);
            container.setBackground(background);
            container.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, context.getResources().getDisplayMetrics()));

            // Text
            TextView textView = new TextView(context);
            textView.setText(text);
            textView.setTextColor(textColor);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            textView.setGravity(Gravity.CENTER);

            container.addView(textView);

            toast.setView(container);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.show();
        } catch (Exception e) {
            Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        }
    }
}