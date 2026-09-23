package com.petal.browser.browser;

import java.util.LinkedList;
import java.util.List;

/**
 * Petal Browser's primary tab container.
 * Completely unified on Mozilla GeckoView / AlbumController architecture, eliminating
 * dual-engine branches and legacy FOSS Browser (Ninja) checks.
 */
public class BrowserContainer {
    private static final List<AlbumController> list = new LinkedList<>();

    public static AlbumController get(int index) {
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
        if (controller != null) {
            controller.destroy();
        }
        list.remove(controller);
    }

    public static int indexOf(AlbumController controller) {
        return list.indexOf(controller);
    }

    public static List<AlbumController> list() {
        return list;
    }

    public static int size() {
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

    public synchronized static void clear() {
        for (AlbumController albumController : list) {
            if (albumController != null) {
                albumController.destroy();
            }
        }
        list.clear();
    }
}
