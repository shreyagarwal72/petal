package com.petal.browser.objects

class CustomRedirect(
    @JvmField val source: String,
    @JvmField val target: String
) {
    fun getSource(): String = source
    fun getTarget(): String = target
}
