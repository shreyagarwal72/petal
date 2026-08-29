package com.petal.browser.objects;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;

import com.petal.browser.activity.BrowserActivity;

public class CustomRedirectsHelper {

    public final static String CUSTOM_REDIRECTS_KEY = "customRedirects";

    public static ArrayList<CustomRedirect> getRedirects(SharedPreferences preferences) throws JSONException {
        ArrayList<CustomRedirect> redirects = new ArrayList<>();
        String redirectsPref = preferences.getString(CUSTOM_REDIRECTS_KEY, "[]");

        if (!preferences.getBoolean("youtube_redirect_cleaned", false)) {
            preferences.edit().putBoolean("youtube_redirect_cleaned", true).apply();
            try {
                JSONArray oldArr = new JSONArray(redirectsPref);
                JSONArray newArr = new JSONArray();
                for (int i = 0; i < oldArr.length(); i++) {
                    JSONObject obj = oldArr.getJSONObject(i);
                    String src = obj.optString("source", "");
                    if (!src.contains("youtube.com")) {
                        newArr.put(obj);
                    }
                }
                redirectsPref = newArr.toString();
                preferences.edit().putString(CUSTOM_REDIRECTS_KEY, redirectsPref).apply();
            } catch (Exception ignored) {}
        }

        JSONArray array = new JSONArray(redirectsPref);
        for (int i = 0; i < array.length(); i++) {
            JSONObject redirect = array.getJSONObject(i);
            String source = redirect.optString("source", "");
            String target = redirect.optString("target", "");
            if (!source.isEmpty() && !target.isEmpty() && !source.contains("youtube.com")) {
                redirects.add(new CustomRedirect(source, target));
            }
        }
        redirects.sort(Comparator.comparing(CustomRedirect::getSource));
        return redirects;
    }

    public static void saveRedirects(ArrayList<CustomRedirect> redirects) throws JSONException {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(BrowserActivity.getAppContext());
        JSONArray array = new JSONArray();
        for (int i = 0; i < redirects.size(); i++) {
            CustomRedirect redirect = redirects.get(i);
            if (!redirect.getSource().contains("youtube.com")) {
                JSONObject object = new JSONObject();
                object.put("source", redirect.getSource());
                object.put("target", redirect.getTarget());
                array.put(object);
            }
        }
        preferences.edit().putString(CUSTOM_REDIRECTS_KEY, array.toString()).apply();
    }
}
