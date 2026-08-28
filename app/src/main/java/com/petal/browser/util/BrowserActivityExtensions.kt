package com.petal.browser.util

import android.content.Context
import android.view.View
import androidx.annotation.StringRes
import com.petal.browser.view.NinjaToast

/**
 * Idiomatic Kotlin Extension Functions for Context, View, and Activity operations.
 */

fun Context.showToast(message: String) {
    NinjaToast.show(this, message)
}

fun Context.showToast(@StringRes resId: Int) {
    NinjaToast.show(this, resId)
}

fun View.show() {
    this.visibility = View.VISIBLE
}

fun View.hide() {
    this.visibility = View.GONE
}

fun View.invisible() {
    this.visibility = View.INVISIBLE
}

fun View.toggleVisibility(visible: Boolean) {
    this.visibility = if (visible) View.VISIBLE else View.GONE
}
