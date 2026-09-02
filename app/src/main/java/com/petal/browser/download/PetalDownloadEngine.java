/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT/TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.petal.browser.download;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.tonyodev.fetch2.AbstractFetchListener;
import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.EnqueueAction;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchConfiguration;
import com.tonyodev.fetch2.NetworkType;
import com.tonyodev.fetch2.Priority;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2core.Downloader;
import com.tonyodev.fetch2okhttp.OkHttpDownloader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Petal High-Speed Parallel Multi-Threaded Download Engine (MDM)
 * Uses Fetch2 + OkHttpDownloader (PARALLEL mode) for high-speed multi-part segmented downloading,
 * real pause/resume, automatic retry on network reconnects, and byte-stream integrity checks.
 */
public class PetalDownloadEngine {
    private static final String TAG = "PetalDownloadEngine";
    private static PetalDownloadEngine sInstance;

    private final Fetch fetch;

    private PetalDownloadEngine(Context context) {
        Context appContext = context.getApplicationContext();

        // Custom robust OkHttpClient for segmented parallel downloading
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build();

        // Configure parallel multi-part segmentation downloader
        // PARALLEL chunking splits large payloads across concurrent threads for maximum throughput
        FetchConfiguration fetchConfiguration = new FetchConfiguration.Builder(appContext)
                .setDownloadConcurrentLimit(12)
                .setProgressReportingInterval(100L)
                .setAutoRetryMaxAttempts(10)
                .enableAutoStart(true)
                .enableRetryOnNetworkGain(true)
                .setHttpDownloader(new OkHttpDownloader(okHttpClient, Downloader.FileDownloaderType.PARALLEL))
                .enableLogging(false)
                .build();
        fetch = Fetch.Impl.getInstance(fetchConfiguration);

        fetch.addListener(new AbstractFetchListener() {
            @Override
            public void onCompleted(@NotNull Download download) {
                Log.d(TAG, "Download completed successfully: " + download.getFile());
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
     * Enqueues a high-speed parallel multi-threaded download request.
     */
    public void enqueueDownload(Context context, String url, String fileName, String userAgent, String cookie, Map<String, String> extraHeaders) {
        enqueueDownload(context, url, fileName, userAgent, cookie, extraHeaders, null);
    }

    private final Map<String, Long> recentEnqueues = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Enqueues a parallel download request and reports the Fetch2-assigned download ID back once queued.
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

        String safeFileName = (fileName != null && !fileName.trim().isEmpty()) ? fileName : SafeDownloadValues.fileName(url, null, null);
        File targetFile = new File(downloadsDir, safeFileName);
        if (targetFile.exists()) {
            String name = safeFileName;
            String extension = "";
            int dotIndex = safeFileName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < safeFileName.length() - 1) {
                name = safeFileName.substring(0, dotIndex);
                extension = safeFileName.substring(dotIndex);
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
        request.setEnqueueAction(EnqueueAction.INCREMENT_FILE_NAME);
        request.setAutoRetryMaxAttempts(10);

        String safeUserAgent = SafeDownloadValues.INSTANCE.header(userAgent, 4096);
        if (safeUserAgent != null && !safeUserAgent.isEmpty()) {
            request.addHeader("User-Agent", safeUserAgent);
        }
        String safeCookie = SafeDownloadValues.INSTANCE.header(cookie, 16384);
        if (safeCookie != null && !safeCookie.isEmpty()) {
            request.addHeader("Cookie", safeCookie);
        }
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                String key = entry.getKey();
                // Never inject manual Accept-Encoding when using OkHttp / Fetch2 parallel downloader
                // OkHttp automatically negotiates and decodes gzip/deflate transparently
                if (key.equalsIgnoreCase("Accept-Encoding")) continue;
                String safeKey = SafeDownloadValues.INSTANCE.header(key, 1024);
                String safeVal = SafeDownloadValues.INSTANCE.header(entry.getValue(), 4096);
                if (safeKey != null && safeVal != null) {
                    request.addHeader(safeKey, safeVal);
                }
            }
        }

        final String finalResolvedFileName = targetFile.getName();
        fetch.enqueue(request, updatedRequest -> {
            Log.d(TAG, "Parallel download enqueued successfully with ID: " + updatedRequest.getId() + ", file: " + filePath);
            if (onEnqueued != null) {
                onEnqueued.accept(updatedRequest.getId(), finalResolvedFileName);
            }
        }, error -> {
            Log.e(TAG, "Failed to enqueue parallel download: " + error);
        });
    }
}
