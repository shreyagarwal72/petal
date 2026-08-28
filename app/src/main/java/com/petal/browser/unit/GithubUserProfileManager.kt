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
object GithubUserProfileManager {

    private const val TAG = "GithubUserProfile";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class GithubProfile(
        public val username;
        public val name;
        public val avatarUrl;
        public val bio;
        public val publicRepos;

        public GithubProfile(val username, val name, val avatarUrl, val bio, val publicRepos) {
            this.username = username != null ? username : "";
            this.name = name != null ? name : "";
            this.avatarUrl = avatarUrl != null ? avatarUrl : "";
            this.bio = bio != null ? bio : "";
            this.publicRepos = publicRepos;
        }
    }

    fun interface GithubProfileCallback {
        void onProfileFetched(GithubProfile profile);
    }

    /**
     * Fetches GitHub user profile metadata.
     * Endpoint: https://api.github.com/users/{username}
     */
    @JvmStatic
    fun fetchProfile(username: String?, callback: GithubProfileCallback?) {
        if (username == null || username.trim().isEmpty()) {
            if (callback != null) callback.onProfileFetched(null);
            return;
        }

        executor.execute {
            GithubProfile profile = null
            var connection: HttpURLConnection? = null
            try {
                URL url = new URL("https://api.github.com/users/" + username.trim());
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET");
                connection.connectTimeout = 3500);
                connection.readTimeout = 3500);
                connection.setRequestProperty("User-Agent", "petal-browser/1.0");

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = BufferedReader(InputStreamReader(connection.inputStream));
                    StringBuilder builder = StringBuilder()
                    val line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject json = JSONObject(builder.toString());
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
                if (connection != null) connection?.disconnect()
            }

            final GithubProfile finalProf = profile;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onProfileFetched(finalProf)
                );
            }
        }
    }
}
