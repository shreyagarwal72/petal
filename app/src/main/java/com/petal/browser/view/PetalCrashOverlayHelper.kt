package com.petal.browser.view

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.petal.browser.R

object PetalCrashOverlayHelper {

    fun showCrashErrorState(
        parent: ViewGroup,
        existingView: View?,
        onReload: () -> Unit
    ): View? {
        if (existingView != null) return existingView
        val context = parent.context
        val density = context.resources.displayMetrics.density

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = (32 * density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurface,
                    0xFF121212.toInt()
                )
            )

            val titleView = TextView(context).apply {
                text = context.getString(R.string.tab_crash_error_title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(
                    MaterialColors.getColor(
                        this,
                        com.google.android.material.R.attr.colorOnSurface,
                        0xFFFFFFFF.toInt()
                    )
                )
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, (12 * density).toInt())
            }
            addView(titleView)

            val msgView = TextView(context).apply {
                text = context.getString(R.string.tab_crash_error_message)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(
                    MaterialColors.getColor(
                        this,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        0xFFAAAAAA.toInt()
                    )
                )
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, (24 * density).toInt())
            }
            addView(msgView)

            val reloadBtn = MaterialButton(context).apply {
                text = context.getString(R.string.ui_reload)
                setOnClickListener {
                    dismissCrashErrorState(parent, this@apply)
                    onReload()
                }
            }
            addView(reloadBtn)
        }
        parent.addView(container)
        return container
    }

    fun dismissCrashErrorState(parent: ViewGroup, errorView: View?): View? {
        if (errorView != null) {
            parent.removeView(errorView)
        }
        return null
    }
}
