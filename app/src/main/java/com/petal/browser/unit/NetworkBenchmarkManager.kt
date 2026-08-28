package com.petal.browser.unit;

import android.util.Log;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NetworkBenchmarkManager performs Cloudflare network latency and download speed benchmarks.
 */
object NetworkBenchmarkManager {

    private const val TAG = "NetworkBenchmarkManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class BenchmarkResult(
        public val pingMs;
        public double downloadSpeedMbps;

        public BenchmarkResult(val pingMs, double downloadSpeedMbps) {
            this.pingMs = pingMs;
            this.downloadSpeedMbps = downloadSpeedMbps;
        }
    }

    fun interface BenchmarkCallback {
        void onBenchmarkCompleted(BenchmarkResult result);
    }

    /**
     * Runs latency ping and speed test against Cloudflare endpoints.
     */
    @JvmStatic
    fun runBenchmark(callback: BenchmarkCallback?) {
        executor.execute {
            val ping = -1;
            double mbps = 0.0;
            var connection: HttpURLConnection? = null

            try {
                // 1. Measure Ping latency
                val startTime = System.currentTimeMillis();
                URL traceUrl = new URL("https://1.1.1.1/cdn-cgi/trace");
                connection = (HttpURLConnection) traceUrl.openConnection();
                connection.requestMethod = "GET");
                connection.connectTimeout = 3000);
                connection.readTimeout = 3000);

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = connection.inputStream;
                    byte[] buf = new byte[1024];
                    while (is.read(buf) != -1) {}
                    is.close();
                    ping = System.currentTimeMillis() - startTime;
                }
                connection?.disconnect()

                // 2. Measure Download speed (small payload ~1MB)
                val speedStartTime = System.currentTimeMillis();
                URL downloadUrl = new URL("https://speed.cloudflare.com/__down?bytes=1000000");
                connection = (HttpURLConnection) downloadUrl.openConnection();
                connection.requestMethod = "GET");
                connection.connectTimeout = 4000);
                connection.readTimeout = 4000);

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = connection.inputStream;
                    byte[] buffer = new byte[4096];
                    val totalBytes = 0;
                    val read;
                    while ((read = is.read(buffer)) != -1) {
                        totalBytes += read;
                    }
                    is.close();
                    val durationMs = System.currentTimeMillis() - speedStartTime;
                    if (durationMs > 0) {
                        double seconds = durationMs / 1000.0;
                        double megabits = (totalBytes * 8) / 1000000.0;
                        mbps = megabits / seconds;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Network benchmark failed", e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            final BenchmarkResult result = new BenchmarkResult(ping, Math.round(mbps * 100.0) / 100.0);
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onBenchmarkCompleted(result)
                );
            }
        }
    }
}
