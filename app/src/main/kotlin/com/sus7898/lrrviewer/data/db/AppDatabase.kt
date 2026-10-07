package com.sus7898.lrrviewer.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Identifier of the (currently single) configured server. Kept in every row so multiple server profiles can be added later without a data migration. */
const val DEFAULT_SERVER_ID = "default"

@Entity(tableName = "reading_progress", primaryKeys = ["serverId", "arcid"])
data class ReadingProgressEntity(
    val serverId: String,
    val arcid: String,
    val title: String,
    /** 0-based index of the last page shown. */
    val page: Int,
    val pageCount: Int,
    val lastReadAt: Long,
    /** Per-archive override of the reading mode (name of [com.sus7898.lrrviewer.data.ReadingMode]), null = use the global default. */
    val readingModeOverride: String? = null,
)

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE serverId = :serverId ORDER BY lastReadAt DESC LIMIT :limit")
    fun observeRecent(serverId: String, limit: Int): Flow<List<ReadingProgressEntity>>

    @Query("SELECT * FROM reading_progress WHERE serverId = :serverId AND arcid = :arcid")
    suspend fun get(serverId: String, arcid: String): ReadingProgressEntity?

    @Upsert
    suspend fun upsert(entity: ReadingProgressEntity)

    @Query("UPDATE reading_progress SET readingModeOverride = :mode WHERE serverId = :serverId AND arcid = :arcid")
    suspend fun setReadingModeOverride(serverId: String, arcid: String, mode: String?)

    @Query("DELETE FROM reading_progress WHERE serverId = :serverId AND arcid = :arcid")
    suspend fun delete(serverId: String, arcid: String)

    @Query("DELETE FROM reading_progress WHERE serverId = :serverId")
    suspend fun deleteAll(serverId: String)
}

@Database(entities = [ReadingProgressEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readingProgressDao(): ReadingProgressDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "lrrviewer.db").build()
    }
}
