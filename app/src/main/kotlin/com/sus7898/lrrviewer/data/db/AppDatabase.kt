package com.sus7898.lrrviewer.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
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

/** User-defined display order of server categories (local only; the server has no category ordering). */
@Entity(tableName = "category_order", primaryKeys = ["serverId", "categoryId"])
data class CategoryOrderEntity(
    val serverId: String,
    val categoryId: String,
    val position: Int,
)

@Dao
interface CategoryOrderDao {
    @Query("SELECT categoryId FROM category_order WHERE serverId = :serverId ORDER BY position ASC")
    fun observeOrder(serverId: String): Flow<List<String>>

    @Insert
    suspend fun insertAll(rows: List<CategoryOrderEntity>)

    @Query("DELETE FROM category_order WHERE serverId = :serverId")
    suspend fun deleteAll(serverId: String)

    @Transaction
    suspend fun replace(serverId: String, categoryIds: List<String>) {
        deleteAll(serverId)
        insertAll(categoryIds.mapIndexed { i, id -> CategoryOrderEntity(serverId, id, i) })
    }
}

/**
 * Pixel dimensions of archive pages, read once from the downloaded file. Lets the webtoon reader lay out
 * (and tile) pages before they are decoded, and is the basis for spreads / reading-mode detection.
 */
@Entity(tableName = "page_info", primaryKeys = ["serverId", "arcid", "pageIndex"])
data class PageInfoEntity(
    val serverId: String,
    val arcid: String,
    val pageIndex: Int,
    val width: Int,
    val height: Int,
)

@Dao
interface PageInfoDao {
    @Query("SELECT * FROM page_info WHERE serverId = :serverId AND arcid = :arcid")
    suspend fun forArchive(serverId: String, arcid: String): List<PageInfoEntity>

    @Query("SELECT * FROM page_info WHERE serverId = :serverId AND arcid = :arcid AND pageIndex = :pageIndex")
    suspend fun get(serverId: String, arcid: String, pageIndex: Int): PageInfoEntity?

    @Upsert
    suspend fun upsert(entity: PageInfoEntity)

    @Query("DELETE FROM page_info WHERE serverId = :serverId AND arcid = :arcid")
    suspend fun deleteArchive(serverId: String, arcid: String)
}

@Database(
    entities = [ReadingProgressEntity::class, CategoryOrderEntity::class, PageInfoEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun categoryOrderDao(): CategoryOrderDao
    abstract fun pageInfoDao(): PageInfoDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "lrrviewer.db").build()
    }
}
