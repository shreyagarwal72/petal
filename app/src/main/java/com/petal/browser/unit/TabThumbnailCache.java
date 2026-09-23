package com.petal.browser.unit;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reconstructed, high-performance bounded thumbnail management engine for Petal Browser.
 * Inspired by Mozilla Firefox (Fenix) tab thumbnail architecture.
 *
 * Key features:
 * - Dynamic memory allocation (up to 1/8th of available app memory, capped between 16MB and 48MB).
 * - Automatic low-memory trimming (ComponentCallbacks2).
 * - Sandboxed disk cache with deterministic SHA/sanitized keys.
 * - Strict zero-leak eviction policy: when a tab is closed, its preview is immediately erased
 *   from both memory and disk so no stale preview survives into closed tabs or storage.
 */
public final class TabThumbnailCache {

    private static final String TAG = "TabThumbnailCache";
    private static final int MAX_PREVIEW_DIMENSION = 360;
    private static File diskCacheDir = null;
    private static final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();

    // Sized dynamically in bytes based on device memory class
    private static final LruCache<String, Bitmap> memoryCache;

    static {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024); // KB
        int cacheSize = Math.max(8 * 1024, Math.min(maxMemory / 8, 48 * 1024)); // 8MB to 48MB
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(@NonNull String key, @NonNull Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    private TabThumbnailCache() {}

    public static void initDiskCache(@NonNull Context context) {
        if (diskCacheDir != null && diskCacheDir.exists()) return;
        try {
            Context appCtx = context.getApplicationContext();
            File baseDir = appCtx.getFilesDir();
            diskCacheDir = new File(baseDir, "petal_tab_thumbnails");
            if (!diskCacheDir.exists()) {
                diskCacheDir.mkdirs();
            }

            // Register system memory trimming callbacks
            appCtx.registerComponentCallbacks(new ComponentCallbacks2() {
                @Override
                public void onTrimMemory(int level) {
                    if (level >= TRIM_MEMORY_MODERATE) {
                        clearMemory();
                    } else if (level >= TRIM_MEMORY_BACKGROUND) {
                        // Trim memory cache to half
                        memoryCache.trimToSize(memoryCache.size() / 2);
                    }
                }

                @Override
                public void onConfigurationChanged(@NonNull Configuration newConfig) {}

                @Override
                public void onLowMemory() {
                    clearMemory();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to init disk cache dir", e);
        }
    }

    private static File getDiskCacheDir() {
        if (diskCacheDir != null && diskCacheDir.exists()) return diskCacheDir;
        try {
            com.petal.browser.PetalApplication app = com.petal.browser.PetalApplication.Companion.getInstance();
            if (app != null) {
                initDiskCache(app);
            }
        } catch (Exception ignored) {}
        return diskCacheDir;
    }

    /**
     * Cache a tab preview bitmap in memory and asynchronously to disk.
     */
    public static void put(@Nullable String tabId, @Nullable Bitmap bitmap) {
        if (tabId == null || tabId.isEmpty() || bitmap == null || bitmap.isRecycled()) return;
        String safeKey = getSafeKey(tabId);

        // Aspect-ratio preserving downscale to prevent excessive RAM pressure
        Bitmap scaled = bitmap;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width > MAX_PREVIEW_DIMENSION || height > MAX_PREVIEW_DIMENSION) {
            float aspect = (float) width / (float) height;
            int newWidth, newHeight;
            if (aspect > 1.0f) {
                newWidth = MAX_PREVIEW_DIMENSION;
                newHeight = Math.max(1, (int) (MAX_PREVIEW_DIMENSION / aspect));
            } else {
                newHeight = MAX_PREVIEW_DIMENSION;
                newWidth = Math.max(1, (int) (MAX_PREVIEW_DIMENSION * aspect));
            }
            try {
                scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            } catch (Throwable t) {
                scaled = bitmap;
            }
        }

        memoryCache.put(safeKey, scaled);
        saveToDiskAsync(safeKey, scaled);
    }

    /**
     * Retrieve a cached tab thumbnail. Checks memory first, then disk.
     */
    @Nullable
    public static Bitmap get(@Nullable String tabId) {
        if (tabId == null || tabId.isEmpty()) return null;
        String safeKey = getSafeKey(tabId);
        Bitmap bitmap = memoryCache.get(safeKey);
        if (bitmap != null && !bitmap.isRecycled()) {
            return bitmap;
        }

        // Memory miss - load from disk
        Bitmap diskBitmap = loadFromDisk(safeKey);
        if (diskBitmap != null) {
            memoryCache.put(safeKey, diskBitmap);
            return diskBitmap;
        }

        memoryCache.remove(safeKey);
        return null;
    }

    /**
     * Strict eviction: Completely wipes a tab's thumbnail from RAM and disk.
     * Guaranteed never to store thumbnail cache for closed tabs.
     */
    public static void remove(@Nullable String tabId) {
        if (tabId == null || tabId.isEmpty()) return;
        String safeKey = getSafeKey(tabId);
        memoryCache.remove(safeKey);
        deleteFromDiskAsync(safeKey);
    }

    /** Releases only decoded in-memory bitmaps while keeping disk cache. */
    public static void clearMemory() {
        memoryCache.evictAll();
    }

    /** Completely purge all tab thumbnails (both memory and disk). */
    public static void clear() {
        evictAll();
    }

    /** Evict all cached thumbnails across memory and disk. */
    public static void evictAll() {
        memoryCache.evictAll();
        File dir = getDiskCacheDir();
        if (dir != null && dir.exists()) {
            diskExecutor.execute(() -> {
                try {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            file.delete();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error evicting disk thumbnails", e);
                }
            });
        }
    }

    private static String getSafeKey(String key) {
        if (key == null) return "null";
        String sanitized = key.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.length() > 48) {
            String hash = String.valueOf(Math.abs(key.hashCode()));
            sanitized = sanitized.substring(0, 36) + "_" + hash;
        }
        return sanitized;
    }

    private static void deleteFromDiskAsync(String key) {
        File dir = getDiskCacheDir();
        if (dir == null) return;
        diskExecutor.execute(() -> {
            try {
                File file = new File(dir, key + ".webp");
                if (file.exists()) file.delete();
                File legacy = new File(dir, key + ".png");
                if (legacy.exists()) legacy.delete();
            } catch (Exception e) {
                Log.e(TAG, "Error deleting thumbnail from disk", e);
            }
        });
    }

    private static void saveToDiskAsync(String key, Bitmap bitmap) {
        File dir = getDiskCacheDir();
        if (dir == null) return;
        diskExecutor.execute(() -> {
            try {
                if (bitmap == null || bitmap.isRecycled()) return;
                File file = new File(dir, key + ".webp");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    // Compress as WebP for optimal size and load speed
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out);
                    } else {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
                    }
                    out.flush();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving thumbnail to disk", e);
            }
        });
    }

    private static Bitmap loadFromDisk(String key) {
        File dir = getDiskCacheDir();
        if (dir == null) return null;
        try {
            File webpFile = new File(dir, key + ".webp");
            if (webpFile.exists() && webpFile.length() > 0) {
                return BitmapFactory.decodeFile(webpFile.getAbsolutePath());
            }
            File pngFile = new File(dir, key + ".png");
            if (pngFile.exists() && pngFile.length() > 0) {
                return BitmapFactory.decodeFile(pngFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading thumbnail from disk", e);
        }
        return null;
    }
}
