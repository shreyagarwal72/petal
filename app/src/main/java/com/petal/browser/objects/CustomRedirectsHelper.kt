package com.petal.browser.objects

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.petal.browser.activity.BrowserActivity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.ArrayList

object CustomRedirectsHelper {
    const val CUSTOM_REDIRECTS_KEY = "customRedirects"

    @JvmStatic
    @Throws(JSONException::class)
    fun getRedirects(preferences: SharedPreferences): ArrayList<CustomRedirect> {
        val redirects = ArrayList<CustomRedirect>()
        var redirectsPref = preferences.getString(CUSTOM_REDIRECTS_KEY, "[]") ?: "[]"

        if (!preferences.getBoolean("youtube_redirect_cleaned", false)) {
            preferences.edit().putBoolean("youtube_redirect_cleaned", true).apply()
            try {
                val oldArr = JSONArray(redirectsPref)
                val newArr = JSONArray()
                for (i in 0 until oldArr.length()) {
                    val obj = oldArr.getJSONObject(i)
                    val src = obj.optString("source", "")
                    if (!src.contains("youtube.com")) {
                        newArr.put(obj)
                    }
                }
                redirectsPref = newArr.toString()
                preferences.edit().putString(CUSTOM_REDIRECTS_KEY, redirectsPref).apply()
            } catch (ignored: Exception) {
            }
        }

        val array = JSONArray(redirectsPref)
        for (i in 0 until array.length()) {
            val redirect = array.getJSONObject(i)
            val source = redirect.optString("source", "")
            val target = redirect.optString("target", "")
            if (source.isNotEmpty() && target.isNotEmpty() && !source.contains("youtube.com")) {
                redirects.add(CustomRedirect(source, target))
            }
        }
        redirects.sortBy { it.source }
        return redirects
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun saveRedirects(redirects: ArrayList<CustomRedirect>) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(BrowserActivity.getAppContext())
        val array = JSONArray()
        for (i in 0 until redirects.size) {
            val redirect = redirects[i]
            if (!redirect.source.contains("youtube.com")) {
                val `object` = JSONObject()
                `object`.put("source", redirect.source)
                `object`.put("target", redirect.target)
                array.put(`object`)
            }
        }
        preferences.edit().putString(CUSTOM_REDIRECTS_KEY, array.toString()).apply()
    }
}
