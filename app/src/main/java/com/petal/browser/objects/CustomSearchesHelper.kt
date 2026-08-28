package com.petal.browser.objects

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.petal.browser.activity.BrowserActivity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.ArrayList

object CustomSearchesHelper {
    const val CUSTOM_REDIRECTS_KEY = "customSearches"

    @JvmStatic
    @Throws(JSONException::class)
    fun getRedirects(preferences: SharedPreferences): ArrayList<CustomRedirect> {
        val redirects = ArrayList<CustomRedirect>()
        val redirectsPref = preferences.getString(CUSTOM_REDIRECTS_KEY, "[]") ?: "[]"

        if (preferences.getString("saved_searches_ok", "no") == "no") {
            redirects.add(CustomRedirect("Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?go=Go&search="))
            redirects.add(CustomRedirect("Fairtranslate ", "https://fairtranslate.eu/?source=auto&q="))
            saveRedirects(redirects)
        }

        val array = JSONArray(redirectsPref)
        for (i in 0 until array.length()) {
            val redirect = array.getJSONObject(i)
            val source = redirect.getString("source")
            val target = redirect.getString("target")
            redirects.add(CustomRedirect(source, target))
            redirects.sortBy { it.source }
            preferences.edit().putString("saved_searches_ok", "yes").apply()
        }
        return redirects
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun saveRedirects(redirects: ArrayList<CustomRedirect>) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(BrowserActivity.getAppContext())
        val array = JSONArray()
        for (i in 0 until redirects.size) {
            val redirect = redirects[i]
            val `object` = JSONObject()
            `object`.put("source", redirect.source)
            `object`.put("target", redirect.target)
            array.put(`object`)
        }
        preferences.edit().putString(CUSTOM_REDIRECTS_KEY, array.toString()).apply()
    }
}
