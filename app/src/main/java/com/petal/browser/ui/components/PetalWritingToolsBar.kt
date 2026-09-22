package com.petal.browser.ui.components

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors

/** Compact, keyboard-independent editing accessory shown above the IME. */
object PetalWritingToolsBar {
    private const val PREF = "sp_writing_tools_bar"

    @JvmStatic
    fun attach(activity: Activity, host: ViewGroup, preferences: SharedPreferences) {
        val bar = LinearLayout(activity).apply {
            tag = "petal_writing_tools"
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 4, 8, 4)
            setBackgroundColor(MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.WHITE))
            elevation = 8f
            visibility = View.GONE
        }
        val actions = listOf(
            "Undo" to { edit(activity)?.onTextContextMenuItem(android.R.id.undo) },
            "Redo" to { edit(activity)?.onTextContextMenuItem(android.R.id.redo) },
            "Select all" to { edit(activity)?.selectAll() },
            "Cut" to { edit(activity)?.onTextContextMenuItem(android.R.id.cut) },
            "Copy" to { edit(activity)?.onTextContextMenuItem(android.R.id.copy) },
            "Paste" to { edit(activity)?.onTextContextMenuItem(android.R.id.paste) },
            "Clear" to { edit(activity)?.setText("") },
            "Hide" to { activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(bar.windowToken, 0) }
        )
        actions.forEach { (label, action) ->
            bar.addView(MaterialButton(activity).apply {
                text = label
                isAllCaps = false
                minWidth = 0
                insetTop = 0; insetBottom = 0
                setPadding(12, 0, 12, 0)
                setOnClickListener { action() }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 44)
            })
        }
        val barLayoutParams: ViewGroup.LayoutParams = if (host is RelativeLayout) {
            RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }
        } else {
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM }
        }
        host.addView(bar, barLayoutParams)
        fun update(insets: WindowInsetsCompat) {
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val visible = insets.isVisible(WindowInsetsCompat.Type.ime()) && preferences.getBoolean(PREF, true)
            bar.visibility = if (visible) View.VISIBLE else View.GONE
            (bar.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.bottomMargin = ime.bottom
                bar.layoutParams = lp
            }
        }
        update(ViewCompat.getRootWindowInsets(host) ?: return)
        preferences.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == PREF) ViewCompat.getRootWindowInsets(host)?.let(::update)
        }
    }

    @JvmStatic
    fun update(host: ViewGroup, insets: WindowInsetsCompat) {
        for (index in 0 until host.childCount) {
            val child = host.getChildAt(index)
            if (child is LinearLayout && child.tag == "petal_writing_tools") {
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                val enabled = PreferenceManager.getDefaultSharedPreferences(host.context).getBoolean(PREF, true)
                child.visibility = if (enabled && insets.isVisible(WindowInsetsCompat.Type.ime())) View.VISIBLE else View.GONE
                (child.layoutParams as? ViewGroup.MarginLayoutParams)?.let { it.bottomMargin = ime.bottom; child.layoutParams = it }
            }
        }
    }
    private fun edit(activity: Activity): android.widget.EditText? = activity.currentFocus as? android.widget.EditText
}
