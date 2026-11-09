package com.petal.browser.browser

import java.util.ArrayList
import java.util.Collections

/**
 * Petal Browser's primary in-memory tab registry and strip manager.
 * Modernized from legacy Lightning Browser BrowserContainer.
 */
open class PetalTabRegistry {
    companion object {
        @JvmStatic
        protected val list: MutableList<AlbumController> = Collections.synchronizedList(ArrayList())

        @JvmStatic
        @Synchronized
        fun get(index: Int): AlbumController = list[index]

        @JvmStatic
        @Synchronized
        fun add(controller: AlbumController) {
            list.add(controller)
        }

        @JvmStatic
        @Synchronized
        fun add(controller: AlbumController, index: Int) {
            list.add(index, controller)
        }

        @JvmStatic
        @Synchronized
        fun replace(index: Int, controller: AlbumController) {
            list[index] = controller
        }

        @JvmStatic
        @Synchronized
        fun remove(controller: AlbumController?) {
            if (controller == null) return
            list.remove(controller)
            controller.destroy()
        }

        @JvmStatic
        @Synchronized
        fun indexOf(controller: AlbumController?): Int = list.indexOf(controller)

        @JvmStatic
        fun list(): List<AlbumController> = list

        @JvmStatic
        @Synchronized
        fun size(): Int = list.size

        @JvmStatic
        @Synchronized
        fun getNormalCount(): Int = list.count { !it.isIncognito() }

        @JvmStatic
        @Synchronized
        fun getIncognitoCount(): Int = list.count { it.isIncognito() }

        @JvmStatic
        @Synchronized
        fun clear() {
            val copy = ArrayList(list)
            list.clear()
            for (albumController in copy) {
                albumController.destroy()
            }
        }

        @JvmStatic
        @Synchronized
        fun releaseAll() {
            val copy = ArrayList(list)
            list.clear()
            for (albumController in copy) {
                try {
                    if (albumController is PetalTabViewController) {
                        albumController.release()
                    } else if (albumController !is PlaceholderAlbumController) {
                        albumController.destroy()
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }
}
