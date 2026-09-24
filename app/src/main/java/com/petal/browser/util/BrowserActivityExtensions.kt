package com.petal.browser.util

import android.content.Context
import android.view.View
import androidx.annotation.StringRes
import com.petal.browser.view.PetalToast

/**
 * Idiomatic Kotlin Extension Functions for Context, View, and Activity operations.
 */

fun Context.showToast(message: String) {
    PetalToast.show(this, message)
}

fun Context.showToast(@StringRes resId: Int) {
    PetalToast.show(this, resId)
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
