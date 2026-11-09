package com.petal.browser.unit

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * PasswordBreachAuditManager checks passwords safely against HaveIBeenPwned API using k-anonymity SHA-1 prefixes.
 * Plain passwords are NEVER sent over the network.
 */
object PasswordBreachAuditManager {

    private const val TAG = "PasswordBreachAudit"
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun interface BreachCallback {
        fun onCheckCompleted(isPwned: Boolean, breachCount: Int)
    }

    /**
     * Checks if password SHA-1 hash exists in HIBP breach database using 5-character k-Anonymity prefix.
     * Endpoint: https://api.pwnedpasswords.com/range/{prefix}
     */
    @JvmStatic
    fun checkPassword(plainPassword: String?, callback: BreachCallback?) {
        if (plainPassword.isNullOrEmpty()) {
            callback?.onCheckCompleted(false, 0)
            return
        }

        executor.execute {
            var isPwned = false
            var breachCount = 0
            var connection: HttpURLConnection? = null

            try {
                // 1. Hash password with SHA-1
                val md = MessageDigest.getInstance("SHA-1")
                val bytes = md.digest(plainPassword.toByteArray(StandardCharsets.UTF_8))
                val sb = StringBuilder()
                for (b in bytes) {
                    sb.append(String.format(Locale.ROOT, "%02X", b))
                }
                val sha1Hash = sb.toString().uppercase(Locale.ROOT)

                // 2. Split into 5-char prefix and 35-char suffix
                val prefix = sha1Hash.substring(0, 5)
                val suffix = sha1Hash.substring(5)

                // 3. Query HIBP range API with prefix ONLY
                val url = URL("https://api.pwnedpasswords.com/range/$prefix")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val parts = line!!.split(":")
                            if (parts.size == 2 && parts[0].trim().equals(suffix, ignoreCase = true)) {
                                isPwned = true
                                breachCount = parts[1].trim().toIntOrNull() ?: 0
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "HIBP password breach audit failed", e)
            } finally {
                connection?.disconnect()
            }

            val finalIsPwned = isPwned
            val finalBreachCount = breachCount
            if (callback != null) {
                Handler(Looper.getMainLooper()).post {
                    callback.onCheckCompleted(finalIsPwned, finalBreachCount)
                }
            }
        }
    }
}
