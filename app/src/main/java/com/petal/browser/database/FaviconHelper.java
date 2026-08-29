package com.petal.browser.database;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.petal.browser.unit.HelperUnit;


public class FaviconHelper extends SQLiteOpenHelper {

    // Database Version
    private static final int DATABASE_VERSION = 1;
    // Database Name
    private static final String DATABASE_NAME = "favicon.db";
    // Table Name
    private static final String TABLE_FAVICON = "Favicon";
    // Column names
    private static final String DOMAIN = "domain";
    private static final String IMAGE = "image";
    // create Table statement
    private static final String CREATE_TABLE_FAVICON = "CREATE TABLE " + TABLE_FAVICON + "(" +
            DOMAIN + " TEXT," +
            IMAGE + " BLOB);";

    public FaviconHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static byte[] convertBytes(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream);
        return stream.toByteArray();
    }

    public static Bitmap getBitmap(byte[] byteimage) {
        return BitmapFactory.decodeByteArray(byteimage, 0, byteimage.length);
    }

    public static void setFavicon(Context context, View view, String url, int id, int idImage) {
        ImageView faviconView = view.findViewById(id);
        try(FaviconHelper faviconHelper = new FaviconHelper(context)) {
            Bitmap bitmap = faviconHelper.getFavicon(url);
            if (faviconView != null) {
                if (bitmap != null) faviconView.setImageBitmap(bitmap);
                else faviconView.setImageResource(idImage);
            }
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_FAVICON);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVICON);
        onCreate(db);
    }

    public synchronized void addFavicon(Context context, String url, Bitmap bitmap) throws SQLiteException {
        String domain = HelperUnit.domain(url);
        if (bitmap == null) return;
        SQLiteDatabase database = this.getWritableDatabase();
        //first delete existing Favicon for domain if available
        database.delete(TABLE_FAVICON, DOMAIN + " = ?", new String[]{domain.trim()});
        byte[] byteImage = convertBytes(bitmap);
        ContentValues values = new ContentValues();
        values.put(DOMAIN, domain);
        values.put(IMAGE, byteImage);
        database.insert(TABLE_FAVICON, null, values);
        database.close();
        cleanUpFaviconDB(context);
    }

    public synchronized void deleteFavicon(String domain) throws SQLiteException {
        SQLiteDatabase database = this.getWritableDatabase();
        database.delete(TABLE_FAVICON, DOMAIN + " = ?", new String[]{domain.trim()});
        database.close();
    }

    public synchronized Bitmap getFavicon(String url) {
        if (url == null) return null;
        String domain = HelperUnit.domain(url);
        try (SQLiteDatabase database = this.getReadableDatabase()) {
            Cursor cursor;
            cursor = database.query(TABLE_FAVICON,
                    new String[]{DOMAIN,
                            IMAGE},
                    DOMAIN + " = ?",
                    new String[]{domain}, null, null, null, null);
            byte[] image;

            if (cursor.moveToFirst()) {
                image = cursor.getBlob(1);
                cursor.close();
                database.close();
                return getBitmap(image);
            } else {
                database.close();
                return null;
            }
        }
    }

    public synchronized List<String> getAllFaviconDomains() {
        SQLiteDatabase database = this.getReadableDatabase();
        List<String> result = new ArrayList<>();
        Cursor cursor;
        cursor = database.query(TABLE_FAVICON,
                new String[]{DOMAIN,
                        IMAGE},
                null, null, null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            result.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        database.close();
        return result;
    }

    public void cleanUpFaviconDB(Context context) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            //Background work here
            List<String> faviconURLs = getAllFaviconDomains();
            RecordAction action = new RecordAction(context);
            List<Record> allEntries = action.listEntries((Activity) context);
            for (String faviconURL : faviconURLs) {
                boolean found = false;
                for (Record entry : allEntries) {
                    if (Objects.equals(HelperUnit.domain(entry.getURL()), faviconURL)) {
                        found = true;
                        break;
                    }
                }
                //If there is no entry in StartSite, Bookmarks, or History using this Favicon -> delete it
                if (!found) {
                    deleteFavicon(faviconURL);
                    Log.d("Favicon delete", faviconURL);
                }
            }
        });
    }

    /**
     * Constructs a high-resolution 64x64 favicon URL using Google Favicon API for any domain.
     * Endpoint: https://www.google.com/s2/favicons?domain={domain}&sz=64
     */
    public static String getGoogleFaviconUrl(String domain) {
        if (domain == null || domain.trim().isEmpty()) return "";
        String cleanDomain = domain.replace("https://", "").replace("http://", "").split("/")[0];
        return "https://www.google.com/s2/favicons?domain=" + cleanDomain + "&sz=64";
    }

    /**
     * Constructs high-resolution PNG favicon URL using IconHorse API as a secondary fallback.
     * Endpoint: https://icon.horse/icon/{domain}
     */
    public static String getIconHorseFaviconUrl(String domain) {
        if (domain == null || domain.trim().isEmpty()) return "";
        String cleanDomain = domain.replace("https://", "").replace("http://", "").split("/")[0];
        return "https://icon.horse/icon/" + cleanDomain;
    }
}