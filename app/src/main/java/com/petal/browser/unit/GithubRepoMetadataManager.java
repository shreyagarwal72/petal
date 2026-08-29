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
 * GithubRepoMetadataManager fetches repository stars, open issues, and latest release tags for GitHub pages.
 */
public class GithubRepoMetadataManager {

    private static final String TAG = "GithubRepoMetadata";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class RepoMetadata {
        public String fullName;
        public int stargazersCount;
        public int openIssuesCount;
        public String defaultBranch;

        public RepoMetadata(String fullName, int stargazersCount, int openIssuesCount, String defaultBranch) {
            this.fullName = fullName != null ? fullName : "";
            this.stargazersCount = stargazersCount;
            this.openIssuesCount = openIssuesCount;
            this.defaultBranch = defaultBranch != null ? defaultBranch : "main";
        }
    }

    public interface RepoMetadataCallback {
        void onMetadataFetched(RepoMetadata metadata);
    }

    /**
     * Fetches GitHub repository metadata for a user/repo path.
     * Endpoint: https://api.github.com/repos/{owner}/{repo}
     */
    public static void fetchRepoInfo(final String owner, final String repo, final RepoMetadataCallback callback) {
        if (owner == null || repo == null || owner.isEmpty() || repo.isEmpty()) {
            if (callback != null) callback.onMetadataFetched(null);
            return;
        }

        executor.execute(() -> {
            RepoMetadata metadata = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://api.github.com/repos/" + owner + "/" + repo);
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
                    metadata = new RepoMetadata(
                        json.optString("full_name", owner + "/" + repo),
                        json.optInt("stargazers_count", 0),
                        json.optInt("open_issues_count", 0),
                        json.optString("default_branch", "main")
                    );
                }
            } catch (Exception e) {
                Log.w(TAG, "GitHub repo metadata fetch failed for: " + owner + "/" + repo, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final RepoMetadata finalMeta = metadata;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onMetadataFetched(finalMeta)
                );
            }
        });
    }
}
