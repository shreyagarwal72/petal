package com.petal.browser.download

import android.app.NotificationManager
import android.os.Build
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

/**
 * Optimizes NotificationManager Binder IPC.
 * Mozilla's DownloadNotification.ensureChannelExists() calls createNotificationChannel()
 * synchronously on the Main Thread on EVERY download progress update tick.
 * On modern Android (SDK 34-37), each call takes a blocking Binder IPC transaction
 * to system_server, freezing the main thread for 1.5 - 2.5 seconds during active downloads.
 *
 * This optimizer caches already-created notification channel IDs in memory so subsequent
 * redundant createNotificationChannel calls return immediately without blocking the Main Thread.
 */
object PetalNotificationChannelOptimizer {
    private const val TAG = "NotifChannelOptimizer"
    private val createdChannels = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var installed = false

    @JvmStatic
    fun install() {
        if (installed || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        synchronized(this) {
            if (installed) return
            try {
                val nmClass = NotificationManager::class.java
                val getServiceMethod = nmClass.getDeclaredMethod("getService")
                getServiceMethod.isAccessible = true
                val originalService = getServiceMethod.invoke(null) ?: return

                val iNmInterface = Class.forName("android.app.INotificationManager")
                val proxy = Proxy.newProxyInstance(
                    iNmInterface.classLoader,
                    arrayOf(iNmInterface),
                    object : InvocationHandler {
                        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
                            val name = method.name
                            if (name == "createNotificationChannels" || name == "createNotificationChannelGroups") {
                                val channelsArg = args?.getOrNull(1)
                                if (areAllChannelsCreated(channelsArg)) {
                                    // Channel already created and registered in system_server.
                                    // Bypass blocking synchronous Binder IPC!
                                    return null
                                }
                                val result = method.invoke(originalService, *(args ?: emptyArray()))
                                recordChannels(channelsArg)
                                return result
                            }
                            if (name == "enqueueNotificationWithTag") {
                                // One notification per download: swap Mozilla's plain one for Petal's live one.
                                val swapped = PetalMozillaNotificationSwapper.intercept(args)
                                if (swapped === PetalMozillaNotificationSwapper.DROP) return null
                                return method.invoke(originalService, *(swapped ?: emptyArray()))
                            }
                            return method.invoke(originalService, *(args ?: emptyArray()))
                        }
                    }
                )

                val sServiceField = nmClass.getDeclaredField("sService")
                sServiceField.isAccessible = true
                sServiceField.set(null, proxy)
                installed = true
                Log.i(TAG, "Notification channel IPC cache optimizer installed successfully")
            } catch (t: Throwable) {
                Log.w(TAG, "Could not install Notification channel optimizer: ${t.message}")
            }
        }
    }

    private fun areAllChannelsCreated(arg: Any?): Boolean {
        if (arg == null) return false
        try {
            val listMethod = arg.javaClass.getMethod("getList")
            val list = listMethod.invoke(arg) as? List<*> ?: return false
            if (list.isEmpty()) return true
            return list.all { item ->
                val id = item?.javaClass?.getMethod("getId")?.invoke(item) as? String
                id != null && createdChannels.contains(id)
            }
        } catch (_: Throwable) {
            return false
        }
    }

    private fun recordChannels(arg: Any?) {
        if (arg == null) return
        try {
            val listMethod = arg.javaClass.getMethod("getList")
            val list = listMethod.invoke(arg) as? List<*> ?: return
            list.forEach { item ->
                val id = item?.javaClass?.getMethod("getId")?.invoke(item) as? String
                if (id != null) createdChannels.add(id)
            }
        } catch (_: Throwable) {}
    }
}
