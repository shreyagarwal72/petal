package com.petal.browser.browser

import com.petal.browser.view.NinjaWebView
import java.util.LinkedList

object BrowserContainer {
    private val list: MutableList<AlbumController> = LinkedList()

    @JvmStatic
    fun get(index: Int): AlbumController {
        return list[index]
    }

    @Synchronized
    @JvmStatic
    fun add(controller: AlbumController) {
        list.add(controller)
    }

    @Synchronized
    @JvmStatic
    fun add(controller: AlbumController, index: Int) {
        list.add(index, controller)
    }

    @Synchronized
    @JvmStatic
    fun remove(controller: AlbumController) {
        if (controller is NinjaWebView) {
            controller.destroy()
        }
        list.remove(controller)
    }

    @JvmStatic
    fun indexOf(controller: AlbumController): Int {
        return list.indexOf(controller)
    }

    @JvmStatic
    fun list(): List<AlbumController> {
        return list
    }

    @JvmStatic
    fun size(): Int {
        return list.size
    }

    @Synchronized
    @JvmStatic
    fun getNormalCount(): Int {
        var count = 0
        for (controller in list) {
            if (controller is NinjaWebView) {
                if (!controller.isIncognito) {
                    count++
                }
            } else {
                count++
            }
        }
        return count
    }

    @Synchronized
    @JvmStatic
    fun getIncognitoCount(): Int {
        var count = 0
        for (controller in list) {
            if (controller is NinjaWebView && controller.isIncognito) {
                count++
            }
        }
        return count
    }

    @Synchronized
    @JvmStatic
    fun clear() {
        for (controller in list) {
            if (controller is NinjaWebView) {
                controller.destroy()
            }
        }
        list.clear()
    }
}
