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
object GithubRepoMetadataManager {

    private const val TAG = "GithubRepoMetadata";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class RepoMetadata(
        public val fullName;
        public val stargazersCount;
        public val openIssuesCount;
        public val defaultBranch;

        public RepoMetadata(val fullName, val stargazersCount, val openIssuesCount, val defaultBranch) {
            this.fullName = fullName != null ? fullName : "";
            this.stargazersCount = stargazersCount;
            this.openIssuesCount = openIssuesCount;
            this.defaultBranch = defaultBranch != null ? defaultBranch : "main";
        }
    }

    fun interface RepoMetadataCallback {
        void onMetadataFetched(RepoMetadata metadata);
    }

    /**
     * Fetches GitHub repository metadata for a user/repo path.
     * Endpoint: https://api.github.com/repos/{owner}/{repo}
     */
    @JvmStatic
    fun fetchRepoInfo(owner: String?, repo: String?, callback: RepoMetadataCallback?) {
        if (owner == null || repo == null || owner.isEmpty() || repo.isEmpty()) {
            if (callback != null) callback.onMetadataFetched(null);
            return;
        }

        executor.execute {
            RepoMetadata metadata = null
            var connection: HttpURLConnection? = null
            try {
                URL url = new URL("https://api.github.com/repos/" + owner + "/" + repo);
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
                if (connection != null) connection?.disconnect()
            }

            final RepoMetadata finalMeta = metadata;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onMetadataFetched(finalMeta)
                );
            }
        }
    }
}
