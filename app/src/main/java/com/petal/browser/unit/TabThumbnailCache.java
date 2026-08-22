package com.petal.browser.unit;

import android.graphics.Bitmap;
import android.util.LruCache;

import androidx.annotation.Nullable;

/**
 * Bounded in-memory cache for the tab manager's live PixelCopy/software-draw preview
 * thumbnails, keyed by tab id (the same {@code hashCode().toString()} id used throughout
 * the Compose tab switcher).
 * <p>
 * Without this, every capture (on page load, tab switch, and tab-visible-in-grid) simply
 * handed a fresh {@link Bitmap} to the caller with nothing bounding how many stayed alive
 * at once - on a tab-heavy session that grows without limit. This cache fixes that: it
 * holds at most {@link #MAX_ENTRIES} thumbnails, and recycles whichever one it evicts so
 * the native bitmap memory backing it is actually freed rather than just dropped from the
 * map and left for the GC to eventually notice.
 */
public final class TabThumbnailCache {

    // Thumbnails are downscaled to ~480px wide before they ever reach this cache (see
    // NinjaWebView#capturePreviewBitmap), so a fixed entry-count bound is cheap and simple
    // rather than needing a byte-size-aware LruCache.
    private static final int MAX_ENTRIES = 18;

    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(MAX_ENTRIES) {
        @Override
        protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
            if (oldValue != null && oldValue != newValue && !oldValue.isRecycled()) {
                oldValue.recycle();
            }
        }
    };

    private TabThumbnailCache() {}

    public static void put(@Nullable String tabId, @Nullable Bitmap bitmap) {
        if (tabId == null || tabId.isEmpty() || bitmap == null || bitmap.isRecycled()) return;
        cache.put(tabId, bitmap);
    }

    @Nullable
    public static Bitmap get(@Nullable String tabId) {
        if (tabId == null || tabId.isEmpty()) return null;
        Bitmap bitmap = cache.get(tabId);
        if (bitmap != null && bitmap.isRecycled()) {
            // Stale entry (recycled out from under us elsewhere) - drop it rather than
            // hand back a bitmap that will crash on draw.
            cache.remove(tabId);
            return null;
        }
        return bitmap;
    }

    /** Call when a tab is actually closed (not on optimistic/pending close) to free its slot. */
    public static void remove(@Nullable String tabId) {
        if (tabId == null || tabId.isEmpty()) return;
        cache.remove(tabId);
    }

    /** Call on incognito session teardown so private-tab thumbnails don't linger in memory. */
    public static void evictAll() {
        cache.evictAll();
    }
}
