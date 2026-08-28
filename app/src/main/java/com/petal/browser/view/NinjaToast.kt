package com.petal.browser.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object NinjaToast {

    @JvmStatic
    fun show(context: Context?, stringResId: Int) {
        if (context == null) return
        show(context, context.getString(stringResId))
    }

    @JvmStatic
    fun show(context: Context?, text: String?) {
        if (context == null || text.isNullOrEmpty()) return

        try {
            val toast = Toast(context)

            // Resolve M3 Theme Colors dynamically
            val surfaceValue = TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHighest, surfaceValue, true)
            if (surfaceValue.data == 0) {
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHigh, surfaceValue, true)
            }
            val backgroundColor = if (surfaceValue.data != 0) surfaceValue.data else Color.parseColor("#2B2D30")

            val onSurfaceValue = TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, onSurfaceValue, true)
            val textColor = if (onSurfaceValue.data != 0) onSurfaceValue.data else Color.WHITE

            // Container layout
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val paddingHorizontal = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, context.resources.displayMetrics).toInt()
            val paddingVertical = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, context.resources.displayMetrics).toInt()
            container.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)

            // M3 Expressive Pill shape background with subtle elevation shadow
            val background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28f, context.resources.displayMetrics)
                setColor(backgroundColor)
            }
            container.background = background
            container.elevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, context.resources.displayMetrics)

            // Text
            val textView = TextView(context).apply {
                this.text = text
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }

            container.addView(textView)

            toast.view = container
            toast.duration = Toast.LENGTH_SHORT
            toast.show()
        } catch (e: Exception) {
            // Fallback to standard system toast if custom layout fails
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
}
