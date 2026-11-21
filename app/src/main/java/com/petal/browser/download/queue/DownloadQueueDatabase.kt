package com.petal.browser.download.queue

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: QueuedDownload): Long

    @Query("SELECT * FROM download_queue ORDER BY priority DESC, createdAt ASC, id ASC")
    suspend fun all(): List<QueuedDownload>

    @Query("SELECT * FROM download_queue ORDER BY priority DESC, createdAt ASC, id ASC")
    fun observeAll(): Flow<List<QueuedDownload>>

    @Query("DELETE FROM download_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM download_queue")
    suspend fun clear()
}

@Database(entities = [QueuedDownload::class], version = 1, exportSchema = false)
abstract class DownloadQueueDatabase : RoomDatabase() {
    abstract fun dao(): DownloadQueueDao

    companion object {
        @Volatile private var instance: DownloadQueueDatabase? = null

        fun get(context: Context): DownloadQueueDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, DownloadQueueDatabase::class.java, "petal_download_queue.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
