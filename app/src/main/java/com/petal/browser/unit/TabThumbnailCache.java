package com.petal.browser.unit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.Executors;

/**
 * Bounded memory & disk cache for tab preview thumbnails.
 * Keyed by tab identifier (or URL/tab ID). Thumbnails persist across app restarts on disk
 * and are only deleted when explicit removal/tab closure occurs.
 */
public final class TabThumbnailCache {

    private static final String TAG = "TabThumbnailCache";
    private static final int MAX_ENTRIES = 24;
    private static File diskCacheDir = null;

    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(MAX_ENTRIES) {
        @Override
        protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
            if (oldValue != null && oldValue != newValue && !oldValue.isRecycled()) {
                oldValue.recycle();
            }
        }
    };

    private TabThumbnailCache() {}

    public static void initDiskCache(Context context) {
        if (diskCacheDir != null) return;
        try {
            File baseDir = context.getApplicationContext().getFilesDir();
            diskCacheDir = new File(baseDir, "petal_tab_thumbnails");
            if (!diskCacheDir.exists()) {
                diskCacheDir.mkdirs();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to init disk cache dir", e);
        }
    }

    public static void put(@Nullable String tabId, @Nullable Bitmap bitmap) {
        if (tabId == null || tabId.isEmpty() || bitmap == null || bitmap.isRecycled()) return;
        String safeKey = getSafeKey(tabId);
        
        // Proportional resize (contain/fit scale) instead of aggressive center-cropping
        Bitmap resized = bitmap;
        int maxDimension = 640;
        if (bitmap.getWidth() > maxDimension || bitmap.getHeight() > maxDimension) {
            float aspect = (float) bitmap.getWidth() / (float) bitmap.getHeight();
            int newWidth, newHeight;
            if (aspect > 1.0f) {
                newWidth = maxDimension;
                newHeight = Math.max(1, (int) (maxDimension / aspect));
            } else {
                newHeight = maxDimension;
                newWidth = Math.max(1, (int) (maxDimension * aspect));
            }
            try {
                resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            } catch (Exception e) {
                resized = bitmap;
            }
        }

        cache.put(safeKey, resized);
        saveToDiskAsync(safeKey, resized);
    }

    @Nullable
    public static Bitmap get(@Nullable String tabId) {
        if (tabId == null || tabId.isEmpty()) return null;
        String safeKey = getSafeKey(tabId);
        Bitmap bitmap = cache.get(safeKey);
        if (bitmap != null && !bitmap.isRecycled()) {
            return bitmap;
        }

        // Memory miss - try loading from disk cache
        Bitmap diskBitmap = loadFromDisk(safeKey);
        if (diskBitmap != null) {
            cache.put(safeKey, diskBitmap);
            return diskBitmap;
        }

        cache.remove(safeKey);
        return null;
    }

    /** Call when a tab is explicitly closed to clear both memory and disk caches. */
    public static void remove(@Nullable String tabId) {
        if (tabId == null || tabId.isEmpty()) return;
        String safeKey = getSafeKey(tabId);
        cache.remove(safeKey);
        deleteFromDiskAsync(safeKey);
    }

    /** Call when all tabs are closed at once so cached thumbnails don't linger. Alias for evictAll(). */
    public static void clear() {
        evictAll();
    }

    /** Call on incognito session teardown so private-tab thumbnails don't linger in memory/disk. */
    public static void evictAll() {
        cache.evictAll();
        if (diskCacheDir != null && diskCacheDir.exists()) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    File[] files = diskCacheDir.listFiles();
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
        return key.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static void deleteFromDiskAsync(String key) {
        if (diskCacheDir == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File file = new File(diskCacheDir, key + ".png");
                if (file.exists()) {
                    file.delete();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting thumbnail from disk", e);
            }
        });
    }

    private static void saveToDiskAsync(String key, Bitmap bitmap) {
        if (diskCacheDir == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (diskCacheDir == null || bitmap == null || bitmap.isRecycled()) return;
                File file = new File(diskCacheDir, key + ".png");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
                    out.flush();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving thumbnail to disk", e);
            }
        });
    }

    private static Bitmap loadFromDisk(String key) {
        if (diskCacheDir == null) return null;
        try {
            File file = new File(diskCacheDir, key + ".png");
            if (file.exists() && file.length() > 0) {
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading thumbnail from disk", e);
        }
        return null;
    }
}
