package com.petal.browser.unit;

import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GithubUserProfileManager fetches GitHub user bio, avatar, and public repo stats.
 */
public class GithubUserProfileManager {

    private static final String TAG = "GithubUserProfile";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class GithubProfile {
        public String username;
        public String name;
        public String avatarUrl;
        public String bio;
        public int publicRepos;

        public GithubProfile(String username, String name, String avatarUrl, String bio, int publicRepos) {
            this.username = username != null ? username : "";
            this.name = name != null ? name : "";
            this.avatarUrl = avatarUrl != null ? avatarUrl : "";
            this.bio = bio != null ? bio : "";
            this.publicRepos = publicRepos;
        }
    }

    public interface GithubProfileCallback {
        void onProfileFetched(GithubProfile profile);
    }

    /**
     * Fetches GitHub user profile metadata.
     * Endpoint: https://api.github.com/users/{username}
     */
    public static void fetchProfile(final String username, final GithubProfileCallback callback) {
        if (username == null || username.trim().isEmpty()) {
            if (callback != null) callback.onProfileFetched(null);
            return;
        }

        executor.execute(() -> {
            GithubProfile profile = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://api.github.com/users/" + username.trim());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3500);
                connection.setReadTimeout(3500);
                connection.setRequestProperty("User-Agent", "petal-browser/1.0");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(builder.toString());
                    profile = new GithubProfile(
                        json.optString("login", username.trim()),
                        json.optString("name", ""),
                        json.optString("avatar_url", ""),
                        json.optString("bio", ""),
                        json.optInt("public_repos", 0)
                    );
                }
            } catch (Exception e) {
                Log.w(TAG, "GitHub user profile fetch failed for: " + username, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final GithubProfile finalProf = profile;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onProfileFetched(finalProf)
                );
            }
        });
    }
}
