package com.petal.browser.browser;

import java.util.ArrayList;
import java.util.List;

/**
 * Petal Browser's primary in-memory tab registry and strip manager.
 * Modernized from legacy Lightning Browser BrowserContainer.
 */
public class PetalTabRegistry {
    protected static final List<AlbumController> list = new ArrayList<>();

    public synchronized static AlbumController get(int index) {
        return list.get(index);
    }

    public synchronized static void add(AlbumController controller) {
        list.add(controller);
    }

    public synchronized static void add(AlbumController controller, int index) {
        list.add(index, controller);
    }

    public synchronized static void replace(int index, AlbumController controller) {
        list.set(index, controller);
    }

    public synchronized static void remove(AlbumController controller) {
        // Unlink first so a throwing destroy() cannot leave a dead controller in the list.
        list.remove(controller);
        if (controller != null) {
            controller.destroy();
        }
    }

    public synchronized static int indexOf(AlbumController controller) {
        return list.indexOf(controller);
    }

    public static List<AlbumController> list() {
        return list;
    }

    public synchronized static int size() {
        return list.size();
    }

    public synchronized static int getNormalCount() {
        int count = 0;
        for (AlbumController controller : list) {
            if (!controller.isIncognito()) {
                count++;
            }
        }
        return count;
    }

    public synchronized static int getIncognitoCount() {
        int count = 0;
        for (AlbumController controller : list) {
            if (controller.isIncognito()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Permanently closes every tab: each controller's destroy() removes its tab from the
     * BrowserStore and deletes its thumbnail. Only for an explicit "close all tabs".
     * Activity teardown must use {@link #releaseAll()} instead.
     */
    public synchronized static void clear() {
        List<AlbumController> copy = new ArrayList<>(list);
        list.clear();
        for (AlbumController albumController : copy) {
            if (albumController != null) {
                albumController.destroy();
            }
        }
    }

    /**
     * Drops only the views (Activity teardown / recreation). The tabs themselves stay in the
     * BrowserStore, so Mozilla's AutoSave keeps the saved session and the tab thumbnails stay
     * on disk. This mirrors Fenix: tabs are removed only when the user closes them, never
     * because a view was destroyed.
     */
    public synchronized static void releaseAll() {
        List<AlbumController> copy = new ArrayList<>(list);
        list.clear();
        for (AlbumController albumController : copy) {
            if (albumController == null) continue;
            try {
                if (albumController instanceof PetalTabViewController) {
                    ((PetalTabViewController) albumController).release();
                } else if (!(albumController instanceof PlaceholderAlbumController)) {
                    albumController.destroy();
                }
            } catch (Throwable ignored) {
                // A failing view teardown must never abort the rest of the release.
            }
        }
    }
}
