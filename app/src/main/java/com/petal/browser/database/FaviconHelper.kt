package com.petal.browser.database

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.View
import android.widget.ImageView
import com.petal.browser.unit.PetalAndroidUtils
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

open class FaviconHelper(context: Context?) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "favicon.db"
        private const val TABLE_FAVICON = "Favicon"
        private const val DOMAIN = "domain"
        private const val IMAGE = "image"
        private const val CREATE_TABLE_FAVICON = "CREATE TABLE " + TABLE_FAVICON + "(" +
                DOMAIN + " TEXT," +
                IMAGE + " BLOB);"

        @JvmStatic
        fun convertBytes(bitmap: Bitmap): ByteArray {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream)
            return stream.toByteArray()
        }

        @JvmStatic
        fun getBitmap(byteimage: ByteArray): Bitmap {
            return BitmapFactory.decodeByteArray(byteimage, 0, byteimage.size)
        }

        @JvmStatic
        fun setFavicon(context: Context, view: View, url: String?, id: Int, idImage: Int) {
            val faviconView = view.findViewById<ImageView>(id)
            try {
                FaviconHelper(context).use { faviconHelper ->
                    val bitmap = faviconHelper.getFavicon(url)
                    if (faviconView != null) {
                        if (bitmap != null) {
                            faviconView.setImageBitmap(bitmap)
                        } else {
                            faviconView.setImageResource(idImage)
                        }
                    }
                }
            } catch (e: Exception) {
                faviconView?.setImageResource(idImage)
            }
        }

        /**
         * Constructs a high-resolution 64x64 favicon URL using Google Favicon API for any domain.
         * Endpoint: https://www.google.com/s2/favicons?domain={domain}&sz=64
         */
        @JvmStatic
        fun getGoogleFaviconUrl(domain: String?): String {
            if (domain.isNullOrBlank()) return ""
            val cleanDomain = domain.replace("https://", "").replace("http://", "").split("/")[0]
            return "https://www.google.com/s2/favicons?domain=$cleanDomain&sz=64"
        }

        /**
         * Constructs high-resolution PNG favicon URL using IconHorse API as a secondary fallback.
         * Endpoint: https://icon.horse/icon/{domain}
         */
        @JvmStatic
        fun getIconHorseFaviconUrl(domain: String?): String {
            if (domain.isNullOrBlank()) return ""
            val cleanDomain = domain.replace("https://", "").replace("http://", "").split("/")[0]
            return "https://icon.horse/icon/$cleanDomain"
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_FAVICON)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FAVICON")
        onCreate(db)
    }

    @Synchronized
    @Throws(SQLiteException::class)
    fun addFavicon(context: Context, url: String?, bitmap: Bitmap?) {
        if (bitmap == null || url == null) return
        val domain = PetalAndroidUtils.domain(url)
        val database = this.writableDatabase
        // first delete existing Favicon for domain if available
        database.delete(TABLE_FAVICON, "$DOMAIN = ?", arrayOf(domain.trim()))
        val byteImage = convertBytes(bitmap)
        val values = ContentValues().apply {
            put(DOMAIN, domain)
            put(IMAGE, byteImage)
        }
        database.insert(TABLE_FAVICON, null, values)
        database.close()
        cleanUpFaviconDB(context)
    }

    @Synchronized
    @Throws(SQLiteException::class)
    fun deleteFavicon(domain: String?) {
        if (domain.isNullOrBlank()) return
        val database = this.writableDatabase
        database.delete(TABLE_FAVICON, "$DOMAIN = ?", arrayOf(domain.trim()))
        database.close()
    }

    @Synchronized
    fun getFavicon(url: String?): Bitmap? {
        if (url == null) return null
        val domain = PetalAndroidUtils.domain(url)
        try {
            val database = this.readableDatabase
            database.use { db ->
                val cursor: Cursor = db.query(
                    TABLE_FAVICON,
                    arrayOf(DOMAIN, IMAGE),
                    "$DOMAIN = ?",
                    arrayOf(domain),
                    null,
                    null,
                    null,
                    null
                )
                cursor.use { c ->
                    return if (c.moveToFirst()) {
                        val image = c.getBlob(1)
                        getBitmap(image)
                    } else {
                        null
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }
    }

    @Synchronized
    fun getAllFaviconDomains(): List<String> {
        val result = ArrayList<String>()
        try {
            val database = this.readableDatabase
            database.use { db ->
                val cursor: Cursor = db.query(
                    TABLE_FAVICON,
                    arrayOf(DOMAIN, IMAGE),
                    null,
                    null,
                    null,
                    null,
                    null
                )
                cursor.use { c ->
                    if (c.moveToFirst()) {
                        while (!c.isAfterLast) {
                            result.add(c.getString(0))
                            c.moveToNext()
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun cleanUpFaviconDB(context: Context) {
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val faviconURLs = getAllFaviconDomains()
                val action = HistoryBookmarkRepository(context)
                val allEntries = if (context is Activity) action.listEntries(context) else emptyList()
                for (faviconURL in faviconURLs) {
                    var found = false
                    for (entry in allEntries) {
                        if (PetalAndroidUtils.domain(entry.url) == faviconURL) {
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        deleteFavicon(faviconURL)
                        Log.d("Favicon delete", faviconURL)
                    }
                }
            } catch (e: Exception) {
                Log.w("FaviconHelper", "cleanUpFaviconDB error", e)
            }
        }
    }
}
