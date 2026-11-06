package com.petal.browser.unit;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Collection;

/**
 * Bounded, asynchronous tab thumbnail storage. Tab IDs are the only cache identity;
 * URLs are deliberately never aliases because multiple tabs can display the same URL.
 * Private thumbnails use a separate RAM-only cache and never read or write disk.
 */
public final class TabThumbnailCache {

    private static final String TAG = "TabThumbnailCache";
    /** Stored width: large enough to stay sharp on a grid card (~170dp wide at 3x density). */
    private static final int MAX_PREVIEW_WIDTH = 480;
    /** Like Firefox/Chrome thumbnails, tall pages are cropped to their top instead of squashed. */
    private static final float MAX_PREVIEW_ASPECT = 1.7f;
    private static final int MAX_PREVIEW_HEIGHT = Math.round(MAX_PREVIEW_WIDTH * MAX_PREVIEW_ASPECT);
    private static final long MAX_DISK_CACHE_BYTES = 64L * 1024L * 1024L;
    /** The disk directory is only re-scanned every N writes instead of after every single write. */
    private static final int TRIM_EVERY_N_WRITES = 12;
    private static final AtomicInteger WRITES_SINCE_TRIM = new AtomicInteger();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    public static final ExecutorService DISK = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "PetalThumbnailDisk");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicLong TOKENS = new AtomicLong();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final ConcurrentHashMap<String, Long> regularVersions = new ConcurrentHashMap<>();
    private static final Set<String> pendingLoads = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<ThumbnailCallback>> loadCallbacks = new ConcurrentHashMap<>();

    private static final LruCache<String, Bitmap> regularMemoryCache;
    private static final LruCache<String, Bitmap> privateMemoryCache;
    private static volatile File diskCacheDir;
    private static volatile boolean initialized;

    static {
        long maxMemoryKb = Math.max(1024L, Runtime.getRuntime().maxMemory() / 1024L);
        int regularKb = (int) Math.max(2048L, Math.min(maxMemoryKb / 8L, 40L * 1024L));
        int privateKb = (int) Math.max(1024L, Math.min(maxMemoryKb / 16L, 8L * 1024L));
        regularMemoryCache = createMemoryCache(regularKb);
        privateMemoryCache = createMemoryCache(privateKb);
    }

    private TabThumbnailCache() {}

    private static LruCache<String, Bitmap> createMemoryCache(int maxKb) {
        return new LruCache<String, Bitmap>(maxKb) {
            @Override
            protected int sizeOf(@NonNull String key, @NonNull Bitmap bitmap) {
                if (bitmap.isRecycled()) return 0;
                return Math.max(1, bitmap.getByteCount() / 1024);
            }
        };
    }

    /** Initializes the cache once and registers memory-pressure callbacks once. */
    public static synchronized void initDiskCache(@NonNull Context context) {
        if (initialized && diskCacheDir != null && diskCacheDir.isDirectory()) return;
        Context appContext = context.getApplicationContext();
        File dir = new File(appContext.getFilesDir(), "petal_tab_thumbnails");
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            Log.e(TAG, "Cannot create thumbnail cache directory: " + dir);
            diskCacheDir = null;
            initialized = false;
            return;
        }
        diskCacheDir = dir;
        initialized = true;
        try {
            appContext.registerComponentCallbacks(new ComponentCallbacks2() {
                @Override public void onTrimMemory(int level) {
                    trimMemory(level);
                }
                @Override public void onConfigurationChanged(@NonNull Configuration newConfig) {}
                @Override public void onLowMemory() { clearMemory(); }
            });
        } catch (Exception e) {
            Log.w(TAG, "Unable to register thumbnail memory callbacks", e);
        }
        final File initializedDir = dir;
        DISK.execute(() -> trimDiskCache(initializedDir));
    }

    /**
     * Tiered trim (Chromium/Fenix style). TRIM_MEMORY_UI_HIDDEN (20) only means the app went to
     * the background: it must never drop thumbnails, otherwise every app switch loses the
     * RAM-only incognito previews and forces a disk reload of every regular one.
     * Private previews have no disk copy, so they are only evicted when the process is about
     * to be killed.
     */
    public static void trimMemory(int level) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            clearMemory();
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            regularMemoryCache.evictAll();
            privateMemoryCache.trimToSize(privateMemoryCache.size() / 2);
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            regularMemoryCache.trimToSize(regularMemoryCache.size() / 2);
        } else if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            regularMemoryCache.trimToSize(regularMemoryCache.size() / 2);
        }
    }

    /**
     * Deletes every stored thumbnail whose tab id is not in [liveTabIds] (closed tabs, crashed
     * sessions, tabs removed through code paths that never evicted).
     * Keeps the directory bounded by the open tabs.
     */
    public static void retainOnly(@Nullable Collection<String> liveTabIds) {
        if (liveTabIds == null) return;
        final HashSet<String> keep = new HashSet<>();
        for (String id : liveTabIds) if (!isBlank(id)) keep.add(diskKey(id) + ".thumb");
        final long generation = GENERATION.get();
        DISK.execute(() -> {
            File dir = getDiskCacheDir();
            if (dir == null || generation != GENERATION.get()) return;
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File file : files) {
                if (!file.isFile() || keep.contains(file.getName())) continue;
                // Leave in-flight temp files of live tabs alone; trimDiskCache sweeps stale ones.
                if (file.getName().endsWith(".tmp")) continue;
                deleteQuietly(file);
            }
        });
    }

    private static File getDiskCacheDir() {
        File dir = diskCacheDir;
        if (dir != null && dir.isDirectory()) return dir;
        try {
            com.petal.browser.PetalApplication app = com.petal.browser.PetalApplication.Companion.getInstance();
            if (app != null) initDiskCache(app);
        } catch (Exception ignored) {}
        return diskCacheDir;
    }

    /**
     * Stores a detached, bounded snapshot. Private snapshots are memory-only.
     *
     * Like Firefox's thumbnail pipeline, an empty (single-colour) capture is never stored: a
     * compositor that has not painted yet returns a blank frame, and persisting it would replace
     * a good thumbnail with an empty card. Returns true only when the snapshot was stored.
     */
    public static synchronized boolean put(@Nullable String tabId, @Nullable Bitmap bitmap, boolean isPrivate) {
        return put(tabId, bitmap, isPrivate, false);
    }

    /**
     * @param allowUniform when true (the page has reported a first contentful paint) a solid
     *                     colour frame such as a dark or white page is a real thumbnail; only a
     *                     fully transparent frame is rejected.
     */
    public static synchronized boolean put(@Nullable String tabId, @Nullable Bitmap bitmap, boolean isPrivate, boolean allowUniform) {
        if (isBlank(tabId) || bitmap == null || bitmap.isRecycled()) return false;
        if (allowUniform ? isTransparentBitmap(bitmap) : isBlankBitmap(bitmap)) return false;
        Bitmap snapshot = makeSnapshot(bitmap);
        if (snapshot == null || snapshot.isRecycled()) return false;
        LruCache<String, Bitmap> cache = isPrivate ? privateMemoryCache : regularMemoryCache;
        cache.put(tabId, snapshot);
        if (isPrivate) return true;

        long token = TOKENS.incrementAndGet();
        long generation = GENERATION.get();
        regularVersions.put(tabId, token);
        DISK.execute(() -> saveToDisk(tabId, snapshot, token, generation));
        return true;
    }

    private static boolean isTransparentBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) return true;
        try {
            for (int yi = 0; yi < 24; yi++) {
                int y = Math.min(height - 1, (int) ((yi + 0.5f) * height / 24));
                for (int xi = 0; xi < 24; xi++) {
                    int x = Math.min(width - 1, (int) ((xi + 0.5f) * width / 24));
                    if ((bitmap.getPixel(x, y) >>> 24) != 0) return false;
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void put(@Nullable String tabId, @Nullable Bitmap bitmap) {
        put(tabId, bitmap, false);
    }

    /**
     * Cheap blank-frame check: samples a coarse grid and reports true when every sampled pixel is
     * identical (fully transparent, solid white, solid black...). Real pages always contain text,
     * images or chrome that differ across a 48x48 sample grid.
     */
    public static boolean isBlankBitmap(@Nullable Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return true;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) return true;
        int stepsX = Math.min(width, 48);
        int stepsY = Math.min(height, 48);
        try {
            int first = 0;
            boolean initialized = false;
            for (int yi = 0; yi < stepsY; yi++) {
                int y = Math.min(height - 1, (int) ((yi + 0.5f) * height / stepsY));
                for (int xi = 0; xi < stepsX; xi++) {
                    int x = Math.min(width - 1, (int) ((xi + 0.5f) * width / stepsX));
                    int pixel = bitmap.getPixel(x, y);
                    if (!initialized) {
                        first = pixel;
                        initialized = true;
                    } else if (pixel != first) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Throwable ignored) {
            // Hardware bitmaps cannot be sampled; treat them as real content.
            return false;
        }
    }

    /** Returns a regular-tab memory entry only; this method never blocks on disk. */
    @Nullable
    public static Bitmap get(@Nullable String tabId) {
        return getMemoryOnly(tabId, false);
    }

    @Nullable
    public static Bitmap getMemoryOnly(@Nullable String tabId) {
        return getMemoryOnly(tabId, false);
    }

    @Nullable
    public static Bitmap getMemoryOnly(@Nullable String tabId, boolean isPrivate) {
        if (isBlank(tabId)) return null;
        Bitmap bitmap = (isPrivate ? privateMemoryCache : regularMemoryCache).get(tabId);
        return bitmap != null && !bitmap.isRecycled() ? bitmap : null;
    }

    /** Asynchronously loads a regular-tab thumbnail. */
    public static void loadAsync(@Nullable String tabId, @NonNull ThumbnailCallback callback) {
        loadFirstAsync(new String[]{tabId == null ? "" : tabId}, false, callback);
    }

    public static void loadFirstAsync(@NonNull String[] identifiers, @NonNull ThumbnailCallback callback) {
        loadFirstAsync(identifiers, false, callback);
    }

    /** Private lookups are memory-only and can never fall through to disk. */
    public static void loadFirstAsync(@NonNull String[] identifiers, boolean isPrivate,
                                      @NonNull ThumbnailCallback callback) {
        if (isPrivate) {
            for (String id : identifiers) {
                Bitmap memory = getMemoryOnly(id, true);
                if (memory != null) {
                    dispatch(callback, memory);
                    return;
                }
            }
            dispatch(callback, null);
            return;
        }
        ArrayList<String> uniqueIds = new ArrayList<>();
        HashSet<String> seenIds = new HashSet<>();
        for (String id : identifiers) {
            if (!isBlank(id) && seenIds.add(id)) uniqueIds.add(id);
        }
        String[] ids = uniqueIds.toArray(new String[0]);
        if (ids.length == 0) {
            dispatch(callback, null);
            return;
        }
        LruCache<String, Bitmap> cache = isPrivate ? privateMemoryCache : regularMemoryCache;
        for (String id : ids) {
            Bitmap memory = cache.get(id);
            if (memory != null && !memory.isRecycled()) {
                dispatch(callback, memory);
                return;
            }
        }
        long generation = GENERATION.get();
        long[] versions = new long[ids.length];
        for (int i = 0; i < ids.length; i++) versions[i] = versionOf(ids[i]);
        String requestKey = String.join("\u0000", ids) + "\u0002";
        if (!pendingLoads.add(requestKey)) {
            loadCallbacks.computeIfAbsent(requestKey, ignored -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(callback);
            return;
        }
        java.util.concurrent.CopyOnWriteArrayList<ThumbnailCallback> callbacks = new java.util.concurrent.CopyOnWriteArrayList<>();
        callbacks.add(callback);
        loadCallbacks.put(requestKey, callbacks);
        DISK.execute(() -> {
            Bitmap loaded = null;
            try {
                String loadedId = null;
                for (String id : ids) {
                    loaded = loadFromDisk(id);
                    if (loaded != null && !loaded.isRecycled()) {
                        loadedId = id;
                        break;
                    }
                    loaded = null;
                }
                boolean valid = generation == GENERATION.get() && !versionsChanged(ids, versions);
                if (!valid) {
                    if (loaded != null && !loaded.isRecycled()) loaded.recycle();
                    loaded = null;
                } else if (loaded != null && loadedId != null) {
                    regularMemoryCache.put(loadedId, loaded);
                    File dir = getDiskCacheDir();
                    File canonical = dir == null ? null : new File(dir, diskKey(loadedId) + ".thumb");
                    if (canonical != null && !canonical.isFile()) put(loadedId, loaded, false);
                }
            } finally {
                java.util.concurrent.CopyOnWriteArrayList<ThumbnailCallback> completed = loadCallbacks.remove(requestKey);
                pendingLoads.remove(requestKey);
                if (completed != null) {
                    for (ThumbnailCallback waiter : completed) dispatch(waiter, loaded);
                }
            }
        });
    }

    /** Evicts an identifier from both memory tiers and from regular-tab disk storage. */
    public static synchronized void remove(@Nullable String tabId) {
        if (isBlank(tabId)) return;
        regularMemoryCache.remove(tabId);
        privateMemoryCache.remove(tabId);
        long token = TOKENS.incrementAndGet();
        long generation = GENERATION.get();
        regularVersions.put(tabId, token);
        DISK.execute(() -> {
            if (generation != GENERATION.get() || versionOf(tabId) != token) return;
            File dir = getDiskCacheDir();
            if (dir == null) return;
            deleteQuietly(new File(dir, diskKey(tabId) + ".thumb"));
            deleteQuietly(new File(dir, legacyKey(tabId) + ".webp"));
            deleteQuietly(new File(dir, legacyKey(tabId) + ".png"));
            regularVersions.remove(tabId, token);
        });
    }

    public static void removeAllIdentifiers(@Nullable String... ids) {
        if (ids == null) return;
        for (String id : ids) if (!isBlank(id)) remove(id);
    }

    public static void clearPrivateCache() {
        privateMemoryCache.evictAll();
    }

    public static synchronized void removePrivate(@Nullable String tabId) {
        if (!isBlank(tabId)) privateMemoryCache.remove(tabId);
    }

    public static void clearMemory() {
        regularMemoryCache.evictAll();
        privateMemoryCache.evictAll();
    }

    public static void clear() {
        evictAll();
    }

    /** Invalidates pending writes/reads and deletes regular disk entries in queue order. */
    public static synchronized void evictAll() {
        regularMemoryCache.evictAll();
        privateMemoryCache.evictAll();
        regularVersions.clear();
        pendingLoads.clear();
        loadCallbacks.clear();
        long generation = GENERATION.incrementAndGet();
        DISK.execute(() -> {
            if (generation != GENERATION.get()) return;
            File dir = getDiskCacheDir();
            if (dir == null) return;
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File file : files) {
                if (generation != GENERATION.get()) return;
                if (file.isFile()) deleteQuietly(file);
            }
        });
    }

    /**
     * Produces a detached, bounded snapshot: top-aligned crop to MAX_PREVIEW_ASPECT, scaled to
     * at most MAX_PREVIEW_WIDTH. The result never aliases [source], so callers may recycle it.
     */
    @Nullable
    private static Bitmap makeSnapshot(Bitmap source) {
        Bitmap software = null;
        try {
            Bitmap from = source;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.getConfig() == Bitmap.Config.HARDWARE) {
                software = source.copy(Bitmap.Config.ARGB_8888, false);
                if (software == null) return null;
                from = software;
            }
            int width = from.getWidth();
            int height = from.getHeight();
            if (width <= 0 || height <= 0) return null;
            int cropHeight = Math.min(height, Math.round(width * MAX_PREVIEW_ASPECT));
            float scale = Math.min(1f, MAX_PREVIEW_WIDTH / (float) width);
            Bitmap.Config config = from.getConfig() == null ? Bitmap.Config.ARGB_8888 : from.getConfig();
            if (scale >= 1f && cropHeight == height) {
                return from.copy(config, false);
            }
            int outWidth = Math.max(1, Math.round(width * scale));
            int outHeight = Math.max(1, Math.round(cropHeight * scale));
            Bitmap out = Bitmap.createBitmap(outWidth, outHeight, config);
            Canvas canvas = new Canvas(out);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            canvas.drawBitmap(from, new Rect(0, 0, width, cropHeight), new Rect(0, 0, outWidth, outHeight), paint);
            return out;
        } catch (Throwable error) {
            Log.w(TAG, "Could not snapshot tab thumbnail", error);
            return null;
        } finally {
            if (software != null && !software.isRecycled()) software.recycle();
        }
    }

    /** Returns [bitmap] itself when it already fits, otherwise a new bounded copy (or [bitmap] on failure). */
    private static Bitmap downscaleIfNeeded(Bitmap bitmap) {
        if (bitmap.getWidth() <= MAX_PREVIEW_WIDTH && bitmap.getHeight() <= MAX_PREVIEW_HEIGHT) return bitmap;
        Bitmap fitted = makeSnapshot(bitmap);
        return fitted == null ? bitmap : fitted;
    }

    private static void saveToDisk(String tabId, Bitmap bitmap, long token, long generation) {
        File dir = getDiskCacheDir();
        if (dir == null || !isCurrent(tabId, token, generation) || bitmap.isRecycled()) return;
        File target = new File(dir, diskKey(tabId) + ".thumb");
        File temp = new File(dir, diskKey(tabId) + "." + token + ".tmp");
        try {
            boolean compressed;
            try (FileOutputStream out = new FileOutputStream(temp)) {
                // Lossy WebP at q86 is ~10x smaller than the old PNG fallback on pre-R devices.
                @SuppressWarnings("deprecation")
                Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
                compressed = bitmap.compress(format, 86, out);
                out.flush();
                out.getFD().sync();
            }
            if (!compressed || !isCurrent(tabId, token, generation)) {
                deleteQuietly(temp);
                return;
            }
            if (!temp.renameTo(target)) {
                deleteQuietly(target);
                if (!temp.renameTo(target)) throw new java.io.IOException("Could not publish thumbnail file");
            }
            if (!isCurrent(tabId, token, generation)) deleteQuietly(target);
            if (WRITES_SINCE_TRIM.incrementAndGet() >= TRIM_EVERY_N_WRITES) {
                WRITES_SINCE_TRIM.set(0);
                trimDiskCache(dir);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed saving thumbnail for tab", e);
            deleteQuietly(temp);
        }
    }

    @Nullable
    private static Bitmap loadFromDisk(String tabId) {
        File dir = getDiskCacheDir();
        if (dir == null) return null;
        Bitmap result = decodeThumbnail(new File(dir, diskKey(tabId) + ".thumb"));
        if (result == null) {
            // Existing installs used a sanitized filename. It is only checked for this
            // exact tab ID; UI callers no longer pass URLs as thumbnail fallbacks.
            result = decodeThumbnail(new File(dir, legacyKey(tabId) + ".webp"));
            if (result == null) result = decodeThumbnail(new File(dir, legacyKey(tabId) + ".png"));
            // Loaded legacy entries are served once and rewritten through the normal
            // versioned put path by the caller after it validates the read generation.
        }
        return result;
    }

    @Nullable
    private static Bitmap decodeThumbnail(File file) {
        if (!file.isFile() || file.length() <= 0) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                deleteQuietly(file);
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (bounds.outWidth / options.inSampleSize > MAX_PREVIEW_WIDTH * 2) options.inSampleSize *= 2;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null || bitmap.isRecycled()) {
                deleteQuietly(file);
                return null;
            }
            file.setLastModified(System.currentTimeMillis());
            Bitmap thumbnail = downscaleIfNeeded(bitmap);
            if (thumbnail != bitmap && !bitmap.isRecycled()) bitmap.recycle();
            return thumbnail;
        } catch (OutOfMemoryError error) {
            Log.w(TAG, "Not enough memory to decode tab thumbnail", error);
            return null;
        } catch (Exception e) {
            Log.w(TAG, "Could not decode tab thumbnail", e);
            deleteQuietly(file);
            return null;
        }
    }

    private static void trimDiskCache(File dir) {
        File[] files = dir.listFiles((parent, name) -> name.endsWith(".thumb") || name.endsWith(".webp") || name.endsWith(".png") || name.endsWith(".tmp"));
        if (files == null || files.length == 0) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long now = System.currentTimeMillis();
        long total = 0L;
        java.util.ArrayList<File> retained = new java.util.ArrayList<>();
        for (File file : files) {
            // Stale temp files (older than a minute) and pre-v2 legacy names are swept. There is
            // deliberately no age limit: a thumbnail lives exactly as long as its tab (retainOnly).
            boolean staleTemp = file.getName().endsWith(".tmp") && now - file.lastModified() > 60_000L;
            if (staleTemp || (!file.getName().endsWith(".tmp") && !file.getName().startsWith("v2_"))) {
                deleteQuietly(file);
            } else if (!file.getName().endsWith(".tmp")) {
                total += file.length();
                retained.add(file);
            }
        }
        for (File file : retained) {
            if (total <= MAX_DISK_CACHE_BYTES) break;
            long size = file.length();
            if (deleteQuietly(file)) total -= size;
        }
    }

    private static String diskKey(String tabId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(tabId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder("v2_");
            for (byte value : digest) key.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return key.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** Previous cache filename format, retained only for one-way per-tab migration. */
    private static String legacyKey(String key) {
        String sanitized = key.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.length() > 48) {
            String hash = String.valueOf(Math.abs(key.hashCode()));
            sanitized = sanitized.substring(0, 36) + "_" + hash;
        }
        return sanitized;
    }

    private static boolean isCurrent(String tabId, long token, long generation) {
        return generation == GENERATION.get() && versionOf(tabId) == token;
    }

    private static long versionOf(String tabId) {
        Long version = regularVersions.get(tabId);
        return version == null ? 0L : version;
    }

    private static boolean versionsChanged(String[] ids, long[] versions) {
        for (int i = 0; i < ids.length; i++) if (versionOf(ids[i]) != versions[i]) return true;
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean deleteQuietly(File file) {
        return !file.exists() || file.delete();
    }

    private static void dispatch(ThumbnailCallback callback, @Nullable Bitmap bitmap) {
        if (Looper.myLooper() == Looper.getMainLooper()) callback.onThumbnailLoaded(bitmap);
        else MAIN.post(() -> callback.onThumbnailLoaded(bitmap));
    }

    public interface ThumbnailCallback {
        void onThumbnailLoaded(@Nullable Bitmap bitmap);
    }
}
