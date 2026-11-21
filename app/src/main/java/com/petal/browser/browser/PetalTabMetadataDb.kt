package com.petal.browser.browser

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Insert
import androidx.room.OnConflictStrategy

/** One row per tab, keyed by tabId. Mirrors [PetalTabMetadata]. */
@Entity(tableName = "tab_metadata")
data class PetalTabMetadataEntity(
    @PrimaryKey val tabId: String,
    val tabGroupId: String?,
    val tabGroupTitle: String?,
    val tabGroupColorHex: String?,
    val customTitle: String?,
    val workspaceId: String?,
    val isPinned: Boolean,
    val isPinnedGlobal: Boolean,
    val parentId: String?
) {
    fun toModel() = PetalTabMetadata(
        tabId, tabGroupId, tabGroupTitle, tabGroupColorHex, customTitle,
        workspaceId, isPinned, isPinnedGlobal, parentId
    )

    companion object {
        fun from(m: PetalTabMetadata) = PetalTabMetadataEntity(
            m.tabId, m.tabGroupId, m.tabGroupTitle, m.tabGroupColorHex, m.customTitle,
            m.workspaceId, m.isPinned, m.isPinnedGlobal, m.parentId
        )
    }
}

@Dao
interface PetalTabMetadataDao {
    @Query("SELECT * FROM tab_metadata")
    fun getAll(): List<PetalTabMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(rows: List<PetalTabMetadataEntity>)

    @Query("DELETE FROM tab_metadata")
    fun deleteAll()

    /** Atomically makes the table equal to [rows]. */
    @Transaction
    fun replaceAll(rows: List<PetalTabMetadataEntity>) {
        deleteAll()
        if (rows.isNotEmpty()) insertAll(rows)
    }
}

@Database(entities = [PetalTabMetadataEntity::class], version = 1, exportSchema = false)
abstract class PetalTabMetadataDatabase : RoomDatabase() {
    abstract fun dao(): PetalTabMetadataDao

    companion object {
        @Volatile private var instance: PetalTabMetadataDatabase? = null

        @JvmStatic
        fun get(context: Context): PetalTabMetadataDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PetalTabMetadataDatabase::class.java,
                    "petal_tab_metadata.db"
                ).build().also { instance = it }
            }
    }
}

/**
 * One-time import of the old SharedPreferences JSON blob (key "tab_metadata_map") into Room.
 * Kept free of Android types so it can be unit-tested on the JVM.
 */
object PetalTabMetadataMigration {
    /** Parses the legacy map; returns empty on blank/corrupt input (never throws). */
    @JvmStatic
    fun parseLegacyJson(json: String?): Map<String, PetalTabMetadata> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, PetalTabMetadata>>() {}.type
            val parsed: Map<String, PetalTabMetadata>? = com.google.gson.Gson().fromJson(json, type)
            parsed.orEmpty().filter { (k, v) -> k.isNotBlank() && v.tabId == k }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
