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
        protected val _list: MutableList<AlbumController> = Collections.synchronizedList(ArrayList())

        @JvmStatic
        val list: List<AlbumController> get() = _list

        @JvmStatic
        fun list(): List<AlbumController> = _list

        @JvmStatic
        val size: Int get() = _list.size

        @JvmStatic
        @Synchronized
        fun size(): Int = _list.size

        @JvmStatic
        operator fun get(index: Int): AlbumController = _list[index]

        @JvmStatic
        @Synchronized
        fun add(controller: AlbumController) {
            _list.add(controller)
        }

        @JvmStatic
        @Synchronized
        fun add(controller: AlbumController, index: Int) {
            _list.add(index, controller)
        }

        @JvmStatic
        @Synchronized
        fun replace(index: Int, controller: AlbumController) {
            _list[index] = controller
        }

        @JvmStatic
        @Synchronized
        fun remove(controller: AlbumController?) {
            if (controller == null) return
            _list.remove(controller)
            controller.destroy()
        }

        @JvmStatic
        @Synchronized
        fun indexOf(controller: AlbumController?): Int = _list.indexOf(controller)

        @JvmStatic
        @Synchronized
        fun getNormalCount(): Int = _list.count { !it.isIncognito() }

        @JvmStatic
        @Synchronized
        fun getIncognitoCount(): Int = _list.count { it.isIncognito() }

        @JvmStatic
        @Synchronized
        fun clear() {
            val copy = ArrayList(_list)
            _list.clear()
            for (albumController in copy) {
                albumController.destroy()
            }
        }

        @JvmStatic
        @Synchronized
        fun releaseAll() {
            val copy = ArrayList(_list)
            _list.clear()
            for (albumController in copy) {
                if (albumController == null) continue
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
