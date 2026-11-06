package com.petal.browser.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Drop-in replacement for [MaterialAlertDialogBuilder] used by every legacy View-system dialog.
 *
 * It keeps the AlertDialog type (so existing Java call sites compile unchanged) but renders it
 * with the Petal Material 3 Expressive language, using colours that follow the live Compose
 * theme through [com.petal.browser.ui.theme.PetalViewThemeBridge]:
 *  - flat 28dp `surfaceContainerHigh` container, no border, soft scrim
 *  - primary-tinted icon, `onSurface` title, `onSurfaceVariant` message
 *  - pill action buttons: positive = filled primary, others = tonal
 */
class PetalAlertDialogBuilder : MaterialAlertDialogBuilder {

    constructor(context: Context) : super(context)
    constructor(context: Context, themeResId: Int) : super(context, themeResId)

    override fun create(): AlertDialog {
        val dialog = super.create()
        styleWindow(dialog)
        val previous = dialog
        previous.setOnShowListener { styleContent(previous) }
        return dialog
    }

    override fun show(): AlertDialog {
        val dialog = create()
        dialog.show()
        styleContent(dialog)
        return dialog
    }

    companion object {

        private fun dp(context: Context, v: Float) = v * context.resources.displayMetrics.density

        private fun color(view: View, attr: Int, fallback: Int) =
            MaterialColors.getColor(view, attr, fallback)

        @JvmStatic
        fun styleWindow(dialog: AlertDialog) {
            val window = dialog.window ?: return
            val ctx = dialog.context
            val surface = MaterialColors.getColor(
                ctx, com.google.android.material.R.attr.colorSurfaceContainerHigh,
                MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F.toInt())
            )
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(surface)
                cornerRadius = dp(ctx, 28f)
            }
            window.setBackgroundDrawable(bg)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.32f)
        }

        @JvmStatic
        fun styleContent(dialog: AlertDialog) {
            val root = dialog.window?.decorView ?: return
            val primary = color(root, com.google.android.material.R.attr.colorPrimary, 0xFFB81D63.toInt())
            val onPrimary = color(root, com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF.toInt())
            val tonal = color(root, com.google.android.material.R.attr.colorSecondaryContainer, primary)
            val onTonal = color(root, com.google.android.material.R.attr.colorOnSecondaryContainer, onPrimary)
            val onSurface = color(root, com.google.android.material.R.attr.colorOnSurface, 0xFF000000.toInt())
            val onVariant = color(root, com.google.android.material.R.attr.colorOnSurfaceVariant, onSurface)

            dialog.findViewById<ImageView>(android.R.id.icon)?.setColorFilter(primary)
            dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(onSurface)
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(onVariant)

            fun pill(button: Button?, filled: Boolean) {
                if (button == null) return
                button.setTextColor(if (filled) onPrimary else onTonal)
                (button as? MaterialButton)?.let { mb ->
                    mb.cornerRadius = dp(mb.context, 100f).toInt()
                    mb.backgroundTintList = ColorStateList.valueOf(if (filled) primary else tonal)
                    mb.minHeight = dp(mb.context, 48f).toInt()
                    mb.rippleColor = ColorStateList.valueOf((if (filled) onPrimary else onTonal) and 0x1FFFFFFF)
                }
                (button.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                    val m = dp(button.context, 3f).toInt()
                    it.setMargins(m, it.topMargin, m, it.bottomMargin)
                    button.layoutParams = it
                }
            }
            pill(dialog.getButton(AlertDialog.BUTTON_POSITIVE), true)
            pill(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), false)
            pill(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), false)
        }
    }
}
