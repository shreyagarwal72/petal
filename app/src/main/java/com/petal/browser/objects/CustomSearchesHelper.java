package com.petal.browser.objects;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

import com.petal.browser.activity.BrowserActivity;

public class CustomSearchesHelper {

    public final static String CUSTOM_REDIRECTS_KEY = "customSearches";

    public static ArrayList<CustomRedirect> getRedirects(SharedPreferences preferences) throws JSONException {
        ArrayList<CustomRedirect> redirects = new ArrayList<>();
        String redirectsPref = preferences.getString(CUSTOM_REDIRECTS_KEY, "[]");

        if (Objects.requireNonNull(preferences.getString("saved_searches_ok", "no")).equals("no")) {
            redirects.add(new CustomRedirect("Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?go=Go&search="));
            redirects.add(new CustomRedirect("Fairtranslate ", "https://fairtranslate.eu/?source=auto&q="));
            saveRedirects(redirects);
        }

        JSONArray array = new JSONArray(redirectsPref);
        for (int i = 0; i < array.length(); i++) {
            JSONObject redirect = array.getJSONObject(i);
            String source = redirect.getString("source");
            String target = redirect.getString("target");
            redirects.add(new CustomRedirect(source, target));
            redirects.sort(Comparator.comparing(CustomRedirect::getSource));
            preferences.edit().putString("saved_searches_ok", "yes").apply();
        }
        return redirects;
    }

    public static void saveRedirects(ArrayList<CustomRedirect> redirects) throws JSONException {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(BrowserActivity.getAppContext());
        JSONArray array = new JSONArray();
        for (int i = 0; i < redirects.size(); i++) {
            CustomRedirect redirect = redirects.get(i);
            JSONObject object = new JSONObject();
            object.put("source", redirect.getSource());
            object.put("target", redirect.getTarget());
            array.put(object);
        }
        preferences.edit().putString(CUSTOM_REDIRECTS_KEY, array.toString()).apply();
    }
}
