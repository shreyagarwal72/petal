package com.petal.browser.download;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.tonyodev.fetch2.AbstractFetchListener;
import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchConfiguration;
import com.tonyodev.fetch2.NetworkType;
import com.tonyodev.fetch2.Priority;
import com.tonyodev.fetch2.Request;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * Petal Fast Download Engine (MDM - Multi-threaded Download Manager)
 * Uses Fetch2 under the hood for parallel multi-chunk downloading, resume support,
 * and high-speed downloads.
 */
public class PetalDownloadEngine {
    private static final String TAG = "PetalDownloadEngine";
    private static PetalDownloadEngine sInstance;

    private final Fetch fetch;

    private PetalDownloadEngine(Context context) {
        Context appContext = context.getApplicationContext();
        FetchConfiguration fetchConfiguration = new FetchConfiguration.Builder(appContext)
                .setDownloadConcurrentLimit(12)
                .setProgressReportingInterval(100L)
                .enableAutoStart(true)
                .enableRetryOnNetworkGain(true)
                .enableLogging(false)
                .build();
        fetch = Fetch.Impl.getInstance(fetchConfiguration);

        fetch.addListener(new AbstractFetchListener() {
            @Override
            public void onCompleted(@NotNull Download download) {
                Log.d(TAG, "Download completed: " + download.getFile());
            }

            @Override
            public void onError(@NotNull Download download, @NotNull Error error, @Nullable Throwable throwable) {
                Log.e(TAG, "Download error: " + error + ", url=" + download.getUrl(), throwable);
            }

            @Override
            public void onProgress(@NotNull Download download, long etaInMilliSeconds, long downloadedBytesPerSecond) {
                Log.d(TAG, "Download speed: " + downloadedBytesPerSecond + " B/s, progress: " + download.getProgress() + "%");
            }
        });
    }

    public static synchronized PetalDownloadEngine getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PetalDownloadEngine(context);
        }
        return sInstance;
    }

    public Fetch getFetch() {
        return fetch;
    }

    /**
     * Enqueues a high-speed multi-threaded download request.
     */
    public void enqueueDownload(Context context, String url, String fileName, String userAgent, String cookie, Map<String, String> extraHeaders) {
        enqueueDownload(context, url, fileName, userAgent, cookie, extraHeaders, null);
    }

    private final Map<String, Long> recentEnqueues = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Enqueues a download request and reports the Fetch2-assigned download ID back once
     * queued, so callers (e.g. BrowserUnit) can start live-tracking/notifications for the
     * exact download that was created instead of guessing an ID.
     */
    public void enqueueDownload(Context context, String url, String fileName, String userAgent, String cookie, Map<String, String> extraHeaders, java.util.function.BiConsumer<Integer, String> onEnqueued) {
        if (url == null || url.isEmpty()) return;

        long now = System.currentTimeMillis();
        Long lastTime = recentEnqueues.get(url);
        if (lastTime != null && (now - lastTime) < 2000L) {
            Log.d(TAG, "Bypassing duplicate download enqueue for URL: " + url);
            return;
        }
        recentEnqueues.put(url, now);
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }

        File targetFile = new File(downloadsDir, fileName);
        if (targetFile.exists()) {
            String name = fileName;
            String extension = "";
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
                name = fileName.substring(0, dotIndex);
                extension = fileName.substring(dotIndex);
            }
            int counter = 1;
            while (targetFile.exists()) {
                targetFile = new File(downloadsDir, name + "(" + counter + ")" + extension);
                counter++;
            }
        }
        String filePath = targetFile.getAbsolutePath();

        Request request = new Request(url, filePath);
        request.setPriority(Priority.HIGH);
        request.setNetworkType(NetworkType.ALL);
        request.setAutoResumption(true);

        if (userAgent != null && !userAgent.isEmpty()) {
            request.addHeader("User-Agent", userAgent);
        }
        if (cookie != null && !cookie.isEmpty()) {
            request.addHeader("Cookie", cookie);
        }
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }

        final String finalResolvedFileName = targetFile.getName();
        fetch.enqueue(request, updatedRequest -> {
            Log.d(TAG, "Download enqueued successfully with ID: " + updatedRequest.getId() + ", file: " + filePath);
            if (onEnqueued != null) {
                onEnqueued.accept(updatedRequest.getId(), finalResolvedFileName);
            }
        }, error -> {
            Log.e(TAG, "Failed to enqueue download: " + error);
        });
    }
}
