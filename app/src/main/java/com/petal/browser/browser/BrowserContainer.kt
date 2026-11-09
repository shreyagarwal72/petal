package com.petal.browser.browser

/**
 * @deprecated Legacy Lightning Browser container. Replaced by [PetalTabRegistry].
 */
@Deprecated("Legacy Lightning Browser container. Replaced by PetalTabRegistry.")
open class BrowserContainer : PetalTabRegistry() {
    companion object {
        @JvmStatic
        val list: List<AlbumController> get() = PetalTabRegistry.list

        @JvmStatic
        fun list(): List<AlbumController> = PetalTabRegistry.list()

        @JvmStatic
        val size: Int get() = PetalTabRegistry.size

        @JvmStatic
        fun size(): Int = PetalTabRegistry.size()

        @JvmStatic
        operator fun get(index: Int): AlbumController = PetalTabRegistry[index]

        @JvmStatic
        fun add(controller: AlbumController) = PetalTabRegistry.add(controller)

        @JvmStatic
        fun add(controller: AlbumController, index: Int) = PetalTabRegistry.add(controller, index)

        @JvmStatic
        fun replace(index: Int, controller: AlbumController) = PetalTabRegistry.replace(index, controller)

        @JvmStatic
        fun remove(controller: AlbumController?) = PetalTabRegistry.remove(controller)

        @JvmStatic
        fun indexOf(controller: AlbumController?): Int = PetalTabRegistry.indexOf(controller)

        @JvmStatic
        fun getNormalCount(): Int = PetalTabRegistry.getNormalCount()

        @JvmStatic
        fun getIncognitoCount(): Int = PetalTabRegistry.getIncognitoCount()

        @JvmStatic
        fun clear() = PetalTabRegistry.clear()

        @JvmStatic
        fun releaseAll() = PetalTabRegistry.releaseAll()
    }
}
