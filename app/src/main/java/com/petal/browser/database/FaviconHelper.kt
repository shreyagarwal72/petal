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
import com.petal.browser.unit.HelperUnit
import java.io.ByteArrayOutputStream
import java.util.ArrayList
import java.util.Objects
import java.util.concurrent.Executors

class FaviconHelper(context: Context?) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_FAVICON)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FAVICON")
        onCreate(db)
    }

    @Synchronized
    @Throws(SQLiteException::class)
    fun addFavicon(context: Context?, url: String?, bitmap: Bitmap?) {
        if (url == null || bitmap == null) return
        val domain = HelperUnit.domain(url)
        val database = this.writableDatabase
        // first delete existing Favicon for domain if available
        database.delete(TABLE_FAVICON, "$DOMAIN = ?", arrayOf(domain.trim()))
        val byteImage = convertBytes(bitmap)
        val values = ContentValues()
        values.put(DOMAIN, domain)
        values.put(IMAGE, byteImage)
        database.insert(TABLE_FAVICON, null, values)
        database.close()
        cleanUpFaviconDB(context)
    }

    @Synchronized
    @Throws(SQLiteException::class)
    fun deleteFavicon(domain: String?) {
        if (domain == null) return
        val database = this.writableDatabase
        database.delete(TABLE_FAVICON, "$DOMAIN = ?", arrayOf(domain.trim()))
        database.close()
    }

    @Synchronized
    fun getFavicon(url: String?): Bitmap? {
        if (url == null) return null
        val domain = HelperUnit.domain(url)
        this.readableDatabase.use { database ->
            val cursor = database.query(
                TABLE_FAVICON,
                arrayOf(DOMAIN, IMAGE),
                "$DOMAIN = ?",
                arrayOf(domain),
                null,
                null,
                null,
                null
            )
            return if (cursor.moveToFirst()) {
                val image = cursor.getBlob(1)
                cursor.close()
                getBitmap(image)
            } else {
                cursor.close()
                null
            }
        }
    }

    @Synchronized
    fun getAllFaviconDomains(): List<String> {
        val database = this.readableDatabase
        val result: MutableList<String> = ArrayList()
        val cursor = database.query(
            TABLE_FAVICON,
            arrayOf(DOMAIN, IMAGE),
            null,
            null,
            null,
            null,
            null
        )
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            result.add(cursor.getString(0))
            cursor.moveToNext()
        }
        cursor.close()
        database.close()
        return result
    }

    fun cleanUpFaviconDB(context: Context?) {
        if (context == null) return
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            // Background work here
            val faviconURLs = getAllFaviconDomains()
            val action = RecordAction(context)
            val allEntries = action.listEntries(context as Activity)
            for (faviconURL in faviconURLs) {
                var found = false
                for (entry in allEntries) {
                    if (Objects.equals(HelperUnit.domain(entry.getURL()), faviconURL)) {
                        found = true
                        break
                    }
                }
                // If there is no entry in StartSite, Bookmarks, or History using this Favicon -> delete it
                if (!found) {
                    deleteFavicon(faviconURL)
                    Log.d("Favicon delete", faviconURL)
                }
            }
        }
    }

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
        fun setFavicon(context: Context?, view: View, url: String?, id: Int, idImage: Int) {
            val faviconView = view.findViewById<ImageView>(id)
            FaviconHelper(context).use { faviconHelper ->
                val bitmap = faviconHelper.getFavicon(url)
                if (faviconView != null) {
                    if (bitmap != null) faviconView.setImageBitmap(bitmap)
                    else faviconView.setImageResource(idImage)
                }
            }
        }

        @JvmStatic
        fun getGoogleFaviconUrl(domain: String?): String {
            if (domain == null || domain.trim().isEmpty()) return ""
            val cleanDomain = domain.replace("https://", "").replace("http://", "").split("/")[0]
            return "https://www.google.com/s2/favicons?domain=$cleanDomain&sz=64"
        }

        @JvmStatic
        fun getIconHorseFaviconUrl(domain: String?): String {
            if (domain == null || domain.trim().isEmpty()) return ""
            val cleanDomain = domain.replace("https://", "").replace("http://", "").split("/")[0]
            return "https://icon.horse/icon/$cleanDomain"
        }
    }
}
